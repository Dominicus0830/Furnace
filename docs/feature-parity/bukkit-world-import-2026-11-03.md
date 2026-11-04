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

## 가져오지 않는 것
- `<level>_nether/level.dat`, `<level>_the_end/level.dat` 의 DragonFight 외 값(Bukkit 월드별 게임 규칙·날씨·시간). 바닐라처럼 주 level.dat 하나를 쓴다.
- `paper-world.yml`·`uid.dat` 등 Bukkit 전용 파일.
- 반대 방향(Furnace → Paper) 변환. Paper 는 처음 켤 때 스스로 바닐라 배치를 옮긴다.
- 청크 안의 Paper 전용 NBT 는 따로 다루지 않는다(일반 청크 읽기와 같음).

## 시험
- 단위: `BukkitWorldImportTest` 7개 — 두 차원 복사·원본 보존·DragonFight, 두 번째 기동 무동작, 기존 바닐라 차원 우선, 빈 폴더 대체, 중단된 임시 폴더 재시도, 바닐라 배치 무변화, level-name 반영.
- 오프라인 서버(2026-11-03, 평지 월드): Furnace 로 네더 (3,70,3) 에 다이아몬드 블록·엔드 같은 자리에 에메랄드 블록을 두고 저장 → `world/DIM-1`·`DIM1` 을 `world_nether/`·`world_the_end/` 로 옮기고 `world_the_end/level.dat` 의 DragonFight 를 DragonKilled=1 로 만듦 → 재기동.
  로그에 두 차원 가져오기·드래곤 전투 가져오기가 찍히고, `execute in … if block` 이 두 블록 모두 "Test passed", 강제 로드 청크(`data/chunks.dat`)도 유지, 주 level.dat 의 DragonKilled=1, 원본 폴더는 그대로. 세 번째 기동은 아무 로그 없이 넘어감.
- 실제 Paper 서버가 만든 월드로는 아직 확인하지 않았다(컨테이너에서 Paper 를 받을 수 없음) — `docs/user-todo.md` 시험 항목.
