// 마법부여(minecraft:enchantment) 정의 — 버전 중립. 바닐라 데이터 팩 마법부여의 "효과(effects)" 를 뺀 모든 필드다.
// 효과는 플러그인이 이벤트에서 ItemEnchantments.level(키) 로 레벨을 읽어 직접 구현한다.
//   supportedItems: 붙일 수 있는 아이템(모루·/enchant·전리품 검사). 태그("#minecraft:enchantable/sword") 하나 또는 아이템 id 목록.
//   primaryItems: 마법 부여대에서 나오는 아이템(null 이면 supportedItems 와 같다).
//   exclusiveSet: 함께 붙을 수 없는 마법부여(태그 하나 또는 id 목록, 비면 없음).
//   비용: 레벨 L 의 최소·최대 비용 = base + perLevelAboveFirst × (L - 1). 마법 부여대·전리품의 레벨 선택에 쓰인다.
//   slots: 효과가 도는 장비 칸 묶음 — any, mainhand, offhand, hand, feet, legs, chest, head, armor, body.
// 마법 부여대·거래·전리품에 나오게 하려면 GameRegistryRegistrar.addToTag 로 바닐라 태그(minecraft:in_enchanting_table,
// minecraft:tradeable, minecraft:on_random_loot, minecraft:non_treasure 등)에 넣는다.
package org.domi.furnace.api.registry;

import java.util.List;
import org.domi.furnace.api.text.Text;

public record EnchantmentDefinition(Text description, List<String> supportedItems, List<String> primaryItems, List<String> exclusiveSet,
                                    int weight, int maxLevel, Cost minCost, Cost maxCost, int anvilCost, List<String> slots) {

    /** 레벨에 따른 비용 = base + perLevelAboveFirst × (레벨 - 1). */
    public record Cost(int base, int perLevelAboveFirst) {
        public int at(int level) { return base + perLevelAboveFirst * (level - 1); }
    }

    private static final java.util.Set<String> SLOTS =
            java.util.Set.of("any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body");

    public EnchantmentDefinition {
        java.util.Objects.requireNonNull(description, "description");
        supportedItems = HolderSets.check("supportedItems", supportedItems, false);
        primaryItems = primaryItems == null ? null : HolderSets.check("primaryItems", primaryItems, false);
        exclusiveSet = exclusiveSet == null ? List.of() : HolderSets.check("exclusiveSet", exclusiveSet, true);
        if (weight < 1 || weight > 1024) throw new IllegalArgumentException("weight 는 1~1024: " + weight);
        if (maxLevel < 1 || maxLevel > 255) throw new IllegalArgumentException("maxLevel 은 1~255: " + maxLevel);
        java.util.Objects.requireNonNull(minCost, "minCost");
        java.util.Objects.requireNonNull(maxCost, "maxCost");
        if (anvilCost < 0) throw new IllegalArgumentException("anvilCost 는 0 이상: " + anvilCost);
        slots = List.copyOf(slots);
        for (String s : slots) if (!SLOTS.contains(s)) throw new IllegalArgumentException("모르는 장비 칸 묶음: " + s);
    }

    public static Builder builder(Text description) { return new Builder(description); }

    public static final class Builder {
        private final Text description;
        private List<String> supported = List.of(), primary, exclusive = List.of(), slots = List.of("mainhand");
        private int weight = 1, maxLevel = 1, anvilCost = 1;
        private Cost minCost = new Cost(1, 10), maxCost = new Cost(51, 10);

        private Builder(Text description) { this.description = description; }

        public Builder supportedItems(String... items) { supported = List.of(items); return this; }
        public Builder primaryItems(String... items) { primary = List.of(items); return this; }
        public Builder exclusiveSet(String... enchantments) { exclusive = List.of(enchantments); return this; }
        public Builder weight(int weight) { this.weight = weight; return this; }
        public Builder maxLevel(int maxLevel) { this.maxLevel = maxLevel; return this; }
        public Builder minCost(int base, int perLevelAboveFirst) { minCost = new Cost(base, perLevelAboveFirst); return this; }
        public Builder maxCost(int base, int perLevelAboveFirst) { maxCost = new Cost(base, perLevelAboveFirst); return this; }
        public Builder anvilCost(int anvilCost) { this.anvilCost = anvilCost; return this; }
        public Builder slots(String... slots) { this.slots = List.of(slots); return this; }

        public EnchantmentDefinition build() {
            return new EnchantmentDefinition(description, supported, primary, exclusive, weight, maxLevel, minCost, maxCost, anvilCost, slots);
        }
    }
}
