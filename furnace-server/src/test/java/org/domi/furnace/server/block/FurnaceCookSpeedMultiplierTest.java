// 화로 굽기 속도 배율(Paper Implement-furnace-cook-speed-multiplier-API) 대조 — PR #381 독립 검토 시험을 옮겼다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.domi.furnace.server.block.entity.FurnaceBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;

class FurnaceCookSpeedMultiplierTest {

    static ItemStack item(String name, int n) { return TestRecipes.item(name, n); }

    private record Rig(RecipeManager rm, BlockPlacementTest.TestLevel level, FurnaceBlockEntity be) {}

    private static Rig rig(String block) throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var level = new BlockPlacementTest.TestLevel() {
            @Override public Object recipes() { return rm; }
        };
        Pos pos = new Pos(1, -60, 1);
        int off = reg.stateIdOrAir("minecraft:" + block, java.util.Map.of("facing", "north", "lit", "false"));
        level.setBlock(pos, off, 3);
        FurnaceBlockEntity be = switch (block) {
            case "blast_furnace" -> FurnaceBlockEntity.blastFurnace(pos, off);
            case "smoker" -> FurnaceBlockEntity.smoker(pos, off);
            default -> FurnaceBlockEntity.furnace(pos, off);
        };
        return new Rig(rm, level, be);
    }

    private static void setInput(Rig r, ItemStack s) {
        ItemStack old = r.be().items[0];
        r.be().items[0] = s;
        r.be().onItemSet(0, old, s, r.level());
    }

    /**
     * R1 재현: 플러그인이 배율을 0.5(느리게)로 두었다가 1.0 으로 되돌린다(CraftFurnace#setCookSpeedMultiplier(1.0) → update 와 같은 NBT).
     * 진행 300 > 새 총 시간 200 인데 배율이 1.0 이라 바닐라 == 비교만 남아 화로가 영영 굽지 않는다. Paper(>=)는 다음 틱에 굽는다.
     */
    @Test
    void restoringMultiplierToOneDoesNotStickFurnace() throws Exception {
        Rig r = rig("furnace");
        var be = r.be();
        be.cookSpeedMultiplier = 0.5;
        setInput(r, item("raw_iron", 2));
        assertEquals(400, be.cookingTotalTime, "ceil(200 / 0.5)");
        be.items[1] = item("coal", 1);
        for (int i = 0; i < 300; i++) be.tick(r.level());
        assertEquals(300, be.cookingProgress);
        // 플러그인: setCookSpeedMultiplier(1.0) → 사본 총 시간 200, update 로 키 지움
        var tag = be.saveWithoutMetadata();
        tag.tags().remove("Paper.CookSpeedMultiplier");
        tag.putShort("CookTimeTotal", (short) 200);
        be.load(tag);
        assertEquals(1.0, be.cookSpeedMultiplier);
        be.tick(r.level());
        assertEquals("minecraft:iron_ingot", ItemTools.itemName(be.items[2]), "배율이 바뀐 화로는 다음 굽기까지 > 도 완료(Paper 는 늘 >=)");
    }

    /** 키 없는 NBT 를 다시 실으면 배율이 1.0 으로 돌아온다(Paper 는 키가 늘 있어 같은 결과). */
    @Test
    void reloadWithoutKeyResetsMultiplier() throws Exception {
        Rig r = rig("furnace");
        var be = r.be();
        var tag = be.saveWithoutMetadata();
        tag.putDouble("Paper.CookSpeedMultiplier", 4.0);
        be.load(tag);
        assertEquals(4.0, be.cookSpeedMultiplier);
        tag.tags().remove("Paper.CookSpeedMultiplier");
        be.load(tag);
        assertEquals(1.0, be.cookSpeedMultiplier);
        // 정수 꼴 키(/data 로 2 를 적음)도 Paper getDouble 처럼 숫자로 읽는다
        tag.putInt("Paper.CookSpeedMultiplier", 2);
        be.load(tag);
        assertEquals(2.0, be.cookSpeedMultiplier);
    }

    /** 굽기를 마친 뒤 다시 잰 총 시간에도 배율이 걸린다(Paper getTotalCookTime(…, multiplier)). 용광로 레시피 시간 100 기준. */
    @Test
    void blastFurnaceAndPostCompletionTotalUseMultiplier() throws Exception {
        Rig r = rig("blast_furnace");
        var be = r.be();
        be.cookSpeedMultiplier = 3.0;
        setInput(r, item("raw_iron", 3));
        assertEquals(34, be.cookingTotalTime, "ceil(100 / 3)");
        be.items[1] = item("coal", 1);
        for (int i = 0; i < 34; i++) be.tick(r.level());
        assertEquals(1, be.items[2].count());
        assertEquals(34, be.cookingTotalTime, "마친 뒤 다시 잰 값도 배율 적용");
        assertEquals(0, be.cookingProgress);
        for (int i = 0; i < 34; i++) be.tick(r.level());
        assertEquals(2, be.items[2].count());
    }

    /** 배율 1.0 의 저장 키 집합은 바닐라와 같다(Paper.* 키 없음). 배율 2 면 double 로 적는다. */
    @Test
    void savedKeysMatchVanillaAtOne() throws Exception {
        Rig r = rig("smoker");
        var be = r.be();
        setInput(r, item("beef", 1));
        assertEquals(100, be.cookingTotalTime);
        assertEquals(java.util.Set.of("BurnTime", "CookTime", "CookTimeTotal", "Items", "RecipesUsed"),
                be.saveWithoutMetadata().tags().keySet());
        be.cookSpeedMultiplier = 2.0;
        assertEquals(2.0, be.saveWithoutMetadata().getDouble("Paper.CookSpeedMultiplier"));
    }

    /** 배율이 늘 1.0 인 화로에 /data 로 진행 > 총 시간을 적으면 바닐라처럼 멈춘다(T141E2e-D1 결정 B+: 서버는 바닐라 그대로, 플러그인 쓰기만 호환 계층이 맞춘다). */
    @Test
    void vanillaOvershootAtOneStaysLikeVanilla() throws Exception {
        Rig r = rig("furnace");
        var be = r.be();
        setInput(r, item("raw_iron", 2));
        be.items[1] = item("coal", 1);
        be.tick(r.level());
        var tag = be.saveWithoutMetadata();
        tag.putShort("CookTime", (short) 250);
        be.load(tag);
        for (int i = 0; i < 5; i++) be.tick(r.level());
        assertTrue(be.items[2].isEmpty(), "바닐라 == 비교: 진행이 총 시간을 넘으면 굽지 않는다");
    }

    /** T141E2e-D1 B+: 호환 CraftFurnace 는 플러그인의 진행 ≥ 총 시간을 총 시간 − 1 로 적는다 — 그 NBT 면 배율 1.0(바닐라 ==)에서도 다음 틱에 굽는다. */
    @Test
    void compatClampedOvershootCooksNextTick() throws Exception {
        Rig r = rig("furnace");
        var be = r.be();
        setInput(r, item("raw_iron", 2));
        be.items[1] = item("coal", 1);
        be.tick(r.level());
        var tag = be.saveWithoutMetadata();
        tag.putShort("CookTime", (short) 199);   // 플러그인이 250 을 적은 update 결과
        be.load(tag);
        be.tick(r.level());
        assertEquals("minecraft:iron_ingot", ItemTools.itemName(be.items[2]));
        assertEquals(0, be.cookingProgress);
    }
}
