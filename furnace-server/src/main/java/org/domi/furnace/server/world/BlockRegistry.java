// blocks.json distill 리소스를 로드해 블록명→default state id, 전체 state 수, full blockstate→id를 제공
package org.domi.furnace.server.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class BlockRegistry {

    private final Map<String, Integer> defaultStateIds;
    private final int totalStateCount;
    private final Map<Integer, String> nameByDefaultId;
    private final Map<String, Integer> stateIds; // canonical key → exact global id (리소스 없으면 빈 맵)
    private final Map<Integer, String> keyByAnyId; // exact global id → canonical key(name 또는 name;k=v,...). stateIds 없으면 빈 맵

    private BlockRegistry(Map<String, Integer> ids, int totalStateCount, Map<String, Integer> stateIds) {
        this.defaultStateIds = ids;
        this.totalStateCount = totalStateCount;
        this.nameByDefaultId = new HashMap<>();
        for (var e : ids.entrySet()) nameByDefaultId.put(e.getValue(), e.getKey());
        this.stateIds = stateIds;
        this.keyByAnyId = new HashMap<>();
        for (var e : stateIds.entrySet()) keyByAnyId.put(e.getValue(), e.getKey());
    }

    public static BlockRegistry load(String resourcePath) {
        Map<String, Integer> ids = new HashMap<>();
        int total = 0;
        ClassLoader cl = BlockRegistry.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("블록 리소스 없음: " + resourcePath);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) {
                    // "# states <N>"
                    String[] h = line.split("\\s+");
                    if (h.length == 3 && h[1].equals("states")) {
                        total = Integer.parseInt(h[2]);
                    }
                    continue;
                }
                int sp = line.indexOf(' ');
                if (sp < 0) continue;
                ids.put(line.substring(0, sp), Integer.parseInt(line.substring(sp + 1).strip()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BlockRegistry(ids, total, loadStateIds(blockstatesPath(resourcePath), cl));
    }

    /** "blocks/1.21.1.txt" → "blocks/blockstates_1.21.1.txt". */
    private static String blockstatesPath(String resourcePath) {
        int slash = resourcePath.lastIndexOf('/');
        String dir = slash < 0 ? "" : resourcePath.substring(0, slash + 1);
        String file = resourcePath.substring(slash + 1);
        return dir + "blockstates_" + file;
    }

    /** full blockstate 테이블. 리소스 없으면 빈 맵(back-compat: default 경로만). */
    private static Map<String, Integer> loadStateIds(String resourcePath, ClassLoader cl) {
        Map<String, Integer> map = new HashMap<>();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) return map;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // "<canonicalKey> <id>" — canonical key엔 공백 없음(name;k=v,...). id는 마지막 공백 뒤.
                int sp = line.lastIndexOf(' ');
                if (sp < 0) continue;
                map.put(line.substring(0, sp), Integer.parseInt(line.substring(sp + 1).strip()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return map;
    }

    public int defaultStateId(String blockId) {
        Integer id = defaultStateIds.get(blockId);
        if (id == null) throw new IllegalArgumentException("미지원 블록: " + blockId);
        return id;
    }

    /** state id → 블록명. exact blockstate 테이블 있으면 전체 state id 커버(props 제외); 없으면(back-compat) default state만. 미지원 id는 air. */
    public String nameOf(int stateId) {
        String key = keyByAnyId.get(stateId);
        if (key != null) {
            int semi = key.indexOf(';');
            return semi < 0 ? key : key.substring(0, semi);
        }
        return nameByDefaultId.getOrDefault(stateId, "minecraft:air");
    }

    /** state id → canonical key(name 또는 name;k=v,...). exact blockstate 테이블 없거나 미지원 id면 null. */
    public String canonicalKeyOf(int stateId) {
        return keyByAnyId.get(stateId);
    }

    /** 블록명 → default state id. 미지원이면 air(0). */
    public int stateIdOrAir(String blockName) {
        Integer id = defaultStateIds.get(blockName);
        return id == null ? 0 : id;
    }

    /** canonical key(name 또는 name;k=v,...) → exact global state id. 미스/테이블 부재 시 default 폴백, 그것도 없으면 air. */
    public int stateIdOrAir(String name, Map<String, String> props) {
        if (props != null && !props.isEmpty() && !stateIds.isEmpty()) {
            Integer exact = stateIds.get(BlockStateKey.of(name, props));
            if (exact != null) return exact;
        }
        return stateIdOrAir(name);
    }

    public int totalStateCount() {
        return totalStateCount;
    }

    // 블록명 -> 소속 state id 불변 집합. 최초 호출 때 전체 키를 한 번만 훑어 색인한다
    // (이전에는 호출마다 2만 6천 개 키를 문자열 비교해, 월드젠 데이터 로드가 수 초씩 걸렸다).
    private volatile java.util.Map<String, Set<Integer>> blockIndex;

    private java.util.Map<String, Set<Integer>> blockIndex() {
        java.util.Map<String, Set<Integer>> idx = blockIndex;
        if (idx == null) {
            HashMap<String, HashSet<Integer>> build = new HashMap<>();
            for (var e : defaultStateIds.entrySet()) {
                build.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(e.getValue());
            }
            for (var e : stateIds.entrySet()) {
                String key = e.getKey();
                int sep = key.indexOf(';');
                String name = sep < 0 ? key : key.substring(0, sep);
                build.computeIfAbsent(name, k -> new HashSet<>()).add(e.getValue());
            }
            HashMap<String, Set<Integer>> frozen = new HashMap<>(build.size() * 2);
            for (var e : build.entrySet()) frozen.put(e.getKey(), Set.copyOf(e.getValue()));
            blockIndex = idx = frozen;
        }
        return idx;
    }

    /** 블록명에 속한 모든 state id 집합(불변). exact blockstate 테이블이 없으면 default state만 반환. */
    public Set<Integer> stateIdsForBlock(String blockName) {
        Set<Integer> ids = blockIndex().get(blockName);
        return ids != null ? ids : Set.of();
    }

    /** direct 팰릿 bitsPerEntry = ceil(log2(전체 state 수)). */
    public int directBits() {
        return 32 - Integer.numberOfLeadingZeros(totalStateCount - 1);
    }
}
