# Paper 설정 키 조사와 Furnace 대응 (2026-10-27, 수정 2)

> 조사 문서다. 코드는 수정하지 않았다. 기준은 Paper 1.21.1 의 `paper-global.yml`, `paper-world-defaults.yml`(`world-settings.default`), `spigot.yml`, `bukkit.yml` 이다.
> 근거: 작성자의 기억과 레포 코드만 썼다. 웹 조회와 Paper 원문 대조는 하지 않았다. **Paper 쪽 키 이름과 기본값은 전부 미확인이다.** 구현 전에 Paper 1.21.1 원문으로 대조해야 한다.
> Furnace 쪽 근거는 `파일:줄` 로 적었다. 경로는 `furnace-server/src/main/java/org/domi/furnace/server/` 기준이다.
> 관련 문서: `docs/furnace-community-wishlist.md` 1.6절(설정 운영 불만 34~36).

## 0. 읽는 법

| 표기 | 의미 |
|---|---|
| **기억** | 작성자 기억이다. 자주 본 값이지만 이번에 원문을 확인하지 않았다 |
| **가정** | 추측이다. 키 이름이나 기본값을 확인하지 못했다 |
| **UNVERIFIED** | 기억이 서로 어긋나거나 Paper 키가 아닐 수도 있는 항목이다. 삭제하거나 검증하기 전까지 사실로 쓰지 않는다 |
| **바닐라와 다름** | 키를 바꾸면 바닐라 1.21.1 과 관측 결과가 달라진다. `예 / 아니오 / 가정` |
| **Furnace 현황** | `있음`(같은 이름·의미, 코드 근거 있음) / `구현됨(키 없음)`(동작은 있으나 설정 키가 없음) / `부분`(이름이나 의미만 비슷함) / `없음` |
| **우선순위** | 아래 정의를 따른다 |

**우선순위 정의 [제안]** (이 문서 전체에서 같은 뜻으로 쓴다)
- **완료**: Furnace 에 이미 구현되어 있다. 근거 파일:줄을 적는다.
- **상**: 기본 동작이 바닐라 값과 같아야 한다. 구현이 있어도 값을 바닐라 원문과 대조해 검증해야 하는 항목이다.
- **중**: 기본값에서는 바닐라 동작을 바꾸지 않고, 옵션을 켜야만 동작이 바뀌는 기능이다. 성능 튜닝은 `experiment.*` 실험 플래그 뒤에 두고, 보안·진단 옵션은 기본 꺼짐으로 둔다.
- **하**: 구현하지 않는다(비목표). 바닐라 취약점 허용, 바닐라와 다른 게임플레이, 레거시 호환 등이다.

**원칙 (AGENTS.md 절대 규칙 2·3)**: Paper 키는 참고용이다. 바닐라 기본값과 다른 값을 기본으로 켜지 않는다.

---

