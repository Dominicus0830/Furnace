// S→C 전투 시작(0x3B): 본문 없음. 바닐라 CombatTracker.recordDamage 첫 피해.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record PlayerCombatEnter() implements Packet {

    public static final int ID = 0x3B;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { }
}
