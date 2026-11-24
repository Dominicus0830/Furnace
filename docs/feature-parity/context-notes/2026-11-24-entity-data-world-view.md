# 월드 엔티티의 칸 보기 (2026-11-24, 엔티티 데이터 설계 D5)

- 월드에 있는 엔티티(플레이어 제외)의 `getEntityData()` 는 `NmsWorldEntityData`(바닐라 모양 `SynchedEntityData` 하위 클래스)다.
  처음 부를 때 한 번 주인 region 에서 서버 보기(`ChunkInternals.watchEntityData`)를 받는다 — 틱 스레드가 아니면 기다리고, 다른 region·전역 스레드는
  ThreadOwnershipException(다른 NMS 엔티티 조작과 같은 규칙). 같은 엔티티는 같은 객체.
- 서버: `SynchedData.watch` 가 갱신기를 단다. 보기 전에는 칸 쓰기 경로에 null 검사 하나만 더해진다. 본 뒤에는 칸이 바뀔 때마다(set·setQuiet) 주인 스레드가
  그 칸만 API 값으로 바꿔 새 목록을 volatile 로 내건다. 값이 API 불변 사본이라 서버가 아이템 스택을 제자리에서 줄여도 읽는 쪽과 겹치지 않는다.
- 읽기(`get`)는 어느 스레드에서도 막히지 않는다: 내건 목록이 바뀌었으면 DataItem 값을 다시 채운다(같은 값 칸은 같은 객체 — PR 600 의 `furnaceAssign`).
  `itemsById` 반사는 마지막으로 읽은 때의 사본이고, 반사로 고친 값은 서버에 가지 않는다.
- 쓰기(`set`)는 `EntityEdit.SetData`(= `EntityOp.SetData`, 칸 배치 검사 + 서버 칸 쓰기·종류별 훅)로 주인에게 넘긴다. 바닐라처럼 같은 값이고 force 가 아니면 아무것도 안 한다 —
  이 거르기는 **서버 칸이 주인 스레드에서** 한다(사본과 견주면 그 사이 서버가 바꾼 값을 되돌리는 쓰기가 사라진다, 검토 PR 607). 서버가 "바뀌었다"고 답한 칸만 이 보기의 더러움에 든다.
  깃발 관용구(`entity-data-flag-set`)는 `EntityEdit.SetDataFlag` 한 번 — 주인 스레드에서 읽고 비트를 써서 다른 스레드의 깃발 쓰기와 엇갈려도 비트를 잃지 않는다.
- `packDirty`·`isDirty` 는 이 보기로 쓴 칸만(그 칸의 지금 서버 값). 서버 추적기의 더러움 표시는 건드리지 않는다 — 결정 항목
  `docs/user-todo/entries/2026-11-23-entity-data-packdirty.md`(추천 유지).
- 엔티티가 월드에서 빠지면(`Entity.setRemoved`) 서버가 `SynchedData.unwatch` 로 사본 갱신을 멈추고 갱신기가 서버 칸을 놓는다. 보기는 마지막 값으로 남는다.
- 함정(검토 PR 607 권장, 후속): 한 번 본 엔티티는 칸이 바뀔 때마다 목록 복사(칸 수만큼)를 낸다 — 자주 바뀌는 칸이면 틱당 합치기를 검토.
  플러그인이 `packDirty` 로 직접 보내면 서버 추적기도 같은 값을 보내 패킷이 한 번 더 간다(결정 항목 위와 같음).
- 넣기 전에 꺼낸 월드 밖 칸 객체는 넣은 뒤 쓰면 여전히 UnsupportedNmsCall(서버에 닿지 않는다) — 메시지가 `getEntityData()` 를 다시 부르라고 알린다.
- 접속한 플레이어의 칸은 아직 UnsupportedNmsCall(가짜 플레이어 설계 뒤).
