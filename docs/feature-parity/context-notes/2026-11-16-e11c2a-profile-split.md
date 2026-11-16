## 2026-11-16 엔티티 UUID 와 게임 프로필 나누기 (T-141 E11c-2a)

설계 `docs/feature-parity/compat-e11c-set-player-profile-design-2026-11-16.md` §3 표대로 서버 `Player` 의 정체성을 둘로 나눴다(동작 불변 — 아직 프로필을 바꾸는 길이 없어 늘 같다).

- 엔티티 UUID `Player.uuid()`: 파일(playerdata·stats·advancements), 접속자 맵·UUID 조회, 숨김 규칙, 탭의 UUID 칸, 채팅 서명 디코더·sender(`PlayerChatState` 의 `ownerUuid`).
- 게임 프로필 `Player.profile()`/`profileState()`(불변 `{GameProfile, 세대}` 한 volatile 묶음): `name()`·`profileProperties()`·선택자 이름·스코어보드·op·화이트리스트·밴(`ServerCore.profileOf`)·접속 권한 레벨·상태 sample·채팅 세션 키 검증(`data.validate(profile().id())`).
- 접속 때 이름 `Player.joinName()`: 채팅 기본 표시 이름(`chatDisplayName`, Paper `adventure$displayName`). 바닐라 `displayName()` 은 프로필 이름.
- furnace-api `Player#profile()`(기본 구현 추가, 공개 API 변화는 이것뿐 — `javap` 대조). 호환: `getPlayerProfile`·NMS `gameProfile` 은 `profile()`, `getDisplayName` 은 사용자 지정이 없으면 접속 이름(`PlayerConnection#name`).
- 시험 훅: `ServerLoginContext#profileForTest`(로그인 때 다른 프로필 주입), `Player#profileForTest`. 시험 `ProfileSplitTest`(서버, 표의 줄마다)·`PlayerProfileSplitTest`(호환).
- 알려진 차이(설계 §8): 이름 조회(`playerByName`·`getPlayerExact`)는 지금 프로필 이름으로 찾는다(Paper 는 접속 때 이름).
