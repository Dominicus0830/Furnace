// 두뇌에 등록된 기억 하나의 모습(읽은 시점 값).
// type 이 null 이면 API 로 다룰 수 없는 내부 값(길, 피해 기록 등)이라 value 도 null 이다.
// value 는 그 모양의 API 값(엔티티는 UUID)이며 비었으면 null 이다. expiresIn 은 남은 틱이고, 영구거나 비었으면 -1 이다.
// plugin 은 플러그인 기억이면 true 다.
package org.domi.furnace.api.entity.ai.brain;

public record MemoryInfo(String key, MemoryValueType<?> type, boolean present, Object value, long expiresIn, boolean plugin) {}
