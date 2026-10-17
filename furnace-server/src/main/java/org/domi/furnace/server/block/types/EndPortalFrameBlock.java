// 바닐라 EndPortalFrameBlock: 엔드 차원문 틀. 바라보는 쪽을 향해 놓이고, 엔더의 눈이 끼면 비교기 신호 15.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class EndPortalFrameBlock extends BlockBehavior {

    static final Prop FACING = B.prop("facing"), EYE = B.prop("eye");

    public EndPortalFrameBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return EYE.with(FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]), false);
    }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return EYE.is(state) ? 15 : 0; }

    /** 무늬(BlockPatternBuilder.aisle) — '?' 아무 칸, 화살표는 눈이 낀 틀과 그 facing. 깊이 1 · 높이 5 · 너비 5. */
    private static final String[] PORTAL_SHAPE = {"?vvv?", ">???<", ">???<", ">???<", "?^^^?"};

    public static boolean hasEye(int state) { return EYE.is(state); }

    public static Prop eyeProp() { return EYE; }

    /**
     * getOrCreatePortalShape().find(level, pos): pos 부터 (+4, +4, +4) 까지 칸(x 먼저, 그다음 y, z)마다 손가락·엄지 방향 쌍을 모두 대 보고
     * 처음 맞는 무늬의 frontTopLeft. 없으면 null.
     */
    public static Pos findPortalShape(BlockLevel level, Pos pos) {
        int size = 5;
        for (int z = 0; z < size; z++) for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            Pos base = pos.offset(x, y, z);
            for (int finger : Dir.ALL) {
                for (int thumb : Dir.ALL) {
                    if (thumb == finger || thumb == Dir.opposite(finger)) continue;
                    if (matches(level, base, finger, thumb)) return base;
                }
            }
        }
        return null;
    }

    private static boolean matches(BlockLevel level, Pos base, int finger, int thumb) {
        for (int i = 0; i < 5; i++) {          // 너비(palm)
            for (int j = 0; j < 5; j++) {      // 높이(thumb)
                char c = PORTAL_SHAPE[j].charAt(i);
                if (c == '?') continue;
                int s = level.getBlockState(translateAndRotate(base, finger, thumb, i, j, 0));
                int facing = switch (c) {
                    case '^' -> Dir.SOUTH;
                    case '>' -> Dir.WEST;
                    case 'v' -> Dir.NORTH;
                    default -> Dir.EAST;
                };
                if (!(B.behavior(s) instanceof EndPortalFrameBlock) || !EYE.is(s) || Dir.byName(FACING.valueName(s)) != facing) return false;
            }
        }
        return true;
    }

    /** BlockPattern.translateAndRotate(pos, finger, thumb, palm, thumbOffset, fingerOffset). */
    private static Pos translateAndRotate(Pos pos, int finger, int thumb, int palm, int thumbOffset, int fingerOffset) {
        int fx = Dir.DX[finger], fy = Dir.DY[finger], fz = Dir.DZ[finger];
        int tx = Dir.DX[thumb], ty = Dir.DY[thumb], tz = Dir.DZ[thumb];
        int cx = fy * tz - fz * ty, cy = fz * tx - fx * tz, cz = fx * ty - fy * tx;   // finger × thumb
        return pos.offset(tx * -thumbOffset + cx * palm + fx * -fingerOffset,
                ty * -thumbOffset + cy * palm + fy * -fingerOffset,
                tz * -thumbOffset + cz * palm + fz * -fingerOffset);
    }
}
