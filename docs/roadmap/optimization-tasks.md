# 최적화·기능 개선 TASK 목록 (1차, 2026-11-12)

`docs/audit/optimization-review-plan.md` 의 검토 프로세스(D1 보고서 → D2 반론 → D3 답변 → D4 판정)를 거친 결과다. 기준 커밋은 `07dec3da` 이다. 근거 보고서는 프로젝트 공유 폴더 `furnace/reviews/opt-review/` 에 있다(`O00`~`O15`, 반론 `wave1-rebuttal-core.md`·`wave1-rebuttal-sim.md`·`wave1-rebuttal-api.md`, 각 보고서 끝의 "답변" 절).

코디네이터는 이 표대로 구현 스레드를 연다. 구현 PR 은 기존 규칙대로 독립 검토 → CI `test` → 병합 순서를 따른다. O13(호환·NMS·패킷)은 #267·E4 코덱·ProtocolLib 주입이 병합된 뒤 따로 검토하고, 이 문서에 2차로 덧붙인다.

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
| T-013 | P1 | STR | 송신 경로: 플레이어별 MPSC 큐 + 한 버퍼 인코딩, 큐에 Runnable 입구를 둬 ConnectionHandler 이벤트 루프 작업 26곳을 같은 순서로 | O03-01·02, 반론 A·C 3.3, O03 답변 | 50명×엔티티 200 서버 루프 CPU −69%, 나가는 바이트 동일(PacketTape) | M | Opus high | T-001, O13(ProtocolLib 주입 길) 조율 | ConnectionHandler, Player |
| T-014 | P1 | STR | 저장 IO: 인코딩·압축 레인 밖(05 → 03) → 청크 단위 쓰기 대기(Paper RegionFileIOThread 식, 04) | O02-03·04·05, O02 답변 순서 | .mca 저장 256개 뒤 읽기 300~404ms → 112~131ms | M~L | Opus high | T-001 | world/storage, anvil |
| T-015 | P1 | OPT | 팔레트 바로 쓰기 도우미: 전송 직렬화(O00-06) + 저장 펼치기 제거(O02-07) | O00-06, O02-07, 반론 A 9.1 | 저장 86~144µs·391KB/청크, 전송 팔레트 unpack 할당 37%. 증명: 바이트 동일 | M | Sonnet high | T-001 | ChunkSection 직렬화 |
| T-016 | P1 | OPT | 플레이어 목록을 차원별 배열로(바닐라처럼 도착 순 append, 리스폰 순서 확인) → 청크 격자 플레이어 색인(O05-02) | O05-01·02, O00-03, 반론 A K2 | 실서버 꼴 약 −30%, 격자까지 가야 나머지. 바닐라 순서로 정확성 K2 함께 해결 | M | Opus high | T-001 | World |
| T-017 | P1 | OPT | 추적기 `playerMoved` 후보 줄이기 | O05-03 | E2000×P32 틱당 1.5~5ms. 상태 변화 집합 동일 증명 | M | Opus high | T-001 | entity/view |
| T-018 | P1 | STR | 충돌 수집 재작성(섹션 단위·공기 섹션·cave_air·모양 할당 제거, 모양 목록 순서 보존) | O05-07, O00-10 소 3000 장면 | 충돌 몫 −50% 가설(몹 틱 −8%). 옛/새 수집기 무작위 대조 | L | Opus high | F3, T-001 | ChunkLevel, Entity |
| T-019 | P1 | OPT | 작은 안전 묶음 A(스케줄러·청크): 곧 될 머리 당겨 꺼내기(O01-03, 스케줄러 CPU −21%), `ownerOf` 이중 조회·박싱·해시 겹침(O01-05②, O00-15), O02-05·06·13·14, O03-03(암호 인코더 배열)·06(직렬화 버퍼 −25%)·13 | 반론 A 7장 T1 | 각 S. 증명 각 항목 | S×여럿 | Sonnet high (O01-05 는 Opus high) | — | sched, world, net |
| T-020 | P1 | OPT | 작은 안전 묶음 B(월드젠·엔티티): 장식 레인 다이어트(O04-02·03, 실측 −13~18%), 엔드 불필요 단계(O04-06·07, −26% 해시 동일), O04-08, O05-05·09(문자열 태그 조회 상수화)·10·12 | 반론 A T1, O04 답변 | NoiseIdentityBench·SharedPipelineBench 해시 | S×여럿 | Sonnet high | — | world/gen, entity |
| T-021 | P1 | OPT | 월드젠 M: 구조물 높이·바닥 열을 기둥 단위로(O04-04), 기후 R-트리 부분 거리 캐시(O04-05), 그 뒤 O04-12 | O04-04·05·12 | 오버월드 CPU 7.3%·9.4%, 네더 13.9%(DebugNonSafepoints 로 낮춘 지분). 해시 | M | Opus high | — | world/gen |
| T-022 | P1 | ADD | 워치독 조기 경고(Paper early-warning, **기본 켬 — 도미니코 확정**) + O01-09 가장 큰 region 몫 | O01-08·09 | 운영 | S | Sonnet medium | — | ServerWatchdog |
| T-023 | P1 | OPT | 기동: AppCDS 자동 보관(start.sh·start.bat·문서, −23~25%), RSA 키·HttpClient 지연(O14-02), `BiomeFeatureData.load`·`BlockRegistry.load` 이진 사본·지연(O14-04 재측정 순위), 생성기 만들기 병렬(O04-19) | O14-01·02·04, O04-19, 반론 C 5.1 | 다시 켜기 3.4초 → 2.6초 안팎 | M | Sonnet high | — | start.sh, ServerCore |
| T-024 | P2 | OPT | 요새 고리: 월드를 열자마자 배경 계산(O04-17) + 다시 켠 월드용 고리 파일(O00-08, 키 = 시드·생성기·데이터 팩 해시) | O04-17, O00-08, 반론 A 9.5 | 새 월드 기동 CPU 57% 가 고리. 결과 동일(같은 계산) | M | Opus high | — | world/gen 구조물 |
| T-025 | P2 | FIX+ | 설정 키: zlib 레벨(기본 6 유지, O02-09), 청크 프레임 압축 레벨 키 공개(기본 6, O03-05), 역압 최대값 기준(O02-06 측정 먼저) | O02-06·09, O03-05 | 운영자 선택 | S | Sonnet medium | — | config |