## 1. `paper-global.yml` (서버 전역)

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 |
|---|---|---|---|---|---|
| `proxies.velocity.enabled` | Velocity 모던 포워딩 사용 | false **기억** | 아니오(프록시) | 부분: `velocity-secret` 이 비어 있지 않으면 켜짐(`Config.java` 주석) | 완료 |
| `proxies.velocity.secret` | 포워딩 비밀키 | 빈 값 **기억** | 아니오 | 있음: `velocity-secret` (`config/Config.java`) | 완료 |
| `proxies.bungee-cord.online-mode` | BungeeCord 레거시 포워딩 | false **기억** | 아니오 | 없음 | 하 (Velocity 만 지원) |
| `proxies.proxy-protocol` | PROXY 프로토콜 수신 | false **가정** | 아니오 | 없음 | 하 |
| `unsupported-settings.allow-piston-duplication` | 피스톤 복제 익스플로잇 허용 | false **기억** | **예** | 없음 | 하 |
| `unsupported-settings.allow-permanent-block-break-exploits` | 영구 블록 파괴 익스플로잇 허용 | false **기억** | **예** | 없음 | 하 |
| `unsupported-settings.allow-headless-pistons` | 헤드리스 피스톤 허용 | false **기억** | **예** | 없음 | 하 |
| `unsupported-settings.allow-unsafe-end-portal-teleportation` | 엔드 포탈 안전하지 않은 텔레포트 | false **기억** | **예** | 없음(엔드 미구현) | 하 |
| `unsupported-settings.*` 나머지 | 익스플로잇 허용 스위치 묶음 | 대부분 false **가정** | **예** | 없음 | 하 (키 목록만 보관) |
| `unsupported-settings.perform-username-validation` | 플레이어 이름 검증 | true **가정** | 가정 | 없음 | 중 (바닐라 검증 규칙과 대조 필요) |
| `watchdog.early-warning-every` / `early-warning-delay` | 틱 지연 경고 주기·지연 | 5000 / 10000 ms **가정** | 아니오(진단) | 부분: `max-tick-time`(`ServerProperties.java`, 바닐라 키)만 있음 | 중 (진단 옵션, 기본 꺼짐) |
| `logging.deobfuscate-stacktraces` | 스택트레이스 난독화 해제 | true **가정** | 아니오 | 없음 | 하 |
| `logging.log-player-ip-addresses` | 접속 IP 기록 여부 | true **기억** | 아니오 | 있음: `log-ips` (`ServerProperties.java:89`, 가리기는 `ConnectionHandler.java:655-657`) | 완료 |
| `chunk-system.io-threads` | 청크 입출력 스레드 수 | -1(자동) **기억** | 아니오 | 부분: `io-threads`(`furnace.properties`, 4 고정, 아래 5.1·8장) | 상 (기본값 자동 여부 검증) |
| `chunk-system.worker-threads` | 청크 생성 작업 스레드 수 | -1(자동) **기억** | 아니오 | 부분: `region-threads` 는 region 스케줄러용이라 의미가 다름 | 하 (Furnace 는 region 모델) |
| `misc.region-file-cache-size` | region 파일 핸들 캐시 크기 | 256 **가정** | 아니오 | 없음 | 하 |
| `misc.fix-entity-position-desync` | 엔티티 위치 어긋남 보정 | true **가정** | 가정(패킷 차이) | 없음 | 중 (패킷 보정 옵션) |
| `packet-limiter.all.max-packet-rate` / `interval` | 패킷 속도 제한과 킥 | 500.0 / 7.0 **가정** | 예(접속 차단) | 부분: `rate-limit`(`ServerProperties.java:58`, 바닐라 키)는 패킷 수 제한. 킥 정책은 없음 | 중 (보안 옵션, 기본 꺼짐) |
| `scoreboards.allow-non-player-entities-on-scoreboards` | 엔티티를 팀·스코어보드에 허용 | false **가정** | **예**(바닐라 금지 해제) | 없음 | 하 |
| `item-validation.*` | 아이템 NBT·컴포넌트 크기 검증 | 설정별 **가정** | 가정 | 없음 | 중 (검증 한도가 바닐라와 같아야 함) |

---

## 2. `paper-world-defaults.yml` (`world-settings.default` 아래, 월드별 재정의 가능)

