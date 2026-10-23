#!/usr/bin/env python3
"""데이터 팩 태그를 클라이언트 숫자 id 로 바꿀 때 필요한 내장 레지스트리 순서(protocol_id)를 뽑는다.

사용: extract_builtin_registries.py <reports/registries.json> <출력 builtin-1.21.1.txt>
  reports = `java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports --output <out>` 결과.
출력: 줄마다 `<레지스트리> <엔트리>` — 레지스트리별 protocol_id 순서(0, 1, 2 …).
태그가 있는 내장 레지스트리와 아이템 컴포넌트 인코딩에 필요한 것을 담는다(동적 레지스트리 순서는 registries/1.21.1.txt).
"""
import json, sys

REGS = ['minecraft:block', 'minecraft:item', 'minecraft:entity_type', 'minecraft:fluid', 'minecraft:game_event',
        'minecraft:instrument', 'minecraft:cat_variant', 'minecraft:point_of_interest_type',
        # 아이템 컴포넌트 네트워크 인코딩용(태그 없음)
        'minecraft:data_component_type', 'minecraft:potion', 'minecraft:mob_effect', 'minecraft:attribute', 'minecraft:sound_event', 'minecraft:map_decoration_type', 'minecraft:armor_material',
        # 메뉴 종류(open_screen 의 menuTypeId)
        'minecraft:menu']
reports = json.load(open(sys.argv[1]))
with open(sys.argv[2], 'w', encoding='utf-8') as out:
    out.write('# 자동 생성: tools/vanilla-data/extract_builtin_registries.py (바닐라 1.21.1 reports/registries.json). 직접 수정하지 말 것.\n')
    for reg in REGS:
        entries = sorted(reports[reg]['entries'].items(), key=lambda kv: kv[1]['protocol_id'])
        for i, (name, v) in enumerate(entries):
            assert v['protocol_id'] == i, (reg, name)
            out.write('%s %s\n' % (reg, name))
