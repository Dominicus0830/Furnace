// 조각된 책장 시험: 칸 맞히기·책 넣기/빼기·점유 상태·비교기(마지막 칸 + 1)·저장 왕복·깔때기 넣기/빼기·퇴비통/주크박스 깔때기 규칙.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.entity.ChiseledBookShelfBlockEntity;
import org.domi.furnace.server.block.entity.HopperBlockEntity;
import org.domi.furnace.server.block.types.ChiseledBookShelfBlock;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChiseledBookShelfTest {

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<String> sounds = new ArrayList<>();
        final List<int[]> levelEvents = new ArrayList<>();
        @Override public void playSound(BlockActor except, Pos pos, String sound, String source, float volume, float pitch) { sounds.add(sound); }
        @Override public void levelEvent(BlockActor except, int type, Pos pos, int data) { levelEvents.add(new int[] {type, data}); }
    }

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        ItemContext.installTags(org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt"));   // #bookshelf_books
    }

    @org.junit.jupiter.api.AfterAll
    static void uninstallTags() { ItemContext.installTags(null); }

    static ItemStack item(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:" + name), n); }

    static final Pos POS = new Pos(1, -60, 1);

    /** facing north 인 책장의 앞면(북쪽 z=1) 칸 slot 을 맞힌 Hit. */
    static Hit hitSlot(int slot) {
        int row = slot / 3, col = slot % 3;
        double u = col == 0 ? 0.2 : col == 1 ? 0.5 : 0.8;   // north: u = 1 - x
        double v = row == 0 ? 0.75 : 0.25;
        return new Hit(POS, Dir.NORTH, POS.x() + (1.0 - u), POS.y() + v, POS.z(), false);
    }

    static int northState() { return B.prop("facing").with(B.defaultState("minecraft:chiseled_bookshelf"), "north"); }

    @Test
    void insertRemoveUpdatesStateAndComparator() {
        Lvl level = new Lvl();
        int st = northState();
        level.setBlock(POS, st, 3);
        var block = (ChiseledBookShelfBlock) B.behavior(st);
        var be = (ChiseledBookShelfBlockEntity) level.getBlockEntity(POS);
        assertNotNull(be);
        var a = new BlockPlacementTest.Actor();
        a.hands[0] = item("book", 3);
        assertEquals(ItemInteractionResult.CONSUME, block.useItemOn(a.hands[0], st, level, POS, a, 0, hitSlot(4)));
        assertEquals(2, a.hands[0].count());
        int now = level.getBlockState(POS);
        assertTrue(B.prop("slot_4_occupied").is(now));
        assertFalse(B.prop("slot_0_occupied").is(now));
        assertEquals(5, block.getAnalogOutputSignal(now, level, POS));
        assertEquals("minecraft:block.chiseled_bookshelf.insert", level.sounds.get(0));
        // 찬 칸에는 기본 상호작용으로, 책이 아니면 기본 상호작용으로, 옆면은 SKIP
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, block.useItemOn(a.hands[0], now, level, POS, a, 0, hitSlot(4)));
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, block.useItemOn(item("stone", 1), now, level, POS, a, 0, hitSlot(1)));
        Hit side = new Hit(POS, Dir.EAST, POS.x() + 1, POS.y() + 0.5, POS.z() + 0.5, false);
        assertEquals(ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, block.useItemOn(a.hands[0], now, level, POS, a, 0, side));
        // 빈 칸을 손으로 누르면 CONSUME(아무것도 안 함)
        assertEquals(InteractionResult.CONSUME, block.useWithoutItem(now, level, POS, a, hitSlot(0)));
        // 마법 책을 0 에
        a.hands[0] = item("enchanted_book", 1);
        block.useItemOn(a.hands[0], now, level, POS, a, 0, hitSlot(0));
        now = level.getBlockState(POS);
        assertEquals(1, block.getAnalogOutputSignal(now, level, POS));
        assertEquals("minecraft:block.chiseled_bookshelf.insert.enchanted", level.sounds.get(1));
        // 4 를 빼면 비교기 5, 상태 해제
        assertEquals(InteractionResult.CONSUME, block.useWithoutItem(now, level, POS, a, hitSlot(4)));
        now = level.getBlockState(POS);
        assertFalse(B.prop("slot_4_occupied").is(now));
        assertEquals(5, block.getAnalogOutputSignal(now, level, POS));
        assertEquals("minecraft:block.chiseled_bookshelf.pickup", level.sounds.get(2));
        assertEquals(1, be.count());
    }

    @Test
    void saveRoundTripAlwaysWritesItemsAndSlot() {
        var be = new ChiseledBookShelfBlockEntity(POS, northState());
        var tag = be.saveWithoutMetadata();
        assertTrue(tag.contains("Items"));
        assertEquals(-1, tag.getInt("last_interacted_slot"));
        var noSlot = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        var back = new ChiseledBookShelfBlockEntity(POS, northState());
        back.load(noSlot);
        assertEquals(0, back.lastInteractedSlot(), "없으면 getInt 기본 0");
        Lvl level = new Lvl();
        level.setBlock(POS, northState(), 3);
        var live = (ChiseledBookShelfBlockEntity) level.getBlockEntity(POS);
        live.setItem(2, item("writable_book", 1), level);
        back.load(live.saveWithoutMetadata());
        assertEquals("minecraft:writable_book", ItemTools.itemName(back.getItem(2)));
        assertEquals(2, back.lastInteractedSlot());
    }

    @Test
    void hopperRulesForBookshelf() {
        Lvl level = new Lvl();
        level.setBlock(POS, northState(), 3);
        var inv = HopperBlockEntity.getContainerAt(level, POS);
        assertNotNull(inv);
        assertEquals(6, inv.size());
        // 책만, 빈 칸에 한 권만
        assertTrue(inv.canPlaceItem(0, item("book", 1)));
        assertFalse(inv.canPlaceItem(0, item("book", 2)));
        assertFalse(inv.canPlaceItem(0, item("stone", 1)));
        ItemStack rest = HopperBlockEntity.addItem(null, inv, item("book", 1), Dir.UP);
        assertTrue(rest.isEmpty());
        assertTrue(B.prop("slot_0_occupied").is(level.getBlockState(POS)));
        // 받는 쪽(깔때기)에 자리가 있어야 뺀다
        level.setBlock(POS.below(), B.defaultState("minecraft:hopper"), 3);
        var hopper = HopperBlockEntity.getContainerAt(level, POS.below());
        assertTrue(inv.canTakeItem(hopper, 0, inv.get(0)));
        for (int i = 0; i < 5; i++) hopper.setItem(i, item("stone", 64));
        assertFalse(inv.canTakeItem(hopper, 0, inv.get(0)));
        // 빼기는 칸을 통째로 비우고 상태 갱신
        ItemStack out = inv.removeItem(0, 1);
        assertEquals(1, out.count());
        assertFalse(B.prop("slot_0_occupied").is(level.getBlockState(POS)));
    }

    @Test
    void composterHopperContainers() {
        Lvl level = new Lvl();
        int c0 = B.defaultState("minecraft:composter");
        level.setBlock(POS, c0, 3);
        var in = HopperBlockEntity.getContainerAt(level, POS);
        assertNotNull(in);
        assertTrue(in.worldly());
        assertArrayEquals(new int[] {0}, in.slotsForFace(Dir.UP));
        assertArrayEquals(new int[0], in.slotsForFace(Dir.DOWN));
        assertFalse(in.canPlaceItemThroughFace(0, item("stone", 1), Dir.UP));
        // 레벨 0 에 확률 > 0 인 퇴비는 언제나 오른다(난수를 쓰지 않는다)
        ItemStack rest = HopperBlockEntity.addItem(null, in, item("wheat_seeds", 1), Dir.UP);
        assertTrue(rest.isEmpty());
        assertEquals(1, B.prop("level").intValue(level.getBlockState(POS)));
        assertArrayEquals(new int[] {1500, 1}, level.levelEvents.get(0));
        // 한 번 받은 용기는 더 받지 않는다
        assertFalse(in.canPlaceItemThroughFace(0, item("wheat_seeds", 1), Dir.UP));
        // 레벨 7 은 칸이 없다
        level.setBlock(POS, B.prop("level").withInt(c0, 7), 3);
        assertEquals(0, HopperBlockEntity.getContainerAt(level, POS).size());
        // 레벨 8 은 아래로 뼛가루
        level.setBlock(POS, B.prop("level").withInt(c0, 8), 3);
        var outInv = HopperBlockEntity.getContainerAt(level, POS);
        assertEquals("minecraft:bone_meal", ItemTools.itemName(outInv.get(0)));
        assertTrue(outInv.canTakeItemThroughFace(0, outInv.get(0), Dir.DOWN));
        assertFalse(outInv.canTakeItemThroughFace(0, outInv.get(0), Dir.UP));
        outInv.removeItem(0, 1);
        assertEquals(0, B.prop("level").intValue(level.getBlockState(POS)));
        assertFalse(outInv.canTakeItemThroughFace(0, item("bone_meal", 1), Dir.DOWN));
    }

    @Test
    void jukeboxHopperRules() {
        Lvl level = new Lvl();
        level.setBlock(POS, B.defaultState("minecraft:jukebox"), 3);
        var inv = HopperBlockEntity.getContainerAt(level, POS);
        assertNotNull(inv);
        assertEquals(1, inv.size());
        assertFalse(inv.canPlaceItem(0, item("stone", 1)));
        assertTrue(inv.canPlaceItem(0, item("music_disc_cat", 1)));
        HopperBlockEntity.addItem(null, inv, item("music_disc_cat", 1), Dir.UP);
        assertTrue(B.prop("has_record").is(level.getBlockState(POS)));
        assertFalse(inv.canPlaceItem(0, item("music_disc_13", 1)), "차 있으면 못 넣는다");
        ItemStack out = inv.removeItem(0, 1);
        assertEquals("minecraft:music_disc_cat", ItemTools.itemName(out));
        assertFalse(B.prop("has_record").is(level.getBlockState(POS)));
    }

    @Test
    void shulkerLidBlockedOnlyWhenClosed() {
        Lvl level = new Lvl();
        int up = B.prop("facing").with(B.defaultState("minecraft:shulker_box"), "up");
        level.setBlock(POS, up, 3);
        var be = (org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity) level.getBlockEntity(POS);
        assertTrue(org.domi.furnace.server.block.types.ShulkerBoxBlock.canOpen(up, level, POS, be));
        level.setBlock(POS.above(), B.defaultState("minecraft:stone"), 3);
        assertFalse(org.domi.furnace.server.block.types.ShulkerBoxBlock.canOpen(up, level, POS, be), "위가 막히면 못 연다");
        // 옆 칸이 막혀도 위로 여는 상자는 열린다
        level.setBlock(POS.above(), B.defaultState("minecraft:air"), 3);
        level.setBlock(POS.relative(Dir.EAST), B.defaultState("minecraft:stone"), 3);
        assertTrue(org.domi.furnace.server.block.types.ShulkerBoxBlock.canOpen(up, level, POS, be));
        // 열리는 중이면 막혀도 참
        level.setBlock(POS.above(), B.defaultState("minecraft:stone"), 3);
        be.triggerEvent(1, 1);
        assertTrue(org.domi.furnace.server.block.types.ShulkerBoxBlock.canOpen(up, level, POS, be));
    }
}
