// S→C 커스텀 페이로드(play 0x19 / 구성 0x01): 채널 이름 문자열 + 나머지 바이트 전부.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/** @param inPlay play 단계(0x19)면 true, 구성 단계(0x01)면 false */
public record CustomPayloadPacket(String channel, byte[] data, boolean inPlay) implements Packet {

    public static final int ID_PLAY = 0x19;
    public static final int ID_CONFIG = 0x01;
    /** 클라이언트→서버 페이로드의 바닐라 상한(ServerboundCustomPayloadPacket: 32767바이트). */
    public static final int MAX_SERVERBOUND = 32767;
    /** 서버→클라이언트 상한(ClientboundCustomPayloadPacket: 1048576바이트). */
    public static final int MAX_CLIENTBOUND = 1048576;

    @Override
    public int id() { return inPlay ? ID_PLAY : ID_CONFIG; }

    @Override
    public void write(ByteBuf buf) {
        if (data.length > MAX_CLIENTBOUND) throw new IllegalArgumentException("페이로드가 너무 크다: " + data.length);
        Types.writeString(buf, channel);
        buf.writeBytes(data);
    }
}
