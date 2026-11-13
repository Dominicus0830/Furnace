# 퇴장 때 Bukkit 권한 정리 (PR #311 사후 검토 R1, 2026-11-13)

- Paper: `PlayerList.remove → CraftPlayer.disconnect → perm.clearPermissions()` — 퇴장 이벤트 처리기들이 다 본 뒤 권한 계산·구독(기본 권한 포함)이 걷힌다.
- Furnace 호환 계층은 가상 채널만 닫고 `clearPermissions` 는 재접속·표 훑기(`retire`)에서만 했다 → 퇴장한 `CraftPlayer` 가 `getDefaultPermSubscriptions`·`getPermissionSubscriptions` 에 남았다.
- 수정: `Compat.disconnected` 가 채널을 닫은 뒤 `clearPermissionState()` 를 **전역 스케줄러로 한 칸 미뤄** 부른다. 같은 MONITOR 에서 이 구독보다 늦게 등록된 Bukkit 퇴장 처리기도 Paper 처럼 권한을 본다(Furnace 는 같은 우선순위를 등록 순으로 부른다).
- 퇴장 구독(`quit-channel-close`)은 이벤트를 하나도 안 듣는 플러그인도 걸리도록 플러그인을 켤 때(`BukkitPluginAdapter.onEnable`)에도 건다(`EventBridge.watchQuit`).
- `clearPermissions` 는 Bukkit 첨부 목록을 지우지 않으므로(Paper 도 같다) Furnace 쪽 거울 첨부는 그대로 남고, 계산된 권한 맵·구독만 비운다. 재접속은 새 `CraftPlayer`(핸들이 다름)라 옛 객체의 정리가 새 접속에 영향이 없다(시험 `rejoinKeepsNewConnectionPermissions`).
- 별건(고치지 않음): 첫 `CraftPlayer` 를 만드는 `compute` 안에서 플러그인이 선언한 권한 객체가 **처음** 만들어지면(`SimplePluginManager.lookup` → `Permission.recalculatePermissibles` → `getOnlinePlayers` 순회 → 같은 UUID `Compat.player`) `IllegalStateException: Recursive update` 가 난다. 시험 플러그인이 `onEnable` 에서 `getPermission` 을 미리 불러 피했다.
