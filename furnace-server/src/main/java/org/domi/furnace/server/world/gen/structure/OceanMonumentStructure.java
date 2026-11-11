// 바다 신전 — 바닐라 OceanMonumentStructure + OceanMonumentPieces(MonumentBuilding 과 방/날개/펜트하우스 하위 조각).
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class OceanMonumentStructure extends StructureType {

    /** BiomeTags.REQUIRED_OCEAN_MONUMENT_SURROUNDING 해석 결과. */
    private final Set<String> surrounding;

    public OceanMonumentStructure(StructureRegistry.Def def) {
        this.surrounding = StructureRegistry.resolveBiomes(new com.google.gson.JsonPrimitive("#minecraft:required_ocean_monument_surrounding"));
    }

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        // 바닐라: biomeSource.getBiomesWithin(청크 중심 x, 해수면, 청크 중심 z, 29, sampler) 전부가 태그에 속해야 한다.
        int x = c.middleBlockX();
        int z = c.middleBlockZ();
        int y = c.terrain.seaLevel();
        int r = 29;
        int qx0 = Math.floorDiv(x - r, 4), qy0 = Math.floorDiv(y - r, 4), qz0 = Math.floorDiv(z - r, 4);
        int qx1 = Math.floorDiv(x + r, 4), qy1 = Math.floorDiv(y + r, 4), qz1 = Math.floorDiv(z + r, 4);
        for (int qz = qz0; qz <= qz1; qz++) {
            for (int qx = qx0; qx <= qx1; qx++) {
                for (int qy = qy0; qy <= qy1; qy++) {
                    if (!surrounding.contains(c.terrain.biomeAt(qx * 4, qy * 4, qz * 4))) return Optional.empty();
                }
            }
        }
        return onTopOfChunkCenter(c, TerrainAccess.HM_OCEAN_FLOOR_WG, b -> {
            int px = c.minBlockX() - 29;
            int pz = c.minBlockZ() - 29;
            Direction dir = Direction.randomHorizontal(c.random);
            b.addPiece(new Building(c.random, px, pz, dir));
        });
    }

    // ------------------------------------------------------------------ 방 정의 (바닐라 MonumentRoomDefinition)

    static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    private static final int[] STEP_X = {0, 0, 0, 0, -1, 1};
    private static final int[] STEP_Y = {-1, 1, 0, 0, 0, 0};
    private static final int[] STEP_Z = {0, 0, -1, 1, 0, 0};

    static int roomIndex(int x, int y, int z) { return y * 25 + z * 5 + x; }

    private static final int GRID_SOURCE = roomIndex(2, 0, 0);
    private static final int GRID_TOP = roomIndex(2, 2, 0);
    private static final int GRID_LEFT_WING = roomIndex(0, 1, 0);
    private static final int GRID_RIGHT_WING = roomIndex(4, 1, 0);

    static final class RoomDef {
        final int index;
        final RoomDef[] conn = new RoomDef[6];
        final boolean[] open = new boolean[6];
        boolean claimed;
        boolean isSource;
        private int scanIndex;

        RoomDef(int index) { this.index = index; }

        void setConnection(int dir, RoomDef other) {
            conn[dir] = other;
            other.conn[dir ^ 1] = this;
        }

        void updateOpenings() {
            for (int i = 0; i < 6; i++) open[i] = conn[i] != null;
        }

        boolean findSource(int i) {
            if (isSource) return true;
            scanIndex = i;
            for (int d = 0; d < 6; d++) {
                if (conn[d] != null && open[d] && conn[d].scanIndex != i && conn[d].findSource(i)) return true;
            }
            return false;
        }

        boolean isSpecial() { return index >= 75; }

        int countOpenings() {
            int n = 0;
            for (int i = 0; i < 6; i++) if (open[i]) n++;
            return n;
        }
    }

    // ------------------------------------------------------------------ 상태 모음

    static final class St {
        final StructureEngine engine;
        final int PRISM, BRICKS, DARK, LANTERN, GOLD, WET_SPONGE, WATER;

        St(StructureEngine e) {
            engine = e;
            PRISM = e.state("minecraft:prismarine");
            BRICKS = e.state("minecraft:prismarine_bricks");
            DARK = e.state("minecraft:dark_prismarine");
            LANTERN = e.state("minecraft:sea_lantern");
            GOLD = e.state("minecraft:gold_block");
            WET_SPONGE = e.state("minecraft:wet_sponge");
            WATER = e.state("minecraft:water");
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

    // ------------------------------------------------------------------ 기반 조각 (바닐라 OceanMonumentPiece)

    abstract static class MonumentPiece extends StructurePiece {
        static final int SEA_LEVEL = 63;
        protected RoomDef room;

        /** OceanMonumentPiece(type, dir, genDepth, box) */
        protected MonumentPiece(Direction dir, BoundingBox box) {
            super(box, dir);
            this.genDepth = 1;   // 바닐라: 펜트하우스·날개 방은 1(건물은 생성자에서 0)
        }

        /** OceanMonumentPiece(type, genDepth, dir, roomDef, w, h, d) */
        protected MonumentPiece(Direction dir, RoomDef room, int w, int h, int d) {
            super(makeRoomBox(dir, room, w, h, d), dir);
            this.room = room;
            this.genDepth = 1;
        }

        private static BoundingBox makeRoomBox(Direction dir, RoomDef room, int w, int h, int d) {
            int idx = room.index;
            int x = idx % 5;
            int z = idx / 5 % 5;
            int y = idx / 25;
            BoundingBox box = makeBoundingBox(0, 0, 0, dir, w * 8, h * 4, d * 8);
            switch (dir) {
                case NORTH -> box.move(x * 8, y * 4, -(z + d) * 8 + 1);
                case SOUTH -> box.move(x * 8, y * 4, z * 8);
                case WEST -> box.move(-(z + d) * 8 + 1, y * 4, x * 8);
                default -> box.move(z * 8, y * 4, x * 8);
            }
            return box;
        }

        protected void fill(PostCtx c, BoundingBox box, int x0, int y0, int z0, int x1, int y1, int z1, int st) {
            generateBox(c, box, x0, y0, z0, x1, y1, z1, st, st, false);
        }

        private boolean isIceOrWater(PostCtx c, int state) {
            String n = c.table().name(state);
            return n.equals("minecraft:ice") || n.equals("minecraft:packed_ice") || n.equals("minecraft:blue_ice") || n.equals("minecraft:water");
        }

        /** generateWaterBox — 얼음/물은 건드리지 않고, 해수면 이상은 공기, 이하는 물로 채운다. */
        protected void waterBox(PostCtx c, BoundingBox box, int x0, int y0, int z0, int x1, int y1, int z1) {
            St s = St.of(c.engine);
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        int b = getBlock(c, x, y, z, box);
                        if (!isIceOrWater(c, b)) {
                            if (getWorldY(y) >= SEA_LEVEL && b != s.WATER) {
                                placeBlock(c, 0, x, y, z, box);
                            } else {
                                placeBlock(c, s.WATER, x, y, z, box);
                            }
                        }
                    }
                }
            }
        }

        protected void generateDefaultFloor(PostCtx c, BoundingBox box, int x, int z, boolean hasOpening) {
            St s = St.of(c.engine);
            if (hasOpening) {
                fill(c, box, x + 0, 0, z + 0, x + 2, 0, z + 8 - 1, s.PRISM);
                fill(c, box, x + 5, 0, z + 0, x + 8 - 1, 0, z + 8 - 1, s.PRISM);
                fill(c, box, x + 3, 0, z + 0, x + 4, 0, z + 2, s.PRISM);
                fill(c, box, x + 3, 0, z + 5, x + 4, 0, z + 8 - 1, s.PRISM);
                fill(c, box, x + 3, 0, z + 2, x + 4, 0, z + 2, s.BRICKS);
                fill(c, box, x + 3, 0, z + 5, x + 4, 0, z + 5, s.BRICKS);
                fill(c, box, x + 2, 0, z + 3, x + 2, 0, z + 4, s.BRICKS);
                fill(c, box, x + 5, 0, z + 3, x + 5, 0, z + 4, s.BRICKS);
            } else {
                fill(c, box, x + 0, 0, z + 0, x + 8 - 1, 0, z + 8 - 1, s.PRISM);
            }
        }

        /** 물(원천 블록)만 지정 상태로 치환한다. */
        protected void replaceWater(PostCtx c, BoundingBox box, int x0, int y0, int z0, int x1, int y1, int z1, int st) {
            St s = St.of(c.engine);
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        if (getBlock(c, x, y, z, box) == s.WATER) placeBlock(c, st, x, y, z, box);
                    }
                }
            }
        }

        /** OceanMonumentPiece.chunkIntersects — 두 로컬 모서리의 월드 XZ 범위가 상자와 겹치는가. */
        protected boolean chunkIntersects(BoundingBox box, int x0, int z0, int x1, int z1) {
            int wx0 = getWorldX(x0, z0), wz0 = getWorldZ(x0, z0);
            int wx1 = getWorldX(x1, z1), wz1 = getWorldZ(x1, z1);
            int minX = Math.min(wx0, wx1), minZ = Math.min(wz0, wz1);
            int maxX = Math.max(wx0, wx1), maxZ = Math.max(wz0, wz1);
            return box.maxX() >= minX && box.minX() <= maxX && box.maxZ() >= minZ && box.minZ() <= maxZ;
        }

        protected void spawnElderGuardian(PostCtx c, BoundingBox box, int x, int y, int z) {
            int wx = getWorldX(x, z), wy = getWorldY(y), wz = getWorldZ(x, z);
            if (box.isInside(wx, wy, wz)) {
                // 엘더 가디언: heal(maxHealth)=80, 생성자에서 setPersistenceRequired
                org.domi.furnace.server.net.io.nbt.NbtCompound nbt = c.view.addEntity("minecraft:elder_guardian", wx + 0.5, wy, wz + 0.5, 0.0F, 0.0F);
                nbt.putByte("PersistenceRequired", (byte) 1);
                nbt.put("Health", 80.0F);
            }
        }
    }

    // ------------------------------------------------------------------ 본체 (바닐라 MonumentBuilding)

    static final class Building extends MonumentPiece {

        @Override

        public String pieceType() { return "omb"; }

        private RoomDef sourceRoom;
        private RoomDef coreRoom;
        private final List<MonumentPiece> children = new ArrayList<>();

        Building(WorldgenRandom random, int x, int z, Direction dir) {
            super(dir, makeBoundingBox(x, 39, z, dir, 58, 23, 58));
            this.genDepth = 0;
            List<RoomDef> rooms = generateRoomGraph(random);
            sourceRoom.claimed = true;
            children.add(new EntryRoom(dir, sourceRoom));
            children.add(new CoreRoom(dir, coreRoom));
            List<RoomFit> fits = new ArrayList<>();
            fits.add(new RoomFit() {
                public boolean fits(RoomDef r) {
                    if (r.open[EAST] && !r.conn[EAST].claimed && r.open[UP] && !r.conn[UP].claimed) {
                        RoomDef e = r.conn[EAST];
                        return e.open[UP] && !e.conn[UP].claimed;
                    }
                    return false;
                }
                public MonumentPiece create(Direction d, RoomDef r, LegacyRandom rnd) {
                    r.claimed = true;
                    r.conn[EAST].claimed = true;
                    r.conn[UP].claimed = true;
                    r.conn[EAST].conn[UP].claimed = true;
                    return new DoubleXYRoom(d, r);
                }
            });
            fits.add(new RoomFit() {
                public boolean fits(RoomDef r) {
                    if (r.open[NORTH] && !r.conn[NORTH].claimed && r.open[UP] && !r.conn[UP].claimed) {
                        RoomDef n = r.conn[NORTH];
                        return n.open[UP] && !n.conn[UP].claimed;
                    }
                    return false;
                }
                public MonumentPiece create(Direction d, RoomDef r, LegacyRandom rnd) {
                    r.claimed = true;
                    r.conn[NORTH].claimed = true;
                    r.conn[UP].claimed = true;
                    r.conn[NORTH].conn[UP].claimed = true;
                    return new DoubleYZRoom(d, r);
                }
            });
            fits.add(new RoomFit() {
                public boolean fits(RoomDef r) { return r.open[NORTH] && !r.conn[NORTH].claimed; }
                public MonumentPiece create(Direction d, RoomDef r, LegacyRandom rnd) {
                    RoomDef r3 = r;
                    if (!r.open[NORTH] || r.conn[NORTH].claimed) r3 = r.conn[SOUTH];
                    r3.claimed = true;
                    r3.conn[NORTH].claimed = true;
                    return new DoubleZRoom(d, r3);
                }
            });
            fits.add(new RoomFit() {
                public boolean fits(RoomDef r) { return r.open[EAST] && !r.conn[EAST].claimed; }
                public MonumentPiece create(Direction d, RoomDef r, LegacyRandom rnd) {
                    r.claimed = true;
                    r.conn[EAST].claimed = true;
                    return new DoubleXRoom(d, r);
                }
            });
            fits.add(new RoomFit() {
                public boolean fits(RoomDef r) { return r.open[UP] && !r.conn[UP].claimed; }
                public MonumentPiece create(Direction d, RoomDef r, LegacyRandom rnd) {
                    r.claimed = true;
                    r.conn[UP].claimed = true;
                    return new DoubleYRoom(d, r);
                }
            });
            fits.add(new RoomFit() {
                public boolean fits(RoomDef r) {
                    return !r.open[WEST] && !r.open[EAST] && !r.open[NORTH] && !r.open[SOUTH] && !r.open[UP];
                }
                public MonumentPiece create(Direction d, RoomDef r, LegacyRandom rnd) {
                    r.claimed = true;
                    return new SimpleTopRoom(d, r);
                }
            });
            fits.add(new RoomFit() {
                public boolean fits(RoomDef r) { return true; }
                public MonumentPiece create(Direction d, RoomDef r, LegacyRandom rnd) {
                    r.claimed = true;
                    return new SimpleRoom(d, r, rnd);
                }
            });

            for (RoomDef room : rooms) {
                if (!room.claimed && !room.isSpecial()) {
                    for (RoomFit fit : fits) {
                        if (fit.fits(room)) {
                            children.add(fit.create(dir, room, random));
                            break;
                        }
                    }
                }
            }

            int px = getWorldX(9, 22), py = getWorldY(0), pz = getWorldZ(9, 22);
            for (MonumentPiece p : children) p.boundingBox().move(px, py, pz);

            BoundingBox wing1 = corners(1, 1, 1, 23, 8, 21);
            BoundingBox wing2 = corners(34, 1, 1, 56, 8, 21);
            BoundingBox top = corners(22, 13, 22, 35, 17, 35);
            int seed = random.nextInt();
            children.add(new WingRoom(dir, wing1, seed++));
            children.add(new WingRoom(dir, wing2, seed++));
            children.add(new Penthouse(dir, top));
        }

        /** BoundingBox.fromCorners(getWorldPos(x0,y0,z0), getWorldPos(x1,y1,z1)). */
        private BoundingBox corners(int x0, int y0, int z0, int x1, int y1, int z1) {
            return new BoundingBox(getWorldX(x0, z0), getWorldY(y0), getWorldZ(x0, z0), getWorldX(x1, z1), getWorldY(y1), getWorldZ(x1, z1));
        }

        interface RoomFit {
            boolean fits(RoomDef r);

            MonumentPiece create(Direction d, RoomDef r, LegacyRandom rnd);
        }

        private List<RoomDef> generateRoomGraph(LegacyRandom random) {
            RoomDef[] grid = new RoomDef[75];
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 4; z++) {
                    int idx = roomIndex(x, 0, z);
                    grid[idx] = new RoomDef(idx);
                }
            }
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 4; z++) {
                    int idx = roomIndex(x, 1, z);
                    grid[idx] = new RoomDef(idx);
                }
            }
            for (int x = 1; x < 4; x++) {
                for (int z = 0; z < 2; z++) {
                    int idx = roomIndex(x, 2, z);
                    grid[idx] = new RoomDef(idx);
                }
            }
            sourceRoom = grid[GRID_SOURCE];
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    for (int y = 0; y < 3; y++) {
                        int idx = roomIndex(x, y, z);
                        if (grid[idx] != null) {
                            for (int d = 0; d < 6; d++) {
                                int nx = x + STEP_X[d], ny = y + STEP_Y[d], nz = z + STEP_Z[d];
                                if (nx >= 0 && nx < 5 && nz >= 0 && nz < 5 && ny >= 0 && ny < 3) {
                                    int nidx = roomIndex(nx, ny, nz);
                                    if (grid[nidx] != null) {
                                        if (nz == z) grid[idx].setConnection(d, grid[nidx]);
                                        else grid[idx].setConnection(d ^ 1, grid[nidx]);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            RoomDef extraTop = new RoomDef(1003);
            RoomDef extraLeft = new RoomDef(1001);
            RoomDef extraRight = new RoomDef(1002);
            grid[GRID_TOP].setConnection(UP, extraTop);
            grid[GRID_LEFT_WING].setConnection(SOUTH, extraLeft);
            grid[GRID_RIGHT_WING].setConnection(SOUTH, extraRight);
            extraTop.claimed = true;
            extraLeft.claimed = true;
            extraRight.claimed = true;
            sourceRoom.isSource = true;
            coreRoom = grid[roomIndex(random.nextInt(4), 0, 2)];
            coreRoom.claimed = true;
            coreRoom.conn[EAST].claimed = true;
            coreRoom.conn[NORTH].claimed = true;
            coreRoom.conn[EAST].conn[NORTH].claimed = true;
            coreRoom.conn[UP].claimed = true;
            coreRoom.conn[EAST].conn[UP].claimed = true;
            coreRoom.conn[NORTH].conn[UP].claimed = true;
            coreRoom.conn[EAST].conn[NORTH].conn[UP].claimed = true;
            List<RoomDef> list = new ArrayList<>();
            for (RoomDef r : grid) {
                if (r != null) {
                    r.updateOpenings();
                    list.add(r);
                }
            }
            extraTop.updateOpenings();
            // Util.shuffle(list, random)
            for (int i = list.size(); i > 1; i--) {
                int j = random.nextInt(i);
                list.set(i - 1, list.set(j, list.get(i - 1)));
            }
            int counter = 1;
            for (RoomDef r : list) {
                int made = 0;
                int tries = 0;
                while (made < 2 && tries < 5) {
                    tries++;
                    int d = random.nextInt(6);
                    if (r.open[d]) {
                        int opp = d ^ 1;
                        r.open[d] = false;
                        r.conn[d].open[opp] = false;
                        if (r.findSource(counter++) && r.conn[d].findSource(counter++)) {
                            made++;
                        } else {
                            r.open[d] = true;
                            r.conn[d].open[opp] = true;
                        }
                    }
                }
            }
            list.add(extraTop);
            list.add(extraLeft);
            list.add(extraRight);
            return list;
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            int v7 = Math.max(MonumentPiece.SEA_LEVEL, 64) - boundingBox.minY();
            waterBox(c, box, 0, 0, 0, 58, v7, 58);
            generateWing(false, 0, c, box, s);
            generateWing(true, 33, c, box, s);
            generateCore(c, box, s);
            generateEntrance(c, box, s);
            generateTop(c, box, s);
            generateSide(c, box, s);
            generateLower(c, box, s);
            generateUpper(c, box, s);



            for (int v8 = 0; v8 < 7; v8++) {
                int v9 = 0;

                while (v9 < 7) {
                    if (v9 == 0 && v8 == 3) {
                        v9 = 6;
                    }

                    int v10 = v8 * 9;
                    int v11 = v9 * 9;

                    for (int v12 = 0; v12 < 4; v12++) {
                        for (int v13 = 0; v13 < 4; v13++) {
                            placeBlock(c, s.BRICKS, v10 + v12, 0, v11 + v13, box);
                            fillColumnDown(c, s.BRICKS, v10 + v12, -1, v11 + v13, box);
                        }
                    }

                    if (v8 != 0 && v8 != 6) {
                        v9 += 6;
                    } else {
                        v9++;
                    }
                }
            }

            for (int v14 = 0; v14 < 5; v14++) {
                waterBox(c, box, -1 - v14, 0 + v14 * 2, -1 - v14, -1 - v14, 23, 58 + v14);
                waterBox(c, box, 58 + v14, 0 + v14 * 2, -1 - v14, 58 + v14, 23, 58 + v14);
                waterBox(c, box, 0 - v14, 0 + v14 * 2, -1 - v14, 57 + v14, 23, -1 - v14);
                waterBox(c, box, 0 - v14, 0 + v14 * 2, 58 + v14, 57 + v14, 23, 58 + v14);
            }



            for (MonumentPiece p : children) {
                if (p.boundingBox().intersects(box)) p.postProcess(c);
            }
        }

        private void generateWing(boolean v0, int v1, PostCtx c, BoundingBox box, St s) {
            int v5 = 24;
            if (chunkIntersects(box, v1, 0, v1 + 23, 20)) {
                fill(c, box, v1 + 0, 0, 0, v1 + 24, 0, 20, s.PRISM);
                waterBox(c, box, v1 + 0, 1, 0, v1 + 24, 10, 20);

                for (int v6 = 0; v6 < 4; v6++) {
                    fill(c, box, v1 + v6, v6 + 1, v6, v1 + v6, v6 + 1, 20, s.BRICKS);
                    fill(c, box, v1 + v6 + 7, v6 + 5, v6 + 7, v1 + v6 + 7, v6 + 5, 20, s.BRICKS);
                    fill(c, box, v1 + 17 - v6, v6 + 5, v6 + 7, v1 + 17 - v6, v6 + 5, 20, s.BRICKS);
                    fill(c, box, v1 + 24 - v6, v6 + 1, v6, v1 + 24 - v6, v6 + 1, 20, s.BRICKS);
                    fill(c, box, v1 + v6 + 1, v6 + 1, v6, v1 + 23 - v6, v6 + 1, v6, s.BRICKS);
                    fill(c, box, v1 + v6 + 8, v6 + 5, v6 + 7, v1 + 16 - v6, v6 + 5, v6 + 7, s.BRICKS);
                }

                fill(c, box, v1 + 4, 4, 4, v1 + 6, 4, 20, s.PRISM);
                fill(c, box, v1 + 7, 4, 4, v1 + 17, 4, 6, s.PRISM);
                fill(c, box, v1 + 18, 4, 4, v1 + 20, 4, 20, s.PRISM);
                fill(c, box, v1 + 11, 8, 11, v1 + 13, 8, 20, s.PRISM);
                placeBlock(c, s.BRICKS, v1 + 12, 9, 12, box);
                placeBlock(c, s.BRICKS, v1 + 12, 9, 15, box);
                placeBlock(c, s.BRICKS, v1 + 12, 9, 18, box);
                int v7 = v1 + (v0 ? 19 : 5);
                int v8 = v1 + (v0 ? 5 : 19);

                for (int v9 = 20; v9 >= 5; v9 -= 3) {
                    placeBlock(c, s.BRICKS, v7, 5, v9, box);
                }

                for (int v10 = 19; v10 >= 7; v10 -= 3) {
                    placeBlock(c, s.BRICKS, v8, 5, v10, box);
                }

                for (int v11 = 0; v11 < 4; v11++) {
                    int v12 = v0 ? v1 + 24 - (17 - v11 * 3) : v1 + 17 - v11 * 3;
                    placeBlock(c, s.BRICKS, v12, 5, 5, box);
                }

                placeBlock(c, s.BRICKS, v8, 5, 5, box);
                fill(c, box, v1 + 11, 1, 12, v1 + 13, 7, 12, s.PRISM);
                fill(c, box, v1 + 12, 1, 11, v1 + 12, 7, 13, s.PRISM);
            }
        }

        private void generateCore(PostCtx c, BoundingBox box, St s) {
            if (chunkIntersects(box, 22, 5, 35, 17)) {
                waterBox(c, box, 25, 0, 0, 32, 8, 20);

                for (int v3 = 0; v3 < 4; v3++) {
                    fill(c, box, 24, 2, 5 + v3 * 4, 24, 4, 5 + v3 * 4, s.BRICKS);
                    fill(c, box, 22, 4, 5 + v3 * 4, 23, 4, 5 + v3 * 4, s.BRICKS);
                    placeBlock(c, s.BRICKS, 25, 5, 5 + v3 * 4, box);
                    placeBlock(c, s.BRICKS, 26, 6, 5 + v3 * 4, box);
                    placeBlock(c, s.LANTERN, 26, 5, 5 + v3 * 4, box);
                    fill(c, box, 33, 2, 5 + v3 * 4, 33, 4, 5 + v3 * 4, s.BRICKS);
                    fill(c, box, 34, 4, 5 + v3 * 4, 35, 4, 5 + v3 * 4, s.BRICKS);
                    placeBlock(c, s.BRICKS, 32, 5, 5 + v3 * 4, box);
                    placeBlock(c, s.BRICKS, 31, 6, 5 + v3 * 4, box);
                    placeBlock(c, s.LANTERN, 31, 5, 5 + v3 * 4, box);
                    fill(c, box, 27, 6, 5 + v3 * 4, 30, 6, 5 + v3 * 4, s.PRISM);
                }
            }
        }

        private void generateEntrance(PostCtx c, BoundingBox box, St s) {
            if (chunkIntersects(box, 15, 20, 42, 21)) {
                fill(c, box, 15, 0, 21, 42, 0, 21, s.PRISM);
                waterBox(c, box, 26, 1, 21, 31, 3, 21);
                fill(c, box, 21, 12, 21, 36, 12, 21, s.PRISM);
                fill(c, box, 17, 11, 21, 40, 11, 21, s.PRISM);
                fill(c, box, 16, 10, 21, 41, 10, 21, s.PRISM);
                fill(c, box, 15, 7, 21, 42, 9, 21, s.PRISM);
                fill(c, box, 16, 6, 21, 41, 6, 21, s.PRISM);
                fill(c, box, 17, 5, 21, 40, 5, 21, s.PRISM);
                fill(c, box, 21, 4, 21, 36, 4, 21, s.PRISM);
                fill(c, box, 22, 3, 21, 26, 3, 21, s.PRISM);
                fill(c, box, 31, 3, 21, 35, 3, 21, s.PRISM);
                fill(c, box, 23, 2, 21, 25, 2, 21, s.PRISM);
                fill(c, box, 32, 2, 21, 34, 2, 21, s.PRISM);
                fill(c, box, 28, 4, 20, 29, 4, 21, s.BRICKS);
                placeBlock(c, s.BRICKS, 27, 3, 21, box);
                placeBlock(c, s.BRICKS, 30, 3, 21, box);
                placeBlock(c, s.BRICKS, 26, 2, 21, box);
                placeBlock(c, s.BRICKS, 31, 2, 21, box);
                placeBlock(c, s.BRICKS, 25, 1, 21, box);
                placeBlock(c, s.BRICKS, 32, 1, 21, box);

                for (int v3 = 0; v3 < 7; v3++) {
                    placeBlock(c, s.DARK, 28 - v3, 6 + v3, 21, box);
                    placeBlock(c, s.DARK, 29 + v3, 6 + v3, 21, box);
                }

                for (int v4 = 0; v4 < 4; v4++) {
                    placeBlock(c, s.DARK, 28 - v4, 9 + v4, 21, box);
                    placeBlock(c, s.DARK, 29 + v4, 9 + v4, 21, box);
                }

                placeBlock(c, s.DARK, 28, 12, 21, box);
                placeBlock(c, s.DARK, 29, 12, 21, box);

                for (int v5 = 0; v5 < 3; v5++) {
                    placeBlock(c, s.DARK, 22 - v5 * 2, 8, 21, box);
                    placeBlock(c, s.DARK, 22 - v5 * 2, 9, 21, box);
                    placeBlock(c, s.DARK, 35 + v5 * 2, 8, 21, box);
                    placeBlock(c, s.DARK, 35 + v5 * 2, 9, 21, box);
                }

                waterBox(c, box, 15, 13, 21, 42, 15, 21);
                waterBox(c, box, 15, 1, 21, 15, 6, 21);
                waterBox(c, box, 16, 1, 21, 16, 5, 21);
                waterBox(c, box, 17, 1, 21, 20, 4, 21);
                waterBox(c, box, 21, 1, 21, 21, 3, 21);
                waterBox(c, box, 22, 1, 21, 22, 2, 21);
                waterBox(c, box, 23, 1, 21, 24, 1, 21);
                waterBox(c, box, 42, 1, 21, 42, 6, 21);
                waterBox(c, box, 41, 1, 21, 41, 5, 21);
                waterBox(c, box, 37, 1, 21, 40, 4, 21);
                waterBox(c, box, 36, 1, 21, 36, 3, 21);
                waterBox(c, box, 33, 1, 21, 34, 1, 21);
                waterBox(c, box, 35, 1, 21, 35, 2, 21);
            }
        }

        private void generateTop(PostCtx c, BoundingBox box, St s) {
            if (chunkIntersects(box, 21, 21, 36, 36)) {
                fill(c, box, 21, 0, 22, 36, 0, 36, s.PRISM);
                waterBox(c, box, 21, 1, 22, 36, 23, 36);

                for (int v3 = 0; v3 < 4; v3++) {
                    fill(c, box, 21 + v3, 13 + v3, 21 + v3, 36 - v3, 13 + v3, 21 + v3, s.BRICKS);
                    fill(c, box, 21 + v3, 13 + v3, 36 - v3, 36 - v3, 13 + v3, 36 - v3, s.BRICKS);
                    fill(c, box, 21 + v3, 13 + v3, 22 + v3, 21 + v3, 13 + v3, 35 - v3, s.BRICKS);
                    fill(c, box, 36 - v3, 13 + v3, 22 + v3, 36 - v3, 13 + v3, 35 - v3, s.BRICKS);
                }

                fill(c, box, 25, 16, 25, 32, 16, 32, s.PRISM);
                fill(c, box, 25, 17, 25, 25, 19, 25, s.BRICKS);
                fill(c, box, 32, 17, 25, 32, 19, 25, s.BRICKS);
                fill(c, box, 25, 17, 32, 25, 19, 32, s.BRICKS);
                fill(c, box, 32, 17, 32, 32, 19, 32, s.BRICKS);
                placeBlock(c, s.BRICKS, 26, 20, 26, box);
                placeBlock(c, s.BRICKS, 27, 21, 27, box);
                placeBlock(c, s.LANTERN, 27, 20, 27, box);
                placeBlock(c, s.BRICKS, 26, 20, 31, box);
                placeBlock(c, s.BRICKS, 27, 21, 30, box);
                placeBlock(c, s.LANTERN, 27, 20, 30, box);
                placeBlock(c, s.BRICKS, 31, 20, 31, box);
                placeBlock(c, s.BRICKS, 30, 21, 30, box);
                placeBlock(c, s.LANTERN, 30, 20, 30, box);
                placeBlock(c, s.BRICKS, 31, 20, 26, box);
                placeBlock(c, s.BRICKS, 30, 21, 27, box);
                placeBlock(c, s.LANTERN, 30, 20, 27, box);
                fill(c, box, 28, 21, 27, 29, 21, 27, s.PRISM);
                fill(c, box, 27, 21, 28, 27, 21, 29, s.PRISM);
                fill(c, box, 28, 21, 30, 29, 21, 30, s.PRISM);
                fill(c, box, 30, 21, 28, 30, 21, 29, s.PRISM);
            }
        }

        private void generateSide(PostCtx c, BoundingBox box, St s) {
            if (chunkIntersects(box, 0, 21, 6, 58)) {
                fill(c, box, 0, 0, 21, 6, 0, 57, s.PRISM);
                waterBox(c, box, 0, 1, 21, 6, 7, 57);
                fill(c, box, 4, 4, 21, 6, 4, 53, s.PRISM);

                for (int v3 = 0; v3 < 4; v3++) {
                    fill(c, box, v3, v3 + 1, 21, v3, v3 + 1, 57 - v3, s.BRICKS);
                }

                for (int v4 = 23; v4 < 53; v4 += 3) {
                    placeBlock(c, s.BRICKS, 5, 5, v4, box);
                }

                placeBlock(c, s.BRICKS, 5, 5, 52, box);

                for (int v5 = 0; v5 < 4; v5++) {
                    fill(c, box, v5, v5 + 1, 21, v5, v5 + 1, 57 - v5, s.BRICKS);
                }

                fill(c, box, 4, 1, 52, 6, 3, 52, s.PRISM);
                fill(c, box, 5, 1, 51, 5, 3, 53, s.PRISM);
            }

            if (chunkIntersects(box, 51, 21, 58, 58)) {
                fill(c, box, 51, 0, 21, 57, 0, 57, s.PRISM);
                waterBox(c, box, 51, 1, 21, 57, 7, 57);
                fill(c, box, 51, 4, 21, 53, 4, 53, s.PRISM);

                for (int v6 = 0; v6 < 4; v6++) {
                    fill(c, box, 57 - v6, v6 + 1, 21, 57 - v6, v6 + 1, 57 - v6, s.BRICKS);
                }

                for (int v7 = 23; v7 < 53; v7 += 3) {
                    placeBlock(c, s.BRICKS, 52, 5, v7, box);
                }

                placeBlock(c, s.BRICKS, 52, 5, 52, box);
                fill(c, box, 51, 1, 52, 53, 3, 52, s.PRISM);
                fill(c, box, 52, 1, 51, 52, 3, 53, s.PRISM);
            }

            if (chunkIntersects(box, 0, 51, 57, 57)) {
                fill(c, box, 7, 0, 51, 50, 0, 57, s.PRISM);
                waterBox(c, box, 7, 1, 51, 50, 10, 57);

                for (int v8 = 0; v8 < 4; v8++) {
                    fill(c, box, v8 + 1, v8 + 1, 57 - v8, 56 - v8, v8 + 1, 57 - v8, s.BRICKS);
                }
            }
        }

        private void generateLower(PostCtx c, BoundingBox box, St s) {
            if (chunkIntersects(box, 7, 21, 13, 50)) {
                fill(c, box, 7, 0, 21, 13, 0, 50, s.PRISM);
                waterBox(c, box, 7, 1, 21, 13, 10, 50);
                fill(c, box, 11, 8, 21, 13, 8, 53, s.PRISM);

                for (int v3 = 0; v3 < 4; v3++) {
                    fill(c, box, v3 + 7, v3 + 5, 21, v3 + 7, v3 + 5, 54, s.BRICKS);
                }

                for (int v4 = 21; v4 <= 45; v4 += 3) {
                    placeBlock(c, s.BRICKS, 12, 9, v4, box);
                }
            }

            if (chunkIntersects(box, 44, 21, 50, 54)) {
                fill(c, box, 44, 0, 21, 50, 0, 50, s.PRISM);
                waterBox(c, box, 44, 1, 21, 50, 10, 50);
                fill(c, box, 44, 8, 21, 46, 8, 53, s.PRISM);

                for (int v5 = 0; v5 < 4; v5++) {
                    fill(c, box, 50 - v5, v5 + 5, 21, 50 - v5, v5 + 5, 54, s.BRICKS);
                }

                for (int v6 = 21; v6 <= 45; v6 += 3) {
                    placeBlock(c, s.BRICKS, 45, 9, v6, box);
                }
            }

            if (chunkIntersects(box, 8, 44, 49, 54)) {
                fill(c, box, 14, 0, 44, 43, 0, 50, s.PRISM);
                waterBox(c, box, 14, 1, 44, 43, 10, 50);

                for (int v7 = 12; v7 <= 45; v7 += 3) {
                    placeBlock(c, s.BRICKS, v7, 9, 45, box);
                    placeBlock(c, s.BRICKS, v7, 9, 52, box);
                    if (v7 == 12 || v7 == 18 || v7 == 24 || v7 == 33 || v7 == 39 || v7 == 45) {
                        placeBlock(c, s.BRICKS, v7, 9, 47, box);
                        placeBlock(c, s.BRICKS, v7, 9, 50, box);
                        placeBlock(c, s.BRICKS, v7, 10, 45, box);
                        placeBlock(c, s.BRICKS, v7, 10, 46, box);
                        placeBlock(c, s.BRICKS, v7, 10, 51, box);
                        placeBlock(c, s.BRICKS, v7, 10, 52, box);
                        placeBlock(c, s.BRICKS, v7, 11, 47, box);
                        placeBlock(c, s.BRICKS, v7, 11, 50, box);
                        placeBlock(c, s.BRICKS, v7, 12, 48, box);
                        placeBlock(c, s.BRICKS, v7, 12, 49, box);
                    }
                }

                for (int v8 = 0; v8 < 3; v8++) {
                    fill(c, box, 8 + v8, 5 + v8, 54, 49 - v8, 5 + v8, 54, s.PRISM);
                }

                fill(c, box, 11, 8, 54, 46, 8, 54, s.BRICKS);
                fill(c, box, 14, 8, 44, 43, 8, 53, s.PRISM);
            }
        }

        private void generateUpper(PostCtx c, BoundingBox box, St s) {
            if (chunkIntersects(box, 14, 21, 20, 43)) {
                fill(c, box, 14, 0, 21, 20, 0, 43, s.PRISM);
                waterBox(c, box, 14, 1, 22, 20, 14, 43);
                fill(c, box, 18, 12, 22, 20, 12, 39, s.PRISM);
                fill(c, box, 18, 12, 21, 20, 12, 21, s.BRICKS);

                for (int v3 = 0; v3 < 4; v3++) {
                    fill(c, box, v3 + 14, v3 + 9, 21, v3 + 14, v3 + 9, 43 - v3, s.BRICKS);
                }

                for (int v4 = 23; v4 <= 39; v4 += 3) {
                    placeBlock(c, s.BRICKS, 19, 13, v4, box);
                }
            }

            if (chunkIntersects(box, 37, 21, 43, 43)) {
                fill(c, box, 37, 0, 21, 43, 0, 43, s.PRISM);
                waterBox(c, box, 37, 1, 22, 43, 14, 43);
                fill(c, box, 37, 12, 22, 39, 12, 39, s.PRISM);
                fill(c, box, 37, 12, 21, 39, 12, 21, s.BRICKS);

                for (int v5 = 0; v5 < 4; v5++) {
                    fill(c, box, 43 - v5, v5 + 9, 21, 43 - v5, v5 + 9, 43 - v5, s.BRICKS);
                }

                for (int v6 = 23; v6 <= 39; v6 += 3) {
                    placeBlock(c, s.BRICKS, 38, 13, v6, box);
                }
            }

            if (chunkIntersects(box, 15, 37, 42, 43)) {
                fill(c, box, 21, 0, 37, 36, 0, 43, s.PRISM);
                waterBox(c, box, 21, 1, 37, 36, 14, 43);
                fill(c, box, 21, 12, 37, 36, 12, 39, s.PRISM);

                for (int v7 = 0; v7 < 4; v7++) {
                    fill(c, box, 15 + v7, v7 + 9, 43 - v7, 42 - v7, v7 + 9, 43 - v7, s.BRICKS);
                }

                for (int v8 = 21; v8 <= 36; v8 += 3) {
                    placeBlock(c, s.BRICKS, v8, 13, 38, box);
                }
            }
        }

    }

    static final class EntryRoom extends MonumentPiece {

        @Override

        public String pieceType() { return "omentry"; }


        EntryRoom(Direction d, RoomDef r) {
            super(d, r, 1, 1, 1);
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            fill(c, box, 0, 3, 0, 2, 3, 7, s.BRICKS);
            fill(c, box, 5, 3, 0, 7, 3, 7, s.BRICKS);
            fill(c, box, 0, 2, 0, 1, 2, 7, s.BRICKS);
            fill(c, box, 6, 2, 0, 7, 2, 7, s.BRICKS);
            fill(c, box, 0, 1, 0, 0, 1, 7, s.BRICKS);
            fill(c, box, 7, 1, 0, 7, 1, 7, s.BRICKS);
            fill(c, box, 0, 1, 7, 7, 3, 7, s.BRICKS);
            fill(c, box, 1, 1, 0, 2, 3, 0, s.BRICKS);
            fill(c, box, 5, 1, 0, 6, 3, 0, s.BRICKS);
            if (room.open[NORTH]) {
                waterBox(c, box, 3, 1, 7, 4, 2, 7);
            }

            if (room.open[WEST]) {
                waterBox(c, box, 0, 1, 3, 1, 2, 4);
            }

            if (room.open[EAST]) {
                waterBox(c, box, 6, 1, 3, 7, 2, 4);
            }
        }
    }

    static final class CoreRoom extends MonumentPiece {

        @Override

        public String pieceType() { return "omcr"; }


        CoreRoom(Direction d, RoomDef r) {
            super(d, r, 2, 2, 2);
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            replaceWater(c, box, 1, 8, 0, 14, 8, 14, s.PRISM);
            int v7 = 7;
            int v8 = s.BRICKS;
            fill(c, box, 0, 7, 0, 0, 7, 15, v8);
            fill(c, box, 15, 7, 0, 15, 7, 15, v8);
            fill(c, box, 1, 7, 0, 15, 7, 0, v8);
            fill(c, box, 1, 7, 15, 14, 7, 15, v8);

            for (int v9 = 1; v9 <= 6; v9++) {
                v8 = s.BRICKS;
                if (v9 == 2 || v9 == 6) {
                    v8 = s.PRISM;
                }

                for (int v11 = 0; v11 <= 15; v11 += 15) {
                    fill(c, box, v11, v9, 0, v11, v9, 1, v8);
                    fill(c, box, v11, v9, 6, v11, v9, 9, v8);
                    fill(c, box, v11, v9, 14, v11, v9, 15, v8);
                }

                fill(c, box, 1, v9, 0, 1, v9, 0, v8);
                fill(c, box, 6, v9, 0, 9, v9, 0, v8);
                fill(c, box, 14, v9, 0, 14, v9, 0, v8);
                fill(c, box, 1, v9, 15, 14, v9, 15, v8);
            }

            fill(c, box, 6, 3, 6, 9, 6, 9, s.DARK);
            fill(c, box, 7, 4, 7, 8, 5, 8, s.GOLD);

            for (int v12 = 3; v12 <= 6; v12 += 3) {
                for (int v13 = 6; v13 <= 9; v13 += 3) {
                    placeBlock(c, s.LANTERN, v13, v12, 6, box);
                    placeBlock(c, s.LANTERN, v13, v12, 9, box);
                }
            }

            fill(c, box, 5, 1, 6, 5, 2, 6, s.BRICKS);
            fill(c, box, 5, 1, 9, 5, 2, 9, s.BRICKS);
            fill(c, box, 10, 1, 6, 10, 2, 6, s.BRICKS);
            fill(c, box, 10, 1, 9, 10, 2, 9, s.BRICKS);
            fill(c, box, 6, 1, 5, 6, 2, 5, s.BRICKS);
            fill(c, box, 9, 1, 5, 9, 2, 5, s.BRICKS);
            fill(c, box, 6, 1, 10, 6, 2, 10, s.BRICKS);
            fill(c, box, 9, 1, 10, 9, 2, 10, s.BRICKS);
            fill(c, box, 5, 2, 5, 5, 6, 5, s.BRICKS);
            fill(c, box, 5, 2, 10, 5, 6, 10, s.BRICKS);
            fill(c, box, 10, 2, 5, 10, 6, 5, s.BRICKS);
            fill(c, box, 10, 2, 10, 10, 6, 10, s.BRICKS);
            fill(c, box, 5, 7, 1, 5, 7, 6, s.BRICKS);
            fill(c, box, 10, 7, 1, 10, 7, 6, s.BRICKS);
            fill(c, box, 5, 7, 9, 5, 7, 14, s.BRICKS);
            fill(c, box, 10, 7, 9, 10, 7, 14, s.BRICKS);
            fill(c, box, 1, 7, 5, 6, 7, 5, s.BRICKS);
            fill(c, box, 1, 7, 10, 6, 7, 10, s.BRICKS);
            fill(c, box, 9, 7, 5, 14, 7, 5, s.BRICKS);
            fill(c, box, 9, 7, 10, 14, 7, 10, s.BRICKS);
            fill(c, box, 2, 1, 2, 2, 1, 3, s.BRICKS);
            fill(c, box, 3, 1, 2, 3, 1, 2, s.BRICKS);
            fill(c, box, 13, 1, 2, 13, 1, 3, s.BRICKS);
            fill(c, box, 12, 1, 2, 12, 1, 2, s.BRICKS);
            fill(c, box, 2, 1, 12, 2, 1, 13, s.BRICKS);
            fill(c, box, 3, 1, 13, 3, 1, 13, s.BRICKS);
            fill(c, box, 13, 1, 12, 13, 1, 13, s.BRICKS);
            fill(c, box, 12, 1, 13, 12, 1, 13, s.BRICKS);
        }
    }

    static final class DoubleXRoom extends MonumentPiece {

        @Override

        public String pieceType() { return "omdxr"; }


        DoubleXRoom(Direction d, RoomDef r) {
            super(d, r, 2, 1, 1);
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            RoomDef v7 = room.conn[EAST];
            RoomDef v8 = room;
            if (room.index / 25 > 0) {
                generateDefaultFloor(c, box, 8, 0, v7.open[DOWN]);
                generateDefaultFloor(c, box, 0, 0, v8.open[DOWN]);
            }

            if (v8.conn[UP] == null) {
                replaceWater(c, box, 1, 4, 1, 7, 4, 6, s.PRISM);
            }

            if (v7.conn[UP] == null) {
                replaceWater(c, box, 8, 4, 1, 14, 4, 6, s.PRISM);
            }

            fill(c, box, 0, 3, 0, 0, 3, 7, s.BRICKS);
            fill(c, box, 15, 3, 0, 15, 3, 7, s.BRICKS);
            fill(c, box, 1, 3, 0, 15, 3, 0, s.BRICKS);
            fill(c, box, 1, 3, 7, 14, 3, 7, s.BRICKS);
            fill(c, box, 0, 2, 0, 0, 2, 7, s.PRISM);
            fill(c, box, 15, 2, 0, 15, 2, 7, s.PRISM);
            fill(c, box, 1, 2, 0, 15, 2, 0, s.PRISM);
            fill(c, box, 1, 2, 7, 14, 2, 7, s.PRISM);
            fill(c, box, 0, 1, 0, 0, 1, 7, s.BRICKS);
            fill(c, box, 15, 1, 0, 15, 1, 7, s.BRICKS);
            fill(c, box, 1, 1, 0, 15, 1, 0, s.BRICKS);
            fill(c, box, 1, 1, 7, 14, 1, 7, s.BRICKS);
            fill(c, box, 5, 1, 0, 10, 1, 4, s.BRICKS);
            fill(c, box, 6, 2, 0, 9, 2, 3, s.PRISM);
            fill(c, box, 5, 3, 0, 10, 3, 4, s.BRICKS);
            placeBlock(c, s.LANTERN, 6, 2, 3, box);
            placeBlock(c, s.LANTERN, 9, 2, 3, box);
            if (v8.open[SOUTH]) {
                waterBox(c, box, 3, 1, 0, 4, 2, 0);
            }

            if (v8.open[NORTH]) {
                waterBox(c, box, 3, 1, 7, 4, 2, 7);
            }

            if (v8.open[WEST]) {
                waterBox(c, box, 0, 1, 3, 0, 2, 4);
            }

            if (v7.open[SOUTH]) {
                waterBox(c, box, 11, 1, 0, 12, 2, 0);
            }

            if (v7.open[NORTH]) {
                waterBox(c, box, 11, 1, 7, 12, 2, 7);
            }

            if (v7.open[EAST]) {
                waterBox(c, box, 15, 1, 3, 15, 2, 4);
            }
        }
    }

    static final class DoubleXYRoom extends MonumentPiece {

        @Override

        public String pieceType() { return "omdxyr"; }


        DoubleXYRoom(Direction d, RoomDef r) {
            super(d, r, 2, 2, 1);
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            RoomDef v7 = room.conn[EAST];
            RoomDef v8 = room;
            RoomDef v9 = v8.conn[UP];
            RoomDef v10 = v7.conn[UP];
            if (room.index / 25 > 0) {
                generateDefaultFloor(c, box, 8, 0, v7.open[DOWN]);
                generateDefaultFloor(c, box, 0, 0, v8.open[DOWN]);
            }

            if (v9.conn[UP] == null) {
                replaceWater(c, box, 1, 8, 1, 7, 8, 6, s.PRISM);
            }

            if (v10.conn[UP] == null) {
                replaceWater(c, box, 8, 8, 1, 14, 8, 6, s.PRISM);
            }

            for (int v11 = 1; v11 <= 7; v11++) {
                int v12 = s.BRICKS;
                if (v11 == 2 || v11 == 6) {
                    v12 = s.PRISM;
                }

                fill(c, box, 0, v11, 0, 0, v11, 7, v12);
                fill(c, box, 15, v11, 0, 15, v11, 7, v12);
                fill(c, box, 1, v11, 0, 15, v11, 0, v12);
                fill(c, box, 1, v11, 7, 14, v11, 7, v12);
            }

            fill(c, box, 2, 1, 3, 2, 7, 4, s.BRICKS);
            fill(c, box, 3, 1, 2, 4, 7, 2, s.BRICKS);
            fill(c, box, 3, 1, 5, 4, 7, 5, s.BRICKS);
            fill(c, box, 13, 1, 3, 13, 7, 4, s.BRICKS);
            fill(c, box, 11, 1, 2, 12, 7, 2, s.BRICKS);
            fill(c, box, 11, 1, 5, 12, 7, 5, s.BRICKS);
            fill(c, box, 5, 1, 3, 5, 3, 4, s.BRICKS);
            fill(c, box, 10, 1, 3, 10, 3, 4, s.BRICKS);
            fill(c, box, 5, 7, 2, 10, 7, 5, s.BRICKS);
            fill(c, box, 5, 5, 2, 5, 7, 2, s.BRICKS);
            fill(c, box, 10, 5, 2, 10, 7, 2, s.BRICKS);
            fill(c, box, 5, 5, 5, 5, 7, 5, s.BRICKS);
            fill(c, box, 10, 5, 5, 10, 7, 5, s.BRICKS);
            placeBlock(c, s.BRICKS, 6, 6, 2, box);
            placeBlock(c, s.BRICKS, 9, 6, 2, box);
            placeBlock(c, s.BRICKS, 6, 6, 5, box);
            placeBlock(c, s.BRICKS, 9, 6, 5, box);
            fill(c, box, 5, 4, 3, 6, 4, 4, s.BRICKS);
            fill(c, box, 9, 4, 3, 10, 4, 4, s.BRICKS);
            placeBlock(c, s.LANTERN, 5, 4, 2, box);
            placeBlock(c, s.LANTERN, 5, 4, 5, box);
            placeBlock(c, s.LANTERN, 10, 4, 2, box);
            placeBlock(c, s.LANTERN, 10, 4, 5, box);
            if (v8.open[SOUTH]) {
                waterBox(c, box, 3, 1, 0, 4, 2, 0);
            }

            if (v8.open[NORTH]) {
                waterBox(c, box, 3, 1, 7, 4, 2, 7);
            }

            if (v8.open[WEST]) {
                waterBox(c, box, 0, 1, 3, 0, 2, 4);
            }

            if (v7.open[SOUTH]) {
                waterBox(c, box, 11, 1, 0, 12, 2, 0);
            }

            if (v7.open[NORTH]) {
                waterBox(c, box, 11, 1, 7, 12, 2, 7);
            }

            if (v7.open[EAST]) {
                waterBox(c, box, 15, 1, 3, 15, 2, 4);
            }

            if (v9.open[SOUTH]) {
                waterBox(c, box, 3, 5, 0, 4, 6, 0);
            }

            if (v9.open[NORTH]) {
                waterBox(c, box, 3, 5, 7, 4, 6, 7);
            }

            if (v9.open[WEST]) {
                waterBox(c, box, 0, 5, 3, 0, 6, 4);
            }

            if (v10.open[SOUTH]) {
                waterBox(c, box, 11, 5, 0, 12, 6, 0);
            }

            if (v10.open[NORTH]) {
                waterBox(c, box, 11, 5, 7, 12, 6, 7);
            }

            if (v10.open[EAST]) {
                waterBox(c, box, 15, 5, 3, 15, 6, 4);
            }
        }
    }

    static final class DoubleYRoom extends MonumentPiece {

        @Override

        public String pieceType() { return "omdyr"; }


        DoubleYRoom(Direction d, RoomDef r) {
            super(d, r, 1, 2, 1);
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            if (room.index / 25 > 0) {
                generateDefaultFloor(c, box, 0, 0, room.open[DOWN]);
            }

            RoomDef v7 = room.conn[UP];
            if (v7.conn[UP] == null) {
                replaceWater(c, box, 1, 8, 1, 6, 8, 6, s.PRISM);
            }

            fill(c, box, 0, 4, 0, 0, 4, 7, s.BRICKS);
            fill(c, box, 7, 4, 0, 7, 4, 7, s.BRICKS);
            fill(c, box, 1, 4, 0, 6, 4, 0, s.BRICKS);
            fill(c, box, 1, 4, 7, 6, 4, 7, s.BRICKS);
            fill(c, box, 2, 4, 1, 2, 4, 2, s.BRICKS);
            fill(c, box, 1, 4, 2, 1, 4, 2, s.BRICKS);
            fill(c, box, 5, 4, 1, 5, 4, 2, s.BRICKS);
            fill(c, box, 6, 4, 2, 6, 4, 2, s.BRICKS);
            fill(c, box, 2, 4, 5, 2, 4, 6, s.BRICKS);
            fill(c, box, 1, 4, 5, 1, 4, 5, s.BRICKS);
            fill(c, box, 5, 4, 5, 5, 4, 6, s.BRICKS);
            fill(c, box, 6, 4, 5, 6, 4, 5, s.BRICKS);
            RoomDef v8 = room;

            for (int v9 = 1; v9 <= 5; v9 += 4) {
                int v10 = 0;
                if (v8.open[SOUTH]) {
                    fill(c, box, 2, v9, v10, 2, v9 + 2, v10, s.BRICKS);
                    fill(c, box, 5, v9, v10, 5, v9 + 2, v10, s.BRICKS);
                    fill(c, box, 3, v9 + 2, v10, 4, v9 + 2, v10, s.BRICKS);
                } else {
                    fill(c, box, 0, v9, v10, 7, v9 + 2, v10, s.BRICKS);
                    fill(c, box, 0, v9 + 1, v10, 7, v9 + 1, v10, s.PRISM);
                }

                int var13 = 7;
                if (v8.open[NORTH]) {
                    fill(c, box, 2, v9, var13, 2, v9 + 2, var13, s.BRICKS);
                    fill(c, box, 5, v9, var13, 5, v9 + 2, var13, s.BRICKS);
                    fill(c, box, 3, v9 + 2, var13, 4, v9 + 2, var13, s.BRICKS);
                } else {
                    fill(c, box, 0, v9, var13, 7, v9 + 2, var13, s.BRICKS);
                    fill(c, box, 0, v9 + 1, var13, 7, v9 + 1, var13, s.PRISM);
                }

                int v11 = 0;
                if (v8.open[WEST]) {
                    fill(c, box, v11, v9, 2, v11, v9 + 2, 2, s.BRICKS);
                    fill(c, box, v11, v9, 5, v11, v9 + 2, 5, s.BRICKS);
                    fill(c, box, v11, v9 + 2, 3, v11, v9 + 2, 4, s.BRICKS);
                } else {
                    fill(c, box, v11, v9, 0, v11, v9 + 2, 7, s.BRICKS);
                    fill(c, box, v11, v9 + 1, 0, v11, v9 + 1, 7, s.PRISM);
                }

                int var14 = 7;
                if (v8.open[EAST]) {
                    fill(c, box, var14, v9, 2, var14, v9 + 2, 2, s.BRICKS);
                    fill(c, box, var14, v9, 5, var14, v9 + 2, 5, s.BRICKS);
                    fill(c, box, var14, v9 + 2, 3, var14, v9 + 2, 4, s.BRICKS);
                } else {
                    fill(c, box, var14, v9, 0, var14, v9 + 2, 7, s.BRICKS);
                    fill(c, box, var14, v9 + 1, 0, var14, v9 + 1, 7, s.PRISM);
                }

                v8 = v7;
            }
        }
    }

    static final class DoubleYZRoom extends MonumentPiece {

        @Override

        public String pieceType() { return "omdyzr"; }


        DoubleYZRoom(Direction d, RoomDef r) {
            super(d, r, 1, 2, 2);
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            RoomDef v7 = room.conn[NORTH];
            RoomDef v8 = room;
            RoomDef v9 = v7.conn[UP];
            RoomDef v10 = v8.conn[UP];
            if (room.index / 25 > 0) {
                generateDefaultFloor(c, box, 0, 8, v7.open[DOWN]);
                generateDefaultFloor(c, box, 0, 0, v8.open[DOWN]);
            }

            if (v10.conn[UP] == null) {
                replaceWater(c, box, 1, 8, 1, 6, 8, 7, s.PRISM);
            }

            if (v9.conn[UP] == null) {
                replaceWater(c, box, 1, 8, 8, 6, 8, 14, s.PRISM);
            }

            for (int v11 = 1; v11 <= 7; v11++) {
                int v12 = s.BRICKS;
                if (v11 == 2 || v11 == 6) {
                    v12 = s.PRISM;
                }

                fill(c, box, 0, v11, 0, 0, v11, 15, v12);
                fill(c, box, 7, v11, 0, 7, v11, 15, v12);
                fill(c, box, 1, v11, 0, 6, v11, 0, v12);
                fill(c, box, 1, v11, 15, 6, v11, 15, v12);
            }

            for (int v13 = 1; v13 <= 7; v13++) {
                int v14 = s.DARK;
                if (v13 == 2 || v13 == 6) {
                    v14 = s.LANTERN;
                }

                fill(c, box, 3, v13, 7, 4, v13, 8, v14);
            }

            if (v8.open[SOUTH]) {
                waterBox(c, box, 3, 1, 0, 4, 2, 0);
            }

            if (v8.open[EAST]) {
                waterBox(c, box, 7, 1, 3, 7, 2, 4);
            }

            if (v8.open[WEST]) {
                waterBox(c, box, 0, 1, 3, 0, 2, 4);
            }

            if (v7.open[NORTH]) {
                waterBox(c, box, 3, 1, 15, 4, 2, 15);
            }

            if (v7.open[WEST]) {
                waterBox(c, box, 0, 1, 11, 0, 2, 12);
            }

            if (v7.open[EAST]) {
                waterBox(c, box, 7, 1, 11, 7, 2, 12);
            }

            if (v10.open[SOUTH]) {
                waterBox(c, box, 3, 5, 0, 4, 6, 0);
            }

            if (v10.open[EAST]) {
                waterBox(c, box, 7, 5, 3, 7, 6, 4);
                fill(c, box, 5, 4, 2, 6, 4, 5, s.BRICKS);
                fill(c, box, 6, 1, 2, 6, 3, 2, s.BRICKS);
                fill(c, box, 6, 1, 5, 6, 3, 5, s.BRICKS);
            }

            if (v10.open[WEST]) {
                waterBox(c, box, 0, 5, 3, 0, 6, 4);
                fill(c, box, 1, 4, 2, 2, 4, 5, s.BRICKS);
                fill(c, box, 1, 1, 2, 1, 3, 2, s.BRICKS);
                fill(c, box, 1, 1, 5, 1, 3, 5, s.BRICKS);
            }

            if (v9.open[NORTH]) {
                waterBox(c, box, 3, 5, 15, 4, 6, 15);
            }

            if (v9.open[WEST]) {
                waterBox(c, box, 0, 5, 11, 0, 6, 12);
                fill(c, box, 1, 4, 10, 2, 4, 13, s.BRICKS);
                fill(c, box, 1, 1, 10, 1, 3, 10, s.BRICKS);
                fill(c, box, 1, 1, 13, 1, 3, 13, s.BRICKS);
            }

            if (v9.open[EAST]) {
                waterBox(c, box, 7, 5, 11, 7, 6, 12);
                fill(c, box, 5, 4, 10, 6, 4, 13, s.BRICKS);
                fill(c, box, 6, 1, 10, 6, 3, 10, s.BRICKS);
                fill(c, box, 6, 1, 13, 6, 3, 13, s.BRICKS);
            }
        }
    }

    static final class DoubleZRoom extends MonumentPiece {

        @Override

        public String pieceType() { return "omdzr"; }


        DoubleZRoom(Direction d, RoomDef r) {
            super(d, r, 1, 1, 2);
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            RoomDef v7 = room.conn[NORTH];
            RoomDef v8 = room;
            if (room.index / 25 > 0) {
                generateDefaultFloor(c, box, 0, 8, v7.open[DOWN]);
                generateDefaultFloor(c, box, 0, 0, v8.open[DOWN]);
            }

            if (v8.conn[UP] == null) {
                replaceWater(c, box, 1, 4, 1, 6, 4, 7, s.PRISM);
            }

            if (v7.conn[UP] == null) {
                replaceWater(c, box, 1, 4, 8, 6, 4, 14, s.PRISM);
            }

            fill(c, box, 0, 3, 0, 0, 3, 15, s.BRICKS);
            fill(c, box, 7, 3, 0, 7, 3, 15, s.BRICKS);
            fill(c, box, 1, 3, 0, 7, 3, 0, s.BRICKS);
            fill(c, box, 1, 3, 15, 6, 3, 15, s.BRICKS);
            fill(c, box, 0, 2, 0, 0, 2, 15, s.PRISM);
            fill(c, box, 7, 2, 0, 7, 2, 15, s.PRISM);
            fill(c, box, 1, 2, 0, 7, 2, 0, s.PRISM);
            fill(c, box, 1, 2, 15, 6, 2, 15, s.PRISM);
            fill(c, box, 0, 1, 0, 0, 1, 15, s.BRICKS);
            fill(c, box, 7, 1, 0, 7, 1, 15, s.BRICKS);
            fill(c, box, 1, 1, 0, 7, 1, 0, s.BRICKS);
            fill(c, box, 1, 1, 15, 6, 1, 15, s.BRICKS);
            fill(c, box, 1, 1, 1, 1, 1, 2, s.BRICKS);
            fill(c, box, 6, 1, 1, 6, 1, 2, s.BRICKS);
            fill(c, box, 1, 3, 1, 1, 3, 2, s.BRICKS);
            fill(c, box, 6, 3, 1, 6, 3, 2, s.BRICKS);
            fill(c, box, 1, 1, 13, 1, 1, 14, s.BRICKS);
            fill(c, box, 6, 1, 13, 6, 1, 14, s.BRICKS);
            fill(c, box, 1, 3, 13, 1, 3, 14, s.BRICKS);
            fill(c, box, 6, 3, 13, 6, 3, 14, s.BRICKS);
            fill(c, box, 2, 1, 6, 2, 3, 6, s.BRICKS);
            fill(c, box, 5, 1, 6, 5, 3, 6, s.BRICKS);
            fill(c, box, 2, 1, 9, 2, 3, 9, s.BRICKS);
            fill(c, box, 5, 1, 9, 5, 3, 9, s.BRICKS);
            fill(c, box, 3, 2, 6, 4, 2, 6, s.BRICKS);
            fill(c, box, 3, 2, 9, 4, 2, 9, s.BRICKS);
            fill(c, box, 2, 2, 7, 2, 2, 8, s.BRICKS);
            fill(c, box, 5, 2, 7, 5, 2, 8, s.BRICKS);
            placeBlock(c, s.LANTERN, 2, 2, 5, box);
            placeBlock(c, s.LANTERN, 5, 2, 5, box);
            placeBlock(c, s.LANTERN, 2, 2, 10, box);
            placeBlock(c, s.LANTERN, 5, 2, 10, box);
            placeBlock(c, s.BRICKS, 2, 3, 5, box);
            placeBlock(c, s.BRICKS, 5, 3, 5, box);
            placeBlock(c, s.BRICKS, 2, 3, 10, box);
            placeBlock(c, s.BRICKS, 5, 3, 10, box);
            if (v8.open[SOUTH]) {
                waterBox(c, box, 3, 1, 0, 4, 2, 0);
            }

            if (v8.open[EAST]) {
                waterBox(c, box, 7, 1, 3, 7, 2, 4);
            }

            if (v8.open[WEST]) {
                waterBox(c, box, 0, 1, 3, 0, 2, 4);
            }

            if (v7.open[NORTH]) {
                waterBox(c, box, 3, 1, 15, 4, 2, 15);
            }

            if (v7.open[WEST]) {
                waterBox(c, box, 0, 1, 11, 0, 2, 12);
            }

            if (v7.open[EAST]) {
                waterBox(c, box, 7, 1, 11, 7, 2, 12);
            }
        }
    }

    static final class SimpleRoom extends MonumentPiece {

        @Override

        public String pieceType() { return "omsimple"; }

        private final int design;

        SimpleRoom(Direction d, RoomDef r, LegacyRandom rnd) {
            super(d, r, 1, 1, 1);
            this.design = rnd.nextInt(3);
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            if (room.index / 25 > 0) {
                generateDefaultFloor(c, box, 0, 0, room.open[DOWN]);
            }

            if (room.conn[UP] == null) {
                replaceWater(c, box, 1, 4, 1, 6, 4, 6, s.PRISM);
            }

            boolean v7 = this.design != 0 && r.nextBoolean() && !room.open[DOWN] && !room.open[UP] && room.countOpenings() > 1;
            if (this.design == 0) {
                fill(c, box, 0, 1, 0, 2, 1, 2, s.BRICKS);
                fill(c, box, 0, 3, 0, 2, 3, 2, s.BRICKS);
                fill(c, box, 0, 2, 0, 0, 2, 2, s.PRISM);
                fill(c, box, 1, 2, 0, 2, 2, 0, s.PRISM);
                placeBlock(c, s.LANTERN, 1, 2, 1, box);
                fill(c, box, 5, 1, 0, 7, 1, 2, s.BRICKS);
                fill(c, box, 5, 3, 0, 7, 3, 2, s.BRICKS);
                fill(c, box, 7, 2, 0, 7, 2, 2, s.PRISM);
                fill(c, box, 5, 2, 0, 6, 2, 0, s.PRISM);
                placeBlock(c, s.LANTERN, 6, 2, 1, box);
                fill(c, box, 0, 1, 5, 2, 1, 7, s.BRICKS);
                fill(c, box, 0, 3, 5, 2, 3, 7, s.BRICKS);
                fill(c, box, 0, 2, 5, 0, 2, 7, s.PRISM);
                fill(c, box, 1, 2, 7, 2, 2, 7, s.PRISM);
                placeBlock(c, s.LANTERN, 1, 2, 6, box);
                fill(c, box, 5, 1, 5, 7, 1, 7, s.BRICKS);
                fill(c, box, 5, 3, 5, 7, 3, 7, s.BRICKS);
                fill(c, box, 7, 2, 5, 7, 2, 7, s.PRISM);
                fill(c, box, 5, 2, 7, 6, 2, 7, s.PRISM);
                placeBlock(c, s.LANTERN, 6, 2, 6, box);
                if (room.open[SOUTH]) {
                    fill(c, box, 3, 3, 0, 4, 3, 0, s.BRICKS);
                } else {
                    fill(c, box, 3, 3, 0, 4, 3, 1, s.BRICKS);
                    fill(c, box, 3, 2, 0, 4, 2, 0, s.PRISM);
                    fill(c, box, 3, 1, 0, 4, 1, 1, s.BRICKS);
                }

                if (room.open[NORTH]) {
                    fill(c, box, 3, 3, 7, 4, 3, 7, s.BRICKS);
                } else {
                    fill(c, box, 3, 3, 6, 4, 3, 7, s.BRICKS);
                    fill(c, box, 3, 2, 7, 4, 2, 7, s.PRISM);
                    fill(c, box, 3, 1, 6, 4, 1, 7, s.BRICKS);
                }

                if (room.open[WEST]) {
                    fill(c, box, 0, 3, 3, 0, 3, 4, s.BRICKS);
                } else {
                    fill(c, box, 0, 3, 3, 1, 3, 4, s.BRICKS);
                    fill(c, box, 0, 2, 3, 0, 2, 4, s.PRISM);
                    fill(c, box, 0, 1, 3, 1, 1, 4, s.BRICKS);
                }

                if (room.open[EAST]) {
                    fill(c, box, 7, 3, 3, 7, 3, 4, s.BRICKS);
                } else {
                    fill(c, box, 6, 3, 3, 7, 3, 4, s.BRICKS);
                    fill(c, box, 7, 2, 3, 7, 2, 4, s.PRISM);
                    fill(c, box, 6, 1, 3, 7, 1, 4, s.BRICKS);
                }
            } else if (this.design == 1) {
                fill(c, box, 2, 1, 2, 2, 3, 2, s.BRICKS);
                fill(c, box, 2, 1, 5, 2, 3, 5, s.BRICKS);
                fill(c, box, 5, 1, 5, 5, 3, 5, s.BRICKS);
                fill(c, box, 5, 1, 2, 5, 3, 2, s.BRICKS);
                placeBlock(c, s.LANTERN, 2, 2, 2, box);
                placeBlock(c, s.LANTERN, 2, 2, 5, box);
                placeBlock(c, s.LANTERN, 5, 2, 5, box);
                placeBlock(c, s.LANTERN, 5, 2, 2, box);
                fill(c, box, 0, 1, 0, 1, 3, 0, s.BRICKS);
                fill(c, box, 0, 1, 1, 0, 3, 1, s.BRICKS);
                fill(c, box, 0, 1, 7, 1, 3, 7, s.BRICKS);
                fill(c, box, 0, 1, 6, 0, 3, 6, s.BRICKS);
                fill(c, box, 6, 1, 7, 7, 3, 7, s.BRICKS);
                fill(c, box, 7, 1, 6, 7, 3, 6, s.BRICKS);
                fill(c, box, 6, 1, 0, 7, 3, 0, s.BRICKS);
                fill(c, box, 7, 1, 1, 7, 3, 1, s.BRICKS);
                placeBlock(c, s.PRISM, 1, 2, 0, box);
                placeBlock(c, s.PRISM, 0, 2, 1, box);
                placeBlock(c, s.PRISM, 1, 2, 7, box);
                placeBlock(c, s.PRISM, 0, 2, 6, box);
                placeBlock(c, s.PRISM, 6, 2, 7, box);
                placeBlock(c, s.PRISM, 7, 2, 6, box);
                placeBlock(c, s.PRISM, 6, 2, 0, box);
                placeBlock(c, s.PRISM, 7, 2, 1, box);
                if (!room.open[SOUTH]) {
                    fill(c, box, 1, 3, 0, 6, 3, 0, s.BRICKS);
                    fill(c, box, 1, 2, 0, 6, 2, 0, s.PRISM);
                    fill(c, box, 1, 1, 0, 6, 1, 0, s.BRICKS);
                }

                if (!room.open[NORTH]) {
                    fill(c, box, 1, 3, 7, 6, 3, 7, s.BRICKS);
                    fill(c, box, 1, 2, 7, 6, 2, 7, s.PRISM);
                    fill(c, box, 1, 1, 7, 6, 1, 7, s.BRICKS);
                }

                if (!room.open[WEST]) {
                    fill(c, box, 0, 3, 1, 0, 3, 6, s.BRICKS);
                    fill(c, box, 0, 2, 1, 0, 2, 6, s.PRISM);
                    fill(c, box, 0, 1, 1, 0, 1, 6, s.BRICKS);
                }

                if (!room.open[EAST]) {
                    fill(c, box, 7, 3, 1, 7, 3, 6, s.BRICKS);
                    fill(c, box, 7, 2, 1, 7, 2, 6, s.PRISM);
                    fill(c, box, 7, 1, 1, 7, 1, 6, s.BRICKS);
                }
            } else if (this.design == 2) {
                fill(c, box, 0, 1, 0, 0, 1, 7, s.BRICKS);
                fill(c, box, 7, 1, 0, 7, 1, 7, s.BRICKS);
                fill(c, box, 1, 1, 0, 6, 1, 0, s.BRICKS);
                fill(c, box, 1, 1, 7, 6, 1, 7, s.BRICKS);
                fill(c, box, 0, 2, 0, 0, 2, 7, s.DARK);
                fill(c, box, 7, 2, 0, 7, 2, 7, s.DARK);
                fill(c, box, 1, 2, 0, 6, 2, 0, s.DARK);
                fill(c, box, 1, 2, 7, 6, 2, 7, s.DARK);
                fill(c, box, 0, 3, 0, 0, 3, 7, s.BRICKS);
                fill(c, box, 7, 3, 0, 7, 3, 7, s.BRICKS);
                fill(c, box, 1, 3, 0, 6, 3, 0, s.BRICKS);
                fill(c, box, 1, 3, 7, 6, 3, 7, s.BRICKS);
                fill(c, box, 0, 1, 3, 0, 2, 4, s.DARK);
                fill(c, box, 7, 1, 3, 7, 2, 4, s.DARK);
                fill(c, box, 3, 1, 0, 4, 2, 0, s.DARK);
                fill(c, box, 3, 1, 7, 4, 2, 7, s.DARK);
                if (room.open[SOUTH]) {
                    waterBox(c, box, 3, 1, 0, 4, 2, 0);
                }

                if (room.open[NORTH]) {
                    waterBox(c, box, 3, 1, 7, 4, 2, 7);
                }

                if (room.open[WEST]) {
                    waterBox(c, box, 0, 1, 3, 0, 2, 4);
                }

                if (room.open[EAST]) {
                    waterBox(c, box, 7, 1, 3, 7, 2, 4);
                }
            }

            if (v7) {
                fill(c, box, 3, 1, 3, 4, 1, 4, s.BRICKS);
                fill(c, box, 3, 2, 3, 4, 2, 4, s.PRISM);
                fill(c, box, 3, 3, 3, 4, 3, 4, s.BRICKS);
            }
        }
    }

    static final class SimpleTopRoom extends MonumentPiece {

        @Override

        public String pieceType() { return "omsimplet"; }


        SimpleTopRoom(Direction d, RoomDef r) {
            super(d, r, 1, 1, 1);
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            if (room.index / 25 > 0) {
                generateDefaultFloor(c, box, 0, 0, room.open[DOWN]);
            }

            if (room.conn[UP] == null) {
                replaceWater(c, box, 1, 4, 1, 6, 4, 6, s.PRISM);
            }

            for (int v7 = 1; v7 <= 6; v7++) {
                for (int v8 = 1; v8 <= 6; v8++) {
                    if (r.nextInt(3) != 0) {
                        int v9 = 2 + (r.nextInt(4) == 0 ? 0 : 1);
                        int v10 = s.WET_SPONGE;
                        fill(c, box, v7, v9, v8, v7, 3, v8, v10);
                    }
                }
            }

            fill(c, box, 0, 1, 0, 0, 1, 7, s.BRICKS);
            fill(c, box, 7, 1, 0, 7, 1, 7, s.BRICKS);
            fill(c, box, 1, 1, 0, 6, 1, 0, s.BRICKS);
            fill(c, box, 1, 1, 7, 6, 1, 7, s.BRICKS);
            fill(c, box, 0, 2, 0, 0, 2, 7, s.DARK);
            fill(c, box, 7, 2, 0, 7, 2, 7, s.DARK);
            fill(c, box, 1, 2, 0, 6, 2, 0, s.DARK);
            fill(c, box, 1, 2, 7, 6, 2, 7, s.DARK);
            fill(c, box, 0, 3, 0, 0, 3, 7, s.BRICKS);
            fill(c, box, 7, 3, 0, 7, 3, 7, s.BRICKS);
            fill(c, box, 1, 3, 0, 6, 3, 0, s.BRICKS);
            fill(c, box, 1, 3, 7, 6, 3, 7, s.BRICKS);
            fill(c, box, 0, 1, 3, 0, 2, 4, s.DARK);
            fill(c, box, 7, 1, 3, 7, 2, 4, s.DARK);
            fill(c, box, 3, 1, 0, 4, 2, 0, s.DARK);
            fill(c, box, 3, 1, 7, 4, 2, 7, s.DARK);
            if (room.open[SOUTH]) {
                waterBox(c, box, 3, 1, 0, 4, 2, 0);
            }
        }
    }

    static final class Penthouse extends MonumentPiece {

        @Override

        public String pieceType() { return "ompenthouse"; }


        Penthouse(Direction d, BoundingBox box) {
            super(d, box);
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            fill(c, box, 2, -1, 2, 11, -1, 11, s.BRICKS);
            fill(c, box, 0, -1, 0, 1, -1, 11, s.PRISM);
            fill(c, box, 12, -1, 0, 13, -1, 11, s.PRISM);
            fill(c, box, 2, -1, 0, 11, -1, 1, s.PRISM);
            fill(c, box, 2, -1, 12, 11, -1, 13, s.PRISM);
            fill(c, box, 0, 0, 0, 0, 0, 13, s.BRICKS);
            fill(c, box, 13, 0, 0, 13, 0, 13, s.BRICKS);
            fill(c, box, 1, 0, 0, 12, 0, 0, s.BRICKS);
            fill(c, box, 1, 0, 13, 12, 0, 13, s.BRICKS);

            for (int v7 = 2; v7 <= 11; v7 += 3) {
                placeBlock(c, s.LANTERN, 0, 0, v7, box);
                placeBlock(c, s.LANTERN, 13, 0, v7, box);
                placeBlock(c, s.LANTERN, v7, 0, 0, box);
            }

            fill(c, box, 2, 0, 3, 4, 0, 9, s.BRICKS);
            fill(c, box, 9, 0, 3, 11, 0, 9, s.BRICKS);
            fill(c, box, 4, 0, 9, 9, 0, 11, s.BRICKS);
            placeBlock(c, s.BRICKS, 5, 0, 8, box);
            placeBlock(c, s.BRICKS, 8, 0, 8, box);
            placeBlock(c, s.BRICKS, 10, 0, 10, box);
            placeBlock(c, s.BRICKS, 3, 0, 10, box);
            fill(c, box, 3, 0, 3, 3, 0, 7, s.DARK);
            fill(c, box, 10, 0, 3, 10, 0, 7, s.DARK);
            fill(c, box, 6, 0, 10, 7, 0, 10, s.DARK);
            int v8 = 3;

            for (int v9 = 0; v9 < 2; v9++) {
                for (int v10 = 2; v10 <= 8; v10 += 3) {
                    fill(c, box, v8, 0, v10, v8, 2, v10, s.BRICKS);
                }

                v8 = 10;
            }

            fill(c, box, 5, 0, 10, 5, 2, 10, s.BRICKS);
            fill(c, box, 8, 0, 10, 8, 2, 10, s.BRICKS);
            fill(c, box, 6, -1, 7, 7, -1, 8, s.DARK);
            waterBox(c, box, 6, -1, 3, 7, -1, 4);
            spawnElderGuardian(c, box, 6, 1, 6);
        }
    }

    static final class WingRoom extends MonumentPiece {

        @Override

        public String pieceType() { return "omwr"; }

        private final int design;

        WingRoom(Direction d, BoundingBox box, int n) {
            super(d, box);
            this.design = n & 1;
        }

        @Override
        public void postProcess(PostCtx c) {
            St s = St.of(c.engine);
            BoundingBox box = c.writableBox;
            LegacyRandom r = c.random;
            if (this.design == 0) {
                for (int v7 = 0; v7 < 4; v7++) {
                    fill(c, box, 10 - v7, 3 - v7, 20 - v7, 12 + v7, 3 - v7, 20, s.BRICKS);
                }

                fill(c, box, 7, 0, 6, 15, 0, 16, s.BRICKS);
                fill(c, box, 6, 0, 6, 6, 3, 20, s.BRICKS);
                fill(c, box, 16, 0, 6, 16, 3, 20, s.BRICKS);
                fill(c, box, 7, 1, 7, 7, 1, 20, s.BRICKS);
                fill(c, box, 15, 1, 7, 15, 1, 20, s.BRICKS);
                fill(c, box, 7, 1, 6, 9, 3, 6, s.BRICKS);
                fill(c, box, 13, 1, 6, 15, 3, 6, s.BRICKS);
                fill(c, box, 8, 1, 7, 9, 1, 7, s.BRICKS);
                fill(c, box, 13, 1, 7, 14, 1, 7, s.BRICKS);
                fill(c, box, 9, 0, 5, 13, 0, 5, s.BRICKS);
                fill(c, box, 10, 0, 7, 12, 0, 7, s.DARK);
                fill(c, box, 8, 0, 10, 8, 0, 12, s.DARK);
                fill(c, box, 14, 0, 10, 14, 0, 12, s.DARK);

                for (int v8 = 18; v8 >= 7; v8 -= 3) {
                    placeBlock(c, s.LANTERN, 6, 3, v8, box);
                    placeBlock(c, s.LANTERN, 16, 3, v8, box);
                }

                placeBlock(c, s.LANTERN, 10, 0, 10, box);
                placeBlock(c, s.LANTERN, 12, 0, 10, box);
                placeBlock(c, s.LANTERN, 10, 0, 12, box);
                placeBlock(c, s.LANTERN, 12, 0, 12, box);
                placeBlock(c, s.LANTERN, 8, 3, 6, box);
                placeBlock(c, s.LANTERN, 14, 3, 6, box);
                placeBlock(c, s.BRICKS, 4, 2, 4, box);
                placeBlock(c, s.LANTERN, 4, 1, 4, box);
                placeBlock(c, s.BRICKS, 4, 0, 4, box);
                placeBlock(c, s.BRICKS, 18, 2, 4, box);
                placeBlock(c, s.LANTERN, 18, 1, 4, box);
                placeBlock(c, s.BRICKS, 18, 0, 4, box);
                placeBlock(c, s.BRICKS, 4, 2, 18, box);
                placeBlock(c, s.LANTERN, 4, 1, 18, box);
                placeBlock(c, s.BRICKS, 4, 0, 18, box);
                placeBlock(c, s.BRICKS, 18, 2, 18, box);
                placeBlock(c, s.LANTERN, 18, 1, 18, box);
                placeBlock(c, s.BRICKS, 18, 0, 18, box);
                placeBlock(c, s.BRICKS, 9, 7, 20, box);
                placeBlock(c, s.BRICKS, 13, 7, 20, box);
                fill(c, box, 6, 0, 21, 7, 4, 21, s.BRICKS);
                fill(c, box, 15, 0, 21, 16, 4, 21, s.BRICKS);
                spawnElderGuardian(c, box, 11, 2, 16);
            } else if (this.design == 1) {
                fill(c, box, 9, 3, 18, 13, 3, 20, s.BRICKS);
                fill(c, box, 9, 0, 18, 9, 2, 18, s.BRICKS);
                fill(c, box, 13, 0, 18, 13, 2, 18, s.BRICKS);
                int v9 = 9;
                int v10 = 20;
                int v11 = 5;

                for (int v12 = 0; v12 < 2; v12++) {
                    placeBlock(c, s.BRICKS, v9, 6, 20, box);
                    placeBlock(c, s.LANTERN, v9, 5, 20, box);
                    placeBlock(c, s.BRICKS, v9, 4, 20, box);
                    v9 = 13;
                }

                fill(c, box, 7, 3, 7, 15, 3, 14, s.BRICKS);
                int var14 = 10;

                for (int v13 = 0; v13 < 2; v13++) {
                    fill(c, box, var14, 0, 10, var14, 6, 10, s.BRICKS);
                    fill(c, box, var14, 0, 12, var14, 6, 12, s.BRICKS);
                    placeBlock(c, s.LANTERN, var14, 0, 10, box);
                    placeBlock(c, s.LANTERN, var14, 0, 12, box);
                    placeBlock(c, s.LANTERN, var14, 4, 10, box);
                    placeBlock(c, s.LANTERN, var14, 4, 12, box);
                    var14 = 12;
                }

                var14 = 8;

                for (int v14 = 0; v14 < 2; v14++) {
                    fill(c, box, var14, 0, 7, var14, 2, 7, s.BRICKS);
                    fill(c, box, var14, 0, 14, var14, 2, 14, s.BRICKS);
                    var14 = 14;
                }

                fill(c, box, 8, 3, 8, 8, 3, 13, s.DARK);
                fill(c, box, 14, 3, 8, 14, 3, 13, s.DARK);
                spawnElderGuardian(c, box, 11, 5, 13);
            }
        }
    }

}
