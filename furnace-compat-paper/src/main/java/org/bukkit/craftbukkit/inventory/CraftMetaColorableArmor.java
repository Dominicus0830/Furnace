// Bukkit ColorableArmorMeta(가죽 투구·갑옷·바지·신발·늑대 갑옷, Paper COLORABLE_ARMOR) — 방어구 장식(CraftMetaArmor)에 염색 색을 더한다.
package org.bukkit.craftbukkit.inventory;

import java.util.EnumSet;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ColorableArmorMeta;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.compat.paper.ItemPdc;
import org.domi.furnace.compat.paper.MetaKind;

@DelegateDeserialization(SerializableMeta.class)
public class CraftMetaColorableArmor extends CraftMetaArmor implements ColorableArmorMeta {

    CraftMetaColorableArmor(ItemStack work, EnumSet<ItemFlag> flags, ItemPdc pdc) {
        super(MetaKind.COLORABLE_ARMOR, work, flags, pdc);
    }

    @Override
    public Color getColor() { return CraftMetaLeatherArmor.colorOf(this); }

    @Override
    public void setColor(Color color) { CraftMetaLeatherArmor.writeColor(this, color); }

    @Override
    public boolean isDyed() { return CraftMetaLeatherArmor.dyedIn(this); }

    @Override
    public CraftMetaColorableArmor clone() { return (CraftMetaColorableArmor) super.clone(); }

    @Override
    void serializeKind(Map<String, Object> out) {
        super.serializeKind(out);
        CraftMetaLeatherArmor.serializeColor(this, out);
    }

    @Override
    void deserializeKind(Map<String, Object> map) {
        super.deserializeKind(map);
        CraftMetaLeatherArmor.deserializeColor(this, map);
    }
}
