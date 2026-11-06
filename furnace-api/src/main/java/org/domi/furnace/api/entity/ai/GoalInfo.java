// 몹에 걸린 목표 하나의 모습(읽은 시점 값). plugin 은 플러그인이 넣은 목표면 true.
package org.domi.furnace.api.entity.ai;

import java.util.Set;

public record GoalInfo(GoalKey key, int priority, Set<GoalControl> controls, boolean running, boolean plugin) {
    public GoalInfo {
        controls = Set.copyOf(controls);
    }
}
