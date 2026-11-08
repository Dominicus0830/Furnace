// /item·/loot 의 엔티티 전용 칸(바닐라 1.21.1 getSlot 바이트코드 대조): 약탈자 가방 300..304, 아이템 엔티티·액자·디스플레이·화살·던진 아이템 contents(0).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.command.ItemSlots;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityContentsSlotTest {
    @BeforeAll
    static void install() { ContainerEntityParityTest.install(); }

    private static ItemStack item(String n, int c) { return ContainerEntityParityTest.item(n, c); }

    @Test
    void pillagerBackpackSlots() {
        var level = new ContainerEntityParityTest.Lvl();
        var p = new Pillager(level, level.nextEntityId());
        assertNull(ItemSlots.of(p, 305), "가방은 5칸");
        assertNotNull(ItemSlots.of(p, 98), "장비 칸은 그대로");
        assertTrue(ItemSlots.of(p, 304).set(item("minecraft:ender_pearl", 40)));
        assertEquals(16, ItemSlots.of(p, 304).get().count(), "진주 최대 16 으로 자른다");
    }

    @Test
    void itemEntityContents() {
        var level = new ContainerEntityParityTest.Lvl();
        var e = new ItemEntity(level, level.nextEntityId(), 0, -60, 0, item("minecraft:stone", 3));
        assertNull(ItemSlots.of(e, 1));
        var a = ItemSlots.of(e, 0);
        assertEquals(3, a.get().count());
        assertTrue(a.set(item("minecraft:dirt", 5)));
        assertEquals(5, e.getItem().count());
    }

    @Test
    void itemFrameContentsKeepsOneAndWorksForGlow() {
        var level = new ContainerEntityParityTest.Lvl();
        var f = new ItemFrame(level, level.nextEntityId());
        var g = new GlowItemFrame(level, level.nextEntityId());
        assertTrue(ItemSlots.of(f, 0).set(item("minecraft:stone", 9)));
        assertEquals(1, f.getItem().count(), "액자는 1개만");
        assertTrue(ItemSlots.of(g, 0).set(item("minecraft:stone", 2)));
        assertFalse(g.getItem().isEmpty());
        assertNull(ItemSlots.of(f, 1));
    }

    @Test
    void itemDisplayContents() {
        var level = new ContainerEntityParityTest.Lvl();
        var d = new Display.ItemDisplay(level, level.nextEntityId());
        assertTrue(ItemSlots.of(d, 0).set(item("minecraft:stone", 9)));
        assertEquals(9, ItemSlots.of(d, 0).get().count(), "디스플레이는 개수를 줄이지 않는다");
    }

    @Test
    void arrowContentsEmptyRestoresDefault() {
        var level = new ContainerEntityParityTest.Lvl();
        var a = new Arrow(level, level.nextEntityId());
        var s = ItemSlots.of(a, 0);
        assertEquals(item("minecraft:arrow", 1).itemId(), s.get().itemId());
        assertTrue(s.set(item("minecraft:spectral_arrow", 1)));
        assertEquals(item("minecraft:spectral_arrow", 1).itemId(), s.get().itemId());
        assertTrue(s.set(ItemStack.EMPTY));
        assertEquals(item("minecraft:arrow", 1).itemId(), s.get().itemId(), "빈 스택이면 기본 줍는 아이템");
        assertNotNull(ItemSlots.of(new ThrownTrident(level, level.nextEntityId()), 0));
    }

    @Test
    void thrownItemContents() {
        var level = new ContainerEntityParityTest.Lvl();
        var sb = new Snowball(level, level.nextEntityId());
        var s = ItemSlots.of(sb, 0);
        assertTrue(s.set(item("minecraft:stone", 7)));
        assertEquals(1, sb.getItem().count(), "던진 아이템은 1개로");
        assertTrue(s.set(ItemStack.EMPTY));
        assertEquals(item("minecraft:snowball", 1).itemId(), sb.getItem().itemId(), "빈 스택이면 기본 아이템");
    }

    private static ItemStack tipped(String potion) {
        return org.domi.furnace.server.item.ItemTools.with(ContainerEntityParityTest.item("minecraft:tipped_arrow", 1),
                "minecraft:potion_contents", java.util.Map.of("potion", potion));
    }

    /** 바닐라 AbstractArrow.getSlot(0) setter = Arrow.setPickupItemStack → updateColor(K) : 색 데이터가 새 스택의 물약 색으로 바뀐다. */
    @Test
    void arrowContentsTippedUpdatesColor() {
        var level = new ContainerEntityParityTest.Lvl();
        var a = new Arrow(level, level.nextEntityId());
        assertEquals(-1, (int) (Integer) a.entityData().get(Arrow.DATA_EFFECT_COLOR));
        ItemSlots.of(a, 0).set(tipped("minecraft:poison"));
        assertNotEquals(-1, (int) (Integer) a.entityData().get(Arrow.DATA_EFFECT_COLOR), "바닐라는 색을 물약 색으로 갱신");
    }

    @Test
    void arrowContentsBackToPlainResetsColor() {
        var level = new ContainerEntityParityTest.Lvl();
        var a = new Arrow(level, level.nextEntityId());
        a.setPotionFromItem(tipped("minecraft:poison"));
        assertNotEquals(-1, (int) (Integer) a.entityData().get(Arrow.DATA_EFFECT_COLOR));
        ItemSlots.of(a, 0).set(ContainerEntityParityTest.item("minecraft:arrow", 1));
        assertEquals(-1, (int) (Integer) a.entityData().get(Arrow.DATA_EFFECT_COLOR), "바닐라는 일반 화살로 바꾸면 색이 -1");
    }
}
