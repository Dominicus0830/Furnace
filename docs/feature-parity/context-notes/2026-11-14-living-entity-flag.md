## 2026-11-14 몹이 "생물 아님"으로 판정되던 문제 + 꿀 미끄럼 효과 (T-040b 검토 R2)

### 원인
- `Entity.isLiving()` 은 바닐라 `entity instanceof LivingEntity` 자리를 대신하는데(4-1b 부터) 기본값 `false` 만 있고 `LivingEntity` 가 덮어쓰지 않았다. 모든 몹·갑옷 거치대가 "생물 아님"이었다.
- 영향 받던 곳 5군데(모두 `isLiving()` 호출):
  1. 돌 압력판 `countTriggeringEntities(livingOnly)` — 몹이 안 눌렀다(플레이어만).
  2. `FarmBlock.fallOn` — 몹이 밭을 안 밟았다.
  3. `PowderSnowBlock.entityInside` — `!isLiving() || 발 칸이 가루눈` 이 늘 참이라 몸만 걸쳐도 끼임 속도가 걸렸다.
  4. `BedBlock`·`SlimeBlock.updateEntityAfterFallOn` — 몹 튕김 계수가 0.8(생물은 1.0).
- 고침: `LivingEntity.isLiving()` → `true`(final).

### 바닐라 대조 (바닐라 1.21.1 서버 jar 를 직접 띄움)
- main 의 `tools/vanilla-data/minecraft_server.1.21.1.jar` 를 평지·오프라인으로 띄우고 콘솔(stdin)로 명령을 보냈다. 튕김·가루눈은 `tick freeze` → `summon` → `tick step 1` → `data get entity … Motion[1]`/`Pos[0]` 를 틱마다 기록.
- 결과(시험 `LivingBlockTriggersTest` 에 그대로 넣음, y 만 +1):
  - 돌 압력판: 좀비(AI 있음)·갑옷 거치대 → 눌림, 아이템 → 안 눌림. NoAI 좀비는 바닐라에서도 안 누른다(움직이지 않아 checkInsideBlocks 가 안 돈다).
  - 밭: 좀비가 5칸 위에서 떨어지면 흙. 갑옷 거치대는 부피 0.494 < 0.512 라 그대로.
  - 슬라임·침대 튕김: 갑옷 거치대 30틱 Motion[1] 이 비트까지 같다. 아이템(0.8 배)도 같음 — 단 땅 위 아이템은 `(tickCount + id) % 4 == 0` 틱에만 움직여서, id % 4 를 바닐라 기록과 같은 위상(0)으로 맞춰야 30틱 모두 같다.
  - 가루눈: 발 칸 공기·위 칸 가루눈에서 갑옷 거치대가 안 느려진다(8틱 Pos[0] 같음).
- 고치기 전 코드에서 새 시험 6개 모두 실패, 고친 뒤 통과.

### 같은 판정에 기대는 다른 곳 (바닐라 `instanceof LivingEntity` 전수)
- 바닐라 jar 에서 `instanceof btn`(LivingEntity) 를 하는 메서드 113곳을 뽑아 블록 쪽(`d**` 클래스)을 Furnace 와 하나씩 맞춰 봤다: 모닥불·달콤한 열매 덤불·위더 장미·거미줄(WEAVING)·마그마 stepOn·거북 알·TNT 투사체 점화·가루눈 fallOn/걷기 판정은 Furnace 도 `instanceof LivingEntity`(플레이어는 따로) 로 맞다. 돌 압력판은 바닐라가 `getEntitiesOfClass(LivingEntity.class, …)` 라 instanceof 목록에 안 나온다.
- **빠져 있던 것: 꿀 블록 `maybeDoSlideEffects`.** 바닐라는 미끄러지는(isSlidingDown) 생물·광산 수레·점화된 TNT·보트(상자 보트 포함)에 대해 `level.random.nextInt(5)==0` 이면 `block.honey_block.slide` 소리(1, 1), 다시 `nextInt(5)==0` 이면 엔티티 이벤트 53(미끄럼 입자)을 보낸다. Furnace 는 통째로 없어서 레벨 난수 소비·소리·입자가 달랐다. 엔티티 쪽(`HoneyBlock.entityInside`)과 플레이어 쪽(`Region.checkInsideBlocksPlayer`, 소리는 자기 빼고·이벤트는 자기 포함 — 바닐라 `Player.playSound`·`broadcastEntityEvent`)에 넣었다.
- 플레이어 쪽 미끄럼 판정은 기존 진행 과제(slide_down_block)와 같은 가정을 쓴다: 서버가 아는 변위(`serverMovement()`)의 y 로 `vy < -0.08` 을 본다. 이동 패킷 없는 틱(travelOnly)에는 보지 않는다(진행 과제와 같음, 가정).

### 금 파일
- 전체 시험 4704개 중 장면 금 파일 3개만 달라졌다: `entity-collision-scene`, `entity-collision-scene-powder`, `scene-village`. 꿀 효과만 넣으면 셋 다 그대로(장면에 미끄러지는 몹이 없음) — 모두 isLiving 수정 때문.
- 자리별로 갈랐다(다른 자리는 고친 채 한 자리만 옛 `false` 로 되돌려 금 파일과 같아지는지): 충돌 장면 둘은 **가루눈** 자리만 되돌리면 옛 금 파일과 같다(몹이 가루눈에 몸만 걸쳐도 끼이던 것). 마을은 **침대** 자리만 되돌리면 같다(주민이 침대에 내려앉을 때 튕김 0.8 → 1.0). 두 동작은 위 바닐라 기록 궤적과 비트까지 맞는다.
