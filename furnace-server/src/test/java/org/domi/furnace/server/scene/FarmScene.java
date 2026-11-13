// 농장 장면(O06-15 `farm`): 울타리 우리(문이 20틱마다 열렸다 닫힌다) 안에 밀밭과 동물, 바깥에 플레이어를 쫓는 좀비. 무작위 틱(밀 자람·농지)도 돈다.
// 블록 모양이 자주 바뀌는 곳에서 길찾는 몹 — O06-06(블록 모양이 바뀌면 길 다시 찾기, F5)·05·10, O07 무작위 틱의 결과 동일 증명 기준.
// 엔티티가 있으므로 새 JVM 모드.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;

public final class FarmScene extends TickScene {
    private static final int FLOOR = SceneLevel.FLOOR_Y;

    private SceneLevel level;
    private int cells;
    private int fenceGateOpen, fenceGateClosed;

    @Override public String name() { return "farm"; }
    @Override public boolean freshJvm() { return true; }
    @Override public String covers() { return "O06-15 농장, O06-06(블록 모양 변화 시 길 다시 찾기)·05·10, 무작위 틱"; }

    private static int penX(int cells, int i) { return (i - cells / 2) * 12 - 4; }

    @Override
    protected void setup(Size size, int round) {
        SceneKit.install();
        level = new SceneLevel("farm", 3);
        level.dayTime = 18000;
        level.gameTime = 18000;
        SceneKit.addPlayer(level, "A", 0.5, FLOOR, 0.5);
        Blocks b = Blocks.get();
        cells = size.pick(2, 5);
        int fence = b.defaultState("minecraft:oak_fence"), gate = b.defaultState("minecraft:oak_fence_gate");
        fenceGateClosed = gate;
        fenceGateOpen = B.prop("open").with(gate, "true");
        int farmland = b.defaultState("minecraft:farmland"), wheat = b.defaultState("minecraft:wheat"), water = b.defaultState("minecraft:water");
        java.util.Random r = new java.util.Random(9);
        for (int i = 0; i < cells; i++) for (int j = 0; j < cells; j++) {
            int x0 = penX(cells, i), z0 = penX(cells, j);
            for (int a = 0; a < 8; a++) {
                level.setBlock(new Pos(x0 + a, FLOOR, z0), fence, 3);
                level.setBlock(new Pos(x0 + a, FLOOR, z0 + 7), fence, 3);
                level.setBlock(new Pos(x0, FLOOR, z0 + a), fence, 3);
                level.setBlock(new Pos(x0 + 7, FLOOR, z0 + a), fence, 3);
            }
            level.setBlock(new Pos(x0 + 3, FLOOR, z0), gate, 3);   // 문(남쪽 가운데)
            for (int a = 2; a <= 5; a++) for (int c = 2; c <= 5; c++) {
                boolean wet = a == 3 && c == 3;
                level.setBlock(new Pos(x0 + a, FLOOR - 1, z0 + c), wet ? water : farmland, 3);
                if (!wet) level.setBlock(new Pos(x0 + a, FLOOR, z0 + c), wheat, 3);
            }
            for (int k = 0; k < 3; k++) {
                var e = Entities.create(level, k == 0 ? "minecraft:sheep" : "minecraft:cow");
                e.moveTo(x0 + 1.5 + k, FLOOR, z0 + 1.5, r.nextFloat() * 360, 0);
                level.addFreshEntity(e);
            }
        }
        int zombies = size.pick(10, 40);
        for (int i = 0; i < zombies; i++) {
            var z = Entities.create(level, "minecraft:zombie");
            double ang = r.nextDouble() * Math.PI * 2, dist = 20 + r.nextInt(25);
            z.moveTo(Math.cos(ang) * dist + 0.5, FLOOR, Math.sin(ang) * dist + 0.5, r.nextFloat() * 360, 0);
            level.addFreshEntity(z);
        }
    }

    @Override protected int ticks(Size size) { return size.pick(300, 600); }

    @Override protected int checkEvery(Size size) { return 100; }

    @Override
    protected void tick(int t) {
        if (t % 20 == 0) {   // 문을 열고 닫는다 — 우리마다 위상을 달리해 늘 몇 개는 열려 있다
            for (int i = 0; i < cells; i++) for (int j = 0; j < cells; j++) {
                boolean open = ((t / 20) + i + j) % 2 == 0;
                level.setBlock(new Pos(penX(cells, i) + 3, FLOOR, penX(cells, j)), open ? fenceGateOpen : fenceGateClosed, 3);
            }
        }
        for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) level.tickChunk(cx, cz, 3);   // 무작위 틱(바닐라 randomTickSpeed 기본 3)
        level.tick();
    }

    @Override
    protected void checkpoint(SceneHash.Recorder rec, String label) {
        IdentityHash h = new IdentityHash();
        SceneKit.blocks(level, h, -48, FLOOR - 2, -48, 47, FLOOR + 3, 47);
        rec.line("farm " + label + " blocks", h);
        rec.line("farm " + label + " entities", SceneKit.entities(level));
        rec.line("farm " + label + " random", new IdentityHash().add(level.random().nextLong()));
        rec.text("farm " + label + " count " + level.entities().tickSnapshot().size() + " wheat=" + SceneKit.count(level, "minecraft:wheat", -48, FLOOR, -48, 47, FLOOR, 47));
    }
}
