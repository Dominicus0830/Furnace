// 위치에서 나는 소리. sound 는 "minecraft:entity.experience_orb.pickup" 같은 이름 — 레지스트리에 없는 이름(리소스 팩 소리)도 된다.
// fixedRange 는 레지스트리에 없는 소리를 보낼 때만 쓰는 고정 거리(null 이면 음량으로 정함). seed 는 소리 변형 고르기 씨앗.
package org.domi.furnace.api.packet.clientbound;

import java.util.Objects;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.world.SoundCategory;

public record ClientboundSoundPacket(String sound, Float fixedRange, SoundCategory category, double x, double y, double z,
                                     float volume, float pitch, long seed) implements ClientboundPacket {

    public ClientboundSoundPacket {
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(category, "category");
        if (sound.indexOf(':') < 0) sound = "minecraft:" + sound;
    }

    public static ClientboundSoundPacket of(String sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
        return new ClientboundSoundPacket(sound, null, category, x, y, z, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    @Override
    public PacketType<ClientboundSoundPacket> type() { return PacketTypes.Clientbound.SOUND; }
}