### 2.1 스폰·디스폰·엔티티

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 |
|---|---|---|---|---|---|
| `entities.spawning.spawn-limits.monsters` | 플레이어 주변 몬스터 상한 | 70 **기억** | 아니오 | 구현됨(키 없음): `MobCategory.java:6` `MONSTER(...,70,...)` | 상 (바닐라 값 검증) |
| `entities.spawning.spawn-limits.animals` | 동물 상한 | 10 **기억** | 아니오 | 구현됨(키 없음): `MobCategory.java:7` `CREATURE`=10 | 상 (검증) |
| `entities.spawning.spawn-limits.water-animals` | 수중 동물 상한 | 5 **기억** | 아니오 | 구현됨(키 없음): `MobCategory.java:11` `WATER_CREATURE`=5 | 상 (검증) |
| `entities.spawning.spawn-limits.ambient` | 주변 생물 상한 | 15 **기억** | 아니오 | 구현됨(키 없음): `MobCategory.java:8` `AMBIENT`=15 | 상 (검증) |
| `entities.spawning.spawn-limits.axolotls` / `water-ambient` / `underground-water-creature` | 세부 카테고리 상한 | 5 / 20 / 5 **기억** | 아니오 | 구현됨(키 없음): `MobCategory.java:9,10,12` | 상 (검증) |
| `entities.spawning.per-player-mob-spawns` | 상한을 플레이어별로 계산 | true **기억** | 가정 | 가정: 스폰 로직(`NaturalSpawner` 계열)에서 확인 필요 | 상 (검증) |
| `entities.spawning.despawn-ranges.<카테고리>.soft` / `hard` | 디스폰 거리 | 몬스터 32 / 128 **기억** | 아니오 | 구현됨(키 없음): `MobCategory.java` 의 `despawnDistance` 128 / 64(물 주변 생물), `NO_DESPAWN_DISTANCE=32`(`MobCategory.java:14`), `entity/spawn/NaturalSpawner.java:281`(`mob.type()` 기준 far 거리) | 상 (검증) |
| `entities.spawning.alt-item-despawn-rate` | 아이템 디스폰 시간 수정 | 끔, 6000틱 **기억** | 아니오(기본 꺼짐) | 없음(동작 자체는 바닐라 6000틱 가정) | 중 (옵션) |
| `entities.spawning.non-player-arrow-despawn-rate` / `creative-arrow-despawn-rate` | 화살 디스폰 시간 | -1(바닐라 기본) **가정** | 아니오 | 없음 | 상 (바닐라 1200틱 검증) |
| `entities.spawning.ticks-per`(bukkit `ticks-per` 와 같은 역할) | 카테고리별 스폰 시도 간격 | 바닐라 값 **가정** | 아니오 | 가정: 구현 여부 미확인 | 상 (검증) |
| `entities.spawning.mob-spawner-tick-rate` | 몬스터 스포너 틱 간격 | 1 **가정** | 아니오 | 없음 | 중 |
| `entities.behavior.nerf-spawner-mobs` | 스포너 몹 약화 | false **기억** | 예(켜면) | 없음 | 하 |
| `entities.behavior.disable-player-crits` | 치명타 끔 | false **가정** | 예(켜면) | 없음 | 하 |
| `entities.behavior.villagers-*` / `disable-chest-cat-detection` | 주민·고양이 행동 조정 | 바닐라 값 **가정** | 예(켜면) | 없음 | 하 |
| `entities.armor-stands.do-collision-entity-lookups` | 아머 스탠드 충돌 조회 | false **가정** | 예 | 없음 | 하 |
| `collisions.max-entity-collisions` | 한 엔티티와 충돌 검사할 상한 | 8 **UNVERIFIED** | 가정 | 바닐라는 상한 대신 `maxEntityCramming` 게임룰(기본 24, `world/GameRules.java:40`, `world/storage/LevelData.java:269`)을 쓴다. Paper 키와 바닐라 게임룰은 역할이 다르다 | 하 (Paper 키 자체가 맞는지 먼저 확인) |
| `entities.entity-tracking-range.players` / `animals` / `monsters` / `misc` / `display` / `other` | 종류별 엔티티 전송 거리(블록) | 48 / 48 / 48 / 32 / 128 / 64 **가정**(display 는 강하게 불확실) | **예**(바닐라 아님) | 부분: `entity-view-distance`(`furnace.properties`, 단일 정수). 바닐라 공식은 `entity-broadcast-range-percentage`(`ServerProperties.java:80`, 있음) | 중 (실험 플래그, 기본은 바닐라 공식) |
| `dab` (활성화 거리 기반 AI 틱 감속) | 멀리 있는 몹 AI 틱 빈도 | 기본값 **UNVERIFIED** | **예** | 없음 | 하 (키 소속 자체가 Paper 인지 확인 필요, Pufferfish 계열로 기억) |
| `tick-rates.*` (센서·행동 틱 간격) | 주민 POI 탐색 등 간격 | 대부분 1, 일부 40 **가정** | **예** | 없음 | 하 |
| `max-tnt-per-tick` / `max-tnt-merges` | TNT 폭발 상한 | 100 **가정** | **예**(상한) | 없음 | 하 |
| `optimize-explosions` | 폭발 계산 최적화 | false **가정** | 가정(결과 동일 여부 미확인) | 없음 | 중 (결과가 바닐라와 같은지 먼저 검증) |
| `environment.disable-explosion-knockback` | 폭발 넉백 끔 | false **가정** | 예(켜면) | 없음 | 하 |
| `environment.disable-thunder` / `disable-ice-and-snow` | 번개·얼음눈 끔 | false **가정** | 예(켜면) | 없음 | 하 |
| `environment.nether-ceiling-void-damage` | 네더 천장 피해 | 바닐라 값 **가정** | 가정 | 없음(네더 미구현) | 하 |

