#!/usr/bin/env python3
"""바닐라 데이터 생성기 출력에서 블록 태그를 '블록 이름' 목록으로 펼쳐 월드젠용 리소스를 만든다.

사용: extract_block_tags.py <generated 디렉터리> <출력 block_tags-1.21.1.txt>
출력 형식(한 줄 = 태그 하나): `<태그 id> <블록 이름>...` (`#` 참조는 재귀로 펼침, 첫 등장 순서·중복 제거)
"""
import json, os, sys

gen, out = sys.argv[1], sys.argv[2]
blocks = set(json.load(open(os.path.join(gen, 'reports/registries.json')))['minecraft:block']['entries'])
base = os.path.join(gen, 'data/minecraft/tags/block')
raw = {}
for root, _, files in os.walk(base):
    for f in files:
        if f.endswith('.json'):
            name = os.path.relpath(os.path.join(root, f), base)[:-5].replace(os.sep, '/')
            raw['minecraft:' + name] = json.load(open(os.path.join(root, f)))
cache = {}
def resolve(tag, stack=()):
    if tag in cache: return cache[tag]
    if tag in stack: sys.exit('태그 순환: ' + tag)
    seen, res = set(), []
    for v in raw[tag]['values']:
        e = v['id'] if isinstance(v, dict) else v
        required = v.get('required', True) if isinstance(v, dict) else True
        items = resolve(e[1:], stack + (tag,)) if e.startswith('#') else [e]
        for it in items:
            if it in seen: continue
            if it not in blocks:
                if not required: continue
                sys.exit('블록이 아님: %s in %s' % (it, tag))
            seen.add(it); res.append(it)
    cache[tag] = res
    return res
with open(out, 'w', encoding='utf-8') as f:
    f.write('# 자동 생성: tools/vanilla-data/extract_block_tags.py (바닐라 1.21.1). 직접 수정하지 말 것.\n')
    for t in sorted(raw):
        f.write('%s %s\n' % (t, ' '.join(resolve(t))))
print('블록 태그 %d개' % len(raw))
