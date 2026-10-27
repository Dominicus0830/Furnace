// 바닐라 FillBiomeCommand: /fillbiome <from> <to> <biome> [replace <filter>]. 바이옴은 quart(4칸) 단위로 바뀐다.
// 모서리를 quart 의 첫 블록으로 맞춘 상자의 부피를 /fill 처럼 commandModificationBlockLimit 와 비교하고, 상자가 걸친 청크가 모두 로드돼 있어야 한다.
// 차이: 청크가 모두 이 명령을 받은 region 이 가진 것이어야 한다(아니면 argument.pos.unloaded). 바이옴 재전송은 청크마다 패킷 하나.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.List;
import java.util.function.IntPredicate;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.ResourceArgument;
import org.domi.furnace.server.command.arguments.ResourceOrTagKeyArgument;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.loot.LootRegistries;

public final class FillBiomeCommand {
    private FillBiomeCommand() { }

    private static final String REGISTRY = "minecraft:worldgen/biome";
    private static final Dynamic2CommandExceptionType ERROR_VOLUME_TOO_LARGE =
            new Dynamic2CommandExceptionType((max, given) -> Text.translatable("commands.fillbiome.toobig", max, given));
    private static final Dynamic2CommandExceptionType ERROR_UNKNOWN_RESOURCE =
            new Dynamic2CommandExceptionType((id, reg) -> Text.translatable("argument.resource.not_found", id, reg));
    private static final Dynamic2CommandExceptionType ERROR_UNKNOWN_TAG =
            new Dynamic2CommandExceptionType((id, reg) -> Text.translatable("argument.resource_tag.not_found", id, reg));

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("fillbiome").requires(s -> s.hasPermission(2))
                .then(argument("from", BlockPosArgument.blockPos())
                        .then(argument("to", BlockPosArgument.blockPos())
                                .then(argument("biome", ResourceArgument.resource(REGISTRY))
                                        .executes(c -> fill(c, null))
                                        .then(literal("replace").then(argument("filter", ResourceOrTagKeyArgument.resourceOrTag(REGISTRY))
                                                .executes(c -> fill(c, ResourceOrTagKeyArgument.get(c, "filter")))))))));
    }

    /** QuartPos.toBlock(QuartPos.fromBlock(x)). */
    private static int quantize(int v) { return (v >> 2) << 2; }

    private static int fill(CommandContext<CommandSource> c, ResourceOrTagKeyArgument.Result filterArg) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        LootRegistries regs = source.getServer().dataPacks().loot().registries();
        return fill(source, regs.entries(REGISTRY), regs::hasTag, regs::tag, c.getArgument("biome", ResourceId.class), filterArg,
                BlockPosArgument.getBlockPos(c, "from"), BlockPosArgument.getBlockPos(c, "to"));
    }

    /** 레지스트리 조회를 인자로 받는 본문(바이옴 이름 목록의 인덱스가 바이옴 id). */
    static int fill(CommandSource source, List<String> names, java.util.function.BiPredicate<String, String> hasTag,
                    java.util.function.BiFunction<String, String, List<String>> tag, ResourceId bid, ResourceOrTagKeyArgument.Result filterArg,
                    int[] a, int[] b) throws CommandSyntaxException {
        String biomeName = bid.namespace() + ":" + bid.path();
        int biomeId = names.indexOf(biomeName);
        if (biomeId < 0) throw ERROR_UNKNOWN_RESOURCE.create(biomeName, REGISTRY);
        IntPredicate filter = id -> true;
        if (filterArg != null) {
            String fname = filterArg.id().namespace() + ":" + filterArg.id().path();
            if (filterArg.tag()) {
                if (!hasTag.test(REGISTRY, fname)) throw ERROR_UNKNOWN_TAG.create(fname, REGISTRY);
                List<String> members = tag.apply(REGISTRY, fname);
                filter = id -> id >= 0 && id < names.size() && members.contains(names.get(id));
            } else {
                int want = names.indexOf(fname);
                if (want < 0) throw ERROR_UNKNOWN_RESOURCE.create(fname, REGISTRY);
                filter = id -> id == want;
            }
        }
        int ax = quantize(a[0]), ay = quantize(a[1]), az = quantize(a[2]), bx = quantize(b[0]), by = quantize(b[1]), bz = quantize(b[2]);
        int minX = Math.min(ax, bx), minY = Math.min(ay, by), minZ = Math.min(az, bz);
        int maxX = Math.max(ax, bx), maxY = Math.max(ay, by), maxZ = Math.max(az, bz);
        IntPredicate f = filter;
        source.level().runAt(minX, minZ, region -> {
            int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            int limit = region.level().gameRuleInt("commandModificationBlockLimit");
            if (volume > limit) {
                source.sendFailure(ERROR_VOLUME_TOO_LARGE.create(limit, volume).getRawMessage());
                return;
            }
            int n = region.fillBiomes(minX, minY, minZ, maxX, maxY, maxZ, biomeId, f);
            if (n < 0) {
                source.sendFailure(BlockPosArgument.ERROR_NOT_LOADED.create().getRawMessage());
                return;
            }
            source.sendSuccess(() -> Text.translatable("commands.fillbiome.success.count", n, minX, minY, minZ, maxX, maxY, maxZ), true);
        });
        return 1;
    }
}
