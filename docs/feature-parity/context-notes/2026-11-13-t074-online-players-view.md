# T-074 `getOnlinePlayers()` 보기 — 접속자 목록 캐시 (2026-11-13)

## 한 것
- `World.onlineArray()` — 서버 전체 접속자 배열(T-016 의 `PlayerRoster.array`, 접속 순·리스폰 때 끝으로)을 복사 없이 노출. 접속·퇴장·리스폰마다 **다른 배열**이라 `==` 로 "집합이 그대로인가"를 안다.
- `PluginHost.onlinePlayers()` — (배열 동일성, `Player.rosterEpoch()`) 이 같으면 지난번에 만든 **고칠 수 없는 `List<PlayerHandle>` 를 그대로** 돌려준다. 달라졌을 때만 끊기지 않은 플레이어로 새로 만든다. 번호를 먼저 읽고 훑으므로 그 사이 바뀐 것은 다음 호출이 반영한다.
- `CraftServer` 의 보기(`OnlineView`) — 서버가 같은 목록 객체를 주는 동안 감싼 `Player[]` 를 다시 만들지 않는다. `size()`·`get(i)` 는 배열 읽기, 순회·`spliterator`·`toArray` 는 **시작 때 배열 하나**를 끝까지 본다(도중 접속·퇴장에도 건너뛰거나 겹치지 않음).

## 결과 동일성
- 순서: Paper `getOnlinePlayers` = `PlayerList.players` 접속 순(리스폰하면 끝). T-016 이 이미 서버 목록을 그 순서로 바꿨고 이 PR 은 그 순서를 그대로 읽는다(옛 `core.players()` 와 같은 원소·순서).
- 살아 있는 보기: 앞서 받아 둔 컬렉션도 새 접속자를 보인다(캐시는 보기 안에 있고 호출마다 목록 객체를 확인).
- 시험: `OnlinePlayersViewTest`(compat-paper) — 모델(접속·퇴장·리스폰 순수 목록 조작) 대조, 옛 구현(매번 새로 만들기) 대조 무작위 각본 60단계, 같은 `CraftPlayer` 객체 재사용, 순회 스냅샷, 퇴장 직후 비워짐.

## 함정·한계
- `Player.markDisconnected` 는 **`rosterEpoch` 를 올린다**(TASK 문서는 "끊김은 epoch 를 안 올림"이라 적었으나 코드는 올림). 이 캐시의 "끊긴 플레이어 거르기"가 그 상승에 기댄다 — `markDisconnected` 에서 `bumpRoster()` 를 빼면 `OnlinePlayersDisconnectTest`(끊김 표시만 하고 바로 보기·읽기 스레드 경합)가 실패한다. (`TestPlayer.close()` 로 퇴장시키는 시험은 roster 제거의 bump 가 가려 못 잡는다.)
- 끊김 표시(`disconnected=true`)와 번호 상승 사이 한 걸음에는 방금 끊긴 플레이어가 한 번 더 보일 수 있다(Paper 도 퇴장 처리가 틱 단위로 늦다).
- `EntityViewsImpl` 의 숨기기·보이기도 `bumpRoster()` 라 그때마다 목록을 한 번 다시 만든다(40명 약 1µs 규모, 호출 빈도에 비해 드묾).
- 남는 한계(TASK 문서에 적은 대로): region 스레드에서 `for (i < size()) get(i)` 를 도는 동안 다른 스레드가 접속·퇴장하면 `size()` 와 `get(i)` 가 서로 다른 배열을 볼 수 있다(Paper 의 `ArrayList` 도 같은 경합). `get` 은 범위를 벗어나면 `IndexOutOfBoundsException`.

## 측정 (이 컨테이너, 40명 접속, 3회 중 마지막)
| | 옛(매번 새로 만들기) | 새 |
|---|---|---|
| `size()` | 484ns | 4.6ns |
| `for (i<size()) get(i)` 40회 | 21.4µs | 0.30µs |
| `for (p : view)` 40명 | 0.78µs | 0.06µs |
(옛 = 이 PR 전 `PluginHost.onlinePlayers()` 본문을 시험에서 그대로 따라 한 것. 임시 측정이라 저장소에는 넣지 않았다.)

- 방금 나간 플레이어의 핸들·`CraftPlayer` 는 다음 호출·접속·퇴장 전까지 캐시(`onlineSnapshot`·`OnlineView.wrapped`)가 한 접속자 목록 분량만큼 붙잡는다. `listIterator()`·`subList` 는 여전히 `size()`/`get(i)` 기반이라 스냅샷이 아니다(옛 구현과 같음).
