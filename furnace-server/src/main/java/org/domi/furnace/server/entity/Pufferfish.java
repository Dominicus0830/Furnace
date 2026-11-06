// 바닐라 Pufferfish: 가까이 오는 것을 보면 부풀어(0→1→2) 닿는 대상을 찌르고 독(60틱×단계)을 건다. 부푼 정도에 따라 몸집이 커진다.
// TODO(플레이어 접촉): 플레이어가 부딪쳐 찔리는 playerTouch(엔티티 접촉 훅 필요). 현재는 aiStep 의 주변 탐색만 한다.
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
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, boundingBox().inflate(0.3), t -> TARGETING_CONDITIONS.test(this, t))) {
                if (target.isAlive()) touch(target);
            }
            level.forEachPlayerIn(boundingBox().inflate(0.3), p -> {
                if (p.isAlive() && TARGETING_CONDITIONS.test(this, p)) touch(p);
            });
        }
    }

    private void touch(Targetable target) {
        int state = getPuffState();
        if (target.hurtByMob(new Damage("mob_attack", false, false, false), 1 + state, this)) {
            MobEffectInstance poison = new MobEffectInstance(MobEffect.POISON, 60 * state, 0);
            if (target instanceof LivingEntity le) le.addEffect(poison);
            else if (target instanceof Player p) p.vitals().addEffect(poison);
            playSound("entity.puffer_fish.sting", 1.0F, 1.0F);
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
