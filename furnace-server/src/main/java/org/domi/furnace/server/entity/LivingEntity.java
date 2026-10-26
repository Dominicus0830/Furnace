// 바닐라 LivingEntity(서버 쪽 핵심): 체력·무적 시간·피해(hurt/actuallyHurt)·밀려남·죽음(20틱 뒤 제거)·낙하 피해·기본 중력 이동. 속성(attribute)은 아직 없어 최대 체력은 종류가 정한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.block.B;
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
    private Targetable lastDamageAttacker;

    /** getLastDamageSource().getEntity(): 최근 40틱 안에 입은 피해를 일으킨 쪽(없으면 null). */
    public Targetable getLastDamageAttacker() {
        return level.gameTime() - lastDamageStamp > 40L ? null : lastDamageAttacker;
    }

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

    public float getXxa() { return xxa; }

    public float getYya() { return yya; }

    public float getZza() { return zza; }

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

    /** 직접 때린 엔티티(투사체)가 따로 있을 때: 밀려나는 방향과 damage_event 의 직접 원인이 그 엔티티다. */
    private Entity pendingDirect;

    /** 지금 처리 중인 피해의 직접 원인 엔티티(투사체 등, 없으면 null). */
    protected Entity directCause() { return pendingDirect; }

    public boolean hurtByVia(Damage source, float amount, Targetable attacker, Entity direct) {
        pendingDirect = direct;
        try {
            return hurtBy(source, amount, attacker);
        } finally {
            pendingDirect = null;
        }
    }

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
            int directId = pendingDirect != null ? pendingDirect.id() : cause;
            if (tracker != null) tracker.broadcast(new DamageEvent(id(), t.registryId(), cause, directId, null));
            if (!t.noImpact()) markHurt();
        }
        if (attacker != null) {   // 맞은 뒤 기록: 마지막으로 때린 몹, 플레이어면 100틱 동안 경험치·전리품 자격
            setLastHurtByMob(attacker);
            if (attacker instanceof Player pl) {
                lastHurtByPlayerTime = 100;
                lastHurtByPlayer = pl;
            }
        }
        if (fresh && (attacker != null || pendingDirect != null) && !DamageType.byKey(source.type()).noKnockback()) {
            double dx = (pendingDirect != null ? pendingDirect.x() : attacker.x()) - x(), dz = (pendingDirect != null ? pendingDirect.z() : attacker.z()) - z();
            while (dx * dx + dz * dz < 1.0E-4) {
                dx = (random.nextDouble() - random.nextDouble()) * 0.01;
                dz = (random.nextDouble() - random.nextDouble()) * 0.01;
            }
            knockback(0.4F, dx, dz);
        }
        lastDamageType = source.type();
        lastDamageAttacker = attacker;
        lastDamageStamp = level.gameTime();
        if (isDeadOrDying()) {
            if (fresh) makeSound(getDeathSound());
            die(source);
        } else if (fresh) {
            playHurtSound(source);
        }
        return true;
    }

    // ------------------------------------------------------------------ 아이템 사용(활 당기기 등)

    private int useItemRemaining;
    private int usingHandIndex;

    public boolean isUsingItem() { return ((Byte) entityData.get(DATA_LIVING_FLAGS) & 1) != 0; }

    public int getUsedItemHand() { return ((Byte) entityData.get(DATA_LIVING_FLAGS) & 2) != 0 ? 1 : 0; }

    private void setLivingEntityFlag(int flag, boolean on) {
        byte b = (Byte) entityData.get(DATA_LIVING_FLAGS);
        entityData.set(DATA_LIVING_FLAGS, (byte) (on ? b | flag : b & ~flag));
    }

    /** LivingEntity.startUsingItem(hand): 쓰는 아이템 지속시간을 72000 으로 둔다(활). */
    public void startUsingItem(int hand) {
        ItemStack s = getItemBySlot(hand == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        if (s.isEmpty() || isUsingItem()) return;
        usingHandIndex = hand;
        useItemRemaining = 72000;
        setLivingEntityFlag(1, true);
        setLivingEntityFlag(2, hand == 1);
    }

    public void stopUsingItem() {
        setLivingEntityFlag(1, false);
        useItemRemaining = 0;
    }

    /** LivingEntity.getTicksUsingItem: 72000 - 남은 틱. */
    public int getTicksUsingItem() { return isUsingItem() ? 72000 - useItemRemaining : 0; }

    private void updateUsingItem() {
        if (isUsingItem()) {
            ItemStack s = getItemBySlot(getUsedItemHand() == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            if (s.isEmpty()) stopUsingItem();
            else if (useItemRemaining > 0) useItemRemaining--;
        }
    }

    // ------------------------------------------------------------------ 상태 효과(LivingEntity.activeEffects)

    private final java.util.Map<org.domi.furnace.server.effect.MobEffect, org.domi.furnace.server.effect.MobEffectInstance> activeEffects = new java.util.LinkedHashMap<>();

    private final org.domi.furnace.server.effect.MobEffect.Host effectHost = new org.domi.furnace.server.effect.MobEffect.Host() {
        public float health() { return LivingEntity.this.health(); }

        public float maxHealth() { return LivingEntity.this.maxHealth(); }

        public void heal(float amount) { LivingEntity.this.heal(amount); }

        public boolean hurtBy(String key, float amount) {
            return LivingEntity.this.hurt(new Damage(key, false, false, false), amount);
        }

        public boolean isPlayer() { return false; }

        public void causeFoodExhaustion(float amount) {}

        public void eat(int nutrition, float saturationModifier) {}

        public float absorption() { return 0.0F; }

        public void setAbsorption(float amount) {}

        public boolean invertedHealAndHarm() { return type.is("minecraft:inverted_healing_and_harm"); }
    };

    public org.domi.furnace.server.effect.MobEffect.Host effectHost() { return effectHost; }

    public java.util.Collection<org.domi.furnace.server.effect.MobEffectInstance> getActiveEffects() { return activeEffects.values(); }

    public boolean hasEffect(org.domi.furnace.server.effect.MobEffect e) { return activeEffects.containsKey(e); }

    public org.domi.furnace.server.effect.MobEffectInstance getEffect(org.domi.furnace.server.effect.MobEffect e) { return activeEffects.get(e); }

    /** LivingEntity.canBeAffected: 되살아나지 않는 몹(언데드)은 재생·독에 안 걸린다. */
    public boolean canBeAffected(org.domi.furnace.server.effect.MobEffectInstance i) {
        if (type.is("minecraft:ignores_poison_and_regen")) {
            return i.effect() != org.domi.furnace.server.effect.MobEffect.REGENERATION && i.effect() != org.domi.furnace.server.effect.MobEffect.POISON;
        }
        return true;
    }

    public boolean addEffect(org.domi.furnace.server.effect.MobEffectInstance incoming) {
        if (!canBeAffected(incoming)) return false;
        var existing = activeEffects.get(incoming.effect());
        boolean changed;
        if (existing == null) {
            activeEffects.put(incoming.effect(), incoming);
            if (tracker != null) tracker.broadcast(org.domi.furnace.server.net.protocol.play.UpdateMobEffect.of(id(), incoming, true));
            onEffectAdded(incoming);
            changed = true;
        } else if (existing.update(incoming)) {
            if (tracker != null) tracker.broadcast(org.domi.furnace.server.net.protocol.play.UpdateMobEffect.of(id(), existing, true));
            onEffectUpdated(existing);
            changed = true;
        } else {
            changed = false;
        }
        return changed;
    }

    protected void onEffectAdded(org.domi.furnace.server.effect.MobEffectInstance i) {
        applyEffectAttributes(i);
        i.effect().onEffectStarted(effectHost, i.amplifier());
    }

    protected void onEffectUpdated(org.domi.furnace.server.effect.MobEffectInstance i) {
        applyEffectAttributes(i);
    }

    private void applyEffectAttributes(org.domi.furnace.server.effect.MobEffectInstance i) {
        for (Object[] m : i.effect().attributeModifiers()) {
            Attribute a = Attribute.byName((String) m[0]);
            if (a == null || attributes().get(a) == null) continue;
            var inst = attributes().get(a);
            inst.removeModifier((String) m[1]);
            inst.addOrReplaceModifier(new org.domi.furnace.server.attribute.AttributeModifier((String) m[1], ((Number) m[2]).doubleValue() * (i.amplifier() + 1), (Integer) m[3]));
        }
    }

    public boolean removeEffect(org.domi.furnace.server.effect.MobEffect effect) {
        var e = activeEffects.remove(effect);
        if (e == null) return false;
        onEffectRemoved(e);
        return true;
    }

    public int removeAllEffects() {
        int n = activeEffects.size();
        for (var e : new java.util.ArrayList<>(activeEffects.values())) {
            activeEffects.remove(e.effect());
            onEffectRemoved(e);
        }
        return n;
    }

    protected void onEffectRemoved(org.domi.furnace.server.effect.MobEffectInstance e) {
        for (Object[] m : e.effect().attributeModifiers()) {
            Attribute a = Attribute.byName((String) m[0]);
            if (a != null && attributes().get(a) != null) attributes().get(a).removeModifier((String) m[1]);
        }
        if (health() > maxHealth()) setHealth(maxHealth());
        if (tracker != null) tracker.broadcast(new org.domi.furnace.server.net.protocol.play.RemoveMobEffect(id(), e.effect().registryId()));
    }

    /** LivingEntity.tickEffects: 효과마다 적용·감소, 끝난 것은 지우고 600틱마다 알린다. */
    protected void tickEffects() {
        if (activeEffects.isEmpty()) return;
        for (var e : new java.util.ArrayList<>(activeEffects.values())) {
            boolean alive = e.tick(effectHost, tickCount, () -> {
                if (tracker != null) tracker.broadcast(org.domi.furnace.server.net.protocol.play.UpdateMobEffect.of(id(), e, true));
            }, () -> { });
            if (!alive) {
                activeEffects.remove(e.effect());
                onEffectRemoved(e);
            } else if (e.duration() % 600 == 0) {
                if (tracker != null) tracker.broadcast(org.domi.furnace.server.net.protocol.play.UpdateMobEffect.of(id(), e, false));
            }
        }
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

    @Override public Targetable getLastHurtByMob() { return lastHurtByMob; }

    @Override public int getLastHurtByMobTimestamp() { return lastHurtByMobTimestamp; }

    public void setLastHurtByMob(Targetable e) {
        lastHurtByMob = e;
        lastHurtByMobTimestamp = tickCount;
    }

    protected int lastHurtMobTimestamp;

    @Override public boolean isPickable() { return !isRemoved(); }

    @Override public double eyeYForExplosion() { return eyeY(); }

    public void setLastHurtByPlayer(Player p) {
        lastHurtByPlayer = p;
        lastHurtByPlayerTime = tickCount;
    }

    @Override public Targetable getLastHurtMob() { return lastHurtMob; }

    @Override public int getLastHurtMobTimestamp() { return lastHurtMobTimestamp; }

    public void setLastHurtMob(Targetable t) {
        lastHurtMob = t;
        lastHurtMobTimestamp = tickCount;
    }

    protected void actuallyHurt(Damage source, float amount) {
        amount = getDamageAfterArmorAbsorb(source, amount);
        amount = getDamageAfterMagicAbsorb(source, amount);
        if (amount != 0.0F) setHealth(health() - amount);
    }

    /** LivingEntity.getDamageAfterArmorAbsorb: CombatRules.getDamageAfterAbsorb(damage, armor, toughness). */
    protected float getDamageAfterArmorAbsorb(Damage source, float amount) {
        if (DamageType.keyHasTag(source.type(), "bypasses_armor")) return amount;
        float armor = (float) attributeValue(Attribute.ARMOR), toughness = (float) attributeValue(Attribute.ARMOR_TOUGHNESS);
        float f = 2.0F + toughness / 4.0F;
        float f1 = Math.clamp(armor - amount / f, armor * 0.2F, 20.0F);
        return amount * (1.0F - f1 / 25.0F);
    }

    /** LivingEntity.getDamageAfterMagicAbsorb: 저항 효과와 방어구 보호 인챈트. */
    protected float getDamageAfterMagicAbsorb(Damage source, float amount) {
        DamageType t = DamageType.byKey(source.type());
        if (DamageType.keyHasTag(source.type(), "bypasses_effects")) return amount;
        var res = getEffect(org.domi.furnace.server.effect.MobEffect.RESISTANCE);
        if (res != null && !DamageType.keyHasTag(source.type(), "bypasses_resistance")) {
            int i = (res.amplifier() + 1) * 5;
            int j = 25 - i;
            amount = Math.max(amount * j / 25.0F, 0.0F);
        }
        if (amount <= 0.0F) return 0.0F;
        if (DamageType.keyHasTag(source.type(), "bypasses_enchantments")) return amount;
        float prot = getDamageProtection(source);
        if (prot > 0.0F) amount = amount * (1.0F - Math.clamp(prot, 0.0F, 20.0F) / 25.0F);
        return amount;
    }

    /** EnchantmentHelper.getDamageProtection: 방어구 네 칸의 보호 인챈트 합. */
    private float getDamageProtection(Damage source) {
        DamageType t = DamageType.byKey(source.type());
        int sum = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = getItemBySlot(slot);
            if (s.isEmpty()) continue;
            sum += org.domi.furnace.server.item.ItemTools.enchantmentLevel(s, "minecraft:protection");
            if (source.fire()) sum += 2 * org.domi.furnace.server.item.ItemTools.enchantmentLevel(s, "minecraft:fire_protection");
            if (source.explosion()) sum += 2 * org.domi.furnace.server.item.ItemTools.enchantmentLevel(s, "minecraft:blast_protection");
            if (t == DamageType.ARROW || t == DamageType.MOB_PROJECTILE) sum += 2 * org.domi.furnace.server.item.ItemTools.enchantmentLevel(s, "minecraft:projectile_protection");
            if (t.isFall()) sum += 3 * org.domi.furnace.server.item.ItemTools.enchantmentLevel(s, "minecraft:feather_falling");
        }
        return sum;
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
        boolean drops = !(lastHurtByMob instanceof Mob killer) || killer.killedEntity(this);   // Entity.killedEntity: false 면 드롭 없음(좀비가 주민을 감염시킴)
        if (drops) dropAllDeathLoot(source);
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

    /** spawnAtLocation(stack, yOffset): 몸 위치 + yOffset 에 아이템 엔티티. */
    public ItemEntity spawnAtLocation(ItemStack stack, float yOffset) {
        if (stack.isEmpty()) return null;
        var ie = new ItemEntity(level, level.nextEntityId(), x(), y() + yOffset, z(), stack);
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

    protected int calculateFallDamage(float distance, float multiplier) {
        return (int) Math.ceil((distance - attributeValue(Attribute.SAFE_FALL_DISTANCE)) * multiplier * attributeValue(Attribute.FALL_DAMAGE_MULTIPLIER));
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier) {
        int dmg = calculateFallDamage(distance, multiplier);
        if (dmg > 0) {
            hurt(new Damage("fall", false, false, false), dmg);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 틱

    @Override
    public void baseTick() {
        int oldAir = getAirSupply();
        super.baseTick();
        tickEffects();
        if (isAlive()) {
            if (isInWall()) hurt(new Damage("in_wall", false, false, false), 1.0F);
            if (isEyeInWater() && !B.is(level.getBlockState(new org.domi.furnace.server.block.Pos((int) Math.floor(x()), (int) Math.floor(eyeY()), (int) Math.floor(z()))), "minecraft:bubble_column")) {
                boolean drowns = !canBreatheUnderwater() && !hasEffect(org.domi.furnace.server.effect.MobEffect.WATER_BREATHING);
                if (drowns) {
                    setAirSupply(decreaseAirSupply(getAirSupply()));
                    if (getAirSupply() == -20) {
                        setAirSupply(0);
                        hurt(new Damage("drown", false, false, false), 2.0F);
                    }
                } else if (getAirSupply() < getMaxAirSupply()) {
                    setAirSupply(increaseAirSupply(getAirSupply()));
                }
            } else if (getAirSupply() < getMaxAirSupply()) {
                setAirSupply(increaseAirSupply(getAirSupply()));
            }
        }
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
        updateUsingItem();
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
        if (isSensitiveToWater() && isInWaterRainOrBubble()) hurt(new Damage("drown", false, false, false), 1.0F);
        if (jumping && isAffectedByFluids()) {
            double d3 = isInLava() ? fluidHeight(false) : fluidHeight(true);
            boolean inWater = isInWater() && d3 > 0.0;
            double d4 = getFluidJumpThreshold();
            if (!inWater || onGround() && !(d3 > d4)) {
                if (!isInLava() || onGround() && !(d3 > d4)) {
                    if ((onGround() || inWater && d3 <= d4) && noJumpDelay == 0) {
                        jumpFromGround();
                        noJumpDelay = 10;
                    }
                } else {
                    jumpInLiquid(true);
                }
            } else {
                jumpInLiquid(false);
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

    public boolean isSwinging() { return swinging; }
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

    public boolean isImmobile() { return isDeadOrDying(); }

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
        if (falling && hasEffect(org.domi.furnace.server.effect.MobEffect.SLOW_FALLING)) gravity = Math.min(gravity, 0.01);
        if (isInWater() && isAffectedByFluids() && !(this instanceof Mob m && m.canStandOnFluid(level.getFluidState(blockPosition())))) {
            double d1 = y();
            float f = isSprinting() ? 0.9F : getWaterSlowDown();
            float f1 = 0.02F;
            float f2 = (float) attributeValue(Attribute.WATER_MOVEMENT_EFFICIENCY);
            if (!onGround()) f2 *= 0.5F;
            if (f2 > 0.0F) {
                f += (0.54600006F - f) * f2;
                f1 += (speed - f1) * f2;
            }
            if (hasEffect(org.domi.furnace.server.effect.MobEffect.DOLPHINS_GRACE)) f = 0.96F;
            moveRelative(f1, inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            double mx = vx(), my = vy(), mz = vz();
            if (horizontalCollision && onClimbable()) my = 0.2;
            mx *= f;
            my *= 0.8F;
            mz *= f;
            double[] adj = fluidFallingAdjustedMovement(gravity, falling, mx, my, mz);
            setDeltaMovement(adj[0], adj[1], adj[2]);
            if (horizontalCollision && isFree(adj[0], adj[1] + 0.6F - y() + d1, adj[2])) setDeltaMovement(adj[0], 0.3F, adj[2]);
        } else if (isInLava() && isAffectedByFluids() && !(this instanceof Mob m2 && m2.canStandOnFluid(level.getFluidState(blockPosition())))) {
            double d1 = y();
            moveRelative(0.02F, inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            if (fluidHeight(false) <= getFluidJumpThreshold()) {
                setDeltaMovement(vx() * 0.5, vy() * 0.8F, vz() * 0.5);
                double[] adj = fluidFallingAdjustedMovement(gravity, falling, vx(), vy(), vz());
                setDeltaMovement(adj[0], adj[1], adj[2]);
            } else {
                setDeltaMovement(vx() * 0.5, vy() * 0.5, vz() * 0.5);
            }
            if (!isNoGravity()) setDeltaMovement(vx(), vy() - gravity / 4.0, vz());
            if (horizontalCollision && isFree(vx(), vy() + 0.6F - y() + d1, vz())) setDeltaMovement(vx(), 0.3F, vz());
        } else {
            float blockFriction = org.domi.furnace.server.block.BlockInfo.get().friction(
                    org.domi.furnace.server.block.B.block(level.getBlockState(getBlockPosBelowThatAffectsMyMovement())));
            float friction = onGround() ? blockFriction * 0.91F : 0.91F;
            // handleRelativeFrictionAndCalculateMovement
            float accel = onGround() ? speed * (0.21600002F / (blockFriction * blockFriction * blockFriction)) : 0.02F;
            moveRelative(accel, inX, inY, inZ);
            double[] c = handleOnClimbable(vx(), vy(), vz());
            setDeltaMovement(c[0], c[1], c[2]);
            move(MOVER_SELF, vx(), vy(), vz());
            double mx = vx(), my = vy(), mz = vz();
            if ((horizontalCollision || jumping) && onClimbable()) my = 0.2;
            double y = my;
            if (hasEffect(org.domi.furnace.server.effect.MobEffect.LEVITATION)) {
                y += (0.05 * (getEffect(org.domi.furnace.server.effect.MobEffect.LEVITATION).amplifier() + 1) - my) * 0.2;
            } else if (!isNoGravity()) {
                y -= gravity;
            }
            if (discardFriction) setDeltaMovement(mx, y, mz);
            else setDeltaMovement(mx * friction, y * 0.98F, mz * friction);
        }
    }

    /** LivingEntity.getJumpBoostPower: 점프 강화 효과 0.1 × (레벨+1). */
    public float getJumpBoostPower() {
        var e = getEffect(org.domi.furnace.server.effect.MobEffect.JUMP_BOOST);
        return e == null ? 0.0F : 0.1F * (e.amplifier() + 1.0F);
    }

    private boolean discardFriction;

    public boolean shouldDiscardFriction() { return discardFriction; }

    public void setDiscardFriction(boolean v) { discardFriction = v; }

    protected float getWaterSlowDown() { return 0.8F; }

    public boolean canBreatheUnderwater() { return type.is("minecraft:can_breathe_under_water"); }

    protected int decreaseAirSupply(int air) { return air - 1; }

    protected int increaseAirSupply(int air) { return Math.min(air + 4, getMaxAirSupply()); }

    /** Entity.isInWall: 눈 높이 칸이 질식시키는 블록 안이다. */
    public boolean isInWall() {
        if (noPhysics) return false;
        float f = bbWidth() * 0.8F;
        double ex = x(), ey = eyeY(), ez = z();
        double x0 = ex - f / 2.0, x1 = ex + f / 2.0, y0 = ey - 5.0E-7, y1 = ey + 5.0E-7, z0 = ez - f / 2.0, z1 = ez + f / 2.0;
        for (int bx = (int) Math.floor(x0); bx <= (int) Math.floor(x1); bx++) {
            for (int by = (int) Math.floor(y0); by <= (int) Math.floor(y1); by++) {
                for (int bz = (int) Math.floor(z0); bz <= (int) Math.floor(z1); bz++) {
                    int st = level.getBlockState(new org.domi.furnace.server.block.Pos(bx, by, bz));
                    if (!B.isAir(st) && org.domi.furnace.server.block.BlockInfo.get().suffocating(st)
                            && org.domi.furnace.server.block.BlockInfo.get().collisionShape(st).move(bx, by, bz).intersects(x0, y0, z0, x1, y1, z1)) return true;
                }
            }
        }
        return false;
    }

    /** Entity.teleportTo(x, y, z): 방향은 그대로 위치만 옮긴다. */
    public void teleportTo(double nx, double ny, double nz) { moveTo(nx, ny, nz, yRot(), xRot()); }

    /** LivingEntity.randomTeleport(x, y, z, particleEffects): 발밑이 단단한 곳까지 내려가 몸이 들어가는지 보고 옮긴다. 실패하면 제자리. */
    public boolean randomTeleport(double nx, double ny, double nz, boolean particleEffects) {
        double ox = x(), oy = y(), oz = z();
        double d3 = ny;
        boolean ok = false;
        org.domi.furnace.server.block.Pos pos = new org.domi.furnace.server.block.Pos((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz));
        if (level.hasChunkAt(pos)) {
            boolean found = false;
            while (!found && pos.y() > level.minBuildHeight()) {
                org.domi.furnace.server.block.Pos below = pos.below();
                if (org.domi.furnace.server.block.BlockInfo.get().blocksMotion(level.getBlockState(below))) {
                    found = true;
                } else {
                    d3--;
                    pos = below;
                }
            }
            if (found) {
                teleportTo(nx, d3, nz);
                if (level.noCollision(boundingBox()) && !level.containsAnyLiquid(boundingBox())) ok = true;
            }
        }
        if (!ok) {
            teleportTo(ox, oy, oz);
            return false;
        }
        if (particleEffects) broadcastEntityEvent((byte) 46);
        if (this instanceof PathfinderMob m) m.getNavigation().stop();
        return true;
    }

    /** 물·비에 피해를 받는 몹인가(LivingEntity.isSensitiveToWater): 엔더맨. */
    public boolean isSensitiveToWater() { return false; }

    /** LivingEntity.jumpInLiquid: 유체 안에서 점프하면 위로 0.04 만큼 밀어 올린다. */
    protected void jumpInLiquid(boolean lava) { setDeltaMovement(vx(), vy() + 0.04F, vz()); }

    public double getFluidJumpThreshold() { return dimEye < 0.4 ? 0.0 : 0.4; }

    // ---- 사다리 오르기(onClimbable)
    private org.domi.furnace.server.block.Pos lastClimbablePos;

    /** LivingEntity.onClimbable: 관전자가 아니고 사다리·덩굴 같은 오를 수 있는 블록 안이다. */
    public boolean onClimbable() {
        org.domi.furnace.server.block.Pos pos = blockPosition();
        int st = level.getBlockState(pos);
        if (org.domi.furnace.server.block.B.isTag(st, "minecraft:climbable")) {
            lastClimbablePos = pos;
            return true;
        }
        return false;
    }

    protected boolean isSuppressingSlidingDownLadder() { return isShiftKeyDown(); }

    public boolean isShiftKeyDown() { return false; }

    private double[] handleOnClimbable(double dx, double dy, double dz) {
        if (onClimbable()) {
            resetFallDistance();
            dx = Math.clamp(dx, -0.15, 0.15);
            dz = Math.clamp(dz, -0.15, 0.15);
            dy = Math.max(dy, -0.15);
        }
        return new double[] {dx, dy, dz};
    }

    private double[] fluidFallingAdjustedMovement(double gravity, boolean falling, double mx, double my, double mz) {
        if (!isNoGravity() && !isSprinting()) {
            double d0;
            if (falling && Math.abs(my - 0.005) >= 0.003 && Math.abs(my - gravity / 16.0) < 0.003) d0 = -0.003;
            else d0 = my - gravity / 16.0;
            return new double[] {mx, d0, mz};
        }
        return new double[] {mx, my, mz};
    }

    private boolean isFree(double dx, double dy, double dz) { return level.noCollision(boundingBox().move(dx, dy, dz)); }

    /** Entity.moveRelative(speed, input): 입력을 바라보는 방향으로 돌려 속도에 더한다(길이가 1을 넘으면 정규화). */
    public void moveRelative(float speed, float ix, float iy, float iz) {
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
