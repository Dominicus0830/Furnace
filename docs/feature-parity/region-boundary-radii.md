# region 경계와 읽기 반경 목록 (M-07 K3, 2026-11-14)

동적 region 은 서로 다른 region 의 구역 사이에 `region-merge-radius` 개의 빈 구역을 둔다. 그래서 한 region 이 가진 칸에서 다른 region 의 칸까지 축 거리는 적어도 **띠 + 1** 이다.

| 설정 | 띠(블록) | 다른 region 까지 최소 거리 |
|---|---|---|
| shift 4, r1 (2026-11-14 전 기본) | 256 | 257 |
| **shift 3, r1 (기본, T-101)** | 128 | **129** |
| shift 2, r2 | 128 | 129 |
| shift 2, r1 (O01-01 에서 철회) | 64 | 65 |

**규칙: region 한정으로(그 region 의 청크·엔티티·POI 만으로) 읽는 반경은 띠 이하여야 결과가 바닐라와 같다.** 이보다 넓은 읽기는 차원 전체 색인을 쓰거나 전역 빌림 구간에서 해야 한다. 기본 띠는 `RegionBandRadiusTest` 가 지킨다(반경 상수를 늘리거나 기본 shift·radius 를 줄이면 실패).

## region 한정 읽기 (띠 128 안이어야 함)

| 읽기 | 반경 | 코드 | 여유(기본) |
|---|---|---|---|
| 번개가 피뢰침 찾기 | 128 | `ChunkLevel.LIGHTNING_ROD_SEARCH_RADIUS` | **0 — 경계값** |
| 오버월드 쪽 지옥문 찾기 | 128 정사각 | `PortalForcer.SEARCH_OVERWORLD` | **0 — 경계값** |
| 네더 쪽 지옥문 찾기 | 16 | `PortalForcer.SEARCH_NETHER` | 112 |
| 전달체 | 96 | | 32 |
| 습격 | 64 | | 64 |
| 신호기 | 50 | | 78 |
| 집·일터 POI, 종, 고양이 스폰 | 48 | | 80 |
| 폭발(기본 몹·블록, power 최대 6 → 엔티티 반경 12) | 12 | `block/Explosion.java` | 116 |

## 차원 전체로 읽음 (shift 와 무관)

- 가장 가까운 플레이어, 몹 스폰·사라짐 거리, 번개 발전과제 256: `playerIndex`·`nearestScan`·`forEachPlayer`.
- `/locate poi` 256: `PoiLocator` 가 region 을 넘어 읽는다.

## 띠를 넘을 수 있는 것 (알려진 차이)

- **NBT 로 키운 폭발**: 폭발 엔티티 상자는 2r+1(`Explosion.java` `radius * 2`)이라 r ≤ 64 면 띠 128 안이다. r 65~128 은 shift 4 에서는 띠 안이었고 **shift 3 에서 새로 넘는다**: 화염구 `ExplosionPower`(최대 127), TNT `explosion_power`(1.21, 0~128, `PrimedTnt`), 크리퍼 `ExplosionRadius`(byte, 충전 안 된 65~127 과 충전된 33~64). 129 이상(충전 + 값 65 이상)만 shift 4 에서도 이미 넘었다.
- **명령으로 키운 follow_range**(최대 2048): shift 4 에서도 이미 넘는다.
- **플러그인 region 한정 조회**(region 스레드의 `entitiesInBoxNow`·`getNearbyEntities` 상자, `rayTraceBlocksNow`·Bukkit `rayTraceBlocks` 광선 — 길이 제한 없음): 128블록 넘는 상자·광선은 다른 region 을 못 본다(`docs/api/compat-paper.md`). `nearbyEntitiesNow` 는 반경 0~128 로 막혀 있어 맞는다.
- **주민이 먼 POI 놓기**(`Villager.releasePoi`)와 안 올라온 청크 POI: K1(M-07 의 나머지, P-O01-11 차원 단위 POI 색인)에서 다룬다.
- **합치기 대기 창**(1틱 이하): 새 구역이 생기고 합쳐지기 전, 두 region 의 청크 사이 거리는 shift 3 에서 112블록 이상(shift 4 는 240)으로 128 아래다. 바닐라 128 읽기(피뢰침·지옥문)의 출발점은 틱이 도는 청크이고 로드 가장자리 한 줄은 틱이 돌지 않아 127+128=255 < 256 으로 1블록 여유가 남는다고 본다 — **추론이고 시험이 없다**(후속).

근거: `furnace/reviews/opt-review-r2/shift3-assessment.md` 6장(공유 폴더).
