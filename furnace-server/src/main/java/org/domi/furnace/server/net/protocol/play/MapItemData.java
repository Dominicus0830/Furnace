// S→C 지도 데이터(map_item_data, 0x2C): 지도 번호, 배율, 잠금, 표식 목록(선택), 바뀐 색 사각형(선택).
// 바닐라 ClientboundMapItemDataPacket — 색 조각은 너비 0 이면 없음(바이트 하나 0).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.map.MapData;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record MapItemData(int mapId, byte scale, boolean locked, List<MapData.Decoration> decorations, Patch patch) implements Packet {

    public static final int ID = 0x2C; // datagen: play.clientbound.map_item_data (protocol_id 44)

    /** MapItemSavedData.MapPatch — 시작 칸·크기·색(너비 x 높이, 가로 먼저). */
    public record Patch(int startX, int startY, int width, int height, byte[] colors) { }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, mapId);
        buf.writeByte(scale);
        buf.writeBoolean(locked);
        buf.writeBoolean(decorations != null);
        if (decorations != null) {
            Types.writeVarInt(buf, decorations.size());
            for (MapData.Decoration d : decorations) {
                Types.writeVarInt(buf, d.type().id());
                buf.writeByte(d.x());
                buf.writeByte(d.y());
                buf.writeByte(d.rot());
                buf.writeBoolean(d.name() != null);
                if (d.name() != null) buf.writeBytes(d.name().networkNbt());
            }
        }
        if (patch != null) {
            buf.writeByte(patch.width());
            buf.writeByte(patch.height());
            buf.writeByte(patch.startX());
            buf.writeByte(patch.startY());
            Types.writeVarInt(buf, patch.colors().length);
            buf.writeBytes(patch.colors());
        } else {
            buf.writeByte(0);
        }
    }
}
