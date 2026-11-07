// 리스폰 위치 시험: 침대에서 일어설 자리와 방향, 정박기 충전 소모(강제·keepAll 이면 안 씀), 충전 0, 강제 지점, 차원 규칙.
// 그리고 정박기 블록: 발광석 충전, 네더에서 쓰면 스폰 지점(메시지·소리), 다른 차원이면 터진다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.RespawnAnchorBlock;
import org.domi.furnace.server.entity.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RespawnFinderTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    /** 칸 높이는 오버월드(슈퍼플랫 바닥 -61)로 두고 차원 키와 타입 값(침대·정박기 규칙은 바닐라처럼 타입의 bedWorks·respawnAnchorWorks)만 바꾼다. */
    static final class Lvl extends BlockPlacementTest.TestLevel {
        final String dim;
        final List<String> sounds = new ArrayList<>();
        Lvl(String dim) { this.dim = dim; }

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public String dimension() { return dim; }
                @Override public DimensionType dimensionType() { return typeOf(dim); }
            };
        }

        @Override
        public void playSound(BlockActor except, double x, double y, double z, String sound, String category, float volume, float pitch) {
            sounds.add(sound);
            super.playSound(except, x, y, z, sound, category, volume, pitch);
        }
    }

    /** 그 차원 키의 바닐라 타입 값을 오버월드 높이로. */
    static DimensionType typeOf(String dim) {
        var t = DimensionType.byLevelKey(dim);
        if (t == null) t = DimensionType.OVERWORLD;
        var o = DimensionType.OVERWORLD;
        return new DimensionType(dim, t.typeKey(), o.minY(), o.height(), o.logicalHeight(), t.hasSkyLight(), t.hasCeiling(), t.ultraWarm(),
                t.natural(), t.coordinateScale(), t.fixedTime(), t.bedWorks(), t.respawnAnchorWorks(), t.piglinSafe(), t.gen());
    }

    static int anchor(int charges) { return REG.stateIdOrAir("minecraft:respawn_anchor", Map.of("charges", Integer.toString(charges))); }

    static final Pos A = new Pos(0, -60, 0);

    @Test
    void chargedAnchorInNetherUsesOneCharge() {
        var nether = new Lvl("minecraft:the_nether");
        nether.setBlock(A, anchor(2), 3);
        var spot = RespawnFinder.findRespawnAndUseSpawnBlock(nether, A, 0.0F, false, false);
        assertNotNull(spot);
        // RESPAWN_OFFSETS 첫 칸(북쪽 0,0,-1)이 비어 있다
        assertEquals(0.5, spot.x(), 1e-9);
        assertEquals(-60.0, spot.y(), 1e-9);
        assertEquals(-0.5, spot.z(), 1e-9);
        assertEquals(0.0F, spot.yaw(), 1e-4, "정박기(남쪽)를 본다");
        assertEquals(1, RespawnAnchorBlock.charge(nether.getBlockState(A)));
        // keepAllPlayerData(엔드 크레딧 뒤)이거나 강제 지점이면 충전을 쓰지 않는다
        assertNotNull(RespawnFinder.findRespawnAndUseSpawnBlock(nether, A, 0.0F, false, true));
        assertEquals(1, RespawnAnchorBlock.charge(nether.getBlockState(A)));
        assertNotNull(RespawnFinder.findRespawnAndUseSpawnBlock(nether, A, 0.0F, true, false));
        assertEquals(1, RespawnAnchorBlock.charge(nether.getBlockState(A)));
        assertNotNull(RespawnFinder.findRespawnAndUseSpawnBlock(nether, A, 0.0F, false, false));
        assertEquals(0, RespawnAnchorBlock.charge(nether.getBlockState(A)));
        // 충전이 없으면 강제가 아닌 이상 없다
        assertNull(RespawnFinder.findRespawnAndUseSpawnBlock(nether, A, 0.0F, false, false));
        assertNotNull(RespawnFinder.findRespawnAndUseSpawnBlock(nether, A, 0.0F, true, false), "강제면 충전 0 이어도");
    }

    @Test
    void anchorOutsideNetherAndBlockedAnchorGiveNothing() {
        var over = new Lvl("minecraft:overworld");
        over.setBlock(A, anchor(4), 3);
        assertNull(RespawnFinder.findRespawnAndUseSpawnBlock(over, A, 0.0F, false, false), "오버월드에서는 정박기가 안 된다");
        assertEquals(4, RespawnAnchorBlock.charge(over.getBlockState(A)));
        var nether = new Lvl("minecraft:the_nether");
        nether.setBlock(A, anchor(3), 3);
        int stone = B.defaultState("minecraft:stone");
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dy != 0 || dz != 0) nether.setBlock(new Pos(dx, -60 + dy, dz), stone, 3);
        }
        assertNull(RespawnFinder.findRespawnAndUseSpawnBlock(nether, A, 0.0F, false, false), "설 자리가 없으면 없다");
        assertEquals(3, RespawnAnchorBlock.charge(nether.getBlockState(A)), "자리가 없으면 충전을 쓰지 않는다");
    }

    @Test
    void bedSpawnStandsBesideBedAndFacesIt() {
        var over = new Lvl("minecraft:overworld");
        Pos foot = new Pos(0, -60, 0), head = new Pos(0, -60, 1);
        over.setBlock(foot, REG.stateIdOrAir("minecraft:red_bed", Map.of("facing", "south", "part", "foot", "occupied", "false")), 3);
        over.setBlock(head, REG.stateIdOrAir("minecraft:red_bed", Map.of("facing", "south", "part", "head", "occupied", "false")), 3);
        var spot = RespawnFinder.findRespawnAndUseSpawnBlock(over, head, 0.0F, false, false);
        assertNotNull(spot);
        assertEquals(-60.0, spot.y(), 1e-9);
        double dx = head.x() + 0.5 - spot.x(), dz = head.z() + 0.5 - spot.z();
        assertTrue(Math.abs(dx) <= 2.0 && Math.abs(dz) <= 3.0, "침대 둘레");
        assertEquals(RespawnFinder.lookAtYaw(new org.domi.furnace.server.entity.Vec3(spot.x(), spot.y(), spot.z()), head), spot.yaw(), 1e-6);
        // 같은 침대라도 네더에서는 안 된다
        var nether = new Lvl("minecraft:the_nether");
        nether.setBlock(foot, over.getBlockState(foot), 3);
        nether.setBlock(head, over.getBlockState(head), 3);
        assertNull(RespawnFinder.findRespawnAndUseSpawnBlock(nether, head, 0.0F, false, false));
        // 침대가 부서졌으면(공기) 강제가 아니므로 없다 → 월드 스폰 + no_respawn_block
        over.setBlock(head, B.defaultState("minecraft:air"), 3);
        assertNull(RespawnFinder.findRespawnAndUseSpawnBlock(over, head, 0.0F, false, false));
    }

    @Test
    void forcedSpawnPointNeedsTwoFreeBlocks() {
        var over = new Lvl("minecraft:overworld");
        var spot = RespawnFinder.findRespawnAndUseSpawnBlock(over, A, 45.0F, true, false);
        assertNotNull(spot);
        assertEquals(0.5, spot.x(), 1e-9);
        assertEquals(-59.9, spot.y(), 1e-9);
        assertEquals(0.5, spot.z(), 1e-9);
        assertEquals(45.0F, spot.yaw(), "강제 지점은 저장된 각도");
        over.setBlock(A.above(), B.defaultState("minecraft:stone"), 3);
        assertNull(RespawnFinder.findRespawnAndUseSpawnBlock(over, A, 45.0F, true, false));
    }

    /** 리스폰 지점을 기억하는 행위자. */
    static final class Actor implements BlockActor {
        final ItemStack[] hands = {ItemStack.EMPTY, ItemStack.EMPTY};
        String dim;
        Pos spawn;
        int setCalls;
        public float yRot() { return 0; }
        public float xRot() { return 0; }
        public double x() { return 2.5; }
        public double y() { return -60; }
        public double z() { return 0.5; }
        public double eyeHeight() { return 1.62; }
        public boolean isSecondaryUseActive() { return false; }
        public boolean mayBuild() { return true; }
        public boolean instabuild() { return false; }
        public boolean isSpectator() { return false; }
        public boolean isCreative() { return false; }
        public int permissionLevel() { return 0; }
        public ItemStack itemInHand(int hand) { return hands[hand]; }
        public void setItemInHand(int hand, ItemStack s) { hands[hand] = s; }
        public Object handle() { return this; }
        @Override public boolean respawnPointIs(String d, Pos p) { return p.equals(spawn) && d.equals(dim); }
        @Override public void setRespawnPosition(String d, Pos p, float angle, boolean forced, boolean msg) {
            assertEquals(0.0F, angle);
            assertFalse(forced);
            assertTrue(msg);
            dim = d;
            spawn = p;
            setCalls++;
        }
    }

    @Test
    void anchorBlockChargesSetsSpawnAndExplodesElsewhere() {
        var nether = new Lvl("minecraft:the_nether");
        nether.setBlock(A, anchor(0), 3);
        var a = new Actor();
        a.hands[0] = new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:glowstone"), 2);
        int st = nether.getBlockState(A);
        Hit hit = new Hit(A, Dir.UP, 0.5, -59.0, 0.5, false);
        assertEquals(InteractionResult.PASS, B.behavior(st).useWithoutItem(st, nether, A, a, hit), "충전 0 이면 넘긴다");
        assertEquals(ItemInteractionResult.CONSUME, B.behavior(st).useItemOn(a.hands[0], st, nether, A, a, 0, hit));
        assertEquals(1, a.hands[0].count());
        st = nether.getBlockState(A);
        assertEquals(1, RespawnAnchorBlock.charge(st));
        assertEquals(InteractionResult.SUCCESS, B.behavior(st).useWithoutItem(st, nether, A, a, hit));
        assertEquals(A, a.spawn);
        assertEquals("minecraft:the_nether", a.dim);
        assertTrue(nether.sounds.contains("minecraft:block.respawn_anchor.set_spawn"));
        assertEquals(InteractionResult.CONSUME, B.behavior(st).useWithoutItem(st, nether, A, a, hit), "이미 이 칸이면 다시 정하지 않는다");
        assertEquals(1, a.setCalls);

        var over = new Lvl("minecraft:overworld");
        over.setBlock(A, anchor(1), 3);
        int os = over.getBlockState(A);
        assertEquals(InteractionResult.CONSUME, B.behavior(os).useWithoutItem(os, over, A, a, hit));
        assertFalse(B.is(over.getBlockState(A), "minecraft:respawn_anchor"), "오버월드에서 쓰면 터져 없어진다");
        assertEquals(1, a.setCalls);
    }

    @Test
    void waterNextToAnchorCountsAsFlowingWater() {
        var nether = new Lvl("minecraft:the_nether");
        assertFalse(isWaterThatWouldFlow(nether, A));
        nether.setBlock(A, B.defaultState("minecraft:water"), 3);
        assertTrue(isWaterThatWouldFlow(nether, A), "원천");
    }

    private static boolean isWaterThatWouldFlow(Lvl level, Pos pos) { return RespawnAnchorBlock.isWaterThatWouldFlow(pos, level); }
}
