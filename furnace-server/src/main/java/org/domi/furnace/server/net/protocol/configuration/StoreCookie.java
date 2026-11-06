// S→C 쿠키 저장(구성 0x0A): 키 문자열 + 바이트 배열(VarInt 길이, 바닐라 상한 5120바이트).
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record StoreCookie(String key, byte[] payload) implements Packet {

    public static final int ID = 0x0A;
    /** 바닐라 ClientboundStoreCookiePacket.MAX_PAYLOAD_SIZE. */
    public static final int MAX_PAYLOAD = 5120;

    public StoreCookie {
        if (payload.length > MAX_PAYLOAD) throw new IllegalArgumentException("쿠키 값이 너무 크다(최대 " + MAX_PAYLOAD + "바이트): " + payload.length);
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, key);
        Types.writeVarInt(buf, payload.length);
        buf.writeBytes(payload);
    }
}
