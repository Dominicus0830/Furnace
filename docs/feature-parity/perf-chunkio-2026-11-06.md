# 청크 저장·로드 성능 점검 (2026-11-06)

대상: 청크 디스크 저장(스냅샷 → NBT → 압축 → .mca)과 로드(.mca → 풀기 → NBT → ChunkColumn), IO 레인.
원칙: 바닐라와 관측 결과(파일 형식·블록·높이맵·저장 내용)가 같으면 되고 내부 구조는 자유. 바꾼 곳마다 예전 구현과 대조하는 시험을 둔다.

## 측정 방법
- 벤치 `furnace-server/src/test/java/org/domi/furnace/server/world/anvil/ChunkIoPipelineBench.java`(기본 꺼짐).
  실행: `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test --tests '*ChunkIoPipelineBench' -Dfurnace.bench=true -i`
  - 노이즈 오버월드(시드 12345) 실제 생성 청크 36개(`generateWithFeatures` + 빛 미리 계산)로 단계마다 한 스레드 CPU 시간·할당 바이트를 청크당으로 잰다.
    5라운드, 아래 숫자는 예열 뒤 라운드(1~4)의 대표값. 4코어 VM, 다른 gradle 없음.
  - `-Dfurnace.bench.profile=<단계>` 면 그 단계만 8초 돌리며 JFR 실행 샘플(1ms)로 상위 프레임을 찍는다.
  - 마지막에 같은 .mca 하나의 1024청크를 IO 레인(바탕 풀 4스레드, 레인 16)으로 한꺼번에 로드하는 벽시계 시간.

## 결과(청크당)

| 단계 | 스레드 | 전 | 후 | 원인 / 조치 |
|---|---|---|---|---|
| forSave(스냅샷) | **region 틱** | 200~260 us | 93~97 us | MOTION_BLOCKING_NO_LEAVES 높이맵을 기둥 256개 × 위에서부터 칸마다 `getBlock`(84%). 섹션 단위로 읽고 한 값 섹션(하늘 공기)은 값 하나로 판정 |
| toNbt | IO 레인 | 1190~1200 us, 921 KB | 330~370 us, 477 KB | 블록 팔레트를 `HashMap<Integer,Integer>` 로 칸마다 박싱·조회(95%). 스레드별 int 열린 주소 해시(첫 등장 순 그대로) |
| NbtIo.write | IO 레인 | 170~190 us | 103~106 us | DataOutputStream(ByteArrayOutputStream) 동기화 호출(long 배열은 원소마다). byte[] 에 바로 쓰기(`NbtIo.toBytes`) |
| compress(zlib 6) | IO 레인 | 820 us | 820 us | zlib 자체 비용. 레벨은 바닐라와 같은 기본값 유지(바꾸지 않음) |
| region.write | IO 레인 | 20~30 us | 같음 | — |
| decompress | IO 스레드 | 190 us, 278 KB | 155 us, 202 KB | InflaterInputStream 512B 버퍼 + ByteArrayOutputStream 키우기. Inflater 에 입력 전체를 한 번에 |
| NbtIo.read | IO 스레드 | 120~140 us | 42 us | DataInputStream 동기화 호출. byte[] 에서 바로 읽기(`NbtIo.read(byte[])`) |
| fromNbt | IO 스레드 | 620 us, 508 KB | 250~270 us, 54 KB | ① 섹션마다 4096칸을 펼친 뒤 `PaletteStorage.load` 가 칸마다 해시 두 번(51%) → NBT 팔레트 인덱스 그대로 넣기(`loadIndexed`). ② 높이맵 재계산이 표면 위 섹션마다 scratch(int[4096])를 열고 **그대로 남김**(28%, 로드 청크 메모리) → scratch 를 열지 않고 읽기 |
| **저장 합계** | | 약 2.4 ms | 약 1.4 ms | 남은 것의 60% 는 zlib |
| **로드 합계** | | 약 0.95 ms | 약 0.47 ms | |

| 같은 .mca 1024청크 동시 로드 | 전(디코드가 레인 안) | 후(디코드는 레인 밖 IO 풀) |
|---|---|---|
| 벽시계 | 445~520 ms | 127~205 ms |

(두 열 모두 위의 빠른 디코드를 쓴 값이다. 차이는 순수하게 레인 밖 병렬화 몫.)

## 바꾼 것

1. **저장 팔레트**(`AnvilChunkCodec.blockStatesFromArray`·`biomesFromArray`): 첫 등장 순 팔레트·인덱스를 스레드별 작업 배열의 열린 주소 해시로. 출력 NBT 는 예전과 같다.
2. **기둥 탐색**(`ChunkColumn.topLocalY`): `motionBlockingNoLeavesBlockY`, MOTION_BLOCKING·OCEAN_FLOOR 지연 재계산이 섹션 단위로 읽는다. 한 값 섹션은 값 하나로 판정, scratch 는 열지 않는다.
   `rebuildHeightmapsAfterSurface` 도 scratch 를 열지 않는다(생성 경로는 이미 열려 있는 scratch 를 그대로 읽으므로 같다).
