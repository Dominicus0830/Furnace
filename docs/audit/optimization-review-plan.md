# 전 모듈 최적화·기능 검토 — 계획 (2026-11-12)

도미니코 지시(2026-11-12): 모든 부분을 하나씩 **최적화, 성능 개선(구조를 바꿔야 하면 검토), 기능 개선, 추가할 것** 관점으로 검토하고, 검토 프로세스(독립 검토자 + 토론)를 거쳐 진행할 TASK 목록을 만든다. 진행 중인 작업이 끝난 뒤 적절한 때에 시작한다.

이 문서는 계획만 담는다. 코드를 고치지 않는다. 결과물은 마지막에 `docs/roadmap/optimization-tasks.md`(TASK 목록)로 나온다.

설계 원칙(변하지 않음): 바닐라·Paper·Spigot 과 **관측 결과**가 같아야 한다. 스레드·자료 구조·내부 구조는 Furnace 가 정한다. 그래서 모든 성능 제안은 "결과가 같다는 것을 어떻게 증명하는가"를 함께 적어야 채택된다.

2차 점검(`second-audit-plan.md`)과의 관계: 2차 점검은 **정확성**(바닐라와 다른 결과)을 찾았다. 이번 검토는 **더 빠르게·더 낫게·더 많이**를 찾는다. 2차 점검의 남은 A17(성능)·A18(운영)은 이 검토의 O00·O14 로 흡수한다.

---

## 0. 한눈에 보기

코드 규모(2026-11-12, 기준 브랜치 `claude/quirky-wright-lra834` `3ae317c6`): `furnace-server` 약 25.8만 줄(시험 774개), `furnace-compat-paper` 4.2만 줄, `furnace-api` 2.7만 줄.

| ID | 파트 | 핵심 범위(`furnace-server/.../server/` 기준) | 규모 | 모델·노력치 | 차수 |
|---|---|---|---|---|---|
| O00 | 전체 성능 기준선·핫스팟 지도 | 실서버 + 봇 장면 측정, 모든 벤치 실행, JFR | 측정 | Opus high | 1 |
| O01 | 스케줄러·region·틱 루프 | `sched`, `world/` 루트의 `Regionizer`·`Region`·`SharedScheduler`·`RegionBorrow`·`TickState`, 전역 박동, `ServerWatchdog` | 약 1.5만 | Opus high | 1 |
| O02 | 청크 수명·저장·IO | `world/anvil`, `world/storage`, `ChunkColumn`·청크 적재/내리기/티켓, `datafix`, `players`, 백업 | 약 1.2만 | Opus high | 1 |
| O03 | 네트워크·청크 전송 | `net`(`protocol`·`io`·`auth`·`registry`), 청크 직렬화·배치·압축·flush, 엔티티 추적 패킷 묶음 | 약 1.3만 | Opus high | 1 |
| O04 | 월드젠 | `world/gen`(노이즈·표면·장식·구조물·`/locate`) | 약 3.7만 | Opus high | 1 |
| O05 | 엔티티 틱·물리·추적 | `entity/` 루트(이동·충돌·피해·효과·발사체·탈것), `entity/view`, `entity/damage`, `effect`, `attribute` | 약 5.6만 | Opus high | 1 |
| O06 | 몹 AI·길찾기·스폰 | `entity/ai`, `entity/spawn`, `entity/gossip`, `world/poi`, `world/raid`, `world/gameevent` | 약 1.9만 | Opus high | 2 |
| O07 | 블록 틱·유체·빛 | `block/` 루트, `block/types`(레드스톤 외), `world/block`, `light`, 무작위 틱 | 약 2.5만 | Opus high | 2 |
| O08 | 레드스톤·피스톤·레일·호퍼 | 레드스톤 부품, 피스톤, 관측기, 레일, 블록 업데이트 전파, 호퍼 | (O07 안) | Opus high | 2 |
| O09 | 블록 엔티티·메뉴·인벤토리·아이템·거래 | `block/entity`, `block/dispenser`, `menu`, `item`, `trade` | 약 1.9만 | Sonnet high | 2 |
| O10 | 레시피·전리품·진행·통계·점수·지도 | `recipe`, `loot`, `advancement`, `stats`, `scores`, `map`, `bossbar` | 약 1.1만 | Sonnet high | 3 |
| O11 | 명령·함수·데이터 팩·채팅 | `command`(brigadier·arguments·exec·builtin), `function`, `data`, `chat` | 약 2.4만 | Sonnet high | 3 |
| O12 | Furnace 플러그인 API·패킷 API·이벤트 | `furnace-api` 전체, `server/plugin`(`packet`·`library`), 이벤트 디스패치, 핫 리로드 | 약 4.7만 | Opus high | 2 |
| O13 | Bukkit·Paper·NMS 호환 계층 | `furnace-compat-paper` 전체(어댑터, `net/minecraft/*` shim, CraftBukkit, ASM, 클래스로더) | 약 4.2만 | Opus high | 3 |
| O14 | 설정·운영·관측·기동·메모리 | `config`, `maintenance`, `profiling`, `metrics`, `log`, `remote`, 기동 시간, 힙·GC 기본값, `start.sh`·`jvm.args.example` | 약 0.5만 + 전역 | Sonnet high | 2 |
| O15 | 시험·벤치·CI 기반 | `furnace-test`, 벤치 13개, 성능 회귀 장치, CI 시간, 봇 시험 회귀화 | 기반 | Sonnet medium | 3 |

