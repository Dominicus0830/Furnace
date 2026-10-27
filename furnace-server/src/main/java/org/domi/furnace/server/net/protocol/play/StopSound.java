// S→C 소리 멈추기(0x6A): 플래그 1=분류 있음, 2=소리 있음. 분류는 varint(SoundSource 순서), 소리는 리소스 이름 문자열.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/** @param source SoundSource 번호(없으면 -1)  @param sound 소리 id 문자열(없으면 null) */
public record StopSound(int source, String sound) implements Packet {

    public static final int ID = 0x6A;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte((source >= 0 ? 1 : 0) | (sound != null ? 2 : 0));
        if (source >= 0) Types.writeVarInt(buf, source);
        if (sound != null) Types.writeString(buf, sound);
    }
}
