# A16 점검(CraftBukkit 어댑터·라이브러리 로더) — 도미니코 확인이 필요한 것 (2026-11-11)

보고서: `docs/audit/findings/A16-nms-craftbukkit-libraries.md`

결정이 필요한 항목은 없다. 아래는 직접 확인하면 좋은 것이고, 추천안대로 후속을 진행한다.

| 번호 | 확인·결정할 것 | 추천 | 이유 |
|---|---|---|---|
| A16-D1 | 서버 제공 라이브러리 중 Mojang 저장소 3개(brigadier 1.3.10·authlib 6.0.54·DataFixerUpper 8.0.16)의 고정 sha256 을 인터넷이 되는 PC 에서 한 번 다시 재 본다. Central 24개는 이번에 직접 내려받아 모두 일치했지만, 이 컨테이너는 `libraries.minecraft.net` 이 403 이라 못 쟀다. | 한 줄로 확인: `curl -sL https://libraries.minecraft.net/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar \| sha256sum` (나머지 둘도 같은 모양). 값이 `ServerLibraries.java` 와 같으면 끝 | 값이 틀리면 그 묶음을 쓰는 Paper 플러그인이 "sha256 이 고정값과 다르다" 로 기동 때 실패한다 |
| A16-D2 | A16-01 수정(몹 위치·체력·생사가 소유 스레드에서 곧바로 바뀐 값)은 비동기 스레드에서의 읽기를 바꾸지 않는다(여전히 만든 시점 값). 이대로 문서화하고 끝낼까? | **그대로 문서화**(Paper 도 비동기 스레드의 엔티티 읽기를 허용하지 않음) | 소유 스레드 규칙을 깨지 않고 흔한 호출 패턴(setHealth 뒤 getHealth 등)만 고친다 |
