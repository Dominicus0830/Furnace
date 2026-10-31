// 바닐라 FenceBlock: 울타리. 단단한 면(연결 예외 블록 제외)·같은 계열 울타리(나무끼리·네더 벽돌끼리)·옆으로 선 울타리 문에 붙는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class FenceBlock extends CrossCollisionBlock {

    private final boolean wooden;

    public FenceBlock(int block) {
        super(block);
        this.wooden = B.isTag(defaultState(), "minecraft:wooden_fences");
    }

    /** connectsTo(state, faceSturdy, dir). */
    public boolean connectsTo(int state, boolean sturdy, int dir) {
        boolean sameFence = B.isTag(state, "minecraft:fences") && B.isTag(state, "minecraft:wooden_fences") == wooden;
        boolean gate = B.behavior(state) instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(state, dir);
        return !B.isExceptionForConnection(state) && sturdy || sameFence || gate;
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // FenceBlock.useWithoutItem: LeadItem.bindPlayerMobs — 끌던 몹을 이 울타리에 묶는다(묶은 게 있으면 SUCCESS)
        if (level instanceof org.domi.furnace.server.block.ChunkLevel cl && player.handle() instanceof org.domi.furnace.server.entity.Player p
                && org.domi.furnace.server.entity.LeashFenceKnotEntity.bindPlayerMobs(p, cl, pos, player)) {
            cl.gameEvent("minecraft:block_attach", pos, player, -1);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        Pos n = pos.north(), e = pos.east(), s = pos.south(), w = pos.west();
        int ns = level.getBlockState(n), es = level.getBlockState(e), ss = level.getBlockState(s), ws = level.getBlockState(w);
        int st = super.getStateForPlacement(ctx);
        st = NORTH.with(st, connectsTo(ns, B.isFaceSturdy(ns, level, n, Dir.SOUTH), Dir.SOUTH));
        st = EAST.with(st, connectsTo(es, B.isFaceSturdy(es, level, e, Dir.WEST), Dir.WEST));
        st = SOUTH.with(st, connectsTo(ss, B.isFaceSturdy(ss, level, s, Dir.NORTH), Dir.NORTH));
        st = WEST.with(st, connectsTo(ws, B.isFaceSturdy(ws, level, w, Dir.EAST), Dir.EAST));
        return WATERLOGGED.with(st, B.waterAt(ctx));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        if (Dir.horizontal(dir)) {
            int o = Dir.opposite(dir);
            return BY_DIR[dir].with(state, connectsTo(neighborState, B.isFaceSturdy(neighborState, level, neighborPos, o), o));
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
