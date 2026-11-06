// 바닐라 TrialSpawnerBlockEntity: TrialSpawner 하나를 감싼다. 상태는 블록 상태(trial_spawner_state)에 산다 — 바뀌면 setBlock(…, 3).
// 갱신 태그 = TrialSpawnerData.getUpdateTag(상태). 관리자만 NBT 를 붙일 수 있다(onlyOpCanSetNbt).
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class TrialSpawnerBlockEntity extends BlockEntity implements TrialSpawner.StateAccessor {

    private final TrialSpawner trialSpawner = new TrialSpawner(this);
    /** 마지막으로 틱을 준 레벨 — markUpdated 가 클라이언트 갱신을 보낼 때 쓴다(없으면 저장 표시만). */
    private BlockLevel level;

    public TrialSpawnerBlockEntity(Pos pos, int state) { super("minecraft:trial_spawner", pos, state); }

    @Override public boolean isValid(int state) { return B.is(state, "trial_spawner"); }

    public TrialSpawner trialSpawner() { return trialSpawner; }

    @Override public boolean onlyOpCanSetNbt() { return true; }

    @Override
    public void load(NbtCompound tag) { trialSpawner.load(tag); }

    @Override
    protected void saveAdditional(NbtCompound tag) { trialSpawner.save(tag); }

    @Override
    public NbtCompound updateTag() { return trialSpawner.getData().getUpdateTag(getState()); }

    @Override public boolean hasUpdatePacket() { return true; }

    @Override public boolean ticking() { return true; }

    @Override
    public void tick(BlockLevel level) {
        this.level = level;
        if (level instanceof ChunkLevel cl) trialSpawner.tickServer(cl, pos, TrialSpawner.OMINOUS.is(blockState));
    }

    /** setEntityId(type, random): 스폰 알을 쓸 때. */
    public void setEntityId(String type, RandomSource random, BlockLevel level) {
        this.level = level;
        trialSpawner.getData().setEntityId(trialSpawner, random, type);
        setChanged(level);
    }

    @Override
    public TrialSpawnerState getState() {
        return TrialSpawner.STATE.has(blockState) ? TrialSpawnerState.byName(TrialSpawner.STATE.valueName(blockState)) : TrialSpawnerState.INACTIVE;
    }

    @Override
    public void setState(BlockLevel level, TrialSpawnerState state) {
        setChanged(level);
        level.setBlockAndUpdate(pos, TrialSpawner.STATE.with(blockState, state.getSerializedName()));
    }

    /** markUpdated: 저장 표시 + 클라이언트에 블록 엔티티 데이터를 다시 보낸다. */
    @Override
    public void markUpdated() {
        if (level != null) {
            setChanged(level);
            level.sendBlockUpdated(pos);
        }
    }
}
