// 바닐라 DimensionArgument(minecraft:dimension) — 차원 id. 풀어 보면 서버가 가진 차원 월드(없으면 argument.dimension.invalid).
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
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.world.World;

public final class DimensionArgument implements ArgumentType<ResourceId> {

    private static final List<String> EXAMPLES = List.of("overworld", "the_nether");
    public static final DynamicCommandExceptionType ERROR_INVALID_VALUE =
            new DynamicCommandExceptionType(id -> Text.translatable("argument.dimension.invalid", id));

    public static DimensionArgument dimension() { return new DimensionArgument(); }

    /** 바닐라 getDimension: 서버에 없는 차원이면 argument.dimension.invalid. */
    public static World getDimension(CommandContext<CommandSource> ctx, String name) throws CommandSyntaxException {
        ResourceId id = ctx.getArgument(name, ResourceId.class);
        String key = id.namespace() + ":" + id.path();
        World w = ctx.getSource().getServer().world(key);
        if (w == null) throw ERROR_INVALID_VALUE.create(key);
        return w;
    }

    @Override
    public ResourceId parse(StringReader reader) throws CommandSyntaxException { return ResourceLocationArgument.id().parse(reader); }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof CommandSource s) {
            return SharedSuggestions.suggest(s.getServer().worlds().stream().map(World::dimension).toList(), builder);
        }
        return Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:dimension"; }
}
