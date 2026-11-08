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
- `ConnectionHandler.onLine(p, task)`: 같은 플레이어의 인벤토리·손·고른 칸을 바꾸는 패킷은 플레이어가 선 칸의 region 작업 큐에서 받은
  순서대로 돈다. 설치(use_item_on)·사용(use_item)·엔티티 상호작용도 같은 큐(같은 region)라 둘이 섞이지 않는다.
  - 줄로 옮긴 것: Q·Ctrl+Q(칸 줄이기·PlayerDropItemEvent·던지기가 한 차례), F 손 바꾸기, set_held_item, pick_item, set_creative_mode_slot,
    edit_book, container_click·container_close·container_button_click·place_recipe·select_trade(열린 창 확인부터 줄에서 — 블록에 매인 창은
    거기서 그 블록 region 으로 넘긴다).
  - `handleUseItemOn` 은 고른 칸·손 스택을 region 작업 안에서 읽는다.
  - 끊긴 뒤 큐에서 꺼낸 줄 작업·use_item_on·use_item·use_entity 는 버린다(바닐라).
- 퇴장 저장(`saveOnLine`): `markDisconnected` 뒤 저장을 줄 끝에 넣는다. 돌던 작업은 끝나고 남은 작업은 버려지므로 저장과 월드가 맞는다.
  위치·순서 번호는 끊길 때 뜬다. 같은 UUID 의 다시 접속 읽기는 `PlayerData.reserveSave`/`afterPendingSaves` 로 그 저장이 IO 에 들어간 뒤 읽는다.
  region 이 5초 안에 돌지 않으면(멈춤) 그 자리에서 뜬다. 내린 월드·닫힌 Regionizer 면 바로 뜬다.

## 남은 차이·주의
- 인벤토리 창 클릭·고른 칸 바꾸기가 서버에서 반영되는 때가 "받자마자"에서 "그 region 의 다음 작업 비우기(최대 한 틱)"로 늦어졌다.
  클라이언트는 예측하므로 보이는 차이는 없고, 바닐라도 패킷을 서버 스레드 작업으로 처리한다.
- 블록이 플레이어와 다른 region 에 있으면(region 경계 — 합치기 반경 안이라 사실상 없음) 설치와 줄 작업이 다른 큐라 순서가 보장되지 않는다.
- 연결 시험(`ConnectionInputTest`)처럼 패킷을 넣고 바로 인벤토리를 보는 시험은 플레이어 region 을 한 번 기다려야 한다(`settle()`).
- 회귀 시험: `furnace-server` `plugin/A04InventoryLineTest`(보고서 재현 8개 + 순서·취소·다시 접속 4개), `furnace-test` `A04RealSocketLineTest`(실제 소켓).
