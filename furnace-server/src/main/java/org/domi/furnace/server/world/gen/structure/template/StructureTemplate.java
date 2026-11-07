// 바닐라 StructureTemplate 포팅 — .nbt 템플릿 로드(팔레트·블록 정렬), 좌표 변환, 월드에 배치.
package org.domi.furnace.server.world.gen.structure.template;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;

public final class StructureTemplate {

    /** 리소스에 없는 템플릿 — 구조물 생성은 건너뛴다(자료 보충 필요). */
    public static final class MissingTemplateException extends IllegalArgumentException {
        public MissingTemplateException(String id) { super("구조물 템플릿 없음: " + id); }
    }


    /** 템플릿 안 블록 하나. 좌표는 템플릿 로컬. */
    public record BlockInfo(int x, int y, int z, int state, NbtCompound nbt) {}

    public record EntityInfo(double x, double y, double z, int bx, int by, int bz, NbtCompound nbt) {}

    public final int sizeX, sizeY, sizeZ;
    private final List<List<BlockInfo>> palettes;
    private final List<EntityInfo> entities;

    /** 바닐라 StructureTemplate.author — 파일에는 저장되지 않는다(구조물 블록의 작성자 칸 전달용). */
    private volatile String author = "";

    private StructureTemplate(int sx, int sy, int sz, List<List<BlockInfo>> palettes, List<EntityInfo> entities) {
        this.sizeX = sx;
        this.sizeY = sy;
        this.sizeZ = sz;
        this.palettes = palettes;
        this.entities = entities;
    }

    /** 아직 아무것도 채우지 않은 템플릿(바닐라 new StructureTemplate()) — 크기 0, 팔레트 없음. */
    public static StructureTemplate empty() { return new StructureTemplate(0, 0, 0, List.of(), List.of()); }

    /**
     * 월드에서 읽어 만든 템플릿(바닐라 fillFromWorld 의 결과). blocks 는 이미 저장 순서(꽉 찬 블록 → 그 밖 → 블록 엔티티 있는 블록)로 정렬돼 있어야 한다.
     * 팔레트는 하나.
     */
    public static StructureTemplate ofWorld(int sx, int sy, int sz, List<BlockInfo> blocks, List<EntityInfo> entities) {
        return new StructureTemplate(sx, sy, sz, List.of(List.copyOf(blocks)), List.copyOf(entities));
    }

    /** 팔레트 하나만 남긴 보기(같은 칸 목록을 공유) — 팔레트 번호를 골라 놓을 때. 번호가 범위 밖이면 IndexOutOfBoundsException. */
    public StructureTemplate onlyPalette(int index) {
        StructureTemplate t = new StructureTemplate(sizeX, sizeY, sizeZ, List.of(palettes.get(index)), entities);
        t.author = author;
        return t;
    }

    public String author() { return author; }

    public void author(String a) { this.author = a; }

    public List<List<BlockInfo>> palettes() { return palettes; }

    /** 바닐라 Palette.blocks(Block) 캐시 — 팔레트별 특정 블록(예: jigsaw) 칸만 원래 순서대로. 템플릿은 스레드 공유라 동시 맵. */
    private final java.util.concurrent.ConcurrentHashMap<String, List<BlockInfo>> blocksByName = new java.util.concurrent.ConcurrentHashMap<>();

    public List<BlockInfo> blocksNamed(int paletteIndex, String blockName, BlockStateTable table) {
        return blocksByName.computeIfAbsent(paletteIndex + "|" + blockName, k -> {
            List<BlockInfo> out = new java.util.ArrayList<>();
            for (BlockInfo info : palettes.get(paletteIndex)) if (table.name(info.state()).equals(blockName)) out.add(info);
            return List.copyOf(out);
        });
    }

    public List<EntityInfo> entities() { return entities; }

    /** 바닐라 getSize(rotation) — 90도 회전이면 x/z 교환. */
    public int sizeX(int rotation) {
        return (rotation == BlockStateTable.ROT_CW90 || rotation == BlockStateTable.ROT_CCW90) ? sizeZ : sizeX;
    }

