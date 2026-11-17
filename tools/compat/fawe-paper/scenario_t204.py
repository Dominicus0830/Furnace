# T-204 4단계(V5): FAWE 놓기 상태(//fixblocks — PlacementStateProcessor)와 부작용(//perf)을 Paper 와 대조한다.
# 같은 기준 월드(gen_base.py)에서 Paper+FAWE / Furnace+FAWE 로 돌려 anvil-diff.py 로 블록을 대조하고, 콘솔의 MARK 사이 예외를 비교한다.
# 쓰기: python -X utf8 scenario_t204.py paper|furnace [이름] [포트]
# FAWE //set 은 부작용 없이 상태를 그대로 쓰므로(울타리가 안 이어진 채) //fixblocks 가 바닐라 놓기 계산으로 고친다.
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from srv import Server, ROOT, log

kind = sys.argv[1]
P = "/" if kind == "paper" else "//"   # scenario.py 와 같은 콘솔 앞붙이 규칙
name = sys.argv[2] if len(sys.argv) > 2 else kind + "-t204"
port = sys.argv[3] if len(sys.argv) > 3 else "25565"
FAWE = os.path.join(ROOT, "jars", "FastAsyncWorldEdit-Paper-2.16.2.jar")
s = Server(name, kind).fresh(world_from=os.path.join(ROOT, "base", "world"), plugins=[FAWE], extra_props=["server-port=" + port])
s.start()
OK = r"(Operation completed|blocks? (have been|has been|were|affected|changed)|affected|Side effect|set to|already set|Selection|First position|Second position|World override|[Ee]rror|[Ee]xception|Unknown|must|cannot|not)"


def we(line, wait=OK, timeout=120, settle=1.5):
    return s.cmd(line, wait=wait, timeout=timeout, settle=settle)


def box(a, b, pattern):
    we(P + "pos1 " + a); we(P + "pos2 " + b); we(P + "set " + pattern)


def mark(label):
    """콘솔 구간 표시 — 결과 문서에서 MARK 사이의 예외(NullPointerException·UnsupportedNmsCall)를 센다."""
    s.cmd("say MARK " + label, wait=r"MARK " + label, timeout=30, settle=0.3)


def timed(label, line, **kw):
    t0 = time.time()
    we(line, **kw)
    log(f"TIME {label} {time.time() - t0:.1f}s")


s.cmd("gamerule randomTickSpeed 0", settle=0.5)
s.cmd("gamerule doFireTick false", settle=0.5)
s.cmd("gamerule doMobSpawning false", settle=0.5)
s.cmd("forceload add -64 -4 0 28", settle=2)
we(P + "world world")

# ---- F1~F4: 잇는 블록을 날것으로 쓴 뒤 //fixblocks(공중 y=100, 지형과 겹치지 않게)
box("-60,100,0", "-55,101,5", "oak_fence"); box("-58,100,2", "-57,101,3", "stone")             # F1 울타리 + 가운데 돌
box("-50,100,0", "-45,100,5", "oak_stairs"); box("-48,100,2", "-47,100,3", "air")              # F2 계단(모서리 꼴)
box("-40,100,0", "-35,100,5", "stone"); box("-40,101,0", "-35,101,5", "redstone_wire")          # F3 레드스톤 가루
box("-38,101,2", "-37,101,3", "redstone_block")
box("-30,100,0", "-25,101,5", "glass_pane"); box("-28,100,2", "-27,101,3", "cobblestone_wall")  # F4 판유리 + 담
mark("F1-4")
we(P + "pos1 -61,99,-1"); we(P + "pos2 -24,102,6")
timed("F1-4-fixblocks", P + "fixblocks", timeout=300, settle=3)

# ---- F5~F7: 바닐라 놓기 계산이 읽기 밖으로 나가는 블록(검토 #474) — Paper 의 FAWE 프록시는 null 필드 NPE, Furnace 는 UnsupportedNmsCall
box("-60,110,20", "-58,110,22", "netherrack"); box("-60,105,20", "-58,109,22", "weeping_vines")  # F5 덩굴(나이 난수)
mark("F5")
we(P + "pos1 -60,105,20"); we(P + "pos2 -58,110,22")
timed("F5-fixblocks-vines", P + "fixblocks", settle=3)

box("-50,99,20", "-46,99,24", "sand"); box("-50,100,20", "-46,105,24", "water")                  # F6 물속 켈프(나이 난수)
box("-48,100,22", "-48,104,22", "kelp")
mark("F6")
we(P + "pos1 -50,99,20"); we(P + "pos2 -46,105,24")
timed("F6-fixblocks-kelp", P + "fixblocks", settle=3)

box("-40,100,20", "-38,100,22", "tube_coral_block")                                                # F7 물 없는 산호 블록(예약 틱)
mark("F7")
we(P + "pos1 -40,100,20"); we(P + "pos2 -38,100,22")
timed("F7-fixblocks-coral", P + "fixblocks", settle=3)
time.sleep(8)   # 산호 예약 틱(60~100틱)이 걸렸다면 죽은 산호로 바뀐다

# ---- N1: 이웃 갱신 켜고 //set(WNA 부작용 — 옆 돌에 붙는 울타리의 이웃 꼴 갱신)
mark("N1")
s.cmd("setblock -21 100 2 stone", settle=0.5)
we(P + "perf neighbors on")
box("-20,100,0", "-16,100,4", "oak_fence")
we(P + "perf neighbors off")

# ---- N2: 갱신 켜고 공중 물 //set → 흐른 뒤 물 칸(예약 유체 틱)
mark("N2")
we(P + "perf update on"); we(P + "perf neighbors on")
box("-10,100,0", "-8,100,2", "water")
we(P + "perf update off"); we(P + "perf neighbors off")
time.sleep(40)   # 떨어지고 퍼지는 데 충분한 시간(약 800 틱) — 끝난 모양만 비교한다

mark("END")
s.cmd("save-all flush", wait=r"(Saved the game|저장|saved)", timeout=300, settle=2)
s.stop()
log("끝:", s.dir)
