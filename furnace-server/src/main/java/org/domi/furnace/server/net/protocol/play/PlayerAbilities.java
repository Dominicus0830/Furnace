// S→C 플레이어 능력(0x38): 플래그 byte(1 무적, 2 비행 중, 4 비행 가능, 8 즉시 파괴) + 비행 속도 float + 시야 배율 float.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record PlayerAbilities(boolean invulnerable, boolean flying, boolean mayFly, boolean instabuild,
                              float flyingSpeed, float walkingSpeed) implements Packet {

    public static final int ID = 0x38;

    /** 크리에이티브 기본값(바닐라 Abilities: flySpeed 0.05, walkSpeed 0.1). */
    public static PlayerAbilities creative() {
        return new PlayerAbilities(true, false, true, true, 0.05f, 0.1f);
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        int flags = (invulnerable ? 1 : 0) | (flying ? 2 : 0) | (mayFly ? 4 : 0) | (instabuild ? 8 : 0);
        buf.writeByte(flags);
        buf.writeFloat(flyingSpeed);
        buf.writeFloat(walkingSpeed);
    }
}
