## 2026-11-20 플레이어가 아닌 엔티티의 다른 월드 텔레포트 (entity-teleport)

**증상(고치기 전).** `EntityHandle.teleport/teleportNow` 가 다른 월드면 곧바로 false 였고, Bukkit `EntityTeleportEvent` 는 호환 계층에도 Furnace API 에도
없었다(같은 월드 이동에도 Paper 는 부른다). 서버 안에는 포털·`/tp` 용 차원 이동 길(`Entity.moveAcross`)이 이미 있었다.

**Paper 1.21.1 가 하는 일(PaperMC 33ff3dff 패치 문맥 + Youer 1.21.1 CraftEntity 사본으로 확인; Paper 소스는 커밋하지 않는다).**
`CraftEntity#teleport(loc, cause, flags)`:
1. 승객이 있는 탈것(`isVehicle`)·사라진 엔티티는 false(아무것도 안 바꾼다).
2. **EntityTeleportEvent #1**(from=지금 위치, to=요청). 취소하거나 `to == null` 이면 false, 아니면 `location = event.getTo()`.
3. 탈것에서 내린다(`stopRiding`) — 이벤트 뒤. 그래서 취소하면 탄 채로 남는다.
4. 월드가 다르면 `entity.changeDimension(new DimensionTransition(월드, 위치, 속도 ZERO, location.getPitch(), location.getYaw(), DO_NOTHING, PLUGIN))` 하고 **무조건 true**.
   - CraftBukkit 은 `DimensionTransition(…, yRot, xRot, …)` 자리에 (pitch, yaw) 순으로 넘긴다 → 도착한 엔티티의 yRot 가 요청한 pitch 값이 된다(xRot 는 지금 그대로, Paper 가 `getXRot` 로 고쳐 쓴다).
   - `changeDimension` 안에서 **EntityTeleportEvent #2**(to=목적 월드, yaw 자리=DimensionTransition.yRot, pitch=지금 xRot). 취소하면 null 이어도 teleport 는 true(탄 데서는 내렸다).
   - 새 엔티티를 만들어 `restoreFrom`(UUID·저장 데이터)·moveTo(위치, yRot, 지금 xRot)·속도 0.
5. 같은 월드면 `moveTo`(속도 0, Paper) + `setYHeadRot(yaw)`(SPIGOT-619), true.

**Furnace 구현.**
- API `EntityTeleportEvent`(취소·`to` 수정). 서버 `GameEvents.entityTeleport` 가 이벤트를 부르고 결과 도착 위치(취소면 null)를 돌려준다. 호환 Bukkit `EntityTeleportEvent` + EventBridge(`to` 쓰기·읽기).
- `EntityHandle.moveEntity` 가 위 순서를 그대로(이벤트 → 내리기 → 같은 월드/다른 월드). 다른 월드는 `Entity.changeDimensionTo`(= `moveAcross`, 포털과 같은 changeDimension: 승객 없음, resetEmptyTime 둘 다, 낙하 블록 훅, 포털 소리·티켓 없음). 도착 region 은 목적 월드가 비동기로 정한다.
- 반환: `teleport()` future 는 도착(또는 되돌림) 뒤 true. `teleportNow` 는 시작이 받아들여지면 true(도착은 비동기 — Paper 의 동기 호출과 다른 점, 청크를 막고 기다리지 않는다).

**아직 안 한 것(후속).**
- 같은 월드 안에서 목적지가 **다른 region** 이면 여전히 false(T-196 의 핸들 핸드오프). 같은 `moveAcross` 길을 쓰면 되지만 이번 범위가 아니다.
- 포털로 건너는 엔티티는 Paper 가 changeDimension 안에서 EntityTeleportEvent 를 (그리고 EntityPortalExitEvent 를) 더 부르는데 Furnace 포털 경로는 아직 안 부른다. Bukkit `EntityPortalEvent`(EntityTeleportEvent 의 하위)도 호환 계층에 클래스가 없다.
- 두 번째 이벤트가 도착 월드를 원래 월드로 되돌리면 Paper 는 같은 월드 changeDimension 이다. Furnace 는 `moveAcross` 를 그대로 태운다(드문 경우, 미검증).
- 플러그인이 건 엔티티 작업(EntityScheduler)은 옛 엔티티가 빠질 때 끝난다(포털과 같다).
