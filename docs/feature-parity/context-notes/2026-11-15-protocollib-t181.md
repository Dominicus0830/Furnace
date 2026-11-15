# ProtocolLib 탭 목록·GameProfile(T-181) 함정 (2026-11-15)

- **javac 는 멤버 클래스를 선언의 거꾸로 InnerClasses 에 적는다**(실험으로 확인: `class A { class X{} class Y{} }` → `A.class.getClasses()` = `[Y, X]`). `Class.getClasses()` 는 InnerClasses 순서를 따른다. ProtocolLib 은 `ClientboundPlayerInfoUpdatePacket.getClasses()[0]` 을 항목(Entry), `[1]` 을 동작 enum 으로 쓰므로 shim 에서는 `Action` 을 먼저 선언해야 `[Entry, Action]` 이 된다. 다른 shim 에서 안쪽 클래스 순서가 중요하면 같은 규칙.
- ProtocolLib `InstanceCreator`(빈 패킷 만들기)는 **private 생성자까지**(`getDeclaredConstructors`) 매개변수 가장 적은 것을 null 기본값으로 불러 본다. 예외가 나면 다음 후보. null 을 받아 주는 작은 생성자를 두면 그것이 쓰인다.
- authlib `GameProfile` 은 shim 생성기가 Object 로 지운다(선택 라이브러리). 손으로 쓴 shim 에서 진짜 형식을 쓰면 authlib 이 없는 서버에서 그 클래스를 **처음 이을 때** NoClassDefFoundError — 필드·메서드 서명에만 두고 만드는 곳은 `Profiles.available()` 뒤에서. `CompatLinkTest` 가 authlib 대역을 뺀 로더로 지킨다.
- 시험 서버(`FurnaceTestServer`)는 틱을 손으로 돌린다. 새 플레이어가 접속할 때 **다른 플레이어에게 알리는** 탭 목록 패킷은 틱에서 나간다 — `join` 뒤 `server.tick()` 없이 기다리면 오지 않는다(시험이 30초 기다리다 실패, 원인 찾느라 시간을 썼다). 접속하는 플레이어 자신이 받는 것은 접속 처리 안에서 바로 나간다.
- 접속하는 플레이어 자신에게 가는 접속 패킷은 Bukkit 플레이어를 감싸기 전이라 ProtocolLib 다리(`ProtocolLibBridge.connection`)에서 연결이 null — 다리에 안 보인다. Paper 에서는 ProtocolLib 이 로그인 때 채널에 주입해 보인다(T-183 과 함께).
