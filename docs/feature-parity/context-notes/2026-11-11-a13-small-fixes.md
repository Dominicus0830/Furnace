# A13 수정 묶음 2·3 (2026-11-11)

- A13-02: `CloneCommand.tryClear(be, level)` 가 주크박스(`clearContent` → 음반 비움·재생 정지)와 독서대(`setBook(EMPTY)`)를 비운다. clone move·setblock·fill 이 쓴다. 음반·책이 떨어져 복제되지 않는다.
- A13-03: `/spreadplayers` respectTeams 는 플레이어의 팀(`Player.team()`)별로 위치를 나눈다. 플레이어가 아닌 엔티티·팀 없는 대상은 한 그룹(바닐라 getNumberOfTeams). 점수판이 안 붙은 플레이어는 팀 없음.
- A13-05: 선택자 `type=!x` 뒤 양성 `type=y`·`type=#태그` 는 `Option 'type' isn't applicable here`(바닐라 isTypeLimitedInversely).
- A13-06: `/setworldspawn`·`/spawnpoint` 범위 밖 오류 키는 `argument.pos.outofbounds`. (`forceload` 등은 outofworld 그대로.)
- A13-07: `particle item` 의 컴포넌트를 저장 형식 코덱(`ItemNbt.read`)으로 읽는다. `vibration` 의 entity 위치 소스는 UUID 조회가 region 왕복이라 아직 거절한다(남음).
- A13-08: `DataPackManager.reload` 가 읽기에 실패하면 이전 팩 선택으로 되돌린다.
- A13-09: `/enchant` 성공 메시지는 인챈트 정의의 `description` 텍스트를 쓰고, 정의가 없을 때만 기본 번역 키를 쓴다.
- 남은 것: A13-01(술어 코덱)·A13-04(텍스트 JSON, 결정 대기).
