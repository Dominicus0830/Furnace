// 월드의 엔티티 핸들. 이벤트가 만든 핸들의 location()/customName()/isValid() 는 "만든 시점" 값이다(낡을 수 있다).
// 최신 값이 필요하면 refresh(), 상태를 바꾸는 호출은 모두 소유 region 스레드에서 실행되고 Future 로 끝을 알린다.
package org.domi.furnace.api.entity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.api.world.World;

public interface Entity {

    UUID uniqueId();

    /** 종류 키(minecraft:zombie, minecraft:player). 불변. */
    String type();

    /** 핸들을 만든 시점의 위치. */
    Location location();

    /** 핸들을 만든 시점의 사용자 지정 이름(없으면 null). */
    Text customName();

    /** 핸들을 만든 시점에 살아 있었는가. */
    boolean isValid();

    default World world() { return Furnace.server().world(location().world()); }

    /** 핸들 시점 위치를 가진 region 의 스케줄러. 엔티티가 그 region 을 벗어났으면 작업은 그 자리의 region 에서 돈다. */
    TaskScheduler scheduler();

    /** 소유 region 에서 최신 상태를 읽은 새 핸들. 엔티티가 그 region 을 떠났거나 사라졌으면 null 로 완료된다. */
    CompletableFuture<? extends Entity> refresh();

    /** 같은 차원 안에서만 옮긴다(소유 region 에서 실행). 옮겼으면 true, 사라졌거나 다른 차원이면 false. */
    CompletableFuture<Boolean> teleport(Location to);

    /** 엔티티를 지운다(플레이어는 UnsupportedOperationException 으로 완료). 이미 없으면 false. */
    CompletableFuture<Boolean> remove();
}
