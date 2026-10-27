# Paper 설정 키 조사와 Furnace 대응 (2026-10-27)

> 조사 문서다. 코드는 수정하지 않았다. 기준은 Paper 1.21.1 의 `paper-global.yml`, `paper-world-defaults.yml`(`world-settings.default`), `spigot.yml`, `bukkit.yml` 이다.
> 근거: 작성자의 일반 지식과 레포 안의 코드·문서만 썼다. 웹 조회는 하지 않았다. 키 이름과 기본값은 버전 사이에 바뀌었을 수 있어 **구현 전에 대조해야 한다.**
> 관련 문서: `docs/furnace-community-wishlist.md` 1.6절(설정 운영 불만 34~36, 설정 키 이동 35).

## 0. 읽는 법

| 표기 | 의미 |
|---|---|
| **일반지식** | 여러 번 본 기본값·키 이름이라 확신이 있지만 이번에 출처를 확인하지 않았다 |
| **가정** | 기억이 불확실하다. 키 이름이나 기본값을 확인하지 못했다. 구현 전에 Paper 소스·설정 파일로 확인해야 한다 |
| **바닐라와 다름** | 키를 바꾸면 바닐라 1.21.1 과 관측 결과가 달라진다고 본다. `예 / 아니오 / 가정` |
| **Furnace 현황** | `있음`(같은 이름·의미) / `부분`(이름이나 의미만 비슷함) / `없음` |
| **우선순위** | `상` = 바닐라 기본값으로 구현해야 할 것(운영자가 먼저 찾는 설정). `중` = 바닐라와 다른 튜닝이라 실험 플래그 뒤에 둘 것. `하` = 구현하지 않거나 비목표 [제안] |

**원칙(AGENTS.md 절대 규칙 2·3 기준)**: Paper 키는 "운영자가 바꾸는 손잡이"로 참고만 한다. 바닐라 기본값과 다른 값을 기본으로 켜지 않는다. 바닐라와 다른 동작을 켜는 키는 `experiment.*` 플래그로 게이팅한다. Furnace 키 이름은 Paper 를 그대로 베끼지 않고 바닐라 용어를 우선한다.

---

