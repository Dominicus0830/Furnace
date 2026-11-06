// 바닐라 ColumnPosArgument(minecraft:column_pos) — "x z" 정수 좌표(절대·'~' 상대). 속성 없음.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;

public final class ColumnPosArgument implements ArgumentType<WorldCoordinates> {

    private static final List<String> EXAMPLES = List.of("0 0", "~ ~", "~1 ~-2", "^ ^", "^-1 ^0");
    public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Text.translatable("argument.pos2d.incomplete"));

    /** 바닐라 ColumnPos(블록 x, z). */
    public record ColumnPos(int x, int z) {
        public int chunkX() { return x >> 4; }

        public int chunkZ() { return z >> 4; }
    }

    public static ColumnPosArgument columnPos() { return new ColumnPosArgument(); }

    /** 바닐라 getColumnPos: 실행 위치 기준으로 풀어 BlockPos.containing 한 x·z. */
    public static ColumnPos getColumnPos(CommandContext<CommandSource> ctx, String name) {
        double[] p = ctx.getArgument(name, WorldCoordinates.class).getPosition(ctx.getSource());
        return new ColumnPos((int) Math.floor(p[0]), (int) Math.floor(p[2]));
    }

    @Override
    public WorldCoordinates parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        if (!reader.canRead()) throw ERROR_NOT_COMPLETE.createWithContext(reader);
        WorldCoordinate x = WorldCoordinate.parseInt(reader);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate z = WorldCoordinate.parseInt(reader);
            return new WorldCoordinates(x, new WorldCoordinate(true, 0.0), z);
        }
        reader.setCursor(start);
        throw ERROR_NOT_COMPLETE.createWithContext(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof CommandSource)) return Suggestions.empty();
        return SharedSuggestions.suggest2DCoordinates(builder.getRemaining(), List.of(SharedSuggestions.TextCoordinates.DEFAULT_GLOBAL), builder, s -> {
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
    public String networkId() { return "minecraft:column_pos"; }
}
