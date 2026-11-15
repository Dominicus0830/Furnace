## 2026-11-15 ProtocolLib D1 — NMS 클래스 찾기 실패 알림 (protocollib-d1)

- ProtocolLib 은 켜진 **뒤**(첫 접속 때 `MinecraftReflection` 이 처음 쓰일 때) Spigot 이름 → Mojang 이름 → 옛 이름을 차례로 찔러 본다. 그래서 "켜기 끝에 한 줄로 모으기"(H7)만으로는 첫 접속 때 21줄이 나왔다. 켠 뒤 실패도 `BATCH_MILLIS`(2초) 모아서 한 줄로 알린다(`NmsReflect.classMissing`).
- 이름 하나는 (플러그인, 이름)마다 한 번만 센다(`WARNED`) — 같은 이름을 다시 찔러도 새 묶음을 만들지 않는다.
- 등급 C(Paper 1.21.1 에 있지만 Furnace 가 일부러 주지 않는 클래스)는 진짜 차이라 묶지 않고 바로 하나씩 경고한다. 다른 버전 접미사(`v1_`)·옛 패키지 탐색은 전처럼 DEBUG.
- 설정 `plugins.compat.nms.missing-class-log`(summary|each)는 플러그인마다 `CompatFormat.transformer` 에서 `NmsReflect.missingClassLog` 로 넘긴다. each 여도 켜는 동안의 실패는 켜기 끝에 한 줄(그 사이는 기능 감지가 대부분이라).
