# NMS 어댑터 N2b 설계 — 필드·배열 수준 청크 쓰기 (2026-11-14)

상태: **확정 설계(구현 전)** — 독립 검토(`/mnt/project-files/furnace/reviews/n2b-design-pr351.md`, 1판 조건부 승인 R1~R15 → 2판 재검토 승인, R16·R17 반영).
결정이 필요한 것은 `docs/user-todo/entries/2026-11-14-nms-n2b-design.md`(D1~D4).

근거: 계획 `compat-nms-adapter-plan-2026-11-11.md` 4장 6번, 계획 검토(4.3 "필드·배열 수준은 보기로는 불가능"), N1+N2a 문서
`compat-nms-n1-n2a-2026-11-14.md`, 2차 CSP B6 검토(FAWE NMS 기호 664 중 없음 330), 도미니코 11-14 결정 "N2b 는 설계 먼저".
대조 자료: FAWE 2.16.2 소스(태그 커밋 `eef5a6ff5` = 도미니코 기동 로그의 `v2.16.2+eef5a6f`) `adapter-1_21`·`worldedit-bukkit`·`worldedit-core`,
바닐라 1.21.1 Mojang 이름 소스(`extremeheat/extracted_minecraft_data` `client1.21.1`, 커밋하지 않음). Paper 1.21.1 소스는 PaperMC/Paper 에 1.21.1 ref 가 없어 보지 못했다 — Paper 고유 동작은 "가정"으로 적고 V6 에서 확인한다.

원칙 두 가지를 그대로 따른다.
1. **바닐라(여기서는 같은 플러그인을 올린 Paper 1.21.1)와 관측 결과가 같아야 한다.** 내부 구조·스레드는 자유.
2. **특정 플러그인 저격 금지.** FAWE 는 검증 대상 중 하나다. 설계 단위는 "NMS 청크 객체의 필드·배열을 직접 만지는 방식" 전체다.

---

## 1. 무엇을 지원해야 하나 — 쓰기 방식 셋

플러그인이 청크 내부를 쓰는 방식은 세 층으로 나뉜다. N2a 는 첫째 층의 일부(`Level.setBlock`·`LevelChunk.setBlockState`)만 했다.

| 층 | 방식 | 대표 사용처 | 가로챌 수 있나 |
|---|---|---|---|
| **W1 메서드 쓰기** | `LevelChunkSection.setBlockState(x,y,z,state[,useLocks])`, 살아 있는 섹션의 `PalettedContainer.set/getAndSet/getAndSetUnchecked`, `Heightmap.setRawData/update`, `DataLayer.set`, `LevelLightEngine.checkBlock`, `ServerChunkCache.blockChanged`, `LevelChunk.removeBlockEntity` | 미니게임·건축 플러그인의 "빠른 블록 놓기" 도우미(섹션에 바로 쓰고 청크를 다시 보냄), WorldEdit, Denizen | 예 — shim 메서드 |
| **W2 객체·필드 교체** | 플러그인이 `new LevelChunkSection(...)`·`new PalettedContainer(...)`·`Data` 레코드를 만들어 **`getSections()` 배열 칸에 대입**(FAWE 는 `VarHandle.compareAndSet`), 리플렉션으로 `PalettedContainer.data`·`LevelChunkSection.biomes`·`tickingBlockCount`·`tickingFluidCount` 대입, Paper 가 공개로 넓힌 `chunk.heightmaps`·`blockEntities` 맵 직접 조작, `BlockEntity.remove` 필드 대입 | FAWE(쓰기의 본체), 맵 생성·청크 복원 도구 | 부분 — 3.3 |
| **W3 원시 배열 제자리 쓰기** | `BitStorage.getRaw()`·`Heightmap.getRawData()` 의 `long[]`, `DataLayer.getData()` 의 `byte[]` | FAWE `removeSectionLighting`(빛 0 채우기), 일부 비트 조작 도구 | 아니오 — **지원하지 않음**(3.3) |

FAWE 2.16.2 `v1_21_R1` 의 실제 쓰기 순서(`PaperweightGetBlocks.internalCall`, 바뀐 청크 하나마다):
1. FAWE 작업 스레드에서 블록 엔티티를 지운다(`nmsChunk.removeBlockEntity`, Paper 판별이면 신호기는 `removeBeacon` — `blockEntities` 맵 직접 `remove`, private `removeBlockEntityTicker`·`removeGameEventListener` MethodHandle, `BlockEntity.remove` 필드 대입).
2. `synchronized(nmsChunk)` 안에서 `nmsChunk.getSections()` 배열을 받고, 기존 섹션의 `tickingBlockCount`·`tickingFluidCount` 를 `Field.setShort` 로 0 대입(편집 중 무작위 틱 멈춤).
3. 같은 스레드에서 새 `PalettedContainer`(5인자 생성자: `IdMap, Strategy, Configuration, BitStorage, List`)·새 `LevelChunkSection(states, biomes)` 를 만들어
   worldedit-core `ReflectionUtils.compareAndSet`(= `MethodHandles.arrayElementVarHandle(Object[].class).compareAndSet`, 서술자 `([Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;)Z`)로 배열 칸을 바꾼다. 바이옴만 바꿀 때는 기존 섹션의 `biomes` 필드를 리플렉션으로 대입(블록은 그대로).
4. 하이트맵은 같은 스레드에서 `chunk.heightmaps.get(type).setRawData(...)`, 빛은 relighter(Paper 판별이면 `ThreadedLevelLightEngine.starlight$serverRelightChunks`) 또는 `DataLayer` 직접 쓰기. 블록 엔티티 만들기·엔티티는 `TaskManager.sync`(주 스레드).
5. 끝으로 `setLightCorrect(true)`·`mustNotSave=false`·`setUnsaved(true)`. 청크 패킷은 기본 설정(LIGHTING 부수 효과 켜짐, `DELAY_PACKET_SENDING=true`)에서는 빛 다시 계산 뒤 `PaperweightStarlightRelighter.postProcessChunks` 가, 그 밖에는 callback 이 보낸다. 둘 다 `ChunkMap.getVisibleChunkIfPresent`(리플렉션) → `MinecraftServer.execute` 안에서 `new ClientboundLevelChunkWithLightPacket(...)` 을 `chunkMap.getPlayers` 에게.
6. 읽기(`update()`)는 FAWE 스레드에서 `sectionLock.writeLock()` 을 쥔 채 `getSections()` → 리플렉션 `data`·`storage`·`palette` → `getRaw()`·`valueFor`. Paper 판별이면 `ThreadingDetector` 잠금을 쓰지 않는다.

