package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.domi.furnace.server.net.ConnectionLimits;
import org.domi.furnace.server.net.protocol.ConnectionState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class OptionKeysTest {

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return p;
    }

    private static List<String> audit(String... kv) {
        List<String> w = new ArrayList<>();
        OptionKeys.audit(props(kv), w::add);
        return w;
    }

    private static Map<String, OptionKeys.Option> table() {
        return OptionKeys.all().stream().collect(Collectors.toMap(OptionKeys.Option::key, o -> o));
    }

    @Test
    void connectionDefenceKeysAreAllInTable() {
        for (String k : ConnectionLimits.knownKeys()) assertTrue(OptionKeys.isKnown(k), "키 표에 없음: " + k);
        // 반대로 키 표의 connection.*·limits.*·proxy-protocol* 키는 로더가 실제로 읽는다
        for (String k : OptionKeys.keys()) {
            if (k.startsWith("connection.") || k.startsWith("limits.") || k.startsWith("proxy-protocol")) {
                assertTrue(ConnectionLimits.knownKeys().contains(k), "로더가 읽지 않는 키: " + k);
            }
        }
    }

    @Test
    void tableDefaultsMatchTheCode() {
        Map<String, OptionKeys.Option> t = table();
        Config c = Config.DEFAULT;
        assertEquals(Long.toString(c.tickNanos()), t.get("tick-nanos").def());
        assertEquals(Integer.toString(c.entityViewDistance()), t.get("entity-view-distance").def());
        assertEquals(Integer.toString(c.regionSectionShift()), t.get("region-section-shift").def());
        assertEquals(Integer.toString(c.regionMergeRadius()), t.get("region-merge-radius").def());
        assertEquals(Integer.toString(c.ioThreads()), t.get("io-threads").def());
        assertEquals(Integer.toString(c.autosaveTicks()), t.get("autosave-ticks").def());
        assertEquals(Integer.toString(c.chunkEvictionGraceTicks()), t.get("chunk-eviction-grace-ticks").def());
        assertEquals(Integer.toString(c.spawnChunkRadius()), t.get("spawn-chunk-radius").def());
        assertEquals(Integer.toString(c.chunksPerTick()), t.get("chunks-per-tick").def());
        assertEquals(Boolean.toString(c.bungeecord()), t.get("bungeecord").def());
        RegionPoolSettings rp = RegionPoolSettings.DEFAULT;
        assertEquals(Boolean.toString(rp.shared()), t.get("shared-region-pool").def());
        assertEquals(Integer.toString(rp.dimCap()), t.get("region-pool-dim-cap").def());
        assertEquals(Integer.toString(rp.starveTicks()), t.get("region-pool-starve-ticks").def());
        OperatorOptions o = OperatorOptions.DEFAULT;
        assertEquals(Boolean.toString(o.strictChatOrder()), t.get("chat.strict-order").def());
        assertEquals(Boolean.toString(o.unsignedChatAsSystem()), t.get("chat.unsigned-as-system").def());
        assertEquals(o.serverBrand(), t.get("server-brand").def());
        assertEquals(Boolean.toString(o.announceSleepStatus()), t.get("announce-sleep-status").def());
        assertEquals(Boolean.toString(o.confirmInterrupt()), t.get("confirm-interrupt").def());
        assertEquals(Integer.toString(o.interruptConfirmSeconds()), t.get("confirm-interrupt-seconds").def());
        ConnectionLimits d = ConnectionLimits.DEFAULT;
        assertEquals(Integer.toString(d.readTimeoutSeconds()), t.get("connection.read-timeout-seconds").def());
        assertEquals(Integer.toString(d.chatLength()), t.get("limits.chat-length").def());
        assertEquals(Integer.toString(d.commandLength()), t.get("limits.command-length").def());
        assertEquals(Integer.toString(d.displayNameLength()), t.get("limits.item.display-name").def());
        assertEquals(Integer.toString(d.bookAuthorLength()), t.get("limits.item.book-author").def());
        assertEquals(Boolean.toString(d.proxyProtocol()), t.get("proxy-protocol").def());
        for (ConnectionState s : new ConnectionState[] {ConnectionState.HANDSHAKE, ConnectionState.STATUS, ConnectionState.LOGIN,
                ConnectionState.CONFIGURATION, ConnectionState.PLAY}) {
            String n = s.name().toLowerCase();
            var l = d.of(s);
            assertEquals(Integer.toString(l.packetsPerSecond()), t.get("connection." + n + ".packets-per-second").def());
            assertEquals(Integer.toString(l.packetsBurst()), t.get("connection." + n + ".packets-burst").def());
            if (!n.equals("play")) assertEquals(Integer.toString(l.timeoutSeconds()), t.get("connection." + n + ".timeout-seconds").def());
        }
    }

    @Test
    void defaultsRaiseNoWarning() {
        assertEquals(List.of(), audit());
    }

    @Test
    void typoKeyGetsASuggestion() {
        List<String> w = audit("chat.strict-ordr", "false", "bungeecord", "false");
        assertEquals(1, w.size());
        assertTrue(w.get(0).contains("chat.strict-ordr") && w.get(0).contains("chat.strict-order"), w.get(0));
    }

    @Test
    void familyPrefixesAndVanillaKeysAreNotTypos() {
        assertEquals(List.of(), audit("plugins.log-level.Foo", "INFO", "dimension-seed.the_end", "5", "view-distance", "10"));
        assertEquals(1, audit("plugins.log-level.", "INFO").size(), "이름 없는 접두는 오타");
    }

    @Test
    void riskyValuesWarn() {
        assertTrue(audit("proxy-protocol", "true").get(0).contains("proxy-protocol.trusted"));
        assertEquals(List.of(), audit("proxy-protocol", "true", "proxy-protocol.trusted", "10.0.0.1"));
        assertTrue(audit("plugins.dev-mode", "true").get(0).contains("dev-mode"));
        assertTrue(audit("tick-nanos", "25000000").get(0).contains("tick-nanos"));
        assertEquals(List.of(), audit("tick-nanos", "50000000"));
        assertTrue(audit("connection.play.packets-per-second", "0").get(0).contains("play"));
        assertTrue(audit("connection.login.timeout-seconds", "0").get(0).contains("login"));
    }

    @Test
    void pluginBooleansAreStrict() {
        assertThrows(IllegalArgumentException.class, () -> org.domi.furnace.server.config.OptionKeys.strictBool(props("plugins.dev-mode", "yes"), "plugins.dev-mode", false));
        assertTrue(OptionKeys.strictBool(props("a", " TRUE "), "a", false));
        assertFalse(OptionKeys.strictBool(props(), "a", false));
    }

    /** 문서의 키 표가 코드와 어긋나지 않는다(키를 추가하고 문서를 안 고치면 실패). */
    @Test
    void docTableListsEveryKey() throws Exception {
        Path doc = null;
        for (String base : new String[] {"docs", "../docs"}) {
            Path c = Path.of(base, "feature-parity", "option-keys-2026-11-07.md");
            if (Files.exists(c)) doc = c;
        }
        Assumptions.assumeTrue(doc != null, "문서를 찾지 못함(작업 디렉터리)");
        String text = Files.readString(doc);
        for (String k : OptionKeys.keys()) {
            if (k.startsWith("connection.") && k.split("\\.").length == 3) continue;   // 단계별 키는 문서에 connection.<단계>.* 로 묶는다
            assertTrue(text.contains("`" + k + "`"), "문서 키 표에 없음: " + k);
        }
    }
}
