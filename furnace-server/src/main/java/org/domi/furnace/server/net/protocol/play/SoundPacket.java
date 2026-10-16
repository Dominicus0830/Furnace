// 소리(clientbound sound): 소리 이벤트 홀더(레지스트리 id+1, 0 이면 직접 값) + 분류 + 위치×8(int) + 크기 + 높이 + 씨앗.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SoundPacket(int soundId, String directSound, int source, double x, double y, double z, float volume, float pitch, long seed)
        implements Packet {

    public static final int ID = 0x68;

    /** SoundSource 순서: master music record weather block hostile neutral player ambient voice. */
    public static int source(String name) {
        return switch (name) {
            case "master" -> 0; case "music" -> 1; case "record" -> 2; case "weather" -> 3; case "blocks", "block" -> 4;
            case "hostile" -> 5; case "neutral" -> 6; case "players", "player" -> 7; case "ambient" -> 8; default -> 9;
        };
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        if (soundId >= 0) {
            Types.writeVarInt(buf, soundId + 1);
        } else {
            Types.writeVarInt(buf, 0);
            Types.writeString(buf, directSound);
            buf.writeBoolean(false);
        }
        Types.writeVarInt(buf, source);
        buf.writeInt((int) (x * 8.0));
        buf.writeInt((int) (y * 8.0));
        buf.writeInt((int) (z * 8.0));
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
        buf.writeLong(seed);
    }
}
