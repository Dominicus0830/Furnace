// blocks.json distill 리소스를 로드해 블록명→default state id와 전체 state 수를 제공
package org.domi.furnace.server.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class BlockRegistry {

    private final Map<String, Integer> defaultStateIds;
    private final int totalStateCount;
    private final Map<Integer, String> nameByDefaultId;

    private BlockRegistry(Map<String, Integer> ids, int totalStateCount) {
        this.defaultStateIds = ids;
        this.totalStateCount = totalStateCount;
        this.nameByDefaultId = new HashMap<>();
        for (var e : ids.entrySet()) nameByDefaultId.put(e.getValue(), e.getKey());
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
        return new BlockRegistry(ids, total);
    }

    public int defaultStateId(String blockId) {
        Integer id = defaultStateIds.get(blockId);
        if (id == null) throw new IllegalArgumentException("미지원 블록: " + blockId);
        return id;
    }

    /** state id → 블록명. default state만 매핑(우리 블록셋 한정); 미지원 id는 air. */
    public String nameOf(int stateId) {
        return nameByDefaultId.getOrDefault(stateId, "minecraft:air");
    }

    /** 블록명 → state id. 미지원이면 air(0). 바닐라 read 폴백용. */
    public int stateIdOrAir(String blockName) {
        Integer id = defaultStateIds.get(blockName);
        return id == null ? 0 : id;
    }

    public int totalStateCount() {
        return totalStateCount;
    }

    /** direct 팰릿 bitsPerEntry = ceil(log2(전체 state 수)). */
    public int directBits() {
        return 32 - Integer.numberOfLeadingZeros(totalStateCount - 1);
    }
}
