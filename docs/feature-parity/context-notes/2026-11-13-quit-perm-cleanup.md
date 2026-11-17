# 퇴장 때 Bukkit 권한 정리 (PR #311 사후 검토 R1, 2026-11-13)

- Paper: `PlayerList.remove → CraftPlayer.disconnect → perm.clearPermissions()` — 퇴장 이벤트 처리기들이 다 본 뒤 권한 계산·구독(기본 권한 포함)이 걷힌다.
- Furnace 호환 계층은 가상 채널만 닫고 `clearPermissions` 는 재접속·표 훑기(`retire`)에서만 했다 → 퇴장한 `CraftPlayer` 가 `getDefaultPermSubscriptions`·`getPermissionSubscriptions` 에 남았다.
- 수정: `Compat.disconnected` 가 채널을 닫고 같은 스레드에서 곧바로 `clearPermissionState()` 한다. 퇴장 이벤트는 Netty 이벤트 루프 스레드(`channelInactive`)에서 불리므로 전역 스케줄러로 미루면 처리기보다 먼저 돌 수 있다(독립 검토 R1에서 재현) — 미루지 않는다.
- 순서: Furnace 는 같은 우선순위를 등록 순으로 부른다. 퇴장 정리 구독(플러그인별, `EventBridge.QUIT_WATCH`)은 플러그인을 켤 때(`BukkitPluginAdapter.onEnable`, 이벤트를 안 듣는 플러그인도 걸리게)와 처리기를 이을 때 걸고, Bukkit `PlayerQuitEvent` MONITOR 처리기가 이어질 때마다 모든 정리 구독을 떼었다 다시 걸어 같은 우선순위 맨 뒤에 둔다 → 모든 Bukkit 퇴장 처리기가 Paper 처럼 권한을 본 뒤에 정리된다. 플러그인이 꺼지면 그 구독을 뗀다.
- `clearPermissions` 는 Bukkit 첨부 목록을 지우지 않으므로(Paper 도 같다) Furnace 쪽 거울 첨부는 그대로 남고, 계산된 권한 맵·구독만 비운다. 재접속은 새 `CraftPlayer`(핸들이 다름)라 옛 객체의 정리가 새 접속에 영향이 없다(시험 `rejoinKeepsNewConnectionPermissions`).
- 별건(고치지 않음): 첫 `CraftPlayer` 를 만드는 `compute` 안에서 플러그인이 선언한 권한 객체가 **처음** 만들어지면(`SimplePluginManager.lookup` → `Permission.recalculatePermissibles` → `getOnlinePlayers` 순회 → 같은 UUID `Compat.player`) `IllegalStateException: Recursive update` 가 난다. 시험 플러그인이 `onEnable` 에서 `getPermission` 을 미리 불러 피했다.