### 3.3 측정 TASK (결과로 다시 판정)

| T-ID | 우선 | 무엇을 재나 | 근거 | 모델 | 다시 판정할 것 |
|---|---|---|---|---|---|
| M-01 | M | region 구역 s4r1·s3r1·s2r2 실측: 봇 32명 흩어짐 MSPT·합치기/나누기 횟수·`splitLocked` 잠금 시간, 8코어 장면 포함 | O01-01, 반론 A 1.1, **도미니코: 실측 뒤 결정** | Opus high | 결정 1 |
| M-02 | M | CPU 예산·스레드 지도: 생성이 region 틱을 굶김(32명 생성 77 → 43청크/초, 가장 느린 TPS 16~17), 리눅스 스레드 우선순위 무시, 두 차원 동시 | O00-04, 반론 A 3.6, O04-01·09·10 | Opus high | 적응 제한 설계, 생성 스레드 기본값(결정 8), 장식 레인 병렬화(O04-01) |
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
| T-033 | P1 | OPT | 이벤트 구독 시 칸 전체 변환: ① 패치 변환 캐시(먼저) ② 지연 스냅샷(T-032 도우미 뒤) | O09-01, O09 답변 | 54칸 스냅샷 23.5µs → 1/20~1/200(가설, A/B) | S+M | Sonnet high | T-030, T-032 | 메뉴·이벤트 |
| T-034 | P1 | STR | 서버 접속 이벤트(AsyncPlayerPreLogin·Configure)를 플러그인 비동기 실행기에서 분리 + 실행기 기본값(결정 D1) | O12-02 | 가상 스레드 pinning 452ms → 1ms | M | Opus high | — | plugin |
| T-035 | P1 | STR | 채팅: 연결별 순서 사슬 하나(chat·chat_command·chat_command_signed·chat_session_update 4패킷). 채팅은 사슬에서, 명령은 전역에 넘기고 전역 람다 반환까지 `whenComplete` 로 기다림(join 금지), 큐 상한, 세션 갱신 중간 시험 | O12-03, O11-07, 반론 C 3.3 2라운드 합의, 결정 D2 | 플러그인 블로킹 채팅 처리기가 전역(시간·명령·RCON·#tick)을 막지 않음 | M | Opus high | T-034, T-013 의 순서 계약 | ConnectionHandler:476·536·641-688 |
| T-036 | P1 | FIX+ | 백업 zip 의 지역 파일 압축 옵션(`backup.compression`, 기본은 결정 D3: STORED 면 CPU −99%·크기 +40~55%, parallel 선택지) | O14-13 + O02-11, 반론 A·C 3.4 | 400MB 11.6초 → 0.69초(STORED) | S | Sonnet medium | — | maintenance/backup |
| T-037 | P1 | OPT | 플러그인 레시피 등록 일괄 + 방송 합치기 | O10-03 | 등록마다 1.3ms·2.3MB + 108KB 전원 재전송 제거 | S | Sonnet high | — | recipe |
| T-038 | P1 | ADD | 컨테이너 API: 블록·엔티티 컨테이너 접근(O09-17) → 임의 메뉴 모양 창 열기·`createInventory` 확장·openMerchant(O09-11, 결정 D8) | O09-11·17 | /invsee·가상 모루·작업대 플러그인 | M→L | Sonnet high → Opus high | 결정 D8 | furnace-api, menu |
| T-039 | P1 | OPT | 명령 후속(F2 뒤): `execute as/at @e` 리전 단위 묶기(O11-02), 선택자 색인·무관 리전 방문 제거(O11-03·04, 측정 먼저) | O11-02~04, 도미니코 O11 결정 | 엔티티 100개 100ms | M | Opus high | F2 | command/exec |
| T-040 | P2 | ADD | 이벤트 확충: H1 → 많이 쓰는 10개 → 나머지(결정 D9). 아이템 손상·줍기·화로·거래·발전 과제·전리품·BlockDropItem(플레이어 파괴 경로) 등. 발화 지점 구현은 O09 쪽 TASK | O09-12, O10-A2·A4, O12, 반론 C 3.9 | 호환 | M×여럿 | Sonnet high | H1, T-032 | 발화 지점 |
| T-041 | P2 | ADD | Bukkit 레시피 API(`ExactChoice` 는 Paper 한계와 같게, 결정 D10) | O10-A1 | 호환 | M | Sonnet high | 결정 D10 | compat (O13 조율) |
| T-042 | P2 | OPT | 클래스로더 이름 색인(다른 플러그인 jar 찾을 때 CNFE 만들기 제거, 60~90µs → 0.05µs), `PluginTasks.guard` 구간을 프로파일러 꺼짐 때 생략 | O12-05·04 | 플러그인 60개 서버 | S×2 | Sonnet high / medium | — | plugin/library |
| T-043 | P2 | OPT | `PlayerAdvancements` 공유 표·비트집합·지연 진행(저장 파일 키 순서 재현 조건) | O10-02 | 플레이어당 647KB·접속 1.16ms. 시제품 측정 먼저 | M | Opus high | M | advancement |
| T-044 | P2 | FIX+ | 운영 묶음: 비동기 로그(`discardingThreshold=0`·`neverBlock=false`·halt 때 비우기), 메트릭·단계 지표(O00-13·O01-09·O03-12·O14-16 하나로), 진단 명령 묶음(반론 C 3.7), OOM 동작(결정 D5), 바닐라 데이터 없을 때(결정 D6), 설정 다시 읽기(결정 D7) | O14-11·16·17·18·22, 반론 C 3.7 | 운영 | M | Sonnet medium | — | config, metrics |
| T-045 | P2 | ADD | Spigot 설정 `advancements.disabled`·`*.disable-saving`(기본 꺼짐, 결정 D11) | O10-A5 | 호환 | S | Sonnet medium | 결정 D11 | config |
| T-046 | P2 | OPT | 데이터 팩 적재: EntityPredicate 정적 플래그를 문맥 객체로 옮긴 뒤 전리품·발전 과제 병렬 파싱 | O11-06, O10 답변 | `/reload`·기동. KillCredit 결과 동일 | M | Sonnet high | — | loot, advancement |
| T-047 | P3 | OPT | 콘솔 명령 틱당 하나만 처리 → 바닐라처럼 큐가 빌 때까지(바닐라 `handleConsoleInputs` 확인 먼저) | O00-14 | 정확성 가능성 | S | Sonnet medium | — | ServerCore |
| M-10 | M | — | 측정: O14-05 데이터 팩 적재(도미니코 PC, Mojang 403), O14-09·14, O10-02 시제품, O12-06/O08-07 레드스톤 이벤트 A/B, 점수판 SetScore 합치기(결정 D12) | 반론 C 7장 | — | — | Sonnet medium~high | — | — |

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
| T-059 | P2 | FIX+ | 게임 비용 진단(몹 AI·예약 틱·레드스톤 폭주·Spark 대체): 계수기·표 하나, 꺼짐이면 비용 0 | O06-16, O07-14, O08-08, O14-19, 반론 B 4장 7번 | 운영 | M | Sonnet high | — | metrics (T-044 와 조율) |
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
4. **결정 뒤**: T-034(D1)·T-035(D2)·T-036(D3)·T-038(D16)·T-040(D17)·T-041(D18)·T-045(D19)·T-061(D24)·T-062(D22).
5. 측정 TASK(M-01~M-21)는 계산·실행이 길어 코디네이터 판단으로 Fable 계산·실행 대기열(한 번에 하나)에 넘길 수 있다. 결과는 이 문서의 해당 행을 다시 판정한다.

## 5. 도미니코 결정

이미 정한 것:
- O11: 명령 region 홉 방식(F2), #tick 은 글로벌 틱이 함수 완료까지 기다림(5초 한도).
- O01 C1 region 구역 기본값 = 실측 뒤 결정(M-01). O01 C2 워치독 조기 경고 = 기본 켬.
- O15: 운영 코드 시험용 난수 이음매 허용, 금 파일 갱신 = 독립 검토 승인 + PR 사유, 야간 벤치는 수동 실행부터.
- O07: 폭발 가림 비율 기억은 Paper 식 옵션·기본 꺼짐으로 진행(T-063). 빛 계산 풀 분리는 보류하지 말고 해결 방법을 찾을 것(T-064). 결과물이 바닐라와 다르거나 문제가 생길 것 같으면 반드시 보고.

남은 결정은 `docs/user-todo/entries/2026-11-12-opt-review-wave1.md` 에 표로 정리했다.
