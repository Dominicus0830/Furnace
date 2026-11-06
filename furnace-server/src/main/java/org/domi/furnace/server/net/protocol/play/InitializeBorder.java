// S→C 월드 보더 초기화(0x25): double×4(중심 x,z / 이전·새 크기) + VarLong 보간 시간 + VarInt×3(최대 좌표, 경고 거리, 경고 시간).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.WorldBorder;

public record InitializeBorder(double centerX, double centerZ, double oldSize, double newSize, long lerpMillis,
                               int absoluteMaxSize, int warningBlocks, int warningTimeSeconds) implements Packet {

    public static final int ID = 0x25;

    /** 바닐라 ClientboundInitializeBorderPacket(WorldBorder): 현재 크기·보간 목표·남은 보간 시간을 한 순간의 상태로 싣는다. */
    public static InitializeBorder of(WorldBorder border) {
        synchronized (border) {
            return new InitializeBorder(border.getCenterX(), border.getCenterZ(), border.getSize(), border.getLerpTarget(),
                    border.getLerpRemainingTime(), border.getAbsoluteMaxSize(), border.getWarningBlocks(), border.getWarningTime());
        }
    }

    /** 바닐라 기본 보더(크기 5.9999968E7, 최대 좌표 29999984, 경고 5블록/15초). */
    public static InitializeBorder vanillaDefault() {
        return new InitializeBorder(0, 0, 5.9999968E7, 5.9999968E7, 0L, 29999984, 5, 15);
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeDouble(centerX);
        buf.writeDouble(centerZ);
        buf.writeDouble(oldSize);
        buf.writeDouble(newSize);
        Types.writeVarLong(buf, lerpMillis);
        Types.writeVarInt(buf, absoluteMaxSize);
        Types.writeVarInt(buf, warningBlocks);
        Types.writeVarInt(buf, warningTimeSeconds);
    }
}
