// 바닐라 BedBlock: 두 칸 침대(발·머리). 쓰면 잠자기·스폰 지점(오버월드), 잠잘 수 없는 차원이면 두 칸을 지우고 터진다(불, 5.0).
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

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        return new org.domi.furnace.server.block.entity.BedBlockEntity(pos, state);
    }

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
            // level.explode(null, badRespawnPointExplosion(가운데), null, 가운데, 5.0, 불, BLOCK)
            if (level instanceof org.domi.furnace.server.block.ChunkLevel cl) cl.explodeBadRespawnPoint(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, null, 0.0F);
            return InteractionResult.SUCCESS;
        }
        if (OCCUPIED.is(state)) {
            if (!kickVillagerOutOfBed(level, pos)) {
                level.sendActionBar(player, org.domi.furnace.api.text.Text.translatable("block.minecraft.bed.occupied"));
            }
            return InteractionResult.SUCCESS;
        }
        // player.startSleepInBed(pos).ifLeft(문제 → 메시지가 있으면 액션바)
        org.domi.furnace.api.text.Text problem = player.startSleepInBed(level, pos);
        if (problem != null) level.sendActionBar(player, problem);
        return InteractionResult.SUCCESS;
    }

    /** kickVillagerOutOfBed: 그 칸에서 자는 마을 사람이 있으면 첫째를 깨운다. */
    private static boolean kickVillagerOutOfBed(BlockLevel level, Pos pos) {
        if (!(level instanceof org.domi.furnace.server.block.ChunkLevel cl)) return false;
        var box = new org.domi.furnace.server.entity.AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1);
        var list = cl.getEntitiesOfClass(org.domi.furnace.server.entity.Villager.class, box, org.domi.furnace.server.entity.LivingEntity::isSleeping);
        if (list.isEmpty()) return false;
        list.get(0).stopSleeping();
        return true;
    }

    /** canSetSpawn: 차원의 bedWorks(오버월드만). */
    public static boolean canSetSpawn(BlockLevel level) { return "minecraft:overworld".equals(level.dimension()); }

    @Override
    public void fallOn(BlockLevel level, int state, Pos pos, org.domi.furnace.server.entity.Entity entity, float fallDistance) {
        super.fallOn(level, state, pos, entity, fallDistance * 0.5F);
    }

    /** updateEntityAfterFallOn: 튕김 0.66(생물 아니면 × 0.8). 웅크렸으면 그냥 멈춤. */
    @Override
    public void updateEntityAfterFallOn(BlockLevel level, org.domi.furnace.server.entity.Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(level, entity);
        } else if (entity.vy() < 0.0) {
            double f = entity.isLiving() ? 1.0 : 0.8;
            entity.setDeltaMovement(entity.vx(), -entity.vy() * 0.66F * f, entity.vz());
        }
    }

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
