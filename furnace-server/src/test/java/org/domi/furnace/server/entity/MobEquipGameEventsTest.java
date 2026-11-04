// 몹·갑옷 거치대 장착 이벤트(LivingEntity.onEquipItem): 칸을 바꾸면(setItemSlot) 장착 소리와 equip/unequip, 제자리 수정(updateItemSlot)은 없음.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobEquipGameEventsTest {

    record Ev(GameEvent event, GameEventContext ctx) {}

    static final class Level extends BlockPlacementTest.TestLevel {
        final List<Ev> events = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();

        @Override
        public void gameEvent(GameEvent event, Vec3 pos, GameEventContext ctx) {
            events.add(new Ev(event, ctx));
            super.gameEvent(event, pos, ctx);
        }

        @Override
        public void playSeededSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch, long seed) {
            sounds.add(sound);
        }

        List<GameEvent> equipEvents() {
            return events.stream().map(Ev::event).filter(e -> e == GameEvent.EQUIP || e == GameEvent.UNEQUIP).toList();
        }

        void clear() { events.clear(); sounds.clear(); }
    }

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static <T extends Entity> T spawn(Level level, String type) {
        @SuppressWarnings("unchecked") T e = (T) Entities.create(level, type);
        e.moveTo(0.5, -60, 0.5, 0, 0);
        e.firstTick = false;
        level.addFreshEntity(e);
        level.clear();
        return e;
    }

    private static ItemStack item(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", name), n); }

    @Test
    void replacingSlotEmitsEquipOrUnequipBySourceMob() {
        var level = new Level();
        Zombie z = spawn(level, "minecraft:zombie");
        z.setItemSlot(EquipmentSlot.HEAD, item("iron_helmet", 1));
        assertEquals(List.of(GameEvent.EQUIP), level.equipEvents());
        assertSame(z, level.events.get(0).ctx().sourceEntity());
        assertEquals(List.of("minecraft:item.armor.equip_iron"), level.sounds);
        level.clear();
        z.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);   // 벗기: unequip, 소리 없음
        assertEquals(List.of(GameEvent.UNEQUIP), level.equipEvents());
        assertTrue(level.sounds.isEmpty());
        level.clear();
        z.setItemSlot(EquipmentSlot.MAINHAND, item("iron_sword", 1));   // 장착 아이템이 아니면 손에 들어도 unequip(바닐라와 같음)
        assertEquals(List.of(GameEvent.UNEQUIP), level.equipEvents());
        assertTrue(level.sounds.isEmpty());
    }

    @Test
    void sameItemFirstTickAndWrongSlot() {
        var level = new Level();
        Zombie z = spawn(level, "minecraft:zombie");
        z.setItemSlot(EquipmentSlot.HEAD, item("iron_helmet", 1));
        level.clear();
        z.setItemSlot(EquipmentSlot.HEAD, item("iron_helmet", 1));   // 같은 아이템·컴포넌트 → 없음
        assertTrue(level.equipEvents().isEmpty());
        // 방어구를 손에: 소리 없이 equip(장착 아이템이지만 그 칸이 아니다)
        z.setItemSlot(EquipmentSlot.MAINHAND, item("diamond_boots", 1));
        assertEquals(List.of(GameEvent.EQUIP), level.equipEvents());
        assertTrue(level.sounds.isEmpty());
        level.clear();
        z.firstTick = true;
        z.setItemSlot(EquipmentSlot.FEET, item("iron_boots", 1));
        assertTrue(level.events.isEmpty());
        assertTrue(level.sounds.isEmpty());
        z.firstTick = false;
        z.setSilent(true);
        z.setItemSlot(EquipmentSlot.FEET, item("golden_boots", 1));   // 조용하면 소리만 없다
        assertEquals(List.of(GameEvent.EQUIP), level.equipEvents());
        assertTrue(level.sounds.isEmpty());
    }

    @Test
    void inPlaceChangesEmitNothing() {
        var level = new Level();
        Zombie z = spawn(level, "minecraft:zombie");
        z.setItemSlot(EquipmentSlot.HEAD, item("leather_helmet", 1));
        level.clear();
        z.hurtEquipped(EquipmentSlot.HEAD, 3);   // 내구도 깎기
        assertEquals(3, org.domi.furnace.server.item.ItemTools.damage(z.getItemBySlot(EquipmentSlot.HEAD)));
        z.hurtEquipped(EquipmentSlot.HEAD, 1000);   // 부서짐(shrink)
        assertTrue(z.getItemBySlot(EquipmentSlot.HEAD).isEmpty());
        assertTrue(level.equipEvents().isEmpty(), level.events.toString());
        assertTrue(level.sounds.isEmpty());
    }

    @Test
    void crossbowChargeAndTotemAreInPlace() {
        var level = new Level();
        Pillager p = spawn(level, "minecraft:pillager");
        p.setItemSlot(EquipmentSlot.MAINHAND, item("crossbow", 1));
        level.clear();
        Crossbow.releaseUsing(p, EquipmentSlot.MAINHAND, 1000);
        assertTrue(Crossbow.isCharged(p.getItemBySlot(EquipmentSlot.MAINHAND)));
        assertTrue(level.equipEvents().isEmpty(), level.events.toString());

        Zombie z = spawn(level, "minecraft:zombie");
        z.setItemSlot(EquipmentSlot.OFFHAND, item("totem_of_undying", 1));
        level.clear();
        assertTrue(z.checkTotemDeathProtection(new Entity.Damage("generic", false, false, false)));
        assertTrue(z.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty());
        assertTrue(level.equipEvents().isEmpty(), level.events.toString());
    }

    @Test
    void conversionDoesNotUnequipOldMob() {
        var level = new Level();
        Zombie z = spawn(level, "minecraft:zombie");
        z.setItemSlot(EquipmentSlot.HEAD, item("iron_helmet", 1));
        level.clear();
        Drowned d = (Drowned) Entities.create(level, "minecraft:drowned");
        z.convertTo(d, true);
        assertEquals("minecraft:iron_helmet", org.domi.furnace.server.item.ItemTools.itemName(d.getItemBySlot(EquipmentSlot.HEAD)));
        assertTrue(level.equipEvents().isEmpty(), level.events.toString());   // 새 몹은 첫 틱, 옛 몹은 copyAndClear
    }

    @Test
    void horseAndWolfBodyArmorOnce() {
        var level = new Level();
        Horse h = spawn(level, "minecraft:horse");
        h.setBodyArmorItem(item("iron_horse_armor", 1));
        assertEquals(List.of(GameEvent.EQUIP), level.equipEvents());
        assertEquals(List.of("minecraft:item.armor.equip_iron"), level.sounds);
        level.clear();
        Wolf w = spawn(level, "minecraft:wolf");
        w.setBodyArmorItem(item("wolf_armor", 1));
        assertEquals(List.of(GameEvent.EQUIP), level.equipEvents());
        assertEquals(List.of("minecraft:item.armor.equip_wolf"), level.sounds);
        level.clear();
        w.setBodyArmorItem(ItemStack.EMPTY);
        assertEquals(List.of(GameEvent.UNEQUIP), level.equipEvents());
    }

    @Test
    void llamaCarpetIsEquipable() {
        var level = new Level();
        Llama l = spawn(level, "minecraft:llama");
        l.setItemSlot(EquipmentSlot.BODY, item("red_carpet", 1));
        assertEquals(List.of(GameEvent.EQUIP), level.equipEvents());
        assertEquals(List.of("minecraft:entity.llama.swag"), level.sounds);
        level.clear();
        l.setItemSlot(EquipmentSlot.BODY, item("moss_carpet", 1));   // 이끼 양탄자는 장착 아이템이 아니다
        assertEquals(List.of(GameEvent.UNEQUIP), level.equipEvents());
        assertTrue(level.sounds.isEmpty());
    }

    @Test
    void armorStandSlotChangeEmits() {
        var level = new Level();
        ArmorStand a = spawn(level, "minecraft:armor_stand");
        a.setItemSlot(EquipmentSlot.CHEST, item("diamond_chestplate", 1));
        assertEquals(List.of(GameEvent.EQUIP), level.equipEvents());
        assertSame(a, level.events.get(0).ctx().sourceEntity());
        assertEquals(List.of("minecraft:item.armor.equip_diamond"), level.sounds);
    }
}
