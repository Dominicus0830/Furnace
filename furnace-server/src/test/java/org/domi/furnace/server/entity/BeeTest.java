// 벌·벌집: 벌집 들어가기/나오기 틱 조건, 꿀 레벨 전이, 저장 왕복, 쏘기, 목표 선택 조건, 고양이 스포너 난수 순서.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.entity.BeehiveBlockEntity;
import org.domi.furnace.server.entity.spawn.CatSpawner;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BeeTest {

    @BeforeAll
    static void install() throws Exception { TestRecipes.manager(); }

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final Pos HIVE = new Pos(8, -60, 8);

    private static int hive(int honey) {
        return REG.stateIdOrAir("minecraft:beehive", Map.of("facing", "south", "honey_level", Integer.toString(honey)));
    }

    private static BeehiveBlockEntity placeHive(PatrolSpawnerTest.PatrolLevel level, int honey) {
        level.setBlock(HIVE, hive(honey), 3);
        return (BeehiveBlockEntity) level.getBlockEntity(HIVE);
    }

    private static List<Bee> bees(PatrolSpawnerTest.PatrolLevel level) {
        return level.entities().tickSnapshot().stream().filter(e -> e instanceof Bee).map(e -> (Bee) e).toList();
    }

    private static BeehiveBlockEntity.Occupant occupant(int ticks, boolean nectar) {
        NbtCompound d = new NbtCompound();
        d.putString("id", "minecraft:bee");
        if (nectar) d.putBoolean("HasNectar", true);
        return new BeehiveBlockEntity.Occupant(d, ticks, nectar ? 2400 : 600);
    }

    @Test
    void occupantLeavesAfterMinTicks() {
        var level = new PatrolSpawnerTest.PatrolLevel(7);
        var be = placeHive(level, 0);
        assertNotNull(be, "벌집은 블록 엔티티를 만든다");
        be.storeBee(occupant(595, false));
        // ticksInHive++ > 600: 595 에서 시작하면 7번째 틱에 나온다
        for (int i = 0; i < 6; i++) be.tick(level);
        assertEquals(1, be.getOccupantCount());
        be.tick(level);
        assertEquals(0, be.getOccupantCount());
        List<Bee> out = bees(level);
        assertEquals(1, out.size());
        Bee bee = out.get(0);
        assertEquals(HIVE, bee.getHivePos());
        assertEquals(8.5, bee.x(), 1e-9);
        assertEquals(8.5 + 0.55 + 0.7F / 2.0F, bee.z(), 1e-6, "남쪽 앞면 바깥");
        assertEquals(0, B.prop("honey_level").intValue(level.getBlockState(HIVE)), "꿀 없는 벌은 꿀을 안 채운다");
    }

    @Test
    void nightOrBlockedFrontKeepsBeesInside() {
        var night = new PatrolSpawnerTest.PatrolLevel(7);
        night.day = 5 * 24000L + 18000L;
        var be = placeHive(night, 0);
        be.storeBee(occupant(700, false));
        be.tick(night);
        assertEquals(1, be.getOccupantCount(), "밤에는 못 나온다");

        var blocked = new PatrolSpawnerTest.PatrolLevel(7);
        var b2 = placeHive(blocked, 0);
        blocked.setBlock(HIVE.south(), REG.stateIdOrAir("minecraft:stone", Map.of()), 3);
        b2.storeBee(occupant(700, false));
        b2.tick(blocked);
        assertEquals(1, b2.getOccupantCount(), "앞이 막히면 못 나온다");
        b2.emptyAllLivingFromHive(blocked, null, blocked.getBlockState(HIVE), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        assertEquals(0, b2.getOccupantCount(), "비상이면 나온다");
        assertEquals(8.5, bees(blocked).get(0).z(), 1e-9, "막혔으면 가운데");
    }

    @Test
    void honeyDeliveryRaisesLevel() {
        var level = new PatrolSpawnerTest.PatrolLevel(11);
        var be = placeHive(level, 4);
        be.storeBee(occupant(2401, true));
        be.tick(level);
        int honey = B.prop("honey_level").intValue(level.getBlockState(HIVE));
        assertTrue(honey == 5, "4 에서 +1(또는 +2 가 5 를 넘어 +1): " + honey);
        assertFalse(bees(level).get(0).hasNectar(), "꿀을 내려놓는다");

        var full = new PatrolSpawnerTest.PatrolLevel(11);
        var b5 = placeHive(full, 5);
        b5.storeBee(occupant(2401, true));
        b5.tick(full);
        assertEquals(5, B.prop("honey_level").intValue(full.getBlockState(HIVE)), "5 이상은 그대로");
    }

    @Test
    void hiveSaveRoundTrips() {
        var level = new PatrolSpawnerTest.PatrolLevel(3);
        var be = placeHive(level, 0);
        be.storeBee(occupant(12, true));
        be.storeBee(BeehiveBlockEntity.Occupant.create(77));
        NbtCompound tag = be.saveWithFullMetadata();
        NbtList list = tag.getList("bees");
        assertEquals(2, list.size());
        NbtCompound first = (NbtCompound) list.get(0);
        assertEquals(12, first.getIntOr0("ticks_in_hive"));
        assertEquals(2400, first.getIntOr0("min_ticks_in_hive"));
        assertEquals("minecraft:bee", first.getCompound("entity_data").getString("id"));
        var back = new BeehiveBlockEntity(HIVE, hive(0));
        back.load(tag);
        assertEquals(2, back.getOccupantCount());
        assertEquals(77, back.getBees().get(1).ticksInHive());
        assertEquals(600, back.getBees().get(1).minTicksInHive());
    }

    @Test
    void beeEntersHiveAndKeepsData() {
        var level = new PatrolSpawnerTest.PatrolLevel(5);
        var be = placeHive(level, 0);
        Bee bee = new Bee(level, level.nextEntityId());
        bee.moveTo(8.5, -59.0, 9.5, 0, 0);
        bee.setHasNectar(true);
        bee.setSavedFlowerPos(new Pos(3, -60, 3));
        level.addFreshEntity(bee);
        be.addOccupant(level, bee);
        assertTrue(bee.isRemoved());
        assertEquals(1, be.getOccupantCount());
        var o = be.getBees().get(0);
        assertEquals(2400, o.minTicksInHive(), "꿀이 있으면 2400");
        assertFalse(o.entityData().contains("Pos"));
        assertFalse(o.entityData().contains("UUID"));
        assertEquals(new Pos(3, -60, 3), be.savedFlowerPos(), "벌집에 꽃이 없으면 넘겨받는다");
    }

    @Test
    void beeSaveRoundTrips() {
        var level = new PatrolSpawnerTest.PatrolLevel(5);
        Bee bee = new Bee(level, level.nextEntityId());
        bee.setHivePos(new Pos(1, 2, 3));
        bee.setSavedFlowerPos(new Pos(4, 5, 6));
        bee.setHasNectar(true);
        bee.setRemainingPersistentAngerTime(321);
        bee.setStayOutOfHiveCountdown(40);
        NbtCompound tag = bee.saveWithoutId(new NbtCompound());
        assertArrayEquals(new int[] {1, 2, 3}, (int[]) tag.tags().get("hive_pos"));
        assertEquals(321, tag.getIntOr0("AngerTime"));
        Bee back = new Bee(level, level.nextEntityId());
        back.load(tag);
        assertEquals(new Pos(1, 2, 3), back.getHivePos());
        assertEquals(new Pos(4, 5, 6), back.getSavedFlowerPos());
        assertTrue(back.hasNectar());
        assertFalse(back.hasStung());
        assertEquals(321, back.getRemainingPersistentAngerTime());
        assertEquals(40, back.getStayOutOfHiveCountdown());
    }

    @Test
    void wantsToEnterHiveConditions() {
        var level = new PatrolSpawnerTest.PatrolLevel(5);
        Bee bee = new Bee(level, level.nextEntityId());
        bee.moveTo(8.5, -59.0, 8.5, 0, 0);
        assertFalse(bee.wantsToEnterHive(), "낮·꿀 없음·지치지 않음");
        bee.setHasNectar(true);
        assertTrue(bee.wantsToEnterHive(), "꿀이 있으면");
        bee.setStayOutOfHiveCountdown(10);
        assertFalse(bee.wantsToEnterHive(), "못 들어가는 시간");
        bee.setStayOutOfHiveCountdown(0);
        bee.setHasNectar(false);
        bee.ticksWithoutNectarSinceExitingHive = 3601;
        assertTrue(bee.wantsToEnterHive(), "3600 틱 넘게 꿀을 못 찾으면");
        var night = new PatrolSpawnerTest.PatrolLevel(5);
        night.day = 5 * 24000L + 18000L;
        Bee nb = new Bee(night, night.nextEntityId());
        assertTrue(nb.wantsToEnterHive(), "밤");
    }

    @Test
    void stingPoisonsByDifficultyAndMarksStung() {
        var level = new PatrolSpawnerTest.PatrolLevel(5);   // 보통 난이도
        Bee bee = new Bee(level, level.nextEntityId());
        bee.moveTo(8.5, -60, 8.5, 0, 0);
        bee.setRemainingPersistentAngerTime(100);
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(9.0, -60, 8.5, 0, 0);
        level.addFreshEntity(bee);
        level.addFreshEntity(cow);
        assertTrue(bee.doHurtTarget(cow));
        assertTrue(bee.hasStung());
        assertFalse(bee.isAngry(), "쏘면 분노가 끝난다");
        assertEquals(1, (Integer) cow.entityData.get(LivingEntity.DATA_STINGER_COUNT));
        var poison = cow.getEffect(org.domi.furnace.server.effect.MobEffect.POISON);
        assertNotNull(poison);
        assertEquals(200, poison.duration(), "보통 10초");
    }

    @Test
    void catSpawnerRandomOrder() {
        long seed = 42;
        var level = new PatrolSpawnerTest.PatrolLevel(seed);
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new io.netty.channel.embedded.EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        var sp = new CatSpawner();
        assertEquals(0, sp.tick(level, List.of(p), false), "동물 스폰 꺼짐");
        assertEquals(0, sp.nextTick());
        assertEquals(0, sp.tick(level, List.of(p), true), "슈퍼플랫에는 마을·오두막이 없다");
        assertEquals(1200, sp.nextTick());
        RandomSource m = RandomSource.create(seed);
        m.nextInt(1);
        m.nextInt(24);
        m.nextBoolean();
        m.nextInt(24);
        m.nextBoolean();
        assertEquals(m.nextLong(), level.random().nextLong(), "플레이어 → x(nextInt·nextBoolean) → z 순서");
        assertEquals(0, sp.tick(level, List.of(p), true));
        assertEquals(1199, sp.nextTick(), "1200 틱마다");
    }
}