## 1. `paper-global.yml` (서버 전역)

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 [제안] |
|---|---|---|---|---|---|
| `proxies.velocity.enabled` | Velocity 모던 포워딩 사용 | false **일반지식** | 아니오(프록시 기능) | 부분: `furnace.properties` 의 `velocity-secret` 이 비어 있지 않으면 켜짐(의미 같음, 플래그 없음) | 완료(검증 필요) |
| `proxies.velocity.secret` | 포워딩 비밀키 | 빈 값 **일반지식** | 아니오 | 있음: `velocity-secret` (`Config.java`) | 완료 |
| `proxies.bungee-cord.online-mode` | BungeeCord 레거시 포워딩 | false **일반지식** | 아니오 | 없음 | 하 (레거시, Velocity 만 지원) |
| `proxies.proxy-protocol` | PROXY 프로토콜 수신 | false **가정** | 아니오 | 없음 | 하 |
| `unsupported-settings.allow-piston-duplication` | 피스톤 복제 익스플로잇 허용 | false **일반지식** | **예** (익스플로잇) | 없음 | 하 (구현 금지, 비목표) |
| `unsupported-settings.allow-permanent-block-break-exploits` | 영구 블록 파괴 익스플로잇 허용 | false **일반지식** | **예** | 없음 | 하 (비목표) |
| `unsupported-settings.allow-headless-pistons` | 헤드리스 피스톤 허용 | false **일반지식** | **예** | 없음 | 하 (비목표) |
| `unsupported-settings.allow-unsafe-end-portal-teleportation` | 엔드 포탈 안전하지 않은 텔레포트 | false **일반지식** | **예** | 없음(엔드 미구현) | 하 |
| `unsupported-settings.allow-tnt-duplication` 외 `unsupported-settings.*` 나머지 | 익스플로잇 허용 스위치 묶음 | 대부분 false **가정** | **예** | 없음 | 하 (전부 비목표, 키 목록만 보관) |
| `unsupported-settings.perform-username-validation` | 플레이어 이름 검증 | true **가정** | 아니오 | 없음(접속 검증은 `online-mode` 로직이 맡음 — 가정) | 중 (검증 규칙 바닐라와 대조 필요) |
| `async-pathfinding.enabled` | 길찾기를 별도 스레드에서 | true **가정** | **가정**(틱 타이밍이 달라짐) | 없음 | 중 (region 스레드 모델과 충돌 가능, 실험 플래그) |
| `async-pathfinding.max-threads` | 길찾기 스레드 수 | 0(자동) **가정** | 가정 | 없음 | 중 |
| `watchdog.early-warning-every` / `early-warning-delay` | 틱 지연 경고 주기·지연 | 5000 / 10000 ms **가정** | 아니오(진단) | 부분: `max-tick-time`(바닐라 키, 워치독 중단 시간)만 있음 | 중 (경고 로그, 틱 진단과 묶기) |
| `logging.deobfuscate-stacktraces` | 스택트레이스 난독화 해제 | true **가정** | 아니오 | 없음 | 하 |
| `logging.log-player-ip-addresses` | 접속 IP 기록 | true **일반지식** | 아니오 | 있음: `log-ips`(바닐라 server.properties 키, 의미 같음) | 완료 |
| `chunk-system.io-threads` | 청크 입출력 스레드 수 | -1(자동) **가정** | 아니오 | 부분: `io-threads`(`furnace.properties`) | 완료(의미 대조 필요) |
| `chunk-system.worker-threads` | 청크 생성 작업 스레드 수 | -1(자동) **가정** | 아니오 | 부분: `region-threads` 는 region 스케줄러용이라 의미가 다름 | 완료(의미 다름 표시) |
| `misc.region-file-cache-size` | region 파일 핸들 캐시 크기 | 256 **가정** | 아니오 | 없음 | 하 |
| `misc.fix-entity-position-desync` | 엔티티 위치 어긋남 보정 | true **가정** | **가정**(패킷 차이) | 없음 | 중 |
| `packet-limiter.all.max-packet-rate` / `interval` | 패킷 속도 제한과 킥 | 500.0 / 7.0 **가정** | 예(접속 차단 정책) | 부분: `rate-limit`(바닐라 키)는 패킷 수 제한 — 의미 유사, 단위 확인 필요 | 중 (보안 목적, 기본 끔 권장) |
| `scoreboards.allow-non-player-entities-on-scoreboards` | 엔티티를 팀·스코어보드에 허용 | false **가정** | **예**(바닐라 금지 해제) | 없음 | 하 |
| `item-validation.*` | 아이템 NBT·컴포넌트 크기 검증 | 설정별 **가정** | 가정 | 없음 | 중 (검증 한도가 바닐라와 같아야 함) |

---

## 2. `paper-world-defaults.yml` (`world-settings.default` 아래, 월드별 재정의 가능)

