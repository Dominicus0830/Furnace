package org.bukkit.craftbukkit.inventory;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.domi.furnace.compat.paper.CompatItemMeta;
import org.domi.furnace.compat.paper.CompatMetaLeatherArmor;
import org.domi.furnace.compat.paper.MetaKind;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/** Paper 1.21.1 CraftItemFactory 의 자리 — 재료를 보고 종류별 메타({@link MetaKind})를 골라 주고, 메타끼리 비교·변환한다. */
public final class CraftItemFactory implements ItemFactory {

    private static final CraftItemFactory INSTANCE = new CraftItemFactory();

    private CraftItemFactory() {}

    public static CraftItemFactory instance() { return INSTANCE; }

    @Override
    public ItemMeta getItemMeta(Material material) {
        if (material == null) throw new IllegalArgumentException("Material cannot be null");
        return metaFor(material, null);
    }

    /** meta 가 null 이면 새 빈 메타, 아니면 그 재료 종류로 옮긴 메타. */
    private static CompatItemMeta metaFor(Material material, CompatItemMeta meta) {
        if (material == Material.AIR) return null;
        String type = material.getKey().toString();
        if (!material.isItem()) {
            // 아이템이 아닌 재료는 일반 메타(Paper: new CraftMetaItem(meta))
            return meta == null ? CompatItemMeta.of(MetaKind.UNSPECIFIC, new org.domi.furnace.api.item.ItemStack(type, 1)) : meta.asKind(MetaKind.UNSPECIFIC);
        }
        MetaKind kind = MetaKind.of(type);
        if (meta == null) return CompatItemMeta.of(kind, new org.domi.furnace.api.item.ItemStack(type, 1));
        return meta.asKind(kind);
    }

    @Override
    public boolean isApplicable(ItemMeta meta, ItemStack stack) {
        if (stack == null) return false;
        return isApplicable(meta, stack.getType());
    }

    @Override
    public boolean isApplicable(ItemMeta meta, Material material) {
        if (material == null || meta == null) return false;
        return own(meta, "Meta of %s not created by %s").applicableTo(material.getKey().toString());
    }

    @Override
    public boolean equals(ItemMeta meta1, ItemMeta meta2) {
        if (meta1 == meta2) return true;
        if (meta1 != null) {
            own(meta1, "First meta of %s does not belong to %s");
        } else {
            return own(meta2, "Second meta of %s does not belong to %s").isEmpty();
        }
        if (meta2 != null) {
            own(meta2, "Second meta of %s does not belong to %s");
        } else {
            return ((CompatItemMeta) meta1).isEmpty();
        }
        return meta1.equals(meta2);
    }

    @Override
    public ItemMeta asMetaFor(ItemMeta meta, ItemStack stack) {
        if (stack == null) throw new IllegalArgumentException("ItemStack stack cannot be null");
        return asMetaFor(meta, stack.getType());
    }

    @Override
    public ItemMeta asMetaFor(ItemMeta meta, Material material) {
        if (material == null) throw new IllegalArgumentException("Material cannot be null");
        return metaFor(material, own(meta, "ItemMeta of %s not created by %s"));
    }

    private static CompatItemMeta own(ItemMeta meta, String message) {
        if (!(meta instanceof CompatItemMeta cm)) {
            throw new IllegalArgumentException(String.format(message, meta != null ? meta.getClass().toString() : "null", CraftItemFactory.class.getName()));
        }
        return cm;
    }

    @Override
    public Color getDefaultLeatherColor() { return CompatMetaLeatherArmor.DEFAULT_LEATHER_COLOR; }

    @Override
    public ItemStack createItemStack(String input) {
        throw new UnsupportedFeature("ItemFactory#createItemStack", "the compat adapter has no item string parser yet");
    }

    @Override
    public Material getSpawnEgg(EntityType type) {
        if (type == null || type == EntityType.UNKNOWN) return null;
        Material egg = Material.matchMaterial(type.getKey() + "_spawn_egg");
        return egg != null && egg.isItem() ? egg : null;
    }

    @Override
    public ItemStack enchantItem(Entity entity, ItemStack item, int level, boolean allowTreasures) { throw enchantUnsupported(); }

    @Override
    public ItemStack enchantItem(World world, ItemStack item, int level, boolean allowTreasures) { throw enchantUnsupported(); }

    @Override
    public ItemStack enchantItem(ItemStack item, int level, boolean allowTreasures) { throw enchantUnsupported(); }

    @Override
    public ItemStack enchantWithLevels(ItemStack itemStack, int levels, boolean allowTreasure, java.util.Random random) { throw enchantUnsupported(); }

    private static UnsupportedFeature enchantUnsupported() {
        return new UnsupportedFeature("ItemFactory#enchantItem / enchantWithLevels", "the compat adapter cannot call the server's enchanting logic yet");
    }

    @Override
    public String getI18NDisplayName(ItemStack item) {
        throw new UnsupportedFeature("ItemFactory#getI18NDisplayName", "the compat adapter has no translation table");
    }

    @Override
    public ItemStack ensureServerConversions(ItemStack item) {
        if (item == null) throw new IllegalArgumentException("item cannot be null");
        return item.clone();
    }
}
