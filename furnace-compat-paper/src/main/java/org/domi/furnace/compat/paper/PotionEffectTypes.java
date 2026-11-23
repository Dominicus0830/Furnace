// 바닐라 상태 효과 종류를 Paper 와 같은 CraftPotionEffectType 으로 만든다(T-264 P2d, 생성자는 패키지 전용 — CraftAccess).
package org.domi.furnace.compat.paper;

import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

public final class PotionEffectTypes {

    private PotionEffectTypes() {}

    /** PotionEffectType 의 상수를 만들 때 부른다. */
    public static PotionEffectType vanilla(int id, NamespacedKey key, PotionEffectTypeCategory category, boolean instant, String legacyName) {
        return CraftAccess.newPotionEffectType(id, key, category, instant, legacyName);
    }
}
