#!/usr/bin/env python3
"""检查 md 文档里的**路径型**引用是否存在（不含裸文件名/网址）。

用法: python3 tools/check_doc_links.py [--fix-map old=new ...]
只检查包含 '/' 的引用（真实路径），裸文件名视为正文提及，不判定为死链。
"""
import io, os, re, subprocess, sys

REF = re.compile(r'(?<![\w/])((?:[\w.\-]+/)+[\w.\-]+\.(?:md|png|json|csv|txt|html))')

def tracked(pattern='*.md'):
    return subprocess.run(['git', 'ls-files', pattern], capture_output=True, text=True).stdout.split()

def main():
    fixes = {}
    args = sys.argv[1:]
    for i, a in enumerate(args):
        if a == '--fix-map':
            for pair in args[i + 1:]:
                if '=' not in pair:
                    continue
                old, new = pair.split('=', 1)
                fixes[old] = new
    dangling = {}
    for f in tracked():
        try:
            txt = io.open(f, encoding='utf-8').read()
        except Exception:
            continue
        new_txt = txt
        for old, new in fixes.items():
            if old in new_txt:
                new_txt = new_txt.replace(old, new)
        if new_txt != txt:
            io.open(f, 'w', encoding='utf-8', newline='\n').write(new_txt)
            print(f'fixed: {f}')
        for m in REF.finditer(new_txt):
            ref = m.group(1)
            if ref.startswith('http'):
                continue
            base = os.path.dirname(f)
            if os.path.exists(os.path.join(base, ref)) or os.path.exists(ref):
                continue
            dangling.setdefault(ref, set()).add(f)
    if not dangling:
        print('OK: 无悬空路径引用')
        return 0
    print(f'悬空路径引用 {len(dangling)} 种:')
    for ref, where in sorted(dangling.items(), key=lambda kv: -len(kv[1])):
        sample = sorted(where)[0]
        print(f'  {ref}  <- {len(where)} 处, 例: {sample}')
    return 1

if __name__ == '__main__':
    sys.exit(main())
