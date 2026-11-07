// 바닐라 PrimedTnt: 점화된 TNT. 중력 0.04·공기 0.98(땅에서 0.7)로 움직이고, 도화선(기본 80틱)이 다 타면 반경 4 폭발(TNT 규칙).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class PrimedTnt extends Entity {
    public static final int DATA_FUSE_ID = 8, DATA_BLOCK_STATE_ID = 9;
    private Targetable owner;
    private java.util.UUID ownerUuid;
    private float explosionPower = 4.0F;

    public PrimedTnt(ChunkLevel level, int id) {
        super(EntityType.TNT, level, id);
    }

    public PrimedTnt(ChunkLevel level, int id, double x, double y, double z, Targetable owner) {
        this(level, id);
        setPos(x, y, z);
        double d0 = level.random().nextDouble() * (float) (Math.PI * 2);
        setDeltaMovement(-Math.sin(d0) * 0.02, 0.2F, -Math.cos(d0) * 0.02);
        setFuse(80);
        xo = x;
        yo = y;
        zo = z;
        this.owner = owner;
        if (owner != null) ownerUuid = NeutralMob.uuidOf(owner);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_FUSE_ID, SetEntityData.SERIALIZER_INT, 80);
        entityData.define(DATA_BLOCK_STATE_ID, SetEntityData.SERIALIZER_BLOCK_STATE, org.domi.furnace.server.block.B.defaultState("tnt"));
    }

    public int getFuse() { return (Integer) entityData.get(DATA_FUSE_ID); }

    public void setFuse(int fuse) { entityData.set(DATA_FUSE_ID, fuse); }

    public Targetable getOwner() {
        if (owner != null && owner.isAlive()) return owner;
        if (ownerUuid != null) owner = level.getTargetableByUuid(ownerUuid);
        return owner;
    }

    /** 원인 사슬(적힌 것이 없을 때): [켠 생물 ignite] — 바닐라 주인만 안다. */
    @Override
    protected org.domi.furnace.api.event.cause.CauseChain derivedOrigin() {
        return owner == null ? org.domi.furnace.api.event.cause.CauseChain.NONE
                : org.domi.furnace.server.plugin.Causes.of(owner, org.domi.furnace.api.event.cause.CauseEntry.IGNITE);
    }

    @Override protected double defaultGravity() { return 0.04; }

    @Override public boolean fireImmune() { return true; }

    @Override public boolean isPickable() { return !isRemoved(); }

    @Override public double eyeYForExplosion() { return y(); }

    @Override
    public void tick() {
        applyGravity();
        move(MOVER_SELF, vx(), vy(), vz());
        setDeltaMovement(vx() * 0.98, vy() * 0.98, vz() * 0.98);
        if (onGround()) setDeltaMovement(vx() * 0.7, vy() * -0.5, vz() * 0.7);
        int i = getFuse() - 1;
        setFuse(i);
        if (i <= 0) {
            discard();
            explode();
        } else {
            updateInWaterStateAndDoFluidPushing();
        }
    }

    private void explode() {
        level.explode(this, x(), y() + bbHeight() / 16.0F, z(), explosionPower, false, level.destroyType("TNT"));
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putShort("fuse", (short) getFuse());
        if (explosionPower != 4.0F) tag.putFloat("explosion_power", explosionPower);
        if (ownerUuid != null) tag.putIntArray("owner", uuidToInts(ownerUuid));
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        setFuse(tag.getIntOr0("fuse") == 0 && !tag.contains("fuse") ? 80 : tag.getIntOr0("fuse"));
        if (tag.tags().get("explosion_power") instanceof Number n && n.floatValue() >= 0.0F && n.floatValue() <= 128.0F) explosionPower = n.floatValue();
        if (tag.tags().get("owner") instanceof int[] u && u.length == 4) ownerUuid = new java.util.UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
    }
}