Fable 은 표에 배정하지 않는다. O00 의 장시간 측정 실행만 코디네이터 판단으로 Fable 계산·실행 대기열에 넘길 수 있다(대기열 규칙: 한 번에 하나).

---

## 1. 모든 검토자에게 공통인 규칙

### 1.1 네 가지 관점 — 항목마다 하나를 붙인다
- **최적화(OPT)**: 지금 구조 안에서 비용 줄이기(할당, 중복 계산, 잠금, 자료 구조, 캐시).
- **구조 변경(STR)**: 스레드 모델·소유권·파이프라인·자료 배치를 바꿔야 얻는 이득. 영향 범위와 단계 나누기를 함께 적는다.
- **기능 개선(FIX+)**: 이미 있는 기능을 더 낫게(설정 가능, 오류 메시지, 운영 편의, API 쓰기 쉬움). 바닐라 결과를 바꾸는 것은 제외하거나 옵션(기본 꺼짐)으로만.
- **추가(ADD)**: 없는 것 중 넣을 가치가 있는 것(Paper·Spigot 이 가진 운영 기능, 플러그인 개발자 불만 표 `paper-spigot-dev-complaints-2026-11-06.md` 의 항목 등).

### 1.2 하지 않는 것
- **코드를 고치지 않는다.** 읽기·측정·시험 작성만. 시험·벤치 코드는 저장소에 커밋하지 않고 `.java.txt` 로 보고서 옆에 둔다.
- 정확성 버그(바닐라와 결과가 다름)를 찾으면 "정확성" 칸에 한 줄로 적고 깊이 파지 않는다(별도 작업으로 넘김).
- 이미 끝난 성능 작업을 다시 제안하지 않는다(4장). 그 결과를 **넘어서는** 근거가 있을 때만.
- 측정 없는 성능 주장은 "가설"로 표시한다. 가설은 TASK 가 아니라 "측정 TASK"가 된다.

### 1.3 항목 형식(보고서의 표)
| 칸 | 내용 |
|---|---|
| ID | `P-<파트>-<번호>` (예: `P-O04-03`) |
| 관점 | OPT / STR / FIX+ / ADD |
| 무엇 | 한두 줄 |
| 근거 | 측정값(벤치·JFR·봇), 코드 위치(`파일:줄`), Paper 가 같은 일을 어떻게 하는지 |
| 기대 이득 | 숫자(예: 새 청크 CPU -15%)와 측정 조건. 없으면 "가설" |
| 결과 동일 증명법 | 해시 벤치(`NoiseIdentityBench`)·옛 구현 대조 무작위 시험·바닐라 바이트코드 대조 중 무엇으로 |
| 위험 | 바닐라 결과·플러그인 호환·스레드 안전 위험 |
| 비용 | S(반나절) / M(하루) / L(여러 PR) |
| 의존 | 먼저 끝나야 할 항목·PR |
| 권장 모델 | 1순위 규칙대로 |

