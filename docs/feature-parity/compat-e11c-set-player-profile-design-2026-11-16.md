# T-141 E11c-2 `Player#setPlayerProfile` 설계 (2026-11-16)

목표: Paper 1.21.1 과 관측 결과가 같은 **완전판**(UUID·이름·skin 모두 교체). "skin 만" 같은 반쪽 구현은 하지 않는다. 이 문서는 구현 전 설계 검토용이며 코드는 없다.

## 1. Paper 가 하는 일 (CraftPlayer.setPlayerProfile, 동작만 비교)
1. `sentListPacket` 이 false(아직 탭 목록을 안 보냄)면 `gameProfile` 만 바꾸고 끝.
2. 아니면 (a) 나를 볼 수 있는 모든 플레이어에게서 나를 **등록 해제**(엔티티 제거 + 탭 제거), (b) `gameProfile` 교체, (c) 같은 플레이어 모두에게 **다시 등록**(탭 추가 + 엔티티 재표시; 이때만 엔티티 UUID 를 새 프로필 UUID 로 잠깐 바꿔 보냄), (d) 본인 화면 갱신 `refreshPlayer`.
3. `refreshPlayer` = 리스폰 패킷(KEEP_ALL_DATA) → 능력치 → 현재 위치 텔레포트 → 권한 레벨 → 월드 정보 → 전체 플레이어 정보 → 경험치 → 활성 효과.
4. 서버 쪽 의미: **엔티티 UUID(`getUniqueId`)는 안 바뀐다.** 바뀌는 것은 게임 프로필(`getPlayerProfile`·`getName`·탭·op·화이트리스트 조회 키).

## 2. Furnace 의 지금 모양
- `Player.uuid`/`name` 은 `final` 이고 엔티티 UUID 와 프로필 UUID 를 겸한다(`Player.java` 76~77행). `profileProperties` 만 가변.
- 접속자 맵은 **entityId 키**(`World.onlinePlayer(eid)`), 탭 기록 `listed` 도 eid→uuid, 시야 규칙 `EntityViewRules.TARGETS` 만 UUID 키. 이름·UUID 조회는 `ServerCore.playerByName/playerByUuid` 가 접속자를 훑는 방식이라 캐시가 없다.
- 프로필을 읽는 곳: `ServerCore.profileOf(p)`(op·화이트리스트·밴 조회, 명령), `Region.syncTabList`(탭 항목), `ConnectionHandler.tabEntry`, `PlayerChatState(profileId)`(서명 채팅 세션), playerdata·stats·advancements 파일 이름(`<uuid>.dat/.json`), 스코어보드(이름 키), 플러그인 쪽 `PlayerHandle`·`OfflinePlayerImpl`.

## 3. 설계: "엔티티 정체성"과 "프로필"을 나눈다 (Paper 와 같은 모양)
- `Player.uuid()` 는 **엔티티 UUID 로 그대로** 둔다 → playerdata·stats·advancements 파일, 접속자 중복 검사, `EntityViewRules.TARGETS`, 엔티티 맵, 저장은 전혀 안 건드린다.
- 새 `volatile GameProfile profile`(id·name·properties, 불변 값)을 `Player` 에 둔다. 접속 시 로그인 결과로 채운다. `uuid()`/`name()` 의 **의미를 나누어**, 프로필을 뜻하는 호출처는 `profileId()`/`profileName()`/`profile()` 로 옮긴다(`name()` 호출은 많아 한 번에 기계적으로 치환하지 말고 호출처를 표로 분류한다: 프로필 의미 = 표시 이름·명령 선택자·op/화이트리스트/밴·탭·채팅 서명 / 정체성 의미 = 파일·맵·로그).
- 프로필을 읽는 곳이 모두 한 접근자를 거치게 한 뒤(먼저 리팩터링 PR, 동작 불변), 설정 길을 붙인다. 순서가 중요: 접근자 분리 PR 이 먼저 병합돼야 한다.

