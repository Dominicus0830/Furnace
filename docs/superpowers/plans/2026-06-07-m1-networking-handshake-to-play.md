# M1 — 네트워킹: 핸드셰이크 → play 진입 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 바닐라 1.21.1(프로토콜 767) 클라이언트가 Furnace에 접속해 status 핑을 받고, 오프라인 로그인 → configuration → play 단계까지 도달한 뒤 빈 월드에서 끊기지 않고 유지되게 한다.

**Architecture:** Netty `ServerBootstrap` 기반. 파이프라인 = `[길이프레이밍 디코더/인코더] → (압축 코덱, 로그인 중 동적 삽입) → ConnectionHandler`. 패킷은 코덱 프레임워크 없이 **패킷마다 `read(ByteBuf)`/`write(ByteBuf)` 수동 작성**(공통은 `Types` 데이터타입 유틸만). 상태머신(handshake/status/login/configuration/play)은 **각 커넥션의 Netty IO 스레드**에서 돈다 — M1엔 공유 world/region 소유 객체가 없어 안전. KeepAlive 타이밍만 기존 `FurnaceScheduler.global()`로 예약하고 실제 전송은 채널 eventLoop로 되돌려 IO 스레드에서 수행.

**Tech Stack:** Java 21, Netty 4.1.115.Final, JUnit 5, 기존 M0 스케줄러(`FurnaceSchedulerImpl`). 프로토콜 1.21.1 = **버전 번호 767**.

**1차 출처:** 패킷 포맷/ID는 Minecraft Wiki(구 wiki.vg) "Java Edition protocol" 1.21.1(767) 문서가 1차 출처다. 본 plan의 패킷 ID 상수는 767 기준이며 각 패킷 클래스에 **명명 상수**로 모아 둔다 — 틀린 ID 하나가 클라 침묵 종료를 일으키므로(설계서 §7 위험) 수정이 한 곳에서 끝나게 한다. 최종 검증은 Task 11의 실제 클라 접속이다.

**M0와의 연결:** M0가 `furnace-server`에 `FurnaceSchedulerImpl`(global+region owner), `Config`/`ConfigLoader`, `FurnaceMain` 부팅을 만들어 뒀다. M1은 여기에 `net/` 패키지를 추가하고 `FurnaceMain`에서 Netty 서버를 기동한다.

**핵심 설계 결정:**
- **패킷 표현 = 수동 read/write.** 패킷마다 record + `write(ByteBuf)` + `static read(ByteBuf)`. 명시적·디버깅 쉬움·학습 목적 부합. 코덱 프레임워크는 패킷 적은 M1엔 과함(YAGNI).
- **상태머신 = Netty 이벤트루프.** M1은 world 미존재라 IO 스레드에서 직접 처리. world 접근이 생기는 M2+에서 핸드오프 지점을 추가한다(retrofit 아님 — 그땐 청크 전송이 명확히 region 소유라 그때 경계를 그음).
- **registry data = known-packs 무데이터 전략.** 1.20.5+ 클라는 play 진입 전 configuration에서 동기화 레지스트리(dimension_type, biome 등)를 받아야 한다. 서버가 `minecraft:core` 알려진 팩을 선언하고 클라가 확인하면, 각 레지스트리 엔트리를 **데이터 없이(id만, hasData=false)** 보낼 수 있고 클라가 자기 내장 팩에서 채운다. → **M1은 NBT 인코딩이 전혀 필요 없다.** 엔트리 id 목록만 리소스로 둔다.
- **압축은 마지막에.** 프로토콜상 임계값 -1로 비활성 가능. play 도달(Task 6~9)을 먼저 비압축으로 끝내고, 압축(Task 10)을 나중에 켠다 — 작은 승리 단위 유지(설계서 §7).

---

## File Structure

| 파일 | 책임 |
|---|---|
| `furnace-server/build.gradle.kts` | Netty 의존 추가 (modify) |
| `furnace-server/.../config/Config.java` | `port`, `compressionThreshold` 필드 추가 (modify) |
| `furnace-server/.../config/ConfigLoader.java` | 새 키 로드 (modify) |
| `furnace-server/.../net/io/Types.java` | VarInt/String/UUID 등 와이어 데이터타입 |
| `furnace-server/.../net/io/FrameDecoder.java` | VarInt 길이 프리픽스 → 프레임 분리 |
| `furnace-server/.../net/io/FrameEncoder.java` | 프레임 → VarInt 길이 프리픽스 |
| `furnace-server/.../net/io/CompressionDecoder.java` | zlib 해제(임계값 기반) |
| `furnace-server/.../net/io/CompressionEncoder.java` | zlib 압축(임계값 기반) |
| `furnace-server/.../net/protocol/ConnectionState.java` | handshake/status/login/configuration/play |
| `furnace-server/.../net/protocol/Packet.java` | clientbound write 마커 인터페이스 |
| `furnace-server/.../net/protocol/ProtocolException.java` | 프로토콜 위반 예외 |
| `furnace-server/.../net/protocol/handshake/Handshake.java` | C→S 0x00 |
| `furnace-server/.../net/protocol/status/*.java` | StatusRequest/Response, PingRequest/PongResponse |
| `furnace-server/.../net/protocol/login/*.java` | LoginStart, SetCompression, LoginSuccess, LoginAcknowledged |
| `furnace-server/.../net/protocol/configuration/*.java` | ClientInformation, KnownPacks(C/S), RegistryData, FinishConfiguration, AckFinishConfiguration |
| `furnace-server/.../net/protocol/play/*.java` | LoginPlay, SyncPlayerPosition, SetDefaultSpawn, ConfirmTeleport, KeepAlive(C/S), Disconnect |
| `furnace-server/.../net/registry/RegistrySet.java` | 동기화 레지스트리 엔트리 id 목록 로더 |
| `furnace-server/src/main/resources/registries/1.21.1.txt` | 레지스트리별 엔트리 id 목록(생성물) |
| `furnace-server/.../net/ConnectionHandler.java` | 커넥션별 상태머신(Netty inbound handler) |
| `furnace-server/.../net/NetworkServer.java` | ServerBootstrap 기동/종료 |
| `furnace-server/.../FurnaceMain.java` | NetworkServer 기동 배선 (modify) |

각 패킷 클래스는 자기 디렉터리(상태별)에 둔다 — 함께 바뀌는 것끼리 모음. ID 상수는 각 클래스 안에 `static final int ID`.

---

## Task 1: Netty 의존 + Config 확장

**Files:**
- Modify: `furnace-server/build.gradle.kts`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/config/Config.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/config/ConfigLoader.java`
- Modify: `furnace-server/src/test/java/org/domi/furnace/server/config/ConfigLoaderTest.java`

- [ ] **Step 1: `build.gradle.kts`에 Netty 추가**

`dependencies` 블록 안 `implementation(project(":furnace-api"))` 아래에 추가:

```kotlin
    implementation("io.netty:netty-all:4.1.115.Final")
```

- [ ] **Step 2: 기존 ConfigLoaderTest에 새 키 테스트 추가**

`ConfigLoaderTest.java`의 `load_returnsDefaults_whenFileMissing` 끝에 단언 2줄 추가:

```java
        assertEquals(25565, c.port());
        assertEquals(256, c.compressionThreshold());
```

그리고 `load_overridesFromFile`의 `Files.writeString` 내용과 단언을 교체:

```java
        Files.writeString(f, "server-name=My Server\ntick-nanos=25000000\nport=25500\ncompression-threshold=-1\n");
        Config c = ConfigLoader.load(f);
        assertEquals("My Server", c.serverName());
        assertEquals(25_000_000L, c.tickNanos());
        assertEquals(25500, c.port());
        assertEquals(-1, c.compressionThreshold());
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ConfigLoaderTest"`
Expected: 컴파일 실패 — `Config`에 `port()`/`compressionThreshold()` 없음.

- [ ] **Step 4: `Config.java` 수정**

레코드 시그니처와 DEFAULT를 교체:

```java
// 서버 런타임 설정 값
package org.domi.furnace.server.config;

public record Config(String serverName, long tickNanos, int port, int compressionThreshold) {

    public static final Config DEFAULT =
            new Config("Furnace Server", 50_000_000L, 25565, 256);
}
```

- [ ] **Step 5: `ConfigLoader.java` 수정**

`load` 메서드의 `return new Config(...)` 직전/부분을 교체(name/tick 파싱 유지하고 두 줄 추가, 반환 교체):

