// 청크의 구조물 시작점·참조(바닐라 ChunkAccess.structureStarts / structuresRefences) — 질의용 압축 형태 + 청크 NBT `structures` 왕복.
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

/**
 * 생성 경로: 이 청크가 시작점인 StructureStart(살아 있는 객체 — 저장할 때 조각 상자를 그때 값으로 쓴다)와,
 * 참조하는 시작점들의 기하(이 청크 기둥에 걸치는 조각만)를 생성 스레드에서 미리 만들어 둔다.
 * 불러온 경로: 원본 `structures` NBT 를 그대로 들고 있다가 다시 쓴다(손실 없음). 참조 시작점의 기하는
 * 처음 질의할 때 시작 청크(로드돼 있으면)에서 찾아 기억한다.
 * 참조 키는 바닐라 ChunkPos.asLong(x 아래 32비트, z 위 32비트).
 */
public final class ChunkStructures {

    public static final ChunkStructures EMPTY = new ChunkStructures(List.of(), new Ref[0], new LinkedHashMap<>(), null, new Ref[0]);

    /** 시작점 하나의 기하: 구조물 id, 시작 청크, adjustBoundingBox 적용 상자, 조각 상자들(6개씩). */
    public record Ref(String id, int chunkX, int chunkZ, int[] box, int[] pieces) {
        /** StructureStart.getBoundingBox().isInside(pos) */
        public boolean isInside(int x, int y, int z) { return inside(box, 0, x, y, z); }

        /** StructureManager.structureHasPieceAt — 조각 하나라도 pos 를 담는가. */
        public boolean hasPieceAt(int x, int y, int z) {
            for (int i = 0; i < pieces.length; i += 6) if (inside(pieces, i, x, y, z)) return true;
            return false;
        }

        private static boolean inside(int[] b, int o, int x, int y, int z) {
            return x >= b[o] && x <= b[o + 3] && z >= b[o + 2] && z <= b[o + 5] && y >= b[o + 1] && y <= b[o + 4];
        }

        /** 청크 기둥(cx,cz)에 걸치는 조각만 남긴 사본(그 청크 안 질의에는 이것으로 충분하다). */
        Ref clipTo(int cx, int cz) {
            int x0 = cx << 4, z0 = cz << 4, x1 = x0 + 15, z1 = z0 + 15;
            int n = 0;
            int[] tmp = new int[pieces.length];
            for (int i = 0; i < pieces.length; i += 6) {
                if (pieces[i + 3] >= x0 && pieces[i] <= x1 && pieces[i + 5] >= z0 && pieces[i + 2] <= z1) {
                    System.arraycopy(pieces, i, tmp, n, 6);
                    n += 6;
                }
            }
            return new Ref(id, chunkX, chunkZ, box, java.util.Arrays.copyOf(tmp, n));
        }
    }

    private final List<StructureStart> liveStarts;   // 생성 경로의 이 청크 시작점(저장용)
    private final Ref[] ownRefs;                     // 이 청크 시작점의 기하(다른 청크가 참조를 풀 때)
    private final Map<String, long[]> references;    // 구조물 id → 시작 청크(ChunkPos.asLong), 넣은 순서
    private final NbtCompound raw;                   // 불러온 원본(있으면 그대로 다시 쓴다)
    private volatile Ref[] resolved;                 // 참조 시작점 기하(이 청크에 걸치는 조각만). null = 아직 못 풀었다

    private ChunkStructures(List<StructureStart> liveStarts, Ref[] ownRefs, Map<String, long[]> references, NbtCompound raw, Ref[] resolved) {
        this.liveStarts = liveStarts;
        this.ownRefs = ownRefs;
        this.references = references;
        this.raw = raw;
        this.resolved = resolved;
    }

    public static long chunkLong(int cx, int cz) { return (cx & 0xFFFFFFFFL) | (cz & 0xFFFFFFFFL) << 32; }

