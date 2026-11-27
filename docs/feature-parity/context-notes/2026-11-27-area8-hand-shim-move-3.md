# 영역 8 손 shim 이전 3묶음 (2026-11-27)

`CommandSourceStack` 을 명세 조각으로 옮겼다(PR 647 의 Paper API `io.papermc.paper.command.brigadier.CommandSourceStack` 가 들어온 뒤). javap 전후(3024 클래스): 다름 1개 —
바닐라 인터페이스 `ExecutionCommandSource`·`SharedSuggestionProvider` 가 새로 붙는 방향. 남은 손 shim: 서버 패킷 수신기 2개(PR 642 대기), PathPackResources.
