// 피스톤 장면(O08-10: "피스톤은 아직 없음"): 레드스톤 블록을 주기로 껐다 켜는 기계 여러 줄. 줄마다 모양이 다르다 —
// 보통 피스톤이 돌 셋 밀기 / 끈끈이 피스톤이 점액 사슬 당기기 / 꿀과 점액이 안 붙음 / 한도 딱 12칸(밀린다) / 13칸(밀지 못한다) / 흑요석에 막힘.
// 피스톤 이동 구조 해석·이동 중 블록 엔티티·블록 이벤트 순서가 바뀌지 않았는지 칸 단위로 본다. 엔티티 없음 → 같은 JVM 모드.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;

public final class PistonScene extends TickScene {
    private static final int PER_LAYER = 20, X0 = -28;

    private SceneLevel level;
    private int machines, layers;
    private int rb, air;
    private long movingSeen;   // 이동 중(moving_piston) 칸을 합쳐 본 횟수(틱마다 셈) — 점검 시점에 안 보이는 이동을 사람이 읽을 수 있게
    private IdentityHash trace;   // 틱마다 모든 기계 칸을 섞은 누적 해시 — 점검 사이의 이동 중(moving_piston) 상태도 해시에 든다

    @Override public String name() { return "piston"; }
    @Override public boolean freshJvm() { return false; }
    @Override public String covers() { return "O08-10 피스톤, 블록 이벤트·이동 중 블록 엔티티 순서"; }

    private int piston(String name, String facing) { return B.prop("facing").with(Blocks.get().defaultState(name), facing); }

    private static int y(int row) { return SceneLevel.FLOOR_Y + 1 + 3 * (row / PER_LAYER); }

    private static int z(int row) { return -30 + 3 * (row % PER_LAYER); }

    @Override
    protected void setup(Size size, int round) {
        SceneKit.install();
        machines = size.pick(40, 200);
        layers = (machines + PER_LAYER - 1) / PER_LAYER;
        level = new SceneLevel("piston", 2);
        trace = new IdentityHash();
        movingSeen = 0;
        Blocks b = Blocks.get();
        rb = b.defaultState("minecraft:redstone_block");
        air = b.defaultState("minecraft:air");
        int stone = b.defaultState("minecraft:stone"), slime = b.defaultState("minecraft:slime_block"), honey = b.defaultState("minecraft:honey_block"),
                glass = b.defaultState("minecraft:glass"), obsidian = b.defaultState("minecraft:obsidian");
        for (int row = 0; row < machines; row++) {
            int y = y(row), z = z(row);
            switch (row % 6) {
                case 0 -> {   // 보통 피스톤이 돌 셋을 민다
                    level.setBlock(new Pos(X0, y, z), piston("minecraft:piston", "east"), 3);
                    for (int i = 1; i <= 3; i++) level.setBlock(new Pos(X0 + i, y, z), stone, 3);
                }
                case 1 -> {   // 끈끈이 피스톤 + 점액이 바로 앞: 점액 사슬이 매 주기 밀리고 끌려 온다
                    level.setBlock(new Pos(X0, y, z), piston("minecraft:sticky_piston", "east"), 3);
                    level.setBlock(new Pos(X0 + 1, y, z), slime, 3);
                    level.setBlock(new Pos(X0 + 2, y, z), stone, 3);
                    level.setBlock(new Pos(X0 + 1, y, z + 1), glass, 3);   // 점액 옆 이웃: 점액은 (꿀이 아닌) 옆 블록을 달고 간다 — 유리가 사슬에 같이 끌려 나온다
                }
                case 2 -> {   // 꿀 블록은 점액과 안 붙는다: 꿀 앞 + 꿀 옆의 점액·돌은 따라오지 않는다
                    level.setBlock(new Pos(X0, y, z), piston("minecraft:sticky_piston", "east"), 3);
                    level.setBlock(new Pos(X0 + 1, y, z), honey, 3);
                    level.setBlock(new Pos(X0 + 2, y, z), stone, 3);
                    level.setBlock(new Pos(X0 + 1, y, z + 1), slime, 3);
                    level.setBlock(new Pos(X0 + 1, y, z + 2), stone, 3);
                }
                case 3, 4 -> {   // 밀 수 있는 한도: 12칸이면 밀리고(case 3) 13칸이면 못 민다(case 4)
                    level.setBlock(new Pos(X0, y, z), piston("minecraft:piston", "east"), 3);
                    for (int i = 1; i <= (row % 6 == 3 ? 12 : 13); i++) level.setBlock(new Pos(X0 + i, y, z), stone, 3);
                }
                default -> {   // 흑요석에 막힘
                    level.setBlock(new Pos(X0, y, z), piston("minecraft:piston", "east"), 3);
                    level.setBlock(new Pos(X0 + 1, y, z), stone, 3);
                    level.setBlock(new Pos(X0 + 2, y, z), obsidian, 3);
                }
            }
        }
    }

    @Override protected int ticks(Size size) { return size.pick(240, 600); }

    @Override protected int checkEvery(Size size) { return 40; }

    private static int period(int row) { return 8 + 4 * (row % 3); }   // 8·12·16틱마다 켜고 끈다

    @Override
    protected void tick(int t) {
        for (int row = 0; row < machines; row++) {
            int p = period(row);
            if (t % p != 0) continue;
            boolean on = (t / p) % 2 == 0;
            level.setBlock(new Pos(X0 - 1, y(row), z(row)), on ? rb : air, 3);
        }
        level.tick();
    }

    @Override
    protected void afterTick(int t) {
        SceneKit.blocks(level, trace, X0 - 2, SceneLevel.FLOOR_Y, -32, X0 + 18, y(machines - 1) + 1, z(PER_LAYER - 1) + 1);
        movingSeen += SceneKit.count(level, "minecraft:moving_piston", X0 - 2, SceneLevel.FLOOR_Y, -32, X0 + 18, y(machines - 1) + 1, z(PER_LAYER - 1) + 1);
    }

    @Override
    protected void checkpoint(SceneHash.Recorder rec, String label) {
        IdentityHash h = new IdentityHash();
        SceneKit.blocks(level, h, X0 - 2, SceneLevel.FLOOR_Y, -32, X0 + 18, y(machines - 1) + 1, z(PER_LAYER - 1) + 1);
        rec.line("piston " + label, h);
        if (trace.count() > 0) rec.line("piston " + label + " trace", trace);   // 틱 0 에는 아직 섞인 값이 없다
        int x1 = X0 + 18, y1 = y(machines - 1) + 1, z1 = z(PER_LAYER - 1) + 1;
        rec.text("piston " + label + " heads=" + SceneKit.count(level, "minecraft:piston_head", X0 - 2, SceneLevel.FLOOR_Y, -32, x1, y1, z1)
                + " moving=" + SceneKit.count(level, "minecraft:moving_piston", X0 - 2, SceneLevel.FLOOR_Y, -32, x1, y1, z1)
                + " movingSeen=" + movingSeen);
    }
}
