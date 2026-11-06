// 바닐라 CloneCommand: /clone [from <차원>] <begin> <end> [to <차원>] <destination> [replace|masked|filtered <필터>] [force|move|normal].
// 순서·결과는 바닐라 그대로(통짜 블록 → 블록 엔티티 → 그 밖의 블록 순으로 놓고, 지울 땐 역순, 블록 엔티티 내용 복사, 예약 틱 복사).
// 차이: 원본·대상 영역이 모두 한 region 에 로드돼 있어야 한다(/fill 과 같다). 다른 차원이거나 다른 region 이면 argument.pos.unloaded 로 실패한다.
// 바닐라는 사이 청크를 불러와 두 영역을 한꺼번에 만지지만 region 모델에서는 두 스레드가 한 영역을 만질 수 없기 때문이다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.entity.ChiseledBookShelfBlockEntity;
import org.domi.furnace.server.block.entity.ContainerBlockEntity;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.PostMessages;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.BlockPredicateArgument;
import org.domi.furnace.server.command.arguments.DimensionArgument;
import org.domi.furnace.server.command.brigadier.ArgumentBuilder;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.world.World;

public final class CloneCommand {
    private CloneCommand() { }

    private static final SimpleCommandExceptionType ERROR_OVERLAP = new SimpleCommandExceptionType(Text.translatable("commands.clone.overlap"));
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE =
            new Dynamic2CommandExceptionType((max, given) -> Text.translatable("commands.clone.toobig", max, given));
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.clone.failed"));

    /** 바닐라 Mode: FORCE·MOVE 는 겹침 허용, NORMAL 은 겹치면 거부. */
    enum Mode {
        FORCE(true), MOVE(true), NORMAL(false);
        final boolean canOverlap;
        Mode(boolean canOverlap) { this.canOverlap = canOverlap; }
    }

    @FunctionalInterface
    private interface CtxFn<R> {
        R apply(CommandContext<CommandSource> c) throws CommandSyntaxException;
    }

    private static final BlockPredicateArgument.Predicate FILTER_AIR = (level, pos) -> !B.isAir(level.getBlockState(pos));

