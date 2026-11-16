## 2026-11-14 EntityChangeBlockEvent 발화 지점 + UUID 로 엔티티 찾기 (T-040b)

근거: T-040a(PR #318) 후속, 검토 `furnace/reviews/t040a-pr318.md` R7, 노트 `2026-11-13-t040a-listener-drop-protection-events.md` 3장.
대조 원문: Paper 1.21.1 패치(커밋 33ff3dff — `Fire-EntityChangeBlockEvent-in-more-places`, `Fix-new-block-data-for-EntityChangeBlockEvent`, `Turtle-API`, `Add-EntityInsideBlockEvent` 등)와 Paper main 의 `CraftEventFactory`·`paper-server/patches/sources`. GPLv3 결정은 대기 중이라 동작 비교용으로만 읽고 코드는 옮기지 않았다.

## 1. 모양
- Furnace API: `EntityChangeBlockEvent(entity, world, pos, oldData, newData[, cancelled])`(취소 가능), 하위 `EntityBreakDoorEvent`(엔티티가 `LivingEntity`). 버스가 상위 구독을 함께 굽으므로 ECB 구독자는 문 부수기도 받는다.
- 서버: `GameEvents.entityChangeBlock(dim, Rider, pos, old, new, cancelled)`·`entityBreakDoor(...)`, 부르는 쪽은 `B.entityChange(level, who, pos, newState[, cancelled])`. **구독자가 없으면 블록을 읽지 않고 `!cancelled`** — 바닐라 흐름·비용 그대로.
- 새 상태: Paper 의 "Fix new block data" 패치처럼 블록을 없애는 자리는 `B.fluidLeftover(state)`(잠긴 블록이면 물, 아니면 공기).
- `cancelled` 미리 켜기: Paper 가 `!mobGriefing` 을 취소 상태로 넘기는 자리(양 풀 먹기, 파괴수 작물 밟기, 가루눈 불 끄기). 처리기가 풀면 게임 규칙을 이기고 바꾼다(Paper 와 같다).
- Bukkit 다리: `WorldChangeMappings` — 취소만 오간다(Bukkit 에도 새 상태 setter 가 없다). `EntityBreakDoorEvent` 는 HandlerList 가 없는 하위 클래스(Bukkit 과 같다). `getBlockData()` 는 사본.

## 2. 발화 지점 (막혔을 때 동작은 Paper 를 따름)
| 자리 | 새 상태 | 막히면 |
|---|---|---|
| 떨어지는 블록 시작(`FallingBlockEntity.fall`) | 유체 잔여 | 칸 그대로, 엔티티를 월드에 넣지 않음 |
| 떨어지는 블록 착지 | 놓을 상태 | 놓지 않고 엔티티만 사라짐(드롭 없음) |
| 엔더맨 들기·놓기 | 유체 잔여 / 놓을 상태 | 그 시도 건너뜀 |
| 좀벌레 깨우기·숨기 | 유체 잔여 또는 본 블록 / 감염 블록 | 그 칸 건너뜀(난수 return 검사도 안 함) / 숨지 않음 |
| 좀비류 문 부수기(`BreakDoorGoal`) | 유체 잔여(`EntityBreakDoorEvent`) | 부수기 시간을 처음부터 다시 셈 |
| 양·염소 풀 먹기(`EatBlockGoal`) | 유체 잔여 / 흙, 미리 취소=`!mobGriefing` | 먹지 않음 |
| 주민 농사(`HarvestFarmland`) | 유체 잔여 / 심을 작물 | 거두지 않음 / 다음 칸 |
| 개구리 알(`TryLaySpawnOnWaterNearLand`) | 알 | 임신 기억만 지움 |
| 벌 작물 키우기·벌집 꿀 | 다음 단계 / 꿀 단계 | 건너뜀 |
| 여우 단 열매·발광 열매, 동굴 덩굴 손 따기 | 열매 없는 상태 | 건너뜀 / SUCCESS |
| 토끼 당근 | 다음 단계 또는 유체 잔여 | 건너뜀 |
| 거북 알 낳기 | 알(개수는 난수를 먼저 뽑음) | 소리·놓기 없이 플래그만 정리 |
| 위더 부수기·파괴수 잎 | 유체 잔여 | 그 칸 건너뜀 |
| 파괴수 작물 밟기(`CropBlock`) | 공기(1.21.1 값), 미리 취소=`!mobGriefing` | 건너뜀 |
| 번개 구리 벗기기 | 벗긴 상태 | 그 칸 건너뜀 |
| 투사체 맞힘: 후렴꽃·장식 단지·뾰족 점적석·TNT | 유체 잔여 | 건너뜀(장식 단지는 main 에서만 확인 — 가정) |
| 가루눈(몹, 미리 취소=`!mobGriefing`·플레이어) | 공기 | 아래 3장 |
| 수련잎(배), 레드스톤 광석(밟기·맞기), 큰 흘림잎(밟기·투사체) | 각 상태 | 건너뜀 |
| 밭 밟기(몹·플레이어) | 흙 | 흙으로 안 바뀜, 낙하 피해는 그대로(CraftBukkit 이 `super.fallOn` 을 맨 위로 옮겼다 — 검토 R1) |
| 손 쓰기: 삽 길·불 끄기, 도끼, 꿀벌집 조각, 물병→진흙, 엔더의 눈, 케이크(먹기·양초), 퇴비통(넣기·꺼내기, 주민 포함) | 각 상태 | PASS 계열 결과(Paper 와 같은 값) |
| 거미줄 효과(Weaving) | 거미줄 | 그 칸 건너뜀 |

## 3. Paper 와 일부러 다르게 둔 것·못 한 것
- **가루눈 불 끄기**: Paper 는 막히면 `return` 해 불 플래그를 지우지 않는다. 미리 취소된 자리(`!mobGriefing`)에서 처리기가 없을 때도 그 `return` 이 타면 바닐라와 결과가 달라지므로, 바닐라 흐름(불은 끄고 블록만 안 바꿈)을 지켰다.
- **밭 밟기**: 막혀도 낙하 피해는 들어간다 — Paper 와 같다. 남는 차이는 Paper 가 그 앞에서 내는 PHYSICAL `PlayerInteractEvent`/`EntityInteractEvent` 가 Furnace 에 없다는 것뿐.
- **몹 밭 밟기는 지금 죽은 자리**: `Entity.isLiving()` 을 `LivingEntity` 가 덮어쓰지 않아 몹이 밭을 밟지 않는다(기존 버그, 검토 R2 — 별도 PR).
- 없음: 물약 병의 불 끄기(Furnace 에 그 동작 자체가 아직 없다, TODO), `BrushableBlockEntity`(1.21.1 Paper 에 그 지점이 없다), `TNTPrimeEvent`·`EntityInteractEvent`.
- 원인 사슬은 싣지 않는다(바꾸는 엔티티 하나).

## 4. 바닐라 맞춤 덤
- `EatBlockGoal` 의 levelEvent 2001 이 풀 블록 *기본 상태* id 를 내도록 고쳤다(바닐라 `Block.getId(Blocks.GRASS_BLOCK.defaultBlockState())`).
- 거북 알 낳기 뒤 `block_place` 게임 이벤트를 낸다(바닐라에 있는데 빠져 있었다).

## 5. UUID 로 엔티티 찾기
- `World#entityByUuid`(비동기)·`entityByUuidNow`(소유 region 스레드만, 아니면 `ThreadOwnershipException`)·`entityIdByUuid`(아무 스레드). 서버는 `World.entityUuidIndex()` 를 `RegionLevel.indexEntity` 에서 id 색인과 함께 고친다(`remove(uuid, e)` 조건부 — 같은 UUID 를 다른 객체가 넘겨받아도 지우지 않는다). `/data merge`·스폰 알 NBT 는 UUID 를 되돌리므로 색인이 어긋나지 않는다.
- 호환 `Compat.entityByUuid`: 틱 스레드(region·전역)에서는 그 region 에서만 보이고 다른 region 이면 null — 서버의 투사체 주인 찾기(`getTargetableByUuid`)와 같은 답. 그 밖의 스레드는 소유 region 답을 최대 `furnace.compat.entityLookupWaitMillis`(기본 1000ms) 기다린다. 약한 캐시에 기대지 않으므로 실행 이력과 상관없다.
- `CraftProjectile.getShooter`·`Bukkit/Server/World#getEntity(UUID)` 가 이 길을 쓴다.
