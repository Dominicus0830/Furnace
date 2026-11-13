# 첫 CraftPlayer 감싸기 중 "Recursive update" (T-073 사후 검토 R4, 2026-11-13)

- 사슬: `Compat.player` → `players.compute` 안에서 `new CraftPlayer` → `PermissibleBase.recalculatePermissions` → `getDefaultPermissions` → 처음 보는 노드의 `lookup` → `new Permission` → `recalculatePermissibles` → `getPermissionSubscriptions` → `getOnlinePlayers` → 같은 UUID `Compat.player` → compute 재진입. plugin.yml 에 `default: true` 권한을 선언한 평범한 플러그인에서 join·quit 처리기가 매번 실패했다.
- 수정 ① `Compat.player` 는 새 CraftPlayer 를 **표 밖에서** 만들고 `putIfAbsent`/`replace` 로 올린다(진 쪽은 `retire`). compute 칸 잠금 안에서 권한 계산을 하지 않으니 같은 칸 재진입도, 서로 다른 UUID 두 스레드의 칸 교착도 없다.
- 수정 ② 권한 계산 경로의 접속자 보충(`getPermissionSubscriptions`·`getDefaultPermSubscriptions`)은 `Compat.onlinePlayersSettled()` — **이 스레드가 만드는 중인 플레이어만 뺀** 접속자. Paper 는 새 플레이어의 CraftPlayer 를 접속자 목록에 넣기 전에 만들므로 같은 결과이고, 그 플레이어는 생성이 끝날 때 스스로 계산한다. 같은 플레이어를 생성 중에 다시 감싸려 하면 무한 재귀 대신 `IllegalStateException`.
- 검토자 권고(`Compat.existingPlayer` 만 보기)와 다른 점: 아직 안 감싼 접속자를 **빼지 않고 감싼다**. Paper 에서는 접속자 전원이 CraftPlayer 를 가져 `getDefaultPermSubscriptions(false)` 에 모두 있기 때문(안 감싼 접속자를 빼면 구독 목록 크기·내용이 Paper 와 다르다). 대신 중첩 감싸기에서 같은 노드의 Bukkit `Permission` 이 둘 생기지 않게 `lookup` 이 생성 뒤 다시 확인한다.
- 시험 `FirstPlayerPermissionRecursionTest` 5개(수정 전 5개 모두 실패): 첫 접속자 생성, 둘째가 첫째를 봄, 스레드 8개 동시 첫 감싸기(교착·중복 없음), 평범한 플러그인의 join·quit 처리기, 중첩 감싸기에서 권한 객체 하나.
