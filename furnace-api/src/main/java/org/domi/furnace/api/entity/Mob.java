// AI 가 있는 몹(동물·몬스터·주민 등). 핸들은 만든 시점 스냅샷이고, AI 를 바꿀 때는 그 몹을 가진 region 스레드에서 MobAi 로 한다.
package org.domi.furnace.api.entity;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.domi.furnace.api.entity.ai.MobAi;

public interface Mob extends LivingEntity {

    /**
     * 그 몹을 가진 region 스레드에서 다음 틱에 action 을 돈다(몹이 region 을 옮겨 가도 따라간다). action 의 결과로 완료하고,
     * 몹이 이미 사라졌거나 그 전에 사라지면 IllegalStateException 으로 완료.
     */
    <T> CompletableFuture<T> editAi(Function<? super MobAi, ? extends T> action);

    /**
     * 지금 이 스레드가 그 몹을 가진 region 스레드면(그 몹 이벤트 처리기 안 등) 바로 MobAi. 아니면 ThreadOwnershipException.
     * 몹이 사라졌으면 빈 값.
     */
    Optional<MobAi> aiNow();
}
