# 최적화·기능 개선 TASK 목록 (1차 2026-11-12, 2차 O13 2026-11-13)

`docs/audit/optimization-review-plan.md` 의 검토 프로세스(D1 보고서 → D2 반론 → D3 답변 → D4 판정)를 거친 결과다. 기준 커밋은 `07dec3da` 이다. 근거 보고서는 프로젝트 공유 폴더 `furnace/reviews/opt-review/` 에 있다(`O00`~`O15`, 반론 `wave1-rebuttal-core.md`·`wave1-rebuttal-sim.md`·`wave1-rebuttal-api.md`, 각 보고서 끝의 "답변" 절).

코디네이터는 이 표대로 구현 스레드를 연다. 구현 PR 은 기존 규칙대로 독립 검토 → CI `test` → 병합 순서를 따른다. O13(호환·NMS·패킷)은 2차로 따로 검토해 6장에 덧붙였다(2026-11-13, 기준 커밋 `14767c19`). 2차 판정으로 바뀐 1차 행(T-013·016·033·035·040·041·042·059)은 그 자리에서 고쳤다.

## 0. 읽는 법

- **우선순위**: P0 = 이득이 크거나 정확성·안정성 문제이고 위험이 낮음, 지금 연다. P1 = 다음 차례. P2 = 이득은 있으나 급하지 않음. M = 측정 먼저(측정 TASK 결과로 구현 여부를 다시 판정). H = 보류(결정·선행 대기).
- **증명**: 성능 TASK 는 "결과 동일 증명법"이 있어야 한다. 대부분 `T-001 IdentityKit` 위에서 증명하므로, 증명 의존 TASK 는 T-001 병합 뒤에 시작한다(표의 "의존" 칸).
- **모델**: 1순위 규칙(설계·성능·복잡한 서버 로직 = Opus high, 일반 구현 = Sonnet medium~high, 문서·단순 = Haiku low).
- **상태**: `진행` = 이미 구현 스레드가 열림, `병합` = 끝남.
- 판정 기호: 채택 / 수정 채택(반론·답변으로 범위나 숫자가 바뀜) / 측정 먼저 / 보류 / 기각.

## 1. 판정 요약

- 보고서 15개, 제안 약 190개. 반론 3개, 원 검토자 답변, 2라운드 1건(채팅 실행기).
- 반론과 답변으로 **숫자가 내려간 것**: 플레이어 배열(O05-01) −60% → 약 −30%, 장식 레인 다이어트(O04-02) −35~50% → 실측 −13~18%, 청크 압축 레벨(O03-05)은 청크 풀이 포화될 때만 이득.
- **전제가 틀려 고친 것**: 채팅 이벤트는 netty 루프가 아니라 전역 스레드에서 발화한다(O12-03). 실제 `.mca` 는 다시 압축하면 64~74% 로 줄어든다(O14-13 크기 전제 철회). region 구역 shift 4→2(r1)는 띠가 64블록이 되어 피뢰침 POI(128) 때문에 결과 동일이 깨진다(O01-01, s2r1 철회).
- **측정으로 확인한 것**: 무작위 틱 마스크 −17%, 주민 산책 창 −60%, 유체 −60%(반론 B 교대 A/B). 이벤트 구독 확인 비용은 벤치가 `GameEvents.NONE` 을 써서 안 보였을 뿐, 처리기 0개에서도 레드스톤 장면 약 5% 다.
- **원인을 확정한 것**: 처음 접속 때 재직렬화 폭주(O00-05)는 버려진 본문의 98.8% 가 이웃 청크의 빛 가장자리 밀기 때문이다. 청크당 직렬화 3.6번, 풀 작업의 63% 가 낡아 버려진다.
- **기각**: O05-X1 방송 패킷 한 번 인코딩 공유(측정상 더 느림), Deflater/Inflater 재사용(이득 0), O07-11 변경 방송 비트맵(레드스톤·유체 A/B 이득 없음), O08-12 호퍼 쉬기, O10 측정 기각 가설 7개, O14-23 오프라인 키 받기 생략(채팅 세션 결과가 바뀔 수 있어 로그만 줄임).

## 2. 이미 열린 정확성·안정성 작업 (검토 중 발견, TASK 목록 전에 시작)

