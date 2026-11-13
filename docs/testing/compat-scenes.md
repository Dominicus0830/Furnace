# 호환 기준 장면 (T-075 / O13 P-O13-14)

Bukkit·Paper 호환 계층(`furnace-compat-paper`)의 **핫 경로가 지금 무엇을 돌려주는지**를 장면 10개로 굳히고, 같은 장면으로 시간을 잰다. 호환 계층 최적화 TASK(T-070 거울 정리·T-071 `handleFor`·T-072 상태 id 표·T-073 감싸개 캐시·T-074 `getOnlinePlayers`·T-076 이벤트 게으르게·T-078 감싸개 읽기, ASM 사전 거르기 P-O13-10, T-013 송신 경로의 ProtocolLib 순서 계약)가 "결과가 같다"를 **저장소 안의 시험으로** 남기게 하려는 것이다.

공용 장치는 서버의 장면 라이브러리·IdentityKit 을 그대로 쓴다(`docs/testing/scenes.md`, `docs/testing/identitykit.md`): `LibraryScene`(한 장면 = 증명·벤치 두 모드), `SceneHash`·`Golden`(금 파일), 새 JVM 모드. 이 장면들이 증명하는 것도 같다 — **"옛 커밋 == 새 커밋"** 이지 Paper 와의 동일성이 아니다(읽기 쪽 Paper 대조는 호환 시험의 몫).

위치
- 장면·목록·시험: `furnace-compat-paper/src/test/java/org/domi/furnace/compat/paper/` (`Scene*.java`, `CompatSceneLibrary`, `CompatSceneLibraryTest`) 와 `.../compat/nms/SceneAsmPrefilter.java`(`mayNeed` 가 패키지 비공개라 같은 패키지)
- 금 파일: `furnace-compat-paper/src/test/resources/identity/scene-compat-<이름>.txt` (서버 시험 소스의 도우미 클래스를 `testImplementation(serverTest)` 로 받는다 — `furnace-compat-paper/build.gradle.kts`)
- 벤치: `furnace-compat-paper/src/bench/java/.../CompatSceneBench.java`

## 장면

| 이름 | 무엇을 굳히나 | 기대는 쪽 | 모드 |
|---|---|---|---|
| `compat-block-reads` | 1.21.1 **모든 블록 상태 26,684개**를 FAST 로 깔고 `getType`·`getBlockData().getAsString`·`isEmpty`·`isLiquid`·`getState` 종류·위치를 상태 2048개마다 한 줄 해시. 못 읽은 수 0, Material 1052종 | T-072 | 새 JVM |
| `compat-world-handles` | `Compat.world` 동일성(1·2·4 스레드가 20,000번씩 불러도 객체 하나), 블록·엔티티·플레이어·Location 이 같은 월드 객체를 낸다, 같은 읽기를 **스레드 종류별**(소유 region·다른 region·전역·시험·비동기)로 했을 때 값 또는 예외 종류(`ThreadOwnershipException`) | T-071·T-072 스레드 축 | 새 JVM |
| `compat-entity-reads` | 엔티티 종류 **129개 전부**(스폰 실패 2종 포함 — `fishing_bobber`·`wolf`)를 스폰해 감싸개 종류·`getType`·`getName`·`getLocation`·`getWorld`·`instanceof`·`equals` 해시, 감싸기 동일성(같은 객체·UUID·번호·`cachedEntity`), 플레이어 읽기 | T-078·T-073 | 새 JVM |
| `compat-wrapper-cache` | 살아 있는 감싸개 1500개(>`PRUNE_AT` 1024)를 쥔 채 새 엔티티 300개 처음 감싸기(놓침 경로), 엔티티 400개 제거 뒤 감싸개, 쥔 것의 절반을 놓고 GC 한 뒤 다시 감싸기의 읽기값(GC 시점과 무관하게 같다) | T-073 | 새 JVM |
| `compat-held-item` | 플레이어 인벤토리 **칸 거울** 의미 28줄: 쓰기가 칸에 닿는다·칸 쪽 쓰기가 거울에 보인다·같은 칸 거울 200개 중 하나로 쓰면 모두 본다·칸을 거울 없이 바꾸면 쥔 거울이 그 순간 값으로 **떨어진다**·GC 로 치워져도 의미가 같다·단축 칸 9개·`getContents` 거울·빈 손(`setType` 해도 칸 안 바뀜)·서버 인벤토리의 최종 상태 | T-070 | 새 JVM |
| `compat-event-conversion` | 처리기 안에서 `BlockPhysicsEvent`(칸 8종×바뀐 상태 2종)·`PlayerMoveEvent`(5종)·`InventoryMoveItemEvent`(3종)가 보는 값(`getType`·`getLocation`·`getItemInMainHand`·`getOnlinePlayers()` 수)과 쓰기(취소·`setItem`·**제자리 수정이 `getItem()` 으로 다시 보이고 Furnace 쪽 결과에 반영됨** — 반론 09(b)) | T-076·T-070·T-072 | 새 JVM |
| `compat-online-players` | `getOnlinePlayers()` 의 **집합**·크기·`stream().count()`·불변성(`add`·`clear` → `UnsupportedOperationException`)·같은 접속자는 같은 `Player` 객체·나갔다 들어온 뒤. 순서는 보지 않는다 | T-074 | 새 JVM |
| `compat-online-order` | `getOnlinePlayers()` **순서만**(지금은 접속 순과 같다. T-074 가 C5 로 의도적으로 바꾸는 쪽) | T-074 | 새 JVM |
| `compat-asm-prefilter` | `NmsClassTransformer.mayNeed` 경계 사례 210개(바늘 14종 × 맨 앞·맨 끝·가운데·한 글자 모자람·마지막 글자 틀림·접두부 겹침·빈 배열 등)의 판정 해시 + **실제 jar 13개 클래스 전부**(기본·버전 접미사 두 옵션)의 (판정, 변환 결과 바이트) 해시 | P-O13-10 | 같은 JVM |
| `compat-protocollib-order` | ProtocolLib 다리가 가로챈 패킷이 클라이언트에 닿는 순서 5장면: 안 바꿈·바꿈(값·취소·다른 종류)·리스너 안 송신(**지금 패킷 앞**)·묶음(리스너 안 송신은 **묶음 앞**)·add_entity 취소 후 묶음으로 바꿔 보내기 + 뒤따르는 메타데이터(**묶음 뒤** — 순서 계약 A). 리스너가 본 순서도 같이 적는다 | T-013 (`wave2-rebuttal-compat.md` 2장 A~D) | 새 JVM |

