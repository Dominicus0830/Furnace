package org.bukkit.inventory.meta.trim;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Translatable;

/** 장식 재료(Paper 1.21.1 TrimMaterial). 데이터 팩이 더한 값은 {@link Registry#TRIM_MATERIAL} 에서 찾는다. Adventure 의 description() 은 이 호환 계층에 없다. */
public interface TrimMaterial extends Keyed, Translatable {

    TrimMaterial QUARTZ = getTrimMaterial("quartz");
    TrimMaterial IRON = getTrimMaterial("iron");
    TrimMaterial NETHERITE = getTrimMaterial("netherite");
    TrimMaterial REDSTONE = getTrimMaterial("redstone");
    TrimMaterial COPPER = getTrimMaterial("copper");
    TrimMaterial GOLD = getTrimMaterial("gold");
    TrimMaterial EMERALD = getTrimMaterial("emerald");
    TrimMaterial DIAMOND = getTrimMaterial("diamond");
    TrimMaterial LAPIS = getTrimMaterial("lapis");
    TrimMaterial AMETHYST = getTrimMaterial("amethyst");

    private static TrimMaterial getTrimMaterial(String key) {
        return Registry.TRIM_MATERIAL.getOrThrow(NamespacedKey.minecraft(key));
    }

    @Override
    NamespacedKey getKey();
}
