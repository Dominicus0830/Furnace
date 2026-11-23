# Geyser 가 부르는 컴포넌트·레지스트리 shim (2026-11-23)

- `BuiltInRegistries.DATA_COMPONENT_TYPE`: 번호 순서는 서버 내장 목록(57종, 바닐라 네트워크 번호). 종류 객체는 `DataComponents.furnaceType(키)` 한 곳에서 정체성을 지킨다.
- `DataComponents.BUNDLE_CONTENTS`·`CONTAINER`·`CONTAINER_LOOT`: 값 클래스를 두지 않은 `DataComponents.Raw` 종류. 값은 `RawComponentValue`(저장 꼴 SNBT). `ItemStack.get/set` 은 값 변환이 없어 기존처럼 UnsupportedNmsCall.
- `DataComponentType.streamCodec()`: 서버의 바닐라 네트워크 코덱을 쓰는 `ItemSupport.encodeComponentValue/decodeComponentValue` 로 읽고 쓴다(종류 번호 없음).
- `DataComponentPatch.entrySet()`: 값 있는 것 다음 지운 것 순서(알려진 차이는 클래스 주석 참고).
- `ServerLevel.getPlayerByUUID`, `Block.getCloneItemStack`(= 블록 아이템 1개; 블록별 재정의는 shim 에 없음).
- 시험 `NmsGeyserGapsTest`(기대 바이트는 바닐라 네트워크 꼴). 서명 관문: 새 차이 0.
