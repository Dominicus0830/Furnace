package org.domi.furnace.compat.nms;

import org.domi.furnace.compat.i18n.CompatLang;
import org.slf4j.LoggerFactory;

/**
 * 내장 레지스트리 shim 을 플러그인이 쓰기 전에 한 스레드에서 미리 초기화한다(PR 579 검토 권장 2). 바닐라 Bootstrap 처럼 서버 시작 때 한 번 —
 * 여러 플러그인 스레드가 레지스트리와 값 클래스({@code VillagerProfession} 등)의 클래스 초기화를 서로 다른 차례로 밟아 초기화 잠금이 엇물리는 일을 없앤다.
 * 실패하면 원인을 한 번 알리고 넘어간다(그 레지스트리를 처음 쓰는 곳에서 같은 클래스 초기화 실패가 다시 난다).
 */
public final class NmsBootstrap {

    private NmsBootstrap() {}

    private static volatile boolean done;

    public static void registries() {
        if (done) return;
        synchronized (NmsBootstrap.class) {
            if (done) return;
            done = true;
            try {
                Class.forName("net.minecraft.core.registries.BuiltInRegistries", true, NmsBootstrap.class.getClassLoader());
            } catch (Throwable t) {
                LoggerFactory.getLogger("Furnace").warn(CompatLang.t("compat.nms.registries-failed", String.valueOf(t.getCause() != null ? t.getCause() : t)));
            }
        }
    }
}
