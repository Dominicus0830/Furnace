// 바닐라 BucketPickup.pickupBlock 구현들(LiquidBlock·SimpleWaterloggedBlock·BubbleColumnBlock·PowderSnowBlock): 양동이로 뜨는 동작.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public final class BucketPickup {
    private BucketPickup() {}

    private static final int WATER = B.id("water"), LAVA = B.id("lava"), BUBBLE_COLUMN = B.id("bubble_column"), POWDER_SNOW = B.id("powder_snow"),
            BARRIER = B.id("barrier");
    private static final Prop LEVEL = B.prop("level"), WATERLOGGED = B.prop("waterlogged");

    /** 이 블록이 BucketPickup 인가. */
    public static boolean is(int state) {
        int b = B.block(state);
        return b == WATER || b == LAVA || b == BUBBLE_COLUMN || b == POWDER_SNOW || WATERLOGGED.has(state);
    }

    /** pickupBlock: 뜬 양동이 아이템 이름(못 뜨면 null). */
    public static String pickupBlock(BlockActor player, BlockLevel level, Pos pos, int state) {
        int b = B.block(state);
        if (b == WATER || b == LAVA) {   // LiquidBlock: 원천만
            if (LEVEL.intValue(state) != 0) return null;
            level.setBlock(pos, B.defaultState("air"), 11);
            return b == WATER ? "minecraft:water_bucket" : "minecraft:lava_bucket";
        }
        if (b == BUBBLE_COLUMN) {
            level.setBlock(pos, B.defaultState("air"), 11);
            return "minecraft:water_bucket";
        }
        if (b == POWDER_SNOW) {
            level.setBlock(pos, B.defaultState("air"), 11);
            level.levelEvent(null, 2001, pos, state);
            return "minecraft:powder_snow_bucket";
        }
        if (b == BARRIER && (player == null || !player.isCreative())) return null;   // BarrierBlock: 크리에이티브만
        if (WATERLOGGED.has(state)) {   // SimpleWaterloggedBlock(겹친 반 블록은 아님)
            if (!WATERLOGGED.is(state) || B.behavior(state) instanceof SlabBlock && SlabBlock.isDouble(state)) return null;
            level.setBlock(pos, WATERLOGGED.with(state, false), 3);
            if (!B.canSurvive(state, level, pos)) level.destroyBlock(pos, true);
            return "minecraft:water_bucket";
        }
        return null;
    }

    /** LiquidBlockContainer.placeLiquid: 블록 동작에 맡긴다(물에 잠기는 블록·모닥불·양초 등). */
    public static boolean placeWater(BlockLevel level, Pos pos, int state, int fluid) {
        return B.behavior(state).placeLiquid(level, pos, state, fluid);
    }
}
