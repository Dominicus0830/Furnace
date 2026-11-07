// 바닐라 Bat: 낮에는 천장에 매달려 쉬고(resting), 플레이어가 4칸 안에 오거나 받침이 사라지면 날아오른다. 날 때는 무작위 목표 칸으로 흔들리며 난다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Bat extends PathfinderMob {

    /** 바닐라는 낙하 처리를 비운다(날아다니는 몹은 착지 입자·낙하 거리·HIT_GROUND 가 없다). */
    @Override
    protected void checkFallDamage(double dy, boolean ground, int state, org.domi.furnace.server.block.Pos pos) {}
    /** canBeLeashed(디컴파일 y): 박쥐(cey)는 못 맨다. */
    @Override
    public boolean canBeLeashed() { return false; }


    /** Bat.isPushable = false, pushEntities 는 비어 있다. */
    @Override public boolean isPushable() { return false; }

    @Override protected void pushEntities() {}

    public static final int DATA_ID_FLAGS = 16;
    private static final TargetingConditions BAT_RESTING_TARGETING = TargetingConditions.forNonCombat().range(4.0);
    private Pos targetPosition;

    public Bat(ChunkLevel level, int id) {
        super(EntityType.BAT, level, id);
        setResting(true);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 6.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    @Override
    protected float getSoundVolume() { return 0.1F; }

    @Override
    public float getVoicePitch() { return super.getVoicePitch() * 0.95F; }

    @Override
    public String getAmbientSound() { return isResting() && random.nextInt(4) != 0 ? null : "entity.bat.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.bat.hurt"; }

    @Override protected String getDeathSound() { return "entity.bat.death"; }

    public boolean isResting() { return ((Byte) entityData.get(DATA_ID_FLAGS) & 1) != 0; }

    public void setResting(boolean resting) {
        byte b = (Byte) entityData.get(DATA_ID_FLAGS);
        entityData.set(DATA_ID_FLAGS, (byte) (resting ? b | 1 : b & -2));
    }

    @Override
    public void tick() {
        super.tick();
        if (isResting()) {
            setDeltaMovement(0.0, 0.0, 0.0);
            setPosRaw(x(), Math.floor(y()) + 1.0 - bbHeight(), z());
        } else {
            setDeltaMovement(vx(), vy() * 0.6, vz());
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        Pos pos = blockPosition();
        Pos above = pos.above();
        if (isResting()) {
            boolean silent = isSilent();
            if (isRedstoneConductor(above, pos)) {
                if (random.nextInt(200) == 0) yHeadRot = random.nextInt(360);
                if (level.getNearestPlayer(BAT_RESTING_TARGETING, this, x(), y(), z()) != null) {
                    setResting(false);
                    if (!silent) level.levelEvent(null, 1025, pos, 0);
                }
            } else {
                setResting(false);
                if (!silent) level.levelEvent(null, 1025, pos, 0);
            }
        } else {
            if (targetPosition != null && (!level.isEmptyBlock(targetPosition) || targetPosition.y() <= level.minBuildHeight())) targetPosition = null;
            if (targetPosition == null || random.nextInt(30) == 0 || closerToCenter(targetPosition, 2.0)) {
                int dx = random.nextInt(7) - random.nextInt(7);
                double dy = random.nextInt(6) - 2.0;
                int dz = random.nextInt(7) - random.nextInt(7);
                targetPosition = new Pos((int) Math.floor(x() + dx), (int) Math.floor(y() + dy), (int) Math.floor(z() + dz));
            }
            double d = targetPosition.x() + 0.5 - x();
            double e = targetPosition.y() + 0.1 - y();
            double f = targetPosition.z() + 0.5 - z();
            double mx = vx() + (Math.signum(d) * 0.5 - vx()) * 0.1F;
            double my = vy() + (Math.signum(e) * 0.7F - vy()) * 0.1F;
            double mz = vz() + (Math.signum(f) * 0.5 - vz()) * 0.1F;
            setDeltaMovement(mx, my, mz);
            float g = (float) (Mth.atan2(mz, mx) * 180.0F / (float) Math.PI) - 90.0F;
            float h = Mth.wrapDegrees(g - yRot());
            zza = 0.5F;
            setYRot(yRot() + h);
            if (random.nextInt(100) == 0 && isRedstoneConductor(above, above)) setResting(true);
        }
    }

    private boolean isRedstoneConductor(Pos blockPos, Pos contextPos) {
        int s = level.getBlockState(blockPos);
        return B.behavior(s).isRedstoneConductor(s, level, contextPos);
    }

    private boolean closerToCenter(Pos p, double dist) {
        double dx = p.x() + 0.5 - x(), dy = p.y() + 0.5 - y(), dz = p.z() + 0.5 - z();
        return dx * dx + dy * dy + dz * dz < dist * dist;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, Damage source) { return false; }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        if (isResting()) setResting(false);
        return super.hurtBy(source, amount, attacker);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("BatFlags", (Byte) entityData.get(DATA_ID_FLAGS));
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_ID_FLAGS, tag.contains("BatFlags") ? tag.getByte("BatFlags") : (byte) 0);
    }
}
