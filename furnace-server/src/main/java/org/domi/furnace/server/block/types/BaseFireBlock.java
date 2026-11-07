// 바닐라 BaseFireBlock: 불·영혼 불의 바탕. 놓이면(오버월드·네더) 흑요석 틀 안이면 지옥문을 만들고, 살 수 없으면 사라진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public abstract class BaseFireBlock extends BlockBehavior {

    static final int FIRE = B.id("fire"), SOUL_FIRE = B.id("soul_fire"), OBSIDIAN = B.id("obsidian");

    protected BaseFireBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return getState(ctx.level(), ctx.clickedPos()); }

    /** getState(level, pos): 아래가 영혼 불 바탕이면 영혼 불, 아니면 불의 설치 상태. */
    public static int getState(BlockLevel level, Pos pos) {
        int below = level.getBlockState(pos.below());
        return SoulFireBlock.canSurviveOnBlock(below) ? B.defaultState(SOUL_FIRE) : FireBlock.instance().stateForPlacement(level, pos);
    }

    /** canBurn: 이 불이 그 블록을 태울 수 있는가. */
    protected abstract boolean canBurn(int state);

    /** fireDamage: 불 1, 영혼 불 2. */
    public float fireDamage() { return 1.0F; }

    /** entityInside: 불에 강하지 않으면 불 틱 +1(0 이 되면 8초 불붙이기), 그리고 불 속 피해. */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        if (!entity.fireImmune()) {
            entity.setRemainingFireTicks(entity.remainingFireTicks() + 1);
            if (entity.remainingFireTicks() == 0) entity.igniteForSeconds(8.0F);
        }
        entity.hurt(org.domi.furnace.server.entity.Entity.Damage.IN_FIRE, fireDamage());
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (B.is(oldState, B.block(state))) return;
        if (inPortalDimension(level)) {
            PortalShape shape = PortalShape.findEmpty(level, pos, Dir.AXIS_X);
            if (shape != null) {
                shape.createPortalBlocks();
                return;
            }
        }
        if (!canSurvive(state, level, pos)) level.removeBlock(pos, false);
    }

    private static boolean inPortalDimension(BlockLevel level) {
        String d = level.dimension();
        return d.equals("minecraft:overworld") || d.equals("minecraft:the_nether");
    }

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        level.levelEvent(null, 1009, pos, 0);   // 불 끄는 소리
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** canBePlacedAt(level, pos, 바라보는 쪽): 부싯돌·불쏘시개가 불을 놓을 수 있는가(빈칸이고 살 수 있거나 지옥문 틀). */
    public static boolean canBePlacedAt(BlockLevel level, Pos pos, int dir) {
        if (!B.isAir(level.getBlockState(pos))) return false;
        int s = getState(level, pos);
        return B.canSurvive(s, level, pos) || isPortal(level, pos, dir);
    }

    private static boolean isPortal(BlockLevel level, Pos pos, int dir) {
        if (!inPortalDimension(level)) return false;
        boolean frame = false;
        for (int d : Dir.ALL) {
            if (B.is(level.getBlockState(pos.relative(d)), OBSIDIAN)) {
                frame = true;
                break;
            }
        }
        if (!frame) return false;
        int axis = Dir.horizontal(dir) ? Dir.axis(Dir.counterClockWise(dir)) : (level.random().nextInt(2) == 0 ? Dir.AXIS_X : Dir.AXIS_Z);
        return PortalShape.findEmpty(level, pos, axis) != null;
    }
}
