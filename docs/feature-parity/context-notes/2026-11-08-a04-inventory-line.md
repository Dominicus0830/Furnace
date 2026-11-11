# 플레이어 인벤토리 줄 (A04 복제·소실 수정, 2026-11-08)

2차 점검 A04 보고서(`docs/audit/findings/A04-dupe-loss.md`)의 A04-01~05 를 한 구조로 닫았다.

## 무엇이 문제였나
- 플레이어 인벤토리는 연결 이벤트 루프(Q·Ctrl+Q·인벤토리 창 클릭·퇴장 저장)에서 바로 바뀌고, 월드 결과(블록 설치·먹이·던지기)는
  region 큐에서 나중에 돌았다. `handleUseItemOn` 은 손 스택을 이벤트 루프에서 떠 두고 region 에서 설치한 뒤 `replace` 로 줄였는데,
  그 사이 칸이 바뀌면 `replace` 가 조용히 실패해 설치 1 + 원래 스택이 모두 남았다(셜커 상자는 내용물째).
- 퇴장 저장이 큐에 남은 설치보다 먼저 떠져 다시 접속하면 복제(A04-02), 취소된 버리기가 끊긴 뒤 칸에 돌아와 소실(A04-03).

## 바닐라
- 한 서버 스레드가 패킷을 받은 순서대로 처리하고, 끊김 처리(저장)도 같은 스레드다. 연결이 닫힌 뒤 꺼낸 패킷은 처리하지 않는다
  (`PacketUtils.ensureRunningOnSameThread` → `shouldHandleMessage`). Paper 는 죽음·수면·끊김(`isImmobile`) 때 버리기·창 클릭 등을 무시한다.

## Furnace 구조
- `ConnectionHandler.InventoryLine`(연결마다 하나): 인벤토리·손·고른 칸을 만지는 패킷 처리를 받은 순서대로 **한 번에 하나씩** 돌린다.
  비우기 차례마다 그때 플레이어가 선 칸의 region 스레드에서 돈다. region 큐(`runAt`)의 순서는 청크 단위라 region 나누기·합치기·텔레포트에서
  앞뒤 작업이 다른 region 에서 겹칠 수 있는데, 줄은 연결 단위라 그래도 겹치지도 앞지르지도 않는다.
  - 줄에 넣는 것: Q·Ctrl+Q(칸 줄이기·PlayerDropItemEvent·던지기가 한 차례), F 손 바꾸기, set_held_item, pick_item,
    set_creative_mode_slot, edit_book, container_click·container_close·container_button_click·place_recipe·select_trade(열린 창 확인부터
    줄에서 — 블록에 매인 창은 그 칸 주인이 지금 region 이면 바로, 아니면 그 region 으로 넘기고 끝날 때까지 줄을 멈춘다), use_item_on, use_item, use_entity,
    사용 끝(player_action 5), 퇴장 저장.
  - use_item_on: 고른 칸·손 스택을 작업 안에서 읽는다. 클릭한 칸의 주인이 지금 region 이 아니면 그 region 으로 넘기고 끝날 때까지 줄을 멈춘다.
  - 끊긴 뒤 꺼낸 작업은 버린다(퇴장 저장만 돈다).
  - 작업 하나가 예외·Error 를 던져도 기록만 하고 줄은 이어진다(Error 를 놓치면 그 연결의 줄이 영원히 멈춘다 — PR #199 검토 R1).
- 퇴장 저장(`saveOnLine`): `markDisconnected` 뒤 줄 끝에 넣는다. 돌던 작업은 끝나고 남은 작업은 버려지므로 저장과 월드가 맞는다.
  위치·순서 번호는 끊길 때 뜬다. `PlayerData.reserveSave`(월드 폴더+UUID)로 자리를 잡아, 같은 UUID 의 다시 접속 읽기(`afterPendingSaves`)와
  `PlayerData.awaitIo`(서버 종료 FLUSH_WORLDS·시험 정리)가 그 저장이 IO 에 들어갈 때까지 기다린다. 줄이 5초 안에 돌지 않으면(멈춘 region)
  그 자리에서 뜬다. 내린 월드·닫힌 Regionizer 면 바로 뜬다.
- save-all·백업은 플레이어 저장 요청에 잡힌 퇴장 저장 자리(`PlayerData.pendingSaves`)도 함께 기다린다 — 방금 나간 플레이어 저장이 빠지지 않게.

## 남은 차이·주의
- 넘긴 작업이 든 region 이 큐째 버려지면(추가 월드를 내리는 순간 등) 그 연결의 줄이 멈춘다. 퇴장 저장은 5초 대체 길이 막는다(검토 N2, 아주 드묾).
- 인벤토리 창 클릭·고른 칸 바꾸기가 서버에서 반영되는 때가 "받자마자"에서 "그 region 의 다음 작업 비우기(최대 한 틱)"로 늦어졌다.
  클라이언트는 예측하므로 보이는 차이는 없고, 바닐라도 패킷을 서버 스레드 작업으로 처리한다.
- 블록 부수기(player_action 0~2)·휘두르기는 줄 밖(블록 region 큐)이다. 도구 내구도 반영만 손을 만지고 복제로 이어지지 않는다.
- 연결 시험(`ConnectionInputTest`·`PacketApiFlowTest`)처럼 패킷을 넣고 바로 인벤토리를 보는 시험은 플레이어 region 을 한 번 기다려야 한다.
- 회귀 시험: `furnace-server` `plugin/A04InventoryLineTest`(보고서 재현 8개 + 순서·취소·다시 접속·종료 대기 5개),
  `furnace-test` `A04RealSocketLineTest`(실제 소켓, CI 밖).
