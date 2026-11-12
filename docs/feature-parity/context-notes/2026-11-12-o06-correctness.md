# O06 정확성 C1~C3: 블록 변경 때 길 다시 찾기·바라보기 조건·공유 난수 (2026-11-12)

O06 검토 보고서(`furnace/reviews/opt-review/O06-mob-ai-spawning.md` 3장)의 정확성 항목 셋. 바닐라 1.21.1 서버 바이트코드(`javap -c -p`)로 대조했다.

## C1 블록 충돌 모양이 바뀌면 길 다시 찾기
- 바닐라 `aqu.a(jd,dtc,dtc,int)` = ServerLevel.sendBlockUpdated: `aqs.a(jd)`(변경 방송) → `epw.a(jd)`(PathTypeCache 무효화) →
  `dtc.k(level,pos)` 충돌 모양(문맥 없음) 둘 → `exs.c(old,new,exg.g)` = Shapes.joinIsNotEmpty(NOT_SAME) 이 거짓이면 끝 →
  `V`(navigatingMobs, ObjectOpenHashSet) 마다 `btp.N()` getNavigation(탈것 조종이면 탈것 것) → `cda.b(jd)` shouldRecomputePath 면 목록에 →
  `W`(isUpdatingNavigations) 켜고 목록 순서대로 `cda.i()` recomputePath → 끄기.
- 부르는 곳 `dcw` Level.setBlock: `(flags & 2) != 0` 이고 청크 단계 `aql.c`(BLOCK_TICKING) 이상일 때만.
- navigatingMobs 는 `aqu$a.d`(onTrackingStart)에서 **모든 Mob** 을 넣고 onTrackingEnd 에서 뺀다.
- Furnace: `ChunkLevel.setBlock` 의 UPDATE_CLIENTS 갈래에서 `updateNavigations`. 몹 목록은 `EntityStore.mobs()`(넣은 순서).
  - 후보를 먼저 모으고 후보가 있을 때만 충돌 모양을 비교한다(둘 다 부수 효과 없음 — 바닐라는 모양 먼저). 몹이 없거나 길이 없으면 모양 계산도 없다.
  - 같은 길찾기가 두 번 모이면(몹이 조종하는 탈것 몹) 두 번 부른다: 두 번째는 20틱 안이라 미룬 표시만 남는다 — 바닐라와 같다.
  - `Shape.differs` = joinIsNotEmpty(NOT_SAME): 한쪽만 비면 참, 같은 객체·같은 상자 목록이면 거짓, 아니면 joinOnlySecond 양쪽.
  - PathTypeCache: Furnace 의 칸별 기억은 탐색마다 비우고, 상태별 기억(`WalkNodeEvaluator.stateMemo`)은 위치와 무관해 무효화할 것이 없다.
  - 순서: recomputePath 는 난수를 쓰지 않고 다른 몹 상태를 건드리지 않으므로 훑는 순서가 결과를 바꾸지 않는다(바닐라도 해시 순서).
- **가정(차이)**: ① BLOCK_TICKING 단계 조건을 보지 않는다(변경 방송 `markChanged` 도 안 본다) — 보더 청크 블록 변경만 다르다.
  ② region 이 다르면 그 region 몹은 보지 않는다(다른 스레드 소유). region 은 합치기 반경만큼 떨어져 있고 길은 자기 region 청크만 읽으므로
  다른 region 칸이 길 근처 구(반지름 = 남은 노드 수)에 드는 일은 드물다.
- 성능 후속: 블록 변경 × 몹 수 훑기는 바닐라와 같은 비용이다. 길 경계 상자 청크 격자 색인은 O06 TASK P-O06-06.

## C2 LookAtPlayerGoal 조건
- 바닐라 `cau`(LookAtPlayerGoal): 대상 종류가 플레이어면 `cef.b().a(거리).a(notRiding)` = forNonCombat().range(거리).selector(EntitySelector.notRiding(mob)),
  canUse 는 `dcw.a(cef, mob, x, eyeY, z)` getNearestPlayer(조건). canContinueToUse 는 `bE` isAlive·거리²·lookTime 만(관전자 검사 없음).
- `bsw.b(bsr)` notRiding: 대상의 탈것을 거슬러 올라가며 mob 이면 거짓.
- `cef.a(btn,btn)` TargetingConditions.test 의 첫 검사는 `btn.eq` canBeSeenByAnyone(!관전자 && 살아 있음)이다. Furnace 는 `ep` canBeSeenAsEnemy(무적 능력도 제외)를 써서
  **비전투 조건이 크리에이티브 플레이어를 빼고 있었다**(유혹·구걸·돌고래·센서의 가까운 플레이어 등). 전투 조건은 바로 뒤 canAttack(=canBeSeenAsEnemy)이 걸러서 결과가 같다.
- 판다 바라보기도 같은 조건으로 고친다(바닐라 `cft$g` 판다 바라보기도 부모의 lookAtContext `g` 로 getNearestPlayer 를 부른다).

## C3 공유 정적 난수
- 바닐라 `cdy`(Sensor) 정적 초기화 `ayw.b()` = RandomSource.createThreadSafe(ThreadSafeLegacyRandomSource). `bxq`(ShufflingList) 는 인스턴스 필드 `ayw b`(목록마다 RandomSource.create()).
- `ayw.a()` = create(RandomSupport.generateUniqueSeed()) — 곱해 가는 원자 값 ^ nanoTime. Furnace `RandomSource.create()` 도 그렇게 바꿨다
  (이전 nanoTime ^ 스레드 id 는 같은 순간에 만든 두 난수의 씨앗이 겹칠 수 있었다 — TriggerGate 는 부를 때마다 ShufflingList 를 만든다).
- 스레드 안전 난수는 잠금으로 감쌌다(바닐라는 CAS). 값이 씨앗으로 고정되지 않는 난수라 분포만 같으면 된다.

## 독립 검토 반영(PR #274, 11-12)
- 가정 ③(R6): 바닐라 navigatingMobs 는 추적(TRACKED, FULL 이상) 청크의 몹이고 Furnace `EntityStore.mobs()` 는 region 에 적재된 몹 전부다. 같은 집합으로 본다.
- R4: `updateNavigations` 를 바닐라 순서(충돌 모양 먼저)로 — 충돌 모양은 표의 공유 객체라 같은 모양 변경(레드스톤 선 세기 등)은 몹을 훑지 않고 끝난다.
- C2 로 비전투 조건이 크리에이티브를 통과시키면서 드러난 곳:
  - R1 `PiglinSpecificSensor`: 금 안 입은 공격 대상 플레이어는 바닐라 `piglin.canAttack(player)` → `canBeSeenAsEnemy` 도 본다(평화 난이도 조건은 기존 TODO).
  - R2 `Pufferfish.SCARY_MOB`: 바닐라 `player.isCreative()` 면 거짓.
  - R3 `ArmorStand.canBeSeenByAnyone = !투명 && !마커`, `LivingEntity.canBeSeenAsEnemy = !무적 && canBeSeenByAnyone()`(바닐라 그대로).
- 이전부터 있던 것(같은 대조에서 나옴): R5 `Raider.HoldGroundAttackGoal.shoutTargeting` 은 forNonCombat(전투면 동맹 검사로 옆 약탈자에게 못 외침),
  R7 Evoker 울로로는 `getNearbyEntities(Sheep, forNonCombat().range(16).selector(파란 양))`(거리 16 구·시야), R8 TemptingSensor 는 나를 탄 플레이어 제외.
