// PR #85(M3) 사후 검토에서 찾은 차이의 회귀 시험(바닐라 1.21.1 바이트코드 대조): 감지 레일 비교기·깔때기 수레의 전리품 표 푸는 시점,
// /item·/loot 의 용기 엔티티·말 칸.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.command.ItemSlots;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContainerEntityReviewM3Test {

    @BeforeAll
    static void install() { ContainerEntityParityTest.install(); }

    private static void tick(Entity e) {
        e.setOldPosAndRot();
        e.tickCount++;
        e.tick();
    }

    /**
     * 바닐라 DetectorRailBlock.getAnalogOutputSignal → AbstractContainerMenu.getRedstoneSignalFromContainer(cart)
     * → cart.getItem(i) → unpackChestVehicleLootTable(null). 열지 않은 상자 수레가 감지 레일에 오면 표가 풀리고 비교기 신호는 전리품 기준.
     */
    @Test
    void detectorRailComparatorUnpacksLootTableCart() {
        var level = new ContainerEntityParityTest.Lvl();
        Pos p = new Pos(0, -60, 0);
        int rail = B.prop("powered").with(B.defaultState("detector_rail"), true);
        level.setBlock(p, rail, 2);
        var cart = (MinecartChest) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.CHEST);
        level.addFreshEntity(cart);
        cart.setLootTable("minecraft:chests/abandoned_mineshaft", 7L);
        int signal = BlockBehaviors.of(rail).getAnalogOutputSignal(rail, level, p);
        assertEquals(1, level.unpacked.size(), "비교기가 읽기 전에 표를 푼다(바닐라 getItem)");
        assertTrue(signal > 0, "풀린 다이아몬드 3개 기준 신호: " + signal);
    }

    /**
     * 바닐라 HopperBlockEntity.suckInItems(level, hopper): 위 용기에서 꺼낼 것이 있거나 아이템 엔티티를 넣을 때만 hopper.getItem 을 부른다.
     * 빨아들일 것이 없는 깔때기 수레의 자기 전리품 표는 그대로 남아야 한다.
     */
    @Test
    void idleHopperMinecartKeepsItsOwnLootTable() {
        var level = new ContainerEntityParityTest.Lvl();
        var cart = (MinecartHopper) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.HOPPER);
        level.addFreshEntity(cart);
        cart.setLootTable("minecraft:chests/abandoned_mineshaft", 7L);
        tick(cart);
        assertEquals(0, level.unpacked.size(), "넣을 것이 없으면 깔때기 수레의 표를 풀지 않는다");
        assertNotNull(cart.lootTable());
    }

    /** getContainerSize·isChestVehicleEmpty 는 표를 풀지 않는다(Paper CraftInventory.getSize/isEmpty 도 그대로). */
    @Test
    void containerSizeAndIsEmptyDoNotUnpack() {
        var level = new ContainerEntityParityTest.Lvl();
        var cart = (MinecartChest) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.CHEST);
        level.addFreshEntity(cart);
        cart.setLootTable("minecraft:chests/abandoned_mineshaft", 7L);
        var c = new org.domi.furnace.server.world.EntityContainer(cart);
        assertEquals(27, c.size());
        assertTrue(c.isEmpty());
        assertEquals(0, level.unpacked.size());
        assertEquals(3, c.get(0).count(), "칸을 읽으면 그때 푼다");
        assertFalse(c.isEmpty());
    }

    /** PR 이 말한 "깔때기가 빨아들일 때 표를 푼다" — 시험이 없던 경로(긍정 확인). */
    @Test
    void hopperMinecartUnderLootCartUnpacksAndPulls() {
        var level = new ContainerEntityParityTest.Lvl();
        var hopper = (MinecartHopper) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.HOPPER);
        level.addFreshEntity(hopper);
        var chest = (MinecartChest) AbstractMinecart.create(level, 0.5, -59, 0.5, AbstractMinecart.Type.CHEST);
        level.addFreshEntity(chest);
        chest.setLootTable("minecraft:chests/abandoned_mineshaft", 7L);
        assertTrue(hopper.suckInItems());
        assertNull(chest.lootTable());
        assertEquals(2, chest.getItem(0).count());
        assertEquals(1, hopper.getItem(0).count());
    }

    /** 바닐라 AbstractMinecartContainer.getSlot(n) = getChestVehicleSlot(n): /item replace entity @e[type=chest_minecart] container.0 이 된다. */
    @Test
    void itemCommandSlotOnChestMinecart() {
        var level = new ContainerEntityParityTest.Lvl();
        var cart = (MinecartChest) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.CHEST);
        level.addFreshEntity(cart);
        assertNotNull(ItemSlots.of(cart, 0), "container.0");
        assertNotNull(ItemSlots.of(new ChestBoat(level, level.nextEntityId()), 26), "상자 배 container.26");
        assertNull(ItemSlots.of(cart, 27), "27칸 밖");
        assertNull(ItemSlots.of(cart, 98), "수레에는 장비 칸이 없다");
    }

    /** getChestVehicleSlot.get/set → unpackChestVehicleLootTable(null) 뒤 칸. set 은 칸 최대 개수로 자른다. */
    @Test
    void itemCommandSlotUnpacksLootFirst() {
        var level = new ContainerEntityParityTest.Lvl();
        var cart = (MinecartChest) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.CHEST);
        level.addFreshEntity(cart);
        cart.setLootTable("minecraft:chests/abandoned_mineshaft", 7L);
        var slot1 = ItemSlots.of(cart, 1);
        assertTrue(slot1.set(ContainerEntityParityTest.item("minecraft:ender_pearl", 40)));
        assertEquals(1, level.unpacked.size(), "칸에 쓰기 전에 표를 푼다(아니면 저장 때 표만 남아 넣은 것이 사라진다)");
        assertEquals(3, cart.getItem(0).count(), "풀린 전리품");
        assertEquals(16, cart.getItem(1).count(), "진주 최대 16");
        assertNull(cart.lootTable());
    }

    /** 바닐라 AbstractHorse.getSlot(400) 안장, AbstractChestedHorse.getSlot(499) 상자, 500+ 인벤토리. */
    @Test
    void itemCommandSlotOnHorseInventory() {
        var level = new ContainerEntityParityTest.Lvl();
        assertNotNull(ItemSlots.of(new Horse(level, level.nextEntityId()), 400), "horse.saddle");
        assertNotNull(ItemSlots.of(new Llama(level, level.nextEntityId()), 499), "horse.chest");
        assertNull(ItemSlots.of(new Horse(level, level.nextEntityId()), 499), "상자를 못 다는 말");
        assertNull(ItemSlots.of(new Horse(level, level.nextEntityId()), 500), "말 인벤토리는 안장 칸뿐");
        assertNotNull(ItemSlots.of(new Horse(level, level.nextEntityId()), 105), "armor.body 는 장비로");
    }

    @Test
    void horseSaddleSlotAcceptsOnlySaddle() {
        var level = new ContainerEntityParityTest.Lvl();
        var horse = new Horse(level, level.nextEntityId());
        var saddle = ItemSlots.of(horse, 400);
        assertFalse(saddle.set(ContainerEntityParityTest.item("minecraft:stone", 1)));
        assertFalse(horse.isSaddled());
        assertTrue(saddle.set(ContainerEntityParityTest.item("minecraft:saddle", 1)));
        assertTrue(horse.isSaddled(), "안장 상태 플래그가 맞춰진다");
        assertTrue(saddle.set(ItemStack.EMPTY));
        assertFalse(horse.isSaddled());
    }

    @Test
    void donkeyChestSlotAddsAndRemovesChest() {
        var level = new ContainerEntityParityTest.Lvl();
        var donkey = new Donkey(level, level.nextEntityId());
        assertNull(ItemSlots.of(donkey, 500), "상자 없으면 horse.0 없음");
        var chest = ItemSlots.of(donkey, 499);
        assertTrue(chest.get().isEmpty());
        assertFalse(chest.set(ContainerEntityParityTest.item("minecraft:stone", 1)));
        assertTrue(chest.set(ContainerEntityParityTest.item("minecraft:chest", 1)));
        assertTrue(donkey.hasChest());
        assertEquals("minecraft:chest", org.domi.furnace.server.item.ItemTools.itemName(chest.get()));
        var s0 = ItemSlots.of(donkey, 500);
        assertNotNull(s0, "상자를 달면 horse.0..14");
        assertNotNull(ItemSlots.of(donkey, 514));
        assertNull(ItemSlots.of(donkey, 515));
        assertTrue(s0.set(ContainerEntityParityTest.item("minecraft:ender_pearl", 40)));
        assertEquals(16, s0.get().count());
        assertTrue(chest.set(ItemStack.EMPTY));
        assertFalse(donkey.hasChest());
        assertNull(ItemSlots.of(donkey, 500), "상자를 떼면 칸이 사라진다(바닐라처럼 내용물도)");
    }
}
