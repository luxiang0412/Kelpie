#!/usr/bin/env python3
"""用 TinyPNG 压缩仓库内的 PNG 证据（保留原文件不达标时不动）。

用法: python3 tools/compress_png.py <api-key> [<api-key> ...]
- 逐张 POST 压缩，输出更小才替换；
- 记录每张的前后体积与压缩比；
- 配额用尽（HTTP 429）时自动换下一个 key。
"""
import base64, json, os, subprocess, sys, time, urllib.error, urllib.request

API = 'https://api.tinypng.com/shrink'

def png_files():
    out = subprocess.run(['git', 'ls-files', '*.png'], capture_output=True, text=True).stdout.split()
    return [f for f in out if os.path.exists(f)]

def shrink(key, path):
    data = open(path, 'rb').read()
    req = urllib.request.Request(API, data=data, headers={
        'Authorization': 'Basic ' + base64.b64encode(('api:' + key).encode()).decode(),
        'Content-Type': 'image/png',
    })
    with urllib.request.urlopen(req, timeout=120) as r:
        body = json.load(r)
        used = r.headers.get('Compression-Count')
    if body['output']['size'] >= body['input']['size']:
        return None, used
    with urllib.request.urlopen(body['output']['url'], timeout=120) as r:
        return r.read(), used

def main(keys):
    files = png_files()
    rows, used_total, ki = [], 0, 0
    for i, f in enumerate(files, 1):
        before = os.path.getsize(f)
        if before < 40_000:
            continue
        for attempt in range(len(keys)):
            key = keys[(ki + attempt) % len(keys)]
            try:
                data, used = shrink(key, f)
                ki = (ki + attempt) % len(keys)
                used_total = int(used or used_total)
                break
            except urllib.error.HTTPError as e:
                if e.code == 429:
                    print(f'  key #{ki+1} 配额用尽，换下一个')
                    ki = (ki + 1) % len(keys)
                    continue
                print(f'  {f}: HTTP {e.code}')
                data = None
                break
            except Exception as e:
                print(f'  {f}: {type(e).__name__} {e}')
                data = None
                break
        if not data:
            continue
        open(f, 'wb').write(data)
        after = os.path.getsize(f)
        rows.append((f, before, after))
        print(f'[{i}/{len(files)}] {f}  {before/1048576:.2f} -> {after/1048576:.2f} MB')
    before_all = sum(r[1] for r in rows); after_all = sum(r[2] for r in rows)
    print(f'合计 {len(rows)} 张：{before_all/1048576:.1f} MB -> {after_all/1048576:.1f} MB '
          f'({(1-after_all/before_all)*100:.0f}% 省下)，TinyPNG 已用配额 {used_total}')
    return rows

if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit('需要至少一个 TinyPNG API key')
    main(sys.argv[1:])
