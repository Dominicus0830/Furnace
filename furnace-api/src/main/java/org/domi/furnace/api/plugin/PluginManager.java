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
}
