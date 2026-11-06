// 부트스트랩 단계에서 플러그인이 받는 환경. 서버 파사드(Furnace)는 아직 없다.
package org.domi.furnace.api.plugin.bootstrap;

import java.nio.file.Path;
import java.util.logging.Logger;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEventManager;
import org.domi.furnace.api.registry.PluginRegistries;

public interface BootstrapContext {

    PluginDescription description();

    /** "[플러그인] 메시지" 형식 로거(나중에 FurnacePlugin.logger() 와 같은 것). */
    Logger logger();

    /** plugins/<이름>/ (만들지는 않는다). */
    Path dataFolder();

    /** 플러그인 jar. */
    Path file();

    /** 수명주기 훅(데이터 팩 발견 등). 훅은 플러그인이 꺼지면 함께 해제된다. */
    LifecycleEventManager lifecycle();

    /** 이 플러그인 이름으로 묶인 플러그인 레지스트리 보기(FurnacePlugin.registries() 와 같은 저장소). */
    PluginRegistries registries();
}
