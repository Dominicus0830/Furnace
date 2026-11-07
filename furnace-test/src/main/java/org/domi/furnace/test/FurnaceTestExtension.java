// JUnit 5 확장: 시험마다 새 시험 서버를 띄우고 끝나면 닫는다(실패해도). 시험 메서드 인자로 FurnaceTestServer 를 받을 수 있다.
//
//   @RegisterExtension
//   final FurnaceTestExtension furnace = FurnaceTestExtension.with(b -> b.plugin(MyPlugin.class));
//
//   @Test void greets(FurnaceTestServer server) { var p = server.join("Steve"); ... }
//
// static 필드에 두면 클래스의 시험들이 서버 하나를 함께 쓴다(BeforeAll/AfterAll) — 시험끼리 상태가 남는다.
package org.domi.furnace.test;

import java.util.function.Consumer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public final class FurnaceTestExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private final Consumer<FurnaceTestServer.Builder> configure;
    private boolean perClass;
    private FurnaceTestServer server;

    private FurnaceTestExtension(Consumer<FurnaceTestServer.Builder> configure) { this.configure = configure; }

    /** 기본 설정(평지, 플러그인 없음). */
    public static FurnaceTestExtension create() { return new FurnaceTestExtension(b -> { }); }

    /** 빌더를 고친 설정. */
    public static FurnaceTestExtension with(Consumer<FurnaceTestServer.Builder> configure) { return new FurnaceTestExtension(configure); }

    /** 지금 시험의 서버. */
    public FurnaceTestServer server() {
        if (server == null) throw new IllegalStateException("시험 밖에서는 서버가 없다");
        return server;
    }

    private void open() {
        var b = FurnaceTestServer.builder();
        configure.accept(b);
        server = b.start();
    }

    private void shut() {
        if (server != null) {
            try {
                server.close();
            } finally {
                server = null;
            }
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        perClass = true;   // static 필드로 등록됐을 때만 불린다
        open();
    }

    @Override
    public void afterAll(ExtensionContext context) { shut(); }

    @Override
    public void beforeEach(ExtensionContext context) { if (!perClass) open(); }

    @Override
    public void afterEach(ExtensionContext context) { if (!perClass) shut(); }

    @Override
    public boolean supportsParameter(ParameterContext p, ExtensionContext e) {
        return p.getParameter().getType() == FurnaceTestServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext p, ExtensionContext e) { return server(); }
}
