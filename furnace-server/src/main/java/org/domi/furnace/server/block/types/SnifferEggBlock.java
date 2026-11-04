// 바닐라 SnifferEggBlock: 스니퍼 알(hatch 0..2). 놓이면 예약 틱(이끼 블록 위면 절반 시간)마다 금이 가고, 다 되면 아기 스니퍼가 나온다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class SnifferEggBlock extends BlockBehavior {

    static final Prop HATCH = B.prop("hatch");
    public static final int MAX_HATCH_LEVEL = 2;
    private static final int REGULAR_HATCH_TIME_TICKS = 24000, BOOSTED_HATCH_TIME_TICKS = 12000, RANDOM_HATCH_OFFSET_TICKS = 300;

    public SnifferEggBlock(int block) { super(block); }

    public int getHatchLevel(int state) { return HATCH.intValue(state); }

    private boolean isReadyToHatch(int state) { return getHatchLevel(state) == MAX_HATCH_LEVEL; }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!isReadyToHatch(state)) {
            level.playSound(null, pos, "minecraft:block.sniffer_egg.crack", "blocks", 0.7F, 0.9F + random.nextFloat() * 0.2F);
            level.setBlock(pos, HATCH.withInt(state, getHatchLevel(state) + 1), 2);
        } else {
            level.playSound(null, pos, "minecraft:block.sniffer_egg.hatch", "blocks", 0.7F, 0.9F + random.nextFloat() * 0.2F);
            level.destroyBlock(pos, false);
            // 아기 스니퍼를 칸 가운데에 만든다 — 몸 방향은 레벨 난수 wrapDegrees(nextFloat × 360).
            level.spawnBabySniffer(pos, wrapDegrees(level.random().nextFloat() * 360.0F));
        }
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        boolean boosted = hatchBoost(level, pos);
        if (boosted) level.levelEvent(null, 3009, pos, 0);
        int time = boosted ? BOOSTED_HATCH_TIME_TICKS : REGULAR_HATCH_TIME_TICKS;
        level.gameEvent("minecraft:block_place", pos, null, state);
        level.scheduleTick(pos, block, time / 3 + level.random().nextInt(RANDOM_HATCH_OFFSET_TICKS));
    }

    /** Mth.wrapDegrees(float). */
    private static float wrapDegrees(float v) {
        float f = v % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    /** hatchBoost: 아래가 #sniffer_egg_hatch_boost(이끼 블록). */
    public static boolean hatchBoost(BlockLevel level, Pos pos) { return B.isTag(level.getBlockState(pos.below()), "minecraft:sniffer_egg_hatch_boost"); }
}
