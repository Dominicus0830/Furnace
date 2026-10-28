// 바닐라 Spider: 벽을 타고(WallClimberNavigation), 밝으면(밝기 ≥ 0.5) 중립이 된다. 거미줄에 걸리지 않고 독에 안 걸린다.
// 스폰 시 해골 기수(1%)와 어려움에서 포션 효과.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.WallClimberNavigation;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Spider extends Monster {
    public static final int DATA_FLAGS_ID = 16;

    public Spider(ChunkLevel level, int id) { this(EntityType.SPIDER, level, id); }

    protected Spider(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) { return new WallClimberNavigation(this, level); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_FLAGS_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new AvoidEntityGoal(this, Armadillo.class, 6.0F, 1.0, 1.2, t -> !((Armadillo) t).isScared()));
        goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(4, new SpiderAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new SpiderTargetGoal(this, Player.class));
        targetSelector.addGoal(3, new SpiderTargetGoal(this, IronGolem.class));
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 16.0).add(Attribute.MOVEMENT_SPEED, 0.3F);
    }

    @Override
    public void tick() {
        super.tick();
        setClimbing(horizontalCollision);
    }

    @Override public boolean onClimbable() { return isClimbing(); }

    @Override public boolean canBeAffected(MobEffectInstance i) { return i.effect() != MobEffect.POISON && super.canBeAffected(i); }

    public boolean isClimbing() { return ((Byte) entityData.get(DATA_FLAGS_ID) & 1) != 0; }

    public void setClimbing(boolean v) {
        byte b = (Byte) entityData.get(DATA_FLAGS_ID);
        entityData.set(DATA_FLAGS_ID, (byte) (v ? b | 1 : b & -2));
    }

    @Override public String getAmbientSound() { return "entity.spider.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.spider.hurt"; }

    @Override protected String getDeathSound() { return "entity.spider.death"; }

    /** Spider.getVehicleAttachmentPoint: 나보다 좁은 탈것에는 (0, 0.3125 × 배율, 0). */
    @Override
    public Vec3 vehicleAttachmentPoint(Entity v) {
        return v.bbWidth() <= bbWidth() ? new Vec3(0.0, riderAttachY() * attributeValue(org.domi.furnace.server.attribute.Attribute.SCALE), 0.0) : super.vehicleAttachmentPoint(v);
    }

    protected double riderAttachY() { return 0.3125; }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
        org.domi.furnace.server.block.RandomSource lr = level.random();
        if (lr.nextInt(100) == 0) {   // 해골 기수: 레벨에 넣지 않고 태운다 — 부른 쪽이 승객째 넣는다
            Skeleton s = new Skeleton(level, level.nextEntityId());
            s.moveTo(x(), y(), z(), yRot(), 0.0F);
            s.finalizeSpawn(lr, difficulty);
            s.startRiding(this);
        }
        // 첫 번째 거미(무리가 없을 때)만: 어려움이면 10% × 지역 난이도로 효과 부여 (그룹 데이터 미구현이라 항상 처음 거미로 본다)
        if (level.difficulty() == 3 && lr.nextFloat() < 0.1F * difficulty.specialMultiplier()) {
            int i = lr.nextInt(5);
            MobEffect eff = i <= 1 ? MobEffect.SPEED : i <= 2 ? MobEffect.STRENGTH : i <= 3 ? MobEffect.REGENERATION : MobEffect.INVISIBILITY;
            addEffect(new MobEffectInstance(eff, MobEffectInstance.INFINITE_DURATION, 0));
        }
    }

    private static final class SpiderAttackGoal extends MeleeAttackGoal {
        private final Spider spider;

        SpiderAttackGoal(Spider spider) {
            super(spider, 1.0, true);
            this.spider = spider;
        }

        @Override public boolean canUse() { return super.canUse() && !spider.isPassenger(); }

        @Override
        public boolean canContinueToUse() {
            float f = spider.getLightLevelDependentMagicValue();
            if (f >= 0.5F && spider.random().nextInt(100) == 0) {
                spider.setTarget(null);
                return false;
            }
            return super.canContinueToUse();
        }
    }

    private static final class SpiderTargetGoal extends NearestAttackableTargetGoal {
        private final Spider spider;

        SpiderTargetGoal(Spider spider, Class<?> type) {
            super(spider, type, true);
            this.spider = spider;
        }

        @Override
        public boolean canUse() {
            float f = spider.getLightLevelDependentMagicValue();
            return f >= 0.5F ? false : super.canUse();
        }
    }
}