## 4. 스레드 소유·재등록 방식
- 대상 플레이어의 region 스레드에서 `profile` 을 **한 번에 바꾸고(volatile 참조 교체)** 세대 번호 `profileGen` 을 올린다. 관찰자에게는 직접 패킷을 쓰지 않는다(탭·추적 상태는 관찰자 region 소유: `a.listed()`·`stopTracking` 은 관찰자 스레드 전용).
- 관찰자 region 은 매 틱 `syncTabList` 에서 `listed` 항목의 `profileGen` 을 비교해 **어긋난 항목을 감지**한다(`rosterEpoch` 처럼 전역 세대 번호를 올려 빠르게 건너뜀): 어긋난 대상 → (a) `RemoveEntities`+`PlayerInfoRemove`(옛 프로필 id), (b) 탭 `PlayerInfoUpdate`(새 프로필) → (c) 다음 추적 틱에 엔티티 스폰. 플러그인이 숨긴 대상은 건너뜀(`EntityViewRules.canSee`). 이렇게 하면 "내 region 이 남의 region 에 작업을 던진다"가 없고 순서도 관찰자 틱 안에서 고정된다.
- 이벤트·플러그인 스레드에서 호출되면 `Owned` 규칙대로 대상 플레이어 region 으로 작업을 올린다(다른 스레드에서 직접 안 바꿈).
- 탭 전송 전(`sentListPacket` 에 해당: 로그인 직후)에는 `profile` 만 바꾼다.

## 5. 본인 화면 갱신 패킷 묶음
Paper 순서 그대로: 리스폰(현재 차원·게임 모드·`KEEP_ALL_DATA`) → 능력치 → 현재 위치로 텔레포트(승인 번호 증가) → 권한 레벨 → 월드 정보(보더·시간·날씨) → 전체 플레이어 정보(탭) → 경험치 → 효과. `ConnectionHandler` 의 리스폰·`finishRespawn` 에서 "차원 이동 없이 다시 보내기" 부분만 함수로 뽑아 쓴다(새 로직을 만들기보다 기존 조각 재사용). 대상 클라이언트가 접속 직후(월드 로딩 중)면 건너뛴다.

## 6. 같이 따라가야 하는 곳 (체크리스트)
| 곳 | 따라가는 방식 |
|---|---|
| 탭 목록·엔티티 스폰(UUID 덮어쓰기) | §4. Paper 는 **재등록 때만** 엔티티 UUID 를 새 프로필 id 로 보낸다 — 나중에 시야에 다시 들어올 때의 스폰 UUID 가 어느 쪽인지 Paper 로 확인해 같은 값을 쓴다(미확인 = 가정) |
| op·화이트리스트·밴 | 프로필 접근자로 조회(§3) |
| 명령 선택자·`playerByName` | 프로필 이름. 같은 이름 둘이 되면 Paper 와 같게 먼저 찾은 쪽(확인 필요) |
| 서명 채팅 세션 | `PlayerChatState(profileId)` 는 로그인 시 키와 묶임 — 프로필 교체 뒤 Paper 가 어떻게 되는지 확인, 모르면 동작을 그대로 두고 명시 |
| 스코어보드 | 항목 이름이 프로필 이름이라 이름 교체 시 항목 이전 여부를 Paper 와 확인 |
| 호환 `getName`·`getPlayerProfile`·`getDisplayName` | 프로필 이름 |
| 호환·서버 `getUniqueId` | 엔티티 UUID 유지 |

## 7. 단계 나누기
1. **E11c-2a**(동작 불변 리팩터링): `Player.profile` 도입 + 호출처 분류·접근자 분리. 시험: 기존 전체.
2. **E11c-2b**: 관찰자 쪽 프로필 세대 감지·재등록(`syncTabList`) + 호환 `setPlayerProfile` + 본인 갱신 묶음. 시험(서버): 두 플레이어 시험 서버에서 관찰자가 받은 패킷 순서(제거→탭→스폰)와 본인 패킷 순서, 숨김 플레이어, 로그인 직후 호출.
3. **E11c-2c**: 위 표의 "Paper 와 확인" 항목 정리(Paper 로 실측 가능한 것은 실서버 대조).

## 8. 위험
- `name()` 호출처 분류 실수 → 파일 이름이 바뀌거나 명령이 어긋남. 대응: 2a 에서 분류표를 PR 에 올리고 독립 검토.
- 재등록 도중 관찰자가 옛 프로필로 한 틱 더 보임: 관찰자 region 이 감지할 때까지 한 틱 지연(Paper 는 같은 틱에 모두 처리). 관측 차이가 문제면 대상 region 이 관찰자 region 에 작업을 보내는 방식으로 바꿀 것 — 검토에서 결정.
