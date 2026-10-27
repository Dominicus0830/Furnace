// 제작기 시험: 칸 잠금·저장 왕복·비교기·깔때기 고르게 채우기·신호로 제작(결과를 앞 용기로·남은 재료·재료 하나씩 줄임)·crafting 틱.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.entity.CrafterBlockEntity;
import org.domi.furnace.server.block.entity.HopperBlockEntity;
import org.domi.furnace.server.block.types.CrafterBlock;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrafterBlockEntityTest {

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<int[]> levelEvents = new ArrayList<>();
        RecipeManager rm;
        @Override public void levelEvent(BlockActor except, int type, Pos pos, int data) { levelEvents.add(new int[] {type, data}); }
        @Override public Object recipes() { return rm; }
    }

    RecipeManager rm;

    @BeforeEach
    void setUp() throws Exception {
        rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
    }

    static ItemStack item(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:" + name), n); }

    static final Pos POS = new Pos(1, -60, 1);

    /** 앞이 동쪽인 제작기. */
    static int eastCrafter() { return B.prop("orientation").with(B.defaultState("minecraft:crafter"), "east_up"); }

    @Test
    void slotLockSaveAndComparator() {
        Lvl level = new Lvl();
        int st = eastCrafter();
        level.setBlock(POS, st, 3);
        var be = (CrafterBlockEntity) level.getBlockEntity(POS);
        be.setSlotState(4, false, level);
        assertTrue(be.isSlotDisabled(4));
        be.items[0] = item("stone", 2);
        be.setSlotState(0, false, level);
        assertFalse(be.isSlotDisabled(0), "찬 칸은 잠글 수 없다");
        var block = (CrafterBlock) B.behavior(st);
        assertEquals(2, block.getAnalogOutputSignal(st, level, POS));
        var tag = be.saveWithoutMetadata();
        assertArrayEquals(new int[] {4}, (int[]) tag.tags().get("disabled_slots"));
        assertEquals(0, tag.getInt("triggered"));
        assertEquals(0, tag.getInt("crafting_ticks_remaining"));
        var back = new CrafterBlockEntity(POS, st);
        back.load(tag);
        assertTrue(back.isSlotDisabled(4));
        assertEquals(2, back.items[0].count());
    }

    @Test
    void hopperFillsEvenlyAndSkipsLocked() {
        Lvl level = new Lvl();
        level.setBlock(POS, eastCrafter(), 3);
        var be = (CrafterBlockEntity) level.getBlockEntity(POS);
        be.setSlotState(1, false, level);
        var inv = HopperBlockEntity.getContainerAt(level, POS);
        // 잠긴 칸 1 은 건너뛰고 0, 2, 3 … 순서로 하나씩
        for (int i = 0; i < 3; i++) assertTrue(HopperBlockEntity.addItem(null, inv, item("oak_planks", 1), Dir.UP).isEmpty());
        assertEquals(1, be.items[0].count());
        assertTrue(be.items[1].isEmpty());
        assertEquals(1, be.items[2].count());
        assertEquals(1, be.items[3].count());
        assertFalse(inv.canPlaceItem(0, item("oak_planks", 1)), "뒤에 빈 칸이 있으면 앞 칸에 더 쌓지 않는다");
        assertFalse(inv.canPlaceItem(1, item("oak_planks", 1)));
    }

    @Test
    void craftsIntoFrontContainerAndShrinksInputs() {
        Lvl level = new Lvl();
        level.rm = rm;
        int st = eastCrafter();
        level.setBlock(POS, st, 3);
        level.setBlock(POS.relative(Dir.EAST), B.defaultState("minecraft:barrel"), 3);
        var be = (CrafterBlockEntity) level.getBlockEntity(POS);
        be.items[0] = item("oak_log", 3);
        var block = (CrafterBlock) B.behavior(st);
        block.tick(st, level, POS, level.random());
        var chest = HopperBlockEntity.getContainerAt(level, POS.relative(Dir.EAST));
        assertEquals("minecraft:oak_planks", ItemTools.itemName(chest.get(0)));
        assertEquals(4, chest.get(0).count());
        assertEquals(2, be.items[0].count());
        int now = level.getBlockState(POS);
        assertTrue(B.prop("crafting").is(now));
        assertTrue(level.levelEvents.isEmpty(), "용기에 다 들어가면 쏘지 않는다(1049·2010 없음)");
        // 6틱 뒤 crafting=false
        for (int i = 0; i < 6; i++) be.tick(level);
        assertFalse(B.prop("crafting").is(level.getBlockState(POS)));
    }

    @Test
    void noRecipeFails1050() {
        Lvl level = new Lvl();
        level.rm = rm;
        int st = eastCrafter();
        level.setBlock(POS, st, 3);
        var be = (CrafterBlockEntity) level.getBlockEntity(POS);
        be.items[0] = item("dirt", 1);
        ((CrafterBlock) B.behavior(st)).tick(st, level, POS, level.random());
        assertArrayEquals(new int[] {1050, 0}, level.levelEvents.get(0));
        assertEquals(1, be.items[0].count());
    }

    @Test
    void ejectsWhenNoContainer() {
        Lvl level = new Lvl();
        level.rm = rm;
        int st = eastCrafter();
        level.setBlock(POS, st, 3);
        var be = (CrafterBlockEntity) level.getBlockEntity(POS);
        be.items[4] = item("oak_log", 1);
        ((CrafterBlock) B.behavior(st)).tick(st, level, POS, level.random());
        assertArrayEquals(new int[] {1049, 0}, level.levelEvents.get(0));
        assertArrayEquals(new int[] {2010, Dir.EAST}, level.levelEvents.get(1));
        assertTrue(be.items[4].isEmpty());
    }
}
