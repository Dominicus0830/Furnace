// 바닐라 GameModeArgument(minecraft:gamemode) — 이름(survival·creative·adventure·spectator).
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
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.world.GameMode;

public final class GameModeArgument implements ArgumentType<GameMode> {

    private static final List<String> EXAMPLES = List.of("survival", "creative");
    public static final DynamicCommandExceptionType ERROR_INVALID =
            new DynamicCommandExceptionType(v -> Text.translatable("argument.gamemode.invalid", v));

    public static GameModeArgument gameMode() { return new GameModeArgument(); }

    public static GameMode getGameMode(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, GameMode.class); }

    @Override
    public GameMode parse(StringReader reader) throws CommandSyntaxException {
        String s = reader.readUnquotedString();
        for (GameMode m : GameMode.values()) if (m.key().equals(s)) return m;
        throw ERROR_INVALID.createWithContext(reader, s);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof CommandSource)) return Suggestions.empty();
        return SharedSuggestions.suggest(java.util.Arrays.stream(GameMode.values()).map(GameMode::key).toList(), builder);
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:gamemode"; }
}
