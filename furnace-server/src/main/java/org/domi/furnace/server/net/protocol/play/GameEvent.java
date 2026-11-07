// S→C 게임 이벤트. event 13 = "청크 로딩 대기 시작"(join 시 클라가 로딩 화면을 정상 종료하게 함).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record GameEvent(int event, float value) implements Packet {

    public static final int ID = 0x22;

    /** event 13: 클라에 "이제부터 청크를 받을 때까지 대기하라"고 알림. */
    public static final int START_WAITING_FOR_CHUNKS = 13;
    public static final int NO_RESPAWN_BLOCK_AVAILABLE = 0;
    public static final int START_RAINING = 1;
    public static final int CHANGE_GAME_MODE = 3;
    public static final int WIN_GAME = 4;      // 바닐라 ClientboundGameEventPacket.START_RAINING
    public static final int STOP_RAINING = 2;
    public static final int RAIN_LEVEL_CHANGE = 7;
    public static final int THUNDER_LEVEL_CHANGE = 8;
    public static final int PUFFER_FISH_STING = 9;
    public static final int ELDER_GUARDIAN_EFFECT = 10;
    public static final int IMMEDIATE_RESPAWN = 11;
    public static final int LIMITED_CRAFTING = 12;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(event);
        buf.writeFloat(value);
    }
}
