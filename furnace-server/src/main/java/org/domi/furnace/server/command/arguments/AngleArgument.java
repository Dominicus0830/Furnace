// 바닐라 AngleArgument(minecraft:angle) — 각도 하나(절대 또는 '~' 상대: 실행자의 yaw 기준), wrapDegrees 로 -180..180.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;
import org.domi.furnace.api.text.Text;

public final class AngleArgument implements ArgumentType<AngleArgument.SingleAngle> {

    private static final List<String> EXAMPLES = List.of("0", "~", "~-5");
    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Text.translatable("argument.angle.incomplete"));
    public static final SimpleCommandExceptionType ERROR_INVALID_ANGLE = new SimpleCommandExceptionType(Text.translatable("argument.angle.invalid"));

    public record SingleAngle(float angle, boolean relative) {
        public float getAngle(CommandSource source) {
            return wrapDegrees(relative ? angle + source.yRot() : angle);
        }
    }

    public static AngleArgument angle() { return new AngleArgument(); }

    public static float getAngle(CommandContext<CommandSource> ctx, String name) {
        return ctx.getArgument(name, SingleAngle.class).getAngle(ctx.getSource());
    }

    /** Mth.wrapDegrees(float). */
    public static float wrapDegrees(float v) {
        float f = v % 360.0f;
        if (f >= 180.0f) f -= 360.0f;
        if (f < -180.0f) f += 360.0f;
        return f;
    }

    @Override
    public SingleAngle parse(StringReader reader) throws CommandSyntaxException {
        if (!reader.canRead()) throw ERROR_NOT_COMPLETE.createWithContext(reader);
        boolean relative = WorldCoordinate.isRelative(reader);
        float f = reader.canRead() && reader.peek() != ' ' ? reader.readFloat() : 0.0f;
        if (Float.isNaN(f) || Float.isInfinite(f)) throw ERROR_INVALID_ANGLE.createWithContext(reader);
        return new SingleAngle(f, relative);
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:angle"; }
}
