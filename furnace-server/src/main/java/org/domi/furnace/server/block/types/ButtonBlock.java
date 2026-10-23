// 바닐라 ButtonBlock: 누르면 켜지고 정해진 틱 뒤 꺼진다. 붙은 칸에 강한 신호.
package org.domi.furnace.server.block.types;

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

    // TODO(폭발): onExplosionHit — 폭발이 블록을 터뜨리면 누른다.

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

    // TODO(엔티티): entityInside — 화살에 눌리는 버튼(canButtonBeActivatedByArrows).

    /** checkPressed: 눌러 둔 화살이 없으면 꺼진다(엔티티 체계 전에는 화살이 없다). */
    protected void checkPressed(int state, BlockLevel level, Pos pos) {
        boolean pressed = false;   // TODO(엔티티): 이 칸 모양 안의 화살
        if (pressed != POWERED.is(state)) {
            level.setBlock(pos, POWERED.with(state, pressed), 3);
            updateNeighbours(state, level, pos);
            playSound(null, level, pos, pressed);
            level.gameEvent(pressed ? "minecraft:block_activate" : "minecraft:block_deactivate", pos, null, -1);
        }
        if (pressed) level.scheduleTick(pos, block, ticksToStayPressed);
    }

    private void updateNeighbours(int state, BlockLevel level, Pos pos) {
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(Dir.opposite(getConnectedDirection(state))), block);
    }
}
