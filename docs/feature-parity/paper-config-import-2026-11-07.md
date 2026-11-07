# Paper/Spigot 설정 가져오기 (O3, 불만 표 93행) — 2026-11-07

Paper/Spigot 서버 폴더를 그대로 옮겨 오면 `bukkit.yml`·`spigot.yml`·`config/paper-global.yml`·`config/paper-world-defaults.yml` 이 같이 따라온다.
Furnace 는 첫 기동 때 이 파일들에서 **Furnace 에 같은 뜻의 키가 있는 값만** `furnace.properties` 로 옮기고, 못 옮긴 키는 이유와 함께 보고서에 남긴다.
월드 폴더(`world_nether`·`world_the_end`·추가 월드) 쪽은 [bukkit-world-import-2026-11-03.md](bukkit-world-import-2026-11-03.md) 가 같은 첫 기동 경로에서 따로 처리한다.
이 묶음이 Q4(월드 가져오기 보강)도 겸한다.

## 1. 동작

| 항목 | 규칙 |
|---|---|
| 언제 | 기동 때 `server.properties`·EULA 확인 뒤, `furnace.properties` 를 읽는 모든 곳보다 먼저(`FurnaceMain`). 위 네 파일 중 하나라도 서버 폴더에 있고 `furnace-config-import-report.txt` 가 없을 때 |
| 읽는 곳 | 서버 폴더(현재 작업 폴더). YAML 은 `furnace-api` 의 설정 파서를 쓴다(새 의존성 없음). 원본은 읽기만 한다 |
| 쓰는 곳 | `furnace.properties` 끝에 `# --- Paper/Spigot 설정에서 가져옴 …` 머리말과 함께 덧붙인다(원래 내용은 바이트 그대로, 임시 파일 후 이동). 파일이 없으면 만든다. **이미 있는 키는 건드리지 않는다**(보고서에 "이미 있어 유지") |
| 보고서 | `furnace-config-import-report.txt`. 종류별로 묶는다: 옮김 · 이미 있음 · Paper 기본값 그대로 · 직접 확인 · 대응 없음 · 무시(버전 표지) · 모르는 키. 비밀키는 `****` 로 가린다 |
| 한 번만 | 보고서가 있으면 다시 하지 않는다. 지우고 켜거나 `-Dfurnace.importConfig=force` 로 켜면 다시 한다(이미 있는 키는 그대로). `-Dfurnace.importConfig=off` 는 끔 |
| 실패해도 | 파일 하나를 못 읽으면(문법 오류) 보고서에 적고 나머지는 계속한다. 예외가 나도 서버는 켠다(경고만). `furnace.properties` 쓰기에 실패하면 보고서를 쓰지 않아 다음 기동 때 다시 시도한다 |
| 옛 `paper.yml` | Paper 1.19 이전 형식이라 읽지 않고 "새 Paper 로 한 번 켜서 `config/paper-global.yml` 로 바꾼 뒤 다시 가져오라"고 보고서에 적는다 |

**원칙**: **Paper 기본값과 같은 값은 옮기지 않는다.** Furnace 의 기본은 바닐라이고(프로젝트 규칙 D3) Paper 설정 파일은 기본값까지 전부 적혀 있어서, 옮기면 "직접 바꾼 값"과 "그냥 있던 값"이 섞인다. 기본값과 같아서 건너뛴 키는 보고서에 "같게 하려면 이 줄을 넣어라"로 남긴다.

## 2. 대응 표 (옮기는 것)

| Paper/Spigot 키 | Furnace 키 | 규칙 |
|---|---|---|
| `spigot.yml` `settings.bungeecord` = true | `bungeecord=true` | Velocity 가 이미 켜져 있으면(옮긴 것 포함) 옮기지 않고 "직접 확인"(둘이 함께 켜지면 기동 실패) |
| `paper-global.yml` `proxies.velocity.enabled`=true + `secret` | `velocity-secret` | secret 이 비어 있으면 "직접 확인". 비밀키는 보고서·로그에 적지 않는다 |
| `paper-global.yml` `proxies.proxy-protocol` = true | `proxy-protocol=true` | 신뢰할 앞단은 `proxy-protocol.trusted` 로 제한하라고 안내 |
| `paper-global.yml` `chunk-system.io-threads` > 0 | `io-threads` | -1(자동)은 Furnace 기본 4 유지 |
| `paper-world-defaults.yml` `chunks.auto-save-interval`(틱) 또는 `bukkit.yml` `ticks-per.autosave` | `autosave-ticks` | `default` 면 bukkit.yml 값을 따른다. 6000(둘 다 기본)이면 안 옮김 |
| `paper-world-defaults.yml` `chunks.delay-chunk-unloads-by`(숫자만=초, `10s`·`2m`·`1h`·`1d` 하나) | `chunk-eviction-grace-ticks` | 틱 = 초 × 20. `500ms`·`1m 30s` 같은 복합·밀리초 표기는 Paper 가 다르게 읽는 것으로 알려져 읽지 않고 "직접 확인"으로 돌린다. Paper 기본(10s)이면 안 옮김 — Furnace 기본은 100틱(5초) |
| `paper-global.yml` `item-validation.display-name` | `limits.item.display-name` | 기본 8192 와 다를 때만 |
| 〃 `item-validation.loc-name` | `limits.item.item-name` | 가장 가까운 대응. 기본 8192 와 다를 때만 |
| 〃 `item-validation.lore-line` | `limits.item.lore-line` | 기본 8192 와 다를 때만 |
| 〃 `item-validation.book.author` | `limits.item.book-author` | 기본 8192 와 다를 때만 |
| 〃 `item-validation.book.page` | `limits.item.book-page` | 기본 16384 와 다를 때만 |

