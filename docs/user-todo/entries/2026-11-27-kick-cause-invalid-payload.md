# 킥 원인 INVALID_PAYLOAD — 바닐라·Paper 와 다른 점 (2026-11-27, 코디네이터 확정 필요 없음, 기록만)

**쉬운 말:** 플러그인이 "누가 왜 쫓겨났는지"(PlayerKickEvent.getCause)를 보는데, Paper 는 클라이언트가 보낸 custom_payload 중
register/unregister·brand 처리가 잘못되면 INVALID_PAYLOAD 원인으로 쫓아냅니다. Furnace 는 크거나 깨진 custom_payload 를 **쫓아내지 않고 버리기만** 해서
이 원인이 나올 일이 없습니다(가정: Paper 1.21.1 소스 기준, 실서버 관측은 안 함).

**안 하면?** 이런 이상한 패킷을 보내는 클라이언트가 Paper 에서는 쫓겨나는데 Furnace 에서는 그냥 무시됩니다. 서버 안전에는 영향이 없고, 쫓겨나는 것을 기록하는 안티치트류만 차이를 봅니다.

**추천:** 지금은 두기. 이 차이를 닫으려면 custom_payload 검사 실패 지점에 `kickWithCause(..., INVALID_PAYLOAD)` 를 붙이면 됩니다(ConnectionHandler 의 custom_payload 처리).

**같이 기록(이번 PR):**
- 시간 초과(keepalive)·금지 문자·채팅 검증 실패·만료/잘못된 서명 키·잘못된 이동(플레이어·탈것)·필수 리소스팩 거절은 이제 PlayerKickEvent(원인)로 나가며 플러그인이 취소할 수 있다.
- 중복 로그인은 DUPLICATE_LOGIN 이벤트를 전역 스레드에서 낸다. 취소되면 새 접속은 같은 계정 이중 접속을 막기 위해 로그인을 마치지 않고 `slow_login` 문구로 끊는다(Paper 는 옛 접속이 나갈 때까지 기다리다 로그인 시간 초과로 끝남 — 문구는 같고 시점만 즉시).
- 이벤트를 내지 않는 곳은 바닐라·Paper 와 같게 그대로: 읽기 타임아웃, 내부 예외, 구성·로그인 단계 시간 초과, 바닐라 패킷 속도 초과.
