// 바닐라 Ravager: 덩치 큰 습격자 짐승. 달리다 막히면 나뭇잎을 부수고, 방패에 막히면 기절했다가 포효(주변 6 피해 + 강한 밀치기)한다.
// TODO(탑승): 일러저 기수 태우기와 기수에 따른 목표 플래그(updateControlFlags)는 탑승 시스템이 생기면.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class Ravager extends Raider {
    private static final double BASE_MOVEMENT_SPEED = 0.3, ATTACK_MOVEMENT_SPEED = 0.35;
    private int attackTick, stunnedTick, roarTick;

    public Ravager(ChunkLevel level, int id) {
        super(EntityType.RAVAGER, level, id);
        xpReward = 20;
        setPathfindingMalus(PathType.LEAVES, 0.0F);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 100.0).add(Attribute.MOVEMENT_SPEED, 0.3).add(Attribute.KNOCKBACK_RESISTANCE, 0.75).add(Attribute.ATTACK_DAMAGE, 12.0)
                .add(Attribute.ATTACK_KNOCKBACK, 1.5).add(Attribute.FOLLOW_RANGE, 32.0).add(Attribute.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.4));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(10, new LookAtMobGoal(this, Mob.class, 8.0F, 0.02F));
        targetSelector.addGoal(2, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Player.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal(this, Villager.class, 10, true, false, t -> !((Villager) t).isBaby()));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal(this, IronGolem.class, true));
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AttackTick", attackTick);
        tag.putInt("StunTick", stunnedTick);
        tag.putInt("RoarTick", roarTick);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        attackTick = tag.getIntOr0("AttackTick");
        stunnedTick = tag.getIntOr0("StunTick");
        roarTick = tag.getIntOr0("RoarTick");
    }

    @Override public int getMaxHeadYRot() { return 45; }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive()) return;
        if (isImmobile()) {
            attributes().get(Attribute.MOVEMENT_SPEED).setBaseValue(0.0);
        } else {
            double wanted = getTarget() != null ? ATTACK_MOVEMENT_SPEED : BASE_MOVEMENT_SPEED;
            double cur = attributes().get(Attribute.MOVEMENT_SPEED).baseValue();
            attributes().get(Attribute.MOVEMENT_SPEED).setBaseValue(cur + 0.1 * (wanted - cur));
        }
        if (horizontalCollision && level.gameRule("mobGriefing")) {
            boolean destroyed = false;
            AABB box = boundingBox().inflate(0.2);
            int x0 = (int) Math.floor(box.minX()), y0 = (int) Math.floor(box.minY()), z0 = (int) Math.floor(box.minZ());
            int x1 = (int) Math.floor(box.maxX()), y1 = (int) Math.floor(box.maxY()), z1 = (int) Math.floor(box.maxZ());
            for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) {
                Pos p = new Pos(bx, by, bz);
                if (B.isTag(level.getBlockState(p), "minecraft:leaves")) destroyed = level.destroyBlock(p, true, null, 512) || destroyed;
            }
            if (!destroyed && onGround()) jumpFromGround();
        }
        if (roarTick > 0) {
            roarTick--;
            if (roarTick == 10) roar();
        }
        if (attackTick > 0) attackTick--;
        if (stunnedTick > 0) {
            stunnedTick--;
            stunnedEffects();
            if (stunnedTick == 0) {
                playSound("entity.ravager.roar", 1.0F, 1.0F);
                roarTick = 20;
            }
        }
    }

    /** 기절 중 6틱에 한 번 입자(난수 소비 보존: 위치 두 번의 [0,1) 난수). */
    private void stunnedEffects() {
        if (random.nextInt(6) == 0) {
            random.nextDouble();
            random.nextDouble();
        }
    }

    @Override
    public boolean isImmobile() { return super.isImmobile() || attackTick > 0 || stunnedTick > 0 || roarTick > 0; }

    @Override
    public boolean hasLineOfSight(Targetable t) { return stunnedTick <= 0 && roarTick <= 0 && super.hasLineOfSight(t); }

    private void strongKnockback(Targetable e) {
        double dx = e.x() - x(), dz = e.z() - z();
        double d = Math.max(dx * dx + dz * dz, 0.001);
        e.addDeltaMovement(dx / d * 4.0, 0.2, dz / d * 4.0);
        if (e instanceof Entity en) en.hurtMarked = true;
    }

    /** Ravager.blockedByShield: 포효 중이 아니면 절반 확률로 40틱 기절(소리·이벤트 39·방패 쥔 쪽을 밀기), 아니면 강한 밀치기. */
    public void blockedByShield(Targetable defender) {
        if (roarTick != 0) return;
        if (random.nextDouble() < 0.5) {
            stunnedTick = 40;
            playSound("entity.ravager.stunned", 1.0F, 1.0F);
            broadcastEntityEvent((byte) 39);
            pushAway(defender);
        } else {
            strongKnockback(defender);
        }
        if (defender instanceof Entity en) en.hurtMarked = true;
        else if (defender instanceof Player p) p.markHurt();
    }

    /** defender.push(this): 서로 가로로 0.05 배로 밀어낸다(플레이어는 Entity 가 아니라 직접 계산). */
    private void pushAway(Targetable defender) {
        if (defender instanceof Entity en) {
            en.push(this);
        } else if (defender instanceof Player pl) {
            if (pl.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return;   // isPushable·noPhysics 거짓
            double dx = x() - defender.x(), dz = z() - defender.z();
            double d = Math.max(Math.abs(dx), Math.abs(dz));
            if (d >= 0.01F) {
                d = Math.sqrt(d);
                dx /= d;
                dz /= d;
                double f = Math.min(1.0 / d, 1.0);
                dx *= f * 0.05F;
                dz *= f * 0.05F;
                defender.addDeltaMovement(-dx, 0.0, -dz);
                if (!isVehicle() && isPushable()) push(dx, 0.0, dz);
            }
        }
    }

    private void roar() {
        if (!isAlive()) return;
        AABB box = boundingBox().inflate(4.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !(x instanceof Ravager))) {
            if (!(e instanceof AbstractIllager)) e.hurtByMob(new Damage("mob_attack", false, false, false), 6.0F, this);
            strongKnockback(e);
        }
        level.forEachPlayerIn(box, p -> {
            if (p.isAlive()) {
                p.hurtByMob(new Damage("mob_attack", false, false, false), 6.0F, this);
                strongKnockback(p);
            }
        });
        for (int i = 0; i < 40; i++) {
            random.nextGaussian();
            random.nextGaussian();
            random.nextGaussian();
        }
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_ACTION);
    }

    @Override
    public boolean doHurtTarget(Targetable t) {
        attackTick = 10;
        broadcastEntityEvent((byte) 4);
        playSound("entity.ravager.attack", 1.0F, 1.0F);
        return super.doHurtTarget(t);
    }

    public int getAttackTick() { return attackTick; }

    public int getStunnedTick() { return stunnedTick; }

    public int getRoarTick() { return roarTick; }

    @Override public void applyRaidBuffs(int wave, boolean captain) { }

    /** Ravager.canBeLeader: 약탈수는 순찰 지도자가 되지 않는다. */
    @Override public boolean canBeLeader() { return false; }

    @Override public String getAmbientSound() { return "entity.ravager.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.ravager.hurt"; }

    @Override protected String getDeathSound() { return "entity.ravager.death"; }

    @Override
    public boolean checkSpawnObstruction(ChunkLevel level) { return !level.containsAnyLiquid(boundingBox()); }

    @Override public String getCelebrateSound() { return "entity.ravager.celebrate"; }
}
