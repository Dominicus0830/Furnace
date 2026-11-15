# NMS 어댑터 N2b-4 — 거울과 들여오기 (2026-11-15)

설계: `compat-nms-n2b-design-2026-11-14.md` 3.2~3.5·3.8·6장 N2b-4 줄. 앞 단계: `compat-nms-n2b1-2026-11-14.md`(PR #365), `compat-nms-n2b2-2026-11-14.md`(PR #373),
`compat-nms-n2b3-2026-11-14.md`(PR #382). 결정: 도미니코 11-14 D1~D4 모두 A. 이 단계에 걸리는 것은 **D1**(다른 스레드의 메서드 쓰기 = 칸 일지, 주인이 1틱 안에 재생),
**D2**(섹션 교체 때 빛 계산 안 함), **D3**(저장 표시 없이 들여온 섹션은 저장하지 않음 + 내려갈 때 경고 한 줄), **D4**(다른 region 스레드 = 비동기 스레드 규칙).

## 1. 무엇이 생겼나

플러그인이 NMS 청크 객체에 **아무 스레드에서나** 쓴 것이 바닐라·Paper 와 같은 결과로 서버 청크에 들어간다. 쓰는 길은 네 가지다.

| 쓰기 | 예 | 주인 region 스레드 | 그 밖의 스레드(FAWE 일꾼·비동기·다른 region) |
|---|---|---|---|
| W1 메서드 쓰기 | `LevelChunkSection#setBlockState`, 블록·바이옴 `PalettedContainer#set/getAndSet`, `LevelChunk#setBlockState`, `Heightmap#setRawData/update`, `setUnsaved` | 서버에 바로(N2b-2 그대로) | **칸 일지**에 쌓고 주인이 그 칸만 재생(보통 같은 틱, 늦어도 틱 끝). 쓴 스레드는 자기 쓰기를 곧바로 읽는다(덧칠). 섹션 개수 필드는 바닐라처럼 바로 ±1 |
| W2 배열·필드 대입 | `sections[i] = new LevelChunkSection(..)`(AASTORE·VarHandle CAS), `biomes` 필드 대입, `PalettedContainer.data` 대입·`read`, 개수 필드 `setShort` | 그 칸 읽기·`setUnsaved`·Bukkit 진입에서 맞춘다 | 틱 끝 **훑기**가 바뀐 구성 요소만 들여온다. 쓰기 장벽(아래)이 알리면 틱을 기다리지 않는다 |
| 읽기 | `getBlockState`, 섹션·컨테이너 읽기, `PalettedContainer.data` 리플렉션 읽기 | 살아 있는 값(그 칸이 맞춰지지 않았으면 먼저 맞춤) | 공개 스냅샷(N2b-3) + 자기 쓰기 덧칠. `data` 필드는 읽는 순간 바닐라 배치로 실체화 |
| W3 제자리 배열 | `BitStorage.getRaw()[k] = ..`, `Heightmap.getRawData()[k] = ..` | **미지원**(설계 3.3) — 사본이라 서버에 닿지 않는다 | 같음 |

들여오기는 바닐라에 없는 경로라 바닐라가 하지 않는 일은 하지 않는다: **저장 표시·빛·하늘빛 원천·하이트맵 재계산·POI·블록 엔티티·패킷·이웃 갱신 없음**(하이트맵은 고정 모드로 낡은 값을 든다 — N2b-2). 플러그인이 `setUnsaved(true)` 를 부르면 그 앞의 쓰기를 먼저 들여온 뒤 저장 표시를 세운다.

### FAWE 2.16.2 흐름(`PaperweightGetBlocks.call`)이 어떻게 지나가나
1. `clearCounts(existing)` — 개수 필드 셋 `setShort(0)`: 장벽 알림 → 주인이 개수만 들여온다(서버 섹션이 무작위 틱 대상에서 빠진다 — Paper 와 같은 의도).
2. `newChunkSection(.., existing.getBiomes())` — 기존 섹션 data 를 리플렉션으로 읽는다: `Field.get(data)` 앞 장벽이 붙은 보기의 `data` 를 그 순간 내용으로 실체화한다.
3. `setSectionAtomic` — worldedit-core `ReflectionUtils.compareAndSet`(Object[] VarHandle, "minecraft" 문자열 없는 클래스): 장벽(이름 힌트 게이트 밖)이 알림 → 주인이 새 섹션을 들여오고 **그 객체를 그 자리의 보기로 붙인다**(정체성 유지 — FAWE 의 다음 CAS 기대값). 빠진 옛 섹션은 그 순간 내용으로 굳혀 떼어 낸다.
4. `setBiomesToChunkSection` — `biomes` 필드 대입: 바이옴만 들여오고 대입한 컨테이너를 보기로 붙인다. 빠진 옛 컨테이너는 옛 내용으로 굳는다.
5. `setHeightmapToGet` — 살아 있는 하이트맵 `setRawData`: 일지(HM_RAW), 주인이 순서대로 재생.
6. `nmsChunk.setUnsaved(true)` — 일지(UNSAVED): 앞선 쓰기 재생·들여오기 뒤 저장 표시.

## 2. 구조

### 2.1 서버 쪽(`ChunkColumn`·`Region`·`World`)
- **단일 진입점 `ChunkColumn.importSection(index, states, biomes, counts)`**(설계 R9): null 인 구성 요소는 그대로. 블록·개수를 바꾸면 먼저 하이트맵 고정. `ownSection`(COW·빛 작업 얼림 끊기) → 블록 교체(개수를 안 주면 바닐라 필드 값 = 옛 블록의 개수 그대로 — 바닐라는 컨테이너 data 를 갈아도 다시 세지 않는다) → 바이옴 교체 → `touch()`. 저장 표시는 세우지 않고 `importedUnsaved` 만(D3).
- **대기 표시**: 컬럼에 `mirrorPending`(volatile, CAS 0→1 을 이긴 쪽만 주인 작업을 보낸다), `mirrorHeld`(대기 중에만 거울을 강하게 쥔다 — 설계 R14), 월드 전체 `World.mirrorPendingChunks`(0 이면 맞춤 지점 비용이 volatile 읽기 하나).
- shim 칸(`ShimRef`)이 shim 과 그 거울을 **약한 참조로 함께** 쥔다. 다른 로드의 거울이 부탁하면 거절(그 거울은 죽음 — 설계 N2b-1 검토 R3).
- **맞춤 지점**: ① 다른 스레드의 부탁 작업(`Region.syncMirrorTask`), ② Bukkit 진입(`WorldHandle.ownedRegion` → `World.syncMirrorIfPending`; 다리의 주인 읽기·쓰기도 이 길), ③ 틱 끝 공개 직전(`publishReadViews` — 거울 있는 청크는 대기가 없어도 훑는다 = 정식 경로), ④ 저장 전(`saveDirtyChunks` → `syncAllMirrors`), ⑤ 내리기 직전(`unloadMirror`).
- **D3 경고**: 내릴 때 맞춘 뒤 `importedUnsaved && !dirty` 면 `[NMS] 청크 x, z (차원) 가 저장 표시 없이 플러그인이 바꾼 섹션을 든 채 내려간다 — … LevelChunk#setUnsaved(true) …` 한 줄(서버 전체 처음 20건, 그 뒤 1000건마다). 그 청크에 예약 틱이 남아 있으면 저장 표시가 없어도 저장되므로(`writeChunkTicks`) 문구가 "예약 틱이 남아 있어 이 청크는 저장되고 그 변경도 함께 저장된다(Paper 에서는 남지 않을 수 있다)"로 바뀐다(검토 R5, 5장). 저장하면(`clearDirty`) 표시가 내려간다. N2b-2 원시 쓰기(주인 스레드 섹션 쓰기)도 같은 표시를 세운다.
- 재생성(`inheritLoadToken`): 대기 표시·거울을 새 컬럼에 잇고 내용 판(`modCount`)을 옛 것보다 크게.
- 맞추기 예외는 틱을 깨지 않고 `[NMS] 거울 맞추기 실패` 경고(처음 5건은 쌓임까지, 그 뒤 100건마다).

### 2.2 다리(`ChunkInternals`, 불안정 내부 API) 새 메서드
`attachChunkShim(.., ChunkMirror)`, `requestMirrorSync`, `importSectionNow`(상태 번호·바이옴 번호·길이 검사 뒤 `importSection`), `contentStampNow`, `publishChunkViewNow`(주인: 지금 판을 내고 돌려준다 — 들여온 객체를 붙이기 전에, 검토 R1), `ChunkView.contentStamp()`, 인터페이스 `ChunkMirror { sync(); published(view); unloaded(); }`.

### 2.3 호환 쪽 — 청크 거울 `FurnaceChunkMirror`(shim 하나에 하나, 플러그인에 보이지 않음)
- **기준(placed)**: 자리마다 Furnace 가 마지막으로 놓은 섹션, 그 섹션의 `biomes` 값, 컨테이너의 `data`, 개수 필드 기대값(`expected`, 3×n).
- **sync()**(주인만 — 다른 스레드가 부르면 아무것도 안 함): ① 잠금 안에서 일지 떼어 내기와 훑기 계획을 한 순간에(들여올 떨어진 컨테이너는 이때 **잡는다**) → ② 잠금 밖에서 일지 재생(바닐라 원시 의미) → ③ 잠금 한 번에: 자리마다 옛 보기 떼어 내기(계획 뒤 그 옛 객체 앞으로 일지에 든 줄은 먼저 재생 — 일지 줄마다 쓴 객체를 단다) → 새 내용 읽기 → 들여오기, 다 들여온 뒤 **새 판 공개 → 그 판으로 붙이기**(5장). **재생이 훑기보다 먼저**: 배열에서 빠진 옛 섹션에 쓴 칸은 새 섹션이 덮는다(Paper 에서도 교체된 섹션에 쓴 것은 남지 않는다).
- **구성 요소 단위**(설계 R3): 배열 칸이 바뀌면 블록·바이옴·개수 모두, `data` 만 바뀌면 블록, `biomes` 필드면 바이옴, 바이옴 컨테이너 `data` 면 바이옴, 개수 필드만이면 개수만. 개수 들여오기는 일지 ±1 과 섞이지 않게 `expected += 들여온 값 - 계획 때 기대값`.
- **검사**: 팔레트 항목 null·서버 레지스트리에 없는 값·저장소 칸 수 틀림이면 그 섹션을 들여오지 않고 (거울, 원인)마다 한 번 `[NMS] 청크 x, z (차원) 거울: 섹션 i 의 내용을 들여오지 않는다: …` 경고(Configuration 은 보지 않는다 — 설계 R13a).
- **붙이기·떼어 내기**: 들여온 플러그인 섹션·컨테이너는 그 자리의 보기가 된다(공급원 `FurnaceSectionSource` — 칸 읽기·쓰기가 청크 shim 으로). 빠진 옛 것은 들여오기 **전에** 그 순간 내용으로 굳힌다(떨어진 객체 — 다시 넣으면 옛 내용이 들어간다).
- **개수 필드**: 붙은 섹션의 필드는 서버 바닐라 개수의 거울이다 — shim 을 만들 때 서버 값, 다른 스레드의 일지 쓰기마다 바닐라 ±1, 틱 끝 공개 뒤 필드가 기대값 그대로면(플러그인 대입 없음) 공개 판 값으로 CAS.
- **덧칠**: 다른 스레드의 칸·바이옴 쓰기를 그 스레드(와 다른 비주인 스레드)가 곧바로 읽게. 재생돼 공개된 순번까지 지운다.
- **`data` 실체화**: 붙은 보기의 `data` 는 평소 쓰이지 않는다(null). `Field.get` 장벽이 그 순간 내용으로 바닐라 배치 data 를 만들고(팔레트 크기 바꿈 처리기 = 그 컨테이너), 내용 판(주인: 컬럼 `modCount`, 다른 스레드: 공개 판의 `contentStamp`, 덧칠이 있으면 늘 다시)이 같으면 앞서 만든 것을 그대로 준다. 플러그인이 바꾼 `data` 는 건드리지 않는다(CAS).
- **잠금**: Furnace 전용 객체 하나(설계 R12 — 플러그인이 볼 수 있는 청크·섹션·컨테이너의 모니터는 쓰지 않는다). 잠금 안에서 다른 스레드를 기다리지 않는다(주인은 잠금 안에서 자기 서버 청크만 고친다 — 들여오기·재생·공개). 잡힌 컨테이너의 바닐라 칸 쓰기·`read` 는 이 잠금 아래에서 한다.
- **내린 청크**: 주인이 맞춘 뒤 거울을 죽이고 붙은 섹션을 그 순간 내용으로 굳혀 떼어 낸다 — 옛 shim 의 읽기는 그 내용, 쓰기·`setUnsaved`·하이트맵 쓰기는 월드(같은 좌표의 새 로드 포함)에 닿지 않는다(바닐라: 죽은 `LevelChunk`).

### 2.4 쓰기 장벽(`NmsClassTransformer`, 설계 3.3-3 — 지연을 줄이는 최적화, 정확성은 훑기가 보장)
- `java/lang/invoke/VarHandle` 의 배열 칸 쓰기(`compareAndSet`·`weakCompareAndSet*`·`compareAndExchange*`·`set*`·`getAndSet*`) 중 서술자가 `([Ljava/lang/Object;I..)` 인 것 → `NmsArrays.*`: **NMS 이름 힌트 게이트 밖**(모든 플러그인 클래스). 원소 형이 `net/minecraft/` 인 서술자도 Object 꼴로 바꿔 돌리고 반환 형은 `CHECKCAST`. 원래 연산 뒤 그 배열이 `LevelChunkSection[]` 이고 거울 배열이면 알림. 다른 원소 형(예: `String[]`)은 바꾸지 않는다(`withInvokeExactBehavior` 핸들 보호).
- 이름 힌트가 있는 클래스의 `Field.get` 앞(`NmsReflect.beforeFieldGet` — `PalettedContainer.data` 실체화)과 `Field.set`·`setShort`·`setInt`·`setByte`·`setChar`·`setBoolean`·`setFloat` 뒤(`NmsReflect.afterFieldSet` — 섹션·컨테이너면 알림). 원래 호출은 플러그인 메서드에 그대로 남긴다(호출자 민감성 유지).
- AASTORE·`Unsafe`·`MethodHandle` setter·`findVarHandle` 은 장벽 없음 — 틱 끝 훑기(주인 스레드면 그 칸 읽기·`setUnsaved`)가 잡는다.

## 3. 시험

- 서버 `ChunkColumnImportSectionTest`(8): 블록만 → 개수 그대로·바닐라 hasOnlyAir 참·Furnace 비공기 수 1·하이트맵 고정·저장 표시 없음·`importedUnsaved`·`modCount`·직렬화 캐시 버림, 블록+개수(음수 포함), 개수만 → 무작위 틱 마스크·저장할 변경 아님, 바이옴만 → 하이트맵 고정 안 함, COW 사본·빛 작업 얼림 보호, 공개 판(바뀐 섹션만 새 사본·내용 판 오름), 원시 쓰기의 `importedUnsaved`, 길이 검사.
- 서버 `ChunkFieldCensusTest`: 새 필드 분류(`mirrorPending`·`mirrorHeld`·`mirrorSyncing`·`importedUnsaved`, `MIRROR_PENDING`).
- 호환 `NmsMirrorTest`(11): 실제로 변환한 플러그인 클래스(FAWE 꼴 `ReflectionUtils` + `Adapter`)를 시험 서버에서 돌린다.
  다른 스레드 섹션 `setBlockState`(옛 상태·자기 쓰기 읽기·개수 필드 +1·**틱 없이** 재생·저장 표시 없음·되돌림), 다른 스레드 `LevelChunk#setBlockState`(하이트맵·저장 표시는 바닐라대로),
  FAWE 꼴 교체(개수 0 → 새 섹션 → Object[] CAS → 틱 없이 들여옴·정체성 유지·새 섹션이 보기·옛 섹션 굳음), NMS 원소 형 VarHandle `compareAndExchange`,
  `biomes` 필드 대입(들여옴·대입 컨테이너가 보기·옛 컨테이너 되돌리면 옛 내용), `data` 리플렉션 읽기(실체화·같은 판이면 같은 객체·바뀌면 새 객체),
  알림 없는 AASTORE(주인 작업 한 바퀴로는 안 들어오고 틱 끝에), 주인 스레드 AASTORE(그 칸 읽기·`setUnsaved` 맞춤 지점), 내린 거울(굳음·월드에 안 닿음·다른 스레드의 `sync`/`unloaded` 무시),
  **동시 쓰기**(스레드 4 × 256칸, 쓰는 사이 틱 4번 — 교착·예외 없음, 개수 필드 = 서버 개수), 변환기 바이트코드(장벽 호출 위치·개수, `normalize` 경계).
- 바꾼 기존 시험: `NmsOwnerRawWriteTest.offThreadRawWritesThrow` 삭제(다른 스레드 쓰기는 이제 예외가 아니라 일지 — `NmsMirrorTest`), `NmsChunkSymbolsTest` 주석(따로 만드는 하이트맵은 N2b-6).
- 안정성: `NmsMirrorTest`·`NmsOwnerRawWriteTest`·`NmsSnapshotReadTest`·`NmsChunkShimsTest` 를 5번 연달아 다시 돌려 모두 통과.
- 검토 반영(5장) 뒤 더한 시험 — 참값은 늘 **서버를 바로** 읽는다(`Region.getBlock`·`ChunkColumn`; 주인의 NMS·Bukkit 읽기는 그 자체가 맞춤 지점이라 빠진 알림·맞추기를 가린다):
  - 호환 `NmsMirrorRaceTest`(13): 검토 재현 r1(들여온 자기 섹션을 틱 전에 다시 읽기)·r2(옛 섹션에 쓰는 동안 교체, 6회)·r3(넣은 직후 4096칸 쓰기, 40회 — 개수 필드 = 서버 개수까지);
    시험 이음매(계획 뒤·들여오기 전)로 결정적인 틈 — 교체를 계획한 뒤 옛 섹션 쓰기(옛 객체에만 남음), 잡힌 새 섹션 쓰기(같은 들여오기에 듦·개수 17), 개수 대입 계획 뒤 일지 ±1 + 서버 쓰기(개수 7);
    주인 작업 안에서 다른 스레드를 기다려 만드는 틈 — 일지와 교체가 한 맞추기(재생이 먼저), 공개 직전 재생 안 한 덧칠이 공개 뒤에도 보임, 쓰기 다섯 번에 깨우기 한 번·맞춘 뒤 대기 청크 수 0, Bukkit 읽기 진입이 맞춤 지점;
    서버 칸을 바꾼 뒤 공개하면 섹션 개수 필드가 따라옴, `Field.set`(data·biomes) 장벽 알림으로 틱 없이 들어옴, 실체화한 data 의 팔레트를 넘치게 하면 그 컨테이너의 data 가 바뀌고 틱 끝에 들어옴.
  - 호환 `NmsMirrorUnloadWarningTest`(1, 디스크 월드): 저장 표시 없이 들여온 청크 둘을 내리면 경고 한 줄씩 — 예약 틱 없는 쪽은 "저장하지 않는다", 예약 틱 남은 쪽은 "함께 저장된다". 다시 불러 보면 앞의 것은 옛 블록, 뒤의 것은 들여온 블록.
  - 서버 `ChunkColumnMirrorPendingTest`(3): 처음 세운 쓰기만 깨움·맞추면 대기 수 0·다른 로드의 거울 거절, 재생성(`inheritLoadToken`)이 대기·거울을 새 컬럼으로, 대기 표시와 맞추기 경쟁 20만 판에서 "표시는 섰는데 거울을 쥐지 않은" 판 0.

## 4. 남은 것·가정

1. **W3(제자리 배열 쓰기) 미지원** — 설계 3.3 그대로. Starlight `getDataLayerData` 가 사본이면 Paper 에서도 효과가 없다(가정, N2b-6 V6 에서 확인).
2. **블록 엔티티·빛 일지 없음** — shim 에 블록 엔티티 객체(`getBlockEntity`·`blockEntities` 맵)와 빛 표면(`DataLayer`·`LevelLightEngine`)이 아직 없어 쓸 길이 없다. 빛은 N2b-5, 블록 엔티티는 그 shim 이 생길 때 같은 일지에 넣는다.
3. **GETFIELD/PUTFIELD 장벽 없음** — 바닐라 1.21.1 의 해당 필드(`states`·`biomes`·개수·`data`·`sections`)는 private/protected 라 플러그인 바이트코드가 직접 닿지 못한다(리플렉션은 `Field` 장벽, 그 밖은 훑기).
4. **주인 스레드의 알림 없는 W2 뒤 같은 틱 Bukkit 읽기**: 장벽 없는 대입(AASTORE)은 대기 표시를 세우지 않아 Bukkit 진입이 맞추지 않는다 — 그 칸을 NMS 로 읽거나 `setUnsaved` 를 부르면 맞춰지고, 늦어도 틱 끝. FAWE 는 CAS(장벽)라 해당 없음.
5. **다른 스레드의 하이트맵 읽기는 덧칠 없음** — 자기 `setRawData` 를 재생·공개 전까지 공개 판으로 읽는다(`update` 돌려줌은 false). FAWE 는 하이트맵을 쓰기만 한다.
6. **알림 없는 대입을 한 거울이 틱 끝 전에 GC 되면**(플러그인이 shim·배열을 모두 놓음) 그 대입은 사라진다. 대기 표시(`mirrorHeld`)는 알림이 있을 때만 거울을 붙잡는다. 바닐라에서는 청크가 붙잡으므로 다르지만, 장벽 없는 대입 + 즉시 놓기 조합을 쓰는 플러그인은 찾지 못했다(가정).
7. N2b-3 검토 R3 의 "거두는 순간 μs 틈"은 그대로(2ms 다시 보기 완화). 슬롯 CAS 로 닫는 것은 필요해지면.
8. 개수 필드는 주인 스레드의 쓰기 뒤 틱 끝에 새로 고친다 — 같은 틱 안에 다른 스레드가 필드를 리플렉션으로 읽으면 한 틱 옛 값(Paper 의 경쟁 읽기와 같은 성질).
9. 따로 만드는 `Heightmap` 생성자·월드젠 하이트맵 쓰기는 여전히 덫(N2b-6 — FAWE 는 쓰지 않는다).
10. 대량 편집 비용(FAWE 64³)·Paper 대조는 N2b-6 노트북 실측(V6).
11. D3 경고는 청크를 내릴 때(`evictIdleChunks`)만 낸다. 서버를 끌 때는 거울을 맞춘 뒤(`saveDirtyChunks → syncAllMirrors`) 저장 표시가 있는 청크만 저장하고 경고는 없다(저장 결과는 바닐라와 같다).
12. **예약 틱이 남은 청크(검토 R5, 가정)**: Furnace 는 내릴 때 예약 틱이 있으면 저장 표시가 없어도 청크를 저장하므로 저장 표시 없이 들여온 내용도 함께 남는다. Paper 1.21.1 에서 같은 경우 남는지는 확인하지 못했다(바닐라 `LevelChunkTicks` 의 저장 판단은 N2b-6 노트북 대조). 그래서 저장 판단은 그대로 두고 경고 문구만 사실대로 바꿨다(도미니코 위임 결정 — 개발자 친화). 확실히 남기려면 `setUnsaved(true)`.
13. **잡기와 같은 순간의 쓰기(이론상 틈)**: 떨어진 컨테이너에 다른 스레드가 바닐라로 쓸 때는 잠금 없이 칸을 쓰고 나서 잡힘 표시를 읽는다(잡혔으면 `lateWrite` 로 일지에). 주인이 바로 그 순간 잡고 읽으면 메모리 순서(StoreLoad)상 둘이 서로를 못 볼 수 있어, 그 칸이 들여오기에도 일지에도 안 들 수 있다. 막으려면 떨어진 컨테이너의 모든 칸 쓰기에 전체 울타리가 필요해 두었다. FAWE 는 새 섹션을 다 채운 뒤 CAS 로 넣고 넣은 섹션에는 다시 쓰지 않는다(가정 — N2b-6 실측). 넣은 직후 계속 쓰는 꼴(검토 r3, 40회 × 4096칸)은 잃은 칸 0.

## 5. 독립 검토(PR #396) 반영 — 2026-11-15

검토 보고서 `furnace/reviews/n2b4-pr396.md`(반려). 고친 것:

- **R1 들여온 섹션을 틱 전에 다시 읽으면 옛 내용**: 붙이기가 읽기 출처를 공개 판으로 바꾸는데 그 판은 들여오기 전 것이었다. 이제 한 맞추기에서 들인 것을 다 들여온 뒤 **새 판을 먼저 내고**(`ChunkInternals.publishChunkViewNow`) 그 판의 내용 판 번호로 붙인다 — 붙은 순간부터 다른 스레드가 읽는 판에 들여온 내용이 있다.
- **R2 교체된 옛 섹션에 쓴 칸이 새 섹션 자리에 새어 듦**: 계획(잠금) 뒤 떼어 내기 전에 옛 섹션 앞으로 일지에 든 줄이 다음 맞추기에서 새 섹션 위에 재생됐다. 일지 줄마다 쓴 객체(섹션·컨테이너)와 순번을 달고, 떼어 내기 직전에 그 객체 앞 줄을 옛 내용 위에 먼저 재생해 일지·덧칠에서 뺀다(`replayTargeted`) — 옛 객체에 남고 새 섹션이 그 자리를 덮는다. 쓰는 쪽은 잠금 안에서 그 섹션이 아직 그 자리의 보기인지 보고, 아니면 떨어진 객체에 바닐라로 쓴다(`FURNACE_DETACHED`). `furnaceIndex` 두 번 읽기(경쟁 시 배열 밖 첨자)도 한 번 읽기로.
- **R3 넣은 직후 그 섹션에 쓴 칸을 잃음**: 들여오기가 내용을 읽은 뒤 붙이기 전에 쓴 칸이 사라졌다. 계획 때 들여올 떨어진 컨테이너를 **잡고**(`furnaceClaim`), 잡힌 컨테이너의 바닐라 칸 쓰기·`read` 는 거울 잠금 아래에서 한다. 읽기·들여오기·공개·붙이기는 잠금 한 번에 하므로 그 사이에 쓰기가 끼지 못한다. 잡기 직전에 시작한 쓰기는 쓴 뒤 다시 보고 일지로 넘긴다(`lateWrite`, 4장 13).
- **R4 대기 표시가 섰는데 거울을 놓음**: 쓰는 쪽이 `mirrorHeld` 를 쓰고 CAS 하는 사이 주인이 표시 0 을 보고 쥠을 내리면 표시만 남았다. 표시를 세운 쪽이 CAS 뒤 다시 쥔다.
- **R5 예약 틱이 남은 청크의 D3 경고가 틀림**: 문구를 사실대로("예약 틱이 남아 있어 이 청크는 저장되고 그 변경도 함께 저장된다 … Paper 에서는 남지 않을 수 있다") — 추천안(a), 4장 12.
- **R6 시험 빈틈**: 3장의 시험을 더했다. 결정적인 틈은 시험 이음매 `FurnaceChunkMirror.testAfterPlan`(O15 허용, 운영에서는 null — volatile 읽기 하나)과 주인 작업 안에서 다른 스레드를 기다리는 방법으로 만든다. 검토 변이 중 생존 11개 + R1~R4 를 되돌리는 변이를 새 시험으로 다시 돌린 결과는 PR 본문.
- 금 파일 갱신 사유(PR 본문): commandapi 해시는 `NMS_1_20_R1`~`NMS_1_21_R5` 9개 클래스의 `Field.set` 장벽 18개 때문, XSeries·cloud·commodore·adventure 는 `Field.get` 장벽만.
