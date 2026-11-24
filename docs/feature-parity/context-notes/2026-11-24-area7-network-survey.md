# 영역 7(network) 껍데기 채우기 — 첫 조사 (2026-11-24)

## 환경 함정
- 이 클라우드에서 서명표 셋 만들기: `tools/compat/sig-tables.sh` 는 Paper 서버 칸을 건너뛴다(오프라인 판정). `./gradlew --offline :worldgen-oracle:printPaperServerJar` 로 jar 경로를 얻은 뒤
  `vanilla-extract:run -Pmode=sig-table -PsigJar=… -PsigName=paper-server -PsigPrefix=…` 와 `-Pmode=fill-table -PsigName=paper` 를 손으로 돌리면 된다(두 표 모두 만들어짐).
- `--offline` 로는 compat 의존성(slf4j·netty 등)이 안 풀린다. 온라인으로 돌리되 Maven 429 가 나면 60초 뒤 재시도.

## 현황 (실측)
- 기준선 영역 7: 클래스 195, 던지는 멤버 906, 채운 377(T-262). 남은 던지는 멤버가 큰 클래스는 `ClientGamePacketListener`(인터페이스), `Connection`, `ServerGamePacketListener`, `ByteBufCodecs`, `FriendlyByteBuf`, 채팅 내용(`TranslatableContents`·`CommonComponents`·`PlayerChatMessage`) 순.
- 손 shim 62개(패킷 생성물 227개는 별도 스키마 경로). Paper 와 멤버가 정확히 같은 것은 7개뿐(`ConnectionProtocol`·`PacketFlow`·`ProtocolInfo`·함수형 인터페이스 3개·`SyncedDataHolder`).
- 남은 던지는 멤버는 값이 아니라 **논리**(코덱 람다·채팅 합성·연결 상태)가 대부분이라 T-262 모양으로 더 채울 것이 거의 없다.
- 플러그인 틈 표(compat-gaps-t264)의 `net.minecraft.network.*` 줄은 대부분 Spigot 난독 이름(`a(Vec3D)`)이라 Spigot 대응표 재계측 전에는 근거로 못 쓴다. 표에 나온 Mojang 클래스(`VecDeltaCodec` 등)는 손 구현이 이미 있다.

## 명세 이전 시험 — 지금은 이득보다 손실이 큼 (되돌림)
함수형 인터페이스 3개(`StreamDecoder/Encoder/MemberEncoder`)를 명세로 옮겨 봤다. 컴파일·`shimLock` 통과, javap 차이는 그 3개뿐이었지만:
1. 생성기가 애노테이션(`@FunctionalInterface`, 런타임 보임)을 내지 않아 바닐라와 반사 결과가 달라진다(손 shim 은 갖고 있었음).
2. `SyncedDataHolder` 를 옮기면 생성 엔티티 구체 클래스 132개가 던지는 `onSyncedDataUpdated` 덮어쓰기를 받아 상위 `Entity` 조각의 구현을 가린다(생성기가 조각의 구현을 못 봄).
3. 상수 인자가 있는 열거형(`ConnectionProtocol`·`PacketFlow`)과 레코드(`PacketType`)는 생성기가 못 만든다(상수는 이름만, 레코드 미지원).
→ 이전은 생성기에 (a) 애노테이션 복사, (b) 조각이 구현한 상위 추상 메서드 인식, (c) 인자 있는 열거형·레코드를 더한 뒤에 해야 손실이 없다. 그 전에는 손 shim 유지가 맞다.
