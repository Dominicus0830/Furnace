// 사막 피라미드 조각 — 바닐라 DesertPyramidPiece.postProcess 라인단위 이식(블록+RNG 소비만, loot 내용물 제외).
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;

import java.util.Map;

public final class DesertPyramidPiece extends StructurePiece {

    public static final int WIDTH = 21;
    public static final int HEIGHT = 15;
    public static final int DEPTH = 21;
    private static final int MIN_Y = -64; // ChunkColumn.MIN_Y

    /** vanilla ScatteredFeaturePiece.heightPosition — 최초 postProcess 호출에서 1회만 계산.-1=미계산. */
    private int heightPosition = -1;
    private final boolean[] hasPlacedChest = new boolean[4];

    public DesertPyramidPiece(WorldgenRandom random, int minBlockX, int minBlockZ) {
        this(Direction.randomHorizontal(random), minBlockX, minBlockZ);
    }

    private DesertPyramidPiece(Direction orientation, int minBlockX, int minBlockZ) {
        super(makeBoundingBox(minBlockX, 64, minBlockZ, orientation, WIDTH, HEIGHT, DEPTH), orientation);
    }

    @Override
    public void postProcess(FeatureWorldView view, BlockRegistry blocks, WorldgenRandom random,
            XoroshiroRandomSource regionRandom, BoundingBox writableBox) {
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
        int stairsNorth = blocks.stateIdOrAir("minecraft:sandstone_stairs", Map.of("facing", facingName(rotateFacing(Direction.NORTH))));
        int stairsSouth = blocks.stateIdOrAir("minecraft:sandstone_stairs", Map.of("facing", facingName(rotateFacing(Direction.SOUTH))));
        int stairsEast = blocks.stateIdOrAir("minecraft:sandstone_stairs", Map.of("facing", facingName(rotateFacing(Direction.EAST))));
        int stairsWest = blocks.stateIdOrAir("minecraft:sandstone_stairs", Map.of("facing", facingName(rotateFacing(Direction.WEST))));
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
                // vanilla BuiltInLootTables.DESERT_PYRAMID — loot 내용물은 생성하지 않는다(Do NOT).
                hasPlacedChest[idx] = createChest(view, writableBox, random, chest, 10 + dx, -11, 10 + dz);
            }
        }

        addCellar(view, writableBox, blocks, regionRandom, sandstone, sand, cutSandstone, chiseledSandstone,
            orangeTerracotta, blueTerracotta);
    }

    private static String facingName(Direction d) {
        return d.name().toLowerCase(java.util.Locale.ROOT);
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
                int y = view.getSurfaceY(x, z) + 1; // getSurfaceY는 최상단 비-공기 y, heightmapPos는 그 위 1칸
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
                            XoroshiroRandomSource regionRandom,
                            int sandstone, int sand, int cutSandstone, int chiseledSandstone,
                            int orangeTerracotta, int blueTerracotta) {
        // 바닐라 Blocks.SANDSTONE_STAIRS.defaultBlockState()(facing=north).rotate(COUNTERCLOCKWISE_90) == facing west(로컬).
        // 이 로컬 facing도 placeBlock을 거치므로 조각 orientation에 따라 world-space facing으로 변환한다.
        int stairsCcw = blocks.stateIdOrAir("minecraft:sandstone_stairs", Map.of("facing", facingName(rotateFacing(Direction.WEST))));
        int px = 16, py = -4, pz = 13;
        addCellarStairs(view, writableBox, regionRandom, stairsCcw, sand, sandstone, px, py, pz);
        addCellarRoom(view, writableBox, regionRandom, px, py, pz, sandstone, sand, cutSandstone, chiseledSandstone, orangeTerracotta, blueTerracotta);
    }

    private void addCellarStairs(FeatureWorldView view, BoundingBox writableBox, XoroshiroRandomSource regionRandom,
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

    private void addCellarRoom(FeatureWorldView view, BoundingBox writableBox, XoroshiroRandomSource regionRandom,
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
        // placeSandBox(suspicious sand 후보 위치) — 블록 미배치(위치 기록/장식용, RNG 미소비)
        // placeCollapsedRoof: 5x5(x-2..x+2, z-2..z+2) 각 칸마다 level.getRandom().nextFloat()<0.33F 소비 후 블록 배치.
        // 이후 randomCollapsedRoofPos 계산용 별도 RandomSource.create(level.getSeed())...는 regionRandom을 소비하지 않고
        // 블록도 배치하지 않으므로(장식용 위치 마커) 재현 불필요.
        int roofY = y + 4;
        for (int rx = x - 2; rx <= x + 2; rx++) {
            for (int rz = z - 2; rz <= z + 2; rz++) {
                int roofState = (regionRandom.nextFloat() < 0.33F) ? sandstone : sand;
                placeBlock(view, writableBox, roofState, rx, roofY, rz);
            }
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
        placeBlock(view, writableBox, cutSandstone, x + 4, y + 1, z);
        placeBlock(view, writableBox, chiseledSandstone, x + 4, y + 2, z);
        placeBlock(view, writableBox, orangeTerracotta, x - 3, y, z);
        placeBlock(view, writableBox, cutSandstone, x - 4, y + 1, z);
        placeBlock(view, writableBox, chiseledSandstone, x - 4, y + 2, z);
        placeBlock(view, writableBox, orangeTerracotta, x, y, z + 3);
        placeBlock(view, writableBox, orangeTerracotta, x, y, z - 3);
        placeBlock(view, writableBox, cutSandstone, x, y + 1, z - 4);
        placeBlock(view, writableBox, chiseledSandstone, x, -2, z - 4);
    }
}
