// 바닐라 AbstractGolem: 골렘 공통 — 소리 없음, 멀리 있어도 사라지지 않는다(removeWhenFarAway 거짓), 주변음 간격 120.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public abstract class AbstractGolem extends PathfinderMob {

    protected AbstractGolem(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override public String getAmbientSound() { return null; }

    @Override protected String getHurtSound(Damage source) { return null; }

    @Override protected String getDeathSound() { return null; }

    @Override
    public int getAmbientSoundInterval() { return 120; }

    @Override
    public boolean removeWhenFarAway(double distSqr) { return false; }
}
