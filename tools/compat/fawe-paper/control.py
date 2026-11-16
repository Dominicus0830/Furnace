# 대조군: 플러그인 없이 같은 기준 월드에서 바닐라 명령만 돌리고 저장한다(FAWE 와 상관없는 Furnace 엔진 차이를 가려낸다).
# 쓰기: python -X utf8 control.py paper|furnace [포트]
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from srv import Server, ROOT, log

kind = sys.argv[1]
port = sys.argv[2] if len(sys.argv) > 2 else "25565"
s = Server(kind + "-control", kind).fresh(world_from=os.path.join(ROOT, "base", "world"), extra_props=["server-port=" + port])
s.start()
s.cmd("gamerule randomTickSpeed 0", settle=0.5)
s.cmd("gamerule doFireTick false", settle=0.5)
for a in ("90 90 99 99", "-40 -40 -31 -31", "60 -60 69 -51"):
    s.cmd("forceload add " + a, settle=1)
time.sleep(3)
s.cmd("fill 90 60 90 99 70 99 minecraft:stone", settle=1)
s.cmd("fill -40 60 -40 -31 69 -31 minecraft:stone", settle=1)
s.cmd("fill 60 90 -60 69 95 -51 minecraft:water", settle=1)
time.sleep(5)
s.cmd("save-all flush", wait=r"(Saved the game|저장|saved)", timeout=300, settle=2)
s.stop()
log("끝:", s.dir)
