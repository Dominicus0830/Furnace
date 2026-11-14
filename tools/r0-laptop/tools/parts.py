# agg 파일의 "모듈별" 절(CPU·할당)을 O 파트로 묶는다. 사용: python3 parts.py 이름=agg파일 ...
import sys, re, collections
M = [("world.gen","O04"),("world(root):chunk","O02·O03 청크 자료"),("world(root):sched-ish","O01"),("world(root):other","world 루트 기타"),
     ("world.anvil","O02"),("world.storage","O02"),("datafix","O02"),("players","O02"),("world.poi","O06"),("world.raid","O06"),("world.gameevent","O06"),
     ("world.block","O07"),("light","O07"),("block.entity","O09"),("block.dispenser","O09"),("block.types","O07·O08"),("block(root)","O07·O08"),
     ("net.","O03"),("entity.ai","O06"),("entity.spawn","O06"),("entity.gossip","O06"),("entity.","O05"),("entity(root)","O05"),("effect","O05"),("attribute","O05"),
     ("menu","O09"),("item","O09"),("trade","O09"),("recipe","O10"),("loot","O10"),("advancement","O10"),("stats","O10"),("scores","O10"),("map","O10"),("bossbar","O10"),
     ("command","O11"),("function","O11"),("data(root)","O11"),("chat","O11"),("plugin","O12"),("api","O12"),("compat","O13"),
     ("sched","O01"),("server(root)","O01"),("metrics","O14"),("profiling","O14"),("log","O14"),("config","O14"),("remote","O14"),("maintenance","O14"),("외부","외부(JDK·Netty)")]
def part(b):
    for p, o in M:
        if b.startswith(p): return o
    return "기타:" + b
def section(txt, title):
    m = re.search(re.escape(title) + r".*?\n((?:\s+[\d.]+%.*\n)+)", txt)
    out = collections.Counter()
    if not m: return out
    for l in m.group(1).splitlines():
        mm = re.match(r"\s+([\d.]+)%\s+(\S+)", l)
        if mm: out[part(mm.group(2))] += float(mm.group(1))
    return out
cols = []
for a in sys.argv[1:]:
    name, f = a.split("=", 1); t = open(f).read()
    cols.append((name, section(t, "CPU 표본 — 모듈별"), section(t, "할당(바이트 가중) — 모듈별")))
keys = sorted({k for _, c, a in cols for k in list(c) + list(a)}, key=lambda k: -sum(c.get(k, 0) for _, c, _ in cols))
print("| 파트 | " + " | ".join(f"{n} CPU / 할당" for n, _, _ in cols) + " |")
print("|---|" + "---|" * len(cols))
for k in keys:
    if max(max(c.get(k, 0), a.get(k, 0)) for _, c, a in cols) < 0.5: continue
    print(f"| {k} | " + " | ".join(f"{c.get(k,0):.1f} / {a.get(k,0):.1f}" for _, c, a in cols) + " |")
