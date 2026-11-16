# 데스크탑 실서버 시험 — 실제 플러그인 4종 동시 로드 (2026-11-16)

- 기준: `claude/quirky-wright-lra834` 최신 `75545a7`(Furnace dev+75545a7, MC 1.21.1). 이 PC(DOMINICUS)에서 JDK 21(`C:\Program Files\Java\jdk-21.0.12`)로 `:furnace-server:fatJar` 빌드 성공(1분 6초. `--offline` 은 `bungeecord-chat` 캐시가 없어 실패해 온라인으로 빌드).
- 시험 서버: 스크래치패드의 빈 폴더(평지·오프라인·RCON). `C:\Users\gagam\OneDrive\Desktop\furnace` 의 실서버·월드는 건드리지 않았고, 거기 `plugins/bukkit-plugins/` 의 jar 4개만 복사해 썼다.
- 플러그인 jar(전부): FastAsyncWorldEdit-Paper-2.16.2, LuckPerms-Bukkit-5.5.91, ProtocolLib(5.4.0), Vault(1.7.3-b131). Desktop 폴더에 다른 플러그인 jar 는 없다(나머지는 LuckPerms 라이브러리·`.libraries` 캐시).
- 도구: 저장소 `tools/bot-test`(mineflayer, `lib.js`·`rcon.js`) + 새 스크립트 `tools/bot-test/round-real4/ptall.js`(1차)·`ptB.js`(재시작 후 2차).
- 시험 후 서버·gradle 데몬 모두 종료, 25565·25575·9225 비움 확인.

## 요약
**4개 모두 로드·활성화되고 `NoSuchMethodError`·`NoClassDefFoundError`·스택 트레이스는 한 번도 안 나왔다.** 기동 23초(첫 실행, 바닐라 데이터 내려받기·라이브러리 다운로드 포함)·11초(재시작). 봇 접속·명령·FAWE 편집·LuckPerms/Vault 연동·퇴장·종료·재시작 모두 정상. 실패는 "기능이 아직 없다"는 경고 형태로 나오고 실제로 막힌 기능은 시험한 범위에서 `ProtocolLib /packet names`(아래 표 4번) 뿐이다.

| # | 단계 | 결과 |
|---|---|---|
| 1 | 4개 로드·활성화 | OK. `plugins` 명령이 4개 모두 나열, 활성화 로그 4줄 정상 |
| 2 | 콘솔 오류·경고 | ERROR 30줄(LegacyMapper, 아래 1번), WARN 은 아래 표. 호환 실패 예외 0 |
| 3 | 봇 접속·스폰·퇴장 | OK(1차·2차 모두). 퇴장 로그 예외 없음 |
| 4 | 채팅·give·time·list·gamemode·tp(RCON/명령) | OK |
| 5 | 이동(전진) | 2차 OK(8.4블록). 1차는 FAWE `//sphere` 로 봇이 돌 구 안에 갇혀 0.2블록 — 시험 오류, 2차 재시험으로 정상 확인 |
| 6 | FAWE 편집 | `//pos1·pos2·size·set stone·count·undo·sphere·replace·copy·paste·wand` 전부 정상. 봇이 받은 블록 변경 확인(stone↔air), 재시작 후 `//set oak_planks` 도 정상. `//wand` 도끼 좌클릭 → pos1 지정까지 됨(PlayerInteract 이벤트 동작) |
| 7 | LuckPerms | `lp info`, `user info`, `creategroup`, `permission set`, `parent add`, `verbose`, `editor`(링크 생성) 정상. 재시작 후 vip 그룹 상속 유지(H2 저장 영속) |
| 8 | Vault | `/vault-info` 가 Permission·Chat 을 LuckPerms 로 잡음(Economy 없음은 경제 플러그인 부재라 정상) |
| 9 | ProtocolLib | `/protocol version·check·listeners·timings` 정상. `/packet names` 는 경고 문구(아래) |
| 10 | 종료·재시작 | `stop` 때 4개 정상 비활성화·저장, 재시작 후 정상 |

## 콘솔 오류·경고 표 (원인별)