### 2.2 청크·저장·전송

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 |
|---|---|---|---|---|---|
| `chunks.auto-save-interval` | 자동 저장 간격 | `default`(바닐라 6000틱) **기억** | 아니오 | 있음(이름 다름): `autosave-ticks`, `Config.java:40` 기본 6000 | 완료 |
| `chunks.max-auto-save-chunks-per-tick` | 틱당 자동 저장 청크 수 | 24 **가정** | 아니오(저장 부하) | 없음. Furnace `chunks-per-tick` 과는 **다른 기능**이다(아래 메모) | 중 (성능 옵션) |
| `chunks.delay-chunk-unloads-by` | 청크 언로드 지연 | 10s **기억** | 예(언로드 타이밍) | 부분(같은 목적, 단위 다름): `chunk-eviction-grace-ticks`, `Config.java:40` 기본 **100틱(5초)** | 중 |
| `chunks.entity-per-chunk-save-limit` | 청크당 저장 엔티티 상한 | 설정별 **가정** | 예(상한이면) | 없음 | 하 |
| `chunks.prevent-moving-into-unloaded-chunks` | 미로드 청크로 이동 차단 | false **가정** | 예(켜면) | 없음 | 중 |
| `feature-seeds.generate-random-seeds-for-all` 및 `seed-*` | 구조물 시드 재정의 | false **가정** | **예**(true 면) | 없음 | 하 (재정의 비목표, 바닐라 시드 체계 유지) |
| `max-growth-height.*` | 작물 최대 성장 높이 | 바닐라 값 **가정** | 예(변경 시) | 없음 | 하 |
| `anti-xray.enabled` / `engine-mode` / `hidden-blocks` / `replacement-blocks` | 광석 은닉 | false **기억** | **예**(클라이언트가 받는 블록이 다름) | 없음 | 하 (관측 parity 와 충돌) |

**메모 (`chunks-per-tick` 은 Paper 대응 키가 없다)**: `chunks-per-tick` 은 플레이어당 틱마다 보내는 청크 패킷 수 상한이다. `world/World.java:55` 주석 `플레이어당 틱 전송 상한`, `world/Region.java:1458` 의 `Math.min(world.chunksPerTick(), world.maxChunkSendsInFlight() - p.chunkSendsInFlight().get())` 가 근거다. 기본값은 `Config.java:40` 의 16이다. Paper `max-auto-save-chunks-per-tick` 은 **저장** 속도 키라서 대응시키지 않는다. Paper 쪽 대응 키는 **없음(청크 전송 속도)** 이다.

### 2.3 호퍼·정밀 틱

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 |
|---|---|---|---|---|---|
| `hopper.cooldown-when-full` | 가득 찬 호퍼 쿨다운 | true **가정** | 가정(false 면 예) | 없음 | 중 (바닐라 동작 대조 필요) |
| `hopper.disable-move-event` | 호퍼 이동 이벤트 생략 | false **가정** | 아니오(성능) | 없음 | 하 |
| `hopper.ignore-occluding-blocks` | 막힌 블록 무시 | false **가정** | 예(켜면) | 없음 | 하 |
| `misc.max-joins-per-tick` 등 접속 상한 | 틱당 접속 처리 수 | 설정별 **가정** | 아니오 | 없음 | 중 (보안·진단 옵션, 기본 꺼짐) |

---