### 1.4 측정 방법(이미 있는 도구를 먼저 쓴다)
- 벤치: `RegionTickBench`, `EntityPerfBench`, `EntityViewTrackerBench`, `ChunkNetBench`, `MultiDimensionTickBench`, `MultiPlayerChunkSendBench`, `PlayerWalkTickBench`, `ChunkIoPipelineBench`, `EntityStoreLockBench`, `ChunkGenBenchmark`, `NoiseIdentityBench`, `PristineBench`, `PristineHashBench` (`-Dfurnace.bench=true`).
- 실서버: fatJar + 봇(`tools/bot-test/`, 청크는 `tools/bot-test/chunkrate/vanillaclient.js` — mineflayer 는 청크 속도 측정 불가).
- 컨테이너 속도가 날마다 다르다(같은 커밋이 14.6초→24.5초). 전/후 비교는 **같은 컨테이너에서 기준 커밋을 다시 빌드해 교대로 3회**.
- Paper 비교가 필요하면 `furnace-worldgen-vs-paper` 메모의 방법(도미니코 PC 의 Paper jar)을 쓴다. 기기 연결이 필요하므로 O00 에서만, 꼭 필요할 때.
- gradle 은 한 컨테이너에서 동시에 하나만.

### 1.5 산출물 위치
- 보고서: 프로젝트 공유 폴더 `furnace/reviews/opt-review/<ID>-<이름>.md`, 재현·벤치 `furnace/reviews/opt-review/<ID>-repro/*.java.txt`. **PR 을 만들지 않는다**(읽기 전용 검토라 CI·충돌이 없다).
- 도미니코 결정이 필요한 것: 보고서 끝 "결정 후보" 절에 적는다. `docs/user-todo/entries/` 파일은 판정 단계(3장)에서 이 스레드가 한 번에 만든다.

---

## 2. 파트별 상세

### O00 전체 성능 기준선·핫스팟 지도 — Opus high, 1차
- **할 일**: 다른 파트가 쓸 공통 숫자를 만든다. 장면: ①빈 서버 유휴, ②봇 1·8·32명 새 땅 이동(시야 10·16), ③이미 만든 땅 접속, ④몹 농장(엔티티 1000·3000), ⑤레드스톤·호퍼 장면, ⑥기동 시간·첫 접속까지 시간, ⑦힙 사용·GC 멈춤. 장면마다 MSPT, CPU, 할당률, JFR 상위 30 프레임을 **모듈별로 묶어** 표로.
- **산출**: "모듈별 CPU·할당 지분" 표 — 각 파트 검토자가 자기 모듈 비중을 보고 우선순위를 잡는다. 벤치 전체 실행 결과(기준값).
- **제외**: 고칠 방안 설계(각 파트가 한다). 단 파트 경계에 걸친 핫스팟은 O00 이 적는다.

### O01 스케줄러·region·틱 루프 — Opus high, 1차
- **볼 것**: region 합치기·나누기 비용, 전역 박동 대기 시간, 스레드 풀 크기·작업 훔치기, 빈 region 틱 비용, `tickChunk` 구역 훑기(X1 이 남긴 후보: 빈 틱의 약 26%), 교차 region 빌리기 횟수·대기, 틱 사이 유휴 처리(`idle-pause`), 많은 차원·월드일 때 굶김.
- **구조 질문**: 지금 region 크기·합치기 규칙이 많은 접속자(흩어진 32명)에서 맞는가, 전역 작업(명령·플러그인 동기 작업)이 region 을 멈추는 시간.

### O02 청크 수명·저장·IO — Opus high, 1차
- **볼 것**: 저장 시 섹션 펼치기 할당(청크당 약 380KB, chunk-io 남은 후보), 팔레트 문자열 쪼개기, 청크 내리기 지연·티켓 수명, 저장 역압, 플레이어 데이터 저장 빈도, 백업 중 성능, datafix 비용(옛 월드 가져오기).
- **추가 후보 예**: 청크 저장 압축 방식 옵션(바닐라 1.21 의 `region-file-compression` 과 같은 범위 안에서).

