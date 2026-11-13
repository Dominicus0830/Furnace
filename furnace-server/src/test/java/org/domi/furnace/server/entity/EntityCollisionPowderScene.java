// T-018 검토 R5: entity-collision-scene 은 문맥 충돌 길(가루눈·움직이는 피스톤 — CollisionTable DYNAMIC)을 지키지 못했다.
// 이 장면은 가루눈 밭 위를 걷는 토끼(가루눈 위를 걷는 몹)·높이서 떨어져 가루눈에 닿는 몹(낙하 거리 2.5 초과 → 0.9 높이 모양)·
// 움직이는 피스톤 칸(블록 엔티티가 든 모양)에 부딪히는 몹을 만들어 20틱마다 엔티티 상태를 해시한다. 새 JVM(씨앗 켜짐) 전용.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.PistonMovingBlockEntity;
import org.domi.furnace.server.identity.SceneHash;

public final class EntityCollisionPowderScene implements SceneHash.Scene {

    @Override
    public void run(SceneHash.Recorder rec) {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        EntityCollisionScene.Arena level = new EntityCollisionScene.Arena();
        level.wallOffArena();
        java.util.Random r = new java.util.Random(0x7018_5);
        int powder = B.defaultState("minecraft:powder_snow");
        // 가루눈 밭(땅 높이 한 칸, 몇 곳은 두 칸): 걷는 토끼는 그 위에 서고 다른 몹은 빠진다
        for (int i = 0; i < 9; i++) {
            int bx = r.nextInt(50) - 22, bz = r.nextInt(50) - 22, h = 1 + r.nextInt(2);
            for (int x = bx; x < bx + 7; x++) for (int z = bz; z < bz + 7; z++) for (int y = -60; y < -60 + h; y++) level.setBlock(new Pos(x, y, z), powder, 2 | 16);
        }
        // 움직이는 피스톤 칸(틱을 돌리지 않으므로 진행도 0 의 모양이 그대로 남는다)
        int moving = B.defaultState("minecraft:moving_piston");
        String[] moved = {"minecraft:stone", "minecraft:oak_slab", "minecraft:oak_fence", "minecraft:piston_head"};
        for (int i = 0; i < 40; i++) {
            Pos p = new Pos(r.nextInt(56) - 24, -60 + r.nextInt(2), r.nextInt(56) - 24);
            level.setBlock(p, moving, 2 | 16);
            level.setBlockEntity(new PistonMovingBlockEntity(p, moving, B.defaultState(moved[r.nextInt(moved.length)]), r.nextInt(6), r.nextBoolean(), r.nextBoolean()));
        }
        String[] types = {"minecraft:rabbit", "minecraft:rabbit", "minecraft:cow", "minecraft:zombie", "minecraft:fox", "minecraft:sheep", "minecraft:chicken", "minecraft:pig"};
        for (int i = 0; i < 120; i++) {
            Entity e = Entities.create(level, types[i % types.length]);
            // 셋 중 하나는 높이서 떨어뜨린다(가루눈에 닿을 때 낙하 거리 > 2.5)
            double y = i % 3 == 0 ? -50 + r.nextInt(6) : -58;
            e.moveTo(r.nextInt(56) - 24 + 0.5, y, r.nextInt(56) - 24 + 0.5, r.nextFloat() * 360, 0);
            level.addFreshEntity(e);
        }
        for (int t = 1; t <= 300; t++) {
            level.time++;
            level.tickEntities(k -> true);
            if (t % 20 == 0) rec.line("tick " + t + " entities", EntityCollisionScene.hash(level));
        }
    }
}
