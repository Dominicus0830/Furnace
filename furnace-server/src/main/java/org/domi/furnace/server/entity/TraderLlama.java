// 바닐라 TraderLlama(chq): 방랑 상인의 라마. 길들여지지 않고, 상인 말고 다른 것에 매이지 않았고, 플레이어 한 명만 타고 있지 않으면
// DespawnDelay(47999)틱이 줄어 0 이면 끈을 풀고 사라진다. 상인에게 매인 동안은 상인의 남은 시간 - 1 을 따른다. 상인을 때린 상대를 공격한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import java.util.EnumSet;
import org.domi.furnace.server.entity.ai.goal.PanicGoal;
import org.domi.furnace.server.entity.ai.goal.TargetGoal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
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
        targetSelector.addGoal(1, new DefendWanderingTraderGoal(this));
    }

    public void setDespawnDelay(int v) { despawnDelay = v; }

    /** TraderLlama.doPlayerRide: 방랑 상인에게 매여 있으면 타지 못한다. */
    @Override
    protected void doPlayerRide(Player p) {
        if (!(getLeashHolder() instanceof WanderingTrader)) super.doPlayerRide(p);
    }

    @Override
    protected void aiStep() {
        super.aiStep();
        maybeDespawn();
    }

    /** maybeDespawn: 사라질 수 있으면 남은 시간을 줄이고(상인에게 매였으면 상인 시간 - 1), 0 이하면 끈을 풀고(아이템 없이) 사라진다. */
    private void maybeDespawn() {
        if (!canDespawn()) return;
        despawnDelay = getLeashHolder() instanceof WanderingTrader t ? t.despawnDelay() - 1 : despawnDelay - 1;
        if (despawnDelay <= 0) {
            dropLeash(true, false);
            discard();
        }
    }

    private boolean canDespawn() {
        boolean leashedToOther = isLeashed() && !(getLeashHolder() instanceof WanderingTrader);
        return !isTamed() && !leashedToOther && !hasExactlyOnePlayerPassenger();
    }

    /** finalizeSpawn: 무리 데이터가 없으면 아기 없음(AgeableMobGroupData(false)). EVENT 스폰의 setAge(0) 은 새 라마가 이미 나이 0 이라 생략. */
    @Override
    public AgeableMob.GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, AgeableMob.GroupData data) {
        if (data == null) data = new AgeableMob.GroupData(false);
        return super.finalizeSpawn(r, difficulty, data);
    }

    /** TraderLlama.TraderLlamaDefendWanderingTraderGoal: 매어 둔 방랑 상인을 마지막으로 때린 상대를 대상으로 삼는다. */
    static final class DefendWanderingTraderGoal extends TargetGoal {
        private final Llama llama;
        private Targetable ownerLastHurtBy;
        private int timestamp;

        DefendWanderingTraderGoal(Llama llama) {
            super(llama, false);
            this.llama = llama;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!llama.isLeashed() || !(llama.getLeashHolder() instanceof WanderingTrader trader)) return false;
            ownerLastHurtBy = trader.getLastHurtByMob();
            int t = trader.getLastHurtByMobTimestamp();
            return t != timestamp && canAttack(ownerLastHurtBy, TargetingConditions.forCombat());
        }

        @Override
        public void start() {
            mob.setTarget(ownerLastHurtBy);
            if (llama.getLeashHolder() instanceof WanderingTrader trader) timestamp = trader.getLastHurtByMobTimestamp();
            super.start();
        }
    }
}
