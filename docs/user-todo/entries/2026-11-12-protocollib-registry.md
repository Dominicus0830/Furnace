# ProtocolLib 다리 1단계 — 결정·확인 (2026-11-12)

ProtocolLib 5.4.0 이 이제 오류 없이 켜진다(패킷 등록부 shim, `docs/feature-parity/compat-protocollib-registry-2026-11-12.md`). 패킷 가로채기는 아직 안 된다.

## 정할 것 (작업을 막지 않음)

1. **실제 Netty 파이프라인을 ProtocolLib 에 열기(다음 단계).** ProtocolLib 이 패킷을 보려면 접속마다 Netty 처리기를 끼울 수 있어야 한다. 바닐라 처리기 이름(`decoder`·`encoder`·`packet_handler`)의 자리를 Furnace 파이프라인에 만들고 서버 소켓 채널을 `channels` 목록에 내놓아야 한다.
   보안·성능 면: 플러그인이 파이프라인을 직접 만지면 연결 방어(속도 제한·프레임 크기 상한)를 건너뛸 수 있다. 추천: **ProtocolLib 같은 패킷 라이브러리 플러그인이 있을 때만**(리플렉션으로 `ServerConnectionListener` 를 읽는 순간) 열고, 방어 처리기는 플러그인 처리기보다 앞에 둔다. 없으면 지금처럼 비용 0.
2. **"NMS shim 에 아직 없다" 경고 소음.** ProtocolLib 은 Spigot·옛 이름 후보를 차례로 시험하다 `ClassNotFoundException` 을 잡는다(정상). 그래서 켤 때 9개, 접속·핫 리로드까지 24개가 나오고, 플러그인이 `PacketType.values()` 를 돌며 지원 여부를 묻는 패킷 로깅 도구는 많게는 297개까지 나온다(이름당 한 번).
   추천: 같은 플러그인이 짧은 시간에 여러 후보를 연달아 찾으면 한 줄로 묶고(예: "ProtocolLib 이 NMS 클래스 10개를 찾았으나 없다 — 후보 이름 시험일 수 있다"), 목록은 디버그로. 지금 그대로 두어도 기능은 같다.
3. **서버 제공 라이브러리에 `commons-lang` 2.6 추가**(알림). Paper·Spigot 은 옛 `org.apache.commons.lang` 을 싣고 ProtocolLib 이 그것을 쓴다. 다른 Central 라이브러리와 같은 규칙(표지 패키지가 보일 때만, 고정 sha256 `50f11b09…`)이라 새 결정은 아니지만 받는 목록이 하나 늘었다.

## 직접 확인하면 좋은 것
- 실제 ProtocolLib jar(GitHub 릴리스판, byte-buddy 포함)로도 켜지는지. 시험은 Central jar + byte-buddy 합본으로 했다.
