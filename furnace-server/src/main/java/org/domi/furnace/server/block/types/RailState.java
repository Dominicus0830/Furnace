// 바닐라 RailState: 레일 하나의 연결(최대 둘)을 계산하고, 이웃 레일과 이어 모양을 바꾼다.
package org.domi.furnace.server.block.types;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;

final class RailState {

    private final BlockLevel level;
    private final Pos pos;
    private final boolean isStraight;
    private int state;
    private final List<Pos> connections = new ArrayList<>();

    RailState(BlockLevel level, Pos pos, int state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.isStraight = ((BaseRailBlock) B.behavior(state)).isStraight();
        updateConnections(BaseRailBlock.shape(state));
    }

    List<Pos> connections() { return connections; }

    private void updateConnections(String shape) {
        connections.clear();
        Pos n = pos.north(), s = pos.south(), w = pos.west(), e = pos.east();
        switch (shape) {
            case "north_south" -> { connections.add(n); connections.add(s); }
            case "east_west" -> { connections.add(w); connections.add(e); }
            case "ascending_east" -> { connections.add(w); connections.add(e.above()); }
            case "ascending_west" -> { connections.add(w.above()); connections.add(e); }
            case "ascending_north" -> { connections.add(n.above()); connections.add(s); }
            case "ascending_south" -> { connections.add(n); connections.add(s.above()); }
            case "south_east" -> { connections.add(e); connections.add(s); }
            case "south_west" -> { connections.add(w); connections.add(s); }
            case "north_west" -> { connections.add(w); connections.add(n); }
            case "north_east" -> { connections.add(e); connections.add(n); }
            default -> {}
        }
    }

    /** removeSoftConnections: 서로 이어지지 않은 연결을 지운다(이어진 쪽은 실제 레일 칸으로). */
    private void removeSoftConnections() {
        for (int i = 0; i < connections.size(); i++) {
            RailState r = getRail(connections.get(i));
            if (r != null && r.connected(this)) connections.set(i, r.pos);
            else connections.remove(i--);
        }
    }

    private boolean hasRail(Pos p) { return BaseRailBlock.isRail(level, p) || BaseRailBlock.isRail(level, p.above()) || BaseRailBlock.isRail(level, p.below()); }

    private RailState getRail(Pos p) {
        int s = level.getBlockState(p);
        if (BaseRailBlock.isRail(s)) return new RailState(level, p, s);
        Pos a = p.above();
        s = level.getBlockState(a);
        if (BaseRailBlock.isRail(s)) return new RailState(level, a, s);
        Pos b = p.below();
        s = level.getBlockState(b);
        return BaseRailBlock.isRail(s) ? new RailState(level, b, s) : null;
    }

    private boolean connected(RailState other) { return hasConnection(other.pos); }

    private boolean hasConnection(Pos p) {
        for (Pos c : connections) if (c.x() == p.x() && c.z() == p.z()) return true;
        return false;
    }

    /** countPotentialConnections: 수평 네 방향 중 레일이 있는 쪽 수. */
    int countPotentialConnections() {
        int n = 0;
        for (int d : Dir.HORIZONTAL) if (hasRail(pos.relative(d))) n++;
        return n;
    }

    private boolean canConnectTo(RailState other) { return connected(other) || connections.size() != 2; }

    private void connectTo(RailState other) {
        connections.add(other.pos);
        Pos n = pos.north(), s = pos.south(), w = pos.west(), e = pos.east();
        boolean cn = hasConnection(n), cs = hasConnection(s), cw = hasConnection(w), ce = hasConnection(e);
        String shape = null;
        if (cn || cs) shape = "north_south";
        if (cw || ce) shape = "east_west";
        if (!isStraight) {
            if (cs && ce && !cn && !cw) shape = "south_east";
            if (cs && cw && !cn && !ce) shape = "south_west";
            if (cn && cw && !cs && !ce) shape = "north_west";
            if (cn && ce && !cs && !cw) shape = "north_east";
        }
        if ("north_south".equals(shape)) {
            if (BaseRailBlock.isRail(level, n.above())) shape = "ascending_north";
            if (BaseRailBlock.isRail(level, s.above())) shape = "ascending_south";
        }
        if ("east_west".equals(shape)) {
            if (BaseRailBlock.isRail(level, e.above())) shape = "ascending_east";
            if (BaseRailBlock.isRail(level, w.above())) shape = "ascending_west";
        }
        if (shape == null) shape = "north_south";
        state = BaseRailBlock.SHAPE.with(state, shape);
        level.setBlock(pos, state, 3);
    }

    private boolean hasNeighborRail(Pos p) {
        RailState r = getRail(p);
        if (r == null) return false;
        r.removeSoftConnections();
        return r.canConnectTo(this);
    }

    /** place(신호, 언제나 놓기, 지금 모양). */
    RailState place(boolean powered, boolean alwaysPlace, String current) {
        Pos n = pos.north(), s = pos.south(), w = pos.west(), e = pos.east();
        boolean rn = hasNeighborRail(n), rs = hasNeighborRail(s), rw = hasNeighborRail(w), re = hasNeighborRail(e);
        String shape = null;
        boolean ns = rn || rs, ew = rw || re;
        if (ns && !ew) shape = "north_south";
        if (ew && !ns) shape = "east_west";
        boolean se = rs && re, sw = rs && rw, ne = rn && re, nw = rn && rw;
        if (!isStraight) {
            if (se && !rn && !rw) shape = "south_east";
            if (sw && !rn && !re) shape = "south_west";
            if (nw && !rs && !re) shape = "north_west";
            if (ne && !rs && !rw) shape = "north_east";
        }
        if (shape == null) {
            if (ns && ew) shape = current;
            else if (ns) shape = "north_south";
            else if (ew) shape = "east_west";
            if (!isStraight) {
                if (powered) {
                    if (se) shape = "south_east";
                    if (sw) shape = "south_west";
                    if (ne) shape = "north_east";
                    if (nw) shape = "north_west";
                } else {
                    if (nw) shape = "north_west";
                    if (ne) shape = "north_east";
                    if (sw) shape = "south_west";
                    if (se) shape = "south_east";
                }
            }
        }
        if ("north_south".equals(shape)) {
            if (BaseRailBlock.isRail(level, n.above())) shape = "ascending_north";
            if (BaseRailBlock.isRail(level, s.above())) shape = "ascending_south";
        }
        if ("east_west".equals(shape)) {
            if (BaseRailBlock.isRail(level, e.above())) shape = "ascending_east";
            if (BaseRailBlock.isRail(level, w.above())) shape = "ascending_west";
        }
        if (shape == null) shape = current;
        updateConnections(shape);
        state = BaseRailBlock.SHAPE.with(state, shape);
        if (alwaysPlace || level.getBlockState(pos) != state) {
            level.setBlock(pos, state, 3);
            for (int i = 0; i < connections.size(); i++) {
                RailState r = getRail(connections.get(i));
                if (r != null) {
                    r.removeSoftConnections();
                    if (r.canConnectTo(this)) r.connectTo(this);
                }
            }
        }
        return this;
    }

    int state() { return state; }
}
