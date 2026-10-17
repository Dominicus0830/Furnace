// 바닐라 SculkSensorBlock: 진동을 들으면 켜져(active) 30틱 신호를 내고, 쿨다운 뒤 쉰다. 물에 잠길 수 있다.
// 진동 듣기(게임 이벤트 리스너·블록 엔티티)는 TODO(게임 이벤트).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class SculkSensorBlock extends BaseEntityBlock {

    static final Prop PHASE = B.prop("sculk_sensor_phase"), POWER = B.prop("power");
    /** 진동 주파수별 소리 높이(NoteBlock.getPitchFromNote). */
    private static final float[] RESONANCE_PITCH_BEND = new float[16];

    static {
        int[] notes = {0, 0, 2, 4, 6, 7, 9, 10, 12, 14, 15, 18, 19, 21, 22, 24};
        for (int i = 0; i < 16; i++) RESONANCE_PITCH_BEND[i] = (float) Math.pow(2.0, (notes[i] - 12) / 12.0);
    }

    public SculkSensorBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return WATERLOGGED.with(defaultState(), B.waterAt(ctx)); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        String phase = PHASE.valueName(state);
        if (!"active".equals(phase)) {
            if ("cooldown".equals(phase)) {
                level.setBlock(pos, PHASE.with(state, "inactive"), 3);
                if (!WATERLOGGED.is(state)) {
                    level.playSound(null, pos, "minecraft:block.sculk_sensor.clicking_stop", "blocks", 1.0F, level.random().nextFloat() * 0.2F + 0.8F);
                }
            }
        } else {
            deactivate(level, pos, state);
        }
    }

    // TODO(엔티티·게임 이벤트): stepOn — 웅크리지 않은 엔티티가 밟으면 STEP 진동.

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, block) && POWER.intValue(state) > 0 && !level.hasScheduledTick(pos, block)) {
            level.setBlock(pos, POWER.withInt(state, 0), 18);
        }
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block)) {
            super.onRemove(state, level, pos, newState, moved);
            if ("active".equals(PHASE.valueName(state))) updateNeighbours(level, pos, state);
        }
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    private static void updateNeighbours(BlockLevel level, Pos pos, int state) {
        int b = B.block(state);
        level.updateNeighborsAt(pos, b);
        level.updateNeighborsAt(pos.below(), b);
    }

    @Override
    public boolean isSignalSource(int state) { return true; }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return POWER.intValue(state); }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) {
        return dir == Dir.UP ? B.behavior(state).getSignal(state, level, pos, dir) : 0;
    }

    public static boolean canActivate(int state) { return "inactive".equals(PHASE.valueName(state)); }

    /** deactivate: 쿨다운으로, 10틱 뒤 틱. */
    public static void deactivate(BlockLevel level, Pos pos, int state) {
        level.setBlock(pos, POWER.withInt(PHASE.with(state, "cooldown"), 0), 3);
        level.scheduleTick(pos, B.block(state), 10);
        updateNeighbours(level, pos, state);
    }

    /** getActiveTicks: 켜져 있는 틱 수. */
    protected int activeTicks() { return 30; }

    /** activate: 켜고 신호 세기 power, 공명 블록(#vibration_resonators)에 공명 이벤트. */
    public void activate(BlockActor source, BlockLevel level, Pos pos, int state, int power, int frequency) {
        level.setBlock(pos, POWER.withInt(PHASE.with(state, "active"), power), 3);
        level.scheduleTick(pos, B.block(state), activeTicks());
        updateNeighbours(level, pos, state);
        tryResonateVibration(source, level, pos, frequency);
        level.gameEvent("minecraft:sculk_sensor_tendrils_clicking", pos, source, -1);
        if (!WATERLOGGED.is(state)) {
            level.playSound(null, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, "minecraft:block.sculk_sensor.clicking", "blocks",
                    1.0F, level.random().nextFloat() * 0.2F + 0.8F);
        }
    }

    public static void tryResonateVibration(BlockActor source, BlockLevel level, Pos pos, int frequency) {
        for (int d : Dir.ALL) {
            Pos p = pos.relative(d);
            int s = level.getBlockState(p);
            if (B.isTag(s, "minecraft:vibration_resonators")) {
                level.gameEvent(RESONATE[frequency], p, source, s);
                level.playSound(null, p, "minecraft:block.amethyst_block.resonate", "blocks", 1.0F, RESONANCE_PITCH_BEND[frequency]);
            }
        }
    }

    /** VibrationSystem.getResonanceEventByFrequency(1..15). */
    private static final String[] RESONATE = new String[16];

    static {
        for (int i = 1; i <= 15; i++) RESONATE[i] = "minecraft:resonate_" + i;
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        // TODO(블록엔티티): 켜져 있으면 마지막 진동 주파수
        return 0;
    }

    @Override
    public void spawnAfterBreak(int state, BlockLevel level, Pos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (dropExperience) tryDropExperience(level, pos, tool, 5, 5);
    }
}
