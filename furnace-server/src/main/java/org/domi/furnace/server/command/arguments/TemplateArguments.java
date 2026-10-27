// 바닐라 TemplateRotationArgument(minecraft:template_rotation)·TemplateMirrorArgument(minecraft:template_mirror) — StringRepresentableArgument.
// 값은 BlockStateTable 의 회전·대칭 상수.
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
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;

public final class TemplateArguments implements ArgumentType<Integer> {
    private static final DynamicCommandExceptionType ERROR_INVALID_VALUE =
            new DynamicCommandExceptionType(v -> Text.translatable("argument.enum.invalid", v));

    /** 바닐라 Rotation 순서·이름(NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90). */
    private static final List<String> ROTATIONS = List.of("none", "clockwise_90", "180", "counterclockwise_90");
    private static final int[] ROTATION_VALUES = {BlockStateTable.ROT_NONE, BlockStateTable.ROT_CW90, BlockStateTable.ROT_CW180, BlockStateTable.ROT_CCW90};
    /** 바닐라 Mirror 순서·이름(NONE, LEFT_RIGHT, FRONT_BACK). */
    private static final List<String> MIRRORS = List.of("none", "left_right", "front_back");
    private static final int[] MIRROR_VALUES = {BlockStateTable.NONE, BlockStateTable.LEFT_RIGHT, BlockStateTable.FRONT_BACK};

    private final List<String> names;
    private final int[] values;
    private final String networkId;

    private TemplateArguments(List<String> names, int[] values, String networkId) {
        this.names = names;
        this.values = values;
        this.networkId = networkId;
    }

    public static TemplateArguments rotation() { return new TemplateArguments(ROTATIONS, ROTATION_VALUES, "minecraft:template_rotation"); }

    public static TemplateArguments mirror() { return new TemplateArguments(MIRRORS, MIRROR_VALUES, "minecraft:template_mirror"); }

    public static int get(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Integer.class); }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        String s = reader.readUnquotedString();
        int i = names.indexOf(s);
        if (i < 0) throw ERROR_INVALID_VALUE.createWithContext(reader, s);
        return values[i];
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestions.suggest(names, builder);
    }

    /** 바닐라: 값 이름 앞의 두 개. */
    @Override
    public Collection<String> getExamples() { return names.subList(0, 2); }

    @Override
    public String networkId() { return networkId; }
}
