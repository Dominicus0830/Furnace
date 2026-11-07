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
            // 바닐라는 lenient Gson 이 JSON 을 읽다 난 오류 메시지를 그대로 보인다("java.io.EOFException: End of input at line 1 column 19 path $.text")
            String msg = gsonSyntaxMessage(reader.getRemaining());
            if (msg == null) msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw ERROR_INVALID_JSON.createWithContext(reader, msg);
        }
    }

    /** 바닐라 ParserUtils.parseJson 과 같은 lenient Gson 파싱이 낸 오류 메시지(문법 오류가 아니면 null). */
    private static String gsonSyntaxMessage(String remaining) {
        try {
            var jr = new com.google.gson.stream.JsonReader(new java.io.StringReader(remaining));
            jr.setLenient(true);
            com.google.gson.JsonParser.parseReader(jr);
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:component"; }
}
