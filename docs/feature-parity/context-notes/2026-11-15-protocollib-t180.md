# ProtocolLib 받는 쪽(2b-3, T-180) 함정 (2026-11-15)

- ProtocolLib 5.4 의 받는 처리기는 패킷 종류를 `InboundProtocolReader`(`"decoder"` **앞**)가 읽어 둔 단계로 찾는다. 가상 채널에서 shim 을 흘릴 때 `"decoder"` 문맥에서 시작하면 단계가 비어 종류를 못 찾는다 — 파이프라인 머리(`pipeline().fireChannelRead`)에서 흘린다.
- `receiveClientPacket` 은 ProtocolLib 이 먼저 리스너를 부른 뒤 `Connection.read(null, 패킷)`(이름 `read`, 인자 `ChannelHandlerContext, Packet`)을 반사로 부른다. ctx 가 null 이다 — 파이프라인으로 온 것(ctx 있음)과 구분하는 데 쓴다.
- 바닐라 `ServerboundUseItemOnPacket` 와이어의 칸 안 위치는 float 셋이고 읽을 때 `블록 좌표 + (double) float` 로 `BlockHitResult.location` 을 만든다. 쓸 때 `(float) (location − 블록 좌표)`. 이 순서로 오가면 float 값이 그대로 돌아온다(시험 `ServerboundBridgeTest`, 좌표 ±29,999,999 포함).
- `ServerboundInteractPacket` 은 바닐라도 생성자·동작 클래스(`InteractionAction`·`InteractionAtLocationAction`)·`ATTACK_ACTION` 이 private 이다. 위치는 와이어에서 float 셋.
- `ServerboundPlayerCommandPacket` 의 번호 생성자는 private(공개 생성자는 Entity 를 받는다), 번호 접근자는 없다(필드 `id`).
- 채팅 확인 비트(`LastSeenMessages.Update.acknowledged`)는 20칸 FixedBitSet — 더 큰 비트는 바닐라도 쓰다 실패한다.
- 레코드 `equals` 는 byte[] 필드(채팅 서명)를 객체로 비교한다 — 서명 있는 채팅은 "손대지 않음" 비교가 늘 다르다고 나와 같은 값을 다시 쓴다(결과는 같다).
- 같은 와이어 이름이 두 방향에 있다(`player_abilities`). 손 코덱 대응표 열쇠를 serverbound 는 `s:<와이어>` 로 나눴다.
- 서명 명령(`/msg` 처럼 글 인자가 있는 명령)은 `chat_command_signed` 로 온다. 값 shim(`ArgumentSignatures`·`RemoteChatSession.Data`)이 빈 껍데기면 범용 다리가 거절해 그 종류가 통째로 빠진다 — 손으로 써야 한다. 채팅 세션 공개 키는 X.509 RSA 바이트 ↔ `PublicKey`.