## 3. `spigot.yml`

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 |
|---|---|---|---|---|---|
| `settings.bungeecord` | BungeeCord 호환 | false **기억** | 아니오 | 없음(Velocity 만) | 하 |
| `settings.attribute.maxHealth.max` / `movementSpeed.max` | 속성 상한 | **UNVERIFIED** (movementSpeed 상한 1024 또는 2048 중 무엇인지 확인 필요) | **예**(바닐라 상한과 다를 수 있음) | 없음 | 하 |
| `settings.player-shuffle` | 플레이어 틱 순서 섞기 | 0 **가정** | **예**(순서가 바뀜) | 없음 | 하 (결정성 깨짐) |
| `settings.netty-threads` | Netty 스레드 수 | 4 **가정** | 아니오 | 부분(의미 다름): `io-threads` 는 청크 I/O | 하 |
| `settings.timeout-time` | 클라이언트 keep-alive 타임아웃 | 60 **가정** | 예(바닐라와 대조 필요) | 없음 | 중 |
| `settings.moved-wrongly-threshold` | 이동 오류 판정 임계 | 0.0625 **가정** | 아니오(진단) | 없음 | 하 |
| `settings.sample-count` | 틱 샘플 수(TPS 계산) | 12 **가정** | 아니오 | 없음 | 하 |
| `settings.restart-on-crash` | 충돌 시 재시작 스크립트 | false **가정** | 아니오 | 없음 | 하 |
| `world-settings.default.entity-activation-range.*` | 엔티티 활성화 거리(AI 감속) | 동물 32 등 **UNVERIFIED** | **예** | 없음 | 하 |
| `world-settings.default.entity-tracking-range.*` | 추적 거리(spigot 판) | 48 / 32 / 64 **기억** | **예** | 없음 | 중 (paper-world-defaults 와 중복, 한쪽만 구현) |
| `world-settings.default.item-despawn-rate` | 아이템 디스폰 틱 | 6000 **기억** | 아니오 | 구현 확인 필요(바닐라 6000틱 기준) | 상 (검증) |
| `world-settings.default.arrow-despawn-rate` | 화살 디스폰 틱 | 1200 **기억** | 아니오 | 구현 확인 필요 | 상 (검증) |
| `world-settings.default.merge-radius.item` / `exp` | 아이템·경험치 병합 반경 | **UNVERIFIED**(2.5 / 3.0 은 옛 값으로 기억, 최근 값은 item 0.5, exp -1 로 기억. 어느 쪽인지 확인 필요) | **예**(병합 결과가 달라짐) | 없음 | 중 |
| `world-settings.default.growth.*-modifier` | 작물 성장 확률 배율 | 100(=바닐라) **기억** | 아니오(기본값) | 없음 | 중 (변경 시 parity 깨짐) |
| `world-settings.default.nerf-spawner-mobs` | 스포너 몹 약화 | false **기억** | 아니오(기본) | 없음 | 하 |
| `world-settings.default.hanging-tick-frequency` | 걸린 엔티티 틱 간격 | 100 **가정** | **예** | 없음 | 하 |
| `world-settings.default.tick-inactive-villagers` | 비활성 주민 틱 | true **가정** | 아니오 | 없음 | 하 |
| `world-settings.default.mob-spawn-range` | 스폰 범위 | **UNVERIFIED**(4 로 적었으나 8 으로 기억) | 가정 | 없음 | 중 |
| `world-settings.default.max-entity-collisions` | 충돌 상한(spigot 판) | **UNVERIFIED**(2 로 적었으나 Spigot 기본 8 로 기억) | 가정 | 바닐라 대응은 `maxEntityCramming` 게임룰(`world/GameRules.java:40`) | 하 |
| `commands.*` (탭 완성, 스팸 제외 등) | 명령 편의 | 설정별 **가정** | 아니오 | 없음 | 하 |
| `players.disable-saving` / `stats.disable-saving` / `advancements.disable-saving` | 저장 끄기 | false **기억** | 예(켜면 저장 안 함) | 없음 | 하 |

---

## 4. `bukkit.yml`

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 |
|---|---|---|---|---|---|
| `spawn-limits.monsters` / `animals` / `water-animals` / `ambient` / `water-ambient` / `water-underground-creature` | 스폰 상한(bukkit 판) | 70 / 10 / 5 / 15 / 20 / 5 **기억** | 아니오 | 구현됨(키 없음): `MobCategory.java` 상수와 같은 값 | 상 (paper-world-defaults 가 우선. 한쪽만 구현) |
| `ticks-per.animal-spawns` | 동물 스폰 시도 간격(틱) | 400 **기억** | 아니오(바닐라 주기와 같은지 검증) | 가정: 미확인 | 상 (검증) |
| `ticks-per.monster-spawns` / `water-spawns` / `water-ambient-spawns` / `water-underground-creature-spawns` / `ambient-spawns` | 스폰 시도 간격 | 1 **기억** | 아니오(검증) | 가정: 미확인 | 상 (검증) |
| `chunk-gc.period-in-ticks` / `load-threshold` | 청크 언로드 GC 주기·기준 | 600 / 0 **기억** | **예**(언로드 타이밍) | 없음 | 중 |
| `settings.allow-end` | 엔드 허용 | true **기억** | 아니오 | 없음(엔드 미구현) | 하 |
| `settings.connection-throttle` | 같은 IP 빠른 재접속 차단(ms) | 4000 **기억** | 예(접속 차단) | 없음 | 중 (보안 옵션, 기본 꺼짐) |
| `settings.warn-on-overload` | 과부하 경고 | true **기억** | 아니오 | 없음 | 하 |
| `settings.query-plugins` | 쿼리에 플러그인 목록 | true **기억** | 아니오 | 없음 | 하 |
| `settings.minimum-api` / `deprecated-verbose` / `use-map-color-cache` | 플러그인·API 호환 | 설정별 **가정** | 아니오 | 없음 | 하 |
| `aliases` (`now-in-commands.yml`) | 명령 별칭 파일 | 파일 지정 **기억** | 아니오 | 없음 | 하 |

