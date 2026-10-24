// 바닐라 ThrowableProjectile / ThrowableItemProjectile: 중력 0.03·공기 0.99(물 0.8)로 날아가다 맞으면 onHit. 던진 아이템을 데이터(8)로 가진다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class ThrowableProjectile extends Projectile {
    public static final int DATA_ITEM_STACK = 8;

    protected ThrowableProjectile(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    protected abstract String defaultItem();

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ITEM_STACK, SetEntityData.SERIALIZER_ITEM,
                new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", defaultItem().replace("minecraft:", "")), 1));
    }

    public void setItem(ItemStack stack) { entityData.set(DATA_ITEM_STACK, stack.withCount(1)); }

    public ItemStack getItem() { return (ItemStack) entityData.get(DATA_ITEM_STACK); }

    /** ThrowableProjectile(type, owner, level): 소유자의 눈높이 - 0.1 위치에서 시작. */
    public void startAt(Targetable owner) {
        setPos(owner.x(), owner.eyeY() - 0.1F, owner.z());
        setOwner(owner);
    }

    @Override protected double defaultGravity() { return 0.03; }

    @Override
    public void tick() {
        super.tick();
        Hit hit = findHit(x(), y(), z(), x() + vx(), y() + vy(), z() + vz());
        if (hit != null) onHit(hit);
        double dx = vx(), dy = vy(), dz = vz();
        double nx = x() + dx, ny = y() + dy, nz = z() + dz;
        double h = Math.sqrt(dx * dx + dz * dz);
        setYRot(lerpRotation(yRotO, (float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(dx, dz) * 180.0F / (float) Math.PI)));
        setXRot(lerpRotation(xRotO, (float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(dy, h) * 180.0F / (float) Math.PI)));
        float inertia = isInWater() ? 0.8F : 0.99F;
        setDeltaMovement(dx * inertia, dy * inertia, dz * inertia);
        applyGravity();
        setPos(nx, ny, nz);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putCompound("Item", ItemStackNbt.write(getItem()));
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("Item") instanceof NbtCompound c) {
            ItemStack s = ItemStackNbt.read(c);
            if (!s.isEmpty()) setItem(s);
        }
    }
}
