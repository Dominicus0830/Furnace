// 부트스트랩 예: 서버가 레지스트리·데이터 팩을 읽기 전에 불린다(Furnace.server() 는 아직 없다).
// jar 안 datapack/ 폴더를 데이터 팩으로 알리고, 다른 플러그인도 쓸 수 있는 "인사말" 레지스트리를 만든다.
package org.domi.furnace.plugintest;

import org.domi.furnace.api.plugin.bootstrap.BootstrapContext;
import org.domi.furnace.api.plugin.bootstrap.PluginBootstrap;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEvents;

public final class SampleBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext ctx) {
        ctx.lifecycle().register(LifecycleEvents.DATAPACK_DISCOVERY, packs -> packs.discover("datapack", "main"));
        ctx.registries().create("sample:greetings", String.class).register("sample:hello", "안녕하세요");
    }
}
