// 바닐라 SetBlockCommand·FillCommand. 좌표·인자는 명령 스레드에서 풀고, 블록을 바꾸는 일은 그 칸 주인 region 스레드에서 한다
// (결과 메시지는 일이 끝난 뒤 전역 스레드에서). /fill 은 영역 전체가 한 region 에 로드돼 있어야 한다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.PostMessages;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.BlockPredicateArgument;
import org.domi.furnace.server.command.arguments.BlockStateArgument;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.world.RegionLevel;

public final class BlockCommands {

    private BlockCommands() {}

    private static final SimpleCommandExceptionType SETBLOCK_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.setblock.failed"));
    private static final SimpleCommandExceptionType FILL_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.fill.failed"));
    private static final Dynamic2CommandExceptionType FILL_TOO_BIG =
            new Dynamic2CommandExceptionType((max, given) -> Text.translatable("commands.fill.toobig", max, given));

    private enum Mode { REPLACE, OUTLINE, HOLLOW, DESTROY }

    // ------------------------------------------------------------------ setblock

    public static void registerSetBlock(CommandDispatcher<CommandSource> d) {
        BlockPredicateArgument.Predicate keep = (level, pos) -> level.isEmptyBlock(pos);
        d.register(literal("setblock").requires(s -> s.hasPermission(2))
                .then(argument("pos", BlockPosArgument.blockPos())
                        .then(argument("block", BlockStateArgument.block())
                                .executes(c -> setBlock(c, Mode.REPLACE, null))
                                .then(literal("destroy").executes(c -> setBlock(c, Mode.DESTROY, null)))
                                .then(literal("keep").executes(c -> setBlock(c, Mode.REPLACE, keep)))
                                .then(literal("replace").executes(c -> setBlock(c, Mode.REPLACE, null))))));
    }

