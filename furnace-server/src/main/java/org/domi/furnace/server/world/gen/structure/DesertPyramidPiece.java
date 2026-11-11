// 사막 피라미드 조각 — 바닐라 DesertPyramidPiece.postProcess 라인단위 이식(블록+RNG 소비만, loot 내용물 제외).
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

import java.util.Map;

public final class DesertPyramidPiece extends StructurePiece {
    @Override
    public String pieceType() { return "tedp"; }

    @Override
    protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
        // ScatteredFeaturePiece 필드
        t.putInt("Width", WIDTH);
        t.putInt("Height", HEIGHT);
        t.putInt("Depth", DEPTH);
        t.putInt("HPos", heightPosition);
        for (int i = 0; i < 4; i++) t.putBoolean("hasPlacedChest" + i, hasPlacedChest[i]);
    }


    public static final int WIDTH = 21;
    public static final int HEIGHT = 15;
    public static final int DEPTH = 21;
    private static final int MIN_Y = -64; // ChunkColumn.MIN_Y

    /** vanilla ScatteredFeaturePiece.heightPosition — 최초 postProcess 호출에서 1회만 계산.-1=미계산. */
    private int heightPosition = -1;
    private final boolean[] hasPlacedChest = new boolean[4];
    /** 바닐라 potentialSuspiciousSandWorldPositions — 월드 좌표 {x,y,z}. */
    final java.util.List<int[]> potentialSuspiciousSand = new java.util.ArrayList<>();
    /** 바닐라 randomCollapsedRoofPos. */
    int[] randomCollapsedRoofPos = {0, 0, 0};
    long worldSeed;

    private void recordSand(int x, int y, int z) {
        potentialSuspiciousSand.add(new int[]{getWorldX(x, z), getWorldY(y), getWorldZ(x, z)});
    }

    public DesertPyramidPiece(WorldgenRandom random, int minBlockX, int minBlockZ) {
        this(Direction.randomHorizontal(random), minBlockX, minBlockZ);
    }

    private DesertPyramidPiece(Direction orientation, int minBlockX, int minBlockZ) {
        super(makeBoundingBox(minBlockX, 64, minBlockZ, orientation, WIDTH, HEIGHT, DEPTH), orientation);
    }

    @Override
    public void postProcess(FeatureWorldView view, BlockRegistry blocks, WorldgenRandom random,
            NoiseRandom regionRandom, BoundingBox writableBox) {
        // vanilla: this.updateHeightPositionToLowestGroundHeight(level, -random.nextInt(3)) — 인자가 항상 먼저 평가된다.
        int heightArg = -random.nextInt(3);
        if (!updateHeightPositionToLowestGroundHeight(view, heightArg)) {
            return;
        }

        int sandstone = blocks.stateIdOrAir("minecraft:sandstone");
        int sand = blocks.stateIdOrAir("minecraft:sand");
        int cutSandstone = blocks.stateIdOrAir("minecraft:cut_sandstone");
        int chiseledSandstone = blocks.stateIdOrAir("minecraft:chiseled_sandstone");
        int sandstoneSlab = blocks.stateIdOrAir("minecraft:sandstone_slab");
        int orangeTerracotta = blocks.stateIdOrAir("minecraft:orange_terracotta");
        int blueTerracotta = blocks.stateIdOrAir("minecraft:blue_terracotta");
        int tnt = blocks.stateIdOrAir("minecraft:tnt");
        int pressurePlate = blocks.stateIdOrAir("minecraft:stone_pressure_plate");
        int air = 0;
        // 로컬(피라미드 설계상 의도) facing → 조각 orientation에 따른 world-space facing으로 변환
        // (바닐라 StructurePiece.placeBlock의 blockstate.mirror/rotate 상당, rotateFacing 참고).
        int stairsNorth = stairsState(blocks, rotateFacing(Direction.NORTH));
        int stairsSouth = stairsState(blocks, rotateFacing(Direction.SOUTH));
        int stairsEast = stairsState(blocks, rotateFacing(Direction.EAST));
        int stairsWest = stairsState(blocks, rotateFacing(Direction.WEST));
        int chest = blocks.stateIdOrAir("minecraft:chest");

        generateBox(view, writableBox, 0, -4, 0, WIDTH - 1, 0, DEPTH - 1, sandstone, sandstone, false);

        for (int i = 1; i <= 9; i++) {
            generateBox(view, writableBox, i, i, i, WIDTH - 1 - i, i, DEPTH - 1 - i, sandstone, sandstone, false);
            generateBox(view, writableBox, i + 1, i, i + 1, WIDTH - 2 - i, i, DEPTH - 2 - i, air, air, false);
        }

        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                fillColumnDown(view, writableBox, sandstone, x, -5, z, MIN_Y);
            }
        }

        generateBox(view, writableBox, 0, 0, 0, 4, 9, 4, sandstone, air, false);
        generateBox(view, writableBox, 1, 10, 1, 3, 10, 3, sandstone, sandstone, false);
        placeBlock(view, writableBox, stairsNorth, 2, 10, 0);
        placeBlock(view, writableBox, stairsSouth, 2, 10, 4);
        placeBlock(view, writableBox, stairsEast, 0, 10, 2);
        placeBlock(view, writableBox, stairsWest, 4, 10, 2);
        generateBox(view, writableBox, WIDTH - 5, 0, 0, WIDTH - 1, 9, 4, sandstone, air, false);
        generateBox(view, writableBox, WIDTH - 4, 10, 1, WIDTH - 2, 10, 3, sandstone, sandstone, false);
        placeBlock(view, writableBox, stairsNorth, WIDTH - 3, 10, 0);
        placeBlock(view, writableBox, stairsSouth, WIDTH - 3, 10, 4);
        placeBlock(view, writableBox, stairsEast, WIDTH - 5, 10, 2);
        placeBlock(view, writableBox, stairsWest, WIDTH - 1, 10, 2);
        generateBox(view, writableBox, 8, 0, 0, 12, 4, 4, sandstone, air, false);
        generateBox(view, writableBox, 9, 1, 0, 11, 3, 4, air, air, false);
        placeBlock(view, writableBox, cutSandstone, 9, 1, 1);
        placeBlock(view, writableBox, cutSandstone, 9, 2, 1);
        placeBlock(view, writableBox, cutSandstone, 9, 3, 1);
        placeBlock(view, writableBox, cutSandstone, 10, 3, 1);
        placeBlock(view, writableBox, cutSandstone, 11, 3, 1);
        placeBlock(view, writableBox, cutSandstone, 11, 2, 1);
        placeBlock(view, writableBox, cutSandstone, 11, 1, 1);
        generateBox(view, writableBox, 4, 1, 1, 8, 3, 3, sandstone, air, false);
        generateBox(view, writableBox, 4, 1, 2, 8, 2, 2, air, air, false);
        generateBox(view, writableBox, 12, 1, 1, 16, 3, 3, sandstone, air, false);
        generateBox(view, writableBox, 12, 1, 2, 16, 2, 2, air, air, false);
        generateBox(view, writableBox, 5, 4, 5, WIDTH - 6, 4, DEPTH - 6, sandstone, sandstone, false);
        generateBox(view, writableBox, 9, 4, 9, 11, 4, 11, air, air, false);
        generateBox(view, writableBox, 8, 1, 8, 8, 3, 8, cutSandstone, cutSandstone, false);
        generateBox(view, writableBox, 12, 1, 8, 12, 3, 8, cutSandstone, cutSandstone, false);
        generateBox(view, writableBox, 8, 1, 12, 8, 3, 12, cutSandstone, cutSandstone, false);
        generateBox(view, writableBox, 12, 1, 12, 12, 3, 12, cutSandstone, cutSandstone, false);
        generateBox(view, writableBox, 1, 1, 5, 4, 4, 11, sandstone, sandstone, false);
        generateBox(view, writableBox, WIDTH - 5, 1, 5, WIDTH - 2, 4, 11, sandstone, sandstone, false);
        generateBox(view, writableBox, 6, 7, 9, 6, 7, 11, sandstone, sandstone, false);
        generateBox(view, writableBox, WIDTH - 7, 7, 9, WIDTH - 7, 7, 11, sandstone, sandstone, false);
        generateBox(view, writableBox, 5, 5, 9, 5, 7, 11, cutSandstone, cutSandstone, false);
        generateBox(view, writableBox, WIDTH - 6, 5, 9, WIDTH - 6, 7, 11, cutSandstone, cutSandstone, false);
        placeBlock(view, writableBox, air, 5, 5, 10);
        placeBlock(view, writableBox, air, 5, 6, 10);
        placeBlock(view, writableBox, air, 6, 6, 10);
        placeBlock(view, writableBox, air, WIDTH - 6, 5, 10);
        placeBlock(view, writableBox, air, WIDTH - 6, 6, 10);
        placeBlock(view, writableBox, air, WIDTH - 7, 6, 10);
        generateBox(view, writableBox, 2, 4, 4, 2, 6, 4, air, air, false);
        generateBox(view, writableBox, WIDTH - 3, 4, 4, WIDTH - 3, 6, 4, air, air, false);
        placeBlock(view, writableBox, stairsNorth, 2, 4, 5);
        placeBlock(view, writableBox, stairsNorth, 2, 3, 4);
        placeBlock(view, writableBox, stairsNorth, WIDTH - 3, 4, 5);
        placeBlock(view, writableBox, stairsNorth, WIDTH - 3, 3, 4);
        generateBox(view, writableBox, 1, 1, 3, 2, 2, 3, sandstone, sandstone, false);
        generateBox(view, writableBox, WIDTH - 3, 1, 3, WIDTH - 2, 2, 3, sandstone, sandstone, false);
        placeBlock(view, writableBox, sandstone, 1, 1, 2);
        placeBlock(view, writableBox, sandstone, WIDTH - 2, 1, 2);
        placeBlock(view, writableBox, sandstoneSlab, 1, 2, 2);
        placeBlock(view, writableBox, sandstoneSlab, WIDTH - 2, 2, 2);
        placeBlock(view, writableBox, stairsWest, 2, 1, 2);
        placeBlock(view, writableBox, stairsEast, WIDTH - 3, 1, 2);
        generateBox(view, writableBox, 4, 3, 5, 4, 3, 17, sandstone, sandstone, false);
        generateBox(view, writableBox, WIDTH - 5, 3, 5, WIDTH - 5, 3, 17, sandstone, sandstone, false);
        generateBox(view, writableBox, 3, 1, 5, 4, 2, 16, air, air, false);
        generateBox(view, writableBox, WIDTH - 6, 1, 5, WIDTH - 5, 2, 16, air, air, false);

        for (int i = 5; i <= 17; i += 2) {
            placeBlock(view, writableBox, cutSandstone, 4, 1, i);
            placeBlock(view, writableBox, chiseledSandstone, 4, 2, i);
            placeBlock(view, writableBox, cutSandstone, WIDTH - 5, 1, i);
            placeBlock(view, writableBox, chiseledSandstone, WIDTH - 5, 2, i);
        }

        placeBlock(view, writableBox, orangeTerracotta, 10, 0, 7);
        placeBlock(view, writableBox, orangeTerracotta, 10, 0, 8);
        placeBlock(view, writableBox, orangeTerracotta, 9, 0, 9);
        placeBlock(view, writableBox, orangeTerracotta, 11, 0, 9);
        placeBlock(view, writableBox, orangeTerracotta, 8, 0, 10);
        placeBlock(view, writableBox, orangeTerracotta, 12, 0, 10);
        placeBlock(view, writableBox, orangeTerracotta, 7, 0, 10);
        placeBlock(view, writableBox, orangeTerracotta, 13, 0, 10);
        placeBlock(view, writableBox, orangeTerracotta, 9, 0, 11);
        placeBlock(view, writableBox, orangeTerracotta, 11, 0, 11);
        placeBlock(view, writableBox, orangeTerracotta, 10, 0, 12);
        placeBlock(view, writableBox, orangeTerracotta, 10, 0, 13);
        placeBlock(view, writableBox, blueTerracotta, 10, 0, 10);

        for (int i = 0; i <= WIDTH - 1; i += WIDTH - 1) {
            placeBlock(view, writableBox, cutSandstone, i, 2, 1);
            placeBlock(view, writableBox, orangeTerracotta, i, 2, 2);
            placeBlock(view, writableBox, cutSandstone, i, 2, 3);
            placeBlock(view, writableBox, cutSandstone, i, 3, 1);
            placeBlock(view, writableBox, orangeTerracotta, i, 3, 2);
            placeBlock(view, writableBox, cutSandstone, i, 3, 3);
            placeBlock(view, writableBox, orangeTerracotta, i, 4, 1);
            placeBlock(view, writableBox, chiseledSandstone, i, 4, 2);
            placeBlock(view, writableBox, orangeTerracotta, i, 4, 3);
            placeBlock(view, writableBox, cutSandstone, i, 5, 1);
            placeBlock(view, writableBox, orangeTerracotta, i, 5, 2);
            placeBlock(view, writableBox, cutSandstone, i, 5, 3);
            placeBlock(view, writableBox, orangeTerracotta, i, 6, 1);
            placeBlock(view, writableBox, chiseledSandstone, i, 6, 2);
            placeBlock(view, writableBox, orangeTerracotta, i, 6, 3);
            placeBlock(view, writableBox, orangeTerracotta, i, 7, 1);
            placeBlock(view, writableBox, orangeTerracotta, i, 7, 2);
            placeBlock(view, writableBox, orangeTerracotta, i, 7, 3);
            placeBlock(view, writableBox, cutSandstone, i, 8, 1);
            placeBlock(view, writableBox, cutSandstone, i, 8, 2);
            placeBlock(view, writableBox, cutSandstone, i, 8, 3);
        }

        for (int i = 2; i <= WIDTH - 3; i += WIDTH - 3 - 2) {
            placeBlock(view, writableBox, cutSandstone, i - 1, 2, 0);
            placeBlock(view, writableBox, orangeTerracotta, i, 2, 0);
            placeBlock(view, writableBox, cutSandstone, i + 1, 2, 0);
            placeBlock(view, writableBox, cutSandstone, i - 1, 3, 0);
            placeBlock(view, writableBox, orangeTerracotta, i, 3, 0);
            placeBlock(view, writableBox, cutSandstone, i + 1, 3, 0);
            placeBlock(view, writableBox, orangeTerracotta, i - 1, 4, 0);
            placeBlock(view, writableBox, chiseledSandstone, i, 4, 0);
            placeBlock(view, writableBox, orangeTerracotta, i + 1, 4, 0);
            placeBlock(view, writableBox, cutSandstone, i - 1, 5, 0);
            placeBlock(view, writableBox, orangeTerracotta, i, 5, 0);
            placeBlock(view, writableBox, cutSandstone, i + 1, 5, 0);
            placeBlock(view, writableBox, orangeTerracotta, i - 1, 6, 0);
            placeBlock(view, writableBox, chiseledSandstone, i, 6, 0);
            placeBlock(view, writableBox, orangeTerracotta, i + 1, 6, 0);
            placeBlock(view, writableBox, orangeTerracotta, i - 1, 7, 0);
            placeBlock(view, writableBox, orangeTerracotta, i, 7, 0);
            placeBlock(view, writableBox, orangeTerracotta, i + 1, 7, 0);
            placeBlock(view, writableBox, cutSandstone, i - 1, 8, 0);
            placeBlock(view, writableBox, cutSandstone, i, 8, 0);
            placeBlock(view, writableBox, cutSandstone, i + 1, 8, 0);
        }

        generateBox(view, writableBox, 8, 4, 0, 12, 6, 0, cutSandstone, cutSandstone, false);
        placeBlock(view, writableBox, air, 8, 6, 0);
        placeBlock(view, writableBox, air, 12, 6, 0);
        placeBlock(view, writableBox, orangeTerracotta, 9, 5, 0);
        placeBlock(view, writableBox, chiseledSandstone, 10, 5, 0);
        placeBlock(view, writableBox, orangeTerracotta, 11, 5, 0);
        generateBox(view, writableBox, 8, -14, 8, 12, -11, 12, cutSandstone, cutSandstone, false);
        generateBox(view, writableBox, 8, -10, 8, 12, -10, 12, chiseledSandstone, chiseledSandstone, false);
        generateBox(view, writableBox, 8, -9, 8, 12, -9, 12, cutSandstone, cutSandstone, false);
        generateBox(view, writableBox, 8, -8, 8, 12, -1, 12, sandstone, sandstone, false);
        generateBox(view, writableBox, 9, -11, 9, 11, -1, 11, air, air, false);
        placeBlock(view, writableBox, pressurePlate, 10, -11, 10);
        generateBox(view, writableBox, 9, -13, 9, 11, -13, 11, tnt, air, false);
        placeBlock(view, writableBox, air, 8, -11, 10);
        placeBlock(view, writableBox, air, 8, -10, 10);
        placeBlock(view, writableBox, chiseledSandstone, 7, -10, 10);
        placeBlock(view, writableBox, cutSandstone, 7, -11, 10);
        placeBlock(view, writableBox, air, 12, -11, 10);
        placeBlock(view, writableBox, air, 12, -10, 10);
        placeBlock(view, writableBox, chiseledSandstone, 13, -10, 10);
        placeBlock(view, writableBox, cutSandstone, 13, -11, 10);
        placeBlock(view, writableBox, air, 10, -11, 8);
        placeBlock(view, writableBox, air, 10, -10, 8);
        placeBlock(view, writableBox, chiseledSandstone, 10, -10, 7);
        placeBlock(view, writableBox, cutSandstone, 10, -11, 7);
        placeBlock(view, writableBox, air, 10, -11, 12);
        placeBlock(view, writableBox, air, 10, -10, 12);
        placeBlock(view, writableBox, chiseledSandstone, 10, -10, 13);
        placeBlock(view, writableBox, cutSandstone, 10, -11, 13);

        for (Direction direction : Direction.PLANE_HORIZONTAL_ORDER) {
            int idx = direction.get2DDataValue();
            if (!hasPlacedChest[idx]) {
                int dx = direction.stepX() * 2;
                int dz = direction.stepZ() * 2;
                hasPlacedChest[idx] = createChest(view, writableBox, random, chest, 10 + dx, -11, 10 + dz, "minecraft:chests/desert_pyramid");
            }
        }

        addCellar(view, writableBox, blocks, regionRandom, sandstone, sand, cutSandstone, chiseledSandstone,
            orangeTerracotta, blueTerracotta);
    }

    private static String facingName(Direction d) {
        return d.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * facing만 넘기면 blockstates 테이블(full-property canonical key)에 매칭이 안 돼 default(facing=north)로
     * 폴백하는 버그가 있었다 — 나머지 프로퍼티를 vanilla 배치 상태 그대로 명시한다.
     * shape=straight 근거: vanilla StructurePiece.placeBlock은 defaultBlockState 기반 상태를 mirror/rotate만
     * 해서 setBlock(flags=2)하고(StairBlock.rotate는 FACING만 변경), WorldGenRegion.setBlock은 updateShape를
     * 호출하지 않는다. stairs는 SHAPE_CHECK_BLOCKS(StructurePiece.java:50-70, 펜스·횃불만)에도 없어
     * postprocessing 마킹조차 없음 — 월드젠 단계 산출물은 항상 shape=straight (oracle 픽스처 실측 일치).
     */
    private int stairsState(BlockRegistry blocks, Direction facing) {
        return blocks.stateIdOrAir("minecraft:sandstone_stairs", Map.of(
            "facing", facingName(facing), "half", "bottom", "shape", "straight", "waterlogged", "false"));
    }

    /** 바닐라 ScatteredFeaturePiece.updateHeightPositionToLowestGroundHeight — MOTION_BLOCKING_NO_LEAVES 최소값(≈getSurfaceY, SURFACE_STRUCTURES 단계엔 나뭇잎 없음). */
    private boolean updateHeightPositionToLowestGroundHeight(FeatureWorldView view, int height) {
        if (heightPosition >= 0) {
            return true;
        }
        int min = Integer.MAX_VALUE;
        boolean any = false;
        for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
            for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
                int y = view.getMotionBlockingNoLeavesY(x, z) + 1; // getSurfaceY는 최상단 비-공기 y, heightmapPos는 그 위 1칸
                min = Math.min(min, y);
                any = true;
            }
        }
        if (!any) return false;
        heightPosition = min;
        boundingBox.move(0, heightPosition - boundingBox.minY() + height, 0);
        return true;
    }

    // ---- 지하실(addCellar) — 바닐라와 동일 블록 배치 + level.getRandom()(regionRandom) 소비 재현 ----

    private void addCellar(FeatureWorldView view, BoundingBox writableBox, BlockRegistry blocks,
                            NoiseRandom regionRandom,
                            int sandstone, int sand, int cutSandstone, int chiseledSandstone,
                            int orangeTerracotta, int blueTerracotta) {
        // 바닐라 Blocks.SANDSTONE_STAIRS.defaultBlockState()(facing=north).rotate(COUNTERCLOCKWISE_90) == facing west(로컬).
        // 이 로컬 facing도 placeBlock을 거치므로 조각 orientation에 따라 world-space facing으로 변환한다.
        int stairsCcw = stairsState(blocks, rotateFacing(Direction.WEST));
        int px = 16, py = -4, pz = 13;
        addCellarStairs(view, writableBox, regionRandom, stairsCcw, sand, sandstone, px, py, pz);
        addCellarRoom(view, writableBox, regionRandom, px, py, pz, sandstone, sand, cutSandstone, chiseledSandstone, orangeTerracotta, blueTerracotta);
    }

    private void addCellarStairs(FeatureWorldView view, BoundingBox writableBox, NoiseRandom regionRandom,
                                  int stairsCcw, int sand, int sandstone, int x, int y, int z) {
        placeBlock(view, writableBox, stairsCcw, 13, -1, 17);
        placeBlock(view, writableBox, stairsCcw, 14, -2, 17);
        placeBlock(view, writableBox, stairsCcw, 15, -3, 17);
        // vanilla addCellarStairs: level.getRandom().nextBoolean() — 순서상 이 지점에서 1회 소비.
        boolean randomBoolean = regionRandom.nextBoolean();
        placeBlock(view, writableBox, sand, x - 4, y + 4, z + 4);
        placeBlock(view, writableBox, sand, x - 3, y + 4, z + 4);
        placeBlock(view, writableBox, sand, x - 2, y + 4, z + 4);
        placeBlock(view, writableBox, sand, x - 1, y + 4, z + 4);
        placeBlock(view, writableBox, sand, x, y + 4, z + 4);
        placeBlock(view, writableBox, sand, x - 2, y + 3, z + 4);
        placeBlock(view, writableBox, randomBoolean ? sand : sandstone, x - 1, y + 3, z + 4);
        placeBlock(view, writableBox, !randomBoolean ? sand : sandstone, x, y + 3, z + 4);
        placeBlock(view, writableBox, sand, x - 1, y + 2, z + 4);
        placeBlock(view, writableBox, sandstone, x, y + 2, z + 4);
        placeBlock(view, writableBox, sand, x, y + 1, z + 4);
    }

    private void addCellarRoom(FeatureWorldView view, BoundingBox writableBox, NoiseRandom regionRandom,
                                int x, int y, int z,
                                int sandstone, int sand, int cutSandstone, int chiseledSandstone, int orangeTerracotta, int blueTerracotta) {
        generateBox(view, writableBox, x - 3, y + 1, z - 3, x - 3, y + 1, z + 2, cutSandstone, cutSandstone, true);
        generateBox(view, writableBox, x + 3, y + 1, z - 3, x + 3, y + 1, z + 2, cutSandstone, cutSandstone, true);
        generateBox(view, writableBox, x - 3, y + 1, z - 3, x + 3, y + 1, z - 2, cutSandstone, cutSandstone, true);
        generateBox(view, writableBox, x - 3, y + 1, z + 3, x + 3, y + 1, z + 3, cutSandstone, cutSandstone, true);
        generateBox(view, writableBox, x - 3, y + 2, z - 3, x - 3, y + 2, z + 2, chiseledSandstone, chiseledSandstone, true);
        generateBox(view, writableBox, x + 3, y + 2, z - 3, x + 3, y + 2, z + 2, chiseledSandstone, chiseledSandstone, true);
        generateBox(view, writableBox, x - 3, y + 2, z - 3, x + 3, y + 2, z - 2, chiseledSandstone, chiseledSandstone, true);
        generateBox(view, writableBox, x - 3, y + 2, z + 3, x + 3, y + 2, z + 3, chiseledSandstone, chiseledSandstone, true);
        generateBox(view, writableBox, x - 3, -1, z - 3, x - 3, -1, z + 2, cutSandstone, cutSandstone, true);
        generateBox(view, writableBox, x + 3, -1, z - 3, x + 3, -1, z + 2, cutSandstone, cutSandstone, true);
        generateBox(view, writableBox, x - 3, -1, z - 3, x + 3, -1, z - 2, cutSandstone, cutSandstone, true);
        generateBox(view, writableBox, x - 3, -1, z + 3, x + 3, -1, z + 3, cutSandstone, cutSandstone, true);
        // placeSandBox(x-2, y+1, z-2, x+2, y+3, z+2): 위치만 기록(y→x→z 순)
        for (int yy = y + 1; yy <= y + 3; yy++)
            for (int xx = x - 2; xx <= x + 2; xx++)
                for (int zz = z - 2; zz <= z + 2; zz++) recordSand(xx, yy, zz);
        int roofY = y + 4;
        for (int rx = x - 2; rx <= x + 2; rx++) {
            for (int rz = z - 2; rz <= z + 2; rz++) {
                int roofState = (regionRandom.nextFloat() < 0.33F) ? sandstone : sand;
                placeBlock(view, writableBox, roofState, rx, roofY, rz);
            }
        }
        {
            // RandomSource.create(level.getSeed()).forkPositional().at(worldPos(x-2, roofY, z-2))
            org.domi.furnace.server.world.gen.carver.LegacyRandom base = new org.domi.furnace.server.world.gen.carver.LegacyRandom(view.worldSeed());
            long fork = base.nextLong();
            long ps = org.domi.furnace.server.world.gen.structure.template.PlaceSettings.getSeed(getWorldX(x - 2, z - 2), getWorldY(roofY), getWorldZ(x - 2, z - 2));
            org.domi.furnace.server.world.gen.carver.LegacyRandom pr = new org.domi.furnace.server.world.gen.carver.LegacyRandom(ps ^ fork);
            int rx = pr.nextInt(5) + (x - 2);
            int rz = pr.nextInt(5) + (z - 2);
            randomCollapsedRoofPos = new int[]{getWorldX(rx, rz), getWorldY(roofY), getWorldZ(rx, rz)};
        }
        placeBlock(view, writableBox, blueTerracotta, x, y, z);
        placeBlock(view, writableBox, orangeTerracotta, x + 1, y, z - 1);
        placeBlock(view, writableBox, orangeTerracotta, x + 1, y, z + 1);
        placeBlock(view, writableBox, orangeTerracotta, x - 1, y, z - 1);
        placeBlock(view, writableBox, orangeTerracotta, x - 1, y, z + 1);
        placeBlock(view, writableBox, orangeTerracotta, x + 2, y, z);
        placeBlock(view, writableBox, orangeTerracotta, x - 2, y, z);
        placeBlock(view, writableBox, orangeTerracotta, x, y, z + 2);
        placeBlock(view, writableBox, orangeTerracotta, x, y, z - 2);
        placeBlock(view, writableBox, orangeTerracotta, x + 3, y, z);
        recordSand(x + 3, y + 1, z);
        recordSand(x + 3, y + 2, z);
        placeBlock(view, writableBox, cutSandstone, x + 4, y + 1, z);
        placeBlock(view, writableBox, chiseledSandstone, x + 4, y + 2, z);
        placeBlock(view, writableBox, orangeTerracotta, x - 3, y, z);
        recordSand(x - 3, y + 1, z);
        recordSand(x - 3, y + 2, z);
        placeBlock(view, writableBox, cutSandstone, x - 4, y + 1, z);
        placeBlock(view, writableBox, chiseledSandstone, x - 4, y + 2, z);
        placeBlock(view, writableBox, orangeTerracotta, x, y, z + 3);
        recordSand(x, y + 1, z + 3);
        recordSand(x, y + 2, z + 3);
        placeBlock(view, writableBox, orangeTerracotta, x, y, z - 3);
        recordSand(x, y + 1, z - 3);
        recordSand(x, y + 2, z - 3);
        placeBlock(view, writableBox, cutSandstone, x, y + 1, z - 4);
        placeBlock(view, writableBox, chiseledSandstone, x, -2, z - 4);
    }

    /** 바닐라 DesertPyramidStructure.afterPlace — 의심스러운 모래(고고학) 배치. */
    static void afterPlace(PostCtx c, StructureStart start) {
        java.util.TreeSet<int[]> set = new java.util.TreeSet<>((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1])
            : a[2] != b[2] ? Integer.compare(a[2], b[2]) : Integer.compare(a[0], b[0]));   // Vec3i.compareTo: y, z, x
        for (StructurePiece p : start.pieces()) {
            if (p instanceof DesertPyramidPiece d) {
                set.addAll(d.potentialSuspiciousSand);
                placeSuspiciousSand(c, d.randomCollapsedRoofPos);
            }
        }
        java.util.List<int[]> list = new java.util.ArrayList<>(set);
        BoundingBox bb = null;   // pieces.calculateBoundingBox(): 높이 보정 이후의 현재 상자
        for (StructurePiece p : start.pieces()) bb = bb == null ? p.boundingBox() : BoundingBox.encapsulate(bb, p.boundingBox());
        int cx = bb.minX() + (bb.maxX() - bb.minX() + 1) / 2;
        int cy = bb.minY() + (bb.maxY() - bb.minY() + 1) / 2;
        int cz = bb.minZ() + (bb.maxZ() - bb.minZ() + 1) / 2;
        org.domi.furnace.server.world.gen.carver.LegacyRandom base = new org.domi.furnace.server.world.gen.carver.LegacyRandom(c.view.worldSeed());
        long fork = base.nextLong();
        org.domi.furnace.server.world.gen.carver.LegacyRandom r = new org.domi.furnace.server.world.gen.carver.LegacyRandom(
            org.domi.furnace.server.world.gen.structure.template.PlaceSettings.getSeed(cx, cy, cz) ^ fork);
        for (int i = list.size(); i > 1; i--) {
            int j = r.nextInt(i);
            list.set(i - 1, list.set(j, list.get(i - 1)));
        }
        int n = Math.min(set.size(), r.nextInt(4) + 5);
        int sand = c.blocks().defaultStateId("minecraft:sand");
        for (int[] p : list) {
            if (n > 0) {
                n--;
                placeSuspiciousSand(c, p);
            } else if (c.writableBox.isInside(p[0], p[1], p[2])) {
                c.view.setBlock(p[0], p[1], p[2], sand);
            }
        }
    }

    private static void placeSuspiciousSand(PostCtx c, int[] p) {
        if (!c.writableBox.isInside(p[0], p[1], p[2])) return;
        c.view.setBlock(p[0], p[1], p[2], c.blocks().defaultStateId("minecraft:suspicious_sand"));
        org.domi.furnace.server.net.io.nbt.NbtCompound nbt = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        nbt.putString("id", "minecraft:brushable_block");
        nbt.putInt("x", p[0]); nbt.putInt("y", p[1]); nbt.putInt("z", p[2]);
        nbt.putString("LootTable", "minecraft:archaeology/desert_pyramid");
        nbt.putLong("LootTableSeed", ((long) (p[0] & 0x3FFFFFF) << 38) | ((long) (p[2] & 0x3FFFFFF) << 12) | (p[1] & 0xFFFL));   // BlockPos.asLong
        c.view.putGeneratedBlockEntity(p[0], p[1], p[2], nbt);
    }
}
