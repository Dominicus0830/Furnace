// 콘솔 입력(바닐라 DedicatedServer "Server console handler") — 표준 입력의 각 줄을 콘솔 권한(4)으로 전역 스레드에서 실행한다.
// 월드를 불러오기 전에 먼저 열린다: 그때까지 들어온 줄 중 stop 은 바로 종료하고, 나머지(월드가 필요한 명령)는 줄을 세워 두었다가
// 서버가 준비되면(attach) 들어온 순서대로 실행한다.
package org.domi.furnace.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsoleInput {

    private static final Logger log = LoggerFactory.getLogger(ConsoleInput.class);

    private final List<String> pending = new ArrayList<>();
    private final IntConsumer exit;
    private ServerCore core;
    private ConsoleTerminal terminal;

    private ConsoleInput(IntConsumer exit) { this.exit = exit; }

    /** 월드 로딩 전에 부른다. 반환값에 서버 준비 후 attach(core) 를 부른다. 터미널이면 JLine 편집기(console.jline 기본 켜짐)를 쓴다. */
    public static ConsoleInput startEarly() {
        return startEarly(true, DEFAULT_HISTORY_SIZE);
    }

    public static final int DEFAULT_HISTORY_SIZE = 500;

    /** jline=false 거나 터미널이 아니면 예전처럼 표준 입력을 줄 단위로 읽는다. historySize 는 기록 파일(.console_history)의 줄 수(0 = 저장 안 함). */
    public static ConsoleInput startEarly(boolean jline, int historySize) {
        ConsoleInput in = new ConsoleInput(System::exit);
        ConsoleTerminal term = jline ? ConsoleTerminal.open(historySize, java.nio.file.Path.of(".console_history")) : null;
        if (term != null) {
            in.terminal = term;
            term.bindCore(() -> in.currentCore());
            in.jlineThread(term);
        } else {
            in.thread(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
        }
        return in;
    }

    private synchronized ServerCore currentCore() { return core; }

    private void jlineThread(ConsoleTerminal term) {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = term.readLine(InterruptConfirm::onConsoleInterrupt)) != null) line(line);
            } catch (Exception e) {
                log.error("Exception handling console input", e);
            } finally {
                term.close();   // 입력이 끝났다(Ctrl+D) — 터미널을 되돌리고 로그는 표준 출력으로
            }
        }, "Server console handler");
        t.setDaemon(true);
        t.start();
    }

    /** 시험용: 읽기·종료 동작을 바꿔 쓴다. 스레드를 만들지 않고 줄 단위로 line() 을 부르게 한다. */
    static ConsoleInput forTest(IntConsumer exit) { return new ConsoleInput(exit); }

    private void thread(BufferedReader reader) {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) line(line);
            } catch (Exception e) {
                log.error("Exception handling console input", e);
            }
        }, "Server console handler");
        t.setDaemon(true);
        t.start();
    }

    /** 서버가 준비됐다 — 기다리던 줄을 순서대로 실행하고 이후 줄은 바로 실행한다. */
    public void attach(ServerCore core) {
        List<String> queued;
        synchronized (this) {
            this.core = core;
            queued = new ArrayList<>(pending);
            pending.clear();
        }
        for (String c : queued) run(core, c);
    }

    void line(String raw) {
        String command = raw.strip();
        if (command.isEmpty()) return;
        ServerCore c;
        synchronized (this) {
            c = core;
            if (c == null) {
                String bare = (command.startsWith("/") ? command.substring(1) : command).strip().toLowerCase(Locale.ROOT);
                if (bare.equals("stop")) {
                    log.info("Stopping the server before it finished starting");
                    exit.accept(0);
                    return;
                }
                pending.add(command);
                log.info("The server is still starting — '{}' will run when the world is ready", command);
                return;
            }
        }
        run(c, command);
    }

    private static void run(ServerCore core, String command) {
        core.scheduler().global().execute(() -> core.runConsoleCommand(command));
    }
}
