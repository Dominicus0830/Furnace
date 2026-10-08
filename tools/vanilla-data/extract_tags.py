#!/usr/bin/env python3
"""바닐라 데이터 생성기 출력에서 Update Tags 리소스를 만든다.

사용: extract_tags.py <generated 디렉터리> <furnace registries/1.21.1.txt> <출력 tags-1.21.1.txt>
  <generated> = `java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --all --output generated` 결과.
출력 형식(한 줄 = 태그 하나): `<레지스트리 키> <태그 id> <엔트리 숫자 id>...`
  - 내장 레지스트리(block, item, fluid, entity_type, game_event, ...)의 id는 reports/registries.json의 protocol_id.
  - 동적 동기화 레지스트리(damage_type, enchantment, banner_pattern, painting_variant, worldgen/biome)의 id는
    Furnace가 클라이언트로 보내는 레지스트리 순서(registries/1.21.1.txt)에서의 인덱스.
  - `#태그` 참조는 재귀적으로 펼친다(바닐라 TagLoader와 같이 첫 등장 순서 유지, 중복 제거).
"""
import json, os, sys

gen, regfile, out = sys.argv[1], sys.argv[2], sys.argv[3]
reports = json.load(open(os.path.join(gen, 'reports/registries.json')))
builtin = {k: {n: v['protocol_id'] for n, v in r['entries'].items()} for k, r in reports.items()}

dyn = {}
for line in open(regfile, encoding='utf-8'):
    line = line.strip()
    if not line or line.startswith('#'): continue
    reg, entry = line.split(' ', 1)
    dyn.setdefault(reg, []).append(entry)
dyn_ids = {k: {n: i for i, n in enumerate(v)} for k, v in dyn.items()}

tags_root = os.path.join(gen, 'data/minecraft/tags')
# (태그 디렉터리, 레지스트리 키)
targets = []
for d in sorted(os.listdir(tags_root)):
    if d == 'worldgen':
        if os.path.isdir(os.path.join(tags_root, 'worldgen/biome')):
            targets.append(('worldgen/biome', 'minecraft:worldgen/biome'))
        continue
    targets.append((d, 'minecraft:' + d))

lines = []
for tdir, reg in targets:
    ids = builtin.get(reg) or dyn_ids.get(reg)
    if ids is None:
        sys.exit('레지스트리 id를 알 수 없음: %s' % reg)
    base = os.path.join(tags_root, tdir)
    raw = {}
    for root, _, files in os.walk(base):
        for f in files:
            if f.endswith('.json'):
                name = os.path.relpath(os.path.join(root, f), base)[:-5].replace(os.sep, '/')
                raw['minecraft:' + name] = json.load(open(os.path.join(root, f)))
    cache = {}
    def resolve(tag, stack=()):
        if tag in cache: return cache[tag]
        if tag in stack: sys.exit('태그 순환: %s' % tag)
        if tag not in raw: sys.exit('없는 태그 참조: %s (%s)' % (tag, reg))
        seen, res = set(), []
        for v in raw[tag]['values']:
            e = v['id'] if isinstance(v, dict) else v
            req = v.get('required', True) if isinstance(v, dict) else True
            items = resolve(e[1:], stack + (tag,)) if e.startswith('#') else [e]
            for it in items:
                if it in seen: continue
                if it not in ids:
                    if not req: continue
                    sys.exit('레지스트리에 없는 엔트리: %s in %s (%s)' % (it, tag, reg))
                seen.add(it); res.append(it)
        cache[tag] = res
        return res
    for tag in sorted(raw):
        lines.append('%s %s %s' % (reg, tag, ' '.join(str(ids[e]) for e in resolve(tag))))

with open(out, 'w', encoding='utf-8') as f:
    f.write('# 자동 생성: tools/vanilla-data/extract_tags.py (바닐라 1.21.1 데이터 생성기 출력). 직접 수정하지 말 것.\n')
    f.write('\n'.join(lines) + '\n')
print('태그 %d개 기록 (%s)' % (len(lines), out))
