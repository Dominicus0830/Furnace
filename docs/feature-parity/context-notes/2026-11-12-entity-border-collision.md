# 2026-11-12 엔티티·월드 보더 충돌 모양 (O05 C1)

근거: CSP O05 보고서 5장 C1. 바닐라 1.21.1 원문(Mojang 이름 소스 extracted_minecraft_data client1.21.1 의 `Entity.collide/collideBoundingBox/collectColliders`,
`CollisionGetter`, `EntityGetter.getEntityCollisions`, `WorldBorder`)과 난독화 서버 클래스(`exs`=Shapes, `exv`=VoxelShape, `ewx`=AABB, `exg`=BooleanOp, `ji$a`=Axis).

## 바닐라 규칙(그대로 옮김)
- `getEntityCollisions(self, box)`: `box.getSize()`(세 변 평균) < 1e-7 이면 빈 목록. 아니면 `getEntities(self, box.inflate(1e-7), NO_SPECTATORS ∧ (self 없음 ? canBeCollidedWith : self.canCollideWith))`
  각각의 `Shapes.create(bb)`(빈 모양도 목록에 든다 — `noCollision` 은 목록이 비었는지만 본다).
- `canBeCollidedWith` 참: 배(늘), 셜커(`isAlive`). `canCollideWith` 기본 = 상대 단단함 ∧ 같은 탈것 무리 아님. 배·수레 = `canVehicleCollide`(단단하거나 **밀 수 있음** — 몹·플레이어).
  윈드 차지끼리는 거짓.
- `Entity.collide`: 엔티티 모양은 `box.expandTowards(m)` 로 **한 번** 모아, 첫 충돌과 오르기 탐색(`collectColliders(…, 탐색 상자)`) 둘 다에 같은 목록을 쓴다.
  `collectColliders` 순서: 엔티티 → (self 가 있고 `isInsideCloseToBorder(self, 상자)`) 보더 → 블록. 순서는 `Shapes.collide` 의 1e-7 조기 0 반환 때문에 결과에 영향.
- `isInsideCloseToBorder(e, box)`: d = max(|xsize|, |zsize|, 1) — **이동으로 넓힌 상자** 기준이라 크게 움직이면 멀리서도 가깝다고 본다. 위치는 엔티티의 지금 위치.
  `distToBorder(e) < 2d ∧ isWithinBounds(e.x, e.z, d)`.
- 보더 모양: `join(INFINITY, box(floor(minX), -∞, floor(minZ), ceil(maxX), +∞, ceil(maxZ)), ONLY_FIRST)` — 격자 x {-∞, x0, x1, +∞}, y {-∞, +∞}, z {-∞, z0, z1, +∞}, 가운데만 빔.
  `Math.ceil(-0.3)` 은 -0.0 이고 바닐라는 옮기지 않은 모양의 좌표를 그대로 빼서 -0.0 을 돌려줄 수 있다(Furnace Shape.collide 는 오프셋 0 이면 더하지 않는다).
  이미 보더 모양 안(밖으로 나간 엔티티)이면 `collide` 는 겹친 칸을 건너뛰어 막지 않는다.
- `noCollision(self, box)`: 블록(`getBlockCollisions(self, …)` — self 문맥, 가루눈) → 엔티티 → self 가 있을 때만 보더(`joinIsNotEmpty(보더, create(box), AND)`).
  `noCollision(box)` = self 없음: 블록 + 단단한 엔티티, 보더 없음. `noBlockCollision` 은 블록만. `getCollisions(self, box)` = 엔티티 모양 다음 블록 모양(보더 없음).
- 오르기 후보(`collectCandidateStepUpHeights`)는 `getCoords(Y)` 격자 좌표 전부. `Shapes.create` 는 세 축 모두 [0,1] 의 1/2^b 배수면 큐브 격자(중간 좌표 포함)라
  Furnace `Shape.box` 도 같은 격자를 만든다(`exactYCoords`). 블록 모양은 여전히 상자 minY/maxY(바닐라와 다를 수 있음 — 기존 근사, 아래 남은 차이).

