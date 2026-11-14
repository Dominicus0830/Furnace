# RandomSource 씨앗 이음매 (T-O15-01, O05 C3)

2026-11-12. 근거: `furnace/reviews/opt-review/O15-tests-ci.md` 3.1절, O05 보고서 C3·P-O05-14.

## 바뀐 것
- `RandomSource.create()`(nanoTime ^ 상수 × 스레드 id) 를 없애고 `RandomSource.createFor(ownerKey, purpose)` 로 바꿨다. 운영 호출 지점 13곳이 모두 주인 열쇠를 넘긴다.
  | 자리 | 열쇠 | 용도 |
  |---|---|---|
  | `Entity.random` | 엔티티 id | ENTITY (UUID 도 이 난수에서 나온다) |
  | `Player.random` | 엔티티 id | PLAYER |
  | `ChunkLevel.random`·`soundRandom`·`randValue` | `RandomSeeds.world(차원, 추가 월드 이름)` | LEVEL·LEVEL_SOUND·LEVEL_RAND_VALUE |
  | `Raid.random` | 습격 id | RAID |
  | `DimensionSpawners.random`·방랑 상인 | `RandomSeeds.world(차원, 이름)` | DIMENSION_SPAWNERS·WANDERING_TRADER |
  | `Warden` 분노 관리 | 엔티티 id | WARDEN_ANGER |
  | `Sensor.RANDOM`(정적, 스레드 안전)·`ShufflingList.random`(목록마다) | 지점 이름 | SENSOR_SCAN·SHUFFLING_LIST |
  | `/random`·`/spreadplayers`·드래곤 수정 yaw | 지점 이름·월드·수정 id | COMMAND_RANDOM·SPREAD_PLAYERS·END_CRYSTAL_YAW |
  | `Explosion` 불 붙이기 난수(불 폭발일 때만 만든다, 2026-11-13) | 폭발 중심 좌표(x·y·z 비트를 `combine`) | EXPLOSION |
- **꺼짐(운영 기본)**: 씨앗은 바닐라 `RandomSupport.generateUniqueSeed` 와 같은 식(원자 uniquifier × 1181783497276652981 ^ nanoTime). 예측 불가이고 같은 시각에 만든 둘도 다르다 — O05 C3(거친 시계에서 같은 난수열 가능성)도 같이 닫힌다. 바닐라 관측 동작은 그대로(바닐라 씨앗도 예측 불가).
- **켜짐(`-Dfurnace.test.seeded=true`)**: 씨앗이 (열쇠, 용도)의 순수 함수(`RandomSeeds.derive`, Stafford13 믹스). 호출 순서 카운터를 쓰지 않으므로 난수 객체를 하나 덜 만드는 최적화가 다른 객체의 씨앗을 밀지 않는다. `static final` 이라 꺼짐이면 분기가 접힌다. Gradle 은 `furnace.*` 속성만 시험·벤치 JVM 에 넘기므로 운영 서버에 새지 않는다.

## 가정·한계
- 레벨 열쇠에 region 번호를 넣지 않았다(검토 R1): 번호는 region 생성·합치기 순번이라 구역 크기를 바꾸는 최적화가 씨앗을 밀기 때문이다. 대신 한 월드의 region 이 여럿이면 레벨 난수열이 같아진다(켜짐 전용, O15 3.1절 한계대로 장면은 region 하나). 엔티티 난수는 엔티티 id 만 쓴다.
- **같은 JVM 에서 같은 장면을 두 번 돌리면 켜짐이어도 달라질 수 있다**(검토 R2): 시험 레벨의 엔티티 id 는 정적 `ChunkLevel.FALLBACK_IDS`(JVM 전역), `Sensor.RANDOM` 도 JVM 에 하나다(`ShufflingList` 는 목록마다지만 열쇠가 상수라 켜짐에서는 모든 목록이 같은 난수열). 장면 해시(T-O15-02 `SceneHash`)는 장면마다 새 JVM 이나 자기 id 할당기를 가진 레벨을 써야 하거나 되감기 도우미를 따로 만든다 — 이 PR 은 하지 않았다.
- 켜짐에서 `/random`(순서 없음)·`/spreadplayers` 는 열쇠가 상수라 몇 번을 불러도 같은 값·배치다(R5). 시험 레벨(`DimensionTestLevel`)은 차원 이름을 열쇠로 넘긴다(R6). 열쇠를 안 넘기는 `ChunkLevel(int)` 는 0.
- 켜짐을 운영에서 실수로 주면 엔티티 id 가 부팅마다 0 부터라 UUID 가 겹칠 수 있다 — 서버 시작 때 경고 로그를 낸다(R7).
- `Sensor.RANDOM` 은 여러 region 스레드가 공유한다 — O06 C3(PR #274)에서 잠금 감싼 스레드 안전 난수(`createThreadSafeFor`)로 바꿨다. 단일 스레드 장면에서는 틱 순서에 따라 결정적이다.
- 아직 이음매 밖인 비결정 난수(장면 해시에 영향 가능): `Region.java` 의 `ThreadLocalRandom`(약 2246행), `LivingEntity` 의 `Math.random()`(밀치기 방향, 바닐라도 같음), `PlayerVitals.xpSeed`, `FurnaceBlockEntity` 의 `ThreadLocalRandom`, `Player` 의 `ThreadLocalRandom`(954행), 보스바·가상 엔티티의 `UUID.randomUUID()`, `WorldClock` 의 `new Random()`(날씨 길이), `ServerCore` 전리품 월드 난수 `ThreadLocalRandom`, `SpawnLocation` 자체 uniquifier, `SoundCommands` 소리 씨앗(패킷 바이트에 들어감), `PlayerHandle` 184행. 장면 해시(T-O15-02·04)가 필요로 하면 그때 같은 이음매로 옮긴다.