    private record Entry(Pos pos, int state, NbtCompound tag) { }

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("clone").requires(s -> s.hasPermission(2))
                .then(corners(c -> c.getSource().level()))
                .then(literal("from").then(argument("sourceDimension", DimensionArgument.dimension())
                        .then(corners(c -> DimensionArgument.getDimension(c, "sourceDimension"))))));
    }

    private static ArgumentBuilder<CommandSource, ?> corners(CtxFn<World> sourceLevel) {
        return argument("begin", BlockPosArgument.blockPos())
                .then(argument("end", BlockPosArgument.blockPos())
                        .then(destination(sourceLevel, c -> c.getSource().level()))
                        .then(literal("to").then(argument("targetDimension", DimensionArgument.dimension())
                                .then(destination(sourceLevel, c -> DimensionArgument.getDimension(c, "targetDimension"))))));
    }

    private static ArgumentBuilder<CommandSource, ?> destination(CtxFn<World> src, CtxFn<World> dst) {
        BlockPredicateArgument.Predicate all = (level, pos) -> true;
        return argument("destination", BlockPosArgument.blockPos())
                .executes(c -> clone(c, src, dst, all, Mode.NORMAL))
                .then(modes(src, dst, c -> all, literal("replace").executes(c -> clone(c, src, dst, all, Mode.NORMAL))))
                .then(modes(src, dst, c -> FILTER_AIR, literal("masked").executes(c -> clone(c, src, dst, FILTER_AIR, Mode.NORMAL))))
                .then(literal("filtered").then(modes(src, dst, c -> BlockPredicateArgument.getBlockPredicate(c, "filter"),
                        argument("filter", BlockPredicateArgument.blockPredicate())
                                .executes(c -> clone(c, src, dst, BlockPredicateArgument.getBlockPredicate(c, "filter"), Mode.NORMAL)))));
    }

    private static ArgumentBuilder<CommandSource, ?> modes(CtxFn<World> src, CtxFn<World> dst, CtxFn<BlockPredicateArgument.Predicate> filter,
                                                          ArgumentBuilder<CommandSource, ?> parent) {
        return parent
                .then(literal("force").executes(c -> clone(c, src, dst, filter.apply(c), Mode.FORCE)))
                .then(literal("move").executes(c -> clone(c, src, dst, filter.apply(c), Mode.MOVE)))
                .then(literal("normal").executes(c -> clone(c, src, dst, filter.apply(c), Mode.NORMAL)));
    }

    /** 칸 처리 중의 명령 오류를 region 스레드에서 알리기 위한 표시. */
    static final class Failed extends Exception {
        final CommandSyntaxException cause;
        Failed(CommandSyntaxException cause) { super(null, null, false, false); this.cause = cause; }
    }

    private static int clone(CommandContext<CommandSource> c, CtxFn<World> srcFn, CtxFn<World> dstFn, BlockPredicateArgument.Predicate filter, Mode mode)
            throws CommandSyntaxException {
        CommandSource source = c.getSource();
        int[] b = BlockPosArgument.getBlockPos(c, "begin"), e = BlockPosArgument.getBlockPos(c, "end"), t = BlockPosArgument.getBlockPos(c, "destination");
        World srcWorld = srcFn.apply(c), dstWorld = dstFn.apply(c);
        int minX = Math.min(b[0], e[0]), minY = Math.min(b[1], e[1]), minZ = Math.min(b[2], e[2]);
        int maxX = Math.max(b[0], e[0]), maxY = Math.max(b[1], e[1]), maxZ = Math.max(b[2], e[2]);
        PostMessages post = new PostMessages();
        return post.finish(source, EntityOps.atRegion(srcWorld, minX, minZ, region -> {
            try {
                RegionLevel level = region.level();
                // 바닐라 getLoadedBlockPos 세 번(begin·end·destination) — 원본 차원은 begin·end, 대상 차원은 destination
                checkLoaded(level, new Pos(b[0], b[1], b[2]));
                checkLoaded(level, new Pos(e[0], e[1], e[2]));
                if (dstWorld != srcWorld) throw new Failed(BlockPosArgument.ERROR_NOT_LOADED.create());
                checkLoaded(level, new Pos(t[0], t[1], t[2]));
                int count = doClone(source, level, minX, minY, minZ, maxX, maxY, maxZ, t[0], t[1], t[2], filter, mode);
                post.success(source, () -> Text.translatable("commands.clone.success", count), true);
                return count;
            } catch (Failed f) {
                throw f.cause;
            }
        }));
    }

    /** 바닐라 clone(): 같은 레벨 안에서 [min,max] 를 (dx,dy,dz) 를 맨 앞 모서리로 하는 자리에 복사한다. 놓은 칸 수를 돌려준다. */
    static int doClone(CommandSource source, RegionLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                       int dx, int dy, int dz, BlockPredicateArgument.Predicate filter, Mode mode) throws Failed {
        int dMaxX = dx + (maxX - minX), dMaxY = dy + (maxY - minY), dMaxZ = dz + (maxZ - minZ);
        boolean overlap = dx <= maxX && dMaxX >= minX && dy <= maxY && dMaxY >= minY && dz <= maxZ && dMaxZ >= minZ;
        if (!mode.canOverlap && overlap) throw new Failed(ERROR_OVERLAP.create());
        int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        int limit = level.gameRuleInt("commandModificationBlockLimit");
        if (volume > limit) throw new Failed(ERROR_AREA_TOO_LARGE.create(limit, volume));
        if (!chunksLoaded(level, minX, minY, minZ, maxX, maxY, maxZ) || !chunksLoaded(level, dx, dy, dz, dMaxX, dMaxY, dMaxZ)) {
            throw new Failed(BlockPosArgument.ERROR_NOT_LOADED.create());
        }
        List<Entry> solid = new ArrayList<>(), withBe = new ArrayList<>(), other = new ArrayList<>();
        Deque<Pos> toClear = new ArrayDeque<>();
        int ox = dx - minX, oy = dy - minY, oz = dz - minZ;
        for (int z = minZ; z <= maxZ; z++) for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {   // BlockPos.betweenClosed
            Pos from = new Pos(x, y, z), to = new Pos(x + ox, y + oy, z + oz);
            if (!filter.test(level, from)) continue;
            int state = level.getBlockState(from);
            if (level.getBlockEntity(from) instanceof BlockEntity be) {
                withBe.add(new Entry(to, state, be.saveWithoutMetadata()));
                toClear.addLast(from);
            } else if (!B.isFullOcclusion(state) && !B.isCollisionShapeFullBlock(state)) {
                other.add(new Entry(to, state, null));
                toClear.addFirst(from);
            } else {
                solid.add(new Entry(to, state, null));
                toClear.addLast(from);
            }
        }
        int barrier = B.defaultState("minecraft:barrier"), air = B.defaultState("minecraft:air");
        if (mode == Mode.MOVE) {
            for (Pos p : toClear) {
                tryClear(level.getBlockEntity(p));
                level.setBlock(p, barrier, 2);
            }
            for (Pos p : toClear) level.setBlock(p, air, 3);
        }
        List<Entry> all = new ArrayList<>(solid);
        all.addAll(withBe);
        all.addAll(other);
        List<Entry> reversed = new ArrayList<>(all);
        Collections.reverse(reversed);
        for (Entry en : reversed) {
            tryClear(level.getBlockEntity(en.pos));
            level.setBlock(en.pos, barrier, 2);
        }
        int count = 0;
        for (Entry en : all) if (level.setBlock(en.pos, en.state, 2)) count++;
        for (Entry en : withBe) {
            if (en.tag != null && level.getBlockEntity(en.pos) instanceof BlockEntity be) {
                be.load(en.tag);
                be.loadComponents(en.tag);   // 바닐라 loadCustomOnly + setComponents
                be.setChanged(level);
            }
            level.setBlock(en.pos, en.state, 2);
        }
        for (Entry en : reversed) level.blockUpdated(en.pos, B.block(level.getBlockState(en.pos)));
        level.blockTicks().copyAreaFrom(level.blockTicks(), minX, minY, minZ, maxX, maxY, maxZ, ox, oy, oz);
        if (count == 0) throw new Failed(ERROR_FAILED.create());
        return count;
    }

    /** 바닐라 hasChunksAt(from, to): 사이 모든 청크가 로드돼 있고 높이가 월드 안에 걸치는가. */
    static boolean chunksLoaded(RegionLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (maxY < level.minBuildHeight() || minY >= level.maxBuildHeight()) return false;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!level.hasChunkAt(new Pos(cx << 4, Math.max(minY, level.minBuildHeight()), cz << 4))) return false;
            }
        }
        return true;
    }

    /** 바닐라 Clearable.tryClear: 블록을 바꾸기 전에 담긴 내용물을 비운다(쏟지 않게). 주크박스·독서대는 아직 빠졌다. */
    static void tryClear(Object be) {
        if (be instanceof ContainerBlockEntity c) java.util.Arrays.fill(c.items, org.domi.furnace.server.entity.ItemStack.EMPTY);
        else if (be instanceof ChiseledBookShelfBlockEntity c) c.clearContent();
    }

    private static void checkLoaded(BlockLevel level, Pos pos) throws Failed {
        if (!level.hasChunkAt(pos)) throw new Failed(BlockPosArgument.ERROR_NOT_LOADED.create());
        if (!level.isInWorldBounds(pos)) throw new Failed(BlockPosArgument.ERROR_OUT_OF_WORLD.create());
    }
}
