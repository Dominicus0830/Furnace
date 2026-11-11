package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bukkit 권한 체계(LuckPerms 같은 권한 플러그인이 기대는 모양): CraftHumanEntity.perm 의 PermissibleBase·첨부·유효 권한, 권한 객체를
 * 리플렉션으로 바꿔 끼우면 Furnace 쪽 판정도 그것을 따르는지, 접속 이벤트(AsyncPlayerPreLoginEvent·PlayerLoginEvent)와 RCON 이벤트,
 * getUnsafe·getOfflinePlayer·getOperators·getOnlineMode. 플러그인은 시험 안에서 컴파일한 진짜 jar 다.
 */
class BukkitPermissionsTest {

    private static final String YML = """
            name: PermProbe
            version: '1'
            main: pp.PermProbe
            api-version: '1.21'
            permissions:
              pp.parent:
                default: false
                children:
                  pp.kid: true
              pp.everyone:
                default: true
            """;

    private static final String PLUGIN = """
            package pp;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class PermProbe extends JavaPlugin {
                @Override public void onEnable() {
                    WiringProbe.plugin = this;
                    getServer().getPluginManager().registerEvents(new Events(), this);
                }
            }
            """;

    /** LuckPerms BukkitConnectionListener 처럼: 로그인 LOWEST 에서 perm 필드를 자기 하위 클래스로 바꾼다. */
    private static final String EVENTS = """
            package pp;
            import java.lang.reflect.Field;
            import org.bukkit.command.RemoteConsoleCommandSender;
            import org.bukkit.entity.Player;
            import org.bukkit.event.*;
            import org.bukkit.event.player.*;
            import org.bukkit.event.server.*;
            import org.bukkit.permissions.PermissibleBase;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class Events implements Listener {
                @EventHandler(priority = EventPriority.LOWEST)
                public void pre(AsyncPlayerPreLoginEvent e) {
                    WiringProbe.LOG.add("pre:" + e.getName() + ":" + e.isAsynchronous() + ":" + e.getLoginResult() + ":" + (e.getUniqueId() != null));
                    if (e.getName().equals("Early")) e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "pre-denied");
                }
                @EventHandler(priority = EventPriority.LOWEST)
                public void login(PlayerLoginEvent e) {
                    Player p = e.getPlayer();
                    WiringProbe.LOG.add("login:" + p.getName() + ":" + e.getResult() + ":" + e.getHostname().contains(":") + ":" + (e.getAddress() != null));
                    if (p.getName().equals("Late")) e.disallow(PlayerLoginEvent.Result.KICK_OTHER, "login-denied");
                    if (p.getName().equals("Injected")) {
                        try {
                            Field f = Class.forName("org.bukkit.craftbukkit.entity.CraftHumanEntity").getDeclaredField("perm");
                            f.setAccessible(true);
                            PermissibleBase old = (PermissibleBase) f.get(p);
                            WiringProbe.LOG.add("old-exact:" + (old.getClass() == PermissibleBase.class));
                            f.set(p, new Custom(p));
                        } catch (ReflectiveOperationException ex) { WiringProbe.failure = ex; }
                    }
                }
                @EventHandler(priority = EventPriority.MONITOR)
                public void loginMonitor(PlayerLoginEvent e) { WiringProbe.LOG.add("login-monitor:" + e.getPlayer().getName() + ":" + e.getResult()); }
                @EventHandler public void join(PlayerJoinEvent e) { WiringProbe.LOG.add("join:" + e.getPlayer().getName()); }
                @EventHandler public void quit(PlayerQuitEvent e) { WiringProbe.LOG.add("quit:" + e.getPlayer().getName()); }
                @EventHandler public void rcon(RemoteServerCommandEvent e) {
                    WiringProbe.LOG.add("rcon:" + e.getSender().getName() + ":" + (e.getSender() instanceof RemoteConsoleCommandSender) + ":" + e.getCommand());
                    if (e.getCommand().equals("stopme")) e.setCancelled(true);
                }
                @EventHandler public void console(ServerCommandEvent e) { WiringProbe.LOG.add("console:" + e.getCommand()); }
            }
            """;

