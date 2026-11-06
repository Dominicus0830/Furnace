// 웅크리기·달리기·침대에서 나오기·탈것 점프·탈것 인벤토리·겉날개 시작. data 는 탈것 점프 세기(0~100)만 쓴다.
package org.domi.furnace.api.packet.serverbound;

import java.util.Objects;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundPlayerCommandPacket(int entityId, Action action, int data) implements ServerboundPacket {

    public enum Action {
        PRESS_SHIFT_KEY, RELEASE_SHIFT_KEY, STOP_SLEEPING, START_SPRINTING, STOP_SPRINTING, START_RIDING_JUMP, STOP_RIDING_JUMP,
        OPEN_INVENTORY, START_FALL_FLYING
    }

    public ServerboundPlayerCommandPacket {
        Objects.requireNonNull(action, "action");
    }

    @Override
    public PacketType<ServerboundPlayerCommandPacket> type() { return PacketTypes.Serverbound.PLAYER_COMMAND; }
}
