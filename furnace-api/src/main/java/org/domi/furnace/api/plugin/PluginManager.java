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
}
