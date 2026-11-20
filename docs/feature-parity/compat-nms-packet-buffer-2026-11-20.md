# NMS shim 1번 묶음 — 패킷 버퍼 읽기·쓰기와 플러그인 메시지 (2026-11-20, T-244)

호환 구멍 순위(`compat-gaps-ranking-2026-11-20.md`)에서 Mojang 이름으로 NMS 를 부르는 표본 중 Axiom·VeinMiner·headpat·squaremap 이 함께 막히던 묶음이다.
모두 플러그인 메시지(채널 + 바이트) 코덱이 `FriendlyByteBuf` 로 자기 자료를 읽고 쓰다가 막혔다. 기준은 Paper 1.21.1 서버 jar(Mojang 이름) 바이트코드를 `javap -c` 로 본 것이다.

## 1. 바꾼 것

| 무엇 | 전 | 후 |
|---|---|---|
| `FriendlyByteBuf` 위임 메서드 반환형 | 모두 `ByteBuf` | Paper 처럼 쓰기·위치 옮기기 등 71개는 `FriendlyByteBuf`(공변, `return this`). 새 버퍼를 주는 것(copy·slice·duplicate·`readBytes(int)` 등)과 order·unwrap·asReadOnly 만 `ByteBuf`. 생성기 `tools/compat/gen-friendly-bytebuf.py` 가 만든다 |
| `readBytes(int)` | 안쪽 버퍼를 돌려줌(잘못) | 읽은 바이트를 담은 새 버퍼(netty·Paper 와 같다) |
| 도우미 | 없음 | `limitValue`·`readCollection`·`writeCollection`·`readList`·`readMap`(둘)·`writeMap`·`readNullable`·`writeNullable`(인스턴스·정적)·`readById`·`writeById`·`readNbt`(넷)·`writeNbt`(둘)·`readResourceKey`·`writeResourceKey`·`readBlockHitResult`·`writeBlockHitResult`·`readVector3f`·`writeVector3f`·`readQuaternion`·`writeQuaternion`(각 인스턴스·정적)·`DEFAULT_NBT_QUOTA` |
| `Utf8String` | 없음 | `read(ByteBuf, int)`·`write(ByteBuf, CharSequence, int)`. `readUtf`·`writeUtf` 가 이것을 부르는 것도 Paper 와 같다 |
| `CustomPacketPayload` | 생성기가 만든 빈 인터페이스 | `type()`, `Type` 레코드, `createType`, `codec(StreamMemberEncoder, StreamDecoder)` |
| `DiscardedPayload` | 없음 | 레코드(id, data) + `codec(ResourceLocation, int)`(상한 넘으면 `Payload may not be larger than N bytes`) |
| `StreamMemberEncoder`·`StreamCodec.ofMember` | 없음 | Paper 와 같다 |
| 플러그인 메시지 보내기 | `connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, buf)))` 가 "custom 종류" 로 거절 | Furnace 공개 API `ClientboundCustomPayloadPacket(채널, 바이트)` 로 바꿔 보낸다. 반대 방향(ProtocolLib 이 서버가 보내는 플러그인 메시지를 볼 때)도 된다 |

같은 바이트·예외 문구를 Paper 바이트코드에서 옮겼다: 개수는 가변 정수, 선택 값은 boolean 뒤 값, NBT 는 1.20.2+ 네트워크 형식(타입 바이트 + 이름 없는 값, null 은 END 한 바이트), `readNbt()` 의 뿌리가 compound 가 아니면 `DecoderException("Not a compound tag: " + tag)`, `limitValue` 는 `DecoderException("Value i is larger than limit n")`, 맞힌 블록은 위치·방향(가변 정수)·블록 안 좌표 float 셋·inside.
`readMap(키, 값)` 의 맵은 바닐라가 `Maps.newHashMapWithExpectedSize`(guava 32.1.2)인데 `HashMap.newHashMap` 과 표 크기가 같아 순회 순서도 같다(n<3 이면 n+1, 아니면 ceil(n/0.75) — 두 식이 n=0·1·2 에서도 같은 2의 거듭제곱 표가 된다).

## 2. 플러그인 메시지 본문 버퍼

