package net.minecraft.core.component;

/**
 * 아이템 데이터 컴포넌트 종류(Mojang 이름 shim, H7b, 등급 B). 바닐라처럼 인터페이스이고, 상수는 {@link DataComponents} 에 있다.
 * 값 변환을 가진 종류({@link DataComponents.Type})는 NMS 값 객체로, 나머지 종류({@link DataComponents.Raw})는 저장 꼴 SNBT 를 든
 * {@link org.domi.furnace.compat.nms.RawComponentValue} 로 값을 주고받는다.
 */
public interface DataComponentType<T> {

    /** 호환 모듈 전용: 레지스트리 키(minecraft:custom_data). */
    String furnaceKey();

    /**
     * 바닐라 streamCodec(): 이 종류 값 하나의 네트워크 꼴 코덱(종류 번호는 붙이지 않는다). 읽고 쓰는 규칙은 서버의 바닐라 컴포넌트 코덱이다.
     */
    net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec();
}
