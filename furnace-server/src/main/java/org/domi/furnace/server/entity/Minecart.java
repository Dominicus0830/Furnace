// 바닐라 Minecart(탈 수 있는 수레). 웅크리지 않았고 빈 수레면 탄다. 활성화 레일은 승객을 내리고 흔들린다(피해 50).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public class Minecart extends AbstractMinecart {

    public Minecart(ChunkLevel level, int id) { super(EntityType.MINECART, level, id); }

    @Override public Type getMinecartType() { return Type.RIDEABLE; }

    @Override protected String dropItem() { return "minecraft:minecart"; }

    /** interact: 웅크렸거나 이미 누가 탔으면 넘기고, 아니면 탄다. */
    public boolean interact(Player p, int hand) {
        if (p.sneaking() || isVehicle()) return false;
        return p.startRiding(this, false);
    }

    /** activateMinecart: 켜진 활성화 레일이면 승객을 내리고, 맞은 시간이 0 이면 흔들림(피해 50). */
    @Override
    public void activateMinecart(int x, int y, int z, boolean powered) {
        if (powered) {
            if (isVehicle()) ejectPassengers();
            if (getHurtTime() == 0) {
                setHurtDir(-getHurtDir());
                setHurtTime(10);
                setDamage(50.0F);
                markHurt();
            }
        }
    }
}
