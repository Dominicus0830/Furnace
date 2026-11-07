// 서버 콘솔의 JLine 줄 편집기: 화살표 이동·지우기·위/아래 기록·Tab 자동완성. 대화형 터미널일 때만 열리고,
// 입력이 파이프·파일이거나(헤드리스, CI, 시험) 터미널을 못 만들면 열지 않는다 — 그때는 ConsoleInput 이 예전처럼 표준 입력을 줄 단위로 읽는다.
// 로그 출력은 {@link ConsoleBridge} 를 거쳐 입력 줄 위에 찍힌다.
package org.domi.furnace.server;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.log.ConsoleBridge;
import org.jline.reader.Candidate;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConsoleTerminal implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConsoleTerminal.class);

    /** 자동완성이 서버 응답을 기다리는 최대 시간 — 서버가 바쁘면 후보 없이 돌아온다(입력이 멈추면 안 된다). */
    static final long COMPLETION_TIMEOUT_MILLIS = 1500;
    static final String PROMPT = "> ";

    private final Terminal terminal;
    private final LineReader reader;
    private volatile Supplier<ServerCore> core = () -> null;

    private ConsoleTerminal(Terminal terminal, int historySize, Path historyFile) {
        this.terminal = terminal;
        DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(null);                 // 명령 글자를 그대로 둔다(역슬래시·따옴표를 편집기가 건드리지 않게)
        parser.setQuoteChars(new char[0]);
        LineReaderBuilder b = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("Furnace")
                .parser(parser)
                .completer((r, line, candidates) -> complete(line.line().substring(0, line.cursor()), line.word(), line.wordCursor(), candidates))
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)   // '!' 를 기록 확장으로 해석하지 않는다
                .option(LineReader.Option.INSERT_TAB, false);
        if (historySize > 0 && historyFile != null) {
            b.variable(LineReader.HISTORY_FILE, historyFile)
                    .variable(LineReader.HISTORY_SIZE, historySize)
                    .variable(LineReader.HISTORY_FILE_SIZE, historySize);
        } else {
            b.variable(LineReader.HISTORY_SIZE, 0);
        }
        this.reader = b.build();
    }

    /**
     * 대화형 터미널이면 편집기를 연다. 아니면(또는 라이브러리·터미널 문제) null — 호출한 쪽이 일반 입력으로 되돌아간다.
     * historySize 0 이면 기록을 파일에 남기지 않는다.
     */
    static ConsoleTerminal open(int historySize, Path historyFile) {
        if (System.console() == null) return null;   // 입력이나 출력이 터미널이 아니다(Java 21: 둘 다 tty 일 때만 non-null)
        try {
            Terminal t = TerminalBuilder.builder()
                    .system(true)
                    .dumb(true)       // 터미널을 못 만들면 예외 대신 dumb 터미널을 받아서 우리가 판단한다
                    .build();
            if (t.getType() == null || t.getType().startsWith(Terminal.TYPE_DUMB)) {
                t.close();
                return null;
            }
            ConsoleTerminal c = new ConsoleTerminal(t, historySize, historyFile);
            ConsoleBridge.install(c::printAbove);
            return c;
        } catch (Throwable e) {   // 네이티브 라이브러리·권한 문제도 서버 기동을 막으면 안 된다
            log.warn("콘솔 줄 편집기(JLine)를 열 수 없어 일반 입력을 쓴다: {}", e.toString());
            return null;
        }
    }

    void bindCore(Supplier<ServerCore> core) { this.core = core; }

    /** 한 줄 읽기. 입력이 끝났거나(Ctrl+D) 닫혔으면 null. Ctrl+C 는 onInterrupt 를 부르고 계속 읽는다. */
    String readLine(Runnable onInterrupt) {
        while (true) {
            try {
                return reader.readLine(PROMPT);
            } catch (UserInterruptException e) {
                onInterrupt.run();
            } catch (EndOfFileException e) {
                return null;
            } catch (java.io.IOError e) {
                return null;
            }
        }
    }

    void printAbove(String text) {
        try {
            reader.printAbove(text);
        } catch (Throwable e) {
            System.out.println(text);   // 편집기가 이미 닫혔으면(종료 중) 그냥 찍는다
        }
    }

    // ------------------------------------------------------------------ 자동완성

    private void complete(String textBeforeCursor, String word, int wordCursor, List<Candidate> out) {
        ServerCore c = core.get();
        if (c == null) return;   // 서버가 준비되기 전에는 명령 트리가 없다
        int wordStart = textBeforeCursor.length() - wordCursor;
        CompletableFuture<List<String[]>> future = new CompletableFuture<>();
        try {
            c.scheduler().global().execute(() -> {
                try {
                    c.commands().suggestions(CommandSource.console(c), textBeforeCursor).whenComplete((s, err) -> {
                        if (err != null || s == null) {
                            future.complete(List.of());
                            return;
                        }
                        List<String[]> res = new java.util.ArrayList<>();
                        for (var sug : s.getList()) {
                            String replaced = textBeforeCursor.substring(0, Math.min(sug.getRange().getStart(), textBeforeCursor.length())) + sug.getText();
                            if (sug.getRange().getStart() < wordStart) continue;   // 낱말 앞까지 바꾸는 후보(따옴표 문구 등)는 건너뛴다
                            res.add(new String[]{replaced.substring(wordStart), sug.getText(), sug.getTooltip() == null ? null : sug.getTooltip().getString()});
                        }
                        future.complete(res);
                    });
                } catch (Throwable t) {
                    future.complete(List.of());
                }
            });
            for (String[] r : future.get(COMPLETION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                out.add(new Candidate(r[0], r[1], null, r[2], null, null, false));
            }
        } catch (Exception ignored) {
            // 시간 초과·종료 중 — 후보 없음
        }
    }

    @Override
    public void close() {
        ConsoleBridge.uninstall();
        try {
            terminal.close();
        } catch (Exception ignored) {
            // 종료 중
        }
    }
}
