// 바닐라 MinecartHopper: 5 칸 깔때기 수레. 켜진 활성화 레일 위에서 꺼진다(Enabled).
// TODO(깔때기 이동): HopperBlockEntity.suckInItems — Furnace 에 깔때기 아이템 이동이 아직 없어 빨아들이지 않는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class MinecartHopper extends AbstractMinecartContainer {

    private boolean enabled = true;

    public MinecartHopper(ChunkLevel level, int id) { super(EntityType.HOPPER_MINECART, level, id, 5); }

    @Override public Type getMinecartType() { return Type.HOPPER; }

    @Override protected String dropItem() { return "minecraft:hopper_minecart"; }

    @Override public int getDefaultDisplayBlockState() { return B.defaultState("hopper"); }

    @Override public int getDefaultDisplayOffset() { return 1; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean v) { enabled = v; }

    /** activateMinecart: 신호가 있으면 끈다. */
    @Override
    public void activateMinecart(int x, int y, int z, boolean powered) {
        boolean b = !powered;
        if (b != enabled) enabled = b;
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enabled", enabled);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
    }
}
