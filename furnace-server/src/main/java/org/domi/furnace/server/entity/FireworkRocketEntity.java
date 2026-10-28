// 바닐라 FireworkRocketEntity: 폭죽 로켓. 위로 날다 수명(10 × 비행 시간 + 무작위)이 다하거나 무언가에 맞으면 터지고, 폭발 장식이 있으면 5 칸 안 생물에게 피해를 준다.
// TODO: 겉날개 비행 중 플레이어에 붙은 로켓(attached) 가속, 폭발 장식 입자는 클라이언트가 이벤트 17 로 그린다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class FireworkRocketEntity extends Projectile {
    public static final int DATA_ID_FIREWORKS_ITEM = 8, DATA_ATTACHED_TO_TARGET = 9, DATA_SHOT_AT_ANGLE = 10;
    private int life, lifetime;

    public FireworkRocketEntity(ChunkLevel level, int id) { super(EntityType.FIREWORK_ROCKET, level, id); }

    public FireworkRocketEntity(ChunkLevel level, int id, double x, double y, double z, ItemStack stack) {
        this(level, id);
        setPos(x, y, z);
        entityData.set(DATA_ID_FIREWORKS_ITEM, stack.withCount(1));
        int flight = 1;
        if (ItemTools.component(stack, "minecraft:fireworks") instanceof Map<?, ?> m && m.get("flight_duration") instanceof Number n) flight += n.intValue();
        setDeltaMovement(random.triangle(0.0, 0.002297), 0.05, random.triangle(0.0, 0.002297));
        lifetime = 10 * flight + random.nextInt(6) + random.nextInt(7);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_FIREWORKS_ITEM, SetEntityData.SERIALIZER_ITEM, defaultItem());
        entityData.define(DATA_ATTACHED_TO_TARGET, SetEntityData.SERIALIZER_OPTIONAL_INT, null);
        entityData.define(DATA_SHOT_AT_ANGLE, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    private static ItemStack defaultItem() { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:firework_rocket"), 1); }

    public ItemStack getItem() { return (ItemStack) entityData.get(DATA_ID_FIREWORKS_ITEM); }

    public void setShotAtAngle(boolean b) { entityData.set(DATA_SHOT_AT_ANGLE, b); }

    public boolean isShotAtAngle() { return (Boolean) entityData.get(DATA_SHOT_AT_ANGLE); }

    @Override public boolean isPickable() { return false; }

    private List<?> explosions() {
        return ItemTools.component(getItem(), "minecraft:fireworks") instanceof Map<?, ?> m && m.get("explosions") instanceof List<?> l ? l : List.of();
    }

    @Override
    public void tick() {
        super.tick();
        if (!isShotAtAngle()) {
            double d = horizontalCollision ? 1.0 : 1.15;
            setDeltaMovement(vx() * d, vy(), vz() * d);
            setDeltaMovement(vx(), vy() + 0.04, vz());
        }
        double dx = vx(), dy = vy(), dz = vz();
        move(MOVER_SELF, dx, dy, dz);
        setDeltaMovement(dx, dy, dz);
        if (!noPhysics) {
            Hit hit = findHit(x(), y(), z(), x() + vx(), y() + vy(), z() + vz());
            if (hit != null) {
                onHit(hit);
                hasImpulse = true;
            }
        }
        double h = Math.sqrt(vx() * vx() + vz() * vz());
        setYRot(lerpRotation(yRotO, (float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(vx(), vz()) * 180.0F / (float) Math.PI)));
        setXRot(lerpRotation(xRotO, (float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(vy(), h) * 180.0F / (float) Math.PI)));
        if (life == 0 && !isSilent()) level.playSound(null, x(), y(), z(), "minecraft:entity.firework_rocket.launch", "ambient", 3.0F, 1.0F);
        life++;
        if (life > lifetime) explode();
    }

    private void explode() {
        broadcastEntityEvent((byte) 17);
        dealExplosionDamage();
        discard();
    }

    @Override
    protected void onHitEntity(Hit hit) {
        super.onHitEntity(hit);
        explode();
    }

    @Override
    protected void onHitBlock(Hit hit) {
        int state = level.getBlockState(hit.blockPos());
        org.domi.furnace.server.block.B.behavior(state).entityInside(state, level, hit.blockPos(), this);
        if (!explosions().isEmpty()) explode();
        super.onHitBlock(hit);
    }

    private void dealExplosionDamage() {
        List<?> ex = explosions();
        float amount = ex.isEmpty() ? 0.0F : 5.0F + ex.size() * 2;
        if (amount <= 0.0F) return;
        Damage src = new Damage("fireworks", false, true, false);
        AABB box = boundingBox().inflate(5.0);
        for (Entity e : level.getEntities(this, box, en -> en instanceof LivingEntity)) {
            double d = (e.x() - x()) * (e.x() - x()) + (e.y() - y()) * (e.y() - y()) + (e.z() - z()) * (e.z() - z());
            if (d > 25.0 || !canSee(e.x(), e.y(), e.z()) && !canSee(e.x(), e.y() + 0.5 * e.bbHeight(), e.z())) continue;
            ((LivingEntity) e).hurtByVia(src, amount * (float) Math.sqrt((5.0 - Math.sqrt(d)) / 5.0), getOwner(), this);
        }
    }

    private boolean canSee(double tx, double ty, double tz) { return org.domi.furnace.server.block.Clip.clipCollider(level, x(), y(), z(), tx, ty, tz).miss(); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Life", life);
        tag.putInt("LifeTime", lifetime);
        tag.putCompound("FireworksItem", ItemStackNbt.write(getItem()));
        tag.putBoolean("ShotAtAngle", isShotAtAngle());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        life = tag.getIntOr0("Life");
        lifetime = tag.getIntOr0("LifeTime");
        entityData.set(DATA_ID_FIREWORKS_ITEM, tag.tags().get("FireworksItem") instanceof NbtCompound c ? ItemStackNbt.read(c) : defaultItem());
        if (tag.contains("ShotAtAngle")) setShotAtAngle(tag.getBoolean("ShotAtAngle"));
    }

    /** 폭죽은 속도가 아니라 대상 위치 − 폭죽 위치. */
    @Override
    public double[] hurtKnockbackDirection(Targetable target) { return new double[] {target.x() - x(), target.z() - z()}; }
}
