// 실제 청크 복사본 격자: 월드젠 코드는 복사본(view)에만 쓰고, commit 이 바뀐 칸·블록 엔티티·엔티티를 실제 월드로 옮긴다(/place 와 구조물 블록이 쓴다).
package org.domi.furnace.server.world;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BlockEntities;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

/**
 * 실제 청크의 복사본 격자. 월드젠 코드는 복사본(view)에만 쓰고, commit 이 바뀐 칸을 실제 월드에 옮긴다.
 * 반경 안에서 이 region 에 없는 청크는 null(읽으면 공기, 쓰면 버려진다).
 */
public final class LiveGrid {
    /** 격자를 만들거나 마칠 수 없다: tooLarge 면 청크 범위가 너무 크고, 아니면 로드 안 된 청크를 건드렸다. */
    public static final class GridException extends RuntimeException {
        public final boolean tooLarge;

        public GridException(boolean tooLarge) {
            super(tooLarge ? "grid too large" : "chunk not loaded", null, false, false);
            this.tooLarge = tooLarge;
        }
    }

    private final RegionLevel level;
    private final int centerX, centerZ, radius, size;
    private final ChunkColumn[] live, copy;
    public final FeatureWorldView view;

    public LiveGrid(RegionLevel level, int centerX, int centerZ, int radius, long seed) {
        this.level = level;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.size = 2 * radius + 1;
        live = new ChunkColumn[size * size];
        copy = new ChunkColumn[size * size];
        int[][] biomes = new int[size * size][];
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int i = (dz + radius) * size + (dx + radius);
                ChunkColumn col = level.listenerChunk(centerX + dx, centerZ + dz);
                if (col == null) continue;
                live[i] = col;
                copy[i] = col.copy();   // blockEntities 는 얕은 복사(같은 BE 객체) — 월드젠 코드는 BE 를 고치지 않고 생성 NBT 만 남긴다는 전제
                biomes[i] = biomeIds(col);
            }
        }
        view = FeatureWorldView.liveGrid(copy, biomes, centerX, centerZ, radius, seed);
    }

    /** 청크 범위를 덮는 가장 작은 정사각 격자. */
    public static final int MAX_GRID_RADIUS = 9;

    public static LiveGrid covering(RegionLevel level, int minCx, int minCz, int maxCx, int maxCz, long seed)  {
        int cx = Math.floorDiv(minCx + maxCx, 2), cz = Math.floorDiv(minCz + maxCz, 2);
        int r = Math.max(Math.max(cx - minCx, maxCx - cx), Math.max(cz - minCz, maxCz - cz));
        // 상한: 19x19 청크(사본 약 361개). jigsaw(최대 거리 128 → 약 17x17)까지 들어간다. 넘으면 실패.
        if (r > MAX_GRID_RADIUS) throw new GridException(true);
        return new LiveGrid(level, cx, cz, Math.max(r, 1), seed);
    }

    /** FeatureWorldView 의 biome 배열 형식: (quartY * 4 + quartZ) * 4 + quartX, quartY 는 열 전체 기준. */
    private static int[] biomeIds(ChunkColumn col) {
        ChunkSection[] sections = col.sections();
        int[] ids = new int[sections.length * 64];
        for (int s = 0; s < sections.length; s++) {
            for (int qy = 0; qy < 4; qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) ids[((s * 4 + qy) * 4 + qz) * 4 + qx] = sections[s].getBiome(qx, qy, qz);
                }
            }
        }
        return ids;
    }

    /** 바뀐 칸을 실제 월드에 놓는다(flag 2). 생성된 블록 엔티티 NBT 를 붙이고 엔티티를 세계에 넣는다. 바뀐 칸 수. */
    /** 범위 밖·로드 안 된 청크를 읽거나 썼으면 일부만 놓인 결과다 — 옮기지 않고 실패한다. */
    public void checkComplete()  {
        if (view.missingAccess() > 0) throw new GridException(false);
    }

    public int commit(int flags) {
        int changed = 0;
        for (int i = 0; i < live.length; i++) {
            ChunkColumn l = live[i], cp = copy[i];
            if (l == null) continue;
            int ox = (centerX + (i % size) - radius) * 16, oz = (centerZ + (i / size) - radius) * 16;
            for (int y = l.minY(); y <= l.maxY(); y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int s = cp.getBlock(x, y, z);
                        if (s == l.getBlock(x, y, z)) continue;
                        level.setBlock(new Pos(ox + x, y, oz + z), s, flags);
                        changed++;
                    }
                }
            }
        }
        for (int i = 0; i < live.length; i++) {
            ChunkColumn l = live[i], cp = copy[i];
            if (l == null) continue;
            int ox = (centerX + (i % size) - radius) * 16, oz = (centerZ + (i / size) - radius) * 16;
            cp.forEachGeneratedBlockEntity((lx, y, lz, nbt) -> adoptBlockEntity(l, ox + lx, y, oz + lz, nbt));
            for (NbtCompound e : cp.generatedEntities()) spawn(e);
        }
        return changed;
    }

    private void adoptBlockEntity(ChunkColumn col, int x, int y, int z, NbtCompound nbt) {
        Pos pos = new Pos(x, y, z);
        NbtCompound tag = nbt.copy();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        String id = tag.tags().get("id") instanceof String s ? s : null;
        if (id != null && BlockEntities.known(id)) {
            var be = BlockEntities.loadStatic(pos, level.getBlockState(pos), tag);
            if (be != null) {
                level.setBlockEntity(be);
                be.afterLoad(level);   // 바닐라는 실제 레벨에 놓은 블록 엔티티에 loadWithComponents(레벨 있음)
            }
            return;
        }
        // 아이템이 든 상자(구조물 템플릿): 생성 상자와 달리 바로 상자 블록 엔티티로 — 안 그러면 청크를 다시 읽기 전까지 열 수 없다
        if (("minecraft:chest".equals(id) || "minecraft:trapped_chest".equals(id)) && tag.contains("Items") && tag.getList("Items").size() > 0
                && id.equals(org.domi.furnace.server.block.Blocks.get().nameOfState(level.getBlockState(pos)))) {
            var chest = ChestBlockEntity.empty(id, x, y, z);
            chest.load(tag);
            chest.loadComponents(tag);
            level.setBlockEntity(chest);
            return;
        }
        // 상자·아직 모르는 종류: 청크 로드 때와 같이 생성 NBT 로 남긴다(상자는 처음 열 때 loot table 상자가 된다)
        col.removeBlockEntityAt(x, y, z);
        col.putGeneratedBlockEntity(x & 15, y, z & 15, tag);
    }

    private void spawn(NbtCompound nbt) {
        Entity e = Entities.load(level, nbt.copy());
        if (e != null) level.addFreshEntityWithPassengers(e);
    }
}
