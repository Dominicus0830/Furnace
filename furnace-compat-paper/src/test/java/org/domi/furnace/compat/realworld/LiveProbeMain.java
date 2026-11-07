package org.domi.furnace.compat.realworld;

import java.nio.file.Files;
import java.nio.file.Path;

/** 개발용: 실제 플러그인 jar 를 올려 결과만 출력한다. {@code ./gradlew :furnace-compat-paper:liveProbe -Pjar=helper}. */
public final class LiveProbeMain {
    public static void main(String[] args) throws Exception {
        Path jar = RealJars.find(args[0]);
        Path dir = Files.createTempDirectory("h4-live");
        var r = LivePluginProbe.run(jar, RealJars.pluginName(jar), 5, dir);
        System.out.println("상태=" + r.status() + " 처음 오류=" + r.firstError());
        for (String e : r.errors()) System.out.println("  - " + e);
        System.exit(0);
    }
}
