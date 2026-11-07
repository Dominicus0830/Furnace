// 행동이 시작하려면 그 기억이 어떤 상태여야 하는가(바닐라 행동의 시작 조건과 같은 뜻).
package org.domi.furnace.api.entity.ai.brain;

public enum MemoryCondition {
    /** 값이 있어야 한다. */
    PRESENT,
    /** 값이 없어야 한다(등록은 되어 있어야 한다). */
    ABSENT,
    /** 이 두뇌에 등록만 되어 있으면 된다. */
    REGISTERED
}
