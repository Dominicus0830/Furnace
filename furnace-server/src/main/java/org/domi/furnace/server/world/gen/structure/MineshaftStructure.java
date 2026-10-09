// 폐광(mineshaft, normal/mesa) — 바닐라 MineshaftStructure + MineshaftPieces(방·복도·교차로·계단) 이식.
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class MineshaftStructure extends StructureType {

    /** 바닐라 MineshaftStructure.Type — 기둥(log)·판자(planks)·울타리(fence) 블록 이름. */
    enum Kind {
        NORMAL("minecraft:oak_log", "minecraft:oak_planks", "minecraft:oak_fence"),
        MESA("minecraft:dark_oak_log", "minecraft:dark_oak_planks", "minecraft:dark_oak_fence");

        final String log, planks, fence;

        Kind(String log, String planks, String fence) {
            this.log = log;
            this.planks = planks;
            this.fence = fence;
        }
    }

    private static final String LOOT = "minecraft:chests/abandoned_mineshaft";

    private final Kind kind;

    public MineshaftStructure(StructureRegistry.Def def) {
        String t = def.json().get("mineshaft_type").getAsString();
        this.kind = "mesa".equals(t) ? Kind.MESA : Kind.NORMAL;
    }

    // ================================================================================================
    // MineshaftStructure.findGenerationPoint / generatePieces
    // ================================================================================================

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        c.random.nextDouble();   // 바닐라가 버리는 첫 nextDouble (소비 순서 유지)
        PiecesBuilder builder = new PiecesBuilder();
        int offset = generatePiecesAndAdjust(builder, c);
        // 바닐라 quirk 그대로: BlockPos(chunk.middleBlockX, 50, chunk.minBlockZ).offset(0, offset, 0)
        return Optional.of(new Stub(c.middleBlockX(), 50 + offset, c.minBlockZ(), b -> {
            for (StructurePiece p : builder.pieces()) b.addPiece(p);
        }));
    }

    private int generatePiecesAndAdjust(PiecesBuilder builder, GenContext c) {
        WorldgenRandom random = c.random;
        Room start = new Room(0, random, c.minBlockX() + 2, c.minBlockZ() + 2, kind);
        builder.addPiece(start);
        start.addChildren(start, builder, random);
        int seaLevel = c.terrain.seaLevel();
        if (kind == Kind.MESA) {
            BoundingBox bb = builder.boundingBox();
            // BoundingBox.getCenter(): min + (max - min + 1) / 2
            int cx = bb.minX() + (bb.maxX() - bb.minX() + 1) / 2;
            int cy = bb.minY() + (bb.maxY() - bb.minY() + 1) / 2;
            int cz = bb.minZ() + (bb.maxZ() - bb.minZ() + 1) / 2;
            int surface = c.terrain.firstFreeHeight(TerrainAccess.HM_WORLD_SURFACE_WG, cx, cz);
            int y = surface <= seaLevel ? seaLevel : random.nextInt(surface - seaLevel + 1) + seaLevel;   // Mth.randomBetweenInclusive
            int dy = y - cy;
            builder.offsetPiecesVertically(dy);
            return dy;
        }
        return builder.moveBelowSeaLevel(seaLevel, c.terrain.minY(), random, 10);
    }

    // ================================================================================================
    // MineshaftPieces 정적 헬퍼
    // ================================================================================================

    /** MineshaftPieces.createRandomShaftPiece */
    private static Base createRandomShaftPiece(PiecesBuilder builder, LegacyRandom random, int x, int y, int z, Direction dir, int genDepth, Kind kind) {
        int i = random.nextInt(100);
        if (i >= 80) {
            BoundingBox bb = Crossing.findCrossing(builder, random, x, y, z, dir);
            if (bb != null) return new Crossing(genDepth, bb, dir, kind);
        } else if (i >= 70) {
            BoundingBox bb = Stairs.findStairs(builder, random, x, y, z, dir);
            if (bb != null) return new Stairs(genDepth, bb, dir, kind);
        } else {
            BoundingBox bb = Corridor.findCorridorSize(builder, random, x, y, z, dir);
            if (bb != null) return new Corridor(genDepth, random, bb, dir, kind);
        }
        return null;
    }

    /** MineshaftPieces.generateAndAddPiece — 깊이 8 초과·시작 조각에서 |dx|,|dz| > 80 이면 null. */
    private static Base generateAndAddPiece(Base start, PiecesBuilder builder, LegacyRandom random, int x, int y, int z, Direction dir, int genDepth) {
        if (genDepth > 8) return null;
        if (Math.abs(x - start.boundingBox().minX()) <= 80 && Math.abs(z - start.boundingBox().minZ()) <= 80) {
            Base piece = createRandomShaftPiece(builder, random, x, y, z, dir, genDepth + 1, start.kind);
            if (piece != null) {
                builder.addPiece(piece);
                piece.addChildren(start, builder, random);
            }
            return piece;
        }
        return null;
    }

    // ================================================================================================
    // 공통 베이스 (MineshaftPiece)
    // ================================================================================================

    /** postProcess 에서 쓰는 블록 상태 id 묶음. */
    private static final class St {
        final int air, cobweb, planks, log, fence, chain, railNS, railEW, spawner;
        final int fenceWest, fenceEast, torchSouth, torchNorth;

        St(PostCtx c, Kind k) {
            StructureEngine e = c.engine;
            air = e.state("minecraft:cave_air");
            cobweb = e.state("minecraft:cobweb");
            planks = e.state(k.planks);
            log = e.state(k.log);
            fence = e.state(k.fence);
            chain = e.state("minecraft:chain");
            railNS = e.state("minecraft:rail[shape=north_south]");
            railEW = e.state("minecraft:rail[shape=east_west]");
            spawner = e.state("minecraft:spawner");
            fenceWest = e.state(k.fence + "[west=true]");
            fenceEast = e.state(k.fence + "[east=true]");
            torchSouth = e.state("minecraft:wall_torch[facing=south]");
            torchNorth = e.state("minecraft:wall_torch[facing=north]");
        }
    }

    abstract static class Base extends StructurePiece {
        final Kind kind;
        int genDepth;

        Base(int genDepth, Kind kind, BoundingBox box, Direction orientation) {
            super(box, orientation);
            this.genDepth = genDepth;
            this.kind = kind;
        }

        void addChildren(Base start, PiecesBuilder builder, LegacyRandom random) {}

        /** MineshaftPiece.canBeReplaced — 광산 자신의 판자·기둥·울타리·사슬은 덮어쓰지 않는다. */
        @Override
        protected boolean canBeReplaced(PostCtx c, int x, int y, int z, BoundingBox box) {
            String n = c.table().name(getBlock(c, x, y, z, box));
            return !n.equals(kind.planks) && !n.equals(kind.log) && !n.equals(kind.fence) && !n.equals("minecraft:chain");
        }

        /** MineshaftPiece.isSupportingBox — x 구간 위(y+1)에 빈 칸이 하나라도 있으면 false. */
        protected boolean isSupportingBox(PostCtx c, BoundingBox box, int xStart, int xEnd, int y, int z) {
            BlockAttrs a = BlockAttrs.get();
            for (int x = xStart; x <= xEnd; x++) {
                if (a.isAir(getBlock(c, x, y + 1, z, box))) return false;
            }
            return true;
        }

        /**
         * MineshaftPiece.isInInvalidLocation — 조각 주변(±1)의 상·하·옆 면에 액체가 닿거나 중심 biome 이 mineshaft_blocking 태그
         * (tags/worldgen/biome/mineshaft_blocking.json = deep_dark)이면 true.
         */
        protected boolean isInInvalidLocation(PostCtx c, BoundingBox box) {
            BoundingBox bb = boundingBox;
            int x0 = Math.max(bb.minX() - 1, box.minX());
            int y0 = Math.max(bb.minY() - 1, box.minY());
            int z0 = Math.max(bb.minZ() - 1, box.minZ());
            int x1 = Math.min(bb.maxX() + 1, box.maxX());
            int y1 = Math.min(bb.maxY() + 1, box.maxY());
            int z1 = Math.min(bb.maxZ() + 1, box.maxZ());
            int mx = (x0 + x1) / 2, my = (y0 + y1) / 2, mz = (z0 + z1) / 2;   // 정수 나눗셈(0 방향 절사) 그대로
            if (c.engine.biomeName(c.view.getBlendedBiomeId(mx, my, mz)).equals("minecraft:deep_dark")) return true;
            BlockAttrs a = BlockAttrs.get();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (a.isLiquid(c.view.getBlock(x, y0, z))) return true;
                    if (a.isLiquid(c.view.getBlock(x, y1, z))) return true;
                }
            }
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    if (a.isLiquid(c.view.getBlock(x, y, z0))) return true;
                    if (a.isLiquid(c.view.getBlock(x, y, z1))) return true;
                }
            }
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    if (a.isLiquid(c.view.getBlock(x0, y, z))) return true;
                    if (a.isLiquid(c.view.getBlock(x1, y, z))) return true;
                }
            }
            return false;
        }

        /** MineshaftPiece.setPlanksBlock — 위쪽 면이 단단하지 않은 칸만 판자로 채운다(직접 setBlock: 회전/mirror 없음). */
        protected void setPlanksBlock(PostCtx c, BoundingBox box, int planks, int x, int y, int z) {
            if (isInterior(c, x, y, z, box)) {
                int wx = getWorldX(x, z), wy = getWorldY(y), wz = getWorldZ(x, z);
                int cur = c.view.getBlock(wx, wy, wz);
                if (!BlockAttrs.get().isFaceSturdy(cur, BlockAttrs.UP)) c.view.setBlock(wx, wy, wz, planks);
            }
        }
    }

    // ================================================================================================
    // 복도 (MineShaftCorridor)
    // ================================================================================================

    static final class Corridor extends Base {
        private final boolean hasRails;
        private final boolean spiderCorridor;
        private boolean hasPlacedSpider;
        private final int numSections;

        Corridor(int genDepth, LegacyRandom random, BoundingBox box, Direction dir, Kind kind) {
            super(genDepth, kind, box, dir);
            this.hasRails = random.nextInt(3) == 0;
            this.spiderCorridor = !hasRails && random.nextInt(23) == 0;
            this.numSections = dir.isXAxis() ? (box.maxX() - box.minX() + 1) / 5 : (box.maxZ() - box.minZ() + 1) / 5;
        }

        static BoundingBox findCorridorSize(PiecesBuilder builder, LegacyRandom random, int x, int y, int z, Direction dir) {
            for (int i = random.nextInt(3) + 2; i > 0; i--) {
                int len = i * 5;
                BoundingBox bb = switch (dir) {
                    case SOUTH -> new BoundingBox(0, 0, 0, 2, 2, len - 1);
                    case WEST -> new BoundingBox(-(len - 1), 0, 0, 0, 2, 2);
                    case EAST -> new BoundingBox(0, 0, 0, len - 1, 2, 2);
                    default -> new BoundingBox(0, 0, -(len - 1), 2, 2, 0);
                };
                bb.move(x, y, z);
                if (builder.findCollisionPiece(bb) == null) return bb;
            }
            return null;
        }

        @Override
        void addChildren(Base start, PiecesBuilder builder, LegacyRandom random) {
            int depth = genDepth;
            int i = random.nextInt(4);
            Direction dir = orientation;
            BoundingBox b = boundingBox;
            if (dir != null) {
                switch (dir) {
                    case SOUTH:
                        if (i <= 1) {
                            generateAndAddPiece(start, builder, random, b.minX(), b.minY() - 1 + random.nextInt(3), b.maxZ() + 1, dir, depth);
                        } else if (i == 2) {
                            generateAndAddPiece(start, builder, random, b.minX() - 1, b.minY() - 1 + random.nextInt(3), b.maxZ() - 3, Direction.WEST, depth);
                        } else {
                            generateAndAddPiece(start, builder, random, b.maxX() + 1, b.minY() - 1 + random.nextInt(3), b.maxZ() - 3, Direction.EAST, depth);
                        }
                        break;
                    case WEST:
                        if (i <= 1) {
                            generateAndAddPiece(start, builder, random, b.minX() - 1, b.minY() - 1 + random.nextInt(3), b.minZ(), dir, depth);
                        } else if (i == 2) {
                            generateAndAddPiece(start, builder, random, b.minX(), b.minY() - 1 + random.nextInt(3), b.minZ() - 1, Direction.NORTH, depth);
                        } else {
                            generateAndAddPiece(start, builder, random, b.minX(), b.minY() - 1 + random.nextInt(3), b.maxZ() + 1, Direction.SOUTH, depth);
                        }
                        break;
                    case EAST:
                        if (i <= 1) {
                            generateAndAddPiece(start, builder, random, b.maxX() + 1, b.minY() - 1 + random.nextInt(3), b.minZ(), dir, depth);
                        } else if (i == 2) {
                            generateAndAddPiece(start, builder, random, b.maxX() - 3, b.minY() - 1 + random.nextInt(3), b.minZ() - 1, Direction.NORTH, depth);
                        } else {
                            generateAndAddPiece(start, builder, random, b.maxX() - 3, b.minY() - 1 + random.nextInt(3), b.maxZ() + 1, Direction.SOUTH, depth);
                        }
                        break;
                    case NORTH:
                    default:
                        if (i <= 1) {
                            generateAndAddPiece(start, builder, random, b.minX(), b.minY() - 1 + random.nextInt(3), b.minZ() - 1, dir, depth);
                        } else if (i == 2) {
                            generateAndAddPiece(start, builder, random, b.minX() - 1, b.minY() - 1 + random.nextInt(3), b.minZ(), Direction.WEST, depth);
                        } else {
                            generateAndAddPiece(start, builder, random, b.maxX() + 1, b.minY() - 1 + random.nextInt(3), b.minZ(), Direction.EAST, depth);
                        }
                }
            }
            if (depth < 8) {
                if (dir != Direction.NORTH && dir != Direction.SOUTH) {
                    for (int x = b.minX() + 3; x + 3 <= b.maxX(); x += 5) {
                        int j = random.nextInt(5);
                        if (j == 0) {
                            generateAndAddPiece(start, builder, random, x, b.minY(), b.minZ() - 1, Direction.NORTH, depth + 1);
                        } else if (j == 1) {
                            generateAndAddPiece(start, builder, random, x, b.minY(), b.maxZ() + 1, Direction.SOUTH, depth + 1);
                        }
                    }
                } else {
                    for (int z = b.minZ() + 3; z + 3 <= b.maxZ(); z += 5) {
                        int j = random.nextInt(5);
                        if (j == 0) {
                            generateAndAddPiece(start, builder, random, b.minX() - 1, b.minY(), z, Direction.WEST, depth + 1);
                        } else if (j == 1) {
                            generateAndAddPiece(start, builder, random, b.maxX() + 1, b.minY(), z, Direction.EAST, depth + 1);
                        }
                    }
                }
            }
        }

        /** MineShaftCorridor.createChest — 상자 대신 레일 위 상자 광산수레(loot seed = random.nextLong()). */
        private boolean createMinecartChest(PostCtx c, St s, BoundingBox box, int x, int y, int z) {
            int wx = getWorldX(x, z), wy = getWorldY(y), wz = getWorldZ(x, z);
            BlockAttrs a = BlockAttrs.get();
            if (box.isInside(wx, wy, wz) && a.isAir(c.view.getBlock(wx, wy, wz)) && !a.isAir(c.view.getBlock(wx, wy - 1, wz))) {
                int rail = c.random.nextBoolean() ? s.railNS : s.railEW;
                placeBlock(c, rail, x, y, z, box);
                long seed = c.random.nextLong();
                NbtCompound cart = c.view.addEntity("minecraft:chest_minecart", wx + 0.5, wy + 0.5, wz + 0.5, 0.0F, 0.0F);
                cart.putString("LootTable", LOOT);
                cart.putLong("LootTableSeed", seed);
                return true;
            }
            return false;
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            if (isInInvalidLocation(c, box)) return;
            St s = new St(c, kind);
            LegacyRandom random = c.random;
            int length = numSections * 5 - 1;
            generateBox(c, box, 0, 0, 0, 2, 1, length, s.air, s.air, false);
            generateMaybeBox(c, box, random, 0.8F, 0, 2, 0, 2, 2, length, s.air, s.air, false, false);
            if (spiderCorridor) {
                generateMaybeBox(c, box, random, 0.6F, 0, 0, 0, 2, 1, length, s.cobweb, s.air, false, true);
            }
            for (int sec = 0; sec < numSections; sec++) {
                int z = 2 + sec * 5;
                placeSupport(c, s, box, 0, 0, z, 2, 2, random);
                maybePlaceCobWeb(c, s, box, random, 0.1F, 0, 2, z - 1);
                maybePlaceCobWeb(c, s, box, random, 0.1F, 2, 2, z - 1);
                maybePlaceCobWeb(c, s, box, random, 0.1F, 0, 2, z + 1);
                maybePlaceCobWeb(c, s, box, random, 0.1F, 2, 2, z + 1);
                maybePlaceCobWeb(c, s, box, random, 0.05F, 0, 2, z - 2);
                maybePlaceCobWeb(c, s, box, random, 0.05F, 2, 2, z - 2);
                maybePlaceCobWeb(c, s, box, random, 0.05F, 0, 2, z + 2);
                maybePlaceCobWeb(c, s, box, random, 0.05F, 2, 2, z + 2);
                if (random.nextInt(100) == 0) createMinecartChest(c, s, box, 2, 0, z - 1);
                if (random.nextInt(100) == 0) createMinecartChest(c, s, box, 0, 0, z + 1);
                if (spiderCorridor && !hasPlacedSpider) {
                    int sz = z - 1 + random.nextInt(3);
                    int wx = getWorldX(1, sz), wy = getWorldY(0), wz = getWorldZ(1, sz);
                    if (box.isInside(wx, wy, wz) && isInterior(c, 1, 0, sz, box)) {
                        hasPlacedSpider = true;
                        c.view.setBlock(wx, wy, wz, s.spawner);
                        putCaveSpiderSpawner(c, wx, wy, wz);
                    }
                }
            }
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= length; z++) setPlanksBlock(c, box, s.planks, x, -1, z);
            }
            placeDoubleLowerOrUpperSupport(c, s, box, 0, -1, 2);
            if (numSections > 1) placeDoubleLowerOrUpperSupport(c, s, box, 0, -1, length - 2);
            if (hasRails) {
                BlockAttrs a = BlockAttrs.get();
                for (int z = 0; z <= length; z++) {
                    int below = getBlock(c, 1, -1, z, box);
                    if (!a.isAir(below) && a.isSolidRender(below)) {
                        float p = isInterior(c, 1, 0, z, box) ? 0.7F : 0.9F;
                        maybeGenerateBlock(c, box, random, p, 1, 0, z, s.railNS);
                    }
                }
            }
        }

        /**
         * 스포너 블록 엔티티 — SpawnerBlockEntity.setEntityId(cave_spider, random): SpawnPotentials 가 비어 있어 가중 선택이
         * 난수를 소비하지 않으므로 random 소비는 없다. 저장 NBT 는 기본 BaseSpawner 필드 + SpawnData.entity.id.
         */
        private void putCaveSpiderSpawner(PostCtx c, int wx, int wy, int wz) {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("id", "minecraft:mob_spawner");
            nbt.putInt("x", wx);
            nbt.putInt("y", wy);
            nbt.putInt("z", wz);
            nbt.put("Delay", (short) 20);
            nbt.put("MinSpawnDelay", (short) 200);
            nbt.put("MaxSpawnDelay", (short) 800);
            nbt.put("SpawnCount", (short) 4);
            nbt.put("MaxNearbyEntities", (short) 6);
            nbt.put("RequiredPlayerRange", (short) 16);
            nbt.put("SpawnRange", (short) 4);
            NbtCompound entity = new NbtCompound();
            entity.putString("id", "minecraft:cave_spider");
            NbtCompound spawnData = new NbtCompound();
            spawnData.putCompound("entity", entity);
            nbt.putCompound("SpawnData", spawnData);
            nbt.putList("SpawnPotentials", new NbtList((byte) 10));
            c.view.putGeneratedBlockEntity(wx, wy, wz, nbt);
        }

        /** placeSupport(level, box, minX, minY, z, maxY, maxX, random) */
        private void placeSupport(PostCtx c, St s, BoundingBox box, int minX, int minY, int z, int maxY, int maxX, LegacyRandom random) {
            if (isSupportingBox(c, box, minX, maxX, maxY, z)) {
                generateBox(c, box, minX, minY, z, minX, maxY - 1, z, s.fenceWest, s.air, false);
                generateBox(c, box, maxX, minY, z, maxX, maxY - 1, z, s.fenceEast, s.air, false);
                if (random.nextInt(4) == 0) {
                    generateBox(c, box, minX, maxY, z, minX, maxY, z, s.planks, s.air, false);
                    generateBox(c, box, maxX, maxY, z, maxX, maxY, z, s.planks, s.air, false);
                } else {
                    generateBox(c, box, minX, maxY, z, maxX, maxY, z, s.planks, s.air, false);
                    maybeGenerateBlock(c, box, random, 0.05F, minX + 1, maxY, z - 1, s.torchSouth);
                    maybeGenerateBlock(c, box, random, 0.05F, minX + 1, maxY, z + 1, s.torchNorth);
                }
            }
        }

        /** maybePlaceCobWeb — isInterior → 확률 → 단단한 이웃 2개 이상일 때만 거미줄. */
        private void maybePlaceCobWeb(PostCtx c, St s, BoundingBox box, LegacyRandom random, float chance, int x, int y, int z) {
            if (isInterior(c, x, y, z, box) && random.nextFloat() < chance && hasSturdyNeighbours(c, box, x, y, z, 2)) {
                placeBlock(c, s.cobweb, x, y, z, box);
            }
        }

        /** hasSturdyNeighbours — Direction.values() 순서(DOWN,UP,NORTH,SOUTH,WEST,EAST), 월드 방향 기준. */
        private boolean hasSturdyNeighbours(PostCtx c, BoundingBox box, int x, int y, int z, int minCount) {
            BlockAttrs a = BlockAttrs.get();
            int wx = getWorldX(x, z), wy = getWorldY(y), wz = getWorldZ(x, z);
            int count = 0;
            for (int d = 0; d < 6; d++) {
                int nx = wx + STEP_X[d], ny = wy + STEP_Y[d], nz = wz + STEP_Z[d];
                if (box.isInside(nx, ny, nz) && a.isFaceSturdy(c.view.getBlock(nx, ny, nz), d ^ 1)) {
                    if (++count >= minCount) return true;
                }
            }
            return false;
        }

        /** placeDoubleLowerOrUpperSupport — x 와 x+2 의 판자 자리에 기둥(아래로) 또는 사슬(위로)을 단다. */
        private void placeDoubleLowerOrUpperSupport(PostCtx c, St s, BoundingBox box, int x, int y, int z) {
            if (c.table().name(getBlock(c, x, y, z, box)).equals(kind.planks)) {
                fillPillarDownOrChainUp(c, s, s.log, x, y, z, box);
            }
            if (c.table().name(getBlock(c, x + 2, y, z, box)).equals(kind.planks)) {
                fillPillarDownOrChainUp(c, s, s.log, x + 2, y, z, box);
            }
        }

        /** fillPillarDownOrChainUp — 아래로 20칸 안에 단단한 바닥이 있으면 기둥, 위로 50칸 안에 매달 곳이 있으면 울타리+사슬. */
        private void fillPillarDownOrChainUp(PostCtx c, St s, int state, int x, int y, int z, BoundingBox box) {
            int wx = getWorldX(x, z), wz = getWorldZ(x, z), i = getWorldY(y);
            if (!box.isInside(wx, i, wz)) return;
            BlockAttrs a = BlockAttrs.get();
            int j = 1;
            boolean down = true;
            for (boolean up = true; down || up; j++) {
                if (down) {
                    int py = i - j;
                    int bs = c.view.getBlock(wx, py, wz);
                    boolean replaceable = isReplaceableByColumn(c, a, bs) && !c.table().name(bs).equals("minecraft:lava");
                    if (!replaceable && a.isFaceSturdy(bs, BlockAttrs.UP)) {   // canPlaceColumnOnTopOf
                        fillColumnBetween(c, state, wx, wz, i - j + 1, i);
                        return;
                    }
                    down = j <= 20 && replaceable && py > -64 + 1;
                }
                if (up) {
                    int py = i + j;
                    int bs = c.view.getBlock(wx, py, wz);
                    boolean replaceable = isReplaceableByColumn(c, a, bs);
                    if (!replaceable && canHangChainFrom(c, wx, py, wz, bs)) {
                        c.view.setBlock(wx, i + 1, wz, s.fence);
                        fillColumnBetween(c, s.chain, wx, wz, i + 2, i + j);
                        return;
                    }
                    up = j <= 50 && replaceable && py < 320 - 1;
                }
            }
        }

        private static void fillColumnBetween(PostCtx c, int state, int wx, int wz, int yFrom, int yToExclusive) {
            for (int y = yFrom; y < yToExclusive; y++) c.view.setBlock(wx, y, wz, state);
        }

        /** canHangChainFrom — Block.canSupportCenter(level, pos, DOWN) 이고 FallingBlock 이 아닐 것. */
        private boolean canHangChainFrom(PostCtx c, int wx, int wy, int wz, int state) {
            return c.shapes().canSupportCenter(c.view, wx, wy, wz, BlockAttrs.DOWN) && !isFallingBlock(c.table().name(state));
        }

        /** FallingBlock 하위 클래스: 모래·자갈·콘크리트 가루·모루·용의 알(+suspicious). */
        private static boolean isFallingBlock(String n) {
            return n.equals("minecraft:sand") || n.equals("minecraft:red_sand") || n.equals("minecraft:gravel")
                || n.equals("minecraft:suspicious_sand") || n.equals("minecraft:suspicious_gravel")
                || n.equals("minecraft:anvil") || n.equals("minecraft:chipped_anvil") || n.equals("minecraft:damaged_anvil")
                || n.equals("minecraft:dragon_egg") || n.endsWith("_concrete_powder");
        }
    }

    // Direction.values() 순서(DOWN,UP,NORTH,SOUTH,WEST,EAST)의 단위 벡터
    private static final int[] STEP_X = {0, 0, 0, 0, -1, 1};
    private static final int[] STEP_Y = {-1, 1, 0, 0, 0, 0};
    private static final int[] STEP_Z = {0, 0, -1, 1, 0, 0};

    // ================================================================================================
    // 교차로 (MineShaftCrossing) — orientation 은 null(월드 좌표 그대로), 방향은 별도 필드
    // ================================================================================================

    static final class Crossing extends Base {
        private final Direction direction;
        private final boolean isTwoFloored;

        Crossing(int genDepth, BoundingBox box, Direction dir, Kind kind) {
            super(genDepth, kind, box, null);
            this.direction = dir;
            this.isTwoFloored = box.maxY() - box.minY() + 1 > 3;
        }

        static BoundingBox findCrossing(PiecesBuilder builder, LegacyRandom random, int x, int y, int z, Direction dir) {
            int height = random.nextInt(4) == 0 ? 6 : 2;
            BoundingBox bb = switch (dir) {
                case SOUTH -> new BoundingBox(-1, 0, 0, 3, height, 4);
                case WEST -> new BoundingBox(-4, 0, -1, 0, height, 3);
                case EAST -> new BoundingBox(0, 0, -1, 4, height, 3);
                default -> new BoundingBox(-1, 0, -4, 3, height, 0);
            };
            bb.move(x, y, z);
            return builder.findCollisionPiece(bb) != null ? null : bb;
        }

        @Override
        void addChildren(Base start, PiecesBuilder builder, LegacyRandom random) {
            int depth = genDepth;
            BoundingBox b = boundingBox;
            switch (direction) {
                case SOUTH:
                    generateAndAddPiece(start, builder, random, b.minX() + 1, b.minY(), b.maxZ() + 1, Direction.SOUTH, depth);
                    generateAndAddPiece(start, builder, random, b.minX() - 1, b.minY(), b.minZ() + 1, Direction.WEST, depth);
                    generateAndAddPiece(start, builder, random, b.maxX() + 1, b.minY(), b.minZ() + 1, Direction.EAST, depth);
                    break;
                case WEST:
                    generateAndAddPiece(start, builder, random, b.minX() + 1, b.minY(), b.minZ() - 1, Direction.NORTH, depth);
                    generateAndAddPiece(start, builder, random, b.minX() + 1, b.minY(), b.maxZ() + 1, Direction.SOUTH, depth);
                    generateAndAddPiece(start, builder, random, b.minX() - 1, b.minY(), b.minZ() + 1, Direction.WEST, depth);
                    break;
                case EAST:
                    generateAndAddPiece(start, builder, random, b.minX() + 1, b.minY(), b.minZ() - 1, Direction.NORTH, depth);
                    generateAndAddPiece(start, builder, random, b.minX() + 1, b.minY(), b.maxZ() + 1, Direction.SOUTH, depth);
                    generateAndAddPiece(start, builder, random, b.maxX() + 1, b.minY(), b.minZ() + 1, Direction.EAST, depth);
                    break;
                case NORTH:
                default:
                    generateAndAddPiece(start, builder, random, b.minX() + 1, b.minY(), b.minZ() - 1, Direction.NORTH, depth);
                    generateAndAddPiece(start, builder, random, b.minX() - 1, b.minY(), b.minZ() + 1, Direction.WEST, depth);
                    generateAndAddPiece(start, builder, random, b.maxX() + 1, b.minY(), b.minZ() + 1, Direction.EAST, depth);
            }
            if (isTwoFloored) {
                if (random.nextBoolean()) {
                    generateAndAddPiece(start, builder, random, b.minX() + 1, b.minY() + 3 + 1, b.minZ() - 1, Direction.NORTH, depth);
                }
                if (random.nextBoolean()) {
                    generateAndAddPiece(start, builder, random, b.minX() - 1, b.minY() + 3 + 1, b.minZ() + 1, Direction.WEST, depth);
                }
                if (random.nextBoolean()) {
                    generateAndAddPiece(start, builder, random, b.maxX() + 1, b.minY() + 3 + 1, b.minZ() + 1, Direction.EAST, depth);
                }
                if (random.nextBoolean()) {
                    generateAndAddPiece(start, builder, random, b.minX() + 1, b.minY() + 3 + 1, b.maxZ() + 1, Direction.SOUTH, depth);
                }
            }
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            if (isInInvalidLocation(c, box)) return;
            St s = new St(c, kind);
            BoundingBox b = boundingBox;
            if (isTwoFloored) {
                generateBox(c, box, b.minX() + 1, b.minY(), b.minZ(), b.maxX() - 1, b.minY() + 3 - 1, b.maxZ(), s.air, s.air, false);
                generateBox(c, box, b.minX(), b.minY(), b.minZ() + 1, b.maxX(), b.minY() + 3 - 1, b.maxZ() - 1, s.air, s.air, false);
                generateBox(c, box, b.minX() + 1, b.maxY() - 2, b.minZ(), b.maxX() - 1, b.maxY(), b.maxZ(), s.air, s.air, false);
                generateBox(c, box, b.minX(), b.maxY() - 2, b.minZ() + 1, b.maxX(), b.maxY(), b.maxZ() - 1, s.air, s.air, false);
                generateBox(c, box, b.minX() + 1, b.minY() + 3, b.minZ() + 1, b.maxX() - 1, b.minY() + 3, b.maxZ() - 1, s.air, s.air, false);
            } else {
                generateBox(c, box, b.minX() + 1, b.minY(), b.minZ(), b.maxX() - 1, b.maxY(), b.maxZ(), s.air, s.air, false);
                generateBox(c, box, b.minX(), b.minY(), b.minZ() + 1, b.maxX(), b.maxY(), b.maxZ() - 1, s.air, s.air, false);
            }
            placeSupportPillar(c, s, box, b.minX() + 1, b.minY(), b.minZ() + 1, b.maxY());
            placeSupportPillar(c, s, box, b.minX() + 1, b.minY(), b.maxZ() - 1, b.maxY());
            placeSupportPillar(c, s, box, b.maxX() - 1, b.minY(), b.minZ() + 1, b.maxY());
            placeSupportPillar(c, s, box, b.maxX() - 1, b.minY(), b.maxZ() - 1, b.maxY());
            int y = b.minY() - 1;
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) setPlanksBlock(c, box, s.planks, x, y, z);
            }
        }

        private void placeSupportPillar(PostCtx c, St s, BoundingBox box, int x, int y, int z, int maxY) {
            if (!BlockAttrs.get().isAir(getBlock(c, x, maxY + 1, z, box))) {
                generateBox(c, box, x, y, z, x, maxY, z, s.planks, s.air, false);
            }
        }
    }

    // ================================================================================================
    // 방 (MineShaftRoom) — orientation 은 null
    // ================================================================================================

    static final class Room extends Base {
        private final List<BoundingBox> childEntranceBoxes = new ArrayList<>();

        Room(int genDepth, LegacyRandom random, int x, int z, Kind kind) {
            super(genDepth, kind, makeBox(random, x, z), null);
        }

        // new BoundingBox(x, 50, z, x + 7 + nextInt(6), 54 + nextInt(6), z + 7 + nextInt(6)) — 인자 평가 순서(x→y→z) 유지
        private static BoundingBox makeBox(LegacyRandom random, int x, int z) {
            int dx = random.nextInt(6);
            int dy = random.nextInt(6);
            int dz = random.nextInt(6);
            return new BoundingBox(x, 50, z, x + 7 + dx, 54 + dy, z + 7 + dz);
        }

        @Override
        void addChildren(Base start, PiecesBuilder builder, LegacyRandom random) {
            int depth = genDepth;
            BoundingBox b = boundingBox;
            int xSpan = b.maxX() - b.minX() + 1;
            int ySpan = b.maxY() - b.minY() + 1;
            int zSpan = b.maxZ() - b.minZ() + 1;
            int i = ySpan - 3 - 1;
            if (i <= 0) i = 1;

            int j = 0;
            while (j < xSpan) {
                j += random.nextInt(xSpan);
                if (j + 3 > xSpan) break;
                Base piece = generateAndAddPiece(start, builder, random, b.minX() + j, b.minY() + random.nextInt(i) + 1, b.minZ() - 1, Direction.NORTH, depth);
                if (piece != null) {
                    BoundingBox pb = piece.boundingBox();
                    childEntranceBoxes.add(new BoundingBox(pb.minX(), pb.minY(), b.minZ(), pb.maxX(), pb.maxY(), b.minZ() + 1));
                }
                j += 4;
            }

            j = 0;
            while (j < xSpan) {
                j += random.nextInt(xSpan);
                if (j + 3 > xSpan) break;
                Base piece = generateAndAddPiece(start, builder, random, b.minX() + j, b.minY() + random.nextInt(i) + 1, b.maxZ() + 1, Direction.SOUTH, depth);
                if (piece != null) {
                    BoundingBox pb = piece.boundingBox();
                    childEntranceBoxes.add(new BoundingBox(pb.minX(), pb.minY(), b.maxZ() - 1, pb.maxX(), pb.maxY(), b.maxZ()));
                }
                j += 4;
            }

            j = 0;
            while (j < zSpan) {
                j += random.nextInt(zSpan);
                if (j + 3 > zSpan) break;
                Base piece = generateAndAddPiece(start, builder, random, b.minX() - 1, b.minY() + random.nextInt(i) + 1, b.minZ() + j, Direction.WEST, depth);
                if (piece != null) {
                    BoundingBox pb = piece.boundingBox();
                    childEntranceBoxes.add(new BoundingBox(b.minX(), pb.minY(), pb.minZ(), b.minX() + 1, pb.maxY(), pb.maxZ()));
                }
                j += 4;
            }

            j = 0;
            while (j < zSpan) {
                j += random.nextInt(zSpan);
                if (j + 3 > zSpan) break;
                Base piece = generateAndAddPiece(start, builder, random, b.maxX() + 1, b.minY() + random.nextInt(i) + 1, b.minZ() + j, Direction.EAST, depth);
                if (piece != null) {
                    BoundingBox pb = piece.boundingBox();
                    childEntranceBoxes.add(new BoundingBox(b.maxX() - 1, pb.minY(), pb.minZ(), b.maxX(), pb.maxY(), pb.maxZ()));
                }
                j += 4;
            }
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            if (isInInvalidLocation(c, box)) return;
            St s = new St(c, kind);
            BoundingBox b = boundingBox;
            generateBox(c, box, b.minX(), b.minY() + 1, b.minZ(), b.maxX(), Math.min(b.minY() + 3, b.maxY()), b.maxZ(), s.air, s.air, false);
            for (BoundingBox e : childEntranceBoxes) {
                generateBox(c, box, e.minX(), e.maxY() - 2, e.minZ(), e.maxX(), e.maxY(), e.maxZ(), s.air, s.air, false);
            }
            generateUpperHalfSphere(c, box, b.minX(), b.minY() + 4, b.minZ(), b.maxX(), b.maxY(), b.maxZ(), s.air, false);
        }

        @Override
        public void move(int dx, int dy, int dz) {
            super.move(dx, dy, dz);
            for (BoundingBox e : childEntranceBoxes) e.move(dx, dy, dz);
        }
    }

    // ================================================================================================
    // 계단 (MineShaftStairs)
    // ================================================================================================

    static final class Stairs extends Base {
        Stairs(int genDepth, BoundingBox box, Direction dir, Kind kind) {
            super(genDepth, kind, box, dir);
        }

        static BoundingBox findStairs(PiecesBuilder builder, LegacyRandom random, int x, int y, int z, Direction dir) {
            BoundingBox bb = switch (dir) {
                case SOUTH -> new BoundingBox(0, -5, 0, 2, 2, 8);
                case WEST -> new BoundingBox(-8, -5, 0, 0, 2, 2);
                case EAST -> new BoundingBox(0, -5, 0, 8, 2, 2);
                default -> new BoundingBox(0, -5, -8, 2, 2, 0);
            };
            bb.move(x, y, z);
            return builder.findCollisionPiece(bb) != null ? null : bb;
        }

        @Override
        void addChildren(Base start, PiecesBuilder builder, LegacyRandom random) {
            int depth = genDepth;
            BoundingBox b = boundingBox;
            if (orientation != null) {
                switch (orientation) {
                    case SOUTH:
                        generateAndAddPiece(start, builder, random, b.minX(), b.minY(), b.maxZ() + 1, Direction.SOUTH, depth);
                        break;
                    case WEST:
                        generateAndAddPiece(start, builder, random, b.minX() - 1, b.minY(), b.minZ(), Direction.WEST, depth);
                        break;
                    case EAST:
                        generateAndAddPiece(start, builder, random, b.maxX() + 1, b.minY(), b.minZ(), Direction.EAST, depth);
                        break;
                    case NORTH:
                    default:
                        generateAndAddPiece(start, builder, random, b.minX(), b.minY(), b.minZ() - 1, Direction.NORTH, depth);
                }
            }
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox box = c.writableBox;
            if (isInInvalidLocation(c, box)) return;
            int air = c.engine.state("minecraft:cave_air");
            generateBox(c, box, 0, 5, 0, 2, 7, 1, air, air, false);
            generateBox(c, box, 0, 0, 7, 2, 2, 8, air, air, false);
            for (int i = 0; i < 5; i++) {
                generateBox(c, box, 0, 5 - i - (i < 4 ? 1 : 0), 2 + i, 2, 7 - i, 2 + i, air, air, false);
            }
        }
    }
}
