// 로더가 플러그인에 넘기는 환경. 플러그인 코드가 직접 쓸 일은 거의 없다(FurnacePlugin 이 감싼다).
package org.domi.furnace.api.plugin;

import java.nio.file.Path;
import java.util.logging.Logger;
import org.domi.furnace.api.Furnace;

public interface PluginContext {

    PluginDescription description();

    Furnace server();

    /** "[플러그인] 메시지" 형식으로 서버 로그에 쓰는 로거. */
    Logger logger();

    /** 관리자가 고치는 설정 파일 폴더 — plugins/<이름>/. 처음 쓸 때 만들어진다. */
    Path configFolder();

    /**
     * 플러그인이 만든 데이터 폴더. 기본은 configFolder() 와 같은 plugins/<이름>/ 이고,
     * 서버 설정 plugins.data-root 가 있으면 <data-root>/<이름>/ 로 갈라진다.
     */
    Path dataFolder();

    /** 플러그인 jar. */
    Path file();

    ClassLoader classLoader();

    /** 이 플러그인 이름으로 묶인 플러그인 레지스트리 보기. */
    org.domi.furnace.api.registry.PluginRegistries registries();

    /**
     * 로더 전용: 지금 {@link FurnacePlugin#setEnabled} 호출이 로더(플러그인 관리자)에서 온 것인가. 거짓이면 setEnabled 는
     * 플러그인 관리자의 enable/disable 로 넘어가 자동 정리까지 모두 돈다(Bukkit 은 setEnabled 를 다른 플러그인이 부를 수 없다).
     * 기본은 참(관리자 없이 만든 시험용 문맥은 예전처럼 바로 켜고 끈다).
     */
    default boolean lifecycleCall() { return true; }
}
