package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.*;

/**
 * T-141 물결 1-D: 엔티티 종류별 Bukkit 형. 소환한 엔티티가 Paper 1.21.1 과 같은 인터페이스(instanceof)와
 * 같은 Craft 클래스 이름을 갖는지, Tameable#isTamed 가 소유 스레드에서 지금 값을 읽는지 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitEntityKindsTest {

    FurnaceTestServer server;
    TestPlayer tp;
    World world;
    BlockPos origin;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Kira");
        server.tick(3);
        var loc = tp.player().location();
        origin = new BlockPos((int) Math.floor(loc.x()), -20, (int) Math.floor(loc.z()));
        server.loadChunk(origin.chunkX(), origin.chunkZ());
        world = Compat.world(server.overworld());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(origin, task); }

    Entity spawn(EntityType type) {
        return owner(() -> world.spawnEntity(new Location(world, origin.x() + 0.5, origin.y(), origin.z() + 0.5), type));
    }

    void check(EntityType type, String craftName, Class<?>... kinds) {
        Entity e = spawn(type);
        try {
            assertEquals(craftName, e.getClass().getSimpleName(), type + " 의 Craft 클래스");
            for (Class<?> k : kinds) assertTrue(k.isInstance(e), type + " 은 " + k.getSimpleName());
            assertEquals(type, e.getType());
            // 같은 엔티티를 다시 감싸도(이벤트 등) 같은 형
            Entity again = Compat.entity(((CraftEntity) e).furnaceHandle());
            assertEquals(e.getClass(), again.getClass());
        } finally {
            owner(() -> { e.remove(); return null; });
        }
    }

    @Test
    void mobsHavePaperTypes() {
        check(EntityType.VILLAGER, "CraftVillager", Villager.class, AbstractVillager.class, Ageable.class, Creature.class, Mob.class, LivingEntity.class);
        check(EntityType.ZOMBIE, "CraftZombie", Zombie.class, Monster.class, Creature.class, LivingEntity.class);
        check(EntityType.COD, "CraftCod", Cod.class, Fish.class, WaterMob.class, Mob.class);
        check(EntityType.WOLF, "CraftWolf", Wolf.class, Tameable.class, Animals.class, Sittable.class);
        check(EntityType.HORSE, "CraftHorse", Horse.class, AbstractHorse.class, Vehicle.class, Tameable.class);
    }

    @Test
    void vehiclesAndProjectilesHavePaperTypes() {
        check(EntityType.MINECART, "CraftMinecartRideable", RideableMinecart.class, Minecart.class, Vehicle.class);
        check(EntityType.BOAT, "CraftBoat", Boat.class, Vehicle.class);
        check(EntityType.ARROW, "CraftArrow", Arrow.class, AbstractArrow.class, Projectile.class);
    }

    @Test
    void nonLivingKindsAreNotLiving() {
        Entity cart = spawn(EntityType.MINECART);
        try {
            assertFalse(cart instanceof LivingEntity);
            assertFalse(cart instanceof Mob);
        } finally {
            owner(() -> { cart.remove(); return null; });
        }
    }

    @Test
    void tamedReadsLiveValueOnOwner() {
        Wolf wolf = (Wolf) spawn(EntityType.WOLF);
        Horse horse = (Horse) spawn(EntityType.HORSE);
        try {
            assertFalse(owner(wolf::isTamed));
            assertFalse(owner(horse::isTamed));
            boolean[] now = owner(() -> {
                ((org.domi.furnace.server.entity.TamableAnimal) serverEntity(wolf)).setTame(true, false);
                ((org.domi.furnace.server.entity.AbstractHorse) serverEntity(horse)).setTamed(true);
                return new boolean[] { wolf.isTamed(), horse.isTamed() };
            });
            assertTrue(now[0], "소유 스레드에서는 길들인 직후 값");
            assertTrue(now[1], "말도");
            assertFalse(wolf.isTamed(), "소유 스레드 밖에서는 핸들 시점 값");
        } finally {
            owner(() -> { wolf.remove(); horse.remove(); return null; });
        }
    }

    org.domi.furnace.server.entity.Entity serverEntity(Entity b) {
        var l = b.getLocation();
        var cp = org.domi.furnace.server.world.ChunkPos.ofBlock(l.getX(), l.getZ());
        return server.internals().world().regionizer().ownerOf(cp.x(), cp.z()).level().entities().get(b.getUniqueId());
    }
}
