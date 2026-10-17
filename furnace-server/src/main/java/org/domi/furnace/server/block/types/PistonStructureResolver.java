// 바닐라 PistonStructureResolver: 피스톤이 밀거나 당길 블록 목록(최대 12)과 부술 블록 목록을 만든다.
// 슬라임·꿀 블록이 옆 블록을 끌고 오는 가지 뻗기·순서 재배열(reorderListAtCollision)까지 바닐라 순서 그대로.
package org.domi.furnace.server.block.types;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;

public class PistonStructureResolver {

    public static final int MAX_PUSH_DEPTH = 12;
    private static final int SLIME = B.id("slime_block"), HONEY = B.id("honey_block");

    private final BlockLevel level;
    private final Pos pistonPos;
    private final boolean extending;
    private final Pos startPos;
    private final int pushDirection;
    private final List<Pos> toPush = new ArrayList<>();
    private final List<Pos> toDestroy = new ArrayList<>();
    private final int pistonDirection;

    public PistonStructureResolver(BlockLevel level, Pos pistonPos, int pistonDirection, boolean extending) {
        this.level = level;
        this.pistonPos = pistonPos;
        this.pistonDirection = pistonDirection;
        this.extending = extending;
        if (extending) {
            this.pushDirection = pistonDirection;
            this.startPos = pistonPos.relative(pistonDirection);
        } else {
            this.pushDirection = Dir.opposite(pistonDirection);
            this.startPos = pistonPos.relative(pistonDirection, 2);
        }
    }

    public boolean resolve() {
        toPush.clear();
        toDestroy.clear();
        int state = level.getBlockState(startPos);
        if (!PistonBaseBlock.isPushable(state, level, startPos, pushDirection, false, pistonDirection)) {
            if (extending && pushReaction(state).equals("destroy")) {
                toDestroy.add(startPos);
                return true;
            }
            return false;
        }
        if (!addBlockLine(startPos, pushDirection)) return false;
        for (int i = 0; i < toPush.size(); i++) {
            Pos p = toPush.get(i);
            if (isSticky(level.getBlockState(p)) && !addBranchingBlocks(p)) return false;
        }
        return true;
    }

    private static String pushReaction(int state) { return B.INFO.pushReaction(B.block(state)); }

    private static boolean isSticky(int state) { int b = B.block(state); return b == SLIME || b == HONEY; }

    /** canStickToEachOther: 슬라임과 꿀끼리는 붙지 않는다. */
    private static boolean canStickToEachOther(int a, int b) {
        int ba = B.block(a), bb = B.block(b);
        if (ba == HONEY && bb == SLIME) return false;
        if (ba == SLIME && bb == HONEY) return false;
        return isSticky(a) || isSticky(b);
    }

    private boolean addBlockLine(Pos origin, int direction) {
        int state = level.getBlockState(origin);
        if (B.isAir(state)) return true;
        if (!PistonBaseBlock.isPushable(state, level, origin, pushDirection, false, direction)) return true;
        if (origin.equals(pistonPos)) return true;
        if (toPush.contains(origin)) return true;
        int i = 1;
        if (i + toPush.size() > MAX_PUSH_DEPTH) return false;
        int back = Dir.opposite(pushDirection);
        while (isSticky(state)) {
            Pos p = origin.relative(back, i);
            int prev = state;
            state = level.getBlockState(p);
            if (B.isAir(state) || !canStickToEachOther(prev, state)
                    || !PistonBaseBlock.isPushable(state, level, p, pushDirection, false, back) || p.equals(pistonPos)) {
                break;
            }
            if (++i + toPush.size() > MAX_PUSH_DEPTH) return false;
        }
        int added = 0;
        for (int k = i - 1; k >= 0; k--) {
            toPush.add(origin.relative(back, k));
            added++;
        }
        int j = 1;
        while (true) {
            Pos p = origin.relative(pushDirection, j);
            int idx = toPush.indexOf(p);
            if (idx > -1) {
                reorderListAtCollision(added, idx);
                for (int k = 0; k <= idx + added; k++) {
                    Pos q = toPush.get(k);
                    if (isSticky(level.getBlockState(q)) && !addBranchingBlocks(q)) return false;
                }
                return true;
            }
            state = level.getBlockState(p);
            if (B.isAir(state)) return true;
            if (!PistonBaseBlock.isPushable(state, level, p, pushDirection, true, pushDirection) || p.equals(pistonPos)) return false;
            if (pushReaction(state).equals("destroy")) {
                toDestroy.add(p);
                return true;
            }
            if (toPush.size() >= MAX_PUSH_DEPTH) return false;
            toPush.add(p);
            added++;
            j++;
        }
    }

    private void reorderListAtCollision(int added, int collisionIndex) {
        List<Pos> a = new ArrayList<>(toPush.subList(0, collisionIndex));
        List<Pos> b = new ArrayList<>(toPush.subList(toPush.size() - added, toPush.size()));
        List<Pos> c = new ArrayList<>(toPush.subList(collisionIndex, toPush.size() - added));
        toPush.clear();
        toPush.addAll(a);
        toPush.addAll(b);
        toPush.addAll(c);
    }

    /** addBranchingBlocks: 끈끈한 블록 옆(미는 축이 아닌 방향)의 붙는 블록 줄을 더한다. */
    private boolean addBranchingBlocks(Pos from) {
        int state = level.getBlockState(from);
        for (int d : Dir.ALL) {
            if (Dir.axis(d) == Dir.axis(pushDirection)) continue;
            Pos p = from.relative(d);
            int s = level.getBlockState(p);
            if (canStickToEachOther(s, state) && !addBlockLine(p, d)) return false;
        }
        return true;
    }

    public int getPushDirection() { return pushDirection; }

    public List<Pos> getToPush() { return toPush; }

    public List<Pos> getToDestroy() { return toDestroy; }
}
