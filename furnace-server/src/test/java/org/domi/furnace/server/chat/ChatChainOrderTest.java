// T-035: 한 연결에서 채팅·서명 없는 명령·서명 채팅·서명 명령 20개를 한꺼번에 보내고, 가운데에 채팅 세션 갱신을 끼운다.
// 채팅은 채팅 실행기에서, 명령은 전역 스레드에서 돌지만 연결별 사슬이 도착 순서를 지킨다 — 받는 쪽 순서가 보낸 순서와 같고,
// 세션 갱신 뒤의 서명 채팅은 새 세션으로 풀린다(순서가 엇갈리면 서명 오류·out-of-order 로 빠진다).
package org.domi.furnace.server.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.NetworkServer;
import org.domi.furnace.server.net.ServerLoginContext;
import org.domi.furnace.server.net.TestClient;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatChainOrderTest {

    @TempDir Path tmp;

    private static void unsignedChat(TestClient c, String text) throws Exception {
        c.write(0x06, x -> { Types.writeString(x, text); x.writeLong(System.currentTimeMillis()); x.writeLong(0); x.writeBoolean(false);
            Types.writeVarInt(x, 0); x.writeBytes(new byte[3]); });
    }

    private static void unsignedCommand(TestClient c, String command) throws Exception {
        c.write(0x04, x -> Types.writeString(x, command));
    }

    @Test
    void mixedChatCommandsAndSessionUpdateKeepArrivalOrder() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        NetworkServer server = null;
        KeyPair services = SignedChatTest.rsa(2048), playerKey = SignedChatTest.rsa(2048);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            core.servicesKeys().setProfileKeys(List.of(services.getPublic()));
            core.settings().update(p -> { var q = p.cloneProperties(); q.put("online-mode", "false"); return q; });
            server = new NetworkServer(0, -1, ServerLoginContext.offline("t").core(core), sched, core.world(), new EntityIdAllocator());
            int port = server.start();
            core.lists().op(GameProfile.offline("Alice"), 4);   // 20개를 몰아 보내도 스팸 킥이 없게(바닐라: op 제외)
            try (TestClient a = TestClient.join(port, "Alice"); TestClient b = TestClient.join(port, "Bob")) {
                a.await(0x6C, x -> {
                    byte[] raw = new byte[x.readableBytes()];
                    x.readBytes(raw);
                    String s = new String(raw, StandardCharsets.ISO_8859_1);
                    return s.contains("multiplayer.player.joined") && s.contains("Bob");
                });
                UUID session = UUID.randomUUID();
                ProfilePublicKey.Data key = SignedChatTest.profileKey(services, playerKey, a.uuid, Instant.now().plusSeconds(3600));
                SignedChatTest.ClientSigner signer = new SignedChatTest.ClientSigner(playerKey.getPrivate(), a.uuid, session);
                long base = System.currentTimeMillis();

                List<String> sent = new ArrayList<>();
                // 세션 전: 서명 없는 채팅과 명령을 번갈아
                for (int i = 0; i < 7; i++) {
                    String t = (i % 2 == 0 ? "u" : "c") + i;
                    if (i % 2 == 0) unsignedChat(a, t);
                    else unsignedCommand(a, "say <" + t + ">");
                    sent.add(t);
                }
                // 가운데: 채팅 세션 갱신 — 뒤따르는 서명 채팅은 이 세션으로 풀려야 한다
                a.write(0x07, x -> new RemoteChatSession.Data(session, key).write(x));
                for (int i = 7; i < 20; i++) {
                    String t = "s" + i;
                    Instant ts = Instant.ofEpochMilli(base + i);
                    long salt = 100 + i;
                    switch (i % 4) {
                        case 0 -> {   // 서명 없는 명령
                            t = "c" + i;
                            unsignedCommand(a, "say <" + t + ">");
                        }
                        case 2 -> {   // 서명 명령 /msg Bob <서명된 인자>
                            MessageSignature sig = signer.sign(new SignedMessageBody(t, ts, salt, LastSeenMessages.EMPTY));
                            String arg = t;
                            a.write(0x05, x -> { Types.writeString(x, "msg Bob " + arg); x.writeLong(ts.toEpochMilli()); x.writeLong(salt);
                                Types.writeVarInt(x, 1); Types.writeString(x, "message"); sig.write(x); Types.writeVarInt(x, 0); x.writeBytes(new byte[3]); });
                        }
                        default -> {   // 서명 채팅
                            MessageSignature sig = signer.sign(new SignedMessageBody(t, ts, salt, LastSeenMessages.EMPTY));
                            String text = t;
                            a.write(0x06, x -> { Types.writeString(x, text); x.writeLong(ts.toEpochMilli()); x.writeLong(salt); x.writeBoolean(true);
                                sig.write(x); Types.writeVarInt(x, 0); x.writeBytes(new byte[3]); });
                        }
                    }
                    sent.add(t);
                }

                // 서명 채팅·서명 명령은 player_chat(0x39), 서명 없는 /say 는 바닐라처럼 시스템 메시지로 감싼 disguised_chat(0x1E) 으로 온다
                List<String> got = new ArrayList<>();
                List<Boolean> signed = new ArrayList<>();
                while (got.size() < sent.size()) {
                    io.netty.buffer.ByteBuf f;
                    try {
                        f = b.read();
                    } catch (java.io.IOException e) {
                        throw new AssertionError("Bob 이 " + sent.size() + "개를 다 받지 못했다. 받은 것: " + got, e);
                    }
                    int id = Types.readVarInt(f);
                    if (id == 0x39) {
                        SignedChatFlowTest.Chat c = SignedChatFlowTest.readChat(f);
                        got.add(c.content());
                        signed.add(c.signed());
                    } else if (id == 0x1E) {
                        byte[] raw = new byte[f.readableBytes()];
                        f.readBytes(raw);
                        String s = new String(raw, StandardCharsets.ISO_8859_1);
                        String hit = null;
                        for (String t : sent) if (t.startsWith("c") && s.contains("<" + t + ">")) hit = t;
                        got.add(hit == null ? "?disguised" : hit);
                        signed.add(false);
                    }
                }
                assertEquals(sent, got, "Bob 이 받은 차례 = Alice 가 보낸 차례");
                for (int i = 0; i < sent.size(); i++) {
                    if (sent.get(i).startsWith("s")) assertTrue(signed.get(i), "세션 갱신 뒤 서명 채팅·서명 명령은 서명된 채 간다: " + sent.get(i));
                }
            }
        } finally {
            if (server != null) server.stop();
            sched.close();
        }
    }
}
