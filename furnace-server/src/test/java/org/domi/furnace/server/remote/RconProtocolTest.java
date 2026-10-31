package org.domi.furnace.server.remote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 바닐라 RconClient 패킷 형식(리틀엔디언, NUL 2개, 4096 글자 분할). */
class RconProtocolTest {

    @Test
    void encodeLayout() {
        byte[] p = RconProtocol.encode(7, 2, "hi");
        ByteBuffer b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(12, b.getInt(0));   // 본문 2 + 10
        assertEquals(7, b.getInt(4));
        assertEquals(2, b.getInt(8));
        assertArrayEquals(new byte[] {'h', 'i', 0, 0}, Arrays.copyOfRange(p, 12, 16));
        assertEquals(16, p.length);
    }

    @Test
    void decodeRoundTripAndUtf8() {
        byte[] p = RconProtocol.encode(-5, 3, "비밀번호");
        RconProtocol.Request r = RconProtocol.decode(Arrays.copyOfRange(p, 4, p.length));
        assertEquals(-5, r.id());
        assertEquals(3, r.type());
        assertEquals("비밀번호", r.payload());
    }

    @Test
    void decodeStopsAtFirstNulAndTooShortIsNull() {
        byte[] body = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(2).put((byte) 'a').put((byte) 0).put((byte) 'b').put((byte) 0).array();
        assertEquals("a", RconProtocol.decode(body).payload());
        assertNull(RconProtocol.decode(new byte[9]));
    }

    @Test
    void lengthBounds() {
        assertFalse(RconProtocol.validLength(9));
        assertTrue(RconProtocol.validLength(10));
        assertTrue(RconProtocol.validLength(1456));
        assertFalse(RconProtocol.validLength(1457));
    }

    @Test
    void responseSplitsAt4096Chars() {
        assertEquals(1, RconProtocol.encodeResponse(1, "").length);   // 빈 응답도 패킷 하나
        assertEquals(1, RconProtocol.encodeResponse(1, "x".repeat(4096)).length);
        byte[][] two = RconProtocol.encodeResponse(1, "x".repeat(4097));
        assertEquals(2, two.length);
        assertEquals(4096 + 14, two[0].length);
        assertEquals(1 + 14, two[1].length);
        assertEquals(3, RconProtocol.encodeResponse(1, "y".repeat(9000)).length);
    }
}
