# 엔더 드래곤 바닐라 재대조 (2026-10-28)

1.21.1 서버 jar 디컴파일을 직접 한 줄씩 읽어 Furnace 와 대조했다. 대상 클래스(난독 이름):
`cia` EnderDragon, `chy` EnderDragonPart, `cir` EnderDragonPhaseManager, `ciq` EnderDragonPhase,
`cic`/`cid` AbstractDragon(Sitting)PhaseInstance, `cig` HoldingPattern, `cio` StrafePlayer, `cii` LandingApproach,
`cij` Landing, `cip` Takeoff, `cim` SittingFlaming, `cin` SittingScanning, `cil` SittingAttacking, `cie` ChargingPlayer,
`cif` Dying, `cih` Hover, `chz` EndCrystal, `cng` DragonFireball, `dxb` EndDragonFight, `dxa` DragonRespawnAnimation,
`ecd` EndPodiumFeature, `edo` SpikeFeature.

## 이전 가정의 확정 (docs/user-todo.md 엔더 드래곤 절)

| 가정 | 결과 |
|---|---|
| 난수 순서(기억 기반) | **확정, 일치.** aiStep 죽는 중 nextFloat×3(입자 위치, 서버도 소비), checkCrystals `nextInt(10)`, checkWalls 지운 칸 있을 때 `nextInt`×3(x,y,z), tickDeath 180~200틱 nextFloat×3, HoldingPattern `nextInt(crystals+3)` → `nextInt((int)(d+2))` → `nextInt(crystals+2)`(단락 평가) → `nextInt(8)`, 경로 다음 칸 높이 `nextFloat()*20` 되뽑기, StrafePlayer `nextInt(8)`, createNewDragon `level.random.nextFloat()*360`, 관문 섞기 `Util.shuffle(RandomSource.create(seed))`, 기둥 재생성 수정 방향 `RandomSource.create()`. 그 밖 단계(Landing/Takeoff/Sitting*/Charging/Dying/Hover)는 서버에서 난수를 쓰지 않는다(doClientTick 만 씀). |
| 죽음 회전 | **확정.** 죽음 애니메이션에 회전은 없다. tickDeath 는 `move(SELF, (0, 0.1, 0))` 만 하고, aiStep 은 죽는 중이면 입자 난수만 쓰고 돌아간다. Furnace 와 같다. |
| normalize 기준값 | **확정, 일치.** aiStep 의 `target - pos` 정규화와 `(sin yRot, vy, -cos yRot)` 정규화, StrafePlayer/SittingScanning 의 `(dx, 0, dz)` 정규화, `acos(dot)*180/π + 0.5`, 10도 기준, 0.8 + 0.15·(dot+1)/2 감속, 0.91 세로 감속 모두 같다. |
| always_hurts_ender_dragons 태그 | **확정.** `#minecraft:is_explosion` 하나(= fireworks, explosion, player_explosion, bad_respawn_point). Furnace 는 `source.explosion() || 태그` 로 같은 집합. |

## 차이 표

