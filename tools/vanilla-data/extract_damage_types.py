#!/usr/bin/env python3
"""바닐라 data/minecraft/damage_type/*.json 에서 피해 유형 표를 만든다.

사용: extract_damage_types.py <data/minecraft/damage_type 디렉터리> <출력 damage_types-1.21.1.txt>
출력 형식(한 줄 = 유형 하나, 파일 이름 순): `<id> <message_id> <scaling> <exhaustion> <effects> <death_message_type>`
  - effects 기본값 hurt, death_message_type 기본값 default(바닐라 코덱 기본값).
"""
import json, os, sys

src, out = sys.argv[1], sys.argv[2]
lines = ['# 바닐라 1.21.1 damage_type (tools/vanilla-data/extract_damage_types.py 로 생성)',
         '# id message_id scaling exhaustion effects death_message_type']
for f in sorted(os.listdir(src)):
    if not f.endswith('.json'): continue
    d = json.load(open(os.path.join(src, f), encoding='utf-8'))
    lines.append(' '.join(['minecraft:' + f[:-5], d['message_id'], d['scaling'], repr(float(d['exhaustion'])),
                           d.get('effects', 'hurt'), d.get('death_message_type', 'default')]))
with open(out, 'w', encoding='utf-8') as o:
    o.write('\n'.join(lines) + '\n')
