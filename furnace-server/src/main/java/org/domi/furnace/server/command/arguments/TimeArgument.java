// 바닐라 TimeArgument(minecraft:time) — 숫자 + 단위(d=24000, s=20, t 또는 없음=1)를 틱으로. 속성: 최소값(int).
package org.domi.furnace.server.command.arguments;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;
import org.domi.furnace.server.net.protocol.Text;

public record TimeArgument(int minimum) implements ArgumentType<Integer> {

    private static final List<String> EXAMPLES = List.of("0d", "0s", "0t", "0");
    private static final SimpleCommandExceptionType ERROR_INVALID_UNIT = new SimpleCommandExceptionType(Text.translatable("argument.time.invalid_unit"));
    private static final Dynamic2CommandExceptionType ERROR_TICK_COUNT_TOO_LOW =
            new Dynamic2CommandExceptionType((found, min) -> Text.translatable("argument.time.tick_count_too_low", min, found));
    private static final Map<String, Integer> UNITS = new java.util.LinkedHashMap<>(Map.of("d", 24000, "s", 20, "t", 1, "", 1));

    public static TimeArgument time() { return new TimeArgument(0); }

    public static TimeArgument time(int minimum) { return new TimeArgument(minimum); }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        float f = reader.readFloat();
        String unit = reader.readUnquotedString();
        int mul = UNITS.getOrDefault(unit, 0);
        if (mul == 0) throw ERROR_INVALID_UNIT.createWithContext(reader);
        int ticks = Math.round(f * mul);
        if (ticks < minimum) throw ERROR_TICK_COUNT_TOO_LOW.createWithContext(reader, ticks, minimum);
        return ticks;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader r = new StringReader(builder.getRemaining());
        try {
            r.readFloat();
        } catch (CommandSyntaxException e) {
            return builder.buildFuture();
        }
        return SharedSuggestions.suggest(UNITS.keySet(), builder.createOffset(builder.getStart() + r.getCursor()));
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:time"; }

    @Override
    public void writeProperties(ByteBuf buf) { buf.writeInt(minimum); }
}
