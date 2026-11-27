# 엔티티 아이템 사용·맞은 흔들림 shim (2026-11-27, 영역 2)

FancyNpcs 가 쓰는 `LivingEntity` 의 `isUsingItem`·`getUsedItemHand`·`startUsingItem(hand[, force])`·`stopUsingItem`·`animateHurt` 를 서버 값에 연결했다.

- 읽기: 월드 엔티티는 `EntityLook.livingFlags`(서버 `DATA_LIVING_ENTITY_FLAGS`), 월드 밖 엔티티는 재생한 칸. 플레이어는 `Player.livingFlagsNow()`
  (손 비트를 기억하지 않는 경쟁 읽기 — `livingFlags()` 는 손 비트를 저장하므로 다른 스레드에서 부르지 않는다).
- 쓰기: `EntityEdit.StartUsingItem`·`StopUsingItem`·`AnimateHurt` 는 `EntityOp` 이기도 해서 월드 밖 엔티티는 기록·재생, 월드 엔티티는 주인 스레드에서 서버 엔티티에 한다.
  서버 몹 `startUsingItem(hand, force)` 는 Paper 규칙(빈 손·이미 사용 중이면 force 일 때만).
- 바닐라 `animateHurt(yaw)` 는 yaw 를 쓰지 않고 `hurtDuration = 10; hurtTime = hurtDuration` 뿐이다(javap 확인).

## 남은 것(가정·후속)
- 접속한 플레이어의 시작·멈춤·흔들림은 UnsupportedNmsCall(다른 플레이어 편집과 같은 규칙). 플레이어 사용 시간은 아이템마다 달라(`Consuming.duration`) 별도 묶음.
- 서버 몹의 사용 시간은 기본 72000(`LivingEntity.getUseDuration`) — 바닐라 `ItemStack.getUseDuration(entity)` 와 다를 수 있다(음식 32 등). 강제로 빈 손을 쓰게 하면
  바닐라는 계속 사용 중이지만 서버 `updateUsingItem` 은 빈 손이면 다음 틱에 멈춘다(가정: 플러그인이 월드 몹에 이렇게 쓰는 경우는 드묾).
- FancyNpcs 는 인스턴스 필드 `hurtTime`·`hurtDuration`·`hurtMarked` 도 직접 읽고 쓴다. 필드는 shim 에 없고(설계상 색인만), 월드 엔티티 값과 이으려면
  NmsLinker 가 GETFIELD/PUTFIELD 를 정적 접근자로 바꾸는 설계가 필요하다(설계 결정 거리).
