package org.bukkit.craftbukkit.block;

import org.bukkit.inventory.BrewerInventory;

/**
 * 블록 엔티티 brewing_stand 의 블록 상태(Paper CraftBrewingStand). 양조 시간·연료는 사본 NBT 의 BrewTime(short)·Fuel(byte) — 바닐라 저장 형식.
 * recipeBrewTime 은 Paper 가 블록 엔티티 필드로만 두고 저장하지 않으므로 사본에선 늘 400 에서 시작하고 update 로 실리지 않는다(Paper 와 같다).
 */
public class CraftBrewingStand extends CraftContainer implements org.bukkit.block.BrewingStand {

    private int brewTime;
    private int fuel;
    private int recipeBrewTime = 400;

    public CraftBrewingStand(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
        this.brewTime = snapshotNbt().getShort("BrewTime");
        this.fuel = snapshotNbt().getByte("Fuel");
    }

    @Override
    protected void applyToSnapshot() {
        super.applyToSnapshot();
        snapshotNbt().putShort("BrewTime", (short) brewTime);
        snapshotNbt().putByte("Fuel", (byte) fuel);
    }

    @Override
    public int getBrewingTime() { return brewTime; }

    @Override
    public void setBrewingTime(int brewTime) { this.brewTime = brewTime; }

    @Override
    public void setRecipeBrewTime(int recipeBrewTime) {
        if (recipeBrewTime <= 0) throw new IllegalArgumentException("recipeBrewTime must be positive");
        this.recipeBrewTime = recipeBrewTime;
    }

    @Override
    public int getRecipeBrewTime() { return recipeBrewTime; }

    @Override
    public int getFuelLevel() { return fuel; }

    @Override
    public void setFuelLevel(int level) { this.fuel = level; }

    @Override
    public BrewerInventory getInventory() { return (BrewerInventory) super.getInventory(); }

    @Override
    public BrewerInventory getSnapshotInventory() { return (BrewerInventory) super.getSnapshotInventory(); }
}
