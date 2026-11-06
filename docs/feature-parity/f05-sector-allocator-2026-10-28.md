# F-05 섹터 할당기 — 바닐라 RegionFile 이식 설계 (2026-10-28)

감사 항목 F-05(`audit-runtime-2026-10-27.md`): `RegionFile` 이 끝에 붙이기만 하고 옛 섹터를 재사용하지 않아 `.mca` 가 계속 커진다. F-14 로 생성 청크를 모두 저장하면서 영향이 커졌다.

## 1. 바꾸기 전 Furnace 쓰기 경로

- 파일: `furnace-server/.../world/anvil/RegionFile.java`, `RandomAccessFile` 하나.
- 생성자: 헤더 앞 4KB(위치표)만 읽는다. 검사 없음. 타임스탬프는 메모리에 없다.
- 기록(`writeCompressed`):
  1. 255섹터 초과면 `.mcc.tmp` 쓰기 → `ATOMIC_MOVE` 로 `.mcc` 교체(헤더보다 먼저).
  2. 새 크기 ≤ 옛 섹터 수면 **같은 자리에 덮어쓰기(in-place)**, 아니면 모든 항목의 `offset+count` 최대값 뒤에 붙이고 `setLength`.
  3. 데이터 + 섹터 패딩 → 위치 항목 4B → 타임스탬프 4B 순서.
  4. 내부 청크면 옛 `.mcc` 삭제.
- 옛 섹터는 해제하지 않는다(누수). 앞쪽에 생긴 구멍은 다시 쓰지 않는다.
- fsync 없음. `close()` 는 그냥 닫는다.
- 직렬화: `RegionFileStore` 의 파일(키)별 잠금. IoLanes 는 같은 청크를 같은 레인에 보내지만, 레인 밖 호출(F-12 참고)이 있어 파일 잠금이 실제 보호막이다. `RegionFile` 메서드 자체는 동기화되어 있지 않았다.
- 크래시 위험: in-place 덮어쓰기 중에 죽으면 그 청크는 **옛 값도 새 값도 아닌** 상태가 된다.

## 2. 바닐라 1.21.1 (서버 jar 디컴파일 읽기 전용 대조: `RegionFile` = 난독 `dwp`, `RegionBitmap` = `dwo`)

- 생성자: 8KB 헤더를 통째로 읽는다. 비트맵에 섹터 0·1(헤더)을 표시. 항목마다
  - `offset < 2` → "overlaps with header" 경고, 메모리에서 0 으로.
  - `count == 0` → 경고, 0 으로.
  - `offset*4096 > 파일 크기` → "out of bounds" 경고, 0 으로(끝 `offset+count` 는 보지 않는다).
  - 그 밖은 `bitmap.force(offset, count)`. **겹침 검사는 없다.**
- `RegionBitmap.allocate(n)`: 0 부터 `nextClearBit` → `nextSetBit` 로 빈 구간을 훑어 길이 ≥ n 인 첫 구간(없으면 끝)을 고른다. 첫 적합.
- `write(pos, buf)`(동기화 메서드):
  1. `sectors = ceil(remaining/4096)`, `remaining = 5 + 압축 길이`. `sectors >= 256` 이면 외부: 임시 파일에 본문을 쓰고 1섹터 스텁 할당·기록.
  2. 아니면 `allocate(sectors)` 후 그 자리에 기록. **옛 자리가 충분해도 항상 새 자리**(옛 섹터는 아직 표시돼 있어 겹치지 않음).
  3. 메모리 위치·타임스탬프 갱신 → 8KB 헤더를 통째로 기록.
  4. 외부면 임시 파일 → `.mcc` 이동, 내부면 `.mcc` 삭제.
  5. 옛 섹터 `free`.
- 패딩: 기록 때 패딩하지 않는다. `close()` 때 파일 끝을 섹터 배수로 맞춘다(1바이트 기록). 트렁케이트 없음.
- fsync: `sync-chunk-writes`(dedicated 기본 true)면 `DSYNC` 로 연다. `flush()`/`close()` 에서 `force(true)`.
- 크래시 일관성: 헤더가 바뀌기 전엔 옛 데이터가 그대로 → 내부 청크는 옛/새 중 하나. 외부 청크는 헤더 뒤에 `.mcc` 를 옮기므로 그 사이에 죽으면 스텁이 가리키는 `.mcc` 가 없거나 옛 것일 수 있다(바닐라의 작은 구멍).

## 3. 결정

바닐라 알고리즘을 그대로 이식한다. `.mca` 형식은 변하지 않는다(바닐라·`tools/parity-audit` 가 그대로 읽음).