→ **모든 쓰기·읽기가 region 스레드가 아닌 플러그인 스레드에서, 플러그인 잠금을 쥔 채 일어난다.** 이것이 N2b 의 본질적 충돌이다(절대 규칙 4: 살아 있는 `ChunkSection` 은 주인 region 스레드만 고친다).

**Paper 판별 조건**: FAWE `PaperSupport.isPaper()` 는 `com.destroystokyo.paper.PaperConfig` 또는 `io.papermc.paper.configuration.Configuration` 클래스 유무로 정하고, Furnace 호환 계층은 후자를 둔다(`furnace-fawe-load`). 그래서 FAWE 는 Furnace 에서 Starlight 다시 계산·잠금 없는 경로를 탄다(실측 확인은 V6).

## 2. 바닐라에서 이 쓰기들이 "하지 않는 일" — 후처리 책임표

바닐라 1.21.1 소스 대조(`LevelChunkSection.setBlockState`·`recalcBlockCounts`, `Heightmap.setRawData`, `ChunkAccess.setLightCorrect`, `ChunkHolder.broadcastChanges`, `ChunkSerializer`). 배열 칸 대입·필드 대입은 그 자체로 아무 부수 효과가 없다.

| 파생 상태 | W1 섹션 `setBlockState` | W2 섹션 교체 | 바닐라에서 누가 맞추나 | **Furnace 책임** |
|---|---|---|---|---|
| 섹션 개수(nonEmpty·ticking 블록·ticking 유체) | 칸마다 ±1. 유체는 **비어 있지 않으면(물 포함)** tickingFluid ±1 | 새 섹션 생성자가 `recalcBlockCounts` — 유체 있는 칸을 nonEmpty 에 **두 번**, tickingFluid 는 `isRandomlyTicking` 유체(용암)만 | 섹션 자신 | 두 경로를 **그대로 포팅**(특이점 포함, 3.4·R6). 필드 대입은 값 그대로(음수 포함) |
| 무작위 틱 대상 | 개수에서(물을 놓은 섹션도 대상이 됨 → `tickChunk` 의 무작위 위치 뽑기 진행) | 개수에서 | — | 같게. 서버 `tickingCount`·T-053 구역 마스크를 바닐라 개수에서 유도(3.4) |
| 하이트맵 4종 | **안 고침** | **안 고침** | 플러그인(`setRawData`) 또는 아무도. 저장 뒤 다시 읽으면 낡은 값 그대로 | **낡은 채로 둔다** — 고정 하이트맵(3.5), 로드 때도 이어 감 |
| 빛 | **안 고침** | **안 고침** | 플러그인(relight·`checkBlock`) 또는 아무도 | **낡은 채로 둔다**(결정 D2) |
| 하늘빛 원천(바닐라 `LevelChunk.skyLightSources`, Furnace `ChunkLight.lowestSource`) | 안 고침(`LevelChunk.setBlockState` 만 고침) | 안 고침 | 다시 계산 때 | 낡은 채로, **다시 계산(3.8) 때 함께 다시 셈** |
| 클라이언트 | 안 보냄 | 안 보냄 | 플러그인(청크 패킷·`blockChanged`) | 안 보냄. 같은 틱에 이미 모인 칸 변경 목록(`ChunkLevel.changed`)은 바닐라처럼 **보낼 때의 현재 블록**을 읽는다(확인됨) |
| 저장 표시(unsaved) | 안 함 | 안 함 | 플러그인 `setUnsaved(true)`, 또는 `setLightCorrect` (바닐라는 그 안에서 `setUnsaved(true)`) | 안 함(결정 D3). Furnace 직렬화 캐시는 버린다 |
| POI | 안 고침 | 안 고침 | 아무도 | 안 고침 |
| 블록 엔티티 | 안 고침 | 안 고침 | 플러그인 | 안 고침. 블록과 안 맞는 블록 엔티티는 바닐라처럼 틱 때 "invalid state" 경고 한 번 뒤 건너뛴다(같은지 검증 항목) |
| 이웃·모양 갱신, 게임 이벤트, Bukkit 이벤트 | 없음 | 없음 | — | 없음 |
| **Furnace 전용 캐시** | — | — | 해당 없음 | **반드시 버리거나 다시 만든다**(3.4 단일 진입점 + 필드 전수 시험, 위험 R1) |

요약: **바닐라에 있는 파생 상태는 바닐라처럼 플러그인 몫으로 두고, Furnace 에만 있는 캐시는 Furnace 가 책임진다.** 이 경계가 "결과가 같다"를 지키는 기준선이다.

## 3. 설계 — 거울(mirror)과 들여오기(import)

### 3.1 한 문장
플러그인에게는 바닐라 모양의 **거울 객체**(진짜 필드를 가진 `LevelChunk`·섹션 배열·`LevelChunkSection`·`PalettedContainer`·`Data`)를 주고,
플러그인 스레드는 거울과 **주인이 공개한 불변 스냅샷**만 만지게 하며(기다리지 않음), Furnace 가 **주인 region 스레드에서** 거울의 바뀐 부분을 서버 청크로 맞춘다(바닐라가 하지 않는 일은 하지 않고).

### 3.2 객체 정체성과 거울 상태 기계

**정체성.** N2a 는 `getSections()` 를 부를 때마다 새 배열·새 보기를 만든다. W2 는 **같은 배열을 다시 받아 칸을 CAS** 하므로 정체성이 안정해야 한다.
- 로드된 청크 한 세대(로드 → 언로드)마다 `LevelChunk` shim 하나, 그 안에 `LevelChunkSection[]` 배열 하나, 칸마다 섹션 객체 하나. 섹션은 `states`·`biomes` 필드를, 컨테이너는 `data` 필드(바닐라처럼 **volatile**)를 실제로 가진다.
- 캐시는 청크 키 + 로드 세대의 약한 참조. 단 **섹션·컨테이너·`Data` 는 그 세대의 청크 shim 을 강하게 참조**한다 — 플러그인이 섹션 하나만 쥐고 있어도 세대 전체(배열 포함)가 살아 있어 다음 `getSections()` 가 같은 배열을 준다(R14: 아니면 FAWE CAS 의 expected 가 어긋나 "Skipping invalid null section").
- 언로드 뒤 다시 로드하면 새 객체(바닐라도 새 `LevelChunk`). 죽은 세대의 거울에 쓰면 월드에 닿지 않는다(바닐라도 떨어진 `LevelChunk` 에 써질 뿐).

