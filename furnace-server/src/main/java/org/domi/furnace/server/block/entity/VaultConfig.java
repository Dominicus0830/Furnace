// 바닐라 VaultConfig: 금고 설정 — 전리품 표, 활성/비활성 범위, 열쇠 아이템, 표시용 전리품 표 덮어쓰기.
// 필드는 모두 lenientOptional(틀린 값은 기본값, key_item 은 없으면 빈 스택). 활성 범위가 비활성 범위보다 크면 설정 전체가 거부된다(기존 값 유지).
// 저장은 DFU optionalFieldOf 규칙 — 기본값과 같은 loot_table·activation_range·deactivation_range 는 쓰지 않고, key_item 은 비어 있지 않으면 늘 쓴다.
package org.domi.furnace.server.block.entity;

import java.util.Optional;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.ItemStackNbt;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public record VaultConfig(String lootTable, double activationRange, double deactivationRange, ItemStack keyItem, Optional<String> overrideLootTableToDisplay) {

    public static final String DEFAULT_LOOT_TABLE = "minecraft:chests/trial_chambers/reward";
    public static final double DEFAULT_ACTIVATION_RANGE = 4.0, DEFAULT_DEACTIVATION_RANGE = 4.5;
    public static final String EMPTY_LOOT_TABLE = "minecraft:empty";

    /** 기본 설정(VaultConfig.DEFAULT): 열쇠는 시련 열쇠 하나. 아이템 표가 설치된 뒤에만 부를 수 있다. */
    public static VaultConfig defaultConfig() {
        Integer key = ItemContext.id("minecraft:item", "minecraft:trial_key");
        return new VaultConfig(DEFAULT_LOOT_TABLE, DEFAULT_ACTIVATION_RANGE, DEFAULT_DEACTIVATION_RANGE,
                key == null ? ItemStack.EMPTY : new ItemStack(key, 1), Optional.empty());
    }

    /** 코덱 해석. 검증(활성 ≤ 비활성) 실패면 null — 부르는 쪽이 이전 설정을 둔다. */
    public static VaultConfig load(NbtCompound t) {
        String table = t.tags().get("loot_table") instanceof String s && BaseSpawner.normalizeId(s) != null ? BaseSpawner.normalizeId(s) : DEFAULT_LOOT_TABLE;
        double act = t.tags().get("activation_range") instanceof Number n ? n.doubleValue() : DEFAULT_ACTIVATION_RANGE;
        double deact = t.tags().get("deactivation_range") instanceof Number n ? n.doubleValue() : DEFAULT_DEACTIVATION_RANGE;
        ItemStack key = t.tags().get("key_item") instanceof NbtCompound c ? ItemStackNbt.read(c) : ItemStack.EMPTY;
        Optional<String> display = t.tags().get("override_loot_table_to_display") instanceof String s && BaseSpawner.normalizeId(s) != null
                ? Optional.of(BaseSpawner.normalizeId(s)) : Optional.empty();
        if (act > deact) return null;
        return new VaultConfig(table, act, deact, key, display);
    }

    public NbtCompound save() {
        NbtCompound t = new NbtCompound();
        if (!lootTable.equals(DEFAULT_LOOT_TABLE)) t.putString("loot_table", lootTable);
        if (Double.compare(activationRange, DEFAULT_ACTIVATION_RANGE) != 0) t.putDouble("activation_range", activationRange);
        if (Double.compare(deactivationRange, DEFAULT_DEACTIVATION_RANGE) != 0) t.putDouble("deactivation_range", deactivationRange);
        if (!keyItem.isEmpty()) t.putCompound("key_item", ItemStackNbt.write(keyItem));
        overrideLootTableToDisplay.ifPresent(s -> t.putString("override_loot_table_to_display", s));
        return t;
    }
}
