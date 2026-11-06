// 바닐라 EntityAnchorArgument(minecraft:entity_anchor) — feet 또는 eyes. 속성 없음.
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
import org.domi.furnace.server.entity.Rider;

public final class EntityAnchorArgument implements ArgumentType<EntityAnchorArgument.Anchor> {

    private static final List<String> EXAMPLES = List.of("eyes", "feet");
    private static final DynamicCommandExceptionType ERROR_INVALID = new DynamicCommandExceptionType(n -> Text.translatable("argument.anchor.invalid", n));

    /** 바닐라 EntityAnchorArgument.Anchor: 발 위치 또는 눈 위치. */
    public enum Anchor {
        FEET("feet"), EYES("eyes");

        public final String name;

        Anchor(String name) { this.name = name; }

        public static Anchor byName(String name) {
            for (Anchor a : values()) if (a.name.equals(name)) return a;
            return null;
        }

        /** 엔티티의 앵커 위치 {x, y, z}. */
        public double[] apply(Rider e) {
            return this == FEET ? new double[] {e.x(), e.y(), e.z()} : new double[] {e.x(), e.eyeY(), e.z()};
        }
    }

    public static EntityAnchorArgument anchor() { return new EntityAnchorArgument(); }

    public static Anchor getAnchor(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Anchor.class); }

    @Override
    public Anchor parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String s = reader.readUnquotedString();
        Anchor a = Anchor.byName(s);
        if (a == null) {
            reader.setCursor(start);
            throw ERROR_INVALID.createWithContext(reader, s);
        }
        return a;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestions.suggest(List.of("feet", "eyes"), builder);
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:entity_anchor"; }
}
