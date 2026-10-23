// 바닐라 NetherPortalBlock: 틀이 깨지거나 같은 축 이웃이 지옥문이 아니게 되면 무너진다. 무작위 틱으로 좀비 피글린을 낳는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class NetherPortalBlock extends BlockBehavior {

    private static final Prop AXIS = B.prop("axis");

    public NetherPortalBlock(int block) { super(block); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if ("minecraft:overworld".equals(level.dimension()) && level.gameRule("doMobSpawning")
                && random.nextInt(2000) < level.difficulty()) {
            // TODO(엔티티): 지옥문 맨 아래까지 내려가 그 위에 좀비 피글린 생성(isValidSpawn), 포탈 쿨다운 설정
        }
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        int dirAxis = Dir.axis(dir);
        int axis = axis(state);
        boolean otherHorizontal = axis != dirAxis && dirAxis != Dir.AXIS_Y;
        return !otherHorizontal && !B.is(neighborState, block) && !new PortalShape(level, pos, axis).isComplete()
                ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    private static int axis(int state) { return "x".equals(AXIS.valueName(state)) ? Dir.AXIS_X : Dir.AXIS_Z; }

    // TODO(엔티티): entityInside — 포탈 이동(차원 이동 체계 뒤).
}
