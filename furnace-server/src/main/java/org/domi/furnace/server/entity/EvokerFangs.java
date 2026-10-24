// 바닐라 EvokerFangs: 소환사가 땅에서 솟게 하는 송곳니. 준비(warmup) 뒤 근처 생물에게 6 피해(소환사의 동맹은 제외)를 주고 22틱 뒤 사라진다.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class EvokerFangs extends Entity {
    private int warmupDelayTicks;
    private boolean sentSpikeEvent;
    private int lifeTicks = 22;
    private LivingEntity owner;
    private UUID ownerUUID;

    public EvokerFangs(ChunkLevel level, int id) { super(EntityType.EVOKER_FANGS, level, id); }

    public void init(double x, double y, double z, float yawRad, int warmup, LivingEntity owner) {
        this.warmupDelayTicks = warmup;
        setOwner(owner);
        setYRot(yawRad * (180.0F / (float) Math.PI));
        setPos(x, y, z);
    }

    @Override protected void defineSynchedData() { }

    public void setOwner(LivingEntity o) {
        owner = o;
        ownerUUID = o == null ? null : o.uuid();
    }

    public LivingEntity getOwner() {
        if (owner == null && ownerUUID != null && level.getTargetableByUuid(ownerUUID) instanceof LivingEntity le) owner = le;
        return owner;
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        warmupDelayTicks = tag.getIntOr0("Warmup");
        owner = null;
        ownerUUID = tag.tags().get("Owner") instanceof int[] u && u.length == 4
                ? new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL) : null;
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putInt("Warmup", warmupDelayTicks);
        if (ownerUUID != null) {
            long m = ownerUUID.getMostSignificantBits(), l = ownerUUID.getLeastSignificantBits();
            tag.putIntArray("Owner", new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l});
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (--warmupDelayTicks < 0) {
            if (warmupDelayTicks == -8) {
                AABB box = boundingBox().inflate(0.2, 0.0, 0.2);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> true)) dealDamageTo(e);
                level.forEachPlayerIn(box, this::dealDamageTo);
            }
            if (!sentSpikeEvent) {
                broadcastEntityEvent((byte) 4);
                sentSpikeEvent = true;
            }
            if (--lifeTicks < 0) discard();
        }
    }

    private void dealDamageTo(Targetable target) {
        LivingEntity owner = getOwner();
        if (target.isAlive() && target.canBeSeenAsEnemy() && target != owner) {
            Damage dmg;
            if (owner == null) {
                dmg = new Damage("magic", false, false, false);
            } else {
                if (owner instanceof AbstractIllager ill && ill.isAlliedTo(target)) return;
                dmg = new Damage("indirect_magic", false, false, false);
            }
            target.hurtByMob(dmg, 6.0F, owner instanceof Mob m ? m : null);
        }
    }
}
