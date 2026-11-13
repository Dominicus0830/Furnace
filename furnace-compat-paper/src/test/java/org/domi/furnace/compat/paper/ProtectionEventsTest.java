package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-040a: ① 어댑터에 없는 이벤트 클래스를 쓰는 처리기 하나가 같은 리스너 클래스의 다른 처리기를 죽이지 않는다(메서드별 등록 대체 길).
 * ② 보호 이벤트 여섯(EntityExplode·BlockPistonExtend/Retract·BlockForm·BlockSpread·ProjectileHit)이 Bukkit 처리기에 닿고 취소·필드가 서버로 돌아간다.
 */
class ProtectionEventsTest {

    private static final String W = "minecraft:overworld";

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() {
        RecorderPlugin.reset();
        WiringProbe.reset();
    }

    // ------------------------------------------------------------------ ① 리스너 통째 탈락 막기

    private static final String YML = """
            name: Fb
            version: '1'
            main: fb.FbPlugin
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package fb;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class FbPlugin extends JavaPlugin {
                @Override public void onEnable() { WiringProbe.plugin = this; }
            }
            """;

    private static final String GONE = """
            package fb.gone;
            import org.bukkit.event.*;
            public class GoneEvent extends Event {
                private static final HandlerList H = new HandlerList();
                @Override public HandlerList getHandlers() { return H; }
                public static HandlerList getHandlerList() { return H; }
            }
            """;

    private static final String BASE = """
            package fb;
            import org.bukkit.event.*;
            import org.bukkit.event.player.*;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public class Base implements Listener {
                @EventHandler public void inherited(PlayerJoinEvent e) { WiringProbe.LOG.add("base:join"); }
                @EventHandler public void hidden(PlayerQuitEvent e) { WiringProbe.LOG.add("base:quit-must-be-hidden"); }
                @EventHandler private void basePrivate(PlayerKickEvent e) { WiringProbe.LOG.add("base:private-must-not-register"); }
            }
            """;

    /** 없는 이벤트(GoneEvent)를 쓰는 처리기·그 형이 서명에 든 일반 메서드와, 있는 이벤트 처리기들(공개·비공개·우선순위·ignoreCancelled·정적)이 한 클래스에 있다. */
    private static final String MIXED = """
            package fb;
            import org.bukkit.event.*;
            import org.bukkit.event.block.*;
            import org.bukkit.event.player.*;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class Mixed extends Base implements Listener {
                @EventHandler public void onGone(fb.gone.GoneEvent e) { WiringProbe.LOG.add("gone"); }
                public fb.gone.GoneEvent plain(fb.gone.GoneEvent e) { return e; }
                @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
                private void breaks(BlockBreakEvent e) { WiringProbe.LOG.add("break:" + e.getBlock().getY()); }
                @EventHandler(priority = EventPriority.LOWEST) public void place(BlockPlaceEvent e) { WiringProbe.LOG.add("place"); }
                @EventHandler public static void staticHandler(PlayerChangedWorldEvent e) { WiringProbe.LOG.add("static:world"); }
                @Override public void hidden(PlayerQuitEvent e) { WiringProbe.LOG.add("sub:quit-unannotated-override-hides"); }
                @EventHandler public void invalid(String notAnEvent) {}
            }
            """;

    /** 그 목록에서 이 플러그인의 처리기(정적 getRegisteredListeners(Plugin) 는 모든 목록을 훑는다). */
    private static List<org.bukkit.plugin.RegisteredListener> forPlugin(HandlerList list, Plugin plugin) {
        List<org.bukkit.plugin.RegisteredListener> out = new ArrayList<>();
        for (var r : list.getRegisteredListeners()) if (r.getPlugin() == plugin) out.add(r);
        return out;
    }

    private Path fbJar() throws Exception {
        return JarCompiler.jar(tmp.resolve("fb.jar"), YML,
                Map.of("fb.FbPlugin", PLUGIN, "fb.gone.GoneEvent", GONE, "fb.Base", BASE, "fb.Mixed", MIXED), java.util.Set.of("fb.gone."));
    }

    /** 플러그인 로거는 핸들러를 거치지 않으므로 필터로 기록을 가로챈다(필터는 늘 통과시킨다). */
    private static final class Capture implements java.util.logging.Filter {
        final List<LogRecord> records = new ArrayList<>();

        @Override public boolean isLoggable(LogRecord r) {
            records.add(r);
            return true;
        }
    }

