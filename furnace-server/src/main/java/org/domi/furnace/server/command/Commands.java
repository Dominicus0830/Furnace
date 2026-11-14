// 바닐라 Commands — 디스패처, 명령 실행(오류 문구·위치 표시), 플레이어별 명령 트리 전송, 서버 측 자동완성.
package org.domi.furnace.server.command;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.domi.furnace.server.command.exec.CommandFuture;
import org.domi.furnace.server.command.exec.CommandQueue;
import org.domi.furnace.server.command.exec.CommandResult;
import org.domi.furnace.server.command.exec.ExecutionContext;
import org.domi.furnace.server.command.exec.ResultCallback;
import org.domi.furnace.api.concurrent.ThreadOwner;
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
    /** 바닐라 명령마다 붙는 이름공간 별칭의 접두(Paper 와 같다: minecraft:give). */
    public static final String VANILLA_NAMESPACE_PREFIX = "minecraft:";
    /** 자동완성 응답 후보 상한(바닐라 1000). */
    private static final int MAX_SUGGESTIONS = 1000;

    private final ServerCore server;
    private final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
    private final CommandQueue queue;

    public Commands(ServerCore server) {
        this.server = server;
        BuiltinCommands.registerAll(dispatcher);
        var global = server.scheduler().global();
        this.queue = new CommandQueue(global, (ThreadOwner) global,
                () -> server.gameRules().getInt("maxCommandChainLength"), () -> server.gameRules().getInt("maxCommandForkCount"),
                CommandQueue.DEFAULT_DEFERRED_TIMEOUT);
        // 종료 순서: 명령 큐를 전역 스레드보다 먼저 닫는다(스케줄러 close 훅은 global.close() 전에 돈다)
        if (server.scheduler() instanceof org.domi.furnace.server.sched.FurnaceSchedulerImpl impl) impl.addCloseHook(queue::close);
    }

    public CommandDispatcher<CommandSource> getDispatcher() { return dispatcher; }

    public static LiteralArgumentBuilder<CommandSource> literal(String name) { return LiteralArgumentBuilder.literal(name); }

    public static <T> RequiredArgumentBuilder<CommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public static Predicate<CommandSource> hasPermission(int level) { return s -> s.hasPermission(level); }

    /** 최상위 명령 큐(전역 스레드). */
    public CommandQueue queue() { return queue; }

    private ThreadOwner globalOwner() { return (ThreadOwner) server.scheduler().global(); }

    /**
     * 앞의 '/' 를 떼고 실행(바닐라 performPrefixedCommand). 결과는 반환값이 아니라 소스 콜백으로 흐른다.
     * 전역 스레드가 아니면 전역으로 넘기고 바로 돌아온다(플러그인 스레드 등). 전역에서는 큐에 넣는다(앞 컨텍스트가 없으면 바로 돈다).
     */
    public void performPrefixedCommand(CommandSource source, String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        if (!globalOwner().isOwnerThread()) {
            try {
                server.scheduler().global().execute(() -> performPrefixedCommand(source, cmd));
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // 종료 중: 명령을 버린다(설계 §5 I6)
            }
            return;
        }
        performCommand(dispatcher.parse(cmd, source), cmd);
    }

    /** 바닐라 performCommand: 파싱 결과를 검사해 실패면 메시지, 아니면 실행 큐에 넣는다. */
    public void performCommand(ParseResults<CommandSource> parse, String command) {
        if (!globalOwner().isOwnerThread()) {
            try {
                server.scheduler().global().execute(() -> performCommand(parse, command));
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // 종료 중: 명령을 버린다(설계 §5 I6)
            }
            return;
        }
        perform(parse, command, null);
    }

    /**
     * 테스트·플러그인용: 명령을 실행하고 컨텍스트가 끝나면 최상위 소스 콜백이 받은 결과로 완료되는 future.
     * 결과를 모으려고 최상위 소스에 기록 콜백을 붙인다(바닐라 최상위는 빈 콜백). 전역 스레드에서 join 하면 assert(데드락).
     */
    public CommandFuture<CommandResult> performPrefixedCommandAsync(CommandSource source, String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        CommandFuture<CommandResult> out = new CommandFuture<>(globalOwner());
        Runnable task = () -> {
            try {
                CommandResult.Recorder rec = new CommandResult.Recorder();
                CommandSource src = source.withCallback(rec, ResultCallback::chain);   // 부른 쪽 콜백은 그대로 두고 덧붙인다
                perform(dispatcher.parse(cmd, src), cmd, src).whenComplete((v, ex) -> {
                    if (ex != null) out.completeExceptionally(ex);
                    else out.complete(rec.snapshot());
                });
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        };
        if (globalOwner().isOwnerThread()) task.run();
        else {
            try {
                server.scheduler().global().execute(task);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                out.complete(CommandResult.NONE);   // 종료 중: 실행하지 않고 끝낸다
            }
        }
        return out;
    }

    /** 플러그인 performCommand 의 future 판: 성공 콜백이 한 번이라도 왔는가(마지막 콜백 기준). */
    public CommandFuture<Boolean> performCommandAsync(CommandSource source, String command) {
        CommandFuture<Boolean> out = new CommandFuture<>(globalOwner());
        performPrefixedCommandAsync(source, command).whenComplete((r, ex) -> {
            if (ex != null) out.completeExceptionally(ex);
            else out.complete(r.success());
        });
        return out;
    }

    /** 전역 스레드: 검사 → 평탄화 → 큐. 돌려주는 future 는 컨텍스트 종료(파싱 실패면 즉시 완료). */
    private CompletableFuture<Void> perform(ParseResults<CommandSource> parse, String command, CommandSource sourceOverride) {
        CommandSource source = sourceOverride != null ? sourceOverride : parse.getContext().getSource();
        ContextChain<CommandSource> chain;
        try {
            validateParseResults(parse);
            chain = ContextChain.tryFlatten(parse.getContext().build(command)).orElseThrow(
                    () -> CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader()));
        } catch (CommandSyntaxException e) {
            sendSyntaxFailure(source, e, command);
            return CompletableFuture.completedFuture(null);
        }
        return queue.enqueue(source, command,
                ctx -> ExecutionContext.queueInitialCommandExecution(ctx, command, chain, source, ResultCallback.EMPTY));
    }

    public static void validateParseResults(ParseResults<CommandSource> parse) throws CommandSyntaxException {
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
        player.send(treeFor(CommandSource.player(server, player)));
    }

    /**
     * source 가 쓸 수 있는 명령 트리. 플레이어 소스면 PlayerCommandSendEvent(구독자가 있을 때만)를 부르고 처리기가 뺀 최상위 이름을 트리에서 뺀다 —
     * 접속 때(연결 스레드)·권한 변경·명령 등록 변경·Player.updateCommands 가 모두 이 길로 온다.
     */
    public CommandTreePacket treeFor(CommandSource source) {
        Player player = source.getPlayer();
        var events = server.events();
        if (player == null || !events.hasSubscribers(org.domi.furnace.api.event.player.PlayerCommandSendEvent.class)) {
            return CommandTreePacket.forSource(dispatcher.getRoot(), source, sendNamespaced());
        }
        return CommandTreePacket.forSource(dispatcher.getRoot(), source, sendNamespaced(), names -> {
            var ev = events.call(new org.domi.furnace.api.event.player.PlayerCommandSendEvent(server.plugins().player(player), names));
            names.retainAll(ev.commands());   // 처리기가 뺀 이름만 뺀다(더한 이름은 무시)
        });
    }

    private static boolean sendNamespaced() { return org.domi.furnace.server.config.OperatorOptions.current().sendNamespacedCommands(); }

    /** 클라이언트 자동완성 요청(바닐라 handleCustomCommandSuggestions). */
    public CompletableFuture<Suggestions> suggestions(CommandSource source, String text) {
        return suggestions(source, text, source.getPlayer() != null);
    }

    /** forClient: 접속자 패킷 응답이면 true — send-namespaced=false 일 때만 이름공간 명령을 거른다(콘솔 자동완성은 거르지 않는다, Paper 와 같다). */
    public CompletableFuture<Suggestions> suggestions(CommandSource source, String text, boolean forClient) {
        StringReader reader = new StringReader(text);
        if (reader.canRead() && reader.peek() == '/') reader.skip();
        ParseResults<CommandSource> parse = dispatcher.parse(reader, source);
        return dispatcher.getCompletionSuggestions(parse).thenApply(s0 -> {
            Suggestions s = s0;
            // send-namespaced=false: 명령 이름 자리(첫 낱말)의 자동완성에서도 이름공간 명령을 뺀다(Paper)
            if (forClient && !sendNamespaced() && s.getRange().getStart() <= 1) {
                s = new Suggestions(s.getRange(), s.getList().stream().filter(x -> x.getText().indexOf(':') < 0).toList());
            }
            return s.getList().size() <= MAX_SUGGESTIONS ? s
                    : new Suggestions(s.getRange(), List.copyOf(s.getList().subList(0, MAX_SUGGESTIONS)));
        });
    }
}
