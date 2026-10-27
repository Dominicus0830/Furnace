// 몬스터 생성기 시험: 저장 왕복(짧은 정수·SpawnData·SpawnPotentials)·id 정규화·갱신 태그·플레이어 범위·지연·고정 시드 난수 순서.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.entity.SpawnerBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpawnerBlockEntityTest {

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final RandomSource rnd;
        boolean playerNear = true;
        final List<int[]> blockEvents = new ArrayList<>();
        Lvl(long seed) { rnd = RandomSource.create(seed); }
        @Override public RandomSource random() { return rnd; }
        @Override public boolean hasNearbyAlivePlayer(double x, double y, double z, double range) { return playerNear; }
        @Override public void blockEvent(Pos pos, int block, int type, int data) { blockEvents.add(new int[] {type, data}); }
    }

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static final Pos POS = new Pos(1, -60, 1);

    static NbtCompound spawnData(String id, boolean darkOnly) {
        NbtCompound d = new NbtCompound();
        NbtCompound e = new NbtCompound();
        e.putString("id", id);
        d.putCompound("entity", e);
        if (darkOnly) {
            NbtCompound r = new NbtCompound();
            r.putInt("sky_light_limit", 0);   // 시험 레벨 하늘빛은 15 → 언제나 실패
            d.putCompound("custom_spawn_rules", r);
        }
        return d;
    }

    static NbtCompound tag(int delay, NbtCompound data, int count) {
        NbtCompound t = new NbtCompound();
        t.putShort("Delay", (short) delay);
        t.putShort("MinSpawnDelay", (short) 200);
        t.putShort("MaxSpawnDelay", (short) 800);
        t.putShort("SpawnCount", (short) count);
        t.putCompound("SpawnData", data);
        return t;
    }

    static SpawnerBlockEntity placed(Lvl level, NbtCompound tag) {
        level.setBlock(POS, B.defaultState("minecraft:spawner"), 3);
        var be = (SpawnerBlockEntity) level.getBlockEntity(POS);
        assertNotNull(be);
        be.load(tag);
        return be;
    }

    @Test
    void saveRoundTripNormalizesIdAndUpdateTagDropsPotentials() {
        Lvl level = new Lvl(1);
        var be = placed(level, tag(20, spawnData("zombie", false), 4));
        NbtCompound out = be.saveWithoutMetadata();
        assertEquals((short) 20, out.getShort("Delay"));
        assertEquals((short) 4, out.getShort("SpawnCount"));
        assertEquals((short) 16, out.getShort("RequiredPlayerRange"));
        assertEquals("minecraft:zombie", out.getCompound("SpawnData").getCompound("entity").getString("id"));
        NbtList pots = out.getList("SpawnPotentials");
        assertEquals(1, pots.size(), "SpawnPotentials 가 없으면 SpawnData 하나(가중치 1)");
        assertEquals(1, ((NbtCompound) pots.get(0)).getInt("weight"));
        assertFalse(be.updateTag().contains("SpawnPotentials"));
        assertTrue(be.updateTag().contains("SpawnData"));
        assertTrue(be.hasUpdatePacket());
        var back = new SpawnerBlockEntity(POS, B.defaultState("minecraft:spawner"));
        back.load(out);
        assertEquals("minecraft:zombie", back.spawner().nextSpawnData().entity().getString("id"));
        assertEquals(1, back.spawner().spawnPotentials().size());
    }

    @Test
    void noPlayerNoRandomAndDelayCountsDown() {
        Lvl level = new Lvl(42);
        var be = placed(level, tag(5, spawnData("minecraft:zombie", false), 4));
        level.playerNear = false;
        be.tick(level);
        assertEquals(5, be.spawner().spawnDelay());
        assertEquals(RandomSource.create(42).nextLong(), level.rnd.nextLong(), "범위 밖이면 난수를 쓰지 않는다");
        level.playerNear = true;
        be.tick(level);
        assertEquals(4, be.spawner().spawnDelay());
    }

    @Test
    void delayMinusOnePicksDelayThenPotentialWithFixedSeed() {
        Lvl level = new Lvl(7);
        NbtCompound t = tag(-1, spawnData("minecraft:zombie", false), 4);
        NbtList pots = new NbtList((byte) 10);
        for (String[] e : new String[][] {{"minecraft:zombie", "3"}, {"minecraft:skeleton", "1"}}) {
            NbtCompound w = new NbtCompound();
            w.putCompound("data", spawnData(e[0], false));
            w.putInt("weight", Integer.parseInt(e[1]));
            pots.add(w);
        }
        t.putList("SpawnPotentials", pots);
        var be = placed(level, t);
        be.tick(level);
        RandomSource ref = RandomSource.create(7);
        int delay = 200 + ref.nextInt(600);
        int pick = ref.nextInt(4);
        assertEquals(delay - 1, be.spawner().spawnDelay(), "delay 뒤 같은 틱에 하나 줄인다");
        assertEquals(pick < 3 ? "minecraft:zombie" : "minecraft:skeleton", be.spawner().nextSpawnData().entity().getString("id"));
        assertEquals(1, level.blockEvents.size());
        assertArrayEquals(new int[] {1, 0}, level.blockEvents.get(0));
        assertEquals(ref.nextLong(), level.rnd.nextLong());
    }

    @Test
    void attemptRandomOrderWhenEveryAttemptFails() {
        Lvl level = new Lvl(1234);
        var be = placed(level, tag(0, spawnData("minecraft:zombie", true), 4));
        be.tick(level);
        // 시도마다: x(nextDouble−nextDouble) · y(nextInt(3)) · z(nextDouble−nextDouble). 칸이 막히면 거기서 그 시도를 끝낸다.
        RandomSource ref = RandomSource.create(1234);
        for (int i = 0; i < 4; i++) {
            double x = POS.x() + (ref.nextDouble() - ref.nextDouble()) * 4 + 0.5;
            double y = POS.y() + ref.nextInt(3) - 1;
            double z = POS.z() + (ref.nextDouble() - ref.nextDouble()) * 4 + 0.5;
            assertNotNull(org.domi.furnace.server.entity.EntityType.byName("minecraft:zombie"));
            // 빛 조건 실패는 난수를 더 쓰지 않는다
        }
        assertEquals(0, be.spawner().spawnDelay(), "아무것도 못 만들면 지연을 다시 고르지 않는다");
        assertTrue(level.blockEvents.isEmpty());
        assertEquals(ref.nextLong(), level.rnd.nextLong());
    }

    @Test
    void unknownEntityIdDelays() {
        Lvl level = new Lvl(9);
        var be = placed(level, tag(0, spawnData("minecraft:no_such_mob", false), 4));
        be.tick(level);
        RandomSource ref = RandomSource.create(9);
        assertEquals(200 + ref.nextInt(600), be.spawner().spawnDelay());
        ref.nextInt(1);   // 후보(SpawnData 하나, 가중치 1)
        assertEquals(ref.nextLong(), level.rnd.nextLong());
    }
}
