package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.*;

/** H6 독립 검토(PR #139)에서 나온 지적의 회귀 시험 — 스냅샷 순서·동시 감싸기·바닐라 컴포넌트 기본값·빈 거울. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CraftAdapterReviewTest {
    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Rev");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(at, task); }

    static net.minecraft.world.item.ItemStack nms(String type, int count, Map<String, String> comps, Set<String> removed) {
        return net.minecraft.world.item.ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack(type, count, comps, removed), count);
    }

    /** 늦게 도착한 옛 스냅샷이 새 위치를 되돌리지 않는다. */
    @Test
    void olderSnapshotDoesNotOverwriteNewer() {
        World world = player.getWorld();
        Entity pig = owner(() -> world.spawnEntity(new Location(world, at.x() + 3.5, at.y() + 1, at.z() + 0.5), EntityType.PIG));
        try {
            var oldSnap = ((CraftEntity) pig).furnaceHandle();
            double x0 = pig.getLocation().getX();
            Location to = new Location(world, at.x() + 5.5, at.y() + 1, at.z() + 0.5);
            owner(() -> { pig.teleport(to); return null; });
            server.tick(2);
            Entity fresh = owner(() -> world.getNearbyEntities(to, 1, 1, 1).stream()
                    .filter(e -> e.getUniqueId().equals(pig.getUniqueId())).findFirst().orElseThrow());
            assertSame(pig, fresh);
            double x1 = pig.getLocation().getX();
            assertNotEquals(x0, x1, 1e-6);
            assertSame(pig, Compat.entity(oldSnap));
            assertEquals(x1, pig.getLocation().getX(), 1e-6, "옛 스냅샷이 새 위치를 덮어썼다");
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }

    /** 같은 엔티티를 두 스레드가 처음 감싸도 CraftEntity(=getHandle 정체성)는 하나다. */
    @Test
    void concurrentFirstWrapKeepsIdentity() throws Exception {
        World world = player.getWorld();
        Entity pig = owner(() -> world.spawnEntity(new Location(world, at.x() + 2.5, at.y() + 1, at.z() + 2.5), EntityType.PIG));
        try {
            var snap = ((CraftEntity) pig).furnaceHandle();
            Field sf = Compat.class.getDeclaredField("state");
            sf.setAccessible(true);
            Object st = sf.get(null);
            Field ef = st.getClass().getDeclaredField("entities");
            ef.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<Object, Object> map = (ConcurrentHashMap<Object, Object>) ef.get(st);
            int split = 0;
            for (int i = 0; i < 500; i++) {
                map.remove(snap.uniqueId());
                CyclicBarrier b = new CyclicBarrier(2);
                AtomicReference<Object> a = new AtomicReference<>(), c = new AtomicReference<>();
                Thread t1 = new Thread(() -> { try { b.await(); a.set(Compat.entity(snap)); } catch (Exception ignored) { } });
                Thread t2 = new Thread(() -> { try { b.await(); c.set(Compat.entity(snap)); } catch (Exception ignored) { } });
                t1.start(); t2.start(); t1.join(); t2.join();
                assertNotNull(a.get());
                if (a.get() != c.get()) split++;
            }
            assertEquals(0, split, "두 스레드가 서로 다른 CraftEntity 를 받았다");
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }

    /** 바닐라 getDamageValue = clamp(damage, 0, getMaxDamage()) — 내구도 없는 돌은 0, 도구는 최대 내구도에서 잘린다. */
    @Test
    void damageValueIsClampedLikeVanilla() {
        assertEquals(0, nms("minecraft:stone", 1, Map.of("minecraft:damage", "5"), Set.of()).getDamageValue());
        var sword = nms("minecraft:diamond_sword", 1, Map.of("minecraft:damage", "99999"), Set.of());
        assertEquals(1561, sword.getMaxDamage());
        assertEquals(1561, sword.getDamageValue());
        assertEquals(7, nms("minecraft:diamond_sword", 1, Map.of("minecraft:damage", "7"), Set.of()).getDamageValue());
        assertEquals(0, net.minecraft.world.item.ItemStack.EMPTY.getMaxDamage());
    }

    /** 바닐라 getMaxStackSize = getOrDefault(max_stack_size, 1): 지운 패치·EMPTY 는 1, 개수 0 이어도 종류 기본값. */
    @Test
    void maxStackSizeFollowsVanillaDefaults() {
        assertEquals(1, nms("minecraft:stone", 1, Map.of(), Set.of("minecraft:max_stack_size")).getMaxStackSize());
        assertEquals(1, net.minecraft.world.item.ItemStack.EMPTY.getMaxStackSize());
        var pearls = nms("minecraft:ender_pearl", 2, Map.of(), Set.of());
        pearls.shrink(2);
        assertTrue(pearls.isEmpty());
        assertEquals(16, pearls.getMaxStackSize());
        assertEquals(64, nms("minecraft:stone", 3, Map.of(), Set.of()).getMaxStackSize());
    }

    /** handle 필드를 리플렉션으로 EMPTY 로 바꾼 거울은 빈 거울처럼 동작한다(EMPTY 를 건드리지 않는다). */
    @Test
    void mirrorHoldingEmptyHandleActsAsEmpty() throws Exception {
        CraftItemStack m = CraftItemStack.asCraftMirror(new net.minecraft.world.item.ItemStack(Item.byKey("minecraft:stone"), 3));
        Field h = CraftItemStack.class.getDeclaredField("handle");
        h.setAccessible(true);
        h.set(m, net.minecraft.world.item.ItemStack.EMPTY);
        assertEquals(0, m.getAmount());
        m.setAmount(5);
        assertTrue(net.minecraft.world.item.ItemStack.EMPTY.isEmpty());
        assertEquals(0, net.minecraft.world.item.ItemStack.EMPTY.getCount());
        assertDoesNotThrow(() -> m.setType(Material.DIRT));
        assertEquals(Material.DIRT, m.getType());
        assertEquals(1, m.getAmount());
        assertTrue(net.minecraft.world.item.ItemStack.EMPTY.isEmpty());
    }

    /** 개수 0 으로 붙어 있는 거울에 setType 하면 CraftBukkit 처럼 같은 NMS 스택에서 종류만 바뀐다(컴포넌트 유지). */
    @Test
    void setTypeOnZeroCountAttachedMirrorStaysAttached() throws Exception {
        var stack = nms("minecraft:stone", 2, Map.of("minecraft:custom_name", "'\"x\"'"), Set.of());
        CraftItemStack m = CraftItemStack.asCraftMirror(stack);
        stack.shrink(2);
        m.setType(Material.DIRT);
        Field h = CraftItemStack.class.getDeclaredField("handle");
        h.setAccessible(true);
        assertSame(stack, h.get(m));
        stack.setCount(1);
        assertEquals("minecraft:dirt", stack.getItem().toString());
        assertEquals("'\"x\"'", stack.furnaceValue().components().get("minecraft:custom_name"));
        // 붙은 스택의 종류 바꾸기도 같은 NMS 스택에서
        m.setType(Material.COBBLESTONE);
        assertSame(stack, h.get(m));
        assertEquals("minecraft:cobblestone", stack.getItem().toString());
        // 공기는 떨어진다
        m.setType(Material.AIR);
        assertNull(h.get(m));
    }
}
