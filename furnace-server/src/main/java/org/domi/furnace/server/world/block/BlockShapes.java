// 바닐라 BlockState.updateShape(direction, neighborState, level, pos, neighborPos) 포팅 — 블록 군(family)별 구현.
// 검증: tools/vanilla-data/ShapeVectors(바닐라 코드로 만든 무작위 이웃 구성 → 결과표)와 tools/parity-audit/ShapeDiff로 차분.
package org.domi.furnace.server.world.block;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.feature.BlockTags;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;

public final class BlockShapes {

    // Direction.values() 순서 — BlockAttrs와 같다.
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    static final int[] DX = {0, 0, 0, 0, -1, 1};
    static final int[] DY = {-1, 1, 0, 0, 0, 0};
    static final int[] DZ = {0, 0, -1, 1, 0, 0};
    static final String[] NAME = {"down", "up", "north", "south", "west", "east"};

    public static int opposite(int d) { return d ^ 1; }

    /** 수평 방향 시계 방향(N→E→S→W). */
    static int clockWise(int d) {
        return switch (d) { case NORTH -> EAST; case EAST -> SOUTH; case SOUTH -> WEST; default -> NORTH; };
    }

    static int counterClockWise(int d) {
        return switch (d) { case NORTH -> WEST; case WEST -> SOUTH; case SOUTH -> EAST; default -> NORTH; };
    }

    static int fromName(String n) {
        return switch (n) { case "down" -> DOWN; case "up" -> UP; case "north" -> NORTH; case "south" -> SOUTH; case "west" -> WEST; default -> EAST; };
    }

    static boolean isHorizontal(int d) { return d >= NORTH; }

    private static volatile BlockShapes INSTANCE;

