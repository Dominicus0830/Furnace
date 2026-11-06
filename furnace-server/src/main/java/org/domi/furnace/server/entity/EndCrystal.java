// 바닐라 EndCrystal: 엔드의 수정. 맞으면 사라지며 위력 6 으로 터진다(폭발에 맞은 경우는 그냥 사라진다). 광선 대상 블록과 아래 받침 표시를 데이터로 가진다.
// 엔더 드래곤의 공격은 무시하고, 전투 중이면 제자리가 비었을 때 불을 붙이며, 파괴를 전투에 알린다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class EndCrystal extends Entity {
    public static final int DATA_BEAM_TARGET = 8, DATA_SHOW_BOTTOM = 9;
    public int time;

    public EndCrystal(ChunkLevel level, int id) {
        super(EntityType.END_CRYSTAL, level, id);
        time = random.nextInt(100000);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_BEAM_TARGET, SetEntityData.SERIALIZER_OPTIONAL_BLOCK_POS, null);
        entityData.define(DATA_SHOW_BOTTOM, SetEntityData.SERIALIZER_BOOLEAN, true);
    }

    @Override
    public void tick() {
        time++;
        checkInsideBlocks();
        Pos p = blockPosition();
        if (level.dragonFight() != null && org.domi.furnace.server.block.B.isAir(level.getBlockState(p))) {
            level.setBlock(p, org.domi.furnace.server.block.types.BaseFireBlock.getState(level, p), 3);
        }
    }

    @Override
    public boolean hurt(Damage source, float amount) { return hurtBy(source, amount, null); }

    /** 맞으면(드래곤에게는 아님) 사라지고, 폭발 피해가 아니면 위력 6 으로 터진 뒤 전투에 알린다. */
    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        if (attacker instanceof EnderDragon) return false;
        if (!isRemoved()) {
            remove(RemovalReason.KILLED);
            if (!source.explosion()) level.explode(this, x(), y(), z(), 6.0F, false, level.destroyType("BLOCK"));
            onDestroyedBy(source, attacker);
        }
        return true;
    }

    private void onDestroyedBy(Damage source, Targetable attacker) {
        EndDragonFight f = level.dragonFight();
        if (f != null) f.onCrystalDestroyed(this, source, attacker);
    }

    @Override
    public void kill() {
        onDestroyedBy(new Damage("generic", false, false, false), null);
        super.kill();
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        Pos target = getBeamTarget();
        if (target != null) tag.putIntArray("beam_target", new int[] {target.x(), target.y(), target.z()});
        tag.putBoolean("ShowBottom", showsBottom());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        if (tag.tags().get("beam_target") instanceof int[] p && p.length == 3) setBeamTarget(new Pos(p[0], p[1], p[2]));
        if (tag.contains("ShowBottom")) setShowBottom(tag.getBoolean("ShowBottom"));
    }

    public void setBeamTarget(Pos pos) { entityData.set(DATA_BEAM_TARGET, pos == null ? null : new int[] {pos.x(), pos.y(), pos.z()}); }

    public Pos getBeamTarget() {
        int[] p = (int[]) entityData.get(DATA_BEAM_TARGET);
        return p == null ? null : new Pos(p[0], p[1], p[2]);
    }

    public void setShowBottom(boolean v) { entityData.set(DATA_SHOW_BOTTOM, v); }

    public boolean showsBottom() { return (Boolean) entityData.get(DATA_SHOW_BOTTOM); }

    @Override public boolean isPickable() { return true; }
}
