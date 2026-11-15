# 2026-11-15 관전자 용기 엿보기(읽기 전용)

E7a(#393) 검토 N1 의 후속. 전에는 `BlockPlacement.useItemOn` 이 관전자면 늘 PASS 라 관전자가 용기를 아예 못 열었고,
`canUnlock` 의 관전자 예외는 닿지 않는 코드였다.

## 바닐라 1.21.1 동작(이번에 맞춘 것)
- `ServerPlayerGameMode.useItemOn` 관전자 길: `getMenuProvider(level, pos)` 가 있으면 `openMenu` → SUCCESS, 없으면 PASS.
  블록의 `useWithoutItem` 을 거치지 않으므로 통계(open_barrel 등)·피글린 분노·셜커 상자 뚜껑 검사가 없다.
- 메뉴 제공자가 있는 블록: 블록 엔티티가 MenuProvider 인 것(상자·덫 상자·통·셜커 상자·화로 3종·깔때기·발사기·공급기·양조기·제작기·신호기·독서대 — 독서대는 `LecternBlock.getMenuProvider` 가 책이 없으면 null)
  과 `getMenuProvider` 를 덮어쓴 블록(제작대·대장장이 작업대(Furnace 에서 CraftingTableBlock 하위라 분기 순서 주의)·석재 절단기·숫돌·모루·마법부여대·베틀·지도 제작대).
  엔더 상자·모닥불·조각된 책장·장식 항아리는 아니다. 상자는 (짝까지) 위가 막혔으면 `combine(..., false)` 라 메뉴가 없다.
- 잠금: `canUnlock` 은 관전자면 늘 참(알림·소리 없음).
- 전리품: `RandomizableContainerBlockEntity.canOpen` 이 전리품 표가 남았으면 관전자를 막고(풀지도 않는다),
  `ServerPlayer.openMenu` 가 메뉴 null + 관전자면 빨간 `container.spectatorCantOpen` 을 액션 바로.
- 여는 수: 상자·통·셜커 상자·엔더 상자의 `startOpen/stopOpen` 은 `!player.isSpectator()` 일 때만(그 순간의 모드로 판단).
  `recheckOpeners` 의 실제 수 세기는 관전자도 센다(바닐라 그대로, Furnace `countContainerOpeners` 도 같다).
- 읽기 전용: `handleContainerClick` 은 관전자면 `sendAllDataToRemote` 만(이미 있었음), 버튼·레시피 놓기·제작기 칸 상태도 관전자 무시(이미 있었음).

## 확인
- 독립 검토(PR #400)가 바닐라 1.21.1 바이트코드로 블록 분기·전리품·여는 수·액션 바·목록을 대조했다. 독서대(책 없음)와 대장장이 작업대 분기 순서를 그 검토에서 고쳤다.
- 블록 → 메뉴 표는 `ContainerLockE2ETest.spectatorMenuPerBlock` 이 지킨다.
