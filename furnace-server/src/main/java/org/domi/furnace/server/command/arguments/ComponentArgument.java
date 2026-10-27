// 바닐라 ComponentArgument(minecraft:component) — lenient JSON 텍스트 컴포넌트 하나. 읽은 만큼만 소비하고 나머지는 다음 인자로 남는다.
package org.domi.furnace.server.command.arguments;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;

public final class ComponentArgument implements ArgumentType<Text> {

    private static final List<String> EXAMPLES = List.of("\"hello world\"", "\"\"", "{\"text\":\"hello world\"}", "[\"\"]");
    public static final DynamicCommandExceptionType ERROR_INVALID_JSON = new DynamicCommandExceptionType(o -> Text.translatable("argument.component.invalid", o));

    public static ComponentArgument textComponent() { return new ComponentArgument(); }

    public static Text getComponent(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, Text.class); }

    @Override
    public Text parse(StringReader reader) throws CommandSyntaxException {
        try {
            JsonTextSerializer.Parsed p = JsonTextSerializer.json().deserializePrefix(reader.getString(), reader.getCursor());
            reader.setCursor(p.end());
            return p.text();
        } catch (RuntimeException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw ERROR_INVALID_JSON.createWithContext(reader, msg);
        }
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:component"; }
}
