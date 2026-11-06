// 바닐라 ResourceLocationArgument(minecraft:resource_location) — [0-9a-z_:/.-] 를 읽어 namespace:path 로.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;

public final class ResourceLocationArgument implements ArgumentType<ResourceId> {

    private static final List<String> EXAMPLES = List.of("foo", "foo:bar", "012");
    public static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Text.translatable("argument.id.invalid"));

    public static ResourceLocationArgument id() { return new ResourceLocationArgument(); }

    public static ResourceId getId(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, ResourceId.class); }

    /** 바닐라 ResourceLocation.isAllowedInResourceLocation. */
    public static boolean isAllowed(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c == '_' || c == ':' || c == '/' || c == '.' || c == '-';
    }

    @Override
    public ResourceId parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && isAllowed(reader.peek())) reader.skip();
        String s = reader.getString().substring(start, reader.getCursor());
        ResourceId id = ResourceId.tryParse(s);
        if (id == null) {
            reader.setCursor(start);
            throw ERROR_INVALID.createWithContext(reader);
        }
        return id;
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:resource_location"; }
}
