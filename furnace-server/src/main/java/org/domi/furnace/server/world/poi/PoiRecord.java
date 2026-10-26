// 바닐라 PoiRecord: 한 칸의 관심 지점(위치·종류·남은 티켓). 티켓을 잡고 놓으면 섹션을 dirty 로.
package org.domi.furnace.server.world.poi;

import org.domi.furnace.server.block.Pos;

public final class PoiRecord {
    private final Pos pos;
    private final PoiType type;
    private int freeTickets;
    private final Runnable setDirty;

    PoiRecord(Pos pos, PoiType type, int freeTickets, Runnable setDirty) {
        this.pos = pos;
        this.type = type;
        this.freeTickets = freeTickets;
        this.setDirty = setDirty;
    }

    PoiRecord(Pos pos, PoiType type, Runnable setDirty) { this(pos, type, type.maxTickets(), setDirty); }

    public Pos pos() { return pos; }

    public PoiType type() { return type; }

    public int freeTickets() { return freeTickets; }

    /** acquireTicket. */
    boolean acquireTicket() {
        if (freeTickets <= 0) return false;
        freeTickets--;
        setDirty.run();
        return true;
    }

    /** releaseTicket. */
    boolean releaseTicket() {
        if (freeTickets >= type.maxTickets()) return false;
        freeTickets++;
        setDirty.run();
        return true;
    }

    public boolean hasSpace() { return freeTickets > 0; }

    public boolean isOccupied() { return freeTickets != type.maxTickets(); }

    /** 바닐라 BlockPos.distSqr(정수 좌표 차의 제곱합). */
    long distSqr(Pos o) {
        long dx = pos.x() - o.x(), dy = pos.y() - o.y(), dz = pos.z() - o.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
