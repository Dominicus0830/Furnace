// 바닐라 Equipable 인 아이템과 그 장비 칸(1.21.1): ArmorItem(머리·가슴·다리·발), 겉날개(가슴), 방패(왼손), 호박 조각·머리/해골(머리).
// 말·늑대 갑옷은 몸통 칸이라 플레이어가 쓰지 못한다(MAINHAND).
package org.domi.furnace.server.menu;

import java.util.Set;

public final class EquipSlots {

    private static final Set<String> HEAD = Set.of("carved_pumpkin", "player_head", "zombie_head", "creeper_head", "dragon_head", "piglin_head",
            "skeleton_skull", "wither_skeleton_skull");

    private EquipSlots() { }

    /** 아이템 이름(minecraft: 접두사 유무 무관) → 장비 칸. 장비가 아니면 MAINHAND. */
    public static EquipSlot forItem(String name) {
        if (name == null) return EquipSlot.MAINHAND;
        String n = name.startsWith("minecraft:") ? name.substring(10) : name;
        if (n.endsWith("_helmet") || HEAD.contains(n)) return EquipSlot.HEAD;
        if (n.endsWith("_chestplate") || n.equals("elytra")) return EquipSlot.CHEST;
        if (n.endsWith("_leggings")) return EquipSlot.LEGS;
        if (n.endsWith("_boots")) return EquipSlot.FEET;
        if (n.equals("shield")) return EquipSlot.OFFHAND;
        return EquipSlot.MAINHAND;
    }
}
