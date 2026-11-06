// 체력이 있는 엔티티. health()/maxHealth() 는 핸들 시점 값(플레이어 핸들은 최근 값).
package org.domi.furnace.api.entity;

import java.util.concurrent.CompletableFuture;

public interface LivingEntity extends Entity {

    float health();

    float maxHealth();

    /** 0..최대 체력으로 잘라 소유 스레드에서 적용한다. 0 이면 죽는다. 엔티티가 없어졌으면 IllegalStateException 으로 완료. */
    CompletableFuture<Void> updateHealth(float health);
}
