// Bukkit LeatherArmorMeta — 염색 색은 minecraft:dyed_color 컴포넌트에서 읽고 쓴다(표시 항목 HIDE_DYE 는 부모가 showInTooltip 에 반영).
package org.domi.furnace.compat.paper;

import java.util.EnumSet;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.inventory.SerializableMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.domi.furnace.api.item.DyedColor;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.api.item.ItemStack;

@DelegateDeserialization(SerializableMeta.class)
public final class CompatMetaLeatherArmor extends CompatItemMeta implements LeatherArmorMeta {

    /** 기본 가죽 색 — Paper CraftItemFactory.DEFAULT_LEATHER_COLOR. */
    public static final Color DEFAULT_LEATHER_COLOR = Color.fromRGB(0xA06540);

    CompatMetaLeatherArmor(ItemStack work, EnumSet<ItemFlag> flags, ItemPdc pdc) { super(work, flags, pdc); }

    @Override
    MetaKind kind() { return MetaKind.LEATHER_ARMOR; }

    @Override
    public Color getColor() {
        DyedColor dye = work.get(ItemComponents.DYED_COLOR);
        return dye == null ? DEFAULT_LEATHER_COLOR : Color.fromRGB(dye.rgb() & 0xFFFFFF);
    }

    @Override
    public void setColor(Color color) {
        work = color == null ? work.without(ItemComponents.DYED_COLOR)
                : work.with(ItemComponents.DYED_COLOR, new DyedColor(color.asRGB(), !flags.contains(ItemFlag.HIDE_DYE)));
    }

    @Override
    public boolean isDyed() { return work.get(ItemComponents.DYED_COLOR) != null; }

    @Override
    public CompatMetaLeatherArmor clone() { return (CompatMetaLeatherArmor) super.clone(); }

    @Override
    void serializeKind(Map<String, Object> out) {
        // Paper 는 기본 가죽 색과 같은 색을 직접 정해도 적지 않는다(CraftMetaLeatherArmor.hasColor(meta))
        if (!DEFAULT_LEATHER_COLOR.equals(getColor())) out.put("color", getColor());
    }

    @Override
    void deserializeKind(Map<String, Object> map) {
        Object c = map.get("color");
        if (c == null) return;
        if (!(c instanceof Color color)) throw new IllegalArgumentException("color(" + c + ") is not a valid " + Color.class);
        setColor(color);
    }
}
