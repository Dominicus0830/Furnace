// 플러그인 API 의 소리·입자 값 → 서버 패킷. WorldHandle(주변에 보내기)·PlayerHandle(한 사람에게)이 같이 쓴다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.audience.Sound;
import org.domi.furnace.api.world.ParticleEffect;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;
import org.domi.furnace.server.net.protocol.play.SoundEntity;
import org.domi.furnace.server.net.protocol.play.SoundPacket;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ItemRegistry;

final class ApiEffects {

    private ApiEffects() {}

    static String soundKey(String key) { return key.indexOf(':') < 0 ? "minecraft:" + key : key; }

    /** 레지스트리에 있는 소리면 번호(참조 형식), 아니면 -1(이름을 직접 싣는다) — ChunkLevel.playSeededSound 와 같다. */
    private static int soundId(String key) {
        Integer id = ItemContext.installed() ? ItemContext.id("minecraft:sound_event", key) : null;
        return id == null ? -1 : id;
    }

    static SoundPacket soundAt(Sound s, double x, double y, double z, long seed) {
        String key = soundKey(s.key());
        return new SoundPacket(soundId(key), key, s.source().ordinal(), x, y, z, s.volume(), s.pitch(), seed);
    }

    static SoundEntity soundOn(Sound s, int entityId, long seed) {
        String key = soundKey(s.key());
        return new SoundEntity(soundId(key), key, s.source().ordinal(), entityId, s.volume(), s.pitch(), seed);
    }

    /** SoundEvent.getRange(volume): 바닐라 소리는 모두 가변 거리 — 크기 1 이하 16칸, 넘으면 16×크기. */
    static double range(float volume) { return volume > 1.0F ? 16.0 * volume : 16.0; }

    /** API 입자 값 → 와이어 옵션. 모르는 종류·블록·아이템이면 IllegalArgumentException. */
    static ParticleOptions particle(ParticleEffect e, BlockRegistry blocks, ItemRegistry items) {
        java.util.Objects.requireNonNull(e, "particle");
        if (!ParticleOptions.Registry.has(e.type())) throw new IllegalArgumentException("알 수 없는 입자 종류: " + e.type());
        return switch (e) {
            case ParticleEffect.Simple s -> new ParticleOptions.Simple(s.type());
            case ParticleEffect.BlockState b -> new ParticleOptions.Block(b.type(), ApiConvert.stateId(b.block(), blocks));
            case ParticleEffect.Dust d -> new ParticleOptions.Dust(r(d.rgb()), g(d.rgb()), b(d.rgb()), d.size());
            case ParticleEffect.DustTransition d -> new ParticleOptions.DustTransition(r(d.fromRgb()), g(d.fromRgb()), b(d.fromRgb()),
                    r(d.toRgb()), g(d.toRgb()), b(d.toRgb()), d.size());
            case ParticleEffect.ColorEffect c -> new ParticleOptions.Color(c.argb());
            case ParticleEffect.Item i -> new ParticleOptions.Item(ApiConvert.item(i.item(), items));
            case ParticleEffect.SculkCharge c -> new ParticleOptions.SculkCharge(c.roll());
            case ParticleEffect.Shriek s -> new ParticleOptions.Shriek(s.delay());
            case ParticleEffect.Vibration v -> v.block() != null
                    ? ParticleOptions.Vibration.toBlock(v.block().x(), v.block().y(), v.block().z(), v.arrivalTicks())
                    : ParticleOptions.Vibration.toEntity(v.entityId(), v.entityYOffset(), v.arrivalTicks());
        };
    }

    // Vector3f.fromRGB24 과 같은 나눗셈(0~255 → 0~1)
    private static float r(int rgb) { return ((rgb >> 16) & 0xFF) / 255.0F; }

    private static float g(int rgb) { return ((rgb >> 8) & 0xFF) / 255.0F; }

    private static float b(int rgb) { return (rgb & 0xFF) / 255.0F; }
}
