package org.bukkit.enchantments;

import java.util.Objects;

/** 마법 부여대의 제안 하나: 단서로 보이는 마법 부여와 레벨, 비용(요구 레벨). */
public class EnchantmentOffer {

    private Enchantment enchantment;
    private int enchantmentLevel;
    private int cost;

    public EnchantmentOffer(Enchantment enchantment, int enchantmentLevel, int cost) {
        this.enchantment = enchantment;
        this.enchantmentLevel = enchantmentLevel;
        this.cost = cost;
    }

    public Enchantment getEnchantment() { return enchantment; }

    public void setEnchantment(Enchantment enchantment) {
        if (enchantment == null) throw new IllegalArgumentException("The enchantment may not be null!");
        this.enchantment = enchantment;
    }

    public int getEnchantmentLevel() { return enchantmentLevel; }

    public void setEnchantmentLevel(int enchantmentLevel) {
        if (enchantmentLevel <= 0) throw new IllegalArgumentException("enchantmentLevel must be positive (was " + enchantmentLevel + ")");
        this.enchantmentLevel = enchantmentLevel;
    }

    public int getCost() { return cost; }

    public void setCost(int cost) {
        if (cost <= 0) throw new IllegalArgumentException("cost must be positive (was " + cost + ")");
        this.cost = cost;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EnchantmentOffer that = (EnchantmentOffer) o;
        return enchantmentLevel == that.enchantmentLevel && cost == that.cost && Objects.equals(enchantment, that.enchantment);
    }

    @Override
    public int hashCode() { return Objects.hash(enchantment, enchantmentLevel, cost); }
}
