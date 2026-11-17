// canBeCollidedWith 를 덮어쓰는 엔티티 종류의 표지(바닐라 1.21.1: 배·셜커). 다른 엔티티가 그 위에 서고 부딪힌다.
// 충돌 후보 훑기(ChunkLevel.entityCollisions)는 상대의 canBeCollidedWith 만 보는 경우 이 표지의 구역 목록만 돈다 —
// canBeCollidedWith 를 덮어쓰면서 이 표지를 달지 않으면 그 엔티티는 충돌에서 빠진다(HardColliderTest 가 막는다).
package org.domi.furnace.server.entity;

public interface HardCollider {
}
