# 마인크래프트 판 상수 (`GameVersion`) 와 바이트코드 동일 증명 (V-01, 2026-11-16)

## 한 곳에서 정한다

`gradle/minecraft.properties` 가 이 서버가 따르는 판을 정한다(게임 버전·프로토콜·DataVersion·리소스 꼬리표·크래프트 패키지 접미사·데이터 팩 형식·oracle 개발 번들).
`:furnace-api:generateGameVersion` 이 이 파일로 `org.domi.furnace.api.GameVersion` 을 `build/generated` 에 만든다(커밋 안 함). 칸은 전부 `public static final` 원시값·문자열 = **컴파일 시간 상수**라 javac 가 쓰는 자리에 값을 그대로 박는다 — 실행 비용 0, 핫패스에 새 분기·조회 없음.

| 칸 | 쓰임 |
|---|---|
| `MINECRAFT` | 버전 이름("1.21.1") — 상태 응답, 로그, `/version`, Query, 크래시 보고서, 플러그인 `minecraftVersion()` |
| `PROTOCOL` | 네트워크 프로토콜 번호(로그인·상태·핸드셰이크 판정, 패킷 스키마·번호표 파일 이름) |
| `DATA_VERSION` | 월드·플레이어·구조물 NBT 의 `DataVersion` |
| `RESOURCE_TAG` | 리소스 파일 이름 꼬리표 — `blocks/definitions_<tag>.txt`, `worldgen/<tag>/…`, `registries/<tag>.txt` |
| `CRAFT_PACKAGE_SUFFIX` | 호환 계층의 `org.bukkit.craftbukkit.<접미사>` |
| `PACK_FORMAT_DATA` | 데이터 팩 `pack_format` |

규칙: 코드(서버·호환 계층·API·시험 도구·시험의 리소스 경로)에 `"1.21.1"`·`767`·`3955`·`_1.21.1` 이름을 직접 쓰지 않고 위 상수를 쓴다. 판을 올리는 홉(U-01)은 이 properties 파일만 바꾼다.
시험이 "바닐라 1.21.1 은 이렇다"는 **기대값**으로 적어 둔 숫자·문구(예: 상태 26684개)는 판 고유 사실이라 그대로 둔다 — 판이 바뀌면 그 시험이 깨지는 것이 올바른 신호다.

`worldgen-oracle` 은 `:worldgen-oracle:run` 이 `-Doracle.resourceTag=<RESOURCE_TAG>` 를 넘기고, paperweight 개발 번들도 같은 파일에서 읽는다.

이름에 판이 들어간 클래스 중 `Protocol767` 계열은 V-03 에서 `ProtocolTable`·`GenericHand`·`GenericCodec`·`GenericComplex` 로 바뀌었고, 패킷 번호는 `PacketIds`(아래)가 맡는다. `EntityData767` 과 번호 상수 묶음 `EntityDataIds` 는 V-04 몫이다(`docs/roadmap/version-upgrade-plan-2026-11-16.md`).

## 바이트코드 동일 증명: `tools/ci/javap-same.sh`

"결과가 바뀌지 않는 정리" PR(V-01~V-04)의 증명 도구. 전·후 컴파일 결과(클래스 폴더)를 견준다.

```
# 1) 정리 전 커밋에서 컴파일해 클래스 폴더를 따로 보관
./gradlew -PskipOracle=true :furnace-api:classes :furnace-server:classes :furnace-compat-paper:classes
cp -r furnace-server/build/classes/java/main /tmp/before-server        # 모듈마다
# 2) 정리 후 커밋에서 다시 컴파일한 뒤
tools/ci/javap-same.sh /tmp/before-server furnace-server/build/classes/java/main \
    --allow-new '^org/domi/furnace/api/GameVersion\.class$'
```

클래스마다 `javap -v -p -c` 를 떠서 상수 풀 크기에서만 오는 차이(상수 풀 번호, `ldc_w`↔`ldc`, 그로 인한 명령 위치 이동)를 `tools/ci/javap-norm.py` 로 지우고 견준다. 필드 서술자·`ConstantValue`, 메서드 명령과 피연산자, 문자열 이어 붙이기 재료(`BootstrapMethods`)는 그대로 남는다. 손으로 쓴 숫자가 생성 상수 참조로 바뀌어도 javac 가 같은 값을 박으면 통과하고, `getstatic`/호출이 새로 생기거나 값이 달라지면 그 클래스만 `다름` 으로 나온다(확인: 프로토콜을 768 로 바꾸면 영향받는 5개 클래스가 정확히 잡힌다).

V-01 결과: furnace-api 782 · furnace-server 3,837 · furnace-compat-paper 1,698 클래스 **전부 같음**(새 클래스는 `GameVersion` 하나).

이름만 바꾼 정리는 `--rename 's/\bProtocol767\b/ProtocolTable/g; …'` 로 "전" 쪽 javap 글자·클래스 경로에 같은 `sed -E` 식을 먼저 적용해 견준다(V-03).

## 패킷 번호 `PacketIds` (V-03)
- 입력 `furnace-api/src/main/resources/org/domi/furnace/api/packet/schema/protocol-<mc.protocol>-ids.txt`(바닐라에서 뽑은 번호표) → `furnace-server` 의 `generatePacketIds` 가 `org.domi.furnace.server.net.protocol.PacketIds` 를 `build/generated/packetIds` 에 만든다(커밋 안 함).
- 상수 이름은 `<상태>_<방향>_<와이어 이름>`(예: `PLAY_CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT`, `PLAY_SERVERBOUND_CHAT`). 상태는 `HANDSHAKE`·`STATUS`·`LOGIN`·`CONFIGURATION`·`PLAY`. 안쪽 클래스를 쓰지 않는다(쓰면 쓰는 클래스마다 `InnerClasses` 항목이 생겨 위 증명에 잡음이 낀다).
- 서버 패킷 클래스의 `ID`·`ID_PLAY`·`ID_CONFIG` 와 `ConnectionHandler` 의 `case`(68개)가 이 상수를 쓴다. 컴파일 시간 상수라 `case` 라벨로 쓸 수 있고 javac 가 숫자를 그대로 박는다.
- `ProtocolTable.CLIENTBOUND/SERVERBOUND`(play 와이어 이름 순서)는 같은 번호표에서 읽는다(`ProtocolIds.current()`). 클래스 로드 때 한 번만 일한다.
- 시험 `PacketClassIdsTest`: 패킷 클래스 141칸마다 "어느 상태·방향의 어느 와이어 이름인가"를 손으로 적어 두고, 그 번호가 번호표와 같은지 본다. 새 판에서 번호표가 바뀌면 상수가 따라 바뀌고, 패킷이 사라지거나 이름이 바뀌면 컴파일이나 이 시험이 먼저 멈춘다.
