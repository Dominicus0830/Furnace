// 별도 JVM 에서 서버를 띄워 보는 작은 main(CompatIsolationTest 가 부른다).
// 패키지를 org.domi.furnace.compat 밖에 둔 것은 일부러다 — 클래스 로드 기록에서 "호환 모듈 클래스" 를 이름 접두로 가를 때 이 main 이 섞이지 않게.
package org.domi.furnace.isolationcheck;

import java.nio.file.Path;
import org.domi.furnace.test.FurnaceTestServer;

public final class IsolationMain {

    private IsolationMain() {}

    /** args[0] = 서버 폴더(plugins/ 에 jar 를 미리 넣어 둘 수 있다). 끝에 "RESULT ..." 한 줄을 찍는다. */
    public static void main(String[] args) {
        Path dir = Path.of(args[0]);
        try (FurnaceTestServer server = FurnaceTestServer.builder().directory(dir).failFast(false).start()) {
            server.tick(5);
            var bot = server.join("IsoBot");
            server.tick(10);
            // 호환 모듈 없이도 평소 서버 일이 모두 돈다: 접속·명령·틱
            var tps = server.execute("tps");
            var plugins = server.api().plugins().plugins().stream().map(p -> p.name()).sorted().toList();
            server.tick(5);
            System.out.println("RESULT players=" + server.players().size() + " tpsOk=" + tps.success() + " plugins=" + plugins);
            bot.close();
        }
        System.out.println("RESULT closed");
        System.exit(0);
    }
}
