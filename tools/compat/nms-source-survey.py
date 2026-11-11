#!/usr/bin/env python3
# NMS 소스 조사(2026-11-11): 널리 쓰이는 플러그인의 1.21.1 대응 모듈 소스에서 net.minecraft·CraftBukkit·com.mojang 클래스 참조를 모아
# "몇 개 플러그인이 같은 클래스를 쓰는가"를 센다. 범용 NMS 어댑터 묶음·우선순위의 근거 — docs/feature-parity/compat-nms-adapter-plan-2026-11-11.md.
#
# 소스는 저장소에 넣지 않는다. 받는 법: MODS 의 저장소를 git clone --depth 1 로 받고, 1.21.1 모듈이 이미 지워진 저장소
# (Citizens2·WorldEdit·Shopkeepers·squaremap·Denizen)는 git fetch --shallow-since=2024-09-01 origin HEAD 뒤 COMMITS 의 커밋으로 checkout.
# FAWE 는 태그 2.16.2(폴더 이름 FastAsyncWorldEdit).
#
# 사용: python3 -I nms-source-survey.py <클론 폴더> <compat-paper src/main/java> > 표.md
# 한계: 클래스 단위다(멤버·리플렉션 문자열은 세지 않는다). Spigot 이름 소스(dynmap·TAB·DecentHolograms)는 패키지만 Mojang 과 같고
# 클래스 이름이 다르다(IBlockData·MinecraftKey·PacketPlayOut…) — 따로 세어져 클래스별 숫자가 낮게 나온다(검토 2026-11-11: 합치면
# ResourceLocation 6→9, BlockState 5→7, 게임 패킷 46→40종). 합치려면 H7d 변환표가 필요해 도구는 합치지 않는다.
# 와일드카드 import(pkg.*)는 다른 곳에서 본 같은 패키지 클래스 이름이 파일에 나오면 그 클래스로 센다.
import collections
import os
import re
import sys

MODS = {
    'FAWE': ['FastAsyncWorldEdit/worldedit-bukkit/adapters/adapter-1_21'],
    'WorldEdit': ['EngineHub_WorldEdit/worldedit-bukkit/adapters/adapter-1.21'],
    'Citizens': ['CitizensDev_Citizens2/v1_21_R2'],          # 1.21.1 모듈은 이미 지워져 1.21.3 판으로 근사
    'Shopkeepers': ['Shopkeepers_Shopkeepers/modules/v1_21_R1'],
    'Denizen': ['DenizenScript_Denizen/v1_21'],
    'dynmap': ['webbukkit_dynmap/bukkit-helper-121'],
    'DecentHolograms': ['DecentSoftware-eu_DecentHolograms/nms/nms-v1_21_R1'],
    'TAB': ['NEZNAMY_TAB/bukkit/v1_21_R1'],
    'LibsDisguises': ['libraryaddict_LibsDisguises/nms/legacy/v1_21_R1'],
    'squaremap': ['jpenilla_squaremap/paper/src/main', 'jpenilla_squaremap/common/src/main'],   # 1.21.3 판
}
COMMITS = {
    'CitizensDev_Citizens2': '8b4a4dd', 'EngineHub_WorldEdit': '920a810', 'Shopkeepers_Shopkeepers': '2a83f0a',
    'jpenilla_squaremap': '8e3cfea', 'DenizenScript_Denizen': '3f523e9',
}

WILDCARD = re.compile(r'^import\s+((?:net\.minecraft|org\.bukkit\.craftbukkit)\.[\w.]+)\.\*;', re.M)
WORD = re.compile(r'\b[A-Z]\w*\b')
IMPORT = re.compile(r'^import\s+(?:static\s+)?((?:net\.minecraft|org\.bukkit\.craftbukkit|com\.mojang)\.[\w.]+?)(?:\.\*)?;', re.M)
QUALIFIED = re.compile(r'\b((?:net\.minecraft|org\.bukkit\.craftbukkit)\.(?:[a-z_0-9]+\.)+[A-Z]\w*)')


def norm(name):
    name = re.sub(r'\.v1_\d+_R\d+', '', name)
    out = []
    for part in name.split('.'):
        out.append(part)
        if part[:1].isupper():
            break
    return '.'.join(out)


def scan(root):
    use = collections.defaultdict(set)
    wild = []   # (플러그인, 패키지, 파일에 나온 대문자 이름들)
    for plugin, dirs in MODS.items():
        for d in dirs:
            for dp, _, fs in os.walk(os.path.join(root, d)):
                for f in fs:
                    if not f.endswith('.java'):
                        continue
                    s = open(os.path.join(dp, f), encoding='utf-8', errors='ignore').read()
                    hits = {norm(x) for x in IMPORT.findall(s)} | {norm(x) for x in QUALIFIED.findall(s)}
                    for h in hits:
                        if h.split('.')[-1][:1].isupper():
                            use[h].add(plugin)
                    pkgs = WILDCARD.findall(s)
                    if pkgs:
                        words = set(WORD.findall(s))
                        wild.extend((plugin, norm(p + '.X')[:-2], words) for p in pkgs)
    known = set(use)
    for plugin, pkg, words in wild:
        for w in words:
            if pkg + '.' + w in known:
                use[pkg + '.' + w].add(plugin)
    return use


def shims(src):
    have = set()
    for top in ('net/minecraft', 'com/mojang', 'org/bukkit/craftbukkit'):
        for dp, _, fs in os.walk(os.path.join(src, top)):
            for f in fs:
                if f.endswith('.java'):
                    have.add(os.path.relpath(os.path.join(dp, f[:-5]), src).replace(os.sep, '.'))
    return have


def group(c):
    p = c.split('.')
    if c.startswith('org.bukkit.craftbukkit'):
        return 'craftbukkit.' + ('.'.join(p[3:-1]) or '(root)')
    if c.startswith('com.mojang'):
        return '.'.join(p[:3])
    return '.'.join(p[2:-1][:3]) or '(root)'


def main():
    use, have = scan(sys.argv[1]), shims(sys.argv[2])
    groups = collections.defaultdict(lambda: [set(), 0, 0])
    for c, ps in use.items():
        g = groups[group(c)]
        g[0].update(ps)
        g[1] += 1
        g[2] += c in have
    print('## 패키지 묶음별 (2개 이상 플러그인)\n\n| 묶음 | 플러그인 수 | 클래스 수 | 이미 shim | 플러그인 |\n|---|---:|---:|---:|---|')
    for k, (ps, n, h) in sorted(groups.items(), key=lambda x: (-len(x[1][0]), -x[1][1], x[0])):
        if len(ps) >= 2:
            print(f'| `{k}` | {len(ps)} | {n} | {h} | {", ".join(sorted(ps))} |')
    print('\n## 클래스별 (4개 이상 플러그인)\n\n| 클래스 | 플러그인 수 | shim | 플러그인 |\n|---|---:|---|---|')
    for c, ps in sorted(use.items(), key=lambda x: (-len(x[1]), x[0])):
        if len(ps) >= 4:
            print(f'| `{c}` | {len(ps)} | {"있음" if c in have else ""} | {", ".join(sorted(ps))} |')


if __name__ == '__main__':
    main()
