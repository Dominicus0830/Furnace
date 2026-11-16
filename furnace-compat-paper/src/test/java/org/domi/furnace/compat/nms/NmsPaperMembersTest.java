package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
}
