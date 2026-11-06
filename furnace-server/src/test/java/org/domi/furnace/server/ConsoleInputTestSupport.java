// ConsoleInput 의 패키지 전용 생성자를 시험이 쓰도록 여는 도우미.
package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;

public final class ConsoleInputTestSupport {

    private ConsoleInputTestSupport() {}

    public static void run(Path tmp, FurnaceSchedulerImpl sched, ServerCore core) {
        List<Integer> exits = new ArrayList<>();
        ConsoleInput in = ConsoleInput.forTest(exits::add);
        in.line("   ");
        in.line("say 아직");   // 서버 준비 전: 줄을 세워 둔다
        assertTrue(exits.isEmpty());
        List<String> console = new java.util.concurrent.CopyOnWriteArrayList<>();
        core.addConsoleListener(t -> console.add(t.getString()));
        in.attach(core);
        in.line("say 이후");
        long end = System.nanoTime() + 5_000_000_000L;
        while (console.size() < 2 && System.nanoTime() < end) Thread.onSpinWait();
        assertEquals(2, console.size(), console.toString());
        assertTrue(console.get(0).contains("아직") && console.get(1).contains("이후"), "들어온 순서대로");

        ConsoleInput early = ConsoleInput.forTest(exits::add);
        early.line("/stop");
        assertEquals(List.of(0), exits, "준비 전 stop 은 바로 종료");
    }
}
