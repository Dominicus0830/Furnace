# 장면 명령 만들기. 사용: python3 gen_cmds.py farm <마리> | redstone
import sys, random
random.seed(1)
kind = sys.argv[1]
out = []
Y = -60  # 평지 땅 위(바닥 -61 이 잔디)
if kind == "farm":
    n = int(sys.argv[2]); W = 64
    x0, z0 = 8, 8
    out.append(f"fill {x0-1} {Y} {z0-1} {x0+W} {Y+2} {z0-1} glass")
    out.append(f"fill {x0-1} {Y} {z0+W} {x0+W} {Y+2} {z0+W} glass")
    out.append(f"fill {x0-1} {Y} {z0-1} {x0-1} {Y+2} {z0+W} glass")
    out.append(f"fill {x0+W} {Y} {z0-1} {x0+W} {Y+2} {z0+W} glass")
    for i in range(n):
        out.append(f"summon cow {x0 + 0.5 + random.randrange(W)} {Y} {z0 + 0.5 + random.randrange(W)}")
elif kind == "redstone":
    # 호퍼 고리 256개(4칸씩, 1024개, 각 고리에 조약돌 64개) — x 8..71, z 8..71 바둑판(2칸 간격)
    k = 0
    for gx in range(16):
        for gz in range(16):
            x, z = 8 + gx * 4, 8 + gz * 4
            it = '{Items:[{Slot:0b,id:"minecraft:cobblestone",count:64}]}'
            out.append(f"setblock {x} {Y} {z} hopper[facing=east]{it}")
            out.append(f"setblock {x+1} {Y} {z} hopper[facing=south]")
            out.append(f"setblock {x+1} {Y} {z+1} hopper[facing=west]")
            out.append(f"setblock {x} {Y} {z+1} hopper[facing=north]")
    # 관측기 시계 64개 + 가루 14칸 + 끝 램프 — x -80..-60, z 8..135(2칸 간격)
    for j in range(64):
        z = 8 + j * 2; x = -80
        out.append(f"fill {x+2} {Y-1} {z} {x+16} {Y-1} {z} stone")
        out.append(f"fill {x+2} {Y} {z} {x+15} {Y} {z} redstone_wire")
        out.append(f"setblock {x+16} {Y} {z} redstone_lamp")
        out.append(f"setblock {x} {Y} {z} observer[facing=east]")
        out.append(f"setblock {x+1} {Y} {z} observer[facing=west]")
print("\n".join(out))
