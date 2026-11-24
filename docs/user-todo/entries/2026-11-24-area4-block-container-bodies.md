# 컨테이너 블록 엔티티 칸 메서드를 채웠다 — 남은 차이 (영역 4, 2026-11-24)
쉬운 말: 상자·통·셜커·깔때기·발사기·투척기·화로 3종·양조대·제작기(12종)의 `getItem`·`setItem`·`removeItem`·`removeItemNoUpdate`·`getContents`·`isEmpty`·`getContainerSize` 와 Paper 가 더한 `getViewers`·`onOpen`·`onClose`·`getOwner`·`getLocation`·`get/setMaxStackSize` 가 이제 던지지 않고 바닐라처럼 동작한다(돌려준 스택을 고치면 칸이 바뀐다).
남은 차이(아직 던지거나 조금 다름):
1. **아직 안 푼 전리품 표**가 있는 상자·통·셜커·깔때기·발사기·제작기는 칸을 읽거나 쓰면 `UnsupportedNmsCall`. 바닐라는 첫 접근 때 표를 풀어 채운다(서버의 푸는 길이 호환 다리에 없다). 안 하면? 열지 않은 던전 상자를 플러그인이 `getItem` 으로 먼저 읽는 경우만 오류. 추천: 서버에 "전리품 풀기" 다리를 하나 더 낸다.
2. **`stillValid`**(플레이어 거리 검사)는 여전히 던진다 — 플레이어 shim 에 눈 위치·`canInteractWithBlock` 이 없어서(엔티티 영역 몫).
3. **주크박스·장식 단지·장식 책장**(`ContainerSingleItem` 계열)은 이번에 안 채웠다(바닐라가 칸마다 규칙이 달라 — 책장은 책만 받음, 주크박스는 재생 시작).
4. 칸 쓰기는 서버의 칸 쓰기로 이어져 **저장 표시·비교기 알림이 항상 따른다**(바닐라 깔때기 `setItem`·`removeItemNoUpdate` 는 안 알림). 화로 0번 칸의 조리 시간 되돌림 등 부수 효과는 서버 쪽 규칙이 한다.
5. `transaction`·`maxStack` 필드는 Paper 에서는 구체 클래스마다 있는데 이 shim 은 공통 상위에 한 번 둔다.
