// S→C 채팅 지우기(0x1C): MessageSignature.Packed — VarInt(id+1), id 가 없으면(0) 서명 256바이트가 이어진다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/** @param cacheId 서명 캐시 번호(없으면 -1)  @param signature cacheId 가 -1 일 때의 256바이트 서명 */
public record DeleteChat(int cacheId, byte[] signature) implements Packet {

    public static final int ID = 0x1C;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        if (cacheId >= 0) {
            Types.writeVarInt(buf, cacheId + 1);
        } else {
            if (signature == null || signature.length != 256) throw new IllegalArgumentException("서명은 256바이트");
            Types.writeVarInt(buf, 0);
            buf.writeBytes(signature);
        }
    }
}
