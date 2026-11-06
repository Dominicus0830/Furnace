// 모든 테스트 클래스 앞에서 아이템 레지스트리가 없으면 깐다. 레지스트리 없이 블록 동작 표를 처음 만든 테스트가
// 정적 초기화(CauldronInteraction·ComposterBlock 등)를 실패시켜 JVM 안의 뒤 테스트가 줄줄이 깨지는 것을 막는다(실행 순서에 의존하지 않게).
package org.domi.furnace.server;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class ItemContextExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!ItemContext.installed()) ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }
}
