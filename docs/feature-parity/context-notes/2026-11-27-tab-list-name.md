# 탭 목록 표시 이름 (2026-11-27, 영역 8)

- 바닐라/Paper: `ServerPlayer.listName`(null=기본), 탭 항목의 update_display_name 칸으로 나간다. Paper `setPlayerListName` 은 `canSee` 인 모두에게 UPDATE_DISPLAY_NAME 을 즉시 보낸다.
- Furnace: `Player.ListName(text, generation)` 묶음 + `Player.Listed.nameGeneration`. 설정하면 접속자 번호를 올려 각 관찰자 region 이 다음 틱 `syncTabList` 에서 `PlayerInfoUpdate.updateDisplayName`(0x20) 한 항목을 보낸다. 같은 값이어도 부를 때마다 세대가 올라 다시 보낸다(Paper 도 부를 때마다 보냄). 빈 문자열은 null.
- 접속 탭·프로필 교체 때의 add 항목에도 이름이 실린다(`tabEntry`).
- 차이: Paper 는 부른 자리에서 즉시 보내고 Furnace 는 관찰자의 다음 틱에 보낸다(패킷 내용·대상은 같다). 접속 때 이름은 저장되지 않는다(Paper 도 같음).
- API: Furnace `Player.listName()/listName(Text)`, Bukkit `getPlayerListName/setPlayerListName`(이름과 같으면 null), Paper `playerListName()/(Component)`, NMS `ServerPlayer.getTabListDisplayName()`. NMS 공개 필드 `listName` 은 shim 에 없다(필드는 서버와 연결할 수 없음) — 필요한 플러그인이 나오면 근거 따라 처리.