### 2.1 스폰·디스폰·엔티티 (바닐라 동작에 직접 영향)

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 [제안] |
|---|---|---|---|---|---|
| `entities.spawning.spawn-limits.monsters` | 플레이어 주변 몬스터 상한 | 70 **일반지식** (바닐라 MONSTER 상한) | 아니오(기본값) | 없음 | 상 |
| `entities.spawning.spawn-limits.animals` | 동물 상한 | 10 **일반지식** | 아니오 | 없음 | 상 |
| `entities.spawning.spawn-limits.water-animals` | 수중 동물 상한 | 5 **일반지식** | 아니오 | 없음 | 상 |
| `entities.spawning.spawn-limits.ambient` | 주변 생물(박쥐) 상한 | 15 **일반지식** | 아니오 | 없음 | 상 |
| `entities.spawning.spawn-limits.axolotls` / `water-ambient` / `underground-water-creature` | 상한(세부 카테고리) | 5 / 20 / 5 **가정** | 아니오 | 없음 | 상 (카테고리 이름 확인 필요) |
| `entities.spawning.per-player-mob-spawns` | 상한을 플레이어별로 계산 | true **일반지식** | 아니오 | 없음 | 상 (바닐라 기본 동작과 같은지 확인) |
| `entities.spawning.despawn-ranges.<카테고리>.soft` / `hard` | 디스폰 거리(소프트 32 / 하드 128 블록) | 몬스터 32 / 128 **일반지식** | 아니오 | 없음 | 상 |
| `entities.spawning.alt-item-despawn-rate` | 아이템 디스폰 시간 수정 | 끔, 기본 6000틱 **가정** | 아니오(기본 꺼짐) | 없음 | 상 (바닐라 6000틱 기준 구현) |
| `entities.spawning.non-player-arrow-despawn-rate` / `creative-arrow-despawn-rate` | 화살 디스폰 시간 | -1(바닐라 기본) **가정** | 아니오 | 없음 | 상 |
| `entities.spawning.ticks-per`(bukkit `ticks-per` 와 같은 역할) | 카테고리별 스폰 시도 간격 | 바닐라 값 **가정** | 아니오 | 없음 | 상 |
| `entities.spawning.mob-spawner-tick-rate` | 몬스터 스포너 틱 간격 | 1 **가정** | 아니오 | 없음 | 중 |
| `entities.behavior.nerf-spawner-mobs` | 스포너 몹 약화 | false **일반지식** | 예(켜면) | 없음 | 하 |
| `entities.behavior.disable-player-crits` | 치명타 끔 | false **가정** | 예(켜면) | 없음 | 하 |
| `entities.behavior.villagers-*` / `disable-chest-cat-detection` | 주민·고양이 행동 조정 | 바닐라 값 **가정** | 예(켜면) | 없음 | 하 (행동 변경은 비목표) |
| `entities.armor-stands.do-collision-entity-lookups` | 아머 스탠드 충돌 조회 | false **가정** | 예 | 없음 | 하 |
| `collisions.max-entity-collisions` | 엔티티 충돌 계산 상한 | 8 **가정** | 예(상한이 있으면) | 없음 | 중 (바닐라는 상한 없음 — 가정 확인 필요) |
| `entities.entity-tracking-range.players` / `animals` / `monsters` / `misc` / `display` / `other` | 엔티티를 클라이언트에 보내는 거리(블록) | 48 / 48 / 48 / 32 / 128 / 64 **가정**(display 는 가정 강함) | 예(바닐라 공식 거리와 다를 수 있음) | 부분: `entity-view-distance` 는 단일 정수, 종류별 아님. `entity-broadcast-range-percentage`(바닐라)는 다른 공식 | 상 (관측에 직접 영향, 바닐라 공식 대조 필수) |
| `entities.entity-activation-range`(spigot `entity-activation-range`) | 멀리 있는 엔티티 AI 감속 | 동물 32 등 **일반지식**(spigot) | **예** | 없음 | 하 (바닐라와 다름, 실험 플래그 뒤) |
| `dab` (활성화 거리 기반 AI 틱 감속) | 멀리 있는 몹 AI 틱 빈도 | 기본값 **가정**(켜짐 여부 확인 필요) | **예** | 없음 | 하 |
| `tick-rates.*` (센서·행동 틱 간격) | 주민 POI 탐색 등 간격 | 대부분 1, 일부 40 **가정** | **예** | 없음 | 하 |
| `max-tnt-per-tick` / `max-tnt-merges` | TNT 폭발 상한 | 100 **가정** | **예**(상한) | 없음 | 하 |
| `optimize-explosions` | 폭발 계산 최적화 | false **가정** | 가정(결과 동일 여부 미확인) | 없음 | 중 (결과가 바닐라와 같은지 먼저 검증) |
| `environment.disable-explosion-knockback` | 폭발 넉백 끔 | false **가정** | 예(켜면) | 없음 | 하 |
| `environment.disable-thunder` / `disable-ice-and-snow` | 번개·얼음눈 끔 | false **가정** | 예(켜면) | 없음 | 하 |
| `environment.nether-ceiling-void-damage` | 네더 천장 피해 | 바닐라 값 **가정** | 가정 | 없음(네더 미구현) | 하 |

