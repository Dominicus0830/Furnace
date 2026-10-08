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

    /** 템플릿 안 블록 하나. 좌표는 템플릿 로컬. */
    public record BlockInfo(int x, int y, int z, int state, NbtCompound nbt) {}

    public record EntityInfo(double x, double y, double z, int bx, int by, int bz, NbtCompound nbt) {}

    public final int sizeX, sizeY, sizeZ;
    private final List<List<BlockInfo>> palettes;
    private final List<EntityInfo> entities;

    private StructureTemplate(int sx, int sy, int sz, List<List<BlockInfo>> palettes, List<EntityInfo> entities) {
        this.sizeX = sx;
        this.sizeY = sy;
        this.sizeZ = sz;
        this.palettes = palettes;
        this.entities = entities;
    }

    public List<List<BlockInfo>> palettes() { return palettes; }

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
            if (in == null) throw new IllegalArgumentException("구조물 템플릿 없음: " + id);
            return fromNbt(registry, NbtIo.readGzip(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static StructureTemplate fromNbt(BlockRegistry registry, NbtCompound root) {
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
