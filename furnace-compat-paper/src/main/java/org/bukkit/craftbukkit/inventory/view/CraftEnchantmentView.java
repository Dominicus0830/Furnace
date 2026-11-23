package org.bukkit.craftbukkit.inventory.view;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventoryEnchanting;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.event.inventory.InventoryType;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/** 마법 부여대 창(Paper 와 같은 이름 — T-264). 제안은 PrepareItemEnchantEvent 안에서만 안다(offers). */
public class CraftEnchantmentView extends CraftInventoryView implements org.bukkit.inventory.view.EnchantmentView {
    private final CraftInventoryEnchanting top;
    private final org.bukkit.enchantments.EnchantmentOffer[] offers;

    public CraftEnchantmentView(CraftPlayer player, CraftInventoryEnchanting top, String title, org.domi.furnace.api.item.ItemStack cursor,
                                org.bukkit.enchantments.EnchantmentOffer[] offers) {
        super(player, top, InventoryType.ENCHANTING, title, cursor);
        this.top = top;
        this.offers = offers;
    }

    @Override
    public org.bukkit.inventory.EnchantingInventory getTopInventory() { return top; }

    @Override
    public int getEnchantmentSeed() {
        throw new UnsupportedFeature("EnchantmentView#getEnchantmentSeed", "the Furnace API does not expose the enchantment seed of the player");
    }

    @Override
    public void setEnchantmentSeed(int seed) {
        throw new UnsupportedFeature("EnchantmentView#setEnchantmentSeed", "the Furnace API cannot change the enchantment seed of the player");
    }

    private org.bukkit.enchantments.EnchantmentOffer[] offers(String what) {
        if (offers == null) throw new UnsupportedFeature(what, "offers are only exposed while Furnace decides them (PrepareItemEnchantEvent)");
        return offers;
    }

    /** Paper 처럼 이벤트의 배열 그대로(고치면 이벤트 결과가 바뀐다). */
    @Override
    public org.bukkit.enchantments.EnchantmentOffer[] getOffers() { return offers("EnchantmentView#getOffers"); }

    @Override
    public void setOffers(org.bukkit.enchantments.EnchantmentOffer[] offers) {
        var mine = offers("EnchantmentView#setOffers");
        if (offers.length != 3) throw new IllegalArgumentException("There must be 3 offers given");
        System.arraycopy(offers, 0, mine, 0, 3);
    }
}
