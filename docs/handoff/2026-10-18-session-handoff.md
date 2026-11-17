# Furnace 세션 인수인계 (2026-10-18)

> **주의(2026-11-06 표시):** 이 문서의 **1.3, 6장, 7장, 8장은 낡았다.** 현재 상태·다음 할 일은 `docs/handoff/2026-10-31-session-handoff.md` 를 본다(그 문서 첫 표가 대체 범위를 적는다). 2장(확정된 결정·설계 원칙), 3장(규칙), 4장(환경·도구), 5장(아키텍처)은 그대로 유효하다. 이 문서는 내용을 지우지 않고 보관용으로 둔다.

이 문서는 새 Claude Code 세션이 **맥락 손실 없이** 작업을 이어가도록 쓴 인수인계서다. 새 세션은 새 클라우드 컨테이너에서 시작하므로 `/tmp` 아래 모든 것(디컴파일 소스, 바닐라 리포트, 스크래치패드, 로그)이 사라진다. 살아남는 것은 git 에 커밋된 것뿐이다. 그래서 사라지는 정보(미구현 인벤토리, 오라클 실행 스크립트, 난독화 클래스 매핑, 패킷 id 등)는 이 문서에 전부 옮겨 두었다.

- 작성 시점: 2026-10-18, 브랜치 `claude/work-continuation-u597cw`, 코드 기준 HEAD `3738fd79` (4-1b). 이 문서를 처음 커밋한 것이 그 위의 `077b29ef` 이고, 2장(확정된 결정과 설계 원칙)을 추가한 개정은 그 뒤 커밋에 들어간다.
- 직전 세션 사용자 방향: **다음은 메뉴(4-2)**
- 사용자에게는 **항상 한국어**로 말한다. 커밋 메시지도 한국어, 코드 주석도 한국어(간결, 기존 스타일).
- **먼저 읽을 순서:** 1장(한눈에) → **2장(확정된 결정과 설계 원칙 — Paper/Bukkit 어댑터 계획, "똑같은 결과 + 비효율은 재설계" 원칙, 스레딩·검증·범위 결정, 사용자 지시)** → 3장(규칙) → 8장(다음 할 일). 2장은 코드나 로드맵 문서 하나만 봐서는 알 수 없는 결정을 모은 것이다.

---

## 1. 한눈에 보기

### 1.1 Furnace 가 무엇인가

Furnace 는 **처음부터 새로 쓰는 멀티스레드 Minecraft 1.21.1 서버**(Java 21 / Gradle)다. 사용자의 말(원문 그대로):

> "명심해. 해당 프로젝트는 기존의 버킷이랑 똑같은 결과물을 보여주되, 최적화 이랑 성능을 최대한 뽑는 버킷이야" (2026-10-08)
> "원본 마크혹은 페이퍼랑 완전히 같아야해" (2026-10-07)
> "기존 마크 버킷에서 비효율적인 부분은 재설계해서 다시 만드는 방식" (2026-10-18)

해석: 관측 가능한 동작이 바닐라/Paper 와 **동일**해야 한다(난수 호출 횟수·순서까지). 그 위에서 성능을 최대한 뽑는다 — 관측 결과를 바꾸지 않는 한 **바닐라/Bukkit 의 비효율적인 내부 구조는 재설계해서 다시 만든다**(2.1.3). 스레딩은 **Folia 방식 동적 regionizer**다. 즉 "이 정도면 비슷하다"로 넘어가지 말고, 결과에 영향을 주는 로직은 바닐라 그대로 이식하되(난수 소비 순서 포함) 스레드 모델은 region 단위로 바꾸고, 결과에 영향이 없는 구조는 더 싸게 만든다. 또 플러그인은 지금 **자체 API(`furnace-api`)** 만 지원하고, **Paper/Bukkit 플러그인은 5단계의 별도 어댑터 모듈 `furnace-compat-paper`** 로 나중에 지원한다(2.3).

### 1.2 모듈

| 모듈 | 내용 |
|---|---|
| `furnace-api` | 플러그인 API(`org.domi.furnace.api.*`): 텍스트 타입, 설정, 이벤트, 스케줄러 등. Paper/Bukkit 어댑터를 나중에 붙일 수 있게 설계 |
| `furnace-server` | 서버 본체(`org.domi.furnace.server.*`). 대부분의 작업이 여기 |
| `furnace-plugin-test` | 샘플 플러그인 |
| `furnace-compat-paper` | **아직 없음 — 로드맵 5단계 예정** Paper/Bukkit 플러그인 호환 어댑터(2.3). `furnace-api` 위에 Bukkit/Paper API 구현을 얹는 별도 모듈 |
| `worldgen-oracle` | paperweight-userdev 기반 월드젠 대조 도구. Paper 저장소에 접속해야 해서 **`ORG_GRADLE_PROJECT_skipOracle=true` 로 제외**해서 쓴다(항상) |

### 1.3 지금 어디까지 왔나

- 월드젠(오버월드·네더·엔드·구조물 전종), 서버 뼈대(설정·월드 형식·온라인 모드·서명 채팅·명령 프레임워크·게임룰·월드 보더·플레이어 생존), 플러그인 API v0, 데이터 팩/레시피/전리품/발전 과제/아이템 컴포넌트, 블록 241종 동작·파괴·무작위 틱·유체·피스톤·비교기, 아이템 사용, **서버 권위 인벤토리 + 엔티티 틀 + 아이템 엔티티(4-1)** 까지 완료.
- HEAD: `3738fd79 4-1b 엔티티 틀 + 아이템 엔티티: 물리·추적·줍기·저장`
- **다음 작업: 4-2 컨테이너 메뉴 프레임워크**(서버 권위 `AbstractContainerMenu`). 지금 메뉴는 25종 중 상자 9x3 하나뿐이고 그것도 클라이언트 결과를 총량 검사만 하고 받아들이는 방식이다.
- 이후 순서: 메뉴 → 명령(83개 중 43개 없음) → 전투/효과/속성 → 폭발 → 빛 엔진 → 청크 티켓 → 몹 → 차원.

---

## 2. 확정된 결정과 설계 원칙

이 장은 **코드만 봐서는 알 수 없는 프로젝트 차원의 결정**(철학, 아키텍처 선택, 성능 전략, 검증 방법, 범위·순서, 플러그인/어댑터 계획, 사용자가 직접 지시·금지한 것)을 한곳에 모았다. 새 세션은 코드를 건드리기 전에 이 장을 먼저 읽는다. 아래 3장 이후의 "규칙·환경·진행"은 이 결정들의 결과다.

- **충돌 시 우선순위:** 사용자의 최신 지시(대화) > `roadmap-2026-10-09.md` 의 "확정된 결정" > 개별 spec/plan 문서. 옛 spec 에 적힌 "비목표"는 2.6 끝의 "결정 변경 이력"에서 대부분 뒤집혔다.
- **출처 표기:** 각 항목 끝의 `[…]` 는 근거다. 사용자의 말은 원문 그대로 인용하고 시각(UTC)을 적었다 — 한국 시각은 +9시간.

| 약어 | 문서 |
|---|---|
| `R` | `docs/feature-parity/roadmap-2026-10-09.md` ("확정된 결정" / "제작 순서" / "어댑터 대비 설계 원칙" / "검증 방법" / "자료 막힘") |
| `S0` | `docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md` (최초 설계서) |
| `DR` | `docs/superpowers/specs/2026-10-17-dynamic-regionizer.md` |
| `RW` | `docs/feature-parity/remaining-work-2026-10-08.md` |
| `WM` / `VA` / `VD` | `worldgen-measurement-2026-10-08.md` / `vanilla-audit-2026-10-07.md` / `vanilla-diff-2026-10-07.md` (모두 `docs/feature-parity/`) |
| `FP` | `docs/feature-parity/{plan,checklist,context-notes}.md` (feature 패리티 작업 기록) |
| `TXT` | `docs/api/text.md` (텍스트 API 사용 설명) |
| `T 날짜 시각` | 직전 세션 대화 기록에서 뽑은 사용자 발언 |

### 2.1 프로젝트 철학 — "똑같은 결과 + 가장 빠른 내부"

#### 2.1.1 한 줄 정의

> "일단 해당 프로젝트 전제가 기존 바닐라, 페이퍼, 스피곳 런처 처럼 작동해야하는거임. 이 프로젝트의 목적은 기존 버킷들 보다 최적화가 훨씬 나은 버킷을 만들기 위해서 처음부터 끝까지 다시 만드는거임." `[T 2026-10-07 13:14]`
> "명심해. 해당 프로젝트는 기존의 버킷이랑 똑같은 결과물을 보여주되, 최적화 이랑 성능을 최대한 뽑는 버킷이야" `[T 2026-10-08 15:16]`
> "원본 마크혹은 페이퍼랑 완전히 같아야해" `[T 2026-10-07 12:46]` (탭 목록 동작을 두고 한 말 — "비슷하면 됐다"는 답을 받지 않는다는 뜻으로 일반화해 받아들였다)

즉 목표는 **Paper/Spigot/Bukkit 서버의 대체품**이다. 플러그인 관리자·플레이어·클라이언트가 보기엔 같고, 안에서는 더 빠르다. "바닐라 재현"이 목적이 아니라 **"같은 결과를 더 싸게"**가 목적이다.

#### 2.1.2 "같다"의 기준 (관측 동일성)

1. **기준 동작 = 바닐라 1.21.1, 그 위에 Paper 가 바꾼 것.** 바닐라와 Paper 가 다르면 Paper 를 따른다(예: 참가 메시지를 본인에게도 보냄, 서버 콘솔 색 출력, 플러그인 명령 이름 충돌을 `플러그인:이름` 으로만 해소, Velocity modern forwarding 규약, 패킷 속도 제한). Paper 고유 동작 목록은 4단계에서 만든다. `[R 확정 3, R 2-8d, R 4단계, RW A-3]`
2. **관측 가능한 것은 전부 같아야 한다:** 패킷 내용·순서·바이트(캡처와 SHA-256 으로 비교), 저장 파일(NBT/region/`level.dat`/`server.properties` 바이트), 명령 결과·트리, 블록 단위 월드, 그리고 **난수를 부르는 횟수와 순서**(이후 모든 결과가 거기에 매달려 있다). `[R 지금 상태 / 2-1~2-5, S0 M4g "시드 비트 일치"]`
3. **바닐라 자신이 비결정적인 곳은 "통계적으로 같음"까지 허용:** 이웃 feature 겹침으로 같은 명령 2회가 0.05~0.6% 달라지는 장식 단계, 스레드가 나뉜 region 별 무작위 틱 난수(2.2). 이런 곳은 "같다고 거짓 주장하지 않기"가 규칙이다. `[VA, WM, R 3-4c]`
4. **정직성 가드(거짓 parity 금지):** 구현하지 못한 것을 근사로 때우고 "일치"라고 하지 않는다. 미구현은 no-op placeholder(feature 목록의 index 보존용 `NoopFeature`)나 `TODO(태그)` 로 **드러내 두고**, 확인 못 한 것은 확인 못 했다고 보고한다. 가설을 사실처럼 쓰지 않는다(예: Mojang 코드가 없을 때 기억에 의존한 부분은 "미검증"으로 표시). `[FP checklist slice 1 "정직성 가드", FP context-notes, R 2-8c "원본 대조 못 함"]`
5. 바닐라와 **의도적으로** 다르게 만든 곳은 코드 주석 + 로드맵에 이유를 적는다. 현재 알려진 의도적/구조적 차이: region 별 무작위 틱 난수, 청크 시야가 원형이 아니라 정사각형, 장식 순서(Furnace 는 공유 월드에서 청크마다 한 번), 조명 엔진 없음(미구현이라 풀브라이트). `[R 3-4c, R 청크 생성·전송, 이 문서 5.8]`

#### 2.1.3 "비효율은 재설계해서 다시 만든다" 원칙

> "기존 마크 버킷에서 비효율적인 부분은 재설계해서 다시 만드는 방식" `[T 2026-10-18 05:31]`
> (직전 세션의 요약, 사용자가 이의 없이 진행시킴) "겉으로 보이는 결과는 바닐라·Paper·Spigot과 똑같이 맞추고, 내부 구현은 성능을 최대한 뽑는 게 이 프로젝트의 목표 … 호출마다 객체를 만들지 않고, 조회 결과를 캐시하고, 불필요한 반복을 줄이는 쪽으로" `[T 2026-10-08 15:16 직후 답변]`

규칙: **"관측 결과가 같다"는 제약만 지키면, 바닐라/Bukkit 의 내부 구조를 줄 단위로 베낄 이유가 없다.** 베끼면 오히려 손해인 곳(호출마다 할당, 반복 스캔, 단일 스레드 가정, 락, 불필요한 변환 계층)은 **바닐라가 낸 결과만 보존하고 구조는 새로 설계**한다. 새 코드를 쓸 때마다 이 판별 질문을 던진다.

1. 이 코드의 **관측 결과**(패킷·저장·난수 순서·순회 순서)는 무엇인가? → 그것만은 정확히 보존한다.
2. 그 결과에 필요 없는 **중간 구조**(객체 생성, 재계산, 스캔, 변환)는 무엇인가? → 없애거나 캐시/사전 계산한다.
3. 결과가 **순회 순서나 해시 순서**에 의존하는가? → 구조는 바꿔도 순서는 재현한다(예: 피스톤 갱신 순서·레드스톤 가루 갱신 순서는 바닐라 `HashMap`/`HashSet` 순회 순서를 같은 해시로 재현, 엔티티 조회는 바닐라 `SectionPos` 정렬 순서).
4. 바이트 단위로 같게 못 하는 최적화는 **채택하지 않거나 보류**한다(예: DF 컴파일러 — 2.4).

**이미 이렇게 재설계한 구체 사례** (새 코드의 본보기):