    private static final String CUSTOM = """
            package pp;
            import org.bukkit.permissions.*;
            public final class Custom extends PermissibleBase {
                public Custom(ServerOperator op) { super(op); }
                @Override public boolean hasPermission(String n) { return n.equals("custom.yes"); }
                @Override public boolean isPermissionSet(String n) { return n.startsWith("custom."); }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("pp.jar"), YML, Map.of("pp.PermProbe", PLUGIN, "pp.Events", EVENTS, "pp.Custom", CUSTOM));
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    private static List<String> log(String prefix) { return WiringProbe.LOG.stream().filter(l -> l.startsWith(prefix)).toList(); }

    @Test
    void attachmentsReachBothBukkitAndFurnaceChecks() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Plain");
            server.tick(2);
            Plugin plugin = WiringProbe.plugin;
            assertNotNull(plugin);
            CraftPlayer cp = (CraftPlayer) Bukkit.getPlayer("Plain");
            assertSame(PermissibleBase.class, cp.permissible().getClass(), "CraftBukkit 처럼 정확히 PermissibleBase(LuckPerms 가 이것을 확인한다)");
            Field perm = CraftHumanEntity.class.getDeclaredField("perm");
            assertSame(PermissibleBase.class, perm.getType());
            assertNotNull(PermissibleBase.class.getDeclaredField("attachments"));
            assertNotNull(PermissibleBase.class.getDeclaredField("permissions"));
            assertNotNull(PermissionAttachment.class.getDeclaredField("permissions"));

            assertFalse(cp.hasPermission("pp.kid"));
            assertFalse(tp.player().hasPermission("pp.kid"));
            PermissionAttachment a = cp.addAttachment(plugin, "pp.parent", true);
            assertTrue(cp.hasPermission("pp.parent"));
            assertTrue(cp.hasPermission("pp.kid"), "자식 권한");
            assertTrue(tp.player().hasPermission("pp.kid"), "Furnace 쪽 판정에도 같은 첨부가 들어간다");
            assertTrue(cp.getEffectivePermissions().stream().anyMatch(i -> i.getPermission().equals("pp.kid") && i.getValue() && i.getAttachment() == a));
            a.setPermission("pp.kid", false);
            assertFalse(cp.hasPermission("pp.kid"));
            assertFalse(tp.player().hasPermission("pp.kid"));
            assertTrue(a.remove());
            assertFalse(cp.hasPermission("pp.parent"));
            assertFalse(tp.player().hasPermission("pp.parent"), "떼면 Furnace 쪽에서도 빠진다");
            assertFalse(a.remove(), "두 번째 remove 는 false(Bukkit)");
            assertThrows(IllegalArgumentException.class, () -> cp.removeAttachment(a), "이미 뗀 첨부");
            for (PermissionAttachmentInfo i : cp.getEffectivePermissions()) assertNull(i.getAttachment(), "남은 것은 기본값뿐: " + i.getPermission());

            List<String> plainLog = WiringProbe.LOG.stream().filter(l -> l.contains("Plain")).toList();
            assertEquals(List.of("pre:Plain:true:ALLOWED:true", "login:Plain:ALLOWED:true:true", "login-monitor:Plain:ALLOWED", "join:Plain"), plainLog,
                    "Bukkit 순서: 사전 로그인(비동기) → 로그인(Player 있음) → 입장");
        }
    }

    /** 독립 검토 지적: 재접속한 새 CraftPlayer 가 옛 객체(같은 UUID)에 밀려 권한 구독에서 빠지면 Bukkit.broadcast(메시지, 권한)를 못 받는다. */
    @Test
    void rejoinedPlayerIsSubscribedAndEqualsIsPerSession() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer first = server.join("Again");
            server.tick(2);
            Player old = Bukkit.getPlayerExact("Again");
            assertTrue(Bukkit.getPluginManager().getPermissionSubscriptions("pp.everyone").contains(old));
            first.close();
            server.tick(2);
            server.join("Again");
            server.tick(2);
            Player now = Bukkit.getPlayerExact("Again");
            assertFalse(now == old, "재접속은 새 객체");
            assertFalse(now.equals(old), "CraftBukkit 처럼 다른 접속의 CraftPlayer 는 같지 않다");
            assertEquals(now, Bukkit.getOfflinePlayer(now.getUniqueId()));
            assertTrue(Bukkit.getPluginManager().getPermissionSubscriptions("pp.everyone").stream().anyMatch(x -> x == now),
                    "새 객체가 구독에 들어 있다");
            assertFalse(Bukkit.getPluginManager().getPermissionSubscriptions("pp.everyone").stream().anyMatch(x -> x == old),
                    "옛 객체는 걷혔다");
            assertEquals(97 * 5 + now.getUniqueId().hashCode(), now.hashCode(), "CraftOfflinePlayer 와 같은 해시 식");
            OfflinePlayer ghost = Bukkit.getOfflinePlayer("Ghost");
            assertEquals(97 * 5 + ghost.getUniqueId().hashCode(), ghost.hashCode());
        }
    }

    @Test
    void swappedPermissibleDecidesFurnaceChecksToo() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer plain = server.join("Plain");
            TestPlayer injected = server.join("Injected");
            server.tick(2);
            assertNull(WiringProbe.failure, () -> "주입 실패: " + WiringProbe.failure);
            assertEquals(List.of("old-exact:true"), log("old-exact:"));
            CraftPlayer cp = (CraftPlayer) Bukkit.getPlayer("Injected");
            assertEquals("pp.Custom", cp.permissible().getClass().getName());
            assertTrue(cp.hasPermission("custom.yes"));
            assertFalse(cp.hasPermission("custom.no"));
            assertTrue(injected.player().hasPermission("custom.yes"), "Furnace 판정도 바꿔 끼운 객체에 묻는다");
            assertFalse(injected.player().hasPermission("custom.no"));
            assertTrue(injected.player().isPermissionSet("custom.no"));
            assertFalse(plain.player().hasPermission("custom.yes"), "다른 플레이어는 그대로");
            assertFalse(plain.player().isPermissionSet("custom.no"));
        }
    }

    @Test
    void loginEventsCanDenyAndDeniedPlayersNeverJoin() throws Exception {
        try (FurnaceTestServer server = start()) {
            IllegalStateException early = assertThrows(IllegalStateException.class, () -> server.join("Early"));
            assertTrue(String.valueOf(early.getCause()).contains("pre-denied") || String.valueOf(early.getMessage()).contains("Early"));
            IllegalStateException late = assertThrows(IllegalStateException.class, () -> server.join("Late"));
            assertNotNull(late);
            server.tick(2);
            assertNull(Bukkit.getPlayerExact("Early"));
            assertNull(Bukkit.getPlayerExact("Late"));
            assertEquals(List.of("pre:Early:true:ALLOWED:true"), WiringProbe.LOG.stream().filter(l -> l.contains("Early")).toList(),
                    "사전 로그인에서 거부 — 로그인·입장 없음");
            assertEquals(List.of("pre:Late:true:ALLOWED:true", "login:Late:ALLOWED:true:true", "login-monitor:Late:KICK_OTHER"),
                    WiringProbe.LOG.stream().filter(l -> l.contains("Late")).toList(), "로그인에서 거부 — 입장·퇴장 없음");
            server.join("After");   // 뒤 접속은 정상
            server.tick(2);
            assertNotNull(Bukkit.getPlayerExact("After"));
        }
    }

    @Test
    void rconCommandsFireRemoteServerCommandEventOnly() throws Exception {
        try (FurnaceTestServer server = start()) {
            String out = server.internals().runRconCommand("scoreboard objectives add rc dummy");
            assertTrue(out.contains("rc"), out);
            assertEquals("", server.internals().runRconCommand("stopme"), "취소하면 빈 응답");
            assertEquals(List.of("rcon:Rcon:true:scoreboard objectives add rc dummy", "rcon:Rcon:true:stopme"), log("rcon:"));
            assertEquals(List.of(), log("console:"), "RCON 명령은 ServerCommandEvent 가 아니다(Bukkit 처리기 목록이 따로다)");
        }
    }

    @Test
    void unsafeValuesAndOfflinePlayers() throws Exception {
        try (FurnaceTestServer server = start()) {
            var unsafe = Bukkit.getUnsafe();
            assertEquals(3955, unsafe.getDataVersion(), "1.21.1 SharedConstants.WORLD_VERSION");
            assertEquals(767, unsafe.getProtocolVersion());
            assertTrue(unsafe.isSupportedApiVersion("1.21"));
            assertTrue(unsafe.isSupportedApiVersion("1.13"));
            assertFalse(unsafe.isSupportedApiVersion("1.22"));
            assertFalse(unsafe.isSupportedApiVersion(null));
            assertEquals(Bukkit.getWorlds().get(0).getName(), unsafe.getMainLevelName(), "오버월드 이름");

            assertFalse(Bukkit.getOnlineMode(), "시험 서버는 오프라인");
            server.join("Nora");
            server.tick(2);
            Player nora = Bukkit.getPlayerExact("Nora");
            assertSame(nora, Bukkit.getOfflinePlayer("nora"), "접속 중이면 그 Player");
            assertSame(nora, Bukkit.getOfflinePlayer(nora.getUniqueId()));
            OfflinePlayer ghost = Bukkit.getOfflinePlayer("Ghost");
            assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Ghost".getBytes(StandardCharsets.UTF_8)), ghost.getUniqueId(), "오프라인 UUID");
            assertEquals("Ghost", ghost.getName());
            assertFalse(ghost.isOnline());
            assertNull(ghost.getPlayer());
            assertEquals(ghost, Bukkit.getOfflinePlayer(ghost.getUniqueId()), "같음은 UUID 로");
            assertThrows(IllegalArgumentException.class, () -> Bukkit.getOfflinePlayer(""));
            assertFalse(Bukkit.getOperators().contains(nora));
            var opOut = server.execute("op @a[name=Nora]");   // 시험 서버는 이름 캐시가 없어 이름으로 주면 바닐라처럼 소문자 오프라인 UUID 가 된다
            server.tick();
            assertTrue(Bukkit.getOperators().stream().anyMatch(o -> o.getUniqueId().equals(nora.getUniqueId())), () -> "op 결과: " + opOut + " / " + Bukkit.getOperators());
            assertInstanceOf(org.bukkit.craftbukkit.CraftOfflinePlayer.class, ghost);
        }
    }
}
