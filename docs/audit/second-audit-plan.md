# 2차 전체 점검 — 파트 나누기 (2026-11-08)

2차 전체 점검 파이프라인의 1단계 문서다. 저장소 전체를 **20개 파트**로 나누고, 파트마다 조사 범위·입력·제외·조사 방법·권장 모델과 노력치를 정한다. 코디네이터는 이 표대로 조사 스레드(조사관)를 연다.

기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `6e8fec9a`(PR #168 병합 시점). 이 문서는 코드를 고치지 않는다.

파이프라인(도미니코 지시, 2026-11-06·11-07):
1. 파트별 조사 스레드가 조사하고 보고서를 쓴다(이 문서).
2. 점검 스레드가 보고서를 검토한다.
3. 타당한 항목은 작업 리스트에 넣고 모델·노력치를 배정해 고친다.
4. 의문이 있으면 보고서를 쓴 스레드와 토론해 맞고 틀림을 가린 뒤 최종안으로 작업한다.

설계 원칙: 바닐라·Paper·Spigot 과 **관측 결과**만 같으면 되고, 성능·스레드·내부 구조는 Furnace 가 정한다. 그래서 조사관은 "코드 모양이 바닐라와 다르다"가 아니라 "관측 결과가 다르다"를 찾는다.

---

## 0. 한눈에 보기

| ID | 파트 | 핵심 범위 | 모델·노력치 | 차수 |
|---|---|---|---|---|
| A01 | 프로토콜·패킷·접속 상태 머신 | `net`, `net/protocol`, `net/io`, `net/registry` | Sonnet high | 2 |
| A02 | 보안·접속 방어·원격 관리 | `net/auth`, `ConnectionLimits`·`ItemValidation`·`ProxyProtocolHandler`, `remote`(RCON·Query), 서명 채팅, 파일 경로 입력 | Opus high | 1 |
| A03 | 스케줄러·region·전역 스레드 안전 | `sched`, `world/SharedScheduler`·`Regionizer`·`Region`·`RegionBorrow`, `ShutdownSequence`·`ServerWatchdog` | Opus high | 1 |
| A04 | 복제·소실 탐색(교차 region·동시 수정) | 인벤토리·메뉴·드롭·탈것·포털·차원 이동·텔레포트·사망 | Opus high | 1 |
| A05 | 청크·저장·영속성 | `world/anvil`, `world/storage`, `players`, 청크 적재·내리기, 백업, `datafix` | Opus high | 1 |
| A06 | 월드젠 바닐라 일치 | `world/gen`, 구조물, `/locate`, 월드 타입, 탐험 지도 | Opus high | 2 |
| A07 | 레드스톤·피스톤·업데이트 순서 | 레드스톤 부품, 피스톤, 관측기, 레일, 블록 업데이트 전파 | Opus high | 1 |
| A08 | 일반 블록·유체·무작위 틱·빛 | `block/types`(레드스톤 외), 유체, 식물, 무작위 틱, `light` | Sonnet high | 2 |
| A09 | 블록 엔티티·메뉴·거래 | `block/entity`, `menu`, `trade`, `world/*Container` | Sonnet high | 2 |
| A10 | 몹 AI·두뇌·스폰 | `entity/ai`, 두뇌 몹, 길찾기, `entity/spawn`, `world/poi`, `world/raid` | Opus high | 1 |
| A11 | 엔티티 물리·전투·효과 | 이동·충돌, 피해·방어구, `effect`, 발사체, 탈것 조종 | Sonnet high | 2 |
| A12 | 아이템·레시피·전리품·진행 | `item`, `recipe`, `loot`, `advancement`, `stats`, `scores`, `map` | Sonnet high | 3 |
| A13 | 명령·함수·데이터 팩·텍스트 | `command`, `function`, 데이터 팩 적재·`/reload`, `chat` 텍스트 컴포넌트 | Sonnet high | 3 |
| A14 | Furnace 플러그인 API 계약 | `furnace-api`, `server/plugin`, 패킷 API, 뷰·가상 엔티티, 핫 리로드 | Opus high | 2 |
| A15 | Bukkit·Paper API 표면 호환 | `furnace-compat-paper` 의 `org.bukkit.*`·`compat/paper`·`compat/sched`·`compat/command` | Sonnet high | 3 |
| A16 | NMS·CraftBukkit·클래스로더 | `compat/nms`, `net/minecraft/*` shim, `org/bukkit/craftbukkit`, ASM 변환, `plugin/library` | Opus high | 3 |
| A17 | 성능·확장성 | 틱 비용, 많은 접속자·엔티티, 청크 전송, 메모리·GC, 기동 시간 | Opus high | 3 |
| A18 | 운영·설정·관측 | `config`, 설정 가져오기, 콘솔, `metrics`, `profiling`, `maintenance`, 크래시 보고서 | Sonnet medium | 3 |
| A19 | 시험·CI 공백 | 패키지별 시험 공백, 흔들리는 시험, CI 조각, 봇 시험 회귀화 | Sonnet medium | 2 |
| A20 | 문서 정합 | `docs/api`, `docs/policy`, `docs/user-todo*`, 인수인계서, `CLAUDE.md` | Haiku low | 1 |

차수는 결정 R5(한 번에 6개 안팎)에 맞춘 시작 순서다(6장). Fable 은 이 표에 배정하지 않았다. 계산·측정이 긴 A17 의 측정 실행만 코디네이터 판단으로 Fable 계산·실행 대기열에 넘길 수 있다.

---

## 1. 모든 조사관에게 공통인 규칙

### 1.1 하지 않는 것
- **코드를 고치지 않는다.** 고칠 내용은 보고서의 "제안" 칸에만 적는다. 고치는 일은 3단계 작업 스레드가 한다.
- 이미 독립 검토가 끝난 PR 의 지적을 다시 올리지 않는다(5장 색인). 그 PR 의 **고친 결과가 틀렸다**는 새 근거가 있으면 올린다.
- 진행 중이거나 대기 중인 묶음(H4 묶음 C 값 타입·D 인벤토리 이벤트, H7d 실제 표 측정)은 범위에서 뺀다. 시작 시점에 코디네이터에게 진행 중 묶음을 한 번 묻는다.
- 이미 "알려진 차이"로 문서화되고 도미니코가 받아들인 것(예: `sync-chunk-writes=false`, 차원 이동 몇 틱 지연, 수면 깨우기 최대 한 틱 차이)은 다시 올리지 않는다. 그 차이가 문서보다 **커졌다**는 근거가 있을 때만 올린다.

### 1.2 조사 방법
- **재현 시험이 기본이다.** 항목마다 실패하는 JUnit 시험(또는 봇 스크립트)을 만들어 기준 커밋에서 돌리고 결과를 적는다. 시험 코드는 저장소에 커밋하지 않고 보고서와 함께 `.java.txt` 로 낸다(아래 1.3).
- **바닐라 대조**: 바닐라 1.21.1 서버 클래스는 `main` 브랜치 `tools/vanilla-data/server-1.21.1/` 에만 있다. `git archive main tools/vanilla-data/server-1.21.1 | tar -x -C <스크래치패드>` 로 꺼내 `javap -c -p` 로 보거나, Vineflower 1.10.1(구글 Maven 미러)로 스크래치패드에 디컴파일한다. **커밋 금지(EULA).** 난독화 이름 대응은 기존 문서·`context-notes/` 를 먼저 본다.
- **Paper 대조**: Paper 원문은 `raw.githubusercontent.com` 또는 `git clone` 으로 1.21.1 커밋을 읽는다. `github.com` 웹·`papermc.io`·`spigotmc.org` 는 막혀 있다. Spigot 원문은 대조 불가 — 그때는 "가정"으로 표시한다.
- **봇 시험**: 실제 접속이 필요하면 오프라인 서버 + mineflayer 봇(`tools/bot-test/README.md`). 화면·소리처럼 봇이 못 보는 것은 보고서의 "사용자 확인" 칸으로 넘긴다.
- **gradle**: 새 컨테이너에서 `--offline` 이 실패하면 스크래치패드에 Maven 미러 init 스크립트를 두고 `-I` 로 넘긴다. 한 컨테이너에서 gradle 을 동시에 두 개 돌리지 않는다. 전체 시험은 약 15분.
- **흔들리는 시험**: `LandMobTest`(개구리·염소·셜커), `GeneratedChunkSaveTest` 틱 분산 시험은 부하 때 가끔 실패한다. 이 실패를 발견으로 올리지 않는다(A19 가 따로 다룬다).

### 1.3 산출물
- 보고서: `docs/audit/findings/<ID>-<짧은 이름>.md` 새 파일 하나를 **문서만 담은 PR** 로 올린다(파일이 서로 달라 병합 충돌이 없다). CI `test` 통과·충돌 없음이면 스레드가 병합한다.
- 재현 시험: `docs/audit/findings/<ID>-repro/*.java.txt`(같은 PR). 컴파일 대상이 아니게 확장자를 `.txt` 로 둔다.
- 도미니코 확인·결정이 필요하면 `docs/user-todo/entries/2026-11-<일>-audit-<ID>.md` 새 파일.
- 끝나면 코디네이터에 항목 수(심각도별)와 PR 링크를 보고한다.

### 1.4 보고서 형식
맨 위에 기준 커밋, 조사한 범위, 조사하지 못한 범위(이유)를 적는다. 항목은 아래 표 한 줄씩, 심각도 높은 순.

| 칸 | 내용 |
|---|---|
| ID | `<파트>-<번호>` (예: `A03-07`) |
| 심각도 | 치명(복제·손상·충돌·보안 구멍) / 높음(눈에 띄는 바닐라 차이, 교착) / 중간 / 낮음 / 참고 |
| 종류 | 바닐라 차이 · Paper/Spigot 호환 · 스레드 안전 · 보안 · 성능 · 시험 공백 · 문서 |
| 근거 | Furnace `파일:줄`, 바닐라 대응 메서드(난독화 이름 포함), Paper 원문 위치 |
| 재현 | 시험 이름·명령과 기준 커밋에서의 결과. 재현 못 했으면 "가정"이라고 적는다 |
| 제안 | 고칠 범위(파일), 위험, 예상 크기(작음/중간/큼), 권장 모델·노력치 |

---

## 2. 파트별 상세

### A01 프로토콜·패킷·접속 상태 머신 — Sonnet high, 2차
- **범위**: `server/net`(`ConnectionHandler`, `NetworkServer`, `PacketTap`, `WritabilityGuard`, `ResourcePackResponses`, `PluginChannels`), `net/protocol`(144개 패킷 코덱), `net/io`, `net/registry`. 핸드셰이크→상태→로그인→구성→플레이 전환, 압축 임계값, keep-alive, 전송(transfer)·쿠키, 구성 단계 재진입.
- **볼 것**: 패킷마다 필드 순서·VarInt 경계·선택 필드·길이 상한이 바닐라 코덱과 같은지, 상태 전환 중 도착한 패킷 처리, keep-alive·시간 초과 값, 구성 단계 레지스트리 동기(데이터 팩·W2 바이옴 포함), 압축 켜고 끌 때 경계.
- **방법**: 바닐라 `StreamCodec` 바이트코드와 코덱 필드 순서를 표로 대조 → 차이 나는 패킷은 `ConcurrentEmbeddedChannel` 로 바이트 왕복 시험. 상태 전환 경합은 봇 `netjoin.js` 로 반복 접속.
- **입력**: `bot-connection-test-*.md`, `n3-iouring-metrics-2026-11-07.md`, `docs/api/packets.md`.
- **제외**: 악성 입력·속도 한도(A02), 패킷 API 의 플러그인 계약(A14).

### A02 보안·접속 방어·원격 관리 — Opus high, 1차
- **범위**: `net/auth`(세션 인증, 암호화, BungeeCord·Velocity 포워딩), `ConnectionLimits`·`TokenBucket`·`ReceiveRateWatch`·`ItemValidation`·`ProxyProtocolHandler`, `remote`(RCON·Query), 서명 채팅·채팅 신고 옵션, 명령 권한 경계(OP 레벨·플러그인 권한), **파일 경로를 받는 곳**(월드 가져오기, 백업 zip, 구조물 템플릿 이름, 데이터 팩, 라이브러리 내려받기 경로·체크섬, `RegionConvert`).
- **볼 것**: 인증 우회(포워딩 켜짐·꺼짐 조합, 서명 없는 BungeeCord), 큰·깊은 NBT/컴포넌트/책/명령 길이로 메모리·CPU 소모, 압축 폭탄, 슬로로리스류 접속 고갈, RCON 무차별 대입·평문, Query 증폭, `..`·절대 경로·zip slip, 플러그인 메시징 채널 크기, 권한 없는 명령 자동완성 노출.
- **방법**: 악성 클라이언트를 EmbeddedChannel 시험으로 만든다(잘린 프레임, 거대 VarInt, 깊은 NBT, 대량 패킷). 경로 입력은 임시 폴더 밖으로 쓰는지 시험. 각 한도를 실제로 넘겨 끊기는지와 서버 자원(힙·스레드) 변화를 측정.
- **입력**: `connection-defenses-2026-11-06.md`, `bungeecord-forwarding-2026-11-03.md`, `operator-options-o1-2026-11-06.md`, user-todo 결정(RCON IP당 4, 포워딩 경고).
- **제외**: 정상 패킷 코덱 일치(A01).

### A03 스케줄러·region·전역 스레드 안전 — Opus high, 1차
- **범위**: `server/sched`, `world/SharedScheduler`·`Regionizer`·`Region`·`RegionLevel`·`RegionBorrow`·`TickState`·`ChunkKeySet`, 전역 박동, region 합치기·나누기, region 경계 엔티티·블록 업데이트, `ShutdownSequence`, `ServerWatchdog`, `/tick` 얼림·가속, `idle-pause`.
- **볼 것**: 교착(전역↔region, region↔region, 모니터를 쥔 채 대기), region 합치기·나누기 도중 작업 유실·중복 실행, 경계 너머 블록 업데이트·엔티티 이동의 순서, 종료 중 새 작업, 공유 풀에서 한 차원 과부하가 다른 차원을 굶기는지, `/tick freeze`·`sprint` 와 region 틱 동기.
- **방법**: 스트레스 시험(무작위 region 합치기·나누기 + 경계 작업 수천 회, 반복 50회), `ThreadMXBean.findDeadlockedThreads` 감시, 기록된 실행 순서와 단일 스레드 기준 실행 비교. 결과는 반복 횟수와 실패 비율로 적는다.
- **입력**: `shared-scheduler-*.md`(설계·검토·S1~S4·기본값), `furnace-region-boundary` 메모, `tick-sprint-design-2026-10-28.md`, `tps-mspt-2026-11-03.md`.
- **제외**: PR #144(옛 동기 작업 region 빌리기), #155·#159(비동기 풀) 검토에서 이미 다룬 지적. 플러그인 쪽 스레드 계약(A14·A15).

### A04 복제·소실 탐색 — Opus high, 1차
- **범위**: 아이템·엔티티가 **두 곳에 동시에 있거나 사라질 수 있는** 모든 경로. 메뉴 클릭(Shift·숫자키·드래그·더블클릭), 컨테이너 블록·엔티티가 부서지거나 내려가는 순간, 깔때기·수레, 사망 드롭·`keepInventory`, 탈것째 포털·차원 이동, region 경계 넘기, 접속 끊김·킥 중 저장, 엔드 포털 중력 블록(옵션), 블록 엔티티 청크 내리기.
- **볼 것**: 바닐라에 없는 복제·소실만. 바닐라에도 있는 복제(옵션으로 연 것 포함)는 "참고"로.
- **방법**: 두 스레드가 같은 아이템을 만지는 장면을 반복 시험으로(같은 틱 메뉴 닫기+블록 부수기, 접속 끊김+텔레포트 등). 시험 끝에 전체 아이템 수 보존을 단언한다. 봇 두 개로 실제 패킷 순서도 한 번.
- **입력**: `furnace-death-drops-pr164`·`pr165` 검토(복제 사례), `furnace-passenger-portal` 메모, `furnace-itemstack-immutable`(칸 교체 규칙), `user-todo/entries/2026-11-08-gravity-block-portal-dupe.md`.
- **제외**: PR #164·#165·#166 지적 자체.

### A05 청크·저장·영속성 — Opus high, 1차
- **범위**: `world/anvil`(region 파일, 섹터 할당, 압축 lz4·zstd), `world/storage`, `players`(플레이어 데이터), 청크 적재·내리기·저장 레인, `level.dat`, `ForcedChunks`, 백업(O4), 종료·`save-all` flush, `datafix`(옛 월드·구조물), Bukkit 월드 가져오기.
- **볼 것**: 강제 종료(`kill -9`) 뒤 손상·되돌림 범위, 손상된 region·청크 NBT 를 만났을 때 바닐라처럼 넘어가는지(서버가 죽지 않는지), 바닐라 1.21.1 이 만든 월드와 왕복(엔티티·POI·블록 엔티티·높이맵·빛), 저장 중 같은 청크 재적재, 플레이어 데이터 저장과 접속 경합, 백업 zip 의 일관성.
- **방법**: 프로세스 시험(서버를 띄워 쓰기 중 kill → 다시 열어 검증), 손상 파일 주입 시험, 바이트 왕복 비교. 바닐라 jar 를 이 환경에서 못 돌리면 바닐라 바이트코드 대조 + 사용자 확인 칸.
- **입력**: `perf-chunkio-2026-11-06.md`, `f05-sector-allocator-2026-10-28.md`, `f14-generated-chunk-save-2026-10-27.md`, `backup-idle-pause-o4-2026-11-07.md`, `bukkit-world-import-2026-11-03.md`, `structure-datafixer-2026-11-07.md`, M12 기록.
- **제외**: 청크 전송 속도(A17), 월드젠 결과(A06).

### A06 월드젠 바닐라 일치 — Opus high, 2차
- **범위**: `world/gen`(노이즈·표면·동굴·장식·구조물), 월드 타입(normal·large_biomes·amplified·flat·debug·single_biome), 구조물 배치·조각, `/locate`, 탐험 지도(`skipKnownStructures`), 다중 청크 장식 제약, W3 훅이 꺼져 있을 때 결과가 같은지.
- **볼 것**: M13 에서 닫지 못한 **구조물 순정 대조**(정글 신전·늪 오두막·해저 신전·전초기지·고대 도시·시련의 방·보루·트레일 유적), `/locate` 결과, 큰 시드·음수 좌표·월드 끝 근처.
- **방법**: worldgen-oracle·바닐라 실행은 이 환경에서 403 이라, ① 바닐라 데이터 JSON 바이트 대조 ② `tools/parity-audit` 도구(`StructCmp`·`ChunkDiff`·`StructScan`)와 기존 fixture ③ 바닐라 바이트코드로 난수 호출 순서 대조. 바닐라 실행이 꼭 필요한 대조는 user-todo 확인 표로 넘긴다.
- **입력**: `audit-worldgen-2026-10-27.md`, `worldgen-measurement-2026-10-08.md`, `checklist.md`, `unimplemented-recheck-2026-11-07.md` 5장, `LLM-Wiki/wiki/Furnace/` 월드젠 기록, `tools/parity-audit/README.md`.
- **제외**: 월드젠 성능(A17), 저장(A05).

### A07 레드스톤·피스톤·업데이트 순서 — Opus high, 1차
- **범위**: 레드스톤 가루·중계기·비교기·관측기·횃불·피스톤(밀기 한도·끈적·블록 엔티티)·레일(동력·감지·작동)·디스펜서·소리 블록·표적·스컬크 감지기의 신호, 이웃 업데이트·모양 업데이트 전파 순서, 예약 틱 우선순위, region 경계에서의 순서.
- **볼 것**: 업데이트 순서가 바닐라와 같은지(기계 장치 동작이 순서에 기대므로 관측 결과 차이), 0틱·1틱 펄스, 준연결(QC), 블록 36개 넘게 미는 피스톤, 경계 걸친 장치. X1 이 자료 구조를 바꾼 레드스톤·동력 레일 경로를 특히 본다.
- **방법**: 유명한 장치(T 플립플롭, 0틱 펄스, 관측기 시계, 비행 기계, 무한 철로)를 시험 월드에 놓고 틱마다 상태 기록 → 바닐라 바이트코드로 계산한 기대값과 비교. 경계 장면은 장치를 region 경계에 걸쳐 놓는다.
- **입력**: `perf-x1-2026-11-07.md`, `x1-pr133` 검토, `vanilla-diff-audit-2026-10-28.md`.
- **제외**: 레드스톤 성능(A17).

### A08 일반 블록·유체·무작위 틱·빛 — Sonnet high, 2차
- **범위**: `block/types` 중 레드스톤 외(식물 성장·번짐, 얼음·눈·가루눈, 불 번짐, 중력 블록, 문·다락문, 침대, 덩굴), 유체(물·용암 흐름·섞임·폭포), 무작위 틱 분포(`randomTickSpeed`), `light`(블록·하늘 빛 전파, 섹션 경계), 블록 설치·부수기 규칙(설치 가능 판정·도구·채굴 속도).
- **볼 것**: 패키지 이름 기준 시험 코드가 적은 영역(`block/types` 본문 약 1.7만 줄에 같은 이름 시험 폴더 약 460줄, 다른 폴더 시험은 세지 않음)이라 **동작 차이와 시험 공백을 같이** 본다. 메모의 작은 후보(가루눈 녹이기, 불·용암·가마솥 상호작용)도 여기서 확인.
- **방법**: 블록 종류별 바닐라 메서드(`randomTick`, `updateShape`, `neighborChanged`, `canSurvive`)와 줄 단위 대조 표를 만들고, 차이마다 단위 시험. 빛은 섹션 경계·청크 경계 무작위 장면을 바닐라 알고리즘 구현과 비교.
- **입력**: `unimplemented-survey-2026-10-31.md`, `m17` 묶음 기록, `vanilla-audit-2026-10-07.md`.
- **제외**: 레드스톤(A07), 블록 엔티티(A09).

### A09 블록 엔티티·메뉴·거래 — Sonnet high, 2차
- **범위**: `block/entity`(화로·훈연기·용광로, 양조기, 깔때기, 신호기, 상자류, 셜커 상자, 표지판, 스포너, 벌집, 캠프파이어, 독서대, 조각된 책장, 크래프터, 시련 스포너·금고), `menu`(제작대·모루·숫돌·베틀·석재 절단기·대장장이 작업대·마법 부여대·지도 제작대), `trade`(주민·떠돌이 상인 거래, 가격, 재고 보충), `world/*Container`.
- **볼 것**: 메뉴별 Shift 클릭 순서·결과 칸 규칙, 경험치 지급(화로), 모루 비용·이름 길이, 마법 부여 시드, 크래프터 순서, 거래 재고·수요 가격, 시련 금고 보상.
- **방법**: 메뉴마다 `quickMoveStack` 바닐라 대조 표 + 단위 시험. 거래는 고정 시드로 바닐라와 같은 제안이 나오는지.
- **입력**: `m3-pr85`·`m8`·`m18` 검토와 기록, `user-todo/entries/2026-11-08-item-contents-slots.md`, `item-slots-pr169` 검토.
- **제외**: 복제 경로(A04 — 발견하면 A04 로 넘김), 플러그인 메뉴 API(A14).

### A10 몹 AI·두뇌·스폰 — Opus high, 1차
- **범위**: `entity/ai`(목표·센서·두뇌 활동), 길찾기·노드 평가, 몹 종류별 행동(피글린·호글린·워든·아홀로틀·개구리·낙타·스니퍼·아르마딜로·브리즈·보그드), `entity/spawn`(자연 스폰·한도·구조물 스폰), `world/poi`, `world/raid`.
- **볼 것**: 난수 호출 순서가 바닐라와 같은지(스폰·AI 결정 재현성), 두뇌 기억 만료 틱, 길찾기 결과(문·물·용암·울타리), 스폰 한도·밀도 계산, 습격 파도. 메모의 후보(몹 AI 난수 시험 안정화)를 이유와 함께 확인.
- **방법**: 고정 시드 장면에서 틱마다 몹 위치·목표·기억을 기록해 바닐라 바이트코드 기반 기대와 비교. 몹 종류마다 바닐라 `registerGoals`·`makeBrain` 의 목표·활동 목록과 우선순위를 표로 대조(빠진 목표 찾기).
- **입력**: `mob-ability-audit-2026-10-31.md`, `vanilla-diff-audit-2026-10-28.md` 후속 처리, `m4`·`m5`·`m14` 기록, `docs/api/mob-ai.md`·`mob-brain.md`.
- **제외**: 전투 피해 계산(A11), AI 성능(A17).

### A11 엔티티 물리·전투·효과 — Sonnet high, 2차
- **범위**: 엔티티 이동·충돌·밀림·물·용암·가루눈 속 움직임, 낙하 피해, 피해 계산(방어구·보호 마법·저항·흡수), 넉백, 무적 시간, `effect`(상태 효과 틱·즉시 효과), 발사체(화살·삼지창·눈덩이·바람 돌격), 탈것 조종(말·돼지·스트라이더·낙타·배·수레), 플레이어 이동 검증(`moved wrongly`).
- **볼 것**: 수치 결과 차이(피해·넉백 거리·낙하 높이), 탈것 위 이동 검증 오탐, 효과 중첩·만료. 메모 후보(`processMoves` 텔레포트 가지 탄 상태 검사, 몹 불 보호·`is_on_fire`)를 확인.
- **방법**: 수치 시험(같은 입력→바닐라 공식으로 계산한 기대값), 봇으로 탈것 이동 검증.
- **입력**: `m6`·`m20`·`m21`·`movement-pr152`·`teleport-window-pr157`·`saturation-pr162`·`effect-give-instant-pr163` 검토, `perf-entity-2026-11-06.md`.
- **제외**: 이동 검증의 보안 측면(A02 는 패킷 단위, 여기는 바닐라 결과).

### A12 아이템·레시피·전리품·진행 — Sonnet high, 3차
- **범위**: `item`(아이템 컴포넌트, 사용·소모·내구도·수리), `recipe`(제작·화로·대장장이·특수 레시피, 레시피 북), `loot`(전리품 표·함수·조건), `advancement`(트리거 전부), `stats`, `scores`(점수판·팀), `map`(지도 렌더·깃발·장식).
- **볼 것**: 컴포넌트 왕복(NBT·네트워크), 특수 레시피(폭죽·깃발·책 복사·지도 복사), 전리품 함수·조건 필드, 발전 과제 트리거가 빠지거나 두 번 불리는지, 통계 값.
- **방법**: 데이터 주도 대조(바닐라 데이터의 레시피·전리품·발전 과제 전체를 읽어 모두 해석되는지, 해석 실패 0 확인), 트리거별 단위 시험.
- **입력**: `advancement-triggers-2026-10-28.md`, `m18` 기록, `compat-item-serialization-2026-11-08.md`.
- **제외**: 메뉴 결과 칸(A09), 아이템 복제(A04).

### A13 명령·함수·데이터 팩·텍스트 — Sonnet high, 3차
- **범위**: `command`(Brigadier 트리, 인자 해석, 선택자, `/execute` 전부, 권한 레벨), `function`(함수·매크로·`/schedule`·태그 `#load`·`#tick`), 데이터 팩 적재·`/reload`·`/datapack`, `chat` 텍스트 컴포넌트(번역 키·선택자·점수 해석), 명령 블록.
- **볼 것**: 명령 출력 문구·반환값·성공 횟수, 오류 문구, 탭 완성 제안, 함수 실행 한도(`maxCommandChainLength`), `/reload` 뒤 상태(레시피·전리품·태그·함수), 데이터 팩 오류 처리.
- **방법**: 바닐라 명령 목록 전체를 표로 → 명령마다 성공·실패 사례 시험. 출력 문구는 바닐라 번역 키와 대조.
- **입력**: `command-audit-m11-2026-11-07.md`, `execute-function-design-2026-10-27.md`, `commands-pr153`·`command-followup-pr158`·`minecraft-alias-pr156` 검토, `docs/api/commands.md`·`function-hooks.md`.
- **제외**: 플러그인 명령 API 계약(A14·A15), M11·PR #153·#156·#158 지적.

### A14 Furnace 플러그인 API 계약 — Opus high, 2차
- **범위**: `furnace-api` 전체와 `server/plugin`(등록소, 이벤트 버스·발화 스레드, 취소 계약, 원인 사슬, 부트스트랩, 라이프사이클·disable 정리, 핫 리로드), `plugin/packet`(패킷 API), 뷰(K2·K3), 가상 엔티티(K1), 빛(W6), 구조물·청크 티켓(W4), 월드젠 훅(W3), 레지스트리(D2·W2), 헤드리스 시험 서버(`furnace-test`).
- **볼 것**: 문서(`docs/api/*`)에 적힌 계약과 실제 동작의 차이, 이벤트를 어느 스레드에서 부르는지와 문서 일치, 취소했을 때 클라이언트 재동기화 빠짐, disable·핫 리로드 뒤 남는 구독·작업·스레드·클래스로더(누수), API 안정성 정책 위반(공개 타입에 내부 타입 노출).
- **방법**: 계약마다 시험 플러그인(`JarCompiler` 로 시험 중 컴파일)으로 확인. 누수는 리로드 100회 뒤 클래스로더 수·스레드 수 측정. 이벤트 발화 지점은 `graphify` 로 전부 찾아 표로.
- **입력**: `docs/api/*.md`, `docs/policy/api-stability.md`·`behavior-guarantees.md`, `furnace-plugin-hot-reload`·`furnace-cause-chain` 기록, `k4-pr168` 검토.
- **제외**: Bukkit 이름으로 보이는 표면(A15).

### A15 Bukkit·Paper API 표면 호환 — Sonnet high, 3차
- **범위**: `furnace-compat-paper` 의 `org/bukkit/*`, `compat/paper`(이벤트 다리 등), `compat/sched`, `compat/command`, `compat/thread`, `plugin.yml`·`paper-plugin.yml` 로더, 설정 YAML(`FileConfiguration`), 권한·메타데이터·PDC, 플러그인 메시징, `SimplePluginManager`.
- **볼 것**: 이벤트 다리의 발화 시점·순서·취소 결과가 Paper 와 같은지, 스케줄러 의미(동기 작업 스레드·지연 0), 인벤토리 뷰·클릭 이벤트, YAML 왕복(주석·순서), `isPrimaryThread()` 등 스레드 질문의 답.
- **방법**: 인기 플러그인이 실제로 부르는 API 목록(`compat-h4-coverage-2026-11-07.md`)을 기준으로 호출마다 Paper 원문과 대조 → 차이마다 시험. 실제 플러그인 jar 가 있으면(결정 8) 기동 시험.
- **입력**: `compat-h1~h4*`, `compat-javaplugin-wiring`, `compat-h4-helper-barriers`, `compat-h4-simplepluginmanager`, `compat-h4e-messaging`, `paper-vs-spigot-2026-11-08.md`, `compat-death-*`, `compat-hologram-entities`, `compat-item-serialization`.
- **제외**: H4 묶음 C·D(대기 중), NMS·CraftBukkit(A16), H1~H4·#146·#148·#150·#161 검토 지적.

### A16 NMS·CraftBukkit·클래스로더 — Opus high, 3차
- **범위**: `compat/nms`, `net/minecraft/*` shim, `org/bukkit/craftbukkit/*`(`getHandle` 등), ASM 변환(버전 접미사, 덫, 정의 거부), Spigot→Mojang 대응표(H7d), 플러그인 클래스로더·`plugin/library`(라이브러리 공유 로더·체크섬), Paper API 지연 로딩.
- **볼 것**: 변환이 바이트코드를 깨는 경우(검증기 오류), 덫이 정상 코드(null `checkcast`·`instanceof`)를 잡는지 재확인, 클래스로더 누수·위임 순서(플러그인끼리 클래스 공유, 라이브러리 버전 충돌), 대응표 메모리(약 80MB) 처리, shim 값이 실제 서버 상태와 어긋나는 곳.
- **방법**: 일부러 꼬인 바이트코드 플러그인(람다·indy·리플렉션·`MethodHandles`)을 시험 중 컴파일해 변환·로드. 실제 NMS 플러그인 jar 가 생기면 그것으로.
- **입력**: `compat-h5*`, `compat-h6`, `compat-h7`, `compat-h7d`, `compat-h4b-libraries`, `h5`·`h6`·`h7`·`h7d-pr160`·`h4b`·`paper-isolation-pr150` 검토.
- **제외**: H7d 실제 표 측정(대기 중), 이미 검토에서 고친 항목.

### A17 성능·확장성 — Opus high, 3차
- **범위**: 틱 비용(청크 틱, 블록 엔티티, 엔티티), 많은 접속자(50·100·200 봇), 엔티티 수천 마리, 청크 전송·생성, 메모리(청크·섹션 공유, 패킷 버퍼), GC 정지, 기동·종료 시간, 공유 region 풀 확장성.
- **볼 것**: X1 이 남긴 후보 4개(섹션 비트마스크, `present` 순회 재사용, `getNearestPlayer`, 많은 접속자 재측정), 플레이어 수에 비례해 커지는 O(n²) 경로, 동기화로 인한 경합, 바닐라·Paper 대비 MSPT.
- **방법**: 기존 측정 장면(`perf-x1`·`perf-entity`·`perf-chunk-send`·`perf-multiplayer-chunks`)을 그대로 다시 돌려 기준값을 갱신하고, JFR 로 상위 비용을 표로. 고칠 후보마다 "관측 결과가 같다"는 시험 방법을 함께 제안.
- **입력**: `perf-summary-2026-11-07.md`, `perf-c1~c5`, `perf-x1-2026-11-07.md` 남은 후보, `furnace-chunk-send-rate` 기록, `shared-scheduler-bench-overload-2026-10-27.md`.
- **제외**: 정확성 버그(다른 파트로 넘김).

### A18 운영·설정·관측 — Sonnet medium, 3차
- **범위**: `config`(`server.properties`·`furnace.properties` 키, 기본값, 오타 경고, `${ENV}`, 마이그레이션), Paper/Spigot 설정 가져오기(O3), 콘솔(JLine·Ctrl+C 확인), `metrics`(Prometheus·JMX), `profiling`(`/perf`), `maintenance`, `CrashReport`, `ServerWatchdog`, 로그 문구, `start.sh`·`start.bat`·`jvm.args.example`.
- **볼 것**: 결정 R4(새 옵션 전부 바꿀 수 있고 기본값은 바닐라와 같음)를 모든 키에 대해 표로 확인, `server.properties` 의 바닐라 키 전부가 같은 의미로 읽히는지, 잘못된 값 처리, 위험한 옵션 경고가 시작 로그·`/furnace config` 에 나오는지, 감시견이 멈춘 region 을 알려 주는지.
- **방법**: 설정 키 전체를 코드에서 뽑아 표(키·기본값·바닐라 기본값·경고 여부)로 만들고 차이마다 시험.
- **입력**: `option-keys-2026-11-07.md`, `config-tools-o2-2026-11-07.md`, `paper-config-import-2026-11-07.md`, `paper-config-keys-2026-10-27.md`, `operator-options-o1-2026-11-06.md`.
- **제외**: 접속 방어 한도 값의 보안 판단(A02).

### A19 시험·CI 공백 — Sonnet medium, 2차
- **범위**: 모든 모듈의 시험 배치와 공백, 흔들리는 시험, `.github/workflows` CI(조각 3개·재시도·보고서), `tools/ci/shard-balance.py`, 봇 시험 스크립트의 회귀 시험화 가능성, `furnace-test` 헤드리스 서버를 서버 시험에 쓸 수 있는지.
- **볼 것**: 시험이 거의 없는 패키지(같은 이름 시험 폴더 기준 `block/types`, `entity/ai`, `item`, `world/poi`, `players`, `datafix`, `scores`, `net/auth` 가 적다 — 다른 폴더의 시험까지 세어 확정할 것), 단언 없이 통과하는 시험, 실패만 재시도로 가려지는 시험(최근 CI 기록에서 재시도로 통과한 시험 목록), 시간 의존 시험.
- **방법**: 시험 커버리지(JaCoCo) 한 번 돌려 패키지별 줄·가지 비율 표, CI 실행 기록에서 재시도 통과 시험을 모은다. 흔들리는 시험은 단독 50회 반복으로 실패율을 잰다.
- **입력**: `docs/ci.md`, `docs/api/testing.md`, `furnace-headless-test-server` 기록, `pr127` 검토.
- **제외**: 각 기능의 동작 차이(해당 파트).

### A20 문서 정합 — Haiku low, 1차
- **범위**: `docs/api/*.md`(API 이름·예시가 실제 코드와 맞는지), `docs/policy/*`, `docs/user-todo.md`·`docs/user-todo/entries/*`(이미 끝난 항목이 남았는지), `docs/handoff/2026-10-31-session-handoff.md`, `CLAUDE.md`·`AGENTS.md`, `README.md`.
- **볼 것**: 사라진·이름이 바뀐 클래스·메서드를 가리키는 문서, 예시 코드가 컴파일되지 않는 것, user-todo 의 낡은 줄(병합된 묶음의 "남은 것"), 인수인계서 4장 "다음 할 일"이 현재와 맞는지.
- **방법**: 문서의 코드 이름을 모두 뽑아 저장소에서 찾고(grep·`graphify query`), 예시 코드는 `furnace-plugin-test` 에 임시로 넣어 컴파일해 본다(커밋 안 함).
- **입력**: 위 파일 전부.
- **제외**: 문서 내용의 기술적 옳음 판단(해당 파트가 함께 본다).

---

## 3. 파트 사이 경계 (헷갈리기 쉬운 것)

| 주제 | 맡는 파트 | 넘기는 경우 |
|---|---|---|
| 아이템 복제·소실 | A04 | 다른 파트가 찾으면 A04 ID 로 옮겨 적고 원 파트 보고서엔 한 줄만 |
| 패킷 | A01 코덱·상태, A02 악성 입력, A14 플러그인 패킷 API | |
| 스레드 | A03 코어, A04 복제, A14 플러그인 이벤트 스레드, A15 Bukkit 스케줄러 | |
| 성능 | A17 | 다른 파트는 "성능 의심"을 참고로만 적고 A17 로 넘김 |
| 이동 | A11 바닐라 결과, A02 이동 검증 악용 | |
| 메뉴 | A09 바닐라 메뉴, A14 플러그인 `Menu` API, A15 Bukkit `Inventory` | |
| 데이터 팩 | A13 적재·`/reload`, A12 내용 해석, A06 월드젠 데이터 | |

---

## 4. 이미 끝난 대조·감사 (입력으로만 쓰고 다시 하지 않는다)

- 1차 감사: `audit-runtime-2026-10-27.md`, `audit-worldgen-2026-10-27.md`, `vanilla-diff-audit-2026-10-28.md`, `vanilla-audit-2026-10-07.md`, `vanilla-diff-2026-10-07.md`.
- 미구현 대조: `unimplemented-survey-2026-10-31.md`, `unimplemented-recheck-2026-11-07.md`(M13), `mob-ability-audit-2026-10-31.md`.
- 묶음별 바닐라 대조 기록: `docs/feature-parity/context-notes.md`, `docs/feature-parity/context-notes/*.md`.
- 사용자 확인 대기(실제 클라이언트로만 볼 수 있는 것): `docs/user-todo.md` 1장, `docs/user-todo/entries/*.md`. 조사관은 이 항목을 다시 올리지 않고, 서버 쪽에서 재현되는 문제가 있을 때만 올린다.

## 5. 독립 검토가 끝난 PR (지적을 다시 올리지 않는다)

검토 보고서는 프로젝트 공유 폴더 `furnace/reviews/` 에 있다.

| 파트 | 검토된 PR (보고서 이름) |
|---|---|
| A01·A02 | `p3-chat-pr151`(채팅·표시 이름) |
| A03 | `legacy-sync-pr144`, `async-pr155`, `async-followup-pr159`, `m10-pr114`(차원 이동·차원별 보더), `pr129`(포털 미리 올리기) |
| A04 | `death-drops-pr164`, `death-drops-pr165`, `death-cancel-keep-pr166`, `pr126`(탈것째 포털), `pr142`(엔드 포털 중력 블록 복제 옵션) |
| A05 | `q8-pr119`, `m12-pr117`, `m12-pr120` |
| A06 | `w4-pr128`, `m19-pr109`(월드 타입) |
| A07 | `x1-pr133` |
| A08 | `m17-pr121`(블록 잔여), `m2-pr115`(스컬크) |
| A09 | `m3-pr85`, `item-slots-pr169`, `m18-pr124` |
| A10 | `m14-pr112` |
| A11 | `m6-pr116`, `m20-pr122`, `m21-pr123`, `movement-pr152`, `teleport-window-pr157`, `saturation-pr162`, `effect-give-instant-pr163`, `small-parity-pr154` |
| A13 | `commands-pr153`, `command-followup-pr158`, `minecraft-alias-pr156` |
| A14 | `k4-pr89`, `k4-pr168` |
| A15 | `h1-pr132`, `h2-pr130`, `h3-pr135`, `h4-pr136`, `h4a-pr138`, `h4e-pr146`, `spm-pr148`, `helper-barriers-pr161` |
| A16 | `h5-pr137`, `h6-pr139`, `h7-pr143`, `h7d-pr160`, `h4b-pr145`, `paper-isolation-pr150` |
| A18 | `m19-pr109`(`/perf`·JMX) |
| A19 | `pr127`(CI 조각·재시도), `pr131`(시험 채널 재진입) |

## 6. 시작 순서 (결정 R5: 6개 안팎씩)

조사는 읽기와 시험만 하므로 파트끼리 파일이 겹쳐도 병합 충돌은 없다(보고서 파일이 다르다). 순서는 위험이 큰 것과 다른 파트의 입력이 되는 것을 앞에 둔다.

| 차수 | 파트 | 이유 |
|---|---|---|
| 1차 | A02, A03, A04, A05, A07, A10, A20 | 손상·복제·보안·교착처럼 치명적일 수 있는 것과, 기계 장치·몹처럼 바닐라 결과 차이가 크게 보이는 것. A20 은 싸고(Haiku) 다른 조사관이 볼 문서를 먼저 바로잡는다 |
| 2차 | A01, A06, A08, A09, A11, A14, A19 | 1차 결과로 고친 뒤 봐야 덜 겹치는 것. A19 는 1차 조사관이 만든 재현 시험을 어디에 둘지 정하는 데 쓰인다 |
| 3차 | A12, A13, A15, A16, A17, A18 | 호환 계층은 H4 묶음 C·D 가 끝난 뒤가 낫고, 성능은 정확성 수정 뒤에 재야 기준값이 오래 간다 |

각 차수의 보고서가 2단계(점검 스레드)로 넘어간 뒤 다음 차수를 연다. 점검 스레드는 차수마다 하나(Opus high)를 두고, 보고서 묶음을 한꺼번에 검토해 중복·경계 넘는 항목을 정리한다.

## 7. 조사 스레드를 열 때 넣을 지시 (코디네이터용 틀)

> 2차 전체 점검 1단계 조사 — 파트 `<ID> <이름>`. `docs/audit/second-audit-plan.md` 의 1장(공통 규칙)과 2장 `<ID>` 절을 따른다. 코드는 고치지 않고, 보고서 `docs/audit/findings/<ID>-<이름>.md` 와 재현 시험 `docs/audit/findings/<ID>-repro/*.java.txt` 를 문서만 담은 PR 로 올린다. CI `test` 통과·충돌 없음이면 병합. 도미니코의 확인·결정이 필요한 것은 `docs/user-todo/entries/<날짜>-audit-<ID>.md` 새 파일. 끝나면 코디네이터에 심각도별 항목 수와 PR 링크를 보고. 모델 `<모델>`(`<노력치>`).
