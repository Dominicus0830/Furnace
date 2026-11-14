# NMS 어댑터 N2b-2 — 주인 스레드 원시 쓰기와 고정 하이트맵 (2026-11-14)

설계: `compat-nms-n2b-design-2026-11-14.md` 3.4·3.5·6장 N2b-2 줄. 앞 단계: `compat-nms-n2b1-2026-11-14.md`(PR #365). 결정: 도미니코 11-14 D1~D4 모두 A.

## 1. 무엇이 생겼나

청크를 가진 region 스레드(주인)에서 플러그인이 NMS 청크 객체에 **바로 쓰는** 길이 바닐라와 같은 결과를 낸다.

| 표면 | 바닐라 의미 | Furnace |
|---|---|---|
| `LevelChunkSection.setBlockState(x,y,z,s[,lock])`(보기) | 칸 쓰기 + 개수 ±1. 하이트맵·빛·저장 표시·패킷·블록 엔티티·이웃 그대로 | 다리 `setSectionStateRawNow(..., vanillaCounts=true)` → `ChunkColumn.setSectionStateRaw` |
| 보기 `getStates()` 의 `set`·`getAndSet`·`getAndSetUnchecked` | 칸 쓰기만(개수 그대로) | 같은 다리, `vanillaCounts=false` |
| 보기 `getBiomes()` 의 `set`·`getAndSet(Unchecked)` | 바이옴 칸 쓰기(N2b-0 되먹임 1) | `setSectionBiomeRawNow` → `ChunkColumn.setSectionBiomeRaw` |
| 보기 `recalcBlockCounts`·`hasOnlyAir`·`write` 의 nonEmpty | 섹션 개수 필드 | 서버가 보관하는 바닐라 개수 셋(`sectionCountsNow`·`setSectionCountsNow`) |
| `LevelChunk.getBlockState` | 섹션 `hasOnlyAir`(nonEmpty == 0)면 공기 | 서버의 바닐라 nonEmpty 로 판정 |
| `Heightmap.setRawData(chunk, type, long[])` | 길이 맞으면 복사, 다르면 경고 + 그 종류 prime | `setHeightmapRawNow`(1 = 복사, 0 = prime) |
| `Heightmap.update(x,y,z,state)` | 바닐라 증분 갱신 | `updateHeightmapNow`(바닐라 포팅) |

다리(`ChunkInternals`, 불안정 내부 API)의 새 메서드는 모두 "Now" 규칙이다: 주인 region 스레드가 아니면 `ThreadOwnershipException`, 청크가 로드돼 있지 않으면 -1/false.
다른 스레드의 쓰기(일지)는 N2b-4(D1)이고 지금은 여전히 예외로 거절한다.

## 2. 서버 쪽 바뀐 것

### 2.1 섹션의 바닐라 개수 셋(설계 3.4, 재검토 R17)
`ChunkSection` 이 바닐라 `nonEmptyBlockCount`(새 필드 `nonEmpty`)·`tickingBlockCount`(`tickingCount`)·`tickingFluidCount`(`fluidCount`)를 **모든 섹션에** 든다.
- 읽기·생성 뒤 처음 필요할 때 바닐라 `recalcBlockCounts`(유체 칸은 nonEmpty 에 두 번, tickingFluid 는 용암만).
- 보통 쓰기(`setBlock`)·섹션 `setBlockState` 는 바닐라처럼 칸마다 ±1(유체는 비어 있지 않으면 물도 tickingFluid).
- 컨테이너 쓰기는 개수를 그대로. 필드 대입(FAWE `clearCounts` 같은 `Field.setShort`)은 short 로 자른 그 값.
- 무작위 틱 대상·T-053 구역 마스크는 이 값(`tickingBlock > 0 || tickingFluid > 0`)을 따른다. 개수가 음수일 수 있어 "모름" 표시를 -1 에서 `Integer.MIN_VALUE` 로 바꿨다.
- Furnace 정의 `nonAirCount`(정확한 비공기 칸 수)는 따로 그대로 둔다(빛·패킷·직렬화가 쓴다).

### 2.2 고정 하이트맵(설계 3.5)
Furnace 하이트맵은 평소 지금 블록에서 계산된다(WORLD_SURFACE 라이브, OCEAN_FLOOR·MOTION_BLOCKING 지연, MOTION_BLOCKING_NO_LEAVES 매번). 원시 쓰기 뒤 바닐라는 낡은 값을 들고 가므로:
- 청크가 처음 원시 쓰기·`setRawData`·`update` 를 받으면 **고정 모드**: 네 종류를 그 순간 값으로 `fixedHeights` 에 실체화한다. 그 뒤 보통 쓰기는 바닐라 `Heightmap.update` 포팅(MB → MBNL → OF → WS 순서, `LevelChunk.setBlockState` 와 같음)으로만 고친다.
- `update`·prime 의 아래로 훑기는 바닐라처럼 `ChunkAccess.getBlockState`(섹션 nonEmpty == 0 이면 공기)를 본다.
- prime 은 바닐라처럼 맨 위 비어 있지 않은 섹션부터 내려가고, 못 찾은 열은 옛 값을 둔다.
- **저장 → 읽기**(R7): `AnvilChunkCodec.fromNbt` 가 다시 계산한 값과 저장된 `Heightmaps` 를 비교해 다르면 그 청크를 고정 모드로 시작한다. 길이가 틀린 종류는 경고 한 줄 뒤 블록에서(바닐라 `setRawData` 와 같음). 없는 종류는 블록에서(바닐라 prime).
- 하이트맵을 읽는 모든 곳(`surfaceBlockY`·`oceanFloorBlockY`·`motionBlockingHeightValue`·`motionBlockingNoLeavesBlockY`·raw 배열·청크 패킷·저장)이 고정 값을 낸다. 거울 `ChunkAccess.heightmaps` 필드도 그 값을 비춘다(N2b-0 되먹임 2).

### 2.3 그 밖
- `ChunkLevel.setBlockStateInChunk` 의 "공기뿐인 섹션에 공기류 쓰기는 아무것도 안 함" 판정을 Furnace `nonAirCount` 에서 바닐라 nonEmpty 로 바꿨다(원시 쓰기·필드 대입 뒤에도 바닐라와 같은 판정).
- 원시 쓰기는 `touch()`(직렬화 본문·스냅샷·하이트맵 캐시 버림)만 하고 `dirty` 는 세우지 않는다(바닐라 섹션 쓰기는 `setUnsaved` 를 부르지 않는다). 저장 표시 없는 편집을 저장하지 않는 경고(D3)는 N2b-4.
- 빛·하늘빛 원천은 계산하지 않는다(D2, Paper 같음).

## 3. 바닐라와 같은지 — 시험

**정답기 V1** `tools/vanilla-data/ExportRawWriteOracle.java` — 바닐라 1.21.1 서버 jar 로 읽은 청크(섹션을 생성자로 만들어 개수를 다시 셈 + `primeHeightmaps`) 60개에 무작위 연산을 섞어 적용한다:
0 = `LevelChunk.setBlockState` 의 블록·하이트맵 부분, 1 = 섹션 `setBlockState`, 2 = 블록 컨테이너 `set`, 3 = `Heightmap.update` 직접, 4 = `setRawData`(맞는 길이), 5 = `setRawData`(틀린 길이 → prime), 6 = 개수 필드 대입.
연산 수 41,512개(종류별 16,671 / 9,173 / 8,278 / 3,689 / 1,584 / 829 / 1,288). 끝에 네 하이트맵 raw 와 섹션 24개의 개수 셋을 적는다.

**시험** `VanillaRawWriteOracleTest`(서버) — 같은 입력을 `ChunkColumn` 에 적용해 네 하이트맵 raw 가 **long 단위로** 같고, 섹션마다 개수 셋·무작위 틱 판정이 같고, T-053 마스크가 덮는지 본다.
시험이 뜻이 있는지도 못 박는다: 고정 하이트맵이 지금 블록과 다른 WORLD_SURFACE 칸이 1,000개를 넘고, 개수 0 인데 블록이 있는 섹션(바닐라 `hasOnlyAir` 가 칸과 어긋남)이 나온다.
이 시험이 구현 중 버그 하나를 잡았다: 지연 재계수가 쓰기 직전에 일어날 때 `nonAirCount` 를 먼저 고쳐 그 칸이 두 번 세였다(재계수를 먼저 하도록 고침).

**V2(부수 효과 없음)** `NmsOwnerRawWriteTest`(호환, 시험 서버):
- 섹션 `setBlockState` → 옛 상태, nonEmpty −1, `Level.getBlockState` 도 새 칸, WORLD_SURFACE 는 낡은 값, `dirty` 그대로, 하늘빛·하늘빛 원천 그대로, 칸 갱신 패킷 없음.
- 컨테이너 `set` 은 개수 그대로. 바이옴 컨테이너 `getAndSet` 이 `getNoiseBiome` 에 보임.
- `setRawData`(맞는 길이) 값이 `getFirstAvailable`·`Level.getHeight` 에, 넘긴 배열을 나중에 고쳐도 그대로(복사). `update` 바닐라 규칙. 틀린 길이 → 블록에서 다시.
- 주인 아닌 스레드의 섹션·컨테이너·하이트맵 쓰기는 `ThreadOwnershipException`.

**저장·읽기** `FixedHeightmapCodecTest`(서버): 보통 청크는 고정 모드가 아님, 원시 쓰기 뒤 낡은 하이트맵이 저장·읽기 뒤에도 같고 그 뒤 보통 쓰기는 바닐라 `update` 를 따름, 블록과 다른 저장값을 그대로 씀, 길이 틀린 종류는 다시 계산.

**필드 전수** `ChunkFieldCensusTest`: `fixedHeights` = HEIGHTMAP, `nonEmpty` = 바닐라 개수(들여오기는 거울 섹션의 개수 필드를 옮긴다 — N2b-4).

## 4. 되먹임·검토 주의 반영

| 출처 | 항목 | 이 단계 |
|---|---|---|
| N2b-0 5장 1 | 바이옴 컨테이너도 같은 규칙 | 주인 스레드 바이옴 칸 쓰기 구현(시험 `biomeContainerWritesThrough`). 일지는 N2b-4 |
| N2b-0 5장 2 | `heightmaps` 필드가 Furnace 하이트맵(고정 값)을 비춤 | 고정 모드 값이 필드의 `Heightmap` 보기로 보인다(`getFirstAvailable`·`getRawData`) |
| N2b-0 5장 3 | WORLD_SURFACE 빈 열 버그 | PR #354 로 이미 고침. 정답기가 WORLD_SURFACE 를 long 단위로 비교 |
| N2b-0 5장 4 | 떨어진 섹션 개수 모델 | N2b-1 에서 끝. 이 단계는 같은 모델을 **서버 섹션**에(재검토 R17) |
| PR #365 검토 R1 | 토큰 = 컬럼 노출 금지 | 새 다리 메서드도 `World`·좌표만 받고 컬럼을 내주지 않는다 |
| PR #365 검토 R3 | 약한 참조·로드 표시 | 주인 스레드 쓰기는 거울을 거치지 않고 바로 서버에 가므로 해당 없음. 일지 거울 붙잡기·로드 표시 확인은 N2b-4 |

## 5. 바닐라와 다른 점·가정(알고 둔 것)

1. **Furnace 내부 읽기는 바닐라 `hasOnlyAir` 공기 판정을 하지 않는다.** Bukkit `getBlock`·`ChunkLevel` 읽기·빛은 칸을 그대로 본다. 차이는 컨테이너 쓰기·필드 대입으로 "nonEmpty == 0 인데 블록이 있는" 섹션을 만든 뒤에만 난다(바닐라에서는 그 섹션이 공기로 읽힌다). 그렇게 쓰는 플러그인은 아직 모른다. NMS 보기(`LevelChunk.getBlockState`)는 바닐라처럼 판정한다.
2. **청크 패킷의 섹션 nonEmpty** 는 Furnace 정확한 비공기 수를 그대로 쓴다(바닐라는 필드 값). 클라이언트는 이 값을 렌더 건너뛰기에만 쓴다 — 후속 항목(R17 꼬리).
3. `setRawData` 의 long 안 쓰지 않는 윗비트(7×9 = 63 비트 뒤 1비트)는 보존하지 않는다. 값 256개는 같다.
4. PR #354(WORLD_SURFACE 빈 열 수정) 전에 Furnace 가 저장한 월드에 낡은 WORLD_SURFACE 가 있으면, 그 청크는 읽을 때 고정 모드로 그 값을 든다 — 바닐라가 그 파일을 읽은 것과 같다.
5. 읽기 때 저장값 비교 비용: 청크마다 MOTION_BLOCKING_NO_LEAVES 한 번 계산과 OCEAN_FLOOR·MOTION_BLOCKING 지연 캐시 미리 채움. 디스크 읽기 경로에서만 든다.
6. 월드젠 하이트맵(`*_WG`) 쓰기와 `Heightmap` 생성자는 여전히 거절(N2b-4). 보기 섹션의 개수 **필드**(GETFIELD/PUTFIELD)는 0 그대로 — 필드 훑기는 N2b-4. 다리 `setSectionCountsNow` 는 있다.

## 6. 정답표를 다시 만들 때

`compat-nms-n2b0-survey-2026-11-14.md` 4장과 같다(서명 지운 내부 jar + 라이브러리):
```
javac -d out -cp "$CP" tools/vanilla-data/ExportRawWriteOracle.java && java -cp "$CP:out" ExportRawWriteOracle oracle.txt
gzip -9 -c oracle.txt > furnace-server/src/test/resources/n2b/rawwrite-oracle-1.21.1.txt.gz
```
생성기(`RawCase`·`pool`·`pick`·`packHeights`)는 도구와 시험에 같은 글자로 있고 입력 해시로 서로 확인한다.