### 2.2 청크·저장

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 [제안] |
|---|---|---|---|---|---|
| `chunks.auto-save-interval` | 자동 저장 간격 | `default`(바닐라 6000틱) **가정** | 아니오 | 부분: `autosave-ticks` = 6000 (`Config.DEFAULT`). 같은 의미 | 상 (있음, 이름 다름) |
| `chunks.max-auto-save-chunks-per-tick` | 틱당 자동 저장 청크 수 | 24 **가정** | 아니오(저장 부하) | 부분: `chunks-per-tick` 은 의미 확인 필요(가정) | 중 |
| `chunks.delay-chunk-unloads-by` | 청크 언로드 지연 | 10s **가정** | 예(언로드 타이밍) | 부분: `chunk-eviction-grace-ticks` 가 같은 목적(틱 단위) | 중 |
| `chunks.entity-per-chunk-save-limit` | 청크당 저장 엔티티 상한 | 설정별 **가정** | 예(상한이면) | 없음 | 하 |
| `chunks.prevent-moving-into-unloaded-chunks` | 미로드 청크로 이동 차단 | false **가정** | 예(켜면) | 없음 | 중 (region 모델에서 필요 여부 검토) |
| `feature-seeds.generate-random-seeds-for-all` 및 `seed-*` (village, desert 등) | 구조물 시드 재정의 | false **가정** | **예**(true 면) | 없음 — 바닐라 시드 체계를 따라야 함 | 상 (기본값만 바닐라와 맞춘다. 재정의는 하) |
| `max-growth-height.*` | 작물 최대 성장 높이 | 바닐라 값 **가정** | 예(변경 시) | 없음 | 하 |
| `anti-xray.enabled` / `engine-mode` / `hidden-blocks` / `replacement-blocks` | 광석 은닉 | false **일반지식** | **예**(클라이언트가 받는 블록이 다름) | 없음 | 하 (관측 결과가 바뀌므로 parity 와 충돌) |

### 2.3 호퍼·정밀 틱

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 [제안] |
|---|---|---|---|---|---|
| `hopper.cooldown-when-full` | 가득 찬 호퍼 쿨다운 | true **가정** | 가정(false 면 예) | 없음 | 중 (바닐라 동작 대조 필요) |
| `hopper.disable-move-event` | 호퍼 이동 이벤트 생략 | false **가정** | 아니오(성능) | 없음 | 하 |
| `hopper.ignore-occluding-blocks` | 막힌 블록 무시 | false **가정** | 예(켜면) | 없음 | 하 |
| `misc.max-joins-per-tick` 등 접속 상한 | 틱당 접속 처리 수 | 설정별 **가정** | 아니오 | 없음 | 중 |

---

