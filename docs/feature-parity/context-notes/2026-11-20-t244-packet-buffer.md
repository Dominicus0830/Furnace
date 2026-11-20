## 2026-11-20 NMS shim 1번 묶음 — 패킷 버퍼·플러그인 메시지 함정 (T-244)

- **플러그인 메시지 본문은 한 번만 바꾼다.** 바닐라·Paper 인코더가 `writeBytes(payload.data())` 로 본문 버퍼를 소비하므로 Furnace 도 바꿀 때 소비한다. 같은 shim 패킷을 두 번 `PacketDelivery.convert` 하면 두 번째는 빈 본문이다 — ProtocolLib 다리는 이 종류를 `losslessRoundTrip` 으로 두어 비교 기준을 다시 만들지 않는다. 새 경로에서 shim 을 미리 바꿔 보는 코드를 더할 때 조심.
- **JOML 서명과 반사.** `FriendlyByteBuf` 에 `org.joml` 형식 서명이 생겨, 버퍼를 `getDeclaredMethods` 로 훑는 플러그인은 JOML 이 없으면 `NoClassDefFoundError` 가 날 수 있다. 따로 할 일은 없다 — 서버가 호환 jar 도 라이브러리 표지로 훑어(`PluginBootstrapper.compatCore`) 호환 계층 서명에 쓰인 묶음을 늘 준비한다. 표지는 패키지 접두(끝이 `/`)만 허용된다(`ServerLibrariesBootstrapTest`) — 클래스 이름 표지를 더하려다 막혔고, 필요도 없었다.
- **`writeNullable(null, …)` 은 모호하다**(인스턴스 `writeNullable(T, enc)` 와 정적 `writeNullable(B, T, enc)`). Paper 도 같은 겹침이라 플러그인은 형 변환을 붙여 컴파일한다 — 시험도 `(String) null`.
- **생성기 공변 규칙**: Paper `FriendlyByteBuf` 는 `ByteBuf` 를 돌려주는 위임 중 새 버퍼를 주는 것(copy·slice·duplicate·retained*·`readBytes(int)`·readSlice·readRetainedSlice)과 order·unwrap·asReadOnly 만 `ByteBuf`, 나머지는 `FriendlyByteBuf`(`return this`). 옛 shim 은 `readBytes(int)` 가 안쪽 버퍼를 돌려주는 버그가 있었다.
