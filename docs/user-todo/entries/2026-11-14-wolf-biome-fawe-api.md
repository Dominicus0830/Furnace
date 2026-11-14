# 늑대 스폰 오류·FAWE 빠진 API 고침 — 확인할 것 (2026-11-14)

배경: 11-14 latest.log(빌드 e19110ca). 근거와 숫자는 `docs/feature-parity/context-notes/2026-11-14-wolf-biome-edge-fawe-api.md`.

## 결정할 것 — 없음

## 직접 확인하면 좋은 것 (이 PR 이 들어간 빌드에서)
1. **FAWE**: 접속, `//wand`, 나무 도끼 좌·우클릭, 브러시 든 채 손 바꾸기, `//sel`. 로그에 `org/bukkit/Effect`, `Material.isLegacy`, `Player.isSneaking` 오류가 더는 없어야 합니다.
   블록 편집 자체(`//set` 등)는 아직 안 됩니다 — `BlockTypesCache` 오류는 NMS 어댑터(N2b) 일입니다.
2. **새 땅의 동물**: 새로 생긴 청크에 소·양·돼지·늑대가 예전보다 눈에 띄게 많아질 수 있습니다. 이게 바닐라와 같은 쪽입니다 — 예전엔 청크 생성 때 바이옴을 못 읽어
   동물 스폰을 건너뛴 청크가 적어도 17% 있었습니다.

## 따로 남긴 것(이 PR 범위 밖)
- FAWE 가 부르는데 아직 없는 Bukkit API 52개(목록은 위 문서 3장) — T-141(API 표면 확장)에서 다룹니다. 특히 `Player.sendBlockChange`, `getAllowFlight/setFlying`, `World.getSpawnLocation` 은 FAWE 를 더 쓰면 곧 걸릴 수 있습니다.
- 종료 때 `NoClassDefFoundError: org/h2/api/ErrorCode`(LuckPerms H2 의 JVM 종료 훅이 이미 닫힌 클래스 로더를 씀)로 크래시 리포트가 하나 생깁니다. Paper 에서도 나는지는 확인하지 않았습니다(가정: 데이터는 그 전에 LuckPerms 가 닫아 손실 없음).