---

## 5. Furnace 현재 설정 구조

설정은 두 파일로 나뉜다. 코드는 `furnace-server/src/main/java/org/domi/furnace/server/config/` 에 있다.

| 파일 | 클래스 | 역할 |
|---|---|---|
| `furnace.properties` | `Config.java`(record), `ConfigLoader.java` | Furnace 고유 런타임 값. 없으면 `Config.DEFAULT`. 잘못된 값은 기동 시 `IllegalArgumentException` |
| `server.properties` | `ServerProperties.java`, `VanillaSettings.java`, `ServerPropertiesFile.java` | 바닐라 `DedicatedServerProperties` 이식. 키·기본값·정규화가 1.21.1 과 같다고 주석에 적혀 있다 |
| (파일 공통) | `VanillaSettings.java` | UTF-8 로 읽고 실패 시 ISO-8859-1 재시도. 읽은 키는 정규화해서 다시 넣고, 모르는 키는 보존 |
| (런타임 변경) | `ServerPropertiesFile.java` | `white-list`, `player-idle-timeout` 처럼 바뀌는 값을 `update` 하면 파일을 다시 쓴다 |

`Config.VANILLA_KEYS` 는 `server-name, port, compression-threshold, view-distance, level-name, level-seed` 6개다. `furnace.properties` 에 있어도 `server.properties` 가 우선한다(`Config.withServerProperties`). 기동 시 무시되는 키는 `ConfigLoader.ignoredVanillaKeys` 가 경고용으로 돌려준다.

### 5.1 `furnace.properties` (Furnace 고유, 기본값은 `Config.java:40`)

| 키 | 의미 | 기본값 | 비고 |
|---|---|---|---|
| `server-name` | 서버 이름 | `Furnace Server` | `server.properties` 의 `motd` 가 덮어씀 |
| `tick-nanos` | 틱 길이(ns) | 50,000,000 | Paper 에 대응 키 없음 |
| `port` | 포트 | 25565 | `server-port` 가 우선 |
| `compression-threshold` | 압축 임계값 | 256 | `network-compression-threshold` 가 우선 |
| `view-distance` | 청크 전송 반경 | 5 | 2~32 로 조정(`ConfigLoader`) |
| `entity-view-distance` | 엔티티 가시 반경 | 8 | Paper `entity-tracking-range` 와 의미가 다름 |
| `region-section-shift` | region 구역 크기 지수(2^값 청크) | 4 | 1~8 |
| `region-merge-radius` | region 합치기 반경(구역 단위) | 1 | 1 이상 |
| `region-threads` | region 작업 스레드 수 | CPU 코어 수 | Paper `chunk-system.worker-threads` 와 의미 다름 |
| `level-name` | 월드 이름 | `world` | `server.properties` 가 우선 |
| `level-seed` | 시드(기본 0) | 0 | `server.properties` 가 우선 |
| `io-threads` | 청크 I/O 스레드 수 | **4 (고정)** | Paper `chunk-system.io-threads` 는 자동(-1 기억). 8장 참고 |
| `autosave-ticks` | 자동 저장 간격(틱) | 6000 | Paper `chunks.auto-save-interval` 과 같은 목적 |
| `chunk-eviction-grace-ticks` | 청크 제거 유예(틱) | **100 (5초)** | Paper `chunks.delay-chunk-unloads-by` 는 10초로 기억(미확인) |
| `spawn-chunk-radius` | 스폰 청크 반경 | 2 | Furnace 고유(Paper 키 아님) |
| `chunks-per-tick` | 플레이어당 틱 청크 전송 상한 | 16 | 2.2절 메모 참고. Paper 대응 키 없음 |
| `velocity-secret` | Velocity 포워딩 비밀키 | 빈 값(끔) | Paper `proxies.velocity.secret` 과 같은 의미 |

### 5.2 `server.properties` (바닐라 키, Furnace 가 읽음)

`ServerProperties.java` 에 있는 키다. 바닐라 키라서 Paper 와 같은 이름이면 같은 의미로 본다.

