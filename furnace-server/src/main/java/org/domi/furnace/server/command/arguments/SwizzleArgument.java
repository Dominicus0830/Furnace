// 바닐라 SwizzleArgument(minecraft:swizzle) — x·y·z 를 겹치지 않게 늘어놓은 축 집합("xyz", "x", 빈 문자열도 가능). 속성 없음.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;

public final class SwizzleArgument implements ArgumentType<EnumSet<SwizzleArgument.Axis>> {

    public enum Axis { X, Y, Z }

    private static final List<String> EXAMPLES = List.of("xyz", "x");
    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Text.translatable("arguments.swizzle.invalid"));

    public static SwizzleArgument swizzle() { return new SwizzleArgument(); }

    @SuppressWarnings("unchecked")
    public static EnumSet<Axis> getSwizzle(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, EnumSet.class); }

    @Override
    public EnumSet<Axis> parse(StringReader reader) throws CommandSyntaxException {
        EnumSet<Axis> set = EnumSet.noneOf(Axis.class);
        while (reader.canRead() && reader.peek() != ' ') {
            char c = reader.read();
            Axis a = switch (c) {
                case 'x' -> Axis.X;
                case 'y' -> Axis.Y;
                case 'z' -> Axis.Z;
                default -> throw ERROR_INVALID.createWithContext(reader);
            };
            if (set.contains(a)) throw ERROR_INVALID.createWithContext(reader);
            set.add(a);
        }
        return set;
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:swizzle"; }
}
