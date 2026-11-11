// 플레이어 속성 값(바닐라 AttributeInstance.getValue): 기본값 + 상태 효과 수정자 + 장비 칸별 attribute_modifiers + 인챈트 속성 효과 + 달리기.
// modifiers() 가 수정자 목록의 유일한 원천이다 — 계산(value)과 살아 있는 맵 동기(Player.syncAttributes)가 같은 함수를 쓴다.
package org.domi.furnace.server.entity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeInstance;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.ItemTools;

public final class PlayerAttributes {
    private PlayerAttributes() {}

    /** LivingEntity.SPEED_MODIFIER_SPRINTING: id minecraft:sprinting, 0.3F, add_multiplied_total(달리는 동안 이동 속도에 붙는다). */
    public static final AttributeModifier SPRINT = new AttributeModifier("minecraft:sprinting", 0.3F, AttributeModifier.ADD_MULTIPLIED_TOTAL);

    /** Player.createAttributes 의 기본값: 공격력 1.0, 이동 속도 0.1F(float 을 double 로 넓힌 값). 나머지는 속성 기본값. */
    public static double base(Attribute a) {
        if (a == Attribute.ATTACK_DAMAGE) return 1.0;
        if (a == Attribute.MOVEMENT_SPEED) return 0.1F;
        return a.defaultValue();
    }

    /** 값 = 기본값에 수정자를 합성하고 속성 범위로 자른다(AttributeInstance.calculateValue). */
    public static double compute(Attribute attr, double base, Collection<AttributeModifier> mods) {
        AttributeInstance inst = new AttributeInstance(attr, () -> {});
        inst.setBaseValue(base);
        for (AttributeModifier m : mods) inst.addOrReplaceModifier(m);
        return inst.value();
    }

    /** 상태 효과가 그 속성에 다는 수정자(MobEffect.AttributeTemplate.create: 양 × (증폭 + 1)). id 는 효과마다 유일하다. */
    public static Map<String, AttributeModifier> effectModifiers(Attribute attr, List<MobEffectInstance> effects) {
        Map<String, AttributeModifier> out = new LinkedHashMap<>();
        if (effects == null) return out;
        for (MobEffectInstance e : effects) {
            for (Object[] m : e.effect().attributeModifiers()) {
                if (!attr.name().equals("minecraft:" + m[0])) continue;
                double amount = ((Number) m[2]).doubleValue() * (e.amplifier() + 1);
                out.put((String) m[1], new AttributeModifier((String) m[1], amount, (Integer) m[3]));
            }
        }
        return out;
    }

    /**
     * 장비가 그 속성에 다는 수정자. bySlot 은 EquipmentSlot.values() 순서(없으면 빈 스택/null). 칸별로 아이템 attribute_modifiers 와
     * 그 칸에서 켜지는 인챈트 속성 효과를 모은다(ItemStack.forEachModifier). 수정자 id 는 한 속성에서 유일하다(같은 id 는 덮어쓴다).
     */
    public static Map<String, AttributeModifier> equipmentModifiers(Attribute attr, ItemStack[] bySlot) {
        Map<String, AttributeModifier> out = new LinkedHashMap<>();
        if (bySlot == null) return out;
        EquipmentSlot[] slots = EquipmentSlot.values();
        for (int i = 0; i < slots.length && i < bySlot.length; i++) {
            ItemStack s = bySlot[i];
            if (s == null || s.isEmpty()) continue;
            String slot = slots[i].slotName();
            for (ItemTools.AttributeEntry e : ItemTools.attributeEntries(s)) {
                if (!e.type().equals(attr.name()) || !ItemTools.slotGroupMatches(e.slot(), slot)) continue;
                out.put(e.id(), new AttributeModifier(e.id(), e.amount(), AttributeModifier.operationOf(e.operation())));
            }
            for (AttributeModifier m : EnchantAttributes.modifiers(attr, s, slots[i])) out.put(m.id(), m);
        }
        return out;
    }

    /** 효과 + 장비 + (이동 속도면) 달리기의 합친 수정자. */
    public static Map<String, AttributeModifier> modifiers(Attribute attr, ItemStack[] bySlot, List<MobEffectInstance> effects, boolean sprinting) {
        return modifiers(attr, bySlot, effects, sprinting, List.of());
    }

    /** 위의 합 + 위치 변화 인챈트(영혼 가속)가 켜 둔 수정자 {@code located}. */
    public static Map<String, AttributeModifier> modifiers(Attribute attr, ItemStack[] bySlot, List<MobEffectInstance> effects, boolean sprinting,
            Collection<AttributeModifier> located) {
        Map<String, AttributeModifier> out = effectModifiers(attr, effects);
        out.putAll(equipmentModifiers(attr, bySlot));
        if (sprinting && attr == Attribute.MOVEMENT_SPEED) out.put(SPRINT.id(), SPRINT);
        for (AttributeModifier m : located) out.put(m.id(), m);
        return out;
    }

    /** 장비 칸 배열(EquipmentSlot.values() 순서): 주 손·보조 손·발·다리·가슴·머리·몸. armor 는 {머리, 가슴, 다리, 발} 순. */
    static ItemStack[] slotsOf(ItemStack mainHand, ItemStack offHand, ItemStack[] armor) {
        ItemStack[] out = new ItemStack[EquipmentSlot.values().length];
        java.util.Arrays.fill(out, ItemStack.EMPTY);
        out[EquipmentSlot.MAINHAND.ordinal()] = mainHand == null ? ItemStack.EMPTY : mainHand;
        out[EquipmentSlot.OFFHAND.ordinal()] = offHand == null ? ItemStack.EMPTY : offHand;
        if (armor != null) {
            EquipmentSlot[] order = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < 4 && i < armor.length; i++) if (armor[i] != null) out[order[i].ordinal()] = armor[i];
        }
        return out;
    }

    /**
     * 속성 값. armor 는 {머리, 가슴, 다리, 발} 순(없으면 빈 스택). 달리기 수정자는 넣지 않는다 — 플레이어 상태가 필요하면
     * {@link #value(Player, Attribute)} 를 쓴다.
     */
    public static double value(Attribute attr, ItemStack mainHand, ItemStack[] armor, List<MobEffectInstance> effects) {
        return compute(attr, base(attr), modifiers(attr, slotsOf(mainHand, null, armor), effects, false).values());
    }

    /** 플레이어의 현재 장비·효과·달리기로 계산(소유 region 스레드 — 인벤토리를 읽는다). */
    public static double value(Player p, Attribute attr) {
        ItemStack[] bySlot = new ItemStack[EquipmentSlot.values().length];
        for (EquipmentSlot s : EquipmentSlot.values()) bySlot[s.ordinal()] = p.equipment(s);
        return compute(attr, base(attr), modifiers(attr, bySlot, p.vitals().activeEffects(), p.sprinting(), p.locatedModifiers(attr)).values());
    }
}