**Furnace 는 거울 객체의 모니터를 쓰지 않는다**(R12). FAWE 는 `synchronized(nmsChunk)`·(비 Paper 경로) `synchronized(section)` 을 쓴다. Furnace 내부 동기화는 거울마다 따로 둔 원자 변수로만 하고, 플러그인이 볼 수 있는 객체에 `synchronized`·`wait` 를 걸지 않는다.

**섹션 거울 상태 기계**(R4). 거울 섹션마다 `seq`(플러그인 쓰기 순번, volatile int), `placed`(Furnace 가 마지막으로 놓은 `states`·`biomes`·`data` 객체 — 훑기의 기준값), `journal`(W1 칸 일지)를 둔다.

| 상태 | 뜻 | 들어오는 길 | 나가는 길 |
|---|---|---|---|
| **붙음·깨끗** | 서버 섹션의 보기. 메서드는 살아 있는 값(주인) 또는 공개 스냅샷(그 밖)으로. 필드를 처음 읽으면 그 판의 스냅샷에서 바닐라 배치 `Data` 를 실체화해 `placed` 로 기록 | 처음 생성, 들여오기 완료 | 플러그인 쓰기 → 더러움 |
| **더러움(일지)** | W1 칸 쓰기만 있음. `journal` 에 (칸, 상태, useLocks) 순서대로. 거울의 실체화 `Data` 에도 바로 반영(자기가 쓴 것을 읽는다) | W1 메서드 | 들여오기(일지 재생) → 붙음·깨끗 |
| **떨어짐** | 플러그인이 `new` 로 만든 섹션이 배열 칸에 들어왔거나, `states`·`biomes`·`data` 필드가 `placed` 와 다른 객체가 됨. 바뀐 **구성 요소**(블록/바이옴/개수) 표시를 가진다 | W2 대입(장벽 알림 또는 훑기 발견) | 들여오기(구성 요소 단위) → 붙음·깨끗 |

규칙:
1. 플러그인 쓰기는 모두 `seq++`(장벽·shim 메서드가 올림). 들여오기는 시작 때 `seq` 를 읽고, 끝에 다시 읽어 **바뀌었으면 대기 표시를 다시 세운다**(들여오는 동안의 쓰기를 잃지 않는다). 일지는 원자적으로 떼어 내 재생한다.
2. 새로 고침(다른 스레드 읽기 판 갱신)은 **붙음·깨끗**일 때만, `data` 필드와 `placed` 를 함께 CAS 로 바꾼다. 떨어짐·더러움 섹션은 새로 고치지 않는다 — 그래서 훑기가 새로 고침을 플러그인 쓰기로 오인하지 않고(기준이 `placed`), 새로 고침이 플러그인 대입을 덮지 않는다.
3. 배열 칸은 `VarHandle.getAcquire` 로 읽는다(FAWE CAS 와 happens-before).

### 3.3 쓰기를 알아채는 길
**정확성은 맞춤 지점 + 훑기가 보장하고, 쓰기 장벽은 지연을 줄이는 최적화다**(R1). 그래서 특정 플러그인의 호출 순서·클래스 이름에 기대지 않는다.

1. **훑기(정식 경로)** — 거울이 살아 있는 청크만 주인 region 이 틱마다 한 번: 배열 칸 객체, 섹션의 `states`·`biomes`, 컨테이너의 `data` 를 `placed` 와 정체성 비교하고, **원시 개수 필드 3개**(`nonEmptyBlockCount`·`tickingBlockCount`·`tickingFluidCount`)도 마지막 값과 비교한다(R6: `Field.setShort` 같은 원시 대입은 정체성 비교로 못 잡는다). 청크당 비교 약 150개. 대입 뒤 1틱 안에 잡는다.
2. **맞춤 지점(바닐라 의미가 있는 메서드)** — `setUnsaved`·`setLightCorrect`, 청크 패킷 생성자, `starlight$serverRelightChunks`·`checkBlock`, 저장·언로드·region 나누기/합치기 직전, 그리고 **주인 스레드(또는 전역 빌림)에서 그 청크의 블록을 읽는 모든 진입** — shim(Level·ServerLevel 좌표 메서드 포함)뿐 아니라 Furnace 의 `ChunkColumn`·World 읽기(Bukkit `getBlock`·`getState`·`getChunkSnapshot` 이 여기로 온다). 청크별 "대기 있음" volatile 표시를 보고 있으면 먼저 맞춘다(R10, 비용은 volatile 읽기 하나).
3. **쓰기 장벽(로드 때 바이트코드 변환, 최적화)** — 알림을 받으면 그 청크에 대기 표시를 세워 다음 맞춤 지점·틱 시작에 바로 맞춘다.
   - `java/lang/invoke/VarHandle` 의 `compareAndSet/set/setVolatile/setRelease/getAndSet` 중 **첫 인자 서술자가 `[Ljava/lang/Object;`** 인 호출 → `NmsArrays.*`(원래 연산 뒤, 그 배열이 등록된 거울 배열이면 알림). 이 바늘은 `NmsClassTransformer` 의 `NMS_HINTS` 게이트 **밖에** 따로 둔다 — FAWE 의 CAS 는 "minecraft" 문자열이 없는 worldedit-core `ReflectionUtils` 에 있다.
   - NMS 힌트가 있는 클래스: `java/lang/reflect/Field.get*/set*` → `NmsReflect.fieldGet/fieldSet`, shim 클래스 필드의 `GETFIELD/PUTFIELD` → shim 접근자, `findSetter/unreflectSetter/findVarHandle` → 알림을 다는 손잡이.
   - `AASTORE` 는 다루지 않는다(타입 판정에 스택 분석 = 클래스 로딩이 필요, COMPUTE_FRAMES 를 피한 이유와 같다). 훑기가 잡는다.
   - 비용: 바뀐 호출 자리에서만 정적 메서드 하나와 정체성 조회 하나.

**W3 미지원**: 거울의 `long[]`(`BitStorage.getRaw`·`Heightmap.getRawData`)·`byte[]`(`DataLayer.getData`)는 사본이라 제자리 쓰기는 서버에 닿지 않는다. 맞춤 지점에서 "실체화 때의 해시와 다르면 (플러그인, 종류)마다 한 번 경고"(선택). 가정: Starlight 의 `getDataLayerData` 가 사본이면 FAWE `removeSectionLighting` 은 Paper 에서도 효과가 없다 — 그렇다면 "미지원"이 오히려 Paper 와 같다(V6 확인, R15).

### 3.4 맞추기(들여오기) — 주인 region 스레드에서

