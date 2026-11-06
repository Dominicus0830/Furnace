// 플레이어 이동. 위치가 있는지(hasPosition)·방향이 있는지(hasRotation)에 따라 바닐라의 네 이동 패킷 가운데 하나가 된다.
// 없는 쪽 값은 0 이며 무시된다. 각도는 도.
package org.domi.furnace.api.packet.serverbound;

import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundMovePlayerPacket(boolean hasPosition, double x, double y, double z, boolean hasRotation, float yaw, float pitch,
                                          boolean onGround) implements ServerboundPacket {

    public static ServerboundMovePlayerPacket position(double x, double y, double z, boolean onGround) {
        return new ServerboundMovePlayerPacket(true, x, y, z, false, 0, 0, onGround);
    }

    public static ServerboundMovePlayerPacket positionRotation(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        return new ServerboundMovePlayerPacket(true, x, y, z, true, yaw, pitch, onGround);
    }

    public static ServerboundMovePlayerPacket rotation(float yaw, float pitch, boolean onGround) {
        return new ServerboundMovePlayerPacket(false, 0, 0, 0, true, yaw, pitch, onGround);
    }

    public static ServerboundMovePlayerPacket statusOnly(boolean onGround) {
        return new ServerboundMovePlayerPacket(false, 0, 0, 0, false, 0, 0, onGround);
    }

    @Override
    public PacketType<ServerboundMovePlayerPacket> type() { return PacketTypes.Serverbound.MOVE_PLAYER; }
}
