// 바닐라 PortalShape: 흑요석 틀 안 지옥문 모양 찾기(불 붙이기로 만들기, 틀이 깨지면 무너뜨리기).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public final class PortalShape {

    private static final int MAX = 21;
    private static final int OBSIDIAN = B.id("obsidian"), NETHER_PORTAL = B.id("nether_portal");
    private static final Prop AXIS = B.prop("axis");

    private final BlockLevel level;
    private final int axis;
    private final int rightDir;
    private int numPortalBlocks;
    private Pos bottomLeft;
    private int height;
    private final int width;

    public PortalShape(BlockLevel level, Pos pos, int axis) {
        this.level = level;
        this.axis = axis;
        this.rightDir = axis == Dir.AXIS_X ? Dir.WEST : Dir.SOUTH;
        this.bottomLeft = calculateBottomLeft(pos);
        if (bottomLeft == null) {
            bottomLeft = pos;
            width = 1;
            height = 1;
        } else {
            width = calculateWidth();
            if (width > 0) height = calculateHeight();
        }
    }

    /** findEmptyPortalShape: 빈(지옥문 칸이 없는) 올바른 틀 — 주어진 축, 안 되면 다른 수평 축. */
    public static PortalShape findEmpty(BlockLevel level, Pos pos, int axis) {
        PortalShape s = new PortalShape(level, pos, axis);
        if (s.isValid() && s.numPortalBlocks == 0) return s;
        s = new PortalShape(level, pos, axis == Dir.AXIS_X ? Dir.AXIS_Z : Dir.AXIS_X);
        return s.isValid() && s.numPortalBlocks == 0 ? s : null;
    }

    private static boolean frame(int state) { return B.is(state, OBSIDIAN); }

    private static boolean isEmpty(int state) { return B.isAir(state) || B.isTag(state, "minecraft:fire") || B.is(state, NETHER_PORTAL); }

    private Pos calculateBottomLeft(Pos pos) {
        int minY = Math.max(level.minBuildHeight(), pos.y() - MAX);
        while (pos.y() > minY && isEmpty(level.getBlockState(pos.below()))) pos = pos.below();
        int dir = Dir.opposite(rightDir);
        int d = getDistanceUntilEdgeAboveFrame(pos, dir) - 1;
        return d < 0 ? null : pos.relative(dir, d);
    }

    private int calculateWidth() {
        int w = getDistanceUntilEdgeAboveFrame(bottomLeft, rightDir);
        return w >= 2 && w <= MAX ? w : 0;
    }

    private int getDistanceUntilEdgeAboveFrame(Pos pos, int dir) {
        for (int i = 0; i <= MAX; i++) {
            Pos p = pos.relative(dir, i);
            int s = level.getBlockState(p);
            if (!isEmpty(s)) {
                if (frame(s)) return i;
                break;
            }
            if (!frame(level.getBlockState(p.below()))) break;
        }
        return 0;
    }

    private int calculateHeight() {
        int h = getDistanceUntilTop();
        return h >= 3 && h <= MAX && hasTopFrame(h) ? h : 0;
    }

    private boolean hasTopFrame(int h) {
        for (int i = 0; i < width; i++) {
            if (!frame(level.getBlockState(bottomLeft.relative(Dir.UP, h).relative(rightDir, i)))) return false;
        }
        return true;
    }

    private int getDistanceUntilTop() {
        for (int h = 0; h < MAX; h++) {
            Pos row = bottomLeft.relative(Dir.UP, h);
            if (!frame(level.getBlockState(row.relative(rightDir, -1)))) return h;
            if (!frame(level.getBlockState(row.relative(rightDir, width)))) return h;
            for (int i = 0; i < width; i++) {
                int s = level.getBlockState(row.relative(rightDir, i));
                if (!isEmpty(s)) return h;
                if (B.is(s, NETHER_PORTAL)) numPortalBlocks++;
            }
        }
        return MAX;
    }

    public boolean isValid() { return bottomLeft != null && width >= 2 && width <= MAX && height >= 3 && height <= MAX; }

    /** createPortalBlocks: 틀 안을 지옥문(축)으로 채운다(플래그 18 — 이웃 알림·모양 갱신 없음). */
    public void createPortalBlocks() {
        int portal = AXIS.with(B.defaultState(NETHER_PORTAL), Dir.axisName(axis));
        for (int h = 0; h < height; h++) {
            for (int i = 0; i < width; i++) level.setBlock(bottomLeft.relative(Dir.UP, h).relative(rightDir, i), portal, 18);
        }
    }

    /** isComplete: 올바른 틀이고 안이 모두 지옥문. */
    public boolean isComplete() { return isValid() && numPortalBlocks == width * height; }
}