**대기 상태는 서버 청크에 둔다**(R8). `ChunkColumn` 에 거울 참조와 "대기 있음" volatile 표시를 두고, 들여오기는 그 표시를 보고 **그때의 거울 상태로 서버를 맞추는 멱등 연산**이다. 대기열 작업이 아니므로 순서 뒤바뀜이 없고, region 나누기·합치기 때 청크와 함께 옮겨진다. 언로드 때 남은 대기는 버린다(바닐라: 죽은 `LevelChunk` 에 쓴 것 — 단 언로드 직전 맞춤 지점에서 한 번 맞춘 뒤 저장 판단, 3.8).
다른 스레드의 알림은 표시를 세우고 그 청크의 주인 region 을 깨우기만 한다.

**단일 진입점** `ChunkColumn.importSection(index, components, ...)`(R9). 디스크 로드 경로(`loadSectionIndexed` — `dirty=true`·하이트맵 UNSET)를 다시 쓰지 않는다. 그 경로는 D3-A·고정 하이트맵과 반대다.

섹션 하나를 맞출 때:
1. **구성 요소 단위**(R3): 일지만 있으면 **그 칸만 재생**(바닐라 `LevelChunkSection.setBlockState` 원시 의미: 개수 ±1, 그 밖 부수 효과 없음 — 그 사이 게임이 바꾼 다른 칸은 그대로). 떨어짐이면 바뀐 구성 요소만: `states`/`data` 가 바뀌었으면 블록, `biomes` 가 바뀌었으면 바이옴, 배열 칸 객체가 바뀌었으면 둘 다, 개수 필드만 바뀌었으면 개수만.
2. 검사: `storage.getBits()`·`getRaw().length`·palette 기준(**Configuration 은 보지 않는다** — FAWE 바이옴 컨테이너는 `SECTION_STATES.getConfiguration(FakeIdMapBiome, bits)` 로 Configuration 과 실제 storage·palette 가 어긋나도 바닐라에서 정상 동작한다, R13a). 팔레트 항목 null·범위 밖·바이옴 번호 틀림이면 들여오지 않고 (플러그인, 사유)마다 한 번 WARN.
3. **하이트맵 고정(3.5)** — 블록을 바꾸기 전에.
4. 섹션 내용 교체(`ownSection` 으로 COW·`lightFrozen` 공유를 끊고), **개수**: 블록 교체면 바닐라 `recalcBlockCounts` 포팅(유체 칸 nonEmpty 이중 셈, tickingFluid 는 무작위 틱 유체만), 일지 재생이면 칸마다 바닐라 ±1(물 포함 tickingFluid), 개수 필드 대입이면 그 값. 바닐라 개수 3개는 들여온 섹션만이 아니라 **모든 서버 섹션에 늘 보관**한다(재검토 R17: 칸 쓰기마다 바닐라 ±1, 로드·생성 때 `recalcBlockCounts` — 물을 놓으면 tickingFluid 가 늘어 그 섹션이 무작위 틱 대상이 되는 차이는 양동이로 물을 놓는 일반 플레이에도 이미 있으므로 **별도 T 항목**으로 고친다). 그리고 **무작위 틱 대상 = `tickingBlock > 0 || tickingFluid > 0`**(바닐라 `isRandomlyTicking`)으로 T-053 구역 마스크를 고친다. 무작위 틱 안에서 칸을 고를 때 쓰는 기존 칸 기준 `tickingCount` 와는 분리한다(이름은 구현 때 정리, 위험 R8).
5. Furnace 전용 캐시 무효화(아래 목록, 필드 전수 시험으로 지킴).
6. **하지 않는 것**: 저장 표시, 빛, 하늘빛 원천, 하이트맵 재계산, POI, 블록 엔티티, 패킷, 이웃 갱신, 이벤트(2장 표).
7. 거울 섹션 `placed` 를 들여온 객체로 갱신, `seq` 비교(3.2 규칙 1).

**전용 캐시 후보 목록**(N2b-0 에서 확정, 검토 R9 가 찾은 것 포함): `cachedSerializedBody`·`inflightSerializedBody`·`cachedMotionBlockingHeightmap`·`cachedWorldSurfaceHeightmap`·`cachedSnapshot`(`touch`), `bodyWaits`/`lightWaits`, `ChunkSection.tickingCount`·T-053 마스크, `scratch`, `lightFrozen`·COW(`ownSection`), `PristineChunk`(ChunkPipeline 사본), `surface`·`oceanFloor`·`motionBlocking`(고정 모드가 다룸), N2a `NmsChunks` 캐시, 붙은 거울의 실체화 `Data`, 공개 스냅샷(3.6). `ChunkLight.lowestSource` 는 바닐라 파생 상태라 낡은 채로 둔다(2장). 방송 목록 `ChunkLevel.changed` 는 보낼 때 현재 블록을 읽으므로 그대로.
**필드 전수 시험**: `ChunkColumn`·`ChunkSection`·`ChunkLight` 의 필드를 리플렉션으로 나열해 "들여오기가 다룸 / 블록 파생 아님" 표에 없는 필드가 있으면 실패 — 앞으로 생길 캐시도 잡는다.

**블록 엔티티·하이트맵·빛의 다른 스레드 쓰기**(R5b): `removeBlockEntity`·`blockEntities` 맵 쓰기·private `removeBlockEntityTicker`/`removeGameEventListener`·`BlockEntity.remove` 대입, `setRawData`, `DataLayer.set`·`queueSectionData` 도 그 청크의 거울에 **한 줄 순서 일지**로 쌓고, 주인이 섹션 들여오기와 같은 순서로 적용한다. `blockEntities` 맵은 거울 맵(주인 스냅샷에서 실체화)이고, 그 맵의 `remove`·`put` 이 일지에 들어간다.

### 3.5 고정 하이트맵 — 낡은 값을 바닐라처럼 유지하기
Furnace 하이트맵은 일부가 **지연 계산**이다(`MOTION_BLOCKING`·`OCEAN_FLOOR` 는 칸 쓰기 때 "모름"으로 두고 읽을 때 지금 블록에서 계산, `MOTION_BLOCKING_NO_LEAVES` 는 늘 계산).
블록이 `setBlock` 으로만 바뀌는 세계에서는 이것이 바닐라의 증분 갱신(`Heightmap.update`)과 같다. 그러나 원시 쓰기 뒤에는 다르다 — 바닐라는 낡은 값을 들고 가고, Furnace 는 새 블록에서 "고쳐진" 값을 낸다(비·눈·몹 스폰·`getHighestBlockYAt`·청크 패킷 하이트맵이 갈린다).

