// 바닐라 ForceLoadCommand: /forceload add|remove <from> [to] | remove all | query [pos]. 붙잡은 청크는 구독자가 없어도 내려가지 않고
// 차원 폴더의 data/chunks.dat 에 저장된다(World.forcedChunks). 한 번에 256 청크까지.
// 차이: 바닐라의 "강제 로드 = 엔티티 틱 레벨 티켓"은 없다 — 청크가 올라와 있는 동안 그 region 이 엔티티·블록을 틱하는 것은 같지만,
// 플레이어가 가까이 없을 때의 무작위 틱·스폰은 Furnace 일반 규칙(플레이어 근처만)을 따른다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.ColumnPosArgument;
import org.domi.furnace.server.command.arguments.ColumnPosArgument.ColumnPos;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.world.ForcedChunks;
import org.domi.furnace.server.world.World;

public final class ForceLoadCommand {
    private ForceLoadCommand() { }

    private static final int MAX_CHUNK_LIMIT = 256;
    private static final Dynamic2CommandExceptionType ERROR_TOO_MANY_CHUNKS = new Dynamic2CommandExceptionType((max, given) -> Text.translatable("commands.forceload.toobig", max, given));
    private static final Dynamic2CommandExceptionType ERROR_NOT_TICKING = new Dynamic2CommandExceptionType((pos, dim) -> Text.translatable("commands.forceload.query.failure", pos, dim));
    private static final SimpleCommandExceptionType ERROR_ALL_ADDED = new SimpleCommandExceptionType(Text.translatable("commands.forceload.added.failure"));
    private static final SimpleCommandExceptionType ERROR_NONE_REMOVED = new SimpleCommandExceptionType(Text.translatable("commands.forceload.removed.failure"));

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("forceload").requires(s -> s.hasPermission(2))
                .then(literal("add").then(argument("from", ColumnPosArgument.columnPos())
                        .executes(c -> changeForceLoad(c.getSource(), ColumnPosArgument.getColumnPos(c, "from"), ColumnPosArgument.getColumnPos(c, "from"), true))
                        .then(argument("to", ColumnPosArgument.columnPos())
                                .executes(c -> changeForceLoad(c.getSource(), ColumnPosArgument.getColumnPos(c, "from"), ColumnPosArgument.getColumnPos(c, "to"), true)))))
                .then(literal("remove")
                        .then(argument("from", ColumnPosArgument.columnPos())
                                .executes(c -> changeForceLoad(c.getSource(), ColumnPosArgument.getColumnPos(c, "from"), ColumnPosArgument.getColumnPos(c, "from"), false))
                                .then(argument("to", ColumnPosArgument.columnPos())
                                        .executes(c -> changeForceLoad(c.getSource(), ColumnPosArgument.getColumnPos(c, "from"), ColumnPosArgument.getColumnPos(c, "to"), false))))
                        .then(literal("all").executes(c -> removeAll(c.getSource()))))
                .then(literal("query").executes(c -> listForceLoad(c.getSource()))
                        .then(argument("pos", ColumnPosArgument.columnPos()).executes(c -> queryForceLoad(c.getSource(), ColumnPosArgument.getColumnPos(c, "pos"))))));
    }

    /** ChunkPos.toString. */
    static String chunkText(int x, int z) { return "[" + x + ", " + z + "]"; }

    static int queryForceLoad(CommandSource source, ColumnPos pos) throws CommandSyntaxException {
        World world = source.level();
        int cx = pos.chunkX(), cz = pos.chunkZ();
        if (world.forcedChunks().contains(cx, cz)) {
            source.sendSuccess(() -> Text.translatable("commands.forceload.query.success", chunkText(cx, cz), world.dimension()), false);
            return 1;
        }
        throw ERROR_NOT_TICKING.create(chunkText(cx, cz), world.dimension());
    }

    static int listForceLoad(CommandSource source) {
        World world = source.level();
        long[] keys = world.forcedChunks().sortedKeys();
        if (keys.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keys.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(chunkText(ForcedChunks.xOf(keys[i]), ForcedChunks.zOf(keys[i])));
            }
            String joined = sb.toString();
            if (keys.length == 1) source.sendSuccess(() -> Text.translatable("commands.forceload.list.single", world.dimension(), joined), false);
            else source.sendSuccess(() -> Text.translatable("commands.forceload.list.multiple", keys.length, world.dimension(), joined), false);
        } else {
            source.sendFailure(Text.translatable("commands.forceload.added.none", world.dimension()));
        }
        return keys.length;
    }

    static int removeAll(CommandSource source) {
        World world = source.level();
        for (long k : world.forcedChunks().sortedKeys()) world.setChunkForced(ForcedChunks.xOf(k), ForcedChunks.zOf(k), false);
        source.sendSuccess(() -> Text.translatable("commands.forceload.removed.all", world.dimension()), true);
        return 0;
    }

    static int changeForceLoad(CommandSource source, ColumnPos from, ColumnPos to, boolean add) throws CommandSyntaxException {
        int minX = Math.min(from.x(), to.x()), minZ = Math.min(from.z(), to.z());
        int maxX = Math.max(from.x(), to.x()), maxZ = Math.max(from.z(), to.z());
        if (minX < -30000000 || minZ < -30000000 || maxX >= 30000000 || maxZ >= 30000000) throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
        int cx0 = minX >> 4, cz0 = minZ >> 4, cx1 = maxX >> 4, cz1 = maxZ >> 4;
        long count = (long) (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
        if (count > MAX_CHUNK_LIMIT) throw ERROR_TOO_MANY_CHUNKS.create(MAX_CHUNK_LIMIT, count);
        World world = source.level();
        int firstX = 0, firstZ = 0, changed = 0;
        for (int x = cx0; x <= cx1; x++) {
            for (int z = cz0; z <= cz1; z++) {
                if (world.setChunkForced(x, z, add)) {
                    if (changed == 0) {
                        firstX = x;
                        firstZ = z;
                    }
                    changed++;
                }
            }
        }
        if (changed == 0) throw (add ? ERROR_ALL_ADDED : ERROR_NONE_REMOVED).create();
        String key = "commands.forceload." + (add ? "added" : "removed");
        int n = changed, fx = firstX, fz = firstZ;
        if (n == 1) {
            source.sendSuccess(() -> Text.translatable(key + ".single", chunkText(fx, fz), world.dimension()), true);
        } else {
            source.sendSuccess(() -> Text.translatable(key + ".multiple", n, world.dimension(), chunkText(cx0, cz0), chunkText(cx1, cz1)), true);
        }
        return n;
    }
}
