package org.domi.furnace.compat.realworld;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 개발용: 실제 플러그인 jar 를 올려 결과만 출력한다. {@code ./gradlew :furnace-compat-paper:liveProbe -Pjar=helper}.
 * 두 번째 인자(-PlibraryCache=폴더)를 주면 그 폴더를 라이브러리 캐시로 offline 으로 써서 서버 제공 라이브러리(H4 묶음 B)를 준 상태로 잰다.
 * -PlibraryCache=online 이면 실제로 내려받는다(캐시는 임시 서버 폴더 아래). -Pcommands="명령1;명령2" 면 켠 뒤 콘솔로 실행해 출력을 보인다.
 * 환경 변수 LIVE_PROBE_SEED=<폴더> 면 그 폴더를 서버 폴더에 먼저 복사한다.
 */
public final class LiveProbeMain {
    public static void main(String[] args) throws Exception {
        Path jar = RealJars.find(args[0]);
        Path dir = Files.createTempDirectory("h4-live");
        // 환경 변수 LIVE_PROBE_SEED=<폴더>: 그 폴더 내용을 서버 폴더에 먼저 복사한다(예: 플러그인이 스스로 받는 라이브러리를 미리 둔 plugins/LuckPerms/libs)
        String seed = System.getenv("LIVE_PROBE_SEED");
        if (seed != null && !seed.isBlank()) {
            Path from = Path.of(seed);
            try (var walk = Files.walk(from)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    Path to = dir.resolve(from.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(to);
                    else Files.copy(p, to);
                }
            }
        }
        org.domi.furnace.server.plugin.library.LibrarySettings libs = null;
        if (args.length > 1 && !args[1].isBlank()) {
            libs = args[1].equals("online") ? org.domi.furnace.server.plugin.library.LibrarySettings.DEFAULT
                    : org.domi.furnace.server.plugin.library.LibrarySettings.DEFAULT.withOffline(true).withCache(Path.of(args[1]).toAbsolutePath());
        }
        java.util.List<String> commands = args.length > 2 && !args[2].isBlank() ? java.util.List.of(args[2].split(";")) : java.util.List.of();
        var r = LivePluginProbe.run(jar, RealJars.pluginName(jar), 5, dir, libs, commands);
        System.out.println("상태=" + r.status() + " 처음 오류=" + r.firstError());
        for (String e : r.errors()) System.out.println("  - " + e);
        System.exit(0);
    }
}