| # | 플러그인 | 로그 | 원인(API/클래스) | 영향 | 제안 |
|---|---|---|---|---|---|
| 1 | FAWE | `LegacyMapper Unknown block/item` 30줄(`grass`, `sign[rotation=N]`, `wall_sign`, `cobblestone_wall[...]`, `grass_path`, `sign`·`rose_red`·`cactus_green`·`dandelion_yellow` 아이템) — 1·2차 모두 30줄 동일 | FAWE 가 `legacy.json` 의 1.12 이름을 DataFixer(`block_state 1631 → 3955`, 호환 계층 로그에 "옛 판 스키마 없음")로 올려 보는데 Furnace 의 `DataFixer` shim 에 옛 판 스키마가 없어 그대로 둠 → 이름 불일치 | 레거시 명령어(`//set 1:0` 같은 숫자 ID)·옛 스케매틱 일부만 영향. 현재 이름 기반 명령은 정상 | T-199 와 같은 항목(노트북 결과와 일치). 레거시 블록·아이템 이름 → 1.21.1 이름 변환표(`grass→short_grass`, `sign→oak_sign`, `wall_sign→oak_wall_sign`, `grass_path→dirt_path` 등) 를 DataFixer shim 에 넣을지 결정 필요 |
| 2 | FAWE | `미지원 NMS 심볼 137개(처음 20개)` | 632개 중 shim 있음 495, 등급 C 29, 없음 108. 예: `ServerChunkCache#blockChanged/pollTask/randomState`, `ServerLevel#<init>(15)`, `ServerLevel#addFreshEntity(2)`, `ClientboundBlockEntityDataPacket#<init>(3)`, `RegistryAccess$Frozen#createSerializationContext` 등 | 호출될 때만 `UnsupportedNmsCall`. 시험한 `//` 명령들은 영향 없음. 청크 직접 교체·엔티티 복사·월드 생성·블록엔티티(표지판·상자) 편집은 이 경로를 탈 가능성 | 사용 빈도 높은 순(`blockChanged`, `addFreshEntity`, `ClientboundBlockEntityDataPacket`, `FullChunkStatus`)으로 shim 추가를 TASK 로 제안. 전체 137개 목록은 `plugins.compat.nms.missing-class-log=each` 로 확인 |
| 3 | FAWE | `NMS 멤버 MinecraftServer#nextTickTime 를 리플렉션으로 찾았으나 shim 에 없다`, `ServerPlayer#playerConnection …` | 리플렉션 필드 조회 shim 누락. FAWE 는 예외를 잡고 계속 감(동작은 이상 없음) | 틱 시각 접근 기능(`nextTickTime`)·플레이어 연결 접근에 쓰이는 보조 기능 일부 | `MinecraftServer#nextTickTime`, `ServerPlayer#playerConnection` 필드 shim 추가 TASK(노트북 결과와 동일) |
| 4 | ProtocolLib | `패킷 파이프라인(ServerConnectionListener)에 끼어들려 한다… 핸드셰이크·상태·로그인 가로채기 안 이어짐`(WARN, 설계상 안내) + `리플렉션으로 찾은 NMS 클래스 9개/14개 없음`(`EnumProtocol`, `SimpleUnboundProtocol`, `ProtocolInfo$a`, `WorldType`, `DataWatcher`, `NBTBase` …) + `/packet names server 0-3` → `Warning: Missing protocol (PLAY, etc) - assuming legacy IDs.` | ProtocolLib 이 Spigot 이름·구 이름을 차례로 시도하는 감지 코드. 그 중 프로토콜 레지스트리(`ProtocolInfo`/`EnumProtocol`) 접근이 실패해 `/packet names` 가 패킷 이름표를 못 만듦. 서버가 스스로 보내는 패킷·들어오는 패킷 가로채기는 아직(compat-protocollib-pipeline-2b-plan) | `/protocol listeners` 가 비어 있어도 다른 플러그인이 ProtocolLib 리스너를 달면 어떻게 되는지는 이번 시험에 안 들어감(리스너 플러그인 없음). 패킷 이름 표시·들어오는 패킷 훅은 불가 | ProtocolLib 에 의존하는 실제 플러그인(예: 상호작용 GUI·홀로그램) 을 올려 리스너 동작을 시험. 2b 계획(들어오는 패킷 가로채기)이 필요한지는 도미니코가 쓸 플러그인에 달림 |
| 5 | FAWE | `'tm' 명령은 이미 있어 fastasyncworldedit:tm 로만 등록` | 바닐라 `/tm`(=`/teammsg` 별칭)과 FAWE `tm`(texture/mask) 충돌. Paper 와 같은 결과인지는 이번에 대조하지 못함(가정) | FAWE `/tm` 별칭 못 씀(`/fastasyncworldedit:tm`) | 경고뿐, 기능 영향 작음. Paper 대조 필요 |
| 6 | FAWE | `플러그인이 빛 다시 계산(starlight$serverRelightChunks)을 부탁했다 — Furnace 는 아직 하지 않는다` (`//set`·`//paste` 등 편집 때마다, 청크 1~20개) | FAWE 의 starlight relight 호출. Furnace 에 relight 지원 없음 | 편집한 블록 주변 빛 값이 옛 값 그대로(지하 편집·발광 블록에서 눈에 띔) | 이번 시험에서 실제 빛 어긋남은 확인 못 함(평지 낮 시간). 지하·밤에서 확인 필요 — 도미니코 직접 확인 항목 후보 |
| 7 | 서버 | `looking_at` player 조건 필드 미지원 ×3 | 발전 과제 조건 `looking_at` | 해당 업적 미달성 | 이미 알려진 항목 |
| 8 | Paper 호환 | `/version <플러그인 이름>` → `Incorrect argument for command` (`/version FastAsyncWorldEdit`, `/version ProtocolLib`) | Furnace `/version` 에 인수 없음. Paper·Bukkit 은 `/version <plugin>` 으로 플러그인 버전·작성자를 보여 줌 | 일부 플러그인·관리자 스크립트가 쓰는 명령 | `/version [플러그인]` 지원 TASK 제안(Paper 대조: 인수 있으면 해당 플러그인 이름·버전·작성자·웹사이트 출력, 없으면 서버 버전) |
| 9 | 설정 | `rcon.password 가 8자 미만`, 오프라인 모드 경고, RCON 0.0.0.0 | 시험 설정 때문 | 없음 | 해당 없음 |

