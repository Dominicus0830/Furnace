#!/usr/bin/env python3
# 바닐라 데이터 생성기 reports/items.json → items/components_1.21.1.json.gz (아이템 이름 → 기본 컴포넌트, 최소화 JSON).
# 사용: python3 extract_item_components.py <reports/items.json> <출력.json.gz>
import gzip, json, sys
items = json.load(open(sys.argv[1], encoding='utf-8'))
out = {name: v['components'] for name, v in items.items()}
with gzip.open(sys.argv[2], 'wt', encoding='utf-8', compresslevel=9) as f:
    json.dump(out, f, separators=(',', ':'), sort_keys=False)
print(len(out))
