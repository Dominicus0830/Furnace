// 바닐라 NeutralMob: 맞으면 화가 나서(AngerTime 틱 동안) 때린 상대(AngryAt)를 쫓는 몹. 구현체가 필드를 가진다.
package org.domi.furnace.server.entity;

import java.util.Objects;
import java.util.UUID;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public interface NeutralMob {
    int getRemainingPersistentAngerTime();

    void setRemainingPersistentAngerTime(int t);

    UUID getPersistentAngerTarget();

    void setPersistentAngerTarget(UUID u);

    void startPersistentAngerTimer();

    Targetable getTarget();

    void setTarget(Targetable t);

    void setLastHurtByMob(Targetable t);

    void setLastHurtByPlayer(Player p);

    boolean canAttackTarget(Targetable t);

    org.domi.furnace.server.block.ChunkLevel level();

    default void addPersistentAngerSaveData(NbtCompound tag) {
        tag.putInt("AngerTime", getRemainingPersistentAngerTime());
        if (getPersistentAngerTarget() != null) tag.putIntArray("AngryAt", Entity.uuidToInts(getPersistentAngerTarget()));
    }

    default void readPersistentAngerSaveData(NbtCompound tag) {
        setRemainingPersistentAngerTime(tag.getInt("AngerTime"));
        if (!(tag.tags().get("AngryAt") instanceof int[] u && u.length == 4)) {
            setPersistentAngerTarget(null);
            return;
        }
        UUID id = new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
        setPersistentAngerTarget(id);
        Targetable t = level().getTargetableByUuid(id);
        if (t instanceof Mob m) {
            setLastHurtByMob(m);
            setTarget(m);
        }
        if (t instanceof Player p) {
            setLastHurtByPlayer(p);
            setLastHurtByMob(p);
        }
    }

    default void updatePersistentAnger(boolean universalAnger) {
        Targetable target = getTarget();
        UUID angerTarget = getPersistentAngerTarget();
        if ((target == null || !target.isAlive()) && angerTarget != null && level().getTargetableByUuid(angerTarget) instanceof Mob) {
            stopBeingAngry();
        } else {
            if (target != null && !Objects.equals(angerTarget, uuidOf(target))) {
                setPersistentAngerTarget(uuidOf(target));
                startPersistentAngerTimer();
            }
            if (getRemainingPersistentAngerTime() > 0 && (target == null || !target.isPlayer() || !universalAnger)) {
                setRemainingPersistentAngerTime(getRemainingPersistentAngerTime() - 1);
                if (getRemainingPersistentAngerTime() == 0) stopBeingAngry();
            }
        }
    }

    static UUID uuidOf(Targetable t) { return t instanceof Player p ? p.uuid() : t instanceof Entity e ? e.uuid() : null; }

    default boolean isAngryAt(Targetable t) {
        if (!canAttackTarget(t)) return false;
        return t.isPlayer() && isAngryAtAllPlayers() ? true : Objects.equals(uuidOf(t), getPersistentAngerTarget());
    }

    default boolean isAngryAtAllPlayers() {
        return level().gameRule("universalAnger") && isAngry() && getPersistentAngerTarget() == null;
    }

    default boolean isAngry() { return getRemainingPersistentAngerTime() > 0; }

    default void playerDied(Player p) {
        if (level().gameRule("forgiveDeadPlayers") && p.uuid().equals(getPersistentAngerTarget())) stopBeingAngry();
    }

    default void forgetCurrentTargetAndRefreshUniversalAnger() {
        stopBeingAngry();
        startPersistentAngerTimer();
    }

    default void stopBeingAngry() {
        setLastHurtByMob(null);
        setPersistentAngerTarget(null);
        setTarget(null);
        setRemainingPersistentAngerTime(0);
    }
}
