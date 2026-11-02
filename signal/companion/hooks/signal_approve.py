#!/usr/bin/env python3
"""Herdr Signal：Claude Code PreToolUse 审批 hook（手机批准）。

流程：
  1. Claude 要调用工具 → 本 hook 写 `<dir>/<request_id>.req`（原子 rename），
     并通过 herdr socket 给自己所在的 pane 挂一个 token `sa_<request_id 尾部>=<request_id>`；
  2. 手机看到请求（SFTP 轮询目录，或 snapshot 里的 token 触发立即读取）→ 写 `<request_id>.answer`；
  3. hook 校验 request_id + nonce 后返回 allow / deny；
  4. 超时没人回答 → 返回 "ask"，退回 Claude 自己的终端确认，电脑前的人照常处理。

身份：request_id 优先用 Claude 提供的 tool_use_id（并发调用各自独立）；pane 来自 herdr 注入的
HERDR_PANE_ID，所以手机能把请求准确挂到对应的 agent 上，不靠猜。

配置（环境变量）：
  SIGNAL_APPROVALS_DIR   请求目录，默认 ~/spike-sandbox/approvals
  SIGNAL_APPROVAL_TTL    等待秒数，默认 120
  SIGNAL_APPROVAL_TOOLS  需要手机批准的工具，逗号分隔，默认 Bash
  SIGNAL_HOOK_LOG        若设置，把每次的原始 hook 输入追加到这个文件（实验用）

只依赖 Python 3 标准库。任何异常都返回 "ask"，绝不因为 hook 自己出错而放行。
"""
import json
import os
import signal
import re
import secrets
import socket
import sys
import time

SOURCE = "signal:approve"


def out(decision: str, reason: str) -> None:
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": decision,
            "permissionDecisionReason": reason,
        }
    }, ensure_ascii=False))
    sys.exit(0)


def herdr(method: str, params: dict) -> None:
    """尽力而为地调用 herdr socket；失败不影响审批本身。"""
    path, pane = os.environ.get("HERDR_SOCKET_PATH"), os.environ.get("HERDR_PANE_ID")
    if not path or not pane:
        return
    req = {"id": f"{SOURCE}:{time.time_ns()}", "method": method, "params": {"pane_id": pane, "source": SOURCE, **params}}
    try:
        c = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        c.settimeout(0.5)
        c.connect(path)
        c.sendall((json.dumps(req) + "\n").encode())
        try:
            c.recv(4096)
        except Exception:
            pass
        c.close()
    except Exception:
        pass


def summarize(tool: str, ti: dict) -> str:
    if tool == "Bash":
        return str(ti.get("command", ""))
    for k in ("file_path", "path", "url", "pattern", "query"):
        if ti.get(k):
            return f"{tool} {ti[k]}"
    return f"{tool} {json.dumps(ti, ensure_ascii=False)[:300]}"


def main() -> None:
    raw = sys.stdin.read()
    log = os.environ.get("SIGNAL_HOOK_LOG")
    if log:
        with open(log, "a", encoding="utf-8") as f:
            f.write(json.dumps({"at": time.time(), "pane": os.environ.get("HERDR_PANE_ID"), "input": json.loads(raw or "{}")}, ensure_ascii=False) + "\n")
    hook = json.loads(raw or "{}")
    tool = str(hook.get("tool_name") or "")
    tools = {t.strip() for t in os.environ.get("SIGNAL_APPROVAL_TOOLS", "Bash").split(",") if t.strip()}
    if tool not in tools:
        sys.exit(0)  # 不管的工具：不输出决定，走 Claude 自己的规则

    d = os.path.expanduser(os.environ.get("SIGNAL_APPROVALS_DIR", "~/spike-sandbox/approvals"))
    ttl = int(os.environ.get("SIGNAL_APPROVAL_TTL", "120"))
    os.makedirs(d, exist_ok=True)
    tuid = str(hook.get("tool_use_id") or "")
    rid = re.sub(r"[^A-Za-z0-9_-]", "", tuid)[-40:] or secrets.token_hex(8)
    nonce = secrets.token_hex(8)
    now = int(time.time())
    ti = hook.get("tool_input") or {}
    payload = {
        "schema_version": 1,
        "request_id": rid,
        "nonce": nonce,
        "question": str(ti.get("description") or f"Claude 想运行 {tool}"),
        "created_at": now,
        "expires_at": now + ttl,
        "pane_id": os.environ.get("HERDR_PANE_ID"),
        "tool": tool,
        "command": summarize(tool, ti),
        "cwd": hook.get("cwd"),
        "agent": "claude",
        "session_id": hook.get("session_id"),
        "tool_use_id": tuid or None,
    }
    req, ans = os.path.join(d, rid + ".req"), os.path.join(d, rid + ".answer")
    # 每个请求一个 token 键（并发工具调用会同时有多个待批准）；键名限 32 个字符
    tkey = "sa_" + rid[-29:]

    def cleanup() -> None:
        for p in (req, ans):
            try:
                os.remove(p)
            except FileNotFoundError:
                pass
        herdr("pane.report_metadata", {"tokens": {tkey: None}})

    # Claude 取消工具调用（用户按 Esc / 超时）会结束 hook 进程：也要清理，否则手机上会留下过期卡片
    def on_signal(signum, _frame):
        cleanup()
        os._exit(1)

    for sig in (signal.SIGTERM, signal.SIGHUP, signal.SIGINT):
        signal.signal(sig, on_signal)
    tmp = req + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)
    os.replace(tmp, req)
    herdr("pane.report_metadata", {"tokens": {tkey: rid}, "ttl_ms": ttl * 1000})

    decision = None
    try:
        deadline = time.time() + ttl
        while time.time() < deadline:
            if os.path.exists(ans):
                try:
                    a = json.load(open(ans, encoding="utf-8"))
                except Exception:
                    time.sleep(0.2)
                    continue
                if a.get("request_id") == rid and a.get("nonce") == nonce and a.get("decision") in ("allow", "deny"):
                    decision = a["decision"]
                else:
                    decision = "invalid"
                break
            time.sleep(0.5)
    finally:
        cleanup()

    if decision == "allow":
        out("allow", "在手机上批准（Herdr Signal）")
    if decision == "deny":
        out("deny", "在手机上拒绝（Herdr Signal）")
    if decision == "invalid":
        out("ask", "手机回答校验失败，改在终端确认")
    out("ask", f"手机 {ttl} 秒内没有回答，改在终端确认")


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as e:  # 绝不因 hook 出错而放行
        out("ask", f"审批 hook 出错（{type(e).__name__}），改在终端确认")
