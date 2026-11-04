// 엔티티 소유 창: 말 인벤토리(HorseInventoryMenu 칸 규칙·shift-클릭·유효성), 말 갑옷 장착, horse_screen_open 바이트,
// 상자 배·상자/깔때기 수레 우클릭으로 창 열기, 엔티티 용기 저장 왕복, 클릭 뒤 변경 동기화.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.menu.AbstractContainerMenu;
import org.domi.furnace.server.menu.ChestMenu;
import org.domi.furnace.server.menu.ClickType;
import org.domi.furnace.server.menu.Container;
import org.domi.furnace.server.menu.EquipSlot;
import org.domi.furnace.server.menu.HopperMenu;
import org.domi.furnace.server.menu.HorseInventoryMenu;
import org.domi.furnace.server.menu.MenuPlayer;
import org.domi.furnace.server.menu.MenuSynchronizer;
import org.domi.furnace.server.menu.PlayerInventoryContainer;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.HorseScreenOpen;
import org.domi.furnace.server.world.EntityContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityInventoryMenuTest {

    @BeforeEach
    void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static ItemStack item(String name, int count) {
        return new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", name), count);
    }

    /** 시험용 메뉴 플레이어: 사거리 판정을 바꿀 수 있다. */
    static final class FakePlayer implements MenuPlayer {
        final PlayerInventory inv;
        boolean creative, inRange = true;
        Object vehicle;

        FakePlayer(PlayerInventory inv) { this.inv = inv; }

        @Override public boolean isCreative() { return creative; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isDeadOrDying() { return false; }
        @Override public boolean hasDisconnected() { return false; }
        @Override public void drop(ItemStack stack, boolean includeThrower) { }
        @Override public void placeItemBackInInventory(ItemStack stack) { inv.add(stack, false, 64, false); }
        @Override public ItemStack addToInventory(ItemStack stack) { return inv.add(stack, false, 64, false); }
        @Override public ItemStack inventoryItem(int i) { return inv.get(PlayerInventory.windowSlot(i)); }
        @Override public void setInventoryItem(int i, ItemStack s) { inv.set(PlayerInventory.windowSlot(i), s); }
        @Override public EquipSlot equipmentSlotFor(ItemStack s) { return EquipSlot.MAINHAND; }
        @Override public boolean hasBindingCurse(ItemStack s) { return false; }
        @Override public double distanceToSqr(double x, double y, double z) { return 0; }
        @Override public boolean canInteractWithEntity(AABB box, double buffer) { return inRange; }
        @Override public boolean isRiding(Object e) { return vehicle == e; }
    }

    /** 보낸 칸 변경을 기록한다. */
    static final class Recorder implements MenuSynchronizer {
        final List<String> events = new ArrayList<>();

        @Override public void sendInitialData(AbstractContainerMenu m, List<ItemStack> items, ItemStack carried, int[] data) { events.add("init" + items.size()); }
        @Override public void sendSlotChange(AbstractContainerMenu m, int slot, ItemStack s) { events.add("slot" + slot + "=" + s.count()); }
        @Override public void sendCarriedChange(AbstractContainerMenu m, ItemStack s) { events.add("carried=" + s.count()); }
        @Override public void sendDataChange(AbstractContainerMenu m, int id, int v) { }
    }

    /** openHorseInventory·openEntityContainer 를 기록하는 연결 쪽. */
    static final class Host implements Player.InventoryHost {
        AbstractHorse horse;
        Container horseContainer;
        ContainerEntity entity;

        @Override public ItemStack slot(int windowSlot) { return ItemStack.EMPTY; }
        @Override public int selected() { return 0; }
        @Override public void replace(int windowSlot, ItemStack expect, ItemStack now) { }
        @Override public int permissionLevel() { return 0; }
        @Override public void openHorseInventory(AbstractHorse h, Container c) { horse = h; horseContainer = c; }
        @Override public void openEntityContainer(ContainerEntity e) { entity = e; }
    }

    BlockPlacementTest.TestLevel level;
    PlayerInventory inv;
    FakePlayer mp;
    Container invC;

    @BeforeEach
    void setUp() {
        level = new BlockPlacementTest.TestLevel();
        inv = new PlayerInventory();
        mp = new FakePlayer(inv);
        invC = new PlayerInventoryContainer(inv);
    }

    private <T extends AbstractHorse> T tamed(T h) {
        h.moveTo(0.5, -60, 0.5, 0, 0);
        h.setOnGround(true);
        h.setTamed(true);
        h.tickCount = 100;
        h.firstTick = false;
        return h;
    }

    private HorseInventoryMenu open(AbstractHorse h) {
        return new HorseInventoryMenu(1, invC, h.inventoryContainer(), h, h.inventoryColumns(), mp);
    }

    private static Player player(Host host) {
        Player p = new Player(950, UUID.randomUUID(), "opener", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.teleporter(t -> {});
        p.applyMove(2.5, -60, 0.5, 30.0F, 20.0F, true);
        p.inventoryHost(host);
        return p;
    }

    // ------------------------------------------------------------------ 말 인벤토리 칸

    @Test
    void horseMenuLayoutWithoutAndWithChest() {
        Horse h = tamed(new Horse(level, level.nextEntityId()));
        HorseInventoryMenu m = open(h);
        assertEquals(2 + 36, m.slots.size(), "안장·갑옷 + 플레이어 36칸");
        assertEquals(0, m.columns());

        Donkey d = tamed(new Donkey(level, level.nextEntityId()));
        d.setChest(true);
        d.createInventory();
        assertEquals(16, d.getInventorySize());
        HorseInventoryMenu dm = open(d);
        assertEquals(2 + 15 + 36, dm.slots.size(), "당나귀 상자 15칸");
        assertEquals(5, dm.columns());
        assertEquals(1, dm.getSlot(2).containerIndex(), "상자 첫 칸은 말 인벤토리 1번");
        assertEquals(15, dm.getSlot(16).containerIndex());
        assertEquals(9, dm.getSlot(17).containerIndex(), "그 뒤 플레이어 주 인벤토리");
        assertEquals(0, dm.getSlot(17 + 27).containerIndex(), "핫바 0");
    }

    @Test
    void saddleAndArmorSlotRules() {
        Horse h = new Horse(level, level.nextEntityId());
        h.moveTo(0.5, -60, 0.5, 0, 0);
        HorseInventoryMenu m = open(h);
        ItemStack saddle = item("saddle", 1), armor = item("iron_horse_armor", 1);
        assertFalse(m.getSlot(0).mayPlace(saddle), "길들이지 않으면 안장 칸이 닫혀 있다");
        assertFalse(m.getSlot(0).isActive());
        h.setTamed(true);
        assertTrue(m.getSlot(0).mayPlace(saddle));
        assertFalse(m.getSlot(0).mayPlace(item("stone", 1)));
        assertTrue(m.getSlot(1).mayPlace(armor));
        assertFalse(m.getSlot(1).mayPlace(item("diamond_chestplate", 1)));
        assertEquals(1, m.getSlot(1).getMaxStackSize());

        Donkey d = tamed(new Donkey(level, level.nextEntityId()));
        HorseInventoryMenu dm = open(d);
        assertFalse(dm.getSlot(1).mayPlace(armor), "당나귀는 갑옷을 못 입는다");
        assertFalse(dm.getSlot(1).isActive());
    }

    @Test
    void clickingSaddleIntoSlotSaddlesHorse() {
        Horse h = tamed(new Horse(level, level.nextEntityId()));
        HorseInventoryMenu m = open(h);
        inv.set(36, item("saddle", 1));   // 핫바 0 = 메뉴 칸 2 + 27
        Recorder rec = new Recorder();
        m.setSynchronizer(rec);
        m.clicked(29, 0, ClickType.PICKUP, mp);
        m.clicked(0, 0, ClickType.PICKUP, mp);
        assertTrue(h.isSaddled(), "안장 칸 → containerChanged → 안장 깃발");
        rec.events.clear();
        m.broadcastChanges();
        assertTrue(rec.events.contains("slot0=1"), "바뀐 칸을 보낸다: " + rec.events);
        assertTrue(rec.events.contains("slot29=0"), rec.events.toString());
        // 다시 꺼내면 안장이 풀린다
        m.clicked(0, 0, ClickType.PICKUP, mp);
        assertFalse(h.isSaddled());
    }

    @Test
    void shiftClickRoutesSaddleArmorAndChest() {
        Donkey d = tamed(new Donkey(level, level.nextEntityId()));
        d.setChest(true);
        d.createInventory();
        HorseInventoryMenu m = open(d);
        int hot0 = 2 + 15 + 27;
        inv.set(36, item("saddle", 1));
        assertFalse(m.quickMoveStack(mp, hot0).isEmpty());
        assertTrue(d.isSaddled(), "shift-클릭한 안장은 안장 칸으로");
        inv.set(37, item("stone", 10));
        m.quickMoveStack(mp, hot0 + 1);
        assertEquals(10, d.inventory[1].count(), "나머지는 상자 첫 칸으로");
        assertTrue(inv.get(37).isEmpty());
        // 말 칸 → 플레이어(핫바 끝부터)
        m.quickMoveStack(mp, 2);
        assertTrue(d.inventory[1].isEmpty());
        assertEquals(10, inv.get(44).count(), "끝(핫바 8)부터 채운다");

        Horse h = tamed(new Horse(level, level.nextEntityId()));
        HorseInventoryMenu hm = open(h);
        int hHot0 = 2 + 27;
        inv.set(36, item("iron_horse_armor", 1));
        hm.quickMoveStack(mp, hHot0);
        assertEquals("minecraft:iron_horse_armor", org.domi.furnace.server.item.ItemTools.itemName(h.getItemBySlot(EquipmentSlot.BODY)));
        assertTrue(inv.get(36).isEmpty());
    }

    @Test
    void shiftClickWithoutChestMovesMainToHotbarButReturnsEmpty() {
        Horse h = tamed(new Horse(level, level.nextEntityId()));
        HorseInventoryMenu m = open(h);
        inv.set(9, item("stone", 5));   // 주 인벤토리 첫 칸 = 메뉴 칸 2
        assertTrue(m.quickMoveStack(mp, 2).isEmpty(), "바닐라는 주 ↔ 핫바 이동 뒤에도 EMPTY");
        assertTrue(inv.get(9).isEmpty());
        assertEquals(5, inv.get(36).count(), "핫바로 옮겨졌다");
        inv.set(40, item("dirt", 3));   // 핫바 4 = 메뉴 칸 2 + 27 + 4
        m.quickMoveStack(mp, 2 + 27 + 4);
        assertEquals(3, inv.get(9).count(), "핫바 → 주 인벤토리");
    }

    @Test
    void stillValidFollowsInventoryLifeAndRange() {
        Donkey d = tamed(new Donkey(level, level.nextEntityId()));
        HorseInventoryMenu m = open(d);
        assertTrue(m.stillValid(mp));
        mp.inRange = false;
        assertFalse(m.stillValid(mp), "멀어지면 닫힌다");
        mp.inRange = true;
        d.setChest(true);
        d.createInventory();
        assertFalse(m.stillValid(mp), "상자를 달면 인벤토리가 바뀌어 창이 무효");
        HorseInventoryMenu m2 = open(d);
        d.setHealth(0.0F);
        assertFalse(m2.stillValid(mp));
    }

    @Test
    void armorSlotClickEquipsBodyArmor() {
        Horse h = tamed(new Horse(level, level.nextEntityId()));
        HorseInventoryMenu m = open(h);
        inv.set(36, item("iron_horse_armor", 1));
        m.clicked(29, 0, ClickType.PICKUP, mp);
        m.clicked(1, 0, ClickType.PICKUP, mp);
        assertFalse(h.getItemBySlot(EquipmentSlot.BODY).isEmpty());
        assertEquals(2.0F, h.getEquipmentDropChance(EquipmentSlot.BODY), "setItemSlotAndDropWhenKilled");
        assertTrue(m.getCarried().isEmpty());
    }

    @Test
    void horseArmorAddsArmorAttribute() throws Exception {
        assumeTrue(org.domi.furnace.server.recipe.TestRecipes.manager() != null);
        Horse h = tamed(new Horse(level, level.nextEntityId()));
        double base = h.attributeValue(Attribute.ARMOR);
        h.setBodyArmorItem(org.domi.furnace.server.recipe.TestRecipes.item("diamond_horse_armor", 1));
        h.setOldPosAndRot();
        h.tick();
        assertEquals(base + 11.0, h.attributeValue(Attribute.ARMOR), 1e-9, "다이아 말 갑옷 +11");
    }

    @Test
    void rightClickArmorEquipsAndSneakOpensInventory() {
        Host host = new Host();
        Player p = player(host);
        Horse h = tamed(new Horse(level, level.nextEntityId()));
        ItemStack armor = item("golden_horse_armor", 1);
        p.inventory().set(36, armor);
        assertTrue(h.interact(p, 0, armor, null));
        assertEquals("minecraft:golden_horse_armor", org.domi.furnace.server.item.ItemTools.itemName(h.getItemBySlot(EquipmentSlot.BODY)));
        assertTrue(p.inventory().get(36).isEmpty(), "손의 것을 하나 쓴다");
        assertNull(p.vehicle(), "갑옷을 입히면 타지 않는다");

        p.sneaking(true);
        assertTrue(h.interact(p, 0, ItemStack.EMPTY, null));
        assertSame(h, host.horse, "길들인 말에 웅크려 우클릭 → 인벤토리 창");
        assertFalse(h.hasInventoryChanged(host.horseContainer));

        Horse wild = new Horse(level, level.nextEntityId());
        wild.moveTo(0.5, -60, 0.5, 0, 0);
        host.horse = null;
        wild.openCustomInventoryScreen(p);
        assertNull(host.horse, "길들이지 않은 말은 열지 않는다");
    }

    @Test
    void horseScreenOpenBytes() {
        assertEquals(0x23, HorseScreenOpen.ID);
        ByteBuf buf = Unpooled.buffer();
        new HorseScreenOpen(7, 5, 0x01020304).write(buf);
        assertEquals(7, buf.readUnsignedByte(), "창 번호는 부호 없는 바이트");
        assertEquals(5, Types.readVarInt(buf), "열 수 VarInt");
        assertEquals(0x01020304, buf.readInt(), "엔티티 번호 Int");
        assertEquals(0, buf.readableBytes());
        buf.release();
        ByteBuf big = Unpooled.buffer();
        new HorseScreenOpen(100, 0, 1).write(big);
        assertEquals(100, big.readUnsignedByte());
        big.release();
    }

    @Test
    void donkeyChestSaveRoundTrip() {
        Donkey d = tamed(new Donkey(level, level.nextEntityId()));
        d.setChest(true);
        d.createInventory();
        d.equipSaddle(item("saddle", 1));
        d.inventory[1] = item("stone", 7);
        d.inventory[15] = item("dirt", 2);
        var tag = d.save();
        Donkey l = (Donkey) Entities.load(level, tag);
        assertTrue(l.hasChest());
        assertTrue(l.isSaddled());
        assertEquals(7, l.inventory[1].count());
        assertEquals(2, l.inventory[15].count());
    }

    // ------------------------------------------------------------------ 상자 배·수레

    @Test
    void chestBoatOpensWhenSneakingOrFull() {
        Host host = new Host();
        Player p = player(host);
        ChestBoat cb = new ChestBoat(level, level.nextEntityId());
        cb.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(cb);
        p.sneaking(true);
        assertTrue(cb.interact(p, 0));
        assertSame(cb, host.entity, "웅크려 우클릭 → 상자 창");
        assertNull(p.vehicle());
        host.entity = null;
        p.sneaking(false);
        assertTrue(cb.interact(p, 0));
        assertSame(cb, p.vehicle(), "자리가 있으면 탄다");
        assertNull(host.entity);
        cb.openCustomInventoryScreen(p);
        assertSame(cb, host.entity, "타고 인벤토리 키");
    }

    @Test
    void minecartContainersOpenOnInteract() {
        Host host = new Host();
        Player p = player(host);
        MinecartChest mc = new MinecartChest(level, level.nextEntityId());
        assertTrue(mc.interact(p, 0));
        assertSame(mc, host.entity);
        MinecartHopper mh = new MinecartHopper(level, level.nextEntityId());
        assertTrue(mh.interact(p, 0));
        assertSame(mh, host.entity);
    }

    @Test
    void entityContainerMenusMoveItems() {
        MinecartChest mc = new MinecartChest(level, level.nextEntityId());
        mc.moveTo(0.5, -60, 0.5, 0, 0);
        var c = new EntityContainer(mc);
        ChestMenu m = ChestMenu.threeRows(1, invC, c, null, mp);
        assertArrayEquals(new int[]{0, -60, 0}, m.containerBlockPos(), "엔티티 좌표의 region 에서 클릭 처리");
        inv.set(36, item("stone", 20));
        m.quickMoveStack(mp, 27 + 27);
        assertEquals(20, mc.getItem(0).count(), "shift-클릭 → 수레 첫 칸");
        Recorder rec = new Recorder();
        m.setSynchronizer(rec);
        m.clicked(0, 0, ClickType.PICKUP, mp);
        m.clicked(5, 0, ClickType.PICKUP, mp);
        m.broadcastChanges();
        assertEquals(20, mc.getItem(5).count());
        assertTrue(rec.events.contains("slot0=0") && rec.events.contains("slot5=20"), rec.events.toString());
        assertTrue(m.stillValid(mp));
        mc.discard();
        assertFalse(m.stillValid(mp), "지워지면 창이 무효");

        MinecartHopper mh = new MinecartHopper(level, level.nextEntityId());
        HopperMenu hm = new HopperMenu(2, invC, new EntityContainer(mh), null, mp);
        assertEquals(5 + 36, hm.slots.size());
        inv.set(37, item("dirt", 3));
        hm.quickMoveStack(mp, 5 + 27 + 1);
        assertEquals(3, mh.getItem(0).count());
    }

    @Test
    void entityContainerSetLimitsStackSize() {
        ChestBoat cb = new ChestBoat(level, level.nextEntityId());
        var c = new EntityContainer(cb);
        c.set(0, item("saddle", 5));
        assertEquals(1, cb.getItem(0).count(), "limitSize: 그 아이템 최대 개수로 자른다");
        assertEquals(1, c.remove(0, 4).count());
        assertTrue(c.remove(0, 1).isEmpty());
    }

    @Test
    void chestBoatAndMinecartSaveRoundTripWithLootTable() {
        ChestBoat cb = new ChestBoat(level, level.nextEntityId());
        cb.moveTo(0.5, -60, 0.5, 0, 0);
        cb.setItem(4, item("diamond", 3));
        ChestBoat l = (ChestBoat) Entities.load(level, cb.save());
        assertEquals(3, l.getItem(4).count());
        assertNull(l.lootTable());

        cb.setLootTable("minecraft:chests/shipwreck_treasure", 42L);
        var tag = cb.save();
        assertEquals("minecraft:chests/shipwreck_treasure", tag.getString("LootTable"));
        assertFalse(tag.contains("Items"), "표가 있으면 아이템 대신");
        ChestBoat l2 = (ChestBoat) Entities.load(level, tag);
        assertEquals("minecraft:chests/shipwreck_treasure", l2.lootTable());
        assertEquals(42L, l2.lootTableSeed());

        MinecartChest mc = new MinecartChest(level, level.nextEntityId());
        mc.moveTo(0.5, -60, 0.5, 0, 0);
        mc.setItem(26, item("stone", 64));
        MinecartChest ml = (MinecartChest) Entities.load(level, mc.save());
        assertEquals(64, ml.getItem(26).count());
        MinecartHopper mh = new MinecartHopper(level, level.nextEntityId());
        mh.moveTo(0.5, -60, 0.5, 0, 0);
        mh.setItem(2, item("dirt", 9));
        mh.setEnabled(false);
        MinecartHopper hl = (MinecartHopper) Entities.load(level, mh.save());
        assertEquals(9, hl.getItem(2).count());
        assertFalse(hl.isEnabled());
    }
}