### O03 네트워크·청크 전송 — Opus high, 1차
- **볼 것**: 청크 직렬화(급한 작업 큐로 옮기기 — 11-11 진단의 남은 후보), 압축 스레드·레벨, flush 묶기, 엔티티 메타데이터·이동 패킷 묶음, 패킷 객체 할당, io_uring/epoll 경로, 접속 32명 이상 방송 비용, 원형/정사각 시야 차이(Paper 원형 1021 vs Furnace 1089 — 바닐라 모양 확인 필요).
- **입력**: `docs/audit/chunk-loading-diagnosis.md`, `perf-chunk-send-2026-11-07.md`, `perf-multiplayer-chunks-2026-11-07.md`.

### O04 월드젠 — Opus high, 1차
- **볼 것**: 남은 노이즈 비용(새 땅 CPU 약 53%), 장식 단일 레인(`furnace-worldgen-decorate`) 병렬화 가능성(바닐라 장식 순서·난수를 지키면서), 7×7 원시 청크 공유, 기후 R-트리(약 8%), 블록별 DF 트리·aquifer 느린 길, 구조물 시작점 계산·요새 고리, 엔드에서 Paper 보다 느린 이유(Paper 2.7초 vs Furnace 3.5초).
- **증명**: `NoiseIdentityBench` hash·starts 모드 전후 같음이 필수.

### O05 엔티티 틱·물리·추적 — Opus high, 1차
- **볼 것**: 충돌 검사(블록·엔티티), 엔티티 저장소 잠금(`EntityStoreLockBench`), 추적·뷰 갱신(`EntityViewTrackerBench`), 플레이어 이동 틱, `getNearestPlayer` 류 검색(많은 접속자), 효과·속성 재계산, 떨어진 아이템 합치기, 경험치 구슬.
- **기능 후보**: Paper 의 엔티티 활성 범위(activation range)류는 **바닐라 결과를 바꾸므로** 옵션(기본 꺼짐)으로만 제안.

### O06 몹 AI·길찾기·스폰 — Opus high, 2차
- **볼 것**: 길찾기 노드 캐시·탐색 상한, 두뇌 몹 센서 주기, POI 조회, 스폰 순환(mob cap 계산), 레이드·주민 거래 재계산. **난수 호출 순서 보존**이 조건.

### O07 블록 틱·유체·빛 — Opus high, 2차
- **볼 것**: 무작위 틱 섹션 건너뛰기, 유체 확산, 빛 재계산(빛 계산 풀 분리 — 11-11 남은 후보), 블록 상태 조회·이웃 갱신 비용, 식물 성장.

