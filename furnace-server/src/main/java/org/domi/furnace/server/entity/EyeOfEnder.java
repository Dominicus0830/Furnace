// 바닐라 EyeOfEnder: 던지면 목표(요새) 방향으로 12칸 안까지 떠올라 80틱 뒤 떨어지는 엔더의 눈. 5분의 4는 아이템으로 떨어지고 나머지는 깨진다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class EyeOfEnder extends Entity {
    public static final int DATA_ITEM_STACK = 8;
    private double tx, ty, tz;
    private int life;
    private boolean surviveAfterDeath;

    public EyeOfEnder(ChunkLevel level, int id) { super(EntityType.EYE_OF_ENDER, level, id); }

    public EyeOfEnder(ChunkLevel level, int id, double x, double y, double z) {
        this(level, id);
        setPos(x, y, z);
    }

    private static ItemStack defaultItem() { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:ender_eye"), 1); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ITEM_STACK, SetEntityData.SERIALIZER_ITEM, defaultItem());
    }

    public void setItem(ItemStack stack) { entityData.set(DATA_ITEM_STACK, stack.isEmpty() ? defaultItem() : stack.withCount(1)); }

    public ItemStack getItem() { return (ItemStack) entityData.get(DATA_ITEM_STACK); }

    /** 날아갈 목표(signalTo 가 정한 값) — 시험·관측용. */
    public double targetX() { return tx; }

    public double targetY() { return ty; }

    public double targetZ() { return tz; }

    public int life() { return life; }

    /** signalTo(pos): 12칸보다 멀면 그 방향 12칸 + 위 8칸을 향하고, 아니면 그 자리를 향한다. */
    public void signalTo(Pos pos) {
        double dx = pos.x() - x(), dz = pos.z() - z();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 12.0) {
            tx = x() + dx / len * 12.0;
            tz = z() + dz / len * 12.0;
            ty = y() + 8.0;
        } else {
            tx = pos.x();
            ty = pos.y();
            tz = pos.z();
        }
        life = 0;
        surviveAfterDeath = random.nextInt(5) > 0;
    }

    @Override
    public void tick() {
        super.tick();
        double mx = vx(), my = vy(), mz = vz();
        double nx = x() + mx, ny = y() + my, nz = z() + mz;
        double h = Math.sqrt(mx * mx + mz * mz);
        setXRot(Projectile.lerpRotation(xRotO, (float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(my, h) * 180.0F / (float) Math.PI)));
        setYRot(Projectile.lerpRotation(yRotO, (float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(mx, mz) * 180.0F / (float) Math.PI)));
        double ddx = tx - nx, ddz = tz - nz;
        float dist = (float) Math.sqrt(ddx * ddx + ddz * ddz);
        float ang = (float) org.domi.furnace.server.entity.ai.control.Mth.atan2(ddz, ddx);
        double speed = h + (dist - h) * 0.0025;
        double vyy = my;
        if (dist < 1.0F) {
            speed *= 0.8;
            vyy *= 0.8;
        }
        int dir = y() < ty ? 1 : -1;
        setDeltaMovement(Math.cos(ang) * speed, vyy + (dir - vyy) * 0.015F, Math.sin(ang) * speed);
        // 바닐라는 서버에서도 입자 호출의 인자(random.nextDouble() 두 번)를 평가한다: 물 밖이면 틱마다 엔티티 난수 두 번 소모
        if (!isInWater()) {
            random.nextDouble();
            random.nextDouble();
        }
        setPos(nx, ny, nz);
        life++;
        if (life > 80) {
            playSound("minecraft:entity.ender_eye.death", 1.0F, 1.0F);
            discard();
            if (surviveAfterDeath) {
                var ie = new ItemEntity(level, level.nextEntityId(), x(), y(), z(), getItem());
                level.addFreshEntity(ie);
            } else {
                level.levelEvent(null, 2003, blockPosition(), 0);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) { tag.putCompound("Item", ItemStackNbt.write(getItem())); }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        setItem(tag.tags().get("Item") instanceof NbtCompound c ? ItemStackNbt.read(c) : defaultItem());
    }
}
