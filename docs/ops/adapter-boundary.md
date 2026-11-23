# 어댑터 경계 검사 (T-249, 2026-11-23)

호환 계층(`furnace-compat-paper`, Bukkit·Paper·NMS 어댑터)과 핵심(`furnace-server`, `furnace-api`)의 의존 방향을 CI 시험이 지킨다.
방향은 **어댑터 → 공개 API(`furnace-api`) → (서버 구현은 `api.internal` 다리로만)** 이다.

| 규칙 | 내용 | 시험 |
|---|---|---|
| ① | `furnace-compat-paper` 주 코드는 `org.domi.furnace.server..` 를 쓰지 않는다 | `furnace-compat-paper` `AdapterDirectionStaticTest` |
| ② | `furnace-api` 는 호환·어댑터 이름(`org.bukkit`, `io.papermc`, `net.minecraft`, `org.spigotmc`, `net.md_5`, `org.domi.furnace.compat`, `org.domi.furnace.server`)을 쓰지 않는다 | `furnace-server` `AdapterBoundaryStaticTest` (서버→호환 방향은 기존 `CompatIsolationStaticTest`) |
| ③ | `api.internal.*` 는 허용 목록의 서버 클래스만 쓴다 | `AdapterBoundaryStaticTest` |

구현은 새 의존성 없이 클래스 파일 상수 풀의 이름을 읽는 바이트코드 검사다(ArchUnit 은 오프라인 CI 캐시에 없어서 쓰지 않았다).

## 허용 목록은 줄어들기만 한다
- ③ 의 허용 목록(`AdapterBoundaryStaticTest.INTERNAL_ALLOWED`)은 현재 위치를 이유와 함께 적은 것이다. **새 클래스를 더하려면 이유를 적고 독립 검토를 받는다.**
- 목록의 클래스가 더는 `api.internal` 을 쓰지 않으면 시험이 "목록에서 지우라"며 실패한다 — 줄인 만큼 목록도 줄어야 통과한다.
- 줄이는 순서(어댑터 분리 점검 권장): 호환 항목 내부 이동·훅 통합 → `ChunkColumn`·`ChunkSection`·`ChunkReadView`·`PaletteStorage` 거울 동기화를 훅 뒤로 → 손 shim 이전.

## 글자 이어 붙이기 구멍
상수 풀 검사는 이름을 이어 붙여 피한 곳을 못 본다. 그런 곳은 `knownStringSplitHolesStillExistWithTheirReason` 에 파일과 이유를 적는다(지금은 `ConsolePrefixConverter` — 로거 이름 접두 판단일 뿐 클래스를 부르지 않는다). 없어지면 시험이 줄을 지우라고 한다.

## 현재 기록된 예외 (2026-11-23 기준)
- ② `PacketSchema`: 중립 패킷 스키마가 바닐라 패킷 패키지 이름을 자료 글자로 가진다(클래스 참조 아님). 스키마 이름 표를 호환 쪽으로 옮기면 지운다.
- ③ 서버 클래스 10개: `*InternalsImpl`·`ScratchWorldsImpl`·`PlacementBridge`(다리 자체), `PluginHost`(진입점 보유), `Region`·`ChunkColumn`·`ChunkSection`·`ChunkReadView`·`PaletteStorage`(어댑터 읽기 보기 접근 — 줄이는 대상).
