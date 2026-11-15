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
        assertTrue(w.get(0).contains("켜지는 동안") && w.get(0).contains("2개") && w.get(0).contains("EnumProtocol"), w.get(0));
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
        assertTrue(line.startsWith("플러그인 " + PLUGIN + " 이(가) 리플렉션으로 찾은 NMS 클래스 11개"), line);
        assertTrue(line.contains("net.minecraft.world.level.World") && line.contains("외 6개"), "앞 몇 개만 보여 준다: " + line);
        assertTrue(!line.contains("IChatBaseComponent"), "나머지 이름은 요약 줄에 없다: " + line);
        assertTrue(line.contains(NmsReflect.MISSING_CLASS_LOG_KEY + "=each") && line.contains("DEBUG"), "이름을 보는 길을 알린다: " + line);
        assertTrue(debugs().stream().anyMatch(d -> d.contains("전체 11개") && d.contains("IChatBaseComponent")), "전체 이름은 DEBUG 한 줄");

        NmsReflect.flushNow(PLUGIN);
        assertEquals(1, warnings().size(), "알린 뒤에는 새 실패가 없으면 다시 알리지 않는다");
        NmsReflect.classMissing(PLUGIN, "net.minecraft.world.level.WorldType");
        NmsReflect.flushNow(PLUGIN);
        assertEquals(2, warnings().size(), "새 이름은 다음 묶음 한 줄로");
        assertTrue(warnings().get(1).contains("1개") && warnings().get(1).contains("WorldType"), warnings().get(1));
    }

    @Test
    void summaryIsLoggedByItselfShortlyAfterABurst() throws Exception {
        NmsReflect.pluginEnabled(PLUGIN);
        for (String n : SPIGOT_ALIASES) NmsReflect.classMissing(PLUGIN, n);
        long deadline = System.nanoTime() + (NmsReflect.BATCH_MILLIS + 10_000) * 1_000_000L;
        while (warnings().isEmpty() && System.nanoTime() < deadline) Thread.sleep(50);
        List<String> w = warnings();
        assertEquals(1, w.size(), () -> "묶음이 끝나면 저절로 한 줄: " + w);
        assertTrue(w.get(0).contains("11개"), w.get(0));
    }

    @Test
    void eachModeWarnsPerNameAfterEnable() {
        NmsReflect.missingClassLog(PLUGIN, NmsReflect.MissingClassLog.parse(" EACH "));
        NmsReflect.classMissing(PLUGIN, "net.minecraft.network.EnumProtocol");
        assertEquals(List.of(), warnings(), "켜는 중 실패는 each 여도 켜기 끝에 한 줄");
        NmsReflect.pluginEnabled(PLUGIN);
        assertEquals(1, warnings().size());
        NmsReflect.classMissing(PLUGIN, "net.minecraft.nbt.NBTBase");
        NmsReflect.classMissing(PLUGIN, "net.minecraft.nbt.NBTTagCompound");
        NmsReflect.classMissing(PLUGIN, "net.minecraft.nbt.NBTBase");
        List<String> w = warnings();
        assertEquals(3, w.size(), () -> String.join("\n", w));
        assertTrue(w.get(1).contains("NMS 클래스 net.minecraft.nbt.NBTBase 를 리플렉션으로 찾았으나"), w.get(1));
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
        assertTrue(w.get(0).contains(gradeC) && w.get(0).contains("등급 C"), w.get(0));
    }

    @Test
    void otherVersionModuleProbesStayAtDebug() {
        NmsReflect.pluginEnabled(PLUGIN);
        NmsReflect.classMissing(PLUGIN, "net.minecraft.server.v1_8_R3.MinecraftServer");
        NmsReflect.flushNow(PLUGIN);
        assertEquals(List.of(), warnings());
    }
}