| # | 위치 | 바닐라 동작 | Furnace(수정 전) | 수정 |
|---|---|---|---|---|
| 1 | EnderDragon.knockBack | 날개 근처 플레이어도 같은 목록이라 `lastHurtByMobTimestamp < tickCount - 2` 일 때만 5 피해 | 플레이어는 검사 없이 매번 5 피해 | 고침 (Player.combatTicks 를 tickCount 자리로) |
| 2 | EnderDragon.tickDeath 200틱 | `remove(KILLED)` 뒤 `gameEvent(ENTITY_DIE)` | gameEvent 없음 | 고침 |
| 3 | EnderDragon.readAdditionalSaveData | `EnderDragonPhase.getById`: 범위 밖 id 는 HOLDING_PATTERN | 0..10 으로 clamp(큰 값 → HOVERING) | 고침 |
| 4 | EnderDragon.onCrystalDestroyed | 파괴자가 플레이어가 아니면 `getNearestPlayer(조건, x,y,z)` — 출처 없음이라 거리 64·투명 검사를 하지 않는다 | 드래곤을 출처로 넘겨 드래곤에서 64 안만 | 고침 |
| 5 | AbstractDragonSittingPhase.onHurt | 직접 원인이 AbstractArrow **또는 WindCharge** 면 불 1초·피해 0 | 화살만 | 고침 |
| 6 | EnderDragon.hurt(source) | 무적 검사 없이 몸통으로 넘긴다(단계 onHurt 가 먼저 돈다, 무적은 LivingEntity.hurt 가 막음) | 앞에서 isInvulnerableTo 로 막아 단계 onHurt 를 건너뜀 | 고침 |
| 7 | EnderDragonPart.hurt | 파트 자신의 isInvulnerableTo(파트는 무적 표시 없음, 불 피해는 드래곤 종류라 면역, isRemoved 는 파트 자신 — 늘 false) | 본체 isInvulnerableTo(무적 표시까지) | 고침(본체가 지워졌으면 막는 것은 의도한 차이) |
| 8 | DragonFireball.onHit | `getEntitiesOfClass(LivingEntity, 상자)` — 플레이어 포함, 4칸 안 첫 대상 자리로 구름 이동 | 플레이어를 빼고 봄 | 고침(순서는 엔티티 뒤 플레이어 — 가정) |
| 9 | EndDragonFight 생성 | `ticksSinceLastPlayerScan = 21` → 첫 틱에 플레이어 훑기 | 0 → 20틱 뒤 | 고침 |
| 10 | EndDragonFight 보스 바 범위 | (x, 128 + origin.y, z) 에서 192 | y 128 고정 | 고침(origin.y=0 이면 같음) |
| 11 | EndDragonFight.isArenaLoaded | 안쪽 반복이 `z = 8+cz; z <= 8+cz` 라 x -8..8, z +8 한 줄만 보고, FULL·BLOCK_TICKING 이상인지 본다 | (-8..8)² 전체가 불러와 있는지 | 고침(줄은 바닐라대로, 상태는 "불러옴" 으로 — 가정) |
| 12 | EndDragonFight.findExitPortal | 블록 엔티티 자리 → 없으면 MOTION_BLOCKING 높이부터 최저 높이까지 BlockPattern 전체 대조, 기억 위치는 null 일 때만 기록 | 기억 위치를 먼저 믿고, NO_LEAVES 높이부터 y 4 까지, 기반암 6칸만 대조, 매번 덮어씀 | 고침(패턴 '#' 전부 대조, 높이·하한·기록 규칙 바닐라대로). 블록 엔티티 단계는 미구현(아래) |
| 13 | EndDragonFight.respawnDragon | 패턴을 못 찾을 때까지 되풀이해 기반암·포털을 엔드 돌로 | 한 번 | 고침 |
| 14 | DragonRespawnAnimation SUMMONING_PILLARS | 기둥 주변 ±10 지우기가 betweenClosed 순서(x 가장 빠름, y, z) | x 바깥, z 안쪽 | 고침 |
| 15 | EndDragonFight.Data | `IsRespawning` 은 `lenientOptionalFieldOf(기본 false)` 이고 저장 값이 언제나 false 라 키를 쓰지 않는다. 읽어서 true 면 START | 읽지 않음 | 고침(읽기만, 쓰지 않음 — 검토 반영) |
| 16 | EndDragonFight.hasActiveExitPortal | 청크 (-8..8)² 의 TheEndPortalBlockEntity(관문 블록 엔티티 포함) 가 하나라도 있으면 true | 받침대 둘레 5×5 에 end_portal 블록이 있는가 | **못 고침**: Furnace 에 end_portal·end_gateway 블록 엔티티가 없다 |
| 17 | 투사체·폭발 → 파트 | Level.getEntities 가 드래곤 파트를 포함 → 화살·폭발이 파트(머리면 배율 없음)에 맞는다 | 엔티티 조회에 파트가 없고 본체는 isPickable=false 라 투사체가 드래곤을 **통과**한다. 폭발은 본체(몸통 배율)로 맞는다 | 고침(2차 커밋, 아래 "파트 판정") |
| 18 | knockBack/hurtAll 피해 후 | `EnchantmentHelper.doPostAttackEffects` | 없음 | 못 고침(마법 부여 후처리 경로 없음) |
| 19 | 날개·머리 대상 순서 | 섹션 순 한 목록(플레이어 섞임) | 엔티티 뒤 플레이어 | 기존 가정 유지 |
| 20 | updateDragon | 드래곤에 사용자 이름이 있으면 보스 바 이름을 그것으로 | 없음 | 못 고침(Entity.hasCustomName 이 언제나 false) |
| 21 | EndCrystal 폭발 | 원인 엔티티가 있으면 `explosion(crystal, 원인)` 피해 출처 | 원인 없이 | 미수정(드래곤은 태그로 어차피 맞음) |

