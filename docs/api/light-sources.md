# 가짜 광원 (W6)

블록을 두지 않고 서버 빛 엔진에 **광원만** 더하고 빼는 API다. 입구는 `Furnace.server().lightSources()`.
Starlight 시절 Paper 플러그인이 NMS 로 하던 "보이지 않는 횃불"(손에 든 횃불 빛, 무대 조명, 어두운 던전 안내등)을 공식 API 로 낸 것이다.

## 빠른 예

```java
var lights = server().lightSources();
World w = player.world();
BlockPos head = player.location().blockPos().offset(0, 1, 0);

lights.set(this, w, head, 14);            // 그 칸이 밝기 14 발광 블록처럼 빛난다
lights.lightAt(w, head).thenAccept(l -> l.ifPresent(v -> log("block=" + v.block() + " sky=" + v.sky())));
lights.remove(this, w, head);             // 지우면 원래 빛으로 돌아간다
```

## 규칙

- **진짜 빛이다.** 빛 엔진이 그 칸의 발광을 `max(블록 발광, 가짜 광원)` 으로 본다. 그래서 퍼지는 모양은 같은 밝기의 `minecraft:light` 블록을 둔 것과 칸마다 같고(`VirtualLightTest`), 클라이언트에 빛 갱신 패킷이 가며, 몹 스폰·작물·얼음 녹기처럼 빛을 보는 규칙도 이 빛을 쓴다. 패킷만 속이는 가짜가 아니다.
- 광원은 **보통 공기·투명 칸에** 둔다. 빛을 막는 블록(돌·흙) 안에 두면 그 칸만 밝고 밖으로 못 나간다(발광하는 꽉 찬 블록과 같다).
- 밝기는 0..15. 0 은 지우기. 범위 밖이면 `IllegalArgumentException` 을 바로 던진다.
- **플러그인별로 센다.** 같은 칸에 두 플러그인이 두면 가장 밝은 값이 쓰이고, 한쪽이 지우면 남은 쪽 값으로 돌아간다. 플러그인이 꺼지면 그 플러그인 몫은 서버가 저절로 지운다.
- `set`·`remove`·`lightAt` 은 그 칸을 가진 region 스레드에서 실행되고, 빛도 그 자리에서 다시 계산된다. 청크가 로드돼 있지 않거나 높이 밖이면 `false`(청크를 만들지 않는다). `level`·`levelOf`·`sources` 는 아무 스레드에서 바로 읽는다.
- 청크가 내려가도 광원은 메모리에 남고, 다시 올라오면 저절로 다시 켜진다. **서버를 다시 켜면 사라진다** — 필요하면 플러그인이 켤 때 다시 둔다.
- 디스크에는 가짜 빛을 쓰지 않는다. 가짜 광원이 있거나 맞닿은 청크는 빛 배열 없이(`isLightOn=0`) 저장해서, 그 월드를 바닐라로 열면 가짜 빛 없이 다시 계산한다. Furnace 는 청크를 읽을 때 늘 빛을 새로 계산하므로 차이가 없다.

## 성능

광원이 하나도 없으면 빛 엔진의 추가 비용은 칸마다 정수 하나 비교다. 광원이 있으면 발광을 볼 때 청크 맵 조회가 한 번 더 든다.
