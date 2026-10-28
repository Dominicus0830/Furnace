// 제작기 메뉴 시험(바닐라 CrafterMenu): 칸 배치 46개(0..8 격자·9..44 플레이어·45 결과), 데이터 10개, 결과 미리보기, 결과 칸 읽기 전용,
// 칸 잠금(빈 칸만, 잠긴 칸엔 못 놓음·shift 이동도 건너뜀), 메뉴 번호 crafter_3x3 = 7 과 open_screen 바이트.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.CrafterBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.OpenScreen;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.BlockEntityContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrafterMenuTest {

    /** region 없이 쓰는 시험용 용기: 바닐라 setItem(잠긴 칸이면 먼저 풀기)만 흉내 내고 저장 표시는 생략. */
    static final class TestContainer extends BlockEntityContainer {
        TestContainer(CrafterBlockEntity be) { super(be); }

        @Override public void set(int index, ItemStack stack) {
            ((CrafterBlockEntity) be).beforeSetItem(index, null);
            be.items[index] = stack == null ? ItemStack.EMPTY : stack;
        }

        @Override public ItemStack remove(int index, int amount) {
            ItemStack cur = get(index);
            if (cur.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int n = Math.min(amount, cur.count());
            be.items[index] = cur.withCount(cur.count() - n);
            return cur.withCount(n);
        }

        @Override public void setChanged() { }

        @Override public void startOpen(MenuPlayer p) { }

        @Override public void stopOpen(MenuPlayer p) { }

        @Override public boolean stillValid(MenuPlayer p) { return true; }
    }

    static final class DataRecorder implements MenuSynchronizer {
        final List<String> events = new ArrayList<>();
        int[] initialData;
        @Override public void sendInitialData(AbstractContainerMenu m, List<ItemStack> items, ItemStack carried, int[] data) { initialData = data; }
        @Override public void sendSlotChange(AbstractContainerMenu m, int slot, ItemStack s) { events.add("slot" + slot); }
        @Override public void sendCarriedChange(AbstractContainerMenu m, ItemStack s) { }
        @Override public void sendDataChange(AbstractContainerMenu m, int id, int v) { events.add("data" + id + "=" + v); }
    }

    PlayerInventory inv;
    CraftingMenuTest.Player player;
    CrafterBlockEntity be;
    CrafterMenu menu;

    @BeforeEach
    void setUp() throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        inv = new PlayerInventory();
        player = new CraftingMenuTest.Player(inv, rm);
        be = new CrafterBlockEntity(new Pos(0, 64, 0), 0);
        var invMenu = new InventoryMenu(inv, player);
        menu = new CrafterMenu(1, invMenu.inventoryContainer(), new TestContainer(be), new int[]{0, 64, 0}, player);
    }

    static ItemStack it(String name, int n) { return TestRecipes.item(name, n); }

    void click(int slot, int button, ClickType type) { menu.clicked(slot, button, type, player); }

    @Test
    void layoutAndDataSlotsMatchVanilla() {
        assertEquals("minecraft:crafter_3x3", menu.menuType());
        assertEquals(46, menu.slots.size(), "9 + 27 + 9 + 결과 1");
        assertEquals(CrafterMenu.RESULT_SLOT, 45);
        assertSame(menu.getSlot(0).container, menu.getSlot(8).container);
        assertEquals(8, menu.getSlot(8).containerIndex());
        assertEquals(9, menu.getSlot(9).containerIndex(), "주 인벤토리 첫 칸(인벤토리 번호 9)");
        assertEquals(0, menu.getSlot(36).containerIndex(), "핫바 첫 칸");
        DataRecorder rec = new DataRecorder();
        menu.setSynchronizer(rec);
        assertEquals(10, rec.initialData.length, "ContainerData 10개(잠금 9 + triggered)");
        be.setTriggered(true);
        menu.broadcastChanges();
        assertTrue(rec.events.contains("data9=1"), rec.events.toString());
        assertTrue(menu.isPowered());
    }

    @Test
    void resultPreviewFollowsGridAndIsReadOnly() {
        inv.set(36, it("oak_log", 3));
        click(36, 0, ClickType.PICKUP);
        click(0, 1, ClickType.PICKUP);   // 격자 첫 칸에 하나
        menu.broadcastChanges();
        ItemStack r = menu.getSlot(45).getItem();
        assertEquals(it("oak_planks", 1).itemId(), r.itemId());
        assertEquals(4, r.count());
        click(-999, 0, ClickType.PICKUP);   // 커서 비우기
        click(45, 0, ClickType.PICKUP);
        assertTrue(menu.getCarried().isEmpty(), "결과 칸은 집을 수 없다");
        click(45, 0, ClickType.QUICK_MOVE);
        click(45, 0, ClickType.THROW);
        assertEquals(1, be.items[0].count(), "재료는 줄지 않는다(제작은 신호로만)");
        assertEquals(4, menu.getSlot(45).getItem().count());
        // 격자를 비우면 미리보기도 빈다
        click(0, 0, ClickType.PICKUP);
        menu.broadcastChanges();
        assertTrue(menu.getSlot(45).getItem().isEmpty());
    }

    @Test
    void disabledSlotRejectsPlacementAndQuickMoveSkipsIt() {
        be.setSlotState(0, false, null);   // 서버 handleContainerSlotStateChanged → setSlotState(slot, false) = 잠금
        assertTrue(menu.isSlotDisabled(0));
        DataRecorder rec = new DataRecorder();
        menu.setSynchronizer(rec);
        assertEquals(1, rec.initialData[0]);
        inv.set(36, it("oak_log", 1));
        click(36, 0, ClickType.PICKUP);
        click(0, 0, ClickType.PICKUP);
        assertTrue(be.items[0].isEmpty(), "잠긴 칸엔 못 놓는다");
        assertFalse(menu.getCarried().isEmpty());
        click(36, 0, ClickType.PICKUP);   // 되돌려 놓기
        click(36, 0, ClickType.QUICK_MOVE);
        assertTrue(be.items[0].isEmpty());
        assertFalse(be.items[1].isEmpty(), "shift 이동은 잠긴 칸을 건너 다음 칸으로");
        be.setSlotState(1, false, null);
        assertFalse(be.isSlotDisabled(1), "찬 칸은 잠글 수 없다");
        be.setSlotState(0, true, null);
        menu.broadcastChanges();
        assertTrue(rec.events.contains("data0=0"), rec.events.toString());
        be.setSlotState(42, false, null);   // 범위 밖 번호는 무시
    }

    @Test
    void menuTypeIdAndOpenScreenBytes() {
        assertEquals(7, ItemContext.id("minecraft:menu", "minecraft:crafter_3x3"), "1.21.1 registries.json menu 레지스트리");
        assertEquals(6, ItemContext.id("minecraft:menu", "minecraft:generic_3x3"));
        var title = org.domi.furnace.api.text.Text.translatable("container.crafter");
        ByteBuf buf = Unpooled.buffer();
        new OpenScreen(5, 7, title).write(buf);
        assertEquals(0x33, OpenScreen.ID);
        assertEquals(5, Types.readVarInt(buf));
        assertEquals(7, Types.readVarInt(buf));
        byte[] rest = new byte[buf.readableBytes()];
        buf.readBytes(rest);
        assertArrayEquals(title.networkNbt(), rest);
        buf.release();
    }
}
