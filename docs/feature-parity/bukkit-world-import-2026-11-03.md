# Paper/Spigot 월드 폴더 가져오기 (2026-11-03)

근거: `docs/feature-parity/paper-spigot-gap-2026-11-03.md` 6장 3번(PR #15).

## 문제
CraftBukkit 계열(Spigot·Paper)은 차원마다 월드 폴더를 따로 둔다.

| 차원 | 바닐라·Furnace | Paper/Spigot |
|---|---|---|
| 오버월드 | `<level>/region` … | `<level>/region` … (같음) |
| 네더 | `<level>/DIM-1/…` | `<level>_nether/DIM-1/…` (+ `<level>_nether/level.dat`) |
| 엔드 | `<level>/DIM1/…` | `<level>_the_end/DIM1/…` (+ `<level>_the_end/level.dat`) |

Furnace 는 바닐라 배치만 읽어서, Paper 서버 폴더를 그대로 옮겨 오면 네더·엔드가 새로 생성됐다(기존 차원 데이터 무시).

## 동작 (`world/storage/BukkitWorldImport`, `FurnaceMain` 에서 level.dat 을 읽은 직후·차원 월드를 만들기 전)
1. `<universe>/<level>_nether/DIM-1` 이 있고 `<level>/DIM-1` 이 없거나 **파일이 하나도 없는 빈 폴더**면 통째로 복사한다. 엔드(`_the_end/DIM1`)도 같다.
   - `<level>/DIM-1.furnace-import` 에 먼저 복사한 뒤 한 번에 이름을 바꾼다. 중간에 꺼지면 다음 기동 때 임시 폴더를 지우고 다시 복사한다.
   - 복사가 끝난 폴더에 `furnace-imported-from.txt`(원본 경로)를 남긴다. 다음 기동부터는 조용히 넘어간다.
   - 심볼릭 링크는 따라가지 않고 링크 그대로 복사한다. `session.lock` 은 복사하지 않는다.
2. 엔드를 가져왔으면 `<level>_the_end/level.dat` 의 `Data.DragonFight` 를 주 level.dat 으로 가져오고 바로 저장한다.
   CraftBukkit 은 엔드 월드 자신의 level.dat 에 드래곤 전투를 쓰고, 주 level.dat 의 값은 기본값이라 그대로 두면 "드래곤을 이미 잡았다"·관문 상태를 잃는다.
3. 바닐라 폴더에 이미 파일이 있고 표시 파일이 없으면(둘 다 있는 경우) 경고만 하고 바닐라 쪽을 쓴다. 섞어 쓰지 않는다.
4. 원본(`<level>_nether`, `<level>_the_end`)은 읽기만 한다. 로그에 "확인이 끝나면 지워도 된다"고 안내한다.
5. 복사에 실패하면(디스크 부족·권한) 서버를 띄우지 않고 끝낸다 — 빈 네더가 새로 생성돼 나중에 가져오기를 막는 일을 피한다.

**옮기기 대신 복사를 고른 이유:** CraftBukkit 이 반대 방향(바닐라 → Bukkit) 이전 때 복사하고 원본을 남기는 것과 같고, 실패·되돌리기가 안전하다. 대신 첫 기동 때 네더·엔드 크기만큼 디스크를 더 쓴다.
"그 경로를 직접 읽기"는 차원 폴더를 쓰는 곳(region·entities·poi·data·raids·forced chunks)이 여럿이라 고칠 곳이 늘고, 다시 Paper 로 돌아갈 때 두 배치가 섞일 위험이 있어 택하지 않았다.

## 보고서와 추가 Bukkit 월드 (2026-11-07, O3/Q4)

`world/storage/BukkitWorldReport` 가 `BukkitWorldImport.run` 직후 `<universe>/furnace-world-import-report.txt` 를 한 번 쓰고 로그에 요약한다(설정 가져오기는 [paper-config-import-2026-11-07.md](paper-config-import-2026-11-07.md)).
Paper 흔적(`bukkit.yml`·`spigot.yml`·`config/paper-global.yml`·`paper.yml`, 또는 `<level>_nether`·`<level>_the_end` 폴더)이 없으면 아무것도 하지 않는다. 보고서가 있으면 다시 쓰지 않고, 새로 가져온 차원이 생기면 갱신한다.

- **가져오지 않은 것**: 네더·엔드의 시간·날씨·스폰·월드 보더(주 월드 것을 씀), `<level>_nether`·`_the_end` 의 `level.dat` 게임 규칙이 주 월드와 다르면 규칙 이름과 값, 날씨가 다르면 알림, 월드별 `paper-world.yml`, 모르는 최상위 파일.
- **추가 Bukkit 월드**: 서버 폴더 바로 아래에서 `level.dat` 와 청크 폴더(`region` · `DIM-1/region` · `DIM1/region`)가 있고 Furnace 설명서(`furnace:world`)가 없는 폴더를 찾는다(주 월드·`_nether`·`_the_end`·`plugins` 등은 제외).
  Bukkit 과 Furnace(W1)의 추가 월드 폴더 배치가 같아서(NORMAL=`<이름>/region`, NETHER=`<이름>/DIM-1/region`, THE_END=`<이름>/DIM1/region`) **복사하지 않고 그 자리에서 올린다.** 합쳐 옮길 것이 없다 —
  Bukkit 의 `<이름>_nether` 는 `<이름>` 과 별개의 월드(NETHER 환경)이기 때문이다. 자동으로 올리지는 않는다: `/worlds load <이름>` 이나 `furnace.properties` 의 `worlds.autoload` 에 넣는다
  (자동으로 올리면 같은 이름으로 `createWorld` 를 부르는 플러그인과 설명서가 달라 충돌할 수 있다). 보고서가 그 줄을 보여 준다.
- **환경·생성기 추론**: 설명서가 없는 폴더를 올릴 때 환경은 폴더 배치로, 생성기는 `level.dat` 의 `WorldGenSettings.dimensions.<차원>.generator`(`minecraft:flat` → 평지(바이옴·층 포함), `minecraft:noise` + `large_biomes`/`amplified`), 구조물은 `generate_features` 로 정한다.
  `/worlds load` 도 같은 추론을 쓴다(전에는 설명서가 없으면 항상 NORMAL·기본 생성기라, Paper 가 만든 네더 월드가 오버월드로 올라왔다). 플러그인 생성기(`ChunkGenerator`)는 `level.dat` 에 안 남아 새 청크는 바닐라 생성기로 만든다. 평지 높이 0 층은 건너뛴다. 생성기 설정을 읽지 못하면(예: 형식이 잘못된 바이옴) 기본 생성기로 대신하지 않고 올리지 않는다(올리면 설명서가 `level.dat` 에 영구히 남아 지형이 섞인다).
- 이름이 월드 이름 규칙(영문·숫자·`_ . -` 1~64자)에 안 맞는 폴더(예: 공백·한글)는 "올릴 수 없다"로 보고한다.

## 가져오지 않는 것
- `<level>_nether/level.dat`, `<level>_the_end/level.dat` 의 DragonFight 외 값(Bukkit 월드별 게임 규칙·날씨·시간). 바닐라처럼 주 level.dat 하나를 쓴다.
- `paper-world.yml`·`uid.dat` 등 Bukkit 전용 파일.
- 반대 방향(Furnace → Paper) 변환. Paper 는 처음 켤 때 스스로 바닐라 배치를 옮긴다.
- 청크 안의 Paper 전용 NBT 는 따로 다루지 않는다(일반 청크 읽기와 같음).

## 시험
- 단위: `BukkitWorldImportTest` 7개 — 두 차원 복사·원본 보존·DragonFight, 두 번째 기동 무동작, 기존 바닐라 차원 우선, 빈 폴더 대체, 중단된 임시 폴더 재시도, 바닐라 배치 무변화, level-name 반영.
- 오프라인 서버(2026-11-03, 평지 월드): Furnace 로 네더 (3,70,3) 에 다이아몬드 블록·엔드 같은 자리에 에메랄드 블록을 두고 저장 → `world/DIM-1`·`DIM1` 을 `world_nether/`·`world_the_end/` 로 옮기고 `world_the_end/level.dat` 의 DragonFight 를 DragonKilled=1 로 만듦 → 재기동.
  로그에 두 차원 가져오기·드래곤 전투 가져오기가 찍히고, `execute in … if block` 이 두 블록 모두 "Test passed", 강제 로드 청크(`data/chunks.dat`)도 유지, 주 level.dat 의 DragonKilled=1, 원본 폴더는 그대로. 세 번째 기동은 아무 로그 없이 넘어감.
- 보고서·추가 월드: `BukkitWorldReportTest` 5개(게임 규칙·날씨·모르는 파일 보고, 추가 월드 감지·환경(NORMAL/NETHER/THE_END)·생성기(평지 층·large_biomes) 추론·부적합 이름, 한 번만 쓰기·새 차원 때 갱신, Paper 흔적 없으면 무동작) + `MultiWorldTest` 에 설명서 없는 네더 폴더 로드 1개.
- 실제 Paper 서버가 만든 월드로는 아직 확인하지 않았다(컨테이너에서 Paper 를 받을 수 없음) — `docs/user-todo.md` 시험 항목.
