// 바닐라 MinecartFurnace: 석탄·숯 하나로 3600 틱(최대 32000) 연료. 연료가 있으면 플레이어 반대쪽으로 민다(PushX·PushZ).
// 최고 속도 4(물속 3) 블록/초. 연기 입자 난수(4 분의 1)는 서버에서도 쓴다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class MinecartFurnace extends AbstractMinecart {

    /** 14: 연료 있음(불 켜진 화로 표시). */
    public static final int DATA_ID_FUEL = 14;

    private int fuel;
    public double xPush, zPush;

    public MinecartFurnace(ChunkLevel level, int id) { super(EntityType.FURNACE_MINECART, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_FUEL, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override public Type getMinecartType() { return Type.FURNACE; }

    @Override protected String dropItem() { return "minecraft:furnace_minecart"; }

    public int fuel() { return fuel; }

    public void setFuel(int v) { fuel = v; }

    protected boolean hasFuel() { return (Boolean) entityData.get(DATA_ID_FUEL); }

    protected void setHasFuel(boolean v) { entityData.set(DATA_ID_FUEL, v); }

    @Override
    public void tick() {
        super.tick();
        if (fuel > 0) fuel--;
        if (fuel <= 0) {
            xPush = 0.0;
            zPush = 0.0;
        }
        setHasFuel(fuel > 0);
        if (hasFuel() && random.nextInt(4) == 0) {
            // addParticle(LARGE_SMOKE) — 서버에서는 보이지 않지만 난수는 쓴다
        }
    }

    @Override
    protected double getMaxSpeed() { return (isInWater() ? 3.0 : 4.0) / 20.0; }

    /** moveAlongTrack: 그 뒤 미는 방향을 지금 속도 방향으로 돌린다(세기는 그대로). */
    @Override
    protected void moveAlongTrack(Pos pos, int state) {
        super.moveAlongTrack(pos, state);
        double v = vx() * vx() + vz() * vz();
        double p = xPush * xPush + zPush * zPush;
        if (p > 1.0E-4 && v > 0.001) {
            double a = Math.sqrt(v), b = Math.sqrt(p);
            xPush = vx() / a * b;
            zPush = vz() / a * b;
        }
    }

    /** applyNaturalSlowdown: 미는 중이면 0.8 배 + 미는 방향 단위 벡터(물속 0.1 배), 아니면 0.98 배 — 그 뒤 기본 감속. */
    @Override
    protected void applyNaturalSlowdown() {
        double d = xPush * xPush + zPush * zPush;
        if (d > 1.0E-7) {
            d = Math.sqrt(d);
            xPush /= d;
            zPush /= d;
            double x = vx() * 0.8 + xPush, y = vy() * 0.0, z = vz() * 0.8 + zPush;
            if (isInWater()) {
                x *= 0.1;
                y *= 0.1;
                z *= 0.1;
            }
            setDeltaMovement(x, y, z);
        } else {
            setDeltaMovement(vx() * 0.98, vy() * 0.0, vz() * 0.98);
        }
        super.applyNaturalSlowdown();
    }

    /** interact: 석탄·숯이면 하나 써서(크리에이티브 제외) 연료 +3600, 연료가 있으면 플레이어 반대쪽으로 민다. */
    public boolean interact(Player p, int hand) {
        var inv = p.inventory();
        int slot = hand == 0 ? inv.heldWindowSlot() : 45;
        ItemStack s = inv.get(slot);
        if ((B.holds(s, "coal") || B.holds(s, "charcoal")) && fuel + 3600 <= 32000) {
            if (!p.hasInfiniteMaterials()) inv.set(slot, s.withCount(s.count() - 1));
            fuel += 3600;
        }
        if (fuel > 0) {
            xPush = x() - p.x();
            zPush = z() - p.z();
        }
        return true;
    }

    @Override
    public int getDefaultDisplayBlockState() {
        int s = B.prop("facing").with(B.defaultState("furnace"), "north");
        return B.prop("lit").with(s, hasFuel());
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("PushX", xPush);
        tag.putDouble("PushZ", zPush);
        tag.putShort("Fuel", (short) fuel);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        xPush = tag.getDouble("PushX");
        zPush = tag.getDouble("PushZ");
        fuel = tag.getShort("Fuel");
    }
}
