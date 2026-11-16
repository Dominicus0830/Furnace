package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.TickPriority;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-204 3단계(설계 V4): 예약 틱·getTickDelay·hasPostProcess, 그리고 놓기 상태 다리 — FAWE PlacementStateProcessor 꼴(값을 바꿔 끼우는 문맥 + 편집 내용을
 * 답하는 레벨 프록시)로 부른 {@code getStateForPlacement}·{@code canSurvive} 가 같은 세계에서 서버 블록 동작을 직접 부른 값과 블록 전수 × 면 × 클릭 자리마다
 * 같은가. 프록시 길(가상 메서드로 읽기)과 서버 월드 길(서버 레벨 직접) 둘 다 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsPlacementTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    int baseX, baseZ;
    static final int Y = 150;
    static final int MAX = Integer.MAX_VALUE;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Placer");
        server.tick(3);
        var loc = tp.player().location();
        baseX = Math.floorDiv((int) Math.floor(loc.x()), 16) * 16;
        baseZ = Math.floorDiv((int) Math.floor(loc.z()), 16) * 16;
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        server.execute("forceload add " + (baseX - 16) + " " + (baseZ - 16) + " " + (baseX + 47) + " " + (baseZ + 111));
        server.tick(5);
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(BlockPos p, Supplier<T> task) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), task); }

    void run(BlockPos p, Runnable task) { on(p, () -> { task.run(); return null; }); }

    void quiet(BlockPos p, BlockState s) { run(p, () -> level.setBlock(p, s, 2 | 16)); }

    /** 놓기 반응 없이(Paper LevelChunk.setBlockState(…, doPlace = false)) — 모래의 onPlace 예약도 없다. */
    void unplaced(BlockPos p, BlockState s) { run(p, () -> level.getChunkAt(p).setBlockState(p, s, false, false)); }

    org.domi.furnace.server.world.RegionLevel serverLevel(BlockPos p) {
        return server.internals().world().regionizer().ownerOf(p.getX() >> 4, p.getZ() >> 4).level();
    }

    static Pos pos(BlockPos p) { return new Pos(p.getX(), p.getY(), p.getZ()); }

    static BlockState named(String block, String... props) {
        var b = org.domi.furnace.server.block.Blocks.get();
        int s = b.defaultState(block);
        for (int i = 0; i < props.length; i += 2) s = b.prop(props[i]).with(s, props[i + 1]);
        return Block.stateById(s);
    }

    // ------------------------------------------------------------------ 예약 틱·지연·후처리

    /** 그 청크 예약 목록에서 그 칸의 것(i 종류, t 남은 틱, p 우선순위) — 서버 저장 꼴. */
    String savedTick(BlockPos p, boolean fluid) {
        return on(p, () -> {
            var root = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            serverLevel(p).saveChunkTicks(p.getX() >> 4, p.getZ() >> 4, root, false);
            var list = (org.domi.furnace.server.net.io.nbt.NbtList) root.tags().get(fluid ? "fluid_ticks" : "block_ticks");
            for (Object o : list.items()) {
                var c = (org.domi.furnace.server.net.io.nbt.NbtCompound) o;
                if (c.getInt("x") == p.getX() && c.getInt("y") == p.getY() && c.getInt("z") == p.getZ()) {
                    return c.getString("i") + " t" + c.getInt("t") + " p" + c.getInt("p");
                }
            }
            return null;
        });
    }

    @Test
    void scheduledTicks() throws Exception {
        BlockPos sand = new BlockPos(baseX + 8, Y, baseZ + 8);
        unplaced(sand, Blocks.SAND.defaultBlockState());
        server.tick(2);
        assertEquals(Blocks.SAND.defaultBlockState(), on(sand, () -> level.getBlockState(sand)), "놓기 반응 없이 깐 모래는 뜬 채로");
        run(sand, () -> level.scheduleTick(sand, Blocks.SAND, 4, TickPriority.VERY_HIGH));
        assertEquals("minecraft:sand t4 p-2", savedTick(sand, false), "서버 예약(종류·지연·우선순위)");
        server.tick(6);
        assertEquals(Blocks.AIR.defaultBlockState(), on(sand, () -> level.getBlockState(sand)), "예약 틱에 떨어졌다");

        // 유체: 조용히 깐 물 원천은 흐르지 않는다 — 예약하면 흐른다. 지연은 getTickDelay
        BlockPos water = new BlockPos(baseX + 8, Y, baseZ + 24);
        quiet(water.below(), Blocks.STONE.defaultBlockState());
        quiet(water, Blocks.WATER.defaultBlockState());
        server.tick(10);
        // 바닐라 LiquidBlock.onPlace 가 예약을 걸므로 물은 이미 흐를 수 있다 — 예약 자체만 본다
        BlockPos water2 = new BlockPos(baseX + 8, Y, baseZ + 40);
        run(water2, () -> level.scheduleTick(water2, Fluids.WATER, Fluids.WATER.getTickDelay(level)));
        assertEquals("minecraft:water t5 p0", savedTick(water2, true));
        run(water2, () -> level.scheduleTick(water2.east(), Fluids.FLOWING_LAVA, 7, TickPriority.EXTREMELY_LOW));
        assertEquals("minecraft:flowing_lava t7 p3", savedTick(water2.east(), true));

        // 다른 스레드: 주인 작업으로 넘긴다(기다리지 않음) — 다음 틱에 예약이 있다
        BlockPos sand2 = new BlockPos(baseX + 9, Y, baseZ + 8);
        unplaced(sand2, Blocks.SAND.defaultBlockState());
        Thread t = new Thread(() -> level.scheduleTick(sand2, Blocks.SAND, 3), "worker");
        t.start();
        t.join(5_000);
        server.tick(1);
        assertEquals("minecraft:sand t2 p0", savedTick(sand2, false), "받은 틱부터 센다");
        server.tick(4);
        assertEquals(Blocks.AIR.defaultBlockState(), on(sand2, () -> level.getBlockState(sand2)));

        // 로드 안 된 원점: 아무것도 안 함
        run(sand, () -> level.scheduleTick(new BlockPos(baseX + 90_000, Y, baseZ), Blocks.SAND, 1));
    }

    @Test
    void tickDelayAndPostProcess() {
        assertEquals(0, Fluids.EMPTY.getTickDelay(level));
        assertEquals(5, Fluids.WATER.getTickDelay(level));
        assertEquals(5, Fluids.FLOWING_WATER.getTickDelay(level));
        assertEquals(30, Fluids.LAVA.getTickDelay(level), "초고온이 아닌 차원");
        assertEquals(30, Fluids.FLOWING_LAVA.getTickDelay(level));
        assertFalse(NmsChunks.bridge().ultraWarm(server.overworld()));
        Set<String> post = new LinkedHashSet<>();
        for (int id = 0; id < NmsBlocks.stateCount(); id++) {
            BlockState s = Block.stateById(id);
            if (s.hasPostProcess(level, BlockPos.ZERO)) post.add(s.getBlock().toString());
        }
        assertEquals(Set.of("Block{minecraft:brown_mushroom}", "Block{minecraft:red_mushroom}", "Block{minecraft:magma_block}"), post,
                "Properties.hasPostProcess(Blocks::always) — 1.21.1 jar");
    }

    // ------------------------------------------------------------------ 놓기 상태 다리(V4)

    /** FAWE PaperweightFaweMutableBlockPlaceContext 와 같은 꼴(시험용 사본 — 값을 바꿔 끼우는 문맥, 플레이어 없음). */
    static final class FaweContext extends BlockPlaceContext {
        static final BlockHitResult DEFAULT = new BlockHitResult(new Vec3(MAX, MAX, MAX), Direction.NORTH, new BlockPos(MAX, MAX, MAX), false);
        private final Level level;
        private BlockHitResult hitResult;
        private Direction direction;
        private BlockPos relativePos;

        FaweContext(Level level) {
            super(level, null, null, null, DEFAULT);
            this.level = level;
            this.replaceClicked = false;
        }

        FaweContext withSetting(BlockHitResult hit, Direction dir) {
            this.hitResult = hit;
            this.direction = dir;
            this.relativePos = hit.getBlockPos().relative(hit.getDirection());
            return this;
        }

        @Override public BlockPos getClickedPos() { return relativePos; }
        @Override public Direction getClickedFace() { return hitResult.getDirection(); }
        @Override public Vec3 getClickLocation() { return hitResult.getLocation(); }
        @Override public boolean isInside() { return hitResult.isInside(); }
        @Override public ItemStack getItemInHand() { return ItemStack.EMPTY; }
        @Override public Player getPlayer() { return null; }
        @Override public InteractionHand getHand() { return null; }
        @Override public Level getLevel() { return level; }
        @Override public Direction getHorizontalDirection() { return direction.getAxis() == Direction.Axis.Y ? Direction.NORTH : direction; }
        @Override public boolean isSecondaryUseActive() { return false; }
        @Override public float getRotation() { return (float) (direction.get2DDataValue() * 90); }
        @Override public boolean canPlace() { return getLevel().getBlockState(getClickedPos()).canBeReplaced(this); }
        @Override public boolean replacingClickedOnBlock() { return false; }
        @Override public Direction getNearestLookingDirection() { return direction; }
        @Override public Direction getNearestLookingVerticalDirection() { return direction.getAxis() == Direction.Axis.Y ? Direction.UP : Direction.DOWN; }
        @Override public Direction[] getNearestLookingDirections() { return new Direction[] {direction}; }
    }

    /** FAWE PaperweightLevelProxy 꼴: 생성자 없이 만든 ServerLevel 이 읽기만 재정의(여기서는 실제 월드를 그대로 답한다). */
    static class ProxyLevel extends ServerLevel {
        ServerLevel real;

        private ProxyLevel() { super(null); }

        static ProxyLevel of(ServerLevel real) throws Exception {
            var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            ProxyLevel p = (ProxyLevel) ((sun.misc.Unsafe) f.get(null)).allocateInstance(ProxyLevel.class);
            p.real = real;
            return p;
        }

        /** 실제 월드 없이 늘 공기(높이 -64..320). */
        static ProxyLevel ofAir() throws Exception { return of(null); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return pos.getX() == MAX || real == null ? null : real.getBlockEntity(pos); }

        @Override
        public BlockState getBlockState(BlockPos pos) { return pos.getX() == MAX || real == null ? Blocks.AIR.defaultBlockState() : real.getBlockState(pos); }

        @Override
        public FluidState getFluidState(BlockPos pos) { return pos.getX() == MAX ? Fluids.EMPTY.defaultFluidState() : getBlockState(pos).getFluidState(); }

        @Override
        public net.minecraft.world.level.border.WorldBorder getWorldBorder() { return real.getWorldBorder(); }

        @Override
        public int getHeight() { return real == null ? 384 : real.getHeight(); }

        @Override
        public int getMinBuildHeight() { return real == null ? -64 : real.getMinBuildHeight(); }
    }

    /** 서버 쪽 같은 문맥(FAWE 문맥의 값을 서버 PlaceContext 로) — 정답. */
    static final class ServerFawe extends PlaceContext {
        final Pos rel;
        final int face, dir;
        final double x, y, z;

        ServerFawe(BlockLevel lv, Pos hitPos, int face, double x, double y, double z) {
            super(lv, 0, new Hit(hitPos, face, x, y, z, false), false);
            this.rel = hitPos.relative(face);
            this.face = face;
            this.dir = Dir.opposite(face);
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override public Pos clickedPos() { return rel; }
        @Override public int clickedFace() { return face; }
        @Override public double clickX() { return x; }
        @Override public double clickY() { return y; }
        @Override public double clickZ() { return z; }
        @Override public boolean isInside() { return false; }
        @Override public org.domi.furnace.server.entity.ItemStack itemInHand() { return org.domi.furnace.server.entity.ItemStack.EMPTY; }
        @Override public int horizontalDirection() { return Dir.axis(dir) == Dir.AXIS_Y ? Dir.NORTH : dir; }
        @Override public boolean isSecondaryUseActive() { return false; }
        @Override public float rotation() { return Dir.to2D(dir) * 90; }

        @Override
        public boolean canPlace() {
            int s = level().getBlockState(rel);
            return BlockBehaviors.of(s).canBeReplaced(s, this);
        }

        @Override public boolean replacingClickedOnBlock() { return false; }
        @Override public int nearestLookingDirection() { return dir; }
        @Override public int nearestLookingVerticalDirection() { return Dir.axis(dir) == Dir.AXIS_Y ? Dir.UP : Dir.DOWN; }
        @Override public int[] nearestLookingDirections() { return new int[] {dir}; }
    }

    /** 놓기 계산이 레벨 난수를 쓰는 블록(덩굴·켈프 나이, 산호 블록의 예약 틱 지연) — 1.21.1 서버 블록 동작에서 찾은 것. */
    static final Set<String> RANDOM_PLACEMENT = Set.of("minecraft:weeping_vines", "minecraft:twisting_vines", "minecraft:cave_vines",
            "minecraft:kelp", "minecraft:tube_coral_block", "minecraft:brain_coral_block", "minecraft:bubble_coral_block", "minecraft:fire_coral_block", "minecraft:horn_coral_block");

    /** 덩굴·켈프 나이(난수)는 비교에서 0 으로 맞춘다 — 서버 난수 소비 순서가 두 길에서 다르다. */
    static int noAge(int s) {
        var b = org.domi.furnace.server.block.Blocks.get();
        String n = b.nameOfState(s);
        return n.endsWith("_vines") || n.equals("minecraft:kelp") ? b.prop("age").with(s, 0) : s;
    }

    static String outcome(Supplier<Integer> s) {
        try {
            Integer v = s.get();
            return v == null || v < 0 ? "null" : "s" + noAge(v);
        } catch (UnsupportedNmsCall e) {
            return "UnsupportedNmsCall(" + e.getMessage() + ")";
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    /** 장면들(놓을 칸 C) — 열린 바닥, 물 안, 이어지는 이웃, 신호, 천장, 비교기(블록 엔티티로 읽는 신호). */
    List<BlockPos> scenes;

    /** 한 번만 깐다(비교기는 다시 놓으면 꺼진 상태로 돌아가고 같은 레드스톤 블록 놓기는 알림이 없다). */
    synchronized List<BlockPos> scenes() {
        if (scenes == null) scenes = buildScenes();
        return scenes;
    }

    List<BlockPos> buildScenes() {
        List<BlockPos> out = new ArrayList<>();
        BlockState stone = Blocks.STONE.defaultBlockState();
        int x = baseX + 8;
        BlockPos c0 = new BlockPos(x, Y, baseZ + 56);   // 열린 바닥
        quiet(c0.below(), stone);
        out.add(c0);
        BlockPos c1 = new BlockPos(x, Y, baseZ + 72);   // 물 원천 안(둘레 돌)
        for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN}) quiet(c1.relative(d), stone);
        quiet(c1, Blocks.WATER.defaultBlockState());
        out.add(c1);
        BlockPos c2 = new BlockPos(x, Y, baseZ + 88);   // 이어지는 이웃: 북 울타리, 동 벽, 남 판유리, 서 계단, 아래·위 돌
        quiet(c2.below(), stone);
        quiet(c2.above(), stone);
        quiet(c2.north(), Blocks.OAK_FENCE.defaultBlockState());
        quiet(c2.east(), Blocks.COBBLESTONE_WALL.defaultBlockState());
        quiet(c2.south(), Blocks.GLASS_PANE.defaultBlockState());
        quiet(c2.west(), Blocks.OAK_STAIRS.defaultBlockState());
        out.add(c2);
        BlockPos c3 = new BlockPos(x + 16, Y, baseZ + 56);   // 신호: 서쪽 레드스톤 블록
        quiet(c3.below(), stone);
        quiet(c3.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        out.add(c3);
        BlockPos c4 = new BlockPos(x + 16, Y, baseZ + 72);   // 천장만
        quiet(c4.above(), stone);
        out.add(c4);
        BlockPos c5 = new BlockPos(x + 16, Y, baseZ + 88);   // 동쪽 비교기(동향 — C 로 낸다), 그 뒤 레드스톤 블록
        quiet(c5.below(), stone);
        quiet(c5.east().below(), stone);
        run(c5, () -> level.setBlock(c5.east(), named("minecraft:comparator", "facing", "east"), 3));
        run(c5, () -> level.setBlock(c5.east().east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3));
        out.add(c5);
        server.tick(6);
        assertTrue(on(c5, () -> serverLevel(c5).hasNeighborSignal(pos(c5))), () -> "비교기 장면: C 에 신호가 든다 — " + on(c5, () -> {
            var rl = serverLevel(c5);
            return Block.stateById(rl.getBlockState(pos(c5.east()))) + " " + Block.stateById(rl.getBlockState(pos(c5.east().east()))) + " "
                    + Block.stateById(rl.getBlockState(pos(c5.east().below()))) + " be=" + rl.getBlockEntity(pos(c5.east())) + " sig=" + rl.getSignal(pos(c5.east()), Dir.EAST)
                    + "/" + rl.getSignal(pos(c5.east()), Dir.WEST);
        }));
        assertTrue(on(c5, () -> serverLevel(c5).getBlockEntity(pos(c5.east())) != null), "비교기 블록 엔티티");
        return out;
    }

    static List<Block> allBlocks() {
        Set<Block> s = new LinkedHashSet<>();
        for (int id = 0; id < NmsBlocks.stateCount(); id++) s.add(Block.stateById(id).getBlock());
        return new ArrayList<>(s);
    }

    /** 블록 전수 × 6면 × 클릭 높이 둘: 프록시 길 = 서버 월드 길 = 서버 직접. */
    @Test
    void placementStateMatchesServer() throws Exception {
        List<BlockPos> scenes = scenes();
        ProxyLevel proxy = ProxyLevel.of(level);
        List<Block> blocks = allBlocks();
        int[] checked = {0};
        Set<String> nonNull = new LinkedHashSet<>();
        for (BlockPos c : scenes) {
            List<String> diffs = on(c, () -> {
                List<String> d = new ArrayList<>();
                BlockLevel rl = serverLevel(c);
                FaweContext viaProxy = new FaweContext(proxy), viaLevel = new FaweContext(level);
                for (Direction face : Direction.values()) {
                    BlockPos hit = c.relative(face.getOpposite());
                    for (int k = -1; k <= 1; k += 2) {
                        double lx = hit.getX() + 0.5 + face.getStepX() * 0.5 + (face.getAxis() == Direction.Axis.Y ? 0.25 * k : 0);
                        double ly = hit.getY() + 0.5 + face.getStepY() * 0.5 + (face.getAxis() != Direction.Axis.Y ? 0.25 * k : 0);
                        double lz = hit.getZ() + 0.5 + face.getStepZ() * 0.5;
                        BlockHitResult h = new BlockHitResult(new Vec3(lx, ly, lz), face, hit, false);
                        viaProxy.withSetting(h, face.getOpposite());
                        viaLevel.withSetting(h, face.getOpposite());
                        ServerFawe truth = new ServerFawe(rl, pos(hit), face.get3DDataValue(), lx, ly, lz);
                        for (Block b : blocks) {
                            int sb = b.defaultBlockState().furnaceStateId();
                            String want = outcome(() -> BlockBehaviors.of(sb).getStateForPlacement(truth));
                            String gotProxy = outcome(() -> { BlockState s = b.getStateForPlacement(viaProxy); return s == null ? null : s.furnaceStateId(); });
                            String gotLevel = outcome(() -> { BlockState s = b.getStateForPlacement(viaLevel); return s == null ? null : s.furnaceStateId(); });
                            checked[0]++;
                            if (!want.equals("null") && want.startsWith("s")) nonNull.add(b.toString());
                            boolean randomOk = RANDOM_PLACEMENT.contains(b.furnaceKey().toString()) && gotProxy.startsWith("UnsupportedNmsCall(")
                                    && gotProxy.contains("random on a plugin level");
                            if ((!want.equals(gotProxy) && !randomOk) || !want.equals(gotLevel)) {
                                d.add(b + " " + face + " k" + k + " @" + c + ": server " + want + " proxy " + gotProxy + " level " + gotLevel);
                            }
                        }
                    }
                }
                return d;
            });
            assertEquals(List.of(), diffs.size() > 40 ? diffs.subList(0, 40) : diffs, "장면 " + c);
        }
        assertTrue(checked[0] > 50_000, "검사 수 " + checked[0]);
        assertTrue(nonNull.contains("Block{minecraft:white_bed}"), "침대 — 월드 경계를 그 레벨에서 읽는다");
        assertTrue(nonNull.size() > 900, "놓을 수 있는 블록 " + nonNull.size());
    }

    /** 바닐라 BlockPlaceContext(플레이어 없음, 손에 그 블록 아이템): 클릭 칸 바꾸기 판정·놓을 칸·놓기 상태가 서버 PlaceContext 와 같다. */
    @Test
    void vanillaContextWithoutPlayer() {
        List<BlockPos> scenes = scenes();
        List<Block> blocks = allBlocks();
        for (BlockPos c : scenes) {
            List<String> diffs = on(c, () -> {
                List<String> d = new ArrayList<>();
                BlockLevel rl = serverLevel(c);
                for (Direction face : Direction.values()) {
                    BlockPos hit = c.relative(face.getOpposite());
                    Vec3 loc = new Vec3(hit.getX() + 0.5 + face.getStepX() * 0.5, hit.getY() + 0.5 + face.getStepY() * 0.5, hit.getZ() + 0.5 + face.getStepZ() * 0.5);
                    for (Block b : blocks) {
                        if (b.asItem() == null || b.asItem().toString().equals("minecraft:air")) continue;
                        ItemStack stack = new ItemStack(b.asItem());
                        var serverStack = org.domi.furnace.server.plugin.ApiConvert.item(stack.furnaceValue());
                        BlockPlaceContext nms = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack, new BlockHitResult(loc, face, hit, false)) {};
                        PlaceContext srv = new PlaceContext(rl, null, 0, serverStack, new Hit(pos(hit), face.get3DDataValue(), loc.x, loc.y, loc.z, false));
                        String want = srv.replacingClickedOnBlock() + " " + srv.clickedPos() + " "
                                + outcome(() -> BlockBehaviors.of(b.defaultBlockState().furnaceStateId()).getStateForPlacement(srv));
                        BlockPos np = nms.getClickedPos();
                        String got = nms.replacingClickedOnBlock() + " " + pos(np) + " "
                                + outcome(() -> { BlockState s = b.getStateForPlacement(nms); return s == null ? null : s.furnaceStateId(); });
                        if (!want.equals(got)) d.add(b + " " + face + " @" + c + ": server " + want + " nms " + got);
                    }
                }
                return d;
            });
            assertEquals(List.of(), diffs.size() > 40 ? diffs.subList(0, 40) : diffs, "장면 " + c);
        }
    }

    /** canSurvive 전 상태 × 장면: 프록시 길 = 서버 월드 길 = 서버 직접. */
    @Test
    void canSurviveMatchesServer() throws Exception {
        List<BlockPos> scenes = scenes();
        ProxyLevel proxy = ProxyLevel.of(level);
        int survivors = 0;
        for (BlockPos c : scenes) {
            int[] n = {0};
            List<String> diffs = on(c, () -> {
                List<String> d = new ArrayList<>();
                BlockLevel rl = serverLevel(c);
                for (int id = 0; id < NmsBlocks.stateCount(); id++) {
                    int sid = id;
                    BlockState s = Block.stateById(id);
                    String want = outcome(() -> BlockBehaviors.of(sid).canSurvive(sid, rl, pos(c)) ? 1 : 0);
                    String p = outcome(() -> s.canSurvive(proxy, c) ? 1 : 0), l = outcome(() -> s.canSurvive(level, c) ? 1 : 0);
                    if (want.equals("s1")) n[0]++;
                    if (!want.equals(p) || !want.equals(l)) d.add(s + " @" + c + ": server " + want + " proxy " + p + " level " + l);
                }
                return d;
            });
            survivors += n[0];
            assertEquals(List.of(), diffs.size() > 40 ? diffs.subList(0, 40) : diffs, "장면 " + c);
        }
        assertNotEquals(0, survivors);
    }

    /**
     * 실제 월드를 읽지 않는 프록시는 아무 스레드에서나 계산된다(FAWE 는 작업 스레드에서 부른다). 서버 월드는 다른 스레드면 공개 스냅샷으로 읽는다(N2b-3).
     * 놓는 플레이어가 있는 문맥은 아직 없다 — 거짓 parity 대신 UnsupportedNmsCall(바닐라 생성자가 canBeReplaced 를 불러 만들 때 난다).
     */
    @Test
    void threadsAndUnsupported() throws Exception {
        BlockPos c = new BlockPos(baseX + 24, Y, baseZ + 104);
        FaweContext airCtx = new FaweContext(ProxyLevel.ofAir()).withSetting(new BlockHitResult(Vec3.atCenterOf(c), Direction.UP, c.below(), false), Direction.DOWN);
        var got = new Object[1];
        Thread t = new Thread(() -> {
            try {
                got[0] = Blocks.OAK_STAIRS.getStateForPlacement(airCtx);
            } catch (Throwable e) {
                got[0] = e;
            }
        }, "worker");
        t.start();
        t.join(5_000);
        assertEquals(named("minecraft:oak_stairs", "facing", "north"), got[0], "작업 스레드에서 프록시 계산");
        assertEquals(Blocks.TORCH.defaultBlockState(), Blocks.TORCH.getStateForPlacement(airCtx));
        assertFalse(Blocks.TORCH.defaultBlockState().canSurvive(ProxyLevel.ofAir(), c), "공기 위 횃불");

        quiet(c.below(), Blocks.STONE.defaultBlockState());
        server.tick(2);
        assertTrue(Blocks.TORCH.defaultBlockState().canSurvive(level, c), "다른 스레드 — 공개 스냅샷");
        assertTrue(on(c, () -> Blocks.TORCH.defaultBlockState().canSurvive(level, c)), "주인 — 서버 레벨");

        Player player = ((org.bukkit.craftbukkit.entity.CraftPlayer) Compat.player(tp.player())).getHandle();
        assertThrows(UnsupportedNmsCall.class, () -> on(c, () -> new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY,
                new BlockHitResult(Vec3.atCenterOf(c), Direction.UP, c.below(), false)) {}));
        assertThrows(UnsupportedNmsCall.class, () -> Fluids.LAVA.getTickDelay(ProxyLevel.ofAir()), "서버 월드가 아닌 레벨의 차원");
    }
}