- 원시 쓰기(W1 섹션 쓰기·W2)를 처음 받는 청크는 **고정 모드**: 그 순간 네 종류를 모두 지금 값으로 실체화해 저장 배열에 두고, 그 뒤로는 바닐라 `Heightmap.update` 포팅 경로로만 고친다.
- `setRawData` 는 바닐라처럼 길이가 맞으면 그대로 복사, 다르면 경고 + 그 종류만 다시 계산(`primeHeightmaps`).
- **저장 → 다시 읽기**(R7): 바닐라는 디스크 `Heightmaps` 를 읽어 낡은 값을 그대로 쓰고 없는 종류만 prime 한다. Furnace 는 지금 로드 때 다시 계산한다. 그래서 로드 때 **저장된 하이트맵을 다시 계산한 값과 비교해 다르면 그 청크를 고정 모드로 시작**한다(비용은 비교 한 번). 바닐라·Paper 가 만든 낡은 하이트맵 월드를 가져올 때도 같은 이득이 있다.
- 비용은 원시 쓰기를 받았거나 저장값이 다른 청크에만 든다.

### 3.6 스레드별 규칙 — 기다리지 않는 읽기

**다른 스레드의 읽기는 기다리지 않는다**(R2). Paper 에서 FAWE 는 작업 스레드에서 섹션을 바로 읽는다(경쟁 읽기). 여기서 블로킹으로 주인에게 건너가면 Paper 에 없던 기다림이 생긴다 — FAWE 는 `sectionLock.writeLock()`·`synchronized(nmsChunk)` 를 쥔 채 읽으므로, 그 잠금을 원하는 region·전역 스레드나 **전역이 빌리고 있어 틱을 쉬는 region** 과 서로 기다리는 교착(T-035 꼴)이 나고, 청크마다 틱 경계를 기다리면 큰 편집이 분 단위가 된다.

- **공개 스냅샷**: 거울이 살아 있거나 "읽기 요청" 표시가 있는 청크에 대해, 주인 region 이 틱 끝에 그 청크의 불변 스냅샷(섹션 팔레트 사본 — `blocksReadCopy`·`ChunkSnapshot` 의 COW 재사용, 하이트맵·블록 엔티티 목록 포함)과 판 번호를 volatile 로 내놓는다. 바뀌지 않은 섹션은 앞 스냅샷의 사본을 그대로 공유한다.
- 다른 스레드의 읽기는 그 스냅샷을 **기다리지 않고** 읽는다. 불변식: **비 region 스레드에 넘어가는 청크 shim 은 반드시 공개 스냅샷을 가진다**(재검토 R16 — 이미 쥔 shim 의 `getSections`·`getBlockState` 에는 "로드 안 됨"이라고 답할 길이 없으므로, 스냅샷이 없는 shim 을 내주지 않는다).
  1. 기다리지 않는 조회(`getChunkNow`·`getChunkAtIfLoadedImmediately`·`getChunkAtIfCachedImmediately`)는 스냅샷이 없으면 **바로 null** 을 주고 읽기 요청 표시만 남긴다(다음 틱 끝에 스냅샷이 생긴다).
  2. Furnace 의 `getChunkAtAsync` 는 Paper 처럼 주인 스레드에서 끝내고, 그 직전에 스냅샷을 공개한다. 그래서 FAWE 의 `thenApply(getHandle)` 도 주인 스레드에서 돈다. FAWE `getChunk()` 는 `levelChunk` 를 캐시하고 `ensureLoaded` 는 조회를 한 번만 한 뒤 future 를 `get()` 하므로 재시도 루프는 생기지 않는다(재검토 확인).
  3. 기다리는 조회(`getChunk(..., true)`·`CraftChunk.getHandle`)는 바닐라 `ServerChunkCache.getChunk` 의 주 스레드 join 과 같은 간선이라 새 기다림이 아니다(지금처럼 로드 안 된 청크의 동기 로드는 이유를 담은 예외).
- 자기가 쓴 것은 거울(일지·떨어진 섹션)에서 읽는다.

| 부르는 스레드 | 읽기 | W1 메서드 쓰기·블록 엔티티·하이트맵·빛 쓰기 | W2 객체·필드 교체 |
|---|---|---|---|
| 주인 region 스레드 / 전역 빌림 구간 | 대기 있으면 먼저 맞춘 뒤 살아 있는 값 | 바로 서버에(바닐라 원시 의미, 대기 있으면 먼저 맞춤) | 거울에 → 다음 맞춤 지점에서(같은 스레드면 다음 블록 읽기 전에) |
| region 이 아닌 스레드(비동기 작업·FAWE 작업 스레드) | 공개 스냅샷(기다리지 않음) | **D1**: 거울 일지 → 주인이 그 칸만 재생(추천) / 예외 | 거울에 → 훑기·맞춤 지점 |
| 다른 region 의 스레드 | **D4**: 비 region 과 같은 규칙(추천) / N2a 처럼 `ThreadOwnershipException` | D4 와 같음 | 거울에(순수) → 훑기·맞춤 지점 |

D4 설명(R11): 기다리지 않는 스냅샷 읽기와 일지 쓰기는 다른 region 스레드에서도 교착이 없다. Paper 에서는 어느 스레드에서 불러도 되므로, 예외를 두면 Paper 에서 되는 플러그인(다른 청크를 만지는 이벤트 리스너 등)이 Furnace 에서 막힌다. 바꾸면 N2a 의 "Now" 다리 규칙이 compat 쪽에서 넓어진다(서버 다리는 그대로, shim 이 스냅샷·일지로 우회).

### 3.7 경쟁 — 들여오기 창
- **W1 일지**: 그 칸만 재생하므로 그 사이 게임이 바꾼 다른 칸은 그대로다. 같은 칸을 게임과 플러그인이 같은 틱에 바꾸면 늦게 적용된 쪽(플러그인)이 이긴다 — Paper 의 경쟁 쓰기와 같은 성질.
- **W2 섹션 교체**: 배열 칸 대입과 들여오기 사이(최대 1 region 틱)에 플레이어가 그 섹션을 바꾸면 들여오기가 덮어쓴다. Paper 도 FAWE 가 섹션을 만든 뒤 CAS 하기 전의 주 스레드 쓰기는 잃는다 — 같은 성질의 경쟁이고 창의 크기만 다르다. **가정으로 문서화**하고, 필요하면 후속으로 "대입 뒤 서버 쓰기 재적용"(판 번호 비교)을 더한다.
- **Bukkit 읽기**: 주인 스레드의 읽기는 3.3-2 로 맞춘 뒤 읽으므로 Paper 처럼 새 블록을 본다. 다른 스레드의 Bukkit 비동기 읽기(스냅샷)는 최대 1틱 옛 블록을 볼 수 있다(Paper 도 경쟁 읽기).

