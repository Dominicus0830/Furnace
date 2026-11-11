# A05 청크·저장·영속성 — 조사 보고서

- 기준 커밋: `claude/quirky-wright-lra834` 8f8b188f (PR #170 병합 뒤). 시험을 돌린 뒤 기준이 5f794a3a(PR #171, 문서만)로 움직였지만 코드는 같다.
- 조사 범위: `world/anvil`(RegionFile·RegionFileStore·IoLanes·Compression·AnvilChunkCodec), `world/gen/AnvilWorldSource`, `Region` 의 청크 적재·퇴출·자동 저장, `World` 의 저장 레인·`flushAllDrained`, `world/storage`(LevelStorage·DirectoryLock·BukkitWorldImport), `entity/PlayerData`, `ForcedChunks`·`RaidStore`·`ServerScoreboard`·`CommandStorage`·`MapStore` 의 파일 쓰기, `maintenance/WorldBackup`(코드 읽기), `datafix`(청크·플레이어 쪽에 쓰이는지).
- 바닐라 대조: 1.21.1 서버 바이트코드(`javap`) — `ChunkMap`(aqb) 의 적재 실패 처리·저장 쿨다운, `PlayerDataStorage`(eri), `LevelChunkSection`(dvj), `Main` 의 판 검사 문자열. Paper 대조: 1.21.1 커밋 `33ff3dff` 의 `Moonrise-optimisation-patches.patch`·`Rewrite-dataconverter-system.patch`.
- 조사하지 못한 범위: 바닐라 서버 jar 를 이 환경에서 띄워 만든 월드와의 바이트 왕복(Mojang 라이브러리 403) — 바이트코드 대조로 대신했다. 봇 접속 시험은 하지 않았다(손상 청크 위에 선 플레이어의 화면은 사용자 확인으로 넘긴다). 백업 zip 의 부하 중 일관성은 O4 시험(`WorldBackupTest`)이 이미 다뤄 코드 읽기만 했다. 5장 색인의 `q8-pr119`·`m12-pr117`·`m12-pr120` 지적은 다시 보지 않았다.
- 재현: `A05-repro/*.java.txt` 네 파일, 시험 13개. 저장소 `furnace-server/src/test/java/...` 의 같은 패키지(파일 머리 `package` 줄)에 `.java` 로 두고 `:furnace-server:test --tests '*A05*'` 로 돌린다. 기준 커밋 결과: 바닐라 기대를 단언한 12개 실패(= 재현), 강제 종료 확인 시험 1개 통과.

## 확인한 것 (바닐라와 같거나 더 안전함)

| 검사 | 방법 | 결과 |
|---|---|---|
| 쓰는 중 SIGKILL 뒤 region 파일 | `A05KillDuringWriteTest`: 자식 JVM 이 청크 48개를 크기를 바꿔 가며(가끔 1MB 넘겨 `.mcc` 외부 청크로) 쉬지 않고 덮어쓰는 중 `destroyForcibly`, 다시 열어 모든 청크 내용 대조. 같은 파일로 5회 | 통과. 240개 모두 어느 한 판의 온전한 내용(외부 판 29개 포함), 파일 길이 항상 4096 배수. 새 자리 쓰기 → 헤더 → 옛 섹터 해제 순서가 지켜진다 |
| `level.dat` | 코드 읽기 | 임시 파일 → `level.dat_old` 백업 → 교체, 읽기 실패 시 `_old` 로 복원(바닐라 `safeReplaceFile`·`restoreLevelDataFromOld` 와 같다) |
| `data/*.dat`(chunks·raids·scoreboard·command_storage·map) | 코드 읽기 | 임시 파일 뒤 이동 — 바닐라(`SavedData.save` 가 제자리 쓰기)보다 안전. 읽기 실패는 로그 뒤 빈 값(바닐라와 같다) |
| `session.lock` | 코드 읽기 | 바닐라 `DirectoryLock` 과 같은 내용·잠금 |
| 퇴출·재적재 순서 | 코드 읽기 | 같은 `.mca` 의 저장·읽기가 한 IO 레인(FIFO)이라 저장 대기 중 재적재가 옛 내용을 읽지 않는다. 엔티티·POI 도 같은 레인 |
| 압축 종류 | 코드 읽기 | 1·2·3·4 는 바닐라와 같다. 5(zstd)는 Furnace 전용이고 `RegionConvert` 로 되돌리는 길이 문서화돼 있다 |
| Bukkit 월드 가져오기 | 코드 읽기 | 원본을 고치지 않고 임시 폴더 복사 → 원자 이동, 표시 파일로 두 번 가져오지 않음 |

## 어긋난 것

| ID | 심각도 | 종류 | 근거 | 재현 | 제안 |
|---|---|---|---|---|---|
| A05-01 | 높음 | 바닐라 차이 | 깨진 청크(압축 본문 손상·NBT 잘림·팔레트 범위 밖 인덱스)는 `AnvilWorldSource.loadChunkAsync`(91·100·131행)에서 예외가 되고 `Region.finishLoad`(457행)가 "다음 요청에서 재시도" 로그만 남긴다. 그 청크는 **영영 올라오지 않는다**: 같은 파일을 다시 읽어 또 실패하고, 시야 미리 불러오기(`sendQueuedChunksAuto`, 2669행)는 틱마다 다시 걸어 경고 두 줄(스택 포함)을 계속 찍는다. 플레이어에게는 그 자리가 빈 구멍이다. 바닐라 1.21.1 `ChunkMap.handleChunkLoadFailure`(aqb `a(Throwable, dcd)`): `Error` 가 아니면 `reportChunkLoadFailure` 로그 뒤 `createEmptyChunk` — **새로 생성**하고 다음 저장에서 덮는다. 팔레트 범위 밖은 `LevelChunkSection` 생성자(dvj)의 블록 수 세기가 `MissingPaletteEntryException`(dvn, RuntimeException)을 내 같은 길을 탄다. Paper Moonrise `ChunkDataLoadTask`: "chunk data will be lost" 로그 뒤 빈 `ProtoChunk` | `A05ChunkCorruptionReproTest` 2개 실패: 깨진 zlib → `UncheckedIOException: 청크 로드 실패 0,0`, 팔레트 인덱스 15/2 → `ArrayIndexOutOfBoundsException`. 두 번째 요청도 같은 실패 | `AnvilWorldSource.loadChunkAsync`·`generate`: 읽기·풀기·디코드 실패를 잡아 로그(바닐라 `reportChunkLoadFailure` 문구 "Failed to load chunk {},{}") 뒤 `generateFallback` 으로 넘기고 dirty 로 둔다. 바닐라처럼 덮어쓰기 전에 원본 바이트를 남길지(예: `region/corrupt/c.x.z.nbt`)는 바닐라 밖 친절이라 선택. `Error` 는 그대로 던진다. 작음. Sonnet(high) |
| A05-02 | 높음 | 바닐라 차이 | `PlayerData.load`(262~286행)는 `<uuid>.dat` 만 본다. 없으면 null, 깨졌으면 `.corrupt` 로 옮기고 null — **새 플레이어로 시작**(인벤토리·위치·경험치 없음). 그런데 저장 `write`(246~249행)는 ".dat → .dat_old 이동" 다음 "임시 → .dat 이동" 이라 그 사이에 꺼지면 `.dat` 이 없고 최신본은 `.dat_old` 에만 있다. 이어서 새 플레이어로 저장이 두 번 되면 `.dat_old` 까지 새 데이터로 덮여 손으로도 되살릴 수 없다. 바닐라 `PlayerDataStorage.load`(eri `b(cmx)`): `.dat` 을 못 읽으면 그 파일을 날짜 붙여 복사해 두고 `Optional.or(() -> load(".dat_old"))`. Paper 도 같다(변환기만 바뀜) | `A05PlayerDataFallbackReproTest` 3개 실패: 이동 사이 종료 뒤 load → null(바닐라는 `.dat_old` x=200), `.dat` 손상 → null(바닐라는 x=100), 새 플레이어 저장 두 번 뒤 `.dat_old` x=0 | `PlayerData.load`·`readRoot`: `.dat` 이 없거나 깨지면 `.dat_old` 를 읽는다(깨진 `.dat` 은 지금처럼 `.corrupt` 로 남김). 기존 시험 `PlayerDataVanillaTest.corruptFile_returnsNull_andKeepsBackup` 은 `.dat_old` 없는 경우로 좁힌다. 작음. Sonnet(medium) |
| A05-03 | 높음 | 바닐라 차이 | 월드를 열 때 `level.dat` 의 판(`Data.Version.Id`·`DataVersion`)을 보지 않는다(`LevelStorage.read` 63행, `LevelData.fromNbt` 110행). 1.21.2 이후 판의 월드를 열면 레지스트리에 없는 블록(`pale_oak_log` 등)이 `BlockRegistry.stateIdOrAir` 로 공기가 되고, 그 청크가 저장되면 **영구히 지워진다**. 바닐라 1.21.1 `Main`: 판이 맞지 않으면 "This world was created by an incompatible version." 를 찍고 기동하지 않는다(문자열 확인) | `A05DataVersionReproTest.newerWorld_isRefusedOrKeepsUnknownBlocks` 실패: DataVersion 4189 `level.dat` 을 그대로 열고, 같은 판 청크를 읽고 다시 쓰면 팔레트가 `minecraft:air` | 기동 때 `level.dat` 판이 3955 보다 크면 바닐라 문구로 멈춘다(주 월드·추가 월드 적재 모두). 바닐라처럼 강제 옵션은 두지 않는다. 작음. Sonnet(medium) |
| A05-04 | 높음 | 바닐라 차이 | 청크·플레이어·엔티티 파일에 데이터 픽서가 없다(`datafix` 는 구조물 템플릿 `TemplateManager` 에만 쓰인다). `AnvilChunkCodec.fromNbt`(315행)·`PlayerData.vanillaStack`(453행)은 DataVersion 을 보지 않는다. 결과: (1) 1.20.4 이하 상자·인벤토리 아이템 `Count:byte` 를 못 읽어 **모두 1개**가 되고 `tag`(마법 부여·이름)도 잃는다(`count` 키가 없으면 1, 415·453행). (2) 1.20.3 에서 이름이 바뀐 블록(`grass`→`short_grass`)이 공기. (3) 1.18 이전 `Level` 감싼 청크는 `sections` 가 없어 **빈(공기) 청크로, 깨끗하게(dirty 아님)** 올라온다 — 옛 월드를 열면 땅이 비어 보이고 플레이어가 떨어진다. 바닐라는 `ChunkMap.upgradeChunkTag`·`DataFixTypes.PLAYER`(eri `a(cmx, ub)` 의 `uq.b` → `azw.a`)로 올린 뒤 읽는다. Paper 는 같은 자리에서 `MCDataConverter`. 1차 감사(`vanilla-diff-2026-10-07.md` 173행 "DataVersion 확인·데이터 픽서 없음", DIFF M)가 적은 것이 아직 열려 있고, 이번에 실제 손실 크기를 재현했다 | `A05DataVersionReproTest` 4개 실패: 1.20.4 상자 다이아몬드 64→1, 1.20.4 플레이어 핫바 64→1, 1.20.1 `grass`→`air`, 1.16.5 청크 stone→`air`(깨끗한 청크) | 범위를 먼저 정해야 한다 — `docs/user-todo/entries/2026-11-08-audit-A05.md` 결정 1. 추천은 "옛 판 청크·플레이어·엔티티 파일을 Q8 픽서 표(`FixStep`, 1452~3954)를 넓혀 올리기"이고, 그 전까지의 안전장치로 **3955 미만 청크는 읽기만 하고 저장하지 않거나(원본 보존) 기동 때 경고 후 멈춤**. 픽서 포팅은 큼. Opus(high). 안전장치만은 작음, Sonnet(medium) |
| A05-05 | 참고 | 바닐라 차이 | 청크는 자동 저장 주기(`autosave-ticks` 6000, `Region` 1315행)와 퇴출 때만 쓴다. 바닐라 1.21.1 `ChunkMap.processUnloads`(aqb `b(BooleanSupplier)`)는 틱에 여유가 있으면 바뀐 청크를 틱당 20개까지, 청크마다 10초 쿨다운(`chunkSaveCooldowns`, `10000L`)으로 바로바로 쓴다. 그래서 `kill -9`·정전 뒤 블록 변경이 되돌아가는 폭이 바닐라는 약 10초, Furnace 는 최대 5분이다. Paper 1.21.1(Moonrise)은 이 바닐라 루프를 지우고 `chunkHolderManager.autoSave()` 로 청크마다 `auto-save-interval` 에 한 번 — **Furnace 와 같다**. 플레이어 데이터는 바닐라도 자동 저장 주기라, 바닐라에선 "블록은 저장됐는데 인벤토리는 되돌아감"(설치한 블록 복제) 틈이 있고 Furnace·Paper 는 둘이 같이 되돌아간다 | 바이트코드·Paper 패치 대조(시험 없음) | 바꾸지 않는 것을 추천(Paper 와 같고 크래시 뒤 블록·인벤토리 어긋남이 덜하다). 바꾼다면 `Region` 에 바닐라식 10초 쿨다운 저장을 옵션으로. 결정 2 |
| A05-06 | 낮음 | 문서 | `AnvilChunkCodec` 머리 주석 6행 "조명은 아직 서버가 계산하지 않으므로 isLightOn=0" 은 지금 코드(`toNbt` 가 빛이 있으면 SkyLight·BlockLight 와 isLightOn=1 을 쓴다)와 다르다. `RegionFile` 126~127행은 javadoc 두 개가 붙어 앞 것(`readChunk` 설명)이 `hasChunk` 에 걸린다 | 읽기 | 주석만 고친다. 작음. Haiku(low) |
| A05-07 | 참고 | 운영 | 외부 청크 쓰기 도중(`c.x.z.mcc.tmp` 쓰기·이동 사이) 꺼지면 `.mcc.tmp` 가 남는다(강제 종료 시험 1회차에서 1개). 다음에 그 청크가 외부로 쓰일 때 덮이므로 손상은 아니고, 백업은 `*.tmp` 를 뺀다. 바닐라도 외부 청크를 `Files.createTempFile(…, "tmp", …)` 로 쓰고 옮겨(dwp) 같은 틈에 임시 파일을 남긴다 | `A05KillDuringWriteTest` 출력 "남은 .mcc.tmp 1" | 그대로 둔다. 원하면 `RegionFile` 을 열 때 그 region 의 낡은 `.mcc.tmp` 를 지운다(작음) |

## 사용자 확인·결정 필요

`docs/user-todo/entries/2026-11-08-audit-A05.md` — 결정 2건(A05-04 범위, A05-05 저장 주기), 확인 1건(A05-01 고친 뒤 손상 청크 자리 화면).

## 심각도별 항목 수

- 치명: 0 / 높음: 4 / 중간: 0 / 낮음: 1 / 참고: 2
