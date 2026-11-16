package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Paper 1.21.1 멤버 이름표: 서버 실측(MinecraftServer 에는 nextTickTime 이 없고 nextTickTimeNanos 만 있다 — javap 로 확인)과 조상 사슬 규칙. */
class NmsPaperMembersTest {

    @Test
    void oldNamesAbsentInPaperAreKnownAbsent() {
        assertEquals(Boolean.FALSE, NmsPaperMembers.hasField("net.minecraft.server.MinecraftServer", "nextTickTime"));
        assertEquals(Boolean.TRUE, NmsPaperMembers.hasField("net.minecraft.server.MinecraftServer", "nextTickTimeNanos"));
        assertEquals(Boolean.FALSE, NmsPaperMembers.hasField("net.minecraft.server.level.ServerPlayer", "playerConnection"));
        assertEquals(Boolean.TRUE, NmsPaperMembers.hasField("net.minecraft.server.level.ServerPlayer", "connection"));
    }

    @Test
    void membersOfAncestorsCount() {
        // ServerPlayer 는 선언하지 않지만 Entity 가 가진 필드·메서드 — 상위 클래스 사슬을 따라 찾는다
        assertEquals(Boolean.TRUE, NmsPaperMembers.hasField("net.minecraft.server.level.ServerPlayer", "tickCount"));
        assertEquals(Boolean.TRUE, NmsPaperMembers.hasMethod("net.minecraft.server.level.ServerPlayer", "getHealth"));
        assertEquals(Boolean.FALSE, NmsPaperMembers.hasMethod("net.minecraft.server.level.ServerPlayer", "sendPacket"));
    }

    @Test
    void unknownClassesAreUnknownNotAbsent() {
        assertNull(NmsPaperMembers.hasField("net.minecraft.not.There", "x"), "표에 없는 클래스는 모름");
        assertNull(NmsPaperMembers.hasField("com.example.Plugin", "x"));
    }

    @Test
    void reflectBridgeSkipsOnlyWhatPaperLacksToo() {
        assertTrue(NmsReflect.absentInPaper(net.minecraft.server.level.ServerPlayer.class, "playerConnection"));
        assertTrue(NmsReflect.absentInPaper(net.minecraft.server.level.ServerPlayer.class, "sendPacket(..)"));
        assertFalse(NmsReflect.absentInPaper(net.minecraft.server.level.ServerPlayer.class, "camera"), "Paper 에 있고 shim 에 없는 필드는 구멍");
        assertFalse(NmsReflect.absentInPaper(net.minecraft.server.level.ServerPlayer.class, "<init>(..)"), "생성자는 늘 경고");
    }

    /**
     * R1(#437 검토): Spigot 이름으로 컴파일된 플러그인은 대응표로 이름이 바뀐 뒤 조회된다. Paper 표 판정은 **실제로 찾은(바뀐) 이름**으로 해야 한다 —
     * 바뀌기 전 Spigot 이름("spigotCamera")은 Paper 표에 없어 "Paper 에도 없음"으로 오판되고, Paper 에 있는 진짜 구멍(camera)의 경고가 사라진다.
     */
    @Test
    void spigotNamedLookupJudgesTheNameActuallyLookedUp() throws Exception {
        String mojang = """
                net.minecraft.server.level.ServerPlayer -> aqv:
                    net.minecraft.server.level.ServerPlayer camera -> zz
                    int playerConnection -> zy
                """;
        String classes = "aqv net/minecraft/server/level/EntityPlayer\n";
        // 클래스 → (난독화 이름 → Spigot 이름). camera 는 Paper 에 있는 멤버, playerConnection 은 Paper 에 없는 옛 이름
        String members = "net/minecraft/server/level/EntityPlayer zz spigotCamera\nnet/minecraft/server/level/EntityPlayer zy spigotOld\n";
        String plugin = "SpigotNamedProbe";
        SpigotMappings.register(plugin, SpigotMappings.parse(new StringReader(mojang), new StringReader(classes), new StringReader(members)));
        Logger furnace = (Logger) LoggerFactory.getLogger("Furnace");
        Level old = furnace.getLevel();
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        furnace.setLevel(Level.DEBUG);
        furnace.addAppender(logs);
        NmsReflect.resetWarnings();
        try {
            assertThrows(NoSuchFieldException.class, () -> NmsReflect.getDeclaredField(net.minecraft.server.level.ServerPlayer.class, "spigotCamera", plugin));
            assertThrows(NoSuchFieldException.class, () -> NmsReflect.getDeclaredField(net.minecraft.server.level.ServerPlayer.class, "spigotOld", plugin));
        } finally {
            furnace.detachAppender(logs);
            furnace.setLevel(old);
            SpigotMappings.register(plugin, null);
            NmsReflect.resetWarnings();
        }
        List<String> warns = logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
        assertTrue(warns.stream().anyMatch(m -> m.contains("ServerPlayer#spigotCamera")), () -> "Paper 에 있는 camera 는 구멍이라 경고(플러그인이 쓴 이름으로 보임): " + warns);
        assertTrue(warns.stream().noneMatch(m -> m.contains("spigotOld")), () -> "바뀐 이름이 Paper 에도 없으면 경고하지 않는다: " + warns);
    }
}