| 영역 | 바닐라/Bukkit 또는 초기 방식 | Furnace 방식 (관측 결과 동일) | 출처 |
|---|---|---|---|
| 지형 생성 | 블록마다 밀도 함수 트리 호출 | 4×8×4 셀 코너 보간, thread-local `NoiseChunk` 버퍼, 표면 규칙 컴파일(기둥 단위 캐시), 대수층 공기 단락, 구조물 시작점 위치 캐시, 직소 속성 캐시, 카버가 청크의 `NoiseChunk` 재사용 → 지형 40→21.5 ms/청크, 출력은 비트 동일 | `S0 M4g`, `R 청크 생성·전송` |
| 장식(feature) | 초기 Furnace: 청크마다 이웃 9칸을 독립 생성(9× 중복) | 바닐라처럼 공유 월드에서 청크마다 한 번(`ChunkPipeline.generateShared`) — 비용 1/9. 생성은 region 틱 스레드 밖 gen 풀 | `R`, `docs/superpowers/specs/2026-06-21-m4gd-feature-pipeline-design.md` |
| 생성 중 청크 보관 | 초기 Furnace: 미완성 가장자리 청크를 상한 2048 저장소에 쌓음 → 장거리 비행 1~2분 뒤 로딩 붕괴 | 오래 안 쓰인 가장자리 청크는 섹션 압축해 보관함(상한 16384)으로 옮기고 되살릴 때 같은 객체, 최근 청크는 버리지 않음 → 5분 비행에서 저하 없음 | `R 2026-10-11` |
| 이벤트 | 이벤트 객체 항상 생성, 핸들러 리스트 순회 | 핸들러 배열을 등록이 바뀔 때만 미리 굽고, **구독자가 없으면 이벤트 객체도 만들지 않음**(`EventBus.hasSubscribers`) | `R 2-8d`, `furnace-api EventBus` |
| 텍스트 | 호출마다 직렬화 | 불변 `Text` 가 인코딩 바이트를 한 번만 만들어 품음, 방송은 1회 인코딩 후 공유, `TextTemplate` 은 미리 컴파일 | `R 2-8a/c`, `TXT` |
| 레시피/태그/발전 과제 패킷 | 필요할 때마다 | 한 번 만들어 둔 바이트 재사용, 바닐라 캡처와 SHA-256 일치 | `R 3-1, 3-2a, 3-2c` |
| 청크 전송 | 단순 순서 전송 | 플레이어당 동시 요청 상한 + 현재 위치 기준 nearest-first, 생성 스레드는 코어 수 비례, "Done" 전 스폰 지역 준비, 직렬화는 워커 풀 → 소유 region 에서 순서대로 송신 | `R 청크 생성·전송`, `docs/remote-pr1-port/context-notes.md` |
| 팰릿 | 단순화 유혹 | "direct-only 단순화 거부" — 단일+간접+직접 전부 구현해 대역폭 최소 | `docs/superpowers/specs/2026-06-07-m2-world-chunk-transfer-design.md` |
| 패킷 처리 | 단일 메인 스레드 | IO 스레드는 파싱만 하고 변경은 소유 region 으로 `execute`; 사전 로그인은 가상 스레드 | `S0`, `R 2-8d` |
| 데이터 로딩 | — | 바닐라 데이터는 저장소에 넣지 않고 공식 서버 jar 에서 첫 실행 때 추출(Paperclip 방식) + 파생 압축 표(`blocks/*.txt` …)만 커밋 | `R 3-1`, `tools/vanilla-data/README.md` |

**반대로, 구조가 비효율적이어도 관측 때문에 일부러 바닐라 그대로 따른 곳** (재설계 금지): 난수 호출 순서(예: `Containers.dropContents` 는 빈 칸도 위치 난수 3번), `HashMap`/`HashSet` 순회 순서에 매달린 갱신 순서, 부분 줍기 때 스택이 조용히 줄어드는 `Inventory.add` 특이점, 땅의 아이템이 4틱마다만 이동하는 것, 블록 이벤트 큐의 중복 제거(`ObjectLinkedOpenHashSet`), 엔티티 가시성 재판단 시점(엔티티 구역 이동·플레이어 이동·추가) 등 — 4-1 이후 5.4 참조. 이 구분이 흐릿하면 "바닐라와 같아야 한다"가 이긴다.

**아직 재설계하지 않은(앞으로 할) 후보:** 네트워크 flush 합치기(`WriteBufferWaterMark`), 적응형 청크 전송(`chunk_batch_received`), 리전 파일(RegionFile) 개선, 장식 저장소의 압축 전 블록 배열(≈700MB) 축소, 미완성 청크의 디스크 저장, 청크 언로드 검사의 이벤트 기반화(매 틱 전수 스캔 제거), 지형 노이즈 최적화(현재 CPU 의 대부분), 방침 B(공유 ProtoColumn 마샬링)로 장식 중복 비용 제거. `[R 4단계 성능 작업, R 청크 관리 검토, RW P3, 2026-06-21 m4gd-feature-pipeline §3.3]`

#### 2.1.4 우선순위와 태도

- **정확성 > 최적화.** 최적화는 "byte-exact 안 깨는 선에서" 한다. 병목을 **측정한 뒤** 최적화한다("병목 확인 후"). `[2026-06-21 m4gd-feature-pipeline §3.5, 2026-06-10 m4ga §성능]`
- 설계 트레이드오프는 "정석·완전최적 쪽"을 택하고 단순화로 도망가지 않는다. 성능 기준점은 **Paper 의 한계를 넘는 것, Pumpkin/Leaf/Folia 급**. `[2026-06-07 m2 §1]`
- 사용자는 하다가 **최적화할 요소가 보이면 말해 주길** 원한다: "하다가 최적화할 요소 있으면 말하고" `[T 2026-10-10 16:29]`. (조용히 큰 리팩터링을 하지 말고 제안한다.)
- 성능 목표의 구체 문구: "빨라지긴 했는데. 좀더 빠르게는 안되나? PUMKIN BUKKIT 정도의 속도를 원하는데...." `[T 2026-10-10 14:52]` (청크 로딩 속도, Pumpkin 은 러스트로 쓴 서버). 측정 상황과 한계는 2.4.
- 최초 설계서의 성격 문구: "학습·장기 하비 프로젝트 … '천천히 그러나 완벽하게'". 지금 작업 속도는 훨씬 높아졌지만 **완벽(정확성) 우선**이라는 페이스는 유효. `[S0 §1]`

#### 2.1.5 클린룸·데이터 정책

- 코드는 처음부터 새로 쓴 Java 21 이다. **Mojang 바이너리/데이터/디컴파일 소스를 저장소에 넣지 않는다**(2.7 의 "금지"). 바닐라 데이터는 사용자가 가진 공식 서버 jar 에서 첫 실행 때 추출하고, 거기서 뽑은 **압축 파생 표만** 커밋한다. 바닐라 디컴파일 소스는 알고리즘을 정확히 이식하기 위한 **읽기 자료**일 뿐이다. 최초 설계서는 "Mojang/Bukkit 코드 미사용"이라고 적었고, 실제 작업은 "디컴파일 로직을 읽고 Java 로 다시 쓰기"(바닐라 코드 한 줄씩 이식 — 3.4)다. 이 둘의 관계(법적 해석 포함)는 사용자가 책임지는 영역이며, 새 세션이 바꾸지 않는다. `[S0 §2, R 3-1]`
- 예외로 사용자가 직접 git 에 올린 `tools/vanilla-data/minecraft_server.1.21.1.jar`·`server-1.21.1/**` 는 그대로 둔다(건드리지 않는다, 늘리지 않는다 — 3.4).
- 순정 서버를 오라클로 돌릴 때 `eula.txt` 에 `eula=true` 를 쓰는 것은 사용자가 직접 허락했다: "Eula는 내가 허용할게. 내가 승인한걸포 ㄱㄱ" `[T 2026-10-08 00:46]`.

### 2.2 스레딩·region 설계 결정

1. **북극성은 Folia 식 region 소유권.** 스레드 소유권을 데이터 모델에 처음부터 박는다. 새 객체·시스템을 만들 때 "이것은 어느 스레드 소유인가"를 먼저 답한다(디버그 빌드의 `assertOwns`/`Owned<T>` 가드가 위반 시 터진다). `[S0 §4.1~4.2]`
2. **동적 regionizer(= Folia 방식)로 확정** — 고정 격자 region 은 폐기. 계기: 블록 엔진을 바닐라와 비교하다 region 경계를 넘는 설치·이웃 갱신이 실패하는 문제가 나왔고, 사용자가 구조를 이렇게 정했다.
   > "결국은 동시에 많은 청크 로드 되더라도 렉을 최소화 하기위한 pumpkin, leaf 버킷 구조 처럼 해야함" `[T 2026-10-16 16:55, 선택지 응답]`
   > "잠깐 leaf가 아니라, 그 폴리아 버킷" `[T 2026-10-17 02:28]` ← **Leaf 가 아니라 Folia 로 정정**
   > "일단 그 리전 시스템 제대로 만들었는지 점검후에 다음 작업 ㄱㄱ" `[T 2026-10-17 04:36]` ← 점검 후 수정 커밋 `b7b4b521`
   내용(`DR`): 구역 = 2^shift×2^shift 청크(기본 16×16), region = 구역 집합, **서로 다른 region 의 구역은 체비셰프로 merge-radius(기본 1) 구역보다 멀다** → 블록 갱신·플레이어 시야가 region 을 넘지 않는다. 합치기·나누기는 **틱 사이에만**, 자료는 청크(위치) 단위로 옮김. 설정은 `region-threads`(기본 코어 수)·`region-section-shift`·`region-merge-radius`. 고정 격자 `region-size` 는 없앴다. 세부는 5.2.
3. **한 region 안은 한 스레드가 바닐라 순서 그대로.** 서로 떨어진 플레이어 무리만 병렬. 따라서 한 region 안의 결과는 바닐라와 같다. 서로 다른 region 의 **무작위 틱 난수**는 스레드가 나뉘면 바이트 단위로 같을 수 없고 통계적으로만 같다(알려진 의도적 차이). `[DR 목표, R 3-4c]`
4. **스케줄러 API 의 "모양"은 M0 에서 고정:** 모든 스케줄 연산은 비동기 반환(`CompletableFuture`)이고, region 1개일 때와 N개일 때 같은 시그니처를 쓴다. `GlobalScheduler`/`RegionScheduler`/`EntityScheduler` 3종을 `furnace-api` 에 1급으로 노출하는 것이 설계다. **현재 `EntityScheduler` 는 아직 없다**(`FurnaceScheduler` 주석: "추후 entity"). `[S0 §4.4, furnace-api FurnaceScheduler]`
5. **교차 region 연산의 방침:** 북극성은 메시지 패싱(타 region 에 작업 큐잉), 초기에는 전역 직렬화. 현재: 모든 월드 작업은 청크 키를 달고 소유 region 에서 돈다(`world.runAtChunk/runAt`), 큐에 넣은 뒤 그 region 이 합쳐지거나 나뉘면 지금 주인에게 다시 보낸다. `/fill` 은 영역이 한 region 에 로드돼 있어야 한다(Folia 방식). 폭발·엔티티 텔레포트처럼 경계를 넘는 원자 연산은 폭발 작업 때 확정한다. `[S0 §4.5, DR, R 3-4d]`
6. **region 스레드 밖에서 하는 일:** 월드 생성(gen 풀·ForkJoin), 디스크 IO(IO 풀), 청크 직렬화(워커 풀), 사전 로그인(가상 스레드), Mojang 세션 호출(비동기). 생성 중 `ProtoColumn` 은 region 에 편입되기 전이라 소유권 규약 밖이고, 완성되면 소유 region 으로 `execute` 해서 넣는다. `[2026-06-21 m4gd-feature-pipeline §3.3, S0 M2]`
7. **전역 틱 배리어:** 시간·날씨·게임룰·`/tick` 같은 전역 상태는 전역 스레드에서 돌리고 region 이 같은 값을 보게 한다. `[R 2-6, VD 2.5]`
8. **교차 region 가시성은 읽기 전용 불변 스냅샷**(M3c 설계). 이 방식은 고정 격자 시대 설계이므로 동적 regionizer 이후 코드가 어떻게 바뀌었는지 `Region.java` 로 확인하고 쓴다. `[2026-06-08 m3c]`
9. **스레드 수에 인위적 상한을 두지 않는다.** 직전 세션 컨테이너가 4코어였을 뿐 서버의 한계가 아니다. 생성 스레드·동시 진행 한도는 실행 PC 의 코어 수를 따른다(예외: 디스크 IO 스레드 기본 4). 사용자 질문: "최대코어수가 4임?" `[T 2026-10-10 14:29]`

### 2.3 플러그인 API 와 Paper/Bukkit 호환 어댑터

#### 2.3.1 결정 요약 (시간순)

1. "플러그인은 일단은 자체 api로 만들게 할건데. 추후에 애댑터(페이퍼 버킷 플러그인)으로 연결 가능하게 만들고 싶어" `[T 2026-10-08 23:59]` → 로드맵 확정 결정 2. 지금은 자체 API(`furnace-api`)만, **나중에 별도 모듈로 Paper/Bukkit 어댑터**.
2. Paper/Bukkit 플러그인은 "쓰는" 것이 아니라 **"지원"하는** 것이다.
   > "해당 버킷은 페이퍼 플러그인을 "지원" 하는거지 쓸것은 아니란 말이지? 어댑터를 만들어서 쓸수 있게 하는거지. 다시 생각해봐" `[T 2026-10-10 07:12]`
   → 이 말이 텍스트 라이브러리 결정을 뒤집었다. 직전 세션은 처음에 "Paper 플러그인이 전부 Adventure 를 쓰니 Adventure 를 API 에 쓰자"고 추천했으나, 사용자가 "Furnace 자체 API 의 성능이 먼저, 호환 비용은 어댑터가 진다"는 전제를 바로잡았다. 원칙: **"호환 비용은 어댑터가 지고, 자체 API는 성능과 우리 구조에 맞춘다."**
