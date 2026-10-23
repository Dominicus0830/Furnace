// state id ↔ (블록 이름, 프로퍼티) 조회·변경 + 바닐라 BlockState.mirror().rotate() 결과표(blocktransforms_1.21.1.txt.gz).
package org.domi.furnace.server.world.gen.structure.template;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.BlockStateKey;

public final class BlockStateTable {

    public static final int NONE = 0, LEFT_RIGHT = 1, FRONT_BACK = 2;
    public static final int ROT_NONE = 0, ROT_CW90 = 1, ROT_CW180 = 2, ROT_CCW90 = 3;

    private static volatile BlockStateTable INSTANCE;

    private final BlockRegistry registry;
    private final int stateCount;
    private final String[] keys;       // canonical key
    private final String[] names;      // 블록 이름
    private final int[] semi;          // 키에서 ';' 위치(없으면 -1)
    private final int[][] transforms;  // [stateId] → 12개(mirror*4 + rot), 변화 없으면 null
    private final ConcurrentHashMap<Long, Integer> withCache = new ConcurrentHashMap<>();

    private BlockStateTable(BlockRegistry registry) {
        this.registry = registry;
        this.stateCount = registry.totalStateCount();
        this.keys = new String[stateCount];
        this.names = new String[stateCount];
        this.semi = new int[stateCount];
        for (int i = 0; i < stateCount; i++) {
            String k = registry.canonicalKeyOf(i);
            if (k == null) k = registry.nameOf(i);
            keys[i] = k;
            int sc = k.indexOf(';');
            semi[i] = sc;
            names[i] = sc < 0 ? k : k.substring(0, sc);
        }
        this.transforms = loadTransforms(stateCount);
    }

    public static BlockStateTable of(BlockRegistry registry) {
        BlockStateTable t = INSTANCE;
        if (t == null || t.registry != registry) {
            synchronized (BlockStateTable.class) {
                t = INSTANCE;
                if (t == null || t.registry != registry) INSTANCE = t = new BlockStateTable(registry);
            }
        }
        return t;
    }

    private static int[][] loadTransforms(int stateCount) {
        int[][] t = new int[stateCount][];
        try (InputStream raw = BlockStateTable.class.getClassLoader().getResourceAsStream("blocks/blocktransforms_1.21.1.txt.gz")) {
            if (raw == null) throw new IllegalStateException("블록 변환표 리소스 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] p = line.split(" ");
                int id = Integer.parseInt(p[0]);
                int[] row = new int[12];
                for (int i = 0; i < 12; i++) row[i] = Integer.parseInt(p[1 + i]);
                t[id] = row;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return t;
    }

    public BlockRegistry registry() { return registry; }

    public String name(int state) { return names[state]; }

    public String key(int state) { return keys[state]; }

    /** 프로퍼티 값 문자열, 없으면 null. */
    public String prop(int state, String prop) {
        int sc = semi[state];
        if (sc < 0) return null;
        String k = keys[state];
        int from = sc + 1;
        int plen = prop.length();
        while (from < k.length()) {
            int comma = k.indexOf(',', from);
            int end = comma < 0 ? k.length() : comma;
            if (end - from > plen && k.charAt(from + plen) == '=' && k.startsWith(prop, from)) {
                return k.substring(from + plen + 1, end);
            }
            if (comma < 0) break;
            from = comma + 1;
        }
        return null;
    }

    public boolean has(int state, String prop) { return prop(state, prop) != null; }

    public boolean isBlock(int state, String blockName) { return names[state].equals(blockName); }

    /** 프로퍼티 하나를 바꾼 state id. 프로퍼티/값이 없으면 원래 state. */
    public int with(int state, String prop, String value) {
        long ck = ((long) state << 32) ^ ((long) prop.hashCode() * 31 + value.hashCode()) & 0xFFFFFFFFL;
        Integer cached = withCache.get(ck);
        if (cached != null) return cached;
        int result = computeWith(state, prop, value);
        withCache.put(ck, result);
        return result;
    }

    public int with(int state, String prop, boolean value) { return with(state, prop, value ? "true" : "false"); }

    private int computeWith(int state, String prop, String value) {
        int sc = semi[state];
        if (sc < 0) return state;
        TreeMap<String, String> map = new TreeMap<>();
        String k = keys[state];
        int from = sc + 1;
        boolean found = false;
        while (from <= k.length()) {
            int comma = k.indexOf(',', from);
            int end = comma < 0 ? k.length() : comma;
            int eq = k.indexOf('=', from);
            String pk = k.substring(from, eq);
            String pv = k.substring(eq + 1, end);
            if (pk.equals(prop)) { pv = value; found = true; }
            map.put(pk, pv);
            if (comma < 0) break;
            from = comma + 1;
        }
        if (!found) return state;
        int id = registry.exactStateId(BlockStateKey.of(names[state], map));
        return id < 0 ? state : id;
    }

    /** 바닐라 state.mirror(mirror).rotate(rotation). */
    public int transform(int state, int mirror, int rotation) {
        int[] row = transforms[state];
        return row == null ? state : row[mirror * 4 + rotation];
    }
}
