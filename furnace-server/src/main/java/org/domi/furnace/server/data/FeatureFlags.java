// 기능 플래그(바닐라 FeatureFlags) — 1.21.1: minecraft:vanilla · minecraft:bundle · minecraft:trade_rebalance.
package org.domi.furnace.server.data;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class FeatureFlags {

    public static final String VANILLA = "minecraft:vanilla", BUNDLE = "minecraft:bundle", TRADE_REBALANCE = "minecraft:trade_rebalance";
    /** 등록 순서(바닐라 REGISTRY). */
    public static final List<String> ALL = List.of(VANILLA, BUNDLE, TRADE_REBALANCE);
    public static final Set<String> DEFAULT = Set.of(VANILLA);

    private FeatureFlags() {}

    public static boolean isSubset(Set<String> flags, Set<String> enabled) { return enabled.containsAll(flags); }

    /** 바닐라 FeatureFlags.printMissingFlags — 등록 순서로 ", " 연결. */
    public static String printMissing(Set<String> enabled, Set<String> required) {
        return ALL.stream().filter(f -> required.contains(f) && !enabled.contains(f)).collect(Collectors.joining(", "));
    }

    /** 알려진 플래그만 남긴다(모르는 이름은 바닐라처럼 경고 후 무시). */
    public static Set<String> known(Iterable<String> names, org.slf4j.Logger log) {
        Set<String> out = new LinkedHashSet<>();
        for (String n : names) {
            String id = ResourceId.parse(n).toString();
            if (ALL.contains(id)) out.add(id);
            else log.warn("Unknown feature flag: {}", id);
        }
        return out;
    }
}
