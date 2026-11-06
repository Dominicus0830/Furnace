# FURNACE 플러그인 플랫폼 기획 노트
## Paper 커뮤니티 불만 · 커스텀 이벤트 · 기능 · API 카탈로그

> 작성일 2026-10-26 / 코드 수정 없음 (조사·기획 문서)
> **기본값 = 바닐라 1.21.1 클라이언트** (+필요 시 리소스팩). 이 문서의 모든 기능은 바닐라 클라이언트로 동작하는 것이 기본이다.
> 동반 클라이언트 모드는 **기본값이 아니며** `furnace.properties`의 `experiment.companion-mod=true`로 켰을 때만 쓰이는 선택 확장이다 (**4B절**, 실험 플래그 규약은 **4.33**).

---

## 0. 기본 원칙: 바닐라 클라이언트가 기본값

| 원칙 | 내용 |
|---|---|
| 기본 동작 | 서버는 **바닐라 1.21.1 클라이언트만으로** 모든 기능이 동작해야 한다. 모드·플러그인 채널이 없는 클라이언트가 기준이다 |
| 리소스팩 | 리소스팩은 모드가 아니라 바닐라 기능이다. 필요하면 쓰되, 거부해도 접속과 게임플레이는 되어야 한다 (외형만 폴백) |
| 동반 모드 | **opt-in 확장**이다. 기본값 `experiment.companion-mod=false`에서는 채널을 열지도 요구하지도 않는다. 모드를 필수로 요구하는 정책은 두지 않는다 (4B.2) |
| 실험 플래그 | 불안정하거나 위험한 기능은 `experiment.*=false` 기본으로 게이팅한다. 전부 꺼진 상태 = 바닐라 클라이언트 기본값 서버 (4.33) |
| 폴백 필수 | 모드로 가능한 기능에는 항상 바닐라 폴백이 있어야 한다 (4B.3의 오른쪽 열) |
| 판단 기준 | 기능을 설계할 때 "모드 없이 어떻게 되는가?"를 먼저 답한다. 답이 "동작 안 함"이면 3, 4절이 아니라 4B절 항목이다 |

## 0.1 읽는 법 (신뢰도 표기)

이 문서는 근거 수준이 섞여 있다. 항목마다 아래 표기를 붙였다.

| 표기 | 의미 |
|---|---|
| **[검증]** | 웹 검색 결과 또는 이 레포 문서에서 직접 확인됨. 출처 링크 있음 |
| **[일반지식]** | 작성자의 일반 지식. 이번 조사에서 출처를 못 찾음. 구현 전 확인 필요 |
| **[제안]** | Furnace에 넣자고 하는 설계 제안. 사실 주장이 아님 |

**조사의 한계**
- 커뮤니티 설문이나 "불만 순위" 자료는 찾지 못했다. 아래 불만 목록은 개별 이슈·문서·가이드에서 모은 것이라 빈도나 순위를 의미하지 않는다.
- 이벤트·API 카탈로그(3, 4절)는 대부분 **[제안]** 이다. Paper에 이미 동등한 이벤트가 있는지는 항목별로 대조하지 않았다.
- Reddit, Discord, Paper GitHub 이슈 전수 조사는 하지 않았다. 순위가 필요하면 별도 작업이 필요하다. (Reddit/admincraft 위시리스트 검색은 시도했으나 결과 없음)
- Minestom 기능 개요 검색은 결과가 없어 일반 지식으로만 참고했다. Sponge/Fabric는 문서 목차와 콜백 패턴 수준만 확인했다.
- `docs.papermc.io`, `forums.papermc.io`는 이 환경에서 직접 열리지 않아(DNS 실패) 검색 결과 요약으로만 확인했다.

**Furnace 현재 상태 (레포 문서 기준)**
- 크리에이티브 전용 존재 서버. 채팅, 명령, 몹, 생존, 빛, 네더/엔드가 없다.
- 플러그인 API는 `furnace-api`에 스케줄러 인터페이스와 `Owned`/`ThreadOwner`뿐. 이벤트 시스템과 플러그인 로더는 M6(예정).
- 따라서 이 문서의 대부분은 "지금 된다"가 아니라 "구조상 가능하다 / 처음부터 설계할 수 있다"이다.

---

## 1. Paper/Bukkit 생태계의 커뮤니티 불만

### 1.1 API 안정성·버전 호환

