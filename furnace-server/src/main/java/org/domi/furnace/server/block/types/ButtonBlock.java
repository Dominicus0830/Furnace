// 바닐라 ButtonBlock: 누르면 켜지고 정해진 틱 뒤 꺼진다. 붙은 칸에 강한 신호.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Explosion;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockSetType;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class ButtonBlock extends FaceAttachedHorizontalDirectionalBlock {

    static final Prop POWERED = B.prop("powered");
    private final BlockSetType type;
    private final int ticksToStayPressed;

    public ButtonBlock(int block) {
        super(block);
        this.type = BlockSetType.of(block);
        this.ticksToStayPressed = org.domi.furnace.server.block.Blocks.get().params(block).get("ticks_to_stay_pressed").getAsInt();
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (POWERED.is(state)) return InteractionResult.CONSUME;
        press(state, level, pos, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onExplosionHit(int state, BlockLevel level, Pos pos, Explosion explosion) {
        if (explosion.canTriggerBlocks() && !POWERED.is(state)) press(state, level, pos, null);
    }

    /** press: 켜고 이웃 알림, 꺼질 틱 예약, 소리, 게임 이벤트. */
    public void press(int state, BlockLevel level, Pos pos, BlockActor player) {
        level.setBlock(pos, POWERED.with(state, true), 3);
        updateNeighbours(state, level, pos);
        level.scheduleTick(pos, block, ticksToStayPressed);
        playSound(player, level, pos, true);
        level.gameEvent("minecraft:block_activate", pos, player, -1);
    }

    protected void playSound(BlockActor player, BlockLevel level, Pos pos, boolean on) {
        level.playSound(on ? player : null, pos, on ? type.buttonClickOn() : type.buttonClickOff(), "blocks", 1.0F, 1.0F);
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

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (POWERED.is(state)) checkPressed(state, level, pos);
    }

    /** entityInside: 화살로 눌리는 종류(나무)이고 꺼져 있으면 checkPressed. */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        if (type.canButtonBeActivatedByArrows() && !POWERED.is(state)) checkPressed(state, level, pos);
    }

    /** checkPressed: 이 칸 모양(감싸는 상자) 안에 화살이 있으면 켜고, 없으면 끈다. 바뀌면 이웃·소리·게임 이벤트(출처 = 화살). */
    protected void checkPressed(int state, BlockLevel level, Pos pos) {
        Object arrow = null;
        if (type.canButtonBeActivatedByArrows()) {
            double[] b = getShape(state, level, pos).bounds();
            if (b != null) arrow = level.firstArrowIn(b[0] + pos.x(), b[1] + pos.y(), b[2] + pos.z(), b[3] + pos.x(), b[4] + pos.y(), b[5] + pos.z());
        }
        boolean pressed = arrow != null;
        if (pressed != POWERED.is(state)) {
            level.setBlock(pos, POWERED.with(state, pressed), 3);
            updateNeighbours(state, level, pos);
            playSound(null, level, pos, pressed);
            level.gameEventFrom(pressed ? "minecraft:block_activate" : "minecraft:block_deactivate", pos, arrow, -1);
        }
        if (pressed) level.scheduleTick(pos, block, ticksToStayPressed);
    }

    private void updateNeighbours(int state, BlockLevel level, Pos pos) {
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(Dir.opposite(getConnectedDirection(state))), block);
    }
}
