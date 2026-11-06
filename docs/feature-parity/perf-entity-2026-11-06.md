# 엔티티 틱·AI 성능 점검 (2026-11-06)

대상: 엔티티 틱, 몹 AI(목표·감지), 길찾기, 엔티티 조회·추적. 원칙: 관측 결과는 바닐라와 같게, 내부 구조는 자유. 측정으로 확인한 것만 고쳤다.

## 측정 방법
- 벤치: `furnace-server/src/test/java/org/domi/furnace/server/world/RegionTickBench.java`(기본 꺼짐). 평지, 시야 10, 플레이어 1명이 반지름 24 원을 걷고,
  몹 1000마리(소·양·돼지·닭·좀비·해골·크리퍼·거미 같은 비율)를 가운데 60×60 칸에 둔다. 밤(18000), 600틱 중 뒤 3/4 의 region 틱 시간.
  - 실행: `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test --rerun --tests org.domi.furnace.server.world.RegionTickBench -Dfurnace.bench=true -Dfurnace.bench.count=1000 -Dfurnace.bench.ticks=600`
  - 이번에 더한 것: `-Dfurnace.bench.cpujfr=true` 면 region 스레드 CPU 샘플(jdk.ExecutionSample 2ms)을 자기·포함 비율과 상위 호출 경로(`RPROF`/`RCALL`)로 찍는다.
    엔티티가 많으면 EmbeddedChannel 보류 작업이 재귀로 깊어져 StackOverflowError 가 나서, 비우기를 큰 스택 스레드에서 한다.
  - 바이옴은 ServerCore 와 같은 길(소유 확인 → region 청크)로 답한다(`roughBiomeAt` 도 같은 길).
- 기준선: 수정 전 커밋 `7d55ec3a` 를 별도 worktree 에 풀고 같은 벤치 파일로 쟀다. 전/후를 교대로 3회.
- 공유 4코어 VM 이라 절대값 잡음이 있다. 엔티티 수도 회차마다 조금 다르다(자연 스폰·소멸).

## 결과

| 회차 | 전 avg / p50 / p95 (ms) | 후 avg / p50 / p95 (ms) |
|---|---|---|
| 1 | 15.36 / 15.32 / 21.36 | 11.88 / 11.32 / 17.99 |
| 2 | 14.95 / 15.04 / 21.98 | 11.25 / 10.73 / 15.74 |
| 3 | 16.09 / 15.61 / 24.62 | 11.17 / 10.70 / 15.49 |

틱 평균 약 26% 감소(15.5 → 11.4ms), p95 약 22.7 → 16.4ms. (뒤의 속성 배열·바이옴 캐시 커밋은 이 표 이후라 포함되지 않았다 — 단독 효과는 잡음 안이었다.)

실서버 봇 시험(평지, 소 600마리 우리, `/tick query` 3회): 전 6.2 / 7.9 / 6.8ms → 후 5.8 / 5.8 / 5.9ms. 소는 표적 찾기가 적어 이득이 작다.

### 수정 전 CPU 샘플 상위(자기 시간)
| 비율 | 위치 | 원인 |
|---|---|---|
| 13.5% | `ArrayList.add` ← `EntityStore.forEachIn` ← `getEntitiesOfClass` ← `NearestAttackableTargetGoal`·`AvoidEntityGoal` | 구역 목록 전체(몹 수백)를 훑고 클래스로 걸렀다. 바닐라는 ClassInstanceMultiMap 으로 그 클래스만 본다 |
| 7.5% | `LivingEntity.isPushable` ← 밀기 후보 | `onClimbable` 이 후보마다 블록을 새로 읽었다(바닐라는 getInBlockState 캐시) |
| 5.8% | `ConcurrentHashMap.get` ← `RegionLevel.chunk` ← `PoiManager.sectionsToVillage` | 좀비 마을 거리가 13×13 청크를 d 마다 다시 찾고, 64칸 캐시가 거의 빗나갔다 |
| 9.7%(포함) | `NaturalSpawner.biomeName` ← `createState` | 엔티티마다 확대 지터 바이옴 — 바닐라는 지터 없는 getRoughBiome |
| 5.3%(포함) | `WalkNodeEvaluator.getPathTypeFromState` | 칸마다 문자열 태그 조회 13번 |

## 바꾼 것