## 3. `spigot.yml`

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 [제안] |
|---|---|---|---|---|---|
| `settings.bungeecord` | BungeeCord 호환 | false **일반지식** | 아니오 | 없음(Velocity 만) | 하 |
| `settings.attribute.maxHealth.max` / `movementSpeed.max` | 속성 상한 | 2048 / 1024 **가정** | **예**(바닐라 상한과 다를 수 있음) | 없음 | 하 (바닐라 상한 그대로) |
| `settings.player-shuffle` | 플레이어 틱 순서 섞기 | 0 **가정** | **예**(순서가 바뀜) | 없음 | 하 (비목표: 결정성 깨짐) |
| `settings.netty-threads` | Netty 스레드 수 | 4 **가정** | 아니오 | 부분: `io-threads`(의미 다름: 청크 I/O) | 하 |
| `settings.timeout-time` | 클라이언트 keep-alive 타임아웃 | 60 **가정** | 예(바닐라와 다를 수 있음) | 없음 | 중 (바닐라 값과 대조 필요) |
| `settings.moved-wrongly-threshold` | 이동 오류 판정 임계 | 0.0625 **가정** | 아니오(진단) | 없음 | 하 |
| `settings.sample-count` | 틱 샘플 수(TPS 계산) | 12 **가정** | 아니오 | 없음 | 하 |
| `settings.restart-on-crash` | 충돌 시 재시작 스크립트 | false **가정** | 아니오 | 없음 | 하 |
| `world-settings.default.entity-activation-range.*` | 엔티티 활성화 거리(AI 감속) | 동물 32, 몬스터 32, 기타 16 등 **일반지식** | **예** | 없음 | 하 |
| `world-settings.default.entity-tracking-range.*` | 추적 거리(spigot 판) | 48 / 32 / 64 **일반지식** | **예** | 없음 | 상 (paper-world-defaults 와 중복 — 한쪽만 구현) |
| `world-settings.default.item-despawn-rate` | 아이템 디스폰 틱 | 6000 **일반지식** | 아니오 | 없음 | 상 |
| `world-settings.default.arrow-despawn-rate` | 화살 디스폰 틱 | 1200 **일반지식** | 아니오 | 없음 | 상 |
| `world-settings.default.merge-radius.item` / `exp` | 아이템·경험치 병합 반경 | 2.5 / 3.0 **일반지식** | **예**(병합 결과가 달라짐) | 없음 | 중 |
| `world-settings.default.growth.*-modifier` | 작물 성장 확률 배율 | 100(=바닐라) **일반지식** | 아니오(기본값) | 없음 | 중 (바닐라 배율 100 기준, 변경 시 parity 깨짐) |
| `world-settings.default.nerf-spawner-mobs` | 스포너 몹 약화 | false **일반지식** | 아니오(기본) | 없음 | 하 |
| `world-settings.default.hanging-tick-frequency` | 걸린 엔티티 틱 간격 | 100 **가정** | **예** | 없음 | 하 |
| `world-settings.default.tick-inactive-villagers` | 비활성 주민 틱 | true **가정** | 아니오 | 없음 | 하 |
| `world-settings.default.mob-spawn-range` | 스폰 범위 | 4 **가정** | 가정 | 없음 | 중 |
| `world-settings.default.max-entity-collisions` | 충돌 상한(구판) | 2 **가정** | **예** | 없음 | 하 |
| `commands.*` (탭 완성, 스팸 제외 등) | 명령 편의 | 설정별 **가정** | 아니오 | 없음 | 하 (명령 시스템 설계 후) |
| `players.disable-saving` / `stats.disable-saving` / `advancements.disable-saving` | 저장 끄기 | false **일반지식** | 예(켜면 저장 안 함) | 없음 | 하 |

---

## 4. `bukkit.yml`

