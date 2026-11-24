# 컨테이너 블록 엔티티가 Container 가 되어 메서드가 던진다 (영역 1, 2026-11-24)
쉬운 말: 상자·화로·호퍼 같은 블록 엔티티 15종이 이제 바닐라처럼 `Container` 로 보인다(`instanceof Container` 참). 하지만 `getItem`·`setItem`·`getContainerSize` 같은 메서드는 아직 몸통이 없어 부르면 `UnsupportedNmsCall` 이 난다(던지는 몸통 48→285). 전에는 `instanceof Container` 가 거짓이라 "아니면 다른 길"로 조용히 넘어가던 플러그인이 이제 오류를 낼 수 있다. 바닐라·Paper 는 참이고 동작한다.
안 하면?: 그 길을 타는 플러그인이 오류. 해결은 영역 4(컨테이너·메뉴) 몸통 채우기를 앞당기는 것(추천). 참고: `ContainerSingleItem` 의 default 메서드(주크박스·장식 단지·조각 책장)도 생성 인터페이스에서는 추상이라 각각 던지는 몸통이 붙었다 — 채울 때 default 를 이을 것.
코디네이터 확정(도미니코 위임): 영역 4 에 전달. 근거 PR 612 검토.