## 3. 옮기지 않고 알려 주는 것

**직접 확인** (비슷한 기능은 있지만 단위·판정이 달라 자동 변환하지 않음)

| 키 | 가리키는 곳 |
|---|---|
| `proxies.bungee-cord.online-mode` | 프록시 뒤에서는 `server.properties` `online-mode=false` |
| `logging.log-player-ip-addresses`=false | `server.properties` `log-ips=false` |
| `spigot.yml settings.timeout-time`(초), `paper-global.yml watchdog.*` | `server.properties` `max-tick-time`(밀리초, 값 × 1000 을 계산해 보여 줌) |
| `chunk-loading-basic.player-max-chunk-send-rate`(초당) | `chunks-per-tick`(틱당, ≈ 값 ÷ 20 을 계산해 보여 줌) |
| `packet-limiter.*`(`all-packets`·`overrides`) | `connection.<단계>.packets-per-second`·`packets-burst` ([connection-defenses-2026-11-06.md](connection-defenses-2026-11-06.md)) |
| `bukkit.yml chunk-gc.*` | `chunk-eviction-grace-ticks` |
| `bukkit.yml settings.world-container` | 명령줄 `--universe` |
| `spigot.yml world-settings.<월드>.view-distance`·`simulation-distance`(숫자) | `server.properties` 의 서버 전체 값 하나(월드별 값 없음) |

**대응 없음** — 아직 키가 없는 것(바닐라 값으로 동작): `spam-limiter.*`(탭 완성·레시피 패킷 도배 제한), `spawn-limits`·`ticks-per`(자동 저장 제외)·`world-settings.*`·`entities.*`·`environment.*`·`hopper.*`·`collisions.*`·`tick-rates.*`·`messages.*`·`commands.*`·`settings.*` 의 Bukkit/Spigot 전용 동작 등.
**비목표**로 못박은 것: `unsupported-settings.*`(익스플로잇 허용), `anti-xray.*`, `player-shuffle`, 속성 상한 변경, `feature-seeds.*`, 저장 끄기. (이유는 보고서에 키마다 적힌다.)
위 어디에도 없는 키는 **모르는 키**로 묶어 첫 세 개를 보여 준다 — 새 Paper 버전의 설정이 늘 때 눈에 띄게 하려는 것이다.

R4(모든 새 옵션을 키로 열고 사용자가 ON/OFF·수치 변경)에 따라 "아직 키가 없는" 항목은 Q1 이후 키가 생길 때마다 이 표의 해당 줄을 "옮기는 것"으로 옮기면 된다. 규칙은 코드의 `PaperConfigImport.Analyzer.rules()` 한 곳에 모여 있고 줄 단위로 추가한다.

## 4. 확인 상태 (정직하게)

- **Paper 쪽 키 이름·기본값은 기억 기준이다.** 이 환경은 papermc 호스트와 Paper 소스 저장소에 접근할 수 없다(프로젝트 범위 밖). 틀려도 서버는 깨지지 않는다 — 모르는 키는 "모르는 키"로, 기본값을 잘못 알았다면 "기본값이라 안 옮김"이 "옮김"으로(또는 반대로) 바뀔 뿐이다. 도미니코가 실제 Paper 1.21.1 폴더로 한 번 돌려 보고서를 보면 된다(`docs/user-todo.md` 1장).
- 시험: `PaperConfigImportTest` 17개(대응 옮기기·기본값 건너뛰기·이미 있는 키 보존·Velocity 우선·비밀키 가림·특수문자 이스케이프 후 `Properties` 로 다시 읽기·`ConfigLoader`/`ConnectionLimits` 가 결과 파일을 에러 없이 읽음·한 번만/force/off·깨진 YAML(문제 줄 원문은 보고서에 안 적음)·옛 `paper.yml`·시간 표기·기존 `bungeecord=true` 위에 Velocity 를 얹지 않음·심볼릭 링크 보존·기본값은 "직접 확인" 소음 안 만듦).
- 독립 검토(2026-11-07, 읽기 전용 에이전트) 반영: 기존 bungeecord 위 Velocity 덧붙임으로 기동 실패하던 조합 차단, 평지 높이 0 층 건너뜀·생성기를 못 읽으면 기본 생성기로 대신하지 않고 올리지 않음, 구문 오류 메시지의 비밀키 노출 차단, 임시 파일 교체 대신 끝에 덧붙이기(권한·링크 보존), 시간 표기 엄격화, `spam-limiter` 설명 정정, 기본값(`timeout-time` 60·`player-max-chunk-send-rate` 75.0)은 안내 소음에서 제외.
- 오프라인 서버 기동(2026-11-07): Paper 모양 설정 4개로 첫 기동 → 로그 요약과 `furnace.properties` 덧붙임, 두 번째 기동은 아무 일 없음(파일 불변), `-Dfurnace.importConfig=force` 는 "이미 있음 6" 으로 파일 불변.
