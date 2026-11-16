# 같은 기준 월드에서 Paper+FAWE / Furnace+FAWE 로 같은 편집을 돌리고 저장한다.
# 쓰기: python -X utf8 scenario.py paper|furnace [이름]
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from srv import Server, ROOT, log

kind = sys.argv[1]
# 콘솔의 WorldEdit 명령 앞붙이: Paper 콘솔은 앞 / 를 떼지 않고, Furnace 콘솔은 바닐라처럼 하나 뗀다
P = "/" if kind == "paper" else "//"
name = sys.argv[2] if len(sys.argv) > 2 else kind + "-fawe"
port = sys.argv[3] if len(sys.argv) > 3 else "25565"
FAWE = os.path.join(ROOT, "jars", "FastAsyncWorldEdit-Paper-2.16.2.jar")
s = Server(name, kind).fresh(world_from=os.path.join(ROOT, "base", "world"), plugins=[FAWE], extra_props=["server-port=" + port])
s.start()
OK = r"(Operation completed|blocks? (have been|has been|were|affected|changed)|affected|Undid|Undo successful|Nothing left to undo|[Nn]othing|copied|pasted|stacked|moved|Selection|First position|Second position|World override|[Ee]rror|[Ee]xception|Unknown|must|cannot|not)"

def we(line, wait=OK, timeout=120, settle=1.5):
    m = s.cmd(line, wait=wait, timeout=timeout, settle=settle)
    return m

s.cmd("gamerule randomTickSpeed 0", settle=0.5)
s.cmd("gamerule doFireTick false", settle=0.5)
we(P + "world world")
# E1 작은 //set (섹션 경계 y=64 를 지남)
we(P + "pos1 -40,60,-40"); we(P + "pos2 -31,69,-31"); we(P + "set stone")
# E2 //replace 로 지표를 걷어냄 → 하이트맵이 내려감
we(P + "pos1 0,40,0"); we(P + "pos2 47,120,47"); we(P + "replace grass_block,dirt,sand,short_grass,tall_grass,podzol,coarse_dirt air")
# E3 64³ //set (지표를 뚫고 올라감 → 하이트맵이 올라감)
we(P + "pos1 -100,40,40"); we(P + "pos2 -37,103,103"); we(P + "set oak_planks", timeout=300, settle=5)
# E4 //copy + //stack + //move
we(P + "pos1 -40,60,-40"); we(P + "pos2 -31,69,-31"); we(P + "copy"); we(P + "stack 2 north"); we(P + "move 12 up")
# E5 공중에 물(물리 없이)
we(P + "pos1 60,90,-60"); we(P + "pos2 69,95,-51"); we(P + "set water")
# E6 //undo (E5 를 되돌림)
we(P + "undo")
# E7 다시 물(작게)
we(P + "pos1 70,90,-40"); we(P + "pos2 75,92,-35"); we(P + "set water")
# 대조군: 바닐라 /fill (FAWE 와 상관없는 Furnace 엔진 차이를 가려내기 위해)
s.cmd("fill 90 60 90 99 70 99 minecraft:stone", settle=1)
time.sleep(5)
s.cmd("save-all flush", wait=r"(Saved the game|저장|saved)", timeout=300, settle=2)
s.stop()
log("끝:", s.dir)
