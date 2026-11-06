// 서버 블록 상태 표(BlockRegistry)에서 블록 종류별 속성·값·기본 상태를 읽어 API 로 보여 준다. 종류마다 처음 물을 때 한 번 계산해 둔다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockType;
import org.domi.furnace.api.world.BlockTypes;
import org.domi.furnace.server.world.BlockRegistry;

public final class BlockTypesImpl implements BlockTypes {

    private final BlockRegistry registry;
    private final Map<String, BlockType> cache = new ConcurrentHashMap<>();

    public BlockTypesImpl(BlockRegistry registry) { this.registry = registry; }

    @Override
    public Optional<BlockType> get(String id) {
        String key = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        BlockType t = cache.get(key);
        if (t != null) return Optional.of(t);
        if (!registry.blockNames().contains(key)) return Optional.empty();
        return Optional.of(cache.computeIfAbsent(key, this::build));
    }

    @Override
    public Collection<String> ids() { return Collections.unmodifiableSet(new TreeSet<>(registry.blockNames())); }

    private BlockType build(String id) {
        BlockData def = ApiConvert.block(registry.defaultStateId(id), registry);
        var ids = new TreeSet<>(registry.stateIdsForBlock(id));
        // 상태 번호 순으로 훑으면 속성마다 값이 번호 순서(바닐라 순서)로 처음 나온다
        Map<String, List<String>> values = new LinkedHashMap<>();
        int states = 0;
        for (int sid : ids) {
            String key = registry.canonicalKeyOf(sid);
            if (key == null) continue;
            states++;
            int semi = key.indexOf(';');
            if (semi < 0) continue;
            for (String kv : key.substring(semi + 1).split(",")) {
                int eq = kv.indexOf('=');
                List<String> vs = values.computeIfAbsent(kv.substring(0, eq), k -> new ArrayList<>());
                String v = kv.substring(eq + 1);
                if (!vs.contains(v)) vs.add(v);
            }
        }
        List<BlockType.PropertyInfo> props = new ArrayList<>();
        for (var e : new java.util.TreeMap<>(values).entrySet()) props.add(new BlockType.PropertyInfo(e.getKey(), kind(e.getValue()), e.getValue()));
        return new Type(id, List.copyOf(props), def, Math.max(states, 1));
    }

    private static BlockType.PropertyInfo.Kind kind(List<String> vs) {
        boolean bool = true, num = true;
        for (String v : vs) {
            if (!v.equals("true") && !v.equals("false")) bool = false;
            if (!v.matches("-?\\d+")) num = false;
        }
        return bool ? BlockType.PropertyInfo.Kind.BOOLEAN : num ? BlockType.PropertyInfo.Kind.INTEGER : BlockType.PropertyInfo.Kind.ENUM;
    }

    private record Type(String id, List<PropertyInfo> properties, BlockData defaultData, int stateCount) implements BlockType {}
}
