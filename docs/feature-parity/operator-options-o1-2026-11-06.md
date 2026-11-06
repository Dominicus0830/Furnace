# 운영 옵션 O1 (채팅 순서·채팅 신고·서버 브랜드·잠자기 문구·Ctrl+C 확인) — 2026-11-06

모두 `furnace.properties` 키이고, 바닐라 결과가 달라지는 것은 기본이 바닐라 동작이다. 코드는 `config/OperatorOptions.java`(서버 하나에 한 벌, 기동 때 읽음).

| 키 | 기본 | 효과 |
|---|---|---|
| `chat.strict-order` | `true` | `false` 면 시각이 거꾸로 간 채팅·서명 명령을 **그 건만 버리고** 서명 체인은 이어 간다. 서명은 그대로 검증하므로 위조는 여전히 체인을 끊는다 |
| `chat.unsigned-as-system` | `false` | `true` 면 플레이어 채팅을 서명 없는 시스템 메시지로 보낸다(클라이언트 채팅 신고·"Not Secure" 대상이 아님). 기동 때 경고 |
| `server-brand` | `Furnace` | 클라이언트 F3 의 서버 브랜드와 level.dat `ServerBrands`. 빈 값이면 기본 |
| `announce-sleep-status` | `true` | `false` 면 "x/y 명 자는 중"·"밤을 건너뛰는 중" 액션바를 보내지 않는다(`sleep.not_possible` 은 그대로) |
| `confirm-interrupt` / `confirm-interrupt-seconds` | `false` / `5` | `true` 면 콘솔 Ctrl+C(SIGINT) 첫 입력은 안내만 하고, 제한 시간(1~60초) 안의 두 번째 입력에서 저장 후 종료. `/stop`·SIGTERM 은 그대로 바로 종료 |

## 64번에 대한 발견

작업 표는 "바닐라 킥은 `SignedMessageChain` 에 구현돼 있다"고 적었지만, 바닐라 1.21.1 바이트코드(`xt$a`·`aru`)를 보면 순서가 어긋난 채팅은 **킥이 아니다**. `chat.disabled.out_of_order_chat` 빨간 메시지를 보내고 체인을 끊어, 그 세션은 재접속 전까지 채팅·서명 명령이 전부 `chain_broken` 으로 막힌다(`multiplayer.disconnect.out_of_order_chat` 은 언어 파일에만 남은 옛 문구). Furnace 기본(`chat.strict-order=true`)은 이 바닐라 동작 그대로다. 그래서 키 이름을 킥 대신 `strict-order` 로 했다.

## 시험
`OperatorOptionsTest`(기본값·파싱·거부), `InterruptConfirmTest`(확인 창), `SignedChatTest.relaxedOrderKeepsChain`(완화 모드: 예외 없이 이어 붙고 표시만, 다음 메시지 정상, 위조는 여전히 끊김). 실제 클라이언트 F3 브랜드·Ctrl+C 는 `docs/user-todo.md` 1장에 확인 행을 올렸다.
