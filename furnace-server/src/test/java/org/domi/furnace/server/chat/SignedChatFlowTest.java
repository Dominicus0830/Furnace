// 실제 소켓 두 명: 비서명 채팅 → 채팅 세션 등록 → 서명 채팅(last seen 확인) → 서명된 /msg → 변조 거부.
package org.domi.furnace.server.chat;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.NetworkServer;
import org.domi.furnace.server.net.ServerLoginContext;
import org.domi.furnace.server.net.TestClient;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SignedChatFlowTest {

    @TempDir Path tmp;

    record Chat(UUID sender, int index, boolean signed, String content, int chatType, int lastSeenCount) {}

    static Chat readChat(ByteBuf b) {
        UUID sender = Types.readUuid(b);
        int index = Types.readVarInt(b);
        boolean signed = b.readBoolean();
        if (signed) b.skipBytes(256);
        String content = Types.readString(b, 256);
        b.readLong();
        b.readLong();
        int n = Types.readVarInt(b);
        for (int i = 0; i < n; i++) if (Types.readVarInt(b) == 0) b.skipBytes(256);
        if (b.readBoolean()) throw new AssertionError("unsigned content 없음이 기대됨");
        assertEquals(0, Types.readVarInt(b), "filter pass-through");
        int type = Types.readVarInt(b) - 1;
        return new Chat(sender, index, signed, content, type, n);
    }

    @Test
    void chatFlow() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        NetworkServer server = null;
        KeyPair services = SignedChatTest.rsa(2048), playerKey = SignedChatTest.rsa(2048);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            core.servicesKeys().setProfileKeys(List.of(services.getPublic()));
            // 오프라인 서버: secure chat 강제 없음(비서명 채팅 허용) — 서명이 오면 그대로 검증한다.
            core.settings().update(p -> { var q = p.cloneProperties(); q.put("online-mode", "false"); return q; });
            assertFalse(core.enforceSecureProfile());
            List<String> console = new java.util.concurrent.CopyOnWriteArrayList<>();   // 서버 스레드가 쓰고 테스트 스레드가 읽는다
            core.addConsoleListener(t -> console.add(t.getString()));
            server = new NetworkServer(0, -1, ServerLoginContext.offline("t").core(core), sched, core.world(), new EntityIdAllocator());
            int port = server.start();
            int chatType = ChatType.CHAT.registryIndex();
            try (TestClient a = TestClient.join(port, "Alice"); TestClient b = TestClient.join(port, "Bob")) {
                // 1) 세션 없는 채팅: 서명 없이 전달, 로그는 [Not Secure]
                a.write(0x06, x -> { Types.writeString(x, "hello"); x.writeLong(System.currentTimeMillis()); x.writeLong(0); x.writeBoolean(false);
                    Types.writeVarInt(x, 0); x.writeBytes(new byte[3]); });
                Chat c1 = readChat(b.await(0x39));
                assertEquals(new Chat(a.uuid, 0, false, "hello", chatType, 0), c1);
                assertEquals("hello", readChat(a.await(0x39)).content(), "보낸 사람도 자기 채팅을 받는다");
                for (int i = 0; i < 500 && !console.contains("[Not Secure] <Alice> hello"); i++) Thread.sleep(20);   // 로그는 브로드캐스트 뒤에 남을 수 있다
                assertTrue(console.contains("[Not Secure] <Alice> hello"), console.toString());

                // 2) 채팅 세션 등록 → Bob 이 initialize_chat 를 받는다
                UUID session = UUID.randomUUID();
                ProfilePublicKey.Data key = SignedChatTest.profileKey(services, playerKey, a.uuid, Instant.now().plusSeconds(3600));
                a.write(0x07, x -> new RemoteChatSession.Data(session, key).write(x));
                b.await(0x3E, x -> x.readByte() == 0x02);

                // 3) 서명된 채팅
                SignedChatTest.ClientSigner signer = new SignedChatTest.ClientSigner(playerKey.getPrivate(), a.uuid, session);
                Instant t1 = Instant.ofEpochMilli(System.currentTimeMillis());
                SignedMessageBody body1 = new SignedMessageBody("signed hi", t1, 11L, LastSeenMessages.EMPTY);
                MessageSignature s1 = signer.sign(body1);
                a.write(0x06, x -> { Types.writeString(x, "signed hi"); x.writeLong(t1.toEpochMilli()); x.writeLong(11L); x.writeBoolean(true);
                    s1.write(x); Types.writeVarInt(x, 0); x.writeBytes(new byte[3]); });
                Chat c2 = readChat(b.await(0x39, x -> Types.readVarInt(x.skipBytes(16)) >= 0 && true));
                while (!c2.content().equals("signed hi")) c2 = readChat(b.await(0x39));
                assertTrue(c2.signed());
                assertEquals(0, c2.index());
                a.await(0x39); // 앨리스도 자기 메시지를 받는다(서명 추적 시작)
                for (int i = 0; i < 500 && !console.contains("<Alice> signed hi"); i++) Thread.sleep(20);   // 부하 시 콘솔 로그가 늦다: 최대 10초
                assertTrue(console.contains("<Alice> signed hi"), "서명된 채팅은 [Not Secure] 없이 로그: " + console);

                // 4) 받은 서명을 확인(창 1칸 이동 + 마지막 칸 확인)하며 다음 메시지
                Instant t2 = Instant.ofEpochMilli(System.currentTimeMillis() + 5);
                SignedMessageBody body2 = new SignedMessageBody("again", t2, 12L, new LastSeenMessages(List.of(s1)));
                MessageSignature s2 = signer.sign(body2);
                BitSet ack = new BitSet();
                ack.set(19);
                byte[] ackBytes = java.util.Arrays.copyOf(ack.toByteArray(), 3);
                a.write(0x06, x -> { Types.writeString(x, "again"); x.writeLong(t2.toEpochMilli()); x.writeLong(12L); x.writeBoolean(true);
                    s2.write(x); Types.writeVarInt(x, 1); x.writeBytes(ackBytes); });
                Chat c3 = readChat(b.await(0x39));
                assertEquals("again", c3.content());
                assertEquals(1, c3.index());
                assertEquals(1, c3.lastSeenCount());
                a.await(0x39);

                // 5) 서명된 /msg Bob secret
                Instant t3 = Instant.ofEpochMilli(System.currentTimeMillis() + 10);
                // 실제 클라이언트처럼 이미 확인한 s1 과 새로 받은 s2 를 계속 확인한다(창 1칸 이동, 비트 18·19).
                SignedMessageBody argBody = new SignedMessageBody("secret", t3, 13L, new LastSeenMessages(List.of(s1, s2)));
                MessageSignature s3 = signer.sign(argBody);
                BitSet ack2 = new BitSet();
                ack2.set(18);
                ack2.set(19);
                byte[] ack2Bytes = java.util.Arrays.copyOf(ack2.toByteArray(), 3);
                a.write(0x05, x -> { Types.writeString(x, "msg Bob secret"); x.writeLong(t3.toEpochMilli()); x.writeLong(13L);
                    Types.writeVarInt(x, 1); Types.writeString(x, "message"); s3.write(x); Types.writeVarInt(x, 1); x.writeBytes(ack2Bytes); });
                Chat incoming = readChat(b.await(0x39));
                assertEquals("secret", incoming.content());
                assertTrue(incoming.signed());
                assertEquals(ChatType.MSG_COMMAND_INCOMING.registryIndex(), incoming.chatType());
                Chat outgoing = readChat(a.await(0x39));
                assertEquals(ChatType.MSG_COMMAND_OUTGOING.registryIndex(), outgoing.chatType());

                // 6) 변조된 서명 → 앨리스에게 빨간 오류 시스템 메시지
                Instant t4 = Instant.ofEpochMilli(System.currentTimeMillis() + 20);
                MessageSignature bad = signer.sign(new SignedMessageBody("original", t4, 14L, LastSeenMessages.EMPTY));
                // /msg 결과(수신·발신 서명 메시지)는 앨리스에게 발신분 하나만 온다 — 확인 상태는 그대로 유지(s1·s2), 새 발신분은 무시.
                BitSet ack3 = new BitSet();
                ack3.set(17);
                ack3.set(18);
                byte[] ack3Bytes = java.util.Arrays.copyOf(ack3.toByteArray(), 3);
                a.write(0x06, x -> { Types.writeString(x, "forged"); x.writeLong(t4.toEpochMilli()); x.writeLong(14L); x.writeBoolean(true);
                    bad.write(x); Types.writeVarInt(x, 1); x.writeBytes(ack3Bytes); });
                ByteBuf sys = a.await(0x6C);
                byte[] raw = new byte[sys.readableBytes()];
                sys.readBytes(raw);
                assertTrue(new String(raw, StandardCharsets.ISO_8859_1).contains("chat.disabled.invalid_signature"));
            }
        } finally {
            if (server != null) server.stop();
            sched.close();
        }
    }
}
