// 모든 테스트 클래스 앞에서 아이템 레지스트리가 없으면 깐다. 레지스트리 없이 블록 동작 표를 처음 만든 테스트가
// 정적 초기화(CauldronInteraction·ComposterBlock 등)를 실패시켜 JVM 안의 뒤 테스트가 줄줄이 깨지는 것을 막는다(실행 순서에 의존하지 않게).
// 또 클래스가 ItemContext 에 건 태그 표(installTags)는 클래스가 끝나면 원래대로 되돌린다. 예: 화로 시험(SyntheticRecipes)이 건
// 레시피 하나뿐인 팩의 빈 태그 표가 남으면 뒤에 도는 GameRegistriesBuiltInTest 가 "바닐라 태그" 대신 빈 표를 보고 실패했다.
package org.domi.furnace.server;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class ItemContextExtension implements BeforeAllCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(ItemContextExtension.class);
    private static final String TAGS_BEFORE = "tagsBefore";

    /** null 태그 표도 되돌려야 하므로 상자에 담아 둔다(Store 는 null 값을 "없음"으로 본다). */
    private record Saved(TagSet tags) {}

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!ItemContext.installed()) ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        context.getStore(NS).put(TAGS_BEFORE, new Saved(ItemContext.tagSet()));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Saved saved = context.getStore(NS).remove(TAGS_BEFORE, Saved.class);
        if (saved != null && ItemContext.tagSet() != saved.tags()) ItemContext.installTags(saved.tags());
    }
}
