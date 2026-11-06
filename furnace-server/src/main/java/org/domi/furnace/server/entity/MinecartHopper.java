// 바닐라 MinecartHopper: 5 칸 깔때기 수레. 켜진 활성화 레일 위에서 꺼진다(Enabled).
// 틱마다 켜져 있으면 위 용기·아이템 엔티티(HopperBlockEntity.suckInItems), 그다음 수레 상자(옆 0.25)의 아이템 엔티티를 빨아들인다.
// 기준 좌표는 (x, y + 0.5, z), 격자에 맞지 않는다(위 칸이 막혀도 빨아들인다).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class MinecartHopper extends AbstractMinecartContainer implements org.domi.furnace.server.block.entity.Hopper {

    private boolean enabled = true;

    public MinecartHopper(ChunkLevel level, int id) { super(EntityType.HOPPER_MINECART, level, id, 5); }

    @Override public Type getMinecartType() { return Type.HOPPER; }

    @Override protected String dropItem() { return "minecraft:hopper_minecart"; }

    @Override public int getDefaultDisplayBlockState() { return B.defaultState("hopper"); }

    @Override public int getDefaultDisplayOffset() { return 1; }

    public boolean isEnabled() { return enabled; }

    @Override public double levelX() { return x(); }

    @Override public double levelY() { return y() + 0.5; }

    @Override public double levelZ() { return z(); }

    @Override public boolean isGridAligned() { return false; }

    @Override
    public org.domi.furnace.server.block.entity.HopperBlockEntity.Inv hopperInv(org.domi.furnace.server.block.BlockLevel l) {
        return org.domi.furnace.server.block.entity.HopperBlockEntity.entityInv(this);
    }

    /** MinecartHopper.tick: 살아 있고 켜져 있으면 빨아들인다(setChanged 는 엔티티에서 할 일이 없다). */
    @Override
    public void tick() {
        super.tick();
        if (isAlive() && isEnabled()) suckInItems();
    }

    /** suckInItems: 깔때기 규칙 → 수레 상자를 옆으로 0.25 넓힌 안의 살아 있는 아이템 엔티티. */
    public boolean suckInItems() {
        if (org.domi.furnace.server.block.entity.HopperBlockEntity.suckInItems(level, this)) return true;
        var inv = hopperInv(level);
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, boundingBox().inflate(0.25, 0.0, 0.25), Entity::isAlive)) {
            if (org.domi.furnace.server.block.entity.HopperBlockEntity.addItem(inv, ie)) return true;
        }
        return false;
    }

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
