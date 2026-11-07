// 바닐라 Pufferfish: 가까이 오는 것을 보면 부풀어(0→1→2) 닿는 대상을 찌르고 독(60틱×단계)을 건다. 부푼 정도에 따라 몸집이 커진다.
// 몹은 aiStep 의 주변 탐색(Mob 만)으로, 플레이어는 플레이어 쪽 접촉(playerTouch)으로 찔린다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Pufferfish extends AbstractFish {
    public static final int DATA_PUFF_STATE = 17;
    private static final Predicate<Targetable> SCARY_MOB = t -> {
        if (t instanceof Player p && p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return false;
        return !(t instanceof Entity e) || !e.type().is("minecraft:not_scary_for_pufferfish");
    };
    static final TargetingConditions TARGETING_CONDITIONS = TargetingConditions.forNonCombat().ignoreInvisibilityTesting().ignoreLineOfSight().selector(SCARY_MOB);

    int inflateCounter;
    int deflateTimer;

    public Pufferfish(ChunkLevel level, int id) {
        super(EntityType.PUFFERFISH, level, id);
        refreshPuffDimensions();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PUFF_STATE, SetEntityData.SERIALIZER_INT, 0);
    }

    public int getPuffState() { return (Integer) entityData.get(DATA_PUFF_STATE); }

    public void setPuffState(int state) {
        entityData.set(DATA_PUFF_STATE, state);
        refreshPuffDimensions();
    }

    private static float getScale(int state) {
        return switch (state) {
            case 0 -> 0.5F;
            case 1 -> 0.7F;
            default -> 1.0F;
        };
    }

    /** getDefaultDimensions: 기본 크기(0.7×0.7, 눈 0.455)를 부푼 정도로 줄인다. */
    private void refreshPuffDimensions() {
        float s = getScale(getPuffState());
        setDimensions(EntityType.PUFFERFISH.width() * s, EntityType.PUFFERFISH.height() * s, EntityType.PUFFERFISH.eyeHeight() * s);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("PuffState", getPuffState());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setPuffState(Math.min(tag.getIntOr0("PuffState"), 2));
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new PufferfishPuffGoal(this));
    }

    @Override
    public void tick() {
        if (isAlive() && isEffectiveAi()) {
            if (inflateCounter > 0) {
                if (getPuffState() == 0) {
                    playSound("entity.puffer_fish.blow_up", getSoundVolume(), getVoicePitch());
                    setPuffState(1);
                } else if (inflateCounter > 40 && getPuffState() == 1) {
                    playSound("entity.puffer_fish.blow_up", getSoundVolume(), getVoicePitch());
                    setPuffState(2);
                }
                inflateCounter++;
            } else if (getPuffState() != 0) {
                if (deflateTimer > 60 && getPuffState() == 2) {
                    playSound("entity.puffer_fish.blow_out", getSoundVolume(), getVoicePitch());
                    setPuffState(1);
                } else if (deflateTimer > 100 && getPuffState() == 1) {
                    playSound("entity.puffer_fish.blow_out", getSoundVolume(), getVoicePitch());
                    setPuffState(0);
                }
                deflateTimer++;
            }
        }
        super.tick();
    }

    @Override
    protected void aiStep() {
        super.aiStep();
        if (isAlive() && getPuffState() > 0) {
            // cfx.aiStep: getEntitiesOfClass(Mob.class) — 플레이어·갑옷 거치대는 여기서 찔리지 않는다
            for (Mob target : level.getEntitiesOfClass(Mob.class, boundingBox().inflate(0.3), t -> TARGETING_CONDITIONS.test(this, t))) {
                if (target.isAlive()) touch(target);
            }
        }
    }

    private void touch(Mob target) {
        int state = getPuffState();
        if (target.hurtByMob(new Damage("mob_attack", false, false, false), 1 + state, this)) {
            target.addEffect(new MobEffectInstance(MobEffect.POISON, 60 * state, 0));
            playSound("entity.puffer_fish.sting", 1.0F, 1.0F);
        }
    }

    /**
     * Pufferfish.playerTouch(cfx): 부풀어 있고 플레이어를 1+단계 만큼 때렸으면, 조용하지 않을 때 그 플레이어에게 PUFFER_FISH_STING(9) 게임 이벤트
     * (클라이언트가 찔림 소리를 낸다) → 독 60×단계 틱.
     */
    @Override
    public void playerTouch(Player p) {
        int state = getPuffState();
        if (state > 0 && p.hurtByMob(new Damage("mob_attack", false, false, false), 1 + state, this)) {
            if (!isSilent()) p.send(new org.domi.furnace.server.net.protocol.play.GameEvent(org.domi.furnace.server.net.protocol.play.GameEvent.PUFFER_FISH_STING, 0.0F));
            p.vitals().addEffect(new MobEffectInstance(MobEffect.POISON, 60 * state, 0));
        }
    }

    @Override public String getAmbientSound() { return "entity.puffer_fish.ambient"; }

    @Override protected String getDeathSound() { return "entity.puffer_fish.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.puffer_fish.hurt"; }

    @Override protected String getFlopSound() { return "entity.puffer_fish.flop"; }

    /** PufferfishPuffGoal: 2칸 안에 겁나는 대상이 있으면 부풀기 시작하고, 없어지면 멈춘다. */
    static final class PufferfishPuffGoal extends Goal {
        private final Pufferfish fish;

        PufferfishPuffGoal(Pufferfish fish) { this.fish = fish; }

        @Override
        public boolean canUse() {
            List<LivingEntity> list = fish.level().getEntitiesOfClass(LivingEntity.class, fish.boundingBox().inflate(2.0), t -> TARGETING_CONDITIONS.test(fish, t));
            if (!list.isEmpty()) return true;
            boolean[] found = {false};
            fish.level().forEachPlayerIn(fish.boundingBox().inflate(2.0), p -> {
                if (TARGETING_CONDITIONS.test(fish, p)) found[0] = true;
            });
            return found[0];
        }

        @Override
        public void start() {
            fish.inflateCounter = 1;
            fish.deflateTimer = 0;
        }

        @Override
        public void stop() { fish.inflateCounter = 0; }
    }

    @Override public ItemStack getBucketItemStack() { return stackOf("pufferfish_bucket"); }
}
