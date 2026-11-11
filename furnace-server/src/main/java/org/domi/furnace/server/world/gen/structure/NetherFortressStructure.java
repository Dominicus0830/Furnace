// 네더 요새 — 바닐라 NetherFortressStructure + NetherFortressPieces(다리/성채 조각 가중치 표, 방·복도·계단·몬스터 왕좌·줄기방).
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;

public final class NetherFortressStructure extends StructureType {

    public NetherFortressStructure(StructureRegistry.Def def) {}

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        // 바닐라: new GenerationStub(new BlockPos(chunkPos.getMinBlockX(), 64, chunkPos.getMinBlockZ()), builder -> generatePieces(builder, ctx))
        return Optional.of(new Stub(c.minBlockX(), 64, c.minBlockZ(), b -> generatePieces(b, c)));
    }

    private static void generatePieces(PiecesBuilder b, GenContext c) {
        // 시작 조각은 청크 최소 코너 + 2 (ctx.chunkPos().getBlockX(2) / getBlockZ(2))
        StartPiece start = new StartPiece(c.random, c.chunkX * 16 + 2, c.chunkZ * 16 + 2);
        b.addPiece(start);
        start.addChildren(start, b, c.random);
        List<Piece> pending = start.pendingChildren;
        while (!pending.isEmpty()) {
            int i = c.random.nextInt(pending.size());
            pending.remove(i).addChildren(start, b, c.random);
        }
        b.moveInsideHeights(c.random, 48, 70);
    }

    // ------------------------------------------------------------------ 상태 모음

    static final class St {
        final StructureEngine engine;
        final int NB, AIR, LAVA, SOUL_SAND, WART, SPAWNER;
        final int STAIRS_N, STAIRS_S, STAIRS_E, STAIRS_W;
        final int FENCE_PLAIN;

        St(StructureEngine e) {
            engine = e;
            NB = e.state("minecraft:nether_bricks");
            AIR = e.state("minecraft:air");
            LAVA = e.state("minecraft:lava");
            SOUL_SAND = e.state("minecraft:soul_sand");
            WART = e.state("minecraft:nether_wart");
            SPAWNER = e.state("minecraft:spawner");
            STAIRS_N = e.state("minecraft:nether_brick_stairs[facing=north]");
            STAIRS_S = e.state("minecraft:nether_brick_stairs[facing=south]");
            STAIRS_E = e.state("minecraft:nether_brick_stairs[facing=east]");
            STAIRS_W = e.state("minecraft:nether_brick_stairs[facing=west]");
            FENCE_PLAIN = e.state("minecraft:nether_brick_fence");
        }

        /** nether_brick_fence 에 지정한 방향 연결을 true 로 둔 상태(바닐라가 코드에서 명시적으로 쓰는 상태). */
        int fence(boolean north, boolean east, boolean south, boolean west) {
            StringBuilder sb = new StringBuilder();
            if (north) sb.append("north=true,");
            if (east) sb.append("east=true,");
            if (south) sb.append("south=true,");
            if (west) sb.append("west=true,");
            if (sb.length() == 0) return FENCE_PLAIN;
            sb.setLength(sb.length() - 1);
            return engine.state("minecraft:nether_brick_fence[" + sb + "]");
        }

        int fenceNS() { return fence(true, false, true, false); }

        int fenceWE() { return fence(false, true, false, true); }

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

    private enum Kind {
        BRIDGE_STRAIGHT, BRIDGE_CROSSING, ROOM_CROSSING, STAIRS_ROOM, MONSTER_THRONE, CASTLE_ENTRANCE,
        CASTLE_SMALL_CORRIDOR, CASTLE_SMALL_CORRIDOR_CROSSING, CASTLE_SMALL_CORRIDOR_RIGHT_TURN, CASTLE_SMALL_CORRIDOR_LEFT_TURN,
        CASTLE_CORRIDOR_STAIRS, CASTLE_CORRIDOR_T_BALCONY, CASTLE_STALK_ROOM
    }

    /** 바닐라 NetherFortressPieces.PieceWeight. */
    static final class PieceWeight {
        final Kind kind;
        final int weight;
        int placeCount;
        final int maxPlaceCount;
        final boolean allowInRow;

        PieceWeight(Kind kind, int weight, int maxPlaceCount, boolean allowInRow) {
            this.kind = kind;
            this.weight = weight;
            this.maxPlaceCount = maxPlaceCount;
            this.allowInRow = allowInRow;
        }

        PieceWeight(Kind kind, int weight, int maxPlaceCount) {
            this(kind, weight, maxPlaceCount, false);
        }

        boolean doPlace(int genDepth) {
            return maxPlaceCount == 0 || placeCount < maxPlaceCount;
        }

        boolean isValid() {
            return maxPlaceCount == 0 || placeCount < maxPlaceCount;
        }
    }

    /** 바닐라 BRIDGE_PIECE_WEIGHTS — StartPiece 생성자에서 placeCount 0 으로 초기화되어 복사된다. */
    private static List<PieceWeight> newBridgeSet() {
        List<PieceWeight> list = new ArrayList<>();
        list.add(new PieceWeight(Kind.BRIDGE_STRAIGHT, 30, 0, true));
        list.add(new PieceWeight(Kind.BRIDGE_CROSSING, 10, 4));
        list.add(new PieceWeight(Kind.ROOM_CROSSING, 10, 4));
        list.add(new PieceWeight(Kind.STAIRS_ROOM, 10, 3));
        list.add(new PieceWeight(Kind.MONSTER_THRONE, 5, 2));
        list.add(new PieceWeight(Kind.CASTLE_ENTRANCE, 5, 1));
        return list;
    }

    /** 바닐라 CASTLE_PIECE_WEIGHTS. */
    private static List<PieceWeight> newCastleSet() {
        List<PieceWeight> list = new ArrayList<>();
        list.add(new PieceWeight(Kind.CASTLE_SMALL_CORRIDOR, 25, 0, true));
        list.add(new PieceWeight(Kind.CASTLE_SMALL_CORRIDOR_CROSSING, 15, 5));
        list.add(new PieceWeight(Kind.CASTLE_SMALL_CORRIDOR_RIGHT_TURN, 5, 10));
        list.add(new PieceWeight(Kind.CASTLE_SMALL_CORRIDOR_LEFT_TURN, 5, 10));
        list.add(new PieceWeight(Kind.CASTLE_CORRIDOR_STAIRS, 10, 3, true));
        list.add(new PieceWeight(Kind.CASTLE_CORRIDOR_T_BALCONY, 7, 2));
        list.add(new PieceWeight(Kind.CASTLE_STALK_ROOM, 5, 2));
        return list;
    }

    /** 바닐라 NetherFortressPieces.findAndCreateBridgePieceFactory. */
    private static Piece findAndCreatePieceFactory(PieceWeight w, PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
        return switch (w.kind) {
            case BRIDGE_STRAIGHT -> BridgeStraight.createPiece(b, r, x, y, z, dir, depth);
            case BRIDGE_CROSSING -> BridgeCrossing.createPiece(b, x, y, z, dir, depth);
            case ROOM_CROSSING -> RoomCrossing.createPiece(b, x, y, z, dir, depth);
            case STAIRS_ROOM -> StairsRoom.createPiece(b, x, y, z, depth, dir);
            case MONSTER_THRONE -> MonsterThrone.createPiece(b, x, y, z, depth, dir);
            case CASTLE_ENTRANCE -> CastleEntrance.createPiece(b, r, x, y, z, dir, depth);
            case CASTLE_SMALL_CORRIDOR -> CastleSmallCorridor.createPiece(b, x, y, z, dir, depth);
            case CASTLE_SMALL_CORRIDOR_RIGHT_TURN -> CastleSmallCorridorRightTurn.createPiece(b, r, x, y, z, dir, depth);
            case CASTLE_SMALL_CORRIDOR_LEFT_TURN -> CastleSmallCorridorLeftTurn.createPiece(b, r, x, y, z, dir, depth);
            case CASTLE_CORRIDOR_STAIRS -> CastleCorridorStairs.createPiece(b, x, y, z, dir, depth);
            case CASTLE_CORRIDOR_T_BALCONY -> CastleCorridorTBalcony.createPiece(b, x, y, z, dir, depth);
            case CASTLE_SMALL_CORRIDOR_CROSSING -> CastleSmallCorridorCrossing.createPiece(b, x, y, z, dir, depth);
            case CASTLE_STALK_ROOM -> CastleStalkRoom.createPiece(b, x, y, z, dir, depth);
        };
    }

    /** 바닐라 NetherBridgePiece.updatePieceWeight — 더 놓을 수 있는 조각이 있으면 총 가중치, 없으면 -1. */
    private static int updatePieceWeight(List<PieceWeight> list) {
        boolean hasMore = false;
        int total = 0;
        for (PieceWeight w : list) {
            if (w.maxPlaceCount > 0 && w.placeCount < w.maxPlaceCount) hasMore = true;
            total += w.weight;
        }
        return hasMore ? total : -1;
    }

    /** 바닐라 NetherBridgePiece.generatePiece(startPiece, weights, accessor, random, x, y, z, direction, depth). */
    private static Piece generatePiece(StartPiece start, List<PieceWeight> list, PiecesBuilder b, LegacyRandom r,
                                       int x, int y, int z, Direction dir, int depth) {
        int total = updatePieceWeight(list);
        boolean ok = total > 0 && depth <= 30;
        int tries = 0;
        while (tries < 5 && ok) {
            tries++;
            int roll = r.nextInt(total);
            for (PieceWeight w : list) {
                roll -= w.weight;
                if (roll < 0) {
                    if (!w.doPlace(depth) || (w == start.previousPiece && !w.allowInRow)) break;
                    Piece p = findAndCreatePieceFactory(w, b, r, x, y, z, dir, depth);
                    if (p != null) {
                        w.placeCount++;
                        start.previousPiece = w;
                        if (!w.isValid()) list.remove(w);
                        return p;
                    }
                }
            }
        }
        return BridgeEndFiller.createPiece(b, r, x, y, z, dir, depth);
    }

    /** 바닐라 NetherBridgePiece.generateAndAddPiece. */
    private static StructurePiece generateAndAddPiece(StartPiece start, PiecesBuilder b, LegacyRandom r,
                                                      int x, int y, int z, Direction dir, int genDepth, boolean isCastle) {
        if (Math.abs(x - start.boundingBox().minX()) <= 112 && Math.abs(z - start.boundingBox().minZ()) <= 112) {
            List<PieceWeight> list = isCastle ? start.castlePieces : start.bridgePieces;
            Piece p = generatePiece(start, list, b, r, x, y, z, dir, genDepth + 1);
            if (p != null) {
                b.addPiece(p);
                start.pendingChildren.add(p);
            }
            return p;
        }
        // 범위 밖: 끝 마개를 만들되(난수 1회 소비) 목록에는 넣지 않는다.
        return BridgeEndFiller.createPiece(b, r, x, y, z, dir, genDepth);
    }

    private static BoundingBox orient(int x, int y, int z, int ox, int oy, int oz, int w, int h, int d, Direction dir) {
        return StrongholdStructure.orientBox(x, y, z, ox, oy, oz, w, h, d, dir);
    }

    // ------------------------------------------------------------------ 공통 기반 (바닐라 NetherBridgePiece)

    abstract static class Piece extends StructurePiece {
        final int genDepth;

        protected Piece(int genDepth, BoundingBox box, Direction dir) {
            super(box, dir);
            this.genDepth = genDepth;
        }

        /** 바닐라 addChildren(startPiece, accessor, random). 기본은 아무 것도 하지 않는다. */
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {}

        protected static boolean isOkBox(BoundingBox box) {
            return box != null && box.minY() > 10;
        }

        /** 바닐라 generateChildForward. */
        protected StructurePiece forward(StartPiece start, PiecesBuilder b, LegacyRandom r, int xOff, int yOff, boolean castle) {
            BoundingBox bb = boundingBox;
            return switch (orientation) {
                case NORTH -> generateAndAddPiece(start, b, r, bb.minX() + xOff, bb.minY() + yOff, bb.minZ() - 1, orientation, genDepth, castle);
                case SOUTH -> generateAndAddPiece(start, b, r, bb.minX() + xOff, bb.minY() + yOff, bb.maxZ() + 1, orientation, genDepth, castle);
                case WEST -> generateAndAddPiece(start, b, r, bb.minX() - 1, bb.minY() + yOff, bb.minZ() + xOff, orientation, genDepth, castle);
                case EAST -> generateAndAddPiece(start, b, r, bb.maxX() + 1, bb.minY() + yOff, bb.minZ() + xOff, orientation, genDepth, castle);
            };
        }

        /** 바닐라 generateChildLeft. */
        protected StructurePiece left(StartPiece start, PiecesBuilder b, LegacyRandom r, int yOff, int zOff, boolean castle) {
            BoundingBox bb = boundingBox;
            return switch (orientation) {
                case NORTH, SOUTH -> generateAndAddPiece(start, b, r, bb.minX() - 1, bb.minY() + yOff, bb.minZ() + zOff, Direction.WEST, genDepth, castle);
                case WEST, EAST -> generateAndAddPiece(start, b, r, bb.minX() + zOff, bb.minY() + yOff, bb.minZ() - 1, Direction.NORTH, genDepth, castle);
            };
        }

        /** 바닐라 generateChildRight. */
        protected StructurePiece right(StartPiece start, PiecesBuilder b, LegacyRandom r, int yOff, int zOff, boolean castle) {
            BoundingBox bb = boundingBox;
            return switch (orientation) {
                case NORTH, SOUTH -> generateAndAddPiece(start, b, r, bb.maxX() + 1, bb.minY() + yOff, bb.minZ() + zOff, Direction.EAST, genDepth, castle);
                case WEST, EAST -> generateAndAddPiece(start, b, r, bb.minX() + zOff, bb.minY() + yOff, bb.maxZ() + 1, Direction.SOUTH, genDepth, castle);
            };
        }

        /** generateBox(edge=fill=state, skipAir=false) 단축. */
        protected void box(PostCtx c, BoundingBox bb, int x0, int y0, int z0, int x1, int y1, int z1, int state) {
            generateBox(c, bb, x0, y0, z0, x1, y1, z1, state, state, false);
        }
    }

    // ------------------------------------------------------------------ 조각들

    /** NeBCr — 다리 교차로(19x10x19). 시작 조각도 이것을 상속한다. */
    static class BridgeCrossing extends Piece {
        BridgeCrossing(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        /** 시작 조각용 생성자: 높이 64, 19x10x19. */
        protected BridgeCrossing(int x, int z, Direction dir) {
            super(0, makeBoundingBox(x, 64, z, dir, 19, 10, 19), dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forward(start, b, r, 8, 3, false);
            left(start, b, r, 3, 8, false);
            right(start, b, r, 3, 8, false);
        }

        static BridgeCrossing createPiece(PiecesBuilder b, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -8, -3, 0, 19, 10, 19, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new BridgeCrossing(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 7, 3, 0, 11, 4, 18, nb);
            box(c, bb, 0, 3, 7, 18, 4, 11, nb);
            box(c, bb, 8, 5, 0, 10, 7, 18, air);
            box(c, bb, 0, 5, 8, 18, 7, 10, air);
            box(c, bb, 7, 5, 0, 7, 5, 7, nb);
            box(c, bb, 7, 5, 11, 7, 5, 18, nb);
            box(c, bb, 11, 5, 0, 11, 5, 7, nb);
            box(c, bb, 11, 5, 11, 11, 5, 18, nb);
            box(c, bb, 0, 5, 7, 7, 5, 7, nb);
            box(c, bb, 11, 5, 7, 18, 5, 7, nb);
            box(c, bb, 0, 5, 11, 7, 5, 11, nb);
            box(c, bb, 11, 5, 11, 18, 5, 11, nb);
            box(c, bb, 7, 2, 0, 11, 2, 5, nb);
            box(c, bb, 7, 2, 13, 11, 2, 18, nb);
            box(c, bb, 7, 0, 0, 11, 1, 3, nb);
            box(c, bb, 7, 0, 15, 11, 1, 18, nb);

            for (int i = 7; i <= 11; i++) {
                for (int j = 0; j <= 2; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                    fillColumnDown(c, nb, i, -1, 18 - j, bb);
                }
            }

            box(c, bb, 0, 2, 7, 5, 2, 11, nb);
            box(c, bb, 13, 2, 7, 18, 2, 11, nb);
            box(c, bb, 0, 0, 7, 3, 1, 11, nb);
            box(c, bb, 15, 0, 7, 18, 1, 11, nb);

            for (int i = 0; i <= 2; i++) {
                for (int j = 7; j <= 11; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                    fillColumnDown(c, nb, 18 - i, -1, j, bb);
                }
            }
        }
    }

    /** NeBEF — 다리 끝 마개(5x10x8). 자체 시드로 들쭉날쭉한 단면을 만든다. */
    static class BridgeEndFiller extends Piece {
        private final int selfSeed;

        BridgeEndFiller(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.selfSeed = r.nextInt();
        }

        static BridgeEndFiller createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -1, -3, 0, 5, 10, 8, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new BridgeEndFiller(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB;
            LegacyRandom rr = new LegacyRandom((long) selfSeed);   // RandomSource.create(seed) = LegacyRandomSource

            for (int i = 0; i <= 4; i++) {
                for (int j = 3; j <= 4; j++) {
                    int k = rr.nextInt(8);
                    box(c, bb, i, j, 0, i, j, k, nb);
                }
            }

            int k = rr.nextInt(8);
            box(c, bb, 0, 5, 0, 0, 5, k, nb);
            k = rr.nextInt(8);
            box(c, bb, 4, 5, 0, 4, 5, k, nb);

            for (int i = 0; i <= 4; i++) {
                int m = rr.nextInt(5);
                box(c, bb, i, 2, 0, i, 2, m, nb);
            }

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 1; j++) {
                    int m = rr.nextInt(3);
                    box(c, bb, i, j, 0, i, j, m, nb);
                }
            }
        }
    }

    /** NeBS — 직선 다리(5x10x19). */
    static class BridgeStraight extends Piece {
        BridgeStraight(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forward(start, b, r, 1, 3, false);
        }

        static BridgeStraight createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -1, -3, 0, 5, 10, 19, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new BridgeStraight(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 0, 3, 0, 4, 4, 18, nb);
            box(c, bb, 1, 5, 0, 3, 7, 18, air);
            box(c, bb, 0, 5, 0, 0, 5, 18, nb);
            box(c, bb, 4, 5, 0, 4, 5, 18, nb);
            box(c, bb, 0, 2, 0, 4, 2, 5, nb);
            box(c, bb, 0, 2, 13, 4, 2, 18, nb);
            box(c, bb, 0, 0, 0, 4, 1, 3, nb);
            box(c, bb, 0, 0, 15, 4, 1, 18, nb);

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 2; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                    fillColumnDown(c, nb, i, -1, 18 - j, bb);
                }
            }

            // 바닐라: $$9 = fence[north,south], $$10 = $$9[east], $$11 = $$9[west]
            int nsE = s.fence(true, true, true, false);
            int nsW = s.fence(true, false, true, true);
            box(c, bb, 0, 1, 1, 0, 4, 1, nsE);
            box(c, bb, 0, 3, 4, 0, 4, 4, nsE);
            box(c, bb, 0, 3, 14, 0, 4, 14, nsE);
            box(c, bb, 0, 1, 17, 0, 4, 17, nsE);
            box(c, bb, 4, 1, 1, 4, 4, 1, nsW);
            box(c, bb, 4, 3, 4, 4, 4, 4, nsW);
            box(c, bb, 4, 3, 14, 4, 4, 14, nsW);
            box(c, bb, 4, 1, 17, 4, 4, 17, nsW);
        }
    }

    /** NeCCS — 성채 계단 복도(5x14x10). */
    static class CastleCorridorStairs extends Piece {
        CastleCorridorStairs(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forward(start, b, r, 1, 0, true);
        }

        static CastleCorridorStairs createPiece(PiecesBuilder b, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -1, -7, 0, 5, 14, 10, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new CastleCorridorStairs(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            int stairs = s.STAIRS_S;
            int fenceNS = s.fenceNS();

            for (int i = 0; i <= 9; i++) {
                int lo = Math.max(1, 7 - i);
                int hi = Math.min(Math.max(lo + 5, 14 - i), 13);
                box(c, bb, 0, 0, i, 4, lo, i, nb);
                box(c, bb, 1, lo + 1, i, 3, hi - 1, i, air);
                if (i <= 6) {
                    placeBlock(c, stairs, 1, lo + 1, i, bb);
                    placeBlock(c, stairs, 2, lo + 1, i, bb);
                    placeBlock(c, stairs, 3, lo + 1, i, bb);
                }

                box(c, bb, 0, hi, i, 4, hi, i, nb);
                box(c, bb, 0, lo + 1, i, 0, hi - 1, i, nb);
                box(c, bb, 4, lo + 1, i, 4, hi - 1, i, nb);
                if ((i & 1) == 0) {
                    box(c, bb, 0, lo + 2, i, 0, lo + 3, i, fenceNS);
                    box(c, bb, 4, lo + 2, i, 4, lo + 3, i, fenceNS);
                }

                for (int x = 0; x <= 4; x++) {
                    fillColumnDown(c, nb, x, -1, i, bb);
                }
            }
        }
    }

    /** NeCTB — 성채 T자 발코니 복도(9x7x9). */
    static class CastleCorridorTBalcony extends Piece {
        CastleCorridorTBalcony(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            int zOff = 1;
            Direction d = orientation;
            if (d == Direction.WEST || d == Direction.NORTH) {
                zOff = 5;
            }

            left(start, b, r, 0, zOff, r.nextInt(8) > 0);
            right(start, b, r, 0, zOff, r.nextInt(8) > 0);
        }

        static CastleCorridorTBalcony createPiece(PiecesBuilder b, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -3, 0, 0, 9, 7, 9, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new CastleCorridorTBalcony(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            int fenceNS = s.fenceNS();
            int fenceWE = s.fenceWE();
            box(c, bb, 0, 0, 0, 8, 1, 8, nb);
            box(c, bb, 0, 2, 0, 8, 5, 8, air);
            box(c, bb, 0, 6, 0, 8, 6, 5, nb);
            box(c, bb, 0, 2, 0, 2, 5, 0, nb);
            box(c, bb, 6, 2, 0, 8, 5, 0, nb);
            box(c, bb, 1, 3, 0, 1, 4, 0, fenceWE);
            box(c, bb, 7, 3, 0, 7, 4, 0, fenceWE);
            box(c, bb, 0, 2, 4, 8, 2, 8, nb);
            box(c, bb, 1, 1, 4, 2, 2, 4, air);
            box(c, bb, 6, 1, 4, 7, 2, 4, air);
            box(c, bb, 1, 3, 8, 7, 3, 8, fenceWE);
            placeBlock(c, s.fence(false, true, true, false), 0, 3, 8, bb);
            placeBlock(c, s.fence(false, false, true, true), 8, 3, 8, bb);
            box(c, bb, 0, 3, 6, 0, 3, 7, fenceNS);
            box(c, bb, 8, 3, 6, 8, 3, 7, fenceNS);
            box(c, bb, 0, 3, 4, 0, 5, 5, nb);
            box(c, bb, 8, 3, 4, 8, 5, 5, nb);
            box(c, bb, 1, 3, 5, 2, 5, 5, nb);
            box(c, bb, 6, 3, 5, 7, 5, 5, nb);
            box(c, bb, 1, 4, 5, 1, 5, 5, fenceWE);
            box(c, bb, 7, 4, 5, 7, 5, 5, fenceWE);

            for (int i = 0; i <= 5; i++) {
                for (int j = 0; j <= 8; j++) {
                    fillColumnDown(c, nb, j, -1, i, bb);
                }
            }
        }
    }

    /** NeCE — 성채 입구(13x14x13, 중앙 용암 우물). */
    static class CastleEntrance extends Piece {
        CastleEntrance(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forward(start, b, r, 5, 3, true);
        }

        static CastleEntrance createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -5, -3, 0, 13, 14, 13, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new CastleEntrance(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 0, 3, 0, 12, 4, 12, nb);
            box(c, bb, 0, 5, 0, 12, 13, 12, air);
            box(c, bb, 0, 5, 0, 1, 12, 12, nb);
            box(c, bb, 11, 5, 0, 12, 12, 12, nb);
            box(c, bb, 2, 5, 11, 4, 12, 12, nb);
            box(c, bb, 8, 5, 11, 10, 12, 12, nb);
            box(c, bb, 5, 9, 11, 7, 12, 12, nb);
            box(c, bb, 2, 5, 0, 4, 12, 1, nb);
            box(c, bb, 8, 5, 0, 10, 12, 1, nb);
            box(c, bb, 5, 9, 0, 7, 12, 1, nb);
            box(c, bb, 2, 11, 2, 10, 12, 10, nb);
            box(c, bb, 5, 8, 0, 7, 8, 0, s.FENCE_PLAIN);
            int fenceWE = s.fenceWE();
            int fenceNS = s.fenceNS();

            for (int i = 1; i <= 11; i += 2) {
                box(c, bb, i, 10, 0, i, 11, 0, fenceWE);
                box(c, bb, i, 10, 12, i, 11, 12, fenceWE);
                box(c, bb, 0, 10, i, 0, 11, i, fenceNS);
                box(c, bb, 12, 10, i, 12, 11, i, fenceNS);
                placeBlock(c, nb, i, 13, 0, bb);
                placeBlock(c, nb, i, 13, 12, bb);
                placeBlock(c, nb, 0, 13, i, bb);
                placeBlock(c, nb, 12, 13, i, bb);
                if (i != 11) {
                    placeBlock(c, fenceWE, i + 1, 13, 0, bb);
                    placeBlock(c, fenceWE, i + 1, 13, 12, bb);
                    placeBlock(c, fenceNS, 0, 13, i + 1, bb);
                    placeBlock(c, fenceNS, 12, 13, i + 1, bb);
                }
            }

            placeBlock(c, s.fence(true, true, false, false), 0, 13, 0, bb);
            placeBlock(c, s.fence(false, true, true, false), 0, 13, 12, bb);
            placeBlock(c, s.fence(false, false, true, true), 12, 13, 12, bb);
            placeBlock(c, s.fence(true, false, false, true), 12, 13, 0, bb);

            for (int i = 3; i <= 9; i += 2) {
                int nsW = s.fence(true, false, true, true);
                int nsE = s.fence(true, true, true, false);
                box(c, bb, 1, 7, i, 1, 8, i, nsW);
                box(c, bb, 11, 7, i, 11, 8, i, nsE);
            }

            box(c, bb, 4, 2, 0, 8, 2, 12, nb);
            box(c, bb, 0, 2, 4, 12, 2, 8, nb);
            box(c, bb, 4, 0, 0, 8, 1, 3, nb);
            box(c, bb, 4, 0, 9, 8, 1, 12, nb);
            box(c, bb, 0, 0, 4, 3, 1, 8, nb);
            box(c, bb, 9, 0, 4, 12, 1, 8, nb);

            for (int i = 4; i <= 8; i++) {
                for (int j = 0; j <= 2; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                    fillColumnDown(c, nb, i, -1, 12 - j, bb);
                }
            }

            for (int i = 0; i <= 2; i++) {
                for (int j = 4; j <= 8; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                    fillColumnDown(c, nb, 12 - i, -1, j, bb);
                }
            }

            box(c, bb, 5, 5, 5, 7, 5, 7, nb);
            box(c, bb, 6, 1, 6, 6, 4, 6, air);
            placeBlock(c, nb, 6, 0, 6, bb);
            placeBlock(c, s.LAVA, 6, 5, 6, bb);
            // 바닐라: getWorldPos(6, 5, 6) 이 상자 안이면 scheduleTick(pos, Fluids.LAVA, 0) — 틱 범위에 들어오면 용암이 흐른다(A06-03).
            // placeBlock 이 이미 같은 칸·종류를 걸었으면 생성 틱 목록이 겹치지 않게 하나로 둔다(ProtoChunkTicks 와 같다).
            int lx = getWorldX(6, 6), ly = getWorldY(5), lz = getWorldZ(6, 6);
            if (bb.isInside(lx, ly, lz)) c.view.scheduleFluidTick(lx, ly, lz, org.domi.furnace.server.block.Fluids.T_LAVA, 0);
        }
    }

    /** NeSCSC — 성채 작은 복도 십자(5x7x5). */
    static class CastleSmallCorridorCrossing extends Piece {
        CastleSmallCorridorCrossing(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forward(start, b, r, 1, 0, true);
            left(start, b, r, 0, 1, true);
            right(start, b, r, 0, 1, true);
        }

        static CastleSmallCorridorCrossing createPiece(PiecesBuilder b, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -1, 0, 0, 5, 7, 5, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new CastleSmallCorridorCrossing(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 0, 0, 0, 4, 1, 4, nb);
            box(c, bb, 0, 2, 0, 4, 5, 4, air);
            box(c, bb, 0, 2, 0, 0, 5, 0, nb);
            box(c, bb, 4, 2, 0, 4, 5, 0, nb);
            box(c, bb, 0, 2, 4, 0, 5, 4, nb);
            box(c, bb, 4, 2, 4, 4, 5, 4, nb);
            box(c, bb, 0, 6, 0, 4, 6, 4, nb);

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                }
            }
        }
    }

    /** NeSCLT — 성채 작은 복도 왼쪽 꺾임(5x7x5, 1/3 확률로 상자). */
    static class CastleSmallCorridorLeftTurn extends Piece {
        private boolean isNeedingChest;

        CastleSmallCorridorLeftTurn(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.isNeedingChest = r.nextInt(3) == 0;
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            left(start, b, r, 0, 1, true);
        }

        static CastleSmallCorridorLeftTurn createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -1, 0, 0, 5, 7, 5, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new CastleSmallCorridorLeftTurn(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 0, 0, 0, 4, 1, 4, nb);
            box(c, bb, 0, 2, 0, 4, 5, 4, air);
            int fenceWE = s.fenceWE();
            int fenceNS = s.fenceNS();
            box(c, bb, 4, 2, 0, 4, 5, 4, nb);
            box(c, bb, 4, 3, 1, 4, 4, 1, fenceNS);
            box(c, bb, 4, 3, 3, 4, 4, 3, fenceNS);
            box(c, bb, 0, 2, 0, 0, 5, 0, nb);
            box(c, bb, 0, 2, 4, 3, 5, 4, nb);
            box(c, bb, 1, 3, 4, 1, 4, 4, fenceWE);
            box(c, bb, 3, 3, 4, 3, 4, 4, fenceWE);
            if (isNeedingChest && bb.isInside(getWorldX(3, 3), getWorldY(2), getWorldZ(3, 3))) {
                isNeedingChest = false;
                createChestLocal(c, bb, 3, 2, 3, -1, "minecraft:chests/nether_bridge");
            }

            box(c, bb, 0, 6, 0, 4, 6, 4, nb);

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                }
            }
        }
    }

    /** NeSC — 성채 작은 복도(5x7x5). */
    static class CastleSmallCorridor extends Piece {
        CastleSmallCorridor(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forward(start, b, r, 1, 0, true);
        }

        static CastleSmallCorridor createPiece(PiecesBuilder b, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -1, 0, 0, 5, 7, 5, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new CastleSmallCorridor(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 0, 0, 0, 4, 1, 4, nb);
            box(c, bb, 0, 2, 0, 4, 5, 4, air);
            int fenceNS = s.fenceNS();
            box(c, bb, 0, 2, 0, 0, 5, 4, nb);
            box(c, bb, 4, 2, 0, 4, 5, 4, nb);
            box(c, bb, 0, 3, 1, 0, 4, 1, fenceNS);
            box(c, bb, 0, 3, 3, 0, 4, 3, fenceNS);
            box(c, bb, 4, 3, 1, 4, 4, 1, fenceNS);
            box(c, bb, 4, 3, 3, 4, 4, 3, fenceNS);
            box(c, bb, 0, 6, 0, 4, 6, 4, nb);

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                }
            }
        }
    }

    /** NeSCRT — 성채 작은 복도 오른쪽 꺾임(5x7x5, 1/3 확률로 상자). */
    static class CastleSmallCorridorRightTurn extends Piece {
        private boolean isNeedingChest;

        CastleSmallCorridorRightTurn(int genDepth, LegacyRandom r, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
            this.isNeedingChest = r.nextInt(3) == 0;
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            right(start, b, r, 0, 1, true);
        }

        static CastleSmallCorridorRightTurn createPiece(PiecesBuilder b, LegacyRandom r, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -1, 0, 0, 5, 7, 5, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new CastleSmallCorridorRightTurn(depth, r, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 0, 0, 0, 4, 1, 4, nb);
            box(c, bb, 0, 2, 0, 4, 5, 4, air);
            int fenceWE = s.fenceWE();
            int fenceNS = s.fenceNS();
            box(c, bb, 0, 2, 0, 0, 5, 4, nb);
            box(c, bb, 0, 3, 1, 0, 4, 1, fenceNS);
            box(c, bb, 0, 3, 3, 0, 4, 3, fenceNS);
            box(c, bb, 4, 2, 0, 4, 5, 0, nb);
            box(c, bb, 1, 2, 4, 4, 5, 4, nb);
            box(c, bb, 1, 3, 4, 1, 4, 4, fenceWE);
            box(c, bb, 3, 3, 4, 3, 4, 4, fenceWE);
            if (isNeedingChest && bb.isInside(getWorldX(1, 3), getWorldY(2), getWorldZ(1, 3))) {
                isNeedingChest = false;
                createChestLocal(c, bb, 1, 2, 3, -1, "minecraft:chests/nether_bridge");
            }

            box(c, bb, 0, 6, 0, 4, 6, 4, nb);

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                }
            }
        }
    }

    /** NeCSR — 성채 줄기방(13x14x13, 영혼 모래 + 네더 와트). */
    static class CastleStalkRoom extends Piece {
        CastleStalkRoom(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forward(start, b, r, 5, 3, true);
            forward(start, b, r, 5, 11, true);
        }

        static CastleStalkRoom createPiece(PiecesBuilder b, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -5, -3, 0, 13, 14, 13, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new CastleStalkRoom(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 0, 3, 0, 12, 4, 12, nb);
            box(c, bb, 0, 5, 0, 12, 13, 12, air);
            box(c, bb, 0, 5, 0, 1, 12, 12, nb);
            box(c, bb, 11, 5, 0, 12, 12, 12, nb);
            box(c, bb, 2, 5, 11, 4, 12, 12, nb);
            box(c, bb, 8, 5, 11, 10, 12, 12, nb);
            box(c, bb, 5, 9, 11, 7, 12, 12, nb);
            box(c, bb, 2, 5, 0, 4, 12, 1, nb);
            box(c, bb, 8, 5, 0, 10, 12, 1, nb);
            box(c, bb, 5, 9, 0, 7, 12, 1, nb);
            box(c, bb, 2, 11, 2, 10, 12, 10, nb);
            int fenceWE = s.fenceWE();
            int fenceNS = s.fenceNS();
            int nsW = s.fence(true, false, true, true);
            int nsE = s.fence(true, true, true, false);

            for (int i = 1; i <= 11; i += 2) {
                box(c, bb, i, 10, 0, i, 11, 0, fenceWE);
                box(c, bb, i, 10, 12, i, 11, 12, fenceWE);
                box(c, bb, 0, 10, i, 0, 11, i, fenceNS);
                box(c, bb, 12, 10, i, 12, 11, i, fenceNS);
                placeBlock(c, nb, i, 13, 0, bb);
                placeBlock(c, nb, i, 13, 12, bb);
                placeBlock(c, nb, 0, 13, i, bb);
                placeBlock(c, nb, 12, 13, i, bb);
                if (i != 11) {
                    placeBlock(c, fenceWE, i + 1, 13, 0, bb);
                    placeBlock(c, fenceWE, i + 1, 13, 12, bb);
                    placeBlock(c, fenceNS, 0, 13, i + 1, bb);
                    placeBlock(c, fenceNS, 12, 13, i + 1, bb);
                }
            }

            placeBlock(c, s.fence(true, true, false, false), 0, 13, 0, bb);
            placeBlock(c, s.fence(false, true, true, false), 0, 13, 12, bb);
            placeBlock(c, s.fence(false, false, true, true), 12, 13, 12, bb);
            placeBlock(c, s.fence(true, false, false, true), 12, 13, 0, bb);

            for (int i = 3; i <= 9; i += 2) {
                box(c, bb, 1, 7, i, 1, 8, i, nsW);
                box(c, bb, 11, 7, i, 11, 8, i, nsE);
            }

            int stairsN = s.STAIRS_N;

            for (int i = 0; i <= 6; i++) {
                int z = i + 4;

                for (int x = 5; x <= 7; x++) {
                    placeBlock(c, stairsN, x, 5 + i, z, bb);
                }

                if (z >= 5 && z <= 8) {
                    box(c, bb, 5, 5, z, 7, i + 4, z, nb);
                } else if (z >= 9 && z <= 10) {
                    box(c, bb, 5, 8, z, 7, i + 4, z, nb);
                }

                if (i >= 1) {
                    box(c, bb, 5, 6 + i, z, 7, 9 + i, z, air);
                }
            }

            for (int x = 5; x <= 7; x++) {
                placeBlock(c, stairsN, x, 12, 11, bb);
            }

            box(c, bb, 5, 6, 7, 5, 7, 7, nsE);
            box(c, bb, 7, 6, 7, 7, 7, 7, nsW);
            box(c, bb, 5, 13, 12, 7, 13, 12, air);
            box(c, bb, 2, 5, 2, 3, 5, 3, nb);
            box(c, bb, 2, 5, 9, 3, 5, 10, nb);
            box(c, bb, 2, 5, 4, 2, 5, 8, nb);
            box(c, bb, 9, 5, 2, 10, 5, 3, nb);
            box(c, bb, 9, 5, 9, 10, 5, 10, nb);
            box(c, bb, 10, 5, 4, 10, 5, 8, nb);
            int stairsE = s.STAIRS_E;
            int stairsW = s.STAIRS_W;
            placeBlock(c, stairsW, 4, 5, 2, bb);
            placeBlock(c, stairsW, 4, 5, 3, bb);
            placeBlock(c, stairsW, 4, 5, 9, bb);
            placeBlock(c, stairsW, 4, 5, 10, bb);
            placeBlock(c, stairsE, 8, 5, 2, bb);
            placeBlock(c, stairsE, 8, 5, 3, bb);
            placeBlock(c, stairsE, 8, 5, 9, bb);
            placeBlock(c, stairsE, 8, 5, 10, bb);
            box(c, bb, 3, 4, 4, 4, 4, 8, s.SOUL_SAND);
            box(c, bb, 8, 4, 4, 9, 4, 8, s.SOUL_SAND);
            box(c, bb, 3, 5, 4, 4, 5, 8, s.WART);
            box(c, bb, 8, 5, 4, 9, 5, 8, s.WART);
            box(c, bb, 4, 2, 0, 8, 2, 12, nb);
            box(c, bb, 0, 2, 4, 12, 2, 8, nb);
            box(c, bb, 4, 0, 0, 8, 1, 3, nb);
            box(c, bb, 4, 0, 9, 8, 1, 12, nb);
            box(c, bb, 0, 0, 4, 3, 1, 8, nb);
            box(c, bb, 9, 0, 4, 12, 1, 8, nb);

            for (int i = 4; i <= 8; i++) {
                for (int j = 0; j <= 2; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                    fillColumnDown(c, nb, i, -1, 12 - j, bb);
                }
            }

            for (int i = 0; i <= 2; i++) {
                for (int j = 4; j <= 8; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                    fillColumnDown(c, nb, 12 - i, -1, j, bb);
                }
            }
        }
    }

    /** NeMT — 몬스터 왕좌(7x8x9, 블레이즈 스포너). */
    static class MonsterThrone extends Piece {
        private boolean hasPlacedSpawner;

        MonsterThrone(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        static MonsterThrone createPiece(PiecesBuilder b, int x, int y, int z, int depth, Direction dir) {
            BoundingBox box = orient(x, y, z, -2, 0, 0, 7, 8, 9, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new MonsterThrone(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 0, 2, 0, 6, 7, 7, air);
            box(c, bb, 1, 0, 0, 5, 1, 7, nb);
            box(c, bb, 1, 2, 1, 5, 2, 7, nb);
            box(c, bb, 1, 3, 2, 5, 3, 7, nb);
            box(c, bb, 1, 4, 3, 5, 4, 7, nb);
            box(c, bb, 1, 2, 0, 1, 4, 2, nb);
            box(c, bb, 5, 2, 0, 5, 4, 2, nb);
            box(c, bb, 1, 5, 2, 1, 5, 3, nb);
            box(c, bb, 5, 5, 2, 5, 5, 3, nb);
            box(c, bb, 0, 5, 3, 0, 5, 8, nb);
            box(c, bb, 6, 5, 3, 6, 5, 8, nb);
            box(c, bb, 1, 5, 8, 5, 5, 8, nb);
            int fenceWE = s.fenceWE();
            int fenceNS = s.fenceNS();
            int fenceW = s.fence(false, false, false, true);
            int fenceE = s.fence(false, true, false, false);
            placeBlock(c, fenceW, 1, 6, 3, bb);
            placeBlock(c, fenceE, 5, 6, 3, bb);
            placeBlock(c, s.fence(true, true, false, false), 0, 6, 3, bb);
            placeBlock(c, s.fence(true, false, false, true), 6, 6, 3, bb);
            box(c, bb, 0, 6, 4, 0, 6, 7, fenceNS);
            box(c, bb, 6, 6, 4, 6, 6, 7, fenceNS);
            placeBlock(c, s.fence(false, true, true, false), 0, 6, 8, bb);
            placeBlock(c, s.fence(false, false, true, true), 6, 6, 8, bb);
            box(c, bb, 1, 6, 8, 5, 6, 8, fenceWE);
            placeBlock(c, fenceE, 1, 7, 8, bb);
            box(c, bb, 2, 7, 8, 4, 7, 8, fenceWE);
            placeBlock(c, fenceW, 5, 7, 8, bb);
            placeBlock(c, fenceE, 2, 8, 8, bb);
            placeBlock(c, fenceWE, 3, 8, 8, bb);
            placeBlock(c, fenceW, 4, 8, 8, bb);
            if (!hasPlacedSpawner) {
                int wx = getWorldX(3, 5), wy = getWorldY(5), wz = getWorldZ(3, 5);
                if (bb.isInside(wx, wy, wz)) {
                    hasPlacedSpawner = true;
                    c.view.setBlock(wx, wy, wz, s.SPAWNER);
                    c.view.putGeneratedBlockEntity(wx, wy, wz, spawnerNbt(wx, wy, wz));
                }
            }

            for (int i = 0; i <= 6; i++) {
                for (int j = 0; j <= 6; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                }
            }
        }

        /** 새 스포너 블록 엔티티에 setEntityId(blaze) 한 뒤의 저장 형태(BaseSpawner.save + 위치). */
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
            entity.putString("id", "minecraft:blaze");
            NbtCompound spawnData = new NbtCompound();
            spawnData.putCompound("entity", entity);
            nbt.putCompound("SpawnData", spawnData);
            nbt.putList("SpawnPotentials", new NbtList((byte) 10));
            return nbt;
        }
    }

    /** NeRC — 방 교차로(7x9x7). */
    static class RoomCrossing extends Piece {
        RoomCrossing(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            forward(start, b, r, 2, 0, false);
            left(start, b, r, 0, 2, false);
            right(start, b, r, 0, 2, false);
        }

        static RoomCrossing createPiece(PiecesBuilder b, int x, int y, int z, Direction dir, int depth) {
            BoundingBox box = orient(x, y, z, -2, 0, 0, 7, 9, 7, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new RoomCrossing(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 0, 0, 0, 6, 1, 6, nb);
            box(c, bb, 0, 2, 0, 6, 7, 6, air);
            box(c, bb, 0, 2, 0, 1, 6, 0, nb);
            box(c, bb, 0, 2, 6, 1, 6, 6, nb);
            box(c, bb, 5, 2, 0, 6, 6, 0, nb);
            box(c, bb, 5, 2, 6, 6, 6, 6, nb);
            box(c, bb, 0, 2, 0, 0, 6, 1, nb);
            box(c, bb, 0, 2, 5, 0, 6, 6, nb);
            box(c, bb, 6, 2, 0, 6, 6, 1, nb);
            box(c, bb, 6, 2, 5, 6, 6, 6, nb);
            int fenceWE = s.fenceWE();
            int fenceNS = s.fenceNS();
            box(c, bb, 2, 6, 0, 4, 6, 0, nb);
            box(c, bb, 2, 5, 0, 4, 5, 0, fenceWE);
            box(c, bb, 2, 6, 6, 4, 6, 6, nb);
            box(c, bb, 2, 5, 6, 4, 5, 6, fenceWE);
            box(c, bb, 0, 6, 2, 0, 6, 4, nb);
            box(c, bb, 0, 5, 2, 0, 5, 4, fenceNS);
            box(c, bb, 6, 6, 2, 6, 6, 4, nb);
            box(c, bb, 6, 5, 2, 6, 5, 4, fenceNS);

            for (int i = 0; i <= 6; i++) {
                for (int j = 0; j <= 6; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                }
            }
        }
    }

    /** NeSR — 계단방(7x11x7). */
    static class StairsRoom extends Piece {
        StairsRoom(int genDepth, BoundingBox box, Direction dir) {
            super(genDepth, box, dir);
        }

        @Override
        void addChildren(StartPiece start, PiecesBuilder b, LegacyRandom r) {
            right(start, b, r, 6, 2, false);
        }

        static StairsRoom createPiece(PiecesBuilder b, int x, int y, int z, int depth, Direction dir) {
            BoundingBox box = orient(x, y, z, -2, 0, 0, 7, 11, 7, dir);
            return isOkBox(box) && b.findCollisionPiece(box) == null ? new StairsRoom(depth, box, dir) : null;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox bb = c.writableBox;
            St s = St.of(c.engine);
            int nb = s.NB, air = s.AIR;
            box(c, bb, 0, 0, 0, 6, 1, 6, nb);
            box(c, bb, 0, 2, 0, 6, 10, 6, air);
            box(c, bb, 0, 2, 0, 1, 8, 0, nb);
            box(c, bb, 5, 2, 0, 6, 8, 0, nb);
            box(c, bb, 0, 2, 1, 0, 8, 6, nb);
            box(c, bb, 6, 2, 1, 6, 8, 6, nb);
            box(c, bb, 1, 2, 6, 5, 8, 6, nb);
            int fenceWE = s.fenceWE();
            int fenceNS = s.fenceNS();
            box(c, bb, 0, 3, 2, 0, 5, 4, fenceNS);
            box(c, bb, 6, 3, 2, 6, 5, 2, fenceNS);
            box(c, bb, 6, 3, 4, 6, 5, 4, fenceNS);
            placeBlock(c, nb, 5, 2, 5, bb);
            box(c, bb, 4, 2, 5, 4, 3, 5, nb);
            box(c, bb, 3, 2, 5, 3, 4, 5, nb);
            box(c, bb, 2, 2, 5, 2, 5, 5, nb);
            box(c, bb, 1, 2, 5, 1, 6, 5, nb);
            box(c, bb, 1, 7, 1, 5, 7, 4, nb);
            box(c, bb, 6, 8, 2, 6, 8, 4, air);
            box(c, bb, 2, 6, 0, 4, 8, 0, nb);
            box(c, bb, 2, 5, 0, 4, 5, 0, fenceWE);

            for (int i = 0; i <= 6; i++) {
                for (int j = 0; j <= 6; j++) {
                    fillColumnDown(c, nb, i, -1, j, bb);
                }
            }
        }
    }

    /** NeStart — 시작 조각(다리 교차로) + 조각 가중치 상태. */
    static final class StartPiece extends BridgeCrossing {
        PieceWeight previousPiece;
        final List<PieceWeight> bridgePieces = newBridgeSet();
        final List<PieceWeight> castlePieces = newCastleSet();
        final List<Piece> pendingChildren = new ArrayList<>();

        StartPiece(org.domi.furnace.server.world.gen.carver.WorldgenRandom random, int x, int z) {
            super(x, z, Direction.randomHorizontal(random));
        }
    }
}
