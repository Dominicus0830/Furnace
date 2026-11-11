// 아이템 술어가 보는 레지스트리·태그(바닐라 HolderLookup 의 술어 쪽 부분). 명령은 서버 레지스트리(ItemContext),
// 전리품·발전 과제는 데이터 팩을 읽을 때 만든 LootRegistries 를 쓴다.
package org.domi.furnace.server.item;

import org.domi.furnace.server.data.ResourceId;

public interface RegistryView {

    /** 그 레지스트리에 id 가 있는가(이 쪽이 모르는 레지스트리면 참 — 검사하지 않음). */
    boolean exists(String registry, String id);

    /** 파싱 때 태그 검사: 그 태그가 있는가(검사하지 않는 쪽은 언제나 참). */
    boolean hasTag(String registry, String tag);

    /** 시험 때: id 가 그 태그에 들어 있는가. */
    boolean inTag(String registry, String tag, String id);

    /** 서버 레지스트리(명령 인자). 태그는 데이터 팩 리로드마다 바뀌므로 시험 때마다 지금 태그를 본다. */
    RegistryView SERVER = new RegistryView() {
        public boolean exists(String registry, String id) {
            if (!ItemContext.installed() || ItemContext.size(registry) == 0) return true;
            ResourceId rid = ResourceId.tryParse(id);
            return rid != null && ItemContext.regs().id(registry, rid) != null;
        }
        public boolean hasTag(String registry, String tag) { return ItemContext.hasTag(registry, tag); }
        public boolean inTag(String registry, String tag, String id) {
            if (!ItemContext.installed()) return false;
            Integer num = ItemContext.id(registry, id);
            return num != null && ItemContext.isInTag(registry, tag, num);
        }
    };
}
