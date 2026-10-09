// 구조물 조각 추상 베이스 — BoundingBox·회전 + postProcess에 필요한 블록배치 헬퍼(바닐라 StructurePiece 미러링).
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

public abstract class StructurePiece {

    protected BoundingBox boundingBox;
    protected final Direction orientation;

    protected StructurePiece(BoundingBox boundingBox, Direction orientation) {
        this.boundingBox = boundingBox;
        this.orientation = orientation;
    }

    public BoundingBox boundingBox() {
        return boundingBox;
    }

    /** 바닐라 StructurePiece.move(dx, dy, dz) — 상자 이동(서브클래스가 추가 상태를 갖는다). */
    public void move(int dx, int dy, int dz) {
        boundingBox.move(dx, dy, dz);
    }

    public Direction orientation() {
        return orientation;
    }

    /**
     * 바닐라 StructurePiece.makeBoundingBox — axis(X/Z)에 따라 offsetX/offsetZ 를 스왑한다.
     * width==depth 인 조각(예: 사막 피라미드)에선 수치상 영향 없음.
     */
    protected static BoundingBox makeBoundingBox(int x, int y, int z, Direction direction, int offsetX, int offsetY, int offsetZ) {
        return direction.isXAxis()
            ? new BoundingBox(x, y, z, x + offsetZ - 1, y + offsetY - 1, z + offsetX - 1)
            : new BoundingBox(x, y, z, x + offsetX - 1, y + offsetY - 1, z + offsetZ - 1);
    }