### 3.8 빛·클라이언트·저장
- **빛**: `getLayerListener(...).getDataLayerData(SectionPos)` 는 공개 스냅샷의 빛 사본 `DataLayer`, `DataLayer.set`·`queueSectionData` 는 거울 일지(3.4). Paper 전용 `starlight$serverRelightChunks(coords, chunkCb, progressCb)` 와 `checkBlock` 은 주인에서 대기를 맞춘 뒤 Furnace 빛 엔진으로 그 청크들을 처음부터 다시 계산(`LevelLight.lightChunk` + 가장자리, **하늘빛 원천 `lowestSource` 다시 세기 포함**), 바뀐 빛은 바닐라 `sectionLightChanged` 처럼 저장 표시와 빛 갱신 패킷 대상에 넣는다. 결과는 "그 블록의 올바른 빛"이라 Paper 의 Starlight 재계산 결과와 같아야 한다(V4·V6).
  플러그인이 다시 계산을 부르지 않으면 낡은 빛 그대로(결정 D2). 낡은 상태에서 이어지는 증분 전파는 바닐라 엔진과 Starlight 사이에서도 다르므로 칸 단위 일치를 약속하지 않는다(가정).
- **클라이언트**: `new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, ...)` shim 은 "그 청크를 다시 보내라" 표지만 들고, `connection.send` 될 때 주인 region 에서 대기를 먼저 맞춘 뒤 그 플레이어에게 `Region.resendChunk` 와 같은 길로 보낸다(패킷 자체는 N3a 몫, 3.9).
- **저장**: `setUnsaved(true)` → `markDirty`. **`setLightCorrect` 도 바닐라처럼 저장 표시**를 한다(R5c). `mustNotSave` 는 받기만 한다(Furnace 에 해당 동작 없음, V6 에서 Paper 의미 확인). 저장·언로드 직전에는 대기를 맞춘 뒤 판단한다.