일치 확인(차이 없음): aiStep 비행 수식 전체, 파트 위치·xo 복원, flapTime, checkCrystals(10틱마다 +1, 32칸), checkWalls(dragon_transparent/dragon_immune, mobGriefing, 2008), hurt 배율(`/4 + min(d,1)`, 0.01 미만 false, 25% 앉은 피해 → TAKEOFF, 치명타 → 체력 1·DYING), 경험치 12000/500·8%/20%, 1028 이벤트, 노드 24개 좌표·높이(`max(seaLevel+10, 높이+5/15)`)·인접 비트·A* 와 실패 처리, 단계 11개의 doServerTick/begin/end/속도/회전 속도, getHeadLookVector, 관문 위치·EndGatewayFeature 모양, EndPodiumFeature 모양·횃불 방향, 기둥 재생성(흑요석·쇠창살 속성·수정·기반암·불), 부활 단계 타이밍(3001 이벤트 0/50/51/52/≥95, 40틱 주기, 80~100, 폭발 위력 5 BLOCK / 6 NONE), 수정 4개 탐색(N,E,S,W 2칸), 처치 시 알 위치(MOTION_BLOCKING).

## 의도한 차이 (검토 반영, 2026-10-28)
- 바닐라는 받침대 높이를 볼 때 청크를 동기로 불러온다. region 스레드는 동기 로드를 못 하므로 받침대 청크(origin ±4)가 없으면 상태 훑기·부활·드래곤 찾기·출구 포털 기록/블록 쓰기를 그 틱엔 건너뛰고 다음 틱에 다시 본다(경기장 판정은 바닐라 z+8 한 줄 그대로).
- 받침대 청크가 없을 때 미룬 출구 포털(active 값은 마지막 호출)과 드래곤 알은 청크가 들어온 첫 틱에 놓는다. tryRespawn 은 위치가 없으면 돌아가고, respawnDragon 은 청크가 없으면 시작하지 않는다. 관문(반지름 96) 청크가 없을 때의 관문 블록은 미루지 않는다(남은 위험).
- 부활 시작의 패턴 치환 반복에 안전 상한(64회, 바뀐 것이 없으면 중단)을 둔다. 정상 받침대에선 한 번에 끝나 결과가 같다. 치환 순서는 dx·dz·dy 이고 바닐라 BlockPattern 순서(방향에 따라 다름)와 다를 수 있다 — 엔드 돌로 바꾸는 것뿐이라 결과 블록은 같다.
- 출구 포털 찾기 1단계(블록 엔티티 자리)와 BlockPattern.find 의 기준점 상자 [pos, pos+6] 24방향 탐색은 이식하지 않았다. Furnace 에 end_portal·end_gateway 블록 엔티티가 없다. 정상 받침대(origin 기둥)에선 두 경로 모두 같은 포털 층 가운데를 찾는다(기존 시험의 처치·부활 경로가 확인).

## 확정 못 한 것 (가정)
- 패턴 탐색에서 받침대 가운데가 origin 기둥이 아닌 경우, 위아래 뒤집힌 방향 패턴(바닐라 BlockPattern 은 모든 방향을 본다).
- isArenaLoaded 의 청크 상태 기준(BLOCK_TICKING) — "불러옴" 으로 근사.
- 날개 밀치기·숨결 화염구 근처 대상의 엔티티/플레이어 섞인 순서.
- 위 표 16~18, 20.

