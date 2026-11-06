// 서버가 보내지 않던 clientbound 패킷의 와이어 형식 — 바이트는 바닐라 디컴파일(StreamCodec)을 손으로 따라가 계산한 값.
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.domi.furnace.server.net.protocol.Packet;
import org.junit.jupiter.api.Test;

class ClientboundMiscPacketsTest {

    private static String hex(Packet p) {
        ByteBuf b = Unpooled.buffer();
        p.write(b);
        String h = ByteBufUtil.hexDump(b);
        b.release();
        return h;
    }

    @Test
    void ids_matchPacketsJson() {
        assertEquals(0x24, new HurtAnimation(1, 0).id());
        assertEquals(0x67, new SoundEntity(0, null, 0, 1, 1, 1, 0).id());
        assertEquals(0x3A, new PlayerCombatEnd(1).id());
        assertEquals(0x3B, new PlayerCombatEnter().id());
        assertEquals(0x1C, new DeleteChat(0, null).id());
        assertEquals(0x3F, PlayerLookAt.atPosition(0, 0, 0, 0).id());
        assertEquals(0x62, new SetSimulationDistance(1).id());
        assertEquals(0x55, new SetChunkCacheRadius(1).id());
        assertEquals(0x7B, new ServerLinksPacket(List.of(), true).id());
        assertEquals(0x10, new ServerLinksPacket(List.of(), false).id());
    }

    @Test
    void hurtAnimation_varIntAndFloat() {
        // id 300 = ac 02, 90.0f = 42b40000
        assertEquals("ac0242b40000", hex(new HurtAnimation(300, 90.0f)));
    }

    @Test
    void soundEntity_registryHolder() {
        // 홀더 id 5 → VarInt 6, 분류 7, 엔티티 3, 1.0f 3f800000, 0.5f 3f000000, seed 1
        assertEquals("060703" + "3f800000" + "3f000000" + "0000000000000001", hex(new SoundEntity(5, null, 7, 3, 1.0f, 0.5f, 1L)));
    }

    @Test
    void soundEntity_directHolder() {
        // 0, 문자열 "a"(01 61), Optional 없음(00), 분류 0, 엔티티 1, 크기 1.0, 높이 1.0, seed 0
        assertEquals("00" + "0161" + "00" + "00" + "01" + "3f800000" + "3f800000" + "0000000000000000",
                hex(new SoundEntity(-1, "a", 0, 1, 1.0f, 1.0f, 0L)));
    }

    @Test
    void combat() {
        assertEquals("", hex(new PlayerCombatEnter()));
        assertEquals("e807", hex(new PlayerCombatEnd(1000)));
    }

    @Test
    void deleteChat_cachedAndInline() {
        assertEquals("03", hex(new DeleteChat(2, null)));
        byte[] sig = new byte[256];
        sig[0] = 0x7f;
        String h = hex(new DeleteChat(-1, sig));
        assertEquals(2 + 512, h.length());
        assertTrue(h.startsWith("007f"));
        assertThrows(IllegalArgumentException.class, () -> hex(new DeleteChat(-1, new byte[3])));
    }

    @Test
    void playerLookAt_positionAndEntity() {
        // 눈(1), 1.0 / 2.0 / -1.0 double, 엔티티 없음
        assertEquals("01" + "3ff0000000000000" + "4000000000000000" + "bff0000000000000" + "00",
                hex(PlayerLookAt.atPosition(1, 1.0, 2.0, -1.0)));
        // 엔티티 7, 눈 앵커
        assertEquals("00" + "0000000000000000".repeat(3) + "01" + "07" + "01", hex(new PlayerLookAt(0, 0, 0, 0, 7, 1)));
    }

    @Test
    void distances() {
        assertEquals("0a", hex(new SetSimulationDistance(10)));
        assertEquals("0c", hex(new SetChunkCacheRadius(12)));
    }

    @Test
    void serverLinks_knownType() {
        // 개수 1, 알려진 종류(true), 종류 0 report_bug, 문자열 "u"
        assertEquals("01" + "01" + "00" + "0175", hex(new ServerLinksPacket(
                List.of(new ServerLinksPacket.Entry(0, null, "u")), true)));
    }
}
