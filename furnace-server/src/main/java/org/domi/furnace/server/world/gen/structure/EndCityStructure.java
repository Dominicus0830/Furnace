// 엔드 시티 — 바닐라 EndCityStructure + EndCityPieces 이식 (템플릿 재귀 생성: 탑/다리/굵은 탑/배, 충돌 검사, 상자·셜커·겉날개 액자).
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processors;

public final class EndCityStructure extends StructureType {

    /** 바닐라 EndCityPieces.MAX_GEN_DEPTH */
    private static final int MAX_GEN_DEPTH = 8;

    public EndCityStructure(StructureRegistry.Def def) {}

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        int rotation = c.random.nextInt(4);   // Rotation.getRandom
        // getLowestYIn5by5BoxOffset7Blocks — 청크 (7,7) 기준, 회전별 부호의 5x5 상자 모서리 4곳 WORLD_SURFACE_WG 최소 높이
        int y = c.lowestYIn5by5BoxOffset7Blocks(rotation);
        int x = c.lowestYIn5by5BoxOffset7BlocksX();
        int z = c.lowestYIn5by5BoxOffset7BlocksZ();
        if (y < 60) return Optional.empty();
        return Optional.of(new Stub(x, y, z, b -> {
            List<Piece> list = new ArrayList<>();
            startHouseTower(c.engine, x, y, z, rotation, list, c.random);
            for (Piece p : list) b.addPiece(p);
        }));
    }

    // ------------------------------------------------------------------ 조각

    /** 바닐라 EndCityPieces.EndCityPiece */
    static final class Piece extends TemplatePiece {
        @Override
        public String pieceType() { return "ecp"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putString("Template", shortTemplateName("end_city"));   // 바닐라 EndCityPiece: "base_floor" 처럼 짧은 이름
            t.putString("Rot", rotationName(settings.rotation));
            t.putBoolean("OW", overwrite);
        }

        /** OW: 덮어쓰기(구조물 블록만 무시) 여부. genDepth 는 StructurePiece 의 것(생성자 기본 0). */
        private final boolean overwrite;

        Piece(StructureEngine e, String name, int x, int y, int z, int rotation, boolean overwrite) {
            super("minecraft:end_city/" + name, e.template("minecraft:end_city/" + name), settings(rotation, overwrite), x, y, z);
            this.overwrite = overwrite;
        }

        private static PlaceSettings settings(int rotation, boolean overwrite) {
            PlaceSettings st = new PlaceSettings();
            st.ignoreEntities = true;
            st.processors.add(overwrite ? Processors.BlockIgnore.structureBlock() : Processors.BlockIgnore.structureAndAir());
            st.rotation = rotation;
            st.mirror = BlockStateTable.NONE;
            return st;
        }

        int rotation() { return settings.rotation; }

        @Override
        protected void handleDataMarker(String marker, int x, int y, int z, PostCtx c) {
            if (marker.startsWith("Chest")) {
                // 마커 아래 칸이 쓰기 상자 안일 때만 전리품 시드를 정한다 (RandomizableContainer.setBlockEntityLootTable)
                if (c.writableBox.isInside(x, y - 1, z)) {
                    setChestLoot(c, x, y - 1, z, "minecraft:chests/end_city_treasure");
                }
            } else if (c.writableBox.isInside(x, y, z)) {   // && Level.isInSpawnableBounds(pos)
                if (marker.startsWith("Sentry")) {
                    c.view.addEntity("minecraft:shulker", x + 0.5, y, z + 0.5, 0.0F, 0.0F);
                } else if (marker.startsWith("Elytra")) {
                    addElytraFrame(c, x, y, z, rotateSouth(settings.rotation));
                }
            }
        }
    }

    /** 바닐라 Rotation.rotate(Direction.SOUTH) → 3D 데이터값(SOUTH=3, WEST=4, NORTH=2, EAST=5). */
    private static int rotateSouth(int rotation) {
        return switch (rotation) {
            case BlockStateTable.ROT_CW90 -> 4;     // 시계 방향: SOUTH → WEST
            case BlockStateTable.ROT_CW180 -> 2;    // NORTH
            case BlockStateTable.ROT_CCW90 -> 5;    // EAST
            default -> 3;
        };
    }

    /** new ItemFrame(level, pos, facing) + setItem(elytra, false) + addFreshEntity — 위치는 블록 중심에서 벽 쪽으로 0.46875. */
    private static void addElytraFrame(PostCtx c, int x, int y, int z, int facing3d) {
        int sx = facing3d == 5 ? 1 : facing3d == 4 ? -1 : 0;
        int sz = facing3d == 3 ? 1 : facing3d == 2 ? -1 : 0;
        float yaw = switch (facing3d) {   // get2DDataValue * 90: SOUTH 0, WEST 90, NORTH 180, EAST 270
            case 4 -> 90.0F;
            case 2 -> 180.0F;
            case 5 -> 270.0F;
            default -> 0.0F;
        };
        NbtCompound nbt = c.view.addEntity("minecraft:item_frame", x + 0.5 - sx * 0.46875, y + 0.5, z + 0.5 - sz * 0.46875, yaw, 0.0F);
        nbt.putInt("TileX", x);
        nbt.putInt("TileY", y);
        nbt.putInt("TileZ", z);
        nbt.putByte("Facing", (byte) facing3d);
        NbtCompound item = new NbtCompound();
        item.putString("id", "minecraft:elytra");
        item.putInt("count", 1);
        nbt.putCompound("Item", item);
        nbt.putByte("ItemRotation", (byte) 0);
        nbt.put("ItemDropChance", 1.0F);
        nbt.putByte("Invisible", (byte) 0);
        nbt.putByte("Fixed", (byte) 0);
    }

    // ------------------------------------------------------------------ 생성기

    /** 바닐라 EndCityPieces.SectionGenerator — 임시 목록 out 에 조각을 이어 붙이고 성공 여부를 돌려준다. */
    private interface SectionGenerator {
        boolean generate(int depth, Piece parent, int[] offset, List<Piece> out, WorldgenRandom random);
    }

    /**
     * 생성기 4종과 상태. 바닐라는 static 싱글턴 + init() 이지만 여기서는 startHouseTower 호출마다 새로 만든다
     * (init() 이 모두 shipCreated=false 로 되돌리므로 동등).
     */
    private static final class Gens {
        final StructureEngine engine;
        /** 다리 생성기의 shipCreated */
        boolean shipCreated;
        final SectionGenerator houseTower = this::houseTowerGen;
        final SectionGenerator tower = this::towerGen;
        final SectionGenerator towerBridge = this::towerBridgeGen;
        final SectionGenerator fatTower = this::fatTowerGen;

        Gens(StructureEngine engine) { this.engine = engine; }

        // ---- HOUSE_TOWER_GENERATOR
        private boolean houseTowerGen(int depth, Piece parent, int[] offset, List<Piece> out, WorldgenRandom random) {
            if (depth > MAX_GEN_DEPTH) return false;
            int rot = parent.rotation();
            Piece p = add(out, addPiece(engine, parent, offset, "base_floor", rot, true));
            int r = random.nextInt(3);
            if (r == 0) {
                p = add(out, addPiece(engine, p, new int[]{-1, 4, -1}, "base_roof", rot, true));
            } else if (r == 1) {
                p = add(out, addPiece(engine, p, new int[]{-1, 0, -1}, "second_floor_2", rot, false));
                p = add(out, addPiece(engine, p, new int[]{-1, 8, -1}, "second_roof", rot, false));
                recursePieces(engine, this, tower, depth + 1, p, null, out, random);
            } else if (r == 2) {
                p = add(out, addPiece(engine, p, new int[]{-1, 0, -1}, "second_floor_2", rot, false));
                p = add(out, addPiece(engine, p, new int[]{-1, 4, -1}, "third_floor_2", rot, false));
                p = add(out, addPiece(engine, p, new int[]{-1, 8, -1}, "third_roof", rot, true));
                recursePieces(engine, this, tower, depth + 1, p, null, out, random);
            }
            return true;
        }

        // ---- TOWER_GENERATOR
        private boolean towerGen(int depth, Piece parent, int[] offset, List<Piece> out, WorldgenRandom random) {
            int rot = parent.rotation();
            int ox = 3 + random.nextInt(2);
            int oz = 3 + random.nextInt(2);
            Piece p = add(out, addPiece(engine, parent, new int[]{ox, -3, oz}, "tower_base", rot, true));
            p = add(out, addPiece(engine, p, new int[]{0, 7, 0}, "tower_piece", rot, true));
            Piece bridgeFrom = random.nextInt(3) == 0 ? p : null;
            int n = 1 + random.nextInt(3);
            for (int i = 0; i < n; i++) {
                p = add(out, addPiece(engine, p, new int[]{0, 4, 0}, "tower_piece", rot, true));
                if (i < n - 1 && random.nextBoolean()) bridgeFrom = p;
            }
            if (bridgeFrom != null) {
                for (int k = 0; k < BRIDGE_ROTATIONS.length; k++) {
                    if (random.nextBoolean()) {
                        Piece b = add(out, addPiece(engine, bridgeFrom, TOWER_BRIDGE_OFFSETS[k], "bridge_end", rotated(rot, BRIDGE_ROTATIONS[k]), true));
                        recursePieces(engine, this, towerBridge, depth + 1, b, null, out, random);
                    }
                }
                p = add(out, addPiece(engine, p, new int[]{-1, 4, -1}, "tower_top", rot, true));
            } else {
                if (depth != 7) {
                    return recursePieces(engine, this, fatTower, depth + 1, p, null, out, random);
                }
                p = add(out, addPiece(engine, p, new int[]{-1, 4, -1}, "tower_top", rot, true));
            }
            return true;
        }

        // ---- TOWER_BRIDGE_GENERATOR
        private boolean towerBridgeGen(int depth, Piece parent, int[] offset, List<Piece> out, WorldgenRandom random) {
            int rot = parent.rotation();
            int n = random.nextInt(4) + 1;
            Piece p = add(out, addPiece(engine, parent, new int[]{0, 0, -4}, "bridge_piece", rot, true));
            p.genDepth = -1;
            int dy = 0;
            for (int i = 0; i < n; i++) {
                if (random.nextBoolean()) {
                    p = add(out, addPiece(engine, p, new int[]{0, dy, -4}, "bridge_piece", rot, true));
                    dy = 0;
                } else {
                    if (random.nextBoolean()) {
                        p = add(out, addPiece(engine, p, new int[]{0, dy, -4}, "bridge_steep_stairs", rot, true));
                    } else {
                        p = add(out, addPiece(engine, p, new int[]{0, dy, -8}, "bridge_gentle_stairs", rot, true));
                    }
                    dy = 4;
                }
            }
            if (!shipCreated && random.nextInt(10 - depth) == 0) {
                int sx = -8 + random.nextInt(8);
                int sz = -70 + random.nextInt(10);
                add(out, addPiece(engine, p, new int[]{sx, dy, sz}, "ship", rot, true));
                shipCreated = true;
            } else if (!recursePieces(engine, this, houseTower, depth + 1, p, new int[]{-3, dy + 1, -11}, out, random)) {
                return false;
            }
            p = add(out, addPiece(engine, p, new int[]{4, dy, 0}, "bridge_end", rotated(rot, BlockStateTable.ROT_CW180), true));
            p.genDepth = -1;
            return true;
        }

        // ---- FAT_TOWER_GENERATOR
        private boolean fatTowerGen(int depth, Piece parent, int[] offset, List<Piece> out, WorldgenRandom random) {
            int rot = parent.rotation();
            Piece p = add(out, addPiece(engine, parent, new int[]{-3, 4, -3}, "fat_tower_base", rot, true));
            p = add(out, addPiece(engine, p, new int[]{0, 4, 0}, "fat_tower_middle", rot, true));
            for (int i = 0; i < 2 && random.nextInt(3) != 0; i++) {
                p = add(out, addPiece(engine, p, new int[]{0, 8, 0}, "fat_tower_middle", rot, true));
                for (int k = 0; k < BRIDGE_ROTATIONS.length; k++) {
                    if (random.nextBoolean()) {
                        Piece b = add(out, addPiece(engine, p, FAT_TOWER_BRIDGE_OFFSETS[k], "bridge_end", rotated(rot, BRIDGE_ROTATIONS[k]), true));
                        recursePieces(engine, this, towerBridge, depth + 1, b, null, out, random);
                    }
                }
            }
            p = add(out, addPiece(engine, p, new int[]{-2, 8, -2}, "fat_tower_top", rot, true));
            return true;
        }
    }

    /** 바닐라 TOWER_BRIDGES / FAT_TOWER_BRIDGES 의 회전 순서(NONE, CW90, CCW90, 180)와 각각의 오프셋. */
    private static final int[] BRIDGE_ROTATIONS = {
        BlockStateTable.ROT_NONE, BlockStateTable.ROT_CW90, BlockStateTable.ROT_CCW90, BlockStateTable.ROT_CW180};
    private static final int[][] TOWER_BRIDGE_OFFSETS = {{1, -1, 0}, {6, -1, 1}, {0, -1, 5}, {5, -1, 6}};
    private static final int[][] FAT_TOWER_BRIDGE_OFFSETS = {{4, -1, 0}, {12, -1, 4}, {0, -1, 8}, {8, -1, 12}};

    /** 바닐라 Rotation.getRotated — NONE0, CW90 1, CW180 2, CCW90 3 의 합(mod 4). */
    private static int rotated(int a, int b) {
        return (a + b) & 3;
    }

    private static Piece add(List<Piece> list, Piece p) {
        list.add(p);
        return p;
    }

    /** 바닐라 EndCityPieces.addHelper — 부모 템플릿 위치에서 새 조각을 만들고 calculateConnectedPosition 만큼 옮긴다. */
    private static Piece addPiece(StructureEngine e, Piece parent, int[] offset, String name, int rotation, boolean overwrite) {
        Piece piece = new Piece(e, name, parent.tx(), parent.ty(), parent.tz(), rotation, overwrite);
        int[] a = TemplatePiece.relative(parent.settings(), offset[0], offset[1], offset[2]);
        int[] b = TemplatePiece.relative(piece.settings(), 0, 0, 0);
        piece.move(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
        return piece;
    }

    /** 바닐라 EndCityPieces.startHouseTower */
    static void startHouseTower(StructureEngine e, int x, int y, int z, int rotation, List<Piece> list, WorldgenRandom random) {
        Gens gens = new Gens(e);   // 각 생성기 init()
        Piece p = add(list, new Piece(e, "base_floor", x, y, z, rotation, true));
        p = add(list, addPiece(e, p, new int[]{-1, 0, -1}, "second_floor_1", rotation, false));
        p = add(list, addPiece(e, p, new int[]{-1, 4, -1}, "third_floor_1", rotation, false));
        p = add(list, addPiece(e, p, new int[]{-1, 8, -1}, "third_roof", rotation, true));
        recursePieces(e, gens, gens.tower, 1, p, null, list, random);
    }

    /** 바닐라 EndCityPieces.recursePieces — 임시 목록에 생성하고, 새 조각이 (부모와 genDepth 가 다른) 기존 조각과 겹치면 통째로 버린다. */
    private static boolean recursePieces(StructureEngine e, Gens gens, SectionGenerator gen, int depth, Piece parent, int[] offset,
                                         List<Piece> list, WorldgenRandom random) {
        if (depth > MAX_GEN_DEPTH) return false;
        List<Piece> temp = new ArrayList<>();
        if (gen.generate(depth, parent, offset, temp, random)) {
            boolean collided = false;
            int genDepth = random.nextInt();
            for (Piece p : temp) {
                p.genDepth = genDepth;
                Piece hit = findCollision(list, p.boundingBox());
                if (hit != null && hit.genDepth != parent.genDepth) {
                    collided = true;
                    break;
                }
            }
            if (!collided) {
                list.addAll(temp);
                return true;
            }
        }
        return false;
    }

    /** 바닐라 StructurePiece.findCollisionPiece(list, box) */
    private static Piece findCollision(List<Piece> list, BoundingBox box) {
        for (Piece p : list) {
            if (p.boundingBox().intersects(box)) return p;
        }
        return null;
    }
}
