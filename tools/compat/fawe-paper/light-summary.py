# M5: 두 월드의 빛을 편집 청크·둘레 한 줄·그 밖으로 나눠 칸 단위로 센다(anvil-diff.py 를 불러 씀).
# 쓰기: python -X utf8 light-summary.py 기준월드 편집전월드 A월드 B월드 --chunks x0 z0 x1 z1
import importlib.util, os, sys
from collections import Counter
spec = importlib.util.spec_from_file_location("ad", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "anvil-diff.py"))
ad = importlib.util.module_from_spec(spec); spec.loader.exec_module(ad)

def blocks_differ(a, b):
    sa, sb = ad.sections_by_y(a), ad.sections_by_y(b)
    for y in set(sa) | set(sb):
        ba = ad.decode_blocks(sa.get(y, {})) or ["minecraft:air"] * 4096
        bb = ad.decode_blocks(sb.get(y, {})) or ["minecraft:air"] * 4096
        if ba != bb: return True
    return False

def light_counts(a, b):
    c = Counter()
    sa, sb = ad.sections_by_y(a), ad.sections_by_y(b)
    for y in set(sa) | set(sb):
        A, B = sa.get(y, {}), sb.get(y, {})
        for lk in ("BlockLight", "SkyLight"):
            la, lb = A.get(lk), B.get(lk)
            if la == lb: continue
            if la is None or lb is None:
                p = la if la is not None else lb
                c[lk + ".onlyOneSide.sections"] += 1
                c[lk + ".onlyOneSide.nonzeroCells"] += sum(1 for i in range(4096) if ad.nib(p, i))
                continue
            c[lk + ".cells"] += sum(1 for i in range(4096) if ad.nib(la, i) != ad.nib(lb, i))
    return c

def main():
    base, before, a, b = sys.argv[1:5]
    x0, z0, x1, z1 = map(int, sys.argv[sys.argv.index("--chunks") + 1:][:4])
    coords = [(x, z) for z in range(z0, z1 + 1) for x in range(x0, x1 + 1)]
    edited = {(x, z) for x, z in coords if blocks_differ(ad.read_chunk(base, x, z), ad.read_chunk(before, x, z))}
    ring = {(x + dx, z + dz) for x, z in edited for dx in (-1, 0, 1) for dz in (-1, 0, 1)} - edited
    ring = {p for p in ring if p in set(coords)}
    cls = {"edited": Counter(), "ring": Counter(), "other": Counter()}
    n = Counter()
    for p in coords:
        k = "edited" if p in edited else "ring" if p in ring else "other"
        n[k] += 1
        cls[k].update(light_counts(ad.read_chunk(a, *p), ad.read_chunk(b, *p)))
    for k in cls: print(k, "chunks=%d" % n[k], dict(sorted(cls[k].items())))

main()
