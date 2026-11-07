package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * H6: CraftBukkit 어댑터(Craft* 계층·getHandle·CraftItemStack 다리)가 실제(헤드리스) 서버 위에서 CraftBukkit 규칙대로 도는지.
 * 플러그인 로더를 거치는 것(버전 접미사 이름·리플렉션)은 {@link NmsPluginLoadTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CraftAdapterTest {

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Nora");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(at, task); }

    // ------------------------------------------------------------------ 계층·캐스트·이름

    @Test
    void bukkitObjectsAreCraftClassesWithPaperNames() {
        assertInstanceOf(CraftServer.class, Bukkit.getServer());
        assertEquals("org.bukkit.craftbukkit.CraftServer", Bukkit.getServer().getClass().getName(), "Paper 1.20.5+ 처럼 접미사 없는 이름");
        assertEquals("org.bukkit.craftbukkit", Bukkit.getServer().getClass().getPackage().getName());

        CraftPlayer cp = (CraftPlayer) Bukkit.getPlayer("Nora");
        assertSame(player, cp);
        // CraftBukkit 계층: CraftPlayer ← CraftHumanEntity ← CraftLivingEntity ← CraftEntity
        assertInstanceOf(CraftHumanEntity.class, cp);
        assertInstanceOf(CraftLivingEntity.class, cp);
        assertInstanceOf(CraftEntity.class, cp);
        assertInstanceOf(CraftWorld.class, cp.getWorld());
        assertEquals("org.bukkit.craftbukkit.entity.CraftPlayer", cp.getClass().getName());
    }

    // ------------------------------------------------------------------ getHandle

    @Test
    void playerHandleIsStableAndPointsBack() {
        CraftPlayer cp = (CraftPlayer) player;
        ServerPlayer h = cp.getHandle();
        assertSame(h, cp.getHandle(), "같은 CraftPlayer 는 같은 핸들(== 비교·Map 키)");
        assertSame(h, ((CraftEntity) Bukkit.getPlayer(player.getUniqueId())).getHandle(), "다시 얻은 플레이어도 같은 핸들");
        assertSame(cp, h.getBukkitEntity());
        assertEquals(player.getUniqueId(), h.getUUID());
        assertEquals(player.getUniqueId().toString(), h.getStringUUID());
        assertEquals("Nora", h.getScoreboardName());
        var loc = tp.player().location();
        assertEquals(loc.x(), h.getX());
        assertEquals(loc.y(), h.getY());
        assertEquals(loc.z(), h.getZ());
        assertTrue(h.isAlive());
        assertFalse(h.isRemoved());
        assertEquals(player.getHealth(), h.getHealth(), 1e-6);
        ServerLevel level = h.serverLevel();
        assertSame(((CraftWorld) player.getWorld()).getHandle(), level);
        assertSame(level, h.level());
        assertSame(player.getWorld(), level.getWorld());
        assertTrue(level.players().contains(h));
        assertEquals(player.getWorld().getSeed(), level.getSeed());
        // 네트워크 id 는 지어내지 않는다
        UnsupportedNmsCall e = assertThrows(UnsupportedNmsCall.class, h::getId);
        assertTrue(e.getMessage().contains("Entity#getId"), e.getMessage());
        assertInstanceOf(UnsupportedOperationException.class, e, "UnsupportedFeature(UnsupportedOperationException) 하위");
    }

    @Test
    void mobHandleIdentitySurvivesFreshSnapshots() {
        World world = player.getWorld();
        Location spot = new Location(world, at.x() + 3.5, at.y() + 1, at.z() + 0.5);
        Entity pig = owner(() -> world.spawnEntity(spot, EntityType.PIG));
        try {
            assertInstanceOf(CraftLivingEntity.class, pig);
            var h = ((CraftEntity) pig).getHandle();
            assertInstanceOf(net.minecraft.world.entity.LivingEntity.class, h);
            assertSame(pig, h.getBukkitEntity());
            assertEquals(pig.getUniqueId(), h.getUUID());
            // 같은 엔티티를 다른 경로(새 스냅샷 핸들)로 다시 얻어도 같은 Bukkit 객체·같은 핸들
            Entity again = owner(() -> world.getNearbyEntities(spot, 1, 1, 1).stream().filter(e -> e.getUniqueId().equals(pig.getUniqueId())).findFirst().orElseThrow());
            assertSame(pig, again);
            assertSame(h, ((CraftEntity) again).getHandle());
            assertEquals(h, ((CraftEntity) again).getHandle());
            assertTrue(((net.minecraft.world.entity.LivingEntity) h).isAlive());
            assertEquals(10f, ((net.minecraft.world.entity.LivingEntity) h).getMaxHealth(), 1e-6, "돼지 최대 체력");
        } finally {
            owner(() -> {
                pig.remove();
                return null;
            });
        }
    }

    @Test
    void worldAndServerHandles() {
        CraftWorld w = (CraftWorld) player.getWorld();
        assertSame(w.getHandle(), w.getHandle());
        assertSame(w.getHandle(), ((CraftWorld) Bukkit.getWorld(w.getName())).getHandle());

        DedicatedServer s = ((CraftServer) Bukkit.getServer()).getServer();
        assertSame(s, MinecraftServer.getServer());
        assertSame(s, w.getHandle().getServer());
        assertEquals("1.21.1", s.getServerVersion());
        assertEquals(Bukkit.getOnlinePlayers().size(), s.getPlayerCount());
        assertEquals(Bukkit.getMaxPlayers(), s.getMaxPlayers());
        int levels = 0;
        for (ServerLevel l : s.getAllLevels()) {
            levels++;
            assertSame(l, ((CraftWorld) l.getWorld()).getHandle());
        }
        assertEquals(Bukkit.getWorlds().size(), levels);
    }

    // ------------------------------------------------------------------ CraftItemStack 다리

    @Test
    void nmsCopyRoundTripKeepsTypeCountAndComponents() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta m = sword.getItemMeta();
        m.setDisplayName("§6Blade");
        sword.setItemMeta(m);
        sword.setDurability((short) 7);

        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(sword);
        assertEquals(1, nms.getCount());
        assertEquals("minecraft:diamond_sword", nms.getItem().toString());
        assertEquals("1 minecraft:diamond_sword", nms.toString(), "바닐라 ItemStack.toString");
        assertEquals("item.minecraft.diamond_sword", nms.getItem().getDescriptionId());
        assertEquals(7, nms.getDamageValue());
        assertEquals(1, nms.getMaxStackSize());

        ItemStack back = CraftItemStack.asBukkitCopy(nms);
        assertEquals(sword, back, "왕복해도 종류·개수·메타가 같다");
        assertEquals("§6Blade", back.getItemMeta().getDisplayName());
        assertFalse(back instanceof CraftItemStack, "asBukkitCopy 는 평범한 사본");

        // 사본이다: 서로 바꿔도 영향 없다
        nms.setCount(5);
        assertEquals(1, sword.getAmount());
        assertEquals(1, back.getAmount());

        // 빈 것
        assertSame(net.minecraft.world.item.ItemStack.EMPTY, CraftItemStack.asNMSCopy(null));
        assertSame(net.minecraft.world.item.ItemStack.EMPTY, CraftItemStack.asNMSCopy(new ItemStack(Material.AIR)));
        ItemStack air = CraftItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack.EMPTY);
        assertEquals(Material.AIR, air.getType());

        // 개수 0 은 바닐라처럼 빈 스택이지만 종류는 남는다(비었나 판정만)
        net.minecraft.world.item.ItemStack zero = CraftItemStack.asNMSCopy(new ItemStack(Material.STONE, 0));
        assertTrue(zero.isEmpty());
        assertEquals(0, zero.getCount());
        assertEquals("minecraft:air", zero.getItem().toString(), "비었으면 getItem 은 공기(바닐라)");
    }

    @Test
    void nmsStackFollowsVanillaRules() {
        net.minecraft.world.item.ItemStack a = CraftItemStack.asNMSCopy(new ItemStack(Material.STONE, 10));
        net.minecraft.world.item.ItemStack b = a.copy();
        assertNotSame(a, b);
        assertFalse(a.equals(b), "바닐라 ItemStack 은 equals 를 덮지 않는다(정체성)");
        assertTrue(net.minecraft.world.item.ItemStack.matches(a, b));
        assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(a, b));
        net.minecraft.world.item.ItemStack part = a.split(4);
        assertEquals(4, part.getCount());
        assertEquals(6, a.getCount());
        assertFalse(net.minecraft.world.item.ItemStack.matches(a, b));
        assertTrue(net.minecraft.world.item.ItemStack.isSameItem(a, b));
        a.shrink(6);
        assertTrue(a.isEmpty());
        assertSame(net.minecraft.world.item.ItemStack.EMPTY, a.copy(), "빈 스택의 copy 는 EMPTY");
        // 종류가 같고 둘 다 비었으면 컴포넌트가 달라도 같다
        net.minecraft.world.item.ItemStack named = CraftItemStack.asNMSCopy(named(Material.STONE, "x"));
        named.setCount(0);
        assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(named, net.minecraft.world.item.ItemStack.EMPTY));
        assertFalse(net.minecraft.world.item.ItemStack.isSameItemSameComponents(CraftItemStack.asNMSCopy(named(Material.STONE, "x")), b));
        assertSame(a.getItem(), net.minecraft.world.item.ItemStack.EMPTY.getItem(), "종류는 키마다 하나");
        assertSame(b.getItem(), part.getItem());
    }

    private static ItemStack named(Material type, String name) {
        ItemStack s = new ItemStack(type);
        ItemMeta m = s.getItemMeta();
        m.setDisplayName(name);
        s.setItemMeta(m);
        return s;
    }

    @Test
    void craftMirrorWritesThroughToTheNmsStackAndDetachesLikeCraftBukkit() throws Exception {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(new ItemStack(Material.STONE, 3));
        CraftItemStack mirror = CraftItemStack.asCraftMirror(nms);
        mirror.setAmount(9);
        assertEquals(9, nms.getCount(), "거울을 바꾸면 NMS 스택이 바뀐다");
        nms.setCount(12);
        assertEquals(12, mirror.getAmount(), "NMS 스택을 바꾸면 거울에서 보인다");
        mirror.setType(Material.DIRT);
        assertEquals("minecraft:dirt", nms.getItem().toString());
        assertEquals(12, nms.getCount());
        ItemMeta m = mirror.getItemMeta();
        m.setDisplayName("Soil");
        mirror.setItemMeta(m);
        assertEquals("Soil", CraftItemStack.asBukkitCopy(nms).getItemMeta().getDisplayName(), "메타도 NMS 스택에 쓴다");

        // 라이브러리가 리플렉션으로 읽는 handle 필드 — 같은 NMS 객체
        var field = CraftItemStack.class.getDeclaredField("handle");
        field.setAccessible(true);
        assertSame(nms, field.get(mirror));

        // CraftBukkit: 개수를 0 으로 하면 거울이 떨어진다(이후 NMS 스택은 그대로)
        mirror.setAmount(0);
        assertNull(field.get(mirror));
        assertEquals(Material.AIR, mirror.getType());
        assertEquals(0, mirror.getAmount());
        assertEquals(0, nms.getCount());
        mirror.setAmount(5);
        assertEquals(0, mirror.getAmount(), "떨어진 빈 거울의 setAmount 는 아무것도 안 한다");
        // 빈 거울에 종류를 주면 개수 1 인 새 NMS 스택
        mirror.setType(Material.STONE);
        assertEquals(1, mirror.getAmount());
        assertNotSame(nms, field.get(mirror));

        // 빈 NMS 스택의 거울은 빈 거울(EMPTY 를 바꾸지 않는다)
        CraftItemStack empty = CraftItemStack.asCraftMirror(net.minecraft.world.item.ItemStack.EMPTY);
        assertNull(field.get(empty));
        empty.setType(Material.STONE);
        assertTrue(net.minecraft.world.item.ItemStack.EMPTY.isEmpty());
        assertEquals(0, net.minecraft.world.item.ItemStack.EMPTY.getCount());
    }

    @Test
    void craftCopiesAndClonesAreIndependent() {
        CraftItemStack copy = CraftItemStack.asCraftCopy(new ItemStack(Material.STONE, 4));
        net.minecraft.world.item.ItemStack inner = CraftItemStack.asNMSCopy(copy);
        inner.setCount(30);
        assertEquals(4, copy.getAmount(), "asNMSCopy 는 CraftItemStack 의 NMS 스택도 사본으로");
        CraftItemStack clone = copy.clone();
        clone.setAmount(2);
        assertEquals(4, copy.getAmount());
        assertInstanceOf(CraftItemStack.class, clone);
        assertEquals(copy.getType(), clone.getType());

        CraftItemStack fresh = CraftItemStack.asNewCraftStack(net.minecraft.world.item.Item.byKey("minecraft:apple"), 3);
        assertEquals(Material.APPLE, fresh.getType());
        assertEquals(3, fresh.getAmount());
        assertEquals(Material.AIR, CraftItemStack.asNewCraftStack(net.minecraft.world.item.Item.byKey("minecraft:apple"), 0).getType());

        // 평범한 Bukkit 스택과 같음 비교·인벤토리 넣기
        assertEquals(new ItemStack(Material.STONE, 4), copy);
        owner(() -> {
            player.getInventory().clear();
            player.getInventory().setItem(0, copy);
            return null;
        });
        assertEquals(new ItemStack(Material.STONE, 4), owner(() -> player.getInventory().getItem(0)));
        owner(() -> {
            player.getInventory().clear();
            return null;
        });
    }
}
