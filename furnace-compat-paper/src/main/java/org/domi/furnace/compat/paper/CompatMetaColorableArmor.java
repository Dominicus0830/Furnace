// Bukkit ColorableArmorMeta(가죽 투구·갑옷·바지·신발·늑대 갑옷, Paper COLORABLE_ARMOR) — 방어구 장식(CompatMetaArmor)에 염색 색을 더한다.
package org.domi.furnace.compat.paper;

import java.util.EnumSet;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.inventory.SerializableMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ColorableArmorMeta;
import org.domi.furnace.api.item.ItemStack;

@DelegateDeserialization(SerializableMeta.class)
public final class CompatMetaColorableArmor extends CompatMetaArmor implements ColorableArmorMeta {

    CompatMetaColorableArmor(ItemStack work, EnumSet<ItemFlag> flags, ItemPdc pdc) {
        super(MetaKind.COLORABLE_ARMOR, work, flags, pdc);
    }

    @Override
    public Color getColor() { return LeatherColor.color(this); }

    @Override
    public void setColor(Color color) { LeatherColor.setColor(this, color); }

    @Override
    public boolean isDyed() { return LeatherColor.dyed(this); }

    @Override
    public CompatMetaColorableArmor clone() { return (CompatMetaColorableArmor) super.clone(); }

    @Override
    void serializeKind(Map<String, Object> out) {
        super.serializeKind(out);
        LeatherColor.serialize(this, out);
    }

    @Override
    void deserializeKind(Map<String, Object> map) {
        super.deserializeKind(map);
        LeatherColor.deserialize(this, map);
    }
}
