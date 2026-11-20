package org.bukkit.inventory.meta.trim;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Translatable;

/** 장식 무늬(Paper 1.21.1 TrimPattern). 데이터 팩이 더한 값은 {@link Registry#TRIM_PATTERN} 에서 찾는다. Adventure 의 description() 은 이 호환 계층에 없다. */
public interface TrimPattern extends Keyed, Translatable {

    TrimPattern SENTRY = getTrimPattern("sentry");
    TrimPattern DUNE = getTrimPattern("dune");
    TrimPattern COAST = getTrimPattern("coast");
    TrimPattern WILD = getTrimPattern("wild");
    TrimPattern WARD = getTrimPattern("ward");
    TrimPattern EYE = getTrimPattern("eye");
    TrimPattern VEX = getTrimPattern("vex");
    TrimPattern TIDE = getTrimPattern("tide");
    TrimPattern SNOUT = getTrimPattern("snout");
    TrimPattern RIB = getTrimPattern("rib");
    TrimPattern SPIRE = getTrimPattern("spire");
    TrimPattern WAYFINDER = getTrimPattern("wayfinder");
    TrimPattern SHAPER = getTrimPattern("shaper");
    TrimPattern SILENCE = getTrimPattern("silence");
    TrimPattern RAISER = getTrimPattern("raiser");
    TrimPattern HOST = getTrimPattern("host");
    TrimPattern FLOW = getTrimPattern("flow");
    TrimPattern BOLT = getTrimPattern("bolt");

    private static TrimPattern getTrimPattern(String key) {
        return Registry.TRIM_PATTERN.getOrThrow(NamespacedKey.minecraft(key));
    }

    @Override
    NamespacedKey getKey();
}
