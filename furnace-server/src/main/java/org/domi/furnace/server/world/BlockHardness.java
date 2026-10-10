// 블록별 destroyTime(바닐라 BlockBehaviour.Properties.destroyTime) — blocks/hardness_1.21.1.txt.
package org.domi.furnace.server.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class BlockHardness {

    private static volatile BlockHardness instance;

    private final Map<String, Float> byName;

    private BlockHardness(Map<String, Float> byName) { this.byName = byName; }

    public static BlockHardness get() {
        BlockHardness h = instance;
        if (h == null) {
            synchronized (BlockHardness.class) {
                if (instance == null) instance = load();
                h = instance;
            }
        }
        return h;
    }

    private static BlockHardness load() {
        Map<String, Float> m = new HashMap<>();
        var in = BlockHardness.class.getClassLoader().getResourceAsStream("blocks/hardness_1.21.1.txt");
        if (in == null) throw new IllegalStateException("blocks/hardness_1.21.1.txt 없음");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sp = line.indexOf(' ');
                m.put(line.substring(0, sp), Float.parseFloat(line.substring(sp + 1)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BlockHardness(Map.copyOf(m));
    }

    /** 경도. 모르는 블록은 0. -1 = 부술 수 없음(기반암 등). */
    public float of(String blockName) {
        Float f = byName.get(blockName);
        return f == null ? 0f : f;
    }
}
