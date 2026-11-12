package net.minecraft.network.syncher;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 엔티티 메타데이터 값 종류 하나(Mojang 이름 shim). 패킷 라이브러리가 값의 Java 형식을 제네릭 인자로 읽는다.
 * 와이어 코덱은 아직 연결되지 않았다(범용 패킷 코덱 대기).
 */
public interface EntityDataSerializer<T> {

    StreamCodec<? super RegistryFriendlyByteBuf, T> codec();

    default EntityDataAccessor<T> createAccessor(int id) { return new EntityDataAccessor<>(id, this); }

    T copy(T value);
}
