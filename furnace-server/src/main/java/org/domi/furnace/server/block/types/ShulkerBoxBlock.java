// 바닐라 ShulkerBoxBlock: 클릭한 면 쪽을 보게 놓인다. 열기(뚜껑이 막히지 않으면)·내용물 보존 드롭은 블록 엔티티 동작.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.entity.ContainerBlockEntity;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class ShulkerBoxBlock extends BaseEntityBlock {

    private static final Prop FACING = B.prop("facing");

    public ShulkerBoxBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return FACING.with(defaultState(), Dir.NAME[ctx.clickedFace()]); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (player.isSpectator()) return InteractionResult.CONSUME;
        if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity be)) return InteractionResult.PASS;
        if (canOpen(state, level, pos, be)) {
            player.openContainer(pos);
            player.awardStat("minecraft:open_shulker_box");
            // TODO(엔티티): PiglinAi.angerNearbyPiglins(player, true)
        }
        return InteractionResult.CONSUME;
    }

    /**
     * canOpen: 닫혀 있지 않으면 열 수 있다. 닫혀 있으면 Shulker.getProgressDeltaAabb(1, facing, 0, 0.5) — 뚜껑 쪽 반 칸 — 를 옮겨
     * 1e-6 줄인 상자에 충돌이 없어야 한다. 가정: 충돌 검사는 블록만(엔티티 충돌·월드 보더는 ChunkLevel.noCollision 에 아직 없다).
     */
    public static boolean canOpen(int state, BlockLevel level, Pos pos, ShulkerBoxBlockEntity be) {
        if (be.animationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED) return true;
        int d = Dir.byName(FACING.valueName(state));
        double minX = 0, minY = 0, minZ = 0, maxX = 1, maxY = 1, maxZ = 1;
        double ex = Dir.DX[d] * 0.5, ey = Dir.DY[d] * 0.5, ez = Dir.DZ[d] * 0.5;   // expandTowards(dir * 0.5)
        if (ex < 0) minX += ex; else maxX += ex;
        if (ey < 0) minY += ey; else maxY += ey;
        if (ez < 0) minZ += ez; else maxZ += ez;
        double cx = -Dir.DX[d] * 1.0, cy = -Dir.DY[d] * 1.0, cz = -Dir.DZ[d] * 1.0;   // contract(-dir * (1 + 0))
        if (cx < 0) minX -= cx; else if (cx > 0) maxX -= cx;
        if (cy < 0) minY -= cy; else if (cy > 0) maxY -= cy;
        if (cz < 0) minZ -= cz; else if (cz > 0) maxZ -= cz;
        double e = 1.0E-6;
        var box = new org.domi.furnace.server.entity.AABB(pos.x() + minX + e, pos.y() + minY + e, pos.z() + minZ + e,
                pos.x() + maxX - e, pos.y() + maxY - e, pos.z() + maxZ - e);
        return !(level instanceof org.domi.furnace.server.block.ChunkLevel cl) || cl.noCollision(box);
    }

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        // TODO(블록엔티티·엔티티): 크리에이티브이고 내용물이 있으면 내용물 담은 셜커 상자 아이템을 떨군다, 아니면 전리품 풀기.
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block)) {
            boolean wasBox = level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity;
            super.onRemove(state, level, pos, newState, moved);
            if (wasBox) level.updateNeighbourForOutputSignal(pos, block);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        // AbstractContainerMenu.getRedstoneSignalFromBlockEntity
        return level.getBlockEntity(pos) instanceof ContainerBlockEntity c ? ChestBlock.redstoneSignalFromContainer(java.util.List.<org.domi.furnace.server.entity.ItemStack[]>of(c.items)) : 0;
    }

    @Override
    public BlockEntity newBlockEntity(Pos pos, int state) { return new org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity(pos, state); }
}
