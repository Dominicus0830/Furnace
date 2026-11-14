package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.StartupProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * plugin.yml {@code load: STARTUP} — Paper(DedicatedServer.initServer → MinecraftServer.loadWorld0)처럼 STARTUP 플러그인을 월드보다 먼저 켜고,
 * 월드를 하나씩 목록에 넣으며 WorldInitEvent, 모두 넣은 뒤 WorldLoadEvent 를 보내고, 그다음 POSTWORLD 플러그인을 켠다. 모든 플러그인의
 * onLoad 도 월드 전이다.
 * FAWE 의 "Server reload detected" 오탐(켤 때 Bukkit.getWorlds() 가 비어 있지 않음)이 이것 때문이었다.
 */
class StartupLoadOrderTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { StartupProbe.LOG.clear(); }

    private Path jar(String name, String load) throws Exception {
        return Jars.bukkit(tmp.resolve(name + ".jar"), "plugin.yml", "name: " + name + "\nversion: 1\nmain: " + StartupProbe.class.getName()
                + "\napi-version: '1.21'\n" + (load == null ? "" : "load: " + load + "\n"), StartupProbe.class);
    }

    @Test
    void startupPluginsEnableBeforeWorldsAndSeeDefaultWorldEvents() throws Exception {
        // 의존 순서상 Late 가 먼저 와도(이름순) STARTUP 인 Early 가 먼저 켜진다
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(true)
                .pluginJar(jar("ALate", null)).pluginJar(jar("BEarly", "STARTUP")).start()) {
            List<String> names = new ArrayList<>();
            for (var w : server.api().worlds()) names.add(w.name());
            List<String> expected = new ArrayList<>();
            expected.add("ALate:onload worlds=0 byName=false");   // onLoad 는 둘 다 월드 전(검토 R1)
            expected.add("BEarly:onload worlds=0 byName=false");
            expected.add("BEarly:enable worlds=0 byName=false");
            for (int i = 0; i < names.size(); i++) expected.add("BEarly:init " + names.get(i) + " visible=" + (i + 1));
            for (String n : names) expected.add("BEarly:load " + n + " visible=" + names.size());
            expected.add("ALate:enable worlds=" + names.size() + " byName=" + names.contains("world"));
            assertEquals(expected, StartupProbe.LOG);
            assertEquals(names.size(), (int) server.onGlobal(() -> org.bukkit.Bukkit.getWorlds().size()));
        }
    }

    @Test
    void withoutStartupPluginsOnlyOnLoadSeesNoWorlds() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(true).pluginJar(jar("Only", null)).start()) {
            int n = server.api().worlds().size();
            assertEquals(List.of("Only:onload worlds=0 byName=false",
                    "Only:enable worlds=" + n + " byName=" + (server.api().worldNamed("world") != null)), StartupProbe.LOG);
        }
    }
}
