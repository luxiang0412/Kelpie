#!/usr/bin/env python3
"""找出 Kotlin 源码里含中文的字符串字面量（跳过注释、处理 ${} 嵌套）。
extract：打印 JSON 列表 [{file,start,end,text}]
apply <map.json>：把 "中文" 换成 t("中文", "English")（map: 中文原文 -> 英文原文，原文都是字面量内部的源码文本）"""
import json, re, sys, pathlib
CJK = re.compile(r'[一-鿿]')

def literals(src):
    i, n, out = 0, len(src), []
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            i = src.find('\n', i); i = n if i < 0 else i; continue
        if src.startswith('/*', i):
            j = src.find('*/', i + 2); i = n if j < 0 else j + 2; continue
        if c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == '\\' else 1
            i = j + 1; continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3); i = n if j < 0 else j + 3; continue
        if c == '"':
            j = i + 1; depth = 0
            while j < n:
                ch = src[j]
                if depth == 0 and ch == '\\': j += 2; continue
                if src.startswith('${', j): depth += 1; j += 2; continue
                if depth > 0:
                    if ch == '{': depth += 1
                    elif ch == '}': depth -= 1
                    elif ch == '"':  # 嵌套字面量
                        k = j + 1
                        while k < n and src[k] != '"': k += 2 if src[k] == '\\' else 1
                        j = k
                    j += 1; continue
                if ch == '"': break
                j += 1
            out.append((i, j + 1, src[i + 1:j])); i = j + 1; continue
        i += 1
    return out

def files():
    root = pathlib.Path('.')
    for p in sorted(root.glob('*/**/src/main/kotlin/**/*.kt')) + sorted(root.glob('app/src/main/kotlin/**/*.kt')):
        yield p

SKIP_LINE = re.compile(r'Log\.(i|w|d|e)\(|require\(|const val')

def extract():
    seen, res = set(), []
    for p in dict.fromkeys(files()):
        s = p.read_text()
        for a, b, t in literals(s):
            if not CJK.search(t): continue
            line = s[s.rfind('\n', 0, a) + 1: s.find('\n', b)]
            if SKIP_LINE.search(line) or 't("' in line[max(0, a - s.rfind(chr(10), 0, a) - 4):a - s.rfind(chr(10), 0, a)]: continue
            res.append({'file': str(p), 'text': t})
    print(json.dumps(res, ensure_ascii=False, indent=0))

def apply(mp):
    m = json.load(open(mp))
    for p in dict.fromkeys(files()):
        s = p.read_text(); out, last, changed = [], 0, False
        for a, b, t in literals(s):
            if t not in m: continue
            pre = s[max(0, a - 3):a]
            if re.search(r'(^|\W)t\($', pre): continue  # 已经包过；注意 Text( 也以 t( 结尾
            out.append(s[last:a]); out.append(f't("{t}", "{m[t]}")'); last = b; changed = True
        if changed:
            out.append(s[last:]); s2 = ''.join(out)
            pkg_imp = 'import dev.herdr.core.herdr.t\n'
            if pkg_imp not in s2 and not s2.startswith('package dev.herdr.core.herdr\n'):
                s2 = re.sub(r'(\npackage [^\n]+\n)', r'\1\n' + pkg_imp, '\n' + s2, count=1)[1:]
            p.write_text(s2); print('updated', p)

if sys.argv[1] == 'extract': extract()
else: apply(sys.argv[2])
