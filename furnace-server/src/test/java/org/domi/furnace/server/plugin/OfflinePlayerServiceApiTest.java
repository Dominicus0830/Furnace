// B6(불만 표 85·86·87번): 오프라인 플레이어 데이터·영속 데이터 편집, OP 레벨·밴·화이트리스트 API, 서비스 레지스트리.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.server.ServiceRegisterEvent;
import org.domi.furnace.api.event.server.ServiceUnregisterEvent;
import org.domi.furnace.api.persistence.PersistentDataType;
import org.domi.furnace.api.service.EconomyService;
import org.domi.furnace.api.service.PermissionService;
import org.domi.furnace.api.service.PlaceholderExpansion;
import org.domi.furnace.api.service.PlaceholderService;
import org.domi.furnace.api.service.ServicePriority;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerData;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfflinePlayerServiceApiTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        plugin.setEnabled(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        host.services().unregisterAll(plugin);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private void writeData(UUID id) {
        PlayerData.save(tmp, id, 1.5, 64, -3.5, 90f, 10f, true, new org.domi.furnace.server.entity.ItemStack[46]);
    }

    private <T> T get(java.util.concurrent.CompletableFuture<T> f) throws Exception {
        return f.get(30, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ 저장 데이터

    @Test
    void offlineEditWritesFileAndKeepsOtherFields() throws Exception {
        writeData(ID);
        var op = host.offlinePlayer(ID);
        assertFalse(op.isOnline());
        assertTrue(get(op.hasPlayedBefore()));
        assertEquals(Boolean.TRUE, get(op.editPersistentData(c -> {
            c.set("furn:coins", PersistentDataType.INTEGER, 7);
            c.set("furn:note", PersistentDataType.STRING, "hi");
        })));
        var view = get(op.persistentData());
        assertEquals(7, view.get("furn:coins", PersistentDataType.INTEGER));
        assertEquals("hi", view.get("furn:note", PersistentDataType.STRING));
        // 위치 등 다른 필드는 그대로, 이전 파일은 .dat_old
        var nbt = get(op.readNbt());
        assertEquals(64.0, nbt.getList("Pos").getDouble(1), 1e-9);
        assertTrue(java.nio.file.Files.exists(tmp.resolve("playerdata").resolve(ID + ".dat_old")));
        // 지우면 BukkitValues 키 자체가 사라진다
        assertEquals(Boolean.TRUE, get(op.editPersistentData(c -> { c.remove("furn:coins"); c.remove("furn:note"); })));
        assertTrue(get(op.persistentData()).isEmpty());
        assertFalse(get(op.readNbt()).contains("BukkitValues"));
    }

    @Test
    void missingFileIsNotCreatedAndFailingEditWritesNothing() throws Exception {
        var op = host.offlinePlayer(ID);
        assertFalse(get(op.hasPlayedBefore()));
        assertNull(get(op.readNbt()));
        assertNull(get(op.persistentData()));
        assertEquals(Boolean.FALSE, get(op.editPersistentData(c -> c.set("furn:a", PersistentDataType.INTEGER, 1))));
        assertFalse(java.nio.file.Files.exists(tmp.resolve("playerdata").resolve(ID + ".dat")));

        writeData(ID);
        var ex = assertThrows(java.util.concurrent.ExecutionException.class, () -> get(op.editPersistentData(c -> {
            c.set("furn:a", PersistentDataType.INTEGER, 1);
            throw new IllegalStateException("고장");
        })));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(get(op.persistentData()).isEmpty(), "예외가 나면 쓰지 않는다");
    }

    @Test
    void knownPlayersListsDataFiles() throws Exception {
        UUID b = UUID.randomUUID();
        writeData(ID);
        writeData(b);
        var ids = new ArrayList<UUID>();
        for (var p : get(host.knownPlayers())) ids.add(p.uniqueId());
        assertTrue(ids.containsAll(List.of(ID, b)));
        assertEquals(2, ids.size());
    }

    @Test
    void onlinePlayerEditGoesThroughTheLivePlayer() throws Exception {
        UUID id = UUID.randomUUID();
        writeData(id);
        Player p = new Player(core.world().entityIds().next(), id, "Live", new EmbeddedChannel());
        p.inventory(new PlayerInventory());
        core.world().playerOnline(p);
        var op = host.offlinePlayer(id);
        assertTrue(op.isOnline());
        assertEquals("Live", op.name());
        assertNotNull(op.player());
        var done = op.editPersistentData(c -> c.set("furn:x", PersistentDataType.INTEGER, 3));
        p.drainOwnerTasks();   // region 에 붙지 않은 시험 플레이어는 틱이 비우는 소유 스레드 작업을 직접 비운다
        assertEquals(Boolean.TRUE, get(done));
        // 접속자 메모리가 바뀌었고 파일은 건드리지 않았다(퇴장 저장이 쓴다)
        assertEquals(3, (int) p.withPersistentData(t -> t.getIntOr0("furn:x")));
        assertTrue(get(PlayerDataFile.rootOf(tmp, id)).getCompound("BukkitValues").tags().isEmpty());
        assertEquals(3, get(op.persistentData()).get("furn:x", PersistentDataType.INTEGER));
    }

    /** 파일 내용 보기. */
    private static final class PlayerDataFile {
        static java.util.concurrent.CompletableFuture<NbtCompound> rootOf(Path dir, UUID id) {
            return java.util.concurrent.CompletableFuture.completedFuture(PlayerData.readRoot(dir, id));
        }
    }

    // ------------------------------------------------------------------ OP·밴·화이트리스트

    @Test
    void opLevelBanAndWhitelistWriteTheServerLists() throws Exception {
        core.profileCache().add(new GameProfile(ID, "Steve"));
        var op = host.offlinePlayer(ID);
        assertEquals("Steve", op.name());
        assertEquals(op, host.offlinePlayerCached("steve"));
        assertNull(host.offlinePlayerCached("nobody-known"));

        assertEquals(0, op.opLevel());
        assertFalse(op.isOp());
        op.setOpLevel(3);
        assertEquals(3, op.opLevel());
        assertTrue(core.lists().getOps().isListed(new GameProfile(ID, "Steve")));
        assertEquals(List.of(op), host.access().operators());
        assertThrows(IllegalArgumentException.class, () -> op.setOpLevel(5));
        op.setOpLevel(0);
        assertFalse(op.isOp());
        assertTrue(host.access().operators().isEmpty());

        assertFalse(op.isBanned());
        Instant until = Instant.now().plusSeconds(3600);
        op.ban("규칙 위반", until, "tester");
        assertTrue(op.isBanned());
        var entry = op.banEntry();
        assertEquals("규칙 위반", entry.reason());
        assertEquals("tester", entry.source());
        assertEquals("Steve", entry.name());
        assertEquals(until.getEpochSecond(), entry.expires().getEpochSecond());
        assertEquals(1, host.access().bannedPlayers().size());
        assertTrue(op.unban());
        assertFalse(op.unban());
        assertNull(op.banEntry());

        assertFalse(op.isWhitelisted());
        op.setWhitelisted(true);
        assertTrue(op.isWhitelisted());
        assertEquals(List.of(op), host.access().whitelisted());
        op.setWhitelisted(false);
        assertFalse(op.isWhitelisted());
    }

    @Test
    void unknownUuidCannotBeWrittenButCanBeRead() {
        var op = host.offlinePlayer(UUID.randomUUID());
        assertNull(op.name());
        assertEquals(0, op.opLevel());
        assertFalse(op.isBanned());
        assertThrows(IllegalStateException.class, () -> op.setOpLevel(1));
        assertThrows(IllegalStateException.class, () -> op.ban("x"));
    }

    @Test
    void banKicksTheOnlinePlayerAndOpSendsLevel() throws Exception {
        UUID id = UUID.randomUUID();
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(core.world().entityIds().next(), id, "Kicked", ch);
        p.inventory(new PlayerInventory());
        core.world().playerOnline(p);
        var op = host.offlinePlayer(id);
        op.setOpLevel(2);
        ch.runPendingTasks();
        int afterOp = ch.outboundMessages().size();
        assertTrue(afterOp >= 2, "권한 레벨·명령 트리를 다시 보낸다: " + afterOp);
        op.ban("bye");
        ch.runPendingTasks();
        assertTrue(ch.outboundMessages().size() > afterOp, "내보내기(연결 끊기) 패킷이 나간다");
    }

    @Test
    void ipBansAndWhitelistSwitch() {
        var a = host.access();
        assertThrows(IllegalArgumentException.class, () -> a.banIp("not-an-ip", "x"));
        assertFalse(a.isIpBanned("203.0.113.9"));
        a.banIp("203.0.113.9", "scan", null, "tester");
        assertTrue(a.isIpBanned("203.0.113.9"));
        assertEquals("scan", a.bannedIps().get(0).reason());
        assertTrue(a.unbanIp("203.0.113.9"));
        assertFalse(a.unbanIp("203.0.113.9"));

        assertFalse(a.isWhitelistEnabled());
        a.setWhitelistEnabled(true);
        assertTrue(a.isWhitelistEnabled());
        a.setWhitelistEnabled(false);
        assertFalse(a.isWhitelistEnabled());
    }

    // ------------------------------------------------------------------ 서비스 레지스트리

    private static final class Perms implements PermissionService {
        final String n;

        Perms(String n) { this.n = n; }

        public String name() { return n; }

        public boolean has(UUID p, String node) { return true; }

        public List<String> groups(UUID p) { return List.of("default"); }
    }

    @Test
    void servicesPickByPriorityThenOrderAndFollowThePlugin() throws Exception {
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("a.jar"), "furnace-plugin.yml", "name: Alpha\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Alpha\n", TestPlugins.Alpha.class);
        PluginSystemTest.jar(dir.resolve("b.jar"), "furnace-plugin.yml", "name: Beta\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Beta\n", TestPlugins.Beta.class);
        PluginHost h = new PluginHost(core, dir);
        try {
            h.pluginsImpl().loadAll();
            h.pluginsImpl().enableAll();
            var alpha = h.plugins().plugin("Alpha");
            var beta = h.plugins().plugin("Beta");
            var reg = h.services();
            List<String> seen = new ArrayList<>();
            h.eventsImpl().subscribe(alpha, ServiceRegisterEvent.class, e -> seen.add("+" + e.registration().service().getSimpleName()));
            h.eventsImpl().subscribe(alpha, ServiceUnregisterEvent.class, e -> seen.add("-" + e.registration().service().getSimpleName()));
            Perms low = new Perms("low"), first = new Perms("first"), second = new Perms("second"), high = new Perms("high");
            assertNull(reg.load(PermissionService.class));
            reg.register(PermissionService.class, low, alpha, ServicePriority.LOW);
            reg.register(PermissionService.class, first, alpha);
            reg.register(PermissionService.class, second, beta);
            assertSame(first, reg.load(PermissionService.class), "같은 우선순위는 먼저 올린 쪽");
            reg.register(PermissionService.class, high, beta, ServicePriority.HIGH);
            assertSame(high, reg.load(PermissionService.class));
            assertEquals(List.of("high", "first", "second", "low"),
                    reg.registrations(PermissionService.class).stream().map(r -> ((Perms) r.provider()).n).toList());
            assertThrows(IllegalArgumentException.class, () -> reg.register(PermissionService.class, high, beta));
            assertEquals(2, reg.registrations(beta).size());
            assertTrue(reg.services().contains(PermissionService.class));

            h.pluginsImpl().disable(beta);   // 플러그인이 꺼지면 올린 것이 풀린다
            assertEquals(List.of("first", "low"), reg.registrations(PermissionService.class).stream().map(r -> ((Perms) r.provider()).n).toList());
            assertTrue(reg.unregister(PermissionService.class, first));
            assertFalse(reg.unregister(PermissionService.class, first));
            assertSame(low, reg.load(PermissionService.class));
            h.pluginsImpl().disable(alpha);
            assertNull(reg.load(PermissionService.class));
            assertTrue(reg.services().isEmpty());
            assertEquals(4, seen.stream().filter(s -> s.startsWith("+")).count());
            assertEquals(3, seen.stream().filter(s -> s.startsWith("-")).count(), "베타가 꺼질 때 둘 + 직접 해제 하나(알파가 꺼질 때는 알파 리스너가 먼저 풀려 마지막 해제는 듣지 못한다)");
        } finally {
            h.pluginsImpl().disableAll();
            h.pluginsImpl().closeAll();
            h.close();
        }
    }

    @Test
    void disabledPluginCannotRegister() {
        var never = new TestPlugins.Gamma();   // 켠 적 없는 플러그인
        assertThrows(IllegalStateException.class, () -> host.services().register(PermissionService.class, new Perms("x"), never));
    }

    @Test
    void economyTransferAndPlaceholderContractsWork() {
        // 표준 인터페이스의 기본 구현(이체 되돌리기·has)을 메모리 구현으로 확인
        var eco = new MemEconomy();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        eco.createAccount(a);
        eco.createAccount(b);
        eco.deposit(a, new BigDecimal("10.50"));
        assertTrue(eco.has(a, new BigDecimal("10.50")));
        assertTrue(eco.transfer(a, b, new BigDecimal("4.25")).success());
        assertEquals(new BigDecimal("6.25"), eco.balance(a));
        assertEquals(new BigDecimal("4.25"), eco.balance(b));
        assertFalse(eco.transfer(a, b, new BigDecimal("100")).success());
        assertEquals(new BigDecimal("6.25"), eco.balance(a));
        eco.failDepositFor = b;   // 입금이 실패하면 인출을 되돌린다
        assertFalse(eco.transfer(a, b, new BigDecimal("1")).success());
        eco.failDepositFor = null;
        assertEquals(new BigDecimal("6.25"), eco.balance(a));

        host.services().register(EconomyService.class, eco, plugin);
        PlaceholderService ph = new MemPlaceholders();
        host.services().register(PlaceholderService.class, ph, plugin);
        ph.register(plugin, new PlaceholderExpansion() {
            public String identifier() { return "eco"; }

            public String onRequest(UUID player, String params) { return params.equals("balance") && player != null ? eco.balance(player).toPlainString() : null; }
        });
        assertEquals("balance=6.25 %eco_unknown% %nope%", host.services().load(PlaceholderService.class).resolve(a, "balance=%eco_balance% %eco_unknown% %nope%"));
    }

    private static final class MemEconomy implements EconomyService {
        final java.util.Map<UUID, BigDecimal> m = new java.util.HashMap<>();
        UUID failDepositFor;

        public String name() { return "mem"; }

        public String currencyNameSingular() { return "coin"; }

        public String currencyNamePlural() { return "coins"; }

        public int fractionalDigits() { return 2; }

        public String format(BigDecimal a) { return a.toPlainString() + " coins"; }

        public boolean hasAccount(UUID p) { return m.containsKey(p); }

        public boolean createAccount(UUID p) { return m.putIfAbsent(p, BigDecimal.ZERO) == null; }

        public BigDecimal balance(UUID p) { return m.getOrDefault(p, BigDecimal.ZERO); }

        public Result withdraw(UUID p, BigDecimal amt) {
            if (amt.signum() < 0) throw new IllegalArgumentException("음수");
            if (!has(p, amt)) return Result.fail(amt, balance(p), "잔액 부족");
            m.put(p, balance(p).subtract(amt));
            return Result.ok(amt, balance(p));
        }

        public Result deposit(UUID p, BigDecimal amt) {
            if (amt.signum() < 0) throw new IllegalArgumentException("음수");
            if (p.equals(failDepositFor)) return Result.fail(amt, balance(p), "입금 실패");
            m.put(p, balance(p).add(amt));
            return Result.ok(amt, balance(p));
        }
    }

    private static final class MemPlaceholders implements PlaceholderService {
        final java.util.Map<String, PlaceholderExpansion> map = new java.util.TreeMap<>();

        public String name() { return "mem"; }

        public void register(org.domi.furnace.api.plugin.FurnacePlugin owner, PlaceholderExpansion e) {
            if (map.putIfAbsent(e.identifier(), e) != null) throw new IllegalArgumentException(e.identifier());
        }

        public boolean unregister(String id) { return map.remove(id) != null; }

        public java.util.Set<String> identifiers() { return map.keySet(); }

        public String resolveOne(UUID player, String ph) {
            int i = ph.indexOf('_');
            var e = map.get(i < 0 ? ph : ph.substring(0, i));
            return e == null ? null : e.onRequest(player, i < 0 ? "" : ph.substring(i + 1));
        }

        public String resolve(UUID player, String text) {
            var m = java.util.regex.Pattern.compile("%([^%\\s]+)%").matcher(text);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String v = resolveOne(player, m.group(1));
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v == null ? m.group() : v));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    }
}
