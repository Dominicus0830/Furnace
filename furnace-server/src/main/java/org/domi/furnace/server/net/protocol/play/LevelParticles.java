// S→C 입자(level_particles 0x29): Boolean 장거리, double×3 위치, float×3 퍼짐, float 최대속도, Int 개수, 입자(종류 VarInt+옵션).
// 배치는 1.21.1 기준(바이트코드로 확인: alwaysShow 필드 없음). id 는 packets.json 의 play/clientbound level_particles = 41.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record LevelParticles(boolean overrideLimiter, double x, double y, double z, float xDist, float yDist, float zDist,
        float maxSpeed, int count, ParticleOptions particle) implements Packet {

    public static final int ID = 0x29;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBoolean(overrideLimiter);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(xDist);
        buf.writeFloat(yDist);
        buf.writeFloat(zDist);
        buf.writeFloat(maxSpeed);
        buf.writeInt(count);
        particle.write(buf);
    }
}