## 시험 중 눈에 띈 점
- `mineflayer` 가 접속 때 `PartialReadError(ArmorTrimMaterial)` 를 찍는다 — `tools/bot-test/README.md` 에 이미 적힌 알려진 봇 쪽 문제(접속·판정에 영향 없음).
- 봇의 `dig` 시험은 이미 공기가 된 블록이라 의미 없었음(결과 PASS 는 무의미) — 블록 파괴 시험은 따로 안 했다. `//wand` 도끼 좌클릭 → FAWE pos1 지정으로 PlayerInteract 계열 이벤트 동작은 확인했다.
- 한 번에 4개를 올려도 기동 직후 `Done`, 퇴장·종료·재시작 모두 무사. 종료는 즉시(1초 미만).

## TASK 등록 제안(코드는 안 고침)
1. **FAWE 레거시 이름 변환 보강(T-199 와 동일)** — LegacyMapper 30줄. DataFixer shim 에 레거시→1.21.1 이름표 추가 또는 FAWE 용 변환 제공.
2. **FAWE NMS shim 우선순위 보강** — 미지원 137개 중 `ServerChunkCache#blockChanged`, `ServerLevel#addFreshEntity`, `ClientboundBlockEntityDataPacket`, `FullChunkStatus`, `MinecraftServer#nextTickTime`, `ServerPlayer#playerConnection` 먼저.
3. **`/version <플러그인>` 지원** — Paper 와 같은 출력.
4. **ProtocolLib 프로토콜 레지스트리 접근(`/packet names` 경고)** 과 들어오는 패킷 가로채기(2b)는 ProtocolLib 을 쓰는 실제 플러그인으로 먼저 시험한 뒤 우선순위 결정.
5. **starlight relight 호출 대응** — 지하·밤 `//set` 후 빛 확인 후 필요하면 relight 구현.

## 시험하지 못한 것(한계)
- 플러그인 리스너·이벤트가 ProtocolLib 위에서 도는지(리스너 플러그인 없음), FAWE `//schem`·`//brush`·`//regen`·청크 지형 생성·월드 간 이동, Vault 경제, 다인원, 성능(TPS)은 이번 범위 밖.
- 실제 플레이 클라이언트 렌더링·소리는 봇으로 확인 불가.