3. 사용자의 결정 지시: "자체 텍스트 타입 ㄱㄱ / 페이퍼 플러그인은 플러그인 폴더안에 bukkit-plugin 폴더 넣어서 거기에 들어간 플러그인으로 구별 ㄱㄱ / MiniMessage 같은 좋은 도구 목록을 뽑아서 우리도 자체 구현 / 쓰는 방법은 Adventure랑 비슷하게 짜도 되고. 좋은 기능 생각나면 추가해도 좋음. / 물어볼거 있으면 나한테 물어봐" `[T 2026-10-10 07:15]`. 이어진 선택지 응답 `[T 2026-10-10 07:17]`: 설정·디스크립터는 **"yaml 을 기본으로 하고. toml랑 json 도 지원되게"**, 마크업 문법은 **MiniMessage 호환**, 폴더 이름은 **`plugins/bukkit-plugins/`**(복수형).
4. 로드맵 5단계에 `furnace-compat-paper` 로 명시, 어댑터 대비 설계 원칙 9개를 `furnace-api` 가 처음부터 지키기로 확정(2.3.3).

#### 2.3.2 이름·폴더·형식 (그대로 지킬 것)

| 항목 | 값 |
|---|---|
| API 모듈 | `furnace-api` (패키지 `org.domi.furnace.api.*`, **외부 런타임 의존성 0**, 서버 내부 `org.domi.furnace.server.*` 를 노출하지 않음) |
| 어댑터 모듈 (아직 없음, 5단계) | **`furnace-compat-paper`** — Bukkit/Paper API 구현체를 `furnace-api` 위에 얹음 |
| 자체 플러그인 폴더/설명 파일 | `plugins/*.jar`, `furnace-plugin.yml`(기본) / `.yaml` / `.toml` / `.json` — 키는 Bukkit `plugin.yml` 과 같음(`name version main depend softdepend loadbefore api-version commands permissions …`) |
| Paper/Bukkit 플러그인 폴더 | **`plugins/bukkit-plugins/`** (`plugin.yml` jar). 지금 서버는 이 폴더의 jar 를 **세기만 하고** "호환 어댑터가 아직 없다"고 경고한다. `plugins/` 바로 아래에 `plugin.yml` jar 가 있으면 오류로 안내 |
| 플러그인 설정 형식 | YAML 기본 + TOML·JSON (주석 보존, 기본값 복사) |
| 텍스트 | `org.domi.furnace.api.text.Text`(불변, Adventure 와 구조 1:1) + MiniMessage 호환 마크업 `TextMarkup` — **Adventure 에 의존하지 않는다** |

#### 2.3.3 어댑터 대비 설계 원칙 9개와 현재 상태 `[R 어댑터 대비 설계 원칙]`

5단계에서 새로 만들 것이 **"API 이름 매핑 · 이벤트 변환 · 스레드 모델 에뮬레이션"뿐**이 되도록, 1~4단계의 `furnace-api` 는 아래를 지킨다. 새 API 를 추가할 때마다 이 표를 대조한다.

| # | 원칙 | 지금 상태 / 지킬 것 |
|---|---|---|
| 1 | **이벤트 모델을 Bukkit 호환 의미로.** 우선순위 LOWEST~MONITOR, `ignoreCancelled`, 취소 가능, 동기/비동기 구분, 리플렉션 없는 람다 등록 + 어노테이션(`@Subscribe`) 등록, 이름·필드는 Bukkit 과 1:1 변환이 쉽게(`PlayerJoinEvent`, `BlockBreakEvent` …) | 구현됨(`EventBus`, `EventPriority`, `Cancellable`, 상위 타입 구독, `AsyncChatEvent`·`AsyncPlayerPreLoginEvent`). **새 이벤트는 Bukkit 의 같은 이름·필드를 먼저 떠올려 만든다.** 이벤트 객체에 서버 내부 타입을 싣지 않는다 |
| 2 | **스레드 모델 명시.** Bukkit 플러그인은 "메인 스레드 한 개"를 가정 → 어댑터가 쓸 **전역 직렬 실행기(global region)** 를 API 에 일급으로. 모든 API 호출에 "어느 스레드에서 호출 가능한가"를 문서화 | `GlobalScheduler`·`region(RegionPos)`·`Owned`/`ThreadOwner` 있음. **API 메서드의 Javadoc 에 스레드 규약을 적는다.** `EntityScheduler` 는 아직 없음(로드맵은 "이미 가졌다"고 적었으나 실제로는 global+region 뿐 — 5단계 전에 추가) |
| 3 | **레지스트리 이름 기반.** Material·BlockData·EntityType·Enchantment 는 바닐라 레지스트리 키(`minecraft:oak_log`)로 식별, 어댑터가 Bukkit enum 으로 매핑. **자체 정수 id 를 API 에 노출하지 않는다** | 지킴(`BlockBreakEvent.blockType` 은 문자열 키, `Location`·`BlockPos`). 새 API 에 숫자 id·내부 enum 을 노출하지 않는다 |
| 4 | **텍스트는 자체 `Text`** (2026-10-10 결정 변경 — 처음 원칙 4번은 "Adventure 통일"이었음). 어댑터가 경계에서 Adventure `Component` ↔ `Text` 변환 | 구현·결정 완료. 어댑터의 변환 정확성은 **같은 입력을 Adventure 직렬화기와 우리 인코더로 JSON 으로 만들어 바이트 단위로 대조하는 테스트**로 보장하기로 함 |
| 5 | **명령·권한·완성.** Bukkit `CommandMap`/`Permission`(기본값 op/true/false, 상속)과 변환 가능한 구조. Brigadier 노드와 레거시 문자열 명령 공존 | 구현됨(`CommandRegistry`, `Permission`·`PermissionDefault`·`PermissionAttachment`; 미등록 노드는 OP, 이름 충돌은 `플러그인:이름`) |
| 6 | **플러그인 로더.** `plugin.yml` 호환 필드를 읽는 디스크립터 + jar 별 클래스로더 격리를 처음부터 | 구현됨(부모 → 자기 jar → 다른 플러그인, depend/softdepend/loadbefore 위상 정렬, 끌 때 작업 자동 취소) |
| 7 | **저장 데이터.** `PersistentDataContainer`(NBT 태그)를 엔티티·아이템·청크·월드에 일관 제공 | **미구현**(소스에 없음). 엔티티·아이템·청크 저장 NBT 를 만들 때 플러그인용 데이터 영역을 같이 설계한다 |
| 8 | **스케줄러.** 틱 지연/반복 + 비동기 + 지역/엔티티별(Folia 식)을 가진 `FurnaceScheduler` 확장 | `runLater`/`runAtFixedRate`/`execute`/`submit` 있음(실행이 겹치지 않음). 엔티티별은 원칙 2 와 같이 추가 |
| 9 | 서버 내부 클래스(`org.domi.furnace.server.*`)를 API 가 노출하지 않는다 | 지킴 |

#### 2.3.4 어댑터의 범위와 "지금 하면 안 되는 것"

- **범위 안:** `plugin.yml` 기반 Bukkit/Paper 플러그인 jar 로딩, Bukkit/Paper **공개 API** 구현(이름 매핑·이벤트 변환·스레드 모델 에뮬레이션). 모듈 `furnace-compat-paper`, 단계 5(마지막). `[R 5단계]`
- **범위 밖(명시):** **NMS/CraftBukkit 내부를 직접 건드리는 플러그인.** 이 jar 는 지원한다고 약속하지 않는다. `[R 5단계]`
- **지금 피할 것 (어댑터가 불가능해지거나 비싸지는 설계):**
  1. Adventure(또는 어떤 외부 텍스트 라이브러리)에 `furnace-api` 가 의존 — 금지. 직전 세션이 Adventure 의존성을 API 모듈에 추가하던 중 사용자가 그 도구 호출을 중단시키고 물었다(`[T 2026-10-10 07:08]` "잠깐. 왜 Adventure 을 쓰는겅?").
  2. API 에 숫자 id·내부 enum·`server.*` 타입 노출.
  3. "메인 스레드는 하나뿐" 또는 "이 API 는 어느 스레드에서 불러도 된다"는 **암묵적 가정**. 호출 가능한 스레드를 문서화하지 않은 메서드를 추가하지 않는다.
  4. Bukkit 과 의미가 다른 이벤트 우선순위·취소 규칙, 동기/비동기 구분 누락.
  5. 플러그인 클래스로더를 서버 클래스와 섞기(격리 유지), `plugins/` 와 `plugins/bukkit-plugins/` 구분 흐리기.
  6. 플러그인 전용 데이터(PDC)를 나중에 붙일 수 있게 NBT 저장 경로를 닫아 버리기.
- 어댑터를 위해 자체 API 를 **느리게 만들지 않는다.** 호환용 변환은 어댑터 경계에서만 비용을 낸다(예: Paper 플러그인이 보낸 Adventure `Component` 만 변환 할당이 생김).
- 자체 플러그인과 Paper 플러그인의 이름 충돌을 막기 위한 폴더 분리, 서버 명령 이름 충돌 규칙(`플러그인:이름`)은 이미 Paper 와 같은 동작으로 맞췄다.

#### 2.3.5 텍스트 API 설계 (왜 자체 타입인가)

- 서버 내부에 이미 바닐라 텍스트를 완전히 표현하는 `Text` 가 있어, 이를 `furnace-api` 로 올리면 **서버와 플러그인이 같은 객체**를 쓰고 변환이 없다. `[T 2026-10-10 07:12 직후 답변]`
- 불변 `Text` 가 **자기 인코딩 바이트를 한 번만 만들어 품고** 방송 시 재사용한다(성능이 Adventure 선택과 무관하게 직렬화 방식에서 갈린다는 판단). JSON(바닐라 필드 순서·Gson 이스케이프)과 플레이 단계 NBT(문자열 접기, HashMap 순서, 이종 리스트 감싸기 …)가 순정 1.21.1 인코더와 바이트 단위로 같다(골든 파일 테스트).
- Adventure 가 Paper 플러그인용으로 필요하면 **어댑터 모듈이** Adventure 를 직접 갖고 경계에서 변환한다. Paper 가 Adventure 버전을 올려도 영향은 어댑터에만 간다.
- MiniMessage 호환 문법을 쓰는 이유: 기존 플러그인 설정 메시지를 그대로 쓸 수 있고 관리자가 새로 배울 게 없다. 우리만의 확장은 추가해도 됨("좋은 기능 생각나면 추가해도 좋음"). 그라디언트·무지개 계산식은 기억한 Adventure 식이라 **원본 대조 못 한 상태**(네트워크 막힘)임을 잊지 않는다. `[R 2-8c, TXT]`

### 2.4 성능 전략과 측정

- **병행 원칙:** 성능 작업은 1~3단계 사이사이에 **출력 불변을 확인하며** 병행한다(별도 "성능 단계"로 미루지 않음). 4단계의 성능 항목: 청크 생성 병목, 네트워크 flush 합치기, 적응형 청크 전송, 리전 파일. `[R 4단계]`
- **측정 수치(기록된 것):** 청크 생성 657→231 ms(단일 스레드, 10-08), 지형 40→21.5 ms/청크(10-10), 월드 소스 초기화 4.0→1.1 s, 비행 봇(4코어, 시야 10) 대각선 누락 104→24/441·2배속 p50 283 ms, 2배속 5분 비행에서 저하 없음(평균 누락 24.6/441, p50 338 ms). 직전 세션 요약에 "초당 약 35청크(4코어)" 도 있었다. 4코어 밖에서는 측정하지 않았다. `[R 청크 생성·전송, RW P3, T 2026-10-10 14:29 응답]`
- **성능 문제는 사용자가 클라이언트로 직접 체험하고 보고**한다("크레이티브로 날아가는데 플레이어 시야에서 청크로딩속도가 못따라가네?" `[T 2026-10-10 13:41]`, "관전 상태로 1분 날라가면 그때쯤 청크로딩 속도가 느려지는데" `[T 2026-10-11 03:52]`). 그래서 비행 봇 벤치(`-Dfurnace.debugEvict=true` 등)로 재현하고 수치를 보고한다. 이 환경에는 실제 클라이언트가 없다.
- **보류한 최적화 — 밀도 함수 바이트코드 컴파일러(C2ME DFC 방식):** 사용자가 "노이즈 컴파일러 ㄱㄱ"라 했다가 장단점 설명을 듣고 "그냥 다음 작업가자. 하다가 최적화할 요소 있으면 말하고"로 **보류**했다 `[T 2026-10-10 15:27~16:29]`. 이유: 측정 상한 10~15%(DF 노드 자체가 지형의 ~30%, 순수 노이즈 계산 ~32%는 줄지 않음), ASM 의존성 필요, 부동소수 결합 순서를 바닐라와 비트 단위로 맞춰야 하는 위험, 경로 이중 유지. 대형 서버 pregen 처럼 지형 처리량이 정말 중요해질 때(4단계) 재검토. `[R 청크 생성·전송 "보류"]`
- **채택하지 않는/조심할 최적화:** 결과를 바꾸는 근사(셀 보간 같은 정확한 최적화는 OK, 다른 결과를 내는 휴리스틱은 NO), 외부 의존성을 늘리는 최적화(상기 ASM, 사용자 승인 없이 추가하지 않는다).
- **청크 관리 검토 결과(사용자 요청 확인 사항, 2026-10-10):** 지금은 시야 구독 기반 로드 + 구독 0 → 100틱 뒤 저장·언로드 + 빈 region 정리뿐이다. 바닐라 티켓 체계(로드 레벨·simulation-distance·엔티티/블록 틱 구역·스폰 청크·forceload·포털/텔레포트 티켓)는 없다 → 블록·엔티티 틱이 들어온 지금(8.2 의 6번) 만든다. 언로드 검사가 매 틱 모든 청크를 훑는 것은 이벤트 기반으로 바꿀 후보. `[R 청크 관리 검토, T 2026-10-10 16:29]`
- **설정 파일 규칙:** 청크 거리 같은 바닐라와 겹치는 키는 **`server.properties` 값이 기준**이다(`furnace.properties` 의 같은 키는 무시하고 경고). `furnace.properties` 는 Furnace 전용 세부값만: `entity-view-distance`(기본 8), `spawn-chunk-radius`(2), `chunks-per-tick`(16), `chunk-eviction-grace-ticks`(100), `region-threads`·`region-section-shift`·`region-merge-radius`, `io-threads`, `autosave-ticks`. 동시 생성 청크 수 상한(코어×2, 최소 16)은 코드 고정. `[T 2026-10-11 03:52 질문의 답변, Config.java]`

