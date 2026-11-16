# T-141 E11c-2 `Player#setPlayerProfile` 설계 (2026-11-16, 독립 검토 R1~R7·권장 반영판)

목표: Paper 1.21.1 과 관측 결과가 같은 **완전판**(UUID·이름·skin 모두 교체). "skin 만" 같은 반쪽 구현은 하지 않는다. 구현 전 설계 문서이며 코드는 없다. 근거 원문: Paper `33ff3dff` 의 `Player.setPlayerProfile-API`·`Adventure`·`Add-sendOpLevel-API`·`Add-Listing-API-for-Player` 패치(검토가 확인한 것. 패치에 없는 CraftBukkit·바닐라 본문은 "추론"으로 표시, 구현 때 Paper 로 실측).

**원칙(코디네이터 결정):** 기본은 Paper 와 같은 동작이다. UUID 를 바꾸는 경우의 관찰자 화면 처리도 Paper 패킷 그대로(§1 R1). Furnace 만 다르게 가는 곳은 이 문서에 근거와 함께 "의도한 차이"로 적는다(§8: 퇴장 뒤 이름 맵 누수 하나뿐).

**확정된 결정(도미니코 승인 2026-11-16, PR #449 코멘트):** D1 = UUID 를 바꾸면 Paper 와 같은 패킷을 보내고 개발자용 콘솔 경고를 **한 번만** 낸다(§1 R1). D2 = `getPlayerExact`·바닐라 선택자는 **접속 때의 이름**으로, `Bukkit.getPlayer` 앞부분 일치는 현재 이름으로 찾고 퇴장 뒤 누수는 따르지 않는다(§8, 앞선 "새 이름으로 찾는다" 추천은 철회). D3 = 탭 목록을 PlayerJoinEvent 뒤로 옮기는 일은 별도 PR(§4, §9 단계 0).

## 1. Paper 가 하는 일 (CraftPlayer.setPlayerProfile)
1. `sentListPacket` 이 false 면 `gameProfile` 만 바꾸고 끝. (접속 중 **PlayerJoinEvent 뒤에** 접속자 탭을 보내고 true 로 둔다 → 조인 이벤트 안에서 부르면 리스폰 없이 프로필만 바뀌고 탭도 처음부터 새 프로필로 나간다.)
2. 아니면: (a) `canSee(나)` 인 모든 플레이어(본인 포함)에게 `unregisterEntity` = `RemoveEntities` + `PlayerInfoRemove(**엔티티 UUID**)`, (b) `gameProfile` 교체, (c) 같은 플레이어 모두에게 `trackAndShowEntity(나, 새 프로필 id)`, (d) 본인에게 `refreshPlayer`.
3. (c) 에서 **새 프로필 id 가 실리는 곳은 재등록의 `PlayerInfoUpdate`(createPlayerInitializing) 한 패킷뿐**이다. 엔티티 UUID 를 새 id 로 바꾸는 것은 그 패킷을 만드는 동안만이고 곧바로 되돌린다. 이어지는 스폰(`AddEntity`)은 **엔티티 UUID** 로 나간다. 재등록 항목의 listed 는 목록 숨김(`unlistPlayer`) 상태를 그대로 따른다(Listing API 패치).
4. 그 밖의 모든 탭 패킷(접속 때 목록, 핑, 게임 모드, INITIALIZE_CHAT, 퇴장 Remove)은 항상 **엔티티 UUID + 현재 프로필의 이름·속성**이다. 서명 채팅의 디코더·INITIALIZE_CHAT·채팅 패킷 sender 도 엔티티 UUID 다. 프로필 id 를 쓰는 곳은 `handleChatSessionUpdate` 의 키 검증 `data.validate(player.getGameProfile(), …)` 하나뿐.
5. `refreshPlayer`(§5)와 `PlayerShowEntityEvent`(§1-R3)가 따라온다. `PlayerHideEntityEvent` 는 부르지 않는다(`unregisterEntity` 를 따로 떼어 냈기 때문).

**R1 — UUID 가 바뀔 때 Paper 의 관측(추론, 실서버 미확인):** 그 순간 접속해 있던 관찰자 클라이언트는 `AddEntity` 의 UUID(엔티티 UUID)로 탭 정보를 찾지 못해 플레이어를 만들지 않는다(바닐라 클라 "Server attempted to add player prior to sending player info"). 새 id 로 올라간 탭 항목은 퇴장 뒤에도 유령으로 남고, 채팅 sender 도 엔티티 UUID 라 관찰자가 보낸 사람을 못 찾는다. 나중에 접속한 사람에게는 정상이다. **Furnace 도 같은 패킷을 낸다**(원칙: 관측 동일; 실제 플러그인은 대개 UUID 를 그대로 둔다). **D1:** 이 경우(새 프로필 id ≠ 엔티티 UUID)에는 개발자가 원인을 알 수 있게 콘솔에 경고를 **한 번만** 낸다 — 서버 전체에서 한 번(`AtomicBoolean`, 플러그인 이름은 호출한 플러그인이 알려지면 함께). 문구는 `CompatLang` 키(`furnace-compat-lang/{en,ko}.properties`)로 두고 `CompatLangTest` 가 키를 본다. 경고는 패킷 내용을 바꾸지 않는다. **UUID 가 같은 경우(스킨·이름만 바꿈, 가장 흔한 쓰임)는 Paper 와 바이트 단위로 같게** 낸다. 두 경우 모두 구현 때 Paper 로 실측해 다르면 이 절을 고친다.

## 2. Furnace 의 지금 모양
- `Player.uuid`/`name` 은 `final` 이고 엔티티 UUID 와 프로필을 겸한다. `profileProperties` 만 가변.
- 접속자 맵은 entityId 키(`World.onlinePlayer(eid)`), 탭 기록 `listed` 는 eid→uuid, `EntityViewRules.TARGETS` 만 UUID 키. `ServerCore.playerByName/playerByUuid` 는 접속자를 훑는다.
- `ConnectionHandler`(1578~1589행)는 바닐라 순서대로 기존 접속자·본인 탭을 먼저 보내고 `world.playerOnline(p)` 을 부르며, **PlayerJoinEvent 는 1675행 `announceJoin` 에서 그 뒤**에 부른다(R7).
- `Region.java` 1693행 주석대로 "시야가 닿는 플레이어는 모두 같은 region" — 엔티티를 보는 관찰자는 대상과 같은 region 이다(권장 1).

## 3. 설계: "엔티티 정체성"과 "프로필"을 나눈다 (Paper 와 같은 모양)
- `Player.uuid()` 는 **엔티티 UUID**(파일·맵·숨김 규칙·탭의 UUID 칸·채팅 sender/디코더) — 바뀌지 않는다.
- 새 `GameProfile profile`(id·name·properties, 불변 값). **세대와 함께 불변 묶음 `{profile, gen}` 하나**를 volatile 로 두고 한 번의 쓰기로 바꾼다. 그 **뒤에** `ROSTER_EPOCH` 를 올린다(권장 2: 따로 두면 관찰자가 "옛 프로필 + 새 세대"를 읽어 영영 재등록을 못 한다). `listed` 값은 `(uuid, gen)`. 세대는 같은지만 비교하므로 오버플로는 무관. 짧은 사이에 여러 번 바꾸면 관찰자는 마지막 것만 본다(중간 패킷 생략) — 의도한 동작.
- 분류표(호출처를 표로 분류한 뒤 접근자 분리, 기계적 치환 금지):

| 곳 | 쓰는 값 | 근거 |
|---|---|---|
| playerdata·stats·advancements 파일 이름, 접속자 중복 검사, 엔티티 맵 | 엔티티 UUID | 정체성 |
| `EntityViewRules.TARGETS`, 탭·핑·게임모드·INITIALIZE_CHAT·퇴장 Remove 의 UUID 칸 | 엔티티 UUID | §1-4 |
| 탭 항목의 이름·속성, `getName`, 호환 `getPlayerProfile`, 호환 `getScoreboardName` | 프로필 | §1 |
| `getPlayerExact`, 바닐라 명령 선택자(`@p` 가 아닌 이름 선택자·`getPlayerByName`) | **접속 때의 이름**(`Player.loginName` 고정 필드) | D2, §8 |
| `Bukkit.getPlayer(이름)` 앞부분 일치 훑기 | 프로필(현재 이름) | D2, Paper 와 같음 |
| op·화이트리스트·밴(`ServerCore.profileOf`) | 프로필 | Paper `getGameProfile()` |
| 채팅 서명 디코더·sender·INITIALIZE_CHAT(`PlayerChatState`) | 엔티티 UUID | R6 |
| 채팅 세션 키 검증(`ConnectionHandler` 의 `data.validate(p.uuid(), …)` 한 곳) | **프로필 id** | R6 |
| 호환 `getUniqueId` | 엔티티 UUID | Paper |
| 호환 `getPlayerProfile().getId()` | 프로필 id(= `getUniqueId` 와 다를 수 있음) | Paper |
| 표시 이름 `getDisplayName`·`displayName()`·기본 채팅 렌더러 | **접속 때의 이름을 붙잡아 둔 것**(옛 이름) | R4 |
| `getPlayerListName`·탭 | listName 이 null 이면 새 이름 | R4 |

- R6: `PlayerChatState(profileId)` 는 엔티티 UUID 를 받으므로 헷갈리지 않게 이름을 `entityId`/`ownerUuid` 로 바꾼다. 참고: UUID 가 바뀐 뒤 키가 갱신되면 Paper(online-mode)는 검증 실패로 kick 한다 — Furnace 도 같다.
- R4: `adventure$displayName` 은 ServerPlayer 를 만들 때 접속 이름으로 정해지고 setPlayerProfile 이 바꾸지 않는다. Furnace 는 접속 때 이름을 따로 붙잡아 `customDisplayName` 이 없을 때 쓴다(지금은 `handle.name()` 이라 새 이름이 나옴). `setDisplayName(null)` 은 그 시점의 `getName()`(새 이름)으로 바뀐다.

## 4. 스레드 소유·재등록 방식 (권장 1·3 반영)
- 호출은 대상 플레이어의 region 스레드에서 적용한다. 소유 스레드이거나 RegionBorrow 로 빌릴 수 있는 옛 동기 문맥이면 **그 자리에서 적용**해 `setPlayerProfile(x); getName()` 이 바로 새 이름을 내게 한다(Paper 동기 호출과 같게). 진짜 비동기 스레드면 region 이 아니라 **엔티티를 따라가는 스케줄러**에 올려 region 이 넘어가도 작업을 잃지 않게 하고, 접속이 끊겼으면 버린다. "비동기 호출 직후의 `getName` 은 옛 값일 수 있음"을 API 문서에 적는다.
- **같은 region 관찰자(엔티티를 볼 수 있는 모두, 본인 포함)는 대상 region 작업 안에서 Paper 순서대로 같은 틱에 끝낸다**: 전원 해제(`RemoveEntities`+`PlayerInfoRemove`(엔티티 UUID)) → 프로필 교체 → 전원 재등록(새 프로필 id 로 `PlayerInfoUpdate`, 이어서 엔티티 UUID 로 스폰, 숨김 상태면 listed=false 유지) → 본인 `refreshPlayer`. 그래서 "다음 추적 틱에 스폰"은 없다.
- **다른 region·다른 월드 관찰자는 탭만** 본다: 관찰자 region 이 `syncTabList` 에서 `listed` 의 `(uuid, gen)` 과 현재 묶음의 세대가 어긋난 항목을 감지해 그 탭 항목을 다시 올린다(`rosterEpoch` 로 빠르게 건너뜀). 눈에 보이지 않는 탭에만 한 틱 지연이 남는다.
- 접속 직후(Paper 의 `sentListPacket` 이 false 인 구간)에는 프로필만 바꾼다. **R7:** Paper 는 조인 이벤트 **뒤에** 탭을 보내므로, Furnace 의 `ConnectionHandler` 접속 순서를 CraftBukkit 순서(PlayerJoinEvent 가 접속자 탭 전송보다 앞)로 옮긴 뒤 "탭 전송 완료" 시점을 Paper 와 같은 곳으로 정한다. 이 순서 변경은 **2b 앞의 별도 PR**(접속 패킷 순서 시험 포함)로 한다.

## 5. 본인 화면 갱신 `refreshPlayer` (R2)
본인도 `canSee(self)=true` 라 **리스폰보다 먼저** 본인 탭의 Remove·Update 를 받는다(같은 region 이므로 같은 작업 안에서). 그 뒤 순서(Paper, 일부는 CraftBukkit 1.21.1 본문 추론):
1. `Respawn`(현재 차원, `KEEP_ALL_DATA`) — 같은 차원 리스폰이라 클라이언트가 월드를 그대로 둔다.
2. 능력치(`onUpdateAbilities`).
3. `internalTeleport` — 이벤트 없이 승인 번호만 늘린다.
4. `sendPlayerPermissionLevel(handle, false)` — 엔티티 이벤트(24+레벨)만. **권한 재계산·명령 트리 재전송은 하지 않는다**(Add-sendOpLevel-API 패치): 새 프로필이 op 여도 명령 트리는 예전 그대로.
5. `sendLevelInfo`(보더·시간·날씨).
6. `sendAllPlayerInfo`(탭 전송이 아니다): 인벤토리 전체, 들고 있는 칸, scaled health(최대 체력 속성+체력), 엔티티 데이터 refresh, reducedDebugInfo 엔티티 이벤트(22/23), 즉시 리스폰 게임 이벤트.
7. 경험치, 활성 효과(`blend=false`).
`finishRespawn` 에서 뽑아 쓸 때 **청크 재전송, 추적 초기화, Respawn·ChangedWorld·Teleport 이벤트, 체력·무적 초기화가 섞이지 않는다**는 것을 시험으로 고정한다.

## 6. R3 — `PlayerShowEntityEvent`
Paper 는 재등록의 `trackAndShowEntity` 를 거치므로 보이는 관찰자마다(본인 포함) `PlayerShowEntityEvent` 를 부른다(`PlayerHideEntityEvent` 는 안 부름). Furnace 서버·호환 계층에 이 이벤트가 아직 없다 → **2b 범위에 넣는다**(furnace-api 취소 불가 이벤트 + 호환 다리 + `EventBridgeTest` fire 목록. 취소 가능하지 않으므로 CancelContractTest 표는 불필요).

## 7. 따라가야 하는 곳 — Paper 원문으로 이미 정해진 답
- 스코어보드: `getScoreboardName()` 은 gameProfile 이름이고 옮겨 주는 처리가 없다 → 팀 소속·점수는 **새 이름 기준**(옛 이름 팀에서 빠짐). usercache 도 바꾸지 않는다.
- 채팅: §3 표(R6). 서명 채팅 INITIALIZE_CHAT 은 엔티티 UUID.
- 표시 이름: §3(R4).
- 스폰 UUID: §1-3.

## 8. 이름 조회 (R5, D2 확정)
- Paper(Spigot `PlayerList.playersByName`)는 접속 때 소문자 이름으로 넣고 퇴장 때 그 시점의 이름으로 뺀다. 그래서 Paper 에서는 `getPlayerExact(새 이름)`=null, `getPlayerExact(옛 이름)`=그 플레이어, 바닐라 선택자 `getPlayerByName` 도 옛 이름, `Bukkit.getPlayer(새 이름)` 은 접두어 훑기(현재 `getName`)로 찾는다. 닉네임 플러그인 서버에서 관리자가 `/ban 진짜이름` 으로 명령하는 흐름이 그대로 유지된다.
- **Furnace 는 Paper 와 같게 한다(D2).** `Player` 에 **접속 때의 이름을 고정 필드**(`loginName`, 로그인 결과로 한 번 채우고 안 바뀐다)로 두고, `ServerCore.playerByName` 계열(`getPlayerExact`·이름 선택자)은 이 필드로 접속자를 훑는다. `Bukkit.getPlayer(이름)` 의 앞부분 일치는 현재 이름(`getName` = 프로필 이름)으로 훑는다. 앞서 문서가 추천했던 "새 이름으로 찾기"는 Paper 와 달라 **철회**했다. R4 에서 접속 때 이름을 붙잡아 두는 것과 같은 값이라 필드를 하나만 둔다(표시 이름 용도와 겸용).
- **의도한 차이는 하나**: Paper 는 퇴장 뒤에도 옛 이름 항목이 맵에 남는 누수가 있는데 따라 하지 않는다. 우리는 접속자를 훑는 방식이라 별도 맵이 없고, 퇴장하면 항목이 자연히 사라진다. 그래서 관측 차이는 "퇴장한 플레이어의 옛 이름으로 `getPlayerExact` 를 하면 Paper 는 퇴장한 플레이어를 돌려줄 수 있는 누수"뿐이다. API 문서·`context-notes` 에 이 한 줄만 적는다.
- 같은 접속 이름 둘은 접속 시 중복 검사로 막히므로 모호성은 없다. 프로필 이름이 다른 플레이어의 접속 이름과 겹쳐도(Paper 와 같게) 먼저 훑은 쪽을 돌려준다 — 시험으로 고정한다.

## 9. 단계 나누기 (권장 4·6 반영)
0. **접속 순서 PR(R7)**: PlayerJoinEvent 를 접속자 탭 전송 앞으로 옮김 + 접속 패킷 순서 시험. 2b 앞.
1. **E11c-2a**(동작 불변 리팩터링): `Player.profile` 묶음 도입 + §3 표대로 접근자 분리. 2a 에서는 `uuid==profileId`·`name==profileName` 이 늘 참이라 분류 실수가 기존 시험으로는 드러나지 않으므로 **시험 전용 훅**(로그인 때 다른 UUID·이름의 프로필을 주입)을 두고 **표의 한 줄마다 시험 하나**(파일 이름, op·화이트리스트·밴, 접속 이름 선택자·`getPlayerExact`(D2)와 현재 이름 앞부분 일치, 탭 UUID·이름, 채팅 디코더·키 검증, 스코어보드, 호환 `getName`·`getDisplayName`·`getUniqueId`·`getPlayerProfile().getId()`)를 붙인다. furnace-api·호환 공개 시그니처 `javap` 비교로 공개 API 변화 없음을 확인.
2. **E11c-2b**: 같은 region 즉시 재등록·본인 갱신·세대 감지·`PlayerShowEntityEvent`·호환 `setPlayerProfile`. 시험: 조인 이벤트 안에서 호출(리스폰 없음), UUID 같음(바이트 단위 Paper 와 같음)·UUID 바뀜(R1; D1 경고가 한 번만 나오고 두 번째 호출에는 안 나옴, 패킷은 경고와 무관하게 같음), 다른 월드 관찰자(탭만), 목록 숨김·숨김 관찰자, 연속 두 번 호출, 관찰자가 감지하기 전에 대상이 퇴장, region 이동 중 호출, 본인 패킷 전체 순서(§5), 갱신 뒤 플레이어별 시간·날씨 유지.
3. **E11c-2c**: 실서버 Paper 와 대조해 추론으로 표시한 항목(§1-R1, §5)을 확인·정정.

성능: 바꿀 때마다 관찰자가 한 번씩 다시 훑는 O(P)뿐이고 평소에는 volatile 읽기 하나가 늘 뿐이다.

## 10. 위험
- 호출처 분류 실수 → 파일 이름이 바뀌거나 명령이 어긋남. 대응: 2a 의 표별 시험·독립 검토.
- UUID 가 바뀌면 Paper 와 같게 관찰자 화면에 플레이어가 안 보일 수 있다(R1, 의도, D1 로 콘솔 경고 한 번). 플러그인이 의존하는 쪽이 있으면 2c 실측 뒤 재논의.
- 이름 조회를 접속 이름으로 하면 `playerByName` 호출처 중 "현재 이름" 의미가 필요한 곳(앞부분 일치)을 빼먹을 수 있다 → 2a 표별 시험에 두 경우를 모두 넣는다.
