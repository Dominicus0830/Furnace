// InventoryMenu / AbstractContainerMenu.doClick 시험 — 바닐라 1.21.1 의 클릭 규칙(디컴파일 원본과 대조)을 칸 상태로 확인한다.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryMenuTest {

    static final int STONE = 1, DIRT = 2, HELMET = 3, SHIELD = 4;

    /** 시험용 플레이어: 던진 것·돌려받은 것을 기록한다. */
    static final class FakePlayer implements MenuPlayer {
        final PlayerInventory inv;
        final List<ItemStack> dropped = new ArrayList<>();
        boolean creative, dead, disconnected;

        FakePlayer(PlayerInventory inv) { this.inv = inv; }

        @Override public boolean isCreative() { return creative; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isDeadOrDying() { return dead; }
        @Override public boolean hasDisconnected() { return disconnected; }
        @Override public void drop(ItemStack stack, boolean includeThrower) { if (!stack.isEmpty()) dropped.add(stack); }
        @Override public void placeItemBackInInventory(ItemStack stack) {
            if (stack.isEmpty()) return;
            ItemStack left = inv.add(stack, false, 64, false);
            if (!left.isEmpty()) drop(left, false);
        }
        @Override public ItemStack addToInventory(ItemStack stack) { return inv.add(stack, false, 64, false); }
        @Override public ItemStack inventoryItem(int i) { return inv.get(PlayerInventory.windowSlot(i)); }
        @Override public void setInventoryItem(int i, ItemStack s) { inv.set(PlayerInventory.windowSlot(i), s); }
        @Override public EquipSlot equipmentSlotFor(ItemStack s) {
            if (s.itemId() == HELMET) return EquipSlot.HEAD;
            if (s.itemId() == SHIELD) return EquipSlot.OFFHAND;
            return EquipSlot.MAINHAND;
        }
        @Override public boolean hasBindingCurse(ItemStack s) { return false; }
    }

    /** 보낸 패킷을 기록하는 동기기. */
    static final class Recorder implements MenuSynchronizer {
        final List<String> events = new ArrayList<>();

        @Override public void sendInitialData(AbstractContainerMenu m, List<ItemStack> items, ItemStack carried, int[] data) {
            events.add("init#" + m.incrementStateId());
        }
        @Override public void sendSlotChange(AbstractContainerMenu m, int slot, ItemStack s) { events.add("slot" + slot + "=" + s.count() + "#" + m.incrementStateId()); }
        @Override public void sendCarriedChange(AbstractContainerMenu m, ItemStack s) { events.add("carried=" + s.count() + "#" + m.incrementStateId()); }
        @Override public void sendDataChange(AbstractContainerMenu m, int id, int v) { events.add("data" + id + "=" + v); }
    }

    PlayerInventory inv;
    FakePlayer player;
    InventoryMenu menu;

    @BeforeEach
    void setUp() {
        inv = new PlayerInventory();
        player = new FakePlayer(inv);
        menu = new InventoryMenu(inv, player);
    }

    static ItemStack st(int id, int n) { return new ItemStack(id, n); }

    // 창 칸 번호: 9..35 주, 36..44 핫바
    void click(int slot, int button, ClickType type) { menu.clicked(slot, button, type, player); }

    @Test
    void pickupPrimaryMovesWholeStackToCursorAndBack() {
        inv.set(10, st(STONE, 30));
        click(10, 0, ClickType.PICKUP);
        assertEquals(st(STONE, 30), menu.getCarried());
        assertTrue(inv.get(10).isEmpty());
        click(12, 0, ClickType.PICKUP);
        assertTrue(menu.getCarried().isEmpty());
        assertEquals(st(STONE, 30), inv.get(12));
    }

    @Test
    void pickupSecondaryTakesHalfRoundedUpAndPlacesOne() {
        inv.set(10, st(STONE, 5));
        click(10, 1, ClickType.PICKUP);
        assertEquals(3, menu.getCarried().count());
        assertEquals(2, inv.get(10).count());
        click(11, 1, ClickType.PICKUP);   // 빈 칸에 하나
        assertEquals(1, inv.get(11).count());
        assertEquals(2, menu.getCarried().count());
    }

    @Test
    void pickupSwapsDifferentItemsAndMergesSameItems() {
        inv.set(10, st(STONE, 10));
        inv.set(11, st(DIRT, 4));
        click(10, 0, ClickType.PICKUP);
        click(11, 0, ClickType.PICKUP);   // 다른 아이템 → 맞바꿈
        assertEquals(st(STONE, 10), inv.get(11));
        assertEquals(st(DIRT, 4), menu.getCarried());
        inv.set(12, st(DIRT, 62));
        click(12, 0, ClickType.PICKUP);   // 같은 아이템 → 64까지만 합치고 나머지는 커서
        assertEquals(64, inv.get(12).count());
        assertEquals(2, menu.getCarried().count());
    }

    @Test
    void pickupOnFullSlotOfSameItemChangesNothing() {
        inv.set(10, st(STONE, 40));
        click(10, 0, ClickType.PICKUP);   // 커서 40
        inv.set(11, st(STONE, 64));
        click(11, 0, ClickType.PICKUP);   // 칸이 꽉 차서 safeInsert 가 0개 넣는다(바닐라와 같다)
        assertEquals(40, menu.getCarried().count());
        assertEquals(64, inv.get(11).count());
    }

    @Test
    void outsideClickDropsCursor() {
        inv.set(10, st(STONE, 6));
        click(10, 0, ClickType.PICKUP);
        click(-999, 1, ClickType.PICKUP);   // 하나 버리기
        assertEquals(List.of(st(STONE, 1)), player.dropped);
        assertEquals(5, menu.getCarried().count());
        click(-999, 0, ClickType.PICKUP);   // 전부 버리기
        assertEquals(st(STONE, 5), player.dropped.get(1));
        assertTrue(menu.getCarried().isEmpty());
    }

    @Test
    void throwDropsOneOrAll() {
        inv.set(40, st(STONE, 7));
        click(40, 0, ClickType.THROW);
        assertEquals(st(STONE, 1), player.dropped.get(0));
        assertEquals(6, inv.get(40).count());
        click(40, 1, ClickType.THROW);
        assertEquals(st(STONE, 6), player.dropped.get(1));
        assertTrue(inv.get(40).isEmpty());
    }

    @Test
    void swapWithHotbarKey() {
        inv.set(10, st(STONE, 3));           // 주 인벤 칸
        inv.set(36, st(DIRT, 5));            // 핫바 0
        click(10, 0, ClickType.SWAP);
        assertEquals(st(DIRT, 5), inv.get(10));
        assertEquals(st(STONE, 3), inv.get(36));
    }

    @Test
    void swapWithOffhandKey() {
        inv.set(10, st(STONE, 3));
        click(10, 40, ClickType.SWAP);
        assertEquals(st(STONE, 3), inv.get(45));
        assertTrue(inv.get(10).isEmpty());
    }

    @Test
    void quickMoveBetweenMainAndHotbar() {
        inv.set(10, st(STONE, 20));
        click(10, 0, ClickType.QUICK_MOVE);
        assertTrue(inv.get(10).isEmpty());
        assertEquals(st(STONE, 20), inv.get(36));     // 핫바 첫 빈 칸
        click(36, 0, ClickType.QUICK_MOVE);
        assertEquals(st(STONE, 20), inv.get(9));      // 주 인벤 첫 빈 칸
    }

    @Test
    void quickMoveMergesIntoExistingStackFirst() {
        inv.set(36, st(STONE, 60));
        inv.set(10, st(STONE, 10));
        click(10, 0, ClickType.QUICK_MOVE);
        assertEquals(64, inv.get(36).count());
        assertEquals(6, inv.get(37).count());
    }

    @Test
    void quickMoveArmorToArmorSlotAndShieldToOffhand() {
        inv.set(10, st(HELMET, 1));
        click(10, 0, ClickType.QUICK_MOVE);
        assertEquals(st(HELMET, 1), inv.get(5));      // 머리 칸
        inv.set(11, st(SHIELD, 1));
        click(11, 0, ClickType.QUICK_MOVE);
        assertEquals(st(SHIELD, 1), inv.get(45));
    }

    @Test
    void armorSlotRejectsWrongItem() {
        inv.set(10, st(STONE, 1));
        click(10, 0, ClickType.PICKUP);
        click(5, 0, ClickType.PICKUP);   // 돌은 머리 칸에 못 놓는다
        assertTrue(inv.get(5).isEmpty());
        assertEquals(st(STONE, 1), menu.getCarried());
    }

    @Test
    void quickCraftChitableSplitsEvenly() {
        inv.set(10, st(STONE, 10));
        click(10, 0, ClickType.PICKUP);
        click(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT);
        click(11, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT);
        click(12, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT);
        click(13, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT);
        click(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT);
        assertEquals(3, inv.get(11).count());
        assertEquals(3, inv.get(12).count());
        assertEquals(3, inv.get(13).count());
        assertEquals(1, menu.getCarried().count());
    }

    @Test
    void quickCraftGreedyPlacesOneEach() {
        inv.set(10, st(STONE, 10));
        click(10, 0, ClickType.PICKUP);
        click(-999, AbstractContainerMenu.getQuickcraftMask(0, 1), ClickType.QUICK_CRAFT);
        click(11, AbstractContainerMenu.getQuickcraftMask(1, 1), ClickType.QUICK_CRAFT);
        click(12, AbstractContainerMenu.getQuickcraftMask(1, 1), ClickType.QUICK_CRAFT);
        click(-999, AbstractContainerMenu.getQuickcraftMask(2, 1), ClickType.QUICK_CRAFT);
        assertEquals(1, inv.get(11).count());
        assertEquals(1, inv.get(12).count());
        assertEquals(8, menu.getCarried().count());
    }

    @Test
    void quickCraftSingleSlotActsLikePickupClick() {
        inv.set(10, st(STONE, 10));
        click(10, 0, ClickType.PICKUP);
        click(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT);
        click(11, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT);
        click(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT);
        assertEquals(st(STONE, 10), inv.get(11));
        assertTrue(menu.getCarried().isEmpty());
    }

    @Test
    void quickCraftCloneTypeNeedsCreative() {
        inv.set(10, st(STONE, 10));
        click(10, 0, ClickType.PICKUP);
        click(-999, AbstractContainerMenu.getQuickcraftMask(0, 2), ClickType.QUICK_CRAFT);
        click(11, AbstractContainerMenu.getQuickcraftMask(1, 2), ClickType.QUICK_CRAFT);
        click(12, AbstractContainerMenu.getQuickcraftMask(1, 2), ClickType.QUICK_CRAFT);
        click(-999, AbstractContainerMenu.getQuickcraftMask(2, 2), ClickType.QUICK_CRAFT);
        assertTrue(inv.get(11).isEmpty());   // 서바이벌: 시작이 거부돼 아무 일도 없다
        assertEquals(10, menu.getCarried().count());
        player.creative = true;
        click(-999, AbstractContainerMenu.getQuickcraftMask(0, 2), ClickType.QUICK_CRAFT);
        click(11, AbstractContainerMenu.getQuickcraftMask(1, 2), ClickType.QUICK_CRAFT);
        click(12, AbstractContainerMenu.getQuickcraftMask(1, 2), ClickType.QUICK_CRAFT);
        click(-999, AbstractContainerMenu.getQuickcraftMask(2, 2), ClickType.QUICK_CRAFT);
        assertEquals(64, inv.get(11).count());
        assertEquals(64, inv.get(12).count());
    }

    @Test
    void pickupAllGathersSameItemIntoCursor() {
        inv.set(10, st(STONE, 10));
        inv.set(11, st(STONE, 20));
        inv.set(12, st(DIRT, 5));
        inv.set(13, st(STONE, 3));
        click(13, 0, ClickType.PICKUP);   // 커서 3
        click(14, 0, ClickType.PICKUP);   // 빈 칸에 놓고
        click(14, 0, ClickType.PICKUP);   // 다시 집어 커서를 채운 상태에서
        click(15, 0, ClickType.PICKUP_ALL);   // 빈 칸 위 더블클릭 → 같은 아이템을 모두 모은다
        assertEquals(33, menu.getCarried().count());
        assertTrue(inv.get(10).isEmpty());
        assertTrue(inv.get(11).isEmpty());
        assertEquals(st(DIRT, 5), inv.get(12));
    }

    @Test
    void cloneNeedsCreativeAndEmptyCursor() {
        inv.set(10, st(STONE, 2));
        click(10, 2, ClickType.CLONE);
        assertTrue(menu.getCarried().isEmpty());
        player.creative = true;
        click(10, 2, ClickType.CLONE);
        assertEquals(st(STONE, 64), menu.getCarried());
        assertEquals(st(STONE, 2), inv.get(10));
    }

    @Test
    void removedReturnsCursorAndCraftGridToInventory() {
        inv.set(1, st(STONE, 3));   // 제작 격자
        inv.set(10, st(DIRT, 2));
        click(10, 0, ClickType.PICKUP);   // 커서에 흙
        menu.removed(player);
        assertTrue(menu.getCarried().isEmpty());
        assertTrue(inv.get(1).isEmpty());
        assertEquals(5, countOf(STONE) + countOf(DIRT));
        assertTrue(player.dropped.isEmpty());
    }

    @Test
    void removedDropsEverythingWhenDead() {
        inv.set(1, st(STONE, 3));
        inv.set(10, st(DIRT, 2));
        click(10, 0, ClickType.PICKUP);
        player.dead = true;
        menu.removed(player);
        assertEquals(2, player.dropped.size());
    }

    int countOf(int id) {
        int n = 0;
        for (ItemStack s : inv.snapshot()) if (s.itemId() == id) n += s.count();
        return n;
    }

    // ------------------------------------------------------------------ 동기

    @Test
    void syncSendsInitialThenOnlyChangedSlotsWithIncrementingStateId() {
        Recorder rec = new Recorder();
        menu.setSynchronizer(rec);
        assertEquals(List.of("init#1"), rec.events);
        rec.events.clear();
        inv.set(10, st(STONE, 5));
        menu.broadcastChanges();
        assertEquals(List.of("slot10=5#2"), rec.events);
        rec.events.clear();
        menu.broadcastChanges();
        assertTrue(rec.events.isEmpty(), "바뀐 게 없으면 아무것도 보내지 않는다");
    }

    @Test
    void clickWithSuppressedUpdatesThenRemoteAcceptedSendsNothing() {
        Recorder rec = new Recorder();
        menu.setSynchronizer(rec);
        rec.events.clear();
        inv.set(10, st(STONE, 5));
        menu.broadcastChanges();
        rec.events.clear();
        // 클라이언트가 예측한 대로의 클릭: 바닐라 handleContainerClick 의 순서
        menu.suppressRemoteUpdates();
        click(10, 0, ClickType.PICKUP);
        menu.setRemoteSlotNoCopy(10, ItemStack.EMPTY);
        menu.setRemoteCarried(st(STONE, 5));
        menu.resumeRemoteUpdates();
        menu.broadcastChanges();
        assertTrue(rec.events.isEmpty(), "서버 결과가 클라이언트 예측과 같으면 추가 패킷이 없다: " + rec.events);
    }

    @Test
    void carriedChangeIsSentAsSlotMinusOneWithIncrementedStateId() {
        Recorder rec = new Recorder();
        menu.setSynchronizer(rec);
        rec.events.clear();
        inv.set(10, st(STONE, 5));
        menu.broadcastChanges();
        rec.events.clear();
        click(10, 0, ClickType.PICKUP);
        menu.broadcastChanges();
        assertEquals(List.of("slot10=0#3", "carried=5#4"), rec.events);
        assertFalse(menu.getCarried().isEmpty());
    }

    @Test
    void stateIdWrapsAt32768() {
        for (int i = 0; i < 32767; i++) menu.incrementStateId();
        assertEquals(32767, menu.getStateId());
        assertEquals(0, menu.incrementStateId());
    }
}