| 키 경로 | 의미 | 기본값 | 바닐라와 다름 | Furnace 현황 | 우선순위 [제안] |
|---|---|---|---|---|---|
| `spawn-limits.monsters` / `animals` / `water-animals` / `ambient` / `water-ambient` / `water-underground-creature` | 스폰 상한(bukkit 판) | 70 / 10 / 5 / 15 / 20 / 5 **일반지식** | 아니오 | 없음 | 상 (paper-world-defaults 가 우선. 한쪽만 구현) |
| `ticks-per.animal-spawns` | 동물 스폰 시도 간격(틱) | 400 **일반지식** | 아니오(바닐라 주기 400 과 같음 — 가정 확인) | 없음 | 상 |
| `ticks-per.monster-spawns` / `water-spawns` / `water-ambient-spawns` / `water-underground-creature-spawns` / `ambient-spawns` | 스폰 시도 간격 | 1 **일반지식** | 아니오(가정 확인) | 없음 | 상 |
| `chunk-gc.period-in-ticks` / `load-threshold` | 청크 언로드 GC 주기·기준 | 600 / 0 **일반지식** | **예**(언로드 타이밍) | 없음 | 중 |
| `settings.allow-end` | 엔드 허용 | true **일반지식** | 아니오 | 없음(엔드 미구현) | 하 (엔드 구현 시기 결정) |
| `settings.connection-throttle` | 같은 IP 빠른 재접속 차단(ms) | 4000 **일반지식** | **예**(접속 차단 정책) | 없음 | 중 (보안 목적, 기본 켜짐 아님 권장) |
| `settings.warn-on-overload` | 과부하 경고 | true **일반지식** | 아니오 | 없음 | 하 |
| `settings.query-plugins` | 쿼리에 플러그인 목록 | true **일반지식** | 아니오 | 없음 | 하 |
| `settings.minimum-api` / `deprecated-verbose` / `use-map-color-cache` | 플러그인·API 호환 | 설정별 **가정** | 아니오 | 없음 | 하 (Bukkit 호환 비목표) |
| `aliases` (`now-in-commands.yml`) | 명령 별칭 파일 | 파일 지정 **일반지식** | 아니오 | 없음 | 하 (명령 시스템 설계 후) |

---

## 5. Furnace 현재 설정 구조

설정은 두 파일이 나뉘어 있다. 코드 위치는 `furnace-server/src/main/java/org/domi/furnace/server/config/` 이다.

| 파일 | 클래스 | 역할 |
|---|---|---|
| `furnace.properties` | `Config.java`(record), `ConfigLoader.java` | Furnace 고유 런타임 값. 없으면 `Config.DEFAULT`. 잘못된 값은 기동 시 `IllegalArgumentException` |
| `server.properties` | `ServerProperties.java`, `VanillaSettings.java`, `ServerPropertiesFile.java` | 바닐라 `DedicatedServerProperties` 이식. 키·기본값·정규화가 1.21.1 과 같다고 주석에 적혀 있다 |
| (파일 공통) | `VanillaSettings.java` | UTF-8 로 읽고 실패 시 ISO-8859-1 재시도. 읽은 키는 정규화해서 다시 넣고, 모르는 키는 보존 |
| (런타임 변경) | `ServerPropertiesFile.java` | `white-list`, `player-idle-timeout` 처럼 바뀌는 값을 `update` 하면 파일을 다시 쓴다 |

`Config.VANILLA_KEYS` 는 `server-name, port, compression-threshold, view-distance, level-name, level-seed` 6개다. `furnace.properties` 에 있어도 `server.properties` 가 우선한다(`Config.withServerProperties`). 기동 시 무시되는 키는 `ConfigLoader.ignoredVanillaKeys` 가 경고용으로 돌려준다.

### 5.1 `furnace.properties` (Furnace 고유)

| 키 | 의미 | 기본값(`Config.DEFAULT`) | 비고 |
|---|---|---|---|
| `server-name` | 서버 이름 | `Furnace Server` | `server.properties` 의 `motd` 가 덮어씀 |
| `tick-nanos` | 틱 길이(ns) | 50,000,000 | Paper 에 대응 키 없음 |
| `port` | 포트 | 25565 | `server-port` 가 우선 |
| `compression-threshold` | 압축 임계값 | 256 | `network-compression-threshold` 가 우선 |
| `view-distance` | 청크 전송 반경 | 5 | 2~32 로 조정(`ConfigLoader`) |
| `entity-view-distance` | 엔티티 가시 반경 | 8 | Paper `entity-tracking-range` 와 의미가 다름(부분) |
| `region-section-shift` | region 구역 크기 지수(2^값 청크) | 4 | 1~8 |
| `region-merge-radius` | region 합치기 반경(구역 단위) | 1 | 1 이상 |
| `region-threads` | region 작업 스레드 수 | CPU 코어 수 | Paper `chunk-system.worker-threads` 와 의미 다름 |
| `level-name` | 월드 이름 | `world` | `server.properties` 가 우선 |
| `level-seed` | 시드(기본 0) | 0 | `server.properties` 가 우선 |
| `io-threads` | I/O 스레드 수 | 4 | Paper `chunk-system.io-threads` 와 대응(가정) |
| `autosave-ticks` | 자동 저장 간격(틱) | 6000 | Paper `chunks.auto-save-interval` 과 대응 |
| `chunk-eviction-grace-ticks` | 청크 제거 유예(틱) | 100 | Paper `chunks.delay-chunk-unloads-by` 와 같은 목적 |
| `spawn-chunk-radius` | 스폰 청크 반경 | 2 | Furnace 고유(Paper 키 아님) |
| `chunks-per-tick` | 틱당 청크 처리 수 | 16 | Paper 대응 키 불명(가정) |
| `velocity-secret` | Velocity 포워딩 비밀키 | 빈 값(끔) | Paper `proxies.velocity.secret` 과 같은 의미 |

