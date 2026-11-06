// 화로 블록 엔티티 시험: 연료 소모·lit 켜짐·굽는 시간 뒤 결과·재료 감소·연료가 떨어지면 꺼짐·진행 되감기·저장 왕복.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import org.domi.furnace.server.block.entity.FurnaceBlockEntity;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.Test;

class FurnaceBlockEntityTest {

    static ItemStack item(String name, int n) { return TestRecipes.item(name, n); }

    @Test
    void smeltsRawIronConsumingCoalAndRewindsWhenFuelRunsOut() throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var level = new BlockPlacementTest.TestLevel() {
            @Override public Object recipes() { return rm; }
        };
        Pos pos = new Pos(1, -60, 1);
        int off = reg.stateIdOrAir("minecraft:furnace", java.util.Map.of("facing", "north", "lit", "false"));
        level.setBlock(pos, off, 3);
        FurnaceBlockEntity be = FurnaceBlockEntity.furnace(pos, off);
        be.items[0] = item("raw_iron", 2);
        be.items[1] = item("coal", 1);
        be.cookingTotalTime = 200;
        be.tick(level);
        assertTrue(be.isLit());
        assertEquals(1600, be.litTime, "감소는 점화보다 먼저라 첫 틱엔 그대로");
        assertEquals(1600, be.litDuration);
        assertTrue(be.items[1].isEmpty());
        assertEquals(reg.stateIdOrAir("minecraft:furnace", java.util.Map.of("facing", "north", "lit", "true")), level.getBlockState(pos));
        for (int i = 0; i < 199; i++) be.tick(level);
        assertEquals("minecraft:iron_ingot", org.domi.furnace.server.item.ItemTools.itemName(be.items[2]));
        assertEquals(1, be.items[2].count());
        assertEquals(1, be.items[0].count());
        assertEquals(0, be.cookingProgress);
        var used = new ArrayList<org.domi.furnace.server.data.ResourceId>();
        be.takeExperience(rm, used);
        assertEquals(1, used.size());
        // 저장 왕복
        var tag = be.saveWithoutMetadata();
        FurnaceBlockEntity back = FurnaceBlockEntity.furnace(pos, off);
        back.load(tag);
        assertEquals(be.litTime, back.litTime);
        assertEquals(be.items[2].count(), back.items[2].count());
        assertEquals(0, back.litDuration, "연료 칸이 비어 litDuration 은 0");
    }

    @Test
    void noFuelNoFire() throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        var level = new BlockPlacementTest.TestLevel() {
            @Override public Object recipes() { return rm; }
        };
        Pos pos = new Pos(1, -60, 1);
        FurnaceBlockEntity be = FurnaceBlockEntity.furnace(pos, B.defaultState("minecraft:furnace"));
        be.items[0] = item("raw_iron", 1);
        be.cookingProgress = 10;
        be.cookingTotalTime = 200;
        be.tick(level);
        assertFalse(be.isLit());
        assertEquals(8, be.cookingProgress, "불이 꺼진 채 진행도는 틱마다 2씩 되감긴다");
    }

    // ------------------------------------------------------------------ 플러그인 취소: 상태가 하나도 안 바뀐다(서버 주도 — 되돌려 보낼 클라이언트 예측 없음)

    /** 이벤트 입구를 바꿔 끼운 시험 레벨. */
    private static BlockPlacementTest.TestLevel levelWith(org.domi.furnace.server.recipe.RecipeManager rm, org.domi.furnace.server.plugin.GameEvents events) {
        return new BlockPlacementTest.TestLevel() {
            @Override public Object recipes() { return rm; }
            @Override public org.domi.furnace.server.plugin.GameEvents gameEvents() { return events; }
        };
    }

    @Test
    void furnaceBurnCancelKeepsFuelAndFireOut(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        RecipeManager rm = org.domi.furnace.server.recipe.SyntheticRecipes.manager(tmp);
        int[] asked = {0};
        var level = levelWith(rm, new org.domi.furnace.server.plugin.GameEvents() {
            @Override public FurnaceBurn furnaceBurn(String dimension, Pos pos, int state, ItemStack fuel, int burnTime) { asked[0]++; return null; }
        });
        Pos pos = new Pos(1, -60, 1);
        FurnaceBlockEntity be = FurnaceBlockEntity.furnace(pos, B.defaultState("minecraft:furnace"));
        be.items[0] = org.domi.furnace.server.recipe.SyntheticRecipes.item("raw_iron", 2);
        be.items[1] = org.domi.furnace.server.recipe.SyntheticRecipes.item("coal", 1);
        be.cookingTotalTime = 200;
        for (int i = 0; i < 5; i++) be.tick(level);
        assertTrue(asked[0] >= 1, "연료를 태우기 전에 물었다");
        assertFalse(be.isLit(), "취소하면 불이 켜지지 않는다");
        assertEquals(1, be.items[1].count(), "취소하면 연료가 줄지 않는다");
        assertEquals(2, be.items[0].count());
    }

    @Test
    void furnaceSmeltCancelKeepsInputAndOutput(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        RecipeManager rm = org.domi.furnace.server.recipe.SyntheticRecipes.manager(tmp);
        int[] asked = {0};
        var level = levelWith(rm, new org.domi.furnace.server.plugin.GameEvents() {
            @Override public ItemStack furnaceSmelt(String dimension, Pos pos, int state, ItemStack source, ItemStack result) { asked[0]++; return null; }
        });
        Pos pos = new Pos(1, -60, 1);
        FurnaceBlockEntity be = FurnaceBlockEntity.furnace(pos, B.defaultState("minecraft:furnace"));
        be.items[0] = org.domi.furnace.server.recipe.SyntheticRecipes.item("raw_iron", 2);
        be.items[1] = org.domi.furnace.server.recipe.SyntheticRecipes.item("coal", 1);
        be.cookingTotalTime = 200;
        for (int i = 0; i < 205; i++) be.tick(level);
        assertTrue(asked[0] >= 1, "다 구워졌을 때 물었다");
        assertEquals(2, be.items[0].count(), "취소하면 재료가 줄지 않는다");
        assertTrue(be.items[2].isEmpty(), "취소하면 결과가 나오지 않는다");
    }
}