`online-mode`, `prevent-proxy-connections`, `server-ip`, `spawn-animals`, `spawn-npcs`, `pvp`, `allow-flight`, `motd`, `bug-report-link`, `force-gamemode`, `enforce-whitelist`, `difficulty`, `gamemode`, `level-name`, `server-port`, `announce-player-achievements`, `enable-query`, `query.port`, `enable-rcon`, `rcon.port`, `rcon.password`, `hardcore`, `allow-nether`, `spawn-monsters`, `use-native-transport`, `enable-command-block`, `spawn-protection`, `op-permission-level`, `function-permission-level`, `max-tick-time`, `max-chained-neighbor-updates`, `rate-limit`, `view-distance`, `simulation-distance`, `max-players`, `network-compression-threshold`, `broadcast-rcon-to-ops`, `broadcast-console-to-ops`, `max-world-size`, `sync-chunk-writes`, `region-file-compression`, `enable-jmx-monitoring`, `enable-status`, `hide-online-players`, `entity-broadcast-range-percentage`, `text-filtering-config`, `resource-pack` 계열 6개, `initial-enabled-packs`, `initial-disabled-packs`, `player-idle-timeout`, `white-list`, `enforce-secure-profile`, `log-ips`, `level-seed`, `generate-structures`, `generator-settings`, `level-type`, `accepts-transfers`.

---

## 6. 대조 요약

| Paper/bukkit/spigot 키 | Furnace 키 / 동작 | 현황 | 근거 |
|---|---|---|---|
| `proxies.velocity.secret` | `velocity-secret` | **있음** | `Config.java` |
| `logging.log-player-ip-addresses` | `log-ips` | **있음** | `ServerProperties.java:89`, `ConnectionHandler.java:655-657`(false 면 주소 가림, 바닐라 `getLoggableAddress` 와 같은 역할) |
| `view-distance` | `view-distance` | **있음** | 바닐라 키 |
| `spawn-animals` / `spawn-monsters` / `spawn-npcs` | 같은 이름 | **있음**(토글) | 바닐라 키. 상한 값은 아래 `spawn-limits` 행 |
| `max-tick-time` | `max-tick-time` | **있음** | 바닐라 키 |
| `rate-limit` | `rate-limit` | **있음**(부분) | 바닐라 키. Paper `packet-limiter` 의 킥 정책은 없음 |
| `allow-nether` | `allow-nether` | **있음** | 바닐라 키(네더 미구현) |
| `chunks.auto-save-interval` | `autosave-ticks` | **있음** | `Config.java:40`, 기본 6000 |
| `chunks.delay-chunk-unloads-by` | `chunk-eviction-grace-ticks` | **부분** | 같은 목적. 기본 100틱(5초) 대 Paper 10초(기억) |
| `chunk-system.io-threads` | `io-threads` | **부분** | `FurnaceMain.java:185` `newFixedThreadPool(config.ioThreads())`, 기본 4 고정. Paper 기본은 자동(-1, 기억) |
| `chunk-system.worker-threads` | `region-threads` | **부분**(의미 다름) | Furnace 는 region 스케줄러 |
| `entity-tracking-range.*` | `entity-view-distance` + `entity-broadcast-range-percentage` | **부분** | 종류별 거리는 없다. 바닐라 공식은 percentage 쪽 |
| `max-auto-save-chunks-per-tick` | 대응 없음 | **없음** | 저장 속도 키. `chunks-per-tick` 은 전송 속도(2.2절) |
| 스폰 상한 `spawn-limits.*` | `MobCategory.java` 상수 | **구현됨(키 없음)** | `MobCategory.java:6-13` |
| 디스폰 거리 | `MobCategory.java` `despawnDistance`, `entity/spawn/NaturalSpawner.java:281` | **구현됨(키 없음)** | `MobCategory.java` |
| `ticks-per.*`, 아이템·화살 디스폰 틱 | 확인 못 함 | **가정** | 구현 위치 미확인 |
| `unsupported-settings.*` | 없음 | **없음**(비목표) | |

**결론 (스폰·디스폰)**: 스폰 상한과 디스폰 거리는 이미 상수로 구현되어 있다(`MobCategory.java:6-13`). 남은 일은 두 가지다. (1) 상수 값이 바닐라 원문과 같은지 검증하는 일, (2) Paper 식 설정 키로 노출할지 결정하는 일. 키 노출은 기본값이 바닐라와 같다는 조건에서만 한다. 따라서 이 항목은 "구현이 먼저"가 아니라 "검증이 먼저"다.

---

## 7. 구현 우선순위 제안 [제안]

