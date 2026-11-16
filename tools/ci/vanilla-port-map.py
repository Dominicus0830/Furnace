#!/usr/bin/env python3
"""@VanillaPort 주석을 모아 바닐라 클래스 → Furnace 파일 대응표(TSV)를 만든다.

사용: python3 tools/ci/vanilla-port-map.py [출력 경로, 기본 build/vanilla-port-map.tsv]
열: 바닐라 클래스 <TAB> Furnace 파일(저장소 기준) <TAB> Furnace 클래스
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
TYPE = re.compile(r'^(?:public\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+|static\s+)*(?:class|record|enum|interface|@interface)\s+(\w+)', re.M)


def main() -> None:
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'build' / 'vanilla-port-map.tsv'
    rows = []
    for path in sorted(ROOT.glob('*/src/main/java/**/*.java')):
        text = path.read_text(encoding='utf-8')
        if '@VanillaPort' not in text or path.name == 'VanillaPort.java':
            continue
        # 최상위 선언 앞의 표지만 읽는다(들여쓰기 없는 줄)
        for m in re.finditer(r'^@(?:org\.domi\.furnace\.server\.)?VanillaPort\((.*?)\)\s*$', text, re.M | re.S):
            names = re.findall(r'"([^"]+)"', m.group(1))
            t = TYPE.search(text, m.end())
            cls = t.group(1) if t else path.stem
            for n in names:
                rows.append((n, str(path.relative_to(ROOT)), cls))
    rows.sort()
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text('vanilla\tfile\tclass\n' + ''.join('\t'.join(r) + '\n' for r in rows), encoding='utf-8')
    print(f'{len(rows)}줄 -> {out}')


if __name__ == '__main__':
    main()
