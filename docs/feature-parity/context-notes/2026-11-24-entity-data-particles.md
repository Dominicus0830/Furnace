# 엔티티 데이터 칸: 입자·위치 값 종류 (2026-11-24, 엔티티 데이터 설계 D4 마무리)

- 입자 shim(`net/minecraft/core/particles/`)을 손 shim 으로 둔다: `ParticleOptions`·`ParticleType`·`SimpleParticleType`·옵션 클래스 8개·`ParticleTypes`(109개).
  `ParticleTypes` 초기화가 바닐라처럼 차례로 `minecraft:particle_type` 내장 레지스트리에 등록하고, `NmsParticles.registry()` 가 처음 꺼낼 때 서버
  내장 목록(`registries/builtin-<판>.txt` 의 particle_type 줄, 추출 스크립트 `extract_builtin_registries.py` 에 더함)과 순서를 견준다.
- 바닐라 값 확인(1.21.1 서버 jar javap): 크기는 `Mth.clamp(s, 0.01, 4)`, 빨강 먼지 색은 `Vec3.fromRGB24(0xFF0000).toVector3f()`(채널마다 double 로 나눈 뒤 float),
  스컬크 색 3790560, `ColorParticleOption.create(종류, r, g, b)` 는 알파 1 + 채널마다 `floor(x * 255)`, 아이템 입자는 빈 스택이면 IllegalArgumentException,
  옵션 있는 종류는 `ParticleTypes` 안의 이름 없는 하위 클래스(`ParticleTypes$1`). 옵션 클래스는 equals 를 두지 않는다(스컬크 충전만 레코드).
- 코덱(`codec()`·`streamCodec()`·`CODEC` 상수)은 아직 없다 — shim 종류의 코덱 메서드는 UnsupportedNmsCall, 상수는 껍데기 합치기의 껍데기 필드.
- 진동 입자는 위치 원천(`PositionSource`) shim 이 없어 생성자·목적지가 껍데기다. 다리는 두 방향 모두 UnsupportedNmsCall 이고, 패킷 미리 보기
  (`PacketDelivery.quickRefusal`)는 값에 진동 입자가 있으면 `particle(vibration)` 처럼 미리 알린다(예외를 만들지 않는 길 유지).
- `GlobalPos` 는 바닐라처럼 레코드(`toString` = "차원키 좌표"). optional_global_pos 칸이 이제 다리를 지난다 — 바닐라 값 종류 31개가 모두 다리에 있다.
- 함정: 서명 관문은 손 shim 의 암묵 기본 생성자도 새 차이로 본다. 바닐라 생성자를 쓸 수 없는 클래스(진동 입자)는 `private` 기본 생성자를 두면
  관문이 보지 않고, 바닐라 생성자는 껍데기 합치기가 더한다.
