#!/usr/bin/env python3
"""把 `cmd package dump-profiles` 的可读输出转成 baseline-prof.txt（HRF）。

用法：profdump2baseline.py dump.prof.txt > app/src/main/baseline-prof.txt
只保留 KEEP 中的包：Compose 等库自带基线配置，这里补自己的代码和没有配置的依赖。
"""
import re, sys
from collections import defaultdict

KEEP = ("dev/herdr/", "net/schmizz/", "com/hierynomus/", "org/bouncycastle/",
        "kotlinx/serialization/", "kotlinx/coroutines/", "androidx/navigation/", "androidx/room/")
PRIM = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S",
        "int": "I", "long": "J", "float": "F", "double": "D"}

def desc(t: str) -> str:
    t = t.strip()
    dims = 0
    while t.endswith("[]"):
        dims += 1; t = t[:-2]
    base = PRIM.get(t) or "L" + t.replace(".", "/") + ";"
    return "[" * dims + base

def main(path):
    flags = defaultdict(set)
    classes = set()
    section = None
    for line in open(path, encoding="utf-8", errors="replace"):
        s = line.strip()
        if s.endswith(":") and s[:-1] in ("hot methods", "startup methods", "post startup methods", "classes"):
            section = s[:-1]; continue
        if not s or section is None or s.startswith(("base.apk", "===", "/")):
            continue
        if section == "classes":
            if " " not in s: classes.add(desc(s))
            continue
        s = re.sub(r"\[[^\]]*\],?\s*$", "", s).rstrip(", ")
        m = re.match(r"^(\S+) (.+)\(([^)]*)\)$", s)
        if not m: continue
        ret, qual, args = m.groups()
        cls, _, name = qual.rpartition(".")
        argd = "".join(desc(a) for a in args.split(",") if a.strip())
        key = f"{desc(cls)}->{name}({argd}){desc(ret)}"
        flags[key].add({"hot methods": "H", "startup methods": "S", "post startup methods": "P"}[section])
    out = []
    for c in sorted(classes):
        if c[1:].startswith(KEEP): out.append(c)
    for k in sorted(flags):
        if k[1:].startswith(KEEP):
            f = "".join(x for x in "HSP" if x in flags[k])
            out.append(f + k)
    print("\n".join(out))

main(sys.argv[1])
