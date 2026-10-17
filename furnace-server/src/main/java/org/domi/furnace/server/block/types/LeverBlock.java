// 바닐라 LeverBlock: 당길 때마다 켜고 끈다. 붙은 칸에 강한 신호.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class LeverBlock extends FaceAttachedHorizontalDirectionalBlock {

    static final Prop POWERED = B.prop("powered");

    public LeverBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        pull(state, level, pos, null);
        return InteractionResult.CONSUME;
    }

    // TODO(폭발): onExplosionHit — 폭발이 블록을 터뜨리면 당긴다.

    /** pull: 켜짐을 뒤집고 이웃 알림, 소리, 게임 이벤트. */
    public void pull(int state, BlockLevel level, Pos pos, BlockActor player) {
        state = POWERED.cycle(state);
        level.setBlock(pos, state, 3);
        updateNeighbours(state, level, pos);
        playSound(player, level, pos, state);
        level.gameEvent(POWERED.is(state) ? "minecraft:block_activate" : "minecraft:block_deactivate", pos, player, -1);
    }

    static void playSound(BlockActor player, BlockLevel level, Pos pos, int state) {
        level.playSound(player, pos, "minecraft:block.lever.click", "blocks", 0.3F, POWERED.is(state) ? 0.6F : 0.5F);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!moved && !B.is(newState, block)) {
            if (POWERED.is(state)) updateNeighbours(state, level, pos);
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) ? 15 : 0; }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) {
        return POWERED.is(state) && getConnectedDirection(state) == dir ? 15 : 0;
    }

    @Override
    public boolean isSignalSource(int state) { return true; }

    private void updateNeighbours(int state, BlockLevel level, Pos pos) {
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(Dir.opposite(getConnectedDirection(state))), block);
    }
}
