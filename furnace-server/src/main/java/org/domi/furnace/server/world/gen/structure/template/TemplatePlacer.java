// 바닐라 StructureTemplate.placeInWorld 포팅 — 팔레트 선택, 프로세서, 블록 배치(물 처리), 블록 엔티티 NBT, 가장자리 shape 갱신.
package org.domi.furnace.server.world.gen.structure.template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.block.BlockShapes;
import org.domi.furnace.server.world.block.ShapeUpdater;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate.BlockInfo;

public final class TemplatePlacer {

    private TemplatePlacer() {}

    private static final Set<String> RANDOMIZABLE_CONTAINERS = Set.of(
        "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:dispenser", "minecraft:dropper",
        "minecraft:hopper", "minecraft:decorated_pot");

    private static boolean isRandomizableContainer(String name) {
        return RANDOMIZABLE_CONTAINERS.contains(name) || name.endsWith("shulker_box");
    }

    /**
     * @param pos     템플릿 원점의 월드 위치
     * @param pivot   placeInWorld의 두 번째 위치(프로세서에 전달)
     * @param random  loot table seed용 난수(WorldgenRandom 등)
     * @param flags   바닐라 setBlock 플래그(월드젠은 2)
     */
    public static boolean placeInWorld(StructureTemplate t, FeatureWorldView view, BlockShapes shapes, BlockStateTable table, Processor.Ctx ctx,
                                       int posX, int posY, int posZ, int pivX, int pivY, int pivZ,
                                       PlaceSettings st, LegacyRandom random, int flags) {
        List<List<BlockInfo>> palettes = t.palettes();
        if (palettes.isEmpty()) return false;
        // getRandomPalette: settings.getRandom(pos).nextInt(size)
        LegacyRandom pr = st.getRandom(posX, posY, posZ);
        List<BlockInfo> list = palettes.get(pr.nextInt(palettes.size()));
        if (!(!list.isEmpty() || (!st.ignoreEntities && !t.entities().isEmpty())) || t.sizeX < 1 || t.sizeY < 1 || t.sizeZ < 1) return false;

        List<BlockInfo> processed = processBlockInfos(ctx, posX, posY, posZ, pivX, pivY, pivZ, st, list);
        List<int[]> flowing = new ArrayList<>(), sources = new ArrayList<>();   // list(흐르는 물 위치), list2(source)
        List<BlockInfo> placed = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        BlockAttrs attrs = BlockAttrs.get();

        for (BlockInfo info : processed) {
            int x = info.x(), y = info.y(), z = info.z();
            if (!st.inBox(x, y, z)) continue;
            int before = view.getBlock(x, y, z);
            // 바닐라: shouldApplyWaterlogging 이면 놓기 전 칸의 유체(없으면 -1 = null)
            int prevFluid = st.keepLiquids ? org.domi.furnace.server.world.gen.feature.FeatureWorldView.fluidTypeOf(before) : -1;
            int state = table.transform(info.state(), st.mirror, st.rotation);
            if (!view.trySetBlock(x, y, z, state)) continue;
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            placed.add(new BlockInfo(x, y, z, state, info.nbt()));
            if (info.nbt() != null) {
                NbtCompound nbt = info.nbt();
                if (isRandomizableContainer(table.name(state))) nbt.putLong("LootTableSeed", random.nextLong());
                nbt.putInt("x", x); nbt.putInt("y", y); nbt.putInt("z", z);
                view.putGeneratedBlockEntity(x, y, z, nbt);
            }
            if (prevFluid >= 0) {
                if (isSourceFluid(org.domi.furnace.server.world.gen.feature.FeatureWorldView.fluidTypeOf(state))) {
                    sources.add(new int[]{x, y, z});
                } else if (table.has(state, "waterlogged")) {
                    placeLiquid(view, table, x, y, z, state, prevFluid);
                    if (!isSourceFluid(prevFluid)) flowing.add(new int[]{x, y, z});   // 바닐라: 이전 유체가 source 가 아니면(빈 유체 포함) 전파 후보
                }
            }
        }

        // 흐르는 물이 있던 자리 — 인접한 source 물에서 침수 전파
        boolean again = true;
        final int[][] dirs = {{0, 1, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};   // UP, NORTH, EAST, SOUTH, WEST
        while (again && !flowing.isEmpty()) {
            again = false;
            Iterator<int[]> it = flowing.iterator();
            while (it.hasNext()) {
                int[] p = it.next();
                // 바닐라: 그 칸 유체가 source 가 될 때까지 위·북·동·남·서 이웃의 source(용암 포함, 이번에 놓은 source 제외)를 본다
                int f = org.domi.furnace.server.world.gen.feature.FeatureWorldView.fluidTypeOf(view.getBlock(p[0], p[1], p[2]));
                for (int i = 0; i < dirs.length && !isSourceFluid(f); i++) {
                    int nx = p[0] + dirs[i][0], ny = p[1] + dirs[i][1], nz = p[2] + dirs[i][2];
                    int f2 = org.domi.furnace.server.world.gen.feature.FeatureWorldView.fluidTypeOf(view.getBlock(nx, ny, nz));
                    boolean inSources = false;
                    for (int[] s : sources) if (s[0] == nx && s[1] == ny && s[2] == nz) { inSources = true; break; }
                    if (isSourceFluid(f2) && !inSources) f = f2;
                }
                if (isSourceFluid(f)) {
                    int s = view.getBlock(p[0], p[1], p[2]);
                    if (table.has(s, "waterlogged")) {
                        placeLiquid(view, table, p[0], p[1], p[2], s, f);
                        again = true;
                        it.remove();
                    }
                }
            }
        }

        if (minX <= maxX) {
            if (!st.knownShape) {
                final int fMinX = minX, fMinY = minY, fMinZ = minZ, sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
                final boolean[] filled = new boolean[sx * sy * sz];
                for (BlockInfo p : placed) filled[((p.y() - fMinY) * sz + (p.z() - fMinZ)) * sx + (p.x() - fMinX)] = true;
                ShapeUpdater.updateShapeAtEdge(view, shapes, minX, minY, minZ, sx, sy, sz,
                    (x, y, z) -> x >= fMinX && x < fMinX + sx && y >= fMinY && y < fMinY + sy && z >= fMinZ && z < fMinZ + sz
                        && filled[((y - fMinY) * sz + (z - fMinZ)) * sx + (x - fMinX)]);
                for (BlockInfo p : placed) {
                    int s = view.getBlock(p.x(), p.y(), p.z());
                    int s2 = ShapeUpdater.updateFromNeighbourShapes(view, shapes, s, p.x(), p.y(), p.z());
                    if (s != s2) view.trySetBlock(p.x(), p.y(), p.z(), s2);
                }
            }
        }
        if (!st.ignoreEntities) addEntities(t, view, posX, posY, posZ, st);
        return true;
    }

    /** 바닐라 StructureTemplate.addEntitiesToWorld — 위치/회전 변환한 NBT 를 청크에 기록(finalizeSpawn 무작위 장비는 엔티티 쪽 난수라 생략). */
    private static void addEntities(StructureTemplate t, FeatureWorldView view, int ox, int oy, int oz, PlaceSettings st) {
        for (StructureTemplate.EntityInfo e : t.entities()) {
            int[] bp = StructureTemplate.transform(e.bx(), e.by(), e.bz(), st.mirror, st.rotation, st.pivotX, st.pivotZ);
            int bx = bp[0] + ox, by = bp[1] + oy, bz = bp[2] + oz;
            if (!st.inBox(bx, by, bz)) continue;
            double x = e.x(), y = e.y(), z = e.z();
            boolean flag = true;
            if (st.mirror == BlockStateTable.LEFT_RIGHT) z = 1.0 - z;
            else if (st.mirror == BlockStateTable.FRONT_BACK) x = 1.0 - x;
            else flag = false;
            int px = st.pivotX, pz = st.pivotZ;
            double vx, vz;
            switch (st.rotation) {
                case BlockStateTable.ROT_CCW90 -> { vx = px - pz + z; vz = px + pz + 1 - x; }
                case BlockStateTable.ROT_CW90 -> { vx = px + pz + 1 - z; vz = pz - px + x; }
                case BlockStateTable.ROT_CW180 -> { vx = px + px + 1 - x; vz = pz + pz + 1 - z; }
                default -> { vx = x; vz = z; }
            }
            double wx = vx + ox, wy = y + oy, wz = vz + oz;
            NbtCompound nbt = e.nbt().copy();
            nbt.tags().remove("UUID");
            org.domi.furnace.server.net.io.nbt.NbtList pos = new org.domi.furnace.server.net.io.nbt.NbtList((byte) 6);
            pos.add(wx); pos.add(wy); pos.add(wz);
            nbt.put("Pos", pos);
            float yRot = 0.0F, xRot = 0.0F;
            if (nbt.contains("Rotation")) {
                org.domi.furnace.server.net.io.nbt.NbtList r = nbt.getList("Rotation");
                yRot = (Float) r.get(0);
                xRot = (Float) r.get(1);
            }
            float f = wrapDegrees(yRot);
            float rotated = switch (st.rotation) {
                case BlockStateTable.ROT_CW180 -> f + 180.0F;
                case BlockStateTable.ROT_CCW90 -> f + 270.0F;
                case BlockStateTable.ROT_CW90 -> f + 90.0F;
                default -> f;
            };
            float mirrored = switch (st.mirror) {
                case BlockStateTable.FRONT_BACK -> -f;
                case BlockStateTable.LEFT_RIGHT -> 180.0F - f;
                default -> f;
            };
            float newYaw = rotated + mirrored - yRot;
            org.domi.furnace.server.net.io.nbt.NbtList rot = new org.domi.furnace.server.net.io.nbt.NbtList((byte) 5);
            rot.add(newYaw); rot.add(xRot);
            nbt.put("Rotation", rot);
            if (nbt.contains("TileX")) {   // 걸이 엔티티(액자·그림)의 블록 좌표
                nbt.putInt("TileX", bx); nbt.putInt("TileY", by); nbt.putInt("TileZ", bz);
            }
            // Villager.finalizeSpawn(STRUCTURE): 처음 AI 틱에 근처 직업 블록으로 직업을 받도록 표시해 저장된다
            if ("minecraft:villager".equals(nbt.tags().get("id"))) nbt.putBoolean("AssignProfessionWhenSpawned", true);
            view.addEntityNbt(nbt, wx, wz);
        }
    }

    private static float wrapDegrees(float v) {
        float f = v % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    /** 바닐라 StructureTemplate.filterBlocks(origin, settings, block) — 선택된 팔레트에서 해당 블록 이름의 칸을 변환 좌표로 반환(쓰기 상자 안만). */
    public static List<BlockInfo> filterBlocks(StructureTemplate t, BlockStateTable table, PlaceSettings st,
                                               int ox, int oy, int oz, String blockName) {
        List<BlockInfo> out = new ArrayList<>();
        List<List<BlockInfo>> palettes = t.palettes();
        if (palettes.isEmpty()) return out;
        List<BlockInfo> palette = t.blocksNamed(st.getRandom(ox, oy, oz).nextInt(palettes.size()), blockName, table);
        for (BlockInfo info : palette) {
            int[] p = StructureTemplate.transform(info.x(), info.y(), info.z(), st.mirror, st.rotation, st.pivotX, st.pivotZ);
            int x = p[0] + ox, y = p[1] + oy, z = p[2] + oz;
            if (!st.inBox(x, y, z)) continue;
            out.add(new BlockInfo(x, y, z, table.transform(info.state(), BlockStateTable.NONE, st.rotation), info.nbt()));
        }
        return out;
    }

    private static boolean isSourceFluid(int fluidType) {
        return fluidType == org.domi.furnace.server.block.Fluids.T_WATER || fluidType == org.domi.furnace.server.block.Fluids.T_LAVA;
    }

    /**
     * 바닐라 SimpleWaterloggedBlock.placeLiquid: 아직 침수되지 않았고 유체가 물 source(Fluids.WATER — 흐르는 물은 아님)일 때만
     * 침수시키고 scheduleTick(pos, 물, 물 틱 지연 5)을 건다(생성 중에는 ProtoChunkTicks 라 지연 0, A06-03).
     */
    private static void placeLiquid(FeatureWorldView view, BlockStateTable table, int x, int y, int z, int state, int fluidType) {
        if ("true".equals(table.prop(state, "waterlogged")) || fluidType != org.domi.furnace.server.block.Fluids.T_WATER) return;
        view.setBlock(x, y, z, table.with(state, "waterlogged", true));
        view.scheduleFluidTick(x, y, z, org.domi.furnace.server.block.Fluids.T_WATER, 5);
    }

    static List<BlockInfo> processBlockInfos(Processor.Ctx ctx, int offX, int offY, int offZ, int pivX, int pivY, int pivZ,
                                             PlaceSettings st, List<BlockInfo> list) {
        List<BlockInfo> originals = new ArrayList<>(list.size());
        List<BlockInfo> processed = new ArrayList<>(list.size());
        for (BlockInfo info : list) {
            int[] p = StructureTemplate.transform(info.x(), info.y(), info.z(), st.mirror, st.rotation, st.pivotX, st.pivotZ);
            BlockInfo cur = new BlockInfo(p[0] + offX, p[1] + offY, p[2] + offZ, info.state(), info.nbt() != null ? info.nbt().copy() : null);
            for (int i = 0; cur != null && i < st.processors.size(); i++) {
                cur = st.processors.get(i).processBlock(ctx, offX, offY, offZ, pivX, pivY, pivZ, info, cur, st);
            }
            if (cur != null) {
                processed.add(cur);
                originals.add(info);
            }
        }
        for (Processor proc : st.processors) {
            processed = proc.finalizeProcessing(ctx, offX, offY, offZ, pivX, pivY, pivZ, originals, processed, st);
        }
        return processed;
    }
}