1. **상 (검증 우선)**: 스폰 상한·디스폰 거리 상수의 바닐라 값 검증(이미 구현), 화살·아이템 디스폰 틱, `ticks-per` 의 바닐라 주기 확인, 자동 저장 기본값 확인. 설정 키를 노출하기 전에 바닐라 값을 기본으로 맞춘다.
2. **중 (기본 꺼짐 옵션)**: 기본값에서 바닐라 동작을 바꾸지 않고, 켰을 때만 바뀌는 기능이다. 성능 튜닝은 `experiment.*` 실험 플래그 뒤에 두고 바닐라 결과와 대조한 뒤에만 켠다. 보안·진단(패킷 제한, 접속 제한, 틱 경고, 틱당 접속 상한)은 기본 꺼짐 옵션으로 둔다. 종류별 엔티티 추적 거리도 여기에 둔다(바닐라 공식과 다르므로 실험 플래그).
3. **하 (비목표)**: `unsupported-settings.*`, 스코어보드 금지 해제, 속성 상한 상향, 플레이어 순서 섞기, 광석 은닉, 스포너 몹 약화, BungeeCord 레거시, Bukkit 명령 별칭 파일, 저장 끄기 옵션, `dab` 와 `tick-rates` 같은 AI 틱 감속.

---

## 8. 확인 필요 목록

### 8.1 UNVERIFIED (사실로 쓰지 않는다. 검증 전까지 구현 근거로 쓰지 않는다)
- `async-pathfinding.*`: Paper 1.21.1 `paper-global.yml` 에 있는지 **확인하지 못했다.** 포크 기능과 혼동했을 가능성이 있다. 표에서 뺐다.
- `dab`: 키가 Paper 에 있는지 불확실하다(Pufferfish 계열로 기억). 기본값도 불확실하다.
- spigot `world-settings.default.max-entity-collisions`: 2 로 적었던 값은 틀렸을 수 있다. Spigot 기본은 8 로 기억한다.
- spigot `mob-spawn-range`: 4 로 적었던 값은 8 로 기억한다.
- spigot `attribute.movementSpeed.max`: 1024 또는 2048 중 무엇인지 확인 못 했다.
- spigot `merge-radius`: 2.5 / 3.0 은 옛 값으로 기억한다. 최근 값으로 item 0.5, exp -1 을 기억하지만 확인 못 했다.
- Paper `collisions.max-entity-collisions`: "바닐라는 상한이 없다"는 옛 표기는 틀렸다. 바닐라에는 `maxEntityCramming` 게임룰(기본 24, `world/GameRules.java:40`)이 있다. Paper 키 자체가 맞는지 먼저 확인해야 한다.
- `entity-activation-range` 기본값(동물 32 등), `entities.entity-tracking-range.display`(128) 기본값.

### 8.2 Furnace 쪽 (확정, 근거 있음)
- `log-ips`: `ServerProperties.java:89` 에서 읽고, `ConnectionHandler.java:655-657` 에서 false 면 주소를 가린다. Paper `logging.log-player-ip-addresses` 와 같은 의미로 **있음**.
- `io-threads`: `FurnaceMain.java:185` 에서 `newFixedThreadPool(config.ioThreads())`, 기본값 4 는 `Config.java:40`. 자동 스레드 수가 아니고 고정이다. Paper 의 자동 기본(-1)과 다르다.
- `chunks-per-tick`: 플레이어당 틱 청크 전송 상한. `World.java:55` 주석, `Region.java:1458` 에서 사용, 기본 16(`Config.java:40`). Paper `max-auto-save-chunks-per-tick` 과는 다른 기능이다.
- `chunk-eviction-grace-ticks`: Furnace 기본 100틱(5초, `Config.java:40`). Paper 는 10초로 기억(미확인). 바닐라와 다르다는 점은 Furnace 기본값이 이미 다르다는 것과, Paper 기본과도 다르다는 것 두 가지다.

### 8.3 Paper 쪽 원문 대조 (여전히 필요)
- 2장과 4장의 Paper 키 이름과 기본값 전부. 특히 `spawn-limits` 카테고리 이름, `ticks-per` 기본, `per-player-mob-spawns` 기본은 Paper 1.21.1 원문으로 확인한다.
- 이 문서는 키 목록을 옮긴 것이다. 동작 동등성을 증명하지 않는다. 동작은 바닐라 서버 jar·oracle 로 확인해야 한다(AGENTS.md 절대 규칙 2).
