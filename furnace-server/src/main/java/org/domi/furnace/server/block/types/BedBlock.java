// 바닐라 BedBlock: 두 칸 침대(발·머리). 쓰면 잠자기·스폰 지점(오버월드), 잠잘 수 없는 차원이면 터진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class BedBlock extends HorizontalDirectionalBlock {

    static final Prop PART = B.prop("part"), OCCUPIED = B.prop("occupied");

    public BedBlock(int block) { super(block); }

    private static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    private static boolean head(int state) { return "head".equals(PART.valueName(state)); }

    /** getNeighbourDirection: 발이면 facing(머리 쪽), 머리면 반대. */
    private static int neighbourDirection(int state) { return head(state) ? Dir.opposite(facing(state)) : facing(state); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!head(state)) {
            pos = pos.relative(facing(state));
            state = level.getBlockState(pos);
            if (!B.is(state, block)) return InteractionResult.CONSUME;
        }
        if (!canSetSpawn(level)) {
            level.removeBlock(pos, false);
            Pos other = pos.relative(Dir.opposite(facing(state)));
            if (B.is(level.getBlockState(other), block)) level.removeBlock(other, false);
            // TODO(폭발): level.explode(BAD_RESPAWN_POINT, 가운데, 5.0, 불, BLOCK)
            return InteractionResult.SUCCESS;
        }
        if (OCCUPIED.is(state)) {
            // TODO(엔티티): 자고 있는 마을 사람이 있으면 깨운다(kickVillagerOutOfBed) — 없으면 메시지
            level.sendActionBar(player, org.domi.furnace.api.text.Text.translatable("block.minecraft.bed.occupied"));
            return InteractionResult.SUCCESS;
        }
        // TODO(수면): Player.startSleepInBed — 스폰 지점 설정, 밤·천둥·괴물 검사, 문제 메시지
        return InteractionResult.SUCCESS;
    }

    /** canSetSpawn: 차원의 bedWorks(오버월드만). */
    public static boolean canSetSpawn(BlockLevel level) { return "minecraft:overworld".equals(level.dimension()); }

    // TODO(엔티티): fallOn(낙하 피해 절반)·updateEntityAfterFallOn(튕김).

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == neighbourDirection(state)) {
            return B.is(neighborState, block) && head(neighborState) != head(state) ? OCCUPIED.with(state, OCCUPIED.is(neighborState)) : B.defaultState("air");
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        if (player.isCreative() && !head(state)) {
            Pos other = pos.relative(facing(state));
            int os = level.getBlockState(other);
            if (B.is(os, block) && head(os)) {
                level.setBlock(other, B.defaultState("air"), 35);
                level.levelEvent(player, 2001, other, os);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int dir = ctx.horizontalDirection();
        Pos other = ctx.clickedPos().relative(dir);
        return B.canBeReplaced(ctx.level().getBlockState(other), ctx) && ctx.level().withinBorder(other)
                ? FACING.with(defaultState(), Dir.NAME[dir]) : -1;
    }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        level.setBlock(pos.relative(facing(state)), PART.with(state, "head"), 3);
        level.blockUpdated(pos, B.id("air"));
        B.updateNeighbourShapes(state, level, pos, 3);
    }
}
