// 바닐라 BrewingStandBlockEntity: 병 3칸 + 재료 + 블레이즈 가루 연료. 연료 20회분, 재료를 넣으면 400틱 뒤 병이 바뀐다.
package org.domi.furnace.server.block.entity;

import java.util.Arrays;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.CraftRemainders;
import org.domi.furnace.server.item.PotionBrewing;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class BrewingStandBlockEntity extends ContainerBlockEntity {

    private static final Prop[] HAS_BOTTLE = {B.prop("has_bottle_0"), B.prop("has_bottle_1"), B.prop("has_bottle_2")};

    /** 남은 양조 시간 · 남은 연료 횟수 (바닐라 data 0, 1). */
    public int brewTime, fuel;
    private boolean[] lastPotionCount;
    private int ingredientItem = -1;

    public BrewingStandBlockEntity(Pos pos, int state) { super("minecraft:brewing_stand", pos, state, 5); }

    @Override public boolean isValid(int state) { return B.is(state, "brewing_stand"); }

    @Override public String defaultTitleKey() { return "container.brewing"; }

    @Override public boolean ticking() { return true; }

    @Override
    public void load(NbtCompound tag) {
        super.load(tag);
        brewTime = tag.getIntOr0("BrewTime");
        if (brewTime > 0) ingredientItem = items[3].itemId();
        fuel = tag.getIntOr0("Fuel");
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        super.saveAdditional(tag);
        tag.putShort("BrewTime", (short) brewTime);
        tag.putByte("Fuel", (byte) fuel);
    }

    private boolean[] bottles() {
        boolean[] b = new boolean[3];
        for (int i = 0; i < 3; i++) b[i] = !items[i].isEmpty();
        return b;
    }

    private boolean isBrewable() {
        ItemStack ing = items[3];
        if (ing.isEmpty() || !PotionBrewing.isIngredient(ing)) return false;
        for (int i = 0; i < 3; i++) if (!items[i].isEmpty() && PotionBrewing.hasMix(items[i], ing)) return true;
        return false;
    }

    private void doBrew(BlockLevel level) {
        if (!level.gameEvents().brew(level.dimension(), pos, items, fuel)) return;   // 플러그인 BrewEvent 취소: 병도 재료도 그대로
        ItemStack ing = items[3];
        for (int i = 0; i < 3; i++) items[i] = PotionBrewing.mix(ing, items[i]);
        ItemStack rest = ing.withCount(ing.count() - 1);
        ItemStack remainder = CraftRemainders.of(ing);
        if (remainder != null && !remainder.isEmpty()) {
            if (rest.isEmpty()) rest = remainder;
            else level.dropItemStack(pos.x(), pos.y(), pos.z(), remainder);   // Containers.dropItemStack(level, 칸 모서리 좌표, 남는 아이템)
        }
        items[3] = rest;
        level.levelEvent(null, 1035, pos, 0);
    }

    @Override
    public void tick(BlockLevel level) {
        ItemStack fuelStack = items[4];
        if (fuel <= 0 && "minecraft:blaze_powder".equals(org.domi.furnace.server.item.ItemTools.itemName(fuelStack))) {
            var burn = level.gameEvents().brewingFuel(level.dimension(), pos, fuelStack, 20);   // 플러그인 BrewingStandFuelEvent
            if (burn == null) return;
            fuel = burn.fuelPower();
            if (fuel > 0 && burn.consuming()) items[4] = fuelStack.withCount(fuelStack.count() - 1);
            setChanged(level);
        }
        boolean brewable = isBrewable();
        boolean brewing = brewTime > 0;
        ItemStack ing = items[3];
        if (brewing) {
            brewTime--;
            boolean done = brewTime == 0;
            if (done && brewable) doBrew(level);
            else if (!brewable || ing.itemId() != ingredientItem) brewTime = 0;
            setChanged(level);
        } else if (brewable && fuel > 0) {
            fuel--;
            brewTime = 400;
            ingredientItem = ing.itemId();
            setChanged(level);
        }
        boolean[] now = bottles();
        if (!Arrays.equals(now, lastPotionCount)) {
            lastPotionCount = now;
            int state = level.getBlockState(pos);
            if (!B.is(state, "brewing_stand")) return;
            for (int i = 0; i < 3; i++) state = HAS_BOTTLE[i].with(state, now[i]);
            blockState = state;
            level.setBlock(pos, state, 2);
        }
    }
}
