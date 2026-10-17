// 바닐라 DaylightDetectorBlock: 햇빛 감지기. 하늘빛·해 각도로 신호(0..15)를 정하고(뒤집으면 15 - 빛), 누르면 뒤집힌다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class DaylightDetectorBlock extends BaseEntityBlock {

    static final Prop POWER = B.prop("power"), INVERTED = B.prop("inverted");

    public DaylightDetectorBlock(int block) { super(block); }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return POWER.intValue(state); }

    /** updateSignalStrength: 하늘빛 - skyDarken 을 해 각도로 줄인다(뒤집혔으면 15 - 빛). */
    public static void updateSignalStrength(int state, BlockLevel level, Pos pos) {
        int i = level.getSkyLight(pos) - level.skyDarken();
        float angle = B.timeOfDay(level) * (float) (Math.PI * 2);
        if (INVERTED.is(state)) {
            i = 15 - i;
        } else if (i > 0) {
            float target = angle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
            angle += (target - angle) * 0.2F;
            i = Math.round(i * Dir.cos(angle));
        }
        i = Math.clamp(i, 0, 15);
        if (POWER.intValue(state) != i) level.setBlock(pos, POWER.withInt(state, i), 3);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!player.mayBuild()) return super.useWithoutItem(state, level, pos, player, hit);
        int s = INVERTED.cycle(state);
        level.setBlock(pos, s, 2);
        level.gameEvent("minecraft:block_change", pos, player, s);
        updateSignalStrength(s, level, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isSignalSource(int state) { return true; }

    /** newBlockEntity — 20틱마다 신호를 다시 재는 틱은 블록 엔티티가(getTicker → tickEntity). */
    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        return new org.domi.furnace.server.block.entity.DaylightDetectorBlockEntity(pos, state);
    }
}
