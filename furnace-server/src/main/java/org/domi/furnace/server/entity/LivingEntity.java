// 바닐라 LivingEntity(서버 쪽 핵심): 체력·무적 시간·피해(hurt/actuallyHurt)·밀려남·죽음(20틱 뒤 제거)·낙하 피해·기본 중력 이동. 속성(attribute)은 아직 없어 최대 체력은 종류가 정한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.DamageEvent;
import org.domi.furnace.server.net.protocol.play.EntityEvent;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class LivingEntity extends Entity {

    /** 엔티티 데이터 번호(LivingEntity): 8 생활 플래그, 9 체력, 10 효과 색, 11 효과 앰비언트, 12 화살 수, 13 침 수, 14 자는 위치. */
    public static final int DATA_LIVING_FLAGS = 8, DATA_HEALTH = 9, DATA_EFFECT_COLOR = 10, DATA_EFFECT_AMBIENT = 11, DATA_ARROW_COUNT = 12,
            DATA_STINGER_COUNT = 13, DATA_SLEEPING_POS = 14;

    public int invulnerableTime, hurtTime, hurtDuration, deathTime;
    protected float lastHurt;
    protected boolean dead;
    protected int noActionTime;
    protected float yHeadRot;

    @Override public float yHeadRot() { return yHeadRot; }

    public void setYHeadRot(float v) { if (Float.isFinite(v)) yHeadRot = v; }

    protected LivingEntity(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        setHealth(maxHealth());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_LIVING_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_HEALTH, SetEntityData.SERIALIZER_FLOAT, 1.0F);
        entityData.define(DATA_EFFECT_COLOR, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_EFFECT_AMBIENT, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_ARROW_COUNT, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_STINGER_COUNT, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_SLEEPING_POS, SetEntityData.SERIALIZER_OPTIONAL_BLOCK_POS, null);
    }

    private AttributeMap attributes;

    /** 종류별 속성 공급자(바닐라 AttributeSupplier): 이 엔티티가 가지는 속성과 기본값. 하위 클래스가 덧붙인다(필드를 쓰지 말 것). */
    protected void createAttributes(AttributeMap map) {
        map.add(Attribute.MAX_HEALTH, 20.0).add(Attribute.KNOCKBACK_RESISTANCE, 0.0).add(Attribute.MOVEMENT_SPEED, 0.7)
                .add(Attribute.ARMOR, 0.0).add(Attribute.ARMOR_TOUGHNESS, 0.0).add(Attribute.ATTACK_KNOCKBACK, 0.0).add(Attribute.FOLLOW_RANGE, 32.0)
                .add(Attribute.GRAVITY, 0.08).add(Attribute.SCALE, 1.0).add(Attribute.STEP_HEIGHT, 0.6).add(Attribute.JUMP_STRENGTH, 0.42)
                .add(Attribute.SAFE_FALL_DISTANCE, 3.0).add(Attribute.FALL_DAMAGE_MULTIPLIER, 1.0).add(Attribute.MAX_ABSORPTION, 0.0)
                .add(Attribute.MOVEMENT_EFFICIENCY, 0.0).add(Attribute.WATER_MOVEMENT_EFFICIENCY, 0.0).add(Attribute.OXYGEN_BONUS, 0.0)
                .add(Attribute.BURNING_TIME, 1.0).add(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 0.0);
    }

    public AttributeMap attributes() {
        if (attributes == null) {
            attributes = new AttributeMap();
            createAttributes(attributes);
        }
        return attributes;
    }

    public double attributeValue(Attribute a) { return attributes().value(a); }

    public float maxHealth() { return (float) attributeValue(Attribute.MAX_HEALTH); }

    public float health() { return (Float) entityData.get(DATA_HEALTH); }

    public void setHealth(float h) { entityData.set(DATA_HEALTH, Math.clamp(h, 0.0F, maxHealth())); }

    public boolean isDeadOrDying() { return health() <= 0.0F; }

    @Override
    public boolean isAlive() { return !isRemoved() && health() > 0.0F; }

    public void heal(float amount) {
        float h = health();
        if (h > 0.0F) setHealth(h + amount);
    }

    // ------------------------------------------------------------------ 피해

    /** 때린 쪽이 플레이어일 때(밀려나는 방향·damage_event 의 원인). */
    public boolean hurtBy(Damage source, float amount, Player attacker) {
        if (isInvulnerableTo(source) || level == null) return false;
        if (isDeadOrDying()) return false;
        noActionTime = 0;
        if (amount < 0.0F) amount = 0.0F;
        boolean fresh = true;
        if (invulnerableTime > 10.0F && !source.bypassesInvulnerability()) {
            if (amount <= lastHurt) return false;
            actuallyHurt(source, amount - lastHurt);
            lastHurt = amount;
            fresh = false;
        } else {
            lastHurt = amount;
            invulnerableTime = 20;
            actuallyHurt(source, amount);
            hurtDuration = 10;
            hurtTime = hurtDuration;
        }
        if (fresh) {
            DamageType t = DamageType.byKey(source.type());
            int cause = attacker == null ? -1 : attacker.entityId();
            if (tracker != null) tracker.broadcast(new DamageEvent(id(), t.registryId(), cause, cause, null));
            if (!t.noImpact()) markHurt();
            if (attacker != null) {
                double dx = attacker.x() - x(), dz = attacker.z() - z();
                while (dx * dx + dz * dz < 1.0E-4) {
                    dx = (random.nextDouble() - random.nextDouble()) * 0.01;
                    dz = (random.nextDouble() - random.nextDouble()) * 0.01;
                }
                knockback(0.4F, dx, dz);
            }
        }
        if (isDeadOrDying()) die(source);
        return true;
    }

    @Override
    public boolean hurt(Damage source, float amount) { return hurtBy(source, amount, null); }

    protected void actuallyHurt(Damage source, float amount) {
        float h = health();
        setHealth(h - amount);
    }

    /** LivingEntity.knockback(strength, x, z): 방향(x,z)의 반대로 밀린다(저항 속성은 아직 없다). */
    public void knockback(double strength, double dx, double dz) {
        strength *= 1.0 - attributeValue(Attribute.KNOCKBACK_RESISTANCE);
        if (strength <= 0.0) return;
        hasImpulse = true;
        double len = Math.sqrt(dx * dx + dz * dz);
        double nx = len < 1.0E-5 ? 0.0 : dx / len * strength, nz = len < 1.0E-5 ? 0.0 : dz / len * strength;
        setDeltaMovement(vx() / 2.0 - nx, onGround() ? Math.min(0.4, vy() / 2.0 + strength) : vy(), vz() / 2.0 - nz);
    }

    public void die(Damage source) {
        if (isRemoved() || dead) return;
        dead = true;
        if (tracker != null) tracker.broadcast(new EntityEvent(id(), (byte) 3));
        // TODO(몹 드롭): 전리품 표·경험치 구슬·장비 드롭
    }

    protected void tickDeath() {
        deathTime++;
        if (deathTime >= 20 && !isRemoved()) remove(RemovalReason.KILLED);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier) {
        int dmg = (int) Math.ceil((distance - attributeValue(Attribute.SAFE_FALL_DISTANCE)) * multiplier * attributeValue(Attribute.FALL_DAMAGE_MULTIPLIER));
        if (dmg > 0) {
            hurt(new Damage("fall", false, false, false), dmg);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 틱

    @Override
    public void baseTick() {
        super.baseTick();
        if (isDeadOrDying() && !isRemoved()) tickDeath();
        if (invulnerableTime > 0) invulnerableTime--;
        if (hurtTime > 0) hurtTime--;
        if (!isRemoved() && !isDeadOrDying()) noActionTime++;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return;
        aiStep();
        travel();
    }

    /** 몹 쪽 갱신(AI 등) — 기본은 없음. */
    protected void aiStep() {}

    /** 바닐라 LivingEntity.travel 의 땅·공중 부분: 이동 뒤 중력 0.08·공기 저항 0.98·땅 마찰 0.91. 물·용암·사다리는 아직 없다. */
    protected void travel() {
        float friction = onGround() ? frictionBelow() * 0.91F : 0.91F;
        move(MOVER_SELF, vx(), vy(), vz());
        double y = vy();
        if (!isNoGravity()) y -= attributeValue(Attribute.GRAVITY);
        setDeltaMovement(vx() * friction, y * 0.98F, vz() * friction);
    }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putFloat("Health", health());
        tag.putShort("HurtTime", (short) hurtTime);
        tag.putInt("HurtByTimestamp", 0);
        tag.putShort("DeathTime", (short) deathTime);
        tag.putFloat("AbsorptionAmount", 0.0F);
        tag.putBoolean("FallFlying", false);
        tag.putList("attributes", attributes().save());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        if (tag.contains("Health")) setHealth(tag.getFloat("Health"));
        hurtTime = tag.getShort("HurtTime");
        yHeadRot = yRot();
        deathTime = tag.getShort("DeathTime");
        attributes().load(tag.tags().get("attributes") instanceof org.domi.furnace.server.net.io.nbt.NbtList l ? l : null);
        if (tag.contains("Health")) setHealth(tag.getFloat("Health"));
    }
}
