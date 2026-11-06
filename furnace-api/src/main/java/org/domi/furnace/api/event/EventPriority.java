// 처리 순서(Bukkit 과 같다): LOWEST 가 먼저, MONITOR 가 마지막. MONITOR 는 결과를 바꾸지 말고 관찰만 한다.
package org.domi.furnace.api.event;

public enum EventPriority {
    LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR
}
