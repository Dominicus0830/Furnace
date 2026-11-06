// 바닐라 TraderLlama: 방랑 상인의 라마. 길들여지지 않고 끈에 안 매였으면 DespawnDelay(47999)틱 뒤 사라진다. 새끼는 짐꾼 라마.
// TODO(방랑 상인·끈): 상인에게 매인 동안 상인의 사라짐 시간을 따르는 것·상인을 지키는 TraderLlamaDefendWanderingTraderGoal·EVENT 스폰 시 나이 0 은 방랑 상인/끈이 생기면.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.PanicGoal;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class TraderLlama extends Llama {
    private int despawnDelay = 47999;

    public TraderLlama(ChunkLevel level, int id) { super(EntityType.TRADER_LLAMA, level, id); }

    @Override public boolean isTraderLlama() { return true; }

    @Override
    protected Llama makeNewLlama(ChunkLevel level) { return new TraderLlama(level, level.nextEntityId()); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DespawnDelay", despawnDelay);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("DespawnDelay") instanceof Number n) despawnDelay = n.intValue();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new PanicGoal(this, 2.0));
    }

    public void setDespawnDelay(int v) { despawnDelay = v; }

    @Override
    public void tick() {
        super.tick();
        if (!isTamed() && !isLeashed() && !hasPlayerPassenger() && --despawnDelay <= 0) discard();
    }

    @Override
    public AgeableMob.GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, AgeableMob.GroupData data) {
        if (data == null) data = new AgeableMob.GroupData(false);
        return super.finalizeSpawn(r, difficulty, data);
    }
}