```java
        String name = props.getProperty("server-name", Config.DEFAULT.serverName());
        long tick = Long.parseLong(
                props.getProperty("tick-nanos", Long.toString(Config.DEFAULT.tickNanos())));
        int port = Integer.parseInt(
                props.getProperty("port", Integer.toString(Config.DEFAULT.port())));
        int compression = Integer.parseInt(
                props.getProperty("compression-threshold",
                        Integer.toString(Config.DEFAULT.compressionThreshold())));
        return new Config(name, tick, port, compression);
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*ConfigLoaderTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: 커밋**

```bash
git add furnace-server
git commit -m "feat(server): add netty dep and network config (port, compression)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: 와이어 데이터타입 (Types) — VarInt/String/UUID

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/io/Types.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/io/TypesTest.java`

VarInt는 프로토콜의 주춧돌(길이 프리픽스·패킷 id·대부분 길이값). 7비트씩 LEB128, 최대 5바이트.

- [ ] **Step 1: 실패하는 테스트 작성**

`TypesTest.java`:
```java
// 와이어 데이터타입(VarInt/String/UUID) 라운드트립 테스트
package org.domi.furnace.server.net.io;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TypesTest {

    @Test
    void varInt_roundTrips_boundaryValues() {
        int[] values = {0, 1, 127, 128, 255, 2_097_151, Integer.MAX_VALUE, -1};
        for (int v : values) {
            ByteBuf buf = Unpooled.buffer();
            Types.writeVarInt(buf, v);
            assertEquals(v, Types.readVarInt(buf), "value=" + v);
            assertFalse(buf.isReadable(), "leftover bytes for " + v);
            buf.release();
        }
    }

    @Test
    void varInt_minValue_uses5Bytes() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeVarInt(buf, -1);
        assertEquals(5, buf.readableBytes());
        buf.release();
    }

    @Test
    void varInt_throws_whenTooLong() {
        ByteBuf buf = Unpooled.buffer();
        for (int i = 0; i < 6; i++) buf.writeByte(0x80); // 계속 continuation 비트
        assertThrows(ProtocolException.class, () -> Types.readVarInt(buf));
        buf.release();
    }

    @Test
    void string_roundTrips() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeString(buf, "Furnace ⛏ 한글");
        assertEquals("Furnace ⛏ 한글", Types.readString(buf, 256));
        buf.release();
    }

    @Test
    void uuid_roundTrips() {
        UUID id = UUID.randomUUID();
        ByteBuf buf = Unpooled.buffer();
        Types.writeUuid(buf, id);
        assertEquals(id, Types.readUuid(buf));
        buf.release();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*TypesTest"`
Expected: 컴파일 실패 — `Types`/`ProtocolException` 없음.

- [ ] **Step 3: `ProtocolException.java` 작성** (Task 4에서도 쓰는 공용 예외, 여기서 먼저 만든다)

```java
// 프로토콜 규약 위반(잘못된 VarInt, 미지원 패킷 등)을 나타내는 예외
package org.domi.furnace.server.net.protocol;

public final class ProtocolException extends RuntimeException {
    public ProtocolException(String message) {
        super(message);
    }
}
```

> 주의: `Types`는 `org.domi.furnace.server.net.io` 패키지지만 `ProtocolException`은 `...net.protocol`이다. `Types`에서 import 한다.

- [ ] **Step 4: `Types.java` 작성**

```java
// 마인크래프트 프로토콜 와이어 데이터타입(VarInt/String/UUID 등) 읽기·쓰기 유틸
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.domi.furnace.server.net.protocol.ProtocolException;

public final class Types {

    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;

    private Types() {}

    /** LEB128 VarInt 읽기. 5바이트 초과 시 ProtocolException. */
    public static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;
        byte current;
        do {
            current = buf.readByte();
            value |= (current & SEGMENT_BITS) << position;
            position += 7;
            if (position > 35) {
                throw new ProtocolException("VarInt too long");
            }
        } while ((current & CONTINUE_BIT) != 0);
        return value;
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~SEGMENT_BITS) != 0) {
            buf.writeByte((value & SEGMENT_BITS) | CONTINUE_BIT);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    /** VarInt 길이 프리픽스 + UTF-8 바이트로 인코딩된 문자열. */
    public static String readString(ByteBuf buf, int maxLen) {
        int len = readVarInt(buf);
        if (len < 0 || len > maxLen * 4) {
            throw new ProtocolException("String length out of bounds: " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        String s = new String(bytes, StandardCharsets.UTF_8);
        if (s.length() > maxLen) {
            throw new ProtocolException("String longer than max: " + s.length());
        }
        return s;
    }

    public static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    /** 128비트 UUID (상위 64 + 하위 64). */
    public static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public static void writeUuid(ByteBuf buf, UUID value) {
        buf.writeLong(value.getMostSignificantBits());
        buf.writeLong(value.getLeastSignificantBits());
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*TypesTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): add wire data types (VarInt/String/UUID) and ProtocolException" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: 프레이밍 — FrameDecoder / FrameEncoder

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/io/FrameDecoder.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/io/FrameEncoder.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/io/FramingTest.java`

각 패킷은 `VarInt 길이 + 그만큼의 바이트`로 감싼다. FrameDecoder는 길이를 읽고 그만큼 모이면 payload만 떼서 다음 핸들러로. 미완 프레임은 버퍼링.

- [ ] **Step 1: 실패하는 테스트 작성**

`FramingTest.java`:
```java
// 길이 프리픽스 프레이밍(인코더/디코더) 라운드트립·부분수신 테스트
package org.domi.furnace.server.net.io;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class FramingTest {

    @Test
    void encodeThenDecode_roundTrips() {
        EmbeddedChannel enc = new EmbeddedChannel(new FrameEncoder());
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4, 5});
        assertTrue(enc.writeOutbound(payload));
        ByteBuf framed = enc.readOutbound();

        EmbeddedChannel dec = new EmbeddedChannel(new FrameDecoder());
        assertTrue(dec.writeInbound(framed));
        ByteBuf out = dec.readInbound();
        assertEquals(5, out.readableBytes());
        for (int i = 1; i <= 5; i++) assertEquals(i, out.readByte());
        out.release();
    }

    @Test
    void decoder_buffersPartialFrame() {
        // 프레임: 길이=3, 바이트 [9,9,9]. 한 바이트씩 흘려보낸다.
        EmbeddedChannel dec = new EmbeddedChannel(new FrameDecoder());
        assertFalse(dec.writeInbound(Unpooled.wrappedBuffer(new byte[]{3})));   // 길이만
        assertFalse(dec.writeInbound(Unpooled.wrappedBuffer(new byte[]{9, 9}))); // 2/3 바이트
        assertTrue(dec.writeInbound(Unpooled.wrappedBuffer(new byte[]{9})));     // 마지막 → 완성
        ByteBuf out = dec.readInbound();
        assertEquals(3, out.readableBytes());
        out.release();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*FramingTest"`
Expected: 컴파일 실패 — `FrameDecoder`/`FrameEncoder` 없음.

- [ ] **Step 3: `FrameDecoder.java` 작성**

```java
// 인바운드 바이트스트림을 VarInt 길이 프리픽스 단위 프레임으로 분리하는 디코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import org.domi.furnace.server.net.protocol.ProtocolException;

public final class FrameDecoder extends ByteToMessageDecoder {

    private static final int MAX_PACKET = 2 * 1024 * 1024; // 2 MiB 안전 상한

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.markReaderIndex();
        // VarInt 길이 읽기 — 미완이면 reset 후 더 기다림
        int length = 0;
        int position = 0;
        while (true) {
            if (!in.isReadable()) {
                in.resetReaderIndex();
                return;
            }
            byte b = in.readByte();
            length |= (b & 0x7F) << position;
            position += 7;
            if (position > 35) {
                throw new ProtocolException("Frame length VarInt too long");
            }
            if ((b & 0x80) == 0) break;
        }
        if (length < 0 || length > MAX_PACKET) {
            throw new ProtocolException("Frame length out of bounds: " + length);
        }
        if (in.readableBytes() < length) {
            in.resetReaderIndex(); // payload 아직 다 안 옴
            return;
        }
        out.add(in.readRetainedSlice(length));
    }
}
```

- [ ] **Step 4: `FrameEncoder.java` 작성**

```java
// 아웃바운드 프레임 payload 앞에 VarInt 길이 프리픽스를 붙이는 인코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public final class FrameEncoder extends MessageToByteEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        Types.writeVarInt(out, msg.readableBytes());
        out.writeBytes(msg);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*FramingTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): add length-prefix frame decoder/encoder" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: 상태 enum + Packet 마커 + ConnectionHandler 골격 + NetworkServer + 부팅 배선

이 Task는 파이프라인을 세우고 서버가 포트를 열게 한다. 아직 패킷 핸들링 로직은 비었다(Task 5에서 status부터 채움). 검증은 "서버가 떠서 포트 LISTEN"까지.

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/ConnectionState.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/Packet.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/NetworkServer.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/NetworkServerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성** (서버 기동 → 포트 TCP 연결 가능)

`NetworkServerTest.java`:
```java
// NetworkServer가 설정 포트를 열어 TCP 연결을 수락하는지 검증
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.Test;

class NetworkServerTest {

    @Test
    void server_acceptsTcpConnection() throws Exception {
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server"); // port 0 = OS가 빈 포트 배정
        int port = server.start();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            assertTrue(s.isConnected());
        } finally {
            server.stop();
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*NetworkServerTest"`
Expected: 컴파일 실패 — `NetworkServer` 없음.

- [ ] **Step 3: `ConnectionState.java` 작성**

```java
// 커넥션의 프로토콜 단계. 패킷 id 의미는 단계마다 다르다.
package org.domi.furnace.server.net.protocol;

public enum ConnectionState {
    HANDSHAKE,
    STATUS,
    LOGIN,
    CONFIGURATION,
    PLAY
}
```

- [ ] **Step 4: `Packet.java` 작성** (clientbound 공통 형태)

```java
// 서버→클라 패킷의 공통 형태. id와 body 직렬화를 노출한다.
package org.domi.furnace.server.net.protocol;

import io.netty.buffer.ByteBuf;

public interface Packet {

    /** 이 패킷의 프로토콜 id(해당 단계 기준). */
    int id();

    /** 패킷 body를 buf에 기록(id는 ConnectionHandler가 앞에 씀). */
    void write(ByteBuf buf);
}
```

- [ ] **Step 5: `ConnectionHandler.java` 골격 작성**

```java
// 한 커넥션의 프로토콜 상태머신. 프레임을 받아 단계별로 해석하고 응답 패킷을 보낸다.
package org.domi.furnace.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.ConnectionState;
import org.domi.furnace.server.net.protocol.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);

    private final String serverName;
    private ConnectionState state = ConnectionState.HANDSHAKE;

    public ConnectionHandler(String serverName) {
        this.serverName = serverName;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        int packetId = Types.readVarInt(frame);
        switch (state) {
            case HANDSHAKE -> handleHandshake(ctx, packetId, frame);
            default -> {
                // Task 5+에서 단계별 분기를 채운다.
                log.debug("미처리 패킷: state={}, id=0x{}", state, Integer.toHexString(packetId));
            }
        }
    }

    private void handleHandshake(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        // Task 5에서 구현.
    }

    /** 응답 패킷 전송: [VarInt id][body] 프레임으로 인코딩 → FrameEncoder가 길이 prefix. */
    void send(ChannelHandlerContext ctx, Packet packet) {
        ByteBuf buf = ctx.alloc().buffer();
        Types.writeVarInt(buf, packet.id());
        packet.write(buf);
        ctx.writeAndFlush(buf);
    }

    void state(ConnectionState newState) {
        this.state = newState;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("커넥션 오류, 종료: {}", cause.toString());
        ctx.close();
    }
}
```

- [ ] **Step 6: `NetworkServer.java` 작성**

```java
// Netty ServerBootstrap으로 프로토콜 파이프라인을 세워 접속을 수락하는 네트워크 서버
package org.domi.furnace.server.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.net.InetSocketAddress;
import org.domi.furnace.server.net.io.FrameDecoder;
import org.domi.furnace.server.net.io.FrameEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkServer {

    private static final Logger log = LoggerFactory.getLogger(NetworkServer.class);

    private final int port;
    private final int compressionThreshold;
    private final String serverName;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public NetworkServer(int port, int compressionThreshold, String serverName) {
        this.port = port;
        this.compressionThreshold = compressionThreshold;
        this.serverName = serverName;
    }

    /** 서버 기동. 실제 바인딩된 포트 반환(port=0이면 OS 배정 포트). */
    public int start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("frameDecoder", new FrameDecoder())
                                .addLast("frameEncoder", new FrameEncoder())
                                .addLast("handler", new ConnectionHandler(serverName));
                    }
                });
        channel = b.bind(port).syncUninterruptibly().channel();
        int boundPort = ((InetSocketAddress) channel.localAddress()).getPort();
        log.info("네트워크 서버 listen: 포트 {} (압축 임계값 {})", boundPort, compressionThreshold);
        return boundPort;
    }

    public void stop() {
        if (channel != null) channel.close().syncUninterruptibly();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        log.info("네트워크 서버 종료");
    }
}
```

> 압축 임계값은 Task 10에서 파이프라인에 실제 반영한다. 지금은 생성자에 보관만.

- [ ] **Step 7: `FurnaceMain.java`에 NetworkServer 배선**

import 추가:
```java
import org.domi.furnace.server.net.NetworkServer;
```

`FurnaceSchedulerImpl scheduler = ...` 줄 다음에 추가:
```java
        NetworkServer network = new NetworkServer(
                config.port(), config.compressionThreshold(), config.serverName());
        network.start();
```

종료 훅(`scheduler.close();` 가 있는 람다) 안, `scheduler.close();` 위에 추가:
```java
            network.stop();
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*NetworkServerTest"`
Expected: PASS (1 test).

- [ ] **Step 9: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): add netty pipeline, connection state machine skeleton, bootstrap wiring" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Handshake + Status (서버 리스트 핑)

이 Task가 끝나면 멀티플레이어 서버 목록에서 Furnace가 **이름·버전·플레이어수와 함께 보인다.** 자동 통합 테스트(소켓)로 검증한다 — M1 첫 진짜 마일스톤 슬라이스.

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/handshake/Handshake.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/status/StatusResponse.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/status/PongResponse.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/StatusPingTest.java`

프로토콜(767):
- C→S Handshake `0x00`: VarInt protocolVersion, String serverAddress, UShort port, VarInt nextState(1=status,2=login).
- STATUS 단계: C→S Status Request `0x00`(빈 body) → S→C Status Response `0x00`(JSON). C→S Ping Request `0x01`(Long) → S→C Pong Response `0x01`(같은 Long).

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`StatusPingTest.java`:
```java
// handshake→status 핑 경로를 실제 소켓으로 검증(JSON에 protocol 767 포함)
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class StatusPingTest {

    @Test
    void handshakeThenStatusRequest_returnsJsonWithProtocol767() throws Exception {
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server");
        int port = server.start();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            // Handshake 프레임 작성: [len][id=0][protoVer][addr][port][next=1]
            ByteBuf hs = Unpooled.buffer();
            Types.writeVarInt(hs, 0x00);
            Types.writeVarInt(hs, 767);
            Types.writeString(hs, "127.0.0.1");
            hs.writeShort(port);
            Types.writeVarInt(hs, 1);
            writeFrame(out, hs);

            // Status Request: [len][id=0]
            ByteBuf req = Unpooled.buffer();
            Types.writeVarInt(req, 0x00);
            writeFrame(out, req);

            // Status Response 프레임 읽기
            int frameLen = readVarInt(in);
            byte[] body = in.readNBytes(frameLen);
            ByteBuf resp = Unpooled.wrappedBuffer(body);
            int id = Types.readVarInt(resp);
            assertEquals(0x00, id);
            String json = Types.readString(resp, 32767);
            assertTrue(json.contains("\"protocol\":767"), json);
            assertTrue(json.contains("Furnace Server"), json);
        } finally {
            server.stop();
        }
    }

    private static void writeFrame(OutputStream out, ByteBuf body) throws Exception {
        ByteBuf framed = Unpooled.buffer();
        Types.writeVarInt(framed, body.readableBytes());
        framed.writeBytes(body);
        byte[] arr = new byte[framed.readableBytes()];
        framed.readBytes(arr);
        out.write(arr);
        out.flush();
    }

    private static int readVarInt(InputStream in) throws Exception {
        int value = 0, position = 0, b;
        do {
            b = in.read();
            if (b == -1) throw new IllegalStateException("EOF");
            value |= (b & 0x7F) << position;
            position += 7;
        } while ((b & 0x80) != 0);
        return value;
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*StatusPingTest"`
Expected: 실패 — handshake/status 미구현이라 응답 없음(타임아웃/EOF).

- [ ] **Step 3: `Handshake.java` 작성** (serverbound — read만)

```java
// C→S 핸드셰이크 패킷. 프로토콜 버전과 다음 단계(status/login)를 알린다.
package org.domi.furnace.server.net.protocol.handshake;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;

public record Handshake(int protocolVersion, String serverAddress, int serverPort, int nextState) {

    public static final int ID = 0x00;

    public static Handshake read(ByteBuf buf) {
        int protocol = Types.readVarInt(buf);
        String addr = Types.readString(buf, 255);
        int port = buf.readUnsignedShort();
        int next = Types.readVarInt(buf);
        return new Handshake(protocol, addr, port, next);
    }
}
```

- [ ] **Step 4: `StatusResponse.java` 작성** (clientbound)

```java
// S→C 서버 상태 JSON(버전/플레이어수/MOTD) 응답 패킷
package org.domi.furnace.server.net.protocol.status;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record StatusResponse(String json) implements Packet {

    public static final int ID = 0x00;

    /** 1.21.1(767) 상태 JSON 빌드. */
    public static StatusResponse of(String motd) {
        String safe = motd.replace("\\", "\\\\").replace("\"", "\\\"");
        String json = "{\"version\":{\"name\":\"1.21.1\",\"protocol\":767},"
                + "\"players\":{\"max\":20,\"online\":0,\"sample\":[]},"
                + "\"description\":{\"text\":\"" + safe + "\"}}";
        return new StatusResponse(json);
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, json);
    }
}
```

- [ ] **Step 5: `PongResponse.java` 작성** (clientbound)

```java
// S→C 핑 응답. 클라가 보낸 페이로드(Long)를 그대로 되돌려 지연 측정에 쓰게 한다.
package org.domi.furnace.server.net.protocol.status;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record PongResponse(long payload) implements Packet {

    public static final int ID = 0x01;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeLong(payload);
    }
}
```

- [ ] **Step 6: `ConnectionHandler` 에 handshake/status 채우기**

import 추가:
```java
import org.domi.furnace.server.net.protocol.handshake.Handshake;
import org.domi.furnace.server.net.protocol.status.PongResponse;
import org.domi.furnace.server.net.protocol.status.StatusResponse;
```

`channelRead0`의 switch를 교체:
```java
        switch (state) {
            case HANDSHAKE -> handleHandshake(ctx, packetId, frame);
            case STATUS -> handleStatus(ctx, packetId, frame);
            default -> log.debug("미처리 패킷: state={}, id=0x{}", state, Integer.toHexString(packetId));
        }
```

`handleHandshake` 본문 구현 + `handleStatus` 추가:
```java
    private void handleHandshake(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        if (packetId != Handshake.ID) {
            ctx.close();
            return;
        }
        Handshake hs = Handshake.read(frame);
        state = switch (hs.nextState()) {
            case 1 -> ConnectionState.STATUS;
            case 2 -> ConnectionState.LOGIN;
            default -> { ctx.close(); yield ConnectionState.HANDSHAKE; }
        };
    }

    private void handleStatus(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> send(ctx, StatusResponse.of(serverName));        // Status Request
            case 0x01 -> send(ctx, new PongResponse(frame.readLong()));   // Ping Request
            default -> ctx.close();
        }
    }
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*StatusPingTest"`
Expected: PASS (1 test).

- [ ] **Step 8: 수동 확인(선택)**

`./gradlew :furnace-server:run` 후 바닐라 1.21.1 클라 멀티플레이어에서 `localhost` 추가 → 서버 목록에 "Furnace Server", 버전 "1.21.1", 0/20 표시. (로그인은 아직 안 됨.)

- [ ] **Step 9: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): handle handshake and status (server list ping)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Login (오프라인)

오프라인 모드 = 암호화/인증 없음. 압축도 이 Task에선 끈다(임계값 -1 가정, Task 10에서 켬). 흐름: C→S Login Start → S→C Login Success → C→S Login Acknowledged → CONFIGURATION 전이.

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/login/LoginStart.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/login/LoginSuccess.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/protocol/login/LoginPacketsTest.java`

프로토콜(767):
- C→S Login Start `0x00`: String name(≤16), UUID playerUuid.
- S→C Login Success `0x02`: UUID, String name, VarInt propertyCount(=0), Boolean strictErrorHandling(false).
- C→S Login Acknowledged `0x03`: 빈 body → CONFIGURATION.

오프라인 UUID: `OfflinePlayer:<name>` MD5(version 3) 규약.

- [ ] **Step 1: 실패하는 테스트 작성** (패킷 read/write 라운드트립)

`LoginPacketsTest.java`:
```java
// 로그인 패킷 read/write 라운드트립 + 오프라인 UUID 생성 검증
package org.domi.furnace.server.net.protocol.login;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class LoginPacketsTest {

    @Test
    void loginStart_reads() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeString(buf, "Dominicus");
        UUID id = UUID.randomUUID();
        Types.writeUuid(buf, id);
        LoginStart p = LoginStart.read(buf);
        assertEquals("Dominicus", p.name());
        assertEquals(id, p.uuid());
    }

    @Test
    void loginSuccess_writes_idAndName() {
        UUID id = UUID.randomUUID();
        LoginSuccess p = new LoginSuccess(id, "Dominicus");
        assertEquals(0x02, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(id, Types.readUuid(buf));
        assertEquals("Dominicus", Types.readString(buf, 16));
        assertEquals(0, Types.readVarInt(buf));   // property count
        assertEquals(0, buf.readByte());          // strict error handling = false
        buf.release();
    }

    @Test
    void offlineUuid_isDeterministic() {
        assertEquals(LoginStart.offlineUuid("Dominicus"), LoginStart.offlineUuid("Dominicus"));
        assertNotEquals(LoginStart.offlineUuid("a"), LoginStart.offlineUuid("b"));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*LoginPacketsTest"`
Expected: 컴파일 실패 — `LoginStart`/`LoginSuccess` 없음.

- [ ] **Step 3: `LoginStart.java` 작성**

```java
// C→S 로그인 시작. 플레이어 이름과 (클라가 보낸) UUID를 담는다. 오프라인 UUID 유틸 포함.
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;

public record LoginStart(String name, UUID uuid) {

    public static final int ID = 0x00;

    public static LoginStart read(ByteBuf buf) {
        String name = Types.readString(buf, 16);
        UUID uuid = Types.readUuid(buf);
        return new LoginStart(name, uuid);
    }

    /** 오프라인 모드 UUID: "OfflinePlayer:<name>"의 MD5(name-based v3). */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 4: `LoginSuccess.java` 작성**

```java
// S→C 로그인 성공. 확정된 UUID와 이름, 빈 속성 목록을 보낸다.
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record LoginSuccess(UUID uuid, String name) implements Packet {

    public static final int ID = 0x02;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeUuid(buf, uuid);
        Types.writeString(buf, name);
        Types.writeVarInt(buf, 0);  // property count
        buf.writeBoolean(false);    // strict error handling
    }
}
```

- [ ] **Step 5: `ConnectionHandler`에 login 채우기**

import 추가:
```java
import org.domi.furnace.server.net.protocol.login.LoginStart;
import org.domi.furnace.server.net.protocol.login.LoginSuccess;
import java.util.UUID;
```

필드 추가(`private ConnectionState state` 아래):
```java
    private String playerName;
    private UUID playerUuid;
```

switch에 `case LOGIN` 추가:
```java
            case LOGIN -> handleLogin(ctx, packetId, frame);
```

메서드 추가:
```java
    private void handleLogin(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { // Login Start
                LoginStart start = LoginStart.read(frame);
                this.playerName = start.name();
                this.playerUuid = LoginStart.offlineUuid(start.name());
                // (압축은 Task 10에서: 여기서 Set Compression 전송 후 코덱 삽입)
                send(ctx, new LoginSuccess(playerUuid, playerName));
            }
            case 0x03 -> { // Login Acknowledged
                state = ConnectionState.CONFIGURATION;
                onEnterConfiguration(ctx); // Task 8에서 구현
            }
            default -> ctx.close();
        }
    }

    private void onEnterConfiguration(ChannelHandlerContext ctx) {
        // Task 8에서 구현(Known Packs 전송 등).
    }
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*LoginPacketsTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): handle offline login (login start/success/ack)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: 동기화 레지스트리 엔트리 목록 (RegistrySet)

Configuration 단계에서 보낼 레지스트리 엔트리 id 목록을 리소스에서 로드한다. known-packs 무데이터 전략이라 **id 문자열만** 필요(NBT 없음).

**Files:**
- Create: `furnace-server/src/main/resources/registries/1.21.1.txt`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/registry/RegistrySet.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/registry/RegistrySetTest.java`

리소스 포맷: 한 줄 = `<registry_key> <entry_id>`. 예:
```
minecraft:dimension_type minecraft:overworld
minecraft:worldgen/biome minecraft:plains
```

- [ ] **Step 1: 리소스 `registries/1.21.1.txt` 생성**

**생성 방법(권장, 정확).** 바닐라 1.21.1 서버 jar로 데이터 생성:
```bash
java -DbundlerMainClass=net.minecraft.data.Main -jar minecraft_server.1.21.1.jar --reports
```
생성된 `generated/data/minecraft/<path>/*.json` 파일명(확장자 제거)이 각 동기화 레지스트리의 엔트리 id다. 아래 레지스트리들에 대해 `minecraft:<registry> minecraft:<파일명>` 줄을 만든다:
`dimension_type`, `worldgen/biome`, `painting_variant`, `wolf_variant`, `damage_type`, `banner_pattern`, `trim_pattern`, `trim_material`, `chat_type`, `jukebox_song`.

> 이 목록은 "클라가 play 진입 시 요구하는 동기화 레지스트리"의 1.21.1 집합이다. 접속 시 클라가 `Missing registry` 류로 끊으면 그 레지스트리를 목록에 추가한다(Task 11 verify에서 확인).

**부트스트랩 최소본(데이터 생성 전 우선 동작 확인용).** 최소한 dimension_type의 overworld만 있어도 컴파일/테스트는 통과한다. 우선 아래 한 줄로 파일을 만들고, 실제 클라 접속(Task 11) 전에 위 생성물로 교체한다:
```
minecraft:dimension_type minecraft:overworld
```

- [ ] **Step 2: 실패하는 테스트 작성**

`RegistrySetTest.java`:
```java
// 레지스트리 엔트리 목록 로더가 리소스를 파싱하고 id를 인덱싱하는지 검증
package org.domi.furnace.server.net.registry;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrySetTest {

    @Test
    void loads_overworld_inDimensionType() {
        RegistrySet set = RegistrySet.load("registries/1.21.1.txt");
        List<String> dims = set.entries("minecraft:dimension_type");
        assertTrue(dims.contains("minecraft:overworld"), dims.toString());
    }

    @Test
    void indexOf_findsEntryPosition() {
        RegistrySet set = RegistrySet.load("registries/1.21.1.txt");
        assertTrue(set.indexOf("minecraft:dimension_type", "minecraft:overworld") >= 0);
        assertEquals(-1, set.indexOf("minecraft:dimension_type", "minecraft:nope"));
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*RegistrySetTest"`
Expected: 컴파일 실패 — `RegistrySet` 없음.

- [ ] **Step 4: `RegistrySet.java` 작성**

```java
// configuration 단계에서 보낼 동기화 레지스트리별 엔트리 id 목록을 리소스에서 로드/조회
package org.domi.furnace.server.net.registry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegistrySet {

    // 삽입 순서 유지 — 레지스트리 순서/엔트리 순서가 곧 클라가 받는 인덱스가 된다.
    private final Map<String, List<String>> registries;

    private RegistrySet(Map<String, List<String>> registries) {
        this.registries = registries;
    }

    public static RegistrySet load(String resourcePath) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        ClassLoader cl = RegistrySet.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("레지스트리 리소스 없음: " + resourcePath);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sp = line.indexOf(' ');
                if (sp < 0) continue;
                String registry = line.substring(0, sp);
                String entry = line.substring(sp + 1).strip();
                map.computeIfAbsent(registry, k -> new ArrayList<>()).add(entry);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new RegistrySet(map);
    }

    /** 모든 레지스트리 키(삽입 순서). */
    public List<String> registryKeys() {
        return new ArrayList<>(registries.keySet());
    }

    /** 주어진 레지스트리의 엔트리 id 목록(없으면 빈 목록). */
    public List<String> entries(String registry) {
        return registries.getOrDefault(registry, List.of());
    }

    /** registry 안 entry의 0-기반 인덱스(없으면 -1). */
    public int indexOf(String registry, String entry) {
        return entries(registry).indexOf(entry);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*RegistrySetTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): load synchronized registry entry ids from resource" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: Configuration 단계 (Known Packs → Registry Data → Finish)

흐름(767):
1. CONFIGURATION 진입 시 S→C Clientbound Known Packs `0x0E`: 팩 1개 `minecraft:core / 1.21.1`.
2. 클라가 보냄(읽고 처리/무시): Client Information `0x00`, Plugin Message `0x02`(brand), Serverbound Known Packs `0x07`.
3. Serverbound Known Packs `0x07` 수신 시: 각 레지스트리당 S→C Registry Data `0x07`(엔트리 = id + hasData=false) 전송 → S→C Finish Configuration `0x03`.
4. C→S Acknowledge Finish Configuration `0x03` 수신 → PLAY 전이 + play 진입 패킷(Task 9).

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/configuration/KnownPacksClientbound.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/configuration/RegistryData.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/configuration/FinishConfiguration.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/protocol/configuration/RegistryDataTest.java`

- [ ] **Step 1: 실패하는 테스트 작성** (Registry Data write 구조 검증)

`RegistryDataTest.java`:
```java
// Registry Data 패킷이 [registryId][count][entry(id, hasData=false)...] 구조로 쓰이는지 검증
package org.domi.furnace.server.net.protocol.configuration;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class RegistryDataTest {

    @Test
    void writes_registryId_count_andEntriesWithoutData() {
        RegistryData p = new RegistryData(
                "minecraft:dimension_type",
                List.of("minecraft:overworld", "minecraft:the_nether"));
        assertEquals(0x07, p.id());

        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals("minecraft:dimension_type", Types.readString(buf, 256)); // registry id
        assertEquals(2, Types.readVarInt(buf));                                // entry count
        assertEquals("minecraft:overworld", Types.readString(buf, 256));
        assertFalse(buf.readBoolean());                                        // hasData=false
        assertEquals("minecraft:the_nether", Types.readString(buf, 256));
        assertFalse(buf.readBoolean());
        assertFalse(buf.isReadable());
        buf.release();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*RegistryDataTest"`
Expected: 컴파일 실패 — `RegistryData` 없음.

- [ ] **Step 3: `RegistryData.java` 작성**

```java
// S→C 한 레지스트리의 엔트리 목록. known-packs 전략이라 데이터 없이 id만 보낸다(hasData=false).
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record RegistryData(String registryId, List<String> entries) implements Packet {

    public static final int ID = 0x07;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, registryId);
        Types.writeVarInt(buf, entries.size());
        for (String entry : entries) {
            Types.writeString(buf, entry);
            buf.writeBoolean(false); // hasData: 클라가 알려진 팩(minecraft:core)에서 채운다
        }
    }
}
```

- [ ] **Step 4: `KnownPacksClientbound.java` 작성**

```java
// S→C 서버가 아는 데이터팩 목록. minecraft:core/1.21.1 하나를 선언해 무데이터 레지스트리 전송을 가능케 함.
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record KnownPacksClientbound() implements Packet {

    public static final int ID = 0x0E;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, 1);                 // pack count
        Types.writeString(buf, "minecraft");       // namespace
        Types.writeString(buf, "core");            // id
        Types.writeString(buf, "1.21.1");          // version
    }
}
```

- [ ] **Step 5: `FinishConfiguration.java` 작성**

```java
// S→C configuration 종료 신호. 클라는 Ack로 응답하고 play로 넘어간다.
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record FinishConfiguration() implements Packet {

    public static final int ID = 0x03;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        // 빈 body
    }
}
```

- [ ] **Step 6: `ConnectionHandler`에 configuration 채우기**

import 추가:
```java
import org.domi.furnace.server.net.protocol.configuration.FinishConfiguration;
import org.domi.furnace.server.net.protocol.configuration.KnownPacksClientbound;
import org.domi.furnace.server.net.protocol.configuration.RegistryData;
import org.domi.furnace.server.net.registry.RegistrySet;
```

필드 추가 + 생성자에서 로드:
```java
    private static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");
```

switch에 추가:
```java
            case CONFIGURATION -> handleConfiguration(ctx, packetId, frame);
```

`onEnterConfiguration` 구현 교체 + `handleConfiguration` 추가:
```java
    private void onEnterConfiguration(ChannelHandlerContext ctx) {
        send(ctx, new KnownPacksClientbound());
    }

    private void handleConfiguration(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00, 0x02 -> { /* Client Information / Plugin Message(brand): 읽지 않고 무시 */ }
            case 0x07 -> { // Serverbound Known Packs → 레지스트리 전송 후 종료
                for (String registry : REGISTRIES.registryKeys()) {
                    send(ctx, new RegistryData(registry, REGISTRIES.entries(registry)));
                }
                send(ctx, new FinishConfiguration());
            }
            case 0x03 -> { // Acknowledge Finish Configuration
                state = ConnectionState.PLAY;
                onEnterPlay(ctx); // Task 9에서 구현
            }
            default -> log.debug("미처리 config 패킷 id=0x{}", Integer.toHexString(packetId));
        }
    }

    private void onEnterPlay(ChannelHandlerContext ctx) {
        // Task 9에서 구현.
    }
```

> 주의: `SimpleChannelInboundHandler`가 frame을 자동 해제하므로 무시 케이스(0x00/0x02)에서 frame을 따로 읽지 않아도 된다.

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*RegistryDataTest"`
Expected: PASS (1 test).

- [ ] **Step 8: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): configuration stage (known packs, registry data, finish)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: Play 진입 (Login(play) + 위치 동기 + 기본 스폰)

PLAY 진입 시 보낼 최소 패킷(767):
- S→C Login(play) `0x2B`.
- S→C Synchronize Player Position `0x40`.
- S→C Set Default Spawn Position `0x54`.
- C→S Confirm Teleportation `0x00`: VarInt teleportId(읽고 확인).

청크는 M2라 클라는 "Loading terrain"에 머문다 — 연결 유지가 M1 목표(설계서 verify).

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/LoginPlay.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/SyncPlayerPosition.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/SetDefaultSpawnPosition.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/protocol/play/PlayPacketsTest.java`

- [ ] **Step 1: 실패하는 테스트 작성** (write 스모크: 예외 없이 직렬화 + id 확인)

`PlayPacketsTest.java`:
```java
// play 진입 패킷들이 예외 없이 직렬화되고 올바른 id를 갖는지 스모크 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class PlayPacketsTest {

    @Test
    void loginPlay_writesWithoutError() {
        LoginPlay p = new LoginPlay(1, "minecraft:overworld", 0);
        assertEquals(0x2B, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertTrue(buf.readableBytes() > 0);
        buf.release();
    }

    @Test
    void syncPlayerPosition_writes_teleportId() {
        SyncPlayerPosition p = new SyncPlayerPosition(0, 64, 0, 42);
        assertEquals(0x40, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertTrue(buf.readableBytes() > 0);
        buf.release();
    }

    @Test
    void setDefaultSpawn_writes() {
        SetDefaultSpawnPosition p = new SetDefaultSpawnPosition(0, 64, 0);
        assertEquals(0x54, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertTrue(buf.readableBytes() > 0);
        buf.release();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*PlayPacketsTest"`
Expected: 컴파일 실패 — play 패킷 클래스 없음.

- [ ] **Step 3: `LoginPlay.java` 작성**

```java
// S→C play 진입 패킷. 엔티티 id·차원·게임모드 등 세션 초기 상태를 알린다(1.20.5+ 포맷, NBT 없음).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/**
 * @param entityId        플레이어 엔티티 id
 * @param dimensionName   차원 식별자(예: "minecraft:overworld")
 * @param dimensionType   dimension_type 레지스트리 내 인덱스(VarInt)
 */
public record LoginPlay(int entityId, String dimensionName, int dimensionType) implements Packet {

    public static final int ID = 0x2B;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(entityId);          // Entity ID
        buf.writeBoolean(false);         // Is hardcore
        Types.writeVarInt(buf, 1);       // Dimension count
        Types.writeString(buf, dimensionName); // Dimension names[0]
        Types.writeVarInt(buf, 20);      // Max players (무시됨)
        Types.writeVarInt(buf, 10);      // View distance
        Types.writeVarInt(buf, 10);      // Simulation distance
        buf.writeBoolean(false);         // Reduced debug info
        buf.writeBoolean(true);          // Enable respawn screen
        buf.writeBoolean(false);         // Do limited crafting
        Types.writeVarInt(buf, dimensionType); // Dimension type (registry index)
        Types.writeString(buf, dimensionName); // Dimension name
        buf.writeLong(0L);               // Hashed seed
        buf.writeByte(1);                // Game mode (1 = creative)
        buf.writeByte(-1);               // Previous game mode (-1 = none)
        buf.writeBoolean(false);         // Is debug
        buf.writeBoolean(true);          // Is flat
        buf.writeBoolean(false);         // Has death location
        Types.writeVarInt(buf, 0);       // Portal cooldown
        buf.writeBoolean(false);         // Enforces secure chat
    }
}
```

> 1.21.1(767) 기준 필드 순서. `sea level`은 1.21.2부터라 **없다**. 게임모드 creative(1) + flat(true)로 빈 월드 시 화면이 자연스럽다.

- [ ] **Step 4: `SyncPlayerPosition.java` 작성**

```java
// S→C 플레이어 위치 동기화. 클라는 같은 teleportId로 Confirm Teleportation을 보내야 한다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SyncPlayerPosition(double x, double y, double z, int teleportId) implements Packet {

    public static final int ID = 0x40;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(0f);          // Yaw
        buf.writeFloat(0f);          // Pitch
        buf.writeByte(0);            // Flags (모두 절대값)
        Types.writeVarInt(buf, teleportId);
    }
}
```

- [ ] **Step 5: `SetDefaultSpawnPosition.java` 작성**

```java
// S→C 월드 기본 스폰 위치(나침반 기준점)
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record SetDefaultSpawnPosition(int x, int y, int z) implements Packet {

    public static final int ID = 0x54;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        // Position = (x<<38) | (z<<12 & 0x3FFFFFF) | (y & 0xFFF)
        long encoded = ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFF);
        buf.writeLong(encoded);
        buf.writeFloat(0f); // Angle
    }
}
```

- [ ] **Step 6: `ConnectionHandler`에 play 진입 채우기**

import 추가:
```java
import org.domi.furnace.server.net.protocol.play.LoginPlay;
import org.domi.furnace.server.net.protocol.play.SetDefaultSpawnPosition;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
```

switch에 추가:
```java
            case PLAY -> handlePlay(ctx, packetId, frame);
```

`onEnterPlay` 구현 교체 + `handlePlay` 추가:
```java
    private void onEnterPlay(ChannelHandlerContext ctx) {
        int dimIndex = Math.max(0,
                REGISTRIES.indexOf("minecraft:dimension_type", "minecraft:overworld"));
        send(ctx, new LoginPlay(1, "minecraft:overworld", dimIndex));
        send(ctx, new SyncPlayerPosition(0, 64, 0, 1));
        send(ctx, new SetDefaultSpawnPosition(0, 64, 0));
        // KeepAlive는 Task 10에서 시작.
    }

    private void handlePlay(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { /* Confirm Teleportation: teleportId 확인(현재는 무시) */ }
            default -> { /* 그 외 play 패킷은 M1에서 무시 */ }
        }
    }
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*PlayPacketsTest"`
Expected: PASS (3 tests).

- [ ] **Step 8: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): enter play (login play, sync position, default spawn)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: KeepAlive + Disconnect + 압축

play 진입 후 연결을 유지하려면 주기적 KeepAlive가 필수다(미응답 시 클라/서버가 끊음). 압축도 여기서 켠다.

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/KeepAliveClientbound.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/Disconnect.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/login/SetCompression.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/io/CompressionEncoder.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/io/CompressionDecoder.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/NetworkServer.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/io/CompressionTest.java`

### 10a — 압축 코덱

압축 활성 후 프레임 payload 포맷: `VarInt dataLength + (dataLength==0 ? 원본 : zlib압축)`. `dataLength`=압축 해제 후 크기, 0이면 비압축(임계값 미만이라 압축 안 함).

- [ ] **Step 1: 압축 라운드트립 테스트 작성**

`CompressionTest.java`:
```java
// 압축 인코더→디코더 라운드트립(임계값 위/아래 모두) 검증
package org.domi.furnace.server.net.io;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class CompressionTest {

    private static ByteBuf bytes(int n, int fill) {
        byte[] a = new byte[n];
        java.util.Arrays.fill(a, (byte) fill);
        return Unpooled.wrappedBuffer(a);
    }

    @Test
    void roundTrips_aboveThreshold() {
        int threshold = 16;
        EmbeddedChannel enc = new EmbeddedChannel(new CompressionEncoder(threshold));
        EmbeddedChannel dec = new EmbeddedChannel(new CompressionDecoder(threshold));

        enc.writeOutbound(bytes(100, 7));
        ByteBuf compressed = enc.readOutbound();
        dec.writeInbound(compressed);
        ByteBuf out = dec.readInbound();
        assertEquals(100, out.readableBytes());
        assertEquals(7, out.readByte());
        out.release();
    }

    @Test
    void roundTrips_belowThreshold() {
        int threshold = 256;
        EmbeddedChannel enc = new EmbeddedChannel(new CompressionEncoder(threshold));
        EmbeddedChannel dec = new EmbeddedChannel(new CompressionDecoder(threshold));

        enc.writeOutbound(bytes(10, 3));
        ByteBuf small = enc.readOutbound();
        dec.writeInbound(small);
        ByteBuf out = dec.readInbound();
        assertEquals(10, out.readableBytes());
        out.release();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*CompressionTest"`
Expected: 컴파일 실패 — 압축 코덱 없음.

- [ ] **Step 3: `CompressionEncoder.java` 작성**

```java
// 아웃바운드 프레임 payload를 임계값 이상이면 zlib 압축, 미만이면 원본+길이0으로 감싸는 인코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.Deflater;

public final class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {

    private final int threshold;
    private final Deflater deflater = new Deflater();

    public CompressionEncoder(int threshold) {
        this.threshold = threshold;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int dataLen = msg.readableBytes();
        if (dataLen < threshold) {
            Types.writeVarInt(out, 0); // 비압축 표시
            out.writeBytes(msg);
            return;
        }
        byte[] input = new byte[dataLen];
        msg.readBytes(input);
        Types.writeVarInt(out, dataLen); // 압축 해제 후 크기
        deflater.setInput(input);
        deflater.finish();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.writeBytes(buffer, 0, n);
        }
        deflater.reset();
    }
}
```

- [ ] **Step 4: `CompressionDecoder.java` 작성**

```java
// 인바운드 프레임 payload를 VarInt 길이로 판별해 zlib 해제(0이면 비압축)하는 디코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import java.util.zip.Inflater;
import org.domi.furnace.server.net.protocol.ProtocolException;

public final class CompressionDecoder extends ByteToMessageDecoder {

    private static final int MAX = 2 * 1024 * 1024;
    private final Inflater inflater = new Inflater();
    @SuppressWarnings("unused")
    private final int threshold;

    public CompressionDecoder(int threshold) {
        this.threshold = threshold;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!in.isReadable()) return;
        int dataLength = Types.readVarInt(in);
        if (dataLength == 0) {
            out.add(in.readRetainedSlice(in.readableBytes())); // 비압축
            return;
        }
        if (dataLength > MAX) {
            throw new ProtocolException("Decompressed size too large: " + dataLength);
        }
        byte[] compressed = new byte[in.readableBytes()];
        in.readBytes(compressed);
        inflater.setInput(compressed);
        byte[] result = new byte[dataLength];
        try {
            int n = inflater.inflate(result);
            if (n != dataLength) {
                throw new ProtocolException("Decompressed size mismatch: " + n + " != " + dataLength);
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new ProtocolException("Bad zlib data: " + e.getMessage());
        } finally {
            inflater.reset();
        }
        out.add(ctx.alloc().buffer(dataLength).writeBytes(result));
    }
}
```

- [ ] **Step 5: 압축 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*CompressionTest"`
Expected: PASS (2 tests).

### 10b — Set Compression + 파이프라인 동적 삽입

- [ ] **Step 6: `SetCompression.java` 작성**

```java
// S→C 압축 활성 패킷. 이후 모든 프레임은 압축 포맷을 쓴다(이 패킷 자체는 비압축으로 전송).
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetCompression(int threshold) implements Packet {

    public static final int ID = 0x03;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, threshold);
    }
}
```

- [ ] **Step 7: `ConnectionHandler`에 압축 임계값 전달 + Login Start에서 압축 활성**

`ConnectionHandler` 생성자/필드 수정 — 압축 임계값을 받게 함:
```java
    private final String serverName;
    private final int compressionThreshold;

    public ConnectionHandler(String serverName, int compressionThreshold) {
        this.serverName = serverName;
        this.compressionThreshold = compressionThreshold;
    }
```

import 추가:
```java
import org.domi.furnace.server.net.io.CompressionDecoder;
import org.domi.furnace.server.net.io.CompressionEncoder;
import org.domi.furnace.server.net.protocol.login.SetCompression;
```

`handleLogin`의 `case 0x00`(Login Start)에서 `send(LoginSuccess)` **직전**에 압축 배선 추가:
```java
            case 0x00 -> { // Login Start
                LoginStart start = LoginStart.read(frame);
                this.playerName = start.name();
                this.playerUuid = LoginStart.offlineUuid(start.name());
                if (compressionThreshold >= 0) {
                    send(ctx, new SetCompression(compressionThreshold)); // 비압축으로 나감
                    ctx.pipeline().addAfter("frameDecoder", "decompress",
                            new CompressionDecoder(compressionThreshold));
                    ctx.pipeline().addAfter("frameEncoder", "compress",
                            new CompressionEncoder(compressionThreshold));
                }
                send(ctx, new LoginSuccess(playerUuid, playerName));
            }
```

> 파이프라인 결과 순서: `frameDecoder, decompress, frameEncoder, compress, handler`.
> 인바운드(head→tail): frameDecoder → decompress → handler. ✓
> 아웃바운드(tail→head): handler → compress → frameEncoder. ✓ (압축 후 길이 prefix)
> Set Compression 패킷은 삽입 **전에** 전송되므로 비압축으로 나가고, 이후 패킷부터 압축 적용 — 프로토콜 규약과 일치.

- [ ] **Step 8: `NetworkServer`에서 ConnectionHandler에 임계값 전달**

`initChannel`의 handler 생성을 수정:
```java
                                .addLast("handler",
                                        new ConnectionHandler(serverName, compressionThreshold));
```

### 10c — KeepAlive + Disconnect

- [ ] **Step 9: `KeepAliveClientbound.java` 작성**

```java
// S→C 연결 유지 핑. 클라는 같은 id로 Keep Alive(serverbound 0x18)를 되돌려야 한다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record KeepAliveClientbound(long id) implements Packet {

    public static final int ID = 0x27;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeLong(id);
    }
}
```

- [ ] **Step 10: `Disconnect.java` 작성**

```java
// S→C play 단계 연결 종료. 사유 텍스트를 JSON 컴포넌트가 아닌 NBT 텍스트로 보낸다... 는 1.20.3+.
// M1은 단순화를 위해 사용하지 않고 채널만 닫는다(아래 주석 참조). 클래스는 향후 확장용 자리만 잡는다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record Disconnect() implements Packet {

    public static final int ID = 0x1D;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        // 1.20.3+ play Disconnect는 NBT 텍스트 컴포넌트를 요구한다.
        // M1은 NBT 미구현이라 이 패킷을 보내지 않고 ctx.close()로 끊는다.
        // (Task 11 이후 NBT 도입 시 사유 텍스트를 채운다.)
        throw new UnsupportedOperationException("M1: NBT 미구현 — ctx.close() 사용");
    }
}
```

> M1에서 명시적 Disconnect 패킷은 보내지 않는다(NBT 의존). 연결 종료는 `ctx.close()`로 처리한다. `Disconnect` 클래스는 ID/자리만 잡아 둔다.

- [ ] **Step 11: `ConnectionHandler`에 KeepAlive 시작/응답 + 미응답 종료**

import 추가:
```java
import io.netty.channel.ChannelHandlerContext;
import org.domi.furnace.server.net.protocol.play.KeepAliveClientbound;
import org.domi.furnace.api.sched.FurnaceScheduler;
import java.util.concurrent.atomic.AtomicBoolean;
```

생성자에 스케줄러 주입(KeepAlive 타이밍용). 생성자/필드 수정:
```java
    private final String serverName;
    private final int compressionThreshold;
    private final FurnaceScheduler scheduler;

    private volatile ChannelHandlerContext ctx;
    private final AtomicBoolean awaitingKeepAlive = new AtomicBoolean(false);
    private volatile long lastKeepAliveId;

    public ConnectionHandler(String serverName, int compressionThreshold, FurnaceScheduler scheduler) {
        this.serverName = serverName;
        this.compressionThreshold = compressionThreshold;
        this.scheduler = scheduler;
    }
```

`channelRead0` 첫 줄에서 ctx 보관(eventLoop 재진입용):
```java
        this.ctx = ctx;
```

`onEnterPlay` 끝에 KeepAlive 시작 추가:
```java
        scheduleKeepAlive();
```

메서드 추가:
```java
    private void scheduleKeepAlive() {
        // 타이밍은 global 스케줄러로(설계 결정), 실제 전송/검사는 채널 eventLoop로 되돌림.
        scheduler.global().executeDelayed(() -> {
            if (ctx == null || !ctx.channel().isActive()) return;
            ctx.channel().eventLoop().execute(this::sendKeepAlive);
        }, 300); // 15초(300틱)
    }

    private void sendKeepAlive() {
        if (awaitingKeepAlive.get()) {
            // 직전 KeepAlive 미응답 → 타임아웃 종료
            log.info("KeepAlive 응답 없음, 종료: {}", playerName);
            ctx.close();
            return;
        }
        lastKeepAliveId = System.nanoTime();
        awaitingKeepAlive.set(true);
        send(ctx, new KeepAliveClientbound(lastKeepAliveId));
        scheduleKeepAlive(); // 다음 주기 재예약
    }
```

`handlePlay`에 KeepAlive 응답 처리 추가(serverbound Keep Alive = `0x18`):
```java
    private void handlePlay(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { /* Confirm Teleportation */ }
            case 0x18 -> { // Keep Alive (serverbound)
                long echoed = frame.readLong();
                if (echoed == lastKeepAliveId) {
                    awaitingKeepAlive.set(false);
                }
            }
            default -> { /* 그 외 무시 */ }
        }
    }
```

- [ ] **Step 12: `NetworkServer`에 스케줄러 주입**

`NetworkServer` 필드/생성자에 `FurnaceScheduler scheduler` 추가:
```java
    private final FurnaceScheduler scheduler;

    public NetworkServer(int port, int compressionThreshold, String serverName, FurnaceScheduler scheduler) {
        this.port = port;
        this.compressionThreshold = compressionThreshold;
        this.serverName = serverName;
        this.scheduler = scheduler;
    }
```

import 추가:
```java
import org.domi.furnace.api.sched.FurnaceScheduler;
```

`initChannel` handler 생성 수정:
```java
                                .addLast("handler",
                                        new ConnectionHandler(serverName, compressionThreshold, scheduler));
```

- [ ] **Step 13: 기존 테스트의 NetworkServer/ConnectionHandler 생성자 업데이트**

`NetworkServerTest`와 `StatusPingTest`의 `new NetworkServer(0, -1, "Furnace Server")` 호출에 스케줄러 인자를 추가한다. 두 테스트 모두 status/연결만 검증하므로 스케줄러는 실제로 안 쓰이지만 생성자 시그니처를 맞춰야 한다. 각 테스트에 import + 생성/정리 추가:

```java
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
```

`NetworkServer` 생성 부분 교체(두 테스트 동일 패턴):
```java
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L);
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server", sched);
```

그리고 각 테스트 `finally`에서 `server.stop();` 다음 줄에 추가:
```java
            sched.close();
```

- [ ] **Step 14: `FurnaceMain` 의 NetworkServer 생성에 스케줄러 전달**

`new NetworkServer(...)` 호출 수정:
```java
        NetworkServer network = new NetworkServer(
                config.port(), config.compressionThreshold(), config.serverName(), scheduler);
```

- [ ] **Step 15: 전체 빌드 + 테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 모든 테스트 통과(M0 + M1: Config, Types, Framing, NetworkServer, StatusPing, LoginPackets, RegistrySet, RegistryData, PlayPackets, Compression).

- [ ] **Step 16: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): keepalive, connection timeout, and packet compression" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 11: 수동 검증 — 바닐라 1.21.1 클라 접속 (M1 최종 verify)

이 Task는 코드를 추가하지 않고 설계서 M1 verify("바닐라 1.21.1 클라가 접속→play 도달→빈 월드서 끊기지 않고 유지")를 실제 클라로 확인한다.

- [ ] **Step 1: 레지스트리 리소스 실데이터로 교체**

Task 7 Step 1의 데이터 생성물(`registries/1.21.1.txt` 전체 레지스트리)로 부트스트랩 최소본을 교체한다. (overworld 한 줄만으론 클라가 다른 레지스트리 누락으로 끊을 수 있음.)

- [ ] **Step 2: 서버 기동**

Run: `./gradlew :furnace-server:run`
Expected 로그:
- `Furnace 부팅: name='Furnace Server', ...`
- `네트워크 서버 listen: 포트 25565 ...`
- `Furnace 기동 완료. ...`

- [ ] **Step 3: 바닐라 1.21.1 클라로 접속**

1. 마인크래프트 자바 1.21.1 실행 → 멀티플레이어 → 서버 추가 `localhost`.
2. 서버 목록에 "Furnace Server" / 1.21.1 / 0 또는 1/20 표시 확인(status).
3. 접속(Join Server).

Expected:
- 로그인 통과(오프라인) → configuration → play 진입.
- 화면: "Loading terrain..." 또는 빈(void/flat) 화면. **청크 미전송(M2)이라 지형은 안 보이는 게 정상.**
- **15초 이상 끊기지 않고 유지**(KeepAlive 동작). 서버 로그에 KeepAlive 미응답 종료가 안 떠야 함.

- [ ] **Step 4: 끊김/누락 디버깅(필요 시)**

- 클라가 즉시 끊기고 "Missing registry minecraft:xxx" → `registries/1.21.1.txt`에 해당 레지스트리 추가.
- play 직전 끊김 → Login(play) 필드 순서/타입을 Minecraft Wiki 767 문서와 대조(특히 게임모드 UByte, dimensionType VarInt 인덱스).
- 패킷 id 의심 → 해당 패킷 클래스 `ID` 상수를 767 문서로 검증.
- 로그 레벨을 DEBUG로 올려(`logback.xml` root level=DEBUG) 미처리 패킷 id 확인.

- [ ] **Step 5: 검증 통과 후 정리**

`logback.xml`을 DEBUG로 바꿨다면 INFO로 되돌린다.

- [ ] **Step 6: 커밋** (실데이터 리소스 반영)

```bash
git add furnace-server
git commit -m "feat(net): ship full 1.21.1 registry entry list; M1 client connect verified" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## M1 완료 기준 (Definition of Done)

설계서 M1 verify 매핑:
- ✅ **Netty 파이프라인, VarInt, 프레이밍/압축** → Task 2(Types/VarInt), 3(Framing), 10(Compression).
- ✅ **프로토콜 767 상태: handshake/status/login/configuration/play** → Task 5/6/8/9.
- ✅ **서버 리스트 핑(status)** → Task 5 (`StatusPingTest` 자동 + 수동).
- ✅ **로그인(오프라인), configuration, play 전이** → Task 6/8/9.
- ✅ **KeepAlive, 디스커넥트** → Task 10 (KeepAlive 주기 전송/미응답 종료).
- ✅ **바닐라 1.21.1 클라 접속→play 도달→유지** → Task 11 수동 verify.

## Self-Review 결과

**1. Spec 커버리지(설계서 §M1).** 모든 항목이 Task에 매핑됨(위 DoD). configuration 단계의 레지스트리 동기화(1.20.5+ 필수)는 spec에 명시 안 됐지만 play 도달의 전제라 Task 7/8로 포함 — 누락 시 클라가 못 들어옴.

**2. Placeholder 스캔.** 모든 코드 단계에 실제 코드 포함. registry 리소스(`1.21.1.txt`)만 외부 데이터 생성이 필요 — Task 7에 생성 명령 + 부트스트랩 최소본 명시(빈칸 아님). Disconnect는 NBT 의존이라 M1 범위 밖임을 명시하고 `ctx.close()` 대체를 못박음(placeholder 아닌 의도적 범위 결정).

**3. 타입 일관성.** `Packet`(id()/write(ByteBuf)) 전 clientbound 패킷이 구현. `Types`(readVarInt/writeVarInt/readString/writeString/readUuid/writeUuid) 전반 일관. `ConnectionHandler` 생성자는 Task 4(1인자)→Task 10(3인자: serverName, compressionThreshold, scheduler)로 진화 — Task 10 Step 8/13에서 모든 호출처(NetworkServer, 두 테스트) 동기 업데이트 명시. `NetworkServer` 생성자도 Task 4(3인자)→Task 10(4인자)로 진화, Step 12/13/14에서 호출처 전부 갱신. `RegistrySet`(load/registryKeys/entries/indexOf)·`RegistryData`(registryId/entries)·play 패킷 ID 상수 일관.

**알려진 위험(설계서 §7 프로토콜 정확도).** play/config 단계 패킷 ID(0x2B, 0x40, 0x54, 0x27, 0x18, 0x0E 등)와 Login(play) 필드 순서는 767 기준으로 작성했으나, 한 곳이라도 틀리면 클라가 침묵 종료한다. 각 ID를 클래스 상수로 모았고, Task 11이 실제 클라로 최종 검증한다. 자동 테스트는 직렬화 구조/라운드트립까지만 보장하고 "ID·필드가 767과 일치"는 보장 못 한다 — 그래서 Task 11이 필수 게이트다.

**범위 결정.** NBT 인코딩을 M1에서 회피(known-packs 무데이터 + Login(play) 1.20.5+ 포맷 + Disconnect 미사용). NBT는 M2(청크/하트빔) 또는 채팅/디스커넥트 사유가 필요할 때 도입. 이로써 M1이 "play 도달+유지"라는 작은 승리에 집중한다.
