// 순정 .mca 월드(들)와 Furnace generateWithFeatures 출력을 블록 단위로 대조하는 독립 하네스(코덱 비공유)
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;

public class Harness {
    static final int MIN_Y = -64, H = 384;

    // ---------- NBT / region (프로젝트 코드 미사용) ----------
    static Object readPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: { int n = in.readInt(); byte[] b = new byte[n]; in.readFully(b); return b; }
            case 8: return in.readUTF();
            case 9: { int t = in.readByte(); int n = in.readInt(); List<Object> l = new ArrayList<>(n);
                      for (int i = 0; i < n; i++) l.add(readPayload(in, t)); return l; }
            case 10: { Map<String, Object> m = new HashMap<>();
                       while (true) { int t = in.readByte(); if (t == 0) break; String k = in.readUTF(); m.put(k, readPayload(in, t)); }
                       return m; }
            case 11: { int n = in.readInt(); int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = in.readInt(); return a; }
            case 12: { int n = in.readInt(); long[] a = new long[n]; for (int i = 0; i < n; i++) a[i] = in.readLong(); return a; }
            default: throw new IOException("bad tag " + type);
        }
    }

    static final Map<Path, byte[]> regionCache = new HashMap<>();

    @SuppressWarnings("unchecked")
    static Map<String, Object> readChunk(Path world, int cx, int cz) throws IOException {
        Path p = world.resolve("region").resolve("r." + (cx >> 5) + "." + (cz >> 5) + ".mca");
        if (!regionCache.containsKey(p)) regionCache.put(p, Files.exists(p) ? Files.readAllBytes(p) : null);
        byte[] f = regionCache.get(p);
        if (f == null) return null;
        int idx = ((cx & 31) + (cz & 31) * 32) * 4;
        int off = ((f[idx] & 0xFF) << 16) | ((f[idx + 1] & 0xFF) << 8) | (f[idx + 2] & 0xFF);
        if (off == 0) return null;
        int pos = off * 4096;
        int len = ByteBuffer.wrap(f, pos, 4).getInt();
        int comp = f[pos + 4];
        InputStream raw = new ByteArrayInputStream(f, pos + 5, len - 1);
        InputStream dec = switch (comp) {
            case 1 -> new GZIPInputStream(raw);
            case 2 -> new InflaterInputStream(raw);
            case 3 -> raw;
            default -> throw new IOException("compression " + comp);
        };
        DataInputStream in = new DataInputStream(new BufferedInputStream(dec));
        int t = in.readByte();
        in.readUTF();
        return (Map<String, Object>) readPayload(in, t);
    }

    // ---------- interning ----------
    static final Map<String, Integer> intern = new HashMap<>();
    static final List<String> keys = new ArrayList<>();

    static int id(String key) {
        Integer v = intern.get(key);
        if (v == null) { v = keys.size(); intern.put(key, v); keys.add(key); }
        return v;
    }

    static String nameOfKey(String key) {
        int s = key.indexOf(';');
        return s < 0 ? key : key.substring(0, s);
    }

    @SuppressWarnings("unchecked")
    static String paletteKey(Map<String, Object> e) {
        String name = (String) e.get("Name");
        Map<String, Object> props = (Map<String, Object>) e.get("Properties");
        if (props == null || props.isEmpty()) return name;
        TreeMap<String, String> sorted = new TreeMap<>();
        for (var en : props.entrySet()) sorted.put(en.getKey(), (String) en.getValue());
        StringBuilder sb = new StringBuilder(name).append(';');
        boolean first = true;
        for (var en : sorted.entrySet()) {
            if (!first) sb.append(',');
            sb.append(en.getKey()).append('=').append(en.getValue());
            first = false;
        }
        return sb.toString();
    }

    static int unpack(long[] data, int bits, int i) {
        int per = 64 / bits;
        long w = data[i / per];
        return (int) ((w >>> ((i % per) * bits)) & ((1L << bits) - 1));
    }

    static int bitsFor(int n) { return 32 - Integer.numberOfLeadingZeros(n - 1); }

    /** 순정 청크 → short[16*16*384] (idx = (y-MIN_Y)*256 + z*16 + x), 값=interned key id */
    @SuppressWarnings("unchecked")
    static short[] vanillaBlocks(Map<String, Object> root) {
        short[] out = new short[16 * 16 * H];
        short air = (short) id("minecraft:air");
        Arrays.fill(out, air);
        List<Object> sections = (List<Object>) root.get("sections");
        for (Object so : sections) {
            Map<String, Object> s = (Map<String, Object>) so;
            int sy = (Byte) s.get("Y");
            if (sy < -4 || sy > 19) continue;
            Map<String, Object> bs = (Map<String, Object>) s.get("block_states");
            if (bs == null) continue;
            List<Object> pal = (List<Object>) bs.get("palette");
            short[] pids = new short[pal.size()];
            for (int i = 0; i < pids.length; i++) pids[i] = (short) id(paletteKey((Map<String, Object>) pal.get(i)));
            long[] data = (long[]) bs.get("data");
            int base = (sy - (MIN_Y >> 4)) * 16 * 256;
            if (pids.length == 1 || data == null) {
                Arrays.fill(out, base, base + 4096, pids[0]);
            } else {
                int bits = Math.max(4, bitsFor(pids.length));
                for (int i = 0; i < 4096; i++) out[base + i] = pids[unpack(data, bits, i)];
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static String[] vanillaBiomes(Map<String, Object> root) {
        String[] out = new String[1536];
        List<Object> sections = (List<Object>) root.get("sections");
        for (Object so : sections) {
            Map<String, Object> s = (Map<String, Object>) so;
            int sy = (Byte) s.get("Y");
            if (sy < -4 || sy > 19) continue;
            Map<String, Object> b = (Map<String, Object>) s.get("biomes");
            List<Object> pal = (List<Object>) b.get("palette");
            long[] data = (long[]) b.get("data");
            int base = (sy + 4) * 64;
            for (int i = 0; i < 64; i++) {
                int pi = (pal.size() == 1 || data == null) ? 0 : unpack(data, Math.max(1, bitsFor(pal.size())), i);
                out[base + i] = (String) pal.get(pi);
            }
        }
        return out;
    }

    static int[] unpackHeights(long[] d) {
        int[] r = new int[256];
        for (int i = 0; i < 256; i++) r[i] = unpack(d, 9, i);
        return r;
    }

    @SuppressWarnings("unchecked")
    static long[] vanillaHeightmap(Map<String, Object> root, String name) {
        Map<String, Object> hm = (Map<String, Object>) root.get("Heightmaps");
        return hm == null ? null : (long[]) hm.get(name);
    }

    // ---------- main ----------
    static class Stats {
        long positions, furnaceMismatch, unstable, hard, explained, novel;
        long vvA2, vvB;
        long biomeCells, biomeMismatch, wsCols, wsMismatch, mbCols, mbMismatch, cols;
        Map<String, long[]> hardPairs = new HashMap<>(), allPairs = new HashMap<>();
        Map<String, Map<String, long[]>> pairsByBiome = new HashMap<>();
        Map<String, long[]> hardCat = new TreeMap<>();
        long[] hardByYBand = new long[H / 32];
        Map<String, long[]> byBiome = new HashMap<>(); // biome -> {chunks, furnaceMM, hard, vvA2, unstable}
    }

    static void bump(Map<String, long[]> m, String k) { m.computeIfAbsent(k, x -> new long[1])[0]++; }

    // vanilla 쪽 블록이 Furnace 미구현 feature에서만 나오는 블록이면 "미구현 feature"
    static final Set<String> MISSING_FEATURE_BLOCKS = Set.of(
        "minecraft:dripstone_block", "minecraft:pointed_dripstone", "minecraft:moss_block", "minecraft:moss_carpet",
        "minecraft:cave_vines", "minecraft:cave_vines_plant", "minecraft:azalea", "minecraft:flowering_azalea",
        "minecraft:azalea_leaves", "minecraft:flowering_azalea_leaves", "minecraft:rooted_dirt", "minecraft:hanging_roots",
        "minecraft:spore_blossom", "minecraft:big_dripleaf", "minecraft:big_dripleaf_stem", "minecraft:small_dripleaf",
        "minecraft:sculk", "minecraft:sculk_vein", "minecraft:sculk_catalyst", "minecraft:sculk_sensor", "minecraft:sculk_shrieker",
        "minecraft:mossy_cobblestone", "minecraft:cobblestone", "minecraft:spawner", "minecraft:chest",
        "minecraft:red_mushroom_block", "minecraft:brown_mushroom_block", "minecraft:mushroom_stem",
        "minecraft:sugar_cane", "minecraft:cactus", "minecraft:bamboo", "minecraft:bamboo_sapling",
        "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:sea_pickle",
        "minecraft:brain_coral_block", "minecraft:tube_coral_block", "minecraft:bubble_coral_block", "minecraft:fire_coral_block", "minecraft:horn_coral_block",
        "minecraft:brain_coral", "minecraft:tube_coral", "minecraft:bubble_coral", "minecraft:fire_coral", "minecraft:horn_coral",
        "minecraft:brain_coral_fan", "minecraft:tube_coral_fan", "minecraft:bubble_coral_fan", "minecraft:fire_coral_fan", "minecraft:horn_coral_fan",
        "minecraft:calcite", "minecraft:amethyst_block", "minecraft:budding_amethyst");
    static final Set<String> REPLACEABLE_SET = Set.of(
        "minecraft:stone", "minecraft:deepslate", "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
        "minecraft:dirt", "minecraft:gravel", "minecraft:clay", "minecraft:sand", "minecraft:coal_ore", "minecraft:iron_ore",
        "minecraft:copper_ore", "minecraft:gold_ore", "minecraft:redstone_ore", "minecraft:lapis_ore", "minecraft:diamond_ore",
        "minecraft:emerald_ore", "minecraft:deepslate_coal_ore", "minecraft:deepslate_iron_ore", "minecraft:deepslate_copper_ore",
        "minecraft:deepslate_gold_ore", "minecraft:deepslate_redstone_ore", "minecraft:deepslate_lapis_ore",
        "minecraft:deepslate_diamond_ore", "minecraft:deepslate_emerald_ore", "minecraft:raw_iron_block", "minecraft:raw_copper_block",
        "minecraft:infested_stone", "minecraft:infested_deepslate", "minecraft:water", "minecraft:lava", "minecraft:cave_air",
        "minecraft:air", "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium", "minecraft:coarse_dirt", "minecraft:snow_block");

    static String category(String an, String fn, boolean propsOnly) {
        if (MISSING_FEATURE_BLOCKS.contains(an) && !propsOnly) return "A. 미구현 feature 블록(순정에만 존재)";
        if (an.equals("minecraft:clay") && Set.of("minecraft:stone", "minecraft:deepslate", "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:gravel").contains(fn)) return "A. 미구현 feature 블록(순정에만 존재)";
        boolean tree = an.endsWith("_leaves") || an.endsWith("_log") || an.endsWith("_wood") || an.equals("minecraft:vine") || an.equals("minecraft:mangrove_roots")
            || fn.endsWith("_leaves") || fn.endsWith("_log") || fn.endsWith("_wood") || fn.equals("minecraft:vine");
        if (tree) return "B. 나무/덩굴(잎·통나무·vine)";
        if (REPLACEABLE_SET.contains(an) && REPLACEABLE_SET.contains(fn)) return "C. 광석/치환 블롭·지표·유체 (구현된 feature 간 충돌)";
        return "D. 기타(식물·꽃·블록 상태 등)";
    }

    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        int cx0 = Integer.parseInt(a[1]), cx1 = Integer.parseInt(a[2]);
        int cz0 = Integer.parseInt(a[3]), cz1 = Integer.parseInt(a[4]);
        String outPrefix = a[5];
        List<Path> worlds = new ArrayList<>();
        for (int i = 6; i < a.length; i++) worlds.add(Path.of(a[i]));

        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        List<String> biomeNames = registries.entries("minecraft:worldgen/biome");
        NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, registries);

        int[] furToIntern = new int[reg.totalStateCount() + 64];
        Arrays.fill(furToIntern, -1);

        Stats st = new Stats();
        List<String> perChunk = new ArrayList<>();
        perChunk.add("cx,cz,furnaceMM,unstable,hard,explained,novel,vvA2,vvB,biomeMM,wsMM,mbMM,nonFull");
        long t0 = System.currentTimeMillis();
        int done = 0, total = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
        int nonFull = 0;

        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                List<Map<String, Object>> vroots = new ArrayList<>();
                for (Path w : worlds) {
                    Map<String, Object> r = readChunk(w, cx, cz);
                    if (r == null) throw new IllegalStateException("missing chunk " + cx + "," + cz + " in " + w);
                    if (!"minecraft:full".equals(r.get("Status"))) { nonFull++; }
                    vroots.add(r);
                }
                short[][] v = new short[worlds.size()][];
                for (int i = 0; i < v.length; i++) v[i] = vanillaBlocks(vroots.get(i));

                ChunkColumn col = "carvers".equals(System.getenv("FURNACE_MODE")) ? src.generateWithCarvers(cx, cz) : src.generateWithFeatures(cx, cz);
                short[] f = new short[16 * 16 * H];
                for (int y = MIN_Y; y < MIN_Y + H; y++)
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++) {
                            int sid = col.getBlock(x, y, z);
                            if (sid >= furToIntern.length) furToIntern = Arrays.copyOf(furToIntern, sid + 1024);
                            int iv = furToIntern[sid];
                            if (iv < 0) {
                                String k = reg.canonicalKeyOf(sid);
                                if (k == null) k = "unknown#" + sid;
                                iv = id(k);
                                furToIntern[sid] = iv;
                            }
                            f[(y - MIN_Y) * 256 + z * 16 + x] = (short) iv;
                        }

                long fm = 0, un = 0, hd = 0, ex = 0, nv = 0, vA2 = 0, vB = 0; Map<String, long[]> localHard = new HashMap<>();
                short[] a0 = v[0];
                for (int p = 0; p < a0.length; p++) {
                    short av = a0[p];
                    boolean unstable = false;
                    for (int i = 1; i < v.length; i++) if (v[i][p] != av) { unstable = true; if (i == 1) vA2++; else vB++; }
                    // 두 번째/세 번째 변종이 서로 다른 경우도 vv 카운트에 반영(i==1 → A2, i>=2 → B)
                    short fv = f[p];
                    if (unstable) un++;
                    if (fv != av) {
                        fm++;
                        String an = nameOfKey(keys.get(av)), fn = nameOfKey(keys.get(fv));
                        String pair = an.equals(fn) ? "props:" + an : an + " -> " + fn;
                        bump(st.allPairs, pair);
                        if (!unstable) {
                            hd++;
                            bump(st.hardPairs, pair); bump(localHard, pair);
                            bump(st.hardCat, category(an, fn, an.equals(fn)));
                            st.hardByYBand[(p / 256) / 32]++;
                        } else {
                            boolean eq = false;
                            for (int i = 1; i < v.length; i++) if (v[i][p] == fv) { eq = true; break; }
                            if (eq) ex++; else nv++;
                        }
                    }
                }

                // biomes
                String[] vb = vanillaBiomes(vroots.get(0));
                int[] fb = src.sampleBiomes(cx, cz);
                int bm = 0;
                for (int i = 0; i < 1536; i++) {
                    String fn = (fb[i] >= 0 && fb[i] < biomeNames.size()) ? biomeNames.get(fb[i]) : "?";
                    if (!fn.equals(vb[i])) bm++;
                }
                // heightmaps
                int wsm = 0, mbm = 0;
                long[] vws = vanillaHeightmap(vroots.get(0), "WORLD_SURFACE");
                long[] vmb = vanillaHeightmap(vroots.get(0), "MOTION_BLOCKING");
                if (vws != null) {
                    int[] x1 = unpackHeights(vws), x2 = unpackHeights(col.worldSurfaceHeightmap());
                    for (int i = 0; i < 256; i++) if (x1[i] != x2[i]) wsm++;
                }
                if (vmb != null) {
                    int[] x1 = unpackHeights(vmb), x2 = unpackHeights(col.motionBlockingHeightmap());
                    for (int i = 0; i < 256; i++) if (x1[i] != x2[i]) mbm++;
                }

                st.positions += a0.length; st.furnaceMismatch += fm; st.unstable += un; st.hard += hd;
                st.explained += ex; st.novel += nv; st.vvA2 += vA2; st.vvB += vB;
                st.biomeCells += 1536; st.biomeMismatch += bm;
                st.cols += 256; st.wsMismatch += wsm; st.mbMismatch += mbm;
                {
                    Map<String, Integer> cnt = new HashMap<>();
                    for (int c = 0; c < 16; c++) cnt.merge(vb[32 * 16 + c], 1, Integer::sum);
                    String dom = Collections.max(cnt.entrySet(), Map.Entry.comparingByValue()).getKey();
                    long[] r = st.byBiome.computeIfAbsent(dom, k -> new long[5]);
                    r[0]++; r[1] += fm; r[2] += hd; r[3] += vA2; r[4] += un; Map<String, long[]> pb = st.pairsByBiome.computeIfAbsent(dom, k -> new HashMap<>()); for (var le : localHard.entrySet()) pb.computeIfAbsent(le.getKey(), k -> new long[1])[0] += le.getValue()[0];
                }
                perChunk.add(cx + "," + cz + "," + fm + "," + un + "," + hd + "," + ex + "," + nv + "," + vA2 + "," + vB
                    + "," + bm + "," + wsm + "," + mbm + "," + (nonFull));
                done++;
                if (done % 10 == 0)
                    System.err.printf("[%d/%d] %.0fs  F=%d hard=%d%n", done, total, (System.currentTimeMillis() - t0) / 1000.0, st.furnaceMismatch, st.hard);
            }
        }

        Files.write(Path.of(outPrefix + "_chunks.csv"), perChunk);
        List<String> rep = new ArrayList<>();
        rep.add("chunks=" + done + " positions=" + st.positions + " nonFullChunks(cumulative)=" + nonFull + " worlds=" + worlds.size());
        rep.add("furnaceMismatch=" + st.furnaceMismatch + String.format(" (%.4f%%)", 100.0 * st.furnaceMismatch / st.positions));
        rep.add("vanillaUnstablePositions=" + st.unstable + String.format(" (%.4f%%)  A1!=A2:%d  A1!=B:%d", 100.0 * st.unstable / st.positions, st.vvA2, st.vvB));
        rep.add("hard(furnace!=vanilla, all variants agree)=" + st.hard + String.format(" (%.4f%%)", 100.0 * st.hard / st.positions));
        rep.add("ambiguous explained(furnace==some variant)=" + st.explained + " novel(furnace==no variant)=" + st.novel);
        rep.add(String.format("biome mismatch cells=%d/%d (%.4f%%)", st.biomeMismatch, st.biomeCells, 100.0 * st.biomeMismatch / st.biomeCells));
        rep.add(String.format("heightmap WORLD_SURFACE col mismatch=%d/%d, MOTION_BLOCKING=%d/%d", st.wsMismatch, st.cols, st.mbMismatch, st.cols));
        rep.add("=== hard mismatches by category ===");
        for (var ce : st.hardCat.entrySet()) rep.add(String.format("  %9d (%5.1f%% of hard)  %s", ce.getValue()[0], 100.0 * ce.getValue()[0] / Math.max(1, st.hard), ce.getKey()));
        rep.add("=== by dominant surface biome (avg per chunk: furnaceMM, hard, A1!=A2, unstable) ===");
        st.byBiome.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).forEach(e -> {
            long[] r = e.getValue();
            rep.add(String.format("  %-34s n=%3d  F=%7.0f  hard=%7.0f  vvA2=%7.0f  unst=%7.0f", e.getKey(), r[0],
                r[1] / (double) r[0], r[2] / (double) r[0], r[3] / (double) r[0], r[4] / (double) r[0]));
        });
        for (var be : st.pairsByBiome.entrySet()) {
            long tot = st.byBiome.get(be.getKey())[0];
            rep.add("--- hard pairs in biome " + be.getKey() + " (n=" + tot + " chunks; counts are per-chunk avg) ---");
            be.getValue().entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).limit(8)
                .forEach(e -> rep.add(String.format("     %8.1f  %s", e.getValue()[0] / (double) tot, e.getKey())));
        }
        rep.add("hard mismatches by y band (32 blocks each from y=-64):");
        for (int i = 0; i < st.hardByYBand.length; i++) rep.add(String.format("  y[%4d..%4d) %d", MIN_Y + i * 32, MIN_Y + i * 32 + 32, st.hardByYBand[i]));
        for (var which : List.of("hard", "all")) {
            Map<String, long[]> m = which.equals("hard") ? st.hardPairs : st.allPairs;
            rep.add("=== top pairs (" + which + ") vanilla -> furnace ===");
            m.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).limit(60)
                .forEach(e -> rep.add(String.format("  %9d  %s", e.getValue()[0], e.getKey())));
        }
        Files.write(Path.of(outPrefix + "_report.txt"), rep);
        rep.forEach(System.out::println);
    }
}
