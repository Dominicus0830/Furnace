# A16-01 몹 위치·체력·생사를 소유 스레드에서 지금 값으로 (2026-11-11)

- `EntityHandle`(Furnace 핸들)의 `location()`·`health()`·`isValid()` 는 만든 시점 값이다. 플러그인이 쥐고 있는 Bukkit 객체(`CraftEntity`)가 이것을 그대로 돌려 줘서 `setHealth` 직후 `getHealth`·`teleport` 직후 `getLocation`·`remove` 직후 `isValid` 가 옛 값이었다.
- 고침: `EntityState.location()·remove()`, `LivingEntityState.health()·maxHealth()·setHealth()` 를 더하고, `CraftEntity`·`CraftLivingEntity` 가 소유 스레드(`EntityStates` 규칙)면 지금 값을 읽고 쓴다. 소유 스레드가 아니거나 플레이어(상태 손잡이 없음, 핸들이 곧 지금 값)면 예전 경로.
- 함정: `Entity.remove()`(API)는 큐로 다음 틱에 지운다 — 같은 스레드에서 곧바로 `isValid` 를 읽는 Bukkit 코드를 위해 상태 손잡이의 `remove()`(바닐라 `discard`, 플래그만)를 쓴다. `stateNow()` 는 사라진 엔티티에는 `Optional.empty()` 를 주고 소유 스레드 검사보다 먼저 비어 있음을 본다(시험에서 `orElseThrow` 가 NoSuchElementException).
- 못 고친 것: 비동기 스레드에서의 읽기(여전히 핸들 시점), NMS shim 의 `getX/Y/Z·isRemoved·isAlive`(어댑터 스레드 몫). 보고서 `docs/audit/findings/A16-nms-craftbukkit-libraries.md`.
- 검토 반영(PR #230 독립 검토): `setHealth(0)` 은 `kill()` 이 아니라 `LivingEntity.dieFromApi()`(체력 0 + `die(generic)`) — `kill()` 은 `hurt` 를 거쳐 피해 이벤트 처리기 안에서 재귀한다. 스폰 이벤트 안 `remove()` 는 `addFreshEntity` 가 이벤트 뒤 `isRemoved` 를 본다. `Entity.isOwnedByCurrentThread()` 로 예외 없이 소유를 묻는다(플레이어는 false, 사라진 엔티티는 true). `isValid` 는 살아 있는 엔티티의 체력>0 을 함께 본다.
- `JavaPluginWiringTest#permissionAndExceptionsFollowBukkitRules` 불안정: `server.tick()` 뒤 `TestPlayer.sync()` 없이 메시지를 읽던 경쟁 — `steve.sync()` 를 더했다.