규칙(장면을 더할 때도 같다)
- 줄에는 정체 해시·엔티티 번호·UUID·시각·플레이어 스폰 좌표를 넣지 않는다(플레이어 스폰 x·z 는 서버를 띄울 때마다 다르다 — y 와 월드 이름만 본다).
- 장면이 가리키는 **예외 종류**는 결과의 일부라 적지만 메시지는 적지 않는다(문구 고침이 결과 변경이 아니다).
- **지금 동작이 틀린 것으로 알려진 값은 굳히지 않는다.** 움직이거나 제거된 뒤의 엔티티 `getLocation()`·`isValid()` 가 만든 시점 값을 돌려주는 것(A16-01, #230 이 바로잡는다)은 장면에서 일부러 뺐다 — 그 PR 이 금 파일을 고쳐야 하는 일이 없게. `getOnlinePlayers()` 순서(C5)만 따로 `compat-online-order` 로 떼어 T-074 가 그 한 파일만 다시 만들게 했다.
- 새 장면 = `CompatScene`(서버를 띄우는 것)이나 `LibraryScene` 을 구현 + `CompatSceneLibrary.all()` 한 줄 + `-Dfurnace.identity.bless=scene-compat-<이름>`(새 파일이라도 PR 본문 "금 파일 갱신" 절과 독립 검토 승인이 필요하다 — `identitykit.md`).

## 증명 모드(평소 시험)

`CompatSceneLibraryTest`(CI `test` 의 `:furnace-compat-paper:test`)가 장면마다 금 파일과 비교한다. 새 JVM 장면은 약 4~6초씩, 전체 약 40초(4코어 로컬). 금 파일 갱신:
```
./gradlew -PskipOracle=true :furnace-compat-paper:test --tests '*CompatSceneLibraryTest*' -Dfurnace.identity.bless=scene-compat-held-item
```
(`-Dfurnace.*` 는 시험 JVM 으로 그대로 넘어간다 — `furnace-compat-paper/build.gradle.kts`.)

**금 파일이 의존하는 것**: `compat-asm-prefilter` 는 ASM 버전과 `realWorld` 의존성 jar 버전(`build.gradle.kts` 에 고정)에 달려 있다 — 그 버전을 올리는 PR 이 사유를 적어 다시 만든다. 나머지는 어댑터·서버 동작에만 달려 있다.

## 벤치 모드(수동 실행)

O15 결정(야간 벤치 미승인)대로 **CI 에 안 넣는다.** 벤치는 `src/bench` 라 평소 `test` 요약에 "건너뜀"이 안 섞인다(컴파일만 `test` 가 확인한다).
```
./gradlew -PskipOracle=true :furnace-compat-paper:compatSceneBench                                  # 전부
./gradlew -PskipOracle=true :furnace-compat-paper:compatSceneBench -Dfurnace.bench.scenes=compat-held-item,compat-block-reads
```
결과 `furnace-compat-paper/build/bench/CompatScene-<장면>.json`(+`.lines.txt`). 칸은 서버 장면 벤치와 같다(`docs/testing/bench.md`): `step_mean/p50/p95/max_ms`·`total_ms`·`allocBytesPerStep`·`hash`. **한 걸음 = 장면이 정한 묶음 하나**(`unit` 칸):

| 장면 | 한 걸음(FULL) | 보는 TASK |
|---|---|---|
| `compat-block-reads` | 모든 상태 26,684개 읽기 한 번(25번) | T-072 |
| `compat-held-item` | 같은 칸에서 `getItemInMainHand` 2000번 부르고 스택을 쥐기(20번) — 거울 목록이 자란다 | T-070 |
| `compat-entity-reads` | 감싼 8마리에게 `getLocation`·`getWorld`·`getType`·`getName` 5000번 | T-078 |
| `compat-wrapper-cache` | 감싼 것 1500개가 있는 채 새 감싸개 300개 처음 만들기(10번) | T-073 |
| `compat-world-handles` | `Compat.world` 2,000,000번(1 스레드) | T-071 |
| `compat-event-conversion` | `BlockPhysicsEvent` 2000번 발행(처리기 1개, 30번) | T-076 |
| `compat-online-players` | 접속자 24명에서 `getOnlinePlayers().stream()…count()` 20,000번 | T-074 |
| `compat-asm-prefilter` | jar 13개 전체 훑기·변환 5번 | P-O13-10 |
| `compat-protocollib-order` | 리스너(프록시)를 거치는 채팅 5000개 보내고 틱 1번 | T-013 ProtocolLib 변형 |

같은 `hash` 일 때(= 같은 의미) 옛·새 커밋을 번갈아 재서 비교한다. 절대 시간으로 막지 않는다.

**첫 측정값**(이 PR 의 기준 커밋, 4코어 컨테이너, 두 번 — 실서버 값이 아니라 장면 크기 의존 값, 실행마다 ±40% 흔들렸다): `compat-held-item` 한 걸음(`getItemInMainHand` 2000번) 평균 약 100~180ms(호출당 약 50~90µs, 같은 칸 거울 목록이 자라는 최악 장면 — O13 보고서의 17~372µs 와 같은 범위), `compat-block-reads` 한 번(상태 26,684개 × 읽기 11종) 약 250~270ms, `compat-online-players` 한 걸음(접속자 24명, 20,000번) 약 227ms(호출당 약 11µs), `compat-world-handles` 한 걸음(`Compat.world` 2,000,000번) 약 87ms(호출당 약 43ns).

## ProtocolLib 변형(T-013 측정)

T-013 의 "서버 루프 CPU −69%" 는 다리가 없는 장면 값이다(`wave2-rebuttal-compat.md` P-O13-14). `compat-protocollib-order` 의 벤치 걸음이 "리스너 한 개가 채팅을 지나는" 변형이다 — T-013 PR 은 옛·새 커밋에서 이 벤치의 `step_*` 을 같이 적는다(진짜 ProtocolLib 라이브러리는 저장소에 넣지 않았다. 리스너 목록과 채널 프록시를 흉내 내는 것은 `ProtocolLibBridgeTest` 와 같은 방법이다). 다리 비용(패킷당 53~115ns)이 몇 %인지는 이 변형의 `step_mean_ms` 와 리스너 없는 같은 장면의 차이로 본다.

## 한계(알고 쓴다)
- **헤드리스 서버 하나, 플레이어는 가짜 클라이언트**다. 실제 플러그인이 일으키는 호출 비율·GC 압력은 아니다 — 그래서 "실서버 몫"의 절대값이 아니라 옛·새 비교용이다.
- `getLocation`·`isValid()` 의 낡은 값(A16-01)은 일부러 안 굳혔다(위). #230 이 병합된 뒤 "움직인 뒤 지금 값" 장면을 `compat-entity-reads` 에 더하는 것은 그 PR 의 몫이다.
- ProtocolLib 순서 장면은 **진짜 ProtocolLib 이 아니라 다리가 가로채는 자리를 흉내 낸 것**이다. 진짜 라이브러리의 리스너 호출 순서(스레드 포함)는 못 본다.
- `compat-asm-prefilter` 의 jar 말뭉치는 `realWorld` 13개다(JDK 클래스 전체는 아니다 — JDK 빌드마다 클래스 목록이 달라 금 파일이 흔들린다). 사전 거르기 판정 자체는 경계 사례 210개가 맡는다.
- 스폰·접속 좌표처럼 서버를 띄울 때마다 달라지는 값은 읽지 않는다. 같은 JVM 두 번째 실행이 달라질 수 있는 장면(엔티티 든 것)은 모두 새 JVM 에서 돈다.
