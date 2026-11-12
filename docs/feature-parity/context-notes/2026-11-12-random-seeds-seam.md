# RandomSource 씨앗 이음매 (T-O15-01, O05 C3)

2026-11-12. 근거: `furnace/reviews/opt-review/O15-tests-ci.md` 3.1절, O05 보고서 C3·P-O05-14.

## 바뀐 것
- `RandomSource.create()`(nanoTime ^ 상수 × 스레드 id) 를 없애고 `RandomSource.createFor(ownerKey, purpose)` 로 바꿨다. 운영 호출 지점 13곳이 모두 주인 열쇠를 넘긴다.
  | 자리 | 열쇠 | 용도 |
  |---|---|---|
  | `Entity.random` | 엔티티 id | ENTITY (UUID 도 이 난수에서 나온다) |
  | `Player.random` | 엔티티 id | PLAYER |
  | `ChunkLevel.random`·`soundRandom`·`randValue` | `RandomSeeds.region(차원, 추가 월드 이름, region id)` | LEVEL·LEVEL_SOUND·LEVEL_RAND_VALUE |
  | `Raid.random` | 습격 id | RAID |
  | `DimensionSpawners.random`·방랑 상인 | `RandomSeeds.region(차원, 이름, 0)` | DIMENSION_SPAWNERS·WANDERING_TRADER |
  | `Warden` 분노 관리 | 엔티티 id | WARDEN_ANGER |
  | `Sensor.RANDOM`·`ShufflingList.RANDOM`(정적) | 지점 이름 | SENSOR_SCAN·SHUFFLING_LIST |
  | `/random`·`/spreadplayers`·드래곤 수정 yaw | 지점 이름·월드·수정 id | COMMAND_RANDOM·SPREAD_PLAYERS·END_CRYSTAL_YAW |
- **꺼짐(운영 기본)**: 씨앗은 바닐라 `RandomSupport.generateUniqueSeed` 와 같은 식(원자 uniquifier × 1181783497276652981 ^ nanoTime). 예측 불가이고 같은 시각에 만든 둘도 다르다 — O05 C3(거친 시계에서 같은 난수열 가능성)도 같이 닫힌다. 바닐라 관측 동작은 그대로(바닐라 씨앗도 예측 불가).
- **켜짐(`-Dfurnace.test.seeded=true`)**: 씨앗이 (열쇠, 용도)의 순수 함수(`RandomSeeds.derive`, Stafford13 믹스). 호출 순서 카운터를 쓰지 않으므로 난수 객체를 하나 덜 만드는 최적화가 다른 객체의 씨앗을 밀지 않는다. `static final` 이라 꺼짐이면 분기가 접힌다. Gradle 은 `furnace.*` 속성만 시험·벤치 JVM 에 넘기므로 운영 서버에 새지 않는다.

## 가정·한계
- region id 는 한 월드 안에서 순서대로 매겨지므로 켜짐의 레벨 난수 안정성은 **region 하나짜리 장면**에서만 보장된다(O15 3.1절 한계와 같다). 엔티티 난수는 엔티티 id 만 쓰므로 id 가 같게 배정되는 장면이면 region 과 무관하게 같다.
- 정적 난수(Sensor·ShufflingList)는 여러 region 스레드가 공유한다(스레드 안전하지 않은 점은 이 PR 이 건드리지 않았다 — O06 C3). 단일 스레드 장면에서는 틱 순서에 따라 결정적이다.
- 아직 이음매 밖인 비결정 난수(장면 해시에 영향 가능): `Region.java` 의 `ThreadLocalRandom`(약 2246행), `LivingEntity` 의 `Math.random()`(밀치기 방향, 바닐라도 같음), `PlayerVitals.xpSeed`, `FurnaceBlockEntity` 의 `ThreadLocalRandom`, `Player` 의 `ThreadLocalRandom`(954행), 보스바·가상 엔티티의 `UUID.randomUUID()`. 장면 해시(T-O15-02·04)가 필요로 하면 그때 같은 이음매로 옮긴다.