### 2.5 검증 방법 (영역별)과 정답 우선순위

**정답의 위계 (`[T 2026-10-08 00:42~00:46]` "worldgen-oracle , paper bukkit, spigot,중이 뭐가 확실해?" 이후 정리):**
1. **진짜 바닐라 1.21.1 서버를 직접 돌린 결과**가 최종 권위다(월드·패킷 캡처·명령 출력). fixture 와 실서버 월드가 어긋나면 실서버가 이긴다. `[FP context-notes 2026-07-02 "fixture 와 실제 바닐라 월드 인게임 대조가 최종 권위"]`
2. 그다음 **Mojang 공식 서버 jar 의 데이터·바이트코드**(레지스트리·태그·레시피·와이어 형식은 `javap`/디컴파일로 직접 확인, 기억에 의존하지 않음).
3. `worldgen-oracle`(Paper dev bundle)은 **데이터 추출용으로만** 쓴다: 데이터는 바닐라와 같지만 동작은 Paper 패치로 어긋날 수 있고, 과거 fixture 가 태그 미바인딩으로 오염된 전례가 있다. Paper 저장소가 막힌 환경에서는 `skipOracle` 로 제외.
4. Paper/Spigot/Bukkit 의 API·소스는 **동작 참고용**이지 데이터 출처가 아니다. 이 샌드박스는 Mojang/Paper/Spigot 모두 막혀 있어(프록시 403) 필요한 jar 는 사용자가 올려 준다.

| 영역 | 검증 방법 | 도구/위치 |
|---|---|---|
| 월드젠 | 순정 서버를 직접 돌려 만든 `.mca` 와 블록 단위 대조. **순정 2회 이상**으로 노이즈 바닥을 재서 hard 불일치(순정 실행들이 모두 같은 값인데 Furnace 만 다른 곳)만 센다. 중심 청크를 **한 번** 생성하며 3×3 origin 추적(`SegBox`), 스폰 영역 오염을 피하려 **링 청크(max(\|x\|,\|z\|)=12)** 로 비교. 지형 단독은 모든 바이옴 feature 를 비운 데이터팩 월드로 | `tools/parity-audit/*`(`RunVanilla`·`Harness`·`SegBox`·`StructCmp`), `WM`, `VA` |
| 월드젠 feature 단위 | 순정 코드 오라클(실제 `WorldGenRegion` 9청크, writeRadius 1)로 fixture 를 만들고 `byte-exact` 대조. **fixture 박제 전에 같은 시드 순정 `.mca` 청크 1개와 오라클 덤프를 대조(자가 검증 필수)**. 난수 호출 시퀀스(`rngTrace`) diff 로 분기 지점을 특정 | `worldgen-oracle`, `FeaturePipelineParityTest` |
| 프로토콜/패킷 | 순정 jar 의 `StreamCodec` 으로 Furnace 패킷을 디코드해 형식 검증, 같은 봇을 순정/Furnace 에 붙여 시퀀스 차분, 순정 캡처와 바이트/SHA-256 대조(레시피·접속 순서·발전 과제 첫 패킷 등). 와이어 형식은 `javap` 로 확인 | `TestClient`, `PacketCapture`, `*VanillaTest` |
| 암호화·인증·서명 채팅 | 순정 `net.minecraft` 서명 검증기를 오라클로 한 단위 테스트. Mojang 인증 서버는 이 환경에서 막혀 **가짜 세션 서버**로 전 과정 시험, 실서버 확인은 사용자 환경에서 | `R 검증 방법` |
| 블록/아이템 동작 | **블록 오라클(차분 비교):** 같은 봇 시나리오를 순정과 Furnace 에 돌려 저장된 블록 상태를 칸 단위 비교. 무작위 틱은 바이트 단위로 같지 않아 영향받는 시나리오는 `randomTickSpeed 0` | `tools/block-oracle/*`, 4.3 |
| 데이터 주도 시스템(전리품·레시피·컴포넌트·발전 과제) | **정답지 골든 테스트:** 순정 서버/코덱으로 뽑은 결과와 전부 비교(전리품 상자 280건·블록 1,098건·엔티티 252건 일치, 컴포넌트 60여 예시, 레시피 패킷 SHA-256) | `*VanillaTest` |
| 엔티티 | 아직 오라클 없음(블록만 비교). 골든 테스트/패킷 단위 대조로 보완하고, **엔티티 오라클 도구는 TODO** | 7.8 |
| 메뉴(4-2) | 블록 오라클로 못 봄 → 패킷 단위 대조(`container_set_slot`/`container_set_content` 시퀀스 캡처) 또는 순정 클래스 로직 골든 | 8.1 |
| 성능 | 출력 불변 + 벤치(비행 봇). 최적화는 byte-exact 가 깨지지 않을 때만 | 2.4 |
| 회귀 | `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test` 전체 통과 후에만 커밋 | 3.2 |

**방법론 원칙:**
- **측정이 틀리면 "고칠 수 없는 차이"를 버그로 착각한다.** 비교 전에 오염 요인(스폰 영역 선행 쓰기, origin 개별 생성, 순정의 더 먼 origin 선행 장식)을 제거한다. `[WM 핵심 교훈]`
- **새 오라클/fixture 를 믿기 전에 검증한다.** oracle 에서 태그 의존 로직(`canSurvive` 류)이 이상하면 태그 바인딩부터 의심(과거 fluid/leaves 태그 미바인딩으로 fixture 전체가 오염). `[FP context-notes 2026-07-02]`
- **보고된 결함은 먼저 진짜인지 검증한다.** 사용자는 반복해서 이렇게 시켰다: "그 문제들이 실제로 있는 문제들인지 객관적으로 다시 검토 해봐." `[T 2026-10-06 15:07]`, "확인된 문제들 간단하게 진짜인지 검증하고. 진짜이면 어떻게 고칠지 계획" `[T 2026-10-07 11:50]`, "진짜인지 간단하게 검증후 ㄱㄱ" `[T 2026-10-07 23:50]`. 직전 세션도 한 번 틀린 수치("블록 파괴 유실 30%")를 보고했다가 NIO 소켓 재측정으로 정정했다 — **측정 도구 자체의 신뢰성도 확인한다.**
- **목록의 완전성도 검증한다.** "저게 끝인지 다시 검토." `[T 2026-10-08 17:12]`, "추가로 빠진거 없는지 확인" `[T 2026-10-07 13:45]`. 새 목록(미구현 인벤토리 등)을 만들면 한 번 더 독립적으로 빠진 것을 찾는다.
- **한계의 정직한 표시:** 실제 클라이언트로 확인하지 못한 것(태그·상태 패킷의 클라 증상 등)은 "클라 확인 필요"로 적는다.

### 2.6 범위·순서 결정 (로드맵 요약과 빠져 있던 4·5단계)

**확정된 결정 3개** `[R 확정된 결정 2026-10-09]`
1. **온라인 모드 사용:** RSA 핸드셰이크 + AES/CFB8 암호화 + Mojang 세션 인증(`hasJoined`) + 프로필 속성(스킨) + **서명된 채팅**(채팅 세션 공개키, 서명 검증, 메시지 체인, last-seen 확인응답, `enforce-secure-profile`). 사용자 말: "온라인, 암호화, 서명된 채팅 모두 쓸거야." `[T 2026-10-08 23:59]` 오프라인 모드는 `online-mode=false` 로 유지(테스트·LAN). Velocity modern forwarding 도 포함.
2. **플러그인:** 자체 API 만 지금, Paper/Bukkit 어댑터는 5단계(2.3).
3. **기준 동작은 계속 바닐라 1.21.1 → Paper.** 월드젠은 순정 서버와 블록 단위로 대조.

**제작 순서(큰 줄기)**: 월드젠 마무리 → 서버 뼈대 → 게임플레이 코어 → Paper 동작·운영·성능 → 플러그인 호환 어댑터. "이 순서는 앞 단계가 뒤 단계의 전제이기 때문이다." 세부 상태는 6.1 표.

| 단계 | 내용 | 비고 |
|---|---|---|
| 1 | 월드젠 마무리: 구조물 전종(jigsaw 포함)·네더·엔드·후처리·sculk 근사·청크 로딩 속도 재검토 | ✅ (후속 목록은 6.1) |
| 2 | 서버 뼈대: 설정·파일 → `level.dat`/월드 형식 → 온라인 모드 → 명령 프레임워크 → 서명 채팅 → 시간·날씨·난이도·월드 보더·게임룰 → 게임모드·체력·허기·피해·사망·리스폰·경험치 → **플러그인 API v0** | ✅. 플러그인 API 는 "명령 등록·이벤트 버스·권한·스케줄러·설정"이 2~5단계와 같은 지점이라 같이 노출한다는 결정 |
| 3 | 게임플레이 코어(작은 세로 조각으로). **데이터 주도 기반부터:** 데이터 팩 로더 → 전리품 표(1,178)·레시피(1,290)·발전 과제(1,399) → 아이템 컴포넌트 → 블록 상태 설치/파괴/이웃 업데이트·틱·유체 → 아이템 엔티티·컨테이너 → 빛 엔진 → 엔티티(몹·동물·투사체·전투) → 차원·포털 → 진행 시스템(발전·통계·점수판·보스바·포션/속성). 월드젠 시점 몹 스폰·스포너·구조물 상자 내용물은 전리품 표 이후에 연결 | 진행 중 (4-2 메뉴가 다음) |
| **4** | **Paper 동작·운영·성능:** Paper 설정 파일 키(`paper-*.yml`, `spigot.yml`, `bukkit.yml`, `commands.yml`, `permissions.yml`)와 **기본 동작 차이 목록을 먼저 작성한 뒤 구현**; `/tps /mspt /version /plugins /timings /paper`; 안티 엑스레이; 몹 스폰 한도·엔티티 활성 범위·청크 로딩 속도 설정; RCON/Query/메트릭; 크래시 리포트·워치독; CI·배포(Docker/fat jar, 시작 스크립트, JVM 플래그). 성능 작업(청크 생성 병목, 네트워크 flush 합치기, 적응형 청크 전송, 리전 파일)은 1~3단계 사이사이에 병행 | 미착수 `[R 4단계, RW A-3, RW 16]` |
| **5** | **플러그인 호환 어댑터 — 별도 모듈 `furnace-compat-paper`:** `furnace-api` 위에 Bukkit/Paper API 구현체를 얹어 `plugin.yml` jar 를 `plugins/bukkit-plugins/` 에서 로드. 새로 만들 것은 API 이름 매핑·이벤트 변환·스레드 모델 에뮬레이션뿐. NMS/CraftBukkit 내부 접근 플러그인은 범위 밖 | 모듈 없음 `[R 5단계]` |

**범위 메모**
- 대상 버전은 **1.21.1(프로토콜 767) 단일**. 멀티 프로토콜은 비목표(YAGNI). `[S0]`
- 지금 로드맵은 "채팅·명령·암호화·게임플레이 없음" 같은 옛 문장이 남아 있다(이 문서가 현재 상태).
- 다음 순서(메뉴 → 명령 → 전투/효과/속성 → 폭발 → 빛 → 청크 티켓 → 몹 → 차원)는 사용자가 방향을 "다음은 메뉴(4-2)"로 정했고, 나머지는 직전 세션이 인벤토리 기반으로 제안한 것이다(8.2). 사용자가 "다음 작업" 같은 짧은 승인으로 이어 가는 패턴이므로, 큰 순서 변경은 먼저 제안한다.
- **원격 PR #1 선별 포팅(2026-10-07 결정):** 원격 `origin/main` 의 PR #1 은 공통조상이 2026-06-08 이라 통머지 대신 `git merge -s ours origin/main` 으로 이력만 합치고 필요한 수정 6건(청크 전달 레이스·델타 `short` 오버플로·입력 검증·ConfigLoader 검증·`PlayerInfoRemove`·이동 패킷 병합)만 로컬 구조에 포팅했다. `docs/remote-pr1-port/*`. (유령 엔티티·유휴 청크 퇴출·heightmap 은 로컬이 이미 해결해서 포팅 안 함.)
- 사용자는 가끔 `main` 에 직접 올린다(예: 서버 jar 업로드). "실수로 main 브랜치에 업로드 했으니 알아서 잘 가져가" `[T 2026-10-09 02:02]` → 작업 브랜치에 병합해 푸시했다(커밋 `ed46c6b5 tools`).

#### 결정 변경 이력 (옛 문서가 지금과 다른 이유)

