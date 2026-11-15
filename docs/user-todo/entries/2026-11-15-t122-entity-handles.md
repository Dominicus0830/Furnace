# T-122 엔티티 손잡이 — 결정 1건 (2026-11-15) — **확정(위임)**

B3-1 결정(Paper 1.21.1 기본값과 같게)대로 손잡이 넷을 넣었습니다(PR #395). 바닐라와 다른 기본값은 **밀기 상한 8**, **종류별 추적 거리**, 아래 T122-1 의 둘이고, 합치기 반경 둘은 Paper 기본이 바닐라와 같습니다.
바닐라로 돌리려면 `config.preset=vanilla`(또는 아래 끄는 키).

## 한눈에

| 항목 | 확정 |
|---|---|
| T122-1. 손잡이가 아닌 Paper 고정 변경 둘을 따를까 | **Paper 와 같게 기본 켬 + 끄는 키**(2026-11-15, 도미니코 위임에 따라 코디네이터가 Paper 호환 취지로 선정) |

## T122-1. Paper·Spigot 이 손잡이와 함께 바꾼 고정 동작

- **무엇**: Paper 원문 대조 중 설정 없이 늘 켜진 변경 둘을 찾았습니다.
  1. 아이템 합치기 방향(Spigot `if (true || …)`): 바닐라는 개수가 적은 쪽을 많은 쪽에 합치는데, Spigot·Paper 는 늘 틱을 도는 쪽으로 합칩니다(어느 아이템이 남는지가 다름). 키 `item-merge.always-into-ticking`(기본 true).
  2. 밀 수 없는 생물은 밀기 코드를 건너뜀(Paper "Don't run entity collision code if not needed"): 사다리·덩굴에 매달린 몹·관전자는 바닐라에서 남을 밀고 cramming 피해도 받지만 Paper 에서는 둘 다 없습니다. 키 `collisions.skip-unpushable`(기본 true).
- **처음 추천**은 바닐라 유지였으나, 위임 결정으로 Paper 와 같게 켰습니다. 끄면 바닐라와 같습니다. 기동 경고에 이 둘도 나옵니다.
- **근거**: Paper 1.21.1 패치 `Cap-Entity-Collisions`·`Don-t-run-entity-collision-code-if-not-needed`, Paper ver/1.21.4 `ItemEntity.java.patch`.

## T122-2. 플레이어 cramming 피해 (확정 — 2026-11-15 위임 결정, 검토 N1)

- **무엇**: Paper 1.21.1 은 `collisions.allow-player-cramming-damage` 기본 false 라, 한곳에 플레이어를 많이 모아도(텔레포트 집합 등) cramming 피해로 죽지 않습니다. 바닐라는 피해를 줍니다.
- **결정**: Paper 와 같게 기본 false. 키 `collisions.allow-player-cramming-damage=true`(또는 `config.preset=vanilla`)면 바닐라. Paper `paper-world-defaults.yml` 값은 가져오기가 옮깁니다. 기동 경고에 나옵니다.
- **근거**: Paper 1.21.1 패치 `Don-t-apply-cramming-damage-to-players`(ServerPlayer.isInvulnerableTo), `Paper-config-files`(WorldConfiguration.Collisions).
