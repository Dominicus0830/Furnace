// 바닐라 RconClient 의 원격 콘솔(Source RCON) 패킷 형식. 길이·요청 id·종류는 리틀엔디언 int, 본문은 UTF-8 + NUL 2개.
// 요청은 최대 1460 바이트(바닐라 버퍼 크기), 응답 본문은 4096 글자씩 나눠 보낸다.
package org.domi.furnace.server.remote;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class RconProtocol {

    private RconProtocol() {}

    public static final int TYPE_RESPONSE = 0;
    public static final int TYPE_COMMAND = 2;
    /** 로그인 요청 종류. */
    public static final int TYPE_LOGIN = 3;
    /** 인증 응답 종류(성공은 요청 id, 실패는 -1). */
    public static final int TYPE_AUTH_RESPONSE = 2;
    public static final int AUTH_FAILED_ID = -1;
    /** 길이 필드(4바이트) 뒤 본문의 최대 바이트 수 = 바닐라 수신 버퍼 1460 - 4. */
    public static final int MAX_PACKET_BODY = 1460 - 4;
    /** 응답 한 패킷의 본문 글자 수 상한. */
    public static final int MAX_RESPONSE_CHARS = 4096;

    /** 해석한 요청. */
    public record Request(int id, int type, String payload) {}

    /** 서버가 보낼 패킷 하나(길이 필드 포함 전체 바이트). */
    public static byte[] encode(int id, int type, String payload) {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(body.length + 14).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(body.length + 10).putInt(id).putInt(type).put(body).put((byte) 0).put((byte) 0);
        return b.array();
    }

    /** 긴 응답을 4096 글자씩 나눈 패킷들(바닐라 do-while: 빈 응답도 빈 패킷 하나). */
    public static byte[][] encodeResponse(int id, String text) {
        int n = Math.max(1, (text.length() + MAX_RESPONSE_CHARS - 1) / MAX_RESPONSE_CHARS);
        byte[][] out = new byte[n][];
        for (int i = 0; i < n; i++) {
            int from = Math.min(i * MAX_RESPONSE_CHARS, text.length());
            out[i] = encode(id, TYPE_RESPONSE, text.substring(from, Math.min(from + MAX_RESPONSE_CHARS, text.length())));
        }
        return out;
    }

    /** 길이 필드 값이 받아들일 수 있는 범위인가(id 4 + 종류 4 + NUL 2 = 최소 10). */
    public static boolean validLength(int length) {
        return length >= 10 && length <= MAX_PACKET_BODY;
    }

    /**
     * 길이 필드를 뺀 본문(id, 종류, 문자열, NUL…)을 해석한다. 바닐라 ave.a(): 문자열은 첫 NUL 까지(없으면 마지막 바이트 직전까지).
     * 형식에 맞지 않으면 null.
     */
    public static Request decode(byte[] body) {
        if (body.length < 10) return null;
        ByteBuffer b = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        int id = b.getInt(0), type = b.getInt(4);
        int end = 8, limit = body.length - 1;
        while (end < limit && body[end] != 0) end++;
        return new Request(id, type, new String(body, 8, end - 8, StandardCharsets.UTF_8));
    }
}
