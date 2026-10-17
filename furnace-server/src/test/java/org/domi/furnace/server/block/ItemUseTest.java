package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 아이템 사용(use_item·useOn)과 시선 판정 — 바닐라 규칙으로 손 계산. */
class ItemUseTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();

    /** 갑옷 칸이 있는 시험용 플레이어(평지 잔디 위 y=-60, 기본은 바로 아래를 본다). */
    static final class User implements BlockActor {
        final ItemStack[] hands = {ItemStack.EMPTY, ItemStack.EMPTY};
        final ItemStack[] armor = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        final List<ItemStack> added = new ArrayList<>();
        final List<String> stats = new ArrayList<>();
        boolean creative, sneaking;
        int permission;
        double x = 0.5, y = -60, z = 0.5;
        float yRot, xRot = 90;

        public float yRot() { return yRot; }
        public float xRot() { return xRot; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public double eyeHeight() { return sneaking ? 1.27 : 1.62; }
        public boolean isSecondaryUseActive() { return sneaking; }
        public boolean mayBuild() { return true; }
        public boolean instabuild() { return creative; }
        public boolean isSpectator() { return false; }
        public boolean isCreative() { return creative; }
        public int permissionLevel() { return permission; }
        public ItemStack itemInHand(int hand) { return hands[hand]; }
        public void setItemInHand(int hand, ItemStack s) { hands[hand] = s; }
        public Object handle() { return this; }
        @Override public ItemStack equipment(int ws) { return armor[ws - 5]; }
        @Override public void setEquipment(int ws, ItemStack s) { armor[ws - 5] = s; }
        @Override public boolean addToInventory(ItemStack s) { added.add(s); return true; }
        @Override public void awardStat(String stat) { stats.add(stat); }
    }

    private static ItemStack stack(String item, int n) { return new ItemStack(ItemContext.id("minecraft:item", item), n); }

    private static String name(ItemStack s) { return ItemTools.itemName(s); }

    private static String at(BlockPlacementTest.TestLevel level, int x, int y, int z) { return B.describe(level.getBlockState(new Pos(x, y, z))); }

    private static InteractionResult useOn(BlockPlacementTest.TestLevel level, User u, Pos pos, int face) {
        return BlockPlacement.useItemOn(level, u, 0, new Hit(pos, face, pos.x() + 0.5, pos.y() + 1.0, pos.z() + 0.5, false));
    }

    @Test
    void clip_hitsTopFaceOfGroundBelow() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new User();
        Clip.Result r = Clip.playerPOV(level, u, Clip.FluidMode.NONE);
        assertFalse(r.miss());
        assertEquals(new Pos(0, -61, 0), r.pos());
        assertEquals(Dir.UP, r.face());
        assertEquals(-60.0, r.y(), 1e-6);
        u.xRot = -90;   // 위를 보면 빗나감
        assertTrue(Clip.playerPOV(level, u, Clip.FluidMode.NONE).miss());
    }

    @Test
    void waterBucket_placesWater_andEmptyBucketPicksItUp() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new User();
        u.hands[0] = stack("minecraft:water_bucket", 1);
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(level, u, 0));
        assertEquals("minecraft:water[level=0]", at(level, 0, -60, 0));
        assertEquals("minecraft:bucket", name(u.hands[0]));
        assertTrue(u.stats.contains("minecraft:used:minecraft:water_bucket"));

        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(level, u, 0));
        assertEquals("minecraft:air", at(level, 0, -60, 0));
        assertEquals("minecraft:water_bucket", name(u.hands[0]));

        // 크리에이티브 용암 양동이는 그대로, 빈 양동이로 뜨면 손은 그대로 두고 찬 양동이를 인벤토리에(없을 때만)
        u.creative = true;
        u.hands[0] = stack("minecraft:lava_bucket", 1);
        ItemUse.useItem(level, u, 0);
        assertEquals("minecraft:lava[level=0]", at(level, 0, -60, 0));
        assertEquals("minecraft:lava_bucket", name(u.hands[0]));
        u.hands[0] = stack("minecraft:bucket", 1);
        ItemUse.useItem(level, u, 0);
        assertEquals("minecraft:air", at(level, 0, -60, 0));
        assertEquals("minecraft:bucket", name(u.hands[0]));
        assertEquals("minecraft:lava_bucket", name(u.added.get(0)));
    }

    @Test
    void waterBucket_waterlogsSlab_andBottleFillsFromSource() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new User();
        level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:oak_slab"), 3);
        u.y = -59;
        u.hands[0] = stack("minecraft:water_bucket", 1);
        ItemUse.useItem(level, u, 0);
        assertEquals("minecraft:oak_slab[type=bottom,waterlogged=true]", at(level, 0, -60, 0));

        u.hands[0] = stack("minecraft:glass_bottle", 3);
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(level, u, 0));
        assertEquals(2, u.hands[0].count());
        ItemStack potion = u.added.get(0);
        assertEquals("minecraft:potion", name(potion));
        assertEquals(Map.of("potion", "minecraft:water"), potion.patch().entries().get("minecraft:potion_contents"));
    }

    @Test
    void tools_tillFlattenStripWax() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new User();
        u.hands[0] = stack("minecraft:iron_hoe", 1);
        assertEquals(InteractionResult.CONSUME, useOn(level, u, new Pos(0, -61, 0), Dir.UP));
        assertEquals("minecraft:farmland[moisture=0]", at(level, 0, -61, 0));
        assertEquals(1, ItemTools.damage(u.hands[0]));

        u.hands[0] = stack("minecraft:iron_shovel", 1);
        useOn(level, u, new Pos(1, -61, 0), Dir.UP);
        assertEquals("minecraft:dirt_path", at(level, 1, -61, 0));
        assertEquals(InteractionResult.PASS, useOn(level, u, new Pos(2, -61, 0), Dir.DOWN), "아래 면은 안 된다");

        Pos log = new Pos(3, -60, 0);
        level.setBlock(log, B.prop("axis").with(B.defaultState("minecraft:oak_log"), "x"), 3);
        u.hands[0] = stack("minecraft:iron_axe", 1);
        useOn(level, u, log, Dir.UP);
        assertEquals("minecraft:stripped_oak_log[axis=x]", at(level, 3, -60, 0));

        Pos cu = new Pos(4, -60, 0);
        level.setBlock(cu, B.defaultState("minecraft:exposed_copper"), 3);
        u.hands[0] = stack("minecraft:honeycomb", 2);
        useOn(level, u, cu, Dir.UP);
        assertEquals("minecraft:waxed_exposed_copper", at(level, 4, -60, 0));
        assertEquals(1, u.hands[0].count());
        u.hands[0] = stack("minecraft:iron_axe", 1);
        useOn(level, u, cu, Dir.UP);   // 밀랍 벗기기
        assertEquals("minecraft:exposed_copper", at(level, 4, -60, 0));
        useOn(level, u, cu, Dir.UP);   // 녹 긁기
        assertEquals("minecraft:copper_block", at(level, 4, -60, 0));
        assertTrue(u.stats.contains("minecraft:used:minecraft:iron_axe"));
    }

    @Test
    void flintAndSteel_placesFire_andBoneMealGrowsCrop() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new User();
        u.hands[0] = stack("minecraft:flint_and_steel", 1);
        useOn(level, u, new Pos(0, -61, 0), Dir.UP);
        assertTrue(at(level, 0, -60, 0).startsWith("minecraft:fire"));
        assertEquals(1, ItemTools.damage(u.hands[0]));

        level.setBlock(new Pos(2, -61, 0), B.defaultState("minecraft:farmland"), 3);
        level.setBlock(new Pos(2, -60, 0), B.defaultState("minecraft:wheat"), 3);
        u.hands[0] = stack("minecraft:bone_meal", 5);
        assertEquals(InteractionResult.CONSUME, useOn(level, u, new Pos(2, -60, 0), Dir.UP));
        assertNotEquals("minecraft:wheat[age=0]", at(level, 2, -60, 0));
        assertEquals(4, u.hands[0].count());
    }

    @Test
    void armor_equipsAndSwaps() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new User();
        u.hands[0] = stack("minecraft:iron_helmet", 1);
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(level, u, 0));
        assertEquals("minecraft:iron_helmet", name(u.armor[0]));
        assertTrue(u.hands[0].isEmpty());
        u.hands[0] = stack("minecraft:diamond_helmet", 1);
        ItemUse.useItem(level, u, 0);
        assertEquals("minecraft:diamond_helmet", name(u.armor[0]));
        assertEquals("minecraft:iron_helmet", name(u.hands[0]), "벗은 투구가 손으로");
        u.hands[0] = stack("minecraft:elytra", 1);
        ItemUse.useItem(level, u, 0);
        assertEquals("minecraft:elytra", name(u.armor[1]));
    }

    @Test
    void enderEye_completesEndPortal() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new User();
        int frame = B.defaultState("minecraft:end_portal_frame");
        Prop facing = B.prop("facing"), eye = B.prop("eye");
        int cx = 10, cz = 10, y = -60;
        Pos last = null;
        for (int i = -1; i <= 1; i++) {
            level.setBlock(new Pos(cx + i, y, cz - 2), eye.with(facing.with(frame, "south"), true), 3);
            level.setBlock(new Pos(cx + i, y, cz + 2), eye.with(facing.with(frame, "north"), true), 3);
            level.setBlock(new Pos(cx - 2, y, cz + i), eye.with(facing.with(frame, "east"), true), 3);
            last = new Pos(cx + 2, y, cz + i);
            level.setBlock(last, eye.with(facing.with(frame, i == 1 ? "west" : "west"), i != 1), 3);
        }
        u.hands[0] = stack("minecraft:ender_eye", 2);
        assertEquals(InteractionResult.CONSUME, useOn(level, u, last, Dir.UP));
        assertEquals(1, u.hands[0].count());
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) assertEquals("minecraft:end_portal", at(level, cx + i, y, cz + j));
    }

    @Test
    void debugStick_cyclesSelectedProperty() {
        var level = new BlockPlacementTest.TestLevel();
        var u = new User();
        u.creative = true;
        u.permission = 2;
        Pos pos = new Pos(0, -60, 0);
        level.setBlock(pos, B.defaultState("minecraft:oak_stairs"), 3);
        u.hands[0] = stack("minecraft:debug_stick", 1);
        useOn(level, u, pos, Dir.UP);   // 고른 속성이 없으면 첫 속성(facing) north → south
        assertEquals("minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", at(level, 0, -60, 0));
        // 오른쪽 클릭은 고른 속성을 적지 않는다 — 왼쪽 클릭이 처음엔 첫 속성(facing), 다음엔 half 를 고른다
        assertTrue(ItemUse.debugStickInteraction(level, u, 0, level.getBlockState(pos), pos, false, u.hands[0]));
        assertEquals(Map.of("minecraft:oak_stairs", "facing"), u.hands[0].patch().entries().get("minecraft:debug_stick_state"));
        ItemUse.debugStickInteraction(level, u, 0, level.getBlockState(pos), pos, false, u.hands[0]);
        assertEquals(Map.of("minecraft:oak_stairs", "half"), u.hands[0].patch().entries().get("minecraft:debug_stick_state"));
        useOn(level, u, pos, Dir.UP);
        assertEquals("minecraft:oak_stairs[facing=south,half=top,shape=straight,waterlogged=false]", at(level, 0, -60, 0));
        u.permission = 0;
        assertEquals(InteractionResult.FAIL, useOn(level, u, pos, Dir.UP), "관리자 권한이 없으면 실패");
    }
}