| 항목 | 결정 | 근거 |
|---|---|---|
| 비트맵 | `SectorBitmap`(BitSet), 바닐라 `RegionBitmap` 과 같은 첫 적합 | 같은 쓰기 시퀀스에서 같은 오프셋 |
| 로드 스캔 | 바닐라와 같은 세 검사, 잘못된 항목은 메모리에서 0(다음 헤더 기록 때 디스크에서도 0 — 바닐라와 같다) | 바닐라 동작 |
| 재할당 | 항상 새 자리 → 헤더 → 옛 해제. in-place 없음 | 바닐라와 같고 크래시 시 옛 데이터 유지 |
| 헤더 기록 | 8KB 통째로(위치+타임스탬프) | 바닐라와 같은 디스크 바이트 |
| 외부 `.mcc` | **헤더보다 먼저** 원자 교체(기존 Furnace 순서 유지) | 바닐라보다 엄격 — 어느 지점 크래시에도 옛/새 중 하나 |
| 패딩 | 기록 때 섹터 패딩 유지(파일 길이 항상 섹터 배수) | 바닐라 close 패딩과 결과 같음. 할당 결정은 파일 길이와 무관 |
| 트렁케이트 | 하지 않음(바닐라도 안 함) | 끝 섹터를 줄이면 크래시 중 위험만 늘어난다 |
| 겹친 헤더 | **바닐라에 없는 방어**: 로드 때 겹침이 보이면 표시해 두고, 해제 때 남은 항목 섹터를 다시 표시 | 바닐라는 한쪽 해제 시 다른 청크 섹터를 비워 다음 할당이 덮어쓴다. 겹침 없는 파일에서는 바닐라와 동일 |
| 실패 처리 | 기록 도중 IOException 이면 새 섹터를 표시한 채 둔다 | 헤더가 이미 새 자리를 가리켰을 수 있다. 누수일 뿐 손상 아님 |
| 동시성 | `readChunk`/`hasChunk`/`writeCompressed` 를 `synchronized` | 파일 잠금 밖 호출에도 비트맵 일관(바닐라도 synchronized) |
| fsync | 이번엔 넣지 않음 | 아래 "남은 위험" |

옛 Furnace 파일 호환: 낭비 섹터는 어떤 항목도 가리키지 않으므로 로드 스캔에서 빈 칸이 되고, 첫 적합이 그 구멍부터 재사용한다. 파일 크기는 줄지 않는다(바닐라도 압축하지 않음).

## 4. 시험 (`RegionSectorAllocatorTest`)

참조 모델 `VanillaRef` 는 바닐라 `RegionBitmap`·생성자 스캔·`write` 순서를 시험 안에 다시 쓴 것이다(디컴파일 코드 복사 아님).

1. `randomSequences_matchVanillaReference`: 4 시드 × 15,000 연산(칸 수 8/64/300/1024 로 덮어쓰기 비율 변화, 1~254섹터 + 드물게 외부 청크). **매 연산** 1024 항목 위치와 비트맵 전체를 참조와 비교, 2,500 연산마다 닫고 다시 열어(참조도 헤더 스캔으로 재구성) 내용 전체 확인.
2. `repeatedRewrite_doesNotGrow`: 5,000번 덮어써도 파일이 상한 안에 머문다(원래 증상).
3. `crashAtArbitraryPoint_everyChunkOldOrNew`: 600회. 임의 상태를 만든 뒤 한 번의 쓰기를 임의 바이트 지점(데이터/헤더 중간, `.mcc` 이동·삭제 직전 포함)에서 중단 → 다시 열어 대상 청크는 옛/새 중 하나, 다른 청크는 그대로, 이어서 30번 더 써도 겹침 없음.
4. `legacyFurnaceFile_reusesLeakedSectors`: 옛 할당기를 복제해 3,000번 써서 낭비 섹터가 많은 파일을 만들고, 열어서 5,000번 더 쓰며 매번 참조와 대조. 기존 청크 내용 유지, 파일이 늘지 않음.
5. `concurrentWritersOutsideLanes_keepBitmapConsistent`: 8 스레드 × 1,500 쓰기(+ 읽기)를 한 `RegionFile` 에 직접. 겹침 없음, 비트맵 = 헤더 + 살아 있는 항목, 내용·재오픈 확인.
6. `overlappingHeaderEntries_survivorNotClobbered`: 겹친 항목 하나를 다시 써도 다른 청크가 남는다.
7. `invalidEntries_droppedLikeVanilla`: 바닐라 세 검사 대조.

변이 확인: in-place 덮어쓰기를 되살리면 7개 중 6개 실패.
기존 `RegionFileTest`, `RegionDeflateOutsideLockTest`(같은 쓰기 시퀀스 두 경로 바이트 동일), `AnvilBulkDecodeParityTest`, `RegionFileStoreTest` 통과.

## 5. 남은 위험