## 시험
`DragonParityRecheckTest`: 잘못된 저장 단계 → HOLDING_PATTERN, 앉은 단계 WindCharge 무시, 무적 드래곤도 단계 onHurt 실행, 파트 불 면역·무적 표시 없음, 수정 파괴 출처 없는 플레이어 탐색(64 밖), 화염구 구름 플레이어 자리, 첫 틱 보스 바·IsRespawning 읽기, 앉은 단계 전이 시나리오(둘러보기→공격→숨결 ×4→이륙→맴돌기), 아무도 없으면 100틱 뒤 이륙.

## 파트 판정 (2차, 검토 설계안)
- EnderDragonPart 는 가벼운 Targetable: 레벨·저장·추적·틱 목록에 없고 isPickable=true, 몹 AI 겨냥 대상 아님(canBeSeenAsEnemy=false). 클라이언트는 본체 스폰 패킷 id+1..8 로 직접 만든다(본체 nextEntityIds(9) 유지).
- 투사체(Projectile.findHitEntity): 조회에 드래곤이 잡히면 바로 뒤에 파트 8개를 상자 검사 없이 후보로(바닐라 Level.getEntities). 0.3 키운 파트 상자로 선분 대조. 맞으면 Projectile.onHit 가 주인(플레이어 포함)·투사체를 파트에 실어 각 투사체의 hurtByMob 경로가 그대로 본체 hurt(파트, …)로 간다 — 앉은 단계의 화살·돌풍구 처리도 동작.
- 폭발(Explosion): 엔티티 반복에서 드래곤 바로 뒤에 파트마다 자기 위치·상자·눈높이(0.85h)로 피해, 밀림 생략. 중복 피해는 LivingEntity 무적 시간 규칙이 거른다. 계산기(shouldDamageEntity)는 부모로 판정(근사).
- setLastHurtMob 에 파트가 오면 null(바닐라: LivingEntity 가 아니면 null).
- 근접 공격(파트): 바닐라 handleInteract 의 canInteractWithEntity(상자, 1.0) = 상자–눈 거리² < 16 으로 바꿨다(다른 엔티티의 눈–발 거리² < 36 판정은 그대로 — 남은 차이).
- 바닐라와 같은 특이점: 섹션 조회는 상자 ±2 만 넓혀, 드래곤 중심 청크와 먼 쪽에서 오는 투사체는 드래곤 자체가 안 잡혀 파트도 못 맞힌다.
- region 경계: 파트는 부모와 같은 region 상태이고 조회는 기존 엔티티 조회의 region 범위를 따른다(다른 region 의 드래곤은 기존 엔티티처럼 보이지 않는다 — 가정, 별도 시험 없음).

## 후속 (M7, 2026-11-07): 표 16·18·20 해소
- 16 hasActiveExitPortal: `EndPortalBlockEntity`(데이터 없는 블록 엔티티, 엔드 포털 블록이 단다)를 추가하고 `EndGatewayBlockEntity` 를 그 하위 종류로 만들었다(바닐라 TheEndGatewayBlockEntity extends TheEndPortalBlockEntity). 판정은 바닐라대로 중심 청크 ±8 의 청크마다 이 종류 블록 엔티티가 있는지 본다(불러와 있는 청크만 — 의도한 차이). 이로써 관문만 있어도 열린 것으로 친다(바닐라 동작).
- 18 doPostAttackEffects: 날개 밀치기(5)·머리·목 피해(10) 뒤 맞은 쪽 장비의 가시를 발동한다(드래곤은 무기가 없어 이것뿐). 벌 침도 같은 한 줄을 채웠다.
- 20 사용자 이름 보스 바: Entity 에 사용자 이름이 생겨 이미 동작 중이라 `barName()` 접근자와 시험만 추가.
- 시험: `DragonParityRecheckTest.activeExitPortalIsJudgedByPortalBlockEntities`, `dragonAttacksTriggerThornsOfVictim`, `bossBarUsesCustomName`.
