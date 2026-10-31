// 바닐라 TamableAnimal: 길들이기(주인 UUID·앉기·길들임 비트), 주인에게 순간이동, 주인이 죽으면 메시지.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class TamableAnimal extends Animal {
    public static final int DATA_FLAGS_ID = 17, DATA_OWNERUUID_ID = 18;
    private boolean orderedToSit;

    protected TamableAnimal(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_FLAGS_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_OWNERUUID_ID, SetEntityData.SERIALIZER_OPTIONAL_UUID, null);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (getOwnerUUID() != null) tag.putIntArray("Owner", uuidToInts(getOwnerUUID()));
        tag.putBoolean("Sitting", orderedToSit);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        UUID owner = null;
        if (tag.tags().get("Owner") instanceof int[] u && u.length == 4) {
            owner = new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
        }
        if (owner != null) {
            setOwnerUUID(owner);
            setTame(true, false);
        }
        orderedToSit = tag.getBoolean("Sitting");
        setInSittingPose(orderedToSit);
    }

    public boolean isTame() { return ((Byte) entityData.get(DATA_FLAGS_ID) & 4) != 0; }

    public void setTame(boolean tame, boolean applyTamingSideEffects) {
        byte b = (Byte) entityData.get(DATA_FLAGS_ID);
        entityData.set(DATA_FLAGS_ID, (byte) (tame ? b | 4 : b & -5));
        if (applyTamingSideEffects) applyTamingSideEffects();
    }

    protected void applyTamingSideEffects() {}

    /** TamableAnimal.checkLeashDistance(bul:64): 앉은 자세면 당기지 않고, 10칸 넘으면 끊는다. */
    @Override
    protected boolean checkLeashDistance(Rider holder, float dist) {
        if (isInSittingPose()) {
            if (dist > 10.0F) dropLeash(true, true);
            return false;
        }
        return super.checkLeashDistance(holder, dist);
    }

    public boolean isInSittingPose() { return ((Byte) entityData.get(DATA_FLAGS_ID) & 1) != 0; }

    public void setInSittingPose(boolean sit) {
        byte b = (Byte) entityData.get(DATA_FLAGS_ID);
        entityData.set(DATA_FLAGS_ID, (byte) (sit ? b | 1 : b & -2));
    }

    public UUID getOwnerUUID() { return (UUID) entityData.get(DATA_OWNERUUID_ID); }

    public void setOwnerUUID(UUID u) { entityData.set(DATA_OWNERUUID_ID, u); }

    /** OwnableEntity.getOwner: 플레이어만 주인이 될 수 있다(안 보이는 곳에 있으면 null). */
    public Player getOwner() {
        UUID u = getOwnerUUID();
        return u == null ? null : level.getTargetableByUuid(u) instanceof Player p ? p : null;
    }

    public void tame(Player p) {
        setTame(true, true);
        setOwnerUUID(p.uuid());
        var me = KillCredit.snapshot(lootEntity());   // 바닐라 TamableAnimal.tame: tame_animal
        p.runOnOwner(() -> {
            var adv = p.advancements();
            if (adv != null) adv.triggerTame(me);
        });
    }

    public boolean isOwnedBy(Targetable t) { return t != null && t == getOwner(); }

    public boolean wantsToAttack(Targetable target, Targetable owner) { return true; }

    @Override
    public boolean canAttackType(Targetable t) { return !isOwnedBy(t) && super.canAttackType(t); }

    public boolean isOrderedToSit() { return orderedToSit; }

    public void setOrderedToSit(boolean v) { orderedToSit = v; }

    public boolean unableToMoveToOwner() { return isOrderedToSit() || isPassenger() || mayBeLeashed() || getOwner() != null && getOwner().vitals().isSpectator(); }

    public boolean shouldTryTeleportToOwner() {
        Player o = getOwner();
        if (o == null) return false;
        double dx = x() - o.x(), dy = y() - o.y(), dz = z() - o.z();
        return dx * dx + dy * dy + dz * dz >= 144.0;
    }

    public void tryToTeleportToOwner() {
        Player o = getOwner();
        if (o != null) teleportToAroundBlockPos(o.blockPosition());
    }

    private void teleportToAroundBlockPos(Pos pos) {
        for (int i = 0; i < 10; i++) {
            int dx = random.nextIntBetweenInclusive(-3, 3);
            int dz = random.nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) >= 2 || Math.abs(dz) >= 2) {
                int dy = random.nextIntBetweenInclusive(-1, 1);
                if (maybeTeleportTo(pos.x() + dx, pos.y() + dy, pos.z() + dz)) return;
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        if (!canTeleportTo(new Pos(x, y, z))) return false;
        moveTo(x + 0.5, y, z + 0.5, yRot(), xRot());
        navigation.stop();
        return true;
    }

    private boolean canTeleportTo(Pos pos) {
        var type = org.domi.furnace.server.entity.ai.navigation.WalkNodeEvaluator.getPathTypeStatic(new org.domi.furnace.server.entity.ai.navigation.PathfindingContext(level, this), pos);
        if (type != PathType.WALKABLE) return false;
        int below = level.getBlockState(pos.below());
        if (!canFlyToOwner() && org.domi.furnace.server.block.B.isTag(below, "minecraft:leaves")) return false;
        Pos here = blockPosition();
        return level.noCollision(boundingBox().move(pos.x() - here.x(), pos.y() - here.y(), pos.z() - here.z()));
    }

    protected boolean canFlyToOwner() { return false; }

    public final boolean unableToMove() { return isInSittingPose() || isPassenger() || getOwner() != null && getOwner().vitals().isSpectator(); }


    /** TamableAnimal.TamableAnimalPanicGoal: 놀라 달리다가도 주인이 너무 멀면(앉아 있지 않을 때) 곁으로 순간이동한다. */
    public static class TamablePanicGoal extends org.domi.furnace.server.entity.ai.goal.PanicGoal {
        private final TamableAnimal tamable;

        public TamablePanicGoal(TamableAnimal mob, double speed, String tag) {
            super(mob, speed, tag);
            this.tamable = mob;
        }

        public TamablePanicGoal(TamableAnimal mob, double speed) {
            super(mob, speed);
            this.tamable = mob;
        }

        @Override
        public void tick() {
            if (!tamable.unableToMoveToOwner() && tamable.shouldTryTeleportToOwner()) tamable.tryToTeleportToOwner();
            super.tick();
        }
    }
}
