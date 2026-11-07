// 걸어갈 목표 기억(minecraft:walk_target): 대상, 속도 배수, 도착으로 치는 거리(칸).
package org.domi.furnace.api.entity.ai.brain;

public record WalkTarget(BrainTarget target, float speed, int closeEnough) {
    public WalkTarget {
        if (target == null) throw new NullPointerException("target");
        if (!(speed > 0) || closeEnough < 0) throw new IllegalArgumentException("speed > 0, closeEnough >= 0 이어야 한다");
    }
}