3. **디스크 로드 섹션 적재**(`PaletteStorage.loadIndexed`, `ChunkColumn.loadSectionIndexed`): NBT 팔레트 값 + 칸별 인덱스에서 바로 만든다. 팔레트 값이 겹치면(모르는 이름 둘 → 공기) 합친다.
   결과 내부 상태(팔레트 순서·비트 수·역맵)까지 `load(펼친 배열)` 과 같다. 인덱스가 팔레트 밖이면 예전처럼 ArrayIndexOutOfBoundsException.
4. **로드 청크 메모리**: 예전에는 디스크에서 읽은 청크가 표면 위 섹션마다 int[4096](16KB)을 쥐고 있었다(위 2·3 으로 없어짐). 그 청크의 저장 스냅샷도 scratch 복사 대신 팔레트 사본을 쓴다.
5. **NBT byte[] 직행**(`NbtIo.toBytes`, `NbtIo.read(byte[])`): 청크·엔티티·POI 저장/로드에 쓴다. 바이트는 DataOutput 경로와 같고(수정 UTF-8 포함), 읽기는 같은 결과·같은 예외 종류.
   ASCII 가 아닌 문자열은 DataInputStream.readUTF 에 맡긴다. 큰 길이 배열은 남은 바이트를 먼저 보고 EOF(예전엔 먼저 할당).
6. **zlib**(`Compression.deflate`·`inflate`): Deflater/Inflater 를 큰 버퍼로 직접. 압축 바이트는 DeflaterOutputStream 과 같다(같은 호출 순서), 풀기는 InflaterInputStream 과 같은 결과·예외(뒤 쓰레기 무시, 잘리면 EOFException, 깨지면 ZipException).
7. **압축 풀기를 파일 잠금 밖으로**(`RegionFile.readPayload`, `RegionFileStore.readChunk`): 쓰기 쪽 F-01 과 같은 꼴. 잠금 안에는 섹터 읽기만 남는다.
8. **디코드를 IO 레인 밖으로**(`AnvilWorldSource.loadChunkAsync` 5인자, `World.ioDecodeExecutor`): 레인에서는 압축 본문 읽기만 하고 풀기·NBT·디코드는 IO 풀에서(순서 없음).
   레인의 의미(같은 .mca 의 읽기·쓰기 FIFO)는 그대로다 — 읽기 자체는 여전히 레인에서 제출 순서대로 한다. POI 읽기는 예전처럼 디코드 뒤 레인에 줄 선다. IO 풀이 닫혔으면(종료 중) 부른 스레드에서.

## 동일성 시험
- `world/ChunkIoFastPathParityTest`: `loadIndexed` 와 `load` 내부 상태 비교(무작위 3000회, 겹치는 값·안 쓰는 항목·간접/직접 경계), 섹션 단위 기둥 탐색 = 칸별 탐색(무작위 200청크 × 256기둥, scratch 열린 섹션 섞음),
  scratch 를 열지 않는 높이맵 재계산 = 예전 계산.
- `world/anvil/ChunkIoFastPathCodecTest`: zlib 압축 바이트 일치(300회), 풀기 결과·예외 일치(잘림·깨짐·뒤 쓰레기 600회), toNbt 섹션 팔레트·데이터가 예전 HashMap 구현과 같음(400회, 경계 1/2/3/16/17/255/256/257/600/4096종) + 왕복,
  디스크 로드 청크가 scratch 를 쥐지 않음, 디코드를 레인 밖에서 해도 저장 직후 로드가 저장본을 읽음(200청크).
- `net/io/nbt/NbtByteArrayParityTest`: 무작위 NBT 3000개 바이트 일치·읽기 결과 일치, 잘림·깨짐 800개 예외 종류 일치, 65535 바이트 넘는 문자열 거부.
- 기존 `AnvilBulkDecodeParityTest`(예전 블록별 디코드와 블록·비공기 수·무작위 틱·높이맵 4종·dirty·재저장 NBT 비교), `VanillaChunkFormatTest`, `AnvilChunkCodecTest` 통과.

## 손대지 않은 것(다음 후보)
- zlib 압축(저장 비용의 60%): 레벨은 바닐라 기본(6) 그대로 둔다. `-Dfurnace.region.compressionLevel` 로 이미 바꿀 수 있다.
- toNbt 의 섹션 펼치기(int[4096] × 24, 청크당 약 380KB 할당): 팔레트 사본에서 바로 묶으면 없앨 수 있으나 이득 대비 범위가 넓어 보류.
- 팔레트 항목 NBT(`paletteEntryOf`)의 문자열 쪼개기: 남은 toNbt 의 작은 몫.