| 커밋 | 내용 | 결과가 같은 근거 |
|---|---|---|
| perf(entity) 클래스별 구역 목록 | 구역마다 처음 찾은 클래스의 목록을 두고 넣기·빼기를 같이 한다. 구역 조회는 Long 상자 없는 해시 | 클래스 목록 = 전체 목록을 그 클래스로 거른 것(순서까지). 바닐라 EntitySection 과 같은 구조 |
| perf(entity) onClimbable 캐시 | `inBlockState()`(틱 시작·칸 이동 때 비움) | 바닐라 onClimbable 도 getInBlockState. 같은 틱 안 블록 변경을 늦게 보는 것도 바닐라와 같다 |
| perf(world) 청크 조회 캐시 | 64 → 1024칸, 곱셈 해시 | 캐시는 같은 ChunkColumn 을 돌려준다(퇴출·주인 변경·틱마다 비움은 그대로) |
| perf(path) 칸 종류 상태별 기억 | getPathTypeFromState 를 상태별 배열로 | 결과가 블록 상태 하나의 성질(태그·유체·LAND 가능)로만 정해진다 |
| perf(entity) 빈 집합 바로 반환 | takeDirty/takeToUpdate, GoalSelector 막힌 칸 없음 | 빈 결과·false 그대로 |
| perf(entity) 속성 번호 배열, 바이옴 캐시 | AttributeMap.get/value, Region.biomeIdAtQuart | 같은 인스턴스·같은 청크 |

## 바닐라와 다르던 것(같이 고침)
1. **마을 중심 판정**: Furnace 는 마을 POI 가 있기만 하면(ANY) 마을로 쳤다. 바닐라 `PoiManager.isVillageCenter` 는 `IS_OCCUPIED`
   (1.21.1 `ces.g(long)` → 람다 `a(ceu)` 가 `ces$b.b` = IS_OCCUPIED 로 getRecords — javap 확인). 주민이 차지하지 않은 종·침대·일터만 있는 곳에서
   나쁜 징조가 습격 징조로 바뀌던 것, 좀비 마을 걷기, 주민 마을 찾기, 습격 중심 이동이 달랐다. 봇 시험: 빈 종 하나 옆에서 나쁜 징조 6초 → 전 jar 는 습격 징조, 후 jar 는 없음.
   기존 시험 `PoiManagerTest.closeToVillageBySectionDistance`·`RaidTest.preRaidCountdownFillsBar` 가 빈 종을 마을로 기대하고 있어 종을 차지시킨 뒤 보도록 고쳤다.
2. **스폰 전하 바이옴**: `NaturalSpawner.createState` 는 바닐라에서 `getRoughBiome(pos, chunk)` = `chunk.getNoiseBiome(x>>2, y>>2, z>>2)`(높이는 월드 안으로 자름, `ddh.a(jd,duy)` javap 확인).
   Furnace 는 확대 지터 바이옴을 써서 바이옴 경계(영혼 모래 골짜기·뒤틀린 숲의 전하)에서 달랐다.

## 봇 시험(`tools/bot-test/entity/entity.js`)
| 항목 | 전 jar | 후 jar |
|---|---|---|
| A. 빈 종만 있는 곳 나쁜 징조 → 습격 징조 | 생김(바닐라와 다름) | 안 생김(바닐라) |
| B. 상자 속 좀비가 주민을 친다 | 20 → 17 | 20 → 17 |
| C. 1.5칸 울타리 끝을 돌아 플레이어에게 온다 | 동쪽 끝으로 돌아 1.54칸 | 동쪽 끝으로 돌아 0.58칸 |
| 소 600마리 `/tick query` 평균 | 6.2 / 7.9 / 6.8ms | 5.8 / 5.8 / 5.9ms |
| `round6/raid.js`(주민이 일터를 차지한 마을) | — | 습격 시작, 3웨이브 승리, 축하 폭죽 16 |

## 남은 후보(이번에 안 함)
- 밀기(`pushEntities`) 후보 검사 자체가 몹이 빽빽하면 10% 안팎 — 바닐라와 같은 알고리즘이라 구조 변경 없이는 더 줄이기 어렵다.
- 블록 충돌 모양 모으기(`collectCollisions`)·`hasLargeCollisionShape` 약 10%.
- `ChunkLevel.playerMoved` 가 이동 패킷마다 region 엔티티 전부를 다시 본다(바닐라 ChunkMap.move 도 같다). 엔티티·플레이어가 아주 많을 때만 의미.
- 별도 확인 필요(이번 변경과 무관, 전/후 jar 같음): 상자 속 좀비가 AI 없는 주민을 한 번 친 뒤 18초 넘게 더 치지 않았다(20 → 17 에서 멈춤).
  바닐라라면 계속 쳐야 한다 — 다음 점검 거리.
