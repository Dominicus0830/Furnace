// 바닐라 LivingEntity(서버 쪽 핵심): 체력·무적 시간·피해(hurt/actuallyHurt)·밀려남·죽음(20틱 뒤 제거)·낙하 피해·기본 중력 이동. 속성(attribute)은 아직 없어 최대 체력은 종류가 정한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.DamageEvent;
import org.domi.furnace.server.net.protocol.play.EntityEvent;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class LivingEntity extends Entity implements Targetable {

    /** 엔티티 데이터 번호(LivingEntity): 8 생활 플래그, 9 체력, 10 효과 색, 11 효과 앰비언트, 12 화살 수, 13 침 수, 14 자는 위치. */
    public static final int DATA_LIVING_FLAGS = 8, DATA_HEALTH = 9, DATA_EFFECT_COLOR = 10, DATA_EFFECT_AMBIENT = 11, DATA_ARROW_COUNT = 12,
            DATA_STINGER_COUNT = 13, DATA_SLEEPING_POS = 14;

    public int invulnerableTime, hurtTime, hurtDuration, deathTime;
    protected float lastHurt;
    protected boolean dead;
    /** 마지막으로 플레이어에게 맞은 뒤 남은 틱(맞으면 100)과 그 플레이어 — 경험치·전리품을 줄지 정한다. */
    protected int lastHurtByPlayerTime;
    protected Player lastHurtByPlayer;
    protected long deathLootTableSeed;
    protected String lastDamageType = "generic";
    private long lastDamageStamp = Long.MIN_VALUE / 2;

    /** getLastDamageSource(): 최근 40틱 안에 입은 피해의 종류(없으면 null). */
    public String getLastDamageType() {
        return level.gameTime() - lastDamageStamp > 40L ? null : lastDamageType;
    }
    protected int noActionTime;

    public int getNoActionTime() { return noActionTime; }

    public void setNoActionTime(int t) { noActionTime = t; }
    protected float yHeadRot, yHeadRotO, yBodyRot, yBodyRotO;
    /** 이동 입력(vanilla xxa 옆·yya 위·zza 앞)과 점프 입력. */
    protected float xxa, yya, zza;
    protected boolean jumping;
    protected int noJumpDelay;
    /** 이동 속도 값(vanilla speed — 땅에서 입력에 곱한다). */
    protected float speed;

    @Override public float yHeadRot() { return yHeadRot; }

    public void setYHeadRot(float v) { if (Float.isFinite(v)) yHeadRot = v; }

    public float yBodyRot() { return yBodyRot; }

    public void setYBodyRot(float v) { if (Float.isFinite(v)) yBodyRot = v; }

    public void setJumping(boolean j) { jumping = j; }

    public void setXxa(float v) { xxa = v; }

    public void setYya(float v) { yya = v; }

    public void setZza(float v) { zza = v; }

    public float getSpeed() { return speed; }

    public void setSpeed(float v) { speed = v; }

    public float getStepHeight() { return (float) attributeValue(Attribute.STEP_HEIGHT); }

    /** 서버가 AI 를 돌리는 쪽인가(isEffectiveAi — 서버에서는 항상). */
    protected boolean isEffectiveAi() { return true; }

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
    protected void createAttributes(AttributeMap map) {   // LivingEntity.createLivingAttributes
        map.add(Attribute.MAX_HEALTH, 20.0).add(Attribute.KNOCKBACK_RESISTANCE, 0.0).add(Attribute.MOVEMENT_SPEED, 0.7)
                .add(Attribute.ARMOR, 0.0).add(Attribute.ARMOR_TOUGHNESS, 0.0).add(Attribute.MAX_ABSORPTION, 0.0)
                .add(Attribute.STEP_HEIGHT, 0.6).add(Attribute.SCALE, 1.0).add(Attribute.GRAVITY, 0.08).add(Attribute.SAFE_FALL_DISTANCE, 3.0)
                .add(Attribute.FALL_DAMAGE_MULTIPLIER, 1.0).add(Attribute.JUMP_STRENGTH, 0.42).add(Attribute.OXYGEN_BONUS, 0.0)
                .add(Attribute.BURNING_TIME, 1.0).add(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 0.0)
                .add(Attribute.WATER_MOVEMENT_EFFICIENCY, 0.0).add(Attribute.MOVEMENT_EFFICIENCY, 0.0);
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

    /** 때린 쪽이 있을 때(밀려나는 방향·damage_event 의 원인·마지막으로 맞은 기록). */
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
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
            int cause = attacker == null ? -1 : attacker.id();
            if (tracker != null) tracker.broadcast(new DamageEvent(id(), t.registryId(), cause, cause, null));
            if (!t.noImpact()) markHurt();
        }
        if (attacker != null) {   // 맞은 뒤 기록: 마지막으로 때린 몹, 플레이어면 100틱 동안 경험치·전리품 자격
            setLastHurtByMob(attacker);
            if (attacker instanceof Player pl) {
                lastHurtByPlayerTime = 100;
                lastHurtByPlayer = pl;
            }
        }
        if (fresh && attacker != null && !DamageType.byKey(source.type()).noKnockback()) {
            double dx = attacker.x() - x(), dz = attacker.z() - z();
            while (dx * dx + dz * dz < 1.0E-4) {
                dx = (random.nextDouble() - random.nextDouble()) * 0.01;
                dz = (random.nextDouble() - random.nextDouble()) * 0.01;
            }
            knockback(0.4F, dx, dz);
        }
        lastDamageType = source.type();
        lastDamageStamp = level.gameTime();
        if (isDeadOrDying()) {
            if (fresh) makeSound(getDeathSound());
            die(source);
        } else if (fresh) {
            playHurtSound(source);
        }
        return true;
    }

    // ------------------------------------------------------------------ 소리

    protected String getHurtSound(Damage source) { return null; }

    protected String getDeathSound() { return null; }

    protected String getAmbientSound() { return null; }

    protected void playHurtSound(Damage source) { makeSound(getHurtSound(source)); }

    protected float getSoundVolume() { return 1.0F; }

    /** getVoicePitch: 아기는 높게(약 1.5), 평소 약 1.0 — 엔티티 난수 두 번을 쓴다. */
    public float getVoicePitch() {
        return isBaby() ? (random.nextFloat() - random.nextFloat()) * 0.2F + 1.5F : (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
    }

    public boolean isBaby() { return false; }

    public void makeSound(String sound) {
        if (sound != null) playSound(sound, getSoundVolume(), getVoicePitch());
    }

    @Override
    public boolean hurt(Damage source, float amount) { return hurtBy(source, amount, null); }

    // ------------------------------------------------------------------ Targetable

    @Override public int id() { return super.id(); }

    @Override public boolean canBeSeenAsEnemy() { return !isInvulnerable() && isAlive(); }

    /** getVisibilityPercent: 웅크림 0.8. TODO(투명·머리 아이템). */
    @Override public double visibilityPercent(Entity looker) { return 1.0; }

    @Override public boolean isPlayer() { return false; }

    @Override
    public boolean hurtByMob(Damage source, float amount, Mob attacker) { return hurtBy(source, amount, attacker); }

    @Override
    public org.domi.furnace.api.text.Text displayName() {
        return org.domi.furnace.api.text.Text.translatable("entity.minecraft." + type.name().substring("minecraft:".length()));
    }

    /** 마지막으로 이 엔티티를 때린 몹(100틱 지나거나 죽으면 비운다)과 맞은 때의 tickCount. */
    protected Targetable lastHurtByMob;
    protected int lastHurtByMobTimestamp;
    /** 마지막으로 이 엔티티가 때린 대상(죽으면 비운다). */
    protected Targetable lastHurtMob;

    public Targetable getLastHurtByMob() { return lastHurtByMob; }

    public int getLastHurtByMobTimestamp() { return lastHurtByMobTimestamp; }

    public void setLastHurtByMob(Targetable e) {
        lastHurtByMob = e;
        lastHurtByMobTimestamp = tickCount;
    }

    public void setLastHurtMob(Targetable t) { lastHurtMob = t; }

    protected void actuallyHurt(Damage source, float amount) {
        float h = health();
        setHealth(h - amount);
    }

    /** LivingEntity.knockback(strength, x, z): 방향(x,z)의 반대로 밀린다(저항 속성은 아직 없다). */
    @Override
    public void knockback(double strength, double dx, double dz) {
        strength *= 1.0 - attributeValue(Attribute.KNOCKBACK_RESISTANCE);
        if (strength <= 0.0) return;
        hasImpulse = true;
        double len = Math.sqrt(dx * dx + dz * dz);
        double nx = len < 1.0E-5 ? 0.0 : dx / len * strength, nz = len < 1.0E-5 ? 0.0 : dz / len * strength;
        setDeltaMovement(vx() / 2.0 - nx, onGround() ? Math.min(0.4, vy() / 2.0 + strength) : vy(), vz() / 2.0 - nz);
    }

    /** LivingEntity.die: 전리품·경험치를 떨군 뒤 죽음 이벤트(3)를 알린다. */
    public void die(Damage source) {
        if (isRemoved() || dead) return;
        dead = true;
        dropAllDeathLoot(source);
        if (tracker != null) tracker.broadcast(new EntityEvent(id(), (byte) 3));
    }

    protected void dropAllDeathLoot(Damage source) {
        boolean byPlayer = lastHurtByPlayerTime > 0;
        if (shouldDropLoot() && level.gameRule("doMobLoot")) {
            dropFromLootTable(source, byPlayer);
            dropCustomDeathLoot(source, byPlayer);
        }
        dropEquipment();
        dropExperience();
    }

    protected boolean shouldDropLoot() { return true; }

    /** 종류의 기본 표: minecraft:entities/<이름>. */
    public String lootTable() { return "minecraft:entities/" + type.name().substring("minecraft:".length()); }

    protected void dropFromLootTable(Damage source, boolean byPlayer) {
        Player killer = byPlayer ? lastHurtByPlayer : null;
        for (ItemStack s : level.entityDrops(lootTable(), lootEntity(), x(), y(), z(), source.type(), killer, killer, deathLootTableSeed)) spawnAtLocation(s);
    }

    protected void dropCustomDeathLoot(Damage source, boolean byPlayer) {}

    /** TODO(장비 드롭): 손·방어구 칸의 드롭 확률. */
    protected void dropEquipment() {}

    /** 경험치: 마지막으로 플레이어에게 맞았고 mob loot 규칙이 켜져 있으면 구슬로 낸다. */
    protected void dropExperience() {
        if (!wasExperienceConsumed() && (isAlwaysExperienceDropper() || lastHurtByPlayerTime > 0 && shouldDropExperience() && level.gameRule("doMobLoot"))) {
            ExperienceOrb.award(level, x(), y(), z(), getExperienceReward());
        }
    }

    protected boolean wasExperienceConsumed() { return false; }

    protected boolean isAlwaysExperienceDropper() { return false; }

    protected boolean shouldDropExperience() { return true; }

    /** getExperienceReward: 종류별 기본 경험치. */
    protected int getExperienceReward() { return 0; }

    /** spawnAtLocation(stack): 몸 위치에 아이템 엔티티(줍기 지연 10). */
    public ItemEntity spawnAtLocation(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var ie = new ItemEntity(level, level.nextEntityId(), x(), y(), z(), stack);
        ie.setDefaultPickUpDelay();
        level.addFreshEntity(ie);
        return ie;
    }

    /** 전리품 조건이 보는 이 엔티티. */
    public org.domi.furnace.server.loot.LootEntity lootEntity() {
        return new org.domi.furnace.server.loot.LootEntity() {
            public String type() { return LivingEntity.this.type.name(); }
            public boolean isOnFire() { return LivingEntity.this.isOnFire(); }
            public boolean onGround() { return LivingEntity.this.onGround(); }
            public double[] position() { return new double[]{x(), y(), z()}; }
        };
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
        if (lastHurtByPlayerTime > 0) lastHurtByPlayerTime--;
        else lastHurtByPlayer = null;
        if (lastHurtMob != null && !lastHurtMob.isAlive()) lastHurtMob = null;
        if (lastHurtByMob != null) {
            if (!lastHurtByMob.isAlive()) setLastHurtByMob(null);
            else if (tickCount - lastHurtByMobTimestamp > 100) setLastHurtByMob(null);
        }
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return;
        updateSwingTime();
        detectEquipmentUpdates();
        aiStep();
        double dx = x() - xo, dz = z() - zo;
        float f = (float) (dx * dx + dz * dz);
        float body = yBodyRot;
        if (f > 0.0025000002F) {
            float dir = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
            float diff = Math.abs(Mth.wrapDegrees(yRot()) - dir);
            body = 95.0F < diff && diff < 265.0F ? dir - 180.0F : dir;
        }
        tickHeadTurn(body);
        while (yRot() - yRotO < -180.0F) yRotO -= 360.0F;
        while (yRot() - yRotO >= 180.0F) yRotO += 360.0F;
        while (yBodyRot - yBodyRotO < -180.0F) yBodyRotO -= 360.0F;
        while (yBodyRot - yBodyRotO >= 180.0F) yBodyRotO += 360.0F;
        while (xRot() - xRotO < -180.0F) xRotO -= 360.0F;
        while (xRot() - xRotO >= 180.0F) xRotO += 360.0F;
        while (yHeadRot - yHeadRotO < -180.0F) yHeadRotO -= 360.0F;
        while (yHeadRot - yHeadRotO >= 180.0F) yHeadRotO += 360.0F;
    }

    /** tickHeadTurn: 몸통 방향이 시선에서 최대 50도(Mob 은 maxBodyYRotDiff) 넘게 벌어지지 않게 끌어온다. */
    protected void tickHeadTurn(float targetBody) {
        float d = Mth.wrapDegrees(targetBody - yBodyRot);
        yBodyRot += d * 0.3F;
        float d2 = Mth.wrapDegrees(yRot() - yBodyRot);
        float max = 50.0F;
        if (Math.abs(d2) > max) yBodyRot += d2 - Math.signum(d2) * max;
    }

    /** LivingEntity.aiStep(vanilla m_) 의 서버 쪽: 작은 속도 지우기 → AI → 점프 → 이동. */
    protected void aiStep() {
        if (noJumpDelay > 0) noJumpDelay--;
        double vx = vx(), vy = vy(), vz = vz();
        if (Math.abs(vx) < 0.003) vx = 0.0;
        if (Math.abs(vy) < 0.003) vy = 0.0;
        if (Math.abs(vz) < 0.003) vz = 0.0;
        setDeltaMovement(vx, vy, vz);
        if (isImmobile()) {
            jumping = false;
            xxa = 0.0F;
            zza = 0.0F;
        } else if (isEffectiveAi()) {
            serverAiStep();
        }
        if (jumping && isAffectedByFluids()) {
            // TODO(물·용암 점프): 유체 높이에 따라 위로 떠오르기(jumpInLiquid)
            if (onGround() && noJumpDelay == 0) {
                jumpFromGround();
                noJumpDelay = 10;
            }
        } else {
            noJumpDelay = 0;
        }
        xxa *= 0.98F;
        zza *= 0.98F;
        travel(xxa, yya, zza);
    }

    // ------------------------------------------------------------------ 장비

    private final java.util.EnumMap<EquipmentSlot, ItemStack> lastEquipmentItems = new java.util.EnumMap<>(EquipmentSlot.class);

    /** getItemBySlot: 종류별 장비 칸(기본: 없음). */
    public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }

    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

    public ItemStack getMainHandItem() { return getItemBySlot(EquipmentSlot.MAINHAND); }

    private static boolean sameStack(ItemStack a, ItemStack b) { return a.itemId() == b.itemId() && a.count() == b.count() && a.patch().equals(b.patch()) || a.isEmpty() && b.isEmpty(); }

    /**
     * detectEquipmentUpdates → collectEquipmentChanges: 바뀐 칸의 속성 수정자(방어·공격력 …)를 갈아 끼우고 장비 패킷을 보낸다.
     * 수정자 id 가 같은 것은 먼저 지우고 다시 단다(transient).
     */
    protected void detectEquipmentUpdates() {
        java.util.List<org.domi.furnace.server.net.protocol.play.SetEquipment.Entry> changes = null;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack cur = getItemBySlot(slot);
            ItemStack last = lastEquipmentItems.getOrDefault(slot, ItemStack.EMPTY);
            if (sameStack(cur, last)) continue;
            if (changes == null) changes = new java.util.ArrayList<>();
            changes.add(new org.domi.furnace.server.net.protocol.play.SetEquipment.Entry(slot, cur));
            lastEquipmentItems.put(slot, cur);
            for (var e : org.domi.furnace.server.item.ItemTools.attributeEntries(last)) {
                if (!org.domi.furnace.server.item.ItemTools.slotGroupMatches(e.slot(), slot.slotName())) continue;
                Attribute a = Attribute.byName(e.type());
                var inst = a == null ? null : attributes().get(a);
                if (inst != null) inst.removeModifier(e.id());
            }
            for (var e : org.domi.furnace.server.item.ItemTools.attributeEntries(cur)) {
                if (!org.domi.furnace.server.item.ItemTools.slotGroupMatches(e.slot(), slot.slotName())) continue;
                Attribute a = Attribute.byName(e.type());
                var inst = a == null ? null : attributes().get(a);
                if (inst != null) {
                    inst.removeModifier(e.id());
                    inst.addOrReplaceModifier(new org.domi.furnace.server.attribute.AttributeModifier(e.id(), e.amount(),
                            org.domi.furnace.server.attribute.AttributeModifier.operationOf(e.operation())));
                }
            }
        }
        if (changes != null && tracker != null) tracker.broadcast(new org.domi.furnace.server.net.protocol.play.SetEquipment(id(), changes));
    }

    // ------------------------------------------------------------------ 휘두르기

    protected boolean swinging;
    protected int swingTime;
    protected int swingingArm;
    public float attackAnim;

    /** getCurrentSwingDuration: 기본 6틱. TODO(성급함·채굴 피로 효과). */
    protected int getCurrentSwingDuration() { return 6; }

    public void swing(int hand) {
        if (!swinging || swingTime >= getCurrentSwingDuration() / 2 || swingTime < 0) {
            swingTime = -1;
            swinging = true;
            swingingArm = hand;
            if (tracker != null) tracker.broadcast(new org.domi.furnace.server.net.protocol.play.Animate(id(), hand == 0 ? 0 : 3));
        }
    }

    protected void updateSwingTime() {
        int duration = getCurrentSwingDuration();
        if (swinging) {
            swingTime++;
            if (swingTime >= duration) {
                swingTime = 0;
                swinging = false;
            }
        } else {
            swingTime = 0;
        }
        attackAnim = (float) swingTime / duration;
    }

    /** 새 AI 한 걸음(Mob 이 목표·제어를 돌린다). */
    protected void serverAiStep() {}

    @Override
    public void moveTo(double nx, double ny, double nz, float yr, float xr) {
        super.moveTo(nx, ny, nz, yr, xr);
        yBodyRot = yHeadRot = yRot();
        yBodyRotO = yHeadRotO = yRot();
    }

    protected boolean isImmobile() { return isDeadOrDying(); }

    protected boolean isAffectedByFluids() { return true; }

    /** getBlockJumpFactor: 칸 → 1.0 이면 아래 칸의 점프 계수. */
    protected float getBlockJumpFactor() {
        float f = org.domi.furnace.server.block.BlockInfo.get().jumpFactor(org.domi.furnace.server.block.B.block(level.getBlockState(blockPosition())));
        return (double) f == 1.0 ? org.domi.furnace.server.block.BlockInfo.get().jumpFactor(org.domi.furnace.server.block.B.block(level.getBlockState(getBlockPosBelowThatAffectsMyMovement()))) : f;
    }

    protected float getJumpPower() { return getJumpPower(1.0F); }

    protected float getJumpPower(float multiplier) { return (float) attributeValue(Attribute.JUMP_STRENGTH) * multiplier * getBlockJumpFactor() + jumpBoostPower(); }

    public boolean isJumping() { return jumping; }

    protected float jumpBoostPower() { return 0.0F; }

    public void jumpFromGround() {
        float power = getJumpPower();
        if (power <= 1.0E-5F) return;
        setDeltaMovement(vx(), power, vz());
        hasImpulse = true;
    }

    /**
     * LivingEntity.travel(input): 땅·공중 가지. 땅 마찰은 디딘 블록, 가속은 {땅: speed × 0.21600002 / 마찰³, 공중: 0.02},
     * 이동 뒤 y 속도에 중력을 빼고 0.98, 수평은 마찰×0.91. TODO(물·용암·사다리·겉날개): 유체 이동, 오르기.
     */
    protected void travel(float inX, float inY, float inZ) {
        double gravity = attributeValue(Attribute.GRAVITY);
        boolean falling = vy() <= 0.0;
        float blockFriction = onGround() ? org.domi.furnace.server.block.BlockInfo.get().friction(
                org.domi.furnace.server.block.B.block(level.getBlockState(getBlockPosBelowThatAffectsMyMovement()))) : 0.0F;
        float friction = onGround() ? blockFriction * 0.91F : 0.91F;
        // handleRelativeFrictionAndCalculateMovement
        float accel = onGround() ? speed * (0.21600002F / (blockFriction * blockFriction * blockFriction)) : 0.02F;
        moveRelative(accel, inX, inY, inZ);
        move(MOVER_SELF, vx(), vy(), vz());
        double y = vy();
        if (!isNoGravity()) y -= gravity;
        setDeltaMovement(vx() * friction, y * 0.98F, vz() * friction);
    }

    /** Entity.moveRelative(speed, input): 입력을 바라보는 방향으로 돌려 속도에 더한다(길이가 1을 넘으면 정규화). */
    protected void moveRelative(float speed, float ix, float iy, float iz) {
        double len = ix * ix + iy * iy + iz * iz;
        if (len < 1.0E-7) return;
        double scale = len > 1.0 ? 1.0 / Math.sqrt(len) : 1.0;
        double ex = ix * scale * speed, ey = iy * scale * speed, ez = iz * scale * speed;
        float sin = Mth.sin(yRot() * (float) (Math.PI / 180.0)), cos = Mth.cos(yRot() * (float) (Math.PI / 180.0));
        addDeltaMovement(ex * cos - ez * sin, ey, ez * cos + ex * sin);
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
        yHeadRot = yBodyRot = yRot();
        yHeadRotO = yBodyRotO = yRot();
        deathTime = tag.getShort("DeathTime");
        attributes().load(tag.tags().get("attributes") instanceof org.domi.furnace.server.net.io.nbt.NbtList l ? l : null);
        if (tag.contains("Health")) setHealth(tag.getFloat("Health"));
    }
}
