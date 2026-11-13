// T-018(P-O05-07) 장면: 장애물(계단·반 블록·울타리·담·가루눈·영혼 모래·꿀·비계·눈 층·덫문·동굴 공기 구덩이)과 배·셜커·월드 보더가 있는 평지에서
// 몹 150마리를 300틱 돌리며 20틱마다 엔티티 상태(위치·속도·땅·충돌 플래그·디딘 칸·체력)를 해시한다. 충돌 수집을 바꿔도 이 해시는 같아야 한다.
// 엔티티 id·난수가 JVM 전역이라 새 JVM(-Dfurnace.test.seeded=true)에서만 결정적이다 — EntityCollisionSceneGoldenTest.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;

public final class EntityCollisionScene implements SceneHash.Scene {

    static final class Arena extends BlockPlacementTest.TestLevel {
        long time;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long gameTime() { return time; }
                @Override public String biomeAt(int x, int y, int z) { return "minecraft:plains"; }
                @Override public int difficulty() { return 2; }
                // 동쪽 끝(x = 38)을 지나는 보더 — 그 근처 몹은 보더 모양과 부딪힌다
                @Override public double[] borderBounds() { return new double[] {-60.0, -60.0, 38.0, 60.0}; }
            };
        }
    }

    @Override
    public void run(SceneHash.Recorder rec) {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        Arena level = new Arena();
        level.wallOffArena();
        java.util.Random r = new java.util.Random(0x7018);
        String[] obstacles = {
                "minecraft:oak_stairs", "minecraft:stone_slab", "minecraft:oak_fence", "minecraft:cobblestone_wall",
                "minecraft:powder_snow", "minecraft:soul_sand", "minecraft:honey_block", "minecraft:scaffolding", "minecraft:snow",
                "minecraft:oak_trapdoor", "minecraft:stone", "minecraft:glass_pane", "minecraft:chest", "minecraft:hopper",
                "minecraft:short_grass", "minecraft:birch_stairs", "minecraft:mud", "minecraft:cobweb"};
        int[] states = new int[obstacles.length];
        for (int i = 0; i < obstacles.length; i++) states[i] = B.defaultState(obstacles[i]);
        for (int i = 0; i < 700; i++) {
            int x = r.nextInt(70) - 28, z = r.nextInt(70) - 28, h = 1 + r.nextInt(2);
            int st = states[r.nextInt(states.length)];
            for (int y = -60; y < -60 + h; y++) level.setBlock(new Pos(x, y, z), st, 2 | 16);
        }
        // 동굴 공기 구덩이(빠진 몹이 공기 아닌 공기 칸 사이로 떨어진다)
        int caveAir = B.defaultState("minecraft:cave_air");
        for (int x = 2; x < 8; x++) for (int z = 2; z < 8; z++) for (int y = -63; y <= -61; y++) level.setBlock(new Pos(x, y, z), caveAir, 2 | 16);
        for (int i = 0; i < 12; i++) {
            Entity e = i % 3 == 0 ? new Shulker(level, level.nextEntityId()) : new Boat(level, level.nextEntityId());
            e.moveTo(r.nextInt(60) - 25 + 0.5, -60, r.nextInt(60) - 25 + 0.5, 0, 0);
            level.addFreshEntity(e);
        }
        String[] types = {"minecraft:cow", "minecraft:sheep", "minecraft:zombie", "minecraft:skeleton", "minecraft:pig", "minecraft:chicken",
                "minecraft:spider", "minecraft:creeper", "minecraft:rabbit", "minecraft:slime", "minecraft:wolf", "minecraft:horse"};
        for (int i = 0; i < 150; i++) {
            Entity e = Entities.create(level, types[i % types.length]);
            e.moveTo(r.nextInt(64) - 26 + 0.5, -60 + r.nextInt(3), r.nextInt(64) - 26 + 0.5, r.nextFloat() * 360, 0);
            level.addFreshEntity(e);
        }
        for (int t = 1; t <= 300; t++) {
            level.time++;
            level.tickEntities(k -> true);
            if (t % 20 == 0) rec.line("tick " + t + " entities", hash(level));
        }
    }

    private static IdentityHash hash(Arena level) {
        List<Entity> list = new ArrayList<>();
        for (Entity e : level.entities().all()) list.add(e);
        list.sort(java.util.Comparator.comparingInt(Entity::id));
        IdentityHash h = new IdentityHash();
        h.add(list.size());
        for (Entity e : list) {
            h.add(e.id()).add(Double.doubleToRawLongBits(e.x())).add(Double.doubleToRawLongBits(e.y())).add(Double.doubleToRawLongBits(e.z()));
            h.add(Double.doubleToRawLongBits(e.vx())).add(Double.doubleToRawLongBits(e.vy())).add(Double.doubleToRawLongBits(e.vz()));
            h.add(e.onGround() ? 1 : 0).add(e.horizontalCollision ? 1 : 0).add(e.verticalCollision ? 1 : 0).add(e.minorHorizontalCollision ? 1 : 0);
            Pos on = e.getOnPos();
            h.add(on.x()).add(on.y()).add(on.z()).add(Float.floatToRawIntBits(e.fallDistance));
            if (e instanceof LivingEntity le) h.add(Float.floatToRawIntBits(le.health()));
        }
        return h;
    }
}
