package org.bukkit.craftbukkit.block;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.FurnaceInventory;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.compat.paper.CompatBlockData;

/**
 * Paper CraftFurnace: 화로류 블록 상태. 값은 사본 NBT(바닐라 AbstractFurnaceBlockEntity 저장 꼴: BurnTime·CookTime·CookTimeTotal 은 short,
 * RecipesUsed 는 레시피 키 → 수)에서 int 로 읽어 들고 update 때 적는다. setBurnTime 은 Paper(SPIGOT-844)처럼 블록 상태 lit 도 맞춘다.
 */
public abstract class CraftFurnace extends CraftContainer implements org.bukkit.block.Furnace {

    private int litTime;
    private int cookingProgress;
    private int cookingTotalTime;

    protected CraftFurnace(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
        NbtCompound n = snapshotNbt();
        litTime = n.getShort("BurnTime");
        cookingProgress = n.getShort("CookTime");
        cookingTotalTime = n.getShort("CookTimeTotal");
    }

    @Override
    protected void applyToSnapshot() {
        super.applyToSnapshot();
        NbtCompound n = snapshotNbt();
        n.putShort("BurnTime", (short) litTime);
        n.putShort("CookTime", (short) cookingProgress);
        n.putShort("CookTimeTotal", (short) cookingTotalTime);
    }

    @Override
    public short getBurnTime() { return (short) litTime; }

    @Override
    public void setBurnTime(short burnTime) {
        litTime = burnTime;
        var d = ((CompatBlockData) getBlockData()).furnace();
        if (d.properties().containsKey("lit")) {
            Map<String, String> props = new HashMap<>(d.properties());
            props.put("lit", Boolean.toString(burnTime > 0));
            setBlockData(CompatBlockData.of(new org.domi.furnace.api.world.BlockData(d.type(), props)));
        }
    }

    @Override
    public short getCookTime() { return (short) cookingProgress; }

    @Override
    public void setCookTime(short cookTime) { cookingProgress = cookTime; }

    @Override
    public int getCookTimeTotal() { return cookingTotalTime; }

    @Override
    public void setCookTimeTotal(int cookTimeTotal) { cookingTotalTime = cookTimeTotal; }

    /** Paper 는 블록 엔티티 필드로 들고 NBT Paper.CookSpeedMultiplier 로 저장한다. Furnace 서버 화로는 배율을 아직 모른다 — 읽기만 된다. */
    @Override
    public double getCookSpeedMultiplier() {
        return snapshotNbt().contains("Paper.CookSpeedMultiplier") ? snapshotNbt().getDouble("Paper.CookSpeedMultiplier") : 1.0;
    }

    @Override
    public void setCookSpeedMultiplier(double multiplier) {
        if (!(multiplier >= 0)) throw new IllegalArgumentException("Furnace speed multiplier cannot be negative");
        if (multiplier > 200) throw new IllegalArgumentException("Furnace speed multiplier cannot more than 200");
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("Furnace#setCookSpeedMultiplier", "Furnace 서버 화로가 굽기 속도 배율을 아직 모른다");
    }

    @Override
    public int getRecipeUsedCount(org.bukkit.NamespacedKey furnaceRecipe) {
        return snapshotNbt().getCompound("RecipesUsed").getInt(furnaceRecipe.toString());
    }

    @Override
    public boolean hasRecipeUsedCount(org.bukkit.NamespacedKey furnaceRecipe) {
        return snapshotNbt().getCompound("RecipesUsed").contains(furnaceRecipe.toString());
    }

    @Override
    public FurnaceInventory getInventory() { return (FurnaceInventory) super.getInventory(); }

    @Override
    public FurnaceInventory getSnapshotInventory() { return (FurnaceInventory) super.getSnapshotInventory(); }
}
