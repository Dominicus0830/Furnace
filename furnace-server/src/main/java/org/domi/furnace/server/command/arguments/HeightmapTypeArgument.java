// 바닐라 HeightmapTypeArgument(minecraft:heightmap) — world_surface·ocean_floor·motion_blocking·motion_blocking_no_leaves. 속성 없음.
package org.domi.furnace.server.command.arguments;

import java.util.Arrays;
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
import org.domi.furnace.server.world.HeightmapType;

public final class HeightmapTypeArgument implements ArgumentType<HeightmapType> {

    private static final DynamicCommandExceptionType ERROR_INVALID = new DynamicCommandExceptionType(v -> Text.translatable("argument.enum.invalid", v));

    public static HeightmapTypeArgument heightmap() { return new HeightmapTypeArgument(); }

    public static HeightmapType getHeightmap(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, HeightmapType.class); }

    @Override
    public HeightmapType parse(StringReader reader) throws CommandSyntaxException {
        String s = reader.readUnquotedString();
        HeightmapType t = HeightmapType.byKey(s);
        if (t == null) throw ERROR_INVALID.createWithContext(reader, s);
        return t;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestions.suggest(Arrays.stream(HeightmapType.values()).map(HeightmapType::key).toList(), builder);
    }

    @Override
    public Collection<String> getExamples() { return List.of("world_surface", "ocean_floor"); }

    @Override
    public String networkId() { return "minecraft:heightmap"; }
}
