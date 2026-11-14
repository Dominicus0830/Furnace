#!/usr/bin/env python3
# N2b-0 필드·배열 수준 NMS 조사(2026-11-14): 플러그인 소스에서 "청크 내부를 메서드가 아니라 필드·배열·원시 자료로 만지는" 모양을 센다.
# nms-source-survey.py 는 클래스 단위라 이 구분을 못 한다. 결과 표 — docs/feature-parity/compat-nms-n2b0-survey-2026-11-14.md.
#
# 사용: python3 -I nms-field-survey.py <폴더> — <폴더>/<플러그인 이름>/**.java 를 플러그인별로 센다(소스는 저장소에 넣지 않는다).
# 한계: 글자 맞추기다. 리플렉션 문자열(getDeclaredField("e"))은 이름만 보이고 대상 클래스는 사람이 확인해야 한다. 각 줄은 위치를 같이 낸다.
import collections
import os
import re
import sys

# (분류, 설명, 정규식). 분류: W1 = 섹션 객체 메서드 쓰기, W2 = 섹션·컨테이너 통째 교체(필드·배열), W3 = 원시 배열 제자리 쓰기,
# R = 다른 스레드 읽기에 쓰이는 섹션·컨테이너 접근, P = 후처리(저장 표시·패킷·빛), X = 리플렉션·핸들(대상 확인 필요)
PATTERNS = [
    ('W1', 'LevelChunkSection.setBlockState', r'\.setBlockState\(\s*\w+\s*&\s*15|section\w*\.setBlockState\(|\bsetBlockState\(\s*\w+\s*,\s*\w+\s*,\s*\w+\s*,'),
    ('W1', 'PalettedContainer.getAndSet(Unchecked)/set', r'\.getAndSet(?:Unchecked)?\(|\.states\.set\('),
    ('W1', 'PalettedContainer acquire/release(잠금)', r'\.acquire\(\)|\.release\(\)'),
    ('W2', 'new LevelChunkSection', r'new\s+LevelChunkSection\('),
    ('W2', 'new PalettedContainer', r'new\s+PalettedContainer\s*<|PALETTEDCONTAINER_CTOR|PalettedContainer\.class\.getConstructors'),
    ('W2', '섹션 배열 원소 교체(CAS·arrayElement)', r'compareAndSet\(|arrayElement(?:Setter|VarHandle)|getSections\(\)\s*\[[^\]]+\]\s*='),
    ('W2', '바이옴 컨테이너 필드 교체', r'setBiomesToChunkSection|biomes\s*=\s*|fieldBiomes|\.set\(\s*section'),
    ('W2', '개수 필드 직접 대입(nonEmpty·ticking)', r'(?i)tickingBlockCount|tickingFluidCount|nonEmptyBlockCount|clearCounts|fieldTickingBlockCount'),
    ('W2', '하이트맵 setRawData', r'setRawData\('),
    ('W3', '원시 long[]·DataLayer byte[] 접근', r'\.getRaw\(\)|getRawData\(\)|new\s+DataLayer\(|DataLayer\b.*\.getData\('),
    ('R', 'getSections()', r'getSections\(\)'),
    ('R', 'getStates()/getBiomes() 복사·읽기', r'getStates\(\)|getBiomes\(\)|\.copy\(\)'),
    ('R', 'heightmaps 필드·getOrCreateHeightmapUnprimed', r'\.heightmaps\b|getOrCreateHeightmapUnprimed|hasPrimedHeightmap|primeHeightmaps'),
    ('P', 'setUnsaved/markUnsaved/setLightCorrect', r'setUnsaved\(|markUnsaved\(|setLightCorrect\(|mustNotSave'),
    ('P', '청크 패킷 직접 만들기·보내기', r'new\s+ClientboundLevelChunkWithLightPacket|ClientboundLevelChunkWithLightPacket\.STREAM_CODEC'),
    ('P', '빛 다시 계산 직접 호출', r'relight|lightEngine|getLightEngine\(\)\.(?:checkBlock|propagate)'),
    ('X', 'getDeclaredField/findGetter/findSetter·Unsafe', r'getDeclaredField\(|find(?:Getter|Setter|VarHandle|Virtual)\(|Unsafe\b'),
]


def main(root):
    hits = collections.defaultdict(lambda: collections.defaultdict(list))
    plugins = sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))
    comp = [(k, desc, re.compile(rx)) for k, desc, rx in PATTERNS]
    for p in plugins:
        for dp, _, fs in os.walk(os.path.join(root, p)):
            for f in sorted(fs):
                if not f.endswith('.java'):
                    continue
                path = os.path.join(dp, f)
                with open(path, encoding='utf-8', errors='replace') as fh:
                    for n, line in enumerate(fh, 1):
                        s = line.strip()
                        if s.startswith('//') or s.startswith('*') or s.startswith('import '):
                            continue
                        for k, desc, rx in comp:
                            if rx.search(line):
                                hits[(k, desc)][p].append(f'{os.path.relpath(path, os.path.join(root, p))}:{n}')
    print('| 분류 | 모양 | ' + ' | '.join(plugins) + ' |')
    print('|---|---|' + '---|' * len(plugins))
    for k, desc, _ in comp:
        row = [str(len(hits[(k, desc)].get(p, []))) or '0' for p in plugins]
        print(f'| {k} | {desc} | ' + ' | '.join(c if c != '0' else '·' for c in row) + ' |')
    if '-v' in sys.argv:
        for k, desc, _ in comp:
            for p in plugins:
                for loc in hits[(k, desc)].get(p, [])[:8]:
                    print(f'{k}\t{desc}\t{p}\t{loc}')


if __name__ == '__main__':
    main(sys.argv[1])
