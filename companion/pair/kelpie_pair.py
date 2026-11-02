#!/usr/bin/env python3
"""Kelpie：在开发机上生成配对二维码，手机扫一下就添加这台主机。

在**要被添加的那台开发机**上运行（它需要读本机的 SSH host key）：

    python3 kelpie_pair.py --session test            # 生成手机专用密钥，询问是否加入 authorized_keys，显示二维码
    python3 kelpie_pair.py --session test --png qr.png
    python3 kelpie_pair.py --list                    # 列出已发给手机的密钥
    python3 kelpie_pair.py --revoke <名称>           # 从 authorized_keys 删除这台手机的密钥

二维码内容是一个链接 `kelpie://pair?d=<base64url(JSON)>`，JSON 字段：
    v 版本(1)  n 名称  h 地址  p 端口  u 用户  s herdr 会话  b herdr 路径
    k {算法: "SHA256:…"}  本机全部 SSH host key 的指纹（手机据此固定指纹，不需要再人工核对）
    key 手机专用的 OpenSSH 私钥（ed25519）   exp 过期时间（unix 秒）

安全说明：
- 二维码里有私钥。只在自己的屏幕上显示，不要截图外传。过期时间只限制手机端接受旧二维码，
  已经加入 authorized_keys 的公钥不会自动失效；不用了请 --revoke。
- 每次配对生成一把新密钥，注释为 `kelpie-<名称>-<日期>`，方便单独撤销。
- 不改 sshd 配置，不碰已有的密钥；只在你确认后往 ~/.ssh/authorized_keys 追加一行。

只依赖 Python 3 标准库和 ssh-keygen。二维码编码器在本文件里（字节模式，自动选版本，纠错级别 L/M）。
"""
import argparse
import base64
import datetime
import getpass
import hashlib
import json
import os
import re
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import time
import zlib

COMMENT_PREFIX = "kelpie-"
# 改名前（Herdr Signal）发的密钥注释是 herdr-signal-…，--list / --revoke 也认
LEGACY_PREFIXES = ("herdr-signal-",)
ALL_PREFIXES = (COMMENT_PREFIX, *LEGACY_PREFIXES)


# ───────────────────────────── QR 编码（ISO/IEC 18004，字节模式） ─────────────────────────────

