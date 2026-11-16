# T-198 블록 엔티티 장면: 상자(내용물)·덫 상자·스포너·화로·신호기가 있는 곳을 FAWE 로 덮고·쌓고·옮기고·되돌린 뒤 저장한다.
# 같은 기준 월드(gen_base.py)에서 Paper+FAWE / Furnace+FAWE 로 돌려 anvil-diff.py 로 블록과 block_entities 를 대조한다.
# 쓰기: python -X utf8 scenario_t198.py paper|furnace [이름] [포트]
# 콘솔에는 위치가 없어 //paste 대신 같은 길(클립보드 → 붙여 넣기: 지울 타일 기록 + 섹션 바꾸기 + 동기 작업 IMMEDIATE·싣기)을 타는 //stack 을 쓴다.
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from srv import Server, ROOT, log

kind = sys.argv[1]
P = "/" if kind == "paper" else "//"   # scenario.py 와 같은 콘솔 앞붙이 규칙
name = sys.argv[2] if len(sys.argv) > 2 else kind + "-t198"
port = sys.argv[3] if len(sys.argv) > 3 else "25565"
FAWE = os.path.join(ROOT, "jars", "FastAsyncWorldEdit-Paper-2.16.2.jar")
s = Server(name, kind).fresh(world_from=os.path.join(ROOT, "base", "world"), plugins=[FAWE], extra_props=["server-port=" + port])
s.start()
OK = r"(Operation completed|blocks? (have been|has been|were|affected|changed)|affected|Undid|Undo successful|Nothing left to undo|[Nn]othing|copied|pasted|stacked|moved|Selection|First position|Second position|World override|[Ee]rror|[Ee]xception|Unknown|must|cannot|not)"
SET = r"(Changed the block|Could not set|[Ee]rror|Unknown|[Ii]ncorrect)"


def we(line, wait=OK, timeout=120, settle=1.5):
    return s.cmd(line, wait=wait, timeout=timeout, settle=settle)


def put(x, y, z, block):
    s.cmd(f"setblock {x} {y} {z} {block}", wait=SET, timeout=30, settle=0.3)


def row(x0, y, z):
    """블록 엔티티 다섯 종류를 2칸 간격으로(내용물·몹 종류가 저장 결과에 남는다)."""
    put(x0, y, z, 'minecraft:chest{Items:[{Slot:0b,id:"minecraft:diamond",count:5},{Slot:13b,id:"minecraft:oak_log",count:64}]}')
    put(x0 + 2, y, z, 'minecraft:spawner{SpawnData:{entity:{id:"minecraft:zombie"}},Delay:20s}')
    put(x0 + 4, y, z, 'minecraft:trapped_chest{Items:[{Slot:3b,id:"minecraft:apple",count:7}]}')
    put(x0 + 6, y, z, 'minecraft:furnace{Items:[{Slot:0b,id:"minecraft:iron_ore",count:3}]}')
    put(x0 + 8, y, z, "minecraft:beacon")


s.cmd("gamerule randomTickSpeed 0", settle=0.5)
s.cmd("gamerule doFireTick false", settle=0.5)
s.cmd("gamerule doMobSpawning false", settle=0.5)
we(P + "world world")

# 준비: 줄 네 개(각 장면용), 공중 y=80(지형과 겹치지 않게)
for z in (-60, -50, -40, -30):
    row(-60, 80, z)
time.sleep(2)

# B1 //set stone 으로 덮기 — 블록 엔티티 지우기(작업 스레드 removeBlockEntity + 되돌리기 기록 storeTile, 신호기는 removeBeacon)
we(P + "pos1 -61,79,-61"); we(P + "pos2 -51,81,-59"); we(P + "set stone")
# B2 //set air 로 비우기
we(P + "pos1 -61,79,-51"); we(P + "pos2 -51,81,-49"); we(P + "set air")
# B3 //undo — B2 를 되돌림(되돌리기 기록의 타일을 동기 작업이 Level#getBlockEntity + loadWithComponents 로 싣는다)
we(P + "undo")
# B4 //copy + //stack 2 east — 붙여 넣기 길(내용물 포함)
we(P + "pos1 -61,79,-41"); we(P + "pos2 -51,81,-39"); we(P + "copy"); we(P + "stack 2 east")
# B5 //move 10 up — 옮기기(옛 칸 지우기 + 새 칸 싣기)
we(P + "pos1 -61,79,-31"); we(P + "pos2 -51,81,-29"); we(P + "move 10 up")


def timed(label, line, **kw):
    t0 = time.time()
    we(line, **kw)
    log(f"TIME {label} {time.time() - t0:.1f}s")


# B6~B9 는 보기 거리 밖이라 청크를 붙잡아 둔다(setblock 이 "That position is not loaded" 로 실패하던 것)
s.cmd("forceload add -21 -21 -11 -19", settle=1)
s.cmd("forceload add -10 -21 20 -19", settle=1)
s.cmd("forceload add 20 -100 35 -85", settle=1)
# B6 다른 region 으로 붙여 넣기(검토 위험 2): region 은 16청크 변 — x<0 쪽 원본을 //stack 으로 x>=0 쪽에. 원본 줄은 x -20..-12
row(-20, 80, -20)
time.sleep(2)
we(P + "pos1 -21,79,-21"); we(P + "pos2 -11,81,-19"); we(P + "copy")
timed("B6-stack-cross-region", P + "stack 3 east")
# B7 상자 많은 곳 //set(검토 위험 1 — 지운 타일마다 되돌리기 기록에 저장): 16×2×16 = 512 상자, 내용물 있음
s.cmd('fill 20 79 -100 35 80 -85 minecraft:chest{Items:[{Slot:0b,id:"minecraft:diamond",count:5}]}', settle=2)
time.sleep(2)
we(P + "pos1 20,79,-100"); we(P + "pos2 35,80,-85")
s.cmd("mspt", settle=1)   # 대조: 편집 전 틱 시간
timed("B7-set-512-chests", P + "set stone", timeout=600, settle=3)
s.cmd("mspt", settle=1)   # 검토(PR #447): 지운 타일 꼴 뜨기가 주인 틱에서 돈다 — 상자 빽빽한 곳 //set 직후 틱 시간(+1 ms 합격선)
# B8 그 //set 을 되돌림(512 상자 내용물을 다시 싣는다)
timed("B8-undo-512-chests", P + "undo", timeout=600, settle=3)
# B9 상자 많은 곳 //copy → //stack(살아 있는 타일 저장 — 타일마다 주인 region 을 기다리는 길)
we(P + "copy")   # 선택은 B7 에서 정해 둔 그대로(다시 지정하면 "Position already set" 이 나온다)
timed("B9-stack-512-chests", P + "stack 1 up", timeout=600, settle=3)

time.sleep(5)
s.cmd("save-all flush", wait=r"(Saved the game|저장|saved)", timeout=300, settle=2)
s.stop()
log("끝:", s.dir)
