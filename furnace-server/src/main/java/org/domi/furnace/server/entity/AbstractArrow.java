// 바닐라 AbstractArrow: 날아가며 중력 0.05·공기 0.99(물 0.6)로 줄고, 블록에 박히면 1200틱 뒤 사라지며, 엔티티를 맞추면 속력 × 기본 피해(2)만큼 때린다.
// TODO:, 화살 박힘 표시(arrow count), 관통 시 맞은 대상 기록, 플레이어가 쏜 화살의 줍기 규칙.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class AbstractArrow extends Projectile {
    public static final int DATA_ID_FLAGS = 8, DATA_PIERCE_LEVEL = 9;
    public enum Pickup { DISALLOWED, ALLOWED, CREATIVE_ONLY }

    protected boolean inGround;
    protected int inGroundTime;
    public Pickup pickup = Pickup.DISALLOWED;
    public int shakeTime;
    private int life;
    private double baseDamage = 2.0;
    private int lastStateInGround = -1;
    private String soundEvent = "entity.arrow.hit";
    private java.util.Set<Integer> piercedEntities;
    /** 관통으로 죽인 생물들(killed_by_crossbow 용 — 관통 화살이 처음 맞을 때 만든다). */
    private java.util.List<LivingEntity> piercedAndKilled;
    protected ItemStack pickupItem = ItemStack.EMPTY;
    /** 쏜 무기(firedFromWeapon) — 힘·밀어내기 계산용. 무기 없이 생긴 화살은 null. */
    private ItemStack firedFromWeapon;

    protected AbstractArrow(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_PIERCE_LEVEL, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    @Override
    public void setOwner(Targetable o) {
        super.setOwner(o);
        if (o instanceof Player && pickup == Pickup.DISALLOWED) pickup = Pickup.ALLOWED;
    }

    public boolean isCritArrow() { return ((Byte) entityData.get(DATA_ID_FLAGS) & 1) != 0; }

    public void setCritArrow(boolean v) { setFlag(1, v); }

    public boolean isNoPhysics() { return level == null ? false : ((Byte) entityData.get(DATA_ID_FLAGS) & 2) != 0; }

    public void setNoPhysics(boolean v) { setFlag(2, v); }

    private void setFlag(int f, boolean v) {
        byte b = (Byte) entityData.get(DATA_ID_FLAGS);
        entityData.set(DATA_ID_FLAGS, (byte) (v ? b | f : b & ~f));
    }

    public byte getPierceLevel() { return (Byte) entityData.get(DATA_PIERCE_LEVEL); }

    public void setPierceLevel(byte v) { entityData.set(DATA_PIERCE_LEVEL, v); }

    public void setBaseDamage(double d) { baseDamage = d; }

    public double getBaseDamage() { return baseDamage; }

    public void setSoundEvent(String s) { soundEvent = s; }

    @Override
    protected double defaultGravity() { return 0.05; }

    public ItemStack getWeaponItem() { return firedFromWeapon; }

    /** 쇠뇌로 쏜 화살인가(바닐라 shotFromCrossbow 플래그 — 쇠뇌가 만든 발사체에만 켜진다). */
    public boolean isShotFromCrossbow() {
        return firedFromWeapon != null && "minecraft:crossbow".equals(org.domi.furnace.server.item.ItemTools.itemName(firedFromWeapon));
    }

    /**
     * AbstractArrow(type, owner, level, pickupItem, firedFromWeapon) 생성자의 나머지: 줍는 아이템(intangible_projectile 이면 창조 전용),
     * 무기 사본, 관통 수(projectile_piercing), 발사체 생성 효과(화염: 100초 불), 마지막에 소유자(플레이어면 줍기 허용).
     */
    public void initFromItem(Targetable owner, ItemStack pickup, ItemStack weapon) {
        pickupItem = pickup;
        if (pickup.patch().entries().get("minecraft:intangible_projectile") != null) this.pickup = Pickup.CREATIVE_ONLY;
        if (weapon != null) {
            firedFromWeapon = weapon;
            int pierce = Math.max(0, org.domi.furnace.server.item.ItemTools.enchantmentLevel(weapon, "minecraft:piercing"));
            if (pierce > 0) setPierceLevel((byte) pierce);
            if (org.domi.furnace.server.item.ItemTools.enchantmentLevel(weapon, "minecraft:flame") > 0) igniteForSeconds(100.0F);
        }
        setOwner(owner);
    }

    /** EnchantmentHelper.modifyDamage(무기): 힘 — 직접 공격자가 #arrows 면 +0.5 × 레벨. */
    double modifyDamage(double d) {
        if (firedFromWeapon == null || !(this instanceof Arrow || this instanceof SpectralArrow)) return d;
        int power = org.domi.furnace.server.item.ItemTools.enchantmentLevel(firedFromWeapon, "minecraft:power");
        float f = (float) d;
        if (power > 0) f += 0.5F * power;
        return Math.max(0.0F, f);
    }

    /** AbstractArrow.doKnockback: 밀어내기 레벨(#arrows) × 0.6 × (1 − 넉백 저항)만큼 수평 속도 방향으로, 위로 0.1. */
    protected void doKnockback(LivingEntity target) {
        double kb = 0.0;
        if (firedFromWeapon != null && (this instanceof Arrow || this instanceof SpectralArrow)) {
            int punch = org.domi.furnace.server.item.ItemTools.enchantmentLevel(firedFromWeapon, "minecraft:punch");
            if (punch > 0) kb = (float) punch;
        }
        if (kb > 0.0) {
            double resist = Math.max(0.0, 1.0 - target.attributeValue(org.domi.furnace.server.attribute.Attribute.KNOCKBACK_RESISTANCE));
            double hx = vx(), hz = vz();
            double len = Math.sqrt(hx * hx + hz * hz);
            if (len < 1.0E-5F) return;
            double sx = hx / len * (kb * 0.6 * resist), sz = hz / len * (kb * 0.6 * resist);
            if (sx * sx + sz * sz > 0.0) {
                target.addDeltaMovement(sx, 0.1, sz);
                target.hasImpulse = true;
            }
        }
    }

    /** AbstractArrow.setEnchantmentEffectsFromEntity: 피해량 = 힘 × 2 + 난이도별 무작위(난이도 × 0.11, 0.57425 사이). */
    public void setEnchantmentEffectsFromEntity(float power) {
        setBaseDamage(power * 2.0F + random.triangle(level.difficulty() * 0.11, 0.57425));
    }

    @Override
    public void tick() {
        super.tick();
        boolean noPhysics = isNoPhysics();
        double dmx = vx(), dmy = vy(), dmz = vz();
        if (xRotO == 0.0F && yRotO == 0.0F) {
            double h = Math.sqrt(dmx * dmx + dmz * dmz);
            setYRot((float) (Mth.atan2(dmx, dmz) * 180.0F / (float) Math.PI));
            setXRot((float) (Mth.atan2(dmy, h) * 180.0F / (float) Math.PI));
            yRotO = yRot();
            xRotO = xRot();
        }
        Pos pos = blockPosition();
        int state = level.getBlockState(pos);
        if (!BlockInfo.get().isAir(state) && !noPhysics) {
            var shape = BlockInfo.get().collisionShape(state);
            if (!shape.isEmpty()) {
                for (int i = 0; i < shape.boxCount(); i++) {
                    double[] b = shape.box(i);
                    if (x() >= pos.x() + b[0] && x() < pos.x() + b[3] && y() >= pos.y() + b[1] && y() < pos.y() + b[4] && z() >= pos.z() + b[2] && z() < pos.z() + b[5]) {
                        inGround = true;
                        break;
                    }
                }
            }
        }
        if (shakeTime > 0) shakeTime--;
        if (isInWaterRainOrBubble() || isInPowderSnow()) clearFire();
        if (inGround && !noPhysics) {
            if (lastStateInGround != state && shouldFall()) startFalling();
            else tickDespawn();
            inGroundTime++;
            return;
        }
        inGroundTime = 0;
        double sx = x(), sy = y(), sz = z();
        double ex = sx + dmx, ey = sy + dmy, ez = sz + dmz;
        var br = org.domi.furnace.server.block.Clip.clipCollider(level, sx, sy, sz, ex, ey, ez);
        if (!br.miss()) {
            ex = br.x();
            ey = br.y();
            ez = br.z();
        }
        Hit hit = br.miss() ? null : new Hit(null, br.pos(), br.face(), br.x(), br.y(), br.z());
        while (!isRemoved()) {
            Hit eh = findHitEntity(sx, sy, sz, ex, ey, ez);
            if (eh != null) hit = eh;
            if (hit != null && hit.isEntity()) {
                Targetable owner = getOwner();
                if (hit.target() instanceof Player && owner instanceof Player) {   // TODO(PvP 규칙): 서로 때릴 수 없으면 맞지 않는다
                }
            }
            if (hit != null && !noPhysics) {
                hitTargetOrDeflectSelf(hit);
                hasImpulse = true;
                break;
            }
            if (eh == null || getPierceLevel() <= 0) break;
            hit = null;
        }
        dmx = vx();
        dmy = vy();
        dmz = vz();
        double nx = x() + dmx, ny = y() + dmy, nz = z() + dmz;
        double h = Math.sqrt(dmx * dmx + dmz * dmz);
        if (noPhysics) setYRot((float) (Mth.atan2(-dmx, -dmz) * 180.0F / (float) Math.PI));
        else setYRot((float) (Mth.atan2(dmx, dmz) * 180.0F / (float) Math.PI));
        setXRot((float) (Mth.atan2(dmy, h) * 180.0F / (float) Math.PI));
        setXRot(lerpRotation(xRotO, xRot()));
        setYRot(lerpRotation(yRotO, yRot()));
        float inertia = 0.99F;
        if (isInWater()) inertia = waterInertia();
        setDeltaMovement(vx() * inertia, vy() * inertia, vz() * inertia);
        if (!noPhysics) applyGravity();
        setPos(nx, ny, nz);
        checkInsideBlocks();   // 날아가며 지난 칸: 불·나무 압력판·인계철선·화살 버튼
    }

    protected float waterInertia() { return 0.6F; }

    protected String defaultHitGroundSound() { return "entity.arrow.hit"; }

    /** 스스로가 아닌 이동(피스톤 등)으로 박힌 곳에서 떨어지면 떨어지기 시작한다. */
    @Override
    public void move(int mover, double dx, double dy, double dz) {
        super.move(mover, dx, dy, dz);
        if (mover != MOVER_SELF && shouldFall()) startFalling();
    }

    private boolean shouldFall() {
        return inGround && level.noCollision(boundingBox().inflate(0.06));
    }

    private void startFalling() {
        inGround = false;
        setDeltaMovement(vx() * random.nextFloat() * 0.2F, vy() * random.nextFloat() * 0.2F, vz() * random.nextFloat() * 0.2F);
        life = 0;
    }

    protected void tickDespawn() {
        life++;
        if (life >= 1200) discard();
    }

    @Override
    protected void onHitEntity(Hit hit) {
        Targetable target = hit.target();
        double speed = Math.sqrt(vx() * vx() + vy() * vy() + vz() * vz());
        float fspeed = (float) speed;
        double base = modifyDamage(baseDamage);
        int damage = (int) Math.ceil(Math.clamp(fspeed * base, 0.0, 2.147483647E9));
        if (getPierceLevel() > 0) {
            if (piercedEntities == null) piercedEntities = new java.util.HashSet<>(5);
            if (piercedAndKilled == null) piercedAndKilled = new java.util.ArrayList<>(5);
            if (piercedEntities.size() >= getPierceLevel() + 1) {
                discard();
                return;
            }
            piercedEntities.add(target.id());
        }
        if (isCritArrow()) {
            long extra = random.nextInt(damage / 2 + 2);   // 바닐라: 화살 자신의 엔티티 난수
            damage = (int) Math.min(extra + damage, Integer.MAX_VALUE);
        }
        Targetable owner = getOwner();
        if (owner instanceof LivingEntity le) le.setLastHurtMob(target);
        boolean enderman = target instanceof Entity e && e.type().name().equals("minecraft:enderman");
        int oldFire = target instanceof Entity e ? e.remainingFireTicks() : 0;
        if (isOnFire() && !enderman && target instanceof Entity e) e.igniteForSeconds(5.0F);
        Entity.Damage dmg = new Entity.Damage(owner instanceof Player ? "arrow" : "arrow", false, false, false);
        boolean hurt;
        Mob attacker = owner instanceof Mob m ? m : null;
        if (target instanceof LivingEntity le) hurt = le.hurtByVia(dmg, (float) damage, owner, this);
        else if (target instanceof Player pl) hurt = pl.hurtByVia(dmg, (float) damage, owner, this);   // 막기 방향·관통은 화살로, 주인이 플레이어면 pvp 를 본다
        else hurt = target.hurtByMob(dmg, (float) damage, attacker);
        if (hurt) {
            if (enderman) return;
            if (target instanceof LivingEntity le) {
                doKnockback(le);
                doPostHurtEffects(le);
                if (!le.isAlive() && piercedAndKilled != null) piercedAndKilled.add(le);
                // 바닐라: 쇠뇌로 쏜 화살이 죽였으면 killed_by_crossbow(관통이면 지금까지 죽인 목록, 아니면 이번 대상)
                if (owner instanceof Player op && isShotFromCrossbow()) {
                    if (piercedAndKilled != null) {
                        var victims = AdvTriggers.lootOfAll(piercedAndKilled);
                        op.triggerAdvancement(a -> a.triggerVictims("minecraft:killed_by_crossbow", victims));
                    } else if (!le.isAlive()) {
                        var victims = AdvTriggers.lootOfAll(java.util.List.of(le));
                        op.triggerAdvancement(a -> a.triggerVictims("minecraft:killed_by_crossbow", victims));
                    }
                }
            }
            playSound(soundEvent, 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
            if (getPierceLevel() <= 0) discard();
        } else {
            if (target instanceof Entity e) e.setRemainingFireTicks(oldFire);
            // 막히거나 무적이면 deflect(REVERSE) 후 속도 × 0.2. 거의 멈췄으면 (줍기 가능 시 아이템을 떨구고) 사라진다
            deflectReverse();
            setOwner(getOwner());
            setDeltaMovement(vx() * 0.2, vy() * 0.2, vz() * 0.2);
            if (vx() * vx() + vy() * vy() + vz() * vz() < 1.0E-7) {
                if (pickup == Pickup.ALLOWED) {
                    ItemStack stack = getPickupItem();
                    if (!stack.isEmpty()) {
                        var ie = new ItemEntity(level, level.nextEntityId(), x(), y() + 0.1F, z(), stack);
                        ie.setDefaultPickUpDelay();
                        level.addFreshEntity(ie);
                    }
                }
                discard();
            }
        }
    }

    /** ProjectileDeflection.REVERSE: 회전 170 + nextFloat*20 (엔티티 난수 1회), 속도 × -0.5, hasImpulse. */
    void deflectReverse() {
        float rot = 170.0F + random.nextFloat() * 20.0F;
        setDeltaMovement(vx() * -0.5, vy() * -0.5, vz() * -0.5);
        setYRot(yRot() + rot);
        yRotO += rot;
        hasImpulse = true;
    }

    protected void doPostHurtEffects(LivingEntity target) {}

    @Override
    protected void onHitBlock(Hit hit) {
        lastStateInGround = level.getBlockState(hit.blockPos());
        super.onHitBlock(hit);
        // 박힌 지점으로 속도 방향의 0.05 만큼 뒤로 붙는다
        double len = Math.sqrt(vx() * vx() + vy() * vy() + vz() * vz());
        double ox = hit.x() - x(), oy = hit.y() - y(), oz = hit.z() - z();
        setDeltaMovement(ox, oy, oz);
        double l2 = Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (l2 > 0.0) setPos(x() - ox / l2 * 0.05F, y() - oy / l2 * 0.05F, z() - oz / l2 * 0.05F);
        playSound(soundEvent, 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
        inGround = true;
        shakeTime = 7;
        setCritArrow(false);
        setPierceLevel((byte) 0);
        setSoundEvent(defaultHitGroundSound());
        piercedEntities = null;
    }

    /** AbstractArrow.playerTouch: 땅에 박혔고 흔들림이 끝나면 줍기 규칙에 따라 인벤토리로. */
    @Override
    public void playerTouch(Player player) {
        if ((inGround || isNoPhysics()) && shakeTime <= 0) {
            if (tryPickup(player)) {
                player.take(this, 1);
                discard();
            }
        }
    }

    /** AbstractArrow.tryPickup: 줍기 규칙. */
    protected boolean tryPickup(Player player) {
        return switch (pickup) {
            case DISALLOWED -> false;
            case ALLOWED -> player.inventory().add(getPickupItem(), false, 64, false).isEmpty();
            case CREATIVE_ONLY -> player.hasInfiniteMaterials();
        };
    }

    protected abstract ItemStack getDefaultPickupItem();

    /** 바닐라는 pickupItemStack.copy(). ItemStack 이 불변 record(ComponentPatch 도 불변)라 그대로 돌려줘도 같다. */
    protected ItemStack getPickupItem() { return pickupItem.isEmpty() ? getDefaultPickupItem() : pickupItem; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putShort("life", (short) life);
        tag.putByte("shake", (byte) shakeTime);
        tag.putBoolean("inGround", inGround);
        tag.putByte("pickup", (byte) pickup.ordinal());
        tag.put("damage", baseDamage);
        tag.putBoolean("crit", isCritArrow());
        tag.putByte("PierceLevel", getPierceLevel());
        tag.putString("SoundEvent", "minecraft:" + soundEvent);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        life = tag.getIntOr0("life");
        shakeTime = tag.getIntOr0("shake") & 255;
        inGround = tag.getBoolean("inGround");
        if (tag.contains("damage")) baseDamage = ((Number) tag.tags().get("damage")).doubleValue();
        int p = tag.getIntOr0("pickup");
        pickup = Pickup.values()[Math.max(0, Math.min(p, 2))];
        setCritArrow(tag.getBoolean("crit"));
        setPierceLevel((byte) tag.getIntOr0("PierceLevel"));
        if (tag.tags().get("SoundEvent") instanceof String s) soundEvent = s.replace("minecraft:", "");
    }

    @Override public boolean isPickable() { return !inGround; }
}
