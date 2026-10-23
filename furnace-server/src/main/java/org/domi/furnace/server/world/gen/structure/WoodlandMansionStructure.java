// 삼림 대저택 — 바닐라 WoodlandMansionStructure + WoodlandMansionPieces(MansionGrid 로 방 배치를 정한 뒤 woodland_mansion/* 템플릿 조각을 쌓는다).
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processors;

public final class WoodlandMansionStructure extends StructureType {

    public WoodlandMansionStructure(StructureRegistry.Def def) {}

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        WorldgenRandom random = c.random;
        int rotation = random.nextInt(4);   // Rotation.getRandom (NONE, CW90, 180, CCW90)
        int y = c.lowestYIn5by5BoxOffset7Blocks(rotation);
        if (y < 60) return Optional.empty();
        int x = c.lowestYIn5by5BoxOffset7BlocksX();
        int z = c.lowestYIn5by5BoxOffset7BlocksZ();
        return Optional.of(new Stub(x, y, z, b -> {
            List<Piece> pieces = new ArrayList<>();
            generate(c.engine, x, y, z, rotation, pieces, random);
            for (Piece p : pieces) b.addPiece(p);
        }));
    }

    /** 바닐라 WoodlandMansionStructure.afterPlace — 저택 바닥 아래를 조약돌로 지면/액체까지 채운다. */
    @Override
    public void afterPlace(PostCtx c, StructureStart start) {
        BlockAttrs a = BlockAttrs.get();
        BoundingBox box = c.writableBox;
        BoundingBox whole = start.rawBox();
        int minY = whole.minY();
        int cobble = c.blocks().defaultStateId("minecraft:cobblestone");
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (!a.isAir(c.view.getBlock(x, minY, z)) && whole.isInside(x, minY, z) && insidePiece(start, x, minY, z)) {
                    for (int y = minY - 1; y > -64; y--) {
                        int s = c.view.getBlock(x, y, z);
                        if (!a.isAir(s) && !a.isLiquid(s)) break;
                        c.view.setBlock(x, y, z, cobble);
                    }
                }
            }
        }
    }

    private static boolean insidePiece(StructureStart start, int x, int y, int z) {
        for (StructurePiece p : start.pieces()) {
            if (p.boundingBox().isInside(x, y, z)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 방향/회전 도우미

    /** 수평 방향 인덱스: 바닐라 Plane.HORIZONTAL 순서 NORTH, EAST, SOUTH, WEST. UP 은 4, 없음은 -1. */
    static final int N = 0, E = 1, S = 2, W = 3, UP = 4, NONE = -1;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};
    /** Direction.from2DDataValue 순서: SOUTH, WEST, NORTH, EAST. */
    private static final int[] FROM_2D = {S, W, N, E};

    static int cw(int d) { return (d + 1) & 3; }

    static int ccw(int d) { return (d + 3) & 3; }

    static int opposite(int d) { return (d + 2) & 3; }

    /** Rotation.rotate(Direction): UP 은 그대로. */
    static int rot(int rotation, int d) { return d == UP ? UP : (d + rotation) & 3; }

    /** Rotation.getRotated(Rotation). */
    static int addRot(int a, int b) { return (a + b) & 3; }

    static final int R_NONE = 0, R_CW90 = 1, R_180 = 2, R_CCW90 = 3;

    /** 불변 블록 좌표. */
    record P(int x, int y, int z) {
        P rel(int d, int n) { return n == 0 ? this : new P(x + DX[d] * n, y, z + DZ[d] * n); }

        P above(int n) { return new P(x, y + n, z); }

        P above() { return above(1); }

        P offset(int dx, int dy, int dz) { return new P(x + dx, y + dy, z + dz); }
    }

    // ------------------------------------------------------------------ 격자 (MansionGrid 가 쓰는 SimpleGrid)

    static final class SimpleGrid {
        private final int[][] grid;
        final int width, height;
        private final int valueIfOutside;

        SimpleGrid(int width, int height, int valueIfOutside) {
            this.width = width;
            this.height = height;
            this.valueIfOutside = valueIfOutside;
            this.grid = new int[width][height];
        }

        void set(int x, int z, int v) {
            if (x >= 0 && x < width && z >= 0 && z < height) grid[x][z] = v;
        }

        void set(int x0, int z0, int x1, int z1, int v) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) set(x, z, v);
            }
        }

        int get(int x, int z) {
            return x >= 0 && x < width && z >= 0 && z < height ? grid[x][z] : valueIfOutside;
        }

        void setIf(int x, int z, int expected, int v) {
            if (get(x, z) == expected) set(x, z, v);
        }

        boolean edgesTo(int x, int z, int v) {
            return get(x - 1, z) == v || get(x + 1, z) == v || get(x, z + 1) == v || get(x, z - 1) == v;
        }
    }

    // ------------------------------------------------------------------ MansionGrid

    static final int ROOM_1x1 = 65536, ROOM_1x2 = 131072, ROOM_2x2 = 262144;
    static final int ORIGIN = 1048576, ENTRANCE = 2097152, STAIRS = 4194304, CORRIDOR = 8388608;
    static final int TYPE_MASK = 983040, ID_MASK = 65535;

    static final class MansionGrid {
        private final WorldgenRandom random;
        final SimpleGrid baseGrid;
        final SimpleGrid thirdFloorGrid;
        final SimpleGrid[] floorRooms;
        final int entranceX, entranceY;

        MansionGrid(WorldgenRandom random) {
            this.random = random;
            this.entranceX = 7;
            this.entranceY = 4;
            this.baseGrid = new SimpleGrid(11, 11, 5);
            baseGrid.set(entranceX, entranceY, entranceX + 1, entranceY + 1, 3);
            baseGrid.set(entranceX - 1, entranceY, entranceX - 1, entranceY + 1, 2);
            baseGrid.set(entranceX + 2, entranceY - 2, entranceX + 3, entranceY + 3, 5);
            baseGrid.set(entranceX + 1, entranceY - 2, entranceX + 1, entranceY - 1, 1);
            baseGrid.set(entranceX + 1, entranceY + 2, entranceX + 1, entranceY + 3, 1);
            baseGrid.set(entranceX - 1, entranceY - 1, 1);
            baseGrid.set(entranceX - 1, entranceY + 2, 1);
            baseGrid.set(0, 0, 11, 1, 5);
            baseGrid.set(0, 9, 11, 11, 5);
            recursiveCorridor(baseGrid, entranceX, entranceY - 2, W, 6);
            recursiveCorridor(baseGrid, entranceX, entranceY + 3, W, 6);
            recursiveCorridor(baseGrid, entranceX - 2, entranceY - 1, W, 3);
            recursiveCorridor(baseGrid, entranceX - 2, entranceY + 2, W, 3);

            while (cleanEdges(baseGrid)) {
            }

            floorRooms = new SimpleGrid[3];
            floorRooms[0] = new SimpleGrid(11, 11, 5);
            floorRooms[1] = new SimpleGrid(11, 11, 5);
            floorRooms[2] = new SimpleGrid(11, 11, 5);
            identifyRooms(baseGrid, floorRooms[0]);
            identifyRooms(baseGrid, floorRooms[1]);
            floorRooms[0].set(entranceX + 1, entranceY, entranceX + 1, entranceY + 1, CORRIDOR);
            floorRooms[1].set(entranceX + 1, entranceY, entranceX + 1, entranceY + 1, CORRIDOR);
            this.thirdFloorGrid = new SimpleGrid(baseGrid.width, baseGrid.height, 5);
            setupThirdFloor();
            identifyRooms(thirdFloorGrid, floorRooms[2]);
        }

        static boolean isHouse(SimpleGrid grid, int x, int z) {
            int v = grid.get(x, z);
            return v == 1 || v == 2 || v == 3 || v == 4;
        }

        boolean isRoomId(int x, int z, int floor, int roomId) {
            return (floorRooms[floor].get(x, z) & ID_MASK) == roomId;
        }

        /** 같은 방 id 를 가진 이웃 칸 방향(없으면 NONE). */
        int get2dDirection(int x, int z, int floor, int roomId) {
            for (int d = 0; d < 4; d++) {
                if (isRoomId(x + DX[d], z + DZ[d], floor, roomId)) return d;
            }
            return NONE;
        }

        private void recursiveCorridor(SimpleGrid grid, int x, int z, int dir, int length) {
            if (length > 0) {
                grid.set(x, z, 1);
                grid.setIf(x + DX[dir], z + DZ[dir], 0, 1);

                for (int i = 0; i < 8; i++) {
                    int d = FROM_2D[random.nextInt(4)];
                    if (d != opposite(dir) && (d != E || !random.nextBoolean())) {
                        int nx = x + DX[dir];
                        int nz = z + DZ[dir];
                        if (grid.get(nx + DX[d], nz + DZ[d]) == 0 && grid.get(nx + DX[d] * 2, nz + DZ[d] * 2) == 0) {
                            recursiveCorridor(grid, x + DX[dir] + DX[d], z + DZ[dir] + DZ[d], d, length - 1);
                            break;
                        }
                    }
                }

                int cwd = cw(dir);
                int ccwd = ccw(dir);
                grid.setIf(x + DX[cwd], z + DZ[cwd], 0, 2);
                grid.setIf(x + DX[ccwd], z + DZ[ccwd], 0, 2);
                grid.setIf(x + DX[dir] + DX[cwd], z + DZ[dir] + DZ[cwd], 0, 2);
                grid.setIf(x + DX[dir] + DX[ccwd], z + DZ[dir] + DZ[ccwd], 0, 2);
                grid.setIf(x + DX[dir] * 2, z + DZ[dir] * 2, 0, 2);
                grid.setIf(x + DX[cwd] * 2, z + DZ[cwd] * 2, 0, 2);
                grid.setIf(x + DX[ccwd] * 2, z + DZ[ccwd] * 2, 0, 2);
            }
        }

        private boolean cleanEdges(SimpleGrid grid) {
            boolean changed = false;
            for (int z = 0; z < grid.height; z++) {
                for (int x = 0; x < grid.width; x++) {
                    if (grid.get(x, z) == 0) {
                        int n = 0;
                        n += isHouse(grid, x + 1, z) ? 1 : 0;
                        n += isHouse(grid, x - 1, z) ? 1 : 0;
                        n += isHouse(grid, x, z + 1) ? 1 : 0;
                        n += isHouse(grid, x, z - 1) ? 1 : 0;
                        if (n >= 3) {
                            grid.set(x, z, 2);
                            changed = true;
                        } else if (n == 2) {
                            int m = 0;
                            m += isHouse(grid, x + 1, z + 1) ? 1 : 0;
                            m += isHouse(grid, x - 1, z + 1) ? 1 : 0;
                            m += isHouse(grid, x + 1, z - 1) ? 1 : 0;
                            m += isHouse(grid, x - 1, z - 1) ? 1 : 0;
                            if (m <= 1) {
                                grid.set(x, z, 2);
                                changed = true;
                            }
                        }
                    }
                }
            }
            return changed;
        }

        private void setupThirdFloor() {
            List<int[]> candidates = new ArrayList<>();
            SimpleGrid second = floorRooms[1];

            for (int z = 0; z < thirdFloorGrid.height; z++) {
                for (int x = 0; x < thirdFloorGrid.width; x++) {
                    int v = second.get(x, z);
                    int type = v & TYPE_MASK;
                    if (type == ROOM_1x2 && (v & ENTRANCE) == ENTRANCE) candidates.add(new int[]{x, z});
                }
            }

            if (candidates.isEmpty()) {
                thirdFloorGrid.set(0, 0, thirdFloorGrid.width, thirdFloorGrid.height, 5);
            } else {
                int[] pick = candidates.get(random.nextInt(candidates.size()));
                int v = second.get(pick[0], pick[1]);
                second.set(pick[0], pick[1], v | STAIRS);
                int dir = get2dDirection(pick[0], pick[1], 1, v & ID_MASK);
                int nx = pick[0] + DX[dir];
                int nz = pick[1] + DZ[dir];

                for (int z = 0; z < thirdFloorGrid.height; z++) {
                    for (int x = 0; x < thirdFloorGrid.width; x++) {
                        if (!isHouse(baseGrid, x, z)) {
                            thirdFloorGrid.set(x, z, 5);
                        } else if (x == pick[0] && z == pick[1]) {
                            thirdFloorGrid.set(x, z, 3);
                        } else if (x == nx && z == nz) {
                            thirdFloorGrid.set(x, z, 3);
                            floorRooms[2].set(x, z, CORRIDOR);
                        }
                    }
                }

                List<Integer> dirs = new ArrayList<>();
                for (int d = 0; d < 4; d++) {
                    if (thirdFloorGrid.get(nx + DX[d], nz + DZ[d]) == 0) dirs.add(d);
                }

                if (dirs.isEmpty()) {
                    thirdFloorGrid.set(0, 0, thirdFloorGrid.width, thirdFloorGrid.height, 5);
                    second.set(pick[0], pick[1], v);
                } else {
                    int d = dirs.get(random.nextInt(dirs.size()));
                    recursiveCorridor(thirdFloorGrid, nx + DX[d], nz + DZ[d], d, 4);

                    while (cleanEdges(thirdFloorGrid)) {
                    }
                }
            }
        }

        private void identifyRooms(SimpleGrid grid, SimpleGrid floor) {
            List<int[]> cells = new ArrayList<>();

            for (int z = 0; z < grid.height; z++) {
                for (int x = 0; x < grid.width; x++) {
                    if (grid.get(x, z) == 2) cells.add(new int[]{x, z});
                }
            }

            // Util.shuffle
            for (int i = cells.size(); i > 1; i--) {
                int j = random.nextInt(i);
                int[] t = cells.get(i - 1);
                cells.set(i - 1, cells.get(j));
                cells.set(j, t);
            }

            int roomId = 10;

            for (int[] cell : cells) {
                int x = cell[0];
                int z = cell[1];
                if (floor.get(x, z) == 0) {
                    int minX = x;
                    int maxX = x;
                    int minZ = z;
                    int maxZ = z;
                    int type = ROOM_1x1;
                    if (floor.get(x + 1, z) == 0 && floor.get(x, z + 1) == 0 && floor.get(x + 1, z + 1) == 0
                        && grid.get(x + 1, z) == 2 && grid.get(x, z + 1) == 2 && grid.get(x + 1, z + 1) == 2) {
                        maxX = x + 1;
                        maxZ = z + 1;
                        type = ROOM_2x2;
                    } else if (floor.get(x - 1, z) == 0 && floor.get(x, z + 1) == 0 && floor.get(x - 1, z + 1) == 0
                        && grid.get(x - 1, z) == 2 && grid.get(x, z + 1) == 2 && grid.get(x - 1, z + 1) == 2) {
                        minX = x - 1;
                        maxZ = z + 1;
                        type = ROOM_2x2;
                    } else if (floor.get(x - 1, z) == 0 && floor.get(x, z - 1) == 0 && floor.get(x - 1, z - 1) == 0
                        && grid.get(x - 1, z) == 2 && grid.get(x, z - 1) == 2 && grid.get(x - 1, z - 1) == 2) {
                        minX = x - 1;
                        minZ = z - 1;
                        type = ROOM_2x2;
                    } else if (floor.get(x + 1, z) == 0 && grid.get(x + 1, z) == 2) {
                        maxX = x + 1;
                        type = ROOM_1x2;
                    } else if (floor.get(x, z + 1) == 0 && grid.get(x, z + 1) == 2) {
                        maxZ = z + 1;
                        type = ROOM_1x2;
                    } else if (floor.get(x - 1, z) == 0 && grid.get(x - 1, z) == 2) {
                        minX = x - 1;
                        type = ROOM_1x2;
                    } else if (floor.get(x, z - 1) == 0 && grid.get(x, z - 1) == 2) {
                        minZ = z - 1;
                        type = ROOM_1x2;
                    }

                    int ex = random.nextBoolean() ? minX : maxX;
                    int ez = random.nextBoolean() ? minZ : maxZ;
                    int entranceFlag = ENTRANCE;
                    if (!grid.edgesTo(ex, ez, 1)) {
                        ex = ex == minX ? maxX : minX;
                        ez = ez == minZ ? maxZ : minZ;
                        if (!grid.edgesTo(ex, ez, 1)) {
                            ez = ez == minZ ? maxZ : minZ;
                            if (!grid.edgesTo(ex, ez, 1)) {
                                ex = ex == minX ? maxX : minX;
                                ez = ez == minZ ? maxZ : minZ;
                                if (!grid.edgesTo(ex, ez, 1)) {
                                    entranceFlag = 0;
                                    ex = minX;
                                    ez = minZ;
                                }
                            }
                        }
                    }

                    for (int zz = minZ; zz <= maxZ; zz++) {
                        for (int xx = minX; xx <= maxX; xx++) {
                            if (xx == ex && zz == ez) {
                                floor.set(xx, zz, ORIGIN | entranceFlag | type | roomId);
                            } else {
                                floor.set(xx, zz, type | roomId);
                            }
                        }
                    }

                    roomId++;
                }
            }
        }
    }

    // ------------------------------------------------------------------ 방 키트 (Floor0Kit / Floor1Kit / Floor2Kit)

    abstract static class RoomKit {
        abstract String room1x1(WorldgenRandom r);

        abstract String room1x1Secret(WorldgenRandom r);

        abstract String room1x2Side(WorldgenRandom r, boolean stairs);

        abstract String room1x2Front(WorldgenRandom r, boolean stairs);

        abstract String room1x2Secret(WorldgenRandom r);

        abstract String room2x2(WorldgenRandom r);

        abstract String room2x2Secret(WorldgenRandom r);
    }

    static final class Floor0Kit extends RoomKit {
        @Override String room1x1(WorldgenRandom r) { return "1x1_a" + (r.nextInt(5) + 1); }

        @Override String room1x1Secret(WorldgenRandom r) { return "1x1_as" + (r.nextInt(4) + 1); }

        @Override String room1x2Side(WorldgenRandom r, boolean stairs) { return "1x2_a" + (r.nextInt(9) + 1); }

        @Override String room1x2Front(WorldgenRandom r, boolean stairs) { return "1x2_b" + (r.nextInt(5) + 1); }

        @Override String room1x2Secret(WorldgenRandom r) { return "1x2_s" + (r.nextInt(2) + 1); }

        @Override String room2x2(WorldgenRandom r) { return "2x2_a" + (r.nextInt(4) + 1); }

        @Override String room2x2Secret(WorldgenRandom r) { return "2x2_s1"; }
    }

    static class Floor1Kit extends RoomKit {
        @Override String room1x1(WorldgenRandom r) { return "1x1_b" + (r.nextInt(4) + 1); }

        @Override String room1x1Secret(WorldgenRandom r) { return "1x1_as" + (r.nextInt(4) + 1); }

        @Override String room1x2Side(WorldgenRandom r, boolean stairs) { return stairs ? "1x2_c_stairs" : "1x2_c" + (r.nextInt(4) + 1); }

        @Override String room1x2Front(WorldgenRandom r, boolean stairs) { return stairs ? "1x2_d_stairs" : "1x2_d" + (r.nextInt(5) + 1); }

        @Override String room1x2Secret(WorldgenRandom r) { return "1x2_se" + (r.nextInt(1) + 1); }

        @Override String room2x2(WorldgenRandom r) { return "2x2_b" + (r.nextInt(5) + 1); }

        @Override String room2x2Secret(WorldgenRandom r) { return "2x2_s1"; }
    }

    static final class Floor2Kit extends Floor1Kit {}

    // ------------------------------------------------------------------ 조각 배치 (MansionPiecePlacer)

    /** WoodlandMansionPieces.PlacementData */
    static final class PlacementData {
        int rotation;
        P pos;
        String wallType;
    }

    static void generate(StructureEngine engine, int x, int y, int z, int rotation, List<Piece> pieces, WorldgenRandom random) {
        MansionGrid grid = new MansionGrid(random);
        new PiecePlacer(engine, random).createMansion(new P(x, y, z), rotation, pieces, grid);
    }

    static final class PiecePlacer {
        private final StructureEngine engine;
        private final WorldgenRandom random;
        private int startX;
        private int startY;

        PiecePlacer(StructureEngine engine, WorldgenRandom random) {
            this.engine = engine;
            this.random = random;
        }

        private void add(List<Piece> out, String name, P pos, int rotation) {
            add(out, name, pos, rotation, BlockStateTable.NONE);
        }

        private void add(List<Piece> out, String name, P pos, int rotation, int mirror) {
            out.add(new Piece(engine, name, pos, rotation, mirror));
        }

        void createMansion(P origin, int rotation, List<Piece> pieces, MansionGrid grid) {
            PlacementData wall = new PlacementData();
            wall.pos = origin;
            wall.rotation = rotation;
            wall.wallType = "wall_flat";
            PlacementData window = new PlacementData();
            entrance(pieces, wall);
            window.pos = wall.pos.above(8);
            window.rotation = wall.rotation;
            window.wallType = "wall_window";

            SimpleGrid base = grid.baseGrid;
            SimpleGrid third = grid.thirdFloorGrid;
            this.startX = grid.entranceX + 1;
            this.startY = grid.entranceY + 1;
            int endX = grid.entranceX + 1;
            int endY = grid.entranceY;
            traverseOuterWalls(pieces, wall, base, S, startX, startY, endX, endY);
            traverseOuterWalls(pieces, window, base, S, startX, startY, endX, endY);
            PlacementData top = new PlacementData();
            top.pos = wall.pos.above(19);
            top.rotation = wall.rotation;
            top.wallType = "wall_window";
            boolean done = false;

            for (int z = 0; z < third.height && !done; z++) {
                for (int x = third.width - 1; x >= 0 && !done; x--) {
                    if (MansionGrid.isHouse(third, x, z)) {
                        top.pos = top.pos.rel(rot(rotation, S), 8 + (z - startY) * 8);
                        top.pos = top.pos.rel(rot(rotation, E), (x - startX) * 8);
                        addWallFlat(pieces, top);
                        traverseOuterWalls(pieces, top, third, S, x, z, x, z);
                        done = true;
                    }
                }
            }

            createRoof(pieces, origin.above(16), rotation, base, third);
            createRoof(pieces, origin.above(27), rotation, third, null);
            RoomKit[] kits = {new Floor0Kit(), new Floor1Kit(), new Floor1Kit()};

            for (int floor = 0; floor < 3; floor++) {
                P floorOrigin = origin.above(8 * floor + (floor == 2 ? 3 : 0));
                SimpleGrid rooms = grid.floorRooms[floor];
                SimpleGrid layout = floor == 2 ? third : base;
                String carpetSouth = floor == 0 ? "carpet_south_1" : "carpet_south_2";
                String carpetWest = floor == 0 ? "carpet_west_1" : "carpet_west_2";

                for (int z = 0; z < layout.height; z++) {
                    for (int x = 0; x < layout.width; x++) {
                        if (layout.get(x, z) == 1) {
                            P p = floorOrigin.rel(rot(rotation, S), 8 + (z - startY) * 8);
                            p = p.rel(rot(rotation, E), (x - startX) * 8);
                            add(pieces, "corridor_floor", p, rotation);
                            if (layout.get(x, z - 1) == 1 || (rooms.get(x, z - 1) & CORRIDOR) == CORRIDOR) {
                                add(pieces, "carpet_north", p.rel(rot(rotation, E), 1).above(), rotation);
                            }

                            if (layout.get(x + 1, z) == 1 || (rooms.get(x + 1, z) & CORRIDOR) == CORRIDOR) {
                                add(pieces, "carpet_east", p.rel(rot(rotation, S), 1).rel(rot(rotation, E), 5).above(), rotation);
                            }

                            if (layout.get(x, z + 1) == 1 || (rooms.get(x, z + 1) & CORRIDOR) == CORRIDOR) {
                                add(pieces, carpetSouth, p.rel(rot(rotation, S), 5).rel(rot(rotation, W), 1), rotation);
                            }

                            if (layout.get(x - 1, z) == 1 || (rooms.get(x - 1, z) & CORRIDOR) == CORRIDOR) {
                                add(pieces, carpetWest, p.rel(rot(rotation, W), 1).rel(rot(rotation, N), 1), rotation);
                            }
                        }
                    }
                }

                String wallName = floor == 0 ? "indoors_wall_1" : "indoors_wall_2";
                String doorName = floor == 0 ? "indoors_door_1" : "indoors_door_2";
                List<Integer> doorDirs = new ArrayList<>();

                for (int z = 0; z < layout.height; z++) {
                    for (int x = 0; x < layout.width; x++) {
                        boolean third3 = floor == 2 && layout.get(x, z) == 3;
                        if (layout.get(x, z) == 2 || third3) {
                            int v = rooms.get(x, z);
                            int type = v & TYPE_MASK;
                            int id = v & ID_MASK;
                            third3 = third3 && (v & CORRIDOR) == CORRIDOR;
                            doorDirs.clear();
                            if ((v & ENTRANCE) == ENTRANCE) {
                                for (int d = 0; d < 4; d++) {
                                    if (layout.get(x + DX[d], z + DZ[d]) == 1) doorDirs.add(d);
                                }
                            }

                            int doorDir = NONE;
                            if (!doorDirs.isEmpty()) {
                                doorDir = doorDirs.get(random.nextInt(doorDirs.size()));
                            } else if ((v & ORIGIN) == ORIGIN) {
                                doorDir = UP;
                            }

                            P p = floorOrigin.rel(rot(rotation, S), 8 + (z - startY) * 8);
                            p = p.rel(rot(rotation, E), -1 + (x - startX) * 8);
                            if (MansionGrid.isHouse(layout, x - 1, z) && !grid.isRoomId(x - 1, z, floor, id)) {
                                add(pieces, doorDir == W ? doorName : wallName, p, rotation);
                            }

                            if (layout.get(x + 1, z) == 1 && !third3) {
                                P q = p.rel(rot(rotation, E), 8);
                                add(pieces, doorDir == E ? doorName : wallName, q, rotation);
                            }

                            if (MansionGrid.isHouse(layout, x, z + 1) && !grid.isRoomId(x, z + 1, floor, id)) {
                                P q = p.rel(rot(rotation, S), 7);
                                q = q.rel(rot(rotation, E), 7);
                                add(pieces, doorDir == S ? doorName : wallName, q, addRot(rotation, R_CW90));
                            }

                            if (layout.get(x, z - 1) == 1 && !third3) {
                                P q = p.rel(rot(rotation, N), 1);
                                q = q.rel(rot(rotation, E), 7);
                                add(pieces, doorDir == N ? doorName : wallName, q, addRot(rotation, R_CW90));
                            }

                            if (type == ROOM_1x1) {
                                addRoom1x1(pieces, p, rotation, doorDir, kits[floor]);
                            } else if (type == ROOM_1x2 && doorDir != NONE) {
                                int d2 = grid.get2dDirection(x, z, floor, id);
                                boolean stairs = (v & STAIRS) == STAIRS;
                                addRoom1x2(pieces, p, rotation, d2, doorDir, kits[floor], stairs);
                            } else if (type == ROOM_2x2 && doorDir != NONE && doorDir != UP) {
                                int d2 = cw(doorDir);
                                if (!grid.isRoomId(x + DX[d2], z + DZ[d2], floor, id)) {
                                    d2 = opposite(d2);
                                }

                                addRoom2x2(pieces, p, rotation, d2, doorDir, kits[floor]);
                            } else if (type == ROOM_2x2 && doorDir == UP) {
                                addRoom2x2Secret(pieces, p, rotation, kits[floor]);
                            }
                        }
                    }
                }
            }
        }

        private void traverseOuterWalls(List<Piece> pieces, PlacementData data, SimpleGrid grid, int dir, int x, int y, int endX, int endY) {
            int cx = x;
            int cy = y;
            int startDir = dir;

            do {
                if (!MansionGrid.isHouse(grid, cx + DX[dir], cy + DZ[dir])) {
                    addCorner(pieces, data);
                    dir = cw(dir);
                    if (cx != endX || cy != endY || startDir != dir) {
                        addWallFlat(pieces, data);
                    }
                } else if (MansionGrid.isHouse(grid, cx + DX[dir], cy + DZ[dir])
                    && MansionGrid.isHouse(grid, cx + DX[dir] + DX[ccw(dir)], cy + DZ[dir] + DZ[ccw(dir)])) {
                    addInnerCorner(pieces, data);
                    cx += DX[dir];
                    cy += DZ[dir];
                    dir = ccw(dir);
                } else {
                    cx += DX[dir];
                    cy += DZ[dir];
                    if (cx != endX || cy != endY || startDir != dir) {
                        addWallFlat(pieces, data);
                    }
                }
            } while (cx != endX || cy != endY || startDir != dir);
        }

        private void createRoof(List<Piece> pieces, P pos, int rotation, SimpleGrid grid, SimpleGrid lowerGrid) {
            for (int z = 0; z < grid.height; z++) {
                for (int x = 0; x < grid.width; x++) {
                    P p = pos.rel(rot(rotation, S), 8 + (z - startY) * 8);
                    p = p.rel(rot(rotation, E), (x - startX) * 8);
                    boolean lower = lowerGrid != null && MansionGrid.isHouse(lowerGrid, x, z);
                    if (MansionGrid.isHouse(grid, x, z) && !lower) {
                        add(pieces, "roof", p.above(3), rotation);
                        if (!MansionGrid.isHouse(grid, x + 1, z)) {
                            P q = p.rel(rot(rotation, E), 6);
                            add(pieces, "roof_front", q, rotation);
                        }

                        if (!MansionGrid.isHouse(grid, x - 1, z)) {
                            P q = p.rel(rot(rotation, E), 0);
                            q = q.rel(rot(rotation, S), 7);
                            add(pieces, "roof_front", q, addRot(rotation, R_180));
                        }

                        if (!MansionGrid.isHouse(grid, x, z - 1)) {
                            P q = p.rel(rot(rotation, W), 1);
                            add(pieces, "roof_front", q, addRot(rotation, R_CCW90));
                        }

                        if (!MansionGrid.isHouse(grid, x, z + 1)) {
                            P q = p.rel(rot(rotation, E), 6);
                            q = q.rel(rot(rotation, S), 6);
                            add(pieces, "roof_front", q, addRot(rotation, R_CW90));
                        }
                    }
                }
            }

            if (lowerGrid != null) {
                for (int z = 0; z < grid.height; z++) {
                    for (int x = 0; x < grid.width; x++) {
                        P p = pos.rel(rot(rotation, S), 8 + (z - startY) * 8);
                        p = p.rel(rot(rotation, E), (x - startX) * 8);
                        boolean lower = MansionGrid.isHouse(lowerGrid, x, z);
                        if (MansionGrid.isHouse(grid, x, z) && lower) {
                            if (!MansionGrid.isHouse(grid, x + 1, z)) {
                                P q = p.rel(rot(rotation, E), 7);
                                add(pieces, "small_wall", q, rotation);
                            }

                            if (!MansionGrid.isHouse(grid, x - 1, z)) {
                                P q = p.rel(rot(rotation, W), 1);
                                q = q.rel(rot(rotation, S), 6);
                                add(pieces, "small_wall", q, addRot(rotation, R_180));
                            }

                            if (!MansionGrid.isHouse(grid, x, z - 1)) {
                                P q = p.rel(rot(rotation, W), 0);
                                q = q.rel(rot(rotation, N), 1);
                                add(pieces, "small_wall", q, addRot(rotation, R_CCW90));
                            }

                            if (!MansionGrid.isHouse(grid, x, z + 1)) {
                                P q = p.rel(rot(rotation, E), 6);
                                q = q.rel(rot(rotation, S), 7);
                                add(pieces, "small_wall", q, addRot(rotation, R_CW90));
                            }

                            if (!MansionGrid.isHouse(grid, x + 1, z)) {
                                if (!MansionGrid.isHouse(grid, x, z - 1)) {
                                    P q = p.rel(rot(rotation, E), 7);
                                    q = q.rel(rot(rotation, N), 2);
                                    add(pieces, "small_wall_corner", q, rotation);
                                }

                                if (!MansionGrid.isHouse(grid, x, z + 1)) {
                                    P q = p.rel(rot(rotation, E), 8);
                                    q = q.rel(rot(rotation, S), 7);
                                    add(pieces, "small_wall_corner", q, addRot(rotation, R_CW90));
                                }
                            }

                            if (!MansionGrid.isHouse(grid, x - 1, z)) {
                                if (!MansionGrid.isHouse(grid, x, z - 1)) {
                                    P q = p.rel(rot(rotation, W), 2);
                                    q = q.rel(rot(rotation, N), 1);
                                    add(pieces, "small_wall_corner", q, addRot(rotation, R_CCW90));
                                }

                                if (!MansionGrid.isHouse(grid, x, z + 1)) {
                                    P q = p.rel(rot(rotation, W), 1);
                                    q = q.rel(rot(rotation, S), 8);
                                    add(pieces, "small_wall_corner", q, addRot(rotation, R_180));
                                }
                            }
                        }
                    }
                }
            }

            for (int z = 0; z < grid.height; z++) {
                for (int x = 0; x < grid.width; x++) {
                    P p = pos.rel(rot(rotation, S), 8 + (z - startY) * 8);
                    p = p.rel(rot(rotation, E), (x - startX) * 8);
                    boolean lower = lowerGrid != null && MansionGrid.isHouse(lowerGrid, x, z);
                    if (MansionGrid.isHouse(grid, x, z) && !lower) {
                        if (!MansionGrid.isHouse(grid, x + 1, z)) {
                            P q = p.rel(rot(rotation, E), 6);
                            if (!MansionGrid.isHouse(grid, x, z + 1)) {
                                P r = q.rel(rot(rotation, S), 6);
                                add(pieces, "roof_corner", r, rotation);
                            } else if (MansionGrid.isHouse(grid, x + 1, z + 1)) {
                                P r = q.rel(rot(rotation, S), 5);
                                add(pieces, "roof_inner_corner", r, rotation);
                            }

                            if (!MansionGrid.isHouse(grid, x, z - 1)) {
                                add(pieces, "roof_corner", q, addRot(rotation, R_CCW90));
                            } else if (MansionGrid.isHouse(grid, x + 1, z - 1)) {
                                P r = p.rel(rot(rotation, E), 9);
                                r = r.rel(rot(rotation, N), 2);
                                add(pieces, "roof_inner_corner", r, addRot(rotation, R_CW90));
                            }
                        }

                        if (!MansionGrid.isHouse(grid, x - 1, z)) {
                            P q = p.rel(rot(rotation, E), 0);
                            q = q.rel(rot(rotation, S), 0);
                            if (!MansionGrid.isHouse(grid, x, z + 1)) {
                                P r = q.rel(rot(rotation, S), 6);
                                add(pieces, "roof_corner", r, addRot(rotation, R_CW90));
                            } else if (MansionGrid.isHouse(grid, x - 1, z + 1)) {
                                P r = q.rel(rot(rotation, S), 8);
                                r = r.rel(rot(rotation, W), 3);
                                add(pieces, "roof_inner_corner", r, addRot(rotation, R_CCW90));
                            }

                            if (!MansionGrid.isHouse(grid, x, z - 1)) {
                                add(pieces, "roof_corner", q, addRot(rotation, R_180));
                            } else if (MansionGrid.isHouse(grid, x - 1, z - 1)) {
                                P r = q.rel(rot(rotation, S), 1);
                                add(pieces, "roof_inner_corner", r, addRot(rotation, R_180));
                            }
                        }
                    }
                }
            }
        }

        private void entrance(List<Piece> pieces, PlacementData d) {
            int west = rot(d.rotation, W);
            add(pieces, "entrance", d.pos.rel(west, 9), d.rotation);
            d.pos = d.pos.rel(rot(d.rotation, S), 16);
        }

        private void addWallFlat(List<Piece> pieces, PlacementData d) {
            add(pieces, d.wallType, d.pos.rel(rot(d.rotation, E), 7), d.rotation);
            d.pos = d.pos.rel(rot(d.rotation, S), 8);
        }

        private void addCorner(List<Piece> pieces, PlacementData d) {
            d.pos = d.pos.rel(rot(d.rotation, S), -1);
            add(pieces, "wall_corner", d.pos, d.rotation);
            d.pos = d.pos.rel(rot(d.rotation, S), -7);
            d.pos = d.pos.rel(rot(d.rotation, W), -6);
            d.rotation = addRot(d.rotation, R_CW90);
        }

        private void addInnerCorner(List<Piece> pieces, PlacementData d) {
            d.pos = d.pos.rel(rot(d.rotation, S), 6);
            d.pos = d.pos.rel(rot(d.rotation, E), 8);
            d.rotation = addRot(d.rotation, R_CCW90);
        }

        /** StructureTemplate.getZeroPositionWithTransform(pos, mirror, rotation, sizeX, sizeZ). */
        private static P zeroPosition(P pos, int mirror, int rotation, int sizeX, int sizeZ) {
            sizeX--;
            sizeZ--;
            int mx = mirror == BlockStateTable.FRONT_BACK ? sizeX : 0;
            int mz = mirror == BlockStateTable.LEFT_RIGHT ? sizeZ : 0;
            return switch (rotation) {
                case R_CCW90 -> pos.offset(mz, 0, sizeX - mx);
                case R_CW90 -> pos.offset(sizeZ - mz, 0, mx);
                case R_180 -> pos.offset(sizeX - mx, 0, sizeZ - mz);
                default -> pos.offset(mx, 0, mz);
            };
        }

        /** BlockPos.rotate(Rotation). */
        private static P rotatePos(P p, int rotation) {
            return switch (rotation) {
                case R_CW90 -> new P(-p.z(), p.y(), p.x());
                case R_180 -> new P(-p.x(), p.y(), -p.z());
                case R_CCW90 -> new P(p.z(), p.y(), -p.x());
                default -> p;
            };
        }

        private void addRoom1x1(List<Piece> pieces, P pos, int rotation, int dir, RoomKit kit) {
            int r = R_NONE;
            String name = kit.room1x1(random);
            if (dir != E) {
                if (dir == N) {
                    r = addRot(r, R_CCW90);
                } else if (dir == W) {
                    r = addRot(r, R_180);
                } else if (dir == S) {
                    r = addRot(r, R_CW90);
                } else {
                    name = kit.room1x1Secret(random);
                }
            }

            P zero = zeroPosition(new P(1, 0, 0), BlockStateTable.NONE, r, 7, 7);
            r = addRot(r, rotation);
            zero = rotatePos(zero, rotation);
            P p = pos.offset(zero.x(), 0, zero.z());
            add(pieces, name, p, r);
        }

        private void addRoom1x2(List<Piece> pieces, P pos, int rotation, int dir1, int dir2, RoomKit kit, boolean stairs) {
            if (dir2 == E && dir1 == S) {
                P p = pos.rel(rot(rotation, E), 1);
                add(pieces, kit.room1x2Side(random, stairs), p, rotation);
            } else if (dir2 == E && dir1 == N) {
                P p = pos.rel(rot(rotation, E), 1);
                p = p.rel(rot(rotation, S), 6);
                add(pieces, kit.room1x2Side(random, stairs), p, rotation, BlockStateTable.LEFT_RIGHT);
            } else if (dir2 == W && dir1 == N) {
                P p = pos.rel(rot(rotation, E), 7);
                p = p.rel(rot(rotation, S), 6);
                add(pieces, kit.room1x2Side(random, stairs), p, addRot(rotation, R_180));
            } else if (dir2 == W && dir1 == S) {
                P p = pos.rel(rot(rotation, E), 7);
                add(pieces, kit.room1x2Side(random, stairs), p, rotation, BlockStateTable.FRONT_BACK);
            } else if (dir2 == S && dir1 == E) {
                P p = pos.rel(rot(rotation, E), 1);
                add(pieces, kit.room1x2Side(random, stairs), p, addRot(rotation, R_CW90), BlockStateTable.LEFT_RIGHT);
            } else if (dir2 == S && dir1 == W) {
                P p = pos.rel(rot(rotation, E), 7);
                add(pieces, kit.room1x2Side(random, stairs), p, addRot(rotation, R_CW90));
            } else if (dir2 == N && dir1 == W) {
                P p = pos.rel(rot(rotation, E), 7);
                p = p.rel(rot(rotation, S), 6);
                add(pieces, kit.room1x2Side(random, stairs), p, addRot(rotation, R_CW90), BlockStateTable.FRONT_BACK);
            } else if (dir2 == N && dir1 == E) {
                P p = pos.rel(rot(rotation, E), 1);
                p = p.rel(rot(rotation, S), 6);
                add(pieces, kit.room1x2Side(random, stairs), p, addRot(rotation, R_CCW90));
            } else if (dir2 == S && dir1 == N) {
                P p = pos.rel(rot(rotation, E), 1);
                p = p.rel(rot(rotation, N), 8);
                add(pieces, kit.room1x2Front(random, stairs), p, rotation);
            } else if (dir2 == N && dir1 == S) {
                P p = pos.rel(rot(rotation, E), 7);
                p = p.rel(rot(rotation, S), 14);
                add(pieces, kit.room1x2Front(random, stairs), p, addRot(rotation, R_180));
            } else if (dir2 == W && dir1 == E) {
                P p = pos.rel(rot(rotation, E), 15);
                add(pieces, kit.room1x2Front(random, stairs), p, addRot(rotation, R_CW90));
            } else if (dir2 == E && dir1 == W) {
                P p = pos.rel(rot(rotation, W), 7);
                p = p.rel(rot(rotation, S), 6);
                add(pieces, kit.room1x2Front(random, stairs), p, addRot(rotation, R_CCW90));
            } else if (dir2 == UP && dir1 == E) {
                P p = pos.rel(rot(rotation, E), 15);
                add(pieces, kit.room1x2Secret(random), p, addRot(rotation, R_CW90));
            } else if (dir2 == UP && dir1 == S) {
                P p = pos.rel(rot(rotation, E), 1);
                p = p.rel(rot(rotation, N), 0);
                add(pieces, kit.room1x2Secret(random), p, rotation);
            }
        }

        private void addRoom2x2(List<Piece> pieces, P pos, int rotation, int dir1, int dir2, RoomKit kit) {
            int dx = 0;
            int dz = 0;
            int r = rotation;
            int mirror = BlockStateTable.NONE;
            if (dir2 == E && dir1 == S) {
                dx = -7;
            } else if (dir2 == E && dir1 == N) {
                dx = -7;
                dz = 6;
                mirror = BlockStateTable.LEFT_RIGHT;
            } else if (dir2 == N && dir1 == E) {
                dx = 1;
                dz = 14;
                r = addRot(rotation, R_CCW90);
            } else if (dir2 == N && dir1 == W) {
                dx = 7;
                dz = 14;
                r = addRot(rotation, R_CCW90);
                mirror = BlockStateTable.LEFT_RIGHT;
            } else if (dir2 == S && dir1 == W) {
                dx = 7;
                dz = -8;
                r = addRot(rotation, R_CW90);
            } else if (dir2 == S && dir1 == E) {
                dx = 1;
                dz = -8;
                r = addRot(rotation, R_CW90);
                mirror = BlockStateTable.LEFT_RIGHT;
            } else if (dir2 == W && dir1 == N) {
                dx = 15;
                dz = 6;
                r = addRot(rotation, R_180);
            } else if (dir2 == W && dir1 == S) {
                dx = 15;
                mirror = BlockStateTable.FRONT_BACK;
            }

            P p = pos.rel(rot(rotation, E), dx);
            p = p.rel(rot(rotation, S), dz);
            add(pieces, kit.room2x2(random), p, r, mirror);
        }

        private void addRoom2x2Secret(List<Piece> pieces, P pos, int rotation, RoomKit kit) {
            P p = pos.rel(rot(rotation, E), 1);
            add(pieces, kit.room2x2Secret(random), p, rotation, BlockStateTable.NONE);
        }
    }

    // ------------------------------------------------------------------ 조각

    static final class Piece extends TemplatePiece {
        Piece(StructureEngine e, String name, P pos, int rotation, int mirror) {
            super("minecraft:woodland_mansion/" + name, e.template("minecraft:woodland_mansion/" + name), makeSettings(rotation, mirror),
                pos.x(), pos.y(), pos.z());
        }

        static PlaceSettings makeSettings(int rotation, int mirror) {
            PlaceSettings st = new PlaceSettings();
            st.ignoreEntities = true;
            st.rotation = rotation;
            st.mirror = mirror;
            st.processors.add(Processors.BlockIgnore.structureBlock());
            return st;
        }

        @Override
        protected void handleDataMarker(String marker, int x, int y, int z, PostCtx c) {
            if (marker.startsWith("Chest")) {
                int rotation = settings.rotation;
                int state = c.blocks().defaultStateId("minecraft:chest");
                int facing = switch (marker) {
                    case "ChestWest" -> rot(rotation, W);
                    case "ChestEast" -> rot(rotation, E);
                    case "ChestSouth" -> rot(rotation, S);
                    case "ChestNorth" -> rot(rotation, N);
                    default -> NONE;
                };
                if (facing != NONE) {
                    state = c.table().with(state, "facing", new String[]{"north", "east", "south", "west"}[facing]);
                }
                // createChest(level, box, random, pos, loot, state) — state 가 null 이 아니므로 reorient 하지 않는다.
                createChestAt(c, c.writableBox, x, y, z, "minecraft:chests/woodland_mansion", state);
            } else {
                List<String> mobs = new ArrayList<>();
                switch (marker) {
                    case "Mage" -> mobs.add("minecraft:evoker");
                    case "Warrior" -> mobs.add("minecraft:vindicator");
                    case "Group of Allays" -> {
                        int n = c.regionRandom.nextInt(3) + 1;
                        for (int i = 0; i < n; i++) mobs.add("minecraft:allay");
                    }
                    default -> {
                        return;
                    }
                }
                for (String id : mobs) {
                    NbtCompound nbt = c.view.addEntity(id, x + 0.5, y, z + 0.5, 0.0F, 0.0F);
                    nbt.putByte("PersistenceRequired", (byte) 1);
                    c.view.setBlock(x, y, z, 0);
                }
            }
        }
    }
}
