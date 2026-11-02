// S→C 엔티티에 붙는 소리(0x67): 소리 홀더(VarInt 레지스트리 id+1, 0 이면 이름 문자열+Optional<float> 고정 범위), VarInt 분류, VarInt 엔티티 번호, float 크기, float 높이, long 씨앗.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/** @param soundId 레지스트리 번호(직접 값이면 -1)  @param directSound 직접 값일 때의 소리 이름 */
public record SoundEntity(int soundId, String directSound, int source, int entityId, float volume, float pitch, long seed)
        implements Packet {

    public static final int ID = 0x67;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        if (soundId >= 0) {
            Types.writeVarInt(buf, soundId + 1);
        } else {
            Types.writeVarInt(buf, 0);
            Types.writeString(buf, directSound);
            buf.writeBoolean(false);   // 고정 범위 없음
        }
        Types.writeVarInt(buf, source);
        Types.writeVarInt(buf, entityId);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
        buf.writeLong(seed);
    }
}
