// 바닐라 AbstractCauldronBlock: 가마솥 바탕. 아이템 쓰기는 종류별 CauldronInteraction 표, 예약 틱은 위 종유석에서 떨어진 유체 받기.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;

public abstract class AbstractCauldronBlock extends BlockBehavior {

    protected final CauldronInteraction.Table interactions;

    protected AbstractCauldronBlock(int block, CauldronInteraction.Table interactions) {
        super(block);
        this.interactions = interactions;
    }

    /** getContentHeight: 엔티티가 안에 있는지 볼 때 쓰는 내용물 높이. */
    protected double getContentHeight(int state) { return 0.0; }

    /** isEntityInsideContent: 엔티티 발이 내용물 높이 아래이고 몸통 위가 바닥(0.25)보다 높은가. */
    protected boolean isEntityInsideContent(int state, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        return isInsideContent(state, pos, entity.y(), entity.boundingBox().maxY());
    }

    /** isEntityInsideContent 의 좌표 꼴(플레이어는 Entity 가 아니라서). */
    public boolean isInsideContent(int state, Pos pos, double feetY, double topY) {
        return feetY < pos.y() + getContentHeight(state) && topY > pos.y() + 0.25;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        return interactions.get(stack).interact(state, level, pos, player, hand, stack);
    }

    public abstract boolean isFull(int state);

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        Pos tip = PointedDripstoneBlock.findStalactiteTipAboveCauldron(level, pos);
        if (tip == null) return;
        int fluid = PointedDripstoneBlock.getCauldronFillFluidType(level, tip);
        if (fluid != Fluids.EMPTY && canReceiveStalactiteDrip(fluid)) receiveStalactiteDrip(state, level, pos, fluid);
    }

    /** 유체 종류(Fluids.T_WATER·T_LAVA)를 종유석에서 받을 수 있는가. */
    protected boolean canReceiveStalactiteDrip(int fluid) { return false; }

    protected void receiveStalactiteDrip(int state, BlockLevel level, Pos pos, int fluid) {}

}
