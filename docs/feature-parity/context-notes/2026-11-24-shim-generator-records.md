# shim 생성기 레코드 지원 (T-230, 2026-11-24)

- 잠금 파일에 `R` 줄(성분 이름·서술자·제네릭 서명). `ShimLock.resolve` 가 서명표의 `recordComponents` 와 같은 이름의 private 필드 서명에서 만든다. shim 에 없는 형식을 가리키는 서명은 지운다(다른 서명과 같은 원칙).
- `ShimGenerator.header` 가 레코드 머리를 쓰고, 컴파일러가 만드는 멤버(표준 생성자·접근자·equals/hashCode/toString·성분 필드)는 명세에 이으면 오류. 레코드엔 `C(ShimCtor)` 를 만들지 않는다.
- 패킷 생성기가 손 조각 폴더(`src/shims/java`)도 "손으로 쓴 소스"로 본다 — 조각 안의 안쪽 클래스(LastSeenMessages$Update 등)를 또 만들지 않게.
- 함정: 손 shim 이 바닐라와 성분이 다르면(ItemLore: Paper 는 `styledLines` 도 있음) 옮길 수 없다 — 호출부를 먼저 고쳐야 한다. 이번엔 제외.
- 증명: `tools/ci/javap-same.sh <전> <후>` 2851 클래스 중 다름 1(Unbreakable — Paper 의 TooltipProvider 구현이 새로 붙음, 새 클래스 TooltipProvider).
