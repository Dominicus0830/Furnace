package org.domi.furnace.compat.nms;

/**
 * 중립 스키마(furnace-api packet/schema)에서 생성한 패킷 shim. 플러그인용이 아니다.
 * 보내기·받기 다리(범용 패킷 코덱)가 이 두 값으로 스키마 항목과 필드 값을 찾는다.
 */
public interface SchemaPacket {

    /** "상태/방향/와이어 이름"(예: {@code game/clientbound/add_entity}). */
    String schemaKey();

    /** 스키마 필드 순서대로의 필드 값(shim 객체 그대로). */
    Object[] schemaValues();
}