- **fsync 없음**: 크래시 모의는 프로세스 크래시(커널에 넘긴 쓰기는 남음) 모델이다. 전원 손실 때는 OS 가 데이터보다 헤더를 먼저 디스크에 내릴 수 있어 새 헤더가 빈 섹터를 가리킬 수 있다. 바닐라 `sync-chunk-writes=true` 에 해당하는 옵션(DSYNC 또는 데이터 뒤 `force`)은 성능 측정 후 별도 작업.
- 헤더 8KB 기록이 섹터 단위로 찢겨도 바뀐 항목은 4바이트 정렬 하나뿐이라 옛/새 중 하나라고 **가정**한다(하드웨어 섹터 원자성).
- 바닐라 jar 로 만든 실제 `.mca` 로 할당 오프셋을 대조하지는 않았다(참조 모델은 디컴파일을 읽고 다시 쓴 것). 바닐라 서버 실행 대조는 미실시.
- 파일 크기는 줄지 않는다. 이미 커진 옛 파일은 구멍 재사용으로 더 커지지 않을 뿐이다.

## 6. 후속(검토 조건): 헤더 두 항목 쓰기, sync-chunk-writes 연결

### 헤더 기록
- sync 꺼짐: 바뀐 위치 항목 4B → 타임스탬프 항목 4B 만 쓴다. 메모리 사본과 디스크가 같으면 8KB 통째 쓰기와 결과 바이트가 같다. 시험 `twoEntryHeaderWrites_byteIdenticalToFullHeader`(3라운드 × 4,000 쓰기, 고정 시계, 무효 항목이 있는 파일 포함)에서 매 비교 시점 파일 전체 바이트가 같다.
- 무효 항목: 바닐라는 로드 때 메모리에서 0 으로 만들고 첫 헤더 기록 때 디스크에도 0 을 쓴다. 로드에서 지운 항목이 있으면(`headerDirty`) 첫 기록만 8KB 통째로 써서 같은 시점에 반영한다.
- **반박(부분):** sync 켜짐(기본값)에서는 두 번 쓰기가 동기화를 한 번 더 일으켜 8KB 한 번보다 느렸다(아래 표: 약 1,210 → 963 청크/초). 그래서 sync 면 8KB 를 한 번에 쓴다(바닐라와 같음). sync 꺼짐에서도 벽시계·CPU 차이는 잡음 범위다. 줄어드는 것은 write 호출 바이트(청크당 24,662 → 16,478B)뿐이고, 페이지 캐시는 4KB 페이지 단위로 내려 쓰므로 실제 디스크 기록량 차이는 크지 않을 것으로 **가정**한다.

### sync-chunk-writes
- `ServerProperties.syncChunkWrites`(기본 true, 바닐라 server.properties 와 같음)를 `FurnaceMain` → 차원별 region/entities/poi `RegionFileStore` → `RegionFile(path, sync)` 로 넘긴다. sync 면 `RandomAccessFile` 모드 `"rwd"`(리눅스에서 O_DSYNC, 바닐라 `StandardOpenOption.DSYNC` 와 같은 효과).
- 닫을 때는 바닐라처럼 sync 와 상관없이 `force(true)`.
- 시험 `syncFlag_controlsOpenModeAndCloseForces`: `/proc/self/fdinfo` 의 flags 로 O_DSYNC 켜짐/꺼짐 확인, close 때 force 1회, 스토어가 설정을 핸들에 넘김. 1·2인자 `RegionFileStore`/`RegionFile` 생성자(시험·도구용)는 sync 꺼짐.

### 측정 (`measure_saveThroughput`, 청크 1,000개 저장, 압축본 약 2~20KB, ext4, 4코어 VM, 한 스레드)

| 설정 | 1회 | 2회 | 3회 | 청크당 write 바이트 |
|---|---|---|---|---|
| sync 끔, 헤더 8KB | 43.1ms / CPU 22.0ms (23,204/s) | 48.2 / 23.3 (20,758/s) | 47.2 / 23.9 (21,196/s) | 24,662 |
| sync 끔, 두 항목 | 44.5 / 21.3 (22,483/s) | 51.1 / 30.9 (19,572/s) | 50.7 / 30.7 (19,731/s) | 16,478 |
| sync 켬, 헤더 8KB | 709.9 / 219.0 (1,409/s) | 828.0 / 204.1 (1,208/s) | 825.1 / 198.0 (1,212/s) | 24,662 |
| sync 켬, 두 항목(채택 안 함) | 1017.0 / 256.3 (983/s) | 1009.1 / 242.0 (991/s) | 1092.5 / 267.4 (915/s) | 16,478 |

sync 를 켜면 저장 처리량이 약 17배 떨어진다(약 21,000 → 1,200 청크/초). 청크 저장은 IO 레인에서 하고 F-14 역압이 있어 틱 지연보다는 저장 대기열 길이에 영향이 간다. 기본값은 바닐라와 같은 true 로 두고, 끌지는 사용자가 결정한다(`docs/user-todo.md`).