## 호출 지점(바닐라 → Furnace)
| 바닐라 | 형태 | Furnace |
|---|---|---|
| Entity.collide / Player·탈것 move | self | `Entity.collideMove(level, self, …)`(플레이어 이동 검증 포함), 겉날개 = `collideBoundingBox` |
| ServerGamePacketListenerImpl noCollision(player, 옛 상자) | self, **move 뒤 위치** | `noCollision(p, mv.x, mv.z, oldBox)` |
| isPlayerCollidingWithAnythingNew | getCollisions(player) | `collisions(p, …)` |
| handleMoveVehicle 두 번 | self=탈것 | `noCollision(v, …)` |
| Player.canFallAtLeast·canPlayerFitWithinBlocksAndEntitiesWhen | self | `noCollision(p, …)` |
| ServerPlayer.adjustSpawnLocation | self=플레이어(그때 위치) | `noCollisionForUnmountedPlayer(sx, sz, …)` — 처음 들어오면 (0,0), 리스폰은 죽은 자리 |
| BoatItem | self=새 배 | `noCollision(boat, …)` — 몹·플레이어 자리에 배를 못 놓는다 |
| WalkNodeEvaluator(PathNavigationRegion = 엔티티 충돌 없음) | self, 엔티티 없음 | `noCollisionIgnoringEntities(mob, …)` |
| Camel.wouldNotSuffocateAtTargetPose | noBlockCollision(self) | `noBlockCollision(this, …)` |
| DismountHelper.canDismountTo | getBlockCollisions(승객) | `canDismountTo(level, 승객, …)` → `noBlockCollision(승객 엔티티, …)` |
| DismountHelper.findSafeDismountLocation(침대·정박기 리스폰, 몹 내리기) | getBlockCollisions(null) | `noBlockCollision(null, …)` — 배·셜커 몸은 안 본다 |
| LivingEntity.randomTeleport(후렴과) | teleportTo 뒤 noCollision(this) | `noCollision(p, nx, nz, …)` — 보더는 도착 자리 기준 |
| Entity.isFree(물·용암 벽 뛰어나오기) | noCollision(this) ∧ !containsAnyLiquid | 같음 |
| EntityType.getYOffset(알·양동이) | getCollisions(null) | `collisions(null, …)` |
| ItemEntity·HangingEntity·ItemFrame·TamableAnimal·LongJump·InsideBrownianWalk·Zombie 지원군·Warden·Ghast·Shulker(둘)·Boat floatBoat·몹 randomTeleport | self | `noCollision(this/mob, …)` |
| ShulkerBox 열기·ArmorStand·스포너·자연 스폰·ExperienceOrb·AbstractArrow | self 없음 | `noCollision(box)` |

바뀐 관측 동작(바닐라와 같아짐): 수레·배는 밀 수 있는 몹·플레이어에 막힌다. 한 번 이동 안에 몸이 닿으면 `move` 가 가로 속도를 0 으로 만들어 그 틱에는
수레가 몹을 태우지 못한다(줍기는 move 뒤 0.2 넓힌 상자 — `MinecartTest` 를 바닐라 순서에 맞게 고쳤다). 몹·아이템은 배·셜커 위에 서고, 배 높이(0.5625)는 걸어 오른다.

곁들여 고침: `AbstractArrow.shouldFall` 은 바닐라가 **위치 점** 상자를 0.06 넓힌 것인데 몸 상자를 넓히고 있었다.

## 검증
- 하네스(커밋 안 함, 바닐라 서버 클래스 + 번들러 jar 라이브러리로 직접 실행): 무작위 30만 건 × 씨앗 3개, 항목 6개(보더 collide, 엔티티 상자 collide,
  보더 겹침, 엔티티 겹침, 모양 목록 collideWithShapes, 오르기 후보) — 불일치 0(-0.0 고친 뒤). 하네스 소스는 프로젝트 파일 `furnace/reviews/o05-c1-collision-repro/`.
