#!/usr/bin/env python3
"""Kelpie：手机审批 hook。同一个脚本支持两种 agent：

  - Claude Code：PreToolUse hook，只拦 KELPIE_APPROVAL_TOOLS 里的工具（默认 Bash）
  - Codex CLI：PermissionRequest hook（`~/.codex/hooks.json`），只在 Codex 本来就要问人时触发，所以不再筛工具
  按 hook 输入里的 hook_event_name 区分。

流程：
  1. Claude 要调用工具 → 本 hook 写 `<dir>/<request_id>.req`（原子 rename），
     并通过 herdr socket 给自己所在的 pane 挂一个 token `sa_<request_id 尾部>=<request_id>`；
  2. 手机看到请求（SFTP 轮询目录，或 snapshot 里的 token 触发立即读取）→ 写 `<request_id>.answer`；
  3. hook 校验 request_id + nonce 后返回 allow / deny；
  4. 超时没人回答 → 退回 agent 自己的终端确认（Claude 返回 "ask"；Codex 不给决定），电脑前的人照常处理。

身份：request_id 优先用 Claude 提供的 tool_use_id（并发调用各自独立）；pane 来自 herdr 注入的
HERDR_PANE_ID，所以手机能把请求准确挂到对应的 agent 上，不靠猜。

配置（环境变量）：
  KELPIE_APPROVALS_DIR   请求目录，默认 ~/.local/state/kelpie/approvals（App 的主机设置里要填同一个目录）
  KELPIE_APPROVAL_TTL    等待秒数，默认 120
  KELPIE_APPROVAL_TOOLS  需要手机批准的工具，逗号分隔，默认 Bash
  KELPIE_HOOK_LOG        若设置，把每次的原始 hook 输入追加到这个文件（实验用）

只依赖 Python 3 标准库。任何异常都退回终端确认，绝不因为 hook 自己出错而放行。
"""
import json
import os
import signal
import re
import secrets
import socket
import sys
import time

SOURCE = "kelpie:approve"
# 和 App 里 ApprovalsPath.DEFAULT 一致；改的话两边一起改
DEFAULT_DIR = "~/.local/state/kelpie/approvals"


CODEX = False  # main() 里按 hook_event_name 设置


def out(decision: str, reason: str) -> None:
    """allow / deny 是明确决定；ask 表示退回 agent 自己的终端确认。"""
    if CODEX:
        # Codex PermissionRequest：behavior 只有 allow / deny；不输出决定就走 Codex 自己的确认
        if decision in ("allow", "deny"):
            print(json.dumps({
                "hookSpecificOutput": {
                    "hookEventName": "PermissionRequest",
                    "decision": {"behavior": decision, "message": reason},
                }
            }, ensure_ascii=False))
        sys.exit(0)
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


def watch_parent(parent: int, req: str, ans: str, cleanup, ttl: int) -> None:
    """Codex 中断（Esc）时用 SIGKILL 结束 hook，信号处理来不及清理。
    分出一个脱离进程组的看守进程：hook 进程没了而请求还在，就替它清理。"""
    try:
        pid = os.fork()
    except OSError:
        return
    if pid:
        return
    try:
        os.setsid()
        # 不能占着 hook 的 stdout：agent 要等它关闭才读结果
        null = os.open(os.devnull, os.O_RDWR)
        for fd in (0, 1, 2):
            os.dup2(null, fd)
        deadline = time.time() + ttl + 10
        while time.time() < deadline:
            if not os.path.exists(req):
                os._exit(0)  # hook 自己清理过了
            try:
                os.kill(parent, 0)
            except ProcessLookupError:
                cleanup()
                os._exit(0)
            time.sleep(0.5)
        cleanup()
    finally:
        os._exit(0)


def summarize(tool: str, ti) -> str:
    if not isinstance(ti, dict):
        return f"{tool} {ti}"[:300]
    cmd = ti.get("command") or ti.get("cmd")
    if isinstance(cmd, list):  # Codex：["bash", "-lc", "…"]
        cmd = cmd[-1] if len(cmd) >= 3 and str(cmd[-2]).startswith("-") else " ".join(map(str, cmd))
    if cmd and (tool in ("Bash", "shell", "exec_command", "local_shell", "shell_command") or CODEX):
        return str(cmd)
    for k in ("file_path", "path", "url", "pattern", "query"):
        if ti.get(k):
            return f"{tool} {ti[k]}"
    return f"{tool} {json.dumps(ti, ensure_ascii=False)[:300]}"


def main() -> None:
    raw = sys.stdin.read()
    log = os.environ.get("KELPIE_HOOK_LOG")
    if log:
        with open(log, "a", encoding="utf-8") as f:
            f.write(json.dumps({"at": time.time(), "pane": os.environ.get("HERDR_PANE_ID"), "input": json.loads(raw or "{}")}, ensure_ascii=False) + "\n")
    hook = json.loads(raw or "{}")
    global CODEX
    CODEX = hook.get("hook_event_name") == "PermissionRequest"
    agent = "codex" if CODEX else "claude"
    tool = str(hook.get("tool_name") or "")
    if not CODEX and hook.get("permission_mode") == "bypassPermissions":
        # claude --dangerously-skip-permissions：用户已经选择不被问，手机上也不问
        sys.exit(0)
    if not CODEX:
        tools = {t.strip() for t in os.environ.get("KELPIE_APPROVAL_TOOLS", "Bash").split(",") if t.strip()}
        if tool not in tools:
            sys.exit(0)  # 不管的工具：不输出决定，走 Claude 自己的规则

    d = os.path.expanduser(os.environ.get("KELPIE_APPROVALS_DIR", DEFAULT_DIR))
    ttl = int(os.environ.get("KELPIE_APPROVAL_TTL", "120"))
    os.makedirs(d, mode=0o700, exist_ok=True)  # 请求里有命令内容，只给自己看
    tuid = str(hook.get("tool_use_id") or hook.get("call_id") or "")
    rid = re.sub(r"[^A-Za-z0-9_-]", "", tuid)[-40:] or secrets.token_hex(8)
    nonce = secrets.token_hex(8)
    now = int(time.time())
    ti = hook.get("tool_input") or {}
    desc = ti.get("description") or ti.get("justification") if isinstance(ti, dict) else None
    name = "Codex" if CODEX else "Claude"
    payload = {
        "schema_version": 1,
        "request_id": rid,
        "nonce": nonce,
        "question": str(desc or f"{name} 想运行 {tool or '命令'}"),
        "created_at": now,
        "expires_at": now + ttl,
        "pane_id": os.environ.get("HERDR_PANE_ID"),
        "tool": tool,
        "command": summarize(tool, ti),
        "cwd": hook.get("cwd"),
        "agent": agent,
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

    # agent 取消工具调用（用户按 Esc / 超时）会结束 hook 进程：也要清理，否则手机上会留下过期卡片
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
    watch_parent(os.getpid(), req, ans, cleanup, ttl)

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
        out("allow", "在手机上批准（Kelpie）")
    if decision == "deny":
        out("deny", "在手机上拒绝（Kelpie）")
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
