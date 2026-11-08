// 바닐라 DetectorRailBlock: 감지 레일. 칸 안(0.2 안쪽 상자)에 광산 수레가 있으면 신호, 20 틱마다 다시 본다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class DetectorRailBlock extends BaseRailBlock {

    private static final Prop POWERED = B.prop("powered");

    public DetectorRailBlock(int block) { super(block, true); }

    @Override
    public boolean isSignalSource(int state) { return true; }

    /** entityInside: 꺼져 있으면 다시 본다. */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        if (!POWERED.is(state)) checkPressed(level, pos, state);
    }

    /** getSearchBB: 칸에서 옆·위로 0.2 안쪽. */
    private static org.domi.furnace.server.entity.AABB searchBox(Pos p) {
        return new org.domi.furnace.server.entity.AABB(p.x() + 0.2, p.y(), p.z() + 0.2, p.x() + 1 - 0.2, p.y() + 1 - 0.2, p.z() + 1 - 0.2);
    }

    private static <T extends org.domi.furnace.server.entity.AbstractMinecart> java.util.List<T> carts(BlockLevel level, Pos pos, Class<T> cls,
            java.util.function.Predicate<? super T> filter) {
        if (!(level instanceof org.domi.furnace.server.block.ChunkLevel cl)) return java.util.List.of();
        return cl.getEntitiesOfClass(cls, searchBox(pos), filter);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (POWERED.is(state)) checkPressed(level, pos, state);
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) ? 15 : 0; }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) && dir == Dir.UP ? 15 : 0; }

    private void checkPressed(BlockLevel level, Pos pos, int state) {
        if (!canSurvive(state, level, pos)) return;
        boolean was = POWERED.is(state);
        boolean pressed = !carts(level, pos, org.domi.furnace.server.entity.AbstractMinecart.class, e -> true).isEmpty();
        if (pressed && !was) {
            int s = POWERED.with(state, true);
            level.setBlock(pos, s, 3);
            updatePowerToConnected(level, pos, s);
            level.updateNeighborsAt(pos, block);
            level.updateNeighborsAt(pos.below(), block);
        }
        if (!pressed && was) {
            int s = POWERED.with(state, false);
            level.setBlock(pos, s, 3);
            updatePowerToConnected(level, pos, s);
            level.updateNeighborsAt(pos, block);
            level.updateNeighborsAt(pos.below(), block);
        }
        if (pressed) level.scheduleTick(pos, block, 20);
        level.updateNeighbourForOutputSignal(pos, block);
    }

    private static void updatePowerToConnected(BlockLevel level, Pos pos, int state) {
        for (Pos p : new RailState(level, pos, state).connections()) {
            int s = level.getBlockState(p);
            level.neighborChanged(s, p, B.block(s), pos, false);
        }
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, B.block(state))) {
            int s = updateState(state, level, pos, moved);
            checkPressed(level, pos, s);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        if (POWERED.is(state)) {
            var cmd = carts(level, pos, org.domi.furnace.server.entity.MinecartCommandBlock.class, e -> true);
            if (!cmd.isEmpty()) return cmd.get(0).successCount();
            // CONTAINER_ENTITY_SELECTOR: 용기이고 살아 있다
            var c = carts(level, pos, org.domi.furnace.server.entity.AbstractMinecartContainer.class, e -> e.isAlive());
            if (!c.isEmpty()) {
                var cart = c.get(0);
                cart.unpackLootTable();   // getRedstoneSignalFromContainer 의 getItem 이 열지 않은 전리품 표를 먼저 푼다
                return cart.redstoneSignal();
            }
        }
        return 0;
    }
}