- 시험: `EntityCollisionShapeTest`(18), `HardColliderTest`(3).
- 검토 반영 뒤(기준 브랜치 92b7239b 합침): 전체 `:furnace-api:test :furnace-server:test` 4430개 실패 0. 검토 하네스 4변형(씨앗 1, 보더 +3e7, flat, 보더 −3e7 flat)
  각 30만 건 — 오르기 후보 불일치 7400·7308 → 0, 남은 것은 −3e7 flat 의 얇은 상자 겹침 260·3(R7, 아래 남은 차이). 전체 `:furnace-api:test :furnace-server:test` 4327개 중 실패 1(MinecartTest — 위 바닐라 순서로 고침, 재실행 통과).

## 검토 반영(PR #291 독립 검토 R1~R9)
- R1: `findSafeDismountLocation` 이 `noCollision(box)`(이제 배·셜커를 봄)를 쓰던 회귀 → 블록만. `canDismountTo` 는 승객 엔티티를 블록 문맥으로 넘긴다.
- R2 후렴과 보더(도착 자리), R3 `isFree` 액체 검사, R4 낙타 자세 상자 `deflate` 제거(바닐라 `wouldNotSuffocateAtTargetPose` 는 줄이지 않음).
- R5: `WorldBorder.bounds()` — 정적 크기면 잠금 없는 volatile 스냅숏(중심·크기·최대 크기·보간 끝에서 지움), 보간 중이면 벽시계로 매번 계산(값은 그대로).
  보더 모양 기억은 (정수 경계, 모양) 한 객체로 바꿔 끼우고 경계는 비트로 비교(-0.0 과 0.0 을 가른다).
- R6: `Shape.box` 의 빈 경우는 `Shapes.empty()` 처럼 Y 격자 {0} 을 가진 빈 모양(크기 0 몸이 오르기 후보 0 − minY 를 내는 바닐라 버릇). 바닐라 엔티티로는 닿지 않는다.
- R8: `HardColliderTest` 가 `canCollideWith` 를 넓게 덮어쓰는 클래스도 잡는다(좁히기만 하는 AbstractWindCharge 는 허용 목록).

## 성능 메모(P-O05-07 가 이어받음)
- 몹·플레이어·투사체 등 상대의 `canBeCollidedWith` 만 보는 self 는 `HardCollider`(배·셜커) 구역 목록만 훑는다(EntityStore 클래스별 목록 — 같은 결과·순서).
  배·수레만 전체 훑기 + 플레이어. canBeCollidedWith 를 덮어쓰면 HardCollider 를 달아야 한다(`HardColliderTest`).
- 보더는 호출마다 `borderBounds()` 한 번(정적 보더면 잠금·할당 없음), 모양은 정수 경계가 같으면 레벨에 기억해 재사용.

## 남은 차이(가정·작음)
- 플레이어는 Furnace 에서 엔티티 저장소 밖이라 배·수레의 충돌 후보에서 엔티티 **뒤**에 온다(바닐라는 구역 안 넣은 순서). 1e-7 조기 반환에만 영향.
- 플레이어 self 의 블록 문맥(가루눈 등)은 여전히 없음(기존). region 경계 너머 엔티티는 안 보임(밀기와 같은 기존 한계).
- 블록 모양의 오르기 후보는 상자 minY/maxY(바닐라는 격자 좌표 — 큐브 격자 반 블록 등에서 후보 1.0 이 더 있을 수 있다). 기존 근사, 이번에 손대지 않음.
- `isUnobstructed`(건설 막기, blocksBuilding)는 Furnace 가 플레이어만 본다 — 별도 항목.
- 겉날개 비행은 바닐라가 `Entity.move` → `collide`(착지할 때 오르기 있음)인데 Furnace 는 `collideBoundingBox`(오르기 없음, 기존 근사).
- 플레이어 승객의 `canDismountTo` 블록 문맥(가루눈 등)은 없음 — 플레이어가 Entity 가 아니라서(엔티티 승객은 넘긴다).
- 좌표 ±3e7 근처에서 폭 ~1e-7 인 아주 얇은 상자의 겹침 판정은 바닐라 `IndexMerger` 가 얇은 칸을 버려 다를 수 있다(검토 하네스 flat 변형:
  보더 260·엔티티 3 / 30만). 실제 호출 지점에는 그런 상자가 없다.
