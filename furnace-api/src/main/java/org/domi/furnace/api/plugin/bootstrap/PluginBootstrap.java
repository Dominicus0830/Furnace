// 부트스트랩 진입점 — 설명 파일의 bootstrapper 키가 가리키는 클래스(인자 없는 생성자)가 구현한다.
// 서버가 블록·아이템·동기화 레지스트리를 읽고 데이터 팩을 켜고 월드를 열기 "전"에, 의존 순서대로 한 번 불린다.
// 이 시점에는 Furnace.server()·월드·플레이어·스케줄러가 없다. 여기서 할 일은 수명주기 훅 등록과 플러그인 레지스트리 준비뿐이다.
package org.domi.furnace.api.plugin.bootstrap;

import org.domi.furnace.api.plugin.FurnacePlugin;

public interface PluginBootstrap {

    /** 레지스트리 고정 전 단계. 던지면 이 플러그인은 불러오지 않고, 여기서 등록한 것은 모두 되돌린다. */
    void bootstrap(BootstrapContext context);

    /**
     * 메인 클래스 인스턴스를 직접 만든다(생성자 인자를 넘기고 싶을 때). null 이면 서버가 main 클래스의 인자 없는 생성자로 만든다.
     * bootstrap 이 끝난 뒤, onLoad 바로 전에 불린다.
     */
    default FurnacePlugin createPlugin(BootstrapContext context) { return null; }
}
