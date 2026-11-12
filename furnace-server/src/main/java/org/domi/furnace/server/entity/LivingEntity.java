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

    /** 바닐라 기본 getMovementEmission = ALL(Entity 쪽 기본을 NONE 으로 둔 것을 되돌린다). */
    @Override
    protected MovementEmission getMovementEmission() { return MovementEmission.ALL; }

    @Override
    public org.domi.furnace.server.scores.PlayerTeam team() { return getTeam(); }


    /** 엔티티 데이터 번호(LivingEntity): 8 생활 플래그, 9 체력, 10 효과 색, 11 효과 앰비언트, 12 화살 수, 13 침 수, 14 자는 위치. */
    public static final int DATA_LIVING_FLAGS = 8, DATA_HEALTH = 9, DATA_EFFECT_PARTICLES = 10, DATA_EFFECT_AMBIENT = 11, DATA_ARROW_COUNT = 12,
            DATA_STINGER_COUNT = 13, DATA_SLEEPING_POS = 14;

    public int invulnerableTime, hurtTime, hurtDuration, deathTime;
    protected float lastHurt;
    protected boolean dead;
    /** 마지막으로 플레이어에게 맞은 뒤 남은 틱(맞으면 100)과 그 플레이어 — 경험치·전리품을 줄지 정한다. */
    protected int lastHurtByPlayerTime;
    protected Player lastHurtByPlayer;
    protected long deathLootTableSeed;
    /** Mob.lootTable(NBT "DeathLootTable"): 있으면 종류 기본 표 대신 쓴다. */
    protected String deathLootTable;
    /** NBT "HurtByTimestamp", "FallFlying": 아직 동작에 쓰지 않고 값만 보존한다. */
    protected int hurtByTimestamp;
    protected boolean fallFlyingSaved;
    /** LivingEntity.absorptionAmount. 몹 쪽 흡수 소비는 아직 없어 값만 보존한다. */
    protected float absorptionAmount;
    protected String lastDamageType = "generic";
    private long lastDamageStamp = Long.MIN_VALUE / 2;
    private Targetable lastDamageAttacker;

    /** getLastDamageSource().getEntity(): 최근 40틱 안에 입은 피해를 일으킨 쪽(없으면 null). */
    public Targetable getLastDamageAttacker() {
        return level.gameTime() - lastDamageStamp > 40L ? null : lastDamageAttacker;
    }

    /** getKillCredit: 마지막으로 때린 플레이어(없으면 마지막으로 때린 몹). */
    public Targetable getKillCredit() { return lastHurtByPlayer != null ? lastHurtByPlayer : lastHurtByMob; }

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

    /** LivingEntity.maxUpStep: 플레이어가 조종하면 최소 1칸. */
    @Override
    public float maxUpStep() {
        float f = getStepHeight();
        return getControllingPassenger() instanceof Player ? Math.max(f, 1.0F) : f;
    }

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
        entityData.define(DATA_EFFECT_PARTICLES, SetEntityData.SERIALIZER_PARTICLES, java.util.List.of());   // 1.20.5+ List<ParticleOptions>
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
                .add(Attribute.FALL_DAMAGE_MULTIPLIER, 1.0).add(Attribute.JUMP_STRENGTH, 0.42F).add(Attribute.OXYGEN_BONUS, 0.0)
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

    /** 지금 처리 중인 피해의 명시 위치(바닐라 DamageSource(type, Vec3), 없으면 null). */
    private double[] pendingPos;
    /** damage_event 에 실을 명시 위치와 직접 엔티티 id(-1 없음, -2 원인만 있음 = 직접 없음, -3 hurtBySource 밖). */
    private double[] pendingEventPos;
    private int pendingDirectId = -3;

    /** 원인·직접 엔티티·위치를 가진 피해(/damage). 원인이 살아 있는 것이 아니면 때린 쪽 없이 직접 엔티티만 본다. */
    public boolean hurtBySource(org.domi.furnace.server.entity.damage.DamageSource src, float amount) {
        // getSourcePosition: 명시 위치, 없으면 직접 엔티티(플레이어도) 위치. damage_event 에는 명시 위치만 싣는다.
        pendingPos = src.position() != null ? src.position() : src.direct() instanceof Player ? src.sourcePosition() : null;
        pendingEventPos = src.position();
        pendingDirectId = src.direct() != null ? src.direct().id() : src.causing() != null ? -2 : -3;
        try {
            return hurtByVia(Damage.of(src.type()), amount, src.attacker(), src.directEntity());
        } finally {
            pendingPos = null;
            pendingEventPos = null;
            pendingDirectId = -3;
        }
    }

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
        if (attacker instanceof Player cp && cp.vitals().instabuild()) source = source.withCreativeSource(true);   // brk.h: 원인 엔티티(화살이면 주인)
        hurtAttacker = attacker;
        try {
            return hurtByInner(source, amount, attacker);
        } finally {
            hurtAttacker = null;
        }
    }

    /** 피해를 처리하는 동안의 원인 엔티티(전투 기록에 이름을 남긴다). */
    private Targetable hurtAttacker;
    private org.domi.furnace.server.entity.damage.CombatTracker combatTracker;

    /** LivingEntity.getCombatTracker: 최근 피해 기록(사망 메시지용 — 지금은 길들인 펫 주인에게 보내는 데만 쓴다). */
    public org.domi.furnace.server.entity.damage.CombatTracker getCombatTracker() {
        if (combatTracker == null) combatTracker = new org.domi.furnace.server.entity.damage.CombatTracker();
        return combatTracker;
    }

    private boolean hurtByInner(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source) || level == null) return false;
        if (isDeadOrDying()) return false;
        // btn.a(brk,F) 오프셋 31~52: #is_fire 피해는 화염 저항 효과가 있으면 받지 않는다(표시·소리 없음)
        if (DamageType.keyHasTag(source.type(), "is_fire") && hasEffect(org.domi.furnace.server.effect.MobEffect.FIRE_RESISTANCE)) return false;
        if (isSleeping()) stopSleeping();
        noActionTime = 0;
        if (amount < 0.0F) amount = 0.0F;
        float evAmount = level.gameEvents().entityDamage(this, source.type(), amount, attacker instanceof Rider r ? r : null, pendingDirect);   // 플러그인 EntityDamageEvent
        if (evAmount < 0.0F) return false;
        amount = evAmount;
        final float dealt = amount;   // 발전 과제 player_hurt_entity·entity_hurt_player 의 dealt(막기 전 피해)
        boolean blocked = false;
        Entity srcEntity = pendingDirect != null ? pendingDirect : attacker instanceof Entity ae ? ae : null;
        if (amount > 0.0F && Shields.isDamageSourceBlocked(isBlocking(), yRot(), x(), z(), pendingPos != null ? new Shields.Source(source.type(), true, pendingPos[0], pendingPos[2], attacker, false, false, null)
                : shieldSource(source.type(), srcEntity, attacker))) {
            hurtCurrentlyUsedShield(amount);
            amount = 0.0F;
            // blockUsingShield(attacker) → attacker.blockedByShield(this): 막은 쪽이 공격자 쪽으로 0.5 밀린다(이어지는 일반 0.4 와 거의 상쇄)
            if (!DamageType.keyHasTag(source.type(), "is_projectile") && (srcEntity instanceof LivingEntity || (srcEntity == null && attacker instanceof Player))) {
                Targetable d = srcEntity instanceof LivingEntity le ? le : attacker;
                Shields.blockedByShield(d, this);   // 기본: 공격자 쪽으로 0.5 밀림(이어지는 0.4 로 상쇄), 파괴수는 기절
            }
            blocked = true;
        }
        // btn.a(brk,F) 오프셋 152: #is_freezing 피해는 #freeze_hurts_extra_types(스트라이더·블레이즈·마그마 큐브)에게 5배
        if (DamageType.keyHasTag(source.type(), "is_freezing") && typeInTag("minecraft:freeze_hurts_extra_types")) amount *= 5.0F;
        // btn.a(brk,F) 오프셋 180~214: #damages_helmet(떨어지는 모루·블록·종유석) 피해는 머리 칸이 비어 있지 않으면 hurtHelmet 뒤 0.75 배
        if (DamageType.keyHasTag(source.type(), "damages_helmet") && !getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            hurtHelmet(source, amount);
            amount *= 0.75F;
        }
        boolean fresh = true;
        // 오프셋 229~: 무적 시간 창 우회는 #bypasses_cooldown 태그(1.21.1 데이터에는 항목이 없다 — 월드 밖·/kill 도 창을 거친다)
        if (invulnerableTime > 10.0F && !DamageType.keyHasTag(source.type(), "bypasses_cooldown")) {
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
            int directId = pendingDirectId >= 0 ? pendingDirectId : pendingDirectId == -2 ? -1 : pendingDirect != null ? pendingDirect.id() : cause;
            if (blocked) broadcastEntityEvent((byte) 29);
            else if (tracker != null) tracker.broadcast(new DamageEvent(id(), t.registryId(), cause, directId, pendingEventPos));
            if (!t.noImpact() && !blocked) markHurt();
        }
        if (attacker != null) {   // 맞은 뒤 기록: 마지막으로 때린 몹, 플레이어면 100틱 동안 경험치·전리품 자격
            setLastHurtByMob(attacker);
            if (attacker instanceof Player pl) {
                lastHurtByPlayerTime = 100;
                lastHurtByPlayer = pl;
            } else if (attacker instanceof Wolf wolf && wolf.isTame()) {   // 바닐라: 길들인 늑대가 때리면 주인(플레이어면)에게 크레딧
                lastHurtByPlayerTime = 100;
                lastHurtByPlayer = wolf.getOwner();
            }
        }
        if (fresh && !DamageType.byKey(source.type()).noKnockback()) {
            // 직접 원인이 투사체면 -calculateHorizontalHurtKnockbackDirection, 아니면 getSourcePosition − 자기 위치, 원인 없음은 (0, 0)
            double dx = 0.0, dz = 0.0;
            if (pendingDirect instanceof Projectile pr) {
                double[] d = pr.hurtKnockbackDirection(this);
                dx = -d[0];
                dz = -d[1];
            } else if (pendingPos != null || pendingDirect != null || attacker != null) {
                dx = (pendingPos != null ? pendingPos[0] : pendingDirect != null ? pendingDirect.x() : attacker.x()) - x();
                dz = (pendingPos != null ? pendingPos[2] : pendingDirect != null ? pendingDirect.z() : attacker.z()) - z();
            }
            knockback(0.4F, dx, dz);
        }
        lastDamageType = source.type();
        lastDamageAttacker = attacker;
        lastDamageStamp = level.gameTime();
        if (isDeadOrDying()) {
            if (!checkTotemDeathProtection(source)) {
                if (fresh) {
                    // 몹 사망 이벤트를 들으면 Paper 처럼 소리는 취소 여부를 본 뒤 낸다(음 높이 난수는 바닐라 자리에서 뽑는다)
                    String ds = getDeathSound();
                    if (ds != null && level.gameEvents().wantsEntityDeath()) pendingDeathSound = new Object[] {ds, getSoundVolume(), getVoicePitch()};
                    else makeSound(ds);
                }
                die(source);
                pendingDeathSound = null;
            }
        } else if (fresh) {
            playHurtSound(source);
        }
        if (!blocked || amount > 0.0F) {   // 바닐라 hurt 끝: 막지 않았으면 효과마다 onMobHurt(벌레 들끓음)
            if (!activeEffects.isEmpty()) {
                var t = org.domi.furnace.server.effect.MobEffectTriggers.Target.of(this);
                for (var e : java.util.List.copyOf(activeEffects.values())) org.domi.furnace.server.effect.MobEffectTriggers.onMobHurt(t, e.effect(), e.amplifier());
            }
        }
        if (attacker instanceof Player ap && ap.advancements() != null && ap.advancements().hasListeners("minecraft:player_hurt_entity")) {
            // 바닐라: 끝에서 source.getEntity() 가 플레이어면 player_hurt_entity(dealt, taken, blocked) — 듣는 조건이 있을 때만 값을 뜬다
            var victim = KillCredit.lootOf(this);
            Rider causing = attacker instanceof Rider r ? r : null;
            Rider direct = pendingDirect != null ? pendingDirect : pendingDirectId == -2 ? null : causing;
            var dmg = KillCredit.source(source.type(), causing, direct);
            final float taken = amount;
            final boolean wasBlocked = blocked;
            ap.triggerAdvancement(a -> a.triggerHurtEntity(victim, dmg, dealt, taken, wasBlocked));
        }
        return !blocked;
    }

    /** 피해 원인 위치(직접 원인 엔티티, 없으면 때린 쪽). 둘 다 없으면 막지 못한다. */
    private static Shields.Source shieldSource(String type, Entity direct, Targetable attacker) {
        if (direct == null && attacker == null) return Shields.Source.none(type);
        double sx = direct != null ? direct.x() : attacker.x(), sz = direct != null ? direct.z() : attacker.z();
        boolean piercing = direct instanceof AbstractArrow a && a.getPierceLevel() > 0;
        return new Shields.Source(type, true, sx, sz, attacker, false, piercing, null);
    }

    /** LivingEntity.isBlocking: 쓰는 아이템이 방패(BLOCK 애니메이션)이고 72000 - 남은 틱 ≥ 5. */
    public boolean isBlocking() {
        if (!isUsingItem()) return false;
        ItemStack s = getItemBySlot(getUsedItemHand() == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return Shields.isShield(s) && Shields.USE_DURATION - useItemRemaining >= Shields.BLOCK_DELAY;
    }

    /** LivingEntity.onBelowWorld(btn.aE): 월드 아래 64 칸을 벗어나면 discard 가 아니라 out_of_world 4 피해(죽음 처리·무적 시간 창을 거친다). */
    @Override
    protected void onBelowWorld() { hurt(Damage.OUT_OF_WORLD, 4.0F); }

    /** LivingEntity.hurtHelmet(btn.c): 몹은 아무것도 안 한다(투구 내구도는 플레이어만 — cmx.c → doHurtEquipment(HEAD)). */
    protected void hurtHelmet(Damage source, float amount) { }

    /** LivingEntity.hurtCurrentlyUsedShield: 몹은 아무것도 안 한다(내구도는 플레이어만). */
    protected void hurtCurrentlyUsedShield(float amount) { }

    // ------------------------------------------------------------------ 아이템 사용(활 당기기 등)

    private int useItemRemaining;
    private int usingHandIndex;

    public boolean isUsingItem() { return ((Byte) entityData.get(DATA_LIVING_FLAGS) & 1) != 0; }

    public int getUsedItemHand() { return ((Byte) entityData.get(DATA_LIVING_FLAGS) & 2) != 0 ? 1 : 0; }

    private void setLivingEntityFlag(int flag, boolean on) {
        byte b = (Byte) entityData.get(DATA_LIVING_FLAGS);
        entityData.set(DATA_LIVING_FLAGS, (byte) (on ? b | flag : b & ~flag));
    }

    /** LivingEntity.startUsingItem(hand): 쓰는 아이템 지속시간을 72000 으로 두고(활) item_interact_start. */
    public void startUsingItem(int hand) {
        ItemStack s = getItemBySlot(hand == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        if (s.isEmpty() || isUsingItem()) return;
        usingHandIndex = hand;
        useItemRemaining = getUseDuration(s);
        setLivingEntityFlag(1, true);
        setLivingEntityFlag(2, hand == 1);
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ITEM_INTERACT_START);
    }

    /** LivingEntity.stopUsingItem: 쓰던 중이었으면 item_interact_finish. */
    public void stopUsingItem() {
        boolean was = isUsingItem();
        setLivingEntityFlag(1, false);
        if (was) gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ITEM_INTERACT_FINISH);
        useItemRemaining = 0;
    }

    /** LivingEntity.getTicksUsingItem: 72000 - 남은 틱. */
    public int getTicksUsingItem() { return isUsingItem() ? 72000 - useItemRemaining : 0; }

    /** 아이템 사용 시간. 기본 72000(활); 마시는 몹이 재정의해 32 등을 준다. */
    protected int getUseDuration(ItemStack s) { return 72000; }

    /** 사용 시간이 다 찼다(바닐라 completeUsingItem). 기본은 아무것도 안 한다. */
    protected void completeUsingItem() {}

    private void updateUsingItem() {
        if (isUsingItem()) {
            ItemStack s = getItemBySlot(getUsedItemHand() == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            if (s.isEmpty()) stopUsingItem();
            else if (useItemRemaining > 0 && --useItemRemaining == 0) completeUsingItem();
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

    /** LivingEntity.isAffectedByPotions: 효과 구름이 건드리는가(방어구 거치대는 아니다). */
    public boolean isAffectedByPotions() { return !(health() <= 0.0F); }

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

    /**
     * LivingEntity.updateInvisibilityStatus: 효과가 바뀌면 투명 효과가 있을 때만 투명으로 두고(효과가 하나도 없으면 해제),
     * 몸에서 나는 효과 입자 목록을 맞춘다 — 입자는 클라이언트가 이 목록을 보고 스스로 뿌린다.
     */
    private void updateInvisibilityStatus() {
        setInvisible(!activeEffects.isEmpty() && hasEffect(org.domi.furnace.server.effect.MobEffect.INVISIBILITY));
        updateEffectParticles();
        syncGlowingFlag();   // 바닐라 updateGlowingStatus(같은 effectsDirty 시점에 이어서 부른다)
    }

    /** 바닐라 updateSynchronizedMobEffectParticles / removeEffectParticles: 효과가 없으면 목록만 비우고 환경 표시는 그대로 둔다. */
    private void updateEffectParticles() {
        entityData.set(DATA_EFFECT_PARTICLES, org.domi.furnace.server.effect.MobEffectInstance.visibleParticles(activeEffects.values()));
        if (!activeEffects.isEmpty()) entityData.set(DATA_EFFECT_AMBIENT, org.domi.furnace.server.effect.MobEffectInstance.allAmbient(activeEffects.values()));
    }

    /** LivingEntity.isCurrentlyGlowing(서버): 발광 효과가 있거나 Glowing 태그. */
    @Override
    public boolean isCurrentlyGlowing() { return hasEffect(org.domi.furnace.server.effect.MobEffect.GLOWING) || super.isCurrentlyGlowing(); }

    protected void onEffectAdded(org.domi.furnace.server.effect.MobEffectInstance i) {
        updateInvisibilityStatus();
        applyEffectAttributes(i);
        i.effect().onEffectStarted(effectHost, i.amplifier());
    }

    protected void onEffectUpdated(org.domi.furnace.server.effect.MobEffectInstance i) {
        updateInvisibilityStatus();
        applyEffectAttributes(i);
    }

    private void applyEffectAttributes(org.domi.furnace.server.effect.MobEffectInstance i) {
        for (Object[] m : i.effect().attributeModifiers()) {
            Attribute a = Attribute.byName((String) m[0]);
            if (a == null || attributes().get(a) == null) continue;
            var inst = attributes().get(a);
            inst.removeModifier((String) m[1]);
            inst.addOrReplaceTransientModifier(new org.domi.furnace.server.attribute.AttributeModifier((String) m[1], ((Number) m[2]).doubleValue() * (i.amplifier() + 1), (Integer) m[3]));
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
        updateInvisibilityStatus();
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

    /** getFallSounds(): {작은 낙하 소리, 큰 낙하 소리}. 바닐라 LivingEntity 는 generic, 몬스터는 hostile, 갑옷 거치대는 armor_stand.fall 둘 다. */
    public String[] fallSounds() { return GENERIC_FALL_SOUNDS; }

    private static final String[] GENERIC_FALL_SOUNDS = {"minecraft:entity.generic.small_fall", "minecraft:entity.generic.big_fall"};

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

    /** getAgeScale: 아기면 0.5. */
    public float getAgeScale() { return isBaby() ? 0.5F : 1.0F; }

    /** getScale(scale 속성) × 나이 배율 — 기본 몸 크기와 부착점의 배율. */
    @Override
    protected float dimensionScale() { return (float) attributeValue(Attribute.SCALE) * getAgeScale(); }

    // ------------------------------------------------------------------ 탑승(LivingEntity)

    /** LivingEntity.getPassengerRidingPosition: 배율은 getScale × getAgeScale. */
    @Override
    public Vec3 getPassengerRidingPosition(Rider r) {
        return new Vec3(x(), y(), z()).add(getPassengerAttachmentPoint(r, (float) attributeValue(Attribute.SCALE) * getAgeScale()));
    }

    /** LivingEntity.stopRiding: 탈것이 바뀌었으면 내릴 자리로 옮긴다(dismountVehicle). */
    @Override
    public void stopRiding() {
        Entity v = vehicle();
        super.stopRiding();
        if (v != null && v != vehicle()) dismountVehicle(v);
    }

    /** dismountVehicle: 지워졌으면 그 자리, 탈것이 있고 차원문 블록 위가 아니면 탈것이 정한 자리, 아니면 둘 중 높은 y. */
    protected void dismountVehicle(Entity v) {
        Vec3 p;
        if (isRemoved()) p = new Vec3(x(), y(), z());
        else if (!v.isRemoved() && !B.isTag(level.getBlockState(v.blockPosition()), "minecraft:portals")) p = v.getDismountLocationForPassenger(this);
        else p = new Vec3(x(), Math.max(y(), v.y()), z());
        dismountTo(p.x(), p.y(), p.z());
    }

    /**
     * travelRidden(player, input): tickRidden 뒤, 서버에서는 조종이 클라이언트 쪽(isControlledByLocalInstance 거짓)이라
     * 스스로 움직이지 않고 속도를 지운 채 칸 안 블록 동작만 본다. 위치는 move_vehicle 이 정한다.
     */
    protected void travelRidden(Player p) {
        tickRidden(p);
        setDeltaMovement(0.0, 0.0, 0.0);
        checkInsideBlocks();
    }

    /** tickRidden(player, input): 탄 플레이어 쪽 회전 등(종류별). */
    protected void tickRidden(Player p) {}

    /** LivingEntity.rideTick: 탄 동안은 낙하 거리를 지운다. */
    @Override
    public void rideTick() {
        super.rideTick();
        resetFallDistance();
    }

    public void makeSound(String sound) {
        if (sound != null) playSound(sound, getSoundVolume(), getVoicePitch());
    }

    @Override
    public boolean hurt(Damage source, float amount) { return hurtBy(source, amount, null); }

    /** LivingEntity.kill: generic_kill 피해 Float.MAX_VALUE(무적 무시, 죽음 처리·전리품은 피해 경로가 한다). */
    @Override
    public void kill() { hurt(Damage.GENERIC_KILL, Float.MAX_VALUE); }

    // ------------------------------------------------------------------ Targetable

    @Override public int id() { return super.id(); }

    @Override public boolean canBeSeenAsEnemy() { return !isInvulnerable() && isAlive(); }

    /** getVisibilityPercent: 웅크림 ×0.8, 투명 ×0.7×방어구 가림, 같은 종류 머리 아이템 ×0.5. */
    @Override
    public double visibilityPercent(Entity looker) {
        ItemStack head = getItemBySlot(EquipmentSlot.HEAD);
        return Visibility.percent(isShiftKeyDown(), isInvisible(),
                Visibility.armorCoverPercentage(head, getItemBySlot(EquipmentSlot.CHEST), getItemBySlot(EquipmentSlot.LEGS), getItemBySlot(EquipmentSlot.FEET)), head, looker);
    }

    @Override public boolean isPlayer() { return false; }

    @Override
    public boolean hurtByMob(Damage source, float amount, Mob attacker) { return hurtBy(source, amount, attacker); }

    @Override
    public org.domi.furnace.api.text.Text displayName() {
        if (hasCustomName()) return getCustomName();
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
        lastHurtMob = t instanceof EnderDragonPart ? null : t;   // 바닐라: LivingEntity 가 아니면 null
        lastHurtMobTimestamp = tickCount;
    }

    protected void actuallyHurt(Damage source, float amount) {
        Rider causing = hurtAttacker instanceof Rider r ? r : null;
        Rider direct = pendingDirect != null ? pendingDirect : causing;
        var damageEvents = level.gameEvents();
        float afterAbsorb;
        if (amount > 0.0F && damageEvents.wantsDamageCalc()) {   // 플러그인 EntityDamageCalculateEvent: 단계마다 바닐라 식 + 플러그인이 바꾼 값
            var calc = damageEvents.entityDamageCalc(this, source.type(), causing, direct, amount, absorptionAmount, new StageRules(source));
            float beforeAbsorb = calc.input(org.domi.furnace.api.event.entity.DamageStage.ABSORPTION);
            afterAbsorb = calc.output(org.domi.furnace.api.event.entity.DamageStage.ABSORPTION);
            if (calc.applies(org.domi.furnace.api.event.entity.DamageStage.RESISTANCE)) {
                awardDealtStat("minecraft:damage_dealt_resisted", calc.input(org.domi.furnace.api.event.entity.DamageStage.RESISTANCE)
                        - calc.output(org.domi.furnace.api.event.entity.DamageStage.RESISTANCE));
            }
            absorptionAmount = Math.max(absorptionAmount - (beforeAbsorb - afterAbsorb), 0.0F);
            awardDealtStat("minecraft:damage_dealt_absorbed", beforeAbsorb - afterAbsorb);
        } else {
            amount = getDamageAfterArmorAbsorb(source, amount);
            amount = getDamageAfterMagicAbsorb(source, amount);
            // btn.f: 흡수 체력을 먼저 깎고 남은 값이 0 이 아닐 때만 체력·전투 기록·ENTITY_DAMAGE
            afterAbsorb = Math.max(amount - absorptionAmount, 0.0F);
            absorptionAmount = Math.max(absorptionAmount - (amount - afterAbsorb), 0.0F);
            awardDealtStat("minecraft:damage_dealt_absorbed", amount - afterAbsorb);
        }
        if (afterAbsorb != 0.0F) {
            // alive 는 피해 전 isAlive()(바닐라와 같다). 낙하 위치는 CombatTracker.recordDamage 처럼 맞은 순간의 것
            getCombatTracker().recordDamage(DamageType.byKey(source.type()), afterAbsorb, currentFallLocation(), fallDistance, tickCount, health() > 0.0F,
                    org.domi.furnace.server.entity.damage.DamageSource.nameOf(causing), org.domi.furnace.server.entity.damage.DamageSource.nameOf(direct));
            setHealth(health() - afterAbsorb);
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_DAMAGE);   // btn:1542
        }
    }

    /** 바닐라 LivingEntity: 원인 엔티티가 플레이어면 그가 입힌 흡수·저항 양을 Math.round(값 * 10) 으로(0 보다 크고 3.4028235E37 보다 작을 때). */
    private void awardDealtStat(String stat, float amount) {
        if (!(hurtAttacker instanceof Player p) || !(amount > 0.0F && amount < 3.4028235E37F)) return;
        int n = Math.round(amount * 10.0F);
        if (p.onOwnerThread()) p.awardStat(stat, n);   // 다른 region 의 플레이어면 그 스레드에서
        else p.runOnOwner(() -> p.awardStat(stat, n));
    }

    /** 피해 계산 단계 이벤트에 넘기는 바닐라 식 — 이 엔티티의 지금 속성·효과·장비로 계산한다. */
    private final class StageRules implements org.domi.furnace.api.event.entity.DamageRules {
        private final Damage source;
        private final org.domi.furnace.server.effect.MobEffectInstance resistance;
        private final float protection;

        StageRules(Damage source) {
            this.source = source;
            this.resistance = getEffect(org.domi.furnace.server.effect.MobEffect.RESISTANCE);
            this.protection = org.domi.furnace.server.entity.damage.CombatRules.damageProtection(source.type(),
                    getItemBySlot(EquipmentSlot.HEAD), getItemBySlot(EquipmentSlot.CHEST), getItemBySlot(EquipmentSlot.LEGS), getItemBySlot(EquipmentSlot.FEET), getItemBySlot(EquipmentSlot.BODY));
        }

        @Override
        public boolean applies(org.domi.furnace.api.event.entity.DamageStage stage) {
            return switch (stage) {
                case ARMOR -> !DamageType.keyHasTag(source.type(), "bypasses_armor");
                case RESISTANCE -> resistance != null && !DamageType.keyHasTag(source.type(), "bypasses_effects")
                        && !DamageType.keyHasTag(source.type(), "bypasses_resistance");
                case ENCHANTMENT -> protection > 0.0F && !DamageType.keyHasTag(source.type(), "bypasses_effects")
                        && !DamageType.keyHasTag(source.type(), "bypasses_enchantments");
                case ENTITY -> entityStageApplies(source);
                case ABSORPTION -> true;
            };
        }

        @Override
        public float apply(org.domi.furnace.api.event.entity.DamageStage stage, float input) {
            return switch (stage) {
                case ARMOR -> org.domi.furnace.server.entity.damage.CombatRules.getDamageAfterAbsorb(input,
                        (float) attributeValue(Attribute.ARMOR), (float) attributeValue(Attribute.ARMOR_TOUGHNESS), source.weapon());
                case RESISTANCE -> resistance == null ? input : org.domi.furnace.server.entity.damage.CombatRules.afterResistance(input, resistance.amplifier());
                case ENCHANTMENT -> protection > 0.0F ? org.domi.furnace.server.entity.damage.CombatRules.getDamageAfterMagicAbsorb(input, protection) : input;
                case ENTITY -> entityStage(source, input);
                case ABSORPTION -> input;
            };
        }
    }

    /** LivingEntity.getDamageAfterArmorAbsorb: CombatRules.getDamageAfterAbsorb(damage, armor, toughness). */
    protected float getDamageAfterArmorAbsorb(Damage source, float amount) {
        if (DamageType.keyHasTag(source.type(), "bypasses_armor")) return amount;
        return org.domi.furnace.server.entity.damage.CombatRules.getDamageAfterAbsorb(amount,
                (float) attributeValue(Attribute.ARMOR), (float) attributeValue(Attribute.ARMOR_TOUGHNESS), source.weapon());
    }

    /** LivingEntity.getDamageAfterMagicAbsorb: 저항 효과, 방어구 보호 인챈트, 그다음 엔티티 고유 경감(마녀). */
    protected float getDamageAfterMagicAbsorb(Damage source, float amount) {
        amount = afterResistanceAndProtection(source, amount);
        return entityStageApplies(source) ? entityStage(source, amount) : amount;
    }

    private float afterResistanceAndProtection(Damage source, float amount) {
        if (DamageType.keyHasTag(source.type(), "bypasses_effects")) return amount;
        var res = getEffect(org.domi.furnace.server.effect.MobEffect.RESISTANCE);
        if (res != null && !DamageType.keyHasTag(source.type(), "bypasses_resistance")) {
            float before = amount;
            amount = org.domi.furnace.server.entity.damage.CombatRules.afterResistance(amount, res.amplifier());
            awardDealtStat("minecraft:damage_dealt_resisted", before - amount);
        }
        if (amount <= 0.0F) return 0.0F;
        if (DamageType.keyHasTag(source.type(), "bypasses_enchantments")) return amount;
        float prot = org.domi.furnace.server.entity.damage.CombatRules.damageProtection(source.type(),
                getItemBySlot(EquipmentSlot.HEAD), getItemBySlot(EquipmentSlot.CHEST), getItemBySlot(EquipmentSlot.LEGS), getItemBySlot(EquipmentSlot.FEET), getItemBySlot(EquipmentSlot.BODY));
        if (prot > 0.0F) amount = org.domi.furnace.server.entity.damage.CombatRules.getDamageAfterMagicAbsorb(amount, prot);
        return amount;
    }

    /** 엔티티 고유 경감이 이 피해에 적용되는가(보호 인챈트 뒤, 흡수 앞 — 바닐라에서는 마녀의 getDamageAfterMagicAbsorb 끝). */
    protected boolean entityStageApplies(Damage source) { return false; }

    /** 엔티티 고유 경감 식. entityStageApplies 가 참일 때 불린다. */
    protected float entityStage(Damage source, float amount) { return amount; }

    /**
     * LivingEntity.checkTotemDeathProtection(1.21.1): bypasses_invulnerability 가 아니면 주 손 → 보조 손 순으로 불사의 토템을 찾아
     * 하나 줄이고 체력 1, 효과 전부 제거, 재생 II 900·흡수 II 100·화염 저항 800, 엔티티 이벤트 35.
     */
    protected boolean checkTotemDeathProtection(Damage source) {
        if (DamageType.keyHasTag(source.type(), "bypasses_invulnerability")) return false;
        ItemStack used = null;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack s = getItemBySlot(slot);
            if (!s.isEmpty() && "minecraft:totem_of_undying".equals(org.domi.furnace.server.item.ItemTools.itemName(s))) {
                used = s;   // 바닐라: 줄이기 전 스택 복사
                updateItemSlot(slot, s.withCount(s.count() - 1));   // shrink(1): 제자리
                break;
            }
        }
        if (used == null) return false;
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ITEM_INTERACT_FINISH);   // btn:g 토템 사용
        setHealth(1.0F);
        removeAllEffects();
        addEffect(new org.domi.furnace.server.effect.MobEffectInstance(org.domi.furnace.server.effect.MobEffect.REGENERATION, 900, 1));
        addEffect(new org.domi.furnace.server.effect.MobEffectInstance(org.domi.furnace.server.effect.MobEffect.ABSORPTION, 100, 1));
        addEffect(new org.domi.furnace.server.effect.MobEffectInstance(org.domi.furnace.server.effect.MobEffect.FIRE_RESISTANCE, 800, 0));
        broadcastEntityEvent((byte) 35);
        return true;
    }

    /** LivingEntity.knockback(strength, x, z): 방향(x,z)의 반대로 밀린다(저항 속성은 아직 없다). */
    @Override
    public void knockback(double strength, double dx, double dz) {
        strength *= 1.0 - attributeValue(Attribute.KNOCKBACK_RESISTANCE);
        if (strength <= 0.0) return;
        hasImpulse = true;
        // 방향이 거의 0 이면 Math.random 으로 다시 뽑는다(엔티티 난수는 쓰지 않는다)
        while (dx * dx + dz * dz < 1.0E-5F) {
            dx = (Math.random() - Math.random()) * 0.01;
            dz = (Math.random() - Math.random()) * 0.01;
        }
        double len = Math.sqrt(dx * dx + dz * dz);
        double nx = len < 1.0E-5 ? 0.0 : dx / len * strength, nz = len < 1.0E-5 ? 0.0 : dz / len * strength;
        setDeltaMovement(vx() / 2.0 - nx, onGround() ? Math.min(0.4, vy() / 2.0 + strength) : vy(), vz() / 2.0 - nz);
    }

    /** LivingEntity.die: 전리품·경험치를 떨군 뒤 죽음 이벤트(3)를 알린다. */
    /** die 안에서: 죽인 피해의 원인 엔티티(damageSource.getEntity, 살아 있는 것만). hurtBy 가 die 직전에 기록한다. */
    protected Targetable damageSourceEntity() { return lastDamageAttacker; }

    public void die(Damage source) {
        if (isRemoved() || dead) return;
        var ge = level.gameEvents();
        if (ge.wantsEntityDeath()) {
            dieWithEvent(source, ge);
            return;
        }
        // getKillCredit().awardKillScore(this, deathScore, source): lastHurtByPlayer, 없으면 lastHurtByMob
        awardKillCredit(source);
        dead = true;
        // damageSource.getEntity().killedEntity: false 면 드롭 없음(좀비가 주민을 감염시킴). Player.killedEntity 는 killed:<종류> 통계 후 true.
        if (killedBySource()) {
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_DIE);   // 스컬크 촉매가 여기서 경험치를 가져간다
            dropAllDeathLoot(source);
        }
        if (tracker != null) tracker.broadcast(new EntityEvent(id(), (byte) 3));
    }

    private void awardKillCredit(Damage source) {
        Targetable credit = lastHurtByPlayer != null ? lastHurtByPlayer : lastHurtByMob;
        if (credit != null) {
            Rider causing = damageSourceEntity() instanceof Rider r ? r : null;
            Rider direct = pendingDirect != null ? pendingDirect : pendingDirectId == -2 ? null : causing;
            KillCredit.award(credit, this, KillCredit.source(source.type(), causing, direct));
        }
    }

    /** killed:<종류> 통계 + damageSource.getEntity().killedEntity — false 면 전리품이 없다. */
    private boolean killedBySource() {
        if (damageSourceEntity() instanceof Player pk) {
            String stat = "minecraft:killed:" + type.name();
            pk.runOnOwner(() -> pk.awardStat(stat, 1));
        }
        return !(damageSourceEntity() instanceof Mob killer) || killer.killedEntity(this);
    }

    /**
     * 플러그인 EntityDeathEvent 를 들을 때의 죽음(Paper 의 취소 가능한 사망과 같은 결과):
     * 전리품 표·특수 전리품·장비 드롭을 바닐라 순서대로 굴려 아이템 엔티티를 만들되 아직 넣지 않고(난수 순서는 바닐라 그대로),
     * 몹 장비 칸·상자 같은 몸 상태 정리는 미뤄 둔 채 이벤트를 부른다. 취소되면 미룬 정리를 버리고 체력을 reviveHealth 로 되돌린다
     * (드롭·경험치·사망 소리·엔티티 이벤트 3 없음). 아니면 소리 → 점수 → entity_die → 이벤트 목록대로 드롭 → 경험치.
     * 플러그인이 손대지 않은 아이템은 미리 만든 아이템 엔티티를 그대로 넣고, 바뀌거나 새로 넣은 아이템은 몸 위치에 새로 만든다.
     */
    private void dieWithEvent(Damage source, org.domi.furnace.server.plugin.GameEvents ge) {
        Object[] sound = pendingDeathSound;
        pendingDeathSound = null;
        dead = true;
        java.util.List<ItemEntity> captured = new java.util.ArrayList<>();
        java.util.List<Runnable> afterDeath = new java.util.ArrayList<>();
        deathCapture = captured;
        deathCleanup = afterDeath;
        try {
            dropDeathItems(source);
        } finally {
            deathCapture = null;
            deathCleanup = null;
        }
        // CraftBukkit getExpReward: dropExperience 의 조건을 미리 본 값(바닐라와 같은 자리에서 굴린다)
        int exp = experienceDropAllowed() ? getExperienceReward() : 0;
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>(captured.size());
        java.util.IdentityHashMap<ItemStack, java.util.ArrayDeque<ItemEntity>> byStack = new java.util.IdentityHashMap<>();
        for (ItemEntity ie : captured) {
            ItemStack st = ie.getItem();
            stacks.add(st);
            byStack.computeIfAbsent(st, k -> new java.util.ArrayDeque<>()).add(ie);
        }
        var out = ge.entityDeath(this, stacks, exp);
        if (out.cancelled()) {
            dead = false;
            setHealth(out.reviveHealth());
            return;
        }
        if (sound != null) playSound((String) sound[0], (Float) sound[1], (Float) sound[2]);
        awardKillCredit(source);   // 장비를 비우기 전(바닐라·Paper 순서) — player_killed_entity 조건이 떨군 장비를 본다
        for (Runnable r : afterDeath) r.run();
        if (killedBySource()) {
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_DIE);   // 스컬크 촉매가 여기서 경험치를 가져간다
            for (ItemStack st : out.drops()) {
                var q = byStack.get(st);
                ItemEntity ie = q == null ? null : q.poll();
                if (ie != null) {
                    level.addFreshEntity(ie);
                } else {
                    var fresh = new ItemEntity(level, level.nextEntityId(), x(), y(), z(), st);   // CraftWorld.dropItem(몸 위치)
                    fresh.setDefaultPickUpDelay();
                    level.addFreshEntity(fresh);
                }
            }
            if (!wasExperienceConsumed() && out.droppedExp() > 0) ExperienceOrb.award(level, x(), y(), z(), out.droppedExp());
        }
        if (tracker != null) tracker.broadcast(new EntityEvent(id(), (byte) 3));
    }

    /** 사망 이벤트를 부르기 전 드롭을 모으는 중이면 그 목록(아니면 null) — spawnAtLocation 이 아이템 엔티티를 넣지 않고 여기에 둔다. */
    private java.util.List<ItemEntity> deathCapture;
    /** 드롭을 모으는 동안 미룬 몸 상태 정리(장비 칸 비우기 등) — 이벤트가 취소되지 않았을 때만 돈다. */
    private java.util.List<Runnable> deathCleanup;
    /** hurt 가 뽑아 둔 사망 소리(이름, 크기, 높이) — 이벤트가 취소되지 않았을 때 낸다. */
    private Object[] pendingDeathSound;

    /**
     * 장비 칸 비우기처럼 죽을 때 드롭과 함께 하는 몸 상태 정리. 사망 이벤트를 위해 드롭을 모으는 중이면 이벤트 뒤로 미루고(취소되면 하지 않는다 —
     * Paper clearedEquipmentSlots·postDeathDropItems), 아니면 바로 한다.
     */
    protected final void afterDeathDrop(Runnable r) {
        if (deathCleanup != null) deathCleanup.add(r);
        else r.run();
    }

    protected void dropAllDeathLoot(Damage source) {
        dropDeathItems(source);
        dropExperience();
    }

    /** dropAllDeathLoot 의 아이템 부분: 전리품 표·특수 전리품(doMobLoot 일 때) → 장비(안장·상자·주머니). */
    private void dropDeathItems(Damage source) {
        boolean byPlayer = lastHurtByPlayerTime > 0;
        if (shouldDropLoot() && level.gameRule("doMobLoot")) {
            dropFromLootTable(source, byPlayer);
            dropCustomDeathLoot(source, byPlayer);
        }
        dropEquipment();
    }

    protected boolean shouldDropLoot() { return true; }

    /** 종류의 기본 표: minecraft:entities/<이름>. */
    public String lootTable() { return "minecraft:entities/" + type.name().substring("minecraft:".length()); }

    protected void dropFromLootTable(Damage source, boolean byPlayer) {
        Player killer = byPlayer ? lastHurtByPlayer : null;
        for (ItemStack s : level.entityDrops(deathLootTable != null ? deathLootTable : lootTable(), lootEntity(), x(), y(), z(), source.type(), killer, killer, deathLootTableSeed)) spawnAtLocation(s);
    }

    protected void dropCustomDeathLoot(Damage source, boolean byPlayer) {}

    /** TODO(장비 드롭): 손·방어구 칸의 드롭 확률. */
    protected void dropEquipment() {}

    /** 경험치: 마지막으로 플레이어에게 맞았고 mob loot 규칙이 켜져 있으면 구슬로 낸다. */
    protected void dropExperience() {
        if (!wasExperienceConsumed() && experienceDropAllowed()) {
            ExperienceOrb.award(level, x(), y(), z(), getExperienceReward());
        }
    }

    private boolean experienceDropAllowed() {
        return isAlwaysExperienceDropper() || lastHurtByPlayerTime > 0 && shouldDropExperience() && level.gameRule("doMobLoot");
    }

    /** skipDropExperience: 스컬크 촉매가 경험치를 가져갔다. */
    private boolean skipDropExperience;

    public void skipDropExperience() { skipDropExperience = true; }

    public boolean wasExperienceConsumed() { return skipDropExperience; }

    /** getExperienceReward(level, killer) — 촉매 리스너용(난수를 쓸 수 있다). */
    public final int experienceReward() { return getExperienceReward(); }

    public final boolean dropsExperience() { return shouldDropExperience(); }

    protected boolean isAlwaysExperienceDropper() { return false; }

    /** LivingEntity.shouldDropExperience: 아기는 경험치(촉매 전하 포함)를 주지 않는다. */
    protected boolean shouldDropExperience() { return !isBaby(); }

    /** getExperienceReward: 종류별 기본 경험치. */
    protected int getExperienceReward() { return 0; }

    /** spawnAtLocation(stack): 몸 위치에 아이템 엔티티(줍기 지연 10). */
    public ItemEntity spawnAtLocation(ItemStack stack) { return spawnAtLocation(stack, 0.0F); }

    /** spawnAtLocation(stack, yOffset): 몸 위치 + yOffset 에 아이템 엔티티. */
    public ItemEntity spawnAtLocation(ItemStack stack, float yOffset) {
        if (stack.isEmpty()) return null;
        var ie = new ItemEntity(level, level.nextEntityId(), x(), y() + yOffset, z(), stack);
        ie.setDefaultPickUpDelay();
        if (deathCapture != null) {   // 사망 이벤트 앞: 만들기만 하고(난수는 지금 굴린다) 넣기는 이벤트 뒤에
            deathCapture.add(ie);
            return ie;
        }
        level.addFreshEntity(ie);
        return ie;
    }

    /** 전리품 조건이 보는 이 엔티티. */
    public org.domi.furnace.server.loot.LootEntity lootEntity() { return LootEntities.of(this); }

    protected void tickDeath() {
        deathTime++;
        if (deathTime >= 20 && !isRemoved()) remove(RemovalReason.KILLED);
    }

    /** LivingEntity.remove: 죽음·버림이면 효과마다 onMobRemoved(돌풍·거미줄·슬라임)를 부르고 효과를 비운 뒤(triggerOnDeathMobEffects) 뺀다. */
    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) triggerOnDeathMobEffects(reason);
        super.remove(reason);
    }

    protected void triggerOnDeathMobEffects(RemovalReason reason) {
        if (activeEffects.isEmpty()) return;
        var t = org.domi.furnace.server.effect.MobEffectTriggers.Target.of(this);
        for (var e : java.util.List.copyOf(activeEffects.values())) org.domi.furnace.server.effect.MobEffectTriggers.onMobRemoved(t, e.effect(), e.amplifier(), reason);
        activeEffects.clear();
    }

    /** LivingEntity.calculateFallDamage(btn.e(FF)I): 맨 앞에서 #fall_damage_immune 종류는 0. */
    protected int calculateFallDamage(float distance, float multiplier) {
        if (typeInTag("minecraft:fall_damage_immune")) return 0;
        return (int) Math.ceil((distance - attributeValue(Attribute.SAFE_FALL_DISTANCE)) * multiplier * attributeValue(Attribute.FALL_DAMAGE_MULTIPLIER));
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, Damage source) {
        boolean flag = super.causeFallDamage(distance, multiplier, source);   // 승객에게도
        int dmg = calculateFallDamage(distance, multiplier);
        if (dmg > 0) {
            hurt(source, dmg);
            return true;
        }
        return flag;
    }

    /**
     * 플러그인이 끈 밀림(CraftBukkit LivingEntity.collides — Bukkit setCollidable). 저장되지 않는다. 바닐라 엔티티는 늘 true.
     * 소유 region 스레드에서만 바꾼다.
     */
    public boolean collides = true;

    /** LivingEntity.isPushable: 살아 있고 관전자가 아니며 사다리 등에 매달리지 않았다(+ CraftBukkit collides). */
    @Override
    public boolean isPushable() { return isAlive() && !isSpectator() && !onClimbable() && collides; }

    // ------------------------------------------------------------------ 틱

    @Override
    public void baseTick() {
        int oldAir = getAirSupply();
        super.baseTick();
        if (isAlive()) {
            if (isInWall()) hurt(new Damage("in_wall", false, false, false), 1.0F);
            if (isEyeInWater() && !B.is(level.getBlockState(new org.domi.furnace.server.block.Pos((int) Math.floor(x()), (int) Math.floor(eyeY()), (int) Math.floor(z()))), "minecraft:bubble_column")) {
                boolean drowns = !canBreatheUnderwater() && !hasWaterBreathing(this);
                if (drowns) {   // 숨을 쉴 수 있으면 줄지 않을 뿐 물속에서 차오르지는 않는다(btn.aw 오프셋 308 → 477)
                    setAirSupply(decreaseAirSupply(getAirSupply()));
                    if (getAirSupply() == -20) {
                        setAirSupply(0);
                        hurt(new Damage("drown", false, false, false), 2.0F);
                    }
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
        tickEffects();   // btn.aw 오프셋 765(eo): 무적 시간·사망 틱·마지막으로 때린 쪽 기록 뒤 — 효과 피해가 줄어든 무적 시간을 본다
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
    }

    private int removeArrowTime;

    /** 몸에 박힌 화살 수(DATA_ARROW_COUNT). */
    public int getArrowCount() { return (Integer) entityData.get(DATA_ARROW_COUNT); }

    public void setArrowCount(int n) { entityData.set(DATA_ARROW_COUNT, n); }

    /** LivingEntity.tick: 박힌 화살이 있으면 20 × (30 − 수) 틱마다 하나씩 빠진다. */
    private void tickArrowCount() {
        int n = getArrowCount();
        if (n > 0) {
            if (removeArrowTime <= 0) removeArrowTime = 20 * (30 - n);
            if (--removeArrowTime <= 0) setArrowCount(n - 1);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return;
        if (isSleeping() && !checkBedExists()) stopSleeping();
        updateSwingTime();
        updateUsingItem();
        tickArrowCount();
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
        refreshDirtyAttributes();
    }

    /** LivingEntity.refreshDirtyAttributes → onAttributeUpdated: 최대 체력이 바뀌어 체력이 넘치면 깎는다. (흡수 상한은 흡수 미구현) */
    protected void refreshDirtyAttributes() {
        for (Attribute a : attributes().takeToUpdate()) {
            if (a == Attribute.MAX_HEALTH) {
                float max = maxHealth();
                if (health() > max) setHealth(max);
            }
        }
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
        // btn.m_ 오프셋 591~611: 슬로 폴링·부양 효과는 매 틱 낙하 거리를 지운다(생물 전체 — 플레이어는 PlayerVitals 쪽)
        if (hasEffect(org.domi.furnace.server.effect.MobEffect.SLOW_FALLING) || hasEffect(org.domi.furnace.server.effect.MobEffect.LEVITATION)) resetFallDistance();
        if (getControllingPassenger() instanceof Player p && isAlive()) travelRidden(p);
        else travel(xxa, yya, zza);   // btn.a(exc): isControlledByLocalInstance(→ isEffectiveAi = !NoAI)가 아니면 움직이지 않는다 — NoAI 몹은 떨어지지도 밀리지도 않는다
        tickFreezing();
        pushEntities();
    }

    /** 바닐라 SPEED_MODIFIER_POWDER_SNOW_ID: 얼어 있는 만큼 이동 속도를 깎는 수정자(transient — 저장하지 않는다). */
    public static final String POWDER_SNOW_SPEED_MODIFIER = "minecraft:powder_snow";

    /**
     * LivingEntity.aiStep 의 "freezing" 구간(btn.m_ 오프셋 678~807, travel 바로 뒤): 죽어 가지 않으면 가루눈 안이고 얼 수 있을 때 ticksFrozen
     * = min(140, +1), 아니면 max(0, −2). 얼음 속도 수정자를 지우고 다시 단다. 40틱마다 완전히 얼었고 얼 수 있으면 freeze 1 피해.
     */
    protected void tickFreezing() {
        if (!isDeadOrDying()) {
            int i = getTicksFrozen();
            if (isInPowderSnow() && canFreeze()) setTicksFrozen(Math.min(TICKS_REQUIRED_TO_FREEZE, i + 1));
            else setTicksFrozen(Math.max(0, i - 2));
        }
        removeFrost();
        tryAddFrost();
        if (tickCount % 40 == 0 && isFullyFrozen() && canFreeze()) hurt(FREEZE, 1.0F);
    }

    private static final Damage FREEZE = Damage.of(DamageType.byKey("freeze"));

    /** removeFrost(btn.dY): 이동 속도에 얼음 수정자가 있으면 뗀다. */
    protected void removeFrost() {
        var inst = attributes().get(Attribute.MOVEMENT_SPEED);
        if (inst != null) inst.removeModifier(POWDER_SNOW_SPEED_MODIFIER);
    }

    /** tryAddFrost(btn.dZ): 발밑(0.2 아래) 칸이 공기가 아니고 얼음 틱이 있으면 −0.05 × 언 비율(더하기)을 단다. */
    protected void tryAddFrost() {
        if (B.isAir(level.getBlockState(getOnPosLegacy()))) return;
        if (getTicksFrozen() <= 0) return;
        var inst = attributes().get(Attribute.MOVEMENT_SPEED);
        if (inst == null) return;
        float f = -0.05F * getPercentFrozen();
        inst.addOrReplaceTransientModifier(new org.domi.furnace.server.attribute.AttributeModifier(POWDER_SNOW_SPEED_MODIFIER, f, org.domi.furnace.server.attribute.AttributeModifier.ADD_VALUE));
    }

    /** LivingEntity.canFreeze(btn.dC): 관전자가 아니고, 머리·몸통·다리·발·몸(BODY) 칸에 #freeze_immune_wearables 를 하나도 입지 않았으며 종류가 면역이 아니다. */
    @Override
    public boolean canFreeze() {
        if (isSpectator()) return false;
        for (EquipmentSlot slot : FREEZE_ARMOR_SLOTS) {
            if (org.domi.furnace.server.item.ItemTags.is(getItemBySlot(slot), "minecraft:freeze_immune_wearables")) return false;
        }
        return super.canFreeze();
    }

    static final EquipmentSlot[] FREEZE_ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.BODY};

    /** MobEffectUtil.hasWaterBreathing(bsa.c): 물숨쉬기 또는 전도체의 힘. */
    public static boolean hasWaterBreathing(LivingEntity e) {
        return e.hasEffect(org.domi.furnace.server.effect.MobEffect.WATER_BREATHING) || e.hasEffect(org.domi.furnace.server.effect.MobEffect.CONDUIT_POWER);
    }

    /**
     * LivingEntity.pushEntities(서버): 몸통에 걸친 밀 수 있는 엔티티를 모아, maxEntityCramming 이 켜져 있고 수가 넘치면
     * 1/4 확률(nextInt(4))로 탈것에 안 탄 것 수를 세어 넘치면 cramming 6 피해, 그리고 후보마다 후보.push(나).
     * 난수는 목록이 비지 않고 규칙 > 0 이고 크기 > 규칙-1 일 때만 부른다.
     */
    protected void pushEntities() {
        if (level == null) return;
        var s = level.pushScratch;
        s.collect(level, this, boundingBox());
        int n = s.size();
        if (n == 0) return;
        int rule = level.gameRuleInt("maxEntityCramming");
        if (rule > 0 && n > rule - 1 && random.nextInt(4) == 0) {
            if (s.countNonPassengers() > rule - 1) hurt(Damage.CRAMMING, 6.0F);
        }
        s.pushAllBy(this);
        s.clear();
    }

    /** LivingEntity.doPush(entity): 후보.push(나). 철 골렘·워든이 덮어쓴다. */
    protected void doPush(Entity e) { e.push(this); }

    /** doPush(플레이어) = player.push(나) — 플레이어 쪽 속도는 클라이언트 권위라 내 몫만 받는다. */
    protected void doPush(Player p) { pushByPlayer(p); }

    // ------------------------------------------------------------------ 장비

    private final java.util.EnumMap<EquipmentSlot, ItemStack> lastEquipmentItems = new java.util.EnumMap<>(EquipmentSlot.class);

    /** getItemBySlot: 종류별 장비 칸(기본: 없음). */
    public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }

    /** setItemSlot: 칸을 다른 스택으로 바꾼다 — 바닐라처럼 onEquipItem(장착 소리·equip/unequip)을 부른다(몹·갑옷 거치대). */
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

    /**
     * 제자리 수정: 바닐라가 칸의 스택을 그 자리에서 고치는 곳(내구도 깎기·부서짐, 쇠뇌 장전·발사, 토템 하나 쓰기, 마법 부여)을 Furnace 는
     * 불변 ItemStack 때문에 새 스택으로 바꿔 넣는다. 그 경우는 이것을 써서 onEquipItem 을 부르지 않는다.
     */
    public void updateItemSlot(EquipmentSlot slot, ItemStack stack) {}

    /**
     * LivingEntity.onEquipItem(slot, old, new): 둘 다 비었거나 같은 아이템·컴포넌트이거나 첫 틱이면 없음. 관전자가 아니면,
     * 조용하지 않고 새 것이 이 칸에 입는 장착 아이템이면 그 장착 소리(엔티티 난수 씨앗), doesEmitEquipEvent 면 장착 아이템이면 equip, 아니면 unequip.
     */
    public void onEquipItem(EquipmentSlot slot, ItemStack oldItem, ItemStack newItem) {
        if (newItem.isEmpty() && oldItem.isEmpty() || org.domi.furnace.server.menu.Stacks.isSameItemSameComponents(oldItem, newItem) || firstTick) return;
        if (level == null || isSpectator()) return;
        EquipmentSlot equipSlot = DispenserEquip.equipableSlot(newItem);
        if (!isSilent() && equipSlot == slot) {
            level.playSeededSound(null, x(), y(), z(), org.domi.furnace.server.block.ItemUse.equipSound(org.domi.furnace.server.item.ItemTools.itemName(newItem)), soundSource(),
                    1.0F, 1.0F, random.nextLong());
        }
        if (doesEmitEquipEvent(slot)) {
            gameEvent(equipSlot != null ? org.domi.furnace.server.world.gameevent.GameEvent.EQUIP : org.domi.furnace.server.world.gameevent.GameEvent.UNEQUIP);
        }
    }

    /** doesEmitEquipEvent: 몹·거치대는 모든 칸(플레이어만 방어구 칸 — ConnectionHandler 쪽). */
    protected boolean doesEmitEquipEvent(EquipmentSlot slot) { return true; }

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
            // ItemStack.forEachModifier 의 인챈트 속성 효과(화염 보호의 burning_time 등) — 몹 장비에도 걸린다
            EnchantAttributes.forEachModifier(last, slot, (a, m) -> {
                var inst = attributes().get(a);
                if (inst != null) inst.removeModifier(m.id());
            });
            EnchantAttributes.forEachModifier(cur, slot, (a, m) -> {
                var inst = attributes().get(a);
                if (inst != null) inst.addOrReplaceTransientModifier(m);
            });
            for (var e : org.domi.furnace.server.item.ItemTools.attributeEntries(cur)) {
                if (!org.domi.furnace.server.item.ItemTools.slotGroupMatches(e.slot(), slot.slotName())) continue;
                Attribute a = Attribute.byName(e.type());
                var inst = a == null ? null : attributes().get(a);
                if (inst != null) {
                    inst.removeModifier(e.id());
                    inst.addOrReplaceTransientModifier(new org.domi.furnace.server.attribute.AttributeModifier(e.id(), e.amount(),
                            org.domi.furnace.server.attribute.AttributeModifier.operationOf(e.operation())));
                }
            }
        }
        if (changes != null && tracker != null) tracker.broadcast(new org.domi.furnace.server.net.protocol.play.SetEquipment(id(), changes));
    }

    /** LivingEntity.igniteForTicks: 태우는 시간 속성(burning_time)을 곱해 올림한다(불 보호 인챈트가 줄인다). */
    @Override
    public void igniteForTicks(int ticks) { super.igniteForTicks((int) Math.ceil(ticks * attributeValue(Attribute.BURNING_TIME))); }

    // ------------------------------------------------------------------ 휘두르기

    protected boolean swinging;

    public boolean isSwinging() { return swinging; }
    protected int swingTime;
    protected int swingingArm;
    public float attackAnim;

    /** getCurrentSwingDuration: 성급함·전달체 힘이면 6−(1+증폭), 채굴 피로면 6+(1+증폭)×2, 아니면 6. */
    protected int getCurrentSwingDuration() {
        var haste = activeEffects.get(org.domi.furnace.server.effect.MobEffect.HASTE);
        var conduit = activeEffects.get(org.domi.furnace.server.effect.MobEffect.CONDUIT_POWER);
        if (haste != null || conduit != null) {
            int amp = Math.max(haste != null ? haste.amplifier() : 0, conduit != null ? conduit.amplifier() : 0);
            return 6 - (1 + amp);
        }
        var fatigue = activeEffects.get(org.domi.furnace.server.effect.MobEffect.MINING_FATIGUE);
        return fatigue != null ? 6 + (1 + fatigue.amplifier()) * 2 : 6;
    }

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

    // ------------------------------------------------------------------ 잠자기(바닐라 LivingEntity.startSleeping/stopSleeping)

    public java.util.Optional<org.domi.furnace.server.block.Pos> getSleepingPos() {
        int[] p = (int[]) entityData.get(DATA_SLEEPING_POS);
        return p == null ? java.util.Optional.empty() : java.util.Optional.of(new org.domi.furnace.server.block.Pos(p[0], p[1], p[2]));
    }

    public void setSleepingPos(org.domi.furnace.server.block.Pos p) { entityData.set(DATA_SLEEPING_POS, new int[] {p.x(), p.y(), p.z()}); }

    public void clearSleepingPos() { entityData.set(DATA_SLEEPING_POS, null); }

    public boolean isSleeping() { return getSleepingPos().isPresent(); }

    /** startSleeping: 타고 있으면 내리고, 침대를 점유 상태로, 자는 자세, 침대 위(0.5, 0.6875, 0.5)로 옮기고 멈춘다. */
    public void startSleeping(org.domi.furnace.server.block.Pos pos) {
        if (isPassenger()) stopRiding();
        int st = level.getBlockState(pos);
        if (B.isTag(st, "minecraft:beds")) level.setBlock(pos, B.prop("occupied").with(st, true), 3, 512);
        setPose(POSE_SLEEPING);
        setPos(pos.x() + 0.5, pos.y() + 0.6875, pos.z() + 0.5);
        setSleepingPos(pos);
        setDeltaMovement(0.0, 0.0, 0.0);
        hasImpulse = true;
    }

    /** stopSleeping: 침대 점유를 풀고 일어설 자리(BedBlock.findStandUpPosition, 없으면 침대 위 칸)로 옮겨 침대를 바라본다. */
    public void stopSleeping() {
        getSleepingPos().ifPresent(pos -> {
            int st = level.getBlockState(pos);
            if (B.isTag(st, "minecraft:beds")) {
                int facing = org.domi.furnace.server.block.Dir.byName(B.prop("facing").valueName(st));
                level.setBlock(pos, B.prop("occupied").with(st, false), 3, 512);
                Vec3 v = DismountHelper.bedStandUpPosition(this, pos, facing, yRot());
                if (v == null) v = new Vec3(pos.x() + 0.5, pos.y() + 1 + 0.1, pos.z() + 0.5);
                double dx = pos.x() + 0.5 - v.x(), dy = pos.y() - v.y(), dz = pos.z() + 0.5 - v.z();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1.0E-4) dx = dz = 0.0;
                else {
                    dx /= len;
                    dz /= len;
                }
                float yr = (float) Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI - 90.0));
                setPos(v.x(), v.y(), v.z());
                setYRot(yr);
                setXRot(0.0F);
            }
        });
        setPose(POSE_STANDING);
        setPos(x(), y(), z());
        clearSleepingPos();
    }

    /** checkBedExists: 자는 자리가 아직 침대인가. */
    protected boolean checkBedExists() { return getSleepingPos().map(p -> B.isTag(level.getBlockState(p), "minecraft:beds")).orElse(false); }

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
        if (!isEffectiveAi()) return;   // btn.a(exc) 첫 줄 da()
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
        return isInWall(level, x(), eyeY(), z(), bbWidth());
    }

    /**
     * Entity.isInWall 의 블록 검사: 눈 위치 둘레 폭×0.8, 높이 1e-6 상자가 질식 블록(공기 아님·isSuffocating)의 충돌 모양과 겹친다.
     * 플레이어(엔티티 계층 밖)도 같은 식을 쓴다. noPhysics(관전자) 검사는 호출자가 한다.
     */
    public static boolean isInWall(ChunkLevel level, double ex, double ey, double ez, float width) {
        float f = width * 0.8F;
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
                if (level.noCollision(this, boundingBox()) && !level.containsAnyLiquid(boundingBox())) ok = true;
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

    /**
     * LivingEntity.onClimbable: 관전자가 아니고 사다리·덩굴 같은 #climbable 블록 안이거나,
     * 열린 다락문이고 바로 아래가 같은 방향 사다리다(trapdoorUsableAsLadder).
     */
    public boolean onClimbable() {
        if (isSpectator()) return false;
        int st = inBlockState();   // 바닐라 getInBlockState(틱·칸 바뀜마다 다시 읽는 캐시) — 밀기 후보마다 불려 블록 조회를 아낀다
        boolean climbable = org.domi.furnace.server.block.B.isTag(st, "minecraft:climbable");
        if (!climbable && !org.domi.furnace.server.block.types.TrapDoorBlock.isTrapDoor(st)) return false;
        org.domi.furnace.server.block.Pos pos = blockPosition();
        if (climbable || org.domi.furnace.server.block.types.TrapDoorBlock.usableAsLadder(st, level.getBlockState(pos.below()))) {
            lastClimbablePos = pos;
            return true;
        }
        return false;
    }

    /** LivingEntity.getLastClimbablePos: 마지막으로 매달렸던 칸(땅에 닿으면 지워진다), 없으면 null. */
    public org.domi.furnace.server.block.Pos lastClimbablePos() { return lastClimbablePos; }

    /**
     * FallLocation.getCurrentFallLocation: 마지막으로 매달렸던 칸이 있으면 그 칸의 <b>지금</b> 블록으로 이름을 정하고
     * (부서져 공기가 됐으면 other_climbable), 없고 물속이면 water, 아니면 null.
     */
    public String currentFallLocation() {
        if (lastClimbablePos != null) return fallLocationOf(level.getBlockState(lastClimbablePos));
        return isInWater() ? "water" : null;
    }

    /** FallLocation.blockToFallLocation: 사다리·다락문 → ladder, 덩굴 → vines, … 그 밖 → other_climbable. */
    public static String fallLocationOf(int state) {
        if (B.is(state, "minecraft:ladder") || B.isTag(state, "minecraft:trapdoors")) return "ladder";
        if (B.is(state, "minecraft:vine")) return "vines";
        if (B.is(state, "minecraft:weeping_vines") || B.is(state, "minecraft:weeping_vines_plant")) return "weeping_vines";
        if (B.is(state, "minecraft:twisting_vines") || B.is(state, "minecraft:twisting_vines_plant")) return "twisting_vines";
        if (B.is(state, "minecraft:scaffolding")) return "scaffolding";
        return "other_climbable";
    }

    /** LivingEntity.checkFallDamage: 땅에 닿으면 착지 블록 입자(onChangedBlock 은 아직 없다), 끝에서 lastClimbablePos 를 지운다. */
    @Override
    protected void checkFallDamage(double dy, boolean ground, int state, org.domi.furnace.server.block.Pos pos) {
        if (ground && fallDistance > 0.0F) {
            LandingParticles.spawn(level, x(), y(), z(), blockPosition(), pos, state, fallDistance, attributeValue(Attribute.SAFE_FALL_DISTANCE));
        }
        super.checkFallDamage(dy, ground, state, pos);
        if (ground) lastClimbablePos = null;
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

    private boolean isFree(double dx, double dy, double dz) { return level.noCollision(this, boundingBox().move(dx, dy, dz)); }

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
        tag.putInt("HurtByTimestamp", hurtByTimestamp);
        tag.putShort("DeathTime", (short) deathTime);
        tag.putFloat("AbsorptionAmount", absorptionAmount);
        tag.putList("attributes", attributes().save());   // transient 수정자(얼음·장비·효과 …)는 AttributeMap 이 뺀다
        if (!activeEffects.isEmpty()) {
            var list = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
            for (var e : activeEffects.values()) list.add(e.save());
            tag.putList("active_effects", list);
        }
        tag.putBoolean("FallFlying", fallFlyingSaved);
        getSleepingPos().ifPresent(p -> {
            tag.putInt("SleepingX", p.x());
            tag.putInt("SleepingY", p.y());
            tag.putInt("SleepingZ", p.z());
        });
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        // 바닐라: internalSetAbsorptionAmount(자르지 않고 그대로)
        absorptionAmount = tag.getFloat("AbsorptionAmount");
        attributes().load(tag.tags().get("attributes") instanceof org.domi.furnace.server.net.io.nbt.NbtList l ? l : null);
        if (tag.tags().get("active_effects") instanceof org.domi.furnace.server.net.io.nbt.NbtList effects) {
            for (Object o : effects.items()) {
                if (o instanceof NbtCompound c) {
                    var e = org.domi.furnace.server.effect.MobEffectInstance.load(c);
                    if (e != null) activeEffects.put(e.effect(), e);
                }
            }
            updateEffectParticles();   // 바닐라는 effectsDirty 초기값 true 로 첫 틱에 맞춘다 — 불러온 효과의 입자도 보인다
        }
        if (tag.tags().get("Team") instanceof String teamName && level != null && level.scoreboard() != null) {
            // 바닐라: 팀이 없으면 경고만 남기고 넘어간다
            var board = level.scoreboard();
            var team = board.getPlayerTeam(teamName);
            if (team != null) board.addPlayerToTeam(uuid.toString(), team);
        }
        if (tag.contains("Health")) setHealth(tag.getFloat("Health"));
        hurtTime = tag.getShort("HurtTime");
        hurtByTimestamp = tag.getIntOr0("HurtByTimestamp");
        fallFlyingSaved = tag.getBoolean("FallFlying");
        yHeadRot = yBodyRot = yRot();
        yHeadRotO = yBodyRotO = yRot();
        deathTime = tag.getShort("DeathTime");
        if (tag.contains("Health")) setHealth(tag.getFloat("Health"));
        if (tag.contains("SleepingX") && tag.contains("SleepingY") && tag.contains("SleepingZ")) {
            var p = new org.domi.furnace.server.block.Pos(tag.getIntOr0("SleepingX"), tag.getIntOr0("SleepingY"), tag.getIntOr0("SleepingZ"));
            setSleepingPos(p);
            setPose(POSE_SLEEPING);
            if (!firstTick) setPos(p.x() + 0.5, p.y() + 0.6875, p.z() + 0.5);
        }
    }
}
