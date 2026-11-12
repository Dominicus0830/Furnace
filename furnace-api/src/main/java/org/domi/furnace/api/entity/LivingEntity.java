// 체력이 있는 엔티티. health()/maxHealth() 는 핸들 시점 값(플레이어 핸들은 최근 값).
package org.domi.furnace.api.entity;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.entity.attribute.Attribute;
import org.domi.furnace.api.entity.attribute.AttributeHandle;

public interface LivingEntity extends Entity {

    float health();

    float maxHealth();

    /** 0..최대 체력으로 잘라 소유 스레드에서 적용한다. 0 이면 죽는다. 엔티티가 없어졌으면 IllegalStateException 으로 완료. */
    CompletableFuture<Void> updateHealth(float health);

    // ------------------------------------------------------------------ 속성 (Bukkit Attributable 과 같은 뜻 — 쓰기는 소유 스레드로 넘어간다)

    /**
     * 이 속성의 핸들. 엔티티가 그 속성을 가지지 않으면 empty(Bukkit 의 getAttribute 가 null 인 경우 — 예: 소는 공격력이 없다).
     * 읽는 값은 핸들을 만든 시점(플레이어는 최근 값)이고, 쓰기는 {@link AttributeHandle#edit} 등으로 소유 region 스레드에서 한다.
     * 이름 변경은 {@link Attribute#find} 가 흡수한다.
     */
    default Optional<AttributeHandle> attribute(Attribute attribute) { throw new UnsupportedOperationException("attribute"); }

    /** 이 엔티티가 가진 속성들(핸들 시점, 레지스트리 순서). */
    default List<Attribute> attributes() { throw new UnsupportedOperationException("attributes"); }

    /** 핸들 시점의 속성 값(없으면 fallback). */
    default double attributeValue(Attribute attribute, double fallback) { return attribute(attribute).map(AttributeHandle::value).orElse(fallback); }

    /**
     * 엔티티에 없는 속성을 달아 준다(Paper registerAttribute — 기본값은 그 속성의 기본값). 이미 있으면 false 로 완료.
     * 등록 순간 클라이언트로 보내지 않는다(클라이언트는 엔티티 종류의 속성만 안다). Paper 와 달리 이미 있으면 바꿔 끼우지 않는다.
     */
    default CompletableFuture<Boolean> registerAttribute(Attribute attribute) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("registerAttribute"));
    }
}
