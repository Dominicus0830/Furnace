## 2026-11-16 setPlayerProfile 결정 D1~D3 반영 (T-141 E11c-2, PR #449 코멘트로 승인)

앞선 노트(`e11c2a-profile-split`, `e11c2b-set-player-profile`)의 "이름 조회는 새 이름으로 찾는다(의도한 차이)"는 **철회**됐다. 아래가 현재 동작이다.

- **D1 — UUID 를 바꾸면 Paper 와 같은 패킷 + 콘솔 경고 한 번.** 패킷은 2b 가 이미 Paper 와 같은 순서로 낸다(`ProfileChangeTest#changedUuidGoesOnlyIntoTheReregistrationEntry`). 호환 `CraftPlayer.setPlayerProfile` 이 새 프로필 id 가 엔티티 UUID 와 다를 때 서버 전체에서 한 번만(`AtomicBoolean`) WARN 을 낸다(`compat.profile-uuid-changed`, 호출한 플러그인을 `NmsCaller.pluginName()` 으로 알면 이름 포함, 모르면 `…-unknown`). 같은 UUID(스킨·이름만 바꿈)는 경고 없음. 경고는 패킷을 바꾸지 않는다. 시험 `PlayerProfileSplitTest#changingTheUuidWarnsOnceServerWide`.
- **D2 — `getPlayerExact`·바닐라 이름 선택자·`/ban` 등은 접속 때의 이름으로 찾는다.** `ServerCore.playerByName` 이 `Player.joinName()`(접속 때 이름 고정 필드, 설계 문서에서는 `loginName`)으로 훑는다. `Bukkit.getPlayer(이름)` 은 정확 일치(접속 이름) 뒤 **현재 이름** 앞부분 일치(Paper `getPlayer(String)` 과 같음). 프로필 이름이 다른 접속자의 접속 이름과 같아도 접속 이름이 맞는 쪽이 이긴다. Paper 와 다른 점은 하나: 퇴장 뒤 옛 이름 항목이 남는 누수는 따르지 않는다(접속자를 훑으므로 퇴장하면 사라짐). 시험 `ProfileSplitTest#entityIdentityAndProfileGoWhereTheTableSays`·`nameLookupsUseTheLoginNameAndLeaveNothingBehind`, 호환 `PlayerProfileSplitTest#bukkitIdentityReadsTheRightSide`.
  - 호출처 점검: `EntitySelector`·`ScoreHolderArgument`·`PlayerListCommands`(banip)·`PluginHost`(`player(name)`·`offlinePlayerCached`·`profileByName`)·팀 사망 메시지(`broadcastDeathMessage` 의 팀원 이름 조회 — Paper 도 같은 이름 맵으로 찾는다)는 모두 접속 이름 쪽. 현재 이름이 필요한 곳은 호환 `CraftServer.getPlayer(String)` 의 앞부분 일치뿐이고 `Player.name()` 을 쓴다.
- **D3 — 탭을 PlayerJoinEvent 뒤로.** 이미 #452(단계 0)에서 끝나 있다(`ConnectionHandler.onEnterPlay`: 접속자 목록에 넣고 `holdTabListing` → 조인 이벤트 → 본인 탭 → 기존 접속자 탭 → `releaseTabListing`). 조인 이벤트 안의 `setPlayerProfile` 은 `tabListingHeld` 동안 프로필만 바꾸므로 리스폰 없이 탭이 처음부터 새 프로필로 나간다(`ProfileChangeTest#insideTheJoinEventOnlyTheProfileChanges`, `JoinTabOrderTest`). 이 스레드에서 새로 바꾼 것은 없다 — 별도 PR 불필요.

후속(#470 검토 비차단 참고):
- `Furnace#player(String)`·`profileByName` 의 API 문서에 "접속 때 이름으로 찾고, 돌려주는 프로필 이름은 다를 수 있다"를 적었다.
- 시험 전용 `CraftPlayer.resetUuidChangeWarning()` 은 `NmsCaller.resetWarnings()` 와 같은 방식이라 그대로 둔다. 나중에 시험 전용 위치(예: 시험 지원 클래스)로 옮길 후보.
- 앞선 노트 `e11c2a`·`e11c2b` 의 "새 이름으로 찾는다"는 이 노트가 철회했고, 설계 문서는 #469 가 맞춘다.
