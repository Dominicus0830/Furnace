# Bukkit Tag 와 Paper 탭 완성 이벤트 호환 (2026-11-14)

시작 로그(FAWE)에서 찾은 두 결함. 근거는 Paper 1.21.1 패치(`33ff3dff`) 직접 대조.

## Tag
- WorldEdit/FAWE `setupTags` 는 `Bukkit.getTags(Tag.REGISTRY_BLOCKS|ITEMS, Material.class)` 로 카테고리를 만들고 `getKey()`·`getValues()` 를 쓴다. 없으면 "doesn't support Tags".
- 상수 이름 규칙(블록 무접두·`ITEMS_`·`FLUIDS_`·`ENTITY_TYPES_`·`GAME_EVENT_` 단수)은 Paper 최신 `Tag.java` 상수 356개와 겹치고 어긋남이 없음을 확인한 뒤 생성기로 만들었다(코드는 옮기지 않고 이름만). 바닐라 1.21.1 태그에는 있는데 Paper 최신에 없는 16개(`concrete_powder`·`tall_flowers` 등 — 이후 버전에서 이름이 바뀐 것)도 같은 규칙으로 상수를 둔다(더 많아서 해가 없다).
- `Tag` 인터페이스는 default 메서드가 없어 `CompatTags.Impl` 을 만들어도 `Tag` 정적 초기화(상수 372개 계산)가 돌지 않는다 — 순환 없음. 상수 접근 시 한 번만 돈다.
- 데이터 팩: `gameRegistries().tag()` 가 이미 데이터 팩 태그를 반영하므로(`ItemContext.tagSet()`), "B.isTag 를 데이터 팩에 연결할지"(small-vanilla-mismatches 결정 1)와 무관하게 API 쪽 태그는 데이터 팩을 본다. 서버 게임 동작이 따로 데이터 팩 태그를 무시하는 문제는 그대로다.

## AsyncTabCompleteEvent (Paper `AsyncTabCompleteEvent.patch`)
- 서버: Paper 는 항상 "Async Tab Complete Thread - #n"(4개)로 넘겨 이벤트를 부른 뒤 handled 가 아니면 메인에서 Brigadier. Furnace 는 구독자가 있을 때만 그렇게 하고, 없으면 기존 길 그대로.
- handled && 후보 있음 → `SuggestionsBuilder(buffer, buffer.length())` 의 `createOffset(lastIndexOf(' ')+1)` 로 만들어 보낸다. 후보가 Guava `Ints.tryParse` 로 읽히면(ASCII 10진·앞 `-` 만, `+` 는 아님) 정수 후보. 취소면 후보를 빈 목록으로 보되 handled 판정(`isHandled()` = 후보 비어 있지 않음 || 표시)은 원래 후보 기준이라, 후보를 채우고 취소하면 응답이 없다.
- 콘솔: Paper 는 `buffer = "/" + 입력줄 전체`. 취소거나 handled 면 이벤트 후보만 쓴다. 동기 `TabCompleteEvent` 재발화는 아직 없다.
- Adventure: 호환 계층은 Adventure 를 싣지 않는다(서버 제공 라이브러리 공유 로더에서만 올라옴). 그래서 Bukkit 이벤트는 `Completion`·`completions()` 없이 문자열 판. FAWE 는 `isCommand`·`getBuffer`·`getSender`·`setCompletions`·`setHandled` 만 쓴다.
- 다리: Furnace `AsyncTabCompleteEvent` 와 Bukkit 이벤트가 후보·handled 를 pull/push 로 맞춘다. Furnace 쪽 설명 글(Text)은 같은 문자열이면 유지.

## PlayerCommandSendEvent (Paper `Async-command-map-building.patch` + CraftBukkit Commands.sendCommands)
- 이벤트에 가는 이름 = 쓸 수 있는 최상위 노드 이름(send-namespaced 거른 뒤) 사본. 처리기가 뺀 이름은 `rootcommandnode.removeCommand` 처럼 루트 자식에서만 지운다 — 뺀 노드가 다른 노드의 리다이렉트 대상이면 트리에는 남는다(번호 매기기는 지운 뒤).
- 모든 트리 전송이 `Commands.treeFor` 를 지난다(접속·`sendPlayerPermissionLevel`·플러그인 명령 등록/해제·`updateCommands`).
