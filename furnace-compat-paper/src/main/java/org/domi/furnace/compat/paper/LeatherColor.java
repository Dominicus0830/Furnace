// 가죽 방어구 염색 색의 읽기·쓰기·직렬화 — 가죽 말 갑옷 메타(CompatMetaLeatherArmor)와 염색 가능 방어구 메타(CompatMetaColorableArmor)가 함께 쓴다.
// 색은 minecraft:dyed_color 컴포넌트에서 읽고 쓴다(표시 항목 HIDE_DYE 는 부모가 showInTooltip 에 반영).
package org.domi.furnace.compat.paper;

import java.util.Map;
import org.bukkit.Color;
import org.bukkit.inventory.ItemFlag;
import org.domi.furnace.api.item.DyedColor;
import org.domi.furnace.api.item.ItemComponents;

final class LeatherColor {

    /** 기본 가죽 색 — Paper CraftItemFactory.DEFAULT_LEATHER_COLOR. */
    static final Color DEFAULT_LEATHER_COLOR = Color.fromRGB(0xA06540);

    private LeatherColor() {}

    static Color color(CompatItemMeta m) {
        DyedColor dye = m.work.get(ItemComponents.DYED_COLOR);
        return dye == null ? DEFAULT_LEATHER_COLOR : Color.fromRGB(dye.rgb() & 0xFFFFFF);
    }

    static void setColor(CompatItemMeta m, Color color) {
        if (color == null) m.clearComponent("minecraft:dyed_color");
        else m.work = m.work.with(ItemComponents.DYED_COLOR, new DyedColor(color.asRGB(), !m.flags.contains(ItemFlag.HIDE_DYE)));
    }

    static boolean dyed(CompatItemMeta m) { return m.work.get(ItemComponents.DYED_COLOR) != null; }

    static void serialize(CompatItemMeta m, Map<String, Object> out) {
        // Paper 는 기본 가죽 색과 같은 색을 직접 정해도 적지 않는다(CraftMetaLeatherArmor.hasColor(meta))
        if (!DEFAULT_LEATHER_COLOR.equals(color(m))) out.put("color", color(m));
    }

    static void deserialize(CompatItemMeta m, Map<String, Object> map) {
        Object c = map.get("color");
        if (c == null) return;
        if (!(c instanceof Color color)) throw new IllegalArgumentException("color(" + c + ") is not a valid " + Color.class);
        setColor(m, color);
    }
}
