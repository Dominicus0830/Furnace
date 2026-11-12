# packet-schema-check — 패킷 중립 스키마 ↔ 바닐라 jar 모양 대조(로컬 전용)

`furnace-api/src/main/resources/org/domi/furnace/api/packet/schema/protocol-767.schema` 는 손으로 쓴 파일이다.
이 도구가 바닐라 1.21.1 서버 클래스와 모양이 맞는지 본다. 바닐라 jar·클래스와 결과는 커밋하지 않는다(절대 규칙 7).

## 보는 것
- 패킷 종류 표(와이어 이름 → 클래스)가 스키마의 상태·방향·와이어 이름과 1:1 인가
- record / class / singleton, 부모 클래스, 인스턴스 필드 수·순서·서술자·제네릭 인자
- `new` 플래그 = 바닐라에 모든 필드를 받는 public 생성자가 있는가
- enum 상수 이름·순서(스키마 enum·type enum)
- 난독화 이름은 "Mojang 이름 ↔ 난독화 이름" 짝이 처음 본 대로 끝까지 한결같은지(일대일)로 맞춘다

필드 이름·접근자 이름·와이어 순서는 난독화돼 여기서 볼 수 없다(와이어는 서버 범용 코덱 시험이 본다).

## 돌리기
```sh
# 바닐라 클래스 꺼내기(스크래치 폴더에, 커밋 금지)
git archive main tools/vanilla-data/server-1.21.1 | tar -x -C /tmp/vanilla
# furnace-api jar 와 ASM(gradle 캐시나 /opt/gradle-*/lib)
./gradlew -PskipOracle=true :furnace-api:jar
CP=<asm-9.x.jar>:<asm-tree-9.x.jar>:furnace-api/build/libs/furnace-api.jar
java -Dstdout.encoding=UTF-8 -cp $CP tools/compat/packet-schema-check/PacketSchemaCheck.java /tmp/vanilla/tools/vanilla-data/server-1.21.1
```
끝줄이 `일치` 면 통과, 아니면 어긋난 곳을 한 줄씩 쓰고 1 로 끝난다.
2026-11-12 결과: 패킷 202종, 이름 짝 306개 일치. 스키마에 일부러 틀린 곳 셋(필드 타입·필드 수·enum 순서)을 넣으면 셋 다 잡는다.

다른 버전으로 올릴 때는 `TYPE_TABLES`(패킷 종류 표 클래스의 난독화 이름)만 바꾼다.
