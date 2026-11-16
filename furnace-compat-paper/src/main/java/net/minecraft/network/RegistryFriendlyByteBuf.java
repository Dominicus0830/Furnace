package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;

/** 레지스트리를 아는 패킷 버퍼 shim(Mojang 이름, N3 1단계). 게임 단계 패킷의 버퍼 형식. */
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {

    private final RegistryAccess registryAccess;

    public RegistryFriendlyByteBuf(ByteBuf source, RegistryAccess registryAccess) {
        super(source);
        this.registryAccess = registryAccess;
    }

    public RegistryAccess registryAccess() { return registryAccess; }

    /** 바닐라: 레지스트리 접근을 묶어 버퍼 만드는 함수를 돌려준다. */
    public static java.util.function.Function<ByteBuf, RegistryFriendlyByteBuf> decorator(RegistryAccess registryAccess) {
        return buf -> new RegistryFriendlyByteBuf(buf, registryAccess);
    }
}
