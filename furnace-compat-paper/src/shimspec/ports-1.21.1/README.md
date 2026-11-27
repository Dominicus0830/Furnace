# 이식 줄(`.ports`) — 형식 요약

설계 전문: `docs/roadmap/port-bridge-2026-11-27.md`(5·6장). 생성기: `furnace-api/src/portgen`(`PortSpec` 검사, `PortGen` 생성).

- 파일: `<영역 번호>-<영역 이름>.ports`(번호·이름은 `../fill-areas.txt`). 첫 줄 `#furnace-ports 1 <판> area=<번호>`.
- 머리줄: `class <NMS 클래스> = <서버 클래스(org.domi.furnace.server. 뒤)> kind=<kind>`.
- 멤버줄: `[static ]<NMS 이름><서술자> -> <서버 메서드> check=<검사> [live|copy]`.
  - 평문 이름은 Paper 서명표의 공개·보호 이름뿐, private·패키지 멤버는 `#지문`(연결 줄과 같다).
  - NMS 정적 멤버는 앞에 `static`. 서버 대상 메서드는 `public` 이어야 한다(생성 구현은 다른 패키지).
  - `ItemStack` 을 돌려주는 줄은 `live`(바닐라가 든 객체) 또는 `copy`(새 객체)를 꼭 적는다.
- 한 줄이 만드는 것: `api.internal.port.<Area>Port` 메서드, `server.port.gen.<Area>PortImpl` 구현(첫 호출 `PortGuards`),
  `compat.nms.port.gen.<Area>Ports` 스텁 + 연결 줄 `<영역>.ports.links`(`FullShells` 가 껍데기 멤버를 스텁으로 잇는다).

## 지금(P1) 되는 것

| 무엇 | 값 |
|---|---|
| 검사 | `pure`(검사 없음, 레벨 인자 금지), `pos`(레벨 하나 + `BlockPos` — 첫 자리 칸의 주인 region 에서) |
| 인자 kind | 원시형, `String`, `BlockState`(상태 번호), `BlockPos`(x·y·z), 레벨(`Level`·`ServerLevel`·`LevelReader` … — 월드의 `ServerLevel` 만, 플러그인 레벨은 P3), `RandomSource`(플러그인 난수를 콜백으로) |
| 반환 kind | `void`, 원시형, `String` |
| 받는 쪽(인스턴스 줄) | `kind=block` |

그 밖(`any`·`menu`·`detached` 검사, 메뉴·아이템·블록 엔티티 kind, `hook`, `new(…)`, `inout`·`same`)은 생성기가 "아직(P2~P4)"으로 거절한다.
