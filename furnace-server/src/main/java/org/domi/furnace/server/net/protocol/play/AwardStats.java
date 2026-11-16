// 통계 갱신(clientbound award_stats 0x04): 항목 수, 각 항목 (stat_type 번호, 값 번호, 값).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.Map;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;
import org.domi.furnace.server.stats.PlayerStats;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundAwardStatsPacket")
public record AwardStats(Map<PlayerStats.Stat, Integer> stats) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_AWARD_STATS;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        int n = 0;
        for (var s : stats.keySet()) if (PlayerStats.ids(s) != null) n++;
        Types.writeVarInt(buf, n);
        for (var e : stats.entrySet()) {
            int[] ids = PlayerStats.ids(e.getKey());
            if (ids == null) continue;
            Types.writeVarInt(buf, ids[0]);
            Types.writeVarInt(buf, ids[1]);
            Types.writeVarInt(buf, e.getValue());
        }
    }
}
