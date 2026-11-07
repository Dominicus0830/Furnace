// C2(불만 표 65번): 플러그인 번역 서비스 — 파일 로드(데이터 폴더 우선·jar 대체), 받는 사람 언어로 렌더, 플러그인이 꺼지면 자동 해제.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginContext;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.translation.TranslationStore;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationServiceApiTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final FurnacePlugin plugin = new FurnacePlugin() {};

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        plugin.init(new PluginContext() {
            final PluginDescription d = new PluginDescription("trt", "1", "x", "", List.of(), null, null, List.of(), List.of(), List.of(),
                    null, java.util.Map.of(), List.of(), null);
            public PluginDescription description() { return d; }
            public org.domi.furnace.api.Furnace server() { return host; }
            public java.util.logging.Logger logger() { return java.util.logging.Logger.getLogger("trt"); }
            public Path configFolder() { return tmp.resolve("trt"); }
            public Path dataFolder() { return tmp.resolve("trt"); }
            public Path file() { return tmp.resolve("trt.jar"); }
            public ClassLoader classLoader() { return getClass().getClassLoader(); }
            public org.domi.furnace.api.registry.PluginRegistries registries() { return null; }
        });
        plugin.setEnabled(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.translations().unregisterAll(plugin);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private void writeKo(String body) throws Exception {
        Path f = tmp.resolve("trt/tr-test/ko_kr.properties");
        Files.createDirectories(f.getParent());
        Files.writeString(f, body, StandardCharsets.UTF_8);
    }

    private static Text hello() { return Text.translatable("trtest.hello", Text.text("Dom")); }

    @Test
    void loadsJarResourceAndDataFolderOverrideAndRendersPerLocale() throws Exception {
        writeKo("trtest.hello=안녕, %s!\n");   // 한국어는 데이터 폴더 파일, 영어는 jar 리소스
        TranslationStore s = host.translations().load(plugin, "tr-test/{locale}.properties", Locale.US, Locale.US, Locale.KOREA);
        assertEquals(List.of(s), host.translations().stores(plugin));
        assertEquals("Hello, Dom!", host.translations().render(hello(), Locale.US).plainText());
        assertEquals("안녕, Dom!", host.translations().render(hello(), Locale.KOREA).plainText());
        // 한국어에 없는 키는 기본 언어로
        assertEquals("English only", host.translations().render(Text.translatable("trtest.only_en"), Locale.KOREA).plainText());
        // 받는 사람(플레이어) 언어로 — 서버가 보낼 때 쓰는 localize
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new EmbeddedChannel());
        p.locale("ko_kr");
        assertEquals("안녕, Dom!", p.localize(hello()).plainText());
        p.locale("en_us");
        assertEquals("Hello, Dom!", p.localize(hello()).plainText());
        // 모르는 키(바닐라 키 등)는 번역 텍스트 그대로 — 클라이언트가 번역한다
        Text vanilla = Text.translatable("block.minecraft.stone");
        assertSame(vanilla, p.localize(vanilla));
    }

    @Test
    void storesComeDownWhenPluginDisables() throws Exception {
        host.translations().load(plugin, "tr-test/{locale}.properties", Locale.US, Locale.US);
        assertEquals("Hello, Dom!", host.translations().render(hello(), Locale.US).plainText());
        host.translations().unregisterAll(plugin);   // PluginManagerImpl.disable 이 부르는 것
        assertTrue(host.translations().stores(plugin).isEmpty());
        Text t = hello();
        assertSame(t, host.translations().render(t, Locale.US), "내려간 뒤에는 키 그대로(바꿀 게 없으면 같은 인스턴스)");
    }

    @Test
    void registerRequiresEnabledPluginAndUnregisterOne() {
        var store = TranslationStore.create(Locale.US).register(Locale.US, "trtest.x", "X");
        host.translations().register(plugin, store);
        host.translations().register(plugin, store);   // 중복 무시
        assertEquals(1, host.translations().stores(plugin).size());
        assertEquals("X", host.translations().render(Text.translatable("trtest.x"), Locale.US).plainText());
        assertTrue(host.translations().unregister(plugin, store));
        assertFalse(host.translations().unregister(plugin, store));
        plugin.setEnabled(false);
        assertThrows(IllegalStateException.class, () -> host.translations().register(plugin, store));
        plugin.setEnabled(true);
    }

    @Test
    void loadRejectsMissingFilesAndBadPattern() {
        assertThrows(IllegalArgumentException.class, () -> host.translations().load(plugin, "tr-test/none.properties", Locale.US, Locale.US));
        assertThrows(IllegalArgumentException.class, () -> host.translations().load(plugin, "tr-test/{locale}.properties", Locale.FRANCE, Locale.FRANCE),
                "기본 언어 파일이 없다");
        assertThrows(IllegalArgumentException.class, () -> host.translations().load(plugin, "../{locale}.properties", Locale.US, Locale.US),
                "데이터 폴더 밖 경로");
        assertTrue(host.translations().stores(plugin).isEmpty());
    }
}
