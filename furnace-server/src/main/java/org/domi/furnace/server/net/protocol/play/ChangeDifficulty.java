// S→C 난이도(0x0B): unsigned byte(0 peaceful,1 easy,2 normal,3 hard) + 잠금 여부.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket")
public record ChangeDifficulty(int difficulty, boolean locked) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_CHANGE_DIFFICULTY;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(difficulty);
        buf.writeBoolean(locked);
    }
}
