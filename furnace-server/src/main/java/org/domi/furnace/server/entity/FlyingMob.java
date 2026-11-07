// 바닐라 FlyingMob: 날아다니는 몹 — 낙하 피해 없음, 사다리를 타지 않고, 물·용암·공중에서 각기 다른 마찰로 미끄러진다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public abstract class FlyingMob extends Mob {

    /** 바닐라는 낙하 처리를 비운다(날아다니는 몹은 착지 입자·낙하 거리·HIT_GROUND 가 없다). */
    @Override
    protected void checkFallDamage(double dy, boolean ground, int state, org.domi.furnace.server.block.Pos pos) {}

    protected FlyingMob(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, Damage source) { return false; }

    @Override
    protected void travel(float inX, float inY, float inZ) {
        if (isInWater()) {
            moveRelative(0.02F, inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            setDeltaMovement(vx() * 0.8F, vy() * 0.8F, vz() * 0.8F);
        } else if (isInLava()) {
            moveRelative(0.02F, inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            setDeltaMovement(vx() * 0.5, vy() * 0.5, vz() * 0.5);
        } else {
            float friction = 0.91F;
            if (onGround()) friction = frictionBelow() * 0.91F;
            float f2 = 0.16277137F / (friction * friction * friction);
            friction = 0.91F;
            if (onGround()) friction = frictionBelow() * 0.91F;
            moveRelative(onGround() ? 0.1F * f2 : 0.02F, inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            setDeltaMovement(vx() * friction, vy() * friction, vz() * friction);
        }
    }

    @Override
    public boolean onClimbable() { return false; }
}
