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
            // 바닐라 ParserUtils.parseJson: 엄격한 Gson 이 읽다 난 오류의 원인 메시지를 보이고, 커서는 JSON 리더가 멈춘 곳까지 옮긴다
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            var jr = new com.google.gson.stream.JsonReader(new java.io.StringReader(reader.getRemaining()));
            jr.setLenient(false);
            try {
                com.google.gson.internal.Streams.parse(jr);
            } catch (com.google.gson.JsonParseException g) {
                msg = g.getCause() != null ? g.getCause().getMessage() : g.getMessage();
                reader.setCursor(reader.getCursor() + readerOffset(jr));
            } catch (RuntimeException ignored) {
                // 엄격한 읽기도 실패하면 위 메시지 그대로
            }
            throw ERROR_INVALID_JSON.createWithContext(reader, msg);
        }
    }

    /** 바닐라 ParserUtils 가 반사로 읽는 JsonReader 의 pos - lineStart. 못 읽으면 0. */
    private static int readerOffset(com.google.gson.stream.JsonReader jr) {
        try {
            var pos = com.google.gson.stream.JsonReader.class.getDeclaredField("pos");
            var lineStart = com.google.gson.stream.JsonReader.class.getDeclaredField("lineStart");
            pos.setAccessible(true);
            lineStart.setAccessible(true);
            return pos.getInt(jr) - lineStart.getInt(jr);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:component"; }
}
