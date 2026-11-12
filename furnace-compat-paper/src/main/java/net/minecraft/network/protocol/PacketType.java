package net.minecraft.network.protocol;

import net.minecraft.resources.ResourceLocation;

/** 패킷 종류 shim(Mojang 이름, N3 1단계): 방향과 이름. 형식 인자가 그 종류의 패킷 클래스다(리플렉션으로 읽히는 모양이 바닐라와 같다). */
public record PacketType<T extends Packet<?>>(PacketFlow flow, ResourceLocation id) {

    @Override
    public String toString() { return flow.id() + "/" + id; }
}
