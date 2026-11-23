# T-252 패킷 코덱 추출기 — 함정과 바닐라 대조 (2026-11-23)

- 1.21.1 패킷 쓰기 방식: `Packet.codec(write, ctor)` 150개 안팎, `StreamCodec.composite` 27, `unit` 9. `writeCollection`·`writeOptional`·`writeNullable` 의 두 번째 인자는 메서드 참조·람다일 수도, **코덱 값 그대로**(`UUIDUtil.STREAM_CODEC` 등 `StreamEncoder` 구현)일 수도 있다. `FriendlyByteBuf.writeNullable(buf, v, enc)` 처럼 **버퍼를 첫 인자로 받는 정적 도우미**도 있다 — 둘 다 풀어야 한다.
- `Stream.apply(ByteBufCodecs::optional)` 처럼 연산을 **메서드 참조**로 넘기는 꼴이 있다(`ServerboundEditBook`·`ResourcePackPush` 등). 호출(`list()`)과 참조(`::optional`)를 모두 읽는다.
- `ItemStack.validatedStreamCodec(코덱)` 는 값 검사만 얹는 껍질 — 안쪽 코덱이 와이어.
- 로그인 거절(`ClientboundLoginDisconnectPacket`)은 **JSON 문자열**. play 의 `component`(NBT)와 다르다. 스키마가 이를 틀리게 적고 있었다(고침).
- `remove_entities` 는 `writeIntIdList`(가변 정수 개수 + 가변 정수들). 스키마 와이어는 `custom`.
- 비트 묶음 필드(`player_position` 의 상대 이동 플래그 i8, 이동 패킷 플래그)는 쓰기 메서드에 분기·연산이 있어 잠금이 칸의 의미를 모른다. 바이트 시험의 무작위 값은 열거형·마스크 칸을 위해 0 만 쓴다(1이 "알 수 없는 애니메이션"이 되는 패킷이 있었다).
- `set_entity_motion` 속도(×8000 짧은 정수)는 범위를 넘는 값을 서버 손 코덱이 잘라 쓴다 → 시험의 `i16` 은 ±1000 안.
- 1.21.2: 컨테이너 번호가 `writeByte` → `VarInt` 로 바뀜(`writeContainerId`, `ByteBufCodecs.CONTAINER_ID` 는 익명 클래스). 쿨다운 패킷은 아이템 번호 → 식별자(`cooldownGroup`).
- 추출기를 jar 안 `net/minecraft/network/codec/` 까지 읽게 했다(잎 코덱의 익명 클래스). 이 때문에 `ByteBufCodecs` 에 새 상수가 생겨도 표를 안 고친다.
- 환경: 이 컨테이너에서 gradle 이 maven central 429 를 낼 수 있다 — 온라인으로 다시 돌리면 된다. 첫 실행은 `--offline` 이 아니라 온라인(`-PskipOracle=true`).
