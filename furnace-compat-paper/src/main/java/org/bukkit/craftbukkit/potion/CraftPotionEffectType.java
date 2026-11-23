package org.bukkit.craftbukkit.potion;

import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

/**
 * Paper CraftPotionEffectType — 바닐라 상태 효과 종류 하나(T-264 P2d, 옛 PotionEffectType$Vanilla). 값은 minecraft:mob_effect 1.21.1 정의다.
 * Paper 생성자는 NMS MobEffect 를 받아 같게 만들 수 없으므로 Furnace 생성자는 패키지 전용이고 {@code PotionEffectTypes} 가 CraftAccess 로 만든다.
 */
public class CraftPotionEffectType extends PotionEffectType {

    private final int id;
    private final NamespacedKey key;
    private final PotionEffectTypeCategory category;
    private final boolean instant;
    private final String legacyName;

    CraftPotionEffectType(int id, NamespacedKey key, PotionEffectTypeCategory category, boolean instant, String legacyName) {
        this.id = id;
        this.key = key;
        this.category = category;
        this.instant = instant;
        this.legacyName = legacyName;
    }

    @Override
    public NamespacedKey getKey() { return key; }

    @Override
    public PotionEffect createEffect(int duration, int amplifier) { return new PotionEffect(this, instant ? 1 : duration, amplifier); }

    @Override
    public boolean isInstant() { return instant; }

    @Override
    public PotionEffectTypeCategory getCategory() { return category; }

    @Override
    public double getDurationModifier() { return 1.0D; }

    @Override
    public int getId() { return id; }

    @Override
    public String getName() { return legacyName != null ? legacyName : key.toString(); }
}
