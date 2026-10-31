// 플레이어 속성 값(바닐라 AttributeInstance.getValue): 기본값 + 상태 효과 수정자 + 장비(주 손·갑옷) attribute_modifiers + 인챈트 속성 효과.
// 플레이어는 속성 지도를 따로 들고 있지 않아 필요할 때 이 값을 계산한다(서버 전용 속성이라 클라이언트로 보내지 않는다).
package org.domi.furnace.server.entity;

import java.util.List;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeInstance;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.ItemTools;

public final class PlayerAttributes {
    private PlayerAttributes() {}

    /** Player.createAttributes 의 기본값: 공격력 1.0, 이동 속도 0.1F(float 을 double 로 넓힌 값). 나머지는 속성 기본값. */
    public static double base(Attribute a) {
        if (a == Attribute.ATTACK_DAMAGE) return 1.0;
        if (a == Attribute.MOVEMENT_SPEED) return 0.1F;
        return a.defaultValue();
    }

    /**
     * 속성 값. armor 는 {머리, 가슴, 다리, 발} 순(없으면 빈 스택). 수정자 id 는 한 속성에서 유일하다(같은 id 는 덮어쓴다).
     */
    public static double value(Attribute attr, ItemStack mainHand, ItemStack[] armor, List<MobEffectInstance> effects) {
        AttributeInstance inst = new AttributeInstance(attr, () -> {});
        inst.setBaseValue(base(attr));
        if (effects != null) {
            for (MobEffectInstance e : effects) {
                for (Object[] m : e.effect().attributeModifiers()) {
                    if (!attr.name().equals("minecraft:" + m[0])) continue;
                    // MobEffect.AttributeTemplate.create: 양 × (증폭 + 1)
                    double amount = ((Number) m[2]).doubleValue() * (e.amplifier() + 1);
                    inst.addOrReplaceModifier(new AttributeModifier((String) m[1], amount, (Integer) m[3]));
                }
            }
        }
        addItem(inst, mainHand, "mainhand");
        if (armor != null) {
            String[] slots = {"head", "chest", "legs", "feet"};
            for (int i = 0; i < 4 && i < armor.length; i++) addItem(inst, armor[i], slots[i]);
        }
        return inst.value();
    }

    /** 장비 한 칸의 수정자: 아이템 attribute_modifiers 와 그 칸에서 켜지는 인챈트의 속성 효과. */
    private static void addItem(AttributeInstance inst, ItemStack s, String slot) {
        if (s == null || s.isEmpty()) return;
        Attribute attr = inst.attribute();
        for (ItemTools.AttributeEntry e : ItemTools.attributeEntries(s)) {
            if (!e.type().equals(attr.name()) || !ItemTools.slotGroupMatches(e.slot(), slot)) continue;
            inst.addOrReplaceModifier(new AttributeModifier(e.id(), e.amount(), AttributeModifier.operationOf(e.operation())));
        }
        if (slot.equals("mainhand")) {
            if (attr == Attribute.MINING_EFFICIENCY) {
                int l = ItemTools.enchantmentLevel(s, "minecraft:efficiency");
                // levels_squared(added 1.0): 레벨² + 1
                if (l > 0) inst.addOrReplaceModifier(new AttributeModifier("minecraft:enchantment.efficiency", (double) (l * l) + 1.0, AttributeModifier.ADD_VALUE));
            } else if (attr == Attribute.SWEEPING_DAMAGE_RATIO) {
                int l = ItemTools.enchantmentLevel(s, "minecraft:sweeping_edge");
                // fraction(linear(1, +1) / linear(2, +1)) = 레벨 / (레벨 + 1)
                if (l > 0) {
                    float n = 1.0F + (l - 1) * 1.0F, d = 2.0F + (l - 1) * 1.0F;
                    inst.addOrReplaceModifier(new AttributeModifier("minecraft:enchantment.sweeping_edge", n / d, AttributeModifier.ADD_VALUE));
                }
            }
        } else if (slot.equals("head") && attr == Attribute.SUBMERGED_MINING_SPEED) {
            int l = ItemTools.enchantmentLevel(s, "minecraft:aqua_affinity");
            // linear(4, +4) add_multiplied_total
            if (l > 0) inst.addOrReplaceModifier(new AttributeModifier("minecraft:enchantment.aqua_affinity", 4.0F + (l - 1) * 4.0F, AttributeModifier.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** 플레이어의 현재 장비로 계산(소유 region 스레드 — 인벤토리를 읽는다). */
    public static double value(Player p, Attribute attr) {
        PlayerInventory inv = p.inventory();
        ItemStack[] armor = {inv.get(5), inv.get(6), inv.get(7), inv.get(8)};
        return value(attr, inv.get(inv.heldWindowSlot()), armor, p.vitals().activeEffects());
    }
}
