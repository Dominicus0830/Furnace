## 2026-11-14 ProtocolLib 5.4.0 실측 점검 (protocollib-audit)

- **ProtocolLib 의 "클래스를 못 찾았다" 경고는 대부분 정상이다.** `MinecraftReflection.getMinecraftClass(별명…)` 는 Spigot 이름 → Mojang 이름 → 옛 이름 순으로 시도한다. 앞 이름이 없다는 경고가 나와도 뒤 이름으로 찾는다. 진짜 없는 것인지는 Probe 결과(`PROBE SWEEPFAIL`)로 본다.
- **ProtocolLib 은 이름이 아니라 모양으로 찾는 곳이 많다.** shim 의 모양이 바닐라와 다르면 이름이 맞아도 실패한다.
  - `NbtFactory` 는 `TagTypes.getType(n).getClass().getEnclosingClass()` 로 태그 클래스를 찾는다. 그래서 `TYPE` 은 그 태그 클래스 **안의** 익명 클래스여야 한다.
  - `BukkitConverters.getSoundConverter` 는 `CraftSound` 에서 "정적, `SoundEvent` 반환, `Sound` 인자 하나" 인 메서드를 정확히 하나 찾는다.
  - `EnumWrappers` 는 PLAYER_INFO 의 동작 enum 을 `getClasses()[1]` 로 찾는다. 이것은 InnerClasses 속성 순서이고, javac 는 소스 선언 순서가 아니라 **처음 참조한 순서**로 적는다. 바닐라 jar 순서(`Entry` → `Action`)와 비교해야 한다(T-181).
- **메타데이터 값 종류는 이름으로 잇는다.** shim `EntityDataSerializers` 상수 이름을 소문자로 바꾸면 Furnace `EntityDataType` 이름과 같다.
  - `Rotations` 는 바닐라가 x 를 `% 360` 하므로, 왕복 시험 값은 0~360 안에서 고른다.
  - 못 바꾸는 종류가 하나라도 있으면 패킷 전체를 거절한다. 일부 칸만 보내면 클라이언트의 엔티티 모양이 Paper 와 달라진다.
- **실측 환경의 함정.**
  - `libraries.minecraft.net` 이 403 이라 authlib 를 못 받는다. 바닐라 번들 jar(`tools/vanilla-data` 의 1.21.1 서버 jar) 안의 `META-INF/libraries/` 사본을 로컬 라이브러리 캐시에 풀어 썼다.
  - 상태 핑은 첫 접속 뒤에 해야 한다. 네트워크는 첫 접속 때 포트를 연다.
- **`TestPlayer` 의 `player_info_update` 해독 실패 2건은 시험 클라이언트의 한계다**("채팅 세션 초기화는 읽지 않는다"). ProtocolLib 과 무관하다.
