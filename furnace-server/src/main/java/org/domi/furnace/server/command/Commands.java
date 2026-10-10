// 바닐라 Commands — 디스패처, 명령 실행(오류 문구·위치 표시), 플레이어별 명령 트리 전송, 서버 측 자동완성.
package org.domi.furnace.server.command;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.ParseResults;
import org.domi.furnace.server.command.brigadier.RequiredArgumentBuilder;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.api.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Commands {

    private static final Logger log = LoggerFactory.getLogger(Commands.class);
    public static final int LEVEL_ALL = 0, LEVEL_MODERATORS = 1, LEVEL_GAMEMASTERS = 2, LEVEL_ADMINS = 3, LEVEL_OWNERS = 4;
    /** 자동완성 응답 후보 상한(바닐라 1000). */
    private static final int MAX_SUGGESTIONS = 1000;

    private final ServerCore server;
    private final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();

    public Commands(ServerCore server) {
        this.server = server;
        BuiltinCommands.registerAll(dispatcher);
    }

    public CommandDispatcher<CommandSource> getDispatcher() { return dispatcher; }

    public static LiteralArgumentBuilder<CommandSource> literal(String name) { return LiteralArgumentBuilder.literal(name); }

    public static <T> RequiredArgumentBuilder<CommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public static Predicate<CommandSource> hasPermission(int level) { return s -> s.hasPermission(level); }

    /** 앞의 '/' 를 떼고 실행(바닐라 performPrefixedCommand). */
    public void performPrefixedCommand(CommandSource source, String command) {
        command = command.startsWith("/") ? command.substring(1) : command;
        performCommand(dispatcher.parse(command, source), command);
    }

    public void performCommand(ParseResults<CommandSource> parse, String command) {
        CommandSource source = parse.getContext().getSource();
        try {
            validateParseResults(parse);
        } catch (CommandSyntaxException e) {
            sendSyntaxFailure(source, e, command);
            return;
        }
        try {
            dispatcher.execute(parse);
        } catch (CommandSyntaxException e) {
            // 실행 중 오류(예: 대상 없음)는 메시지만(바닐라 CommandSourceStack.handleError).
            source.sendFailure(e.getRawMessage());
        } catch (Exception e) {
            Text detail = Text.text(e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            log.error("Command exception: /{}", command, e);
            source.sendFailure(Text.translatable("command.failed").hoverText(detail));
        }
    }

    private static void validateParseResults(ParseResults<CommandSource> parse) throws CommandSyntaxException {
        if (!parse.getReader().canRead()) return;
        if (parse.getExceptions().size() == 1) throw parse.getExceptions().values().iterator().next();
        if (parse.getContext().getRange().isEmpty()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader());
        }
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parse.getReader());
    }

    /** 바닐라 finishParsing 의 실패 표시: 사유 + "...입력<빨간 밑줄 나머지>[HERE]". */
    private static void sendSyntaxFailure(CommandSource source, CommandSyntaxException e, String command) {
        source.sendFailure(e.getRawMessage());
        if (e.getInput() != null && e.getCursor() >= 0) {
            int i = Math.min(e.getInput().length(), e.getCursor());
            Text context = Text.empty().color(org.domi.furnace.api.text.NamedTextColor.GRAY).clickEvent(new org.domi.furnace.api.text.ClickEvent(org.domi.furnace.api.text.ClickEvent.Action.SUGGEST_COMMAND, "/" + command));
            if (i > 10) context = context.append(Text.text("..."));
            context = context.append(Text.text(e.getInput().substring(Math.max(0, i - 10), i)));
            if (i < e.getInput().length()) {
                context = context.append(Text.text(e.getInput().substring(i)).color(org.domi.furnace.api.text.NamedTextColor.RED).decoration(org.domi.furnace.api.text.TextDecoration.UNDERLINED, true));
            }
            context = context.append(Text.translatable("command.context.here").color(org.domi.furnace.api.text.NamedTextColor.RED).decoration(org.domi.furnace.api.text.TextDecoration.ITALIC, true));
            source.sendFailure(context);
        }
    }

    /** 플레이어가 쓸 수 있는 노드만 담은 명령 트리를 보낸다. */
    public void sendCommands(Player player) {
        player.send(CommandTreePacket.forSource(dispatcher.getRoot(), CommandSource.player(server, player)));
    }

    public CommandTreePacket treeFor(CommandSource source) {
        return CommandTreePacket.forSource(dispatcher.getRoot(), source);
    }

    /** 클라이언트 자동완성 요청(바닐라 handleCustomCommandSuggestions). */
    public CompletableFuture<Suggestions> suggestions(CommandSource source, String text) {
        StringReader reader = new StringReader(text);
        if (reader.canRead() && reader.peek() == '/') reader.skip();
        ParseResults<CommandSource> parse = dispatcher.parse(reader, source);
        return dispatcher.getCompletionSuggestions(parse).thenApply(s -> s.getList().size() <= MAX_SUGGESTIONS ? s
                : new Suggestions(s.getRange(), List.copyOf(s.getList().subList(0, MAX_SUGGESTIONS))));
    }
}