바닐라·Paper 인코더는 `buf.writeBytes(payload.data())` 라서 **보낼 때 본문 버퍼의 읽기 위치가 끝까지 옮겨진다** — 같은 패킷 객체를 두 번 보내면 두 번째는 빈 본문이다. Furnace 도 바꿀 때 읽어서 옮긴다(같은 결과).
그래서 이 종류는 ProtocolLib 다리에서 "값이 그대로 도는 종류"(`PacketDelivery.losslessRoundTrip`)로 두어, 리스너에게 보이기 전 비교 기준을 만들려고 한 번 더 바꾸지 않는다(두 번 바꾸면 두 번째가 빈 본문이 된다).
리스너가 본문 버퍼를 읽으면 그만큼 덜 나가는 것도 Paper 와 같다.

`DiscardedPayload` 가 아닌 본문(바닐라 디버그·브랜드 본문, 플러그인이 직접 만든 본문 클래스)은 `UnsupportedNmsCall` 로 거절한다. Paper 에서도 플러그인 본문 클래스는 인코더가 `DiscardedPayload` 코덱으로 쓰다 `ClassCastException` 이 난다.
받는 쪽(클라이언트 → 서버 플러그인 메시지)은 이 묶음 밖이다 — 지금처럼 Bukkit `registerIncomingPluginChannel` 이나 Furnace 패킷 API 로 받는다(`ServerboundBridge`).

## 3. JOML

`writeVector3f`·`readQuaternion` 등의 서명이 `org.joml` 형식이다(컴파일만 `compileOnly`). Paper 서버에는 JOML 이 늘 있다.
ProtocolLib 처럼 패킷 버퍼를 **반사로 훑는** 플러그인은 `getDeclaredMethods` 가 모든 서명을 한꺼번에 풀어, JOML 이 없으면 그 자리에서 `NoClassDefFoundError` 가 날 수 있다.
Furnace 는 이미 이것을 막는다: 서버가 호환 jar 자체도 라이브러리 표지로 훑어(`PluginBootstrapper` 의 `compatCore`) 호환 계층 서명에 보이는 묶음은 플러그인이 안 써도 준비한다.
호환 jar 에 `org/joml/` 가 생겼으므로 JOML 은 이제 호환 플러그인이 하나라도 있으면 준비된다(`plugins.compat.libraries=needed` 기본값, Maven Central 의 고정 sha256 jar). 서버 라이브러리 목록은 바꾸지 않았다.

## 4. 알려진 차이(남김)

- NBT 바이트 한도(`NbtAccounter` quota, 패킷 2 MiB): 세지 않는다. 깊이 512 는 지킨다. 기존 패킷 NBT 읽기(`NbtWire`)와 같은 상태이고 `NbtAccounter` 주석에 적혀 있다 — 플러그인이 넘는 자료를 스스로 읽는 경우에만 다르다(가정: 드물다).
- 모르는 NBT 타입 번호: 바닐라는 `IOException("Invalid tag id")` 를 `EncoderException` 으로 싸고, Furnace 는 `IllegalArgumentException("unknown NBT tag type")`.
- `ItemStack.STREAM_CODEC`·`OPTIONAL_STREAM_CODEC`(Axiom·VeinMiner)는 아이템 코덱이라 이 묶음 밖이다(아이템 구성 요소 코덱이 필요하다).

## 5. 확인

- 시험 `FriendlyByteBufHelpersTest`(8): 기대 바이트·예외 문구, 공변 반환형(반사), `readBytes(int)`, `DiscardedPayload` 코덱·상한, 플러그인 메시지 두 방향·본문 버퍼 소비·남의 본문 거절.
- `SchemaPacketReverseTest`: 실제 서버에서 `connection.send(… DiscardedPayload …)` 가 클라이언트에 채널·바이트 그대로 가고, 되돌리기 표에서 `custom_payload` 가 "된다" 쪽으로 옮겨졌다.
- 서명 관문: 공변 반환형으로 기준선의 낡은 줄 71개를 지웠다(새 차이 0).
- 감사 다시 돌리기(같은 표본 5개): 없는 심볼 행 224 → 182. 버퍼·플러그인 메시지 쪽 42행이 모두 사라졌다(Axiom 없는 심볼 129 → 92, headpat 7 → 1, VeinMiner 56 → 50, squaremap 46 → 45).
