# T-141 E8 — BlockLockCheckEvent (2026-11-15)

기준: Paper 1.21.1(33ff3dff) `patches/api/Add-BlockLockCheckEvent.patch`·`patches/server/Add-BlockLockCheckEvent.patch`.

- Paper 는 `BaseContainerBlockEntity.canUnlock(player, lock, name, blockEntity)` 에서 그 블록 엔티티가 아직 그 칸에 있으면 이벤트를 부른다.
  잠기지 않은 블록·관전자에게도 불린다. 결과가 `ALLOW` 면 연다. `DENY` 이거나 (관전자가 아니고 열쇠가 안 맞으면) 알림 글을 액션 바로, 소리를 그 플레이어에게 보내고 막는다
  (각각 null 이면 안 보냄). 열쇠는 이벤트가 정한 것, 아니면 주 손.
- Furnace: 서버 API `org.domi.furnace.api.event.block.BlockLockCheckEvent`(결과·열쇠·알림 글 `Text`·소리 `Sound`)를 `ConnectionHandler.canUnlock` 에서
  구독자가 있을 때만 부른다(`GameEvents.blockLockCheck`). 구독자가 없으면 바닐라 검사 그대로(이벤트 객체를 만들지 않는다). 큰 상자는 반쪽마다, 첫째(type=right)부터 —
  첫째가 막히면 둘째는 안 불린다(바닐라 `&&`). 신호기도 불린다.
- 기본 열쇠는 **누를 때 주 손**(#393 R1 과 같은 까닭 — 열기가 다른 차례로 넘어간다). Paper 의 `getKeyItem()` 기본값은 이벤트 때의 주 손이라 같은 차례 안에서는 같다.
- Bukkit 호환 이벤트에는 Adventure 판 멤버(알림 글·소리)가 없다(호환 계층 관례, NoSuchMethodError). 결과·열쇠만 주고받는다.
- 구멍: Furnace 관전자는 아직 용기를 아예 열지 못해 이 검사까지 오지 않는다(Paper 는 관전자에게도 이벤트를 부른다). 관전자 용기 열기를 고칠 때 같이 시험한다.
