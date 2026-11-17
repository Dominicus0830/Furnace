package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * D1(2026-11-15): 플러그인이 리플렉션으로 찾다 없는 NMS 클래스 알림. ProtocolLib 은 첫 접속 때 Spigot 이름 → Mojang 이름 → 옛 이름을 차례로 찔러 보며
 * 21개 이름에서 ClassNotFoundException 을 받는다(Paper 1.21.1 도 같고 아무것도 찍지 않는다). 기본(summary)은 플러그인마다 한 줄, each 는 이름마다 한 줄.
 */
class NmsMissingClassLogTest {

    private static final String PLUGIN = "MissLogProbe";
    /** 도미니코 기동 로그(11-14)에서 ProtocolLib 이 첫 접속 때 찔러 본 이름 일부 — 모두 Spigot 이름이고 Mojang 이름으로는 있다. */
    private static final List<String> SPIGOT_ALIASES = List.of(
            "net.minecraft.world.level.World", "net.minecraft.network.syncher.DataWatcher", "net.minecraft.nbt.NBTBase",
            "net.minecraft.nbt.NBTTagCompound", "net.minecraft.network.syncher.DataWatcher$Item", "net.minecraft.network.protocol.status.ServerPing",
            "net.minecraft.stats.Statistic", "net.minecraft.world.level.block.state.IBlockData", "net.minecraft.network.NetworkManager",
            "net.minecraft.server.network.PlayerConnection", "net.minecraft.network.chat.IChatBaseComponent");

