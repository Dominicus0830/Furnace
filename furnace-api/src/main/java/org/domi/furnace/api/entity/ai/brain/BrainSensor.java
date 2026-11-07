// 플러그인이 만드는 두뇌 센서: scanRate 틱마다 주변을 보고 기억을 채운다. 바닐라 센서 뒤에, 넣은 순서대로 돈다.
// 그 몹을 가진 region 스레드에서 몹 틱 안에서 불린다 — 막히는 일을 하지 말 것. 넘겨받는 MobAi·MobBrain 은 그 호출 안에서만 쓴다.
// 첫 감지는 바닐라 센서처럼 0~scanRate-1 틱 사이 무작위로 어긋난다(몹이 한꺼번에 몰리지 않게, 월드 난수와 무관).
// 플러그인이 꺼지면 다시 불리지 않고 스스로 빠진다. 예외를 던지면 로그 한 번 남기고 빠진다. 저장되지 않는다.
package org.domi.furnace.api.entity.ai.brain;

import java.util.Set;
import org.domi.furnace.api.entity.ai.MobAi;

public interface BrainSensor {

    /** 이 센서의 이름표(찾기·빼기에 쓴다). 플러그인 이름공간을 쓸 것. */
    SensorKey key();

    /** 몇 틱에 한 번 감지하는가(1 이상). 넣을 때 한 번 읽는다. */
    default int scanRate() { return 20; }

    /** 이 센서가 채우는 기억. 넣을 때 두뇌에 등록된다(없던 기억이면 새로 등록). */
    default Set<MemoryKey<?>> memories() { return Set.of(); }

    void sense(MobAi mob, MobBrain brain);
}
