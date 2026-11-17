// 엔티티를 따라가는 스케줄러(Folia EntityScheduler 와 같은 뜻). 작업은 그 엔티티를 지금 가진 region 스레드에서 돈다.
// 엔티티가 다른 region 으로 넘어가면 작업도 함께 넘어간다.
// 엔티티가 사라지면 아직 돌지 않은 작업 대신 retired 가 한 번 불린다. 사라지는 경우는 죽음, 제거, 청크와 함께 내림, 차원 이동이며 플레이어는 접속 종료다.
// retired 는 엔티티를 내린 스레드에서 불리고, null 이면 아무것도 하지 않는다.
// 지연은 그 엔티티가 있는 region 의 틱 단위다. region 이 밀리면 그만큼 늦어진다. 아무 스레드에서 호출할 수 있다.
package org.domi.furnace.api.entity;

import org.domi.furnace.api.sched.ScheduledTask;

public interface EntityScheduler {

    /**
     * delayTicks 뒤에 한 번 실행한다(0 과 1 은 다음 틱). 엔티티가 이미 사라졌으면 아무것도 예약하지 않고 false.
     */
    boolean execute(Runnable task, Runnable retired, long delayTicks);

    /** execute 와 같지만 취소 손잡이를 돌려준다. 엔티티가 이미 사라졌으면 null. */
    ScheduledTask runDelayed(Runnable task, Runnable retired, long delayTicks);

    /**
     * initialDelayTicks 뒤부터 periodTicks 마다 반복한다(실행이 끝난 뒤부터 다음 주기를 잰다). 예외가 나도 반복은 이어지고 로그만 남는다.
     * 엔티티가 사라지면 반복이 멈추고 retired 가 한 번 불린다. 엔티티가 이미 사라졌으면 null.
     */
    ScheduledTask runAtFixedRate(Runnable task, Runnable retired, long initialDelayTicks, long periodTicks);

    /** 엔티티가 사라져 더 받지 않는가(최근 값). */
    boolean isRetired();
}
