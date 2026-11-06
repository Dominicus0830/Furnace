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

    /** plugins/<이름>/ — 처음 쓸 때 만들어진다. */
    Path dataFolder();

    /** 플러그인 jar. */
    Path file();

    ClassLoader classLoader();
}