### O08 레드스톤·피스톤·레일·호퍼 — Opus high, 2차
- **볼 것**: 레드스톤 가루 갱신 연쇄(바닐라 순서 유지 — Eigencraft 류는 결과가 달라 옵션으로만), 피스톤 이동 블록 엔티티, 호퍼 검색, 관측기. X1(PR #133) 결과 위에서.

### O09 블록 엔티티·메뉴·인벤토리·아이템·거래 — Sonnet high, 2차
- **볼 것**: 블록 엔티티 틱 판정, 화로·양조기 틱, 메뉴 동기 패킷(변경분만 보내는지), `ItemStack` 불변 구조의 복사 비용(`furnace-itemstack-immutable`), 아이템 컴포넌트 비교·해시.

### O10 레시피·전리품·진행·통계·점수·지도 — Sonnet high, 3차
- **볼 것**: 레시피 조회 색인, 진행 기준 검사 빈도(틱마다 도는 트리거), 전리품 표 평가, 지도 갱신 패킷, 점수판 방송.

### O11 명령·함수·데이터 팩·채팅 — Sonnet high, 3차
- **볼 것**: 함수 실행(`/function` 대량 명령 — 데이터 팩 서버의 핵심 부하), 명령 분석 캐시, 선택자 검색 비용(`@e[distance=..]`), 자동완성, 채팅 컴포넌트 직렬화, `/reload` 시간.

### O12 Furnace 플러그인 API·패킷 API·이벤트 — Opus high, 2차
- **볼 것**: 이벤트 디스패치 비용(처리기 없는 이벤트를 만들지 않는지), 패킷 가로채기 비용, API 쓰기 쉬움(11-12 원칙: NMS·패킷을 쉽게), 빠진 API(개발자 불만 표 대조), 핫 리로드 누수.
- **제외**: "쉬운 NMS·패킷 API" 설계 스레드가 다루는 E1~E9 범위는 그 설계안을 입력으로만.

### O13 Bukkit·Paper·NMS 호환 계층 — Opus high, 3차
- **볼 것**: 어댑터 경로의 객체 할당·반사 호출 비용(핫 경로: `getLocation`, `getBlock`, 이벤트 변환), ASM 변환 기동 시간, 클래스로더 조회, 스레드 넘김(소유 스레드 읽기) 비용, 많이 쓰이는 플러그인(LuckPerms·WorldEdit/FAWE·ProtocolLib) 실제 부하.
- **원칙**: 특정 플러그인 저격이 아니라 일반 어댑터 개선.

### O14 설정·운영·관측·기동·메모리 — Sonnet high, 2차
- **볼 것**: 기동 시간 분해(데이터 추출·레지스트리·월드 적재), 기본 JVM 옵션·GC 선택, `/tps`·`/mspt`·프로파일러(Spark 대체) 기능, 설정 오류 안내, 크래시 보고서, 메트릭 노출. 2차 점검 A18 범위를 포함.

### O15 시험·벤치·CI 기반 — Sonnet medium, 3차
- **볼 것**: 성능 회귀를 잡는 장치(벤치 기준값을 CI 나 정기 실행에 올리기), 전체 시험 약 15분 단축, 흔들리는 시험, 앞 차수 검토자가 만든 재현·벤치 중 저장소에 넣을 것. 다른 파트 TASK 의 "결과 동일 증명법"이 쓸 공용 장치 제안.

---

## 3. 토론(검토 프로세스)

차수마다 아래 네 단계를 거친다. 이 스레드(로드맵 스레드)가 진행자다.

1. **D1 보고서** — 파트별 검토자 스레드가 1장 형식으로 보고서를 쓴다.
2. **D2 반론** — 차수마다 **교차 검토자 스레드 하나**(Opus high, 그 차수 보고서를 쓰지 않은 스레드)가 모든 항목에 찬성·반대·수정을 단다. 반드시 따지는 것: ①측정이 있는가, ②결과 동일을 정말 증명할 수 있는가, ③Paper 는 같은 문제를 어떻게 푸는가·더 단순한 대안, ④다른 파트 제안과 겹치거나 부딪치는가(예: O03 직렬화 큐 vs O01 박동 구조). 산출: `furnace/reviews/opt-review/wave<N>-rebuttal.md`.
3. **D3 답변** — 진행자가 반론을 원 검토자 스레드에 전달하고, 검토자는 보고서 끝 "답변" 절에 동의·반박을 쓴다. 의견이 갈리면 한 번 더 주고받고 끝낸다(최대 2회).
4. **D4 판정** — 진행자가 항목마다 **채택 / 수정 채택 / 측정 먼저 / 보류 / 기각**을 정하고 이유 한 줄을 적는다. 도미니코 결정이 필요한 것(바닐라와 다른 옵션, 큰 구조 변경, 기본값 변경)은 `docs/user-todo/entries/<날짜>-opt-review-wave<N>.md` 에 표로.

## 4. 이미 끝난 성능 작업(입력, 다시 하지 않음)

`docs/feature-parity/perf-summary-2026-11-07.md` 와 `perf-*.md` 전부(C1~C5, 청크 IO #38, 엔티티 #43, X1 #133, 청크 전송 #92·#211, M12, 노이즈 #222, 네더 표면 #228, 표면 바이옴 #232), `docs/audit/chunk-loading-diagnosis.md`, 프로젝트 공유 폴더 `furnace/worldgen-vs-paper-2026-11-12/report.md`, `furnace/chunk-send-vs-gen-2026-11-12/report.md`.

## 5. 시작 조건과 순서

도미니코 지시(11-12): 진행 중인 작업을 끝낸 뒤 시작한다. 검토는 기준 커밋을 고정하고 읽기만 하므로, **그 커밋이 곧 크게 바뀌지 않을 때** 시작해야 보고서가 오래 간다.

| 차수 | 파트 | 시작 조건(모두 병합 또는 닫힘) |
|---|---|---|
| 1차 | O00, O01, O02, O03, O04, O05 | PR #246(ProtocolLib 4b), #260(MemorySection 객체 참조), A15 마지막 묶음(엔티티 경계 상자·광선), E4c 후속. 코어 런타임 파트라 호환 PR 의 영향은 작지만, 기준 커밋을 한 번에 맞추려고 같이 기다린다 |
| 2차 | O06, O07, O08, O09, O12, O14 | 1차 D4 판정 끝. #230(A16 몹 실시간 읽기)은 O12 전에 |
| 3차 | O10, O11, O13, O15 | 2차 D4 판정 끝. #220(A12 술어)은 O10 전, #215(A13 묶음)는 O11 전, #231(authlib)·쉬운 NMS 설계 진행분은 O13 전 |

- 시작 조건의 PR 이 24시간 넘게 움직이지 않으면 기다리지 않고 코디네이터에게 알린 뒤 해당 파트만 뒤로 미룬다.
- 오래 열린 PR #44(11-06)는 조건에서 뺀다.
- 구현 TASK 는 TASK 목록이 올라간 뒤에만 시작한다. 구현 스레드는 그 시점에 열린 PR 의 파일과 겹치지 않게 순서를 잡는다(TASK 표의 "겹치는 파일" 칸).

## 6. 최종 산출물 — TASK 목록

`docs/roadmap/optimization-tasks.md` (차수마다 갱신하는 문서 PR). 표 칸: `T-ID`, 우선순위(P0 큰 이득·낮은 위험 ~ P3), 관점, 내용, 근거 보고서 항목 ID, 기대 이득, 결과 동일 증명법, 크기, 모델·노력치(1순위 규칙), 의존, 겹치는 파일. 코디네이터는 이 표대로 구현 스레드를 연다. 구현 PR 은 기존 규칙대로 독립 검토자 검토 → CI `test` → 병합.

## 7. 검토자 스레드를 열 때 넣을 지시(코디네이터용 틀)

> 전 모듈 최적화·기능 검토 D1 — 파트 `<ID> <이름>`. `docs/audit/optimization-review-plan.md` 의 1장(공통 규칙)과 2장 `<ID>` 절을 따른다. 기준 커밋 `<커밋>`. 코드는 고치지 않고 PR 도 만들지 않는다. 보고서는 프로젝트 공유 폴더 `furnace/reviews/opt-review/<ID>-<이름>.md`, 벤치·재현은 `furnace/reviews/opt-review/<ID>-repro/`. O00 기준선 보고서(`furnace/reviews/opt-review/O00-baseline.md`)가 있으면 먼저 읽는다. 끝나면 이 로드맵 스레드에 관점별 항목 수와 상위 5개를 보고하고, 반론(D2)이 오면 보고서 끝 "답변" 절로 답한다. 모델 `<모델>`(`<노력치>`).

교차 검토자(D2) 틀:

> 전 모듈 최적화·기능 검토 D2 반론 — `<N>`차. `docs/audit/optimization-review-plan.md` 3장 D2 를 따른다. 대상 보고서: `furnace/reviews/opt-review/` 의 `<ID 목록>`. 항목마다 찬성·반대·수정과 이유(측정·결과 동일 증명·Paper 대안·파트 사이 충돌)를 `furnace/reviews/opt-review/wave<N>-rebuttal.md` 에 쓴다. 필요하면 직접 벤치를 돌려 주장을 확인한다. 코드는 고치지 않는다. Opus(high).