| 옛 결정 | 새 결정 | 날짜·출처 |
|---|---|---|
| 비목표: 온라인 모드(Mojang 인증) | 사용(암호화·서명 채팅 포함) | 2026-10-08 `[T]`, `R 확정 1` |
| 비목표: "바닐라 100% 기능 패리티" | 목표: 관측 동일(난수 순서까지) | 2026-10-07~08 `[T]`, `R` |
| 고정 격자 region(M3b, 동적 region 은 후속 최적화) | 동적 regionizer(Folia 방식) | 2026-10-17 `[T]`, `DR` |
| 어댑터 원칙 4번: 텍스트는 Adventure 로 통일 | 자체 `Text`, 어댑터가 경계 변환 | 2026-10-10 `[T]`, `R 원칙 4` |
| "플러그인 호환 범위"가 미결(A-1) | 자체 API 먼저 + 5단계 Paper 어댑터(NMS 제외) | 2026-10-08 23:59 `[T]`, `RW A-1 → R` |
| 장식을 청크마다 독립 생성(9× 중복) | 바닐라처럼 공유 월드에서 청크마다 한 번 | 2026-10-10 `R` |
| M2 에서 Anvil 읽기 | M4(쓰기와 한 단위)로 이동 | `2026-06-07 m2 §2` |
| `onlinePlayers` 거리 기반 탭 목록 | 접속자 전원, 거리 무관(바닐라/Paper) | 2026-10-07 `[T]` |

### 2.7 사용자 선호·지시·금지사항 (원문과 일시)

**말투·보고**
- 사용자에게는 **항상 한국어**: "한국어로 해. 나한테 전달할떄는." `[T 2026-10-08 03:12]`, "한국어로" `[10-10 12:53]`, "한국어로 마무리" `[10-11 03:50]`. 커밋 메시지·코드 주석도 한국어.
- 작업 전 설명 요구: "다음 작업이 뭔지 설명 먼저 ㄱㄱ" `[10-08 13:45]`. 큰 변경은 **무엇을·왜·어떻게**를 짧게 설명하고 승인을 받는다. 승인은 보통 "ㄱㄱ"/"이어서 ㄱㄱ"/"이제 다음 작업"/"시작해"로 온다.
- **사용자가 "내가 지시하기전에는 시작하지말고"(2026-10-09 01:44)라고 한 상태에서는 지시("시작해") 전까지 작업을 시작하지 않는다.** "다음 작업은 내가 승인하면 하고"(10-10 14:29) 도 같다. 구조적·범위가 큰 변경(예: Adventure 도입, region 구조)은 먼저 선택지를 묻는다 — "물어볼거 있으면 나한테 물어봐"(10-10 07:15).
- 반대로 "알아서 진행 ㄱㄱ"(10-06 15:17)·"이어서 ㄱㄱ" 처럼 **계획 승인 후에는 묻지 않고 끝까지** 진행한다. 계획은 "수정 계획 한번만 간단하게 검토하고 진행해"(10-07 11:56)처럼 **한 번만 짧게 자체 검토한 뒤** 진행.
- 보고는 "무엇을 바닐라와 맞췄고 무엇이 남았는지" 구체적으로, 확인 못 한 것은 못 했다고(2.1.2 의 정직성 가드).

**위임 (모델 계층)**
- "일단 중요한 작업은 opus5.5가 하고 조금 덜 중요한 작업은 sonnet5.5이 하고. 단순 작업은 haiku5.5 한테 시켜줘." `[T 2026-10-09 02:04]`, 그 전에 "이제 부터 간단한 작업은 haiku5.5에게 시켜줘." `[10-08 02:01]`, "일단 하위 모델 시켜서 아직 안된 목록들 뽑아보라고 해봐." `[10-18 03:34]`, 인수인계 문서도 "하위 모델(sonnet 혹은 haiku)에게 시켜서" `[10-18 05:10]`. → **판단·정확도가 걸린 설계·이식·최종 판단은 가장 강한 모델이 직접, 패턴이 정해진 구현·검토·문서 정리는 중간 모델, 반복·탐색·실행 요약은 가장 가벼운 모델.** 하위 모델 결과는 직접 확인한 뒤 반영하고 순정 대조로 다시 검증한다. (모델 이름은 당시 기준; 새 세션이 다른 모델이어도 "무거운 일은 직접, 가벼운 일은 가벼운 모델" 취지를 따른다.)
- **단, 바닐라 포팅은 직접 한다.** 2026-10-17 에 블록 이식 서브에이전트 2개를 사용자가 중단시켰고, 그 뒤로 포팅 서브에이전트를 다시 띄우지 않았다. 서브에이전트는 조사·인벤토리·목록·로그 요약용. (3.4)
- 출력은 길게 읽지 말고 요약만 시킨다(로그 22,000줄을 통째로 읽지 말고 `grep`).

**Git·푸시**
- 작업 브랜치는 `claude/work-continuation-u597cw`. PR 은 사용자가 시킬 때만: "PR 만들어줘" `[10-06 15:43]`, PR 감시/구독 제안은 "그거는 ㄴㄴ" `[10-06 15:49]`, "PR 머지해줘 main 으로" `[10-06 15:51]`.
- "그전에 커밋 한번 해줘" → "push 는 안했잖아" `[10-17 14:41~14:43]`: **커밋과 푸시는 한 묶음이고, 푸시 결과(`Everything up-to-date` 포함)를 그대로 보여 준다.** 푸시 출력을 숨기면(`-q`) 불만이 나온다.
- 커밋은 테스트 통과 후에만(3.2). 이 규칙은 2026-10-07 에 `&&` 로 이은 명령에서 실패한 전체 테스트가 푸시를 막지 못해 5개 실패 상태로 푸시됐던 사고 이후 굳었다 — 테스트와 커밋·푸시를 한 줄 `&&` 에 넣지 않고 결과를 눈으로 확인한다.

**환경·자료**
- 바닐라 jar 는 사용자가 올려 준다(Mojang 호스트가 막혀 있음): 분할 zip `server.z01~z05`(끝 조각 없음)를 올렸다가, 구조물 NBT 가 필요해 `tools/vanilla-data/server-1.21.1` 에 통째로 올림 `[10-08 00:44~00:46, 10-09 02:02]`. 자료가 더 필요하면 **어디에 올릴지 먼저 알려 달라고** 요청하고 지시를 기다린다(`.gitignore` 된 폴더·경로 안내, jar 커밋 금지).
- 프록시 우회 시도 금지, 네트워크가 필요하면 `read_documentation`(environment.network).
- 사용량 한도가 걸렸다 풀리면 자동 메시지가 온다("작업 중에 사용량 한도에 도달했지만 지금은 재설정되었습니다. 중단했던 부분부터 계속 진행해 주세요.") — **그 자리에서 이어 한다.**
- 세션이 길어져 캐시가 쌓이면 사용자가 새 세션으로 옮긴다(이 인수인계의 계기). 새 세션도 필요한 정보를 문서에 계속 남긴다.

**금지/주의 목록 (한곳에)**
1. Mojang 바닐라 데이터·디컴파일 소스·`generated/`·jar 를 새로 커밋하지 않는다(이미 사용자가 올려 둔 것은 건드리지 않는다).
2. Mojang/papermc 호스트 우회 금지.
3. 포팅 서브에이전트 금지(직접 포팅).
4. 테스트 안 돈 코드 커밋 금지, 푸시 출력 숨기기 금지, PR 은 요청 시에만.
5. 커밋/코드에 모델 ID 를 넣지 않는다.
6. `furnace-api` 에 외부 의존성 추가 금지(Adventure 포함).
7. 사용자가 "시작하지 말라"고 한 동안 작업 시작 금지.
8. 근사를 일치로 보고하기 금지.

### 2.8 문서 간 불일치와 열린 질문

