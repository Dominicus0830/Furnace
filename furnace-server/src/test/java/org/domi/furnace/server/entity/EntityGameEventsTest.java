// 엔티티·아이템 쪽 게임 이벤트(진동) 누락분: 바닐라 호출 지점과 같은 종류·출처·자리로 level.gameEvent 가 불리는가.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.entity.MobGameEventsTest.RecLevel;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.world.EntityContainer;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityGameEventsTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static <T extends Entity> T spawn(RecLevel level, String type, double x, double z) {
        @SuppressWarnings("unchecked") T e = (T) Entities.create(level, type);
        e.moveTo(x, -60, z, 0, 0);
        e.firstTick = false;
        level.addFreshEntity(e);
        return e;
    }

    private static ItemStack item(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", name), n); }

    private static Player player(RecLevel level, Player.InventoryHost host) {
        Player p = new Player(950, UUID.randomUUID(), "p", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.teleporter(t -> {});
        p.applyMove(2.5, -60, 0.5, 0.0F, 0.0F, true);
        p.currentLevel = level;
        if (host != null) p.inventoryHost(host);
        return p;
    }

    private static Object only(RecLevel level, GameEvent e) {
        var evs = level.of(e);
        assertEquals(1, evs.size(), e.name + " " + level.events);
        return evs.get(0).ctx().sourceEntity();
    }

    @Test
    void mountAndDismountFromVehicleWithPassengerAsSource() {
        var level = new RecLevel();
        Boat boat = spawn(level, "minecraft:boat", 0.5, 0.5);
        Zombie z = spawn(level, "minecraft:zombie", 0.5, 0.5);
        level.events.clear();
        assertTrue(z.startRiding(boat, true));
        assertSame(z, only(level, GameEvent.ENTITY_MOUNT));
        assertEquals(boat.x(), level.of(GameEvent.ENTITY_MOUNT).get(0).pos().x());
        z.stopRiding();
        assertSame(z, only(level, GameEvent.ENTITY_DISMOUNT));
    }

    @Test
    void waterSplashEmitsSplashFromSelf() {
        var level = new RecLevel();
        Zombie z = spawn(level, "minecraft:zombie", 0.5, 0.5);
        level.events.clear();
        z.doWaterSplashEffect();
        assertSame(z, only(level, GameEvent.SPLASH));
    }

    @Test
    void mobItemUseStartAndFinishOnce() {
        var level = new RecLevel();
        Skeleton s = spawn(level, "minecraft:skeleton", 0.5, 0.5);
        s.setItemSlot(EquipmentSlot.MAINHAND, item("bow", 1));
        level.events.clear();
        s.startUsingItem(0);
        s.startUsingItem(0);   // 쓰는 중이면 다시 시작하지 않는다
        assertSame(s, only(level, GameEvent.ITEM_INTERACT_START));
        s.stopUsingItem();
        s.stopUsingItem();     // 쓰던 중이 아니면 끝 이벤트도 없다
        assertSame(s, only(level, GameEvent.ITEM_INTERACT_FINISH));
    }

    @Test
    void mobTotemEmitsItemInteractFinish() {
        var level = new RecLevel();
        Zombie z = spawn(level, "minecraft:zombie", 0.5, 0.5);
        z.setItemSlot(EquipmentSlot.OFFHAND, item("totem_of_undying", 1));
        level.events.clear();
        z.hurtBy(new Entity.Damage("generic", false, false, false), 1000.0F, null);
        assertTrue(z.isAlive());
        assertSame(z, only(level, GameEvent.ITEM_INTERACT_FINISH));
    }

    @Test
    void playerItemUseStartAndFinish() {
        var level = new RecLevel();
        Player p = player(level, null);
        p.startUsingItem(0, 32);
        p.startUsingItem(0, 32);
        assertSame(p, only(level, GameEvent.ITEM_INTERACT_START));
        assertEquals(2.5, level.of(GameEvent.ITEM_INTERACT_START).get(0).pos().x());
        p.stopUsingItem();
        p.stopUsingItem();
        assertSame(p, only(level, GameEvent.ITEM_INTERACT_FINISH));
        assertEquals(-1, p.usingHand);
    }

    @Test
    void vehicleDamageUsesAttackerAsSource() {
        var level = new RecLevel();
        Minecart cart = spawn(level, "minecraft:minecart", 0.5, 0.5);
        Boat boat = spawn(level, "minecraft:boat", 4.5, 0.5);
        Zombie z = spawn(level, "minecraft:zombie", 2.5, 0.5);
        level.events.clear();
        cart.hurtBy(new Entity.Damage("mob_attack", false, false, false), 1.0F, z);
        assertSame(z, only(level, GameEvent.ENTITY_DAMAGE));
        level.events.clear();
        boat.hurtBy(new Entity.Damage("mob_attack", false, false, false), 1.0F, z);
        assertSame(z, only(level, GameEvent.ENTITY_DAMAGE));
        assertEquals(4.5, level.of(GameEvent.ENTITY_DAMAGE).get(0).pos().x());
    }

    @Test
    void itemEntityAndShulkerBulletDamage() {
        var level = new RecLevel();
        var ie = new ItemEntity(level, level.nextEntityId(), 0.5, -60, 0.5, item("stone", 1));
        level.addFreshEntity(ie);
        level.events.clear();
        ie.hurt(new Entity.Damage("generic", false, false, false), 1.0F);
        assertNull(only(level, GameEvent.ENTITY_DAMAGE));

        ShulkerBullet b = spawn(level, "minecraft:shulker_bullet", 3.5, 0.5);
        level.events.clear();
        b.hurt(new Entity.Damage("generic", false, false, false), 1.0F);
        assertSame(b, only(level, GameEvent.ENTITY_DAMAGE));
        assertEquals(3.5, level.of(GameEvent.ENTITY_DAMAGE).get(0).pos().x());
        assertTrue(b.isRemoved());
    }

    @Test
    void armorStandPunchEmitsDamage() {
        var level = new RecLevel();
        ArmorStand a = spawn(level, "minecraft:armor_stand", 0.5, 0.5);
        level.events.clear();
        assertTrue(a.hurtBy(new Entity.Damage("player_attack", false, false, false), 1.0F, null));
        assertEquals(1, level.of(GameEvent.ENTITY_DAMAGE).size());
        assertFalse(a.isRemoved());
    }

    @Test
    void shulkerPeekOpensAndCloses() throws Exception {
        var level = new RecLevel();
        Shulker s = spawn(level, "minecraft:shulker", 0.5, 0.5);
        var m = Shulker.class.getDeclaredMethod("setRawPeekAmount", int.class);
        m.setAccessible(true);
        level.events.clear();
        m.invoke(s, 100);
        assertSame(s, only(level, GameEvent.CONTAINER_OPEN));
        m.invoke(s, 0);
        assertSame(s, only(level, GameEvent.CONTAINER_CLOSE));
    }

    @Test
    void chestMinecartAndChestBoatOpenAndCloseWithPlayerSource() {
        var level = new RecLevel();
        var host = new EntityInventoryMenuTest.Host();
        Player p = player(level, host);
        MinecartChest mc = spawn(level, "minecraft:chest_minecart", 0.5, 0.5);
        level.events.clear();
        assertTrue(mc.interact(p, 0));
        assertSame(p, only(level, GameEvent.CONTAINER_OPEN));
        assertEquals(0.5, level.of(GameEvent.CONTAINER_OPEN).get(0).pos().x());
        new EntityContainer(mc, p, null).stopOpen(null);   // region 밖(시험)에서는 바로 부른다
        assertSame(p, only(level, GameEvent.CONTAINER_CLOSE));

        MinecartHopper mh = spawn(level, "minecraft:hopper_minecart", 5.5, 0.5);
        level.events.clear();
        assertTrue(mh.interact(p, 0));
        new EntityContainer(mh, p, null).stopOpen(null);
        assertTrue(level.of(GameEvent.CONTAINER_OPEN).isEmpty(), "깔때기 수레는 이벤트 없음");
        assertTrue(level.of(GameEvent.CONTAINER_CLOSE).isEmpty());

        ChestBoat cb = spawn(level, "minecraft:chest_boat", 8.5, 0.5);
        p.sneaking(true);
        level.events.clear();
        assertTrue(cb.interact(p, 0));
        assertSame(p, only(level, GameEvent.CONTAINER_OPEN));
        cb.openCustomInventoryScreen(p);
        assertEquals(2, level.of(GameEvent.CONTAINER_OPEN).size());
        new EntityContainer(cb, p, null).stopOpen(null);
        assertSame(p, only(level, GameEvent.CONTAINER_CLOSE));
    }

    @Test
    void armadilloAndCamelActions() {
        var level = new RecLevel();
        Armadillo a = spawn(level, "minecraft:armadillo", 0.5, 0.5);
        level.events.clear();
        a.rollUp();
        assertSame(a, only(level, GameEvent.ENTITY_ACTION));
        a.rollOut();
        assertEquals(2, level.of(GameEvent.ENTITY_ACTION).size());
        level.events.clear();
        assertTrue(a.brushOffScute());
        assertSame(a, only(level, GameEvent.ENTITY_INTERACT));

        Camel c = spawn(level, "minecraft:camel", 4.5, 0.5);
        level.events.clear();
        c.sitDown();   // 시험 레벨의 게임 시간이 0 이라 앉은 상태(음수 틱)로는 남지 않는다 — 이벤트만 본다
        assertSame(c, only(level, GameEvent.ENTITY_ACTION));
        c.standUpInstantly();
        assertEquals(2, level.of(GameEvent.ENTITY_ACTION).size());
    }

    @Test
    void ravagerRoarIsEntityAction() throws Exception {
        var level = new RecLevel();
        Ravager r = spawn(level, "minecraft:ravager", 0.5, 0.5);
        var m = Ravager.class.getDeclaredMethod("roar");
        m.setAccessible(true);
        level.events.clear();
        m.invoke(r);
        assertSame(r, only(level, GameEvent.ENTITY_ACTION));
    }

    @Test
    void ominousItemSpawnerPlacesWithSpawnedEntityAsSource() throws Exception {
        var level = new RecLevel();
        OminousItemSpawner s = spawn(level, "minecraft:ominous_item_spawner", 0.5, 0.5);
        var set = OminousItemSpawner.class.getDeclaredMethod("setItem", ItemStack.class);
        set.setAccessible(true);
        set.invoke(s, item("stone", 1));
        var m = OminousItemSpawner.class.getDeclaredMethod("spawnItem");
        m.setAccessible(true);
        level.events.clear();
        m.invoke(s);
        assertInstanceOf(ItemEntity.class, only(level, GameEvent.ENTITY_PLACE));
        assertEquals(0.5, level.of(GameEvent.ENTITY_PLACE).get(0).pos().x());
    }

    @Test
    void ominousItemSpawnerShootsProjectileItemsDownward() throws Exception {   // OminousItemSpawner.spawnItem: ProjectileItem 은 asProjectile + 아래로 shoot
        var level = new RecLevel();
        OminousItemSpawner s = spawn(level, "minecraft:ominous_item_spawner", 0.5, 0.5);
        var set = OminousItemSpawner.class.getDeclaredMethod("setItem", ItemStack.class);
        set.setAccessible(true);
        set.invoke(s, item("arrow", 1));
        var m = OminousItemSpawner.class.getDeclaredMethod("spawnItem");
        m.setAccessible(true);
        level.events.clear();
        m.invoke(s);
        Object src = only(level, GameEvent.ENTITY_PLACE);
        assertInstanceOf(AbstractArrow.class, src);
        assertTrue(((AbstractArrow) src).vy() < 0, "아래로 쏜다");
    }
}
