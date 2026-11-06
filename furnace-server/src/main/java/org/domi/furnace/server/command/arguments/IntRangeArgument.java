// 바닐라 RangeArgument.Ints(minecraft:int_range) — "5", "1..", "..5", "1..5" 정수 범위. 속성 없음.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;

public final class IntRangeArgument implements ArgumentType<MinMaxBounds> {

    private static final List<String> EXAMPLES = List.of("0..5", "0", "-5", "-100..", "..100");

    public static IntRangeArgument intRange() { return new IntRangeArgument(); }

    public static MinMaxBounds getRange(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, MinMaxBounds.class); }

    @Override
    public MinMaxBounds parse(StringReader reader) throws CommandSyntaxException { return MinMaxBounds.fromReader(reader, true); }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:int_range"; }
}
