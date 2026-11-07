// 바닐라 LivingEntity.getVisibilityPercent / getArmorCoverPercentage: 몹이 겨냥할 때 대상이 얼마나 눈에 띄는가(0..1).
// 웅크리면 ×0.8, 투명이면 ×0.7×방어구 가림(0.1 아래는 0.1), 몹과 같은 종류의 머리 아이템을 쓰면 ×0.5.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.item.ItemTools;

public final class Visibility {
    private Visibility() {}

    /** 방어구 칸 4개 중 차 있는 비율(getArmorCoverPercentage). */
    public static float armorCoverPercentage(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
        int worn = 0;
        for (ItemStack s : new ItemStack[] {head, chest, legs, feet}) if (s != null && !s.isEmpty()) worn++;
        return (float) worn / 4.0F;
    }

    /** 보는 쪽 종류와 짝이 맞는 머리 아이템인가(해골↔해골 머리 …). 철골렘이 쓴 좀비 머리처럼 짝이 안 맞으면 거짓. */
    static boolean matchingHead(String lookerType, String headItem) {
        return switch (lookerType) {
            case "minecraft:skeleton" -> headItem.equals("minecraft:skeleton_skull");
            case "minecraft:zombie" -> headItem.equals("minecraft:zombie_head");
            case "minecraft:piglin", "minecraft:piglin_brute" -> headItem.equals("minecraft:piglin_head");
            case "minecraft:creeper" -> headItem.equals("minecraft:creeper_head");
            default -> false;
        };
    }

    /**
     * getVisibilityPercent(lookingEntity). looker 가 null 이면 머리 아이템은 보지 않는다.
     * 계산 순서와 실수 연산(double 곱, 가림은 float → double)을 바닐라 그대로 둔다.
     */
    public static double percent(boolean discrete, boolean invisible, float armorCover, ItemStack head, Entity looker) {
        double d = 1.0;
        if (discrete) d *= 0.8;
        if (invisible) {
            float f = armorCover;
            if (f < 0.1F) f = 0.1F;
            d *= 0.7 * (double) f;
        }
        if (looker != null && head != null && !head.isEmpty() && matchingHead(looker.type().name(), ItemTools.itemName(head))) d *= 0.5;
        return d;
    }
}
