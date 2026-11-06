// 연결이 끊긴 이유. PlayerQuitEvent.reason() 와 PlayerConnection.quitReason() 이 같은 값을 준다.
// 이름은 마인크래프트 버전과 무관한 개념이다(새 버전이 이유를 늘리면 여기에 더한다).
package org.domi.furnace.api.net;

public enum QuitReason {
    /** 클라이언트가 스스로 나갔다(메뉴에서 나가기·창 닫기·네트워크 끊김) — 서버가 끊은 게 아니다. */
    DISCONNECTED,
    /** 서버가 사유를 보여 주고 내보냈다(/kick·밴·허용 목록·중복 로그인·플러그인 kick·자리비움·스팸·검증 실패 등). */
    KICKED,
    /** 시간 초과: 읽기 타임아웃(keep-alive 에 답이 없음)이나 접속 단계 시간 제한. */
    TIMED_OUT,
    /** 잘못된 패킷·디코드 실패·속도 제한 초과 등 연결 상태가 올바르지 않아 서버가 그냥 닫았다. */
    ERRONEOUS_STATE,
    /** 서버가 꺼지는 중이라 내보냈다(관리자 킥과 구분하려고 따로 둔다). */
    SERVER_SHUTDOWN
}
