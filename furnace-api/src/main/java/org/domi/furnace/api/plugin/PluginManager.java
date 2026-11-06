// 불러온 플러그인 목록·상태.
package org.domi.furnace.api.plugin;

import java.util.Collection;

public interface PluginManager {

    Collection<FurnacePlugin> plugins();

    /** 이름(대소문자 무시)으로, 없으면 null. */
    FurnacePlugin plugin(String name);

    boolean isEnabled(String name);

    /** 켜진 플러그인을 끈다(명령·이벤트·작업·권한 첨부가 모두 정리된다). */
    void disable(FurnacePlugin plugin);

    /** 플러그인 로거의 수준을 바꾼다(서버 설정 plugins.log-level.<이름> 으로 시작값을 줄 수 있다). */
    void setLogLevel(String pluginName, java.util.logging.Level level);

    /** 개발 모드(furnace.properties plugins.dev-mode=true)인가. 핫 리로드는 개발 모드에서만 된다. */
    default boolean devMode() { return false; }

    /**
     * 개발 모드 전용 핫 리로드: 그 플러그인을 끄고(자동 정리 포함) jar 를 새 클래스로더로 다시 읽어 켠다.
     * 그 플러그인에 기대는 플러그인(depend·softdepend·loadbefore, 실제로 그 jar 의 클래스를 가져다 쓴 플러그인)도 함께 다시 읽는다.
     * 불러오지 않은 이름이라도 plugins/ 에 그 이름의 jar 가 있으면 새로 불러오고, jar 가 없어졌으면 내리기만 한다.
     * 작업은 전역 스레드에서 돌고, 결과는 다시 켠 플러그인 이름(켠 순서). 개발 모드가 아니면 IllegalStateException 으로 끝난다.
     */
    default java.util.concurrent.CompletableFuture<java.util.List<String>> reload(String name) {
        return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("핫 리로드는 개발 모드(plugins.dev-mode=true)에서만 된다"));
    }
}
