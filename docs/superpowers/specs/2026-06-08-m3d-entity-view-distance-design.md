# M3d — 엔티티 가시 거리 분리 Design

**상태:** 설계 승인 대기 → plan → 구현. M3 가족 후속(M3c 위에 쌓음, 브랜치 `m3c-region` 연속).

## 1. 문제

엔티티 가시 거리가 청크 렌더 거리(`view-distance`)에 묶여 있다. `Region.updateVisibility`:

```java
double range = world.viewDistance() * 16.0;   // vd=20 → 320블록
```

→ view-distance를 키우면(렌더 목적) 엔티티가 320블록 밖에서도 보임. 바닐라는 세 거리를 분리한다. 청크 렌더(view) / 엔티티 트래킹(Spigot `entity-tracking-range`) / 시뮬레이션(틱 범위). M3d는 **엔티티 가시 거리**를 분리한다.

## 2. 범위

**포함:** `view-distance`와 독립된 `entity-view-distance`(청크 단위) 설정 도입. 가시 계산 range·교차region 스냅샷 스캔 반경을 이 값 기준으로.

**제외(향후):** 시뮬레이션 거리(청크/몹/블록 틱 범위)는 **M4+**로 미룸. 지금 Furnace엔 틱 대상(몹·블록틱·아이템)이 없어 다스릴 게 없다. M4(블록 업데이트)·M5(엔티티/AI)서 틱 대상이 생기면 region 틱 루프를 "플레이어 주변 sim-distance 청크만 틱"으로 게이트한다. 지금은 region 전체 무조건 틱.

## 3. 핵심 결정

- **단위 = 청크.** `view-distance`와 같은 단위라 설정 일관. 기본 8(=128블록).
- **클램프.** `entity-view-distance > view-distance`면 미렌더 청크의 엔티티가 허공에 뜸. 유효값 = `min(entityViewDistance, viewDistance)`. 한 곳서 클램프해 range·neighborRadius 둘 다 같은 유효값 사용.
- **neighborRadius도 엔티티 거리 기준.** M3c는 `neighborRadius = ceil(viewDistance/regionSize)+1`. M3d는 유효 엔티티 거리 기준으로 바꿔 스냅샷 스캔 반경을 실제 가시 범위로 축소(최적화). 가시성에 필요한 후보는 엔티티 거리 내뿐이므로 정확성 무손실.

## 4. 동작

```
effectiveEntityChunks = min(entityViewDistance, viewDistance)
range(블록)            = effectiveEntityChunks * 16
neighborRadius         = ceil(effectiveEntityChunks / regionSize) + 1
```

최적화 예: vd=20, entity=8, regionSize=4 → 스냅샷 스캔 반경 `ceil(20/4)+1=6`칸 → `ceil(8/4)+1=3`칸. 후보 수 대폭 감소.

## 5. 컴포넌트 변경

| 파일 | 변경 |
|---|---|
| `config/Config.java` | record에 `int entityViewDistance` 추가, DEFAULT=8 |
| `config/ConfigLoader.java` | `entity-view-distance` 키 파싱(기본 Config.DEFAULT) |
| `world/World.java` | 생성자 +1 인자(`entityViewDistance`), 필드+게터. `neighborRadius()`를 유효 엔티티 거리 기준으로. 유효값 헬퍼(`effectiveEntityChunks()`) |
| `world/Region.java` | `updateVisibility`의 `range = world.effectiveEntityChunks() * 16.0` |
| `FurnaceMain.java` | World 생성에 `config.entityViewDistance()` 배선 |

> `WorldSource`/`Region.updateChunks`(청크 로드)는 무변경 — 여전히 `view-distance` 사용. 청크 렌더와 엔티티 가시는 이제 독립.

## 6. 데이터 흐름

설정 로드(ConfigLoader) → Config.entityViewDistance → World 생성자 → World.effectiveEntityChunks() → Region.updateVisibility range + World.neighborRadius(). 단일 방향, 불변.

## 7. 테스트

- `ConfigLoaderTest`: `entity-view-distance` 파싱 + 누락 시 기본 8.
- `SnapshotRegistryTest.neighborRadius`: 기대값을 엔티티 거리 기준으로 갱신(예 entity=10, regionSize=4 → 유효=min(10,vd) → ceil/regionSize+1).
- 신규 클램프 테스트: `entityViewDistance > viewDistance`면 유효값이 viewDistance로 클램프되는지.
- `CrossRegionVisibilityTest`: 회귀 확인 — entity 기본 8, A·B 16블록 → 여전히 보임(유효 range≥16블록).
- World 생성자 +1 인자 → 기존 테스트 호출부 갱신.

## 8. 향후 방향

세 거리 개념 전부 바닐라 표준이고 Furnace도 같은 길.
- **view-distance** — 청크 렌더(M2부터).
- **entity-view-distance** — 엔티티 가시(M3d, 본 spec).
- **simulation-distance** — 청크/몹/블록 틱 범위(M4+, 틱 대상 생길 때).