### 3.9 다른 묶음에 기대는 것
- **N3a**: `ChunkMap.getVisibleChunkIfPresent`(리플렉션)·`ChunkHolder`·`chunkMap.getPlayers`·청크 패킷 보내기, `MinecraftServer.execute`.
- 티켓: `addRegionTicket`·`addTicketAtLevel`·`removeTicketAtLevel`·`TicketType.create` → Furnace 청크 티켓(플러그인당 한도 결정 11-08 그대로).
- 레지스트리·값: `Block.BLOCK_STATE_REGISTRY`(IdMapper), `Registry.asHolderIdMap`, `registryOrThrow(BIOME)`(PR #347 R10), Paper 표면(`moonrise$getEntityLookup`, `MCUtil.MAIN_EXECUTOR`, `ChunkHolderManager.UNLOAD_COOLDOWN`).
- FAWE 가 실제로 편집하려면 이 밖에 `SharedConstants`·`DataComponentPatch.CODEC` 등 어댑터 선택·초기화 기호가 먼저 풀려야 한다(기동 로그의 없음 330 대부분은 N2b 밖). **N2b 의 완료 기준은 "필드 수준 쓰기 계약"(V1~V5)이고, FAWE 실제 편집은 별도 통합 단계(N2b-6)다.**

## 4. 대안 비교

| 안 | 내용 | 장점 | 단점 | 판단 |
|---|---|---|---|---|
| **A. 거울 + 공개 스냅샷 + 들여오기(이 문서)** | 3장 | region 규칙 그대로, 다른 스레드가 기다리지 않음(교착 없음), 플러그인 순서·클래스 이름에 기대지 않음(훑기가 정식 경로), 바닐라 부수 효과를 흉내 낼 자리가 한 곳(`importSection`) | 객체 정체성·상태 기계·변환 규칙 추가, W2 들여오기 창(≤1틱) 경쟁, W3 미지원, 스냅샷 메모리 | **추천** |
| B. 서버 섹션을 바닐라 배치로 바꿔 그대로 노출 | Furnace `ChunkSection` 자체를 shim 이 넘겨줌 | 창 없음, 복사 없음 | 다른 스레드가 살아 있는 섹션을 고침 = 절대 규칙 4 위반·잠금 필요, scratch·COW·`lightFrozen` 최적화와 충돌, 서버 전반 재작성 | 기각 |
| C. 플러그인 스레드가 편집 동안 region 을 빌림 | `RegionBorrow` 를 비동기 스레드로 넓힘 | 쓰기 순서가 정확 | FAWE 의 무거운 계산 동안 region 틱 정지(MSPT), 플러그인 잠금과 엮인 교착 위험(지금도 전역만 빌린다), 바닐라 객체 → Furnace 변환은 어차피 필요 | 기각(전역 스레드는 기존 빌림 그대로) |
| D. 맞춤 지점만(`setUnsaved`·청크 보내기) | 계획 검토 원안 | 단순 | 플러그인 호출 순서에 기댐, 저장 표시를 안 하는 플러그인은 영영 안 들어감 | A 의 한 겹으로 흡수 |
| E. FAWE 전용 어댑터를 Furnace 가 제공 | 결정 카드 A 안 | 빠름 | 11-11 도미니코가 B(범용)를 고름, 저격 금지 원칙 | 기각 |
| F. 지금처럼 등급 C(던짐) | — | 비용 없음 | FAWE·WorldEdit 계 블록 편집 불가 유지 | 기각 |

## 5. 바닐라와 같은지 검증하는 방법

| # | 무엇 | 어떻게 | 단계 |
|---|---|---|---|
| V1 | 자료 구조 의미(섹션·팔레트·`BitStorage`·생성자·`setBlockState` 개수·`recalcBlockCounts`·바이옴 컨테이너·`setRawData`) | **바닐라 jar 를 부트스트랩한 정답기**: 시험이 `main` 의 번들러 jar 에서 바닐라 `LevelChunkSection`(dvj)·`PalettedContainer`(dvq)·`Heightmap` 을 직접 만들어 같은 입력을 넣고, Furnace shim 결과와 칸·개수 3개·`isRandomlyTicking`·`hasOnlyAir`·하이트맵 raw 를 대조. 무작위 입력 수천 개, **물·용암·물 잠긴 블록·cave_air 필수**, **FAWE 가 만드는 모양**(5인자 생성자 + Configuration 과 어긋난 바이옴 컨테이너, `GlobalPalette` 바이옴, `ZeroBitStorage`) 그대로. 하이트맵 정답기는 바닐라 `ProtoChunk`(바이옴 레지스트리)가 필요해 부트스트랩 비용을 N2b-0 에서 먼저 확인 | N2b-0·1·2 |
| V2 | 부수 효과 없음 | Furnace 시험 서버에서 W1·W2 뒤 단언: 패킷 0, 빛 배열·하늘빛 원천 그대로, 하이트맵 그대로(고정 모드), dirty 그대로(`setUnsaved`·`setLightCorrect` 뒤에만 참), POI·블록 엔티티 그대로, 이웃 블록 그대로. 저장 → 다시 로드 뒤 하이트맵이 낡은 값 그대로(R7) | N2b-2·4 |
| V3 | Furnace 전용 캐시 빠짐 없음 | (a) **필드 전수 시험**(3.4) (b) 무효화 호출을 하나씩 지우는 **돌연변이 시험**(d11 방식) (c) 들여오기 뒤 청크 직렬화 바이트 = 같은 블록을 디스크에서 읽어 들인 청크의 직렬화 바이트 — **하이트맵은 빼고 비교**(고정된 낡은 값과 다시 계산한 값은 정당하게 다르다) 또는 일관된 상태에서 비교 | N2b-0·4 |
| V4 | 다시 계산한 빛 | 들여오기 + relight 뒤의 빛·하늘빛 원천 = 같은 블록 배치를 새로 로드해 `lightChunk` 한 결과. 실제 Paper 와는 V6 | N2b-5 |
| V5 | 동시성 | 비동기 스레드 여럿이 거울을 쓰는 동안 region 틱·나누기·합치기·언로드·저장. **FAWE core 의 실제 CAS 자리**(`ReflectionUtils.compareAndSet` 과 같은 모양의 플러그인 클래스, "minecraft" 문자열 없음)가 장벽 또는 훑기로 1틱 안에 잡히는지. **플러그인 잠금 + 전역 빌림 + 비동기 읽기** 장면(FAWE `sectionLock` 을 쥔 스레드가 읽는 동안 전역이 그 잠금을 원하고 그 region 을 빌림)에서 교착 없음. 들여오기 중 쓰기·새로 고침 겹침(상태 기계 규칙 1·2). 단언: 서버 쪽 소유 예외 0, 찢어진 들여오기 0, 잃은 쓰기 0, 언로드된 거울 쓰기는 월드에 안 닿음, 다른 스레드 읽기 대기 시간 분포 | N2b-3·4 |
| V6 | 실제 플러그인 결과 | 테스트 노트북: 같은 시드 월드에서 **Paper 1.21.1 + FAWE 2.16.2** 와 **Furnace + FAWE 2.16.2** 로 같은 명령(`//set`·`//replace`·`//copy`+`//paste`·`//undo`, 작은 것과 64³)을 돌린 뒤 region 파일을 칸 단위로 비교(블록·바이옴·하이트맵·빛·블록 엔티티 NBT). 더해서 **편집 직후 같은 틱의 Bukkit 읽기**(다른 플러그인 표본), **물을 놓은 섹션의 무작위 틱 진행**, Paper 판별(`PaperSupport.isPaper`)·Starlight `getDataLayerData` 사본 여부·`clearCounts` 반응·`mustNotSave` 의미(R15). 빠른 블록 놓기 표본 플러그인(섹션 `setBlockState` + `setUnsaved` + 청크 패킷)과 squaremap 읽기도 같은 방식. Paper jar 는 이 컨테이너에서 못 받으므로 도미니코 컴퓨터·노트북에서 | N2b-6 |

## 6. 단계별 구현 계획

각 단계는 PR 하나, 독립 검토 뒤 병합. 단계 사이에 이미 있는 기능은 깨지지 않는다(N2b 기호는 그 단계 전까지 계속 함정). 검토 의견대로 **다른 스레드 읽기를 거울·들여오기 앞으로** 옮겼다(FAWE 는 쓰기 전에 반드시 읽는다).

| 단계 | 내용 | 끝 기준 | 모델·노력치 제안 |
|---|---|---|---|
| **N2b-0 조사** | Furnace 블록 파생 캐시 전수 목록·필드 전수 시험 뼈대, 여러 플러그인(FAWE·WorldEdit·squaremap·Denizen + 빠른 블록 놓기 라이브러리 표본)의 필드·배열 수준 기호 표(`nms-source-survey.py` 확장), V1 정답기 뼈대·`ProtoChunk` 부트스트랩 비용 | 목록·표·정답기 시험 1개 | Sonnet high |
| **N2b-1 떨어진 객체** | 생성자들(`LevelChunkSection` 2종, `PalettedContainer` 공개·Paper 5인자, `Strategy`·`Configuration`, 팔레트 4종, `SimpleBitStorage`·`ZeroBitStorage`, `IdMapper`), 진짜 필드(`data` volatile), 바닐라 개수 두 경로, 안정 정체성 캐시(섹션 → 청크 강한 참조) | V1 통과, 서버 쓰기 없음 | Sonnet high |
| **N2b-2 W1(주인 스레드) + 고정 하이트맵** | 다리 메서드(`setSectionStateRawNow`·`sectionCountsNow`/`setSectionCountsNow`·`setHeightmapRawNow`), 주인 스레드 원시 쓰기, 바닐라 개수 3개 보관·무작위 틱 대상 유도, 고정 하이트맵(바닐라 `Heightmap.update` 포팅) + **로드 때 저장값 비교로 고정 시작** | V1·V2 | Opus high |
| **N2b-3 다른 스레드 읽기** | 공개 스냅샷(틱 끝, COW 재사용, 판 번호, 읽기 요청 표시), 기다리지 않는 읽기·한도 대기, 블록 엔티티 목록 스냅샷, D4 에 따라 다른 region 스레드 규칙 | V5(읽기·교착 장면) | Opus high |
| **N2b-4 거울·들여오기** | 상태 기계(3.2), `ChunkColumn` 대기 표시·`importSection` 단일 진입점·필드 전수 시험, 칸 일지(D1), 구성 요소 단위 들여오기, 훑기(정체성 + 원시 개수), 맞춤 지점(Bukkit·World 읽기 진입 포함), 쓰기 장벽(VarHandle 은 힌트 게이트 밖, Field·GETFIELD/PUTFIELD), 블록 엔티티·하이트맵·빛 일지, 저장·언로드·나누기 훅 | V2·V3·V5 | Opus xhigh(동시성) |
| **N2b-5 빛·클라이언트** | `DataLayer`·`LevelLightEngine` 표면, `starlight$serverRelightChunks`·`checkBlock`(하늘빛 원천 포함), 청크 패킷 shim(N3a 와 함께), `setLightCorrect`(저장 표시)·`mustNotSave` | V4 | Opus high |
| **N2b-6 통합** | FAWE 실제 jar 로 없는 기호 재측정 → 나머지 표면(어댑터 선택·초기화 기호) 묶음, V6 Paper 대조 | V6 차이 0(또는 문서화된 가정만) | Opus high + 노트북 실측 |

N2b-0·1 은 N3a 와 파일이 겹치지 않아 병행할 수 있다. N2b-5 의 패킷 부분과 N2b-6 은 N3a 뒤.

## 7. 위험

| # | 위험 | 영향 | 줄이는 법 |
|---|---|---|---|
| R1 | Furnace 전용 블록 파생 캐시를 하나라도 빠뜨림(앞으로 생길 캐시 포함) | 바닐라와 조용히 다른 결과(가장 위험) | `importSection` 단일 진입점, **필드 전수 시험**(새 필드가 생기면 실패), V3 돌연변이 시험·직렬화 바이트 동치 |
| R2 | 다른 스레드 읽기가 기다림을 만들어 교착·처리량 저하 | 서버 멈춤(정확성) | **기다리지 않는 공개 스냅샷**이 기본(3.6), 한도 대기만, Furnace 는 플러그인 모니터를 쓰지 않음(R12), V5 교착 장면 |
| R3 | 거울 상태 기계 경쟁(들여오기 중 쓰기, 새로 고침 오인, 대입 유실) | 잃은 쓰기·되돌림 | 3.2 상태 기계(`seq`·`placed`·CAS 새로 고침), V5 |
| R4 | W2 들여오기 창(≤1틱) 동안 게임 쓰기를 덮어씀 | 드문 경쟁에서 칸 차이 | W1 은 칸 일지라 없음. W2 는 Paper 도 같은 성질 — 가정으로 문서화, 필요 시 재적용(3.7) |
| R5 | W3 원시 배열 쓰기를 쓰는 플러그인 | 쓰기가 사라짐 | 지원 안 함 문서화 + 맞춤 지점 해시 경고, 사용처가 나오면 그 배열만 살아 있는 사본 고려 |
| R6 | 쓰기 장벽 바이트코드 변환의 부작용 | 검증기 오류·성능 | 장벽은 최적화일 뿐(정확성은 훑기), VarHandle 은 서술자로 좁게, `AASTORE` 제외, NmsLinker 기존 시험 틀(스택 모양 유지), 비용 측정 |
| R7 | 거울·스냅샷 메모리(청크 하나 수십 KB) | 큰 FAWE 작업에서 힙 | 지연 실체화(필드를 읽은 섹션만), 바뀌지 않은 섹션은 앞 스냅샷 공유, 세대 단위 수명(강한 참조는 플러그인이 쥔 동안만), 언로드 때 버림, 작업 중 측정 |
| R8 | 개수 의미 차이(바닐라 3개 vs Furnace 칸 기준 `tickingCount`·-1=모름) | 무작위 틱 대상·위치 차이 | 바닐라 3개를 따로 보관해 대상 판정에 쓰고, 칸 고르기용 Furnace 값과 분리, "모름"은 별도 표시, V1 에 물·용암 |
| R9 | 낡은 빛이 이후 증분 갱신에서 바닐라와 다르게 진화 | 빛은 엔진이 달라 원래 약속 못 함 | 하이트맵은 고정 모드로 정확히(저장·로드 포함), 빛은 가정으로 문서화(D2) |
| R10 | 잘못된 입력(팔레트 null·길이 틀림)을 거부 | 바닐라는 받아들여 나중에 깨짐 → 관측 차이 | 거부 + 경고(세계를 깨는 쪽으로 같아질 이유는 없다고 봄). Configuration 어긋남은 정상 입력으로 받음 |
| R11 | N3a·기타 기호가 늦으면 FAWE 실측이 늦음 | 완료 확인 지연 | N2b 완료 기준을 계약(V1~V5)으로, FAWE 는 N2b-6 통합으로 분리 |
| R12 | Paper 1.21.1 고유 동작을 소스로 확인 못 함 | 가정이 틀릴 수 있음 | 가정 목록(R15 항목)을 V6 확인 항목으로 |

## 8. 도미니코 결정 (요약, 상세는 user-todo 항목)
- **D1** region 이 아닌 스레드의 섹션 메서드 쓰기: A 거울에 칸 일지로 쓰고 주인이 그 칸만 1틱 안에 반영(Paper 처럼 동작, 추천) / B 예외(엄격).
- **D2** 플러그인이 빛을 다시 계산하지 않을 때: A 바닐라·Paper 처럼 낡은 빛 그대로(추천) / B Furnace 가 들여온 섹션의 빛을 자동 계산(끄는 키, 바닐라와 다름).
- **D3** 들여오기 뒤 저장 표시: A 바닐라처럼 플러그인이 `setUnsaved`·`setLightCorrect` 해야 저장(추천) / B 들여오면 자동 저장 표시(데이터 잃음 방지, 바닐라와 다름).
- **D4** 다른 region 스레드에서 남의 청크 내부를 읽고 쓰기: A 비동기 스레드와 같은 규칙(스냅샷 읽기·일지 쓰기, Paper 처럼 동작, 추천) / B 지금(N2a)처럼 오류.

## 9. 독립 검토 반영 기록 (PR #351, 1판 → 2판)
보고서 `/mnt/project-files/furnace/reviews/n2b-design-pr351.md`. R1(VarHandle 장벽 게이트 밖·훑기가 정식 경로·AASTORE 제외) → 3.3. R2(기다리지 않는 공개 스냅샷, 단계 순서) → 3.6·6장. R3(칸 일지·구성 요소 단위) → 3.4·3.7. R4(상태 기계) → 3.2. R5(하늘빛 원천·다른 스레드 블록 엔티티 쓰기·`setLightCorrect`·패킷 시점) → 1장·2장·3.4·3.8. R6(바닐라 개수 특이점·원시 개수 훑기) → 2장·3.3·3.4. R7(저장 뒤 고정 하이트맵·`getRawData` W3) → 1장·3.5. R8(청크별 동기화 표시) → 3.4. R9(단일 진입점·필드 전수 시험) → 3.4·V3. R10(Bukkit 읽기 맞춤) → 3.3·3.7. R11 → D4. R12(모니터 금지) → 3.2. R13(Configuration 무시·V 빈틈) → 3.4·5장. R14(강한 참조) → 3.2. R15(Paper 가정) → 1장·V6.
재검토(2판, 승인): R16(스냅샷 없는 shim 을 비 region 스레드에 내주지 않음, 한도 대기 문장 삭제) → 3.6. R17(바닐라 개수 3개를 모든 섹션에, 물 tickingFluid 차이는 별도 T 항목) → 3.4.
