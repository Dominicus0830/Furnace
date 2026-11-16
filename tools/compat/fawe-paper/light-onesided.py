# M5: 한쪽에만 있는 빛 섹션의 값 모양(한결같음/섞임)을 분류한다.
import importlib.util, os, sys
from collections import Counter
spec = importlib.util.spec_from_file_location("ad", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "anvil-diff.py")); ad = importlib.util.module_from_spec(spec); spec.loader.exec_module(ad)
base, ref, a, b = sys.argv[1:5]
x0,z0,x1,z1 = map(int, sys.argv[6:10]) if False else (-8,-8,7,7)
def blocks_differ(p, q):
    sa, sb = ad.sections_by_y(p), ad.sections_by_y(q)
    return any((ad.decode_blocks(sa.get(y,{})) or ["a"]*4096) != (ad.decode_blocks(sb.get(y,{})) or ["a"]*4096) for y in set(sa)|set(sb))
edited = {(x,z) for z in range(z0,z1+1) for x in range(x0,x1+1) if blocks_differ(ad.read_chunk(base,x,z), ad.read_chunk(ref,x,z))}
out = {"edited": Counter(), "nonedited": Counter()}
for z in range(z0,z1+1):
    for x in range(x0,x1+1):
        A, B = ad.read_chunk(a,x,z), ad.read_chunk(b,x,z)
        sa, sb = ad.sections_by_y(A), ad.sections_by_y(B)
        k = "edited" if (x,z) in edited else "nonedited"
        for y in set(sa)|set(sb):
            for lk in ("SkyLight","BlockLight"):
                la, lb = sa.get(y,{}).get(lk), sb.get(y,{}).get(lk)
                if la == lb or (la is not None and lb is not None): continue
                side = "onlyPaper" if la is not None else "onlyFurnace"
                p = la if la is not None else lb
                vals = {ad.nib(p,i) for i in range(4096)}
                shape = "uniform%d" % next(iter(vals)) if len(vals)==1 else "mixed"
                out[k][(lk, side, shape)] += 1
for k in out:
    print(k)
    for kk, v in sorted(out[k].items()): print("  ", kk, v)
