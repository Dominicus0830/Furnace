#!/usr/bin/env python3
"""블록 정의(바닐라 1.21.1 데이터 생성기 reports/blocks.json)에서 Furnace 블록 동작 틀이 쓰는 표를 뽑는다.

사용: extract_block_definitions.py <reports/blocks.json> <출력 blocks/definitions_1.21.1.txt>
줄마다: <블록> <정의 종류> <첫 상태 id> <기본 상태 id> <속성들> <매개변수 JSON>
  - 정의 종류: definition.type(바닐라 블록 코덱 = 블록 클래스 종류, 예 minecraft:stair)
  - 속성들: 이름=값|값…;… (바닐라 StateDefinition 순서 — 이름순, 상태 id 는 마지막 속성이 가장 빨리 바뀐다), 없으면 -
  - 매개변수: definition 에서 type·properties 를 뺀 나머지(나무 종류·버튼 지속 틱·계단 바탕 상태 등), 없으면 {}
"""
import json, sys

blocks = json.load(open(sys.argv[1]))
with open(sys.argv[2], 'w', encoding='utf-8') as out:
    out.write('# 자동 생성: tools/vanilla-data/extract_block_definitions.py (바닐라 1.21.1 reports/blocks.json). 직접 수정하지 말 것.\n')
    for name, b in blocks.items():
        d = b['definition']
        states = b['states']
        first = min(s['id'] for s in states)
        default = next(s['id'] for s in states if s.get('default'))
        props = b.get('properties', {})
        # 혼합 기수 배치 검사: 마지막 속성이 가장 빨리 바뀐다
        names = sorted(props.keys())
        for s in states:
            idx = 0
            for p in names:
                idx = idx * len(props[p]) + props[p].index(s['properties'][p])
            assert s['id'] == first + idx, (name, s)
        pspec = ';'.join(p + '=' + '|'.join(props[p]) for p in names) or '-'
        params = {k: v for k, v in d.items() if k not in ('type', 'properties')}
        out.write('%s %s %d %d %s %s\n' % (name, d['type'], first, default, pspec, json.dumps(params, separators=(',', ':'), sort_keys=True)))
