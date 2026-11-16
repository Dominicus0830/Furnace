// 바닐라 RandomCommand: /random value|roll <범위> [시퀀스], /random reset (* | <시퀀스>) [시드 [월드시드 포함 [시퀀스 id 포함]]].
// 시퀀스 없이 쓰면 레벨 난수, 시퀀스를 주면 서버 전체가 공유하는 이름 붙은 Xoroshiro 난수(RandomSequences)를 쓴다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.IntRangeArgument;
import org.domi.furnace.server.command.arguments.MinMaxBounds;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.RequiredArgumentBuilder;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;

public final class RandomCommand {
    private RandomCommand() { }

    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_LARGE = new SimpleCommandExceptionType(Text.translatable("commands.random.error.range_too_large"));
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_SMALL = new SimpleCommandExceptionType(Text.translatable("commands.random.error.range_too_small"));

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("random")
                .then(drawLiteral("value", false))
                .then(drawLiteral("roll", true))
                .then(literal("reset").requires(s -> s.hasPermission(2))
                        .then(literal("*").executes(c -> resetAll(c.getSource()))
                                .then(argument("seed", Arguments.integer())
                                        .executes(c -> resetAll(c.getSource(), seed(c), true, true))
                                        .then(argument("includeWorldSeed", Arguments.bool())
                                                .executes(c -> resetAll(c.getSource(), seed(c), worldSeed(c), true))
                                                .then(argument("includeSequenceId", Arguments.bool())
                                                        .executes(c -> resetAll(c.getSource(), seed(c), worldSeed(c), sequenceId(c)))))))
                        .then(argument("sequence", ResourceLocationArgument.id()).suggests(RandomCommand::suggestSequences)
                                .executes(c -> reset(c.getSource(), id(c)))
                                .then(argument("seed", Arguments.integer())
                                        .executes(c -> reset(c.getSource(), id(c), seed(c), true, true))
                                        .then(argument("includeWorldSeed", Arguments.bool())
                                                .executes(c -> reset(c.getSource(), id(c), seed(c), worldSeed(c), true))
                                                .then(argument("includeSequenceId", Arguments.bool())
                                                        .executes(c -> reset(c.getSource(), id(c), seed(c), worldSeed(c), sequenceId(c))))))))
        );
    }

    private static LiteralArgumentBuilder<CommandSource> drawLiteral(String name, boolean announce) {
        return literal(name).then(argument("range", IntRangeArgument.intRange())
                .executes(c -> draw(c.getSource(), IntRangeArgument.getRange(c, "range"), null, announce))
                .then(RequiredArgumentBuilder.<CommandSource, ResourceId>argument("sequence", ResourceLocationArgument.id())
                        .suggests(RandomCommand::suggestSequences)
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> draw(c.getSource(), IntRangeArgument.getRange(c, "range"), id(c), announce))));
    }

    private static java.util.concurrent.CompletableFuture<org.domi.furnace.server.command.brigadier.Suggestions> suggestSequences(
            CommandContext<CommandSource> c, org.domi.furnace.server.command.brigadier.SuggestionsBuilder b) {
        var names = new java.util.ArrayList<String>();
        for (ResourceId id : c.getSource().getServer().randomSequences().ids()) names.add(id.toString());
        return SharedSuggestions.suggest(names, b);
    }

    private static ResourceId id(CommandContext<CommandSource> c) { return ResourceLocationArgument.getId(c, "sequence"); }

    private static int seed(CommandContext<CommandSource> c) { return c.getArgument("seed", Integer.class); }

    private static boolean worldSeed(CommandContext<CommandSource> c) { return c.getArgument("includeWorldSeed", Boolean.class); }

    private static boolean sequenceId(CommandContext<CommandSource> c) { return c.getArgument("includeSequenceId", Boolean.class); }

    /** 바닐라 randomSample: min = 범위 최소(없으면 Integer.MIN), max = 최대(없으면 MAX). 폭 0 은 너무 작고, 2^31-1 이상은 너무 크다. */
    static int draw(CommandSource source, MinMaxBounds range, ResourceId sequence, boolean announce) throws CommandSyntaxException {
        int min = range.min() == null ? Integer.MIN_VALUE : (int) (double) range.min();
        int max = range.max() == null ? Integer.MAX_VALUE : (int) (double) range.max();
        long span = (long) max - min;
        if (span == 0L) throw ERROR_RANGE_TOO_SMALL.create();
        if (span >= Integer.MAX_VALUE) throw ERROR_RANGE_TOO_LARGE.create();
        int value;
        if (sequence != null) {
            NoiseRandom r = source.getServer().randomSequences().get(sequence);
            synchronized (r) { value = r.nextInt(max - min + 1) + min; }   // Mth.randomBetweenInclusive
        } else {
            value = org.domi.furnace.server.block.RandomSource.createFor(org.domi.furnace.server.block.RandomSeeds.key("command.random"), org.domi.furnace.server.block.RandomSeeds.Purpose.COMMAND_RANDOM).nextInt(max - min + 1) + min;
        }
        if (announce) {
            source.getServer().broadcastSystemMessage(Text.translatable("commands.random.roll", source.getDisplayName(), value, min, max), false);
        } else {
            int v = value;
            source.sendSuccess(() -> Text.translatable("commands.random.sample.success", v), false);
        }
        return value;
    }

    private static int reset(CommandSource source, ResourceId id) {
        source.getServer().randomSequences().reset(id);
        source.sendSuccess(() -> Text.translatable("commands.random.reset.success", Text.text(id.toString())), false);
        return 1;
    }

    private static int reset(CommandSource source, ResourceId id, int seed, boolean worldSeed, boolean sequenceId) {
        source.getServer().randomSequences().reset(id, seed, worldSeed, sequenceId);
        source.sendSuccess(() -> Text.translatable("commands.random.reset.success", Text.text(id.toString())), false);
        return 1;
    }

    private static int resetAll(CommandSource source) {
        int n = source.getServer().randomSequences().clear();
        source.sendSuccess(() -> Text.translatable("commands.random.reset.all.success", n), false);
        return n;
    }

    private static int resetAll(CommandSource source, int seed, boolean worldSeed, boolean sequenceId) {
        var seqs = source.getServer().randomSequences();
        seqs.setSeedDefaults(seed, worldSeed, sequenceId);
        int n = seqs.clear();
        source.sendSuccess(() -> Text.translatable("commands.random.reset.all.success", n), false);
        return n;
    }
}