| ID | 내용 | 출처 | 모델 | 상태 |
|---|---|---|---|---|
| F1 | 시야 모양을 바닐라 원형(+1 테두리)으로. vd16 1089 → 1021청크 | O03 C1 | Opus high | 진행 |
| F2 | region 명령 1틱 지연으로 #tick 함수가 초당 6.7회 → 20회 (도미니코 승인 방식, O11 7절, 반론 C 위험 3개) | O11-01 | Opus high | 진행 |
| F3 | 엔티티 충돌에 보트·셜커 등 엔티티 모양과 월드 보더 모양 넣기(noCollision·오르기 경로 포함) | O05 C1 | Opus high | 진행 |
| F4 | 속성 계산 순서, `B.isTag` /reload, 정적 난수 스레드 안전(createFor 위에서), LookAtPlayerGoal 조건, `Activity.hashCode` 두뇌 순서 | O05 C2·C4, O06 C2·C3, 반론 B C-B1 | Sonnet high | 진행 |
| F5 | 블록 모양이 바뀌면 몹 길 다시 찾기 + 공간 색인 | O06 C1 + P-O06-06 | Opus high | 진행 |
| F6 | `spawnChunkRadius` 게임 규칙을 읽어 플레이어 없는 스폰 청크 장치 돌리기 | O08 | Sonnet high | 진행 |
| F7 | `/save-all` 이 전역 스레드를 막지 않게(RCON `save-all flush` 완료 순서 시험 필수) | O14-10 | Opus high | 진행 |
| H1 | Bukkit 이벤트 다리 6종(BlockPlace 등) 잇기 + NOT_FIRED 낡음 방지 시험 | O12 H1 | Sonnet high | 진행 |
| T-O15-01 | RandomSource 주인 열쇠 씨앗 이음매 | O15, O05 C3 | Sonnet high | 병합(#272) |
| F9 | PR #230(A16-01): 플레이어 아닌 엔티티 `getLocation`·`getHealth`·`isValid` 가 감싸개 만든 시점 값(O13 C1). 충돌 풀림(11-13) → 병합만 남음 | O13 C1, 반론 2차 | — | 병합 대기 |
| F8 | **C-L1** region 합치기·나누기(`ChunkLevel.absorb`·`splitTo`)가 처리 전 빛 검사 집합과 방송 전 `lightChanges` 를 옮기지 않음. 재현: 합친 뒤 발광석 칸 블록빛 0(바닐라 15), 빛 갱신 패킷 6개 사라짐, 나눈 뒤 14. 고침: `absorb` 는 검사 집합을 순서대로 이어 붙이고 `lightChanges` 를 OR, `splitTo` 는 청크 기준으로 나눔. 시험: 세 경우 + 실제 Regionizer 합치기 경로 하나 | O07 빛 풀 설계 5장(`O07MergeLightProbe`) | Sonnet high | 시작 대기 |

## 3. TASK 표

### 3.1 공용 기반 (먼저)

| T-ID | 우선 | 관점 | 내용 | 근거 | 기대 이득·증명 | 크기 | 모델 | 의존 | 겹치는 파일 |
|---|---|---|---|---|---|---|---|---|---|
| T-001 | P0 | 기반 | IdentityKit: 차분 도우미(시드 로그·변이 점검), SceneHash 금 파일 비교, 결정성 자기 점검, PacketTape, 옛 구현 오라클 수명 규칙. 장면마다 새 JVM 또는 되감기(정적 id·난수) | O15 T-O15-02, #272 노트 | 성능 TASK 대부분의 증명 수단. 금 파일 갱신 = 독립 검토 승인 + PR 사유(도미니코 승인) | M | Opus high | — | 시험 소스만 (진행) |
| T-002 | P0 | 기반 | bench 소스셋·JSON 출력·속성 이름 통일, 벤치 기본을 실서버 모양 이벤트 입구로(`GameEvents.NONE` 이면 구독 확인 비용이 안 보임 — X1 호퍼 수치도 해당), JFR `DebugNonSafepoints`·stackdepth 고정, `RegionTickBench` phases 0 고치기(`-Dfurnace.tickstats`), `ChunkGenBenchmark` CI 게이트(CI 약 14.7초 절약) | O15 T-O15-03, O00, O08 JFR 지적 | 측정 신뢰도 | S~M | Sonnet high | — | build.gradle, 벤치 |
| T-003 | P1 | 기반 | 공용 장면 라이브러리(엔티티·유체·폭발·피스톤·몹 AI·월드젠 장식 포함 해시 `SharedPipelineBench`, 빛 장면 셋 `O07LightProbe` crater24·roof48·lamps400) | O15 T-O15-04, O05-14, O04-18, O06-15 | 증명 장면 | M | Sonnet high | T-001 | 시험 소스 |
| T-004 | P1 | 기반 | region-section-shift 2·3 시험 가능하게(지금 시험 World 는 16·4만) + `TestServers.core` 워커 수 | O15 T-O15-06, region-stall-check | O01-01 결정 전 필요 | S | Sonnet medium | — | 시험 도우미 |
| T-005 | P2 | 기반 | `bench.yml` 수동 실행(workflow_dispatch), 성능 게이트는 결정적 계수(해시·틱당 할당량)만. 할당량 안정성 측정 먼저 | O15 T-O15-09, 결정 C2 | 회귀 방지 | S | Sonnet medium | T-002 | .github |

### 3.2 코어 런타임 (O00~O05)

| T-ID | 우선 | 관점 | 내용 | 근거 | 기대 이득·증명 | 크기 | 모델 | 의존 | 겹치는 파일 |
|---|---|---|---|---|---|---|---|---|---|
| T-010 | P0 | OPT | `biomeZoomSeed` 를 매번 SHA-256 하지 말고 (seed, hasSeed, zoomSeed) 불변 묶음을 setter 에서 계산 | O00-01, 반론 A 9.1 | 32명 새 땅 할당 24% 제거. 자명 동일 + 단위 시험 | S | Sonnet high | — | ServerCore, World |
| T-011 | P0 | STR | 재직렬화 폭주: 이웃 빛 가장자리가 본문 캐시를 비우지 않게. ① 미리 직렬화를 이웃 4개 빛 붙인 뒤로 미루기 먼저, ② 블록/빛 부분 캐시 분리는 그 뒤 측정 | O00-05(계수 확정), 반론 A 9.3 | 8명 첫 접속 TPS 11.3 → 회복, 직렬화 3.6회/청크 → 약 1. 증명: 봇이 받은 청크 해시 = 서버 청크 해시 | M | Opus high | T-001 | World, ChunkColumn, ChunkLevel |
| T-012 | P0 | STR | 빛 묶음: 빛 층 게으른 할당·균일 층 공유(O07-05, O02-02 흡수) → 디스크 청크 저장 빛 읽기(O02-01, `attachPrecomputed` 가장자리 밀기 유지) → `pushEdge` 균일 구역 건너뛰기(O07-06) | O07-05·06, O02-01·02, O00-07, 반론 A 3.1, O02 답변 | 청크 빛 메모리 −96%(실서버 약 0.9GB+), 디스크 로드 CPU −45%, G1 최장 정지 원인 후보. 증명: 빛 SHA-1 같음(이웃 내림·편집·다시 올림, "저장 뒤 이웃이 올라와 번졌는데 다시 저장 안 된 청크" 장면) | L | Opus high | T-001 | light/, ChunkLight, anvil |
| | | | **위험(보고 필수)**: 가장자리 밀기를 유지하면, 가장자리가 어긋난 채 저장된 월드(가져온 월드 등)에서 바닐라가 디스크 빛을 그대로 쓰는 경우 Furnace 는 이음매를 더 밝게 고칠 수 있다. 바닐라 1.21.1 이 이웃 적재 때 가장자리를 전파하는지 원문으로 먼저 확인하고, 다르면 바닐라 쪽으로 맞추거나 도미니코에게 보고한다. 빛 장면에서 `markLightChanged`·청크 조회가 빛 시간의 약 75%(O07 답변) — 박싱 제거를 이 TASK 에 포함 | | | | | | | | |
| T-013 | P1 | STR | 송신 경로: 플레이어별 MPSC 큐 + 한 버퍼 인코딩, 큐에 Runnable 입구를 둬 ConnectionHandler 이벤트 루프 작업 26곳을 같은 순서로 | O03-01·02, 반론 A·C 3.3, O03 답변 | 50명×엔티티 200 서버 루프 CPU −69%, 나가는 바이트 동일(PacketTape) | M | Opus high | T-001, ProtocolLib 순서 계약 = `wave2-rebuttal-compat.md` 2장 A~D 를 수용 기준으로(넣는 입구가 `deferInsideListener` 를 큐보다 먼저 봄, 탭·다리는 루프에서 패킷 단위, `Nested` 는 큐 원소로, 넣을 때 인코딩하지 않음). 증명 PacketTape 5장면(안 바꿈·바꿈·리스너 안 보내기·묶음·취소 후 묶음 + 뒤따르는 메타데이터). 측정에 ProtocolLib 변형(T-075) | ConnectionHandler, Player |
| T-014 | P1 | STR | 저장 IO: 인코딩·압축 레인 밖(05 → 03) → 청크 단위 쓰기 대기(Paper RegionFileIOThread 식, 04) | O02-03·04·05, O02 답변 순서 | .mca 저장 256개 뒤 읽기 300~404ms → 112~131ms | M~L | Opus high | T-001 | world/storage, anvil |
| T-015 | P1 | OPT | 팔레트 바로 쓰기 도우미: 전송 직렬화(O00-06) + 저장 펼치기 제거(O02-07) | O00-06, O02-07, 반론 A 9.1 | 저장 86~144µs·391KB/청크, 전송 팔레트 unpack 할당 37%. 증명: 바이트 동일 | M | Sonnet high | T-001 | ChunkSection 직렬화 |
| T-016 | P1 | OPT | 플레이어 목록을 차원별 배열로(바닐라처럼 도착 순 append, 리스폰 순서 확인) + 전체 접속자 도착 순 배열(Bukkit `getOnlinePlayers()` 순서가 지금 CHM 해시 순 = 정확성 C5, Paper 는 접속 순. T-074 가 이 배열을 씀) → 청크 격자 플레이어 색인(O05-02) | O05-01·02, O00-03, 반론 A K2 | 실서버 꼴 약 −30%, 격자까지 가야 나머지. 바닐라 순서로 정확성 K2 함께 해결 | M | Opus high | T-001 | World |
| T-017 | P1 | OPT | 추적기 `playerMoved` 후보 줄이기 | O05-03 | E2000×P32 틱당 1.5~5ms. 상태 변화 집합 동일 증명 | M | Opus high | T-001 | entity/view |
| T-018 | P1 | STR | 충돌 수집 재작성(섹션 단위·공기 섹션·cave_air·모양 할당 제거, 모양 목록 순서 보존) | O05-07, O00-10 소 3000 장면 | 충돌 몫 −50% 가설(몹 틱 −8%). 옛/새 수집기 무작위 대조 | L | Opus high | F3, T-001 | ChunkLevel, Entity |
| T-019 | P1 | OPT | 작은 안전 묶음 A(스케줄러·청크): 곧 될 머리 당겨 꺼내기(O01-03, 스케줄러 CPU −21%), `ownerOf` 이중 조회·박싱·해시 겹침(O01-05②, O00-15), O02-05·06·13·14, O03-03(암호 인코더 배열)·06(직렬화 버퍼 −25%)·13 | 반론 A 7장 T1 | 각 S. 증명 각 항목 | S×여럿 | Sonnet high (O01-05 는 Opus high) | — | sched, world, net |
| T-020 | P1 | OPT | 작은 안전 묶음 B(월드젠·엔티티): 장식 레인 다이어트(O04-02·03, 실측 −13~18%), 엔드 불필요 단계(O04-06·07, −26% 해시 동일), O04-08, O05-05·09(문자열 태그 조회 상수화)·10·12 | 반론 A T1, O04 답변 | NoiseIdentityBench·SharedPipelineBench 해시 | S×여럿 | Sonnet high | — | world/gen, entity |
| T-021 | P1 | OPT | 월드젠 M: 구조물 높이·바닥 열을 기둥 단위로(O04-04), 기후 R-트리 부분 거리 캐시(O04-05), 그 뒤 O04-12 | O04-04·05·12 | 오버월드 CPU 7.3%·9.4%, 네더 13.9%(DebugNonSafepoints 로 낮춘 지분). 해시 | M | Opus high | — | world/gen |
| T-022 | P1 | ADD | 워치독 조기 경고(Paper early-warning, **기본 켬 — 도미니코 확정**) + O01-09 가장 큰 region 몫 | O01-08·09 | 운영 | S | Sonnet medium | — | ServerWatchdog |
| T-023 | P1 | OPT | 기동: AppCDS 자동 보관(start.sh·start.bat·문서, −23~25%), RSA 키·HttpClient 지연(O14-02), `BiomeFeatureData.load`·`BlockRegistry.load` 이진 사본·지연(O14-04 재측정 순위), 생성기 만들기 병렬(O04-19) | O14-01·02·04, O04-19, 반론 C 5.1 | 다시 켜기 3.4초 → 2.6초 안팎 | M | Sonnet high | — | start.sh, ServerCore |
| T-024 | **P1**(2차 B2 올림) | OPT | 요새 고리: 월드를 열자마자 배경 계산(O04-17) + 다시 켠 월드용 고리 파일(O00-08, 키 = 시드·생성기·데이터 팩 해시). 2차 조건: 계산을 `computeIfAbsent` 안에서 하지 않기, 파일 없는 다시 켜기는 Done 뒤 시작 | O04-17, O00-08, 반론 A 9.5, 2차 B2 4장 | 새 월드 기동 CPU 57% 가 고리. 2차 시제품: 새 월드 Done 8.1 → 5.6초, 무조건 배경 계산하면 다시 켜기 2.4 → 3.1초 회귀. 결과 동일(같은 계산) | M | Opus high | — | world/gen 구조물 |
| T-025 | P2 | FIX+ | 설정 키: zlib 레벨(기본 6 유지, O02-09), 청크 프레임 압축 레벨 키 공개(기본 6, O03-05), 역압 최대값 기준(O02-06 측정 먼저) | O02-06·09, O03-05 | 운영자 선택 | S | Sonnet medium | — | config |

### 3.3 측정 TASK (결과로 다시 판정)

| T-ID | 우선 | 무엇을 재나 | 근거 | 모델 | 다시 판정할 것 |
|---|---|---|---|---|---|
| M-01 | M | region 구역 s4r1·s3r1·s2r2 실측: 봇 32명 흩어짐 MSPT·합치기/나누기 횟수·`splitLocked` 잠금 시간, 8코어 장면 포함 | O01-01, 반론 A 1.1, **도미니코: 실측 뒤 결정** | Opus high | 결정 1 |
| M-02 | **끝남(2차 B2)** → T-110·T-111 | CPU 예산·스레드 지도: 생성이 region 틱을 굶김(32명 생성 77 → 43청크/초, 가장 느린 TPS 16~17), 리눅스 스레드 우선순위 무시, 두 차원 동시 | O00-04, 반론 A 3.6, O04-01·09·10 | Opus high | 적응 제한 설계, 생성 스레드 기본값(결정 8), 장식 레인 병렬화(O04-01) |
| M-03 | M | 힙 지배자 트리(`[I` 1.84GB 의 주인), 스냅샷 수명 | O00-07, 반론 A 9.4 | Sonnet high | T-012 다음 메모리 TASK |
| M-04 | M | 직렬화 결과 캐시: 기준·"전송 직전 큐 비우기"·대안 B(쉬는 region drain 차례) 3방 A/B | O03-07, O03 답변 | Opus high | 소유 규칙 예외 여부 |
| M-05 | M | 청크 풀 포화 장면에서 압축 레벨 4 대 6, libdeflate | O03-05, 결정 3·4 | Sonnet high | 결정 3·4 |
| M-06 | M | 퇴출 훑기(O01-06 + O02-12 하나로), O02-08·15, O03-04·08·09·14, O04-10·11·13, O05-04·06·08·11·13 | 반론 A T8 | Sonnet high | 각 항목 |
| M-07 | M | region POI 읽기 반경 목록 문서화(K3) + 안 올라온 청크 POI(K1, 바닐라 `getOrLoad` 가 디스크를 읽음 — O01 이 확인) → P-O01-11 차원 단위 POI 읽기 색인(L, 정확성 수정) 설계 | 반론 A K1·K3, O01 답변 | Opus high | P-O01-11 구현 |

### 3.4 API·운영·내용 (O09~O12, O14)

| T-ID | 우선 | 관점 | 내용 | 근거 | 기대 이득·증명 | 크기 | 모델 | 의존 | 겹치는 파일 |
|---|---|---|---|---|---|---|---|---|---|
| T-030 | P0 | OPT | `inventory_changed` 트리거: 슬롯 변경 한 번에 세기 한 번 + 아이템 id 색인 | O10-01, 반론 C 3.5 | 370µs → 약 1µs. 옛 매처와 차분 시험 | S~M | Sonnet high | — | ConnectionHandler.fireInventoryChanged (T-033 보다 먼저) |
| T-031 | P0 | FIX+ | 로그 롤오버 gzip 을 잠금 밖·비동기로 | O14-12 | 100MB 1.4초 동안 모든 로깅 스레드 멈춤 제거 | S | Sonnet medium | — | LatestLogAppender |
| T-032 | P1 | OPT | Bukkit 이벤트 다리 전역 잠금 제거(다리 칸) + 게으른 이벤트 내용 도우미(O12-07·O08-07 공통) | O12-01, 반론 C 3.6 | 다리 이벤트 처리량 1M/s 상한 제거 | S+M | Sonnet high | H1 | EventBridge (O13 파일) |
| T-033 | P1 | OPT | 이벤트 구독 시 칸 전체 변환: ① 패치 변환 캐시(먼저) ② 지연 스냅샷(T-032 도우미 뒤). 수용 기준에 어댑터 포함: `InventoryResultMappings.container()` 의 즉시 `snap.contents()` 복사를 없애고 스냅샷 참조를 쥐어 칸별로 읽기, 시험 "처리기가 `getItem(3)` 만 읽으면 다른 칸은 변환 0"(O13 6-2·P-O13-09(c)) | O09-01, O09 답변 | 54칸 스냅샷 23.5µs → 1/20~1/200(가설, A/B) | S+M | Sonnet high | T-030, T-032 | 메뉴·이벤트 |
| T-034 | P1 | STR | 서버 접속 이벤트(AsyncPlayerPreLogin·Configure)를 플러그인 비동기 실행기에서 분리 + 실행기 기본값(결정 D1) | O12-02 | 가상 스레드 pinning 452ms → 1ms | M | Opus high | — | plugin |
| T-035 | P1 | STR | 채팅: 연결별 순서 사슬 하나(chat·chat_command·chat_command_signed·chat_session_update 4패킷). 채팅은 사슬에서, 명령은 전역에 넘기고 전역 람다 반환까지 `whenComplete` 로 기다림(join 금지), 큐 상한, 세션 갱신 중간 시험 | O12-03, O11-07, 반론 C 3.3 2라운드 합의, 결정 D2 | 플러그인 블로킹 채팅 처리기가 전역(시간·명령·RCON·#tick)을 막지 않음 | M | Opus high | T-034, T-013 의 순서 계약. `tap.inbound` 는 루프에서 도착 순으로, 사슬에 넘기기 전에(ProtocolLib 받는 리스너 2b-3 이 Paper 처럼 보게, `wave2-rebuttal-compat.md` 2장 D) | ConnectionHandler:476·536·641-688 |
| T-036 | P1 | FIX+ | 백업 zip 의 지역 파일 압축 옵션(`backup.compression`, 기본은 결정 D3: STORED 면 CPU −99%·크기 +40~55%, parallel 선택지) | O14-13 + O02-11, 반론 A·C 3.4 | 400MB 11.6초 → 0.69초(STORED) | S | Sonnet medium | — | maintenance/backup |
| T-037 | P1 | OPT | 플러그인 레시피 등록 일괄 + 방송 합치기 | O10-03 | 등록마다 1.3ms·2.3MB + 108KB 전원 재전송 제거 | S | Sonnet high | — | recipe |
| T-038 | P1 | ADD | 컨테이너 API: 블록·엔티티 컨테이너 접근(O09-17) → 임의 메뉴 모양 창 열기·`createInventory` 확장·openMerchant(O09-11, 결정 D16) | O09-11·17 | /invsee·가상 모루·작업대 플러그인 | M→L | Sonnet high → Opus high | 결정 D16 | furnace-api, menu |
| T-039 | P1 | OPT | 명령 후속(F2 뒤): `execute as/at @e` 리전 단위 묶기(O11-02), 선택자 색인·무관 리전 방문 제거(O11-03·04, 측정 먼저) | O11-02~04, 도미니코 O11 결정 | 엔티티 100개 100ms | M | Opus high | F2 | command/exec |
| T-040a | **P0** | FIX+ | 리스너 통째 탈락 막기 + 보호 이벤트: ① `registerEvents` 가 `NoClassDefFoundError` 로 실패할 때만 ASM 으로 리스너 바이트를 읽어 매개변수 클래스가 풀리는 `@EventHandler` 만 `MethodHandles.privateLookupIn` 으로 등록(정상 경로 그대로), 빠진 처리기 이름·없는 클래스를 경고에(A-2) ② 보호 6종 Bukkit 클래스·다리: `EntityExplode`, `BlockPistonExtend/Retract`, `BlockSpread`, `BlockForm`, `ProjectileHit` + `EntityChangeBlock` 발화 지점 확인 | P-O13-06·07, C3·C4, 반론 2차 0장 1·2번 | 어댑터 Bukkit 이벤트 클래스가 92개뿐이라 하나만 없어도 같은 리스너의 `BlockBreak`·`PlayerInteract` 처리기까지 사라짐. 보호 플러그인이 폭발·피스톤 그리핑을 못 막음. 증명: 없는 클래스 + 있는 클래스 한 리스너 시험, 이벤트마다 취소·필드 반영 시험(H1 틀) | S~M | Sonnet high | #306(T-032) 병합 직후(같은 파일 충돌만) | EventBridge, Mappings, SimplePluginManager |
| T-040b | P1 | ADD | 많이 쓰는 이벤트 클래스·다리: 순서는 jar 훑기 결과(T-079 A-1). 서버가 이미 발화하는 약 20종(`PlayerPickupItem`·`PlayerItemConsume`·`EntityTarget`·`ServerListPing`·`PlayerAdvancementDone`·`PlayerSwapHandItems`·`PlayerFish`·포털·침대·탈것·길들이기·번식·`PlayerEditBook`·탑승 등) 먼저, 아이템 손상·화로·거래·전리품·BlockDropItem 처럼 발화 지점이 없는 것은 O09 쪽 TASK 와 함께(결정 D17) | O09-12, O10-A2·A4, O12, P-O13-06, 반론 C 3.9 | 호환 | M×여럿 | Sonnet high | T-040a | 발화 지점, Mappings |
| T-040c | P2 | ADD | 없는 Paper 이벤트 껍데기 클래스(처리기 목록만, `NOT_FIRED`): 플러그인 본문이 `instanceof`·`new`·`getHandlerList()` 로 직접 쓰는 경우용. 대상은 A-1 보고로 실제 쓰인 것부터. API 이름·서명만 쓰고 코드는 옮기지 않음 | P-O13-07, 반론 2차 6장 D3 | 호환 | M | Sonnet high | T-040a, T-079 | compat 이벤트 |
| T-041a | P2 | ADD | Bukkit 레시피 API 클래스·등록·조회(Shaped·Shapeless·Cooking 4종·Stonecutting·Smithing 2종·`RecipeChoice.MaterialChoice`), 변환 한 점 `Compat.recipe`, `Server.addRecipe` 는 묶음 등록(T-037) 위에서. 한 인자 `addRecipe` 의 재전송 여부는 1.21.1 Paper 로 확인(최신 main 은 안 보냄) | O10-A1, O13 6-3, 반론 2차 3장 | 호환 | M | Sonnet high | T-037 | compat (org.bukkit.inventory) |
| T-041b | P2 | ADD | `RecipeChoice.ExactChoice` 를 Paper 와 같은 한계로(결정 D18) | O10-A1 | 호환 | M | Sonnet high | T-041a, 결정 D18 | compat |
| T-042 | P2 | OPT | 클래스로더 이름 색인(다른 플러그인 jar 찾을 때 CNFE 만들기 제거, 60~90µs → 0.05µs), `PluginTasks.guard` 구간을 프로파일러 꺼짐 때 생략, `PluginClassLoader.findOwn` 의 `synchronized jar()` 잠금 줄이기(P-O13-10(c), 병렬 클래스 적재 장면을 측정에 추가) | O12-05·04 | 플러그인 60개 서버 | S×2 | Sonnet high / medium | — | plugin/library |
| T-043 | P2 | OPT | `PlayerAdvancements` 공유 표·비트집합·지연 진행(저장 파일 키 순서 재현 조건) | O10-02 | 플레이어당 647KB·접속 1.16ms. 시제품 측정 먼저 | M | Opus high | M | advancement |
| T-044 | P2 | FIX+ | 운영 묶음: 비동기 로그(`discardingThreshold=0`·`neverBlock=false`·halt 때 비우기), 메트릭·단계 지표(O00-13·O01-09·O03-12·O14-16 하나로), 진단 명령 묶음(반론 C 3.7), OOM 동작(결정 D13), 바닐라 데이터 없을 때(결정 D14), 설정 다시 읽기(결정 D15) | O14-11·16·17·18·22, 반론 C 3.7 | 운영 | M | Sonnet medium | — | config, metrics |
| T-045 | P2 | ADD | Spigot 설정 `advancements.disabled`·`*.disable-saving`(기본 꺼짐, 결정 D19) | O10-A5 | 호환 | S | Sonnet medium | 결정 D19 | config |
| T-046 | P2 | OPT | 데이터 팩 적재: EntityPredicate 정적 플래그를 문맥 객체로 옮긴 뒤 전리품·발전 과제 병렬 파싱 | O11-06, O10 답변 | `/reload`·기동. KillCredit 결과 동일 | M | Sonnet high | — | loot, advancement |
| T-047 | P3 | OPT | 콘솔 명령 틱당 하나만 처리 → 바닐라처럼 큐가 빌 때까지(바닐라 `handleConsoleInputs` 확인 먼저) | O00-14 | 정확성 가능성 | S | Sonnet medium | — | ServerCore |
| M-10 | M | — | 측정: O14-05 데이터 팩 적재(도미니코 PC, Mojang 403), O14-09·14, O10-02 시제품, O12-06/O08-07 레드스톤 이벤트 A/B, 점수판 SetScore 합치기(결정 D20) | 반론 C 7장 | — | — | Sonnet medium~high | — | — |

### 3.5 월드 시뮬레이션 (O06~O08)

| T-ID | 우선 | 관점 | 내용 | 근거 | 기대 이득·증명 | 크기 | 모델 | 의존 | 겹치는 파일 |
|---|---|---|---|---|---|---|---|---|---|
| T-050 | P0 | OPT | 유체 퍼짐 칸 기억을 평면 배열로(+ 면 가림 빠른 경로·상태 쌍 기억 O07-02, 바닐라 `OCCLUSION_CACHE` 모양) | O07-01·02, 반론 B 2장 | 유체 장면 −60~63%(두 번 실측). 씨앗 고정 `O07FluidDiffProbe` 를 옛 구현 대조 시험으로 | S | Sonnet high | — | FluidTicker |
| T-051 | P0 | OPT | 주민 산책 마을 섹션 찾기를 창 하나로(창 안 마을 중심 좌표만 모아 체비셰프 최솟값) | O06-01, 반론 B 1장 | region 틱 −60%(POI 없는 최악 장면, 마을 장면은 다시 잼). 옛 함수를 오라클로 무작위 POI 대조 | S | Sonnet high | — | entity/ai 산책 |
| T-052 | P1 | OPT | 스폰 바이옴 줄: T-010 → 자기 region 청크로 바이옴 번호 바로(O00-02 = O06-04) → `BiomeSpawns` 번호 표(O06-03). 남는 동시 맵 몫은 T-019 `ownerOf` | O00-02, O06-03·04, 반론 B 4장 10번 | 32명 새 땅 서버 CPU 21~22% 가 자연 스폰. 바이옴 번호 전수 대조(확대 지터 경계 포함), 표 전수 | M | Sonnet high | T-010 | entity/spawn, ServerCore |
| T-053 | P1 | OPT | 무작위 틱 구역 비트마스크(무효화는 구역 쓰기 메서드에, 공유 구역이면 켠 쪽, `-ea` 에서 틱마다 다시 계산해 단언) | O07-04, 반론 B 2장 | 빈 장면 region 틱 −17%(상한 시제품 실측) | M | Opus high | T-001 | ChunkSection, ChunkColumn |
| T-054 | P1 | OPT | 이벤트 구독 확인을 필드 읽기로(등록·해제·핫 리로드 때만 다시 굽기). 처리기가 0개여도 레드스톤 장면 약 5%(p95 약 11%) | O12-06, O00-09, 반론 B 0장 2번 | 증명: 등록 바뀜 직후 첫 이벤트부터 처리기 호출 시험 | S | Sonnet high | — | EventBusImpl (T-032 보다 먼저) |
| T-055 | P1 | STR | 예약 틱 줄: 원시 (칸,종류) 집합(O08-02①, −9.6%) → 청크별 컨테이너(O07-07, 바닐라 `LevelChunkTicks` 모양, `parked` 흉내 제거). 청크가 없을 때 예약은 바닐라처럼 버리는지 시험으로 고정(단 적재 중 청크의 저장된 예약은 버리지 않게, 기준을 바닐라 청크 적재 단계와 맞춤. 버릴 때도 바닐라 `createTick` 처럼 `subTickCount` 는 올려 순서 번호가 밀리지 않게) | O08-02①, O07-07, 반론 B 4장 2번 | 실행 순서 대조 시험(A07 하네스)을 첫 PR 에서. 예약 2만이면 청크당 0.8ms → 수 µs | M | Opus high | T-001 | BlockTicks (한 스레드가 차례로) |
| T-056 | P1 | OPT | 상태 표 묶음: 유체·`canHoldFluid` 표(O07-03), `isAir` 표, 레드스톤 동작 정적 표(O08-01①, 클래스 초기화 순환 시험), `facing` 표(O08-02②), O08-05 상수, 직업 블록 비트셋(O06-11), 면 모양 번호(O07-08). 태그 `/reload` 무효화 훅 공용 | 반론 B 4장 6번 | 모든 상태 전수 = 옛 함수. 레드스톤 −6.8% 등 | M | Sonnet high | — | 흩어짐 — 짧게 한 번에 |
| T-057 | P1 | OPT | 레드스톤 가루 순서 계산 무할당(O08-01②) + `NO_SIGNAL` 레벨 필드(O08-04, BlockLevel 구현 목록 점검) + 횃불 과열 칸별 개수 | O08-01②·04·05 | 가루 6000개 장면 −7%, 순서 대조 20만 좌표 | S | Sonnet high | — | redstone |
| T-058 | P2 | STR | 길찾기 표를 원시 배열로(바닐라 fastutil 과 같은 모양) | O06-05 | 길 하나 −42%, 경로 200개 서명 같음. 틱 몫 1~2% 라 추격 장면에서 커지면 올림 | M | Sonnet high | T-003 | pathfinding |
| T-059 | P2 | FIX+ | 게임 비용 진단(몹 AI·예약 틱·레드스톤 폭주·Spark 대체): 계수기·표 하나, 꺼짐이면 비용 0. + 느린 Bukkit 처리기·`RegionBorrow` 오래 쥠 경고(P-O13-15): 기본은 이벤트 한 번(처리기 묶음) 단위로 재고 처리기 단위는 프로파일러 켬 또는 문턱을 넘은 뒤에만(T-032 뒤 처리기 호출 109~129ns 라 처리기마다 `nanoTime` 두 번은 +20~30%), 빌림 경고는 O01 워치독 조기 경고 장치에 | O06-16, O07-14, O08-08, O14-19, 반론 B 4장 7번 | 운영 | M | Sonnet high | — | metrics (T-044 와 조율) |
| T-060 | P2 | OPT | 작은 묶음: 시선 기억 박싱(O06-09), POI 범위 질의 ①②(O06-12, ③은 술어 호출 기록 대조 뒤), 빛 검사 집합 박싱·`markLightChanged` 박싱(O07-11 → T-012 에. 11-13 교대 A/B: 빛 청크 캐시+중복 제거 시제품이 `lamps400` −8%·흔들림 안이라 성능 근거는 없음, 할당·정리 이유로만), 게임 규칙 틱당 한 번 읽기(O07-11) | 반론 B | 각 S | S | Sonnet medium | — | 흩어짐 |
| T-061 | P2 | ADD | Paper 호퍼 설정 중 `hopper.disable-move-event`(처리기 있을 때 이벤트 생략, 결과는 바닐라 같음)만. 나머지 둘은 타이밍이 바뀌어 보류 | O08-09, O09 옵션 표, 결정 D-6 | 호퍼 팜 + 플러그인 | S | Sonnet medium | 결정 D24 | hopper, config |
| T-062 | P2 | ADD | 길찾기를 월드 보더 밖으로 안 하게 옵션(기본 꺼짐) | O06-17, 결정 D-2 | 보더 서버 | S | Sonnet medium | 결정 D22 | pathfinding |
| T-063 | P2 | ADD | 폭발 가림 비율 기억(Paper `optimize-explosions` 식) 옵션, 기본 꺼짐 — **도미니코 진행 결정(O07)**. 결과 같은 폭발 칸 기억(O07-09)과 같은 폭발 벤치로 | O07-10 | 옵션 켤 때만 결과가 달라질 수 있음(같은 틱 연쇄 폭발) — 문서에 적음 | M | Opus high | T-003 폭발 벤치 | explosion |
| T-064 | P2 | STR | 빛 계산 풀 분리 = **안 C(바닐라 방식: 보이는 사본 + 따로 도는 빛 작업)를 옵션, 기본 꺼짐**으로. region 은 빛 검사·청크 빛 켜기를 작업 줄에 넣고, 빛 작업은 쓰기용 구역 사본에 계산해 region 스레드에 게시. 해결할 문제 P1~P11(설계 4장): P1 빛 작업의 블록 상태 경쟁 읽기 = 팔레트·값 배열을 한 불변 객체로 바꿔 끼우는 저장소가 전제, P2 구역 쓰기 시 복사, P7 저장은 밀린 빛을 기다린 뒤, P9 `setVirtualLight` 는 그 자리 계산 유지, P10 시험은 옵션 끔 | O07-12, 도미니코 결정, `O07-light-pool-design.md` | 빛 40ms 장치(`lamps400`)에서 region 틱 약 1ms. 증명: 옵션 끔 = 지금과 바이트 같음(전체 시험 + 빛 장면 해시), 옵션 켬 = 빛을 끝까지 기다린 뒤 값이 끔과 같음, region 틱 교대 A/B. **바닐라와 다른 점(보고됨)**: 켜면 빛 보이는 시점이 과부하 때 1~2틱 늦어짐(바닐라 과부하와 같은 성질), 저장은 밀린 빛을 기다려 바닐라의 옛 빛 굳음 결함은 따르지 않음 | L | Opus high | F8, T-015·O02 의 `ChunkSection` 저장소 정리(P1) | light/, ChunkSection, ChunkLevel |
| M-20 | M | — | 측정: 마을 중심 region 틱 기억(O06-02 대안, 01 뒤 3% 넘게 남을 때만 색인), GoalSelector 비트(O06-07), 두뇌 고정 비용(O06-08, C-B1 뒤 바닐라 순서 복사), 지역 상한(O06-10, T-016 뒤), 폭발 칸 기억(O07-09, 폭발 벤치), 신호 질의 `Pos`(O08-03), Alternate Current 시제품(O08-06, 결정 D23) | 반론 B | — | — | Sonnet high (AC 시제품은 Opus high) | T-003 | — |
| M-21 | M | — | 확인: `skyDarken` 을 부를 때마다 전역 `volatile dayTime` 으로 계산 — 바닐라는 틱 머리에 한 번. 맞으면 정확성·성능 둘 다 | O07 5장, 반론 B 2장 | — | S | Sonnet high | — | O01 파일 |

정확성 C-B1(`Activity.hashCode` 를 바닐라처럼 `name.hashCode()`, 두뇌 활동 순회 순서 고정)은 F4 정합 묶음에 넣었다(진행).

기각: O07-11 변경 방송 비트맵(유체·레드스톤 두 장면 A/B 차이 없음), 빛 풀 안 A(한 묶음을 여러 스레드로 — 표시 구역 수가 달라질 수 있고 복잡도 최대)·안 B(풀에 맡기고 다음 접근 전 기다림 — 결과는 같지만 빛 50ms 넘는 장면에서 TPS 이득 없음)·안 D 빛 청크 캐시 시제품(교대 A/B −8% 흔들림 안), O08-12 호퍼 쉬기·피스톤 기억(깨움 조건 하나만 빠져도 타이밍이 바닐라와 다름 — O09-03b 깔때기 잠들기도 같은 기준으로 측정 먼저).

## 4. 여는 순서 (코디네이터용)

파일이 겹치지 않는 것끼리 병렬로 연다(도미니코 지시: 병렬 가능한 것은 병렬). 같은 파일 줄(예약 틱 `BlockTicks`, `ConnectionHandler`, 빛 패키지)은 한 스레드가 차례로 맡는다.

1. **지금 (증명 장치 없이 가능, 서로 파일 안 겹침)**: F8(빛 합치기·나누기 정확성), T-010, T-030, T-031, T-050, T-051, T-054, T-002, T-023 의 AppCDS 부분.
2. **T-001 IdentityKit 병합 뒤**: T-011, T-012, T-053, T-055, T-015, T-016, T-017, T-003.
3. **F3 충돌 병합 뒤**: T-018. **F2 #tick 병합 뒤**: T-039. **T-010 뒤**: T-052. **T-054 뒤**: T-032 → T-033. **F8 과 `ChunkSection` 저장소 정리 뒤**: T-064(빛 풀 옵션).
4. **결정 뒤**: T-034(D1)·T-035(D2)·T-036(D3)·T-038(D16)·T-040b(D17)·T-041b(D18)·T-045(D19)·T-061(D24)·T-062(D22).
5. **2차(O13) 순서**는 6.3 절.
6. 측정 TASK(M-01~M-21)는 계산·실행이 길어 코디네이터 판단으로 Fable 계산·실행 대기열(한 번에 하나)에 넘길 수 있다. 결과는 이 문서의 해당 행을 다시 판정한다.

## 5. 도미니코 결정

이미 정한 것:
- O11: 명령 region 홉 방식(F2), #tick 은 글로벌 틱이 함수 완료까지 기다림(5초 한도).
- O01 C1 region 구역 기본값 = 실측 뒤 결정(M-01). O01 C2 워치독 조기 경고 = 기본 켬.
- O15: 운영 코드 시험용 난수 이음매 허용, 금 파일 갱신 = 독립 검토 승인 + PR 사유, 야간 벤치는 수동 실행부터.
- O07: 폭발 가림 비율 기억은 Paper 식 옵션·기본 꺼짐으로 진행(T-063). 빛 계산 풀 분리는 보류하지 말고 해결 방법을 찾을 것(T-064). 결과물이 바닐라와 다르거나 문제가 생길 것 같으면 반드시 보고.

남은 결정은 `docs/user-todo/entries/2026-11-12-opt-review-wave1.md` 에 표로 정리했다.

## 6. 2차 — O13 호환 계층 (2026-11-13)

기준 커밋 `14767c19`(#267·#275·#277 병합 뒤). 보고서 `furnace/reviews/opt-review/O13-compat.md`, 반론 `wave2-rebuttal-compat.md`(재현물 `O13-repro/`, `wave2-rebuttal-compat-repro/`). 반론은 반대 0, 찬성 7, 수정 7이었고, 수정은 모두 코드·측정 근거가 있어 원 검토자 답변(D3) 없이 반론 쪽으로 판정했다.

### 6.1 판정 요약

- **가장 큰 것은 성능이 아니라 호환이다.** 어댑터의 Bukkit 이벤트 클래스가 92개뿐이다. 플러그인 리스너 클래스가 없는 클래스(`EntityExplodeEvent` 등)를 하나만 써도, Bukkit 등록 규칙상 그 리스너 클래스 전체가 등록되지 않는다. 그러면 같은 클래스의 `BlockBreakEvent`·`PlayerInteractEvent` 처리기까지 사라진다. 보호 플러그인이 폭발·피스톤을 못 막는 문제(C4)도 원인이 같다. → **T-040a (P0)**.
- **고친 것**:
  - P-O13-06 의 "다리 대응이 없다"는 "클래스 자체가 없다"로 고쳤다.
  - P-O13-05 는 새 배열 대신 T-016 의 도착 순 배열을 쓴다.
  - P-O13-10 은 상수 풀 파서 대신 한 번 훑기로 바꿨다. 판정 차이가 0이고 8~10배 빠르다.
  - P-O13-09(b) 의 동일성 판정은 제자리 수정을 놓치므로 변경 번호로 바꿨다.
  - P-O13-15 의 처리기마다 `nanoTime` 재기는 T-032 뒤에 +20~30% 라 묶음 단위로 바꿨다.
- **새 정확성 C5**: Bukkit `getOnlinePlayers()` 순서가 `ConcurrentHashMap<eid>` 값 순서다. Paper 는 접속 순이다. 1차 K2 와 원인이 같아 T-016 에서 함께 고친다.
- **1차 표 오기 정정**: 결정 번호가 반론 C 의 지역 번호로 남아 있었다. T-038 D8→D16, T-040 D9→D17, T-041 D10→D18, T-044 D5·6·7→D13·14·15, T-045 D11→D19, M-10 D12→D20 으로 고쳤다.
- **보류**:
  - 거울 재사용(P-O13-03(b), "끊긴 거울" 의미가 바뀜 — 정확성 판단으로 따로).
  - 서버 엔티티에 감싸개 칸 두기(P-O13-04(c), NMS 어댑터 모듈 경계 결정 때).
  - ASM 결과 디스크 캐시(P-O13-10(b), 한 번 훑기 뒤 다시 재서 기동 몫이 남을 때만).
- **기각**:
  - ProtocolLib 다리의 얕은 필드 비교(제자리 목록 수정을 놓침).
  - ASM 상수 풀 파서 안(판정이 달라져 증명이 늘고 이득은 비슷).
  - `handleFor` 를 서버 `World` 칸에 붙이기(이득이 `get` 한 번).
- **새 결정 없음.** 보고서의 D1(살아 있는 보기)은 A15-08 에서 이미 정해졌다. D5(#230)는 충돌이 풀려 병합만 남았다. D2·D4 는 보류로 충분하다. D3(껍데기 범위)은 T-040a 대체 길 + A-1 보고로 질문이 사라졌다. 남는 것은 1차 D17(추천 갱신)과 D18 이다.
- 실제 플러그인 부하:
  - LuckPerms 는 `PermissibleBase` 를 바꿔 끼워 어댑터 권한 경로를 안 지난다.
  - ProtocolLib 다리는 패킷당 53~115ns 로 작다. 다만 추적 엔티티 감싸개를 쥐어 T-073 문턱을 상시 넘긴다.
  - FAWE 는 편집 어댑터가 없어 기동·ASM 만 쟀다. 편집 부하는 E7 위에서 다시 잰다.

### 6.2 TASK 표

1차 행을 고친 것(T-013·016·033·035·040a~c·041a~b·042·059, F9)은 3장·2장의 해당 자리에 있다. 아래는 새 행이다.

| T-ID | 우선 | 관점 | 내용 | 근거 | 기대 이득·증명 | 크기 | 모델 | 의존 | 겹치는 파일 |
|---|---|---|---|---|---|---|---|---|---|
| T-070 | **P0** | OPT | `CompatInventory.mirror` 약한 참조 목록 폭주: `ReferenceQueue` 로 죽은 참조만 치우거나 "지난 정리 뒤 길이의 2배를 넘을 때만 정리"(분할 상환). 같은 칸 거울 재사용은 빼고 따로 판단 | P-O13-03, 반론 2차 0장 4번 | `getItemInMainHand()` 17~372µs(최악 장면, 실서버는 µs 대 가설) → 약 200ns. 증명: 같은 조작 열을 옛/새 구현에 주고 단계마다 인벤토리·거울 읽기 값 비교(IdentityKit `Differential`) + 기존 `CompatInventory` 시험 | S | Sonnet high | — | CompatInventory |
| T-071 | P1 | OPT | `PluginHost.handleFor` 빠른 길: `worldHandles.get` 먼저, `old.handle() == w` 면 바로 돌려주고 놓쳤을 때만 `compute` | P-O13-01 | 어댑터 호출 바닥 비용, 1→4스레드 처리량 17→2.8 M/s 역전 제거. 증명: 같은 월드 = 같은 핸들, 내리기·다시 만들기 = 새 핸들, 내리기와 조회 동시 반복 시험 | S | Sonnet high | T-042 등 O12 계열과 순서만 | PluginHost |
| T-072 | P1 | OPT | 상태 id 표: 서버 `ApiConvert.block` 불변 `BlockData` 를 상태 id 배열에(기동 때 또는 게으르게, 1.21.1 블록 레지스트리는 데이터 팩으로 안 바뀜), 어댑터 `Material` 표는 블록 번호별(약 1,000칸), Bukkit `BlockData` 는 상태별 불변 + `clone` 으로 내줌 | P-O13-02, 반론 2차 1장 | `Block.getType()` 632ns → 시제품 42.5ns. 증명: 모든 상태 전수 `표 == matchMaterial(...)`(시제품 차이 0) + 스레드 축(소유 region·다른 region·전역 빌림·비동기)에서 결과 종류(값/예외) 같음 | S~M | Sonnet high | #306(T-032) | ApiConvert, CompatBlock, CompatBlockData |
| T-073 | P1 | OPT | 엔티티·플레이어 감싸개 캐시 정리: 키를 아는 `WeakReference` + `ReferenceQueue`(또는 분할 상환 문턱) | P-O13-04(a·b) | 살아 있는 감싸개 2천/2만에서 놓침당 18/144µs → 상수. ProtocolLib 다리 `held` 가 큰 서버에서 문턱을 상시 넘김. 증명: 같은 엔티티 = 같은 Bukkit 객체, 사라진 뒤 회수, 스폰·제거 열 차분 시험 | S | Sonnet high | — | Compat |
| T-074 | P1 | OPT | `getOnlinePlayers()` 보기: T-016 의 전체 접속자 도착 순 배열을 읽고, Bukkit 감싸개 배열은 `rosterEpoch` 가 바뀔 때만 다시 만듦. `isDisconnected()` 거르기 유지(끊김은 `markDisconnected` 가 epoch 를 올림 — 캐시가 그에 기댐). 순회·분할자는 시작 때 배열 하나(C2) | P-O13-05, C2·C5, 반론 2차 0장 3번 | `size()` 704ns → 수 ns, 40명 stream 29.7µs·인덱스 순회 57~76µs(O(N²)) → 선형. 증명 기준은 **Paper 접속 순**과 살아 있는 보기 의미(보관한 참조가 새 접속자를 보여 줌). `for (i<size()) get(i)` 를 region 스레드에서 도는 경합은 남는 한계로 문서에 | S | Sonnet high | T-016 | CraftServer, PluginHost |
| T-075 | P1 | ADD | 호환 기준 장면: O00 기준선에 흔한 Bukkit 플러그인 모양 시험 플러그인 변형(`BlockPhysicsEvent`·`PlayerMoveEvent`·`InventoryMoveItemEvent` 처리기에서 `getType`·`getLocation`·`getItemInMainHand`, 틱마다 `getOnlinePlayers().stream()`, ProtocolLib 엔티티 패킷 리스너). O13 미니 벤치를 #289 벤치 기반으로. T-013 측정에 ProtocolLib 변형 | P-O13-14, 반론 2차 1장 | T-070~074·T-076 의 실서버 몫 측정(지금은 가설), 회귀 감시. 수동 실행(O15 결정) | M | Sonnet high | #289, T-002 | 벤치, furnace-test |
| T-076 | P2 | OPT | Bukkit 이벤트 내용 게으르게: (a) `BlockPhysicsEvent` 블록 데이터는 상태 id 만 잡고 첫 `getChangedBlockData()` 때 만듦 (b) 처리기마다 도는 아이템 변환은 어댑터 `ItemStack` 의 **변경 번호**(모든 변경 메서드가 올림)로 "(객체, 번호) 같으면 생략" — 동일성 판정은 제자리 수정을 놓쳐 기각, 대상은 깔때기(`InventoryMoveItem`)만 (c)는 T-033 에 | P-O13-09, 반론 2차 1장 | `BlockPhysics` 다리 비용 약 −50%(가설), 깔때기 처리기당 아이템 변환 제거. 증명: 반사로 `ItemStack` 의 모든 public 변경 메서드가 번호를 올리는지 전수 확인 + 차분 시험(안 고치면 안 되씀, 고치면 반영). 1.21.1 Bukkit `InventoryMoveItemEvent.getItem()` 이 사본인지 확인 한 줄 | M | Sonnet high | T-072, #306 | Mappings, InventoryResultMappings, ItemStack |
| T-077 | P2 | OPT | 채팅 레거시 렌더러를 받는 사람 무관으로(이벤트당 한 번 계산, Paper `viewerUnaware` 와 같음) + `Mappings.FORMAT_BASE` 를 #306 `EventSlot` 칸으로 | P-O13-08 | 받는 사람당 596ns → 이벤트당 한 번, 두 번째 전역 잠금 제거. 증명: 받는 사람마다 채팅 바이트 비교(PacketTape, 서식 바꾸는 시험 플러그인) | S | Sonnet high | #306, T-035 뒤(렌더 호출 스레드가 바뀜) | Mappings, 채팅 |
| T-078 | P2 | OPT | 어댑터 감싸개 "지금 값" 읽기를 할당 없이: 엔티티 종류 → `EntityType` 표, 월드는 T-071, 이름은 처음 한 번 | P-O13-13 | 호출당 20~80ns(가설). 주목적은 #230 위에서 비용을 늘리지 않기. 증명: `HeldEntityLiveStateTest` + 종류 표 전수 비교 | S | Sonnet high | F9(#230) | CraftEntity, CraftLivingEntity |
| T-079 | P2 | OPT+ADD | ASM 사전 거르기를 바늘 14개 한 번 훑기(첫 바이트 분기)로, `VERSIONED_INTERNAL` 바늘 제거(포함됨). 그 김에 **A-1**: 상수 풀의 `org/bukkit/`·`io/papermc/`·`com/destroystokyo/` 이름 중 어댑터에 없는 것을 플러그인마다 한 줄 경고 | P-O13-10(a), 반론 2차 4장 A-1·5장 | 클래스당 45~63µs → 6~7µs(JDK 15,405 클래스, 판정 차이 0), 플러그인 30개 기동 1~3초(가설). 증명: 실제 jar 8개 전체 클래스에서 옛/새 판정 같음. A-1 은 로그만 | S | Sonnet high | — | NmsClassTransformer |
| T-080 | P3 | OPT | Bukkit 처리기 실행기를 `LambdaMetafactory` 로(Furnace `@Subscribe` 공장 같이 씀), 비공개는 `MethodHandle` | P-O13-11 | 처리기 호출당 약 5ns. 증명: 예외 감싸기(`InvocationTargetException` → `EventException`)·비공개 메서드/클래스 시험. T-040a 대체 길과 같은 PR 이면 실행기 경로 하나 | S | Sonnet medium | T-040a | SimplePluginManager |
| T-081 | P3 | OPT | ProtocolLib 다리: 왕복이 정확한 종류는 "전" 변환 대신 원래 Furnace 패킷을 기준으로(표는 `SchemaPacketReverseTest` 에 묶음) | P-O13-12 | 패킷당 약 30ns. 증명: PacketTape(안 바꾸는/바꾸는 리스너) | S | Sonnet medium | ProtocolLib 2b-3·2b-4 | ProtocolLibBridge |

### 6.3 여는 순서

1. **지금**: T-070, T-040a(#306 병합 직후 — 같은 파일이라 충돌만 피함), F9 #230 병합. T-070 과 T-040a 는 파일이 달라 동시에 연다.
2. **#306 병합 뒤**: T-072 → T-076(a). **#230 뒤**: T-078.
3. **P1 묶음**: T-071, T-073, T-075 는 서로 파일이 안 겹쳐 병렬로 연다. **T-016 뒤**: T-074.
4. T-013 구현 스레드에는 `wave2-rebuttal-compat.md` 2장 A~D 를 그대로 수용 기준으로 넘긴다.
5. **P2~P3**: T-079, T-040b(T-079 의 A-1 결과 순), T-040c, T-077(T-035 뒤), T-080(T-040a 와 함께 가능), T-081(2b-3·2b-4 뒤).

## 7. 2차 CSP — B2 월드젠 확정분 (2026-11-14)

보고서: 공유 폴더 `furnace/reviews/opt-review-r2/B2-worldgen.md`(기준 `a242b1af`). 2차 전체 판정 절은 계획 스레드가 따로 붙인다 — 여기에는 도미니코가 확정한 D8 과 그에 딸린 TASK 만 적는다.

**D8 확정(2026-11-14, 도미니코 "Ok")**: 생성 스레드 기본값은 코어−1 유지, 스레드 수 줄이기(코어÷2)는 기각(32명 새 땅 받은 청크/초 −15%). T-111 바로 적용. T-110 은 B1 청크 파이프라인 판정과 같이 실측 A/B 뒤 적용. 차원 공유 생성 풀·`worldgen-threads` 키(P-O04-09)는 T-111 뒤.

| ID | 우선 | 관점 | 내용 | 출처 | 근거·증명 | 크기 | 모델 | 의존 | 파일 |
|---|---|---|---|---|---|---|---|---|---|
| T-111 | P0 | OPT(운영) | 리눅스 `start.sh`·`jvm.args.example`·운영 문서에 `-XX:ThreadPriorityPolicy=1`. 지금 코드의 `MIN_PRIORITY`(생성 풀·레인·gen-wait)가 리눅스에서 처음 효과를 낸다. 루트가 아니어도 낮추기는 된다(JVM 경고 한 줄 — 문서에 설명). **윈도에서는 효과 없음(윈도 JVM 은 원래 우선순위를 따름)을 문서에 명시**, `start.bat` 은 바꾸지 않는다 | 2차 B2 5장(M-02) | 32명 새 땅 가장 느린 TPS 최저 15.5~17 → 18.5(루트 아님), 오버+네더 동시 12~13 → 17. 받은 청크/초 −3~7%. 계산 불변이라 결과 동일 증명 불필요 | S | Sonnet medium | — | start.sh, jvm.args.example, docs |
| T-110 | P1(측정 뒤) | FIX+/OPT | 생성 요청 대기열을 거리 우선으로: `furnace-gen-wait` FIFO → 요청한 플레이어들과의 지금 거리 우선 큐(나이 가산), 시작 안 한 요청이 어느 시야(+2)에도 없으면 뒤로·취소. 미리 생성(P-O04-15)도 같은 큐 | 2차 B2 5.2, R0 F-R0-01 | 32명 새 땅 `gen.queue` 평균 4.4~6초 vs 생성 60ms. **B1 의 T-011 이웃 기다림 판정과 같이 A/B**(시야 채움·걷기 끝 위치·TPS) 뒤 적용. 블록 결과는 생성 순서 의존(바닐라와 같은 기준) | M | Opus high | B1 F-R0-01 판정, T-111 | AnvilWorldSource, FurnaceMain, Region |
| (P-O04-09) | P1(T-111 뒤) | STR | 생성 풀 차원 공유 + `worldgen-threads` 키(기본 코어−1) | O04-09, 2차 B2 3장 | 두 차원 동시 새 땅 B 기본 TPS 12 안팎. 공정성(차원별 대기 상한) A/B | M | Opus high | T-111 | ChunkPipeline, FurnaceMain |

## 8. 2차 CSP — B3 엔티티·몹 확정분 (2026-11-14)

보고서: 공유 폴더 `furnace/reviews/opt-review-r2/B3-entities-mobs.md`(기준 `a242b1af`). 2차 전체 판정 절은 계획 스레드가 따로 붙인다 — 여기에는 도미니코가 확정한 B3-1 과 그에 딸린 TASK 만 적는다.

**B3-1 확정(2026-11-14, 도미니코 "고쳐")**: 활성 범위(D11) 다음 Spigot·Paper 엔티티 손잡이는 **Paper 1.21.1 기본값과 같게** 둔다(끄는·바꾸는 설정 키는 둠). 기본값은 Paper 원문(`33ff3dff` `Paper-config-files.patch`)으로 확인했다.

| 손잡이 | 바닐라 | Spigot 기본 | Paper·Furnace 기본 | 바닐라와 |
|---|---|---|---|---|
| 아이템 합치기 반경 | 0.5(옆으로만) | 2.5 | 0.5 | 같음 |
| 경험치 구슬 합치기 반경 | 없음 | 3.0 | -1(끔) | 같음 |
| 몹끼리 밀기 상한 | 없음 | 8(추정) | 8 | **다름**(9마리 넘게 겹치면 미는 대상이 줄어듦) |
| 추적 거리 | 종류별 `clientTrackingRange`×16(플레이어 512, 몬스터 128, 동물·주민 160, 아이템 96) | 48/48/48/32 | 플레이어 128, 몬스터 96, 동물·주민·물 96, 기타 96, 디스플레이 128, 그 밖 64 | **다름**(보이는 거리만, 월드 상태는 같음) |

| ID | 우선 | 관점 | 내용 | 출처 | 근거·증명 | 크기 | 모델 | 의존 | 파일 |
|---|---|---|---|---|---|---|---|---|---|
| T-122 | P1 | ADD | Spigot·Paper 엔티티 손잡이 4종을 위 표의 Paper 기본값으로: `merge-radius.item`·`merge-radius.exp`(spigot.yml 이름), `collisions.max-entity-collisions`(paper-world-defaults 이름), `entity-tracking-range.*`(Paper 의 물·주민=동물 범위, 드래곤 예외 포함). D11 과 같은 설정 클래스·`PaperConfigImport` 규칙·`config.preset=vanilla` 에서 바닐라 값. 바닐라와 다른 두 개(밀기 상한·추적 거리)는 PR 본문에 예외로 명시 | P-O05-17, 2차 B3-1 | 시험 JVM 기본은 바닐라 값(D11 의 `EntityActivationOffExtension` 방식)이라 기존 금 파일 그대로. 새 장면(몹 20마리 한 칸 밀기, 추적 거리 경계)으로 켬/끔 차이 금 파일. 합치기 둘은 기본값이 바닐라와 같음을 시험으로 고정. 측정: 밀기 1.6%·추적 3.8~4.1%(봇 16명 땅), 몹 농장 장면에서 다시 잼 | M | Sonnet high | D11(#326) | entity/, config/EntityActivationSettings 옆, PaperConfigImport |

## 9. 60명 동시 새 땅 탐험 (2026-11-14)

보고서: 공유 폴더 `furnace/reviews/w60-explore/W60-explore.md`(기준 `8af93120`, 시제품 diff `repro/`), 독립 검토 `W60-review.md`(같은 시제품 25판 재현, 9장 이견 해소). 클라우드 4코어·루트 아닌 사용자·위치 패킷 봇. 장면 L = 흩어진 60명(생성 처리량), 장면 R = 스폰에서 퍼지는 60명(region 하나, y200). 결정 W-1~W-4 는 `docs/user-todo/entries/2026-11-14-w60-explore.md`. **W-2·W-3·W-4 확정(2026-11-14, 도미니코 "추천대로"): W-2 기본 꺼짐, W-3 만든다, W-4 굶김 없음. W-1 ① 바로 다음 틱·따라잡기 없음(설명 뒤 확정).**

**T-110(7장) 은 T-170 으로 대체한다.** 반론 X 의 "플레이어당 상한 낮추기"는 FIFO 위에 혼자 넣으면 60명에서 역효과(생성 27~33 → 12~23개/초)라 T-170 ③ 으로 흡수했다.

| ID | 우선 | 관점 | 내용 | 출처 | 근거·증명 | 크기 | 모델 | 의존 | 파일 |
|---|---|---|---|---|---|---|---|---|---|
| T-170 | P0 | FIX+/OPT | 생성 요청 대기열 재설계: ① 생성 요청은 그 차원 플레이어들과의 지금 체비셰프 거리 순(주기적으로 다시 잼), 같으면 넣은 순 ② 시야(+여유) 밖이 된 요청은 **시작 전이면 취소하고 플레이어 자리(`chunkPrefetches`) 반납**(원형·장식 중간 상태는 안 버림) ③ 작은 **전역 동시 생성 예산**(생성 스레드 × k, 4코어 8~24)으로 가장 가까운 것만 시작, 플레이어당 상한은 대기 목록에 올리는 수로만. 플레이어 없는 차원의 요청은 FIFO. 미리 생성(T-175)은 맨 뒤 | W60 2-1장, 검토 2장 | 장면 L: 지금 24~33 → 취소·반납 + 작은 상한 31~35개/초(3판), 원형/완성 5.5~6.6 → 3.6~4.3. 시제품 "취소 없음"(45~56)은 지나친 요청이 자리를 막은 사고 값이라 근거 아님(60명 중 약 30명 발밑 0). **통과 조건: 시작 못 한 채 시야 밖에 남은 요청(`batch.gqstale`) 0, 걷는 동안 발밑(링 0~2) 평균 0.05 미만인 사람 0명**, 32명·60명 lines + 1명 첫 시야(반론 X: 고정 상한 4 면 6 → 7.8초 — 나빠지지 않을 것). 측정 칸: 반납 시점 {즉시, 5초} × 예산 {플레이어당 16·4, 전역 8·16·24}. 블록 결과는 생성 순서 의존(1차·B2 기준과 같음) | M | Opus high | W-4 ① 확정 | AnvilWorldSource, FurnaceMain, Region, World |
| T-170a | P1 | OPT(지연) | 빛 미리 계산(`Region.loadChunk` 의 `thenApplyAsync(…, world.genPool())`)을 생성 대기 풀 밖(작은 전용 풀 또는 IO 해독 풀)으로 | W60 2장 `hop.genpool`, 검토 6장 2번 | 생성이 끝난 청크가 빛 계산 전에 14~20초 다시 줄 서는 것과, 디스크에서 읽은 청크가 남의 새 땅 생성 뒤에 서는 것을 없앤다. 처리량은 그대로(검토: 빛만 먼저 28개/초). 빛 결과는 스레드와 무관 — 증명 불필요. T-170 과 독립 | S | Sonnet medium | — | Region |
| T-171 | P0 | OPT(스케줄) | `Regionizer.afterTick` 재스케줄: 틱이 간격을 넘기면 `now + tick`(한 칸 쉼) 대신 `now`(바로 다음 틱), 따라잡기 없음(Folia 와 같음) — W-1 결정대로 | W60 3장, 검토 3장 | 장면 R TPS 평균 9.3~11.6 → 12.4~15.0, 땅 높이(y120) 7.78 → 11.09, 미리 만든 땅 13.6 → 17.2. 장면 L 손해 없음. 게임 계산 불변. 확인 장면: 워커 4개에 무거운 region 5개(60~80ms)+가벼운 region 1개 — 가벼운 region TPS ≥ 19.5·틱 간격 p99, `/tick sprint` 끝·병합 직후 분기 그대로 | S | Sonnet high | W-1 ① 확정 | Regionizer, 지표 문서 |
| T-172 | P2 | OPT(운영) | 생성 조절기: 가장 느린 region 1초 TPS < 19 면 동시 생성 수를 줄이고 회복하면 늘림. 설정 키, **기본 꺼짐(W-2 확정)** | W60 3장 R5thr | TPS 최저 4~6 → 9.8, 생성 −35%(1판). 8코어·반복 필요 | S | Sonnet medium | T-171 | GenPriorityExecutor(또는 T-170 큐) |
| T-173 | P1 | 측정 도구 | `w60.js`(lines/radial, 사람별 링 0~2 분포·이동 거리·되돌림, **땅 높이를 따라 나는 모드**) + `run1.sh`·`sum.py`(생성/초·원형/완성·`gen.queue`·`hop.genpool`·`batch.gqstale`)를 `tools/bot-test/chunkrate/` 로. B1 T-100 과 합침 | W60 8장, 검토 10장 | 다인원 새 땅 PR 의 공통 장면. `gen.queue` 는 시작한 요청만 잰다(생존자 편향)는 주석 포함 | S | Sonnet medium | — | tools/bot-test |
| T-175 | P1 | ADD(운영) | 미리 생성 명령(`/furnace pregen <반경 또는 월드경계>`, T-170 큐 맨 뒤, 진행률·중지·재개, 저장 역압 존중) + 운영 문서 "다인원 탐험 서버: 월드 경계 + 미리 생성" — P-O04-15(D9) 와 같은 것 | W60 3장 R4disk | 같은 길을 미리 만든 땅에서 CPU 278 → 136%, TPS 17.2(T-171 과 함께). 같은 생성기라 결과 동일 | M | Sonnet high | T-170(W-3 확정) | command/, world/ |
| T-176 | P2 | ADD(호환) | Spigot `mob-spawn-range`(기본 8 = 바닐라 128블록과 같음)를 설정 키로. 낮추면 Spigot 과 같고 바닐라와 다름(문서 명시) | W60 6장 | 땅 높이 장면에서 스폰 몫부터 잰 뒤 우선순위 확정(y200 장면은 몹이 덜 붙음) | S | Sonnet medium | T-173 땅 높이 장면 | config/, NaturalSpawner |
| T-177 | P2(측정 먼저) | OPT | `Region.collectTickingChunks` 의 로드 청크 × 플레이어 훑기(장면 R 약 8,000청크 × 60명)를 플레이어 쪽 17×17 칸 모으기로, 청크 순서는 지금과 같게 | 검토 6장 3번 | 같은 집합·같은 순서면 결과 동일. 몫은 아직 안 잼 — dontinline 프로파일 먼저 | S | Sonnet high | — | Region |

메모: 장면 R 의 region 스레드 구성비(자연 스폰 52%·`Regionizer.owns/ownerOf` 자기 시간 20%)는 y200 장면·인라인 미확인 값이라 T-019·자연 스폰 TASK 의 근거로 쓰지 않는다 — 땅 높이 장면과 `-XX:CompileCommand=dontinline` 판으로 다시 잰다.
