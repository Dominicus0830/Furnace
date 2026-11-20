// Bukkit LeatherArmorMeta(가죽 말 갑옷, Paper LEATHER_ARMOR) — 염색 색은 minecraft:dyed_color 컴포넌트에서 읽고 쓴다.
// 가죽 투구·갑옷·바지·신발·늑대 갑옷은 장식(trim)도 다루는 CompatMetaColorableArmor 가 맡는다.
package org.domi.furnace.compat.paper;

import java.util.EnumSet;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.inventory.SerializableMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.domi.furnace.api.item.ItemStack;

@DelegateDeserialization(SerializableMeta.class)
public final class CompatMetaLeatherArmor extends CompatItemMeta implements LeatherArmorMeta {

    /** 기본 가죽 색 — Paper CraftItemFactory.DEFAULT_LEATHER_COLOR. */
    public static final Color DEFAULT_LEATHER_COLOR = LeatherColor.DEFAULT_LEATHER_COLOR;

    CompatMetaLeatherArmor(ItemStack work, EnumSet<ItemFlag> flags, ItemPdc pdc) {
        super(work, flags, pdc);
    }

    @Override
    MetaKind kind() { return MetaKind.LEATHER_ARMOR; }

    @Override
    public Color getColor() { return LeatherColor.color(this); }

    @Override
    public void setColor(Color color) { LeatherColor.setColor(this, color); }

    @Override
    public boolean isDyed() { return LeatherColor.dyed(this); }

    @Override
    public CompatMetaLeatherArmor clone() { return (CompatMetaLeatherArmor) super.clone(); }

    @Override
    void serializeKind(Map<String, Object> out) { LeatherColor.serialize(this, out); }

    @Override
    void deserializeKind(Map<String, Object> map) { LeatherColor.deserialize(this, map); }
}