    private static int setBlock(CommandContext<CommandSource> c, Mode mode, BlockPredicateArgument.Predicate filter) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        int[] p = BlockPosArgument.getBlockPos(c, "pos");
        BlockStateArgument.BlockInput input = BlockStateArgument.getBlock(c, "block");
        Pos pos = new Pos(p[0], p[1], p[2]);
        PostMessages post = new PostMessages();
        return post.finish(source, EntityOps.atRegion(source.level(), pos.x(), pos.z(), region -> {
            {
                RegionLevel level = region.level();
                checkLoaded(level, pos);
                if (filter != null && !filter.test(level, pos)) throw SETBLOCK_FAILED.create();
                boolean place;
                if (mode == Mode.DESTROY) {
                    level.destroyBlock(pos, true);
                    place = !B.isAir(input.state()) || !level.isEmptyBlock(pos);
                } else {
                    // TODO(블록 엔티티): Clearable.tryClear — 바꾸기 전에 담긴 내용물을 비운다(쏟지 않게).
                    place = true;
                }
                if (place && !place(level, pos, input, 2)) throw SETBLOCK_FAILED.create();
                level.blockUpdated(pos, B.block(input.state()));
                post.success(source, () -> Text.translatable("commands.setblock.success", pos.x(), pos.y(), pos.z()), true);
                return 1;
            }
        }));
    }

    // ------------------------------------------------------------------ fill

    public static void registerFill(CommandDispatcher<CommandSource> d) {
        BlockPredicateArgument.Predicate keep = (level, pos) -> level.isEmptyBlock(pos);
        d.register(literal("fill").requires(s -> s.hasPermission(2))
                .then(argument("from", BlockPosArgument.blockPos())
                        .then(argument("to", BlockPosArgument.blockPos())
                                .then(argument("block", BlockStateArgument.block())
                                        .executes(c -> fill(c, Mode.REPLACE, null))
                                        .then(literal("replace")
                                                .executes(c -> fill(c, Mode.REPLACE, null))
                                                .then(argument("filter", BlockPredicateArgument.blockPredicate())
                                                        .executes(c -> fill(c, Mode.REPLACE, BlockPredicateArgument.getBlockPredicate(c, "filter")))))
                                        .then(literal("keep").executes(c -> fill(c, Mode.REPLACE, keep)))
                                        .then(literal("outline").executes(c -> fill(c, Mode.OUTLINE, null)))
                                        .then(literal("hollow").executes(c -> fill(c, Mode.HOLLOW, null)))
                                        .then(literal("destroy").executes(c -> fill(c, Mode.DESTROY, null)))))));
    }

    private static int fill(CommandContext<CommandSource> c, Mode mode, BlockPredicateArgument.Predicate filter) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        int[] a = BlockPosArgument.getBlockPos(c, "from"), b = BlockPosArgument.getBlockPos(c, "to");
        BlockStateArgument.BlockInput input = BlockStateArgument.getBlock(c, "block");
        Pos from = new Pos(a[0], a[1], a[2]), to = new Pos(b[0], b[1], b[2]);
        int minX = Math.min(a[0], b[0]), minY = Math.min(a[1], b[1]), minZ = Math.min(a[2], b[2]);
        int maxX = Math.max(a[0], b[0]), maxY = Math.max(a[1], b[1]), maxZ = Math.max(a[2], b[2]);
        PostMessages post = new PostMessages();
        return post.finish(source, EntityOps.atRegion(source.level(), minX, minZ, region -> {
            {
                RegionLevel level = region.level();
                checkLoaded(level, from);
                checkLoaded(level, to);
                long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
                int limit = level.gameRuleInt("commandModificationBlockLimit");
                if (volume > limit) throw FILL_TOO_BIG.create(limit, volume);
                // 바닐라는 사이의 청크를 동기로 불러오지만, 여기선 영역 전체가 이 region 에 로드돼 있어야 한다.
                for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                    for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                        if (!level.hasChunkAt(new Pos(cx << 4, minY, cz << 4))) throw BlockPosArgument.ERROR_NOT_LOADED.create();
                    }
                }
                List<Pos> changed = new ArrayList<>();
                int count = 0;
                int air = B.defaultState("minecraft:air");
                for (int z = minZ; z <= maxZ; z++) for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {   // BlockPos.betweenClosed
                    Pos pos = new Pos(x, y, z);
                    if (filter != null && !filter.test(level, pos)) continue;
                    boolean edge = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                    BlockStateArgument.BlockInput what = switch (mode) {
                        case REPLACE -> input;
                        case OUTLINE -> edge ? input : null;
                        case HOLLOW -> edge ? input : new BlockStateArgument.BlockInput(air, null);
                        case DESTROY -> {
                            level.destroyBlock(pos, true);
                            yield input;
                        }
                    };
                    if (what == null) continue;
                    // TODO(블록 엔티티): Clearable.tryClear.
                    if (place(level, pos, what, 2)) {
                        changed.add(pos);
                        count++;
                    }
                }
                for (Pos pos : changed) level.blockUpdated(pos, B.block(level.getBlockState(pos)));
                if (count == 0) throw FILL_FAILED.create();
                int n = count;
                post.success(source, () -> Text.translatable("commands.fill.success", n), true);
                return n;
            }
        }));
    }

    // ------------------------------------------------------------------ 공통

    /** BlockPosArgument.getLoadedBlockPos: 이 region 에 로드돼 있고 월드 안이어야. */
    private static void checkLoaded(BlockLevel level, Pos pos) throws CommandSyntaxException {
        if (!level.hasChunkAt(pos)) throw BlockPosArgument.ERROR_NOT_LOADED.create();
        if (!level.isInWorldBounds(pos)) throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
    }

    /** BlockInput.place(level, pos, flags): 이웃 모양으로 맞춘 상태(공기가 되면 원래 상태)를 놓는다. */
    private static boolean place(BlockLevel level, Pos pos, BlockStateArgument.BlockInput input, int flags) {
        int s = B.updateFromNeighbourShapes(input.state(), level, pos);
        if (B.isAir(s)) s = input.state();
        if (!level.setBlock(pos, s, flags)) return false;
        // TODO(블록 엔티티): input.nbt() 가 있으면 블록 엔티티에 loadWithComponents(x·y·z 는 칸 좌표로) 후 setChanged.
        return true;
    }
}
