// 이식 다리가 줄을 돌리지 않기로 한 경우(짝 형식이 틀림, 검사가 거절) — 호환 스텁이 UnsupportedNmsCall 로 바꿔 던진다.
package org.domi.furnace.api.internal.port;

/**
 * 서버 쪽 생성 구현·kind 변환이 "이 호출은 이 줄로 돌릴 수 없다"고 알릴 때 던진다. 조용히 다른 값을 내지 않는다(설계 3.1).
 * 호환 쪽 생성 스텁이 받아 플러그인에게는 바닐라 이름이 든 {@code UnsupportedNmsCall} 로 보인다.
 */
public final class PortRefused extends RuntimeException {

    private final String what;

    /** what: 이식 줄의 이름(오류 문구용), reason: 왜 거절했는가. */
    public PortRefused(String what, String reason) {
        super(what + ": " + reason);
        this.what = what;
    }

    /** 이식 줄의 이름. */
    public String what() { return what; }

    /** 거절 이유(이름을 뺀 부분). */
    public String reason() { return getMessage().substring(what.length() + 2); }
}
