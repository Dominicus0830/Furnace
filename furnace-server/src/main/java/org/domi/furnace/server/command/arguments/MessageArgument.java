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
import org.domi.furnace.api.text.Text;

public final class MessageArgument implements ArgumentType<MessageArgument.Message> {

    public static final Dynamic2CommandExceptionType TOO_LONG = new Dynamic2CommandExceptionType((len, max) -> Text.translatable("argument.message.too_long", len, max));
    private static final List<String> EXAMPLES = List.of("Hello world!", "foo", "@e", "Hello @p :)");
    private static final int MAX_LENGTH = 256;

    public record Part(int start, int end, EntitySelector selector) {}

    public record Message(String text, List<Part> parts) {
        /**
         * 선택자 부분을 찾은 이름들(회색 ", " 로 이음)로 바꾼 텍스트 — 일반 엔티티도 고른다(바닐라 Message.resolveComponent).
         * 엔티티는 region 이 나눠 가져 읽기가 끝난 뒤 완성되는 future 다(플레이어만이면 이미 끝나 있다).
         */
        public java.util.concurrent.CompletableFuture<Text> toComponentAsync(CommandSource source) {
            if (parts.isEmpty() || !source.hasPermission(2)) return java.util.concurrent.CompletableFuture.completedFuture(Text.text(text));
            List<java.util.concurrent.CompletableFuture<List<org.domi.furnace.server.entity.Rider>>> found = new ArrayList<>(parts.size());
            try {
                for (Part p : parts) found.add(p.selector().findRiders(source));
            } catch (CommandSyntaxException e) {
                return java.util.concurrent.CompletableFuture.failedFuture(e);
            }
            return org.domi.furnace.server.command.EntityOps.thenOnGlobal(source, java.util.concurrent.CompletableFuture.allOf(found.toArray(new java.util.concurrent.CompletableFuture[0])), v -> {
                Text out = Text.text(text.substring(0, parts.get(0).start()));
                int i = parts.get(0).start();
                for (int k = 0; k < parts.size(); k++) {
                    Part p = parts.get(k);
                    if (p.start() > i) out = out.append(Text.text(text.substring(i, p.start())));
                    List<org.domi.furnace.server.entity.Rider> riders = found.get(k).join();
                    if (!riders.isEmpty()) out = out.append(org.domi.furnace.server.command.ComponentUtils.formatList(riders, org.domi.furnace.server.command.ComponentUtils.defaultSeparator()));
                    i = p.end();
                }
                if (i < text.length()) out = out.append(Text.text(text.substring(i)));
                return out;
            });
        }

        /** 선택자 부분을 찾은 이름들로 바꾼 텍스트(플레이어만 — 동기 경로: 강퇴·밴 사유 등). */
        public Text toComponent(CommandSource source) throws CommandSyntaxException {
            if (parts.isEmpty() || !source.hasPermission(2)) return Text.text(text);
            Text out = Text.text(text.substring(0, parts.get(0).start()));
            int i = parts.get(0).start();
            for (Part p : parts) {
                if (p.start() > i) out = out.append(Text.text(text.substring(i, p.start())));
                List<Player> found = p.selector().findPlayers(source);
                if (!found.isEmpty()) out = out.append(EntitySelector.joinNames(found));
                i = p.end();
            }
            if (i < text.length()) out = out.append(Text.text(text.substring(i)));
            return out;
        }
    }

    public static MessageArgument message() { return new MessageArgument(); }

    /**
     * 바닐라 resolveChatMessage — 서명된 인자가 있으면 그 메시지(꾸민 내용은 선택자를 펼친 것), 없으면 시스템(위장) 메시지. future 판: 일반 엔티티를 고르는 선택자가 있어도 읽기가 끝난 뒤 전역 스레드에서 callback 을 부르고 result 를 돌려준다.
     * 명령 본문은 이 future 를 EntityOps.result 로 감싸 돌려준다.
     */
    public static java.util.concurrent.CompletableFuture<Integer> resolveChatMessageAsync(
            CommandContext<CommandSource> ctx, String name, int result,
            java.util.function.Consumer<org.domi.furnace.server.chat.PlayerChatMessage> callback) {
        Message message = ctx.getArgument(name, Message.class);
        CommandSource source = ctx.getSource();
        var signed = source.signedArgument(name);
        return org.domi.furnace.server.command.EntityOps.thenOnGlobal(source, message.toComponentAsync(source), component -> {
            if (signed != null) callback.accept(signed.withUnsignedContent(component));
            else callback.accept(org.domi.furnace.server.chat.PlayerChatMessage.system(message.text()).withUnsignedContent(component));
            return result;
        });
    }

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