    private Logger furnace;
    private Level oldLevel;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        NmsReflect.resetWarnings();
        NmsReflect.resetStartupMisses();
        furnace = (Logger) LoggerFactory.getLogger("Furnace");
        oldLevel = furnace.getLevel();
        furnace.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        furnace.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        furnace.detachAppender(logs);
        furnace.setLevel(oldLevel);
        NmsReflect.resetWarnings();
        NmsReflect.resetStartupMisses();
    }

    private List<String> warnings() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> debugs() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.DEBUG).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void missesWhileStartingBecomeOneLineAtEnable() {
        NmsReflect.classMissing(PLUGIN, "net.minecraft.network.EnumProtocol");
        NmsReflect.classMissing(PLUGIN, "net.minecraft.network.protocol.EnumProtocolDirection");
        assertEquals(List.of(), warnings(), "켜는 중에는 모은다");
        NmsReflect.pluginEnabled(PLUGIN);
        List<String> w = warnings();
        assertEquals(1, w.size(), () -> String.join("\n", w));
        assertTrue(w.get(0).contains("while starting") && w.get(0).contains("looked up 2 NMS classes") && w.get(0).contains("EnumProtocol"), w.get(0));
    }

    @Test
    void missesAfterEnableAreSummarisedInOneLineWithHowToSeeEveryName() {
        NmsReflect.pluginEnabled(PLUGIN);
        for (String n : SPIGOT_ALIASES) NmsReflect.classMissing(PLUGIN, n);
        for (String n : SPIGOT_ALIASES) NmsReflect.classMissing(PLUGIN, n);   // 같은 이름을 다시 찔러도 다시 세지 않는다
        assertEquals(List.of(), warnings(), "켠 뒤 실패는 잠시 모은다(이름마다 경고하지 않는다)");
        NmsReflect.flushNow(PLUGIN);
        List<String> w = warnings();
        assertEquals(1, w.size(), () -> String.join("\n", w));
        String line = w.get(0);
        assertTrue(line.startsWith("Plugin " + PLUGIN + " looked up 11 NMS classes"), line);
        assertTrue(line.contains("net.minecraft.world.level.World") && line.contains("and 6 more"), "앞 몇 개만 보여 준다: " + line);
        assertTrue(!line.contains("IChatBaseComponent"), "나머지 이름은 요약 줄에 없다: " + line);
        assertTrue(line.contains(NmsReflect.MISSING_CLASS_LOG_KEY + "=each"), "이름을 모두 보는 설정을 알린다: " + line);
        assertTrue(!line.contains("DEBUG"), "운영자가 켤 수 없는 DEBUG 로그는 안내하지 않는다(검토 R3): " + line);
        assertTrue(debugs().stream().anyMatch(d -> d.contains("전체 11개") && d.contains("IChatBaseComponent")), "전체 이름은 DEBUG 한 줄");

        NmsReflect.flushNow(PLUGIN);
        assertEquals(1, warnings().size(), "알린 뒤에는 새 실패가 없으면 다시 알리지 않는다");
        NmsReflect.classMissing(PLUGIN, "net.minecraft.world.level.WorldType");
        NmsReflect.flushNow(PLUGIN);
        assertEquals(2, warnings().size(), "새 이름은 다음 묶음 한 줄로");
        assertTrue(warnings().get(1).contains("looked up 1 NMS classes") && warnings().get(1).contains("WorldType"), warnings().get(1));
    }

    @Test
    void summaryIsLoggedByItselfShortlyAfterABurst() throws Exception {
        NmsReflect.pluginEnabled(PLUGIN);
        for (String n : SPIGOT_ALIASES) NmsReflect.classMissing(PLUGIN, n);
        long deadline = System.nanoTime() + (NmsReflect.BATCH_MILLIS + 10_000) * 1_000_000L;
        while (warnings().isEmpty() && System.nanoTime() < deadline) Thread.sleep(50);
        List<String> w = warnings();
        assertEquals(1, w.size(), () -> "묶음이 끝나면 저절로 한 줄: " + w);
        assertTrue(w.get(0).contains("looked up 11 NMS classes"), w.get(0));
    }

    @Test
    void eachModeWarnsPerNameWhileStartingAndAfterEnable() {
        NmsReflect.missingClassLog(PLUGIN, NmsReflect.MissingClassLog.parse(" EACH "));
        NmsReflect.classMissing(PLUGIN, "net.minecraft.network.EnumProtocol");
        assertEquals(1, warnings().size(), "each 면 켜는 중 실패도 이름마다 바로(검토 R2)");
        assertTrue(warnings().get(0).contains("net.minecraft.network.EnumProtocol"), warnings().get(0));
        NmsReflect.pluginEnabled(PLUGIN);
        assertEquals(1, warnings().size(), "켜기 끝에 모아 둔 것이 없다");
        NmsReflect.classMissing(PLUGIN, "net.minecraft.nbt.NBTBase");
        NmsReflect.classMissing(PLUGIN, "net.minecraft.nbt.NBTTagCompound");
        NmsReflect.classMissing(PLUGIN, "net.minecraft.nbt.NBTBase");
        List<String> w = warnings();
        assertEquals(3, w.size(), () -> String.join("\n", w));
        assertTrue(w.get(1).contains("looked up the NMS class net.minecraft.nbt.NBTBase by reflection"), w.get(1));
        NmsReflect.missingClassLog(PLUGIN, NmsReflect.MissingClassLog.SUMMARY);
        NmsReflect.classMissing(PLUGIN, "net.minecraft.nbt.NBTTagList");
        assertEquals(3, warnings().size(), "summary 로 돌리면 다시 모은다");
        assertNull(NmsReflect.MissingClassLog.parse("loud"), "모르는 값");
    }

    @Test
    void gradeCClassesStillWarnOneByOne() {
        NmsReflect.pluginEnabled(PLUGIN);
        String gradeC = "net.minecraft.world.level.levelgen.NoiseChunk";
        assertTrue(NmsGrades.gradeC(gradeC.replace('.', '/')) != null, "시험 전제: 등급 C 클래스");
        NmsReflect.classMissing(PLUGIN, gradeC);
        List<String> w = warnings();
        assertEquals(1, w.size(), "Paper 에는 있는 클래스라 바로 하나씩 경고한다");
        assertTrue(w.get(0).contains(gradeC) && w.get(0).contains("Grade C"), w.get(0));
    }

    @Test
    void otherVersionModuleProbesStayAtDebug() {
        NmsReflect.pluginEnabled(PLUGIN);
        NmsReflect.classMissing(PLUGIN, "net.minecraft.server.v1_8_R3.MinecraftServer");
        NmsReflect.flushNow(PLUGIN);
        assertEquals(List.of(), warnings());
    }

    @Test
    void namesAlreadyReportedAreNotReportedAgain() {
        NmsReflect.pluginEnabled(PLUGIN);
        NmsReflect.classMissing(PLUGIN, "net.minecraft.nbt.NBTBase");
        NmsReflect.flushNow(PLUGIN);
        assertEquals(1, warnings().size());
        NmsReflect.classMissing(PLUGIN, "net.minecraft.nbt.NBTBase");   // 알린 이름을 다시 찌른다(ProtocolLib 은 접속마다 같은 이름을 찔러 본다)
        NmsReflect.flushNow(PLUGIN);
        assertEquals(1, warnings().size(), "알린 이름은 다음 묶음에 다시 나오지 않는다");
    }

    @Test
    void pluginThatFailsBeforeEnableStillGetsOneLineWhenDisabled() {
        NmsReflect.classMissing(PLUGIN, "net.minecraft.network.EnumProtocol");
        assertEquals(List.of(), warnings());
        NmsReflect.pluginDisabled(PLUGIN);   // onEnable 에서 예외로 켜지지 못하고 꺼졌다
        List<String> w = warnings();
        assertEquals(1, w.size(), () -> String.join("\n", w));
        assertTrue(w.get(0).contains("while starting") && w.get(0).contains("EnumProtocol"), w.get(0));
    }

    /** 검토 S1: 끄고 바로 다시 켜면(핫 리로드) 지난 기간의 예약 알림이 새로 켜는 중의 이름을 "켜지는 동안" 표시 없이 일찍 알리지 않는다. */
    @Test
    void staleScheduledSummaryFromBeforeReloadDoesNotFire() throws Exception {
        NmsReflect.pluginEnabled(PLUGIN);
        NmsReflect.classMissing(PLUGIN, "net.minecraft.nbt.NBTBase");   // 예약이 걸린다
        NmsReflect.pluginDisabled(PLUGIN);                              // 남은 것을 알리고 끈다
        assertEquals(1, warnings().size());
        NmsReflect.classMissing(PLUGIN, "net.minecraft.network.EnumProtocol");   // 다시 켜는 중
        Thread.sleep(NmsReflect.BATCH_MILLIS + 700);
        assertEquals(1, warnings().size(), () -> "지난 예약이 새 기간의 이름을 알렸다: " + warnings());
        NmsReflect.pluginEnabled(PLUGIN);
        assertEquals(2, warnings().size());
        assertTrue(warnings().get(1).contains("while starting") && warnings().get(1).contains("EnumProtocol"), warnings().get(1));
    }

    /** 검토 R1: 모으는 동안 다른 스레드가 알려도(예약 알림·켜기 끝) 이름이 사라지지 않는다. */
    @Test
    void namesAddedWhileAnotherThreadFlushesAreNotLost() throws Exception {
        NmsReflect.pluginEnabled(PLUGIN);
        int threads = 4, per = 20_000;
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            int tt = t;
            ts[t] = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < per; i++) NmsReflect.classMissing(PLUGIN, "net.minecraft.world.level.Race" + tt + "_" + i);
            });
            ts[t].start();
        }
        go.countDown();
        boolean alive = true;
        while (alive) {
            NmsReflect.flushNow(PLUGIN);
            alive = false;
            for (Thread t : ts) alive |= t.isAlive();
        }
        for (Thread t : ts) t.join();
        NmsReflect.flushNow(PLUGIN);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("전체 (\\d+)개");
        int reported = 0;
        for (String d : debugs()) {
            java.util.regex.Matcher m = p.matcher(d);
            if (m.find()) reported += Integer.parseInt(m.group(1));
        }
        assertEquals(threads * per, reported, "요약에 들어간 이름 수");
    }
}
