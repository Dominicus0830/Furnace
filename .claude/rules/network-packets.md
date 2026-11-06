---
paths:
  - "furnace-server/src/main/java/org/domi/furnace/server/net/**"
  - "furnace-server/src/test/java/org/domi/furnace/server/net/**"
---
# 네트워크·패킷 규칙 (프로토콜 1.21.1)

## 구조
- Netty boss/worker → `ConnectionHandler`(프로토콜 상태머신) → 상태별 패킷. 상태는 `ConnectionState`(handshake/status/login/configuration/play).
- S→C 패킷은 `net/protocol/<상태>/` 아래 `record X(...) implements Packet`. 상수 `ID`, `id()`, `write(ByteBuf)`를 갖고, 패킷 id는 `ConnectionHandler`가 앞에 쓴다. 기존 패킷(`SetHealth` 등)을 따라 쓴다.
- 직렬화는 `net/io/Types`(VarInt 등)와 `net/io/nbt/*`를 쓴다.
- 파일 맨 위 한 줄 주석에 방향·id·필드 형식을 적는다(예: `S→C 체력/허기(0x5D): float, VarInt, float`).

## 규칙
1. **패킷 id는 추측하지 않는다.** 바닐라 데이터 생성기의 `reports/packets.json`(1.21.1)으로 확인한다. 추출 방법은 `tools/vanilla-data/README.md`.
2. 바닐라 jar·생성 결과는 커밋하지 않는다.
3. 클라이언트 입력은 신뢰하지 않는다. 비유한(NaN/Inf)·범위 밖·급이동·로드되지 않은 청크·도달 거리 초과를 거부한다(`docs/remote-pr1-port/checklist.md` 3번).
4. 델타 이동은 `short` 범위를 넘으면 텔레포트 패킷으로 전환한다(`fitsDelta`).
5. 구독 후 청크가 도착하기 전의 `BlockUpdate`는 보류했다가 청크 뒤에 보낸다.
6. 레지스트리·태그 패킷은 `net/registry/`와 `resources/registries/`가 원천이다. 값을 코드에 하드코딩하지 않는다.
7. 패킷 속도 제한(`TokenBucket`)·읽기 타임아웃·종료 flush 경로를 우회하지 않는다.
8. 프로토콜 상수가 코드에 산재해 있어 버전업 때 부채가 된다. 새 상수는 한곳에 모으고 1.21.1 전용임을 주석으로 남긴다.
9. 패킷 추가 시 직렬화 단위 테스트를 같이 추가한다(바닐라 바이트 기대값이 있으면 그것과 대조).