_ECC_CODEWORDS = {
    "L": (-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
    "M": (-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
}
_NUM_BLOCKS = {
    "L": (-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
    "M": (-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
}
_FORMAT_BITS = {"L": 1, "M": 0}


def _gf_mul(x: int, y: int) -> int:
    z = 0
    for i in reversed(range(8)):
        z = (z << 1) ^ ((z >> 7) * 0x11D)
        z ^= ((y >> i) & 1) * x
    return z


def _rs_divisor(degree: int) -> list:
    result = [0] * (degree - 1) + [1]
    root = 1
    for _ in range(degree):
        for j in range(degree):
            result[j] = _gf_mul(result[j], root)
            if j + 1 < degree:
                result[j] ^= result[j + 1]
        root = _gf_mul(root, 0x02)
    return result


def _rs_remainder(data: list, divisor: list) -> list:
    result = [0] * len(divisor)
    for b in data:
        factor = b ^ result.pop(0)
        result.append(0)
        for i, coef in enumerate(divisor):
            result[i] ^= _gf_mul(coef, factor)
    return result


def _raw_modules(ver: int) -> int:
    result = (16 * ver + 128) * ver + 64
    if ver >= 2:
        n = ver // 7 + 2
        result -= (25 * n - 10) * n - 55
        if ver >= 7:
            result -= 36
    return result


def _data_codewords(ver: int, ecl: str) -> int:
    return _raw_modules(ver) // 8 - _ECC_CODEWORDS[ecl][ver] * _NUM_BLOCKS[ecl][ver]


def _align_positions(ver: int, size: int) -> list:
    if ver == 1:
        return []
    n = ver // 7 + 2
    step = 26 if ver == 32 else (ver * 4 + n * 2 + 1) // (n * 2 - 2) * 2
    return list(reversed([size - 7 - i * step for i in range(n - 1)] + [6]))


class QR:
    def __init__(self, data: bytes, ecl: str = "M"):
        for ver in range(1, 41):
            cap_bits = _data_codewords(ver, ecl) * 8
            need = 4 + (8 if ver < 10 else 16) + len(data) * 8
            if need <= cap_bits:
                break
        else:
            raise ValueError(f"内容太长（{len(data)} 字节），放不进二维码")
        self.ver, self.ecl = ver, ecl
        self.size = ver * 4 + 17
        n = self.size
        self.m = [[False] * n for _ in range(n)]
        self.fn = [[False] * n for _ in range(n)]
        self._draw_function_patterns()
        self._draw_codewords(self._add_ecc(self._encode(data, cap_bits)))
        best, best_pen = 0, None
        for mask in range(8):
            self._apply_mask(mask)
            self._draw_format(mask)
            pen = self._penalty()
            if best_pen is None or pen < best_pen:
                best, best_pen = mask, pen
            self._apply_mask(mask)  # 撤销（异或）
        self._apply_mask(best)
        self._draw_format(best)

    # 功能图形
    def _set_fn(self, x, y, dark):
        self.m[y][x] = dark
        self.fn[y][x] = True

    def _draw_function_patterns(self):
        n = self.size
        for i in range(n):
            self._set_fn(6, i, i % 2 == 0)
            self._set_fn(i, 6, i % 2 == 0)
        for cx, cy in ((3, 3), (n - 4, 3), (3, n - 4)):
            for dy in range(-4, 5):
                for dx in range(-4, 5):
                    x, y = cx + dx, cy + dy
                    if 0 <= x < n and 0 <= y < n:
                        self._set_fn(x, y, max(abs(dx), abs(dy)) not in (2, 4))
        pos = _align_positions(self.ver, n)
        last = len(pos) - 1
        for i, a in enumerate(pos):
            for j, b in enumerate(pos):
                if (i, j) in ((0, 0), (0, last), (last, 0)):
                    continue
                for dy in range(-2, 3):
                    for dx in range(-2, 3):
                        self._set_fn(a + dx, b + dy, max(abs(dx), abs(dy)) != 1)
        self._draw_format(0)  # 先占位
        if self.ver >= 7:
            rem = self.ver
            for _ in range(12):
                rem = (rem << 1) ^ ((rem >> 11) * 0x1F25)
            bits = self.ver << 12 | rem
            for i in range(18):
                bit = (bits >> i) & 1 == 1
                a, b = n - 11 + i % 3, i // 3
                self._set_fn(a, b, bit)
                self._set_fn(b, a, bit)

    def _draw_format(self, mask):
        data = _FORMAT_BITS[self.ecl] << 3 | mask
        rem = data
        for _ in range(10):
            rem = (rem << 1) ^ ((rem >> 9) * 0x537)
        bits = (data << 10 | rem) ^ 0x5412
        bit = lambda i: (bits >> i) & 1 == 1
        n = self.size
        for i in range(6):
            self._set_fn(8, i, bit(i))
        self._set_fn(8, 7, bit(6))
        self._set_fn(8, 8, bit(7))
        self._set_fn(7, 8, bit(8))
        for i in range(9, 15):
            self._set_fn(14 - i, 8, bit(i))
        for i in range(8):
            self._set_fn(n - 1 - i, 8, bit(i))
        for i in range(8, 15):
            self._set_fn(8, n - 15 + i, bit(i))
        self._set_fn(8, n - 8, True)

    # 数据
    def _encode(self, data: bytes, cap_bits: int) -> list:
        bits = []
        def put(val, length):
            bits.extend((val >> i) & 1 for i in reversed(range(length)))
        put(0b0100, 4)
        put(len(data), 8 if self.ver < 10 else 16)
        for b in data:
            put(b, 8)
        put(0, min(4, cap_bits - len(bits)))
        put(0, -len(bits) % 8)
        pad = 0xEC
        while len(bits) < cap_bits:
            put(pad, 8)
            pad ^= 0xEC ^ 0x11
        return [int("".join(map(str, bits[i:i + 8])), 2) for i in range(0, len(bits), 8)]

    def _add_ecc(self, data: list) -> list:
        nblocks = _NUM_BLOCKS[self.ecl][self.ver]
        ecclen = _ECC_CODEWORDS[self.ecl][self.ver]
        raw = _raw_modules(self.ver) // 8
        nshort = nblocks - raw % nblocks
        shortlen = raw // nblocks
        div = _rs_divisor(ecclen)
        blocks, k = [], 0
        for i in range(nblocks):
            dat = data[k:k + shortlen - ecclen + (0 if i < nshort else 1)]
            k += len(dat)
            ecc = _rs_remainder(dat, div)
            if i < nshort:
                dat = dat + [0]
            blocks.append(dat + ecc)
        out = []
        for i in range(len(blocks[0])):
            for j, blk in enumerate(blocks):
                if i != shortlen - ecclen or j >= nshort:
                    out.append(blk[i])
        return out

    def _draw_codewords(self, cw: list):
        n, i, total = self.size, 0, len(cw) * 8
        right = n - 1
        while right >= 1:
            if right == 6:
                right = 5
            for vert in range(n):
                for j in range(2):
                    x = right - j
                    upward = (right + 1) & 2 == 0
                    y = n - 1 - vert if upward else vert
                    if not self.fn[y][x] and i < total:
                        self.m[y][x] = (cw[i >> 3] >> (7 - (i & 7))) & 1 == 1
                        i += 1
            right -= 2

    def _apply_mask(self, mask):
        f = (
            lambda x, y: (x + y) % 2 == 0,
            lambda x, y: y % 2 == 0,
            lambda x, y: x % 3 == 0,
            lambda x, y: (x + y) % 3 == 0,
            lambda x, y: (x // 3 + y // 2) % 2 == 0,
            lambda x, y: x * y % 2 + x * y % 3 == 0,
            lambda x, y: (x * y % 2 + x * y % 3) % 2 == 0,
            lambda x, y: ((x + y) % 2 + x * y % 3) % 2 == 0,
        )[mask]
        for y in range(self.size):
            for x in range(self.size):
                if not self.fn[y][x] and f(x, y):
                    self.m[y][x] = not self.m[y][x]

    def _penalty(self) -> int:
        """罚分（规则 1–4）：只用来挑一个更好扫的掩码，不影响正确性。
        规则 3（数据里出现像定位图案的 1:1:3:1:1）不能省：实测会让识别器找错定位点。"""
        n, m, pen = self.size, self.m, 0
        p1, p2 = [1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0], [0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1]
        for lines in (m, [list(c) for c in zip(*m)]):
            for row in lines:
                r = [0] * 4 + [1 if v else 0 for v in row] + [0] * 4  # 两侧当作静区
                for i in range(len(r) - 10):
                    w = r[i:i + 11]
                    if w == p1 or w == p2:
                        pen += 40
                run, prev = 0, None
                for v in row:
                    if v == prev:
                        run += 1
                    else:
                        if run >= 5:
                            pen += run - 2
                        run, prev = 1, v
                if run >= 5:
                    pen += run - 2
        for y in range(n - 1):
            for x in range(n - 1):
                if m[y][x] == m[y][x + 1] == m[y + 1][x] == m[y + 1][x + 1]:
                    pen += 3
        dark = sum(map(sum, m))
        pen += abs(dark * 20 - n * n * 10) // (n * n) * 10
        return pen

    # 输出
    def terminal(self, quiet: int = 2) -> str:
        """用上半块字符一行画两排模块；显式指定黑白前景 / 背景色，深色终端也能扫。"""
        n = self.size
        dark = lambda x, y: 0 <= x < n and 0 <= y < n and self.m[y][x]
        lines = []
        for y in range(-quiet, n + quiet, 2):
            s = []
            for x in range(-quiet, n + quiet):
                top, bot = dark(x, y), dark(x, y + 1)
                s.append(f"\033[{30 if top else 97}m\033[{40 if bot else 107}m▀")
            lines.append("".join(s) + "\033[0m")
        return "\n".join(lines)

    def png(self, path: str, scale: int = 8, quiet: int = 4):
        n = self.size
        w = (n + 2 * quiet) * scale
        rows = []
        for py in range(w):
            y = py // scale - quiet
            row = bytearray([0])
            for px in range(w):
                x = px // scale - quiet
                row.append(0 if 0 <= x < n and 0 <= y < n and self.m[y][x] else 255)
            rows.append(bytes(row))
        def chunk(t, d):
            return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
        with open(path, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n")
            f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, w, 8, 0, 0, 0, 0)))
            f.write(chunk(b"IDAT", zlib.compress(b"".join(rows), 9)))
            f.write(chunk(b"IEND", b""))


# ───────────────────────────── 配对 ─────────────────────────────

def host_key_fingerprints(ssh_dir: str) -> dict:
    out = {}
    for name in sorted(os.listdir(ssh_dir)):
        if not (name.startswith("ssh_host_") and name.endswith("_key.pub")):
            continue
        try:
            alg, b64 = open(os.path.join(ssh_dir, name)).read().split()[:2]
            blob = base64.b64decode(b64)
        except Exception:
            continue
        fp = "SHA256:" + base64.b64encode(hashlib.sha256(blob).digest()).decode().rstrip("=")
        out[alg] = fp
    return out


def all_addresses() -> list:
    """本机所有非回环 IPv4（`ip -4 -o addr`），用来在有多块网卡 / VPN 时提醒用户选地址。"""
    try:
        out = subprocess.run(["ip", "-4", "-o", "addr", "show"], capture_output=True, text=True, timeout=3).stdout
    except Exception:
        return []
    res = []
    for line in out.splitlines():
        parts = line.split()
        if len(parts) >= 4 and parts[2] == "inet":
            ip = parts[3].split("/")[0]
            if not ip.startswith("127."):
                res.append((parts[1], ip))
    return res


def default_address() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("10.255.255.255", 1))  # 不发包，只为选出对外网卡的地址
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return socket.gethostname()


def generate_key(comment: str):
    d = tempfile.mkdtemp(prefix="kelpie-pair-")
    try:
        path = os.path.join(d, "key")
        subprocess.run(["ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", comment, "-f", path], check=True)
        return open(path).read(), open(path + ".pub").read().strip()
    finally:
        shutil.rmtree(d, ignore_errors=True)


def authorized_keys_path() -> str:
    return os.path.expanduser("~/.ssh/authorized_keys")


def install_pubkey(pub: str):
    p = authorized_keys_path()
    os.makedirs(os.path.dirname(p), mode=0o700, exist_ok=True)
    existing = open(p).read() if os.path.exists(p) else ""
    with open(p, "a") as f:
        if existing and not existing.endswith("\n"):
            f.write("\n")
        f.write(pub + "\n")
    os.chmod(p, 0o600)


def list_keys() -> list:
    p = authorized_keys_path()
    if not os.path.exists(p):
        return []
    return [l.rstrip("\n") for l in open(p) if any(pre in l for pre in ALL_PREFIXES)]


def revoke(label: str) -> int:
    p = authorized_keys_path()
    if not os.path.exists(p):
        return 0
    lines = open(p).readlines()
    pat = re.compile(r"\s(?:" + "|".join(re.escape(p) for p in ALL_PREFIXES) + r")" + re.escape(slug(label)) + r"-\d{8}\s*$")
    keep = [l for l in lines if not pat.search(l)]
    removed = len(lines) - len(keep)
    if removed:
        shutil.copy2(p, p + f".bak.{int(time.time())}")
        with open(p, "w") as f:
            f.writelines(keep)
    return removed


def slug(s: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "-", s).strip("-") or "phone"


def build_link(payload: dict) -> str:
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
    return "kelpie://pair?d=" + base64.urlsafe_b64encode(raw).decode().rstrip("=")


def main():
    ap = argparse.ArgumentParser(description="生成 Kelpie 配对二维码")
    ap.add_argument("--label", default=socket.gethostname(), help="手机上显示的名称（默认主机名）")
    ap.add_argument("--host", default=None, help="手机连接用的地址（默认对外网卡 IP）")
    ap.add_argument("--port", type=int, default=22)
    ap.add_argument("--user", default=getpass.getuser())
    ap.add_argument("--session", default=os.environ.get("HERDR_SESSION", "default"), help="herdr 会话名")
    ap.add_argument("--herdr-bin", default=shutil.which("herdr") or os.path.expanduser("~/.local/bin/herdr"))
    ap.add_argument("--ssh-dir", default="/etc/ssh", help="读取 host key 的目录")
    ap.add_argument("--key-file", help="不生成新密钥，改用这把已有的私钥（它必须已经在 authorized_keys 里）")
    ap.add_argument("--install", choices=("ask", "yes", "no"), default="ask", help="是否把新公钥加入 ~/.ssh/authorized_keys")
    ap.add_argument("--ttl", type=int, default=600, help="二维码有效秒数（手机端检查）")
    ap.add_argument("--ecl", choices=("L", "M"), default="L", help="纠错级别（屏幕显示用 L 就够）")
    ap.add_argument("--png", help="另存一份 PNG")
    ap.add_argument("--link", action="store_true", help="同时打印链接文本")
    ap.add_argument("--list", action="store_true", help="列出已发给手机的密钥")
    ap.add_argument("--revoke", metavar="LABEL", help="从 authorized_keys 删除某台手机的密钥")
    a = ap.parse_args()

    if a.list:
        keys = list_keys()
        print("\n".join(k.split()[-1] for k in keys) if keys else "没有 kelpie 密钥")
        return
    if a.revoke:
        n = revoke(a.revoke)
        print(f"删除了 {n} 行（已备份 authorized_keys）" if n else "没有找到对应的密钥")
        return

    fps = host_key_fingerprints(a.ssh_dir)
    if not fps:
        sys.exit(f"在 {a.ssh_dir} 里找不到 ssh_host_*_key.pub，无法给出指纹")

    if a.key_file:
        key = open(os.path.expanduser(a.key_file)).read()
        if "PRIVATE KEY-----" not in key:
            sys.exit("--key-file 不像 OpenSSH 私钥")
    else:
        comment = f"{COMMENT_PREFIX}{slug(a.label)}-{datetime.date.today():%Y%m%d}"
        key, pub = generate_key(comment)
        do = a.install
        if do == "ask":
            print(f"新的手机专用公钥：\n  {pub}\n")
            do = "yes" if input(f"加入 {authorized_keys_path()}？[y/N] ").strip().lower() in ("y", "yes") else "no"
        if do == "yes":
            install_pubkey(pub)
            print(f"已加入。以后可以用 --revoke {slug(a.label)} 撤销。\n")
        else:
            print("没有安装公钥：手机能添加主机，但要等你把上面这行公钥加进 authorized_keys 才能连上。\n")

    host = a.host
    if not host:
        host = default_address()
        cands = all_addresses()
        if len(cands) > 1:
            print("这台机器有多个地址，默认用了 " + host + "。手机连不上这个地址的话，用 --host 指定：")
            for dev, ip in cands:
                print(f"  {ip:<16} {dev}")
            print()
    payload = {
        "v": 1, "n": a.label, "h": host, "p": a.port, "u": a.user,
        "s": a.session, "b": a.herdr_bin, "k": fps, "key": key, "exp": int(time.time()) + a.ttl,
    }
    link = build_link(payload)
    qr = QR(link.encode(), a.ecl)
    print(qr.terminal())
    print(f"\n{a.label}  {a.user}@{payload['h']}:{a.port}  会话 {a.session}  （二维码 {a.ttl // 60} 分钟内有效，版本 {qr.ver}）")
    for alg, fp in fps.items():
        print(f"  {alg:<22} {fp}")
    print("在 Kelpie 里：设置 → 添加主机 → 扫码。看完请清屏（二维码里有私钥）。")
    if a.png:
        qr.png(a.png)
        os.chmod(a.png, 0o600)
        print(f"PNG：{a.png}（用完请删除）")
    if a.link:
        print("\n" + link)


if __name__ == "__main__":
    main()
