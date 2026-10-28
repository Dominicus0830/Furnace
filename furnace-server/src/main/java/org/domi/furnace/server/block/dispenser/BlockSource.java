// 바닐라 BlockSource(level, pos, state, blockEntity): 발사기 동작이 받는 발사 원천. slot 은 지금 쏘는 칸(불변 스택이라 남은 것 넣기 전에 칸을 먼저 갱신한다).
package org.domi.furnace.server.block.dispenser;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.entity.DispenserBlockEntity;

public record BlockSource(BlockLevel level, Pos pos, int state, DispenserBlockEntity blockEntity, int slot) {

    private static final Prop FACING = B.prop("facing");

    /** 발사기가 바라보는 방향(facing). */
    public int facing() { return Dir.byName(FACING.valueName(state)); }

    /** 앞 칸. */
    public Pos front() { return pos.relative(facing()); }

    public double centerX() { return pos.x() + 0.5; }

    public double centerY() { return pos.y() + 0.5; }

    public double centerZ() { return pos.z() + 0.5; }

    /** DispenserBlock.getDispensePosition(source, distance, offset): 가운데 + 방향 × 거리 + 오프셋. */
    public double[] dispensePosition(double distance, double ox, double oy, double oz) {
        int d = facing();
        return new double[] {centerX() + distance * Dir.DX[d] + ox, centerY() + distance * Dir.DY[d] + oy, centerZ() + distance * Dir.DZ[d] + oz};
    }

    /** Direction.toYRot: (2D 값 & 3) × 90 — 위·아래는 2D 값이 −1 이라 270. */
    public static float toYRot(int dir) {
        int d2 = switch (dir) {
            case Dir.SOUTH -> 0;
            case Dir.WEST -> 1;
            case Dir.NORTH -> 2;
            case Dir.EAST -> 3;
            default -> -1;
        };
        return (d2 & 3) * 90;
    }
}
