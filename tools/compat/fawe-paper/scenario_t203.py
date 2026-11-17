# T-203 4단계: FAWE //regen 을 Paper 와 대조한다(설계 compat-nms-t203-regen-design-2026-11-17.md).
# 같은 기준 월드(gen_base.py)를 고친 뒤 //regen 네 가지를 돌려 anvil-diff.py 로 블록·블록 엔티티·바이옴을 대조하고,
# 콘솔의 TIME 줄로 걸린 시간을, MARK 사이 예외를 비교한다.
# 쓰기: python -X utf8 scenario_t203.py paper|furnace [이름] [포트]
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from srv import Server, ROOT, log

kind = sys.argv[1]
P = "/" if kind == "paper" else "//"   # scenario.py 와 같은 콘솔 앞붙이 규칙
name = sys.argv[2] if len(sys.argv) > 2 else kind + "-t203"
port = sys.argv[3] if len(sys.argv) > 3 else "25565"
FAWE = os.path.join(ROOT, "jars", "FastAsyncWorldEdit-Paper-2.16.2.jar")
s = Server(name, kind).fresh(world_from=os.path.join(ROOT, "base", "world"), plugins=[FAWE], extra_props=["server-port=" + port])
s.start()
OK = r"(Operation completed|blocks? (have been|has been|were|affected|changed)|affected|[Rr]egenerat|set to|Selection|First position|Second position|World override|[Ee]rror|[Ee]xception|Unknown|must|cannot|not)"


def we(line, wait=OK, timeout=120, settle=1.5):
    return s.cmd(line, wait=wait, timeout=timeout, settle=settle)


def sel(a, b):
    we(P + "pos1 " + a); we(P + "pos2 " + b)


def mark(label):
    s.cmd("say MARK " + label, wait=r"MARK " + label, timeout=30, settle=0.3)


def regen(label, a, b, args=""):
    """선택 뒤 //regen. 큰 선택이면 FAWE 가 확인을 묻는다 — 그때만 //confirm."""
    mark(label)
    sel(a, b)
    t0 = time.time()
    done = r"Region regenerated|Unknown command|confirm"   # 오류가 나도 FAWE 는 끝에 이 줄을 낸다
    m = we((P + "regen " + args).strip(), wait=done, timeout=600, settle=0.5)
    if "confirm" in m.group(0):
        we(P + "confirm", wait=r"Region regenerated", timeout=600, settle=0.5)
    log(f"TIME {label} {time.time() - t0:.1f}s")
    time.sleep(3)


s.cmd("gamerule randomTickSpeed 0", settle=0.5)
s.cmd("gamerule doFireTick false", settle=0.5)
s.cmd("gamerule doMobSpawning false", settle=0.5)
s.cmd("forceload add -96 -64 111 111", settle=5)
we(P + "world world")

# 고쳐 둔 곳(되돌아오는지 보려고): 돌 상자 + 상자(내용물)
we(P + "pos1 -20,50,-20"); we(P + "pos2 20,110,20"); we(P + "set stone", timeout=300)
s.cmd("setblock 0 111 0 chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:3}]}", settle=0.5)

# R1 같은 시드 — 청크 경계를 넘는 선택, 세로 전체
regen("R1", "-20,-64,-20", "20,319,20")
# R2 다른 시드
regen("R2", "40,-64,-60", "70,319,-30", "12345")
# R3 다른 시드 + 바이옴도(-b)
regen("R3", "-80,-64,40", "-50,319,70", "-b 777")
# R4 다른 시드 + 바이옴 하나(//regen <시드> <바이옴>) — 3단계 바이옴 공급자 길
regen("R4", "60,-64,60", "100,319,100", "999 desert")
# R5 같은 시드 + 바이옴 하나 + -b
regen("R5", "-80,-64,-40", "-50,319,-10", "-b 20261115 snowy_plains")

mark("END")
s.cmd("save-all flush", wait=r"(Saved the game|저장|saved)", timeout=300, settle=2)
s.stop()
log("끝:", s.dir)
