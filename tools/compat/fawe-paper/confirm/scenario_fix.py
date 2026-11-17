# T-204 확정 실험: F1~F4 + //fixblocks 한 번만 돌리고 저장한다(FAWE queue.parallel-threads 를 인자로 고정 가능).
# 쓰기: python -X utf8 scenario_fix.py paper|furnace 이름 포트 [parallel-threads]
import os, sys, time
sys.path.insert(0, r"E:\t198-work")
from srv import Server, ROOT, log

kind, name, port = sys.argv[1], sys.argv[2], sys.argv[3]
pt = sys.argv[4] if len(sys.argv) > 4 else None
P = "/" if kind == "paper" else "//"
FAWE = os.path.join(ROOT, "jars", "FastAsyncWorldEdit-Paper-2.16.2.jar")
s = Server(name, kind).fresh(world_from=os.path.join(ROOT, "base", "world"), plugins=[FAWE], extra_props=["server-port=" + port])
if pt:
    d = os.path.join(s.dir, "plugins", "FastAsyncWorldEdit"); os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "config.yml"), "w", encoding="utf-8") as f: f.write("queue:\n  parallel-threads: %s\n" % pt)
s.start()
OK = r"(Operation completed|blocks? (have been|has been|were|affected|changed)|affected|Side effect|set to|already set|Selection|First position|Second position|World override|[Ee]rror|[Ee]xception|Unknown|must|cannot|not)"

def we(line, wait=OK, timeout=120, settle=1.5): return s.cmd(line, wait=wait, timeout=timeout, settle=settle)
def box(a, b, pattern):
    we(P + "pos1 " + a); we(P + "pos2 " + b); we(P + "set " + pattern)

s.cmd("gamerule randomTickSpeed 0", settle=0.5); s.cmd("gamerule doFireTick false", settle=0.5); s.cmd("gamerule doMobSpawning false", settle=0.5)
s.cmd("forceload add -64 -4 0 28", settle=2)
we(P + "world world")
box("-60,100,0", "-55,101,5", "oak_fence"); box("-58,100,2", "-57,101,3", "stone")
box("-50,100,0", "-45,100,5", "oak_stairs"); box("-48,100,2", "-47,100,3", "air")
box("-40,100,0", "-35,100,5", "stone"); box("-40,101,0", "-35,101,5", "redstone_wire")
box("-38,101,2", "-37,101,3", "redstone_block")
box("-30,100,0", "-25,101,5", "glass_pane"); box("-28,100,2", "-27,101,3", "cobblestone_wall")
we(P + "pos1 -61,99,-1"); we(P + "pos2 -24,102,6")
t0 = time.time(); we(P + "fixblocks", timeout=300, settle=3); log("TIME fixblocks %.1fs" % (time.time() - t0))
s.cmd("save-all flush", wait=r"(Saved the game|저장|saved)", timeout=300, settle=2)
s.stop()
log("끝:", s.dir)
