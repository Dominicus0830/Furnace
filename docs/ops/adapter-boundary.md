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
- ③ 서버 클래스 10개(3단계 T-251 뒤, 처음 12개): `*InternalsImpl`·`ScratchWorldsImpl`·`PlacementBridge`(다리 자체), `PluginHost`(진입점 보유), `ChunkSection`·`ChunkReadView`·`PaletteStorage`(읽기 보기 자료형을 복사 없이 내주려고 API 인터페이스를 직접 구현 — 3b 에서 측정해 정한다). `Region`·`ChunkColumn` 은 3단계에서 거울 훅(`world.ChunkMirrorOps`) 뒤로 옮겨 빠졌다 — `docs/ops/adapter-boundary-step3-plan.md`.

## 훅 한 곳으로 모으기 (T-250, 2026-11-23, 권장 2단계)
핵심이 어댑터를 "호출만" 하게, 어댑터가 꽂는 지점과 그 지점을 부르는 곳을 한 곳씩으로 줄였다. 동작은 그대로다(기존 시험·금 파일 무변경).

| 지점 | 어댑터가 꽂는 것 | 핵심에서 부르는 곳 | 지키는 시험 |
|---|---|---|---|
| 형식 어댑터 | `PluginFormat`(SPI, `CompatGap` 은 그 자료형) | `PluginFormats`·`PluginBootstrapper` | — |
| 어댑터 설정 `plugins.compat.*` | (읽기) `Internals#compatOption` — 공개 `Furnace` 에서 뺐다. 어댑터는 `compat.CompatOptions.get` 하나로 읽는다 | `InternalsImpl` → `PluginHost` | `publicApiHasNoAdapterOnlyMember`(공개 API 에 "호환 어댑터 전용" 표시 항목 금지 — 문구 검사라 다른 말로 적으면 못 잡는다. `ConfigSection`·`PersistentDataView`·`GlobalWait`·`EventSlot` 의 "호환 계층이 쓰는" 항목은 일반 API 로 보고 남겼다) |
| 플러그인 레벨을 통한 배치(T-211 통로 모드) | `FeatureWorldView.WriteSink`·`EdgeUpdater`(`LiveFeatures`·`LiveStructures` 가 `useSink` 로 꽂는다) | 월드젠 조각·feature 는 뜻 이름 메서드(`pluginLevelOwnsBlockEntity`·`pluginLevelWriteFlags`·`requireDirectSectionWrites`)만 부른다. `sinkMode()` 를 직접 묻는 곳은 템플릿 배치 엔진(`TemplatePlacer`·`ShapeUpdater`)뿐 | `sinkModeStaysInPlacementEngine` |
| 패킷 가로채기(플러그인 패킷 API·ProtocolLib 다리) | `net.PacketTap`(연결 속성) | 꽂기 `PacketServiceImpl`, 보내기 `net.PacketTaps.outbound`(리스너 안에서 보낸 패킷의 Paper 순서 포함 — 예전 `SharedChunkFrames` 안), 받기 `ConnectionHandler` | `packetTapIsCalledFromOnePlacePerDirection` |
| 섹션 바닐라 개수 대입(FAWE 등이 필드에 대입) | `ChunkInternals#setSectionVanillaCounts`(다리) | `ChunkColumn` → `ChunkSection#setVanillaCounts`(이제 패키지 전용) | 컴파일러 |

- 남은 줄: `World#entityUuidById` 는 호환 전용이 아니라 일반 API(`entityIdByUuid` 의 반대 방향)라 그대로 두고 설명만 고쳤다. `api.internal` 허용 목록(③)은 이 단계로 줄지 않는다(12줄 그대로) — 남은 줄은 다리 구현과 3단계(거울 동기화·읽기 보기) 대상이다.
- `TemplatePlacer`·`ShapeUpdater` 의 통로 분기는 Paper 가 감싸개를 벗긴 레벨로 쓰는 차례(방벽 flag 20·블록 엔티티·모양 갱신·`blockUpdated`)를 바닐라 코드 모양대로 따른 것이라 뜻 이름으로 감싸지 않았다 — 감싸면 바닐라와 줄 단위 대조가 어려워진다.
- `sinkModeStaysInPlacementEngine` 은 파일 단위로 `.sinkMode()` 글자만 본다. 허용된 두 파일 안에서 늘어나는 것, 월드젠 코드가 `setLiveWriteFlags`·`setSinkDepth`·`edgeUpdater()` 같은 통로 훅을 직접 만지는 것은 못 막는다(다음 단계 후보).
- **어댑터와 서버는 늘 같은 빌드로 나간다.** 호환 모듈은 서버 `fatJar`·배포판 안(`META-INF/furnace/compat`, `furnace-server/build.gradle.kts` 의 `compatBundle`)에 실려 서버와 한 묶음이다 — 그래서 다리(`api.internal`)·SPI 를 서버와 어댑터가 함께 바꿔도 다른 빌드끼리 짝지어 `NoSuchMethodError` 가 날 일이 없다. 호환 모듈을 따로 배포하게 되면 이 전제를 다시 본다.
