package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.light.LightTables;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A08-01·A08-02 회귀. 잔디·균사체·나일리움이 "위 칸이 빛을 막는가"를, 신호기가 "빛줄기가 막히는가"를
 * 바닐라 LightEngine.getLightBlockInto / BlockState.getLightBlock 그대로 판정한다(예전엔 가림 모양 근사였다).
 * 기대값은 바닐라 1.21.1 에서 추출한 빛 표(LightTables)로 계산한다.
 */
class A08LightBlockTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks BL = Blocks.get();

    /** 바닐라 SpreadingSnowyDirtBlock.canBeGrass 의 빛 부분: 잔디(빈 모양 취급) 위 칸 above 가 빛을 16 이상 막는가. */
    static boolean vanillaBlocksGrass(int above) {
        int grass = BL.defaultState("minecraft:grass_block");
        return LightTables.lightBlockInto(grass, above, 1 /* UP */, LightTables.lightBlock(above)) >= 15;
    }

    private static int grassAfterRandomTick(String aboveBlockState) {
        var level = new BlockPlacementTest.TestLevel();
        Pos g = new Pos(8, -61, 8);
        int grass = BL.defaultState("minecraft:grass_block");
        level.setBlock(g, grass, 2 | 16);
        int above = BL.defaultState(aboveBlockState);
        level.setBlock(g.above(), above, 2 | 16);
        BlockBehaviors.of(grass).randomTick(grass, level, g, level.random());
        return level.getBlockState(g);
    }

    @Test
    void grassSurvivesUnderCarpet() {
        int dirt = BL.defaultState("minecraft:dirt");
        assertFalse(vanillaBlocksGrass(BL.defaultState("minecraft:white_carpet")), "바닐라: 양탄자는 빛을 막지 않는다");
        assertNotEquals(dirt, grassAfterRandomTick("minecraft:white_carpet"), "양탄자 밑 잔디가 흙이 되면 안 된다");
    }

    @Test
    void grassSurvivesUnderRepeaterAndComparator() {
        int dirt = BL.defaultState("minecraft:dirt");
        for (String n : new String[] {"minecraft:repeater", "minecraft:comparator", "minecraft:composter", "minecraft:moss_carpet"}) {
            assertFalse(vanillaBlocksGrass(BL.defaultState(n)), n);
            assertNotEquals(dirt, grassAfterRandomTick(n), n + " 밑 잔디가 흙이 되면 안 된다");
        }
    }

    @Test
    void grassDiesUnderTintedGlass() {
        int dirt = BL.defaultState("minecraft:dirt");
        assertTrue(vanillaBlocksGrass(BL.defaultState("minecraft:tinted_glass")), "바닐라: 색유리(빛 15)는 막는다");
        assertEquals(dirt, grassAfterRandomTick("minecraft:tinted_glass"), "색유리 밑 잔디는 흙이 된다");
    }

    /** 모든 위 칸 상태에서 잔디의 무작위 틱 결과가 바닐라 canBeGrass 와 같다(눈 한 겹은 참, 가득 찬 유체는 거짓, 나머지는 빛 판정). */
    @Test
    void grassMatchesVanillaRuleForEveryAboveState() {
        var level = new BlockPlacementTest.TestLevel();
        Pos g = new Pos(8, -61, 8);
        int grass = BL.defaultState("minecraft:grass_block");
        int dirt = BL.defaultState("minecraft:dirt");
        int bad = 0;
        java.util.Set<String> names = new java.util.TreeSet<>();
        for (int s = 0; s < BL.stateCount(); s++) {
            String n = BL.nameOfState(s);
            if (n.equals("minecraft:snow") || Fluids.amount(B.fluid(s)) == 8 || n.contains("piston") || n.contains("leaves")) continue;   // 눈·유체는 따로 규칙, 피스톤·잎은 이웃 갱신이 상태를 바꿈
            level.setBlock(g, grass, 2 | 16);
            level.setBlock(g.above(), s, 2 | 16);
            BlockBehaviors.of(grass).randomTick(grass, level, g, level.random());
            boolean becameDirt = level.getBlockState(g) == dirt;
            if (becameDirt != vanillaBlocksGrass(s)) {
                bad++;
                names.add(n);
            }
        }
        assertEquals(0, bad, "바닐라와 다른 위 칸 상태 " + bad + "개, 블록 " + names.size() + "종: " + names);
    }

    @Test
    void beaconBeamPassesBottomSlabAndCarpetByLightTable() {
        // 바닐라 BeaconBlockEntity: getLightBlock(state) >= 15 && !bedrock 일 때만 빛줄기가 끊긴다.
        for (String n : new String[] {"minecraft:oak_slab[type=bottom]", "minecraft:oak_stairs", "minecraft:white_carpet", "minecraft:snow",
                "minecraft:farmland", "minecraft:daylight_detector", "minecraft:dirt_path"}) {
            String bare = n.contains("[") ? n.substring(0, n.indexOf('[')) : n;
            int s = BL.defaultState(bare);
            assertTrue(LightTables.lightBlock(s) < 15, bare + " 는 바닐라 빛 막힘 15 미만");
            assertTrue(beamActive(bare), bare + " 는 신호기 빛줄기를 끊으면 안 된다");
        }
    }

    private static boolean beamActive(String aboveState) {
        var level = new BlockPlacementTest.TestLevel();
        Pos b = new Pos(10, -61, 10);
        int beacon = BL.defaultState("minecraft:beacon");
        level.setBlock(b, beacon, 2 | 16);
        if (aboveState != null) level.setBlock(b.above(), BL.defaultState(aboveState), 2 | 16);
        var be = new org.domi.furnace.server.block.entity.BeaconBlockEntity(b, beacon);
        for (int i = 0; i < 40; i++) be.tick(level);
        return be.isBeamActive();
    }

    /** 대조군: 아무것도 안 올리면 빛줄기가 열리고, 돌을 올리면 끊긴다(바닐라도 같다). 아래 둘은 통과해야 한다. */
    @Test
    void controlOpenSkyAndStone() {
        assertTrue(beamActive(null), "대조군: 열린 하늘");
        assertFalse(beamActive("minecraft:stone"), "대조군: 돌은 빛줄기를 끊는다");
    }

    @Test
    void beaconBeamStaysOpenUnderBottomSlabCarpetAndSnowLayer() {
        for (String n : new String[] {"minecraft:oak_slab", "minecraft:white_carpet", "minecraft:snow", "minecraft:oak_stairs", "minecraft:repeater"}) {
            assertTrue(beamActive(n), "바닐라는 " + n + " 를 통과한다(getLightBlock < 15) — 신호기 빛줄기가 끊기면 안 된다");
        }
    }

    private static int nyliumAfterRandomTick(String nylium, String aboveBlockState) {
        var level = new BlockPlacementTest.TestLevel();
        Pos p = new Pos(8, -61, 8);
        int n = BL.defaultState(nylium);
        level.setBlock(p, n, 2 | 16);
        level.setBlock(p.above(), BL.defaultState(aboveBlockState), 2 | 16);
        BlockBehaviors.of(n).randomTick(n, level, p, level.random());
        return level.getBlockState(p);
    }

    /** 바닐라 NyliumBlock.canBeNylium 도 같은 getLightBlockInto 식: 양탄자 밑은 그대로, 돌·색유리 밑은 네더랙. */
    @Test
    void nyliumUsesLightBlockInto() {
        int netherrack = BL.defaultState("minecraft:netherrack");
        for (String nylium : new String[] {"minecraft:crimson_nylium", "minecraft:warped_nylium"}) {
            assertEquals(BL.defaultState(nylium), nyliumAfterRandomTick(nylium, "minecraft:white_carpet"), nylium + " 양탄자 밑");
            assertEquals(BL.defaultState(nylium), nyliumAfterRandomTick(nylium, "minecraft:comparator"), nylium + " 비교기 밑");
            assertEquals(netherrack, nyliumAfterRandomTick(nylium, "minecraft:stone"), nylium + " 돌 밑");
            assertEquals(netherrack, nyliumAfterRandomTick(nylium, "minecraft:tinted_glass"), nylium + " 색유리 밑");
        }
    }
}
