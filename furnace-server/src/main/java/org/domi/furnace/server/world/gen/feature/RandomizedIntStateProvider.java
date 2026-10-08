// randomized_int_state_provider — source가 고른 state의 정수 property를 values에서 뽑은 값으로 교체(vanilla 소비 순서: source → values)
package org.domi.furnace.server.world.gen.feature;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class RandomizedIntStateProvider implements StateProvider {

    private final StateProvider source;
    private final String property;
    private final TreeIntProvider values;
    private final BlockRegistry registry;
    private final Pattern propertyPattern;
    private final Map<Long, Integer> cache = new ConcurrentHashMap<>();

    public RandomizedIntStateProvider(StateProvider source, String property, TreeIntProvider values, BlockRegistry registry) {
        this.source = source;
        this.property = property;
        this.values = values;
        this.registry = registry;
        this.propertyPattern = Pattern.compile("([\\[,])" + Pattern.quote(property) + "=([^,\\]]*)");
    }

    @Override
    public int getState(WorldgenRandom random, int x, int y, int z) {
        int state = source.getState(random, x, y, z);
        String key = registry.canonicalKeyOf(state);
        if (!propertyPattern.matcher(key).find()) return state;     // 속성이 없으면 values를 뽑지 않고 그대로 반환
        int value = values.sample(random);
        return cache.computeIfAbsent(((long) state << 32) | (value & 0xffffffffL), k -> withValue(key, value));
    }

    private int withValue(String key, int value) {
        Matcher m = propertyPattern.matcher(key);
        m.find();
        String replaced = key.substring(0, m.start()) + m.group(1) + property + "=" + value + key.substring(m.end());
        int br = replaced.indexOf('[');
        String name = replaced.substring(0, br);
        Map<String, String> props = new HashMap<>();
        for (String kv : replaced.substring(br + 1, replaced.length() - 1).split(",")) {
            int eq = kv.indexOf('=');
            props.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        return registry.stateIdOrAir(name, props);
    }
}
