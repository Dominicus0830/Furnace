// 인챈트의 minecraft:attributes 효과(EnchantmentAttributeEffect) 표 — data/minecraft/enchantment/*.json 의 effects.minecraft:attributes.
// 수정자 id = "minecraft:enchantment.<이름>/<칸 이름>"(dar.getModifier: id.withSuffix("/" + slot)). 인챈트가 켜지는 칸에서만 달린다(slots).
// 양은 LevelBasedValue 를 float 연산 그대로 계산한다: linear = base + perLevelAboveFirst × (레벨 - 1).
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.item.ItemTools;

final class EnchantAttributes {
    private EnchantAttributes() {}

    private interface Amount { double at(int level); }

    private record Eff(String enchant, Attribute attr, EquipmentSlot slot, int op, Amount amount) {}

    static float linear(float base, float perLevel, int level) { return base + perLevel * (float) (level - 1); }

    private static final List<Eff> EFFECTS = List.of(
            // 효율: levels_squared(added 1.0) = 레벨² + 1
            new Eff("minecraft:efficiency", Attribute.MINING_EFFICIENCY, EquipmentSlot.MAINHAND, AttributeModifier.ADD_VALUE, l -> (double) (l * l) + 1.0),
            // 쓸어 담기: fraction(linear(1,+1), linear(2,+1)) = 레벨 / (레벨 + 1)
            new Eff("minecraft:sweeping_edge", Attribute.SWEEPING_DAMAGE_RATIO, EquipmentSlot.MAINHAND, AttributeModifier.ADD_VALUE,
                    l -> linear(1.0F, 1.0F, l) / linear(2.0F, 1.0F, l)),
            // 친수성: linear(4,+4) add_multiplied_total
            new Eff("minecraft:aqua_affinity", Attribute.SUBMERGED_MINING_SPEED, EquipmentSlot.HEAD, AttributeModifier.ADD_MULTIPLIED_TOTAL, l -> linear(4.0F, 4.0F, l)));

    /** 그 칸의 그 아이템이 그 속성에 다는 인챈트 수정자. */
    static List<AttributeModifier> modifiers(Attribute attr, ItemStack stack, EquipmentSlot slot) {
        List<AttributeModifier> out = new ArrayList<>(1);
        for (Eff e : EFFECTS) {
            if (e.attr != attr || e.slot != slot) continue;
            int l = ItemTools.enchantmentLevel(stack, e.enchant);
            if (l <= 0) continue;
            String id = "minecraft:enchantment." + e.enchant.substring("minecraft:".length()) + "/" + slot.slotName();
            out.add(new AttributeModifier(id, e.amount.at(l), e.op));
        }
        return out;
    }
}
