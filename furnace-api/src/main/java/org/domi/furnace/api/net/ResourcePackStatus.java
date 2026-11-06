// 클라이언트가 리소스 팩 요청에 보낸 응답 상태(마인크래프트 프로토콜의 값 이름을 따르되 번호는 드러내지 않는다).
package org.domi.furnace.api.net;

public enum ResourcePackStatus {
    SUCCESSFULLY_LOADED(true),
    DECLINED(true),
    FAILED_DOWNLOAD(true),
    /** 요청을 받아들여 내려받기 시작함(중간 상태 — 이어서 끝 상태가 온다). */
    ACCEPTED(false),
    /** 내려받기가 끝나 적용 중(중간 상태). */
    DOWNLOADED(false),
    INVALID_URL(true),
    FAILED_RELOAD(true),
    DISCARDED(true);

    private final boolean terminal;

    ResourcePackStatus(boolean terminal) { this.terminal = terminal; }

    /** 끝 상태인가. {@code sendResourcePack} 의 Future 는 끝 상태에서만 완료된다(ACCEPTED·DOWNLOADED 는 중간 상태라 건너뛴다). */
    public boolean isTerminal() { return terminal; }

    /** 팩이 클라이언트에 적용됐는가. */
    public boolean isSuccess() { return this == SUCCESSFULLY_LOADED; }
}
