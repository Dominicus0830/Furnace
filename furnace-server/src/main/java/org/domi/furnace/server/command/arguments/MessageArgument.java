// 바닐라 MessageArgument(minecraft:message) — 나머지 전부(최대 256자). 권한 2 이상이면 안의 @선택자를 이름 목록으로 바꾼다.
package org.domi.furnace.server.command.arguments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.Text;

public final class MessageArgument implements ArgumentType<MessageArgument.Message> {

    public static final Dynamic2CommandExceptionType TOO_LONG = new Dynamic2CommandExceptionType((len, max) -> Text.translatable("argument.message.too_long", len, max));
    private static final List<String> EXAMPLES = List.of("Hello world!", "foo", "@e", "Hello @p :)");
    private static final int MAX_LENGTH = 256;

    public record Part(int start, int end, EntitySelector selector) {}

    public record Message(String text, List<Part> parts) {
        /** 선택자 부분을 찾은 이름들로 바꾼 텍스트. */
        public Text toComponent(CommandSource source) throws CommandSyntaxException {
            if (parts.isEmpty() || !source.hasPermission(2)) return Text.literal(text);
            Text out = Text.literal(text.substring(0, parts.get(0).start()));
            int i = parts.get(0).start();
            for (Part p : parts) {
                if (p.start() > i) out.append(Text.literal(text.substring(i, p.start())));
                List<Player> found = p.selector().findPlayers(source);
                if (!found.isEmpty()) out.append(EntitySelector.joinNames(found));
                i = p.end();
            }
            if (i < text.length()) out.append(Text.literal(text.substring(i)));
            return out;
        }
    }

    public static MessageArgument message() { return new MessageArgument(); }

    public static Text getMessage(CommandContext<CommandSource> ctx, String name) throws CommandSyntaxException {
        return ctx.getArgument(name, Message.class).toComponent(ctx.getSource());
    }

    @Override
    public Message parse(StringReader reader) throws CommandSyntaxException { return parseText(reader, true); }

    @Override
    public <S> Message parse(StringReader reader, S source) throws CommandSyntaxException {
        return parseText(reader, !(source instanceof CommandSource cs) || cs.hasPermission(2));
    }

    public static Message parseText(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
        if (reader.getRemainingLength() > MAX_LENGTH) throw TOO_LONG.create(reader.getRemainingLength(), MAX_LENGTH);
        String s = reader.getRemaining();
        if (!allowSelectors) {
            reader.setCursor(reader.getTotalLength());
            return new Message(s, List.of());
        }
        List<Part> parts = new ArrayList<>();
        int base = reader.getCursor();
        while (reader.canRead()) {
            if (reader.peek() == '@') {
                int j = reader.getCursor();
                try {
                    EntitySelector selector = new EntitySelectorParser(reader, true).parse();
                    parts.add(new Part(j - base, reader.getCursor() - base, selector));
                } catch (CommandSyntaxException e) {
                    if (e.getType() != EntitySelectorParser.ERROR_MISSING_SELECTOR_TYPE && e.getType() != EntitySelectorParser.ERROR_UNKNOWN_SELECTOR_TYPE) throw e;
                    reader.setCursor(j + 1);
                }
            } else {
                reader.skip();
            }
        }
        return new Message(s, parts);
    }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:message"; }
}
