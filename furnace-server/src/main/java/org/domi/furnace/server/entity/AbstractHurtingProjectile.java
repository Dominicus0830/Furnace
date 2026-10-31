// 바닐라 AbstractHurtingProjectile: 가속도(accelerationPower)만큼 같은 방향으로 계속 빨라지는 투사체(불덩이·해골 머리). 중력은 받지 않고 불이 붙어 날아간다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public abstract class AbstractHurtingProjectile extends Projectile {
    public static final double INITIAL_ACCELERATION_POWER = 0.1;
    public double accelerationPower = INITIAL_ACCELERATION_POWER;

    protected AbstractHurtingProjectile(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    /** (x, y, z)에서 movement 방향으로 accelerationPower 의 속도로 출발. */
    public void launch(double x, double y, double z, double mx, double my, double mz) {
        moveTo(x, y, z, yRot(), xRot());
        assignDirectionalMovement(mx, my, mz, accelerationPower);
    }

    /** 소유자 위치에서 출발하고 소유자의 방향을 따른다. */
    public void launchFrom(Targetable owner, double mx, double my, double mz) {
        launch(owner.x(), owner.y(), owner.z(), mx, my, mz);
        setOwner(owner);
        if (owner instanceof Entity e) {
            setYRot(e.yRot());
            setXRot(e.xRot());
        }
    }

    private void assignDirectionalMovement(double mx, double my, double mz, double accel) {
        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        if (len < 1.0E-5F) setDeltaMovement(0.0, 0.0, 0.0);
        else setDeltaMovement(mx / len * accel, my / len * accel, mz / len * accel);
        hasImpulse = true;
    }

    protected boolean shouldBurn() { return true; }

    protected float getInertia() { return 0.95F; }

    protected float getLiquidInertia() { return 0.8F; }

    @Override
    public void tick() {
        Targetable owner = getOwner();
        if ((owner == null || owner.isAlive()) && level.hasChunkAt(blockPosition())) {
            super.tick();
            if (shouldBurn()) setRemainingFireTicks(1);
            Hit hit = findHit(x(), y(), z(), x() + vx(), y() + vy(), z() + vz());
            if (hit != null) onHit(hit);
            double dx = vx(), dy = vy(), dz = vz();
            double nx = x() + dx, ny = y() + dy, nz = z() + dz;
            double h = Math.sqrt(dx * dx + dz * dz);
            setYRot(lerpRotation(yRotO, (float) (Mth.atan2(dx, dz) * 180.0F / (float) Math.PI)));
            setXRot(lerpRotation(xRotO, (float) (Mth.atan2(dy, h) * 180.0F / (float) Math.PI)));
            float inertia = isInWater() ? getLiquidInertia() : getInertia();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double ux = len < 1.0E-5F ? 0.0 : dx / len, uy = len < 1.0E-5F ? 0.0 : dy / len, uz = len < 1.0E-5F ? 0.0 : dz / len;
            setDeltaMovement((dx + ux * accelerationPower) * inertia, (dy + uy * accelerationPower) * inertia, (dz + uz * accelerationPower) * inertia);
            setPos(nx, ny, nz);
        } else {
            discard();
        }
    }

    /** cne.onDeflection: 쳐서 튕기면 가속을 처음 값으로, 그 밖에는 반으로. */
    @Override
    protected void onDeflection(boolean byAttack) {
        if (byAttack) accelerationPower = INITIAL_ACCELERATION_POWER;
        else accelerationPower *= 0.5;
    }

    /** cne.hurt: 무적이 아니면 맞은 것으로 친다(아무 일도 없다). */
    @Override
    public boolean hurt(Damage source, float amount) { return !isInvulnerableTo(source); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.put("acceleration_power", accelerationPower);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("acceleration_power") instanceof Number n) accelerationPower = n.doubleValue();
    }
}
