// 바닐라 Vec3Argument(minecraft:vec3) — "x y z" 실수 좌표(절대·'~' 상대) 또는 '^' 로컬 좌표. centerCorrect 면 정수 절대값은 +0.5.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;

public record Vec3Argument(boolean centerCorrect) implements ArgumentType<BlockPosArgument.Coordinates> {

    private static final List<String> EXAMPLES = List.of("0 0 0", "0.1 -0.5 .9", "~0.5 ~1 ~-5");

    public static Vec3Argument vec3() { return new Vec3Argument(true); }

    public static Vec3Argument vec3(boolean centerCorrect) { return new Vec3Argument(centerCorrect); }

    /** 바닐라 getVec3: 실행 위치·회전을 기준으로 푼 {x, y, z}. */
    public static double[] getVec3(CommandContext<CommandSource> context, String name) {
        return context.getArgument(name, BlockPosArgument.Coordinates.class).getPosition(context.getSource());
    }

    @Override
    public BlockPosArgument.Coordinates parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') return BlockPosArgument.LocalCoordinates.parse(reader);
        int start = reader.getCursor();
        WorldCoordinate x = WorldCoordinate.parseDouble(reader, centerCorrect);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate y = WorldCoordinate.parseDouble(reader, false);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate z = WorldCoordinate.parseDouble(reader, centerCorrect);
                return new BlockPosArgument.World(new WorldCoordinates(x, y, z));
            }
        }
        reader.setCursor(start);
        throw BlockPosArgument.ERROR_NOT_COMPLETE.createWithContext(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof CommandSource)) return Suggestions.empty();
        String remaining = builder.getRemaining();
        Collection<SharedSuggestions.TextCoordinates> candidates = !remaining.isEmpty() && remaining.charAt(0) == '^'
                ? List.of(SharedSuggestions.TextCoordinates.DEFAULT_LOCAL) : List.of(SharedSuggestions.TextCoordinates.DEFAULT_GLOBAL);
        return SharedSuggestions.suggestCoordinates(remaining, candidates, builder, s -> {
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
    public String networkId() { return "minecraft:vec3"; }
}