    /**
     * 블록 배치 — 바닐라 StructurePiece.postProcess 상당.
     * regionRandom: 바닐라 WorldGenLevel.getRandom() 미러(순수 NoiseRandom, decoration용 WorldgenRandom과는 별개 소비 규칙).
     */
    public void postProcess(FeatureWorldView view, BlockRegistry blocks, WorldgenRandom random,
            org.domi.furnace.server.world.gen.noise.NoiseRandom regionRandom, BoundingBox writableBox) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + ": postProcess(PostCtx)를 구현해야 한다");
    }

    /** 바닐라 StructurePiece.postProcess(level, sm, generator, random, box, chunkPos, pivot) — 기본은 구형 시그니처로 위임. */
    public void postProcess(PostCtx c) {
        postProcess(c.view, c.blocks(), c.random, c.regionRandom, c.writableBox);
    }

    /**
     * 바닐라 StructurePiece.placeBlock 의 blockstate.mirror(this.mirror).rotate(this.rotation) 상당
     * — orientation별 mirror/rotation은 vanilla StructurePiece.setOrientation과 동일하게 매핑한다:
     * NORTH: mirror NONE/rotation NONE, SOUTH: mirror LEFT_RIGHT(N/S swap)/rotation NONE,
     * EAST: mirror NONE/rotation CW90, WEST: mirror LEFT_RIGHT/rotation CW90 (mirror 먼저 적용 후 rotate).
     * facing 프로퍼티를 가진(HorizontalDirectionalBlock 계열, 예: 계단) 블록에 로컬 facing을 지정해
     * placeBlock으로 넘기기 전에 호출해 world-space facing으로 변환한다.
     */
    protected Direction rotateFacing(Direction local) {
        Direction mirrored = (orientation == Direction.SOUTH || orientation == Direction.WEST)
            ? mirrorLeftRight(local)
            : local;
        return (orientation == Direction.EAST || orientation == Direction.WEST)
            ? rotateClockwise90(mirrored)
            : mirrored;
    }

    /** 바닐라 Mirror.LEFT_RIGHT.mirror(Direction) — Z축(NORTH/SOUTH)만 반전, X축(EAST/WEST)은 불변. */
    private static Direction mirrorLeftRight(Direction d) {
        return switch (d) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            default -> d;
        };
    }

    private static Direction rotateClockwise90(Direction d) {
        return switch (d) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
        };
    }

    // ---- 바닐라 StructurePiece 좌표변환 헬퍼 (getWorldX/Y/Z) ----

    // 바닐라 StructurePiece.getWorldX/Y/Z — orientation == null(광산 방/교차로)이면 좌표를 변환 없이 그대로 월드 좌표로 쓴다.
    protected int getWorldX(int x, int z) {
        if (orientation == null) return x;
        return switch (orientation) {
            case NORTH, SOUTH -> boundingBox.minX() + x;
            case WEST -> boundingBox.maxX() - z;
            case EAST -> boundingBox.minX() + z;
        };
    }

    protected int getWorldY(int y) {
        if (orientation == null) return y;
        return y + boundingBox.minY();
    }

    protected int getWorldZ(int x, int z) {
        if (orientation == null) return z;
        return switch (orientation) {
            case NORTH -> boundingBox.maxZ() - z;
            case SOUTH -> boundingBox.minZ() + z;
            case WEST, EAST -> boundingBox.minZ() + x;
        };
    }

    // ---- 블록 배치 헬퍼 ----

    protected void placeBlock(FeatureWorldView view, BoundingBox writableBox, int stateId, int x, int y, int z) {
        int wx = getWorldX(x, z);
        int wy = getWorldY(y);
        int wz = getWorldZ(x, z);
        if (writableBox.isInside(wx, wy, wz)) {
            view.setBlock(wx, wy, wz, stateId);
        }
    }

    protected int getBlock(FeatureWorldView view, BoundingBox writableBox, int x, int y, int z) {
        int wx = getWorldX(x, z);
        int wy = getWorldY(y);
        int wz = getWorldZ(x, z);
        if (!writableBox.isInside(wx, wy, wz)) return 0; // air
        return view.getBlock(wx, wy, wz);
    }

    /** 바닐라 StructurePiece.generateBox(경계/내부 상태, existingOnly). */
    protected void generateBox(
            FeatureWorldView view, BoundingBox writableBox,
            int xMin, int yMin, int zMin, int xMax, int yMax, int zMax,
            int boundaryStateId, int insideStateId, boolean existingOnly) {
        for (int y = yMin; y <= yMax; y++) {
            for (int x = xMin; x <= xMax; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (!existingOnly || getBlock(view, writableBox, x, y, z) != 0) {
                        int stateId = (y != yMin && y != yMax && x != xMin && x != xMax && z != zMin && z != zMax)
                            ? insideStateId
                            : boundaryStateId;
                        placeBlock(view, writableBox, stateId, x, y, z);
                    }
                }
            }
        }
    }

    /** 바닐라 StructurePiece.fillColumnDown — 대체 가능(공기/유체) 블록을 따라 아래로 채운다. */
    protected void fillColumnDown(FeatureWorldView view, BoundingBox writableBox, int stateId, int x, int y, int z, int minY) {
        int wx = getWorldX(x, z);
        int wz = getWorldZ(x, z);
        int wy = getWorldY(y);
        if (!writableBox.isInside(wx, wy, wz)) return;
        while (view.getBlock(wx, wy, wz) == 0 && wy > minY + 1) {
            view.setBlock(wx, wy, wz, stateId);
            wy--;
        }
    }

    /**
     * 바닐라 StructurePiece.createChest(pos-arg overload) — 블록 배치만. loot 내용물은 생성하지 않되
     * random.nextLong() 소비(바닐라 ChestBlockEntity.setLootTable seed)는 재현한다.
     */
    protected boolean createChest(FeatureWorldView view, BoundingBox writableBox, WorldgenRandom random, int chestStateId, int x, int y, int z) {
        int wx = getWorldX(x, z);
        int wy = getWorldY(y);
        int wz = getWorldZ(x, z);
        if (!writableBox.isInside(wx, wy, wz) || view.getBlock(wx, wy, wz) == chestStateId) {
            return false;
        }
        view.setBlock(wx, wy, wz, chestStateId);
        random.nextLong(); // loot table seed — 내용물 생성은 생략(Do NOT: loot table 구현 금지)
        return true;
    }

    // =====================================================================================================
    // 바닐라 StructurePiece 의 PostCtx 기반 헬퍼 — mirror/rotation 변환 + 상자 검사 + SHAPE_CHECK 표시.
    // =====================================================================================================

    /** 바닐라 SHAPE_CHECK_BLOCKS: 배치 직후 markPosForPostprocessing 되는 블록 이름. */
    private static final java.util.Set<String> SHAPE_CHECK_BLOCKS = java.util.Set.of(
        "minecraft:nether_brick_fence", "minecraft:torch", "minecraft:wall_torch", "minecraft:oak_fence", "minecraft:spruce_fence",
        "minecraft:dark_oak_fence", "minecraft:acacia_fence", "minecraft:birch_fence", "minecraft:jungle_fence", "minecraft:ladder",
        "minecraft:iron_bars");

    private int pieceMirror() {
        return (orientation == Direction.SOUTH || orientation == Direction.WEST)
            ? org.domi.furnace.server.world.gen.structure.template.BlockStateTable.LEFT_RIGHT
            : org.domi.furnace.server.world.gen.structure.template.BlockStateTable.NONE;
    }

    private int pieceRotation() {
        return (orientation == Direction.WEST || orientation == Direction.EAST)
            ? org.domi.furnace.server.world.gen.structure.template.BlockStateTable.ROT_CW90
            : org.domi.furnace.server.world.gen.structure.template.BlockStateTable.ROT_NONE;
    }

    /** 훅: 해당 로컬 칸에 쓸 수 있는가(바닐라 canBeReplaced). */
    protected boolean canBeReplaced(PostCtx c, int x, int y, int z, BoundingBox box) { return true; }

    /** 바닐라 placeBlock(level, state, x, y, z, box). */
    protected void placeBlock(PostCtx c, int state, int x, int y, int z, BoundingBox box) {
        int wx = getWorldX(x, z), wy = getWorldY(y), wz = getWorldZ(x, z);
        if (!box.isInside(wx, wy, wz)) return;
        if (!canBeReplaced(c, x, y, z, box)) return;
        int m = pieceMirror(), r = pieceRotation();
        if (m != org.domi.furnace.server.world.gen.structure.template.BlockStateTable.NONE
            || r != org.domi.furnace.server.world.gen.structure.template.BlockStateTable.ROT_NONE) {
            state = c.table().transform(state, m, r);
        }
        c.view.setBlock(wx, wy, wz, state);
        if (SHAPE_CHECK_BLOCKS.contains(c.table().name(state))) c.view.markPosForPostprocessing(wx, wy, wz);
    }

    /** 바닐라 getBlock(level, x, y, z, box) — 상자 밖은 공기. */
    protected int getBlock(PostCtx c, int x, int y, int z, BoundingBox box) {
        int wx = getWorldX(x, z), wy = getWorldY(y), wz = getWorldZ(x, z);
        return box.isInside(wx, wy, wz) ? c.view.getBlock(wx, wy, wz) : 0;
    }

    protected boolean isAirAt(PostCtx c, int x, int y, int z, BoundingBox box) {
        return org.domi.furnace.server.world.BlockAttrs.get().isAir(getBlock(c, x, y, z, box));
    }

    /** 바닐라 isInterior(level, x, y, z, box): 위쪽 칸이 상자 안이고 그 y 가 해당 칸의 OCEAN_FLOOR heightmap 아래. */
    protected boolean isInterior(PostCtx c, int x, int y, int z, BoundingBox box) {
        int wx = getWorldX(x, z), wy = getWorldY(y + 1), wz = getWorldZ(x, z);
        if (!box.isInside(wx, wy, wz)) return false;
        return wy < c.view.getWgOceanFloorHeight(wx, wz);   // level.getHeight(OCEAN_FLOOR_WG, x, z): 첫 빈 칸 y
    }

    protected void generateAirBox(PostCtx c, BoundingBox box, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) placeBlock(c, 0, x, y, z, box);
    }

    protected void generateBox(PostCtx c, BoundingBox box, int x0, int y0, int z0, int x1, int y1, int z1,
                               int edge, int fill, boolean skipAir) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    if (skipAir && isAirAt(c, x, y, z, box)) continue;
                    if (y != y0 && y != y1 && x != x0 && x != x1 && z != z0 && z != z1) placeBlock(c, fill, x, y, z, box);
                    else placeBlock(c, edge, x, y, z, box);
                }
    }

    /** 바닐라 BlockSelector. */
    public interface BlockSelector {
        void next(org.domi.furnace.server.world.gen.carver.LegacyRandom random, int x, int y, int z, boolean edge);

        int state();
    }

    protected void generateBox(PostCtx c, BoundingBox box, int x0, int y0, int z0, int x1, int y1, int z1,
                               boolean skipAir, org.domi.furnace.server.world.gen.carver.LegacyRandom random, BlockSelector sel) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    if (skipAir && isAirAt(c, x, y, z, box)) continue;
                    sel.next(random, x, y, z, y == y0 || y == y1 || x == x0 || x == x1 || z == z0 || z == z1);
                    placeBlock(c, sel.state(), x, y, z, box);
                }
    }

    /** 바닐라 generateMaybeBox(level, box, random, chance, x0..z1, edge, fill, skipAir, requireSkylight). */
    protected void generateMaybeBox(PostCtx c, BoundingBox box, org.domi.furnace.server.world.gen.carver.LegacyRandom random, float chance,
                                    int x0, int y0, int z0, int x1, int y1, int z1, int edge, int fill, boolean skipAir, boolean requireSkylight) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    if (random.nextFloat() > chance) continue;
                    if (skipAir && isAirAt(c, x, y, z, box)) continue;
                    if (requireSkylight && !isInterior(c, x, y, z, box)) continue;
                    if (y != y0 && y != y1 && x != x0 && x != x1 && z != z0 && z != z1) placeBlock(c, fill, x, y, z, box);
                    else placeBlock(c, edge, x, y, z, box);
                }
    }

    /** 바닐라 maybeGenerateBlock(level, box, random, chance, x, y, z, state). */
    protected void maybeGenerateBlock(PostCtx c, BoundingBox box, org.domi.furnace.server.world.gen.carver.LegacyRandom random, float chance,
                                      int x, int y, int z, int state) {
        if (random.nextFloat() < chance) placeBlock(c, state, x, y, z, box);
    }

    /** 바닐라 generateUpperHalfSphere(level, box, x0..z1, state, skipAir). */
    protected void generateUpperHalfSphere(PostCtx c, BoundingBox box, int x0, int y0, int z0, int x1, int y1, int z1, int state, boolean skipAir) {
        float fx = (float) (x1 - x0 + 1);
        float fy = (float) (y1 - y0 + 1);
        float fz = (float) (z1 - z0 + 1);
        float cx = (float) x0 + fx / 2.0F;
        float cz = (float) z0 + fz / 2.0F;
        for (int y = y0; y <= y1; y++) {
            float dy = (float) (y - y0) / fy;
            for (int x = x0; x <= x1; x++) {
                float dx = ((float) x - cx) / (fx * 0.5F);
                for (int z = z0; z <= z1; z++) {
                    float dz = ((float) z - cz) / (fz * 0.5F);
                    if (skipAir && isAirAt(c, x, y, z, box)) continue;
                    float d = dx * dx + dy * dy + dz * dz;
                    if (d <= 1.05F) placeBlock(c, state, x, y, z, box);
                }
            }
        }
    }

    /** 바닐라 fillColumnDown(level, state, x, y, z, box). */
    protected void fillColumnDown(PostCtx c, int state, int x, int y, int z, BoundingBox box) {
        int wx = getWorldX(x, z), wy = getWorldY(y), wz = getWorldZ(x, z);
        if (!box.isInside(wx, wy, wz)) return;
        org.domi.furnace.server.world.BlockAttrs a = org.domi.furnace.server.world.BlockAttrs.get();
        while (isReplaceableByColumn(c, a, c.view.getBlock(wx, wy, wz)) && wy > c.view.minY() + 1) {
            c.view.setBlock(wx, wy, wz, state);
            wy--;
        }
    }

    /** 바닐라 isReplaceableByStructures(state): air / liquid / glow lichen / seagrass / tall seagrass. */
    protected boolean isReplaceableByColumn(PostCtx c, org.domi.furnace.server.world.BlockAttrs a, int state) {
        String n = c.table().name(state);
        return a.isAir(state) || a.isLiquid(state) || n.equals("minecraft:glow_lichen") || n.equals("minecraft:seagrass")
            || n.equals("minecraft:tall_seagrass");
    }

    /** 바닐라 StructurePiece.reorient(level, pos, chestState) — 상자 방향 선택. */
    public static int reorientChest(PostCtx c, int wx, int wy, int wz, int state) {
        org.domi.furnace.server.world.BlockAttrs a = org.domi.furnace.server.world.BlockAttrs.get();
        String[] horiz = {"north", "east", "south", "west"};
        int[][] step = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};   // NORTH, EAST, SOUTH, WEST (dx, dz)
        int found = -1;
        for (int d = 0; d < 4; d++) {
            int n = c.view.getBlock(wx + step[d][0], wy, wz + step[d][1]);
            if (c.table().name(n).equals("minecraft:chest")) return state;
            if (a.isSolidRender(n)) {
                if (found != -1) { found = -1; break; }
                found = d;
            }
        }
        if (found != -1) return c.table().with(state, "facing", horiz[(found + 2) & 3]);
        int f = java.util.Arrays.asList(horiz).indexOf(c.table().prop(state, "facing"));
        int nx = wx + step[f][0], nz = wz + step[f][1];
        if (a.isSolidRender(c.view.getBlock(nx, wy, nz))) {
            f = (f + 2) & 3;   // Direction.g() = getOpposite
            nx = wx + step[f][0]; nz = wz + step[f][1];
        }
        if (a.isSolidRender(c.view.getBlock(nx, wy, nz))) {
            f = (f + 1) & 3;   // Direction.h() = getClockWise
            nx = wx + step[f][0]; nz = wz + step[f][1];
        }
        if (a.isSolidRender(c.view.getBlock(nx, wy, nz))) {
            f = (f + 2) & 3;
        }
        return c.table().with(state, "facing", horiz[f]);
    }

    /**
     * 바닐라 createChest(level, box, random, worldPos, lootTable, stateOrNull) — 내용물은 생략, 시드 난수만 소비한다.
     * @param state 음수면 reorient 로 기본 상자 상태를 만든다.
     */
    protected boolean createChestAt(PostCtx c, BoundingBox box, int wx, int wy, int wz, String loot, int state) {
        String chest = "minecraft:chest";
        if (!box.isInside(wx, wy, wz) || c.table().name(c.view.getBlock(wx, wy, wz)).equals(chest)) return false;
        if (state < 0) state = reorientChest(c, wx, wy, wz, c.blocks().defaultStateId(chest));
        c.view.setBlock(wx, wy, wz, state);
        long seed = c.random.nextLong();
        org.domi.furnace.server.net.io.nbt.NbtCompound nbt = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        nbt.putString("id", "minecraft:chest");
        nbt.putInt("x", wx); nbt.putInt("y", wy); nbt.putInt("z", wz);
        nbt.putString("LootTable", loot);
        nbt.putLong("LootTableSeed", seed);
        c.view.putGeneratedBlockEntity(wx, wy, wz, nbt);
        return true;
    }

    /** 바닐라 createChest(level, box, random, x, y, z, direction, lootTable) 로컬 좌표 버전(방향 지정). */
    protected boolean createChestLocal(PostCtx c, BoundingBox box, int x, int y, int z, int facingState, String loot) {
        return createChestAt(c, box, getWorldX(x, z), getWorldY(y), getWorldZ(x, z), loot, facingState);
    }
}