| 불일치/열린 점 | 현재 해석 |
|---|---|
| `S0` 의 비목표(온라인 모드·100% 패리티)와 "Mojang/Bukkit 코드 미사용" vs 사용자 최신 지시(온라인 모드 사용, "원본과 완전히 같아야해", 바닐라 로직 그대로 이식) | 2.6 의 변경 이력을 따른다. 코드 미사용은 "저장소에 Mojang/Bukkit 소스·데이터를 넣지 않는다"로 해석 |
| `R` 어댑터 원칙 8 은 `FurnaceScheduler` 가 "지역/엔티티별(Folia식)을 이미 가졌다"고 하나 코드는 global+region 뿐 | 엔티티 스케줄러는 미구현(2.3.3 #2·#8) |
| `R` 어댑터 원칙 7(`PersistentDataContainer`)은 코드에 없음 | 미구현, 엔티티·아이템·청크 NBT 작업 때 설계 |
| `R` "지금 상태" 절의 "채팅·명령·암호화·게임플레이 없음" | 옛 문장. 현재는 6장이 정본 |
| 이전 이 문서(5.2)의 "기본 구성에서 월드 전체가 한 region 이면 바닐라와 같다" | 동적 regionizer 이전(`region-size=0` 단계) 문장이었다. 지금은 플레이어들이 가까워 한 region 이 될 때 그 region 안이 바닐라와 같다 |
| 모델 위임 선호(중요한 일은 Opus, 덜 중요한 일은 Sonnet, 단순 작업은 Haiku)와 "포팅은 직접" | 포팅은 직접, 그 밖의 가벼운 일만 위임으로 정리(2.7) |
| 사용자가 직접 `main` 에 올린 Mojang jar/클래스 vs "Mojang 데이터는 커밋하지 않는다" | 사용자 소유 결정이라 그대로 두고 확대하지 않는다(3.4) |
| 열린 질문: 저장소 `README.md` 부재 — 직전 세션이 "중간 점검 소개글"을 대화로만 작성했고(2026-10-11) 문서화는 하지 않았다 | 사용자가 요청하면 2.1 의 한 줄 정의와 6.1 표로 작성 |
| 열린 질문: 4단계 Paper 기본 동작 차이 목록은 아직 없음 | 4단계 시작 시 먼저 작성 |
| 열린 질문: 교차 region 원자 연산(폭발·피스톤 경계)의 최종 방식 | 폭발 작업 때 확정 |

---

## 3. 반드시 지킬 규칙

### 3.1 브랜치·푸시
- 작업 브랜치는 **`claude/work-continuation-u597cw` 하나만**. PR 은 사용자가 요청할 때만.
- 푸시: `git push -u origin claude/work-continuation-u597cw` — **푸시 출력을 사용자에게 그대로 보여 준다.** (`-q` 로 출력을 숨겼다가 사용자가 불만을 표한 적이 있다.)
- 커밋/푸시는 사용자가 시켰거나 작업 완료 흐름에서만. 이 인수인계 문서 자체도 부모 에이전트가 커밋한다.

### 3.2 테스트 우선 규칙 (커밋 전 필수)
- **모든 커밋 전에** 다음이 통과해야 한다:
  ```bash
  ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test
  ```
- `ORG_GRADLE_PROJECT_skipOracle=true` 를 빼면 `:worldgen-oracle` 프로젝트 설정 단계에서 Paper 저장소 접속이 필요해 실패한다. **항상 붙인다.**
- 전체 스위트는 약 **4분**. 백그라운드로 돌리고 로그 파일에 대해 until-loop 로 기다린다(포그라운드 sleep 은 막혀 있음). 예:
  ```bash
  (ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test > /tmp/test.log 2>&1; echo "EXIT=$?" >> /tmp/test.log) &
  # 완료 대기: until grep -q '^EXIT=' /tmp/test.log; do sleep 5; done; tail -30 /tmp/test.log
  ```
- 일부 테스트만: `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test --tests '*ItemEntityTest*'`
- Stop-hook 이 "There are uncommitted changes… commit and push" 라고 해도, **테스트를 먼저 끝낸다.** 테스트 안 돈 코드를 커밋하지 않는다.
- gradle 은 동시에 두 개 돌리지 않는다(블록 오라클도 gradle 을 부르므로 테스트와 동시 실행 금지).

### 3.3 커밋 규칙
- 커밋 메시지는 **한국어**. 제목 스타일 예: `4-1b 엔티티 틀 + 아이템 엔티티: 물리·추적·줍기·저장`
- 트레일러(이 세션에서 쓴 정확한 문구):
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01C51tC8178cHA97RMDgfg7e
  ```
  **새 세션은 자기 system reminder 가 주는 Claude-Session URL(그리고 모델 표기 줄)을 쓴다.** 위 값은 직전 세션 것이다. 커밋/코드에 **모델 ID 를 넣지 않는다.**
- 코드 주석은 한국어, 간결하게, 기존 파일 스타일(파일 머리 주석 1~3줄 + 필요한 곳에 "바닐라 X.y 의 …" 식 짧은 설명)을 따른다.

### 3.4 작업 방식
- **포팅은 직접 한다.** 포팅 서브에이전트를 다시 띄우지 않는다(2026-10-17 사용자가 블록 이식 서브에이전트 2개를 중단시킴). 그 밖의 가벼운 일은 사용자의 위임 선호대로 하위 모델에 맡긴다: 중요한 설계·이식·최종 판단은 가장 강한 모델이 직접, 덜 중요한 일은 중간 모델, 단순·반복 작업(실행·로그 요약·목록·파일 정리)은 가장 가벼운 모델(2.7). 하위 모델 결과는 직접 확인한 뒤 반영한다.
- 바닐라 코드는 **그대로** 이식한다: 필드 이름·메서드 순서·`random.nextXxx()` 호출 순서·HashMap 순회 순서까지. (예: 퇴비통·피스톤 갱신 순서가 바닐라 HashMap 순회 순서를 같은 해시로 재현.) 단 이것은 **관측 결과에 영향을 주는 부분**에 대한 규칙이다. 결과에 영향이 없는 구조적 비효율(호출마다 할당, 반복 스캔, 단일 스레드 가정, 불필요한 변환)은 2.1.3 의 "비효율은 재설계" 원칙대로 더 싸게 만든다 — 판별 질문은 2.1.3.
- 네트워크 프록시가 **Mojang/papermc 호스트를 막는다.** 우회 시도 금지. 바닐라 데이터는 저장소의 jar 에서 뽑는다.
- **Mojang 바닐라 데이터는 커밋하지 않는다**(첫 실행 때 바닐라 jar 에서 추출). 파생된 압축 리소스(`furnace-server/src/main/resources/blocks/*.txt`, `registries/*.txt` 등)는 커밋한다.
  - 주의: 과거에 사용자가 `tools/vanilla-data/minecraft_server.1.21.1.jar` 와 `tools/vanilla-data/server-1.21.1/**`(내부 서버 jar 를 푼 클래스 파일 12,951개)를 **이미 git 에 올려 두었다**(커밋 `ed46c6b5 tools`). 새로 추가하지 말고 지우지도 말 것. 디컴파일 소스(`.java`)나 생성 리포트(`generated/`)는 **절대 커밋 금지.**
- 사용자에게 보고할 때는 한국어로, 무엇을 바닐라와 맞췄고 무엇이 남았는지 구체적으로.

---

## 4. 환경·도구

### 4.1 기본 명령

```bash
cd /home/user/Furnace
ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:compileJava      # 컴파일만
ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test              # 전체 테스트(≈4분)
ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:installDist    # 실행 배포(오라클이 사용)
# 서버 실행 파일: furnace-server/build/install/furnace-server/bin/furnace-server
```

- `--offline` 은 gradle 캐시(`~/.gradle`)에 의존한다. **새 컨테이너에 캐시가 없으면** 처음 한 번은 `--offline` 없이 의존성(Maven Central: gson, netty, slf4j, logback, junit)을 받아야 할 수 있다. 막히면 `read_documentation`(environment.network)로 허용 호스트를 확인하고, 우회하지 않는다.
- Java 21, 서버 기본 JVM 옵션 `-Xmx6G`(`furnace-server/build.gradle.kts`).
- `-Dfurnace.*` 시스템 프로퍼티는 테스트 JVM 에 그대로 전달된다(오라클 봇/덤프가 이 방식으로 인자를 받는다).
- 바닐라 jar 위치: 서버는 `-Dfurnace.vanillaJar=<경로>` 또는 캐시에서 바닐라 데이터를 첫 실행 때 추출한다. 저장소에 `tools/vanilla-data/minecraft_server.1.21.1.jar` 가 있다(위 3.4 주의 참고).

### 4.2 새 컨테이너에서 사라지는 것과 복원 방법

| 사라지는 것 | 직전 세션 위치 | 복원 방법 |
|---|---|---|
| 오라클 실행 스크립트 `run-dbg.sh` | 스크래치패드 | 저장소에 **`tools/block-oracle/run.sh` 가 이미 있다**(거의 동일). 차이는 4.3 참조. 디버깅 변형을 쓰려면 아래 전문을 `tools/block-oracle/run-dbg.sh` 로 저장(커밋은 선택) |
| 디컴파일된 바닐라 소스 `<scratchpad>/decomp/src/<cls>.java` (4,058개) | 스크래치패드 | 아래 4.5 절차로 재생성 |
| 바닐라 리포트 `/tmp/claude-0/jar/work/gen/reports/*.json` (registries/packets/commands/blocks/items) | /tmp | 바닐라 데이터 생성기(4.5)로 재생성 |
| 블록 이름→클래스 맵 `/tmp/claude-0/jar/work/block_class.json` | /tmp | `ExportBlockProps` 등으로 재생성하거나 디컴파일 `dga`(Blocks) 정적 초기화를 읽는다 |
| Vineflower 디컴파일러 `/tmp/claude-0/jar/tools/vineflower.jar` | /tmp | Maven Central `org.vineflower:vineflower` (프록시가 허용하면). 못 받으면 4.5 의 대안 |
| 이 문서에 요약한 미구현 인벤토리 | 스크래치패드 `unimplemented-inventory.md` | 7장에 전부 옮김 |
| 오라클 작업 폴더(`oracle-*`)·각종 로그 | 스크래치패드 | 필요하면 다시 실행 |

### 4.3 블록 오라클 (순정 서버와의 차분 비교)

**목적:** 같은 봇 시나리오를 순정 1.21.1 서버와 Furnace 에 각각 돌리고, 저장된 월드의 블록 상태를 칸 단위로 비교한다. 블록 동작 이식이 바닐라와 같은지 확인하는 기본 도구. (엔티티는 비교하지 않는다 — 블록만. 엔티티 오라클은 TODO.)

```bash
bash tools/block-oracle/run.sh <시나리오 파일> <영역 x1,y1,z1,x2,y2,z2> <작업 폴더>
# 직전 세션은 디버깅 변형 run-dbg.sh 를 같은 인자로 썼다:
bash run-dbg.sh <시나리오 파일> <영역> <작업 폴더>
```

- 출력: 일치하면 `일치`, 아니면 `차이 N칸 — <작업 폴더>/diff.txt`.
- 흐름: `installDist` → 순정 서버(jar) 기동 → 봇(`ScenarioBot` 테스트)이 시나리오 수행 → 순정 종료 → Furnace 기동 → 같은 봇 → 종료 → 두 월드를 `WorldDump` 테스트로 `x y z 상태` 텍스트로 덤프 → diff.
- 두 서버 모두 평지(`level-type=minecraft:flat`), 구조물 없음, 시드 1, 크리에이티브, 오프라인, 압축 끔. 봇 이름 `Oracle` 은 OP 로 등록돼 `/tp`·`/save-all` 사용.
- **gradle 을 직접 부르므로 다른 gradle 실행과 동시에 돌리지 않는다. 백그라운드로 실행**하고 로그 파일을 기다린다(순정 서버 기동·월드 저장까지 수 분).
- 영역(직전 세션이 쓴 값):

  | 시나리오 | 영역 |
  |---|---|
  | `scenarios/items.txt` | `-2,-63,-2,40,-55,30` |
  | `scenarios/pistons.txt` | `-10,-61,-10,40,-50,40` |
  | `scenarios/fluids.txt` | `-40,-62,-40,40,-55,40` |
  | `scenarios/break-chain.txt`, `scenarios/survival-dig.txt` | `-16,-63,-16,16,-50,16` |
  | `scenarios/basic-place.txt`, `scenarios/types-mix.txt` | 스크립트 머리 주석 참고 |

- **최종 결과(4-1b 이후): items, break-chain, survival-dig, pistons 모두 `일치`.**
- 무작위 틱은 바이트 단위로 같지 않다(순정 레벨 난수는 시드 없음) → 영향 받을 수 있는 시나리오는 `cmd gamerule randomTickSpeed 0` 로 끈다.
- 시나리오 문법(`furnace-server/src/test/java/org/domi/furnace/server/oracle/ScenarioBot.java` 머리말):
  `tp x y z [yaw pitch]`, `look yaw pitch`, `item <핫바 0..8> <아이템> [개수]`, `hold <0..8>`, `sneak on|off`, `use x y z <면> [cx cy cz] [off] [inside]`, `useitem [off]`, `break x y z <면>`, `dig x y z <면> <틱>`, `cmd <명령>`, `wait <틱>`.
- 평지 잔디 윗면은 y=-61, 서 있는 높이 y=-60. 크리에이티브 손 닿는 거리(약 5칸) 밖은 `tp` 로 옮긴 뒤 설치.
- 메뉴(4-2)를 하면 컨테이너 조작은 블록 오라클로 못 본다. 대신 **패킷 단위 대조**(순정 서버에 봇을 붙여 `container_set_slot`/`container_set_content` 시퀀스 캡처 → Furnace 와 비교) 또는 순정 클래스 로직을 직접 호출하는 골든 테스트를 쓰는 것이 이 프로젝트의 기존 방식이다(예: `DataComponentsVanillaTest`, `VanillaRecipesTest`).

#### 오라클 스크립트 전문 (`run-dbg.sh`, 직전 세션에서 쓴 그대로)

저장소의 `tools/block-oracle/run.sh` 와의 차이는 두 가지뿐이다: (1) `REPO` 가 `/home/user/Furnace` 로 고정(저장소판은 스크립트 위치에서 계산), (2) `bot()` 이 gradle 출력을 `$W/bot.log` 에 모으고 실패 시 `ScenarioBot` 테스트 XML 을 `$W/bot-<포트>.xml` 로 복사한다(저장소판은 `/dev/null`). 디버깅할 때 유용하니 필요하면 `tools/block-oracle/run-dbg.sh` 로 저장해 쓰되, 기능 개선(2)만 `run.sh` 에 반영해도 좋다.

```bash
#!/bin/bash
# 블록 동작 차분 비교: 같은 시나리오를 순정 1.21.1 서버와 Furnace 에 돌리고 저장된 블록을 비교한다.
# 사용: run.sh <시나리오 파일> <영역 x1,y1,z1,x2,y2,z2> <작업 폴더>
#   VANILLA_JAR  순정 서버 jar(기본 tools/vanilla-data/minecraft_server.1.21.1.jar — 커밋하지 않는다)
# 결과: <작업 폴더>/vanilla.txt, furnace.txt, diff.txt (diff 가 비면 일치)
set -u
REPO=/home/user/Furnace
SCN=$(realpath "$1"); AREA=$2; W=$(realpath -m "$3")
VANILLA_JAR=${VANILLA_JAR:-$REPO/tools/vanilla-data/minecraft_server.1.21.1.jar}
FURNACE_BIN=$REPO/furnace-server/build/install/furnace-server/bin/furnace-server
NAME=${ORACLE_NAME:-Oracle}
UUID=$(python3 -c "import hashlib,uuid,sys;h=bytearray(hashlib.md5(b'OfflinePlayer:$NAME').digest());h[6]=h[6]&0x0f|0x30;h[8]=h[8]&0x3f|0x80;print(uuid.UUID(bytes=bytes(h)))")

prepare() {   # <폴더> <포트>
    rm -rf "$1"; mkdir -p "$1"
    cat > "$1/server.properties" <<P
online-mode=false
level-type=minecraft\:flat
generate-structures=false
level-seed=1
gamemode=creative
force-gamemode=true
difficulty=peaceful
spawn-protection=0
network-compression-threshold=-1
server-port=$2
max-players=4
enable-command-block=false
view-distance=4
simulation-distance=4
P
    echo "eula=true" > "$1/eula.txt"
    echo "[{\"uuid\":\"$UUID\",\"name\":\"$NAME\",\"level\":4,\"bypassesPlayerLimit\":false}]" > "$1/ops.json"
}

run_server() {   # <폴더> <명령…>  — FIFO 로 콘솔을 잡고 Done 까지 기다린다
    local dir=$1; shift
    rm -f "$dir/stdin.fifo"; mkfifo "$dir/stdin.fifo"
    ( cd "$dir" && exec "$@" < stdin.fifo > out.log 2>&1 ) &
    eval "PID_$(basename "$dir")=$!"
    exec {FD}>"$dir/stdin.fifo"
    for i in $(seq 1 300); do grep -q 'Done (' "$dir/out.log" 2>/dev/null && break; sleep 1; done
    eval "FD_$(basename "$dir")=$FD"
}

stop_server() {   # <폴더>
    local fdvar="FD_$(basename "$1")"; local fd=${!fdvar}
    local pidvar="PID_$(basename "$1")"; local pid=${!pidvar}
    echo "stop" >&"$fd"; exec {fd}>&-
    for i in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
}

bot() {   # <포트>
    (cd "$REPO" && ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:test --tests '*ScenarioBot*' \
        -Dfurnace.scn.port=$1 -Dfurnace.scn.script="$SCN" -Dfurnace.scn.name=$NAME --rerun-tasks >>"$W/bot.log" 2>&1) || { echo "봇 실패(포트 $1)"; cp furnace-server/build/test-results/test/TEST-org.domi.furnace.server.oracle.ScenarioBot.xml "$W/bot-$1.xml"; }
}

dump() {   # <월드> <출력>
    (cd "$REPO" && ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:test --tests '*WorldDump*' \
        -Dfurnace.dump.world="$1" -Dfurnace.dump.area="$AREA" -Dfurnace.dump.out="$2" --rerun-tasks >/dev/null 2>&1) || echo "덤프 실패: $1"
}

mkdir -p "$W"
(cd "$REPO" && ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:installDist) || exit 1
prepare "$W/vanilla" 25601
prepare "$W/furnace" 25602
run_server "$W/vanilla" java -Xmx2G -jar "$VANILLA_JAR" nogui
bot 25601
stop_server "$W/vanilla"
run_server "$W/furnace" env JAVA_OPTS="-Xmx3G -Dfurnace.vanillaJar=$VANILLA_JAR" "$FURNACE_BIN"
bot 25602
stop_server "$W/furnace"
dump "$W/vanilla/world" "$W/vanilla.txt"
dump "$W/furnace/world" "$W/furnace.txt"
diff "$W/vanilla.txt" "$W/furnace.txt" > "$W/diff.txt"
if [ -s "$W/diff.txt" ]; then echo "차이 $(grep -c '^<' "$W/diff.txt")칸 — $W/diff.txt"; else echo "일치"; fi
```

### 4.4 바닐라 참고 자료 (사라짐 — 재생성 필요)

직전 세션에서 이렇게 썼다.
- 난독화된 1.21.1 서버 소스: `<scratchpad>/decomp/src/<난독화클래스>.java` (Vineflower 로 내부 서버 jar 를 디컴파일). 이름이 난독화돼 있어 아래 매핑표가 필수.
- 리포트: `/tmp/claude-0/jar/work/gen/reports/` 의 `registries.json`, `packets.json`, `commands.json`, `blocks.json`, `items.json`.
- 블록 이름 → 클래스 맵: `/tmp/claude-0/jar/work/block_class.json` (예: `"stone": "dfy"`).

### 4.5 바닐라 데이터·디컴파일 재생성

1. 저장소의 `tools/vanilla-data/minecraft_server.1.21.1.jar`(번들러 jar, 추적됨)가 있는지 확인. 없으면 `tools/vanilla-data/server-1.21.1/`(이미 풀어 둔 내부 서버 클래스) 를 쓴다. Mojang 호스트는 막혀 있으므로 새로 내려받으려 하지 않는다.
2. 리포트 생성(`tools/vanilla-data/README.md` 의 "준비"):
   ```bash
   java -DbundlerMainClass=net.minecraft.data.Main -jar tools/vanilla-data/minecraft_server.1.21.1.jar --reports --output <임시폴더>/generated
   # 번들러가 안 되면: java -cp "<내부 서버 jar>:<libraries/*.jar>" net.minecraft.data.Main --all --output generated
   ```
   → `generated/reports/{registries,packets,commands,blocks,items}.json`, `generated/data/**`.
3. 디컴파일: Vineflower(`java -jar vineflower.jar <내부 서버 jar 또는 server-1.21.1 폴더> <출력 폴더>`). Vineflower 를 못 받으면(프록시) Maven 캐시·`~/.gradle` 에 paperweight 쪽 사본이 있는지 찾아본다. 그래도 없으면 CFR 등 오프라인 가능한 도구를 쓰고, 그것도 없으면 **Mojang 매핑 이름으로 기억한 바닐라 로직을 매우 신중하게** 이식하고 TODO 로 "미검증" 표시한다(추측으로 단정 금지).
4. 클래스 매핑이 필요하면 아래 표를 먼저 본다. `tools/vanilla-data/Export*.java` 는 난독화 이름을 직접 참조하므로 **1.21.1 전용**이다(실행 예: `ab.a(); akt.a();` 로 SharedConstants+Bootstrap 후 레지스트리 순회).
5. 디컴파일 소스·`generated/` 는 임시 폴더에만 두고 커밋하지 않는다.

### 4.6 난독화 클래스 매핑 (1.21.1 서버, 직전 세션에서 확인)

| 난독화 | Mojang 이름 | 난독화 | Mojang 이름 |
|---|---|---|---|
| `bsr` | Entity | `cjh` | ItemEntity |
| `aqt` | ServerEntity | `aqb` | ChunkMap (내부 `b` = TrackedEntity) |
| `arq` | PlayerChunkSender | `exc` | Vec3 |
| `ewx` | AABB | `cmx` | Player |
| `btn` | LivingEntity | `ajy` | EntityDataSerializers |
| `dga` | Blocks | `cut` | Items |
| `avp` | SoundEvents | `dfl` | BaseFireBlock |
| `div` | FarmBlock | `dnd` | SlimeBlock |
| `dgj` | CactusBlock | `dpm` | WebBlock |
| `djs` | HoneyBlock | `dge` | BubbleColumnBlock |
| `dlt` | PowderSnowBlock | `dfr` | BedBlock |
| `dkg` | LavaCauldronBlock | `dkh` | LayeredCauldronBlock |
| `dlw` | PressurePlateBlock | `dpp` | WeightedPressurePlateBlock |
| `dlx` | PumpkinBlock | `dfy` | (기본 `Block` 계열: stone·dirt 등이 이 클래스) |
| `akt` | Bootstrap (Export 도구가 사용) | `ab` | SharedConstants |

(`dfy` 는 `block_class.json` 에서 stone/granite/dirt 등의 클래스로 확인된 값이며 일반 `Block` 으로 추정. 확정 아님.)

메뉴(4-2) 작업에서 찾아야 할 클래스는 재생성한 디컴파일 소스에서 문자열(`"container.chest"`, `"minecraft:generic_9x3"` 등)이나 `MenuType` 정적 초기화로 역추적한다.

### 4.7 엔티티 데이터 직렬화기 id (1.21.1 `EntityDataSerializers`, `ajy`)

| id | 종류 | id | 종류 |
|---|---|---|---|
| 0 | byte | 12 | direction |
| 1 | int | 13 | optional uuid |
| 2 | long | 14 | block state |
| 3 | float | 15 | optional block state |
| 4 | string | 16 | compound |
| 5 | component | 17 | particle |
| 6 | optional component | 18 | particles |
| 7 | item stack | 19 | villager data |
| 8 | boolean | 20 | optional unsigned int |
| 9 | rotations | 21 | pose |
| 10 | block pos | | |
| 11 | optional block pos | | |

`net/protocol/play/SetEntityData.java` 에 현재 구현된 직렬화기 상수: BYTE 0, INT 1, FLOAT 3, ITEM 7, BOOLEAN 8, POSE 21. 필요하면 늘린다.

### 4.8 클라이언트바운드 play 패킷 id (엔티티 작업에 쓴 것)

| id | 패킷 | id | 패킷 |
|---|---|---|---|
| 0x00 | bundle_delimiter | 0x5A | set_entity_motion |
| 0x01 | add_entity | 0x6F | take_item_entity |
| 0x58 | set_entity_data | 0x70 | teleport_entity |
| 0x2E | move_entity_pos | 0x2F | move_entity_pos_rot |
| 0x30 | move_entity_rot | 0x48 | rotate_head |
| 0x42 | remove_entities | 0x1F | entity_event |
| 0x15 | container_set_slot | | |

**4-2(메뉴)에 필요한 id (이번에 `packets.json` 에서 확인):**
- 클라이언트바운드: `container_close` 0x12, `container_set_content` 0x13, `container_set_data` 0x14, `container_set_slot` 0x15, `open_screen` 0x33, `place_ghost_recipe` 0x37, `merchant_offers` 0x2D, `set_equipment` 0x5B, `block_entity_data` 0x07, `animate` 0x03, `hurt_animation` 0x24, `award_stats` 0x04, `level_particles` 0x29, `explode` 0x20, `add_experience_orb` 0x02, `update_attributes` 0x75, `update_mob_effect` 0x76, `remove_mob_effect` 0x43, `set_passengers` 0x5F, `sound` 0x68, `sound_entity` 0x67, `stop_sound` 0x6A.
- 서버바운드: `container_button_click` 0x0D, `container_click` 0x0E, `container_close` 0x0F, `container_slot_state_changed` 0x10, `interact` 0x16, `pick_item` 0x20, `place_recipe` 0x22, `player_action` 0x24, `rename_item` 0x2A, `select_trade` 0x2D, `set_carried_item` 0x2F, `set_creative_mode_slot` 0x32, `swing` 0x36, `use_item_on` 0x38, `use_item` 0x39.
- 엔티티 종류 id 는 `furnace-server/src/main/resources/registries/entity_types-1.21.1.txt` 의 줄 순서(0부터). `minecraft:item` = 58, `minecraft:player` = 128 (총 130종).

### 4.9 메뉴 종류 레지스트리 id (`minecraft:menu`, 1.21.1 — `registries.json` 에서 확인)

| id | 이름 | id | 이름 |
|---|---|---|---|
| 0 | generic_9x1 | 13 | enchantment |
| 1 | generic_9x2 | 14 | furnace |
| 2 | generic_9x3 | 15 | grindstone |
| 3 | generic_9x4 | 16 | hopper |
| 4 | generic_9x5 | 17 | lectern |
| 5 | generic_9x6 | 18 | loom |
| 6 | generic_3x3 | 19 | merchant |
| 7 | crafter_3x3 | 20 | shulker_box |
| 8 | anvil | 21 | smithing |
| 9 | beacon | 22 | smoker |
| 10 | blast_furnace | 23 | cartography_table |
| 11 | brewing_stand | 24 | stonecutter |
| 12 | crafting | | |

(현재 코드는 `OpenScreen(containerId, 2, "Chest")` 로 generic_9x3 = 2 만 쓴다.)

---

## 5. 아키텍처 요약

경로는 모두 `furnace-server/src/main/java/org/domi/furnace/server/` 기준(이하 `S/`).

### 5.1 패키지

| 패키지 | 내용 |
|---|---|
| `S/FurnaceMain`, `ServerCore` | 진입점·전역 서비스(명령, 플러그인, 이벤트, 게임룰, 틱) |
| `S/net/` | Netty 서버. `ConnectionHandler`(핸드셰이크→로그인→설정→플레이 패킷 처리, 약 1,900줄), `net/protocol/play/*`(패킷 클래스), `net/io`(Types, NBT), `net/auth`(온라인 모드) |
| `S/world/` | `World`(공유 자원 + regionizer), `Region`, `RegionLevel`, `Regionizer`, `ChunkColumn`/`ChunkSection`(청크 저장), `anvil/`(디스크), `gen/`(월드젠: 노이즈·바이옴·카버·feature·구조물·jigsaw), `WorldBorder`, `GameRules`, `WorldClock` |
| `S/block/` | 블록 엔진. `ChunkLevel`(블록 레벨 + 엔티티 레벨의 본체), `BlockLevel`(인터페이스), `BlockBehavior`(블록 동작 기본 클래스 — 바닐라 `BlockBehaviour`), `types/`(바닐라 블록 클래스 이식, 파일 284개), `entity/`(블록 엔티티), `ItemUse`, `BlockBreaking`, `Shape`(VoxelShape), `Clip`, `NeighborUpdater`, `FluidTicker` |
| `S/entity/` | `Entity`, `ItemEntity`, `Player`(+`PlayerVitals`, `FoodData`, `PlayerData`, `Abilities`), `PlayerInventory`, `EntityStore`, `EntityTracker`, `SynchedData`, `EntityType`, `Entities`, `AABB`, `ItemStack`, `EntityView` |
| `S/item/` | 아이템 컴포넌트 57종 코덱(저장 NBT/JSON·네트워크), 패치 모델 |
| `S/data/`, `S/recipe/`, `S/loot/`, `S/advancement/` | 데이터 팩 로더, 레시피(1,290개), 전리품 표 평가, 발전 과제(1,399개) |
| `S/command/` | Brigadier 명령(`BuiltinCommands`, `builtin/`, `arguments/`) |
| `S/plugin/`, `S/sched/` | 플러그인 로더/이벤트/스케줄러 |
| `S/config/` | `server.properties`, `furnace.properties` |

### 5.2 스레딩 모델 — Folia 식 동적 regionizer

설계 문서: `docs/superpowers/specs/2026-10-17-dynamic-regionizer.md`

- 청크를 **구역(section) = 2^shift × 2^shift 청크**(기본 shift 4 → 16×16 청크)로 묶고, 구역들의 집합이 **region** 이다. 불변식: 서로 다른 region 의 구역은 체비셰프 거리로 `merge-radius`(기본 1) 구역보다 멀다 → 블록 갱신·플레이어 시야가 region 을 넘지 않는다.
- 청크 내용(`ChunkColumn`)은 월드 공용 맵(`World.chunks`, `ConcurrentHashMap<long, ChunkColumn>`)에 있지만 **그 청크의 구역을 가진 region 스레드만** 만진다.
- 작업 스레드 풀(`furnace-region-N`, 설정 `region-threads`)이 가장 이른 다음 틱 시각의 region 부터 한 번에 한 스레드가 돈다. `Region.runTick`: 작업 큐(inbox) → 지연 작업 → 게임 틱(`Region.tick`).
- **작업 보내기:** 모든 월드 작업은 청크 키를 달고 소유 region 에서 돈다. `World.runAtChunk(ChunkPos, Consumer<Region>)`, `World.runAt(blockX, blockZ, task)`, `runAtRequested(...)`(클라이언트가 보낸 좌표용), `submitAtChunk(...)`(결과 `CompletableFuture`). 큐에 넣은 뒤 그 region 이 합쳐지거나 나뉘면 `Region.mine()` 이 아니라고 보고 지금 주인에게 다시 보낸다.
- **합치기(merge)/나누기(split):** 틱 사이에만(`Regionizer` 잠금 아래). 가까워진 region 은 합치고(`absorbData`, `RegionLevel.absorbFrom`), 멀어지면 연결 성분으로 나눈다(`splitData`, `RegionLevel.splitInto`). **자료는 청크(위치) 단위로 옮긴다** — 예약 틱, 변경 방송 대기, 블록 이벤트, ack 대기, 블록 엔티티 틱 목록, **엔티티**(`entities.splitTo(target.entities, inTarget)` + `e.setLevel(target)`).
- 플러그인 `scheduler.region(RegionPos)` 의 RegionPos 는 청크 좌표.
- 결과 동일성: 한 region 안에서는 한 스레드가 바닐라 순서로 돈다. 플레이어들이 가까워 한 region 이면 그 안은 바닐라와 같고, region 이 나뉘면 region 별 무작위 틱 난수는 바이트 단위로 같을 수 없고 통계적으로만 같다(알려진 의도적 차이, 2.1.2·2.2). (옛 문장 "기본 구성에서 월드 전체가 한 region" 은 동적 regionizer 이전 `region-size=0` 단계의 설명이라 고쳤다.)
- 소유 확인: `world.assertOwns(region)`. `Regionizer.chunkKey(cx,cz)` / `chunkX(key)` / `chunkZ(key)` 로 키 변환.

### 5.3 레벨·틱 순서

`Region.tick()` → `RegionLevel.tickBlocks(tickingChunks, n, blockEntityTicking, inRegion)`:

1. 예약 틱(`runScheduledTicks`, 블록·유체)
2. 무작위 청크 틱(`shuffle` 후 `tickChunk` — 천둥·얼음/눈·구역별 무작위 틱)
3. `broadcastChanges()` — 블록 변경 방송
4. `trackEntities` — 바닐라 `ChunkMap.tick`(엔티티 추적)
5. `runBlockEvents()`
6. `tickEntities` — 엔티티 틱
7. `tickBlockEntities` — 블록 엔티티 틱

이후 `Region.tick` 이 플레이어 처리(`processMoves` → `level.playerMoved`, `tickVitals`, `touchEntities`(줍기), `tickBreaking`, `broadcastInventory`, 청크 전송, 가시성), `handOffEntities`, `flushAcks`, 청크 퇴출/자동 저장을 한다.

- 엔티티 틱 청크 = 이 region 이 가진 로드된 청크 중 플레이어가 시뮬레이션 거리 −1(체비셰프) 안인 것(`Region.blockEntityTicking`).
- 무작위 틱 청크 = 비관전 플레이어 시뮬레이션 거리(체비셰프 ≤ min(sim,11)−1, 최대 8) + 청크 중심 128 칸 안(`collectTickingChunks`).

### 5.4 엔티티 시스템 (4-1)

- **저장소:** `EntityStore`(region 의 `RegionLevel`/`ChunkLevel` 소유). 틱 목록은 `LinkedHashMap`(넣은 순서, 틱 중 추가는 다음 틱부터 — `tickSnapshot()`), 구역 조회용 `TreeMap<Long(SectionPos 키), ArrayList<Entity>>` — 바닐라 `SectionPos` 정렬 순서로 AABB 조회(`getEntities`, `getEntitiesOfClass`)해서 줍기·합치기 순서가 바닐라와 같다.
- **`Entity`**(약 915줄): 위치·속도·회전·몸통 AABB, `move`(블록 충돌 — 바닐라 `BlockCollisions` 칸 순회, VoxelShape 격자 충돌, y 먼저 · |x|<|z| 면 z 먼저), 디딘 블록(`checkSupportingBlock`), 낙하(`fallOn`)·`updateEntityAfterFallOn`·`stepOn`·`entityInside`·속도 계수·`makeStuckInBlock`·`moveTowardsClosestSpace`, `baseTick`(물/용암 높이·흐름 밀기(`FlowingFluid.getFlow` 이식)·첨벙 소리·눈 높이 유체·불·용암 피해·세계 밑 제거), `RemovalReason`, `save/load`(NBT). 엔티티 데이터 번호 0..7(공유 플래그·공기·이름·이름 보임·조용함·중력 없음·자세·얼음 틱), 아이템은 8.
- **`SynchedData`:** 바닐라 `SynchedEntityData`. dirty 값만 `packDirty`, 처음 보일 때는 `nonDefaultValues`. 아이템 스택은 바닐라처럼 객체 동일성 비교.
- **`ItemEntity`**(270줄): 중력 0.04·공기 0.98·디딘 블록 미끄러움, 땅에서는 **4틱마다만 이동**(나머지 틱엔 중력만 누적), 물·용암에서 뜨기, 합치기(움직였으면 2틱·아니면 40틱, 같은 아이템·컴포넌트, 64 상한), 수명 6000, 체력 5, 줍기(`playerTouch`: 지연·주인·부분 줍기)·`take_item_entity`, NBT(Health·Age·PickupDelay·Thrower·Owner·Item).
- **`EntityType`:** 레지스트리 번호·크기·눈높이·추적 거리·갱신 주기·델타 추적·불 면역. 지금은 `ITEM` 만 정의(`of(...)` 로 추가). **`Entities.load(level, tag)`** 가 저장 NBT → 엔티티(지금 `minecraft:item` 만, 모르는 종류는 null → 호출 쪽이 원본 NBT 보존).
- **추적(`EntityTracker`):** 바닐라 `ChunkMap.TrackedEntity + ServerEntity`. 보임 판정은 **엔티티 구역 이동 · 플레이어 이동 패킷(`Region.processMoves` → `level.playerMoved`) · 추가** 때만 다시 한다(거리 = min(종류 범위 × 방송 비율, 시야 × 16), 청크 전달 완료 확인 `chunkTrackedBy`). 스폰은 **묶음(bundle: add_entity + 데이터 + 속도)**. 변화는 갱신 주기·`hasImpulse`·데이터 변화 때 1/4096 델타(넘침·400번째·땅 닿음 변화면 teleport)·속도(1e-7)·데이터·머리. `hurtMarked` 즉시 속도. 각도는 `Mth.floor`(바닐라) — `Types.writeAngle` 도 이에 맞춰 고침.
- **region 이동:** 엔티티가 다른 region 소유 청크로 들어가면 `Region.handOffEntities` → 소유자가 있으면 `world.runAtChunk(..., r -> r.level().adoptEntity(e))`; **소유자 없는(로드 안 된) 청크**면 `ChunkLevel.evictToDisk(e)` 가 `UNLOADED_TO_CHUNK` 로 제거 후 entities 파일에 덧붙임(`World.appendEntitiesAsync`).
- **저장:** `entities/` 지역 파일(바닐라 `EntityStorage` 형식 `{DataVersion, Position, Entities}`). 청크 로드 때 IO 풀에서 읽어 넣고(`Region.loadEntities`), 내릴 때 저장 후 제거, 자동 저장 때 엔티티가 있거나 있던 청크. 모르는 종류는 원본 NBT 보존.
- **줍기:** `Region.touchEntities(p)` 가 플레이어마다 매 틱, 몸통 상자를 (1, 0.5, 1) 넓힌 상자 안 엔티티에 `playerTouch`.
- **드롭 생성:** `ChunkLevel.popResource(pos, stack)`, `popResourceFromFace(pos, dir, stack)`(doTileDrops 확인 포함), `dropContents(pos, ItemStack[])`(= `Containers.dropContents`: **빈 칸도 위치 난수 3번** 소비, 10~30 개씩, 삼각 분포 속도). 플레이어 버리기는 연결 스레드에서 인벤토리를 먼저 바꾼 뒤 `world.runAt(...)` → `Region.playerDrop(p, stack, dropAround, includeThrower)`.
- **엔티티 id:** `World.entityIds()` 의 `EntityIdAllocator` 를 플레이어와 공유(`FurnaceMain` 이 같은 할당기를 연결). `RegionLevel.nextEntityId()` 가 이를 쓴다(`ChunkLevel` 기본은 폴백 증가 카운터 — 테스트용).
- **블록 훅(Entity 쪽 호출):** `fallOn`/`stepOn`/`entityInside` 등. 구현된 블록: 불·영혼 불, 선인장, 거미줄·가루눈, 슬라임, 침대, 꿀, 건초, 경작지, 거품 기둥, 가마솥(용암/물), 압력판(나무/돌/무게판), 인계선.

### 5.5 플레이어 인벤토리 (4-1a)

`S/entity/PlayerInventory.java`(167줄) — **서버 권위**:
- 창 칸 0..45: `0` 제작 결과, `1..4` 제작 칸, `5..8` 머리·가슴·다리·발, `9..35` 주 인벤, `36..44` 핫바, `45` 왼손. + 커서(`carried`) + 든 칸(`selected`).
- `slots[]`(서버 진실) 와 `remote[]`(클라이언트가 아는 값)를 따로 들고 `broadcastChanges()` 가 바닐라 `AbstractContainerMenu.broadcastChanges` 처럼 **달라진 칸만** `container_set_slot` 으로 보낸다(`stateId` 관리 `incrementStateId`). `fullState()` = `container_set_content`.
- `add(stack, infinite, maxStack, damaged)`: 바닐라 `Inventory.add` 칸 순서(든 칸 → 왼손 → 0..35, 다음 빈 칸) + **"마지막 시도가 다 넣지 못하면 false"** 특이점(부분 줍기에서 스택이 조용히 줄어든다).
- 메서드는 `synchronized` — region 스레드(줍기·쓰기)와 연결 스레드(클릭·저장)가 함께 쓴다.
- `Inventory` 번호 → 창 칸: `windowSlot(inventoryIndex)` (0..8 → 36..44, 9..35 → 그대로, 36..39 → 장비, 40 → 45).

### 5.6 컨테이너(상자) — 현재의 임시 구현

- `ConnectionHandler.openChest(x,y,z)`(약 1875줄): 컨테이너 id 1..100 순환(`nextContainerId`), `Player.openChest(containerId, x,y,z)` 로 열린 상자 기억, 소유 region 에서 `ChestBlockEntity`(전리품 풀기 포함) 내용을 읽어 `OpenScreen(containerId, 2, "Chest")` + `ContainerSetContent(63칸: 상자 27 + 주 27 + 핫바 9, stateId 1)` 전송. **더블 상자 병합 없음.**
- `case 0x0E`(container_click): 클라이언트가 보낸 `changedSlots` + `carried` 를 받아, **플레이어 인벤 창(containerId 0)** 은 `applyInventoryClick`(아이템 종류별 총량 보존 검사 후 수용, 아니면 `fullState()` 로 되돌림), **상자 창**은 `clickType==0`(PICKUP)만 처리해 칸 번호로 상자/인벤에 직접 반영. `stateId` 는 읽고 버린다(검증 안 함). 그 외 clickType(QUICK_MOVE·SWAP·CLONE·THROW·QUICK_CRAFT·PICKUP_ALL)은 무시(버리기 THROW·창 밖 클릭은 `inventoryDropClick` 으로 일부 처리).
- 제작 결과 칸(0) 변경 보고는 거부, 제작 격자/레시피 매칭 없음.
- **4-2 에서 이 전체를 서버 권위 메뉴로 교체한다**(8장).

### 5.7 블록 엔진

- `block/types/` 에 바닐라 블록 정의 종류(definition type) 241종 이식(누락 검사 테스트 `BlockTypesTest`). 블록 속성/상태/모양은 `resources/blocks/*.txt`(바닐라에서 파생한 압축 표)에서 로드.
- `BlockBehavior`(222줄)은 바닐라 `BlockBehaviour` 대응: `setPlacedBy`, `playerWillDestroy`, `playerDestroy`, `getDrops`, `getDestroyProgress`, `useItemOn`, `useWithoutItem`, `attack`, `canPlaceLiquid`, `getCollisionShape(state, level, pos, BlockActor)` 등. **`BlockActor`(플레이어 같은 행위자 인터페이스)가 `Entity` 가 아니다** → 엔티티별 충돌 문맥(가루눈·발판)이 막혀 있음.
- 블록 엔티티: `block/entity/BlockEntity` 일반 틀(종류 id = block_entity_type 순서, load/saveAdditional, getUpdateTag, setChanged) + `BlockEntities` 팩토리(comparator, daylight_detector, piston). 상자는 별도 경로(`world/ChestBlockEntity`, `RegionLevel.createBlockEntity` 에서 분기). 나머지 40종은 NBT 보존만.
- 피스톤(`PistonBaseBlock`, `PistonStructureResolver`, `PistonMovingBlockEntity`, `MovingPistonBlock`), 유체(`FlowingFluid`·물·용암, `FluidTicker`), 레드스톤 가루(1.21.1 기본 알고리즘, 갱신 순서 = 바닐라 HashSet 순회 재현), 무작위 틱(`ChunkLevel.tickChunk`).
- 아이템 사용: `block/ItemUse`(양동이·유리병·부싯돌·삽·도끼·밀랍·가위·뼛가루·엔더의 눈 …), 서버 시선 판정 `Item.getPlayerPOVHitResult` → `Level.clip`(`Clip`), `Region.handleUseItem`.
- 블록 파괴: `BlockBreaking`(ServerPlayerGameMode 이식).

### 5.8 영속성·네트워크

- 청크: Anvil 형식(`world/anvil/*`, `AnvilChunkCodec`)으로 `region/` 에 저장, 모르는 NBT·블록 엔티티 보존. 예약 틱은 `block_ticks`/`fluid_ticks`. 엔티티는 `entities/` 별도 지역 파일(바닐라 1.17+ 방식). 플레이어는 `playerdata/<uuid>.dat`(체력·허기·경험치·게임 모드·능력·스폰·인벤토리(`components` compound)·recipeBook).
- 접속 패킷 순서·내용은 바닐라 1.21.1 캡처와 일치하도록 맞춰 둠(레지스트리 동기화 순서 포함).
- `ConnectionHandler.handlePlay` 의 `switch(packetId)` 가 서버바운드 play 패킷 58개 중 25개를 처리(7장 참고). 연결 스레드는 Netty 이벤트 루프이고, 월드 변경은 `world.runAt...` 로 region 스레드에 넘긴다.
- **알려진 선재 불일치:** 청크 시야가 바닐라의 원형(`ChunkTrackingView`) 대신 **정사각형(체비셰프)** 이다.

### 5.9 테스트 (`furnace-server/src/test/java/org/domi/furnace/server/`)

최근 작업과 관련된 주요 클래스:

| 클래스 | 위치 | 내용 |
|---|---|---|
| `ItemEntityTest` | `block/` | 아이템 엔티티 물리·합치기·줍기·드롭·NBT·블록 훅 (@Test 22개) |
| `EntityRegionTest` | `world/` | region 틱에서 엔티티가 돌고 줍는지, 부순 블록이 엔티티를 떨구는지 |
| `ItemUseTest` | `block/` | 양동이·괭이 등 아이템 사용 |
| `PistonTest` | `block/` | 피스톤·비교기 |
| `FluidTest` | `block/` | 유체 흐름 |
| `BlockArgumentsTest` | `block/` | 블록 상태/술어 인자 파서(`/setblock`, `/fill`) |
| `RandomTickTest` | `block/` | 무작위 틱·날씨 |
| `ConnectionInputTest` | `net/` | ConnectionHandler 입력 검증(비정상 이동·임의 좌표 요청·스폰 좌표) |
| 기타 | | `BlockBreakingTest`, `BlockPlacementTest`, `BlockTypesTest`, `GameplayFlowTest`, `ChestInteractionTest`, `PlayerVitalsTest`, `*VanillaTest`(바닐라 골든), `oracle/ScenarioBot`·`WorldDump`(수동 실행용 — 인자가 없으면 skip) |

테스트 헬퍼: `TestServers.java`, `net/TestClient.java`(가짜 클라이언트), `PacketCapture`.

---


---

> 6~10장(진행 상황·미구현 목록·다음 할 일·첫 메시지 예시·부록)은 낡아 `archive/2026-10-18-ch6-10-stale.md` 로 옮겼다(2026-11-17).
