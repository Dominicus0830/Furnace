// 보스 바(clientbound boss_event): 추가·제거·진행도·이름·모양·표시 옵션. 색: 0 분홍 1 파랑 2 빨강 3 초록 4 노랑 5 보라 6 흰색. 모양: 0 진행 막대 1~4 6/10/12/20 칸.
// 옵션 비트: 1 화면 어둡게, 2 보스 음악, 4 안개.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record BossEvent(UUID bossId, int action, Text name, float progress, int color, int overlay, int flags) implements Packet {

    public static final int ID = 0x0A;
    public static final int ADD = 0, REMOVE = 1, UPDATE_PROGRESS = 2, UPDATE_NAME = 3, UPDATE_STYLE = 4, UPDATE_PROPERTIES = 5;

    public static BossEvent add(UUID id, Text name, float progress, int color, int overlay, int flags) { return new BossEvent(id, ADD, name, progress, color, overlay, flags); }

    public static BossEvent remove(UUID id) { return new BossEvent(id, REMOVE, null, 0, 0, 0, 0); }

    public static BossEvent progress(UUID id, float progress) { return new BossEvent(id, UPDATE_PROGRESS, null, progress, 0, 0, 0); }

    public static BossEvent name(UUID id, Text name) { return new BossEvent(id, UPDATE_NAME, name, 0, 0, 0, 0); }

    public static BossEvent style(UUID id, int color, int overlay) { return new BossEvent(id, UPDATE_STYLE, null, 0, color, overlay, 0); }

    public static BossEvent properties(UUID id, int flags) { return new BossEvent(id, UPDATE_PROPERTIES, null, 0, 0, 0, flags); }

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeUuid(buf, bossId);
        Types.writeVarInt(buf, action);
        switch (action) {
            case ADD -> {
                buf.writeBytes(name.networkNbt());
                buf.writeFloat(progress);
                Types.writeVarInt(buf, color);
                Types.writeVarInt(buf, overlay);
                buf.writeByte(flags);
            }
            case UPDATE_PROGRESS -> buf.writeFloat(progress);
            case UPDATE_NAME -> buf.writeBytes(name.networkNbt());
            case UPDATE_STYLE -> {
                Types.writeVarInt(buf, color);
                Types.writeVarInt(buf, overlay);
            }
            case UPDATE_PROPERTIES -> buf.writeByte(flags);
            default -> { }
        }
    }
}
