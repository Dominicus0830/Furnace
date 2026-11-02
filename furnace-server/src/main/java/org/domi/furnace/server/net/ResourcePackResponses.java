// 리소스 팩 응답(serverbound resource_pack: 구성 0x06 / play 0x2B) 해석 — 바닐라 ServerboundResourcePackPacket.Action.
package org.domi.furnace.server.net;

public final class ResourcePackResponses {
    private ResourcePackResponses() { }

    /** 바닐라 Action 순서: 0 SUCCESSFULLY_LOADED, 1 DECLINED, 2 FAILED_DOWNLOAD, 3 ACCEPTED, 4 DOWNLOADED, 5 INVALID_URL, 6 FAILED_RELOAD, 7 DISCARDED. */
    public static final int DECLINED = 1;
    public static final int ACCEPTED = 3;
    public static final int DOWNLOADED = 4;
    public static final int ACTION_COUNT = 8;

    /** Action.isTerminal: ACCEPTED·DOWNLOADED 만 중간 상태다(구성 작업은 끝 상태에서만 이어진다). */
    public static boolean isTerminal(int action) {
        return action != ACCEPTED && action != DOWNLOADED;
    }

    /** 필수 팩을 거절(DECLINED)하면 쫓아낸다 — 바닐라는 이 상태 하나만 본다(다운로드 실패·잘못된 URL 은 그대로 통과). */
    public static boolean kicksRequired(int action, boolean required) {
        return required && action == DECLINED;
    }
}
