// 요새(스트롱홀드) — 바닐라 StrongholdStructure + StrongholdPieces(조각 가중치 표, 복도·교차로·계단·도서관·감옥·엔드 포탈 방).
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;

public final class StrongholdStructure extends StructureType {

    public StrongholdStructure(StructureRegistry.Def def) {}

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        // 바닐라: new GenerationStub(chunkPos.getWorldPosition(), builder -> generatePieces(builder, ctx))
        return Optional.of(new Stub(c.minBlockX(), 0, c.minBlockZ(), b -> generatePieces(b, c)));
    }

    private static void generatePieces(PiecesBuilder b, GenContext c) {
        int i = 0;
        StartPiece start;
        do {
            b.clear();
            c.random.setLargeFeatureSeed(c.seed + (long) (i++), c.chunkX, c.chunkZ);
            // StrongholdPieces.resetPieces() — 정적 상태 대신 StartPiece 가 조각 가중치 상태를 갖는다(스레드 안전)
            start = new StartPiece(c.random, c.chunkX * 16 + 2, c.chunkZ * 16 + 2);
            b.addPiece(start);
            start.addChildren(start, b, c.random);
            List<Piece> pending = start.pendingChildren;
            while (!pending.isEmpty()) {
                int j = c.random.nextInt(pending.size());
                pending.remove(j).addChildren(start, b, c.random);
            }
            b.moveBelowSeaLevel(c.terrain.seaLevel(), c.terrain.minY(), c.random, 10);
        } while (b.isEmpty() || start.portalRoomPiece == null);
    }

    // ------------------------------------------------------------------ 상태 모음

    static final class St {
        final StructureEngine engine;
        final int STONE_BRICKS, CAVE_AIR, SLAB, SLAB_DOUBLE, BRICK_SLAB, COBWEB, PLANKS, BOOKSHELF, TORCH, COBBLE, WATER, LAVA;
        final int TORCH_N, TORCH_E, TORCH_S, TORCH_W, LADDER_S, LADDER_W, BRICK_STAIRS_N, COBBLE_STAIRS_S;
        final int OAK_DOOR_LOWER, OAK_DOOR_UPPER, IRON_DOOR_LOWER, IRON_DOOR_UPPER, IRON_DOOR_W_LOWER, IRON_DOOR_W_UPPER;
        final int BUTTON_N, BUTTON_S, SPAWNER, END_PORTAL;
        final int BARS_NS, BARS_EW, BARS_NSE, BARS_W, BARS_EW2, BARS_E;
        final int FENCE_NS, FENCE_EW, FENCE_ALL, FENCE_NE, FENCE_SE, FENCE_NW, FENCE_SW, FENCE_E, FENCE_W;
        final int[] FRAME = new int[8];

        St(StructureEngine e) {
            engine = e;
            STONE_BRICKS = e.state("minecraft:stone_bricks");
            CAVE_AIR = e.state("minecraft:cave_air");
            SLAB = e.state("minecraft:smooth_stone_slab");
            SLAB_DOUBLE = e.state("minecraft:smooth_stone_slab[type=double]");
            BRICK_SLAB = e.state("minecraft:stone_brick_slab");
            COBWEB = e.state("minecraft:cobweb");
            PLANKS = e.state("minecraft:oak_planks");
            BOOKSHELF = e.state("minecraft:bookshelf");
            TORCH = e.state("minecraft:torch");
            COBBLE = e.state("minecraft:cobblestone");
            WATER = e.state("minecraft:water");
            LAVA = e.state("minecraft:lava");
            TORCH_N = e.state("minecraft:wall_torch[facing=north]");
            TORCH_E = e.state("minecraft:wall_torch[facing=east]");
            TORCH_S = e.state("minecraft:wall_torch[facing=south]");
            TORCH_W = e.state("minecraft:wall_torch[facing=west]");
            LADDER_S = e.state("minecraft:ladder[facing=south]");
            LADDER_W = e.state("minecraft:ladder[facing=west]");
            BRICK_STAIRS_N = e.state("minecraft:stone_brick_stairs[facing=north]");
            COBBLE_STAIRS_S = e.state("minecraft:cobblestone_stairs[facing=south]");
            OAK_DOOR_LOWER = e.state("minecraft:oak_door");
            OAK_DOOR_UPPER = e.state("minecraft:oak_door[half=upper]");
            IRON_DOOR_LOWER = e.state("minecraft:iron_door");
            IRON_DOOR_UPPER = e.state("minecraft:iron_door[half=upper]");
            IRON_DOOR_W_LOWER = e.state("minecraft:iron_door[facing=west]");
            IRON_DOOR_W_UPPER = e.state("minecraft:iron_door[facing=west,half=upper]");
            BUTTON_N = e.state("minecraft:stone_button[facing=north]");
            BUTTON_S = e.state("minecraft:stone_button[facing=south]");
            SPAWNER = e.state("minecraft:spawner");
            END_PORTAL = e.state("minecraft:end_portal");
            BARS_NS = e.state("minecraft:iron_bars[north=true,south=true]");
            BARS_EW = e.state("minecraft:iron_bars[west=true,east=true]");
            BARS_NSE = e.state("minecraft:iron_bars[north=true,south=true,east=true]");
            BARS_W = e.state("minecraft:iron_bars[west=true]");
            BARS_EW2 = e.state("minecraft:iron_bars[east=true,west=true]");
            BARS_E = e.state("minecraft:iron_bars[east=true]");
            FENCE_NS = e.state("minecraft:oak_fence[north=true,south=true]");
            FENCE_EW = e.state("minecraft:oak_fence[west=true,east=true]");
            FENCE_ALL = e.state("minecraft:oak_fence[north=true,south=true,west=true,east=true]");
            FENCE_NE = e.state("minecraft:oak_fence[north=true,east=true]");
            FENCE_SE = e.state("minecraft:oak_fence[south=true,east=true]");
            FENCE_NW = e.state("minecraft:oak_fence[north=true,west=true]");
            FENCE_SW = e.state("minecraft:oak_fence[south=true,west=true]");
            FENCE_E = e.state("minecraft:oak_fence[east=true]");
            FENCE_W = e.state("minecraft:oak_fence[west=true]");
            String[] facings = {"north", "south", "east", "west"};
            for (int f = 0; f < 4; f++) {
                FRAME[f * 2] = e.state("minecraft:end_portal_frame[facing=" + facings[f] + ",eye=false]");
                FRAME[f * 2 + 1] = e.state("minecraft:end_portal_frame[facing=" + facings[f] + ",eye=true]");
            }
        }

        private static volatile St cached;

        static St of(StructureEngine e) {
            St s = cached;
            if (s == null || s.engine != e) {
                s = new St(e);
                cached = s;
            }
            return s;
        }
    }

    // ------------------------------------------------------------------ 조각 종류와 가중치 표

    private enum Kind { STRAIGHT, PRISON_HALL, LEFT_TURN, RIGHT_TURN, ROOM_CROSSING, STRAIGHT_STAIRS_DOWN, STAIRS_DOWN, FIVE_CROSSING, CHEST_CORRIDOR, LIBRARY, PORTAL_ROOM }

    /** 바닐라 StrongholdPieces.PieceWeight. */
    static class PieceWeight {
        final Kind kind;
        final int weight;
        int placeCount;
        final int maxPlaceCount;

        PieceWeight(Kind kind, int weight, int maxPlaceCount) {
            this.kind = kind;
            this.weight = weight;
            this.maxPlaceCount = maxPlaceCount;
        }

        boolean doPlace(int genDepth) {
            return maxPlaceCount == 0 || placeCount < maxPlaceCount;
        }

        boolean isValid() {
            return maxPlaceCount == 0 || placeCount < maxPlaceCount;
        }
    }

    /** resetPieces() — 매 시도마다 새 가중치 표(placeCount 0). */
    private static List<PieceWeight> newPieceSet() {
        List<PieceWeight> list = new ArrayList<>();
        list.add(new PieceWeight(Kind.STRAIGHT, 40, 0));
        list.add(new PieceWeight(Kind.PRISON_HALL, 5, 5));
        list.add(new PieceWeight(Kind.LEFT_TURN, 20, 0));
        list.add(new PieceWeight(Kind.RIGHT_TURN, 20, 0));
        list.add(new PieceWeight(Kind.ROOM_CROSSING, 10, 6));
        list.add(new PieceWeight(Kind.STRAIGHT_STAIRS_DOWN, 5, 5));
        list.add(new PieceWeight(Kind.STAIRS_DOWN, 5, 5));
        list.add(new PieceWeight(Kind.FIVE_CROSSING, 5, 4));
        list.add(new PieceWeight(Kind.CHEST_CORRIDOR, 5, 4));
        list.add(new PieceWeight(Kind.LIBRARY, 10, 2) {
            @Override
            boolean doPlace(int genDepth) {
                return super.doPlace(genDepth) && genDepth > 4;
            }
        });
        list.add(new PieceWeight(Kind.PORTAL_ROOM, 20, 1) {
            @Override
            boolean doPlace(int genDepth) {
                return super.doPlace(genDepth) && genDepth > 5;
            }
        });
        return list;
    }

    /** updatePieceWeight() — 더 놓을 수 있는 조각이 있는지 + 총 가중치 재계산. */
    private static boolean updatePieceWeight(StartPiece start) {
        boolean hasMore = false;
        start.totalWeight = 0;
        for (PieceWeight w : start.pieceSet) {
            if (w.maxPlaceCount > 0 && w.placeCount < w.maxPlaceCount) hasMore = true;
            start.totalWeight += w.weight;
        }
        return hasMore;
    }

    private static Piece findAndCreatePieceFactory(Kind kind, PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
        return switch (kind) {
            case STRAIGHT -> Straight.createPiece(b, r, x, y, z, dir, depth);
            case PRISON_HALL -> PrisonHall.createPiece(b, r, x, y, z, dir, depth);
            case LEFT_TURN -> LeftTurn.createPiece(b, r, x, y, z, dir, depth);
            case RIGHT_TURN -> RightTurn.createPiece(b, r, x, y, z, dir, depth);
            case ROOM_CROSSING -> RoomCrossing.createPiece(b, r, x, y, z, dir, depth);
            case STRAIGHT_STAIRS_DOWN -> StraightStairsDown.createPiece(b, r, x, y, z, dir, depth);
            case STAIRS_DOWN -> StairsDown.createPiece(b, r, x, y, z, dir, depth);
            case FIVE_CROSSING -> FiveCrossing.createPiece(b, r, x, y, z, dir, depth);
            case CHEST_CORRIDOR -> ChestCorridor.createPiece(b, r, x, y, z, dir, depth);
            case LIBRARY -> Library.createPiece(b, r, x, y, z, dir, depth);
            case PORTAL_ROOM -> PortalRoom.createPiece(b, x, y, z, dir, depth);
        };
    }

    private static Piece generatePieceFromSmallDoor(StartPiece start, PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
        if (!updatePieceWeight(start)) return null;
        if (start.imposedPiece != null) {
            Piece p = findAndCreatePieceFactory(start.imposedPiece, b, r, x, y, z, dir, depth);
            start.imposedPiece = null;
            if (p != null) return p;
        }
        int tries = 0;
        while (tries < 5) {
            tries++;
            int roll = r.nextInt(start.totalWeight);
            for (PieceWeight w : start.pieceSet) {
                roll -= w.weight;
                if (roll < 0) {
                    if (!w.doPlace(depth) || w == start.previousPiece) break;
                    Piece p = findAndCreatePieceFactory(w.kind, b, r, x, y, z, dir, depth);
                    if (p != null) {
                        w.placeCount++;
                        start.previousPiece = w;
                        if (!w.isValid()) start.pieceSet.remove(w);
                        return p;
                    }
                }
            }
        }
        BoundingBox box = FillerCorridor.findPieceBox(b, r, x, y, z, dir);
        return box != null && box.minY() > 1 ? new FillerCorridor(depth, box, dir) : null;
    }

    private static StructurePiece generateAndAddPiece(StartPiece start, PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
        if (depth > 50) return null;
        if (Math.abs(x - start.boundingBox().minX()) <= 112 && Math.abs(z - start.boundingBox().minZ()) <= 112) {
            Piece p = generatePieceFromSmallDoor(start, b, r, x, y, z, dir, depth + 1);
            if (p != null) {
                b.addPiece(p);
                start.pendingChildren.add(p);
            }
            return p;
        }
        return null;
    }

    /** BoundingBox.orientBox(x, y, z, offX, offY, offZ, width, height, depth, dir). */
    static BoundingBox orientBox(int x, int y, int z, int ox, int oy, int oz, int w, int h, int d, Direction dir) {
        return switch (dir) {
            case SOUTH -> new BoundingBox(x + ox, y + oy, z + oz, x + w - 1 + ox, y + h - 1 + oy, z + d - 1 + oz);
            case NORTH -> new BoundingBox(x + ox, y + oy, z - d + 1 + oz, x + w - 1 + ox, y + h - 1 + oy, z + oz);
            case WEST -> new BoundingBox(x - d + 1 + oz, y + oy, z + ox, x + oz, y + h - 1 + oy, z + w - 1 + ox);
            case EAST -> new BoundingBox(x + oz, y + oy, z + ox, x + d - 1 + oz, y + h - 1 + oy, z + w - 1 + ox);
        };
    }

    private static int ySpan(BoundingBox b) { return b.maxY() - b.minY() + 1; }

    // ------------------------------------------------------------------ 블록 선택기 (바닐라 SmoothStoneSelector)

    static final class StoneBricksSelector implements StructurePiece.BlockSelector {
        private final St s;
        private int state;

        StoneBricksSelector(St s) { this.s = s; }

        @Override
        public void next(LegacyRandom random, int x, int y, int z, boolean edge) {
            if (edge) {
                float f = random.nextFloat();
                if (f < 0.2F) state = s.engine.state("minecraft:cracked_stone_bricks");
                else if (f < 0.5F) state = s.engine.state("minecraft:mossy_stone_bricks");
                else if (f < 0.55F) state = s.engine.state("minecraft:infested_stone_bricks");
                else state = s.STONE_BRICKS;
            } else {
                state = s.CAVE_AIR;
            }
        }

        @Override
        public int state() { return state; }
    }

    // ------------------------------------------------------------------ 공통 기반 (바닐라 StrongholdPiece)

    enum SmallDoorType { OPENING, WOOD_DOOR, GRATES, IRON_DOOR }

    abstract static class Piece extends StructurePiece {
        protected SmallDoorType entryDoor = SmallDoorType.OPENING;

        /** 바닐라 StrongholdPiece: EntryDoor = 문 종류 이름. */
        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            t.putString("EntryDoor", entryDoor.name());
        }

        protected Piece(int genDepth, BoundingBox box, Direction dir) {
            super(box, dir);
            this.genDepth = genDepth;
        }

        /** 바닐라 addChildren(startPiece, accessor, random). */
        abstract void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r);

        protected static boolean isOkBox(BoundingBox box) {
            return box != null && box.minY() > 10;
        }

        protected SmallDoorType randomSmallDoor(LegacyRandom r) {
            return switch (r.nextInt(5)) {
                case 2 -> SmallDoorType.WOOD_DOOR;
                case 3 -> SmallDoorType.GRATES;
                case 4 -> SmallDoorType.IRON_DOOR;
                default -> SmallDoorType.OPENING;
            };
        }

        protected void generateSmallDoor(PostCtx c, BoundingBox box, SmallDoorType type, int x, int y, int z) {
            St s = St.of(c.engine);
            switch (type) {
                case OPENING -> generateBox(c, box, x, y, z, x + 3 - 1, y + 3 - 1, z, s.CAVE_AIR, s.CAVE_AIR, false);
                case WOOD_DOOR -> {
                    placeBlock(c, s.STONE_BRICKS, x, y, z, box);
                    placeBlock(c, s.STONE_BRICKS, x, y + 1, z, box);
                    placeBlock(c, s.STONE_BRICKS, x, y + 2, z, box);
                    placeBlock(c, s.STONE_BRICKS, x + 1, y + 2, z, box);
                    placeBlock(c, s.STONE_BRICKS, x + 2, y + 2, z, box);
                    placeBlock(c, s.STONE_BRICKS, x + 2, y + 1, z, box);
                    placeBlock(c, s.STONE_BRICKS, x + 2, y, z, box);
                    placeBlock(c, s.OAK_DOOR_LOWER, x + 1, y, z, box);
                    placeBlock(c, s.OAK_DOOR_UPPER, x + 1, y + 1, z, box);
                }
                case GRATES -> {
                    placeBlock(c, s.CAVE_AIR, x + 1, y, z, box);
                    placeBlock(c, s.CAVE_AIR, x + 1, y + 1, z, box);
                    placeBlock(c, s.BARS_W, x, y, z, box);
                    placeBlock(c, s.BARS_W, x, y + 1, z, box);
                    placeBlock(c, s.BARS_EW2, x, y + 2, z, box);
                    placeBlock(c, s.BARS_EW2, x + 1, y + 2, z, box);
                    placeBlock(c, s.BARS_EW2, x + 2, y + 2, z, box);
                    placeBlock(c, s.BARS_E, x + 2, y + 1, z, box);
                    placeBlock(c, s.BARS_E, x + 2, y, z, box);
                }
                case IRON_DOOR -> {
                    placeBlock(c, s.STONE_BRICKS, x, y, z, box);
                    placeBlock(c, s.STONE_BRICKS, x, y + 1, z, box);
                    placeBlock(c, s.STONE_BRICKS, x, y + 2, z, box);
                    placeBlock(c, s.STONE_BRICKS, x + 1, y + 2, z, box);
                    placeBlock(c, s.STONE_BRICKS, x + 2, y + 2, z, box);
                    placeBlock(c, s.STONE_BRICKS, x + 2, y + 1, z, box);
                    placeBlock(c, s.STONE_BRICKS, x + 2, y, z, box);
                    placeBlock(c, s.IRON_DOOR_LOWER, x + 1, y, z, box);
                    placeBlock(c, s.IRON_DOOR_UPPER, x + 1, y + 1, z, box);
                    placeBlock(c, s.BUTTON_N, x + 2, y + 1, z + 1, box);
                    placeBlock(c, s.BUTTON_S, x + 2, y + 1, z - 1, box);
                }
            }
        }

        protected StructurePiece forwardChild(StartPiece start, PiecesBuilder b, LegacyRandom r, int xOff, int yOff) {
            BoundingBox bb = boundingBox;
            return switch (orientation) {
                case NORTH -> generateAndAddPiece(start, b, r, bb.minX() + xOff, bb.minY() + yOff, bb.minZ() - 1, orientation, genDepth);
                case SOUTH -> generateAndAddPiece(start, b, r, bb.minX() + xOff, bb.minY() + yOff, bb.maxZ() + 1, orientation, genDepth);
                case WEST -> generateAndAddPiece(start, b, r, bb.minX() - 1, bb.minY() + yOff, bb.minZ() + xOff, orientation, genDepth);
                case EAST -> generateAndAddPiece(start, b, r, bb.maxX() + 1, bb.minY() + yOff, bb.minZ() + xOff, orientation, genDepth);
            };
        }

        protected StructurePiece leftChild(StartPiece start, PiecesBuilder b, LegacyRandom r, int yOff, int zOff) {
            BoundingBox bb = boundingBox;
            return switch (orientation) {
                case NORTH, SOUTH -> generateAndAddPiece(start, b, r, bb.minX() - 1, bb.minY() + yOff, bb.minZ() + zOff, Direction.WEST, genDepth);
                case WEST, EAST -> generateAndAddPiece(start, b, r, bb.minX() + zOff, bb.minY() + yOff, bb.minZ() - 1, Direction.NORTH, genDepth);
            };
        }

        protected StructurePiece rightChild(StartPiece start, PiecesBuilder b, LegacyRandom r, int yOff, int zOff) {
            BoundingBox bb = boundingBox;
            return switch (orientation) {
                case NORTH, SOUTH -> generateAndAddPiece(start, b, r, bb.maxX() + 1, bb.minY() + yOff, bb.minZ() + zOff, Direction.EAST, genDepth);
                case WEST, EAST -> generateAndAddPiece(start, b, r, bb.minX() + zOff, bb.minY() + yOff, bb.maxZ() + 1, Direction.SOUTH, genDepth);
            };
        }
    }

    /** 바닐라 StrongholdPieces.StartPiece(+ 조각 가중치 상태). */
    static final class StartPiece extends StairsDown {
        @Override
        public String pieceType() { return "shstart"; }

        PieceWeight previousPiece;
        PortalRoom portalRoomPiece;
        final List<Piece> pendingChildren = new ArrayList<>();
        final List<PieceWeight> pieceSet = newPieceSet();
        Kind imposedPiece;
        int totalWeight;

        StartPiece(LegacyRandom random, int x, int z) {
            super(0, x, z, Direction.randomHorizontal((org.domi.furnace.server.world.gen.carver.WorldgenRandom) random));
        }
    }

    // ------------------------------------------------------------------ 조각들

    static class ChestCorridor extends Piece {
        @Override
        public String pieceType() { return "shcc"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putBoolean("Chest", hasPlacedChest);
        }

        private boolean hasPlacedChest;

        ChestCorridor(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.entryDoor = randomSmallDoor(r);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forwardChild(start, b, r, 1, 1);
        }

        static ChestCorridor createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -1, -1, 0, 5, 5, 7, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new ChestCorridor(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            St s = St.of(c.engine);
            generateBox(c, box, 0, 0, 0, 4, 4, 6, true, r, new StoneBricksSelector(s));
            generateSmallDoor(c, box, entryDoor, 1, 1, 0);
            generateSmallDoor(c, box, SmallDoorType.OPENING, 1, 1, 6);
            generateBox(c, box, 3, 1, 2, 3, 1, 4, s.STONE_BRICKS, s.STONE_BRICKS, false);
            placeBlock(c, s.BRICK_SLAB, 3, 1, 1, box);
            placeBlock(c, s.BRICK_SLAB, 3, 1, 5, box);
            placeBlock(c, s.BRICK_SLAB, 3, 2, 2, box);
            placeBlock(c, s.BRICK_SLAB, 3, 2, 4, box);
            for (int z = 2; z <= 4; z++) placeBlock(c, s.BRICK_SLAB, 2, 1, z, box);
            if (!hasPlacedChest && box.isInside(getWorldX(3, 3), getWorldY(2), getWorldZ(3, 3))) {
                hasPlacedChest = true;
                createChestLocal(c, box, 3, 2, 3, -1, "minecraft:chests/stronghold_corridor");
            }
        }
    }

    static class FillerCorridor extends Piece {
        @Override
        public String pieceType() { return "shfc"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putInt("Steps", steps);
        }

        private final int steps;

        FillerCorridor(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.steps = (dir == Direction.NORTH || dir == Direction.SOUTH) ? (box.maxZ() - box.minZ() + 1) : (box.maxX() - box.minX() + 1);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {}

        static BoundingBox findPieceBox(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir) {
            BoundingBox box = orientBox(x, y, z, -1, -1, 0, 5, 5, 4, dir);
            StructurePiece collision = b.findCollisionPiece(box);
            if (collision == null) return null;
            if (collision.boundingBox().minY() == box.minY()) {
                for (int i = 2; i >= 1; i--) {
                    box = orientBox(x, y, z, -1, -1, 0, 5, 5, i, dir);
                    if (!collision.boundingBox().intersects(box)) return orientBox(x, y, z, -1, -1, 0, 5, 5, i + 1, dir);
                }
            }
            return null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            St s = St.of(c.engine);
            for (int i = 0; i < steps; i++) {
                placeBlock(c, s.STONE_BRICKS, 0, 0, i, box);
                placeBlock(c, s.STONE_BRICKS, 1, 0, i, box);
                placeBlock(c, s.STONE_BRICKS, 2, 0, i, box);
                placeBlock(c, s.STONE_BRICKS, 3, 0, i, box);
                placeBlock(c, s.STONE_BRICKS, 4, 0, i, box);
                for (int y = 1; y <= 3; y++) {
                    placeBlock(c, s.STONE_BRICKS, 0, y, i, box);
                    placeBlock(c, s.CAVE_AIR, 1, y, i, box);
                    placeBlock(c, s.CAVE_AIR, 2, y, i, box);
                    placeBlock(c, s.CAVE_AIR, 3, y, i, box);
                    placeBlock(c, s.STONE_BRICKS, 4, y, i, box);
                }
                placeBlock(c, s.STONE_BRICKS, 0, 4, i, box);
                placeBlock(c, s.STONE_BRICKS, 1, 4, i, box);
                placeBlock(c, s.STONE_BRICKS, 2, 4, i, box);
                placeBlock(c, s.STONE_BRICKS, 3, 4, i, box);
                placeBlock(c, s.STONE_BRICKS, 4, 4, i, box);
            }
        }
    }

    static class FiveCrossing extends Piece {
        @Override
        public String pieceType() { return "sh5c"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putBoolean("leftLow", leftLow);
            t.putBoolean("leftHigh", leftHigh);
            t.putBoolean("rightLow", rightLow);
            t.putBoolean("rightHigh", rightHigh);
        }

        private final boolean leftLow, leftHigh, rightLow, rightHigh;

        FiveCrossing(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.entryDoor = randomSmallDoor(r);
            this.leftLow = r.nextBoolean();
            this.leftHigh = r.nextBoolean();
            this.rightLow = r.nextBoolean();
            this.rightHigh = r.nextInt(3) > 0;
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            int i = 3;
            int j = 5;
            if (orientation == Direction.WEST || orientation == Direction.NORTH) {
                i = 8 - i;
                j = 8 - j;
            }
            forwardChild(start, b, r, 5, 1);
            if (leftLow) leftChild(start, b, r, i, 1);
            if (leftHigh) leftChild(start, b, r, j, 7);
            if (rightLow) rightChild(start, b, r, i, 1);
            if (rightHigh) rightChild(start, b, r, j, 7);
        }

        static FiveCrossing createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -4, -3, 0, 10, 9, 11, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new FiveCrossing(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            St s = St.of(c.engine);
            int air = s.CAVE_AIR;
            StoneBricksSelector sel = new StoneBricksSelector(s);
            generateBox(c, box, 0, 0, 0, 9, 8, 10, true, r, sel);
            generateSmallDoor(c, box, entryDoor, 4, 3, 0);
            if (leftLow) generateBox(c, box, 0, 3, 1, 0, 5, 3, air, air, false);
            if (rightLow) generateBox(c, box, 9, 3, 1, 9, 5, 3, air, air, false);
            if (leftHigh) generateBox(c, box, 0, 5, 7, 0, 7, 9, air, air, false);
            if (rightHigh) generateBox(c, box, 9, 5, 7, 9, 7, 9, air, air, false);
            generateBox(c, box, 5, 1, 10, 7, 3, 10, air, air, false);
            generateBox(c, box, 1, 2, 1, 8, 2, 6, false, r, sel);
            generateBox(c, box, 4, 1, 5, 4, 4, 9, false, r, sel);
            generateBox(c, box, 8, 1, 5, 8, 4, 9, false, r, sel);
            generateBox(c, box, 1, 4, 7, 3, 4, 9, false, r, sel);
            generateBox(c, box, 1, 3, 5, 3, 3, 6, false, r, sel);
            generateBox(c, box, 1, 3, 4, 3, 3, 4, s.SLAB, s.SLAB, false);
            generateBox(c, box, 1, 4, 6, 3, 4, 6, s.SLAB, s.SLAB, false);
            generateBox(c, box, 5, 1, 7, 7, 1, 8, false, r, sel);
            generateBox(c, box, 5, 1, 9, 7, 1, 9, s.SLAB, s.SLAB, false);
            generateBox(c, box, 5, 2, 7, 7, 2, 7, s.SLAB, s.SLAB, false);
            generateBox(c, box, 4, 5, 7, 4, 5, 9, s.SLAB, s.SLAB, false);
            generateBox(c, box, 8, 5, 7, 8, 5, 9, s.SLAB, s.SLAB, false);
            generateBox(c, box, 5, 5, 7, 7, 5, 9, s.SLAB_DOUBLE, s.SLAB_DOUBLE, false);
            placeBlock(c, s.TORCH_S, 6, 5, 6, box);
        }
    }

    static class LeftTurn extends Piece {
        @Override
        public String pieceType() { return "shlt"; }

        LeftTurn(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.entryDoor = randomSmallDoor(r);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            if (orientation != Direction.NORTH && orientation != Direction.EAST) rightChild(start, b, r, 1, 1);
            else leftChild(start, b, r, 1, 1);
        }

        static LeftTurn createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -1, -1, 0, 5, 5, 5, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new LeftTurn(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            St s = St.of(c.engine);
            generateBox(c, box, 0, 0, 0, 4, 4, 4, true, c.random, new StoneBricksSelector(s));
            generateSmallDoor(c, box, entryDoor, 1, 1, 0);
            if (orientation != Direction.NORTH && orientation != Direction.EAST) generateBox(c, box, 4, 1, 1, 4, 3, 3, s.CAVE_AIR, s.CAVE_AIR, false);
            else generateBox(c, box, 0, 1, 1, 0, 3, 3, s.CAVE_AIR, s.CAVE_AIR, false);
        }
    }

    static class RightTurn extends Piece {
        @Override
        public String pieceType() { return "shrt"; }

        RightTurn(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.entryDoor = randomSmallDoor(r);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            if (orientation != Direction.NORTH && orientation != Direction.EAST) leftChild(start, b, r, 1, 1);
            else rightChild(start, b, r, 1, 1);
        }

        static RightTurn createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -1, -1, 0, 5, 5, 5, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new RightTurn(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            St s = St.of(c.engine);
            generateBox(c, box, 0, 0, 0, 4, 4, 4, true, c.random, new StoneBricksSelector(s));
            generateSmallDoor(c, box, entryDoor, 1, 1, 0);
            if (orientation != Direction.NORTH && orientation != Direction.EAST) generateBox(c, box, 0, 1, 1, 0, 3, 3, s.CAVE_AIR, s.CAVE_AIR, false);
            else generateBox(c, box, 4, 1, 1, 4, 3, 3, s.CAVE_AIR, s.CAVE_AIR, false);
        }
    }

    static class Library extends Piece {
        @Override
        public String pieceType() { return "shli"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putBoolean("Tall", isTall);
        }

        private final boolean isTall;

        Library(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.entryDoor = randomSmallDoor(r);
            this.isTall = ySpan(box) > 6;
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {}   // 도서관은 막다른 방 (바닐라에 addChildren 없음)

        static Library createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -4, -1, 0, 14, 11, 15, dir);
            if (!isOkBox(box) || b.findCollisionPiece(box) != null) {
                box = orientBox(x, y, z, -4, -1, 0, 14, 6, 15, dir);
                if (!isOkBox(box) || b.findCollisionPiece(box) != null) return null;
            }
            return new Library(depth, r, box, dir);
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            St s = St.of(c.engine);
            int height = isTall ? 11 : 6;
            generateBox(c, box, 0, 0, 0, 13, height - 1, 14, true, r, new StoneBricksSelector(s));
            generateSmallDoor(c, box, entryDoor, 4, 1, 0);
            generateMaybeBox(c, box, r, 0.07F, 2, 1, 1, 11, 4, 13, s.COBWEB, s.COBWEB, false, false);
            for (int i = 1; i <= 13; i++) {
                if ((i - 1) % 4 == 0) {
                    generateBox(c, box, 1, 1, i, 1, 4, i, s.PLANKS, s.PLANKS, false);
                    generateBox(c, box, 12, 1, i, 12, 4, i, s.PLANKS, s.PLANKS, false);
                    placeBlock(c, s.TORCH_E, 2, 3, i, box);
                    placeBlock(c, s.TORCH_W, 11, 3, i, box);
                    if (isTall) {
                        generateBox(c, box, 1, 6, i, 1, 9, i, s.PLANKS, s.PLANKS, false);
                        generateBox(c, box, 12, 6, i, 12, 9, i, s.PLANKS, s.PLANKS, false);
                    }
                } else {
                    generateBox(c, box, 1, 1, i, 1, 4, i, s.BOOKSHELF, s.BOOKSHELF, false);
                    generateBox(c, box, 12, 1, i, 12, 4, i, s.BOOKSHELF, s.BOOKSHELF, false);
                    if (isTall) {
                        generateBox(c, box, 1, 6, i, 1, 9, i, s.BOOKSHELF, s.BOOKSHELF, false);
                        generateBox(c, box, 12, 6, i, 12, 9, i, s.BOOKSHELF, s.BOOKSHELF, false);
                    }
                }
            }
            for (int i = 3; i < 12; i += 2) {
                generateBox(c, box, 3, 1, i, 4, 3, i, s.BOOKSHELF, s.BOOKSHELF, false);
                generateBox(c, box, 6, 1, i, 7, 3, i, s.BOOKSHELF, s.BOOKSHELF, false);
                generateBox(c, box, 9, 1, i, 10, 3, i, s.BOOKSHELF, s.BOOKSHELF, false);
            }
            if (isTall) {
                generateBox(c, box, 1, 5, 1, 3, 5, 13, s.PLANKS, s.PLANKS, false);
                generateBox(c, box, 10, 5, 1, 12, 5, 13, s.PLANKS, s.PLANKS, false);
                generateBox(c, box, 4, 5, 1, 9, 5, 2, s.PLANKS, s.PLANKS, false);
                generateBox(c, box, 4, 5, 12, 9, 5, 13, s.PLANKS, s.PLANKS, false);
                placeBlock(c, s.PLANKS, 9, 5, 11, box);
                placeBlock(c, s.PLANKS, 8, 5, 11, box);
                placeBlock(c, s.PLANKS, 9, 5, 10, box);
                int fenceEW = s.FENCE_EW;   // west+east
                int fenceNS = s.FENCE_NS;   // north+south
                generateBox(c, box, 3, 6, 3, 3, 6, 11, fenceNS, fenceNS, false);
                generateBox(c, box, 10, 6, 3, 10, 6, 9, fenceNS, fenceNS, false);
                generateBox(c, box, 4, 6, 2, 9, 6, 2, fenceEW, fenceEW, false);
                generateBox(c, box, 4, 6, 12, 7, 6, 12, fenceEW, fenceEW, false);
                placeBlock(c, s.FENCE_NE, 3, 6, 2, box);
                placeBlock(c, s.FENCE_SE, 3, 6, 12, box);
                placeBlock(c, s.FENCE_NW, 10, 6, 2, box);
                for (int i = 0; i <= 2; i++) {
                    placeBlock(c, s.FENCE_SW, 8 + i, 6, 12 - i, box);
                    if (i != 2) placeBlock(c, s.FENCE_NE, 8 + i, 6, 11 - i, box);
                }
                for (int y = 1; y <= 7; y++) placeBlock(c, s.LADDER_S, 10, y, 13, box);
                int fenceE = s.FENCE_E;
                placeBlock(c, fenceE, 6, 9, 7, box);
                int fenceW = s.FENCE_W;
                placeBlock(c, fenceW, 7, 9, 7, box);
                placeBlock(c, fenceE, 6, 8, 7, box);
                placeBlock(c, fenceW, 7, 8, 7, box);
                int all = s.FENCE_ALL;
                placeBlock(c, all, 6, 7, 7, box);
                placeBlock(c, all, 7, 7, 7, box);
                placeBlock(c, fenceE, 5, 7, 7, box);
                placeBlock(c, fenceW, 8, 7, 7, box);
                placeBlock(c, c.table().with(fenceE, "north", "true"), 6, 7, 6, box);
                placeBlock(c, c.table().with(fenceE, "south", "true"), 6, 7, 8, box);
                placeBlock(c, c.table().with(fenceW, "north", "true"), 7, 7, 6, box);
                placeBlock(c, c.table().with(fenceW, "south", "true"), 7, 7, 8, box);
                int torch = s.TORCH;
                placeBlock(c, torch, 5, 8, 7, box);
                placeBlock(c, torch, 8, 8, 7, box);
                placeBlock(c, torch, 6, 8, 6, box);
                placeBlock(c, torch, 6, 8, 8, box);
                placeBlock(c, torch, 7, 8, 6, box);
                placeBlock(c, torch, 7, 8, 8, box);
            }
            createChestLocal(c, box, 3, 3, 5, -1, "minecraft:chests/stronghold_library");
            if (isTall) {
                placeBlock(c, s.CAVE_AIR, 12, 9, 1, box);
                createChestLocal(c, box, 12, 8, 1, -1, "minecraft:chests/stronghold_library");
            }
        }
    }

    static class PortalRoom extends Piece {
        @Override
        public String pieceType() { return "shpr"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putBoolean("Mob", hasPlacedSpawner);
        }

        private boolean hasPlacedSpawner;

        PortalRoom(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            if (start != null) start.portalRoomPiece = this;
        }

        static PortalRoom createPiece(PiecesBuilder b, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -4, -1, 0, 11, 8, 16, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new PortalRoom(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            St s = St.of(c.engine);
            StoneBricksSelector sel = new StoneBricksSelector(s);
            generateBox(c, box, 0, 0, 0, 10, 7, 15, false, r, sel);
            generateSmallDoor(c, box, SmallDoorType.GRATES, 4, 1, 0);
            generateBox(c, box, 1, 6, 1, 1, 6, 14, false, r, sel);
            generateBox(c, box, 9, 6, 1, 9, 6, 14, false, r, sel);
            generateBox(c, box, 2, 6, 1, 8, 6, 2, false, r, sel);
            generateBox(c, box, 2, 6, 14, 8, 6, 14, false, r, sel);
            generateBox(c, box, 1, 1, 1, 2, 1, 4, false, r, sel);
            generateBox(c, box, 8, 1, 1, 9, 1, 4, false, r, sel);
            generateBox(c, box, 1, 1, 1, 1, 1, 3, s.LAVA, s.LAVA, false);
            generateBox(c, box, 9, 1, 1, 9, 1, 3, s.LAVA, s.LAVA, false);
            generateBox(c, box, 3, 1, 8, 7, 1, 12, false, r, sel);
            generateBox(c, box, 4, 1, 9, 6, 1, 11, s.LAVA, s.LAVA, false);
            int barsNS = s.BARS_NS;
            int barsEW = s.BARS_EW;
            for (int i = 3; i < 14; i += 2) {
                generateBox(c, box, 0, 3, i, 0, 4, i, barsNS, barsNS, false);
                generateBox(c, box, 10, 3, i, 10, 4, i, barsNS, barsNS, false);
            }
            for (int i = 2; i < 9; i += 2) {
                generateBox(c, box, i, 3, 15, i, 4, 15, barsEW, barsEW, false);
            }
            int stairs = s.BRICK_STAIRS_N;
            generateBox(c, box, 4, 1, 5, 6, 1, 7, false, r, sel);
            generateBox(c, box, 4, 2, 6, 6, 2, 7, false, r, sel);
            generateBox(c, box, 4, 3, 7, 6, 3, 7, false, r, sel);
            for (int x = 4; x <= 6; x++) {
                placeBlock(c, stairs, x, 1, 4, box);
                placeBlock(c, stairs, x, 2, 5, box);
                placeBlock(c, stairs, x, 3, 6, box);
            }
            boolean allEyes = true;
            boolean[] eyes = new boolean[12];
            for (int i = 0; i < eyes.length; i++) {
                eyes[i] = r.nextFloat() > 0.9F;
                allEyes &= eyes[i];
            }
            // FRAME[facing*2 + eye]: facing 0=north 1=south 2=east 3=west
            placeBlock(c, s.FRAME[eyes[0] ? 1 : 0], 4, 3, 8, box);
            placeBlock(c, s.FRAME[eyes[1] ? 1 : 0], 5, 3, 8, box);
            placeBlock(c, s.FRAME[eyes[2] ? 1 : 0], 6, 3, 8, box);
            placeBlock(c, s.FRAME[2 + (eyes[3] ? 1 : 0)], 4, 3, 12, box);
            placeBlock(c, s.FRAME[2 + (eyes[4] ? 1 : 0)], 5, 3, 12, box);
            placeBlock(c, s.FRAME[2 + (eyes[5] ? 1 : 0)], 6, 3, 12, box);
            placeBlock(c, s.FRAME[4 + (eyes[6] ? 1 : 0)], 3, 3, 9, box);
            placeBlock(c, s.FRAME[4 + (eyes[7] ? 1 : 0)], 3, 3, 10, box);
            placeBlock(c, s.FRAME[4 + (eyes[8] ? 1 : 0)], 3, 3, 11, box);
            placeBlock(c, s.FRAME[6 + (eyes[9] ? 1 : 0)], 7, 3, 9, box);
            placeBlock(c, s.FRAME[6 + (eyes[10] ? 1 : 0)], 7, 3, 10, box);
            placeBlock(c, s.FRAME[6 + (eyes[11] ? 1 : 0)], 7, 3, 11, box);
            if (allEyes) {
                int portal = s.END_PORTAL;
                placeBlock(c, portal, 4, 3, 9, box);
                placeBlock(c, portal, 5, 3, 9, box);
                placeBlock(c, portal, 6, 3, 9, box);
                placeBlock(c, portal, 4, 3, 10, box);
                placeBlock(c, portal, 5, 3, 10, box);
                placeBlock(c, portal, 6, 3, 10, box);
                placeBlock(c, portal, 4, 3, 11, box);
                placeBlock(c, portal, 5, 3, 11, box);
                placeBlock(c, portal, 6, 3, 11, box);
            }
            if (!hasPlacedSpawner) {
                int wx = getWorldX(5, 6), wy = getWorldY(3), wz = getWorldZ(5, 6);
                if (box.isInside(wx, wy, wz)) {
                    hasPlacedSpawner = true;
                    c.view.setBlock(wx, wy, wz, s.SPAWNER);
                    c.view.putGeneratedBlockEntity(wx, wy, wz, spawnerNbt(wx, wy, wz));
                }
            }
        }

        /** 새 스포너 블록 엔티티에 setEntityId(silverfish) 한 뒤의 저장 형태(BaseSpawner.save + 위치). */
        private static NbtCompound spawnerNbt(int x, int y, int z) {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("id", "minecraft:mob_spawner");
            nbt.putInt("x", x);
            nbt.putInt("y", y);
            nbt.putInt("z", z);
            nbt.put("Delay", (short) 20);
            nbt.put("MinSpawnDelay", (short) 200);
            nbt.put("MaxSpawnDelay", (short) 800);
            nbt.put("SpawnCount", (short) 4);
            nbt.put("MaxNearbyEntities", (short) 6);
            nbt.put("RequiredPlayerRange", (short) 16);
            nbt.put("SpawnRange", (short) 4);
            NbtCompound entity = new NbtCompound();
            entity.putString("id", "minecraft:silverfish");
            NbtCompound spawnData = new NbtCompound();
            spawnData.putCompound("entity", entity);
            nbt.putCompound("SpawnData", spawnData);
            nbt.putList("SpawnPotentials", new NbtList((byte) 10));
            return nbt;
        }
    }

    static class PrisonHall extends Piece {
        @Override
        public String pieceType() { return "shph"; }

        PrisonHall(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.entryDoor = randomSmallDoor(r);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forwardChild(start, b, r, 1, 1);
        }

        static PrisonHall createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -1, -1, 0, 9, 5, 11, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new PrisonHall(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            St s = St.of(c.engine);
            int air = s.CAVE_AIR;
            StoneBricksSelector sel = new StoneBricksSelector(s);
            generateBox(c, box, 0, 0, 0, 8, 4, 10, true, r, sel);
            generateSmallDoor(c, box, entryDoor, 1, 1, 0);
            generateBox(c, box, 1, 1, 10, 3, 3, 10, air, air, false);
            generateBox(c, box, 4, 1, 1, 4, 3, 1, false, r, sel);
            generateBox(c, box, 4, 1, 3, 4, 3, 3, false, r, sel);
            generateBox(c, box, 4, 1, 7, 4, 3, 7, false, r, sel);
            generateBox(c, box, 4, 1, 9, 4, 3, 9, false, r, sel);
            for (int y = 1; y <= 3; y++) {
                placeBlock(c, s.BARS_NS, 4, y, 4, box);
                placeBlock(c, s.BARS_NSE, 4, y, 5, box);
                placeBlock(c, s.BARS_NS, 4, y, 6, box);
                placeBlock(c, s.BARS_EW, 5, y, 5, box);
                placeBlock(c, s.BARS_EW, 6, y, 5, box);
                placeBlock(c, s.BARS_EW, 7, y, 5, box);
            }
            placeBlock(c, s.BARS_NS, 4, 3, 2, box);
            placeBlock(c, s.BARS_NS, 4, 3, 8, box);
            placeBlock(c, s.IRON_DOOR_W_LOWER, 4, 1, 2, box);
            placeBlock(c, s.IRON_DOOR_W_UPPER, 4, 2, 2, box);
            placeBlock(c, s.IRON_DOOR_W_LOWER, 4, 1, 8, box);
            placeBlock(c, s.IRON_DOOR_W_UPPER, 4, 2, 8, box);
        }
    }

    static class RoomCrossing extends Piece {
        @Override
        public String pieceType() { return "shrc"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putInt("Type", type);
        }

        private final int type;

        RoomCrossing(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.entryDoor = randomSmallDoor(r);
            this.type = r.nextInt(5);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forwardChild(start, b, r, 4, 1);
            leftChild(start, b, r, 1, 4);
            rightChild(start, b, r, 1, 4);
        }

        static RoomCrossing createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -4, -1, 0, 11, 7, 11, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new RoomCrossing(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            St s = St.of(c.engine);
            int air = s.CAVE_AIR;
            generateBox(c, box, 0, 0, 0, 10, 6, 10, true, r, new StoneBricksSelector(s));
            generateSmallDoor(c, box, entryDoor, 4, 1, 0);
            generateBox(c, box, 4, 1, 10, 6, 3, 10, air, air, false);
            generateBox(c, box, 0, 1, 4, 0, 3, 6, air, air, false);
            generateBox(c, box, 10, 1, 4, 10, 3, 6, air, air, false);
            switch (type) {
                case 0 -> {
                    placeBlock(c, s.STONE_BRICKS, 5, 1, 5, box);
                    placeBlock(c, s.STONE_BRICKS, 5, 2, 5, box);
                    placeBlock(c, s.STONE_BRICKS, 5, 3, 5, box);
                    placeBlock(c, s.TORCH_W, 4, 3, 5, box);
                    placeBlock(c, s.TORCH_E, 6, 3, 5, box);
                    placeBlock(c, s.TORCH_S, 5, 3, 4, box);
                    placeBlock(c, s.TORCH_N, 5, 3, 6, box);
                    placeBlock(c, s.SLAB, 4, 1, 4, box);
                    placeBlock(c, s.SLAB, 4, 1, 5, box);
                    placeBlock(c, s.SLAB, 4, 1, 6, box);
                    placeBlock(c, s.SLAB, 6, 1, 4, box);
                    placeBlock(c, s.SLAB, 6, 1, 5, box);
                    placeBlock(c, s.SLAB, 6, 1, 6, box);
                    placeBlock(c, s.SLAB, 5, 1, 4, box);
                    placeBlock(c, s.SLAB, 5, 1, 6, box);
                }
                case 1 -> {
                    for (int i = 0; i < 5; i++) {
                        placeBlock(c, s.STONE_BRICKS, 3, 1, 3 + i, box);
                        placeBlock(c, s.STONE_BRICKS, 7, 1, 3 + i, box);
                        placeBlock(c, s.STONE_BRICKS, 3 + i, 1, 3, box);
                        placeBlock(c, s.STONE_BRICKS, 3 + i, 1, 7, box);
                    }
                    placeBlock(c, s.STONE_BRICKS, 5, 1, 5, box);
                    placeBlock(c, s.STONE_BRICKS, 5, 2, 5, box);
                    placeBlock(c, s.STONE_BRICKS, 5, 3, 5, box);
                    placeBlock(c, s.WATER, 5, 4, 5, box);
                }
                case 2 -> {
                    for (int i = 1; i <= 9; i++) {
                        placeBlock(c, s.COBBLE, 1, 3, i, box);
                        placeBlock(c, s.COBBLE, 9, 3, i, box);
                    }
                    for (int i = 1; i <= 9; i++) {
                        placeBlock(c, s.COBBLE, i, 3, 1, box);
                        placeBlock(c, s.COBBLE, i, 3, 9, box);
                    }
                    placeBlock(c, s.COBBLE, 5, 1, 4, box);
                    placeBlock(c, s.COBBLE, 5, 1, 6, box);
                    placeBlock(c, s.COBBLE, 5, 3, 4, box);
                    placeBlock(c, s.COBBLE, 5, 3, 6, box);
                    placeBlock(c, s.COBBLE, 4, 1, 5, box);
                    placeBlock(c, s.COBBLE, 6, 1, 5, box);
                    placeBlock(c, s.COBBLE, 4, 3, 5, box);
                    placeBlock(c, s.COBBLE, 6, 3, 5, box);
                    for (int i = 1; i <= 3; i++) {
                        placeBlock(c, s.COBBLE, 4, i, 4, box);
                        placeBlock(c, s.COBBLE, 6, i, 4, box);
                        placeBlock(c, s.COBBLE, 4, i, 6, box);
                        placeBlock(c, s.COBBLE, 6, i, 6, box);
                    }
                    placeBlock(c, s.TORCH_N, 5, 3, 5, box);   // wall_torch 기본 상태(facing=north)
                    for (int i = 2; i <= 8; i++) {
                        placeBlock(c, s.PLANKS, 2, 3, i, box);
                        placeBlock(c, s.PLANKS, 3, 3, i, box);
                        if (i <= 3 || i >= 7) {
                            placeBlock(c, s.PLANKS, 4, 3, i, box);
                            placeBlock(c, s.PLANKS, 5, 3, i, box);
                            placeBlock(c, s.PLANKS, 6, 3, i, box);
                        }
                        placeBlock(c, s.PLANKS, 7, 3, i, box);
                        placeBlock(c, s.PLANKS, 8, 3, i, box);
                    }
                    placeBlock(c, s.LADDER_W, 9, 1, 3, box);
                    placeBlock(c, s.LADDER_W, 9, 2, 3, box);
                    placeBlock(c, s.LADDER_W, 9, 3, 3, box);
                    createChestLocal(c, box, 3, 4, 8, -1, "minecraft:chests/stronghold_crossing");
                }
                default -> {}
            }
        }
    }

    static class StairsDown extends Piece {
        @Override
        public String pieceType() { return "shsd"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putBoolean("Source", isSource);
        }

        private final boolean isSource;

        /** 일반 계단 조각. */
        StairsDown(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.isSource = false;
            this.entryDoor = randomSmallDoor(r);
        }

        /** 시작 조각용(isSource = true). */
        StairsDown(int genDepth, int x, int z, Direction dir) {
            super(genDepth, makeBoundingBox(x, 64, z, dir, 5, 11, 5), dir);
            this.isSource = true;
            this.entryDoor = SmallDoorType.OPENING;
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            if (isSource) start.imposedPiece = Kind.FIVE_CROSSING;
            forwardChild(start, b, r, 1, 1);
        }

        static StairsDown createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -1, -7, 0, 5, 11, 5, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new StairsDown(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            St s = St.of(c.engine);
            int sb = s.STONE_BRICKS;
            int slab = s.SLAB;
            generateBox(c, box, 0, 0, 0, 4, 10, 4, true, c.random, new StoneBricksSelector(s));
            generateSmallDoor(c, box, entryDoor, 1, 7, 0);
            generateSmallDoor(c, box, SmallDoorType.OPENING, 1, 1, 4);
            placeBlock(c, sb, 2, 6, 1, box);
            placeBlock(c, sb, 1, 5, 1, box);
            placeBlock(c, slab, 1, 6, 1, box);
            placeBlock(c, sb, 1, 5, 2, box);
            placeBlock(c, sb, 1, 4, 3, box);
            placeBlock(c, slab, 1, 5, 3, box);
            placeBlock(c, sb, 2, 4, 3, box);
            placeBlock(c, sb, 3, 3, 3, box);
            placeBlock(c, slab, 3, 4, 3, box);
            placeBlock(c, sb, 3, 3, 2, box);
            placeBlock(c, sb, 3, 2, 1, box);
            placeBlock(c, slab, 3, 3, 1, box);
            placeBlock(c, sb, 2, 2, 1, box);
            placeBlock(c, sb, 1, 1, 1, box);
            placeBlock(c, slab, 1, 2, 1, box);
            placeBlock(c, sb, 1, 1, 2, box);
            placeBlock(c, slab, 1, 1, 3, box);
        }
    }

    static class Straight extends Piece {
        @Override
        public String pieceType() { return "shs"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putBoolean("Left", leftChild);
            t.putBoolean("Right", rightChild);
        }

        private final boolean leftChild, rightChild;

        Straight(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.entryDoor = randomSmallDoor(r);
            this.leftChild = r.nextInt(2) == 0;
            this.rightChild = r.nextInt(2) == 0;
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forwardChild(start, b, r, 1, 1);
            if (leftChild) leftChild(start, b, r, 1, 2);
            if (rightChild) rightChild(start, b, r, 1, 2);
        }

        static Straight createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -1, -1, 0, 5, 5, 7, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new Straight(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            St s = St.of(c.engine);
            generateBox(c, box, 0, 0, 0, 4, 4, 6, true, r, new StoneBricksSelector(s));
            generateSmallDoor(c, box, entryDoor, 1, 1, 0);
            generateSmallDoor(c, box, SmallDoorType.OPENING, 1, 1, 6);
            maybeGenerateBlock(c, box, r, 0.1F, 1, 2, 1, s.TORCH_E);
            maybeGenerateBlock(c, box, r, 0.1F, 3, 2, 1, s.TORCH_W);
            maybeGenerateBlock(c, box, r, 0.1F, 1, 2, 5, s.TORCH_E);
            maybeGenerateBlock(c, box, r, 0.1F, 3, 2, 5, s.TORCH_W);
            if (leftChild) generateBox(c, box, 0, 1, 2, 0, 3, 4, s.CAVE_AIR, s.CAVE_AIR, false);
            if (rightChild) generateBox(c, box, 4, 1, 2, 4, 3, 4, s.CAVE_AIR, s.CAVE_AIR, false);
        }
    }

    static class StraightStairsDown extends Piece {
        @Override
        public String pieceType() { return "shssd"; }

        StraightStairsDown(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.entryDoor = randomSmallDoor(r);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forwardChild(start, b, r, 1, 1);
        }

        static StraightStairsDown createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orientBox(x, y, z, -1, -7, 0, 5, 11, 8, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new StraightStairsDown(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            St s = St.of(c.engine);
            generateBox(c, box, 0, 0, 0, 4, 10, 7, true, c.random, new StoneBricksSelector(s));
            generateSmallDoor(c, box, entryDoor, 1, 7, 0);
            generateSmallDoor(c, box, SmallDoorType.OPENING, 1, 1, 7);
            int stairs = s.COBBLE_STAIRS_S;
            for (int i = 0; i < 6; i++) {
                placeBlock(c, stairs, 1, 6 - i, 1 + i, box);
                placeBlock(c, stairs, 2, 6 - i, 1 + i, box);
                placeBlock(c, stairs, 3, 6 - i, 1 + i, box);
                if (i < 5) {
                    placeBlock(c, s.STONE_BRICKS, 1, 5 - i, 1 + i, box);
                    placeBlock(c, s.STONE_BRICKS, 2, 5 - i, 1 + i, box);
                    placeBlock(c, s.STONE_BRICKS, 3, 5 - i, 1 + i, box);
                }
            }
        }
    }
}
