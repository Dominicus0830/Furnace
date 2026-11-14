// 바닐라 `entity instanceof LivingEntity` 자리(Entity.isLiving): 돌 압력판·밭 밟기·가루눈 끼임·침대·슬라임 튕김·꿀 미끄럼 효과.
// 기대값은 바닐라 1.21.1 서버 jar 를 직접 띄워(평지, tick freeze → summon → tick step 1 → data get) 기록한 값이다.
// 바닐라 바닥은 y=-62 돌(서는 높이 -61), 여기 시험 레벨은 서는 높이 -60 이라 y 만 +1 옮겼다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.HoneyBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LivingBlockTriggersTest {

    private static final Blocks BL = Blocks.get();

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static void tick(ChunkLevel level, int n) {
        for (int i = 0; i < n; i++) {
            level.runScheduledTicks();
            level.tickEntities(k -> true);
        }
    }

    private static Entity spawn(ChunkLevel level, String type, double x, double y, double z) {
        var e = (Entity) Entities.create(level, "minecraft:" + type);
        e.moveTo(x, y, z, 0, 0);
        assertTrue(level.addFreshEntity(e));
        return e;
    }

    private static ItemEntity item(ChunkLevel level, double x, double y, double z) {
        var e = new ItemEntity(level, level.nextEntityId(), x, y, z, B.stack("minecraft:stone", 1), 0, 0, 0);
        assertTrue(level.addFreshEntity(e));
        return e;
    }

    @Test
    void everyLivingEntityIsLiving() {
        var level = new BlockPlacementTest.TestLevel();
        for (String t : new String[] {"zombie", "cow", "armor_stand", "bat", "slime", "villager"}) {
            assertTrue(((Entity) Entities.create(level, "minecraft:" + t)).isLiving(), t);
        }
        for (String t : new String[] {"minecart", "tnt", "boat", "arrow", "falling_block"}) {
            assertFalse(((Entity) Entities.create(level, "minecraft:" + t)).isLiving(), t);
        }
    }

    // ------------------------------------------------------------------ 돌 압력판

    /** 바닐라: 갑옷 거치대·좀비는 돌 압력판을 누르고 아이템은 못 누른다(나무 판은 누른다). */
    @Test
    void stonePlatePressedByMobsNotItems() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), BL.defaultState("minecraft:stone_pressure_plate"), 3);
        level.setBlock(new Pos(2, -60, 0), BL.defaultState("minecraft:stone_pressure_plate"), 3);
        level.setBlock(new Pos(4, -60, 0), BL.defaultState("minecraft:stone_pressure_plate"), 3);
        spawn(level, "zombie", 0.5, -60, 0.5);
        spawn(level, "armor_stand", 2.5, -60, 0.5);
        item(level, 4.5, -60, 0.5).setPickUpDelay(32767);
        tick(level, 1);
        assertEquals("minecraft:stone_pressure_plate[powered=true]", BL.describe(level.getBlockState(new Pos(0, -60, 0))), "좀비");
        assertEquals("minecraft:stone_pressure_plate[powered=true]", BL.describe(level.getBlockState(new Pos(2, -60, 0))), "갑옷 거치대");
        assertEquals("minecraft:stone_pressure_plate[powered=false]", BL.describe(level.getBlockState(new Pos(4, -60, 0))), "아이템");
    }

    // ------------------------------------------------------------------ 밭

    /** 바닐라: 좀비가 높이 떨어지면 밭이 흙이 된다. 갑옷 거치대는 부피(0.5×0.5×1.975 < 0.512)가 작아 못 밟는다. */
    @Test
    void zombieTramplesFarmlandArmorStandDoesNot() {
        var level = new EntityBlockHookTest.Level();
        Pos at = new Pos(8, -61, 5);
        int farmland = BL.defaultState("minecraft:farmland");
        level.setBlock(at, farmland, 3);
        var zombie = spawn(level, "zombie", 8.5, -60, 5.5);
        B.behavior(farmland).fallOn(level, farmland, at, zombie, 6.0F);
        assertEquals("minecraft:dirt", BL.describe(level.getBlockState(at)), "좀비");

        level.setBlock(at, farmland, 3);
        var stand = spawn(level, "armor_stand", 8.5, -60, 8.5);
        B.behavior(farmland).fallOn(level, farmland, at, stand, 12.0F);
        assertTrue(B.is(level.getBlockState(at), B.id("minecraft:farmland")), "갑옷 거치대");

        level.griefing = false;
        B.behavior(farmland).fallOn(level, farmland, at, zombie, 6.0F);
        assertTrue(B.is(level.getBlockState(at), B.id("minecraft:farmland")), "mobGriefing 꺼짐");
    }

    // ------------------------------------------------------------------ 튕김

    /** 바닐라 Motion[1](tick step 1 마다): 슬라임 위 갑옷 거치대(생물 1.0 배), 침대 발 위 갑옷 거치대(0.66 배), 슬라임 위 아이템(0.8 배). */
    private static final double[] SLIME_STAND = {
        -0.0784000015258789, -0.1552320045166016, -0.230527368912964, -0.30431682745754424, -0.37663049823865513,
        -0.44749789698341763, -0.5169479491049732, -0.5850090015087522, -0.6517088341626173, 0.5602746683838455,
        0.4706691841766809, 0.38285580794457064, 0.29679869756219535, 0.21246272774605843, 0.1298134757176633,
        0.04881720715342668, -0.030559137584406426, -0.1083479569414665, -0.18458100139508934, -0.25928938641366966,
        -0.33250360515682775, -0.40425354092157306, -0.47456847933954494, -0.5434771203303083, 0.4542075867638266,
        0.36672344216599334, 0.2809889787914891, 0.19696920304921983, 0.11462982121924593, 0.033937225455372436};
    private static final double[] BED_STAND = {
        -0.0784000015258789, -0.1552320045166016, -0.230527368912964, -0.30431682745754424, -0.37663049823865513,
        -0.44749789698341763, -0.5169479491049732, -0.5850090015087522, -0.6517088341626173, -0.7170746714356033,
        0.38540392341547586, 0.2992958507722839, 0.2149099379395746, 0.13221174175398598, 0.0511675079147662,
        -0.02825584279346527, -0.1060907280024123, -0.18236891699176302, -0.2571215436562177, -0.33037911921317653,
        -0.4021715446562735, 0.18172456895695704, 0.09969007951806008, 0.01929627830325734, -0.05948964842063942,
        -0.13669985811278054, -0.2123658650837467, 0.05895824814178259, -0.02062091722239263, -0.09860850079713647};
    private static final double[] SLIME_ITEM = {
        -0.0392, -0.07761599999999999, -0.11526368, -0.15215840639999997, -0.18831523827199997,
        -0.22374893350655997, -0.25847395483642877, -0.2925044757397002, -0.32585438622490615, -0.358537298500408,
        -0.3905665525303998, -0.42195522147979175, 0.3621728936401567, 0.3221728936401567, 0.28217289364015674,
        0.2373294357673536, 0.1933828470520065, 0.15031519011096636, 0.10810888630874703, 0.06674670858257209,
        0.026211774410920645, -0.013512461077297769, -0.05244221185575181, -0.09059336761863677, -0.12798150026626404,
        -0.16462187026093877, -0.20052943285572, 0.18857507535888451, 0.1485750753588845, 0.1085750753588845};

    private static final int ITEM_ID_MOD4 = 0;   // 0..3 을 다 돌려 30틱 모두 바닐라와 같은 것은 0 뿐

    @Test
    void bounceOnSlimeAndBedMatchesVanilla() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -61, 5), BL.defaultState("minecraft:slime_block"), 3);
        int foot = B.prop("facing").with(B.prop("part").with(BL.defaultState("minecraft:red_bed"), "foot"), "south");
        int head = B.prop("facing").with(B.prop("part").with(BL.defaultState("minecraft:red_bed"), "head"), "south");
        level.setBlock(new Pos(3, -61, 5), foot, 2);
        level.setBlock(new Pos(3, -61, 6), head, 2);
        var slime = spawn(level, "armor_stand", 0.5, -57, 5.5);
        var bed = spawn(level, "armor_stand", 3.5, -57, 5.5);
        // 땅 위 아이템은 (tickCount + id) % 4 == 0 일 때만 움직인다 — 바닐라 기록과 같은 위상의 id 로 맞춘다
        int id = level.nextEntityId();
        while (Math.floorMod(id, 4) != ITEM_ID_MOD4) id = level.nextEntityId();
        var it = new ItemEntity(level, id, 0.5, -57, 5.5, B.stack("minecraft:stone", 1), 0, 0, 0);
        assertTrue(level.addFreshEntity(it));
        it.setPickUpDelay(32767);
        it.setDeltaMovement(0, 0, 0);
        for (int i = 0; i < SLIME_STAND.length; i++) {
            tick(level, 1);
            assertEquals(SLIME_STAND[i], slime.vy(), 0.0, "슬라임 갑옷 거치대 틱 " + (i + 1));
            assertEquals(BED_STAND[i], bed.vy(), 0.0, "침대 갑옷 거치대 틱 " + (i + 1));
            assertEquals(SLIME_ITEM[i], it.vy(), 0.0, "슬라임 아이템 틱 " + (i + 1));
        }
    }

    // ------------------------------------------------------------------ 가루눈

    /** 바닐라 Pos[0]: 발 칸은 공기, 위 칸에 가루눈 — 생물은 발 칸이 가루눈일 때만 끼인다(갑옷 거치대는 안 느려진다). */
    private static final double[] SNOW_STAND_X = {
        -0.2, 0.07300000786781308, 0.32143002218723316, 0.4570728257609046, 0.5311338051145065,
        0.5715711045384717, 0.5936498725884637, 0.6057048813439803};

    @Test
    void powderSnowAboveFeetDoesNotSlowLiving() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -59, 11), BL.defaultState("minecraft:powder_snow"), 3);
        var stand = spawn(level, "armor_stand", -0.5, -60, 11.5);
        stand.setDeltaMovement(0.3, 0.0, 0.0);
        for (int i = 0; i < SNOW_STAND_X.length; i++) {
            tick(level, 1);
            assertEquals(SNOW_STAND_X[i], stand.x(), 0.0, "틱 " + (i + 1));
        }
    }

    // ------------------------------------------------------------------ 꿀 미끄럼 효과

    /** 바닐라 HoneyBlock.maybeDoSlideEffects: 생물·광산 수레·TNT·보트는 레벨 난수 nextInt(5) 를 두 번 뽑는다. 아이템은 안 뽑는다. */
    @Test
    void honeySlideEffectsDrawLevelRandomForLivingOnly() {
        Pos honey = new Pos(0, -59, 0);
        for (String t : new String[] {"item", "zombie", "armor_stand", "minecart", "tnt", "boat"}) {
            assertEquals(t.equals("item") ? 0 : 2, drawsBySlide(honey, t), t);
        }
        assertTrue(HoneyBlock.doesEntityDoHoneyBlockSlideEffects((Entity) Entities.create(new BlockPlacementTest.TestLevel(), "minecraft:chest_boat")));
    }

    /** 꿀 블록 옆면에 붙어 떨어지는 엔티티로 entityInside 를 한 번 부르고, 레벨 난수를 nextInt(5) 몇 번 뽑았는지 센다. */
    private static int drawsBySlide(Pos honey, String type) {
        var level = new BlockPlacementTest.TestLevel();
        int s = BL.defaultState("minecraft:honey_block");
        level.setBlock(honey, s, 3);
        Entity e = type.equals("item") ? new ItemEntity(level, level.nextEntityId(), 0, 0, 0, B.stack("minecraft:stone", 1), 0, 0, 0)
                : (Entity) Entities.create(level, "minecraft:" + type);
        // 동쪽 면에 붙어(x 중심에서 0.4375 + 폭/2 보다 멀리) 아래로 떨어지는 중
        e.moveTo(honey.x() + 0.5 + 0.4375 + e.bbWidth() / 2.0 + 0.01, honey.y() + 0.2, honey.z() + 0.5, 0, 0);
        e.setOnGround(false);
        e.setDeltaMovement(0, -0.2, 0);
        var r = (LegacyRandomSource) level.random();
        long before = r.rawState();
        B.behavior(s).entityInside(s, level, honey, e);
        assertEquals(-0.05, e.vy(), 0.0, type + ": 미끄럼 이동");
        long after = r.rawState();
        var replay = new LegacyRandomSource(0);
        replay.restoreState(before, false, 0);
        for (int n = 0; n <= 4; n++) {
            if (replay.rawState() == after) return n;
            replay.nextInt(5);
        }
        return -1;
    }
}
