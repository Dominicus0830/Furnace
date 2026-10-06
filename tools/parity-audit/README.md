# 순정 1.21.1 대 Furnace 월드 생성 대조 도구

순정 서버를 직접 돌려 만든 `.mca`를 정답지로 Furnace 출력을 블록·바이옴·heightmap 단위로 대조한다.
결과 해석은 [`docs/feature-parity/vanilla-audit-2026-10-07.md`](../../docs/feature-parity/vanilla-audit-2026-10-07.md), 당시 원본 출력은 `reports/`.

모든 도구는 Gradle 모듈이 아니라 **단일 파일 Java**다(JDK 21). 저장소 루트에서 실행한다.

## 준비
1. `./gradlew :furnace-server:compileJava` — `furnace-server/build/classes`, `build/resources` 생성.
2. 클래스패스 = `furnace-server/build/classes/java/main` + `furnace-server/build/resources/main` + `furnace-api/build/classes/java/main` + gson 2.11.0 + slf4j-api 2.0.16 + logback-classic/core 1.5.12(`~/.gradle/caches/modules-2`의 jar). 구분자는 Windows `;`.
3. 컴파일 — `javac -cp <클래스패스> -d <출력폴더> tools/parity-audit/*.java`. 이후 `java -cp "<출력폴더>;<클래스패스>" Harness ...` 처럼 실행.
4. 순정 서버 jar — `build/minecraft_server.1.21.1.jar`(gitignore 대상 `build/`). `RunVanilla`는 `eula.txt`에 **`eula=true`를 기록**하므로 Mojang EULA에 동의한 사람만 실행할 것.

## 흐름
| 단계 | 도구 | 인자 / 설명 |
|---|---|---|
| 순정 월드 생성 | `RunVanilla` | `<월드폴더> <시드> <포트> <서버jar> <java> <역순0/1> <cxMin> <cxMax> <czMin> <czMax> [구조물1/0] [데이터팩폴더]` — `generate-structures=false`(기본), `/tick freeze`, forceload 후 save 안정화까지 대기 |
| 블록·바이옴·heightmap 대조 | `Harness` | `<시드> <cxMin> <cxMax> <czMin> <czMax> <출력접두사> <월드1> [월드2 ...]` — 월드 2개 이상이면 순정 실행 간 차이(노이즈 바닥)로 hard 불일치를 분리. `FURNACE_MODE=carvers`면 지형+카버만 비교 |
| 순정끼리 비교 | `VV` | `<cxMin> <cxMax> <czMin> <czMax> <월드A> <월드B>` — 경계/내부·블록 종류·y대역 분포 |
| 지형 단독 정답지 | `MakePack` → `RunVanilla` | 순정 server jar 안 `data/minecraft/worldgen/biome/*.json`을 `dp_src/`에 풀고 `MakePack` 실행 → `nofeat/` 데이터팩(모든 바이옴 features 비움)을 `RunVanilla`의 마지막 인자로 |
| 가설 실험용 팩 | `dfnoop/` | `dark_forest_vegetation`의 거대 버섯 2항목을 `no_op`으로 바꾼 데이터팩(Furnace의 Noop 동작을 순정에서 흉내) |

## 보조 진단
| 도구 | 용도 |
|---|---|
| `BiomeDiff` | 바이옴 불일치 쿼트 나열 |
| `BiomeFx` | 바이옴 JSON의 기후·색·강수를 plains와 비교(`dp_src/` 필요) |
| `NoopUse` | 오버월드 바이옴이 쓰는 placed feature 중 미구현 타입 집계(저장소 루트에서 실행) |
| `StructScan` | 순정 월드의 구조물 시작점 종류별 개수 |
| `DesertSeed` → `PyrCmp`, `PyrOut` | 사막 신전이 생기는 시드 탐색, 신전 BB 안/밖 대조(순정 구조물 ON/OFF 월드 2개 필요) |
| `SpawnCheck` | `level.dat` 스폰 대 `defaultSpawn()` |
| `HmDbg`, `TreeCmp`, `CountBlk` | heightmap 컬럼 차이, 특정 블록 위치 겹침률, 블록 개수 |
| `PersistCheck`, `WorldPersistCheck`, `FurnaceMca`, `CountSaved` | 저장→재시작 후 바이옴 보존 종단 간 재현, 저장된 청크 수·NBT 키 구성 |

## 주의
- 순정 자체도 같은 명령으로 두 번 돌리면 장식 단계에서 0.05~0.6% 달라진다(이웃 feature 겹쳐 쓰기 순서). 비교는 항상 순정 2회 이상으로 노이즈를 재서 해석할 것.
- `Harness` 표준출력에 Furnace 내부 디버그(`SEA#…`)가 섞인다. 결과는 `<접두사>_report.txt`/`_chunks.csv`.
- 월드 폴더는 `.gitignore`에 없으므로 저장소 밖(임시 폴더)에서 생성할 것.
