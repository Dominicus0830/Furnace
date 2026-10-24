// 바닐라 AbstractArrow: 날아가며 중력 0.05·공기 0.99(물 0.6)로 줄고, 블록에 박히면 1200틱 뒤 사라지며, 엔티티를 맞추면 속력 × 기본 피해(2)만큼 때린다.
// TODO: 인챈트(관통·밀치기·불·무한), 화살 박힘 표시(arrow count), 관통 시 맞은 대상 기록, 플레이어가 쏜 화살의 줍기 규칙.
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
    protected ItemStack pickupItem = ItemStack.EMPTY;

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
                onHit(hit);
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
    }

    protected float waterInertia() { return 0.6F; }

    protected String defaultHitGroundSound() { return "entity.arrow.hit"; }

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
        int damage = (int) Math.ceil(Math.clamp(speed * baseDamage, 0.0, 2.147483647E9));
        if (getPierceLevel() > 0) {
            if (piercedEntities == null) piercedEntities = new java.util.HashSet<>(5);
            if (piercedEntities.size() >= getPierceLevel() + 1) {
                discard();
                return;
            }
            piercedEntities.add(target.id());
        }
        if (isCritArrow()) {
            long extra = level.random().nextInt(damage / 2 + 2);
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
        else hurt = target.hurtByMob(dmg, (float) damage, attacker);
        if (hurt) {
            if (enderman) return;
            if (target instanceof LivingEntity le) {
                doPostHurtEffects(le);
            }
            playSound(soundEvent, 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
            if (getPierceLevel() <= 0) discard();
        } else {
            if (target instanceof Entity e) e.setRemainingFireTicks(oldFire);
            // 막히거나 무적이면 튕겨 나간다: 속도 × -0.1, 회전 +180~
            setDeltaMovement(vx() * -0.1, vy() * -0.1, vz() * -0.1);
            setYRot(yRot() + 180.0F);
            yRotO += 180.0F;
            if (vx() * vx() + vy() * vy() + vz() * vz() < 1.0E-7) discard();
        }
    }

    protected void doPostHurtEffects(LivingEntity target) {}

    @Override
    protected void onHitBlock(Hit hit) {
        lastStateInGround = level.getBlockState(hit.blockPos());
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
            boolean ok = switch (pickup) {
                case DISALLOWED -> false;
                case ALLOWED -> player.inventory().add(getPickupItem(), false, 64, false).isEmpty();
                case CREATIVE_ONLY -> player.hasInfiniteMaterials();
            };
            if (ok) {
                player.take(this, 1);
                discard();
            }
        }
    }

    protected abstract ItemStack getDefaultPickupItem();

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