### 5.2 `server.properties` (바닐라 키, Furnace 가 읽음)

`ServerProperties.java` 에 있는 키다. 바닐라 키라서 Paper 와 같은 이름이면 같은 의미로 본다.

`online-mode`, `prevent-proxy-connections`, `server-ip`, `spawn-animals`, `spawn-npcs`, `pvp`, `allow-flight`, `motd`, `bug-report-link`, `force-gamemode`, `enforce-whitelist`, `difficulty`, `gamemode`, `level-name`, `server-port`, `announce-player-achievements`, `enable-query`, `query.port`, `enable-rcon`, `rcon.port`, `rcon.password`, `hardcore`, `allow-nether`, `spawn-monsters`, `use-native-transport`, `enable-command-block`, `spawn-protection`, `op-permission-level`, `function-permission-level`, `max-tick-time`, `max-chained-neighbor-updates`, `rate-limit`, `view-distance`, `simulation-distance`, `max-players`, `network-compression-threshold`, `broadcast-rcon-to-ops`, `broadcast-console-to-ops`, `max-world-size`, `sync-chunk-writes`, `region-file-compression`, `enable-jmx-monitoring`, `enable-status`, `hide-online-players`, `entity-broadcast-range-percentage`, `text-filtering-config`, `resource-pack`·`resource-pack-id`·`resource-pack-sha1`·`resource-pack-hash`·`require-resource-pack`·`resource-pack-prompt`, `initial-enabled-packs`, `initial-disabled-packs`, `player-idle-timeout`, `white-list`, `enforce-secure-profile`, `log-ips`, `level-seed`, `generate-structures`, `generator-settings`, `level-type`, `accepts-transfers`.

---

## 6. 대조 요약: 같은 이름·같은 의미로 이미 있는 키

| Paper/bukkit/spigot 키 | Furnace 키 | 현황 | 메모 |
|---|---|---|---|
| `proxies.velocity.secret` | `velocity-secret` (furnace.properties) | **있음** | 이름만 다름. Paper 의 `enabled` 플래그는 없고 빈 값이면 끔 |
| `logging.log-player-ip-addresses` | `log-ips` (server.properties) | **있음** | 바닐라 키. 의미 같음(가정 확인) |
| `view-distance` | `view-distance` | **있음** | 바닐라 키. Paper 도 같음 |
| `spawn-animals` / `spawn-monsters` / `spawn-npcs` | 같은 이름 | **있음** | 바닐라 키. 스폰 **상한**(paper-world-defaults `spawn-limits`)은 없음 |
| `max-tick-time` (watchdog 중단 시간) | `max-tick-time` | **있음** | 바닐라 키 |
| `rate-limit` (패킷 속도) | `rate-limit` | **있음**(부분) | 바닐라 키. Paper `packet-limiter` 는 킥 정책까지 있어 세부는 다름 |
| `allow-nether` | `allow-nether` | **있음** | 바닐라 키(네더 미구현) |
| `chunks.auto-save-interval` | `autosave-ticks` | **있음**(이름 다름) | 같은 목적, 단위 틱. 기본 6000 |
| `chunks.delay-chunk-unloads-by` | `chunk-eviction-grace-ticks` | **부분** | 같은 목적, 단위 틱 |
| `chunk-system.io-threads` | `io-threads` | **부분** | 가정 |
| `chunk-system.worker-threads` | `region-threads` | **부분**(의미 다름) | Furnace 는 region 스케줄러 |
| `entity-tracking-range.*` | `entity-view-distance` | **부분** | 종류별 거리 없음. `entity-broadcast-range-percentage`(바닐라)와도 다름 |
| `max-auto-save-chunks-per-tick` | `chunks-per-tick` | **부분**(가정) | 의미 확인 필요 |
| `spigot.world-settings.item-despawn-rate` | 없음 | 없음 | |
| `spawn-limits.*`, `ticks-per.*`, `despawn-ranges` | 없음 | 없음 | 상 우선순위 |
| `unsupported-settings.*` | 없음 | 없음 | 비목표 |

