// 바닐라 Vec2Argument(minecraft:vec2) — "x z" 두 좌표(절대·'~' 상대). 속성 없음. centerCorrect 면 정수 절대값은 +0.5.
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

public record Vec2Argument(boolean centerCorrect) implements ArgumentType<WorldCoordinates> {

    /** 바닐라 net.minecraft.world.phys.Vec2(float 두 개). */
    public record Vec2(float x, float y) {}

    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Text.translatable("argument.pos2d.incomplete"));
    private static final List<String> EXAMPLES = List.of("0 0", "~ ~", "0.1 -0.5", "~1 ~-2");

    public static Vec2Argument vec2() { return new Vec2Argument(true); }

    public static Vec2Argument vec2(boolean centerCorrect) { return new Vec2Argument(centerCorrect); }

    /** 바닐라 getVec2: 실행 위치를 기준으로 풀어 (x, z) 를 float 로 줄인다. */
    public static Vec2 getVec2(CommandContext<CommandSource> context, String name) {
        double[] p = context.getArgument(name, WorldCoordinates.class).getPosition(context.getSource());
        return new Vec2((float) p[0], (float) p[2]);
    }

    @Override
    public WorldCoordinates parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        if (!reader.canRead()) throw ERROR_NOT_COMPLETE.createWithContext(reader);
        WorldCoordinate x = WorldCoordinate.parseDouble(reader, centerCorrect);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate z = WorldCoordinate.parseDouble(reader, centerCorrect);
            return new WorldCoordinates(x, new WorldCoordinate(true, 0.0), z);
        }
        reader.setCursor(start);
        throw ERROR_NOT_COMPLETE.createWithContext(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof CommandSource)) return Suggestions.empty();
        String remaining = builder.getRemaining();
        // 서버 쪽 CommandSourceStack 은 절대 좌표 후보로 항상 "~ ~ ~" 만 낸다('^' 로 시작하면 로컬 후보).
        Collection<SharedSuggestions.TextCoordinates> candidates = !remaining.isEmpty() && remaining.charAt(0) == '^'
                ? List.of(SharedSuggestions.TextCoordinates.DEFAULT_LOCAL) : List.of(SharedSuggestions.TextCoordinates.DEFAULT_GLOBAL);
        return SharedSuggestions.suggest2DCoordinates(remaining, candidates, builder, s -> {
            try {
                parse(new StringReader(s));
                return true;
            } catch (CommandSyntaxException e) {
                return false;
            }
        });
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:vec2"; }
}