    public int sizeZ(int rotation) {
        return (rotation == BlockStateTable.ROT_CW90 || rotation == BlockStateTable.ROT_CCW90) ? sizeX : sizeZ;
    }

    // ------------------------------------------------------------------ 로드

    public static StructureTemplate load(BlockRegistry registry, String id) {
        String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        String res = "worldgen/1.21.1/structure/" + path + ".nbt";
        try (InputStream in = StructureTemplate.class.getClassLoader().getResourceAsStream(res)) {
            // 바닐라 StructureTemplateManager.getOrCreate: 없는 템플릿은 빈 템플릿(크기 0, 팔레트 없음)으로 대신한다
            // (예: 1.21.1 ancient_city/walls/no_corners 풀이 가리키는 intact_horizontal_wall_stairs_5 는 jar 에 없다).
            if (in == null) return new StructureTemplate(0, 0, 0, List.of(), List.of());
            return fromNbt(registry, NbtIo.readGzip(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static StructureTemplate fromNbt(BlockRegistry registry, NbtCompound root) {
        NbtList size = root.getList("size");
        int sx = (Integer) size.get(0), sy = (Integer) size.get(1), sz = (Integer) size.get(2);
        NbtList blocks = root.getList("blocks");
        List<List<BlockInfo>> palettes = new ArrayList<>();
        BlockAttrs attrs = BlockAttrs.get();
        if (root.contains("palettes")) {
            NbtList ps = root.getList("palettes");
            for (int i = 0; i < ps.size(); i++) palettes.add(loadPalette(registry, attrs, (NbtList) ps.get(i), blocks));
        } else {
            palettes.add(loadPalette(registry, attrs, root.getList("palette"), blocks));
        }
        List<EntityInfo> entities = new ArrayList<>();
        if (root.contains("entities")) {
            NbtList es = root.getList("entities");
            for (int i = 0; i < es.size(); i++) {
                NbtCompound e = (NbtCompound) es.get(i);
                if (!e.contains("nbt")) continue;
                NbtList pos = (NbtList) e.tags().get("pos");
                NbtList bpos = (NbtList) e.tags().get("blockPos");
                entities.add(new EntityInfo((Double) pos.get(0), (Double) pos.get(1), (Double) pos.get(2),
                    (Integer) bpos.get(0), (Integer) bpos.get(1), (Integer) bpos.get(2), e.getCompound("nbt")));
            }
        }
        return new StructureTemplate(sx, sy, sz, palettes, entities);
    }

    private static List<BlockInfo> loadPalette(BlockRegistry registry, BlockAttrs attrs, NbtList palette, NbtList blocks) {
        int[] states = new int[palette.size()];
        for (int i = 0; i < states.length; i++) {
            NbtCompound e = (NbtCompound) palette.get(i);
            String name = e.getString("Name");
            Map<String, String> props = new TreeMap<>();
            if (e.contains("Properties")) {
                for (Map.Entry<String, Object> p : e.getCompound("Properties").tags().entrySet()) props.put(p.getKey(), (String) p.getValue());
            }
            int id = registry.exactStateId(org.domi.furnace.server.world.BlockStateKey.of(name, props));
            if (id < 0) id = registry.stateIdOrAir(name);
            states[i] = id;
        }
        List<BlockInfo> full = new ArrayList<>(), withNbt = new ArrayList<>(), other = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound b = (NbtCompound) blocks.get(i);
            NbtList pos = b.getList("pos");
            int state = states[b.getInt("state")];
            NbtCompound nbt = b.contains("nbt") ? b.getCompound("nbt") : null;
            BlockInfo info = new BlockInfo((Integer) pos.get(0), (Integer) pos.get(1), (Integer) pos.get(2), state, nbt);
            if (nbt != null) withNbt.add(info);
            else if (isFullCube(attrs, state)) full.add(info);
            else other.add(info);
        }
        Comparator<BlockInfo> order = Comparator.<BlockInfo>comparingInt(BlockInfo::y).thenComparingInt(BlockInfo::x).thenComparingInt(BlockInfo::z);
        full.sort(order);
        other.sort(order);
        withNbt.sort(order);
        List<BlockInfo> all = new ArrayList<>(full.size() + other.size() + withNbt.size());
        all.addAll(full);
        all.addAll(other);   // 바닐라 buildInfoLists: 꽉 찬 블록 → 그 밖 → NBT 블록
        all.addAll(withNbt);
        return all;
    }

    /**
     * 바닐라 fillFromWorld 의 분류·정렬: NBT 가 있으면 NBT 목록, 아니면 꽉 찬 블록이면 첫 목록, 아니면 그 밖 목록. 각각 y → x → z 로 정렬한 뒤
     * 꽉 찬 블록 → 그 밖 → NBT 순으로 잇는다(loadPalette 와 같은 순서).
     */
    public static List<BlockInfo> orderForSave(List<BlockInfo> infos) {
        BlockAttrs attrs = BlockAttrs.get();
        List<BlockInfo> full = new ArrayList<>(), withNbt = new ArrayList<>(), other = new ArrayList<>();
        for (BlockInfo info : infos) {
            if (info.nbt() != null) withNbt.add(info);
            else if (isFullCube(attrs, info.state())) full.add(info);
            else other.add(info);
        }
        Comparator<BlockInfo> order = Comparator.<BlockInfo>comparingInt(BlockInfo::y).thenComparingInt(BlockInfo::x).thenComparingInt(BlockInfo::z);
        full.sort(order);
        other.sort(order);
        withNbt.sort(order);
        List<BlockInfo> all = new ArrayList<>(infos.size());
        all.addAll(full);
        all.addAll(other);
        all.addAll(withNbt);
        return all;
    }

    /** 바닐라 DataVersion(1.21.1). */
    public static final int DATA_VERSION = 3955;

    /**
     * 바닐라 StructureTemplate.save: 팔레트가 하나면 palette, 여럿이면 palettes. 칸은 첫 팔레트 순서대로 {pos, state, nbt?}, 상태 번호는 첫 팔레트에서
     * 처음 나온 순서. 팔레트가 비면 빈 blocks·palette. 끝에 entities·size·DataVersion.
     */
    public NbtCompound toNbt(BlockStateTable table) {
        NbtCompound root = new NbtCompound();
        if (palettes.isEmpty()) {
            root.putList("blocks", new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND));
            root.putList("palette", new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND));
        } else {
            List<BlockInfo> first = palettes.get(0);
            List<java.util.LinkedHashMap<Integer, Integer>> ids = new ArrayList<>();   // 팔레트별 상태 → 번호(처음 나온 순서)
            for (int i = 0; i < palettes.size(); i++) ids.add(new java.util.LinkedHashMap<>());
            NbtList blocks = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
            java.util.Map<Integer, Integer> firstIds = ids.get(0);
            for (int i = 0; i < first.size(); i++) {
                BlockInfo info = first.get(i);
                NbtCompound b = new NbtCompound();
                b.putList("pos", intList(info.x(), info.y(), info.z()));
                Integer id = firstIds.get(info.state());
                if (id == null) {
                    id = firstIds.size();
                    firstIds.put(info.state(), id);
                }
                b.putInt("state", id);
                if (info.nbt() != null) b.putCompound("nbt", info.nbt());
                blocks.add(b);
                for (int p = 1; p < palettes.size(); p++) ids.get(p).put(palettes.get(p).get(i).state(), id);
            }
            root.putList("blocks", blocks);
            if (palettes.size() == 1) {
                root.putList("palette", paletteNbt(table, firstIds));
            } else {
                NbtList all = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_LIST);
                for (var m : ids) all.add(paletteNbt(table, m));
                root.putList("palettes", all);
            }
        }
        NbtList es = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        for (EntityInfo e : entities) {
            NbtCompound c = new NbtCompound();
            NbtList pos = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_DOUBLE);
            pos.add(e.x()); pos.add(e.y()); pos.add(e.z());
            c.putList("pos", pos);
            c.putList("blockPos", intList(e.bx(), e.by(), e.bz()));
            if (e.nbt() != null) c.putCompound("nbt", e.nbt());
            es.add(c);
        }
        root.putList("entities", es);
        root.putList("size", intList(sizeX, sizeY, sizeZ));
        root.putInt("DataVersion", DATA_VERSION);
        return root;
    }

    private static NbtList intList(int... v) {
        NbtList l = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_INT);
        for (int i : v) l.add(i);
        return l;
    }

    /** 번호 순서(맵 삽입 순서)대로 {Name, Properties?}. */
    private static NbtList paletteNbt(BlockStateTable table, java.util.Map<Integer, Integer> ids) {
        NbtList out = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        for (int state : ids.keySet()) {
            NbtCompound e = new NbtCompound();
            String key = table.key(state);
            int sc = key.indexOf(';');
            e.putString("Name", sc < 0 ? key : key.substring(0, sc));
            if (sc >= 0) {
                NbtCompound props = new NbtCompound();
                for (String kv : key.substring(sc + 1).split(",")) {
                    int eq = kv.indexOf('=');
                    props.putString(kv.substring(0, eq), kv.substring(eq + 1));
                }
                e.putCompound("Properties", props);
            }
            out.add(e);
        }
        return out;
    }

    /** 바닐라: !block.hasDynamicShape() && state.isCollisionShapeFullBlock — 충돌 모양의 6면이 모두 가득인 블록으로 근사. */
    private static boolean isFullCube(BlockAttrs attrs, int state) {
        for (int d = 0; d < 6; d++) if (!attrs.isCollisionFaceFull(state, d)) return false;
        return true;
    }

    // ------------------------------------------------------------------ 좌표 변환

    /** 바닐라 StructureTemplate.transform(pos, mirror, rotation, pivot) — 결과를 {x,y,z}로. */
    public static int[] transform(int x, int y, int z, int mirror, int rotation, int pivotX, int pivotZ) {
        boolean changed = true;
        switch (mirror) {
            case BlockStateTable.LEFT_RIGHT -> z = -z;
            case BlockStateTable.FRONT_BACK -> x = -x;
            default -> changed = false;
        }
        switch (rotation) {
            case BlockStateTable.ROT_CCW90:
                return new int[]{pivotX - pivotZ + z, y, pivotX + pivotZ - x};
            case BlockStateTable.ROT_CW90:
                return new int[]{pivotX + pivotZ - z, y, pivotZ - pivotX + x};
            case BlockStateTable.ROT_CW180:
                return new int[]{pivotX + pivotX - x, y, pivotZ + pivotZ - z};
            default:
                return new int[]{x, y, z};
        }
    }

    /** 바닐라 getZeroPositionWithTransform(pos, mirror, rotation, sizeX, sizeZ). */
    public static int[] zeroPositionWithTransform(int px, int py, int pz, int mirror, int rotation, int sizeX, int sizeZ) {
        int sx = sizeX - 1;
        int sz = sizeZ - 1;
        int i = mirror == BlockStateTable.FRONT_BACK ? sx : 0;
        int j = mirror == BlockStateTable.LEFT_RIGHT ? sz : 0;
        return switch (rotation) {
            case BlockStateTable.ROT_CCW90 -> new int[]{px + j, py, pz + sx - i};
            case BlockStateTable.ROT_CW90 -> new int[]{px + sz - j, py, pz + i};
            case BlockStateTable.ROT_CW180 -> new int[]{px + sx - i, py, pz + sz - j};
            default -> new int[]{px + i, py, pz + j};
        };
    }

    /** 바닐라 getBoundingBox(origin, rotation, pivot, mirror) — {minX,minY,minZ,maxX,maxY,maxZ}. */
    public int[] boundingBox(int ox, int oy, int oz, int rotation, int pivotX, int pivotZ, int mirror) {
        int[] a = transform(0, 0, 0, mirror, rotation, pivotX, pivotZ);
        int[] b = transform(sizeX - 1, sizeY - 1, sizeZ - 1, mirror, rotation, pivotX, pivotZ);
        return new int[]{
            Math.min(a[0], b[0]) + ox, Math.min(a[1], b[1]) + oy, Math.min(a[2], b[2]) + oz,
            Math.max(a[0], b[0]) + ox, Math.max(a[1], b[1]) + oy, Math.max(a[2], b[2]) + oz};
    }
}
