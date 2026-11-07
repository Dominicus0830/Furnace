// 바닐라 ServerFunctionManager(alg) — 함수 라이브러리 참조, #minecraft:tick 스냅샷, 리로드 뒤 첫 틱의 #minecraft:load.
// 전역 스레드 전용(ticking·postReload 는 전역 틱이 읽는다). 라이브러리 참조만 volatile — 명령 인자 해석·자동완성이 읽는다.
// 함수 호출 하나는 CommandQueue 의 별개 최상위 컨텍스트다(바닐라도 함수마다 executeCommandInContext).
package org.domi.furnace.server.function;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.event.server.FunctionExecuteEvent.Trigger;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.exec.CallFunction;
import org.domi.furnace.server.command.exec.ResultCallback;
import org.domi.furnace.server.data.ResourceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FunctionManager {

    private static final Logger log = LoggerFactory.getLogger(FunctionManager.class);
    public static final ResourceId TICK_FUNCTION_TAG = ResourceId.parse("minecraft:tick");
    public static final ResourceId LOAD_FUNCTION_TAG = ResourceId.parse("minecraft:load");

    private final ServerCore server;
    private final ThreadOwner owner;
    private volatile FunctionLibrary library;
    private List<CommandFunction> ticking = List.of();
    private boolean postReload;
    /** 아직 시작하지 못하고 명령 큐에서 기다리는 tick 함수(설계 §4.2 — 같은 함수가 이미 기다리면 이번 틱 것은 건너뛴다). */
    private final Set<ResourceId> waitingTick = new HashSet<>();
    private final Set<ResourceId> warnedSkip = new HashSet<>();

    public FunctionManager(ServerCore server, ThreadOwner owner, FunctionLibrary library) {
        this.server = server;
        this.owner = owner;
        this.library = library;
        this.ticking = List.copyOf(library.tag(TICK_FUNCTION_TAG));
        this.postReload = true;
    }

    private void checkThread() {
        assert owner.isOwnerThread() : "FunctionManager 는 전역 스레드 전용: " + Thread.currentThread().getName();
    }

    public FunctionLibrary library() { return library; }

    /**
     * 전역 틱 맨 앞(ServerCore.tick). /tick freeze 중에는 ServerCore 가 tick() 자체를 건너뛰므로 여기도 돌지 않고, step 중에는 돈다
     * (바닐라 alg.b 의 runsNormally 검사와 같다). 할 일이 없으면 바로 돌아온다.
     */
    public void tick() {
        if (ticking.isEmpty() && (!postReload || library.tag(LOAD_FUNCTION_TAG).isEmpty())) return;
        checkThread();
        if (postReload) {
            postReload = false;
            executeTagFunctions(library.tag(LOAD_FUNCTION_TAG), Trigger.LOAD);
        }
        executeTagFunctions(ticking, Trigger.TICK);
    }

    private void executeTagFunctions(Collection<CommandFunction> functions, Trigger trigger) {
        CommandSource source = gameLoopSender();
        for (CommandFunction f : functions) enqueue(f, source, trigger);
    }

    /** 바닐라 alg.a(ig, et): 함수 하나를 새 최상위 컨텍스트로(콜백 EMPTY). */
    public void execute(CommandFunction function, CommandSource source) { enqueue(function, source, Trigger.SCHEDULED); }

    /** 호출 경로를 지정해 실행(플러그인 FunctionExecuteEvent 의 trigger 로 전해진다). */
    public void execute(CommandFunction function, CommandSource source, Trigger trigger) { enqueue(function, source, trigger); }

    private void enqueue(CommandFunction function, CommandSource source, Trigger trigger) {
        boolean tick = trigger == Trigger.TICK;
        checkThread();
        ResourceId id = function.id();
        CommandFunction.PlainTextFunction instantiated;
        try {
            // 바닐라 alg.a: 인자 없이 인스턴스화 — 매크로 함수는 missing_arguments 로 조용히 실행만 실패한다(태그는 그대로)
            instantiated = function.instantiate(null, server.commands().getDispatcher());
        } catch (FunctionInstantiationException e) {
            return;
        } catch (Exception e) {
            log.warn("Failed to execute function {}", id, e);   // 바닐라 alg.java:59-60
            return;
        }
        if (tick) {
            if (waitingTick.contains(id)) {
                if (warnedSkip.add(id)) log.warn("tick 함수 {} 가 앞 실행을 기다리는 중이라 이번 틱 실행을 건너뜀(region 왕복이 긴 함수)", id);
                return;
            }
            waitingTick.add(id);
        }
        server.commands().queue().enqueue(source, "function " + id, ctx -> {
            if (tick) waitingTick.remove(id);
            CallFunction.queueInitial(ctx, instantiated, source, ResultCallback.EMPTY, trigger);
        });
    }

    /** 바닐라 FunctionCallback·FunctionTagCallback: 라이브러리에서 지금 찾아 게임 루프 소스로 실행한다(없으면 조용히 무시). */
    public void runScheduled(ScheduledEvents.Callback cb) {
        checkThread();
        if (cb.tag()) {
            for (CommandFunction f : library.tag(cb.id())) execute(f, gameLoopSender());
        } else {
            library.function(cb.id()).ifPresent(f -> execute(f, gameLoopSender()));
        }
    }

    /** 바닐라 alg.a(alf): 새 라이브러리로 바꾸고 tick 스냅샷 갱신, 다음 틱에 #load. /reload 의 background 스레드에서 부르지 말고 전역으로 넘긴다. */
    public void replaceLibrary(FunctionLibrary lib) {
        checkThread();
        this.library = lib;
        this.ticking = List.copyOf(lib.tag(TICK_FUNCTION_TAG));
        this.postReload = true;
        this.warnedSkip.clear();   // 리로드 뒤에는 다시 한 번 경고한다
    }

    /** 바닐라 getGameLoopSender: 서버 소스, 권한 2, 출력 숨김. */
    public CommandSource gameLoopSender() { return CommandSource.console(server).withPermission(2).withSilent(); }

    /** 지표·테스트: 지금 tick 함수 수. */
    public int tickingCount() { return ticking.size(); }
}
