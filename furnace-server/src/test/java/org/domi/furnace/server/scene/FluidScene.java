// 유체 장면(O07 유체 지문 `O07FluidDiffProbe` 를 저장소로): 모양 블록이 섞인 돌 바닥 위에 물·용암 원천을 주기로 놓았다 치우며
// 예약 틱(유체 퍼짐)을 돌린다. 유체 퍼짐 칸 기억(O07-03)·유체 틱 최적화가 칸 결과를 바꾸지 않았는지 본다.
// 블록만 다루므로 같은 JVM 모드. 난수 소비(레벨 난수)도 해시에 넣어 "난수를 몇 번 썼는지"도 같아야 한다.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;

public final class FluidScene extends TickScene {
    private static final String[] TERRAIN = {"minecraft:stone", "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:oak_slab", "minecraft:oak_fence",
            "minecraft:oak_sign", "minecraft:ladder", "minecraft:glass", "minecraft:oak_stairs", "minecraft:sugar_cane", "minecraft:oak_trapdoor",
            "minecraft:iron_bars", "minecraft:oak_leaves", "minecraft:snow", "minecraft:dirt_path"};
    private static final int FLOOR = -60;

    private SceneLevel level;
    private Pos[] sources;
    private int seed, water, lava, air;

    @Override public String name() { return "fluid"; }
    @Override public boolean freshJvm() { return false; }
    @Override public String covers() { return "O07 유체(퍼짐 칸 기억·틱 최적화), O07-03"; }

    @Override
    protected void setup(Size size, int round) {
        seed = round;
        level = new SceneLevel("fluid-" + seed, 2);
        ((LegacyRandomSource) level.random()).setSeed(seed);
        Blocks b = Blocks.get();
        java.util.Random r = new java.util.Random(seed);
        for (int x = -24; x < 24; x++) for (int z = -24; z < 24; z++) for (int y = FLOOR; y < FLOOR + 6; y++) {
            if (y == FLOOR && r.nextInt(12) == 0) { level.setBlock(new Pos(x, y, z), b.defaultState("minecraft:air"), 2); continue; }   // 바닥 구멍
            if (y > FLOOR && r.nextInt(3) == 0) level.setBlock(new Pos(x, y, z), b.defaultState(TERRAIN[r.nextInt(TERRAIN.length)]), 2);
        }
        water = b.defaultState("minecraft:water");
        lava = b.defaultState("minecraft:lava");
        air = b.defaultState("minecraft:air");
        sources = new Pos[30];
        for (int i = 0; i < sources.length; i++) sources[i] = new Pos(r.nextInt(44) - 22, FLOOR + 6 + r.nextInt(3), r.nextInt(44) - 22);
    }

    @Override protected int rounds(Size size) { return size.pick(3, 12); }

    @Override protected int ticks(Size size) { return size.pick(300, 600); }

    @Override protected int checkEvery(Size size) { return 150; }

    @Override
    protected void tick(int t) {
        if (t % 150 == 0 || t % 150 == 100) {   // 원천을 150틱마다 놓고 100틱 뒤 치운다(마른 뒤 다시 흐르는 경로)
            boolean on = t % 150 == 0;
            for (int i = 0; i < sources.length; i++) level.setBlock(sources[i], on ? (i % 4 == 0 ? lava : water) : air, 3);
        }
        level.runScheduledTicks();
    }

    @Override
    protected void checkpoint(SceneHash.Recorder rec, String label) {
        IdentityHash h = new IdentityHash();
        SceneKit.blocks(level, h, -26, FLOOR - 1, -26, 25, FLOOR + 10, 25);
        h.add(level.random().nextInt(1 << 30));
        rec.line("fluid " + label, h);
        rec.text("fluid " + label + " water=" + SceneKit.count(level, "minecraft:water", -26, FLOOR - 1, -26, 25, FLOOR + 10, 25)
                + " lava=" + SceneKit.count(level, "minecraft:lava", -26, FLOOR - 1, -26, 25, FLOOR + 10, 25));
    }
}