    public static BlockShapes of(BlockRegistry registry) {
        BlockShapes s = INSTANCE;
        if (s == null || s.registry != registry) {
            synchronized (BlockShapes.class) {
                s = INSTANCE;
                if (s == null || s.registry != registry) INSTANCE = s = new BlockShapes(registry);
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ 군 분류

    private static final byte NONE = 0, FENCE = 1, BARS = 2, GATE = 3, WALL = 4, STAIRS = 5, DOOR = 6, DOUBLE_PLANT = 7, BUSH = 8,
        CARPET = 9, CHEST = 10, TORCH = 11, WALL_TORCH = 12, LADDER = 13, LANTERN = 14, SNOW = 15, SNOWY = 16, BED = 17,
        TRIPWIRE = 18, TRIPWIRE_HOOK = 19, FACE_ATTACHED = 20, PRESSURE_PLATE = 21, SIGN = 22, WALL_SIGN = 23, BANNER = 24,
        WALL_BANNER = 25, CAMPFIRE = 26, RAIL = 27, VINE = 28, SEA_PICKLE = 29, ATTACHED_STEM = 30, SOUL_FIRE = 31;

    // BushBlock.mayPlaceOn 변형
    private static final byte PLACE_DIRT = 1, PLACE_FARMLAND = 2, PLACE_DEAD_BUSH = 3, PLACE_SOUL_SAND = 4;

    private final BlockRegistry registry;
    private final BlockStateTable table;
    private final BlockAttrs attrs;
    private final byte[] family;
    private final byte[] placeOn;
    private final boolean[] fences, woodenFences, walls, stairs, exceptions, barsLike, fenceGates, wallPostOverride;
    private final boolean[] unstableBottomCenter, dirtTag, snowTag, snowCanSurvive, snowCannotSurvive, deadBushPlaceOn, farmland, soulSand, hayBlock;
    private final boolean[] isChest, isSnowLayer, isTripwire, isTripwireHook, isVineBlock;
    private final byte[] wallTests;
    private final byte[] centerMask, rigidMask, nonEmptyMask;

    private BlockShapes(BlockRegistry registry) {
        this.registry = registry;
        this.table = BlockStateTable.of(registry);
        this.attrs = BlockAttrs.get();
        int n = registry.totalStateCount();
        this.family = new byte[n];
        this.placeOn = new byte[n];
        this.fences = flag(n, tag("minecraft:fences"));
        this.woodenFences = flag(n, tag("minecraft:wooden_fences"));
        this.walls = flag(n, tag("minecraft:walls"));
        this.stairs = flag(n, tag("minecraft:stairs"));
        this.fenceGates = flag(n, tag("minecraft:fence_gates"));
        this.wallPostOverride = flag(n, tag("minecraft:wall_post_override"));
        this.unstableBottomCenter = flag(n, tag("minecraft:unstable_bottom_center"));
        this.dirtTag = flag(n, tag("minecraft:dirt"));
        this.snowTag = flag(n, tag("minecraft:snow"));
        this.snowCanSurvive = flag(n, tag("minecraft:snow_layer_can_survive_on"));
        this.snowCannotSurvive = flag(n, tag("minecraft:snow_layer_cannot_survive_on"));
        this.deadBushPlaceOn = flag(n, tag("minecraft:dead_bush_may_place_on"));
        this.farmland = flag(n, Set.of("minecraft:farmland"));
        this.soulSand = flag(n, Set.of("minecraft:soul_sand"));
        this.hayBlock = flag(n, Set.of("minecraft:hay_block"));
        this.isChest = flag(n, Set.of("minecraft:chest", "minecraft:trapped_chest"));
        this.isSnowLayer = flag(n, Set.of("minecraft:snow"));
        this.isTripwire = flag(n, Set.of("minecraft:tripwire"));
        this.isTripwireHook = flag(n, Set.of("minecraft:tripwire_hook"));
        this.isVineBlock = flag(n, Set.of("minecraft:vine"));
        Set<String> exc = new HashSet<>(tag("minecraft:shulker_boxes"));
        exc.addAll(Set.of("minecraft:barrier", "minecraft:carved_pumpkin", "minecraft:jack_o_lantern", "minecraft:melon", "minecraft:pumpkin"));
        for (String b : registry.blockNames()) if (b.endsWith("_leaves")) exc.add(b);
        this.exceptions = flag(n, exc);
        Set<String> bars = new HashSet<>();
        for (String b : registry.blockNames()) {
            if (b.equals("minecraft:iron_bars") || b.equals("minecraft:glass_pane") || b.endsWith("_stained_glass_pane")) bars.add(b);
        }
        this.barsLike = flag(n, bars);
        for (String b : registry.blockNames()) classify(b);
        for (int s = 0; s < n; s++) {
            if (fences[s]) family[s] = FENCE;
            else if (barsLike[s]) family[s] = BARS;
            else if (fenceGates[s]) family[s] = GATE;
            else if (walls[s]) family[s] = WALL;
            else if (stairs[s]) family[s] = STAIRS;
        }
        this.wallTests = loadWallTests(n);
        this.centerMask = new byte[n];
        this.rigidMask = new byte[n];
        this.nonEmptyMask = new byte[n];
        loadSupports();
    }

    private void classify(String b) {
        String name = b.substring("minecraft:".length());
        byte fam = NONE;
        byte place = 0;
        if (name.endsWith("_door")) fam = DOOR;
        else if (Set.of("tall_grass", "large_fern", "sunflower", "lilac", "rose_bush", "peony").contains(name)) { fam = DOUBLE_PLANT; place = PLACE_DIRT; }
        else if (name.endsWith("_sapling") || Set.of("dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
            "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "torchflower", "short_grass", "fern",
            "sweet_berry_bush").contains(name)) { fam = BUSH; place = PLACE_DIRT; }
        else if (Set.of("wheat", "carrots", "potatoes", "beetroots", "melon_stem", "pumpkin_stem").contains(name)) { fam = BUSH; place = PLACE_FARMLAND; }
        else if (name.equals("dead_bush")) { fam = BUSH; place = PLACE_DEAD_BUSH; }
        else if (name.equals("nether_wart")) { fam = BUSH; place = PLACE_SOUL_SAND; }
        else if (name.equals("attached_melon_stem") || name.equals("attached_pumpkin_stem")) { fam = ATTACHED_STEM; place = PLACE_DIRT; }
        else if (name.endsWith("_carpet")) fam = CARPET;
        else if (name.equals("chest") || name.equals("trapped_chest")) fam = CHEST;
        else if (name.equals("torch") || name.equals("soul_torch") || name.equals("redstone_torch")) fam = TORCH;
        else if (name.equals("wall_torch") || name.equals("soul_wall_torch") || name.equals("redstone_wall_torch")) fam = WALL_TORCH;
        else if (name.equals("ladder")) fam = LADDER;
        else if (name.equals("lantern") || name.equals("soul_lantern")) fam = LANTERN;
        else if (name.equals("snow")) fam = SNOW;
        else if (name.equals("grass_block") || name.equals("podzol") || name.equals("mycelium")) fam = SNOWY;
        else if (name.endsWith("_bed")) fam = BED;
        else if (name.equals("tripwire")) fam = TRIPWIRE;
        else if (name.equals("tripwire_hook")) fam = TRIPWIRE_HOOK;
        else if (name.endsWith("_button") || name.equals("lever")) fam = FACE_ATTACHED;
        else if (name.endsWith("_pressure_plate")) fam = PRESSURE_PLATE;
        else if (name.endsWith("_wall_sign")) fam = WALL_SIGN;
        else if (name.endsWith("_sign") && !name.endsWith("hanging_sign")) fam = SIGN;
        else if (name.endsWith("_wall_banner")) fam = WALL_BANNER;
        else if (name.endsWith("_banner")) fam = BANNER;
        else if (name.equals("campfire") || name.equals("soul_campfire")) fam = CAMPFIRE;
        else if (name.equals("rail") || name.equals("powered_rail") || name.equals("detector_rail") || name.equals("activator_rail")) fam = RAIL;
        else if (name.equals("vine")) fam = VINE;
        else if (name.equals("sea_pickle")) fam = SEA_PICKLE;
        else if (name.equals("soul_fire")) fam = SOUL_FIRE;
        if (fam == NONE) return;
        for (int id : registry.stateIdsForBlock(b)) { family[id] = fam; placeOn[id] = place; }
    }

    private boolean[] flag(int n, Set<String> names) {
        boolean[] arr = new boolean[n];
        for (String name : names) for (int id : registry.stateIdsForBlock(name)) arr[id] = true;
        return arr;
    }

    private static Set<String> tag(String t) { return new HashSet<>(BlockTags.get().blocksOf(t)); }

    private static byte[] loadWallTests(int n) {
        byte[] t = new byte[n];
        try (InputStream raw = BlockShapes.class.getClassLoader().getResourceAsStream("blocks/blockwalltests_1.21.1.txt.gz")) {
            if (raw == null) throw new IllegalStateException("담장 테스트 리소스 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int sp = line.indexOf(' ');
                t[Integer.parseInt(line.substring(0, sp))] = (byte) Integer.parseInt(line.substring(sp + 1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return t;
    }

    private void loadSupports() {
        try (InputStream raw = BlockShapes.class.getClassLoader().getResourceAsStream("blocks/blocksupports_1.21.1.txt.gz")) {
            if (raw == null) throw new IllegalStateException("지지 마스크 리소스 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] p = line.split(" ");
                int id = Integer.parseInt(p[0]);
                centerMask[id] = (byte) Integer.parseInt(p[1]);
                rigidMask[id] = (byte) Integer.parseInt(p[2]);
                if (p.length > 3) nonEmptyMask[id] = (byte) Integer.parseInt(p[3]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ updateShape

    /**
     * 바닐라 BlockState.updateShape. state의 (x,y,z)에서 dir 쪽 이웃(neighbor)이 바뀌었다고 보고 새 state를 돌려준다
     * (공기로 바뀌면 0). level은 중심 블록의 다른 이웃을 읽는 용도.
     */
    public int updateShape(ShapeLevel level, int state, int dir, int neighbor, int x, int y, int z) {
        switch (family[state]) {
            case FENCE:
                return isHorizontal(dir) ? with(state, NAME[dir], connectsToFence(state, neighbor, opposite(dir))) : state;
            case BARS:
                return isHorizontal(dir) ? with(state, NAME[dir], attachesToBars(neighbor, opposite(dir))) : state;
            case GATE:
                return gate(level, state, dir, neighbor, x, y, z);
            case WALL:
                return wall(level, state, dir, neighbor, x, y, z);
            case STAIRS:
                return isHorizontal(dir) ? with(state, "shape", stairsShape(level, state, x, y, z)) : state;
            case DOOR: {
                boolean lower = "lower".equals(table.prop(state, "half"));
                if (axisOf(dir) != 1 || lower != (dir == UP)) {
                    return lower && dir == DOWN && !canSurvive(level, state, x, y, z) ? 0 : state;
                }
                return family[neighbor] == DOOR && !table.prop(neighbor, "half").equals(table.prop(state, "half"))
                    ? table.with(neighbor, "half", table.prop(state, "half")) : 0;
            }
            case DOUBLE_PLANT: {
                boolean lower = "lower".equals(table.prop(state, "half"));
                boolean vertical = axisOf(dir) == 1;
                if (!vertical || lower != (dir == UP) || (sameBlock(neighbor, state) && !table.prop(neighbor, "half").equals(table.prop(state, "half")))) {
                    // BushBlock.updateShape: 생존 못 하면 공기
                    return canSurvive(level, state, x, y, z) ? state : 0;
                }
                return 0;
            }
            case BUSH:
            case CARPET:
            case SNOW:
            case SEA_PICKLE:
                return canSurvive(level, state, x, y, z) ? state : 0;
            case ATTACHED_STEM: {
                // StemBlock 쪽 열매가 사라지면 줄기로 되돌아감 — 열매 블록 이름 판단
                return canSurvive(level, state, x, y, z) ? attachedStem(state, dir, neighbor) : 0;
            }
            case CHEST:
                return chest(state, dir, neighbor);
            case TORCH:
                return dir == DOWN && !canSurvive(level, state, x, y, z) ? 0 : state;
            case WALL_TORCH:
            case LADDER:
            case WALL_SIGN:
            case WALL_BANNER:
                return opposite(dir) == fromName(table.prop(state, "facing")) && !canSurvive(level, state, x, y, z) ? 0 : state;
            case LANTERN: {
                int connected = "true".equals(table.prop(state, "hanging")) ? DOWN : UP;
                return opposite(connected) == dir && !canSurvive(level, state, x, y, z) ? 0 : state;
            }
            case SNOWY:
                return dir == UP ? with(state, "snowy", snowTag[neighbor]) : state;
            case BED:
                return bed(state, dir, neighbor);
            case TRIPWIRE:
                return isHorizontal(dir) ? with(state, NAME[dir], tripwireConnects(neighbor, dir)) : state;
            case TRIPWIRE_HOOK:
                return opposite(dir) == fromName(table.prop(state, "facing")) && !canSurvive(level, state, x, y, z) ? 0 : state;
            case FACE_ATTACHED:
                return opposite(faceAttachedDir(state)) == dir && !canSurvive(level, state, x, y, z) ? 0 : state;
            case PRESSURE_PLATE:
            case SIGN:
            case BANNER:
                return dir == DOWN && !canSurvive(level, state, x, y, z) ? 0 : state;
            case CAMPFIRE:
                return dir == DOWN ? with(state, "signal_fire", hayBlock[neighbor]) : state;
            case VINE:
                return vine(level, state, dir, x, y, z);
            case SOUL_FIRE:
                return canSurvive(level, state, x, y, z) ? state : 0;
            default:
                return state;
        }
    }

    private int with(int state, String prop, boolean v) { return table.with(state, prop, v); }

    private int with(int state, String prop, String v) { return table.with(state, prop, v); }

    private boolean sturdy(int state, int dir) { return attrs.isFaceSturdy(state, dir); }

    private boolean sameBlock(int a, int b) { return table.name(a).equals(table.name(b)); }

    // ------------------------------------------------------------------ canSurvive

    private boolean canSupportCenter(ShapeLevel level, int x, int y, int z, int dir) {
        int s = level.getBlock(x, y, z);
        if (dir == DOWN && unstableBottomCenter[s]) return false;
        return (centerMask[s] & (1 << dir)) != 0;
    }

    private boolean canSupportRigid(ShapeLevel level, int x, int y, int z) {
        return (rigidMask[level.getBlock(x, y, z)] & (1 << UP)) != 0;
    }

    /** 바닐라 Block.canSurvive — 지원하는 군만 구현, 나머지는 항상 true. */
    public boolean canSurvive(ShapeLevel level, int state, int x, int y, int z) {
        int below = level.getBlock(x, y - 1, z);
        switch (family[state]) {
            case DOOR:
                return "lower".equals(table.prop(state, "half")) ? sturdy(below, UP) : sameBlock(below, state) && "lower".equals(table.prop(below, "half"));
            case DOUBLE_PLANT:
                if ("upper".equals(table.prop(state, "half"))) return sameBlock(below, state) && "lower".equals(table.prop(below, "half"));
                return mayPlaceOn(state, below);
            case BUSH:
            case ATTACHED_STEM:
                return mayPlaceOn(state, below);
            case CARPET:
                return !attrs.isAir(below);
            case TORCH:
                return canSupportCenter(level, x, y - 1, z, UP);
            case WALL_TORCH: {
                int facing = fromName(table.prop(state, "facing"));
                int o = opposite(facing);
                return sturdy(level.getBlock(x + DX[o], y + DY[o], z + DZ[o]), facing);
            }
            case LADDER: {
                int facing = fromName(table.prop(state, "facing"));
                int o = opposite(facing);
                return sturdy(level.getBlock(x + DX[o], y + DY[o], z + DZ[o]), facing);
            }
            case LANTERN: {
                int connected = "true".equals(table.prop(state, "hanging")) ? DOWN : UP;
                int d = opposite(connected);   // 지지 블록이 있는 방향
                return canSupportCenter(level, x + DX[d], y + DY[d], z + DZ[d], opposite(d));
            }
            case SNOW: {
                if (snowCannotSurvive[below]) return false;
                if (snowCanSurvive[below]) return true;
                return attrs.isCollisionFaceFull(below, UP) || isSnowLayer[below] && "8".equals(table.prop(below, "layers"));
            }
            case SEA_PICKLE:
                return (nonEmptyMask[below] & (1 << UP)) != 0 || sturdy(below, UP);
            case TRIPWIRE_HOOK: {
                int facing = fromName(table.prop(state, "facing"));
                int o = opposite(facing);
                return isHorizontal(facing) && sturdy(level.getBlock(x + DX[o], y + DY[o], z + DZ[o]), facing);
            }
            case FACE_ATTACHED: {
                int d = faceAttachedDir(state);
                int o = opposite(d);   // 지지 블록 방향
                return sturdy(level.getBlock(x + DX[o], y + DY[o], z + DZ[o]), d);
            }
            case PRESSURE_PLATE:
                return canSupportRigid(level, x, y - 1, z) || canSupportCenter(level, x, y - 1, z, UP);
            case SIGN:
            case BANNER:
                return attrs.isSolid(below);
            case WALL_SIGN:
            case WALL_BANNER: {
                int facing = fromName(table.prop(state, "facing"));
                int o = opposite(facing);
                return attrs.isSolid(level.getBlock(x + DX[o], y + DY[o], z + DZ[o]));
            }
            case RAIL:
                return canSupportRigid(level, x, y - 1, z);
            case SOUL_FIRE:
                return soulFireBase(below);
            default:
                return true;
        }
    }

    private boolean[] soulFireBaseCache;

    private boolean soulFireBase(int below) {
        if (soulFireBaseCache == null) soulFireBaseCache = flag(registry.totalStateCount(), tag("minecraft:soul_fire_base_blocks"));
        return soulFireBaseCache[below];
    }

    private boolean mayPlaceOn(int state, int below) {
        return switch (placeOn[state]) {
            case PLACE_FARMLAND -> farmland[below];
            case PLACE_DEAD_BUSH -> deadBushPlaceOn[below];
            case PLACE_SOUL_SAND -> soulSand[below];
            default -> dirtTag[below] || farmland[below];
        };
    }

    private int faceAttachedDir(int state) {
        String face = table.prop(state, "face");
        return switch (face) {
            case "ceiling" -> DOWN;
            case "floor" -> UP;
            default -> fromName(table.prop(state, "facing"));
        };
    }

    // -- 개별 군 --

    private int attachedStem(int state, int dir, int neighbor) {
        // AttachedStemBlock.updateShape: 열매가 사라지면 age=7 줄기로 — 상태 id 변환은 이름으로
        int facing = fromName(table.prop(state, "facing"));
        String fruit = table.name(state).equals("minecraft:attached_melon_stem") ? "minecraft:melon" : "minecraft:pumpkin";
        if (!table.name(neighbor).equals(fruit) && dir == facing) {
            String stem = table.name(state).equals("minecraft:attached_melon_stem") ? "minecraft:melon_stem" : "minecraft:pumpkin_stem";
            return registry.exactStateId(stem + ";age=7");
        }
        return state;
    }

    private int chest(int state, int dir, int neighbor) {
        if (isChest[neighbor] && isHorizontal(dir) && sameBlock(neighbor, state)) {
            String nType = table.prop(neighbor, "type");
            if ("single".equals(table.prop(state, "type")) && !"single".equals(nType)
                && table.prop(state, "facing").equals(table.prop(neighbor, "facing")) && chestConnected(neighbor) == opposite(dir)) {
                return with(state, "type", "left".equals(nType) ? "right" : "left");
            }
        } else if (chestConnected(state) == dir) {
            return with(state, "type", "single");
        }
        return state;
    }

    private int chestConnected(int state) {
        int facing = fromName(table.prop(state, "facing"));
        return "left".equals(table.prop(state, "type")) ? clockWise(facing) : counterClockWise(facing);
    }

    private int bed(int state, int dir, int neighbor) {
        int facing = fromName(table.prop(state, "facing"));
        int toward = "foot".equals(table.prop(state, "part")) ? facing : opposite(facing);
        if (dir == toward) {
            return sameBlock(neighbor, state) && !table.prop(neighbor, "part").equals(table.prop(state, "part"))
                ? with(state, "occupied", table.prop(neighbor, "occupied")) : 0;
        }
        return state;
    }

    private boolean tripwireConnects(int neighbor, int dir) {
        if (isTripwireHook[neighbor]) return fromName(table.prop(neighbor, "facing")) == opposite(dir);
        return isTripwire[neighbor];
    }

    // -- vine --

    private boolean faceFull(int state, int face) {
        return attrs.isFaceSturdy(state, face) || attrs.isCollisionFaceFull(state, face);
    }

    /** VineBlock.isAcceptableNeighbour(level, pos, dir) = canAttachTo: 이웃의 dir.opposite 면이 가득. */
    private boolean acceptable(ShapeLevel level, int x, int y, int z, int dir) {
        return faceFull(level.getBlock(x, y, z), opposite(dir));
    }

    private int vine(ShapeLevel level, int state, int dir, int x, int y, int z) {
        if (dir == DOWN) return state;
        int s = state;
        if ("true".equals(table.prop(s, "up"))) s = with(s, "up", acceptable(level, x, y + 1, z, DOWN));
        int above = level.getBlock(x, y + 1, z);
        for (int d : new int[]{NORTH, EAST, SOUTH, WEST}) {
            if (!"true".equals(table.prop(s, NAME[d]))) continue;
            boolean supported = acceptable(level, x + DX[d], y, z + DZ[d], d);
            if (!supported) supported = isVineBlock[above] && "true".equals(table.prop(above, NAME[d]));
            s = with(s, NAME[d], supported);
        }
        boolean any = "true".equals(table.prop(s, "up"));
        for (int d : new int[]{NORTH, EAST, SOUTH, WEST}) any |= "true".equals(table.prop(s, NAME[d]));
        return any ? s : 0;
    }

    // -- fence / bars --

    private boolean connectsToFence(int self, int neighbor, int dirFromNeighbor) {
        boolean sturdy = sturdy(neighbor, dirFromNeighbor);
        boolean same = fences[neighbor] && woodenFences[neighbor] == woodenFences[self];
        boolean gate = fenceGates[neighbor] && gateConnects(neighbor, dirFromNeighbor);
        return !exceptions[neighbor] && sturdy || same || gate;
    }

    private boolean gateConnects(int gate, int dir) {
        int facing = fromName(table.prop(gate, "facing"));
        return axisOf(facing) == axisOf(clockWise(dir));
    }

    private static int axisOf(int d) { return d == UP || d == DOWN ? 1 : (d == NORTH || d == SOUTH ? 2 : 0); }

    private boolean attachesToBars(int neighbor, int dirFromNeighbor) {
        return !exceptions[neighbor] && sturdy(neighbor, dirFromNeighbor) || barsLike[neighbor] || walls[neighbor];
    }

    // -- fence gate --

    private int gate(ShapeLevel level, int state, int dir, int neighbor, int x, int y, int z) {
        int facing = fromName(table.prop(state, "facing"));
        if (axisOf(dir) == axisOf(clockWise(facing))) {
            int opp = opposite(dir);
            int other = level.getBlock(x + DX[opp], y + DY[opp], z + DZ[opp]);
            boolean inWall = walls[neighbor] || walls[other];
            return with(state, "in_wall", inWall);
        }
        return state;
    }

    // -- wall --

    private boolean wallConnects(int self, int neighbor, boolean sturdy, int dirFromNeighbor) {
        boolean gate = fenceGates[neighbor] && gateConnects(neighbor, dirFromNeighbor);
        return walls[neighbor] || !exceptions[neighbor] && sturdy || barsLike[neighbor] || gate;
    }

    private static boolean notNone(String v) { return !"none".equals(v); }

    private int wall(ShapeLevel level, int state, int dir, int neighbor, int x, int y, int z) {
        if (dir == DOWN) return state;
        if (dir == UP) return wallTop(level, state, x, y, z, neighbor);
        // sideUpdate
        int opp = opposite(dir);
        boolean n = dir == NORTH ? wallConnects(state, neighbor, sturdy(neighbor, opp), opp) : notNone(table.prop(state, "north"));
        boolean e = dir == EAST ? wallConnects(state, neighbor, sturdy(neighbor, opp), opp) : notNone(table.prop(state, "east"));
        boolean s = dir == SOUTH ? wallConnects(state, neighbor, sturdy(neighbor, opp), opp) : notNone(table.prop(state, "south"));
        boolean w = dir == WEST ? wallConnects(state, neighbor, sturdy(neighbor, opp), opp) : notNone(table.prop(state, "west"));
        int above = level.getBlock(x, y + 1, z);
        return wallFinish(state, above, n, e, s, w);
    }

    private int wallTop(ShapeLevel level, int state, int x, int y, int z, int aboveState) {
        boolean n = notNone(table.prop(state, "north"));
        boolean e = notNone(table.prop(state, "east"));
        boolean s = notNone(table.prop(state, "south"));
        boolean w = notNone(table.prop(state, "west"));
        return wallFinish(state, aboveState, n, e, s, w);
    }

    private int wallFinish(int state, int above, boolean n, boolean e, boolean s, boolean w) {
        int tests = wallTests[above];
        int st = state;
        st = with(st, "north", wallSide(n, tests, 2));
        st = with(st, "east", wallSide(e, tests, 16));
        st = with(st, "south", wallSide(s, tests, 4));
        st = with(st, "west", wallSide(w, tests, 8));
        return with(st, "up", wallPost(st, above, tests));
    }

    /** 바닐라 a(boolean,exv,exv): 연결이면 위 블록 아래면이 테스트 상자를 덮으면 tall, 아니면 low. */
    private static String wallSide(boolean connected, int tests, int mask) {
        if (!connected) return "none";
        return (tests & mask) != 0 ? "tall" : "low";
    }

    private boolean wallPost(int state, int above, int tests) {
        if (walls[above] && "true".equals(table.prop(above, "up"))) return true;
        String n = table.prop(state, "north"), e = table.prop(state, "east"), s = table.prop(state, "south"), w = table.prop(state, "west");
        boolean noN = "none".equals(n), noE = "none".equals(e), noS = "none".equals(s), noW = "none".equals(w);
        boolean straightOpen = noN && noE && noW && noS || noN != noS || noW != noE;
        if (straightOpen) return true;
        boolean tallPair = "tall".equals(n) && "tall".equals(s) || "tall".equals(e) && "tall".equals(w);
        if (tallPair) return false;
        return isWallPostOverride(above) || (tests & 1) != 0;
    }

    private boolean isWallPostOverride(int state) { return wallPostOverride[state]; }

    // -- stairs --

    private String stairsShape(ShapeLevel level, int state, int x, int y, int z) {
        int facing = fromName(table.prop(state, "facing"));
        String half = table.prop(state, "half");
        int ahead = level.getBlock(x + DX[facing], y, z + DZ[facing]);
        if (stairs[ahead] && half.equals(table.prop(ahead, "half"))) {
            int d = fromName(table.prop(ahead, "facing"));
            if (axisOf(d) != axisOf(facing) && canTakeShape(level, state, x, y, z, opposite(d))) {
                return d == counterClockWise(facing) ? "outer_left" : "outer_right";
            }
        }
        int behindDir = opposite(facing);
        int behind = level.getBlock(x + DX[behindDir], y, z + DZ[behindDir]);
        if (stairs[behind] && half.equals(table.prop(behind, "half"))) {
            int d = fromName(table.prop(behind, "facing"));
            if (axisOf(d) != axisOf(facing) && canTakeShape(level, state, x, y, z, d)) {
                return d == counterClockWise(facing) ? "inner_left" : "inner_right";
            }
        }
        return "straight";
    }

    private boolean canTakeShape(ShapeLevel level, int state, int x, int y, int z, int face) {
        int bs = level.getBlock(x + DX[face], y + DY[face], z + DZ[face]);
        return !stairs[bs] || !table.prop(bs, "facing").equals(table.prop(state, "facing")) || !table.prop(bs, "half").equals(table.prop(state, "half"));
    }
}
