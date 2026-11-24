# Component 가 brigadier Message 를 구현하게 할까 (component-brigadier-message) — 2026-11-24

근거: network 영역 Component 계열 구현(`docs/feature-parity/context-notes/2026-11-24-component-contents-visit.md`).

**한 줄 요약:** Paper 의 `Component` 는 brigadier `Message` 를 구현합니다. Furnace 의 brigadier 는 "쓰는 플러그인이 있을 때만 서버가 붙이는 선택 라이브러리"라서, `Component` 가 `Message` 를 구현하면 brigadier 가 없을 때 `Component` 자체가 못 올라갑니다. 그래서 지금은 구현하지 않았습니다. 추천대로 진행 중이며 급하지 않습니다.

| # | 물음 | 안 하면 어떻게 되나 | 추천 |
|---|---|---|---|
| C1 | **결정:** brigadier 를 항상 붙여서 `Component` 가 `Message` 를 구현하게 할까, 지금처럼 안 할까 | 플러그인이 `Component` 를 brigadier 의 `Message` 자리(예: 명령 오류 메시지)에 그대로 넘기면 실패합니다. `instanceof Message` 검사도 Paper 와 다릅니다 | **A. 지금 유지(구현 안 함)** — brigadier 를 쓰는 플러그인은 거의 `LiteralMessage`·`Component.translatable` 을 직접 만듭니다(가정) |

## C1. 추천 A (코디네이터 확정 전, 도미니코 확인 대기)

- 쉬운 말: 채팅 글(`Component`)은 거의 모든 플러그인이 씁니다. 명령 라이브러리(brigadier)는 명령 플러그인만 씁니다. Paper 에서는 채팅 글이 "명령 메시지"도 겸하지만, 우리는 명령 라이브러리가 없는 서버에서도 채팅 글이 떠야 합니다.
- 예: 플러그인이 `new SimpleCommandExceptionType(Component.literal("실패"))` 처럼 채팅 글을 명령 메시지 자리에 넘기면, 지금은 컴파일은 되지만(자리가 `Message`) 실행 때 형식이 맞지 않아 실패합니다.
- 고를 것:
  - **A(추천). 지금처럼.** `Component` 는 항상 올라갑니다. 위 예처럼 쓰는 플러그인이 실제로 나오면 그때 B.
  - B. brigadier 를 항상 붙이고 `Component` 가 `Message` 를 구현. Paper 와 같아지지만 brigadier 를 안 쓰는 서버도 라이브러리를 늘 읽어 시작이 조금 늦고, 로더 순서를 바꿔야 합니다.
- 안 하면(A): Paper 와 다른 점은 위 예 한 가지입니다.

코디네이터 확정(도미니코 위임) 전: A 로 진행.

## 실측(2026-11-24)
인기 플러그인 100개에서 Component 를 brigadier Message 자리에 넘기는 곳의 수: 이 환경에는 플러그인 jar 가 없어 못 셌다(러너에서 jar 훑기 필요). 0 이면 추천 A 확정.
