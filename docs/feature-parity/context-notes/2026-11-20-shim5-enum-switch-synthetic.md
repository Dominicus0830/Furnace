# shim 의 enum switch 가 서명 관문을 깨뜨린다 (2026-11-20, NMS shim 5a-2)

- shim 클래스 안에서 **다른 클래스의 enum 으로 switch** 하면 javac 가 합성 클래스 `<그 클래스>$1`(switch 표)을 만든다.
- 바닐라 쪽에 같은 이름의 익명 클래스가 있으면(예: `ServerPlayer$1` = 바닐라의 `ContainerSynchronizer` 익명 구현) 서명 관문
  (`:vanilla-extract:signatureGate`)이 "type-missing … ServerPlayer$1 - ContainerSynchronizer" 새 차이로 실패한다.
- 해결: shim 에서는 enum switch 대신 `==` 비교(또는 삼항 연산자)로 고른다. 같은 enum 파일 안의 switch 나 패턴 switch 도 합성 클래스를
  만들 수 있으니, 바닐라에 `$1`·`$2` 익명 클래스가 있는 shim(ServerPlayer·Level·Entity 등)에서는 특히 피한다.