    /** 시작점의 현재 기하(조각 상자 값 복사). */
    public static Ref refOf(StructureStart s) {
        BoundingBox b = s.boundingBox();
        int[] box = {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
        List<StructurePiece> ps = s.pieces();
        int[] pieces = new int[ps.size() * 6];
        for (int i = 0; i < ps.size(); i++) {
            BoundingBox p = ps.get(i).boundingBox();
            int o = i * 6;
            pieces[o] = p.minX(); pieces[o + 1] = p.minY(); pieces[o + 2] = p.minZ();
            pieces[o + 3] = p.maxX(); pieces[o + 4] = p.maxY(); pieces[o + 5] = p.maxZ();
        }
        return new Ref(s.structureId(), s.chunkX(), s.chunkZ(), box, pieces);
    }

    /**
     * 생성 직후(장식까지 끝난 뒤, 생성 스레드에서): own = 이 청크가 시작점인 것, nearby = 반경 8 청크 안의 시작점 후보.
     * 바닐라 ChunkGenerator.createReferences: 반경 8 안에서 adjustBoundingBox 상자가 청크의 xz 범위에 걸치는 시작점을 참조한다
     * (x 바깥 → z 안쪽 순서로 훑는다).
     */
    public static ChunkStructures generated(int cx, int cz, List<StructureStart> own, List<StructureStart> nearby) {
        int x0 = cx << 4, z0 = cz << 4, x1 = x0 + 15, z1 = z0 + 15;
        List<StructureStart> sorted = new ArrayList<>();
        for (StructureStart s : nearby) {
            if (Math.abs(s.chunkX() - cx) > 8 || Math.abs(s.chunkZ() - cz) > 8) continue;
            BoundingBox b = s.boundingBox();
            if (b.maxX() >= x0 && b.minX() <= x1 && b.maxZ() >= z0 && b.minZ() <= z1) sorted.add(s);
        }
        if (own.isEmpty() && sorted.isEmpty()) return EMPTY;
        sorted.sort(java.util.Comparator.comparingInt(StructureStart::chunkX).thenComparingInt(StructureStart::chunkZ));
        Map<String, List<Long>> refs = new LinkedHashMap<>();
        Ref[] res = new Ref[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            StructureStart s = sorted.get(i);
            refs.computeIfAbsent(s.structureId(), k -> new ArrayList<>()).add(chunkLong(s.chunkX(), s.chunkZ()));
            res[i] = refOf(s).clipTo(cx, cz);
        }
        Ref[] ownR = new Ref[own.size()];
        for (int i = 0; i < own.size(); i++) ownR[i] = refOf(own.get(i));
        return new ChunkStructures(List.copyOf(own), ownR, toArrays(refs), null, res);
    }

    private static Map<String, long[]> toArrays(Map<String, List<Long>> m) {
        Map<String, long[]> out = new LinkedHashMap<>();
        for (var e : m.entrySet()) out.put(e.getKey(), e.getValue().stream().mapToLong(Long::longValue).toArray());
        return out;
    }

    /** 이 청크가 시작점인 구조물들의 기하. */
    public Ref[] ownStarts() { return ownRefs; }

    /** 구조물 id → 참조 시작 청크(ChunkPos.asLong). */
    public Map<String, long[]> references() { return references; }

    public boolean isEmpty() { return ownRefs.length == 0 && references.isEmpty(); }

    /**
     * 이 청크가 참조하는 시작점들(참조 순서). lookup: 시작 청크 ChunkPos.asLong → 그 청크의 ChunkStructures(로드 안 됐으면 null).
     * 생성 경로는 미리 풀려 있어 lookup 을 부르지 않는다.
     */
    public Ref[] referenced(LongFunction<ChunkStructures> lookup, int cx, int cz) {
        Ref[] r = resolved;
        if (r != null) return r;
        List<Ref> out = new ArrayList<>();
        boolean complete = true;
        for (var e : references.entrySet()) {
            for (long key : e.getValue()) {
                ChunkStructures start = lookup == null ? null : lookup.apply(key);
                if (start == null) { complete = false; continue; }
                for (Ref s : start.ownRefs) if (s.id().equals(e.getKey())) out.add(s.clipTo(cx, cz));
            }
        }
        r = out.toArray(new Ref[0]);
        if (complete) resolved = r;
        return r;
    }

    // ------------------------------------------------------------------ NBT

    /** 바닐라 SerializableChunkData 의 `structures` 컴파운드: {starts: {id: start}, References: {id: long[]}}. */
    public NbtCompound toNbt() {
        if (raw != null) return raw.copy();
        NbtCompound root = new NbtCompound();
        NbtCompound starts = new NbtCompound();
        for (StructureStart s : liveStarts) {
            NbtCompound st = new NbtCompound();
            st.putString("id", s.structureId());
            st.putInt("ChunkX", s.chunkX());
            st.putInt("ChunkZ", s.chunkZ());
            st.putInt("references", 0);
            NbtList children = new NbtList(NbtIo.TAG_COMPOUND);
            for (StructurePiece p : s.pieces()) {
                BoundingBox b = p.boundingBox();
                NbtCompound c = new NbtCompound();
                c.putString("id", "furnace:piece");   // 조각 상세는 아직 직렬화하지 않는다(상자만)
                c.putIntArray("BB", new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
                children.add(c);
            }
            st.putList("Children", children);
            starts.putCompound(s.structureId(), st);
        }
        root.putCompound("starts", starts);
        NbtCompound refs = new NbtCompound();
        for (var e : references.entrySet()) if (e.getValue().length > 0) refs.putLongArray(e.getKey(), e.getValue().clone());
        root.putCompound("References", refs);
        return root;
    }

    /** 청크 NBT `structures` 를 읽는다(바닐라 형식). 원본은 그대로 보존한다. */
    public static ChunkStructures fromNbt(NbtCompound structures) {
        if (structures == null) return EMPTY;
        List<Ref> own = new ArrayList<>();
        if (structures.tags().get("starts") instanceof NbtCompound starts) {
            for (var e : starts.tags().entrySet()) {
                if (!(e.getValue() instanceof NbtCompound st)) continue;
                Ref r = parseStart(e.getKey(), st);
                if (r != null) own.add(r);
            }
        }
        Map<String, long[]> refs = new LinkedHashMap<>();
        if (structures.tags().get("References") instanceof NbtCompound rc) {
            for (var e : rc.tags().entrySet()) {
                if (e.getValue() instanceof long[] a && a.length > 0) refs.put(e.getKey(), a.clone());
            }
        }
        return new ChunkStructures(List.of(), own.toArray(new Ref[0]), refs, structures.copy(), refs.isEmpty() ? new Ref[0] : null);
    }

    /** StructureStart.loadStaticStart 의 기하 부분: INVALID 이거나 조각이 없으면 null(isValid=false). */
    private static Ref parseStart(String key, NbtCompound st) {
        String id = st.tags().get("id") instanceof String s ? s : key;
        if ("INVALID".equals(id)) return null;
        if (!(st.tags().get("Children") instanceof NbtList children)) return null;
        int[] pieces = new int[children.size() * 6];
        int n = 0;
        for (Object o : children.items()) {
            if (o instanceof NbtCompound c && c.tags().get("BB") instanceof int[] bb && bb.length == 6) {
                // BoundingBox 코덱: min/max 순서가 뒤집혀 있어도 바로잡는다
                pieces[n] = Math.min(bb[0], bb[3]); pieces[n + 1] = Math.min(bb[1], bb[4]); pieces[n + 2] = Math.min(bb[2], bb[5]);
                pieces[n + 3] = Math.max(bb[0], bb[3]); pieces[n + 4] = Math.max(bb[1], bb[4]); pieces[n + 5] = Math.max(bb[2], bb[5]);
                n += 6;
            }
        }
        if (n == 0) return null;
        pieces = java.util.Arrays.copyOf(pieces, n);
        int[] box = {pieces[0], pieces[1], pieces[2], pieces[3], pieces[4], pieces[5]};
        for (int i = 6; i < n; i += 6) {
            for (int k = 0; k < 3; k++) {
                box[k] = Math.min(box[k], pieces[i + k]);
                box[k + 3] = Math.max(box[k + 3], pieces[i + k + 3]);
            }
        }
        // Structure.adjustBoundingBox: terrain_adaptation 이 none 이 아니면 12 늘린다
        if (!"none".equals(StructureRegistry.terrainAdaptationOf(id))) {
            for (int k = 0; k < 3; k++) { box[k] -= 12; box[k + 3] += 12; }
        }
        return new Ref(id, st.getIntOr0("ChunkX"), st.getIntOr0("ChunkZ"), box, pieces);
    }
}
