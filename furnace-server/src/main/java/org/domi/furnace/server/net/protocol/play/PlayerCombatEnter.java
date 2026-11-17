// S→C 전투 시작(0x3B): 본문 없음. 바닐라 CombatTracker.recordDamage 첫 피해.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket")
public record PlayerCombatEnter() implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_PLAYER_COMBAT_ENTER;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { }
}
