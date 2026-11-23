// Bukkit LeatherArmorMeta(가죽 말 갑옷, Paper LEATHER_ARMOR) — 염색 색은 minecraft:dyed_color 컴포넌트에서 읽고 쓴다.
// 가죽 투구·갑옷·바지·신발·늑대 갑옷은 장식(trim)도 다루는 CraftMetaColorableArmor 가 맡는다.
package org.bukkit.craftbukkit.inventory;

import java.util.EnumSet;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.domi.furnace.api.item.DyedColor;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.compat.paper.ItemPdc;
import org.domi.furnace.compat.paper.MetaKind;

@DelegateDeserialization(SerializableMeta.class)
class CraftMetaLeatherArmor extends CraftMetaItem implements LeatherArmorMeta {

    CraftMetaLeatherArmor(ItemStack work, EnumSet<ItemFlag> flags, ItemPdc pdc) {
        super(work, flags, pdc);
    }

    @Override
    MetaKind kind() { return MetaKind.LEATHER_ARMOR; }

    @Override
    public Color getColor() { return colorOf(this); }

    @Override
    public void setColor(Color color) { writeColor(this, color); }

    @Override
    public boolean isDyed() { return dyedIn(this); }

    @Override
    public CraftMetaLeatherArmor clone() { return (CraftMetaLeatherArmor) super.clone(); }

    @Override
    void serializeKind(Map<String, Object> out) { serializeColor(this, out); }

    @Override
    void deserializeKind(Map<String, Object> map) { deserializeColor(this, map); }

    // ---- 가죽 염색 색 — 이 메타와 염색 가능 방어구 메타(CraftMetaColorableArmor)가 함께 쓴다(Paper 도 이 클래스의 정적 도우미를 함께 쓴다).
    // 색은 minecraft:dyed_color 컴포넌트에서 읽고 쓴다(표시 항목 HIDE_DYE 는 부모가 showInTooltip 에 반영).

    static Color colorOf(CraftMetaItem m) {
        DyedColor dye = m.work.get(ItemComponents.DYED_COLOR);
        return dye == null ? CraftItemFactory.DEFAULT_LEATHER_COLOR : Color.fromRGB(dye.rgb() & 0xFFFFFF);
    }

    static void writeColor(CraftMetaItem m, Color color) {
        if (color == null) m.clearComponent("minecraft:dyed_color");
        else m.work = m.work.with(ItemComponents.DYED_COLOR, new DyedColor(color.asRGB(), !m.flags.contains(ItemFlag.HIDE_DYE)));
    }

    static boolean dyedIn(CraftMetaItem m) { return m.work.get(ItemComponents.DYED_COLOR) != null; }

    static void serializeColor(CraftMetaItem m, Map<String, Object> out) {
        // Paper 는 기본 가죽 색과 같은 색을 직접 정해도 적지 않는다(CraftMetaLeatherArmor.hasColor(meta))
        if (!CraftItemFactory.DEFAULT_LEATHER_COLOR.equals(colorOf(m))) out.put("color", colorOf(m));
    }

    static void deserializeColor(CraftMetaItem m, Map<String, Object> map) {
        Object c = map.get("color");
        if (c == null) return;
        if (!(c instanceof Color color)) throw new IllegalArgumentException("color(" + c + ") is not a valid " + Color.class);
        writeColor(m, color);
    }
}