**없음 중 상 우선순위인 것**: 스폰 상한·ticks-per(바닐라 값과 같게), 디스폰 거리와 아이템·화살 디스폰 틱, 엔티티 추적 거리(종류별), 시드 체계 기본값. 이것들은 Furnace 가 자연 스폰·엔티티 가시성을 구현할 때 바닐라 기본값을 그대로 쓰면 되는 항목이라 **설정 키 추가보다 바닐라 값 구현이 먼저다.**

---

## 7. 구현 우선순위 제안 [제안]

1. **상 (바닐라 값으로 먼저 구현)**: 스폰 상한과 카테고리별 `ticks-per`, 디스폰 거리와 아이템·화살 디스폰 틱, 엔티티 추적 거리, 자동 저장 간격(이미 있음), 시드 기본값. 키를 노출하기 전에 바닐라 값을 기본으로 맞춘다.
2. **중 (실험 플래그 뒤, 기본 꺼짐)**: 비동기 길찾기, 청크 언로드 지연, 호퍼 쿨다운, 폭발 최적화, 아이템 병합 반경, 가득 찬 청크 처리 같은 성능 튜닝. 각각 바닐라와 결과가 같은지 대조한 뒤에만 켠다. 접속 제한·패킷 킥은 보안 기능으로 따로 본다.
3. **하 (구현 안 함 / 비목표)**: `unsupported-settings.*`(익스플로잇 허용), `scoreboards.*` 의 바닐라 금지 해제, 속성 상한 상향, 플레이어 순서 섞기, 광석 은닉(anti-xray: 클라이언트가 받는 블록이 바뀌어 관측 parity 와 충돌), 스포너 몹 약화, BungeeCord 레거시, Bukkit 명령 별칭 파일, 저장 끄기 옵션.

---

## 8. 확인 필요 목록 (가정)

- 위 표에서 **가정**으로 표시한 모든 기본값과 키 경로. 특히 `spawn-limits` 세부 카테고리 이름, `entity-tracking-range` 의 `display` 기본값, `dab` 기본 켜짐 여부, `unsupported-settings.*` 전체 목록, `async-pathfinding` 기본값, `packet-limiter` 수치는 구현 전에 Paper 1.21.1 의 `paper-global.yml` / `paper-world-defaults.yml` 원문으로 확인한다.
- Furnace 쪽 가정: `chunks-per-tick` 의미, `io-threads` 가 Paper `chunk-system.io-threads` 와 같은 역할인지, `log-ips` 가 로그인 IP 기록 여부만 보는지 — 코드를 읽어 확인하지 않았다.
- 이 문서는 키 목록을 옮긴 것이고 Paper 와의 동작 동등성을 증명하지 않는다. 동작은 바닐라 서버 jar·oracle 로 확인해야 한다(AGENTS.md 절대 규칙 2).