    @Test
    void missingEventClassNoLongerDropsTheOtherHandlersOfTheListener() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(fbJar()).start()) {
            var dana = server.join("Dana").player();
            server.tick(2);
            Plugin fb = WiringProbe.plugin;
            assertNotNull(fb, "플러그인이 켜졌다");
            Capture log = new Capture();
            fb.getLogger().setFilter(log);
            Object mixed = fb.getClass().getClassLoader().loadClass("fb.Mixed").getDeclaredConstructor().newInstance();
            // 일반 경로는 NoClassDefFoundError — 이 때문에 예전에는 리스너 전체가 사라졌다
            assertThrows(NoClassDefFoundError.class, () -> mixed.getClass().getMethods());
            Bukkit.getPluginManager().registerEvents((org.bukkit.event.Listener) mixed, fb);

            // 풀리는 처리기는 모두 등록: 공개(부모에서 상속)·비공개·정적, 우선순위·ignoreCancelled 가 그대로
            var breaks = forPlugin(org.bukkit.event.block.BlockBreakEvent.getHandlerList(), fb);
            assertEquals(1, breaks.size());
            assertEquals(EventPriority.HIGH, breaks.get(0).getPriority());
            assertTrue(breaks.get(0).isIgnoringCancelled());
            assertEquals(EventPriority.LOWEST, forPlugin(org.bukkit.event.block.BlockPlaceEvent.getHandlerList(), fb).get(0).getPriority());
            assertEquals(1, forPlugin(org.bukkit.event.player.PlayerJoinEvent.getHandlerList(), fb).size(), "부모의 공개 처리기는 상속된다");
            assertEquals(0, forPlugin(org.bukkit.event.player.PlayerQuitEvent.getHandlerList(), fb).size(), "주석 없는 덮어쓰기가 부모 처리기를 가린다(getMethods 규칙)");
            assertEquals(0, forPlugin(org.bukkit.event.player.PlayerKickEvent.getHandlerList(), fb).size(), "부모의 비공개 메서드는 getMethods 에 없다");
            assertEquals(1, forPlugin(org.bukkit.event.player.PlayerChangedWorldEvent.getHandlerList(), fb).size());

            // 실제로 불린다(Furnace 이벤트 → 다리 → 메서드 핸들)
            var bus = server.api().events();
            bus.call(new org.domi.furnace.api.event.player.PlayerJoinEvent(dana, org.domi.furnace.api.text.Text.text("hi")));
            bus.call(new org.domi.furnace.api.event.player.PlayerQuitEvent(dana, org.domi.furnace.api.text.Text.text("bye")));
            bus.call(new org.domi.furnace.api.event.player.PlayerChangedWorldEvent(dana, W));
            bus.call(new org.domi.furnace.api.event.block.BlockBreakEvent(W, new BlockPos(1, 64, 2), "minecraft:stone", dana));
            assertTrue(WiringProbe.LOG.contains("base:join"), WiringProbe.LOG.toString());
            assertTrue(WiringProbe.LOG.contains("break:64"), WiringProbe.LOG.toString());
            assertTrue(WiringProbe.LOG.contains("static:world"), WiringProbe.LOG.toString());
            assertFalse(WiringProbe.LOG.stream().anyMatch(l -> l.contains("must")), WiringProbe.LOG.toString());
            assertFalse(WiringProbe.LOG.contains("gone"));

            // 경고에 건너뛴 처리기 이름과 없는 클래스가 있다(A-2)
            String warn = log.records.stream().filter(r -> r.getLevel() == java.util.logging.Level.WARNING).map(LogRecord::getMessage).findFirst().orElse("");
            assertTrue(warn.contains("onGone(GoneEvent)") && warn.contains("fb.gone.GoneEvent"), warn);
            assertTrue(warn.contains("invalid") == false, "서명이 틀린 것은 경고가 아니라 잘못된 서명 안내");
            assertTrue(log.records.stream().anyMatch(r -> r.getLevel() == java.util.logging.Level.SEVERE && r.getMessage().contains("invalid EventHandler method signature")),
                    "이벤트가 아닌 인자를 받는 @EventHandler 는 정상 경로와 같은 안내");
        }
    }

    @Test
    void aListenerWhoseOnlyHandlerIsMissingStillSkipsWithTheWarning() throws Exception {
        String only = """
                package fb;
                import org.bukkit.event.*;
                public final class Only implements Listener { @EventHandler public void onGone(fb.gone.GoneEvent e) {} }
                """;
        Path jar = JarCompiler.jar(tmp.resolve("only.jar"), YML, Map.of("fb.FbPlugin", PLUGIN, "fb.gone.GoneEvent", GONE, "fb.Only", only), java.util.Set.of("fb.gone."));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            server.tick(2);
            Plugin fb = WiringProbe.plugin;
            Capture log = new Capture();
            fb.getLogger().setFilter(log);
            Bukkit.getPluginManager().registerEvents((org.bukkit.event.Listener) fb.getClass().getClassLoader().loadClass("fb.Only").getDeclaredConstructor().newInstance(), fb);
            assertTrue(log.records.stream().anyMatch(r -> r.getMessage().contains("fb.gone.GoneEvent")), log.records.toString());
            assertTrue(fb.isEnabled(), "플러그인은 계속 켜져 있다");
        }
    }

    // ------------------------------------------------------------------ ② 보호 이벤트

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    private static <E extends Event> List<E> seen(Class<E> type) {
        List<E> out = new ArrayList<>();
        for (Event e : RecorderPlugin.SEEN) if (e.getClass() == type) out.add(type.cast(e));
        return out;
    }

    private static <E extends Event> E only(Class<E> type) {
        List<E> l = seen(type);
        assertEquals(1, l.size(), type.getSimpleName() + " 이 정확히 한 번 와야 한다: " + RecorderPlugin.SEEN);
        return l.get(0);
    }

    @Test
    void entityExplodeShowsFieldsAndBlockListEditsAndCancelReachTheServer() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            var dana = server.join("Dana").player();
            server.tick(2);
            var bus = server.api().events();
            var pig = server.await(server.overworld().spawnEntity("minecraft:pig", dana.location()));
            var at = new org.domi.furnace.api.world.Location(W, 5.5, 64, 5.5, 0f, 0f);
            var a = new BlockPos(5, 64, 5);
            var b = new BlockPos(6, 64, 5);
            var c = new BlockPos(7, 64, 5);

            var ev = bus.call(new org.domi.furnace.api.event.entity.EntityExplodeEvent(pig, at, new ArrayList<>(List.of(a, b)),
                    org.domi.furnace.api.event.cause.CauseChain.NONE, 0.25f, "DESTROY_WITH_DECAY"));
            assertFalse(ev.isCancelled());
            var x = only(org.bukkit.event.entity.EntityExplodeEvent.class);
            assertEquals(org.bukkit.entity.EntityType.PIG, x.getEntityType());
            assertEquals(0.25f, x.getYield(), 0f);
            assertEquals(ExplosionResult.DESTROY_WITH_DECAY, x.getExplosionResult());
            assertEquals(5.5, x.getLocation().getX(), 0.0);
            assertEquals(2, x.blockList().size());
            assertEquals(b.x(), x.blockList().get(1).getX());

            // 목록을 고치면(빼기·넣기) 서버가 그 목록을 따른다
            RecorderPlugin.reset();
            RecorderPlugin.ACTIONS.put("EntityExplodeEvent", e -> {
                var ee = (org.bukkit.event.entity.EntityExplodeEvent) e;
                ee.blockList().remove(0);
                ee.blockList().add(ee.blockList().get(0).getWorld().getBlockAt(c.x(), c.y(), c.z()));
            });
            var list = new ArrayList<>(List.of(a, b));
            var after = bus.call(new org.domi.furnace.api.event.entity.EntityExplodeEvent(pig, at, list, org.domi.furnace.api.event.cause.CauseChain.NONE, 1f, "DESTROY"));
            assertEquals(List.of(b, c), after.blocks());
            assertFalse(after.isCancelled());

            // 취소(WorldGuard 처럼 터진 자리를 보호) → 서버도 취소
            RecorderPlugin.ACTIONS.put("EntityExplodeEvent", e -> ((org.bukkit.event.entity.EntityExplodeEvent) e).setCancelled(true));
            assertTrue(bus.call(new org.domi.furnace.api.event.entity.EntityExplodeEvent(pig, at, new ArrayList<>(List.of(a)))).isCancelled());
        }
    }

    @Test
    void pistonEventsShowBlocksDirectionAndCancel() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            server.join("Dana");
            server.tick(2);
            var bus = server.api().events();
            var pos = new BlockPos(1, 64, 2);
            var pushed = List.of(new BlockPos(1, 65, 2), new BlockPos(1, 66, 2));

            bus.call(new org.domi.furnace.api.event.block.BlockPistonExtendEvent(W, pos, "up", false, pushed));
            var ext = only(org.bukkit.event.block.BlockPistonExtendEvent.class);
            assertEquals(BlockFace.UP, ext.getDirection());
            assertEquals(2, ext.getBlocks().size());
            assertEquals(2, ext.getLength());
            assertEquals(66, ext.getBlocks().get(1).getY());
            assertThrows(UnsupportedOperationException.class, () -> ext.getBlocks().add(ext.getBlock()), "CraftBukkit 의 목록은 바꿀 수 없다");
            assertEquals(pos.y(), ext.getBlock().getY(), "이벤트 블록은 피스톤 자신");

            // 수축: 끌 블록이 있으면 미는 방향(바라보는 쪽의 반대), 끌 블록이 없으면(일반 피스톤) 바라보는 쪽 — Paper 와 같다
            RecorderPlugin.reset();
            bus.call(new org.domi.furnace.api.event.block.BlockPistonRetractEvent(W, pos, "up", true, List.of(new BlockPos(1, 66, 2))));
            assertEquals(BlockFace.DOWN, only(org.bukkit.event.block.BlockPistonRetractEvent.class).getDirection());
            RecorderPlugin.reset();
            bus.call(new org.domi.furnace.api.event.block.BlockPistonRetractEvent(W, pos, "up", false, List.of()));
            var empty = only(org.bukkit.event.block.BlockPistonRetractEvent.class);
            assertEquals(BlockFace.UP, empty.getDirection());
            assertTrue(empty.getBlocks().isEmpty());

            // 취소 → 서버도 취소
            RecorderPlugin.ACTIONS.put("BlockPistonExtendEvent", e -> ((org.bukkit.event.block.BlockPistonExtendEvent) e).setCancelled(true));
            RecorderPlugin.ACTIONS.put("BlockPistonRetractEvent", e -> ((org.bukkit.event.block.BlockPistonRetractEvent) e).setCancelled(true));
            assertTrue(bus.call(new org.domi.furnace.api.event.block.BlockPistonExtendEvent(W, pos, "north", true, pushed)).isCancelled());
            assertTrue(bus.call(new org.domi.furnace.api.event.block.BlockPistonRetractEvent(W, pos, "north", true, pushed)).isCancelled());
        }
    }

    @Test
    void blockFormAndSpreadHaveSeparateHandlerListsAndCancel() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            server.join("Dana");
            server.tick(2);
            var bus = server.api().events();
            var pos = new BlockPos(1, 64, 2);

            bus.call(new org.domi.furnace.api.event.block.BlockSpreadEvent(W, pos, new BlockPos(1, 64, 3), BlockData.of("minecraft:dirt"), BlockData.of("minecraft:grass_block")));
            var spread = only(org.bukkit.event.block.BlockSpreadEvent.class);
            assertEquals(Material.GRASS_BLOCK, spread.getNewState().getType());
            assertEquals(3, spread.getSource().getZ());
            assertEquals(2, spread.getBlock().getZ());
            assertEquals(0, seen(org.bukkit.event.block.BlockFormEvent.class).size(), "BlockSpread 는 BlockForm 처리기 목록으로 오지 않는다(Bukkit 과 같다)");
            assertEquals(0, seen(org.bukkit.event.block.BlockGrowEvent.class).size());

            RecorderPlugin.reset();
            bus.call(new org.domi.furnace.api.event.block.BlockFormEvent(W, pos, BlockData.of("minecraft:lava"), BlockData.of("minecraft:obsidian")));
            assertEquals(Material.OBSIDIAN, only(org.bukkit.event.block.BlockFormEvent.class).getNewState().getType());
            assertEquals(0, seen(org.bukkit.event.block.BlockSpreadEvent.class).size());
            assertEquals(0, seen(org.bukkit.event.block.BlockGrowEvent.class).size(), "BlockForm 은 BlockGrow 의 하위지만 처리기 목록이 따로다");

            RecorderPlugin.ACTIONS.put("BlockFormEvent", e -> ((org.bukkit.event.block.BlockFormEvent) e).setCancelled(true));
            RecorderPlugin.ACTIONS.put("BlockSpreadEvent", e -> ((org.bukkit.event.block.BlockSpreadEvent) e).setCancelled(true));
            assertTrue(bus.call(new org.domi.furnace.api.event.block.BlockFormEvent(W, pos, BlockData.AIR, BlockData.of("minecraft:stone"))).isCancelled());
            assertTrue(bus.call(new org.domi.furnace.api.event.block.BlockSpreadEvent(W, pos, pos, BlockData.AIR, BlockData.of("minecraft:fire"))).isCancelled());
        }
    }

    @Test
    void projectileHitShowsShooterHitBlockAndCancel() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            var dana = server.join("Dana").player();
            server.tick(2);
            var bus = server.api().events();
            var host = (org.domi.furnace.server.plugin.PluginHost) server.api();
            var serverPlayer = server.internals().playerByName("Dana");
            // 플레이어가 던진 눈덩이(주인 = Dana)의 핸들 — 원인 사슬에 SHOOT 이 든다
            org.domi.furnace.api.entity.Entity snowball = server.internals().world().submitAtChunk(new org.domi.furnace.server.world.ChunkPos(0, 0), r -> {
                var level = r.level();
                var s = new org.domi.furnace.server.entity.Snowball(level, level.nextEntityId());
                s.moveTo(1.5, 65, 2.5, 0, 0);
                s.setOwner(serverPlayer);
                level.addFreshEntity(s);
                return org.domi.furnace.server.plugin.EntityHandle.of(host, s);
            }).get(30, java.util.concurrent.TimeUnit.SECONDS);
            var pos = new BlockPos(1, 64, 2);

            bus.call(new org.domi.furnace.api.event.entity.ProjectileHitEvent(snowball, null,
                    new org.domi.furnace.api.world.BlockSnapshot(W, pos, BlockData.of("minecraft:stone")), "up"));
            var hit = only(org.bukkit.event.entity.ProjectileHitEvent.class);
            assertInstanceOf(org.bukkit.entity.Projectile.class, hit.getEntity());
            assertEquals(org.bukkit.entity.EntityType.SNOWBALL, hit.getEntity().getType());
            assertEquals(64, hit.getHitBlock().getY());
            assertEquals(BlockFace.UP, hit.getHitBlockFace());
            assertNull(hit.getHitEntity());
            assertEquals(dana.uniqueId(), hit.getEntity().getOwnerUniqueId());
            assertInstanceOf(org.bukkit.entity.Player.class, hit.getEntity().getShooter(), "플레이어가 쏜 투사체의 쏜 쪽은 Player 다(보호 플러그인이 권한을 본다)");
            assertEquals("Dana", ((org.bukkit.entity.Player) hit.getEntity().getShooter()).getName());

            // 엔티티를 맞춘 경우: 블록·면 없음
            RecorderPlugin.reset();
            var pig = server.await(server.overworld().spawnEntity("minecraft:pig", dana.location()));
            bus.call(new org.domi.furnace.api.event.entity.ProjectileHitEvent(snowball, pig, null, null));
            var byEntity = only(org.bukkit.event.entity.ProjectileHitEvent.class);
            assertEquals(org.bukkit.entity.EntityType.PIG, byEntity.getHitEntity().getType());
            assertNull(byEntity.getHitBlock());
            assertNull(byEntity.getHitBlockFace());

            RecorderPlugin.ACTIONS.put("ProjectileHitEvent", e -> ((org.bukkit.event.entity.ProjectileHitEvent) e).setCancelled(true));
            assertTrue(bus.call(new org.domi.furnace.api.event.entity.ProjectileHitEvent(snowball, pig, null, null)).isCancelled());
        }
    }

    @Test
    void protectionEventsAreKnownAndNotMarkedNotFired() {
        for (Class<?> c : List.of(org.bukkit.event.entity.EntityExplodeEvent.class, org.bukkit.event.block.BlockPistonExtendEvent.class,
                org.bukkit.event.block.BlockPistonRetractEvent.class, org.bukkit.event.block.BlockFormEvent.class, org.bukkit.event.block.BlockSpreadEvent.class,
                org.bukkit.event.entity.ProjectileHitEvent.class)) {
            assertTrue(EventBridge.isBridged(c.asSubclass(Event.class)), c.getSimpleName());
        }
        // 서버 발화 지점이 아직 없는 EntityChangeBlock 은 안내만(T-040a 확인 결과)
        assertTrue(EventBridge.isKnownButNotFired(org.bukkit.event.entity.EntityChangeBlockEvent.class));
        assertNotNull(HandlerList.getHandlerLists());
    }
}
