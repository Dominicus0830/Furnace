// 모닥불 블록 엔티티 시험: 올리기·요리 완료 틱·드롭·꺼지면 되감기·가득 차면 못 올림·저장 왕복·블록이 바뀔 때 쏟기.
package org.domi.furnace.server.block;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.entity.CampfireBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;

class CampfireBlockEntityTest {

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final RecipeManager rm;
        final List<ItemStack> dropped = new ArrayList<>();
        Lvl(RecipeManager rm) { this.rm = rm; }
        @Override public Object recipes() { return rm; }
        @Override public void dropItemStack(double x, double y, double z, ItemStack s) { dropped.add(s); }
    }

    /** 바닐라 markUpdated 는 같은 상태로 sendBlockUpdated 한다(같은 상태 setBlock 은 아무것도 보내지 않는다) — 올린 음식이 클라이언트에 보이게. */
    @Test
    void markUpdatedResendsTheBlockAndItsData() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        var sent = new ArrayList<Pos>();
        var level = new BlockPlacementTest.TestLevel() {
            @Override public void sendBlockUpdated(Pos p) { sent.add(p); }
        };
        Pos pos = new Pos(1, -60, 1);
        int lit = reg.stateIdOrAir("minecraft:campfire", Map.of("facing", "north", "lit", "true", "signal_fire", "false", "waterlogged", "false"));
        level.setBlock(pos, lit, 3);
        var be = new CampfireBlockEntity(pos, lit);
        be.markUpdated(level);
        assertEquals(List.of(pos), sent);
        assertTrue(be.hasUpdatePacket(), "바닐라 getUpdatePacket 이 패킷을 낸다");
    }

    /** 바닐라 cookTick: 다 익어 떨군 칸마다 sendBlockUpdated(같은 상태) — 떨군 음식이 클라이언트 모닥불에서 사라진다. */
    @Test
    void cookedFoodLeavingResendsTheBlock() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        var sent = new ArrayList<Pos>();
        var dropped = new ArrayList<ItemStack>();
        var level = new BlockPlacementTest.TestLevel() {
            @Override public void sendBlockUpdated(Pos p) { sent.add(p); }
            @Override public void dropItemStack(double x, double y, double z, ItemStack s) { dropped.add(s); }
        };
        Pos pos = new Pos(1, -60, 1);
        int lit = reg.stateIdOrAir("minecraft:campfire", Map.of("facing", "north", "lit", "true", "signal_fire", "false", "waterlogged", "false"));
        level.setBlock(pos, lit, 3);
        var be = new CampfireBlockEntity(pos, lit);
        assertTrue(be.placeFood(null, 0, new ItemStack(1, 1), 2, level));
        assertEquals(1, sent.size(), "올릴 때 한 번(markUpdated)");
        be.tick(level);
        assertEquals(1, sent.size());
        be.tick(level);
        assertEquals(1, dropped.size(), "레시피 없으면 그 아이템 그대로 떨군다");
        assertEquals(List.of(pos, pos), sent, "떨굴 때 한 번 더");
        assertTrue(be.items[0].isEmpty());
    }

    @Test
    void cooksBeefAfterItsCookingTimeAndDropsResult() throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        Lvl level = new Lvl(rm);
        Pos pos = new Pos(1, -60, 1);
        int lit = reg.stateIdOrAir("minecraft:campfire", Map.of("facing", "north", "lit", "true", "signal_fire", "false", "waterlogged", "false"));
        level.setBlock(pos, lit, 3);
        var be = new CampfireBlockEntity(pos, lit);
        ItemStack beef = TestRecipes.item("beef", 2);
        var entry = be.getCookableRecipe(beef, level);
        assertNotNull(entry);
        assertEquals(600, entry.recipe().cookingTime());
        assertTrue(be.placeFood(null, 0, beef, entry.recipe().cookingTime(), level));
        assertEquals(1, be.items[0].count());
        for (int i = 0; i < 599; i++) be.tick(level);
        assertTrue(level.dropped.isEmpty());
        assertEquals(599, be.cookingProgress[0]);
        be.tick(level);
        assertEquals(1, level.dropped.size());
        assertEquals("minecraft:cooked_beef", ItemTools.itemName(level.dropped.get(0)));
        assertTrue(be.items[0].isEmpty());
        // 저장 왕복
        be.placeFood(null, 0, TestRecipes.item("potato", 1), 600, level);
        for (int i = 0; i < 10; i++) be.tick(level);
        var back = new CampfireBlockEntity(pos, lit);
        back.load(be.saveWithoutMetadata());
        assertEquals(10, back.cookingProgress[0]);
        assertEquals(600, back.cookingTime[0]);
        assertEquals("minecraft:potato", ItemTools.itemName(back.items[0]));
    }

    @Test
    void unlitCampfireRewindsAndFullCampfireRefuses() throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        Lvl level = new Lvl(rm);
        Pos pos = new Pos(1, -60, 1);
        int off = reg.stateIdOrAir("minecraft:campfire", Map.of("facing", "north", "lit", "false", "signal_fire", "false", "waterlogged", "false"));
        level.setBlock(pos, off, 3);
        var be = new CampfireBlockEntity(pos, off);
        for (int i = 0; i < 4; i++) assertTrue(be.placeFood(null, 0, TestRecipes.item("beef", 1), 600, level));
        assertNull(be.getCookableRecipe(TestRecipes.item("beef", 1), level), "빈 칸이 없으면 올릴 수 없다");
        be.cookingProgress[0] = 5;
        be.tick(level);
        assertEquals(3, be.cookingProgress[0]);
        be.tick(level);
        be.tick(level);
        assertEquals(0, be.cookingProgress[0], "0 아래로는 내려가지 않는다");
    }
}
