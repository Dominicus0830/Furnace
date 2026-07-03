// 구조물 조각 추상 베이스 — BoundingBox·회전 + postProcess에 필요한 블록배치 헬퍼(바닐라 StructurePiece 미러링).
package org.domi.furnace.server.world.gen.structure;

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
     * regionRandom: 바닐라 WorldGenLevel.getRandom() 미러(순수 XoroshiroRandomSource, decoration용 WorldgenRandom과는 별개 소비 규칙).
     */
    public abstract void postProcess(FeatureWorldView view, BlockRegistry blocks, WorldgenRandom random,
            org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource regionRandom, BoundingBox writableBox);

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

    protected int getWorldX(int x, int z) {
        return switch (orientation) {
            case NORTH, SOUTH -> boundingBox.minX() + x;
            case WEST -> boundingBox.maxX() - z;
            case EAST -> boundingBox.minX() + z;
        };
    }

    protected int getWorldY(int y) {
        return y + boundingBox.minY();
    }

    protected int getWorldZ(int x, int z) {
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
}
