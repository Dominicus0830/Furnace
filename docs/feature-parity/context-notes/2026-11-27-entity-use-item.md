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

## 몹 아이템 사용을 바닐라로(같은 날 후속)
- 서버 `LivingEntity` 가 쓰는 아이템(`useItem`)을 기억하고 틱마다 손의 같은 종류(`isSameItem`, 빈 것끼리 같음)로 갱신, 다르면 멈춘다. 남은 틱은 바닐라처럼 0 아래로도 준다.
- 사용 시간·애니메이션·놓을 때 쓰기·마무리는 `MobItemUse`(javap 대조): 음식 eat_seconds×20, 물약·우유·불길한 병 32, 꿀 40, 쇠뇌 장전+3, 활·방패·삼지창 72000, 망원경 1200, 솔 200, 뿔은 악기.
- 효과 시점(사용 시간 7/32 지난 뒤 남은 틱 4 배수): 마시기 소리는 레벨 난수, 먹기는 입자 수×3 엔티티 난수(서버 addParticle 은 없음) 뒤 소리 난수 셋.
- 마무리(플레이어가 아닌 생물): 물약은 소모하지 않음(PotionItem 은 플레이어만 consume), 우유는 소모만(양동이 없음), 꿀은 먹기 뒤 독 제거·비면 유리병, 수상한 스튜·후렴과 포함.
  방랑 상인의 물약·우유 재정의는 지웠다(바닐라는 UseItemGoal.stop 이 손을 비운다).
- 차이(가정 아님, 선택): Paper 는 completeUsingItem 에서 startUsingItem(hand, true) 로 item_interact_start 를 한 번 더 낸다 — 바닐라·플레이어 경로처럼 내지 않는다.
  Paper 의 실시간 먹기 보정(nanoTime)도 하지 않는다. 몹의 onUseTick·releaseUsing 효과(쇠뇌 장전 소리 등)는 지금처럼 목표가 맡는다.
