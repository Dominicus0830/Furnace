# T-122 엔티티 손잡이 — 확인 1건 (2026-11-15)

B3-1 결정(Paper 1.21.1 기본값과 같게)대로 손잡이 넷을 넣었습니다. 바닐라와 다른 기본값은 **밀기 상한 8**과 **종류별 추적 거리** 둘이고, 합치기 반경 둘은 Paper 기본이 바닐라와 같습니다.
바닐라로 돌리려면 `furnace.properties` 에 `collisions.max-entity-collisions=-1`, `entity-tracking-range.enabled=false`(또는 `config.preset=vanilla`).

## 한눈에

| 항목 | 지금 | 추천 |
|---|---|---|
| T122-1. 손잡이가 아닌 Paper 고정 변경 둘을 따를까 | 따르지 않음(바닐라 그대로) | **그대로 둠** — 손잡이가 아니라 늘 켜진 바닐라와 다른 동작이고, B3-1 은 손잡이 기본값 결정이었다 |

## T122-1. Paper·Spigot 이 손잡이와 함께 바꾼 고정 동작

- **무엇**: Paper 원문을 대조하다 손잡이 옆에 설정 없이 늘 켜진 변경 둘을 찾았습니다.
  1. 아이템 합치기 방향(Spigot `if (true || …)`): 바닐라는 개수가 적은 쪽을 많은 쪽에 합치는데, Spigot·Paper 는 늘 틱을 도는 쪽으로 합칩니다(어느 아이템이 남는지가 다름).
  2. 밀 수 없는 생물은 밀기 코드를 아예 건너뜀(Paper "Don't run entity collision code if not needed"): 사다리·덩굴에 매달린 몹은 바닐라에서 남을 밀고 cramming 피해도 받지만 Paper 에서는 둘 다 없습니다.
- **지금**: 둘 다 바닐라대로 두었습니다. 밀기 상한 장면은 이 차이와 상관없이 Paper 의 상한 규칙(횟수 세기·상한 8)과 같습니다.
- **선택지**: A(추천) 그대로 바닐라. B Paper 처럼 바꾸되 끄는 키를 둔다.
- **근거**: Paper 1.21.1 패치 `Cap-Entity-Collisions`·`Don-t-run-entity-collision-code-if-not-needed`, Paper ver/1.21.4 `ItemEntity.java.patch`.
