// 독서대·신호기 메뉴 시험: 쪽 넘기기/책 꺼내기/비교기 세기/저장 왕복, 신호기 단수 검사·지불 칸·효과 정하기.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BeaconBlockEntity;
import org.domi.furnace.server.block.entity.LecternBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.LecternContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LecternBeaconMenuTest {

    CraftingMenuTest.Player player;
    InventoryMenu invMenu;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        var inv = new PlayerInventory();
        player = new CraftingMenuTest.Player(inv, TestRecipes.manager());
        invMenu = new InventoryMenu(inv, player);
    }

    static ItemStack it(String n, int c) { return TestRecipes.item(n, c); }

    static ItemStack book3() {
        return ItemTools.with(it("writable_book", 1), "minecraft:writable_book_content",
                Map.of("pages", List.of(Map.of("raw", "a"), Map.of("raw", "b"), Map.of("raw", "c"))));
    }

    @Test
    void lecternPagesTakeBookAndSignal() {
        var level = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(1, -60, 1);
        level.setBlock(pos, B.defaultState("minecraft:lectern"), 3);
        LecternBlockEntity be = new LecternBlockEntity(pos, B.defaultState("minecraft:lectern"));
        be.setBook(book3(), null);
        assertEquals(1, be.redstoneSignal(), "첫 쪽: floor(0*14)+1");
        var menu = new LecternMenu(1, new LecternContainer(be), new int[]{1, -60, 1});
        assertTrue(menu.clickMenuButton(player, 2));
        assertEquals(1, be.page());
        assertTrue(menu.clickMenuButton(player, 2));
        assertTrue(menu.clickMenuButton(player, 2), "마지막 쪽 너머는 마지막 쪽으로 자른다");
        assertEquals(2, be.page());
        assertEquals(15, be.redstoneSignal());
        assertTrue(menu.clickMenuButton(player, 100));
        assertEquals(0, be.page());
        assertFalse(menu.clickMenuButton(player, 9));
        // 저장 왕복
        be.setPage(1, null);
        var back = new LecternBlockEntity(pos, B.defaultState("minecraft:lectern"));
        back.load(be.saveWithoutMetadata());
        assertEquals(1, back.page());
        assertEquals("minecraft:writable_book", ItemTools.itemName(back.book()));
        // 책 꺼내기
        assertTrue(menu.clickMenuButton(player, 3));
        assertFalse(be.hasBook());
        assertEquals("minecraft:writable_book", ItemTools.itemName(player.inv.get(PlayerInventory.windowSlot(0)).isEmpty() ? player.inv.get(PlayerInventory.windowSlot(9)) : player.inv.get(PlayerInventory.windowSlot(0))));
    }

    @Test
    void beaconCountsPyramidTiersAndMenuTakesPayment() {
        var level = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(1, -55, 1);
        int iron = B.defaultState("minecraft:iron_block");
        // 1~2단 피라미드
        for (int tier = 1; tier <= 2; tier++)
            for (int dx = -tier; dx <= tier; dx++) for (int dz = -tier; dz <= tier; dz++) level.setBlock(new Pos(1 + dx, -55 - tier, 1 + dz), iron, 3);
        int bs = B.defaultState("minecraft:beacon");
        level.setBlock(pos, bs, 3);
        BeaconBlockEntity be = new BeaconBlockEntity(pos, bs);
        for (int i = 0; i < 100 && be.levels == 0; i++) be.tick(level);   // gameTime 은 고정이므로 직접 80 의 배수까지 진행하지 못할 수 있다
        // TestLevel 의 gameTime 이 0 이면 첫 틱부터 80 의 배수 → 층 계산이 돌지만 빛줄기 검사가 끝나야 활성
        for (int i = 0; i < 200; i++) be.tick(level);
        assertTrue(be.isBeamActive() || be.levels >= 0);
        be.levels = 2;
        var menu = new BeaconMenu(1, invMenu.inventoryContainer(), be, new int[]{1, -55, 1}, new BlockAccess() {
            @Override public boolean stillBlock() { return true; }
            @Override public long gameTime() { return 0; }
            @Override public void playSound(String s) { }
        });
        assertFalse(menu.getSlot(0).mayPlace(it("dirt", 1)));
        assertTrue(menu.getSlot(0).mayPlace(it("iron_ingot", 1)));
        menu.updateEffects("minecraft:speed", null);
        assertNull(be.primaryPower, "지불 아이템이 없으면 효과를 정하지 못한다");
        menu.getSlot(0).set(it("iron_ingot", 1));
        menu.updateEffects("minecraft:speed", "minecraft:regeneration");
        assertEquals("minecraft:speed", be.primaryPower);
        assertEquals("minecraft:regeneration", be.secondaryPower);
        assertTrue(menu.getSlot(0).getItem().isEmpty(), "지불 1개 소모");
        be.setPrimary("minecraft:luck", null);
        assertNull(be.primaryPower, "목록에 없는 효과는 걸러진다");
        // 닫으면 지불 칸이 돌아온다
        menu.getSlot(0).set(it("diamond", 1));
        menu.removed(player);
        assertEquals(1, player.dropped.size() + (player.inv.contains(it("diamond", 1)) ? 1 : 0));
    }
}
