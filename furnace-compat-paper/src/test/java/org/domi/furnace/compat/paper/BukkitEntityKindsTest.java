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

    /** 길들인 뒤 새 스냅숏으로 다시 감싸면(getEntities) 같은 감싸개의 핸들이 바뀌어, 소유 스레드 밖에서도 새 값을 읽는다. */
    @Test
    void tamedOffThreadFollowsFreshHandle() {
        Wolf wolf = (Wolf) spawn(EntityType.WOLF);
        Horse horse = (Horse) spawn(EntityType.HORSE);
        try {
            assertFalse(horse.isTamed(), "말: 밖에서 핸들 값 false");
            owner(() -> {
                ((org.domi.furnace.server.entity.TamableAnimal) serverEntity(wolf)).setTame(true, false);
                ((org.domi.furnace.server.entity.AbstractHorse) serverEntity(horse)).setTamed(true);
                return null;
            });
            for (Entity e : world.getEntities()) {
                if (e.getUniqueId().equals(wolf.getUniqueId())) assertSame(wolf, e, "늑대는 같은 감싸개");
                if (e.getUniqueId().equals(horse.getUniqueId())) assertSame(horse, e, "말은 같은 감싸개");
            }
            assertTrue(wolf.isTamed(), "늑대: 새 핸들 시점 값");
            assertTrue(horse.isTamed(), "말: 새 핸들 시점 값");
        } finally {
            owner(() -> { wolf.remove(); horse.remove(); return null; });
        }
    }

    /** 이름 표의 모든 줄이 컴파일된 계층과 맞는가(손으로 쓴 인터페이스가 표와 어긋나도 잡는다). 서버를 쓰지 않는다. */
    @Test
    void tableMatchesCompiledHierarchy() throws Exception {
        java.nio.file.Path table = java.nio.file.Path.of("..", "tools", "compat", "bukkit-entity-hierarchy-1.21.1.txt");
        java.util.List<String> bad = new java.util.ArrayList<>();
        int rows = 0;
        for (String l : java.nio.file.Files.readAllLines(table)) {
            if (l.isBlank() || l.startsWith("#")) continue;
            String[] r = l.trim().split("\\s+");
            rows++;
            if (r[0].equals("T")) {
                if (r[1].equals("UNKNOWN")) continue;
                Class<?> iface = Class.forName(r[2]);
                Class<?> craft;
                try {
                    craft = Class.forName("org.bukkit.craftbukkit.entity." + r[3]);
                } catch (ClassNotFoundException e) {
                    bad.add("클래스 없음 " + r[3]);
                    continue;
                }
                if (!iface.isAssignableFrom(craft)) bad.add(r[3] + " 은 " + r[2] + " 가 아니다");
                if (java.lang.reflect.Modifier.isAbstract(craft.getModifiers())) bad.add(r[3] + " 이 추상");
            } else {
                Class<?> c = Class.forName(r[1]);
                if (!c.isInterface()) bad.add(r[1] + " 이 인터페이스가 아니다");
                for (int i = 2; i < r.length; i++) if (!Class.forName(r[i]).isAssignableFrom(c)) bad.add(r[1] + " 은 " + r[i] + " 가 아니다");
            }
        }
        assertTrue(rows > 250, "표 줄 수 " + rows);
        assertEquals(java.util.List.of(), bad);
    }

    /** Paper 처럼 체력 메서드는 Damageable 에, 이름·UUID 는 AnimalTamer 에 선언된다(그 형으로 컴파일한 플러그인이 메서드를 찾는다). */
    @Test
    void methodsDeclaredWherePaperDeclaresThem() throws Exception {
        for (String m : new String[] {"getHealth", "getMaxHealth"}) assertEquals(Damageable.class, Damageable.class.getMethod(m).getDeclaringClass(), m);
        assertEquals(Damageable.class, Damageable.class.getMethod("setHealth", double.class).getDeclaringClass());
        assertEquals(AnimalTamer.class, AnimalTamer.class.getMethod("getName").getDeclaringClass());
        assertEquals(AnimalTamer.class, AnimalTamer.class.getMethod("getUniqueId").getDeclaringClass());
        assertTrue(AnimalTamer.class.isAssignableFrom(org.bukkit.OfflinePlayer.class), "OfflinePlayer 도 AnimalTamer");
    }

    org.domi.furnace.server.entity.Entity serverEntity(Entity b) {
        var l = b.getLocation();
        var cp = org.domi.furnace.server.world.ChunkPos.ofBlock(l.getX(), l.getZ());
        return server.internals().world().regionizer().ownerOf(cp.x(), cp.z()).level().entities().get(b.getUniqueId());
    }
}
