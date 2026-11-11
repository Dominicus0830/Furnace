# 독서대 책 글 풀기·/attribute 행운 (PR #217 후속 R2·R3)

- 바닐라 `drk`(LecternBlockEntity): `setBook(cuq)`는 `a(cuq, null)` → `b(cuq, cmx)` 에서 레벨이 서버 레벨이고 `written_book_content` 가 있으면 `cwr.a(스택, 독서대 소스, 플레이어)`(resolveBookComponents)를 부른다. 소스는 `a(cmx)`: 플레이어 없으면 이름·표시 이름 "Lectern", 권한 2, 칸 가운데, 회전 0. 책 올리기(`dkj` tryPlaceBook)는 `drk.b(cuq)` 라 플레이어는 늘 null. NBT 읽기(`loadAdditional`)도 같은 `b` 를 거치지만 청크를 읽을 때는 레벨이 없어 풀리지 않는다.
- Furnace: `LecternBlockEntity.resolveBook` → `BlockLevel.resolveLecternBook` → `ServerCore.resolveLecternBook`(`CommandSource.lectern`). 책 열기와 같은 쪽 풀기 `ItemUse.resolveBookStack` 를 공용으로 쓴다. 다른 region 엔티티를 찾는 선택자는 늦게 끝나고, 그새 책이 바뀌었으면 버린다(표지판과 같은 한계).
- `PlayerAttributes.value(Player, attr)`: 맵의 기본값과 /attribute 로 단 수정자(장비·효과·달리기·위치 효과가 아닌 것)를 합친다 — 낡은 managed 수정자는 세지 않아 동기 틱 전에도 정확하다. 전리품 luck·낚시·공격력·이동 속도 등 이 함수를 쓰는 모든 계산에 /attribute 가 반영된다.