| # | 불만 | 신뢰도 | 근거 | Furnace의 대응 방향 |
|---|---|---|---|---|
| 1 | 데이터 컴포넌트 API는 마인크래프트 변경에 따라 깨질 수 있고 하위 호환을 약속하지 않는다 | **[검증]** | [Paper 문서](https://docs.papermc.io/paper/dev/data-component-api/) | 플러그인 API 자체 버전 관리. 컴포넌트를 래핑해서 MC 변경을 API 뒤에서 흡수 [제안] |
| 2 | ItemMeta ↔ 바닐라 변환에 잔여 이슈가 많다 (1.20.6 릴리스 노트) | **[검증]** | [Paper 1.20.6](https://papermc.io/news/paper-velocity-1-20-6/) | 처음부터 컴포넌트 기반 단일 아이템 모델. 변환 계층 없음 [제안] |
| 3 | `canPlaceOn`/`canBreak`가 재료 목록으로 표현 안 되게 바뀌어 기존 메서드가 깨짐 | **[검증]** | [Issue #10818](https://github.com/PaperMC/Paper/issues/10818) | 술어 기반 API를 처음부터 제공 [제안] |
| 4 | 일부 메서드가 deprecated 표시·대체 안내 없이 사라진다 | **[검증]** | 같은 이슈 | `@Deprecated(since, forRemoval)` + 최소 N버전 유지 정책 + 자동 마이그레이션 리포트 [제안] |
| 5 | 1.21.4로 올릴 때 Component 받는 ItemMeta 이름 메서드 등이 사라졌다 | **[검증]** | [포럼 스레드](https://forums.papermc.io/threads/the-problem-with-components-in-paper-1-21-4.1526/) | 같음 |
| 6 | 26.1부터 난독화 이름이 없어져, Spigot 매핑으로 컴파일한 플러그인은 로드되지 않는다 | **[검증]** (2차 출처) | [모범 사례 글](https://www.mcrpg.com/articles/paper-plugin-best-practices) | Furnace는 서버 내부 이름과 API가 분리 (구현 무지 API) |
| 7 | API에 없는 건 NMS + paperweight-userdev로 내부 접근해야 하고, 리플렉션은 재난독화 대상이 아니며, 최신 userdev만 공식 지원 | **[검증]** | [Paper 문서](https://docs.papermc.io/paper/dev/userdev/) | 플러그인이 NMS를 쓸 필요가 없을 만큼 확장 포인트를 API로 제공 [제안] |
| 8 | 서버 업데이트 때마다 플러그인이 부분 로드·오류·메모리 누수를 일으키고, 자동 업데이트가 서버보다 먼저 일어나기도 한다 | **[검증]** (가이드) | [Falix 가이드](https://client.falixnodes.net/guides/minecraft-plugins/plugin-updates) | 안정 ABI 계약 + 플러그인 선언형 `apiVersion` 체크 [제안] |
| 9 | 유지되지 않는 플러그인에 서버 운영자가 묶인다 | **[검증]** (오래된 글, 패턴만) | [포럼](https://www.techpowerup.com/forums/threads/tpu-minecraft-server.139751/post-2692309) | 호환성 검사기를 서버에 내장 (CompatGuard 같은 외부 도구가 존재한다는 점이 수요를 보여줌) [제안] |
| 10 | Spigot/Bukkit 호환을 위해 낡은 API 형태가 계속 남는다 | **[일반지식]** | - | Furnace는 Bukkit 호환 비목표 → 깨끗한 API 가능. 대신 기존 플러그인은 재작성 필요 |

### 1.2 하드코딩 / 닫힌 시스템

| # | 불만 | 신뢰도 | 근거 | Furnace의 대응 방향 |
|---|---|---|---|---|
| 11 | 난이도는 4개 고정 enum, 월드별 설정. 낮은 난이도를 만들려면 하드로 두고 데미지·허기를 깎는 우회 | **[검증]** | [javadoc](https://jd.papermc.io/paper/1.21.1/org/bukkit/Difficulty.html), [Aternos](https://support.aternos.org/hc/en-us/articles/360047224532-Setting-the-difficulty-for-Spigot-PaperMC-servers), [DifficultyManagement](https://hangar.papermc.io/Otsel/DifficultyManagement) | 난이도를 "프로파일 객체"로 일급화. 클라 UI는 4칸 한계라 표시만 대체 |
| 12 | 커스텀 게임룰 API가 없어 `/gamerule` 명령에 코드를 주입해 우회 | **[검증]** | [MoreRules](https://hangar.papermc.io/GamerDuck123/MoreRules) | 타입 있는 게임룰 레지스트리 |
| 13 | 배너 패턴 같은 하드코딩 enum에 커스텀을 추가하면 깨진다. 아이템 데이터에 하드 한계(예: lore 256줄) | **[검증]** | [PaperMC 포럼](https://forums.papermc.io/) | 레지스트리 기반, enum 금지 |
| 14 | 커스텀 레지스트리 API가 없어 플러그인끼리 데이터를 공유하려면 각자 정적 맵을 둔다 | **[검증]** | [Issue #12383](https://github.com/PaperMC/Paper/issues/12383) (열림, triage 대기) | 플러그인 정의 레지스트리를 1급으로 |
| 15 | 클래스로더 누수 우려 때문에 커스텀 레지스트리 도입이 어렵다는 반응 | **[검증]** | 같은 이슈 (lynxplay 코멘트) | 레지스트리 항목에 소유 플러그인 태그 + 비활성화 시 자동 정리 |
| 16 | 커스텀 인챈트는 한동안 API로 불가능했고 지금은 experimental 레지스트리 API로 이동 중 | **[검증]** | [포럼 답변](https://forums.papermc.io/threads/custom-enchantments.1191/) | 데이터 레지스트리 등록 API |
| 17 | 커스텀 블록은 안 쓰는 블록 상태(노트블록·버섯·트립와이어)를 리소스팩으로 재텍스처하거나 프록시 블록 + 디스플레이 엔티티로 우회한다 | **[검증]** | [repack](https://github.com/RATR2/repack), [가이드](https://guide.astroworldmc.com/how-to-add-custom-blocks-minecraft) | 서버 논리 블록과 클라 표현 블록 분리 (네트워크 계층 변환) |
| 18 | 서버만으로 월드 높이를 바꿀 수 없다 (클라가 바닐라 dimension은 자기 복사본을 씀) | **[검증]** | [Higher Heights](https://modrinth.com/project/higher-heights-datapack) | 새 키의 dimension_type 전송으로 가능한지 검증 필요 |

### 1.3 명령어

| # | 불만 | 신뢰도 | 근거 | Furnace의 대응 방향 |
|---|---|---|---|---|
| 19 | 명령 API가 experimental이라 바뀔 수 있다 | **[검증]** | [Paper 명령 문서](https://docs.papermc.io/paper/dev/commands) | 명령 시스템을 코어에서 처음부터 설계, 바닐라 명령도 같은 레지스트리 |
| 20 | 플러그인 별칭이 바닐라 명령을 덮어쓰는 동작을 별도 커밋으로 고쳐야 했다 | **[검증]** | [커밋 #11186](https://ayakael.net/mirrors/papermc/commit/67d414a9274232ad68deb3d9b08bf1f7bc699108) | 바닐라/플러그인 구분 없는 단일 트리 |
| 21 | 클라이언트에 보내는 노드를 플러그인이 덮어쓸 수 없어 WrapperArgumentType으로 우회 | **[검증]** | [PR #8235](https://github.com/PaperMC/Paper/pull/8235) | 플레이어별 명령 트리 직접 구성 |
| 22 | Brigadier로 등록한 명령이 Bukkit 명령맵용 래퍼로 감싸져 권한 검사가 불안정하다는 보고 | **[검증]** (3차 출처) | [DEV 글](https://dev.to/eande171/youre-writing-paper-commands-wrong-2o8i) | 래퍼 계층 자체를 만들지 않음 |

### 1.4 이벤트·라이프사이클

| # | 불만 | 신뢰도 | 근거 | Furnace의 대응 방향 |
|---|---|---|---|---|
| 23 | `PlayerLoginEvent`가 deprecated 예정인데, LuckPerms는 대체 이벤트로는 Player 객체 생성 즉시 권한 데이터를 주입할 수 없다고 한다. 대체 API(PR #13201)는 병합 없이 닫혔고 이슈는 열려 있음 | **[검증]** | [LuckPerms #4121](https://github.com/LuckPerms/LuckPerms/issues/4121) | 연결 상태 머신(핸드셰이크→로그인→구성→플레이)마다 명시적 훅 단계. 권한 주입 전용 단계 [제안] |
| 24 | 폐기 경고가 콘솔에 반복 출력되어 플러그인 작성자가 어쩔 수 없다는 불만 | **[검증]** | 같은 이슈 | 경고 레벨을 플러그인이 제어 가능, 대체 API를 먼저 제공 후 폐기 [제안] |
| 25 | `PlayerJoinEvent` 안에서 텔레포트하면 예상 밖 동작 | **[검증]** (제목만 확인) | [Issue #7361](https://github.com/PaperMC/Paper/issues/7361) | 접속 단계별 이벤트(configuration/pre-spawn/post-spawn) 분리 |
| 26 | `PlayerJumpEvent`는 Paper 전용이라 Spigot에서 쓸 수 없다 (서버 소프트웨어별 API 차이) | **[검증]** | [Spigot 스레드](https://www.spigotmc.org/threads/playerjumpevent-not-found.469072/) | 단일 서버, 단일 API |
| 27 | "Could not pass event PlayerJoinEvent" 류 오류가 플러그인 이슈 트래커에 반복 등장 | **[검증]** (존재만) | [ItemsAdder #4687](https://github.com/PluginBugs/Issues-ItemsAdder/issues/4687) | 이벤트 핸들러 예외 격리 + 플러그인별 오류 리포트 [제안] |

### 1.5 스레딩·성능

| # | 불만 | 신뢰도 | 근거 | Furnace의 대응 방향 |
|---|---|---|---|---|
| 28 | Folia: 사실상 모든 플러그인이 수정 필요, `folia-supported` 선언 필요 | **[검증]** | [Folia 위키](https://github.com/PaperMC/Folia/wiki/Plugin-Compatibility) | 처음부터 region 스레딩 전제의 API |
| 29 | Folia: Bukkit 스케줄러, `Entity#teleport`, 스코어보드, 포탈, 월드 로드/언로드 등이 깨짐 | **[검증]** | 같은 위키 | 모든 API가 비동기 시그니처 |
| 30 | Folia: 비동기 이벤트가 아직 없고, 교차 region 접근은 데이터 손상 위험 | **[검증]** | 같은 위키 | 소유권 가드 + 이벤트 스레드 계약 |
| 31 | 청크 로드를 동기 `teleport`로 하면 메인 스레드가 막힌다 (그래서 `teleportAsync`) | **[검증]** | [Paper 문서](https://docs.papermc.io/paper/1.20/dev/entity-teleport) | 처음부터 전부 비동기 |
| 32 | 빌리저 많은 서버, 몹 파밍 부하 문의가 반복된다 | **[검증]** (포럼 문의만) | [포럼](https://forums.papermc.io/threads/17-18-player-tps-problem.571/) | region 병렬 틱 |
| 33 | 레드스톤·호퍼·몹 AI가 메인 스레드 하나에 묶인다 | **[일반지식]** | - | 설계 목표 (M3 완료, 게임플레이는 미구현) |

### 1.6 설정·운영

| # | 불만 | 신뢰도 | 근거 | Furnace의 대응 방향 |
|---|---|---|---|---|
| 34 | 설정이 `server.properties`/`bukkit.yml`/`spigot.yml`/Paper 설정으로 여러 겹이라 우선순위가 헷갈린다 | **[검증]** (구조만; 불만 자체는 추정) | [Paper 설정 문서](https://paper.readthedocs.io/en/latest/server/configuration.html) | 단일 스키마 설정 + `effective-config` 덤프 명령 [제안] |
| 35 | 설정 키가 버전 간에 이동·개명된다 | **[검증]** (가이드) | [XGaming 가이드](https://xgamingserver.com/blog/how-to-configure-your-minecraft-server-paper-global-yml/) | 설정 스키마 버전 + 자동 마이그레이션 [제안] |
| 36 | 몹 행동 등 설정성을 위해 Purpur 같은 별도 포크를 쓴다 → 소프트웨어 파편화 | **[검증]** (2차) | [비교글](https://space-node.net/blog/paper-vs-purpur-minecraft-server-2026) | 몹 행동을 데이터/플러그인 정책으로 열기 |

### 1.7 생태계 구조적 불만 (일반지식, 이번 조사에서 출처 못 찾음)

아래는 마인크래프트 서버 개발·운영에서 흔히 알려진 문제들이지만 이번 조사에서 직접 출처를 확인하지 못했다. 방향 참고용으로만 쓴다.

| # | 불만 | 신뢰도 | Furnace 대응 제안 |
|---|---|---|---|
| 37 | 권한·경제·채팅 포맷 등 공용 API가 표준화되어 있지 않아 Vault, PlaceholderAPI, ProtocolLib 같은 중간 의존성이 필수 | **[일반지식]** | 권한·경제·플레이스홀더를 코어 서비스 인터페이스로 [제안] |
| 38 | 패킷 조작을 하려면 ProtocolLib 또는 NMS가 필요 | **[일반지식]** | 패킷 이벤트 공식 API |
| 39 | 플레이어별 가짜 블록/엔티티/스코어보드가 표준 API가 아님 | **[일반지식]** | 플레이어 뷰 API |
| 40 | NPC·홀로그램이 표준 API가 아니라 플러그인마다 구현이 다름 | **[일반지식]** | 코어에 경량 가상 엔티티 |
| 41 | `/reload`로 플러그인을 다시 읽으면 상태가 꼬임 | **[일반지식]** | 격리된 핫 리로드를 정식 지원하거나 명시적으로 금지 |
| 42 | 인벤토리 GUI 구현이 이벤트 취소 꼼수에 의존 | **[일반지식]** | 서버 구동 컨테이너를 1급으로 |
| 43 | 플러그인 간 로딩 순서·의존성 지옥 | **[일반지식]** | 의존성 그래프 + 서비스 레지스트리 |
| 44 | 서버 크래시 시 어느 플러그인이 원인인지 찾기 어렵다 | **[일반지식]** | 플러그인별 예외 귀속 + 틱 시간 귀속 |
| 45 | 한 플러그인의 느린 코드가 전체 틱을 막는다 | **[일반지식]** | 플러그인별 틱 예산·워치독 |
| 46 | 보안: 플러그인이 임의 코드/파일/네트워크 접근 가능 | **[일반지식]** | 권한 선언 매니페스트 (한계 있음, 아래 4.15) |
| 47 | 월드/청크 단위 영속 데이터 API가 제한적 | **[일반지식]** | 청크/월드/엔티티/아이템 공용 데이터 컨테이너 |
| 48 | 테스트하기 어렵다 (목 서버 필요) | **[일반지식]** | 헤드리스 테스트 서버 + 결정적 틱 |

### 1.8 추가 조사: 수요 신호와 일반지식 불만

**수요 신호 (존재 자체가 불만의 증거)**

| # | 신호 | 신뢰도 | 근거 | Furnace의 대응 방향 |
|---|---|---|---|---|
| 49 | 바닐라 입력·UI로 안 되는 것을 위해 **서버 플러그인 + Fabric 클라이언트 모드 쌍**이 이미 쓰이고 있다 (Keybind: 서버가 보낸 액션 목록을 클라 키로 바인딩 / InputEngine: 키 입력을 서버 커스텀 이벤트로 전달 / KrepAPI: 서버가 추가 키바인드 정의) | **[검증]** (커뮤니티 페이지, 2차) | [Keybind](https://modrinth.com/mod/keybind), [InputEngine](https://modrinth.com/project/cd7YQRjU), [KrepAPI](https://moddex.gg/mod/krepapi) | 동반 모드 규격을 공식화 (4B절) |
| 50 | 권한·채팅·경제를 하나의 인터페이스로 묶는 Vault가 사실상 필수 의존성이다. 원격 조회를 위해 ServerTap(REST API) 같은 플러그인이 따로 쓰인다 | **[검증]** (2차) | [검색 결과 요약](https://servertap.io/), [ServerTap](https://github.com/servertap-io/servertap) | 권한·경제 서비스 인터페이스와 원격 관리 API를 코어에 (4.13, 4.20) |
| 51 | 커스텀 아이템 외형 방식이 버전별로 갈린다: 1.21.2 이하는 `custom_model_data`(정수), 1.21.3+는 `item_model`(키). `equippable` 컴포넌트도 1.21.2+ | **[검증]** (플러그인 문서, 2차) | [CuriosPaper](https://curiospaper.run.place/resource-pack/custom-model-data/), [NexoMC FAQ](https://docs.nexomc.com/general-usage/faq/itemmodels-vs.-custommodeldata), [Oraxen](https://docs.oraxen.com/creating-content/items/components) | **Furnace는 1.21.1 고정 → `custom_model_data` 정수만 쓸 수 있다.** 4.32의 제약 참고 |
| 52 | 일부 아이템 기능(`equip_sound` 등)이 "Paper 서버 필요"로 문서화되어 서버 소프트웨어 간 API 차이가 드러난다 | **[검증]** (2차) | 같은 Oraxen 문서 | 단일 서버·단일 API로 해소 |

**일반지식 불만 (이번 조사에서 직접 출처 못 찾음 — 방향 참고용)**

| # | 불만 | 신뢰도 | Furnace 대응 제안 |
|---|---|---|---|
| 53 | 이벤트를 취소하면 클라이언트 상태가 서버와 어긋나는데(고스트 블록·아이템), 재동기화 책임이 플러그인에 있다 | **[일반지식]** | 취소 시 코어가 자동 재동기화 |
| 54 | 우선순위(HIGHEST/MONITOR) 오용으로 취소 상태를 서로 덮어쓴다 | **[일반지식]** | 결과 합성 규칙을 이벤트별로 강제, MONITOR는 읽기 전용 |
| 55 | 블록·엔티티를 **누가 왜 바꿨는지**(원인) 알 수 없어 로깅·롤백 플러그인이 이벤트를 짜맞춰 추측한다 | **[일반지식]** | 모든 변경에 Cause 부착 (4.16) |
| 56 | 보호 플러그인(WorldGuard류)과 다른 플러그인 사이에 "여기서 해도 되나?"를 묻는 표준 질의가 없다 | **[일반지식]** | `ProtectionCheck` 공용 질의 (3.14, 4.18) |
| 57 | 서버 쪽에 플레이어 이동 물리가 불완전해서 안티치트 오탐이 많다 | **[일반지식]** | 서버 물리 모델 + 예측 이동 질의 API (4.22). 현재 Furnace는 충돌·물리 모델이 없음 |
| 58 | 강제 청크 로딩·티켓을 세밀하게 제어하기 어렵다 | **[일반지식]** | 소유 플러그인이 있는 티켓 API (4.10) |
| 59 | 멀티서버 간 데이터 동기화·세션 이전에 표준이 없다 | **[일반지식]** | 쿠키/Transfer + 네트워크 코어 (4.21) |
| 60 | 플러그인 설치·업데이트·의존성 해결을 해주는 패키지 매니저가 없다 | **[일반지식]** | 매니페스트 기반 의존성 해결 + 저장소 연동 (4.30) |
| 61 | YAML 들여쓰기 실수 같은 설정 오류에 메시지가 불친절하다 | **[일반지식]** | 스키마 검증 + 줄 번호 오류 (4.14) |
| 62 | 로컬라이제이션을 플러그인마다 제각각 구현한다 | **[일반지식]** | 코어 번역 서비스 (4.12) |
| 63 | 콘솔 로그가 비구조적이고 노이즈가 많다 | **[일반지식]** | 구조화 로깅, 플러그인별 레벨 (4.15) |
| 64 | 데이터팩과 플러그인의 경계가 모호하다 (함수 호출, 명령 연동) | **[일반지식]** | 데이터팩 통합 API (4.23) |
| 65 | 맵·책·표지판 같은 바닐라 UI를 활용할 API가 빈약하다 | **[일반지식]** | 4.26 |
| 66 | 닉네임·스킨 변경이 프로필 서명 때문에 제한적이다 (서명된 텍스처가 필요) | **[일반지식]** | 프로필 API + 서명 서비스 위임 (4.27) |
| 67 | 서버 재시작 없이 월드·설정을 바꾸기 어렵다 | **[일반지식]** | 핫 설정, 월드 핫 로드 (4.14) |


---

## 2. 클라이언트 모드가 필요해서 제외하는 것

서버만 고쳐서는 되지 않는다. 3, 4절에는 포함하지 않았고, **동반 모드를 깔았을 때 가능한 것은 4B절에 따로 모았다.**

- 난이도 선택 UI에 5번째 값을 보여주기 (프로토콜이 4값. **[일반지식]**)
- 클라이언트가 모르는 새 명령 인자 파서 타입
- 새 키바인드, 새 렌더 패스, 클라이언트 측 새 GUI 레이아웃
- 클라이언트 물리 예측(이동·충돌) 변경
- 리소스팩으로도 못 만드는 새 블록 모델 형태
- 월드 높이 4064블록 초과 (데이터팩 기준 한계, [Higher Heights](https://modrinth.com/project/higher-heights-datapack))

**리소스팩이 필요한 것 (모드는 아님)**: 커스텀 블록/아이템 외형, 커스텀 사운드, 커스텀 GUI 텍스처, 폰트 기반 UI.

---

## 3. 커스텀 이벤트 카탈로그 **[제안]**

### 3.0 이벤트 시스템 설계 원칙

| 속성 표기 | 의미 |
|---|---|
| **C** | 취소 가능 (cancellable) |
| **M** | 결과 수정 가능 (값을 바꿔서 영향을 줄 수 있음) |
| **R** | Region 스레드에서 발화 (해당 위치 소유 스레드) |
| **E** | Entity 스레드에서 발화 (엔티티를 따라다님) |
| **G** | Global 스레드에서 발화 |
| **N** | Netty/IO 스레드에서 발화 (월드 접근 금지, 즉시 반환) |
| **A** | 비동기 핸들러 허용 (Future를 반환해 결과를 늦게 줄 수 있음) |

원칙
1. 이벤트마다 **발화 스레드를 계약으로 문서화**한다. (Folia의 불만: 비동기 이벤트 부재와 교차 region 접근 위험 → 1.5)
2. 핸들러 예외는 **플러그인 단위로 격리**하고 오류를 귀속한다.
3. 우선순위(LOWEST~MONITOR)와 `ignoreCancelled`는 유지하되 **결과 합성 규칙**(마지막이 이김/최댓값/합)을 이벤트별로 정의한다.
4. 대체 API가 먼저 나오고 나서 폐기한다. (1.4 #23, #24)
5. 아래 이름은 가칭이다. Bukkit/Paper와 동등한 이벤트가 이미 있는지는 항목별로 확인하지 않았다.

### 3.0b 추가 설계 원칙: 결과 타입과 원인(Cause)

- **결과 타입**: 이벤트 핸들러가 `PASS`(다음 핸들러로) / `SUCCESS`(처리 완료, 이후 핸들러 중단) / `FAIL`(거부) 중 하나를 반환하는 방식. Fabric의 콜백이 이 패턴이다. 예: AttackBlockCallback의 예제가 `PASS`를 반환해 다른 콜백이 계속 실행되게 한다. **[검증]** ([Fabric 위키](https://wiki.fabricmc.net/tutorial:callbacks)) 취소 불리언보다 의도가 명확하다. **[제안]**
- **Cause**: 모든 상태 변경 이벤트는 "누가 이 변경을 일으켰나"(플레이어 → 아이템 → 블록 → 플러그인 ...)의 체인을 담는다. 4.16 참고. **[제안]**
- **자동 재동기화**: 클라이언트가 예측으로 바꾼 상태를 서버가 취소하면 코어가 해당 블록/슬롯/위치를 자동으로 되돌려 보낸다. 1.8 #53. **[제안]**
- **MONITOR 단계 읽기 전용**: 이 단계에서는 결과 수정 불가. 1.8 #54. **[제안]**

### 3.1 서버 수명주기

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `ServerBootstrapEvent` | 레지스트리 동결 전 등록 단계 (커스텀 블록/인챈트/바이옴 등록) | G |
| `RegistryFreezeEvent` | 레지스트리 동결 직전 마지막 수정 기회 | G |
| `ServerStartingEvent` / `ServerStartedEvent` | 기동 단계 알림 | G |
| `ServerTickStartEvent` / `ServerTickEndEvent` | 글로벌 틱 경계 | G |
| `RegionTickStartEvent` / `RegionTickEndEvent` | region 틱 경계 (프로파일링·예산) | R |
| `ServerStoppingEvent` | 종료 시작 (플러그인 정리) | G |
| `ServerSaveEvent` | 전체 저장 시작/끝 | G |
| `ServerWatchdogEvent` | 틱 지연 감지 (원인 region/플러그인 정보 포함) | G |
| `ConfigReloadEvent` | 설정 재로드 (변경 키 diff 포함) | G M |
| `PluginEnableEvent` / `PluginDisableEvent` / `PluginReloadEvent` | 플러그인 수명주기 | G |

### 3.2 연결·로그인 (상태 머신 단계별)

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `ConnectionOpenEvent` | TCP 연결 수립 직후. IP 차단·속도 제한 | C N |
| `HandshakeEvent` | 핸드셰이크 수신 (프로토콜 버전, 호스트명, next-state) | C N |
| `StatusRequestEvent` | 서버 목록 핑 응답 (MOTD, 인원, 샘플, 파비콘 변경) | M N |
| `LoginStartEvent` | 이름·UUID 수신. 검증·화이트리스트·밴 | C N A |
| `LoginAuthEvent` | 인증 결과 (온라인 모드/포워딩) 처리. **권한 주입 전용 단계** (1.4 #23 대응) | C M N A |
| `LoginPluginMessageEvent` | Login Plugin Request/Response (프록시 포워딩 등) | N A |
| `ConfigurationStartEvent` | 구성 단계 진입 | N A |
| `ConfigurationRegistrySyncEvent` | 클라이언트에 보낼 데이터 레지스트리 목록 수정 | M N |
| `ConfigurationTagsEvent` | 태그 패킷 내용 수정 | M N |
| `ConfigurationResourcePackEvent` | 리소스팩 푸시 (복수 팩, 강제 여부) | M N A |
| `ConfigurationCookieEvent` | 쿠키 읽기/쓰기 | N A |
| `ConfigurationFinishEvent` | 구성 완료, 플레이 전환 직전 | C N A |
| `PlayerSpawnLocationEvent` | 최초 스폰 위치/월드 결정 (월드·region 선택) | M A |
| `PlayerJoinEvent` | 플레이 상태 진입 후. 이 시점부터 월드 API 사용 가능 | E |
| `PlayerFirstJoinEvent` | 최초 접속 | E |
| `PlayerQuitEvent` | 연결 종료. 사유(타임아웃/킥/자진) 포함 | E |
| `PlayerKickEvent` | 서버가 끊을 때 사유 문구 수정 | C M E |
| `PlayerTransferEvent` | 다른 서버로 Transfer 패킷 전송 | C M E |
| `PlayerReconfigureEvent` | 플레이 중 구성 단계로 되돌림 (리소스팩 교체 등) | C E |
| `PlayerClientInfoEvent` | 클라 설정(언어, 시야거리, 스킨 레이어, 채팅 설정) 수신/변경 | E |
| `PlayerClientBrandEvent` | 클라이언트 브랜드 수신 | N |
| `PlayerKeepAliveTimeoutEvent` | 킵얼라이브 타임아웃 | C N |
| `PlayerLatencyChangeEvent` | 핑 임계 초과 | E |

### 3.3 플레이어 입력·행동

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `PlayerMoveEvent` | 위치·시선 변화 (임계값 설정 가능, 매 패킷 발화 방지) | C M E |
| `PlayerMovePacketEvent` | 원본 이동 패킷 (안티치트용, 변환 전) | C N |
| `PlayerJumpEvent` | 점프 | E |
| `PlayerSneakToggleEvent` / `PlayerSprintToggleEvent` | 웅크리기/달리기 토글 | C E |
| `PlayerInputEvent` | 방향키·점프·웅크리기 입력 상태 변화 | E |
| `PlayerSwimToggleEvent` / `PlayerGlideToggleEvent` / `PlayerCrawlToggleEvent` | 자세 변화 | C E |
| `PlayerFallEvent` | 낙하 착지 (거리, 피해량 수정) | C M E |
| `PlayerTeleportEvent` | 이동 사유(명령/포탈/펄/플러그인) 포함 | C M E A |
| `PlayerInteractBlockEvent` | 블록 우/좌클릭 (손, 면, 히트벡터 포함) | C E |
| `PlayerInteractEntityEvent` | 엔티티 상호작용 | C E |
| `PlayerInteractAirEvent` | 허공 클릭 | C E |
| `PlayerSwingArmEvent` | 팔 휘두르기 (공격 아닌 경우 포함) | C E |
| `PlayerUseItemStartEvent` / `PlayerUseItemTickEvent` / `PlayerUseItemStopEvent` | 아이템 사용 진행 (활 당기기, 먹기, 방패) | C E |
| `PlayerItemConsumeEvent` | 소비 완료 (효과 수정) | C M E |
| `PlayerItemHeldEvent` | 핫바 슬롯 변경 | C E |
| `PlayerSwapHandsEvent` | F키 | C E |
| `PlayerDropItemEvent` | 아이템 버리기 (Q, 컨테이너 밖 클릭) | C E |
| `PlayerPickupItemAttemptEvent` | 줍기 시도 (아이템 엔티티 병합 전) | C E |
| `PlayerPickupItemEvent` | 줍기 성공 | E |
| `PlayerPickupXpEvent` | 경험치 줍기 (양 수정) | C M E |
| `PlayerBedEnterEvent` / `PlayerBedLeaveEvent` | 침대 | C E |
| `PlayerRespawnEvent` | 리스폰 위치 수정 | M E |
| `PlayerDeathEvent` | 사망 (메시지, 드롭, 경험치 유지 여부 수정) | M E |
| `PlayerGameModeChangeEvent` | 게임모드 변경 | C M E |
| `PlayerAbilitiesChangeEvent` | 비행 가능/비행 중 변경 | C E |
| `PlayerFlightToggleEvent` | 비행 토글 | C E |
| `PlayerCommandSuggestEvent` | 자동완성 요청 수신 | M E |
| `PlayerChatTabCompleteEvent` | 채팅 탭 완성 | M E |
| `PlayerAdvancementEvent` | 발전 과제 달성/취소 | C E |
| `PlayerStatisticChangeEvent` | 통계 증가 | C M E |
| `PlayerRecipeDiscoverEvent` | 레시피 해금 | C E |
| `PlayerBookEditEvent` / `PlayerSignEditEvent` | 책/표지판 편집 | C M E |
| `PlayerResourcePackStatusEvent` | 리소스팩 수락/거부/실패 | E |
| `PlayerSpectateEntityEvent` | 관전 대상 시작/종료 | C E |
| `PlayerPingSampleEvent` | 디버그 샘플 구독 | E |

### 3.4 채팅·명령

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `ChatPreprocessEvent` | 서명 검증 전. 길이·속도 제한 | C N |
| `PlayerChatEvent` | 채팅 (수신자 목록, 포맷터, 렌더러 수정) | C M E A |
| `ChatSignatureEvent` | 채팅 서명 정책 (서명 요구/무시/보고) | M |
| `SystemMessageSendEvent` | 시스템 메시지 전송 직전 (필터, 번역) | C M |
| `ActionBarSendEvent` / `TitleSendEvent` | 오버레이 메시지 전송 | C M |
| `CommandRegisterEvent` | 명령 등록 시점 (바닐라 포함, 교체·래핑) | M G |
| `CommandTreeSendEvent` | 플레이어에게 보낼 명령 트리 수정 | M E |
| `CommandPreExecuteEvent` | 파싱 결과 확인 후 실행 전 | C M E A |
| `CommandPostExecuteEvent` | 실행 후 (결과 코드, 소요 시간) | E |
| `CommandSuggestEvent` | 제안 목록 수정 | M E A |
| `CommandFeedbackEvent` | 명령 피드백 메시지 가로채기 (op 브로드캐스트 포함) | C M |
| `ConsoleCommandEvent` | 콘솔 입력 | C M G |
| `RconCommandEvent` | RCON 입력 | C M G |

### 3.5 블록

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `BlockBreakEvent` | 플레이어 파괴 (드롭, 경험치 수정) | C M R |
| `BlockBreakProgressEvent` | 채굴 진행 (속도 수정, 서버 논리 블록용) | C M R |
| `BlockDamageStartEvent` / `BlockDamageAbortEvent` | 채굴 시작/중단 | C R |
| `BlockPlaceEvent` | 설치 (대체된 블록 정보 포함) | C M R |
| `BlockMultiPlaceEvent` | 문·침대·상자 등 다중 블록 설치 | C R |
| `BlockDropItemEvent` | 드롭 생성 직전 | C M R |
| `BlockPhysicsEvent` | 이웃 업데이트 | C R |
| `BlockNeighborChangeEvent` | 이웃 변경 알림 (원인 방향) | R |
| `BlockScheduledTickEvent` | 예약 틱 | C R |
| `BlockRandomTickEvent` | 랜덤 틱 (성장 속도 조절) | C R |
| `BlockGrowEvent` / `BlockSpreadEvent` / `BlockFadeEvent` | 작물·잔디·눈 변화 | C R |
| `BlockBurnEvent` / `BlockIgniteEvent` | 불 | C R |
| `BlockFromToEvent` | 유체 흐름 | C R |
| `BlockFluidLevelChangeEvent` | 유체 레벨 변화 | C R |
| `BlockExplodeEvent` | 블록 폭발 (영향 블록 목록 수정) | C M R |
| `BlockPistonExtendEvent` / `BlockPistonRetractEvent` | 피스톤 | C M R |
| `BlockRedstoneChangeEvent` | 레드스톤 신호 변화 | C M R |
| `BlockRedstoneTickBudgetEvent` | 레드스톤 업데이트 예산 초과 | R |
| `BlockStateChangeEvent` | 모든 블록 상태 변경의 공통 후크 (`setBlock` 경로 전체) | R |
| `BlockEntityLoadEvent` / `BlockEntityUnloadEvent` | 블록 엔티티 로드/언로드 | R |
| `BlockEntityTickEvent` | 블록 엔티티 틱 | C R |
| `ContainerOpenEvent` / `ContainerCloseEvent` | 컨테이너 | C R E |
| `HopperTransferEvent` | 호퍼 이동 (아이템 수정) | C M R |
| `DispenserFireEvent` | 발사기/공급기 | C M R |
| `CauldronChangeEvent` | 가마솥 | C R |
| `NoteBlockPlayEvent` | 노트 블록 | C M R |
| `SignChangeEvent` | 표지판 | C M R |
| `LeavesDecayEvent` | 잎 소멸 | C R |
| `CustomBlockBreakEvent` / `CustomBlockInteractEvent` | 서버 논리 블록 (4.5) | C R |

### 3.6 엔티티

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `EntitySpawnEvent` | 모든 스폰 (사유: 자연/스포너/알/명령/플러그인 포함) | C M R |
| `EntityRemoveEvent` | 제거 (사유 포함) | R |
| `EntityTickEvent` | 엔티티 틱 (비활성화·감속) | C E |
| `EntityMoveEvent` | 몹 이동 | C E |
| `EntityDamageEvent` | 피해 (원인, 최종 데미지 계산 단계 노출) | C M E |
| `EntityDamageCalculateEvent` | 데미지 공식 각 단계(방어력, 인챈트, 저항) 개입 — **구현됨(2026-11-06, `docs/api/entity-world-design.md` 4.1)** | M E |
| `EntityKnockbackEvent` | 넉백 벡터 수정 | C M E |
| `EntityDeathEvent` | 사망 (드롭, 경험치 수정) | M E |
| `EntityTargetEvent` | 몹이 타깃 선택 | C M E |
| `EntityPathfindEvent` | 경로 탐색 시작/완료 | C E |
| `EntityGoalSelectEvent` | AI 목표 후보 선정 (커스텀 AI 주입) | C M E |
| `EntityAttackEvent` | 근접/원거리 공격 시도 | C E |
| `EntityShootBowEvent` / `ProjectileLaunchEvent` / `ProjectileHitEvent` | 투사체 | C M E |
| `EntityMountEvent` / `EntityDismountEvent` | 탑승 | C E |
| `EntityTeleportEvent` | 엔티티 텔레포트 (포탈, 펄, 플러그인) | C M E A |
| `EntityRegionTransferEvent` | **region 경계 이동** (소유 스레드 변경 알림) | E |
| `EntityPortalEnterEvent` / `EntityPortalExitEvent` | 포탈 | C M E |
| `EntityExplodeEvent` | 엔티티 폭발 | C M E |
| `EntityEffectApplyEvent` / `EntityEffectRemoveEvent` | 포션 효과 | C M E |
| `EntityBreedEvent` / `EntityTameEvent` | 번식·길들이기 | C M E |
| `EntityTransformEvent` | 변환 (좀비→익사자 등) | C E |
| `EntityEquipEvent` | 장비 변경 | C E |
| `EntityMetadataSendEvent` | 클라에 보내는 메타데이터 수정 (플레이어별 외형) | M N |
| `EntityTrackEvent` / `EntityUntrackEvent` | 플레이어에게 보이기 시작/끝 (뷰 거리 기반) | C E |
| `EntityLoadEvent` / `EntityUnloadEvent` | 청크와 함께 로드/언로드 | R |
| `ItemEntityMergeEvent` | 아이템 엔티티 병합 | C R |
| `ItemEntityDespawnEvent` | 아이템 소멸 | C R |
| `ExperienceOrbMergeEvent` | 경험치 구슬 병합 | C R |
| `VillagerTradeEvent` | 거래 실행/목록 수정 | C M E |
| `VillagerProfessionChangeEvent` | 직업 변경 | C E |
| `RaidStartEvent` / `RaidWaveEvent` / `RaidEndEvent` | 습격 | C R |
| `MobSpawnCapEvent` | 스폰 상한 계산 (플레이어별 상한 구현) | M R |
| `MobSpawnAttemptEvent` | 스폰 시도 단계 (위치 후보, 팩 크기) | C M R |
| `CustomEntityTickEvent` | 서버 논리 엔티티 (4.6) | C E |

### 3.7 아이템·인벤토리

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `InventoryClickEvent` | 클릭 (클릭 종류, 슬롯, 커서, 변경 슬롯 목록 전부) | C M E |
| `InventoryDragEvent` | 드래그 분배 | C M E |
| `InventoryMoveItemEvent` | 호퍼 등 자동 이동 | C M R |
| `InventoryOpenEvent` / `InventoryCloseEvent` | 열기/닫기 (닫기 사유) | C E |
| `InventoryResyncEvent` | 클라 재동기화 (stateId 불일치) | E |
| `CraftEvent` / `PrepareCraftEvent` | 제작 결과 미리보기/실행 | C M E |
| `CraftingGridChangeEvent` | 격자 변경 | E |
| `SmeltEvent` / `FurnaceBurnEvent` / `FurnaceProgressEvent` | 화로 (속도 수정) | C M R |
| `BrewEvent` / `BrewingProgressEvent` | 양조 | C M R |
| `EnchantPrepareEvent` / `EnchantApplyEvent` | 마법 부여대 (옵션 목록 수정) | C M E |
| `AnvilPrepareEvent` / `AnvilTakeEvent` | 모루 (비용, 결과 수정) | C M E |
| `GrindstonePrepareEvent` | 숫돌 | C M E |
| `SmithingPrepareEvent` | 대장장이 작업대 | C M E |
| `StonecutterSelectEvent` | 석재 절단기 | C E |
| `LoomPatternEvent` | 베틀 | C M E |
| `TradeSelectEvent` | 거래 선택 | C E |
| `ItemDamageEvent` | 내구도 소모 (양 수정) | C M E |
| `ItemBreakEvent` | 아이템 파괴 | E |
| `ItemMendEvent` | 수선 | C M E |
| `ItemComponentChangeEvent` | 컴포넌트 변경 (any set 경로) | C M E |
| `ItemRepairCostEvent` | 모루 비용 | M E |
| `ItemEnchantLimitEvent` | 인챈트 레벨/조합 상한 | M E |
| `CreativeSlotSetEvent` | 크리에이티브 슬롯 설정 (아이템 검증) | C M E |

### 3.8 월드·청크

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `WorldLoadEvent` / `WorldUnloadEvent` | 월드 | G A |
| `WorldCreateEvent` | 새 월드 생성 직전 (설정 수정) | M G |
| `WorldSaveEvent` | 저장 | G A |
| `ChunkRequestEvent` | 청크 요청 (생성/로드 우선순위 수정) | M R |
| `ChunkLoadEvent` / `ChunkUnloadEvent` | 청크 | R |
| `ChunkGenerateStartEvent` / `ChunkGenerateEndEvent` | 생성 단계 완료 | R A |
| `ChunkStatusChangeEvent` | status 변화 (empty→…→full) | R |
| `ChunkSendEvent` / `ChunkUnsendEvent` | 클라에 청크 전송/제거 | C E |
| `ChunkSerializeEvent` | 직렬화 직전 (플레이어별 가짜 블록 주입) | M N |
| `ChunkSaveEvent` | 저장 | R A |
| `ChunkTicketAddEvent` / `ChunkTicketRemoveEvent` | 로딩 티켓 | C G |
| `RegionCreateEvent` / `RegionDestroyEvent` | region 생성/소멸 (스레드 배정 포함) | G |
| `RegionMergeEvent` / `RegionSplitEvent` | region 병합·분할 | G |
| `RegionLoadBalanceEvent` | 스레드 재배정 | G |
| `TimeSkipEvent` | 시간 건너뜀 (수면 등) | C M R |
| `WeatherChangeEvent` | 날씨 | C M R |
| `ThunderChangeEvent` / `LightningStrikeEvent` | 번개 | C R |
| `WorldBorderChangeEvent` / `WorldBorderReachEvent` | 월드 보더 | C M R |
| `GameRuleChangeEvent` | 게임룰 변경 (범위별) | C M G |
| `DifficultyChangeEvent` | 난이도 프로파일 변경 | C M G |
| `StructureGrowEvent` | 나무·버섯 성장 | C M R |
| `PortalCreateEvent` | 포탈 생성 | C M R |
| `SpawnChangeEvent` | 월드 스폰 변경 | C M G |
| `SculkSpreadEvent` | 스컬크 확산 | C R |
| `FireSpreadEvent` | 불 번짐 | C R |
| `ExplosionCalculateEvent` | 폭발 계산 (블록 목록, 피해 계산) | C M R |

### 3.9 월드젠 (파이프라인 단계 훅)

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `WorldGenBiomeEvent` | 바이옴 샘플 결과 수정 (quart 단위) | M R A |
| `WorldGenNoiseEvent` | 노이즈/밀도 함수 평가 결과 수정 | M R A |
| `WorldGenSurfaceEvent` | 표면 규칙 적용 후 | M R A |
| `WorldGenCarverEvent` | 카버 적용 전/후 | C M R A |
| `WorldGenFeatureEvent` | 피처 배치 전/후 (단계 번호, 피처 id) | C M R A |
| `WorldGenStructureStartEvent` | 구조물 시작 위치 결정 | C M R A |
| `WorldGenStructurePieceEvent` | 구조물 조각 배치 | C M R A |
| `WorldGenLootEvent` | 월드젠 시 상자 전리품 생성 | M R |
| `WorldGenSpawnEvent` | 월드젠 시 몹 스폰 | C M R |
| `WorldGenFinalizeEvent` | 청크 생성 완료 직전 (후처리) | M R A |
| `WorldGenHeightmapEvent` | 하이트맵 계산 결과 | M R |
| `WorldGenSpawnPointEvent` | 월드 스폰 탐색 | M G |

월드젠 훅은 Furnace가 바닐라 시드 비트 일치를 목표로 하고 파이프라인이 단계별로 분리되어 있어서 열기 쉬운 편이다. 다만 현재 피처 일부가 Noop이라 훅이 의미 있는 범위는 구현이 진행된 만큼이다. (레포: `vanilla-diff-2026-10-07.md` §2.4)

### 3.10 스케줄러·스레딩·진단

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `TaskExceptionEvent` | 스케줄된 작업 예외 (플러그인 귀속) | G |
| `TaskOverrunEvent` | 작업이 예산 초과 | G |
| `RegionOverloadEvent` | region MSPT가 임계 초과 | G |
| `CrossRegionAccessEvent` | 소유권 위반 접근 시도 감지 (디버그용) | G |
| `ThreadOwnershipViolationEvent` | `Owned` 가드 위반 | G |
| `PluginTickBudgetEvent` | 플러그인별 틱 사용량 보고 | G |
| `ProfilerSampleEvent` | 샘플링 프로파일러 결과 | G |
| `ChunkIoSlowEvent` | 청크 IO 지연 | G |
| `PacketRateLimitEvent` | 패킷 속도 제한 도달 | C N |

### 3.11 네트워크·패킷

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `PacketReceiveEvent` | 서버바운드 패킷 (파싱 후) | C M N |
| `PacketSendEvent` | 클라이언트바운드 패킷 (직렬화 전) | C M N |
| `RawPacketReceiveEvent` / `RawPacketSendEvent` | 바이트 수준 | C M N |
| `PacketErrorEvent` | 파싱 실패 | N |
| `CustomPayloadReceiveEvent` / `CustomPayloadSendEvent` | 플러그인 채널 | C N |
| `BundlePacketEvent` | 번들 패킷 구성 | M N |

### 3.12 스코어보드·UI·시각

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `ScoreboardUpdateEvent` | 점수판 변경 | C M |
| `BossBarUpdateEvent` | 보스바 | C M |
| `TabListUpdateEvent` | 탭 목록 (플레이어별 표시명, 헤더/푸터) | M |
| `NameTagUpdateEvent` | 이름표 (팀 접두사 등) | M |
| `ParticleSendEvent` | 파티클 전송 (플레이어별 필터링) | C M N |
| `SoundSendEvent` | 사운드 전송 | C M N |
| `LevelEventSendEvent` | 레벨 이벤트(블록 파괴 파티클 등) 전송 | C M N |

### 3.13 레지스트리·데이터·콘텐츠

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `RegistryRegisterEvent` | 레지스트리 항목 등록 | C G |
| `RegistryRemoveEvent` | 소유 플러그인 비활성화로 항목 제거 | G |
| `LootTableGenerateEvent` | 전리품 생성 (목록 수정) | C M R |
| `LootTableLoadEvent` | 전리품 테이블 로드 | M G |
| `RecipeRegisterEvent` | 레시피 등록/제거 | C G |
| `RecipeBookSyncEvent` | 레시피북 동기화 | M E |
| `TagUpdateEvent` | 태그 변경 (재전송 필요) | M G |
| `AdvancementRegisterEvent` | 발전 과제 | C G |
| `EnchantmentRegisterEvent` | 인챈트 | C G |
| `DamageTypeRegisterEvent` | 피해 타입 | C G |
| `BiomeRegisterEvent` / `DimensionTypeRegisterEvent` | 바이옴/차원 타입 | C G |
| `DataPackReloadEvent` | 데이터팩 리로드 | G |

### 3.14 게임 규칙·난이도·정책 (이 문서의 핵심)

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `DifficultyProfileApplyEvent` | 난이도 프로파일이 효과를 적용하는 시점 (데미지 배율 등 수정) | M R |
| `HungerChangeEvent` | 허기 변화 | C M E |
| `SaturationChangeEvent` | 포화도 | C M E |
| `NaturalRegenEvent` | 자연 재생 | C M E |
| `HealthChangeEvent` | 체력 변화 (모든 경로) | C M E |
| `OxygenChangeEvent` | 산소 | C M E |
| `FreezeTickEvent` | 가루눈 동결 | C M E |
| `DamageModifierEvent` | 데미지 배율 단계별 (난이도, 방어, 효과) | M E |
| `MobSpawnPolicyEvent` | 스폰 정책 (밝기·난이도·거리 조건) | C M R |
| `PvPCheckEvent` | PvP 허용 여부 판정 (영역 보호 통합 지점) | C M R |
| `ProtectionCheckEvent` | 블록/엔티티 보호 판정의 공통 질의 (보호 플러그인 통합용) | C M R A |
| `BuildPermissionEvent` | 설치/파괴 권한 질의 | C M R A |
| `FakePlayerActionEvent` | 가짜 플레이어/자동화가 수행하는 행동 | C R |

### 3.15 영속성·데이터

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `PlayerDataLoadEvent` | 플레이어 데이터 로드 (플러그인 저장소로 대체 가능) | M A |
| `PlayerDataSaveEvent` | 저장 | C M A |
| `PlayerDataCorruptEvent` | 손상 감지 (복구 정책) | M A |
| `ChunkDataLoadEvent` / `ChunkDataSaveEvent` | 청크 NBT 읽기/쓰기 후크 | M A |
| `LevelDataLoadEvent` / `LevelDataSaveEvent` | level.dat | M G A |
| `BackupStartEvent` / `BackupEndEvent` | 백업 | G A |
| `DataContainerChangeEvent` | PDC 계열 변경 | C M |

### 3.16 전투 세부

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `CriticalHitEvent` | 치명타 판정/배율 | C M E |
| `SweepAttackEvent` | 휩쓸기 대상 목록 수정 | C M E |
| `AttackCooldownEvent` | 공격 쿨다운 계산 (1.8식 PvP 구현점) | M E |
| `ShieldBlockEvent` / `ShieldDisableEvent` | 방패 막기 / 도끼로 무력화 | C M E |
| `ArmorDamageEvent` | 방어구 내구도 소모 | C M E |
| `ThornsReflectEvent` | 가시 반사 | C M E |
| `TotemUseEvent` | 불사의 토템 발동 | C M E |
| `FireDamageTickEvent` | 화염 피해 틱 | C M E |
| `ProjectileReflectEvent` | 투사체 반사 | C M E |
| `MaceSmashEvent` | 철퇴 강타 (낙하 거리 기반 피해) | C M E |
| `ArrowPickupEvent` | 화살 줍기 | C E |
| `TridentThrowEvent` / `TridentReturnEvent` | 삼지창 | C E |
| `FireworkExplodeEvent` | 폭죽 폭발 | C M R |
| `WindChargeBurstEvent` | 돌풍구 | C M R |
| `EndCrystalExplodeEvent` | 엔드 크리스탈 폭발 | C M R |
| `TntPrimeEvent` | TNT 점화 | C M R |
| `FallDamageCalcEvent` | 낙하 피해 계산 (높이, 블록 감쇠) | M E |

### 3.17 마법·포션·효과

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `PotionSplashEvent` | 투척 포션 (영향 엔티티 목록 수정) | C M R |
| `LingeringPotionEvent` | 잔류 포션 | C M R |
| `AreaEffectCloudApplyEvent` | 효과 구름 적용 | C M R |
| `BeaconEffectEvent` | 신호기 효과 | C M R |
| `ConduitEffectEvent` | 전달체 효과 | C M R |
| `EffectTickEvent` | 효과 틱 (재생·독 같은 주기 효과 수정) | C M E |
| `EnchantEffectTriggerEvent` | 인챈트 효과 발동 시점 | C M E |
| `MendingXpDistributeEvent` | 수선 경험치 분배 | C M E |
| `EnchantmentOfferEvent` | 마법 부여대 제안 목록 | M E |
| `LootingLevelEvent` / `FortuneLevelEvent` | 약탈/행운 레벨 보정 | M E |

### 3.18 농사·자원

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `CropGrowEvent` | 작물 성장 단계 | C M R |
| `BoneMealEvent` | 뼛가루 | C M R |
| `ComposterFillEvent` | 퇴비통 | C M R |
| `BeehiveEnterEvent` / `BeehiveExitEvent` | 벌집 | C R |
| `HoneyHarvestEvent` | 꿀 수확 | C M R |
| `FishingBiteEvent` | 입질 시점/대기 시간 | C M E |
| `FishingCatchEvent` | 낚시 결과 (전리품 수정) | C M E |
| `SheepShearEvent` / `CowMilkEvent` | 가위질/우유 | C M E |
| `EggLayEvent` | 닭 산란 | C M R |
| `AnimalBreedStartEvent` | 번식 시작 (사랑 모드) | C E |
| `TurtleEggEvent` | 거북알 | C R |
| `SaplingGrowEvent` | 묘목 성장 | C M R |
| `PlantSpreadEvent` | 덩굴·사탕수수·대나무 등 성장 | C R |
| `DripstoneGrowEvent` / `AmethystGrowEvent` | 광물 성장 | C R |

### 3.19 이동수단·이동

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `BoatMoveEvent` / `MinecartMoveEvent` | 탈것 이동 | C E |
| `MinecartCollideEvent` | 광산 수레 충돌 | C E |
| `HorseJumpEvent` | 말 점프 (세기) | C M E |
| `HorseInventoryEvent` | 말 인벤토리 | C E |
| `ElytraGlideStartEvent` / `ElytraBoostEvent` | 겉날개 | C M E |
| `RiptideEvent` | 급류 | C M E |
| `EnderPearlLandEvent` | 엔더 진주 착지 | C M E |
| `ChorusFruitTeleportEvent` | 후렴과 | C M E |
| `PortalTravelStartEvent` / `PortalCooldownEvent` | 포탈 이동 | C M E |
| `WaterEnterEvent` / `WaterExitEvent` | 물 출입 | E |
| `SwimEvent` | 수영 상태 | E |
| `ClimbEvent` | 사다리·덩굴 오르기 | C E |

### 3.20 레드스톤·기계

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `ObserverTriggerEvent` | 관측기 | C R |
| `ComparatorReadEvent` | 비교기 읽기값 | C M R |
| `RepeaterDelayEvent` | 중계기 지연 | M R |
| `DaylightSensorEvent` | 일광 감지기 | M R |
| `TargetBlockHitEvent` | 과녁 블록 | C M R |
| `LightningRodEvent` | 피뢰침 | C R |
| `SculkSensorTriggerEvent` / `SculkShriekerEvent` | 스컬크 | C R |
| `CommandBlockExecuteEvent` | 명령 블록 | C M R |
| `CrafterCraftEvent` | 제작기 | C M R |
| `PistonPushLimitEvent` | 피스톤 밀기 한도 (12) | M R |
| `SlimeBlockMoveEvent` | 점액 블록 이동 | C R |
| `LecternPageChangeEvent` | 독서대 | C R |
| `BellRingEvent` | 종 | C R |
| `JukeboxPlayEvent` | 주크박스 | C M R |
| `DecoratedPotInsertEvent` | 장식된 항아리 | C R |
| `SignWaxEvent` | 표지판 밀랍 | C R |

### 3.21 월드 오브젝트·구조물·영역

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `SpawnerSpawnEvent` | 몹 스포너 | C M R |
| `TrialSpawnerActivateEvent` | 시련 스포너 | C M R |
| `VaultUnlockEvent` | 금고 (시련) | C M R |
| `ItemFramePlaceEvent` / `ItemFrameRotateEvent` | 아이템 액자 | C E |
| `PaintingPlaceEvent` | 그림 | C R |
| `ArmorStandManipulateEvent` | 갑옷 거치대 | C E |
| `LeashAttachEvent` / `LeashBreakEvent` | 끈 | C E |
| `MapInitializeEvent` / `MapUpdateEvent` | 지도 (커스텀 렌더용) | C M E |
| `RespawnAnchorChargeEvent` | 리스폰 정박기 | C R |
| `BedExplodeEvent` | 침대 폭발 | C M R |
| `EndCrystalPlaceEvent` | 엔드 크리스탈 설치 | C R |
| `DragonPhaseChangeEvent` | 엔더 드래곤 단계 | C M E |
| `WitherSpawnEvent` | 위더 소환 | C R |
| `BossBattleStartEvent` | 보스 전투 시작 | C E |
| `StructureEnterEvent` / `StructureExitEvent` | 플레이어가 구조물에 들어옴/나감 | E |
| `BiomeEnterEvent` / `BiomeExitEvent` | 바이옴 경계 통과 | E |
| `ChunkBorderCrossEvent` | 청크 경계 통과 | E |
| `RegionEnterEvent` / `RegionExitEvent` | 보호 영역 출입 (4.18) | C E |
| `LightLevelChangeEvent` | 광량 변화 (조명 엔진 생기면) | R |

### 3.22 팀·점수·UI 추가분

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `TeamCreateEvent` / `TeamRemoveEvent` | 팀 | C G |
| `TeamMemberChangeEvent` | 팀원 추가/제거 | C E |
| `ObjectiveCreateEvent` | 목표 | C G |
| `ScoreChangeEvent` | 점수 변경 (플레이어별 보드 포함) | C M E |
| `DisplaySlotChangeEvent` | 표시 위치 | C M E |
| `NametagVisibilityEvent` | 이름표 표시 여부 | M E |
| `GlowToggleEvent` | 발광 | C E |
| `TooltipRenderEvent` | 아이템 툴팁 구성 직전 (서버 측 lore 삽입) | M N |

### 3.23 관리·보안·안티치트

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `WhitelistChangeEvent` | 화이트리스트 | C G |
| `BanEvent` / `UnbanEvent` | 밴 | C G |
| `OpChangeEvent` | OP 변경 | C G |
| `PlayerReportEvent` | 채팅 신고 수신 | E |
| `MuteEvent` | 음소거 | C E |
| `JoinFloodDetectedEvent` | 접속 폭주 감지 | N |
| `NameCollisionEvent` / `DuplicateLoginEvent` | 중복 접속 (기존 세션 처리 정책) | C M N |
| `MovedTooQuicklyEvent` | 과속 이동 감지 (바닐라 "moved too quickly") | C M E |
| `MovedWronglyEvent` | 위치 불일치 | C M E |
| `InvalidMoveEvent` | NaN/범위 밖 | C E |
| `ReachViolationEvent` | 사거리 초과 상호작용 | C M E |
| `InteractRateLimitEvent` | 상호작용 속도 제한 | C M E |
| `PlacementOutOfRangeEvent` | 범위 밖 설치 시도 | C E |
| `InventoryDesyncEvent` | 인벤토리 동기화 불일치 | E |
| `OversizedPacketEvent` | 비정상 크기 패킷 | C N |
| `ProtocolViolationEvent` | 프로토콜 위반 (킥 정책) | C M N |

### 3.24 서비스 (권한·경제·프로필)

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `PermissionChangeEvent` / `GroupChangeEvent` | 권한/그룹 변경 (명령 트리 재전송 트리거) | G |
| `PermissionCheckEvent` | 권한 질의 후크/캐시 무효화 | M A |
| `BalanceChangeEvent` | 잔액 변동 | C M A |
| `EconomyTransactionEvent` | 거래 | C M A |
| `NicknameChangeEvent` | 닉네임 | C M E |
| `SkinChangeEvent` / `ProfileUpdateEvent` | 스킨/프로필 | C M E A |
| `LocaleChangeEvent` | 클라 언어 변경 | E |

### 3.25 데이터팩·함수

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `FunctionExecuteEvent` | 함수 실행 전후 | C M R |
| `FunctionScheduleEvent` | 예약 함수 | C M G |
| `PredicateEvaluateEvent` | 술어 평가 결과 수정 | M R |
| `ItemModifierApplyEvent` | 아이템 수정자 적용 | C M R |
| `DataPackLoadEvent` / `DataPackErrorEvent` | 데이터팩 로드/오류 | G |
| `CustomTriggerFireEvent` | 발전 과제 커스텀 트리거 | C E |
| `TagReloadEvent` | 태그 재로드 | G |
| `LootConditionEvaluateEvent` | 전리품 조건 평가 | M R |

### 3.26 IO·저장·운영 진단

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `RegionFileOpenEvent` | 리전 파일 열기 | N |
| `ChunkCorruptionDetectedEvent` | 손상 청크 감지 (복구 정책) | M A |
| `ChunkSaveFailEvent` | 저장 실패 | A |
| `DiskSpaceLowEvent` | 디스크 부족 | G |
| `SessionLockLostEvent` | 세션 락 상실 | G |
| `WorldMigrationEvent` / `DataVersionUpgradeEvent` | 월드/데이터 마이그레이션 | M G A |
| `GcPauseEvent` | GC 정지 시간 | G |
| `MemoryPressureEvent` | 메모리 압박 (캐시 축소 신호) | G |
| `ThreadPoolSaturationEvent` | IO/생성 풀 포화 | G |
| `NetworkBandwidthEvent` | 대역폭 임계 | G |

### 3.27 멀티서버·원격

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `InterServerMessageEvent` | 서버 간 메시지 수신 | A |
| `PlayerRouteRequestEvent` | 플레이어를 다른 서버로 보낼 대상 결정 | M E A |
| `SessionHandoffEvent` | 세션 이전 (쿠키로 상태 전달) | M E A |
| `ProxyForwardingEvent` | 프록시 포워딩 정보 | M N |
| `RemoteCommandEvent` | REST/원격 콘솔 입력 | C M G |
| `WebhookDispatchEvent` | 외부 웹훅 발송 직전 | C M G A |
| `MetricsExportEvent` | 메트릭 내보내기 | G |

### 3.28 인스턴스·미니게임

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `InstanceCreateEvent` / `InstanceDestroyEvent` | 인스턴스 월드 | G |
| `InstanceResetEvent` | 초기 상태로 복원 | C G |
| `InstanceTemplateCloneEvent` | 템플릿 복제 | M G A |
| `InstanceTickEvent` | 인스턴스 단위 틱 | R |
| `GameStateChangeEvent` | 선택형 게임 프레임워크의 상태 머신 | C M G |
| `ArenaAssignEvent` | 플레이어를 아레나에 배정 | C M E |

### 3.29 접속 큐·슬롯

| 이벤트 | 용도 | 속성 |
|---|---|---|
| `LoginQueueEvent` | 접속 대기열 진입 | C M N A |
| `QueuePositionChangeEvent` | 대기 순번 갱신 | N |
| `SlotReservationEvent` | 예약 슬롯 (VIP 등) | C M N A |
| `PlayerCapCheckEvent` | 최대 인원 판정 | M N |

### 3.30 사용자 정의 이벤트 API

| 항목 | 설명 |
|---|---|
| `CustomEvent` 정의 | 플러그인이 이벤트 타입을 정의하고 스레드 계약·취소 가능 여부·결과 합성 규칙을 선언 |
| 이벤트 스코프 | 플러그인/월드/region 단위 버스. 범위가 좁은 구독은 비용이 싸다 |
| 조건부 구독 | 필터(월드, 위치, 권한)를 구독 시점에 선언해 불필요한 호출 제거 |
| 이벤트 위임 | 한 플러그인이 다른 플러그인의 이벤트를 재발행 |
| 이벤트 기록·재생 | 개발 모드에서 발화 순서를 기록하고 테스트에서 재생 |
| 핸들러 계측 | 핸들러별 호출 횟수·소요 시간 (4.15 프로파일러와 연동) |


---

## 4. 기능·API 카탈로그 **[제안]**

각 항목의 "근거"는 1절의 불만 번호 또는 Furnace의 기존 설계다.

### 4.1 플러그인 로더·수명주기

| 항목 | 설명 | 근거 |
|---|---|---|
| 선언형 매니페스트 | 이름, 버전, `apiVersion`, 의존성 그래프, 소프트 의존성, 로드 단계 | 1.1 #8 |
| 단계별 로드 | bootstrap(레지스트리 등록) → enable → ready(서버 시작 후) | 1.4 #23 |
| 클래스로더 격리 | 플러그인별 로더, 공유 API만 부모 위임 | 1.2 #15 |
| 자동 정리 | 비활성화 시 이벤트 핸들러, 스케줄 작업, 레지스트리 항목, 명령, 패킷 후크 일괄 해제 | 1.2 #15, 1.7 #41 |
| 핫 리로드 | 격리 로더 기반. 또는 "리로드 비지원"을 명시하는 것도 선택지 | 1.7 #41 |
| 서비스 레지스트리 | 플러그인이 인터페이스를 등록하고 다른 플러그인이 조회 (Vault류 대체) | 1.7 #37, #43 |
| 플러그인 간 이벤트 | 플러그인이 정의한 이벤트를 다른 플러그인이 구독 | 1.2 #14 |
| 예외 귀속 | 스택 트레이스에서 플러그인 식별, 오류 누적 시 자동 비활성화 옵션 | 1.7 #44 |
| 플러그인별 로거·설정 폴더·리소스 | 기본 제공 | - |
| 호환성 검사 | 시작 시 `apiVersion` 불일치 플러그인 보고 | 1.1 #9 |

### 4.2 스케줄러·동시성

| 항목 | 설명 | 근거 |
|---|---|---|
| Global/Region/Entity 스케줄러 공개 | 설계 문서 4.4 (이미 인터페이스 있음) | 1.5 #28 |
| 비동기 스케줄러 | IO/연산 풀에서 실행, 월드 접근 금지 | 1.5 |
| 지연·주기·반복 | tick 단위와 시간 단위 둘 다 | - |
| `CompletableFuture` 기반 월드 접근 | `world.getBlockAsync(pos)`, `loadChunkAsync` | 1.5 #31 |
| 소유권 쿼리 | `scheduler.isOwnedByCurrentThread(pos/entity)` | 1.5 #30 |
| 소유권 위반 동작 설정 | 개발 모드: 즉시 예외, 운영 모드: 경고 + 위임 | 설계 문서 4.2 |
| 태스크 귀속·예산 | 플러그인별 CPU 시간 집계, 초과 시 경고/중단 | 1.7 #45 |
| 워치독 | region별 스레드 정지 감지, 덤프 | 1.7 #44 |
| 락 없는 교차 region 메시지 | `region(pos).execute(...)` 큐잉 | 설계 문서 4.5 |
| 원자적 교차 region 연산 | 다중 region 블록 변경 묶음 (M5 이후) | 설계 문서 4.5 |
| 틱 프리즈/스텝 | `/tick`과 유사하게 region별로 | 3.1 |
| 결정적 틱 모드 | 테스트용 고정 시드·순서 | 1.7 #48 |

### 4.3 명령 시스템

| 항목 | 설명 | 근거 |
|---|---|---|
| 단일 명령 트리 | 바닐라 명령도 플러그인 명령도 동일 레지스트리 | 1.3 #19-22 |
| 교체·래핑·삭제 | 기존 노드 위에 실행 로직을 덧씌우기 | 사용자 요청 |
| 인자 노드 추가 | 바닐라 `/tp`에 서브 노드 추가 | 1.3 #21 |
| 플레이어별 트리 | 권한·상태별로 다른 트리 전송 | 1.3 #21 |
| 선언형/빌더 API | Brigadier 호환 + 타입 안전 | - |
| 비동기 명령 | `CompletableFuture<Result>` | 1.5 |
| region 인식 실행 | 대상이 다른 region이면 자동 위임 | 설계 문서 4.4 |
| 서브커맨드 별칭·리다이렉트 | - | - |
| 제안(Suggestion) 비동기 | DB 조회 등 | - |
| 선택자 확장 | 서버 해석 커스텀 옵션 (클라 경고 가능) | 3.4 |
| 명령 블록·함수 통합 | 데이터팩 함수에서 플러그인 명령 호출 | 1.3 |
| 실행 컨텍스트 | 실행자, 위치, 방향, 스토리 변수 | - |
| 실행 감사 로그 | 누가 무엇을 실행했는지 구조화 로깅 | - |
| 도움말 자동 생성 | 트리에서 도움말 생성 | - |
| 권한 노드 자동 연결 | 명령 노드에 권한 선언 | 1.7 #37 |
| 실행 속도 제한 | 플레이어/명령별 | - |

### 4.4 레지스트리·데이터 주도 콘텐츠

| 항목 | 설명 | 근거 |
|---|---|---|
| 플러그인 정의 레지스트리 | 타입 있는 사용자 레지스트리 | 1.2 #14 |
| 소유권 태깅 | 항목마다 소유 플러그인 | 1.2 #15 |
| 동결 단계 | bootstrap 후 변경 불가 | 3.1 |
| 데이터 레지스트리 전송 | 인챈트, 피해 타입, 페인팅, 배너 패턴, 늑대 변종, 노래, 트림 등 config 단계에서 직접 전송 (클라가 동기화하는 레지스트리 한정, **[일반지식]**) | 1.2 #13, #16 |
| 태그 API | 블록/아이템/엔티티 태그 편집, 변경 시 재전송 | - |
| 레시피 API | 등록/제거/교체, 커스텀 레시피 타입 (서버 계산) | - |
| 전리품 테이블 API | 코드/JSON 둘 다 | - |
| 발전 과제 API | 동적 등록, 커스텀 트리거 | - |
| 커스텀 게임룰 | 타입, 기본값, 범위(월드/region/플레이어) | 1.2 #12 |
| 커스텀 난이도 프로파일 | 이름·배율 묶음. 월드/region/플레이어/시간에 연결 | 1.2 #11 |
| 커스텀 속성(Attribute) | 서버 논리 속성 (클라 표시는 기존 속성에 매핑) **[일반지식 - 클라 한계 검증 필요]** | - |
| 커스텀 데미지 타입 | 사망 메시지, 경감 규칙 포함 | 1.2 #16 |
| 커스텀 바이옴/차원 타입 | 새 키로 전송 (검증 필요) | 1.2 #18 |
| 커스텀 효과(상태 이상) | 서버 논리 효과 + 표시는 기존 효과/아이콘 | - |
| 커스텀 사운드 이벤트 | 리소스팩 사운드 참조 | - |

### 4.5 블록

| 항목 | 설명 | 근거 |
|---|---|---|
| 이중 레지스트리 | 서버 논리 블록 ↔ 클라 표현 블록 (네트워크 계층 변환) | 1.2 #17 |
| 블록 상태 수 무제한 | 클라 표현은 재사용, 서버는 자체 상태 | 1.2 #17 |
| 커스텀 채굴 | 경도, 도구 효율, 채굴 시간 서버 계산 (클라 진행바는 표현 블록 기준이라 불일치 가능 **[일반지식]**) | 1.2 #17 |
| 커스텀 드롭·소리·파티클 | - | - |
| 커스텀 틱 | 랜덤/예약/매 틱, region 병렬 | 설계 문서 |
| 커스텀 블록 엔티티 | 서버 논리 데이터 + 저장 | - |
| 이웃 업데이트 훅 | 지지 조건 (`canSurvive`) 정의 | - |
| 방향·회전 속성 | 표현 매핑 | - |
| 가상 블록 레이어 | 플레이어별 가짜 블록 (4.9) | 1.7 #39 |
| 블록 변경 배치 API | 대량 변경을 묶어 청크 단위 한 번에 전송 | - |
| 블록 쿼리 API | 충돌 박스, 경도, 투명도, 발광, 유체 | - |
| 월드 편집 프리미티브 | 영역 복사/붙여넣기/채우기 (WorldEdit류의 코어 지원) | - |

### 4.6 엔티티·AI

| 항목 | 설명 | 근거 |
|---|---|---|
| 서버 논리 엔티티 타입 | 표현 타입 + 모델(디스플레이/아이템 디스플레이)로 외형 | 1.2 #17 |
| AI 완전 교체 | 목표/행동 트리를 플러그인 코드로 | 1.7 |
| 비동기 패스파인딩 | 오프스레드 탐색 + 결과 전달 (설계 문서 M5) | 설계 문서 |
| 경로 API | 경로 노드 조회/수정, 커스텀 이동 비용 함수 | - |
| 감각 API | 시야, 소리, 냄새 같은 센서 추상화 | - |
| 엔티티 메타데이터 API | 플레이어별 메타데이터 오버라이드 | 1.7 #39 |
| 엔티티 속성 API | 이동 속도, 체력, 공격력 | - |
| 엔티티 틱 감속 | 거리/활성도별 틱 주기 조절 | 1.5 #32 |
| 엔티티 그룹·팀 | - | - |
| 가상 엔티티 (NPC) | 월드에 저장되지 않는 엔티티, 플레이어별 가시성 | 1.7 #40 |
| 디스플레이 엔티티 헬퍼 | 텍스트/블록/아이템 디스플레이 | 1.7 #40 |
| 가짜 플레이어 | 서버 측 플레이어 엔티티 (연결 없음) | - |
| 엔티티 쿼리 | 반경/박스/조건 비동기 조회, region 경계 투명 처리 | - |
| 스폰 정책 API | 상한, 조건, 팩 크기, 플레이어별 | 1.1 |
| 투사체 API | 궤적 계산, 커스텀 충돌 | - |
| 탑승·부착 | - | - |

### 4.7 아이템·컴포넌트

| 항목 | 설명 | 근거 |
|---|---|---|
| 단일 컴포넌트 모델 | 변환 계층 없는 아이템 | 1.1 #2 |
| 타입 안전 컴포넌트 API | 모든 컴포넌트에 타입 있는 접근자 | 1.1 #1 |
| 크기 정책 | 하드 한계를 서버 정책으로 (lore 줄 수 등) | 1.2 #13 |
| 커스텀 컴포넌트 | 플러그인 정의 데이터 (클라 비전송) | - |
| 아이템 데이터 컨테이너 | PDC 대응 | 1.7 #47 |
| 플레이어별 아이템 외형 | 인벤토리 패킷 변환으로 같은 아이템을 다르게 표시 | 1.7 #39 |
| 아이템 술어 API | `canPlaceOn`/`canBreak` 대체 | 1.1 #3 |
| 커스텀 아이템 행동 | 사용·우클릭·소비 로직 | - |
| 쿨다운 API | 아이템별 | - |
| 내구도 API | - | - |
| 스택 크기 정책 | 컴포넌트 최대 99까지 (그 이상은 가상 스택 **[제안]**) | - |

### 4.8 인벤토리·GUI

| 항목 | 설명 | 근거 |
|---|---|---|
| 서버 구동 컨테이너 | 클릭 처리 전권, 임의 슬롯 수 (클라가 지원하는 메뉴 타입 한도) | 1.7 #42 |
| 가상 슬롯 | 서버 논리 슬롯과 표시 슬롯 분리 | - |
| 페이지네이션 | 큰 목록 헬퍼 | - |
| 상태 동기화 | `stateId` 정확 처리, 재동기화 자동 | vanilla-diff §2.2 |
| 커서·드래그 정확 처리 | 모든 클릭 종류 | vanilla-diff §2.2 |
| 컨테이너 제목 동적 변경 | - | - |
| 메뉴 빌더 | 선언형 | - |
| 입력 UI | 모루/표지판/책 입력을 이용한 텍스트 입력 헬퍼 | - |
| 대화상자 | 다이얼로그 (1.21.6+ 기능, 1.21.1에는 없음 **[일반지식]**) | - |

### 4.9 플레이어 뷰·가상 상태

| 항목 | 설명 | 근거 |
|---|---|---|
| 개인 스코어보드 | 플레이어마다 독립 | 1.7 #39 |
| 개인 탭 목록/이름표 | - | - |
| 가짜 블록/영역 | 플레이어에게만 보이는 블록 변경 | 1.7 #39 |
| 가짜 엔티티 | 플레이어에게만 보이는 엔티티 | 1.7 #40 |
| 개인 시간·날씨 | - | - |
| 개인 월드 보더 | 경계 연출 | - |
| 개인 하이라이트 | 발광 엔티티 효과 | - |
| 개인 파티클/사운드 | - | - |
| 개인 보스바/액션바/타이틀 | - | - |
| 시야 거리 오버라이드 | 플레이어별 | - |
| 카메라 | 관전 엔티티를 이용한 카메라 제어 | - |
| 리소스팩 관리 | 복수 팩, 스왑, 강제 | 1.2 |

### 4.10 월드·월드젠

| 항목 | 설명 | 근거 |
|---|---|---|
| 월드 API | 생성/로드/언로드/삭제를 비동기로 | 1.5 #29 |
| 인스턴스 월드 | 메모리 전용, 템플릿 복제 (미니게임) | - |
| 스냅샷·롤백 | CoW 청크 스냅샷 | - |
| 저장 백엔드 교체 | 인터페이스 `ChunkStorage` | - |
| 월드젠 단계 훅 | 3.9 이벤트 + 전용 인터페이스 | 설계 문서 M4g |
| 밀도 함수 교체 | JSON/코드 | 설계 문서 |
| 바이옴 소스 교체 | 다중 노이즈 파라미터 커스텀 | - |
| 구조물 등록 | 커스텀 구조물 + 배치 규칙 | - |
| 피처 등록 | 커스텀 피처 타입 | - |
| 청크 티켓 | 강제 로드 영역 (플러그인 소유) | vanilla-diff §2.3 |
| 청크 데이터 컨테이너 | 청크에 영구 데이터 부착 | 1.7 #47 |
| 월드 데이터 컨테이너 | 월드/레벨 단위 | - |
| 높이맵·광원 쿼리 | - | - |
| 시드 일치 모드 | 바닐라 시드 일치 + 부분 오버라이드 | 설계 문서 |
| 월드 보더 API | 다중 보더(영역별) | - |
| 날씨·시간 API | 월드/region/플레이어 단위 | - |
| 차원 등록 | 커스텀 차원 (검증 필요) | 1.2 #18 |
| 월드 편집기 코어 | 대량 편집 프리미티브 | 4.5 |
| 청크 공유·병합 | 여러 월드가 하나의 청크 저장소 공유 | - |
| 월드 마이그레이션 | 바닐라/Paper 월드 로드 + 정보 보존 | vanilla-diff §2.3 |

### 4.11 네트워크·프로토콜

| 항목 | 설명 | 근거 |
|---|---|---|
| 패킷 이벤트 | 3.11 | 1.7 #38 |
| 타입 있는 패킷 클래스 | 모든 패킷에 접근자 | - |
| 패킷 번들 | - | - |
| 플러그인 메시징 | 커스텀 채널 | - |
| Transfer·쿠키 | 프록시 없이 멀티서버 | vanilla-diff §2.1 |
| 프록시 포워딩 | Velocity/Bungee 호환 | vanilla-diff §2.1 |
| 연결 제한·보호 | 속도 제한, 연결 수 제한, 패킷 크기 제한 | 레포 커밋 로그 |
| 통계 API | 연결별 대역폭, 패킷 수 | - |
| 압축·암호화 선택 | - | - |
| 서버 링크 | 서버 목록 링크 | vanilla-diff §2.1 |
| 상태 응답 커스텀 | MOTD, 샘플, 파비콘 | vanilla-diff §2.1 |
| 쿠키 기반 상태 | 서버 간 이전 시 상태 유지 | - |
| 다중 프로토콜 지원 훅 | 장기 | 설계 문서 비목표 |

### 4.12 채팅·텍스트

| 항목 | 설명 | 근거 |
|---|---|---|
| 텍스트 컴포넌트 라이브러리 | Adventure 유사 불변 컴포넌트 | - |
| 채팅 파이프라인 | 포맷, 필터, 수신자 선택, 서명 정책 | 3.4 |
| 채널 | 로컬/전역/그룹 채팅 코어 지원 | - |
| 번역 | 언어별 메시지 번들, 클라 로케일 기반 | 3.2 |
| 플레이스홀더 | 코어 서비스 | 1.7 #37 |
| 미니메시지 파서 | - | - |
| 시스템 메시지 후크 | - | 3.4 |
| 폰트 기반 UI | 리소스팩 폰트 + 비트맵 | - |

### 4.13 권한·경제·공용 서비스

| 항목 | 설명 | 근거 |
|---|---|---|
| 권한 서비스 인터페이스 | 노드, 컨텍스트(월드/서버), 그룹. 구현은 플러그인 | 1.7 #37, 1.4 #23 |
| 로그인 시점 권한 주입 | 3.2 `LoginAuthEvent` | 1.4 #23 |
| 경제 서비스 인터페이스 | - | 1.7 #37 |
| 채팅 메타(접두사) 서비스 | - | - |
| 영역 보호 질의 서비스 | `ProtectionCheckEvent` 통합 | 3.14 |
| 화이트리스트·밴·OP | 코어 + 교체 가능 | vanilla-diff §2.1 |
| 감사 로그 서비스 | 구조화 이벤트 스트림 | - |
| 통계·메트릭 서비스 | Prometheus/OpenTelemetry 노출 | - |

### 4.14 설정·데이터·영속성

| 항목 | 설명 | 근거 |
|---|---|---|
| 단일 스키마 설정 | 타입, 기본값, 문서 포함 | 1.6 #34 |
| 유효 설정 덤프 | 병합 결과 확인 명령 | 1.6 #34 |
| 설정 스키마 버전·자동 마이그레이션 | - | 1.6 #35 |
| 플러그인 설정 API | 타입 안전, 핫 리로드, 변경 이벤트 | - |
| 데이터 컨테이너 | 플레이어/엔티티/아이템/블록 엔티티/청크/월드 공용 | 1.7 #47 |
| 저장소 추상화 | 플러그인 데이터 저장 (파일/SQLite/원격) 어댑터 | - |
| 플레이어 데이터 교체 | 외부 저장소로 | 3.15 |
| 원자적 저장 | 임시 파일 + rename | 레포 커밋 로그 (이미 개선됨) |
| 백업 API | 스냅샷, 증분 | 3.15 |
| 세션 락 | 두 인스턴스 방지 | vanilla-diff §2.3 |
| 데이터 픽서 | 버전 간 NBT 마이그레이션 | vanilla-diff §2.3 |

### 4.15 진단·테스트·보안

| 항목 | 설명 | 근거 |
|---|---|---|
| 내장 프로파일러 | region/플러그인/이벤트별 시간 | 1.7 #44, #45 |
| 틱 트레이싱 | 느린 틱의 호출 스택 기록 | - |
| 이벤트 트레이스 | 어떤 핸들러가 얼마나 걸렸는지 | - |
| 메모리 귀속 | 플러그인별 할당 추정 | 1.1 #8 |
| 헤드리스 테스트 서버 | 연결 없는 가짜 클라이언트로 통합 테스트 | 1.7 #48 |
| 결정적 시뮬레이션 | 고정 시드·순서 (위치 기반 시드 덕분) | 설계 문서 |
| 리플레이 | 입력 로그 재생 | - |
| 가짜 클라이언트 | 프로토콜 수준 테스트 클라 | - |
| 플러그인 샌드박스 | 파일/네트워크 접근 선언. **JVM 샌드박스는 한계가 크다 (SecurityManager 폐기 **[일반지식]**)**: 완전 격리 약속은 하지 말 것 | 1.7 #46 |
| 서명된 플러그인 | 배포 무결성 검증 | - |
| 크래시 리포트 | 플러그인 귀속 포함 | 1.7 #44 |
| 헬스 엔드포인트 | 운영 모니터링용 | - |

### 4.16 원인(Cause)·컨텍스트 추적

| 항목 | 설명 | 근거 |
|---|---|---|
| Cause 체인 | 모든 변경 이벤트에 "플레이어 → 아이템 → 블록 → 플러그인" 원인 체인 부착 | 1.8 #55 |
| 컨텍스트 스택 | 플러그인이 자기 작업에 컨텍스트(작업 이름, 소유자)를 푸시. 하위 이벤트가 상속 | 1.8 #55 |
| 원인별 필터 | `event.cause().first(Player.class)`, `.contains(Plugin)` | - |
| 순환 방지 | 플러그인이 일으킨 변경에 같은 플러그인 핸들러가 재진입하는 것을 감지 | - |
| 스레드 간 전파 | 비동기 작업/region 위임 시 Cause가 따라감 | 설계 문서 4.4 |
| Sponge 선례 | SpongeAPI는 Events, Data API, Services, Permissions, Economy 등을 문서화한다. Cause 구조 자체는 이번 조사에서 확인 못 함 **[일반지식]** | [Sponge 문서](https://docs.spongepowered.org/master/en/) |

### 4.17 변경 트랜잭션·롤백

| 항목 | 설명 |
|---|---|
| 변경 묶음 | 여러 블록/엔티티/인벤토리 변경을 하나의 단위로 `commit()`/`rollback()` |
| 사전 검증 | 커밋 전에 보호 영역·권한 질의를 일괄 수행 |
| region 인식 | 묶음이 여러 region에 걸치면 각 소유 스레드에 위임하고 결과를 합침 (설계 문서 4.5의 교차 region 원자 연산과 같은 문제) |
| 폭발·피스톤 등 | 코어가 만드는 대량 변경도 같은 트랜잭션 모델로 노출 |
| 변경 diff 조회 | 커밋 전 "무엇이 바뀔지" 미리보기 |
| 실패 정책 | 부분 실패 시 전체 롤백/가능한 만큼 적용 중 선택 |

### 4.18 보호 영역·플래그 코어

| 항목 | 설명 | 근거 |
|---|---|---|
| 영역 개념 | 월드 내 영역(박스/다각형/청크 집합)을 코어 객체로 | 1.8 #56 |
| 플래그 레지스트리 | `pvp`, `build`, `mob-spawn` 같은 플래그를 플러그인이 정의 | 1.8 #56 |
| 질의 API | `regions.query(pos).flag(BUILD, player)` | 3.14 |
| 영역 출입 이벤트 | 3.21 | - |
| 공간 인덱스 | R-tree 등으로 빠른 조회 (region 스레드 안전) | - |
| 상속·우선순위 | 영역 간 우선순위, 상속 | - |
| 소유권 | 영역마다 소유 플러그인/플레이어 | - |

### 4.19 구조화 로깅·감사·롤백 저장소

| 항목 | 설명 | 근거 |
|---|---|---|
| 변경 로그 코어 | 블록·컨테이너·엔티티 변경을 Cause와 함께 이벤트 스트림으로 노출 | 1.8 #55 |
| 저장 어댑터 | 파일/SQLite/외부 DB로 쓰는 어댑터 인터페이스 | - |
| 조회 API | 위치·시간·행위자별 조회 | - |
| 롤백 API | 조건에 맞는 변경을 되돌림 (4.17과 연동) | - |
| 보존 정책 | 기간·용량 | - |
| 성능 격리 | 로깅이 틱을 막지 않도록 별도 스레드/배치 | 설계 문서 |

### 4.20 원격 관리 API

| 항목 | 설명 | 근거 |
|---|---|---|
| REST/WebSocket 엔드포인트 | 플레이어 목록, 월드, 명령 실행, 메트릭 조회 | 1.8 #50 |
| 인증·권한 | 토큰, 스코프, 속도 제한 | - |
| 이벤트 스트림 | 서버 이벤트를 구독해서 외부로 푸시 | - |
| 원격 콘솔 | 로그 스트리밍, 명령 입력 | - |
| 헬스·준비 상태 | 컨테이너 환경의 liveness/readiness | - |
| 메트릭 | Prometheus/OpenTelemetry | 4.15 |
| 플러그인 확장 | 플러그인이 엔드포인트를 등록 | - |

### 4.21 멀티서버·네트워크 코어

| 항목 | 설명 | 근거 |
|---|---|---|
| Transfer·쿠키 | 프록시 없이 서버 간 이동 + 상태 전달 | 1.8 #59, vanilla-diff §2.1 |
| 서버 간 메시징 | 발행/구독, 요청/응답 | - |
| 서버 레지스트리 | 같은 네트워크 서버 목록, 상태, 인원 | - |
| 공유 플레이어 데이터 | 저장소 어댑터를 공유하는 세션 이전 | 4.14 |
| 큐·로비 | 접속 대기열, 서버 선택 | 3.29 |
| 프록시 포워딩 | Velocity/Bungee 호환 | vanilla-diff §2.1 |
| 분산 락 | 같은 플레이어 데이터를 두 서버가 동시에 쓰지 않도록 | - |

### 4.22 안티치트 기반

| 항목 | 설명 | 근거 |
|---|---|---|
| 서버 물리 모델 | 충돌·이동 시뮬레이션 (현재 Furnace에는 없음, vanilla-diff §2.2) | 1.8 #57 |
| 예측 이동 질의 | "이 입력에서 다음 위치는?" 을 플러그인이 호출 | 1.8 #57 |
| 입력 상태 노출 | 키 입력, 시선, 지상 여부, 패킷 시각 | 3.3 |
| 이동 검증 훅 | 3.23 이벤트 | - |
| 패킷 타이밍 | 패킷 도착 시각·순서 | 3.11 |
| 위반 점수 서비스 | 플러그인이 위반을 보고하고 코어가 집계·조치 (킥/밴/기록) | - |
| 상호작용 검증 | 사거리·시선·교차 판정 | 3.23 |
| 리플레이 증거 | 위반 직전 N초 입력 기록 | 4.15 |

### 4.23 데이터팩 통합

| 항목 | 설명 | 근거 |
|---|---|---|
| 데이터팩 로더 | 월드/전역 데이터팩 로드, 우선순위 | 1.8 #64 |
| 플러그인 번들 데이터팩 | 플러그인 JAR 안에 데이터팩을 넣어 자동 등록 | - |
| 함수 호출 API | 플러그인 → 함수 실행, 함수 → 플러그인 명령 | 3.25 |
| 술어·아이템 수정자 | 코드로도 등록 | - |
| 오류 보고 | 데이터팩 파싱 오류를 줄 번호와 함께 | 1.8 #61 |
| 리로드 | 서버 재시작 없이 | - |

### 4.24 성능·튜닝 API

| 항목 | 설명 | 근거 |
|---|---|---|
| 엔티티 활성 범위 | 거리·종류별 틱 빈도 정책 | 1.1 #32 |
| 몹 상한 | 월드/region/플레이어별 | 3.6 |
| 청크 우선순위 | 로딩/생성 우선순위 정책 플러그인화 | 3.8 |
| 틱 예산 | region/플러그인별 CPU 시간 한도 | 4.2 |
| 적응형 시야 거리 | 부하에 따라 플레이어별 시야 거리 조정 | - |
| 부하 분산 | region→스레드 배정 정책 교체 | 3.8 |
| 캐시 정책 | 청크·표면 높이·바이옴 캐시 크기 조정 | vanilla-diff §2.4 |
| 병목 리포트 | 가장 비싼 region/엔티티/플러그인 순위 | 4.15 |

### 4.25 선택형 게임·미니게임 프레임워크

| 항목 | 설명 |
|---|---|
| 상태 머신 | 대기 → 시작 → 진행 → 종료 상태와 전이 이벤트 |
| 아레나 관리 | 템플릿 월드 복제, 리셋, 풀링 (4.10) |
| 팀·매칭 | 팀 구성, 밸런싱, 큐 |
| 관전·부활 | 관전 모드, 리스폰 정책 |
| 점수·승리 조건 | 선언형 |
| 라운드 제한 시간 | 틱 단위 타이머 |
| 코어에 넣을지 | 코어 필수가 아니라 `furnace-gameframework` 같은 공식 선택 모듈로 두는 것을 추천 **[제안]** |

### 4.26 맵·책·표지판 UI

| 항목 | 설명 | 근거 |
|---|---|---|
| 지도 캔버스 | 커스텀 이미지를 지도 아이템에 렌더 (바닐라 클라 그대로) | 1.8 #65 |
| 책 UI | 서버가 책을 열어 텍스트 UI로 사용 | - |
| 표지판 입력 | 표지판 편집으로 텍스트 입력 받기 | - |
| 독서대 | - | - |
| 모루 입력 | 모루 이름 칸으로 텍스트 입력 | - |
| 폰트 UI | 리소스팩 폰트 + 컴포넌트로 GUI 제목에 이미지 | - |

### 4.27 프로필·스킨·닉네임

| 항목 | 설명 | 근거 |
|---|---|---|
| 프로필 프로퍼티 API | 스킨 텍스처 값/서명 | 1.8 #66 |
| 스킨 서비스 | 서명된 텍스처를 외부에서 받아오는 어댑터 (서명은 Mojang만 가능 **[일반지식]**) | - |
| 닉네임 | 탭/이름표/채팅에 표시명 분리 | 3.24 |
| 이름 숨기기 | 팀·이름표 설정 활용 | - |
| 개인별 스킨 변경 | 특정 관찰자에게만 다른 스킨 (플레이어 정보 패킷 변환) | 4.9 |

### 4.28 사운드·연출·컷신

| 항목 | 설명 |
|---|---|
| 사운드 큐 | 시퀀스, 페이드, 위치 고정/플레이어 추적 |
| 음악 | 리소스팩 사운드 + 스트림 제어 |
| 타이틀 시퀀스 | 타이틀·서브타이틀·액션바 타임라인 |
| 카메라 | 관전 엔티티/디스플레이 엔티티를 이용한 카메라 경로 |
| 입자 쇼 | 파티클 도형 라이브러리 (비동기 계산 → 배치 전송) |
| 연출 타임라인 | 틱 단위 이벤트 스케줄링 DSL |

### 4.29 접속 큐·슬롯·화이트리스트

| 항목 | 설명 |
|---|---|
| 큐 시스템 | 풀 서버에서 대기열 + 순번 표시 |
| 예약 슬롯 | 권한 기반 우선 접속 |
| 화이트리스트 소스 | 파일/DB/외부 API 교체 |
| 밴 저장소 | 만료, 사유, 소스 교체 |
| 접속 폭주 방어 | 연결 속도 제한, 봇 패턴 감지 |

### 4.30 개발자 경험(DX)

| 항목 | 설명 | 근거 |
|---|---|---|
| 문서 자동 생성 | 이벤트 문서에 **발화 스레드·취소 가능·결과 합성 규칙** 자동 표기 | 3.0 |
| API 호환성 검사 | 릴리스마다 공개 API 바이너리 호환성 검사(japicmp 류)를 CI에 | 1.1 #1-#5 |
| 폐기 정책 | `@Deprecated(since, forRemoval)` + 최소 N개 버전 유지 + 마이그레이션 문서 자동 생성 | 1.1 #4 |
| 로컬 테스트 서버 | Gradle 플러그인으로 `runServer` 한 줄 | - |
| 샘플·템플릿 | 이벤트·명령·GUI·스케줄러 샘플 (현재 `furnace-plugin-test`가 시작점) | 레포 |
| 핫 리로드 개발 모드 | 코드 변경 즉시 반영 | 4.1 |
| 패키지 매니저 | 매니페스트 기반 의존성 해결, 저장소 연동 | 1.8 #60 |
| 마이그레이션 가이드 | Paper 플러그인에서 포팅하는 가이드/대응표 (어댑터는 비목표로 두되 선택지로 검토) | 1.1 #10 |
| 성능 힌트 | "이 핸들러는 region 스레드에서 느립니다" 같은 정적/동적 경고 | 4.15 |
| IDE 지원 | 스레드 계약 어노테이션 (`@RegionThread`, `@AsyncSafe`) 으로 린트 | 3.0 |

### 4.31 로케일·접근성

| 항목 | 설명 | 근거 |
|---|---|---|
| 번역 번들 | 언어별 키-값, 플레이어 클라 로케일로 선택 | 1.8 #62 |
| 복수형·서식 | 숫자·날짜·복수형 | - |
| 번역 키 자동 전송 | 바닐라 번역 키는 클라가 번역하게 키로 전송 | - |
| 접근성 | 색 대비, 효과음 대체 텍스트 같은 서버 정책 옵션 | - |

### 4.32 프로토콜 버전(1.21.1) 제약 메모

Furnace는 1.21.1(프로토콜 767)로 고정되어 있다. 이 버전에서 **쓸 수 없거나 형태가 다른 것**이 있다. **[일반지식 + 2차 출처]**

| 항목 | 상태 | 영향 |
|---|---|---|
| `item_model` 컴포넌트 | 1.21.3+ ([CuriosPaper](https://curiospaper.run.place/resource-pack/custom-model-data/) 등) | 1.21.1에서는 `custom_model_data` 정수로 아이템 외형 분기 |
| `equippable` 컴포넌트 | 1.21.2+ ([Oraxen](https://docs.oraxen.com/creating-content/items/components)) | 커스텀 모자/갑옷 외형은 다른 방식 필요 |
| 다이얼로그(서버 구동 대화상자) | 1.21.6+ **[일반지식]** | 1.21.1에서는 컨테이너/책/표지판 UI 사용 |
| `PlayerInput` 패킷 | 1.21.2+ **[일반지식]** — 단, vanilla-diff 문서가 Player Input 0x26을 서버바운드 목록에 올려둔 것과 충돌하니 구현 전 확인 | 입력 이벤트(3.3) 구현 방식에 영향 |
| 프로토콜 업그레이드 | 설계 문서상 비목표 (멀티 프로토콜 미지원) | 위 기능이 필요하면 별도 마일스톤 |

> 이 표의 "1.21.2+" 같은 버전 경계는 대부분 2차 출처나 일반지식이다. **구현 전에 1.21.1 프로토콜 문서로 하나씩 확인해야 한다.**

---

### 4.33 실험 기능(experiment) 시스템

`furnace.properties`에 **불안정하거나 위험한 기능을 `experiment.<이름>=true|false`로 켜고 끄는** 방식. 모든 키의 기본값은 `false`이고, 전부 꺼진 상태가 "바닐라 클라이언트 기본값" 서버다. **[제안]**

```properties
# furnace.properties — 전부 기본 false. 필요한 것만 true로.
experiment.companion-mod=false
experiment.async-events=false
experiment.player-views=false
```

#### 4.33.1 규약 (논의로 확정된 5가지)

| # | 규약 | 이유 |
|---|---|---|
| 1 | **알 수 없는 `experiment.*` 키는 기동 실패** + 유효한 키 목록 출력. 값은 `true`/`false`만 허용하고 `yes`, `ture` 등은 거부 (`Boolean.parseBoolean`의 조용한 `false` 변환 금지) | 현재 `ConfigLoader`는 모르는 키를 조용히 무시하고 boolean 파서가 없다. 오타로 "켰다고 믿었는데 안 켜진" 상태를 막는다 |
| 2 | **`Experiment` enum + 불변 `Set<Experiment>`** 를 `Config`에 하나만 둔다. `Config` 레코드(현재 15개 위치 기반 필드)에 boolean을 필드로 늘리지 않는다. 기동 시 한 번 읽어 불변 스냅샷으로 고정 | region 스레드가 병렬로 돌므로 `Properties`를 스레드에서 직접 읽으면 안 된다 |
| 3 | **플러그인 API 연결**: `server.experiments().isEnabled(...)` 조회, 매니페스트에 "필요 실험" 선언 → 꺼져 있으면 로드 거부 + 이유 안내 | 4.1 |
| 4 | **이름은 `experiment.*`** 로 한다. `feature-flag`는 쓰지 않는다 | 마인크래프트의 feature flags/experimental 데이터팩과 혼동. Furnace는 이미 Feature Flags 패킷을 보낸다 |
| 5 | **수명 관리**: 실험은 승격(일반 설정 또는 기본 켜짐) 또는 삭제로 끝낸다. 테스트는 "전부 꺼짐"과 "하나씩만 켜짐"으로 제한한다. 디스크 포맷을 바꾸는 실험은 `위험` 표시 + 기동 경고 | 플래그 N개 = 조합 2^N. 영구 분기 방지 |

#### 4.33.2 enum에 담을 메타데이터

| 필드 | 설명 |
|---|---|
| `key` | 설정 키 (`companion-mod`) |
| `summary` | 한 줄 설명 (기동 로그와 오류 메시지에 사용) |
| `stage` | `experimental`(언제든 바뀌거나 삭제) / `preview`(호환 약속이 약함) |
| `risk` | `none` / `dangerous`(디스크·보안·바닐라 호환에 영향 → 기동 시 큰 경고) |
| `requires` / `conflicts` | 함께 켜야 하는/같이 켜면 안 되는 실험 |
| `restartRequired` | 처음엔 전부 `true`(기동 시에만 읽음). 런타임 토글은 명령 시스템 이후 선택적으로 |
| `retired` | 승격·삭제된 키. 옛 설정 파일에 남아 있어도 **실패하지 않고** 경고만 낸다 (규약 1의 예외, 설정 업그레이드 보호) |

#### 4.33.3 기동 시 동작

1. 켜진 실험 목록을 기동 로그에 한 번 출력하고, `dangerous`가 있으면 별도 경고 블록을 낸다.
2. 켜진 실험은 **Furnace 소유 메타 파일**에 사용 이력을 기록한다. 바닐라 `level.dat`을 읽지도 쓰지도 않는 현재 구조(vanilla-diff §2.3)라서 별도 파일이 필요하다. 나중에 실험을 껐을 때 "이 월드는 X 실험으로 저장된 데이터가 있다"를 알려주기 위함이다.
3. `requires`/`conflicts` 위반은 기동 실패 (원인 키 명시).
4. 서버 목록 핑에는 켜진 실험을 노출하지 않는다 (정보 노출 방지).
5. 명령 시스템이 생기면 `/furnace experiments`로 상태 조회.

#### 4.33.4 실험 후보 (3, 4절 항목에서 선별) **[제안]**

모두 기본 `false`. "선행"은 이 실험을 만들려면 먼저 있어야 하는 코어 기능이다.

| 키 | 대상 (참조) | 위험 | 선행 |
|---|---|---|---|
| `companion-mod` | 동반 모드 채널·핸드셰이크 (4B). `true`는 이전의 `optional` 정책에 해당. `required` 정책은 두지 않음 | none | 커스텀 페이로드 처리 |
| `async-events` | 비동기 이벤트 핸들러, `CompletableFuture` 결과 (3.0) | none | 이벤트 버스 |
| `cross-region-atomic` | 교차 region 원자 블록 연산·변경 트랜잭션 (4.17) | none | 이벤트 버스, M5 |
| `region-rebalance` | region→스레드 동적 재배정 (3.8) | none | M3 |
| `player-views` | 플레이어별 가짜 블록·엔티티·스코어보드 (4.9) | none | 엔티티, 스코어보드 패킷 |
| `packet-api` | 패킷 이벤트 공개 (3.11, 4.11) | dangerous (보안·성능) | 이벤트 버스 |
| `data-registry-sync` | 커스텀 인챈트·피해 타입 등 데이터 레지스트리 전송 (4.4) | none | 레지스트리 (클라 동작 검증 필요) |
| `custom-dimension-types` | 커스텀 dimension_type/바이옴 전송 (4.4, 4.10) | dangerous (클라 호환) | 위와 동일, 검증 필요 |
| `dual-block-registry` | 서버 논리 블록 ↔ 클라 표현 블록 분리 (4.5) | dangerous (저장 포맷) | 블록 상태 테이블 |
| `custom-entity-types` | 서버 논리 엔티티 (4.6) | none | 엔티티 코어 (M5) |
| `worldgen-hooks` | 월드젠 단계 훅 (3.9) | none | 월드젠 파이프라인 |
| `worldgen-density-override` | density function / noise_settings 교체 (4.10) | dangerous (바닐라 시드 일치 깨짐) | 월드젠 |
| `instance-worlds` | 메모리 전용 인스턴스 월드 (4.10) | none | 월드 API |
| `chunk-snapshots` | CoW 스냅샷·롤백 (4.10, 4.19) | none | 청크 영속성 |
| `plugin-hot-reload` | 플러그인 핫 리로드 (4.1) | dangerous (상태 꼬임) | 격리 클래스로더 |
| `remote-admin-api` | REST/WebSocket 관리 API (4.20) | dangerous (보안) | 인증 체계 |
| `transfer-cookies` | Transfer·쿠키 멀티서버 (4.21) | none | 구성 단계 |
| `anticheat-physics` | 서버 물리·예측 이동 질의 (4.22) | none | 서버 이동 물리 (현재 없음) |
| `virtual-stack-size` | 99 초과 가상 스택 (4.7) | dangerous (저장 포맷) | 컴포넌트 아이템 모델 |
| `fast-redstone` | 레드스톤 고속 모드 (4.2 / G) | dangerous (바닐라와 동작 차이) | 레드스톤 (M5) |
| `mob-spawn-policy` | 스폰 알고리즘 교체 (3.14) | none | 몹 스폰 (M5) |
| `combat-model` | 전투·데미지 모델 교체 (3.14) | dangerous (바닐라와 동작 차이) | 전투 |
| `per-player-time-weather` | 개인 시간·날씨 (4.9) | none | 시간·날씨 패킷 |
| `custom-difficulty` | 난이도 프로파일 (3.14, 4.4) | none | 난이도·게임룰 코어 |
| `custom-gamerules` | 커스텀 게임룰 (4.4) | none | 게임룰 코어 |
| `command-override` | 바닐라 명령 교체·래핑·삭제 (4.3) | dangerous (바닐라 동작 변경) | 명령 시스템 |

> 후보 중 `custom-difficulty`, `custom-gamerules`, `command-override`처럼 사용자 요구가 큰 것은 안정화되면 **실험을 졸업**시켜 기본 켜짐 또는 일반 설정으로 승격하는 것을 전제로 한다.

---

## 4B. 동반 클라이언트 모드 (기본값 아님 — 서버 설정으로 켰을 때만)

> 3, 4절은 바닐라 클라이언트가 기본이다. 이 절은 **서버 운영자가 동반 모드를 켜고 플레이어가 설치한 경우에만** 쓰이는 확장이다. 기본 설정에서는 이 절의 어떤 기능도 활성화되지 않는다. 모든 항목은 [제안]이다.

### 4B.1 이미 쓰이는 사례 **[검증]** (커뮤니티 페이지, 2차)

| 사례 | 하는 일 | 출처 |
|---|---|---|
| Keybind | 서버가 액션 목록을 보내면 클라가 키바인드로 등록. 서버가 검증·실행. 권한 노드, 쿨다운, 256바이트 패킷 상한 | [Modrinth](https://modrinth.com/mod/keybind) |
| InputEngine | 키 눌림/뗌을 경량 페이로드로 서버에 보내 커스텀 이벤트로 발행. Fabric/NeoForge 지원 | [Modrinth](https://modrinth.com/project/cd7YQRjU) |
| KrepAPI | 서버가 추가 키바인드를 정의. 버전 핸드셰이크로 모드를 필수로 요구할 수도 있음 | [moddex](https://moddex.gg/mod/krepapi) |

→ 이 쌍들은 모두 **서버 플러그인 + 클라 모드** 구조이고, 바닐라 서버가 이 클라를 받아주는 방식이다. Furnace는 이 규격을 공식화할 수 있다.

### 4B.2 설계 원칙

1. **기본은 꺼짐**: `experiment.companion-mod=false`(기본)에서는 동반 모드 채널을 등록하지 않고 핸드셰이크도 보내지 않아 바닐라 서버와 동일하게 동작한다. `true`로 켜면 모드가 있는 클라이언트에서만 확장 기능이 활성화되고, 모드가 없는 클라이언트는 항상 폴백으로 접속한다. **모드를 필수로 요구하는 `required` 정책은 두지 않는다** (바닐라 클라이언트 기본값 원칙에 반함). 실험 플래그 규약은 4.33.
2. **능력 협상(capability negotiation)**: 접속 시 `furnace:companion` 채널로 모드 버전·지원 기능 목록을 교환. 서버는 `player.companion().supports(Feature.CUSTOM_KEYBINDS)`로 분기한다.
3. **서버 권위**: 클라 데이터는 신뢰하지 않는다. 입력·상태는 서버가 다시 검증한다 (안티치트 관점).
4. **필수 모드 없음**: 모드 유무와 관계없이 접속은 항상 허용한다. 모드가 없으면 해당 기능은 폴백으로 동작한다.
5. **버전 고정**: 서버가 1.21.1 단일 버전이라 모드도 1.21.1 + 로더 1~2종으로 유지 비용을 낮출 수 있다 **[일반지식]**.
6. **별도 저장소**: 서버 코어와 릴리스 주기를 분리하는 `furnace-companion` 모듈/저장소 **[제안]**.

### 4B.3 기능 아이디어

| # | 기능 | 모드가 없을 때의 폴백 |
|---|---|---|
| 1 | 서버 정의 키바인드 | 명령/채팅 |
| 2 | 5번째 이상 난이도·커스텀 규칙 이름을 설정 화면에 표시 | 액션바/보스바 |
| 3 | 새 명령 인자 파서(색상 선택기, 좌표 피커 등)와 클라 측 자동완성 | 문자열 인자 + 서버 제안 |
| 4 | 커스텀 GUI 화면 (바닐라 컨테이너 레이아웃에 묶이지 않음) | 서버 구동 컨테이너 |
| 5 | 커스텀 HUD 요소 (쿨다운, 체력바, 미니맵) | 액션바/보스바/폰트 UI |
| 6 | 클라 예측 동기화: 커스텀 이동·점프·대시 물리 | 서버 보정(끊김 발생) |
| 7 | 바닐라 모델 형식을 넘는 커스텀 블록 모양 | 표현 블록(리소스팩) |
| 8 | 커스텀 엔티티 모델·애니메이션 (Geckolib 류) | 디스플레이 엔티티 조합 |
| 9 | 커스텀 파티클/후처리 효과 | 바닐라 파티클 |
| 10 | 미니맵·웨이포인트 데이터 채널 | 없음 |
| 11 | 서버 정의 키-값 상태를 클라가 읽는 데이터 채널 | 없음 |
| 12 | 채팅 UI 개선 (채널 탭, 필터) | 단일 채팅 |
| 13 | 배경음악·사운드 믹싱 제어 | 리소스팩 사운드 |
| 14 | 카메라/시점 제어(컷신) | 관전 엔티티 |
| 15 | 아이템 동적 툴팁 | lore |
| 16 | 커스텀 레시피 UI | 바닐라 제작대 |
| 17 | 배치 프리뷰(고스트 블록) | 없음 |
| 18 | 리소스팩 대신 모드에 에셋 내장 (대용량 팩 배포 부담 감소) | 리소스팩 |
| 19 | 서버 접속 전 사전 검증(프로토콜/모드 버전) UI | 접속 후 안내 |
| 20 | 클라 설정 동기화(서버가 권장 설정 제안) | 없음 |

### 4B.4 서버 측 API (제안)

| API | 설명 |
|---|---|
| `Companion.supports(player, feature)` | 능력 질의 |
| `Companion.send(player, channel, payload)` | 전용 페이로드 전송 |
| `CompanionChannel.register(id, codec, handler)` | 채널·코덱 등록 |
| `CompanionHandshakeEvent` | 모드 버전·지원 기능 수신 |
| `CompanionMissingEvent` | 모드가 없는 클라이언트 접속 알림 (폴백 선택용; 거부 불가) |
| `CompanionPayloadEvent` | 모드가 보낸 데이터 수신 (검증 필수) |
| 폴백 선택기 | 같은 기능을 모드 유무에 따라 다른 구현으로 라우팅 |

### 4B.5 주의

- 클라 모드를 쓰면 **"바닐라 클라로 접속"이라는 서버의 장점이 사라질 수 있다.** 그래서 필수 정책을 두지 않고 `experiment.companion-mod` 뒤에서 선택형으로만 제공한다.
- 모드 검증(서명 핸드셰이크)으로 클라를 신뢰하려는 설계는 우회될 수 있다. **[일반지식]** 안티치트 판단은 서버가 해야 한다.
- 3번(새 인자 파서)과 14번(높이 4064 초과 같은 한계)은 모드가 있어도 서버-클라 프로토콜 수준 합의가 필요한 영역이라 난이도가 높다.


---

## 5. 우선순위 제안

### P0 — 이것 없이는 다른 게 못 나간다 (Furnace 현재 공백)
1. **채팅·명령 패킷과 명령 시스템 코어** (현재 없음 → 4.3 전체의 전제)
2. **이벤트 버스** (스레드 계약 포함, 3.0)
3. **플러그인 로더 + 매니페스트 + 수명주기 정리** (4.1)
4. **`-ea` 없이도 켜지는 소유권 가드** (레포 결함 #17)
5. **접속 시퀀스 완성** (게임모드, 체력, 시간, 난이도 등 — 상태가 있어야 규칙 API가 의미 있음)

### P1 — Paper 대비 차별점 (Furnace 설계와 직결)
6. 스케줄러 공개 + 비동기 월드 접근 (4.2)
7. 레지스트리 시스템과 소유권 정리 (4.4)
8. 난이도 프로파일·게임룰 API (3.14, 4.4)
9. 패킷 이벤트 공식 API (3.11, 4.11)
10. 월드젠 단계 훅 (3.9, 4.10)
11. 플레이어 뷰 API (4.9)

### P2 — 콘텐츠 API (게임플레이 구현이 선행되어야 함)
12. 블록 이중 레지스트리, 서버 논리 엔티티 (4.5, 4.6)
13. 인벤토리·GUI (4.8) — 클릭 종류 전체 처리가 선행
14. 컴포넌트 아이템 모델 (4.7)
15. 몹 AI·패스파인딩 (M5)

### P3 — 생태계 인프라
16. 권한·경제 등 서비스 인터페이스 (4.13)
17. 진단·프로파일러 (4.15)
18. 헤드리스 테스트 서버 (4.15)
19. 설정 스키마 통일 (4.14)

### 5.1 추가분 우선순위

| 우선순위 | 항목 | 이유 |
|---|---|---|
| **P0 (이벤트 버스 설계와 동시에)** | 결과 타입(PASS/SUCCESS/FAIL), Cause 체인, 자동 재동기화, 스레드 계약 문서화 (3.0b, 4.16) | 나중에 끼워 넣으면 모든 이벤트 시그니처를 갈아엎어야 한다. 설계 문서 4.4의 "시그니처 먼저 고정" 원칙과 같은 이유 |
| **P0** | 1.21.1 프로토콜 제약 확인 (4.32) | 이벤트·컴포넌트 설계가 이 위에 선다 |
| **P0** | 실험 플래그 시스템 (4.33): 엄격한 boolean 파서, `experiment.*` 오타 거부, 불변 `Experiment` 집합, `retired` 처리 | 이후 모든 불안정 기능이 이 뒤에 숨는다. 먼저 있어야 기능을 코어에 안전하게 넣을 수 있다 |
| **P1** | 변경 트랜잭션 (4.17), 보호 영역 질의 (4.18), 구조화 로깅 코어 (4.19) | Cause와 한 묶음. 교차 region 원자 연산(설계 문서 4.5)과 같은 문제를 푼다 |
| **P1** | 원격 관리 API (4.20), 메트릭 (4.15), 구조화 로깅 | 운영 필수 |
| **P2** | 안티치트 기반 (4.22) | 서버 물리 모델이 생긴 뒤 의미가 있다 |
| **P2** | 데이터팩 통합 (4.23), 성능 튜닝 API (4.24), 맵/책/표지판 UI (4.26) | 게임플레이 구현 이후 |
| **P3** | 멀티서버 코어 (4.21), 접속 큐 (4.29), 프로필 (4.27), 연출 (4.28), 로케일 (4.31) | 생태계 단계 |
| **P3** | 선택형 게임 프레임워크 (4.25) | 코어 밖 공식 모듈로 |
| **별도 트랙** | 동반 클라이언트 모드 (4B) | 서버 코어 안정 후 별도 저장소. 선택형으로만 |
| **상시** | DX (4.30): API 호환성 CI, 폐기 정책 | 1.1의 불만 대부분을 구조적으로 막는 가장 싼 수단 |

### 주의점
- 3, 4절의 상당수는 **게임플레이 시스템이 먼저 구현되어야** 이벤트를 발화할 지점이 생긴다 (몹, 레드스톤, 인벤토리 클릭, 유체 등).
- 클라이언트 동작에 의존하는 항목(블록 이중 레지스트리, 커스텀 속성, 차원 타입 키)은 **실제 1.21.1 클라이언트로 먼저 검증**한 뒤 설계를 확정해야 한다.
- API 안정성이 가장 큰 차별점이 될 수 있다 (1.1). 이를 위해 **API 버전 정책**(semver, 폐기 기간)을 코드보다 먼저 정해 두는 것을 권한다.

---

## 6. 출처 목록

**Paper / Folia 공식·준공식**
- [Paper: Data components](https://docs.papermc.io/paper/dev/data-component-api/)
- [Paper 1.20.6 릴리스](https://papermc.io/news/paper-velocity-1-20-6/)
- [Paper: Commands (Brigadier)](https://docs.papermc.io/paper/dev/commands)
- [Paper: userdev](https://docs.papermc.io/paper/dev/userdev/)
- [Paper: Entity teleport](https://docs.papermc.io/paper/1.20/dev/entity-teleport)
- [Paper: Difficulty javadoc](https://jd.papermc.io/paper/1.21.1/org/bukkit/Difficulty.html)
- [Paper 설정 문서 (readthedocs)](https://paper.readthedocs.io/en/latest/server/configuration.html)
- [Folia: Plugin Compatibility](https://github.com/PaperMC/Folia/wiki/Plugin-Compatibility)

**GitHub 이슈·커밋**
- [Paper #12383 Custom Registry API](https://github.com/PaperMC/Paper/issues/12383)
- [Paper #10818 ItemMeta Destroyable/PlaceableKeys](https://github.com/PaperMC/Paper/issues/10818)
- [Paper #7361 PlayerJoinEvent teleport](https://github.com/PaperMC/Paper/issues/7361)
- [Paper PR #8235](https://github.com/PaperMC/Paper/pull/8235)
- [Paper 커밋 #11186 plugin aliases override vanilla commands](https://ayakael.net/mirrors/papermc/commit/67d414a9274232ad68deb3d9b08bf1f7bc699108)
- [LuckPerms #4121 PlayerLoginEvent](https://github.com/LuckPerms/LuckPerms/issues/4121)
- [ItemsAdder #4687](https://github.com/PluginBugs/Issues-ItemsAdder/issues/4687)

**커뮤니티·가이드·플러그인 (2차 이하 출처)**
- [PaperMC 포럼: components 1.21.4](https://forums.papermc.io/threads/the-problem-with-components-in-paper-1-21-4.1526/)
- [PaperMC 포럼: custom enchantments](https://forums.papermc.io/threads/custom-enchantments.1191/)
- [PaperMC 포럼: TPS 문제](https://forums.papermc.io/threads/17-18-player-tps-problem.571/)
- [Spigot 스레드: PlayerJumpEvent](https://www.spigotmc.org/threads/playerjumpevent-not-found.469072/)
- [Spigot 스레드: Paper with NMS](https://www.spigotmc.org/threads/paper-with-nms-using-gradle.540724/)
- [Aternos: 난이도 설정](https://support.aternos.org/hc/en-us/articles/360047224532-Setting-the-difficulty-for-Spigot-PaperMC-servers)
- [DifficultyManagement](https://hangar.papermc.io/Otsel/DifficultyManagement)
- [MoreRules](https://hangar.papermc.io/GamerDuck123/MoreRules)
- [repack (커스텀 블록)](https://github.com/RATR2/repack)
- [커스텀 블록 가이드](https://guide.astroworldmc.com/how-to-add-custom-blocks-minecraft)
- [Higher Heights 데이터팩](https://modrinth.com/project/higher-heights-datapack)
- [Paper 플러그인 모범 사례](https://www.mcrpg.com/articles/paper-plugin-best-practices)
- [Falix: 플러그인 업데이트 가이드](https://client.falixnodes.net/guides/minecraft-plugins/plugin-updates)
- [Paper vs Purpur 2026](https://space-node.net/blog/paper-vs-purpur-minecraft-server-2026)
- [XGaming: paper-global.yml](https://xgamingserver.com/blog/how-to-configure-your-minecraft-server-paper-global-yml/)
- [DEV: Paper 명령 작성법](https://dev.to/eande171/youre-writing-paper-commands-wrong-2o8i)
- [Keybind (Modrinth)](https://modrinth.com/mod/keybind)
- [InputEngine (Modrinth)](https://modrinth.com/project/cd7YQRjU)
- [KrepAPI (moddex)](https://moddex.gg/mod/krepapi)
- [ServerTap](https://github.com/servertap-io/servertap)
- [Fabric 콜백 튜토리얼](https://wiki.fabricmc.net/tutorial:callbacks)
- [Sponge 문서](https://docs.spongepowered.org/master/en/)
- [CuriosPaper: custom model data](https://curiospaper.run.place/resource-pack/custom-model-data/)
- [NexoMC: ItemModels vs CustomModelData](https://docs.nexomc.com/general-usage/faq/itemmodels-vs.-custommodeldata)
- [Oraxen: Item Components](https://docs.oraxen.com/creating-content/items/components)

**이 레포 문서**
- `docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md`
- `docs/feature-parity/vanilla-diff-2026-10-07.md`
- `LLM-Wiki/wiki/Furnace/아키텍처-개요.md`
