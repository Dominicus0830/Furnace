// 바닐라 DaylightDetectorBlockEntity: 저장할 것은 없고, 하늘빛이 있는 차원에서 20틱마다 신호를 다시 잰다(DaylightDetectorBlock.tickEntity).
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.DaylightDetectorBlock;

public class DaylightDetectorBlockEntity extends BlockEntity {

    private static final int DAYLIGHT_DETECTOR = B.id("daylight_detector");

    public DaylightDetectorBlockEntity(Pos pos, int state) { super("minecraft:daylight_detector", pos, state); }

    /** getTicker: 하늘빛이 없는 차원에선 틱을 걸지 않는다 — 여기선 틱 안에서 거른다. */
    @Override
    public boolean ticking() { return true; }

    @Override
    public void tick(BlockLevel level) {
        if (!level.hasSkyLight()) return;
        if (level.gameTime() % 20L == 0L) DaylightDetectorBlock.updateSignalStrength(level.getBlockState(pos), level, pos);
    }

    @Override
    public boolean isValid(int state) { return B.block(state) == DAYLIGHT_DETECTOR; }
}
