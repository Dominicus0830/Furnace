// 바닐라 Breeze: 바람을 다루는 시련의 몹. 브레인으로 움직이며 대상 주위를 미끄러지다 바람 폭탄을 쏘고(BreezeWindCharge), 대상 뒤쪽으로 길게 뛴다.
// 플레이어·철 골렘만 공격한다. 애니메이션·입자는 클라이언트 몫이라 생략하되 엔티티 난수는 같은 수만큼 쓴다.
// 날아오는 투사체는 바람 구슬이 아니면 튕겨 낸다(projectileDeflection).
package org.domi.furnace.server.entity;

import java.util.Optional;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.entity.ai.navigation.PathType;

public class Breeze extends Monster {
    private int whirlCounter;

    public Breeze(ChunkLevel level, int id) {
        super(EntityType.BREEZE, level, id);
        setPathfindingMalus(PathType.DANGER_TRAPDOOR, -1.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        xpReward = 10;
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Breeze> makeBrain() {
        Brain<Breeze> b = new Brain<>(java.util.List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_ATTACKABLE, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.WALK_TARGET,
                MemoryModuleType.BREEZE_JUMP_COOLDOWN, MemoryModuleType.BREEZE_JUMP_INHALING, MemoryModuleType.BREEZE_SHOOT, MemoryModuleType.BREEZE_SHOOT_CHARGING,
                MemoryModuleType.BREEZE_SHOOT_RECOVER, MemoryModuleType.BREEZE_SHOOT_COOLDOWN, MemoryModuleType.BREEZE_JUMP_TARGET, MemoryModuleType.BREEZE_LEAVING_WATER,
                MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.PATH),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY, SensorType.NEAREST_PLAYERS, SensorType.BREEZE_ATTACK_ENTITY));
        return BreezeAi.makeBrain(this, b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Breeze> breezeBrain() { return (Brain<Breeze>) brain; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.63F).add(Attribute.MAX_HEALTH, 30.0).add(Attribute.FOLLOW_RANGE, 24.0).add(Attribute.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected Targetable getTargetFromBrain() { return super.getTargetFromBrain(); }

    @Override public Targetable getTarget() { return getTargetFromBrain(); }

    /** getHurtBy: 최근 나를 다치게 한 생물. */
    Optional<Targetable> getHurtByTarget() { return breezeBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).map(t -> (Targetable) t); }

    @Override
    public boolean canAttackType(Targetable t) { return t.isPlayer() || t instanceof IronGolem; }

    @Override
    protected void aiStep() {
        int pose = getPose();
        if (pose == POSE_SHOOTING || pose == POSE_INHALING || pose == POSE_STANDING) random.nextInt(1);   // 지면 입자 수(1 + nextInt(1)) — 입자는 클라이언트 몫
        whirlCounter = whirlCounter == 0 ? 1 + random.nextInt(79) : whirlCounter - 1;
        if (whirlCounter == 0) {
            float pitch = 0.7F + 0.4F * random.nextFloat();
            float vol = 0.8F + 0.2F * random.nextFloat();
            level.playSound(null, x(), y(), z(), "minecraft:entity.breeze.whirl", "hostile", vol, pitch);
        }
        super.aiStep();
    }

    /** Entity.lookAt(EYES, pos): 눈에서 그 지점을 바라보게 회전. */
    void lookAtEyes(double px, double py, double pz) {
        double dx = px - x(), dy = py - eyeY(), dz = pz - z();
        double h = Math.sqrt(dx * dx + dz * dz);
        setXRot(wrapDegrees((float) -(org.domi.furnace.server.entity.ai.control.Mth.atan2(dy, h) * 57.29577951308232)));
        setYRot(wrapDegrees((float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(dz, dx) * 57.29577951308232) - 90.0F));
        setYHeadRot(yRot());
        xRotO = xRot();
        yRotO = yRot();
    }

    private static float wrapDegrees(float d) {
        float f = d % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    /** 블록 칸의 아랫면 가운데에서 수평 4, 수직 10 안인가(Breeze.withinInnerRing). */
    boolean withinInnerRing(double px, double py, double pz) {
        double cx = blockX() + 0.5, cy = blockY(), cz = blockZ() + 0.5;
        double dx = px - cx, dz = pz - cz;
        return dx * dx + dz * dz < 16.0 && Math.abs(py - cy) < 10.0;
    }

    @Override
    protected void customServerAiStep() {
        breezeBrain().tick(level, this);
        BreezeAi.updateActivity(this);
        super.customServerAiStep();
    }

    @Override
    public String getAmbientSound() {
        if (getTarget() != null && onGround()) return null;
        return onGround() ? "entity.breeze.idle_ground" : "entity.breeze.idle_air";
    }

    @Override protected String getHurtSound(Damage source) { return "entity.breeze.hurt"; }

    @Override protected String getDeathSound() { return "entity.breeze.death"; }

    @Override
    public boolean causeFallDamage(float distance, float multiplier) {
        if (distance > 3.0F) playSound("entity.breeze.land", 1.0F, 1.0F);
        return super.causeFallDamage(distance, multiplier);
    }

    @Override
    public boolean isInvulnerableTo(Damage source) { return super.isInvulnerableTo(source); }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (attacker instanceof Breeze) return false;
        return super.hurtBy(source, amount, attacker);
    }

    /** Breeze.getProjectileDeflection: 바람 구슬(wind_charge·breeze_wind_charge)이 아니면 소리를 내며 반대로 튕긴다. */
    @Override
    public Projectile.Deflection projectileDeflection(Projectile p) {
        String n = p.type().name();
        if (!n.equals("minecraft:breeze_wind_charge") && !n.equals("minecraft:wind_charge")) return Projectile.Deflection.BREEZE;
        return Projectile.Deflection.NONE;
    }

    @Override public int getMaxHeadXRot() { return 25; }

    @Override public int getHeadRotSpeed() { return 30; }
}
