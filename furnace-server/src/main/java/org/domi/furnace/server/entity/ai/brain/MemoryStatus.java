// 바닐라 MemoryStatus: 행동이 요구하는 기억 상태(값 있음 / 값 없음 / 등록만 되어 있으면 됨).
package org.domi.furnace.server.entity.ai.brain;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.entity.ai.memory.MemoryStatus")
public enum MemoryStatus { VALUE_PRESENT, VALUE_ABSENT, REGISTERED }
