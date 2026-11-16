// 바닐라 Evoker: 송곳니(EvokerFangs)를 솟게 하고, 벡스를 불러내고, 파란 양을 빨갛게 바꾸는 주문을 쓰는 일러저.
// TODO(습격): 습격 중 승리 축하·집단 소환 제한은 습격 시스템이 생기면.
package org.domi.furnace.server.entity;

import java.util.List;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class Evoker extends SpellcasterIllager {
    private Sheep wololoTarget;

    public Evoker(ChunkLevel level, int id) {
        super(EntityType.EVOKER, level, id);
        xpReward = 10;
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.5).add(Attribute.FOLLOW_RANGE, 12.0).add(Attribute.MAX_HEALTH, 24.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new EvokerCastingSpellGoal());
        goalSelector.addGoal(2, new AvoidEntityGoal(this, Player.class, 8.0F, 0.6, 1.0));
        goalSelector.addGoal(4, new EvokerSummonSpellGoal());
        goalSelector.addGoal(5, new EvokerAttackSpellGoal());
        goalSelector.addGoal(6, new EvokerWololoSpellGoal());
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new InteractGoal(this, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtMobGoal(this, Mob.class, 8.0F, 0.02F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true).setUnseenMemoryTicks(300));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Villager.class, false).setUnseenMemoryTicks(300));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, false));
    }

    @Override
    public boolean isAlliedTo(Targetable other) {
        if (other == null) return false;
        if (other == this) return true;
        if (super.isAlliedTo(other)) return true;
        return other instanceof Vex v ? v.getOwner() != null && isAlliedTo(v.getOwner()) : false;
    }

    @Override public void applyRaidBuffs(int wave, boolean captain) { }

    @Override public String getAmbientSound() { return "entity.evoker.ambient"; }

    @Override protected String getDeathSound() { return "entity.evoker.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.evoker.hurt"; }

    @Override protected String getCastingSoundEvent() { return "entity.evoker.cast_spell"; }

    void setWololoTarget(Sheep s) { wololoTarget = s; }

    Sheep getWololoTarget() { return wololoTarget; }

    private static double distSqr(Entity a, Targetable b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        return dx * dx + dy * dy + dz * dz;
    }

    class EvokerCastingSpellGoal extends SpellcasterCastingSpellGoal {
        @Override
        public void tick() {
            if (getTarget() != null) getLookControl().setLookAt(getTarget().x(), getTarget().eyeY(), getTarget().z(), getMaxHeadYRot(), getMaxHeadXRot());
            else if (wololoTarget != null) getLookControl().setLookAt(wololoTarget, getMaxHeadYRot(), getMaxHeadXRot());
        }
    }

    class EvokerAttackSpellGoal extends SpellcasterUseSpellGoal {
        @Override protected int getCastingTime() { return 40; }

        @Override protected int getCastingInterval() { return 100; }

        @Override
        protected void performSpellCasting() {
            Targetable target = getTarget();
            double minY = Math.min(target.y(), y());
            double maxY = Math.max(target.y(), y()) + 1.0;
            float angle = (float) Mth.atan2(target.z() - z(), target.x() - x());
            if (distSqr(Evoker.this, target) < 9.0) {
                for (int i = 0; i < 5; i++) {
                    float f = angle + i * (float) Math.PI * 0.4F;
                    createSpellEntity(x() + Mth.cos(f) * 1.5, z() + Mth.sin(f) * 1.5, minY, maxY, f, 0);
                }
                for (int j = 0; j < 8; j++) {
                    float f = angle + j * (float) Math.PI * 2.0F / 8.0F + (float) (Math.PI * 2.0 / 5.0);
                    createSpellEntity(x() + Mth.cos(f) * 2.5, z() + Mth.sin(f) * 2.5, minY, maxY, f, 3);
                }
            } else {
                for (int k = 0; k < 16; k++) {
                    double d = 1.25 * (k + 1);
                    createSpellEntity(x() + Mth.cos(angle) * d, z() + Mth.sin(angle) * d, minY, maxY, angle, k);
                }
            }
        }

        private void createSpellEntity(double x, double z, double minY, double maxY, float angle, int delay) {
            Pos pos = new Pos((int) Math.floor(x), (int) Math.floor(maxY), (int) Math.floor(z));
            boolean found = false;
            double yOffset = 0.0;
            do {
                Pos below = pos.below();
                int belowState = level.getBlockState(below);
                if (B.isFaceSturdy(belowState, level, below, Dir.UP)) {
                    if (!level.isEmptyBlock(pos)) {
                        int state = level.getBlockState(pos);
                        double[] b = B.behavior(state).getCollisionShape(state, level, pos, org.domi.furnace.server.block.CollisionContext.EMPTY).bounds();
                        if (b != null) yOffset = b[4];
                    }
                    found = true;
                    break;
                }
                pos = pos.below();
            } while (pos.y() >= (int) Math.floor(minY) - 1);
            if (found) {
                EvokerFangs fangs = new EvokerFangs(level, level.nextEntityId());
                fangs.init(x, pos.y() + yOffset, z, angle, delay, Evoker.this);
                level.addFreshEntity(fangs);
                level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_PLACE, new Vec3(x, pos.y() + yOffset, z),
                        org.domi.furnace.server.world.gameevent.GameEventContext.of(Evoker.this));
            }
        }

        @Override protected String getSpellPrepareSound() { return "entity.evoker.prepare_attack"; }

        @Override protected IllagerSpell getSpell() { return IllagerSpell.FANGS; }
    }

    class EvokerSummonSpellGoal extends SpellcasterUseSpellGoal {
        private final TargetingConditions vexCountTargeting = TargetingConditions.forNonCombat().range(16.0).ignoreLineOfSight().ignoreInvisibilityTesting();

        @Override
        public boolean canUse() {
            if (!super.canUse()) return false;
            int count = 0;
            for (Vex v : level.getEntitiesOfClass(Vex.class, boundingBox().inflate(16.0), x -> true)) if (vexCountTargeting.test(Evoker.this, v)) count++;
            return random.nextInt(8) + 1 > count;
        }

        @Override protected int getCastingTime() { return 100; }

        @Override protected int getCastingInterval() { return 340; }

        @Override
        protected void performSpellCasting() {
            for (int i = 0; i < 3; i++) {
                Pos base = blockPosition();
                Pos pos = new Pos(base.x() - 2 + random.nextInt(5), base.y() + 1, base.z() - 2 + random.nextInt(5));
                Vex vex = new Vex(level, level.nextEntityId());
                vex.moveTo(pos.x() + 0.5, pos.y(), pos.z() + 0.5, 0.0F, 0.0F);
                vex.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(pos));
                vex.setOwner(Evoker.this);
                vex.setBoundOrigin(pos);
                vex.setLimitedLife(20 * (30 + random.nextInt(90)));
                level.addFreshEntity(vex.spawnReason("SPELL"));
                level.gameEventFrom("minecraft:entity_place", pos, Evoker.this, -1);
            }
        }

        @Override protected String getSpellPrepareSound() { return "entity.evoker.prepare_summon"; }

        @Override protected IllagerSpell getSpell() { return IllagerSpell.SUMMON_VEX; }
    }

    class EvokerWololoSpellGoal extends SpellcasterUseSpellGoal {
        private final TargetingConditions wololoTargeting =
                TargetingConditions.forNonCombat().range(16.0).selector(t -> t instanceof Sheep sh && sh.getColor() == DyeColor.BLUE);

        @Override
        public boolean canUse() {
            if (getTarget() != null) return false;
            if (isCastingSpell()) return false;
            if (tickCount < nextAttackTickCount) return false;
            if (!level.gameRule("mobGriefing")) return false;
            // ServerLevel.getNearbyEntities(Sheep, wololoTargeting, this, 상자): 상자 안 양을 넣은 순서대로 조건(거리 16·시야·파란 양)으로 거른다.
            List<Sheep> list = new java.util.ArrayList<>();
            for (Sheep s : level.getEntitiesOfClass(Sheep.class, boundingBox().inflate(16.0, 4.0, 16.0), x -> true)) if (wololoTargeting.test(Evoker.this, s)) list.add(s);
            if (list.isEmpty()) return false;
            wololoTarget = list.get(random.nextInt(list.size()));
            return true;
        }

        @Override
        public boolean canContinueToUse() { return wololoTarget != null && attackWarmupDelay > 0; }

        @Override
        public void stop() {
            super.stop();
            wololoTarget = null;
        }

        @Override
        protected void performSpellCasting() {
            Sheep s = wololoTarget;
            if (s != null && s.isAlive()) s.setColor(DyeColor.RED);
        }

        @Override protected int getCastWarmupTime() { return 40; }

        @Override protected int getCastingTime() { return 60; }

        @Override protected int getCastingInterval() { return 140; }

        @Override protected String getSpellPrepareSound() { return "entity.evoker.prepare_wololo"; }

        @Override protected IllagerSpell getSpell() { return IllagerSpell.WOLOLO; }
    }

    @Override public String getCelebrateSound() { return "entity.evoker.celebrate"; }
}
