# 컨테이너 블록 엔티티 칸 메서드를 채웠다 — 남은 차이 (영역 4, 2026-11-24)
쉬운 말: 상자·통·셜커·깔때기·발사기·투척기·화로 3종·양조대·제작기(12종)의 `getItem`·`setItem`·`removeItem`·`removeItemNoUpdate`·`getContents`·`isEmpty`·`getContainerSize` 와 Paper 가 더한 `getViewers`·`onOpen`·`onClose`·`getOwner`·`getLocation`·`get/setMaxStackSize` 가 이제 던지지 않고 바닐라처럼 동작한다(돌려준 스택을 고치면 칸이 바뀐다).
남은 차이(아직 던지거나 조금 다름):
1. (해결) 열지 않은 전리품 표는 서버 칸 접근이 바닐라처럼 먼저 푼다(`WorldHandle` 의 칸 보기 실행 앞).
2. **`stillValid`**(플레이어 거리 검사)는 여전히 던진다 — 플레이어 shim 에 눈 위치·`canInteractWithBlock` 이 없어서(엔티티 영역 몫).
3. 장식 단지는 채웠다. **주크박스**(서버가 용기로 안 다룸 — 재생 상태가 따로)와 **장식 책장**(책만 받고 블록 상태 `updateState` 를 해야 함)은 아직 던진다 — 다음 묶음.
4. 칸 쓰기는 서버의 칸 쓰기로 이어져 **저장 표시·비교기 알림이 항상 따른다**(바닐라 깔때기 `setItem`·`removeItemNoUpdate` 는 안 알림). 화로 0번 칸의 조리 시간 되돌림 등 부수 효과는 서버 쪽 규칙이 한다.
5. `transaction`·`maxStack` 필드는 Paper 에서는 구체 클래스마다 있는데 이 shim 은 공통 상위에 한 번 둔다.
6. `setMaxStackSize` 는 이 shim 의 `setItem` 한도에만 닿는다 — 서버 안 깔때기 이동 등은 서버 규칙(64)대로 넣는다(서버에 칸 한도 설정이 없음).
7. (해결) `getContents()` 는 칸 목록의 보기라 `set` 이 칸에 쓰인다(크기는 고정 — 바닐라 NonNullList 와 같다).
