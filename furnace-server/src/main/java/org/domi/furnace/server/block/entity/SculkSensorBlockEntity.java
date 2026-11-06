// 바닐라 SculkSensorBlockEntity: 진동 수신기(반경 8). 마지막 진동 주파수(비교기 출력)와 진동 상태("listener")를 저장한다.
// 보정된 조각 감지기(CalibratedSculkSensorBlockEntity)는 반경 16, 뒤쪽 신호가 있으면 그 주파수만 듣는다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.CalibratedSculkSensorBlock;
import org.domi.furnace.server.block.types.SculkSensorBlock;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.domi.furnace.server.world.gameevent.GameEventListener;
import org.domi.furnace.server.world.gameevent.PositionSource;
import org.domi.furnace.server.world.gameevent.VibrationSystem;

public class SculkSensorBlockEntity extends BlockEntity implements VibrationSystem, GameEventListener.Provider {

    private static final int SCULK_SENSOR = B.id("sculk_sensor"), CALIBRATED = B.id("calibrated_sculk_sensor");

    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.Listener vibrationListener = new VibrationSystem.Listener(this);
    private final VibrationSystem.User vibrationUser;
    private int lastVibrationFrequency;
    /** 진동이 바뀌면 청크를 저장 대상으로(setChanged) — 틱 중에 받은 레벨. */
    private BlockLevel level;

    public SculkSensorBlockEntity(Pos pos, int state) { this("minecraft:sculk_sensor", pos, state); }

    protected SculkSensorBlockEntity(String type, Pos pos, int state) {
        super(type, pos, state);
        this.vibrationUser = createVibrationUser();
    }

    public static SculkSensorBlockEntity calibrated(Pos pos, int state) { return new Calibrated(pos, state); }

    protected VibrationSystem.User createVibrationUser() { return new VibrationUser(pos); }

    @Override
    public void load(NbtCompound tag) {
        lastVibrationFrequency = tag.getIntOr0("last_vibration_frequency");
        if (tag.tags().get(Data.NBT_TAG_KEY) instanceof NbtCompound l) {
            Data d = Data.load(l);
            if (d != null) vibrationData = d;
        }
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        tag.putInt("last_vibration_frequency", lastVibrationFrequency);
        tag.putCompound(Data.NBT_TAG_KEY, vibrationData.save());
    }

    @Override
    public Data getVibrationData() { return vibrationData; }

    @Override
    public User getVibrationUser() { return vibrationUser; }

    @Override
    public GameEventListener getListener() { return vibrationListener; }

    public VibrationSystem.Listener getVibrationListener() { return vibrationListener; }

    public int getLastVibrationFrequency() { return lastVibrationFrequency; }

    public void setLastVibrationFrequency(int f) { lastVibrationFrequency = f; }

    @Override
    public boolean ticking() { return true; }

    /** 블록 getTicker: VibrationSystem.Ticker.tick(서버 레벨만). */
    @Override
    public void tick(BlockLevel level) {
        this.level = level;
        if (level instanceof ChunkLevel cl) VibrationSystem.Ticker.tick(cl, vibrationData, vibrationUser);
    }

    @Override
    public boolean isValid(int state) { return B.block(state) == SCULK_SENSOR; }

    protected class VibrationUser implements VibrationSystem.User {
        public static final int LISTENER_RANGE = 8;
        protected final Pos blockPos;
        private final PositionSource positionSource;

        public VibrationUser(Pos pos) {
            this.blockPos = pos;
            this.positionSource = PositionSource.block(pos);
        }

        @Override
        public int getListenerRadius() { return LISTENER_RANGE; }

        @Override
        public PositionSource getPositionSource() { return positionSource; }

        @Override
        public boolean canTriggerAvoidVibration() { return true; }

        @Override
        public boolean canReceiveVibration(ChunkLevel level, Pos pos, GameEvent event, GameEventContext ctx) {
            return !pos.equals(blockPos) || event != GameEvent.BLOCK_DESTROY && event != GameEvent.BLOCK_PLACE
                    ? SculkSensorBlock.canActivate(blockState()) : false;
        }

        @Override
        public void onReceiveVibration(ChunkLevel level, Pos pos, GameEvent event, Object sourceEntity, Object projectileOwner, float distance) {
            int state = blockState();
            if (SculkSensorBlock.canActivate(state)) {
                setLastVibrationFrequency(event.frequency());
                int power = VibrationSystem.getRedstoneStrengthForDistance(distance, getListenerRadius());
                if (B.behavior(state) instanceof SculkSensorBlock b) b.activate(sourceEntity, level, blockPos, state, power, getLastVibrationFrequency());
            }
        }

        @Override
        public void onDataChanged() {
            if (level != null) setChanged(level);
        }

        @Override
        public boolean requiresAdjacentChunksToBeTicking() { return true; }
    }

    /** CalibratedSculkSensorBlockEntity. */
    public static final class Calibrated extends SculkSensorBlockEntity {

        public Calibrated(Pos pos, int state) { super("minecraft:calibrated_sculk_sensor", pos, state); }

        @Override
        protected VibrationSystem.User createVibrationUser() { return new CalibratedUser(pos); }

        @Override
        public boolean isValid(int state) { return B.block(state) == CALIBRATED; }

        private final class CalibratedUser extends VibrationUser {
            CalibratedUser(Pos pos) { super(pos); }

            @Override
            public int getListenerRadius() { return 16; }

            @Override
            public boolean canReceiveVibration(ChunkLevel level, Pos pos, GameEvent event, GameEventContext ctx) {
                int back = getBackSignal(level, blockPos, blockState());
                return back != 0 && event.frequency() != back ? false : super.canReceiveVibration(level, pos, event, ctx);
            }

            private static int getBackSignal(BlockLevel level, Pos pos, int state) {
                int d = Dir.opposite(CalibratedSculkSensorBlock.facing(state));
                return level.getSignal(pos.relative(d), d);
            }
        }
    }
}
