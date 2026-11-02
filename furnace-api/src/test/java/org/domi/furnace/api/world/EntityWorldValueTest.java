// 엔티티·월드 API 값 객체 계약: BlockData 왕복·정규화, ItemStack 불변성, 이벤트 취소·검증.
package org.domi.furnace.api.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.entity.EntityDamageEvent;
import org.domi.furnace.api.event.inventory.InventoryClickEvent;
import org.domi.furnace.api.event.player.PlayerMoveEvent;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;
import org.junit.jupiter.api.Test;

class EntityWorldValueTest {

    @Test
    void blockDataParsesAndPrintsCanonically() {
        BlockData d = BlockData.parse("oak_stairs[half=top,facing=north]");
        assertEquals("minecraft:oak_stairs", d.type());
        assertEquals("minecraft:oak_stairs[facing=north,half=top]", d.toString(), "속성은 이름순으로 정규화");
        assertEquals(d, BlockData.parse(d.toString()));
        assertEquals("minecraft:stone", BlockData.parse("stone").toString());
        assertEquals(d.with("half", "bottom"), BlockData.parse("minecraft:oak_stairs[facing=north,half=bottom]"));
        assertTrue(BlockData.AIR.isAir());
        assertThrows(IllegalArgumentException.class, () -> BlockData.parse("stone[facing"));
        assertThrows(IllegalArgumentException.class, () -> BlockData.parse("stone[a=1,a=2]"));
        assertThrows(IllegalArgumentException.class, () -> BlockData.parse("stone[=1]"));
        assertThrows(UnsupportedOperationException.class, () -> d.properties().put("x", "y"), "불변");
    }

    @Test
    void itemStackIsImmutableValue() {
        ItemStack s = new ItemStack("diamond_sword", 1);
        assertEquals("minecraft:diamond_sword", s.type());
        ItemStack damaged = s.withComponent("damage", "7");
        assertEquals(0, s.damage(), "원본은 그대로");
        assertEquals(7, damaged.damage());
        assertEquals(Map.of("minecraft:damage", "7"), damaged.components());
        assertTrue(s.isSimilar(s.withAmount(5)));
        assertFalse(s.isSimilar(damaged));
        assertTrue(s.withAmount(0).isEmpty());
        assertTrue(ItemStack.EMPTY.isEmpty());
        ItemStack removed = damaged.withoutComponent("minecraft:damage");
        assertTrue(removed.components().isEmpty());
        assertEquals(java.util.Set.of("minecraft:damage"), removed.removedComponents());
        assertThrows(IllegalArgumentException.class, () -> new ItemStack("stone", -1));
        assertThrows(UnsupportedOperationException.class, () -> damaged.components().put("a", "b"));
    }

    @Test
    void inventorySnapshotCopiesAndBoundsChecks() {
        var list = new java.util.ArrayList<>(List.of(ItemStack.of("stone")));
        InventorySnapshot snap = new InventorySnapshot("minecraft:player", list);
        list.clear();
        assertEquals(1, snap.size(), "만든 뒤 원본을 바꿔도 스냅샷은 그대로");
        assertEquals("minecraft:stone", snap.get(0).type());
        assertTrue(snap.get(99).isEmpty());
        assertTrue(snap.get(-1).isEmpty());
    }

    @Test
    void cancellableEventsStartUncancelledAndToggle() {
        var move = new PlayerMoveEvent(null, new Location("minecraft:overworld", 0, 0, 0), new Location("minecraft:overworld", 1, 0, 0));
        assertInstanceOf(Cancellable.class, move);
        assertFalse(move.isCancelled());
        move.setCancelled(true);
        assertTrue(move.isCancelled());
        assertTrue(move.hasChangedBlock());

        var dmg = new EntityDamageEvent(null, "minecraft:fall", 3f);
        dmg.setDamage(1.5f);
        assertEquals(1.5f, dmg.damage());
        assertThrows(IllegalArgumentException.class, () -> dmg.setDamage(-1f));
        assertThrows(IllegalArgumentException.class, () -> dmg.setDamage(Float.NaN));

        var click = new InventoryClickEvent(null, new InventorySnapshot("minecraft:crafting", List.of(ItemStack.of("dirt"))), 0, 0, "pickup", ItemStack.EMPTY);
        assertEquals("minecraft:dirt", click.current().type());
        assertTrue(new InventoryClickEvent(null, new InventorySnapshot("x", List.of()), -999, 0, "pickup", ItemStack.EMPTY).current().isEmpty());
    }
}
