package org.domi.furnace.compat.paper;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Vibration;
import org.domi.furnace.api.audience.Sound;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.ParticleEffect;

/** Bukkit 소리·입자 값 → Furnace API 값(World·Player 의 playSound·spawnParticle 이 같이 쓴다). */
public final class CompatEffects {

    private CompatEffects() {}

    public static Sound.Source source(SoundCategory c) {
        return switch (c) {
            case MASTER -> Sound.Source.MASTER;
            case MUSIC -> Sound.Source.MUSIC;
            case RECORDS -> Sound.Source.RECORD;
            case WEATHER -> Sound.Source.WEATHER;
            case BLOCKS -> Sound.Source.BLOCK;
            case HOSTILE -> Sound.Source.HOSTILE;
            case NEUTRAL -> Sound.Source.NEUTRAL;
            case PLAYERS -> Sound.Source.PLAYER;
            case AMBIENT -> Sound.Source.AMBIENT;
            case VOICE -> Sound.Source.VOICE;
        };
    }

    /** Bukkit Sound 의 키(minecraft:entity.player.levelup). */
    public static String key(org.bukkit.Sound s) { return s.getKey().toString(); }

    /**
     * Furnace Sound 값. Bukkit 은 크기·높이를 검사하지 않고 와이어에 그대로 싣는다 — Furnace 값은 0~2 높이·0 이상 크기만 받으므로
     * 높이는 0~2 로, 크기는 0 이상으로 죈다(바닐라 클라이언트가 높이를 0.5~2 로 다시 죄므로 들리는 결과는 같다. 음수 크기는 들리지 않는 것도 같다).
     * 이름이 리소스 id 가 아니면 IllegalArgumentException(Paper 의 Identifier.parse 와 같다).
     */
    public static Sound sound(String key, SoundCategory category, float volume, float pitch) {
        float v = volume >= 0.0F ? (Float.isInfinite(volume) ? Float.MAX_VALUE : volume) : 0.0F;
        float p = pitch >= 0.0F ? Math.min(pitch, 2.0F) : 0.0F;
        return new Sound(key, source(category), v, p);
    }

    /**
     * Bukkit 입자 + data → Furnace 입자 값. data 형이 맞지 않으면 IllegalArgumentException(CraftParticle 과 같은 문구),
     * 옵션이 필요한데 data 가 null 이어도 IllegalArgumentException.
     */
    public static ParticleEffect particle(Particle particle, Object data) {
        if (particle == null) throw new IllegalArgumentException("particle cannot be null");
        Class<?> type = particle.getDataType();
        if (type != Void.class && data == null) throw new IllegalArgumentException("missing required data " + type);
        if (data != null && !type.isInstance(data)) throw new IllegalArgumentException("data (" + data.getClass() + ") should be " + type);
        String key = particle.getKey().toString();
        // CraftParticle 처럼 입자 종류의 data 형으로 고른다(data 의 실제 형이 아니다 — DUST 에 DustTransition 을 주면 처음 색의 보통 먼지)
        if (type == Void.class) return ParticleEffect.of(key);
        if (type == Particle.DustTransition.class) {
            Particle.DustTransition d = (Particle.DustTransition) data;
            return new ParticleEffect.DustTransition(d.getColor().asRGB(), d.getToColor().asRGB(), d.getSize());
        }
        if (type == Particle.DustOptions.class) {
            Particle.DustOptions d = (Particle.DustOptions) data;
            return new ParticleEffect.Dust(d.getColor().asRGB(), d.getSize());
        }
        if (type == Color.class) return new ParticleEffect.ColorEffect(((Color) data).asARGB());
        if (type == org.bukkit.block.data.BlockData.class) return new ParticleEffect.BlockState(key, blockData((org.bukkit.block.data.BlockData) data));
        if (type == org.bukkit.inventory.ItemStack.class) return new ParticleEffect.Item(CompatItems.toFurnace((org.bukkit.inventory.ItemStack) data));
        if (type == Float.class) return new ParticleEffect.SculkCharge((Float) data);
        if (type == Integer.class) return new ParticleEffect.Shriek((Integer) data);
        if (type == Vibration.class) return vibration((Vibration) data);
        throw new IllegalArgumentException("data (" + data.getClass() + ") should be " + type);
    }

    private static org.domi.furnace.api.world.BlockData blockData(org.bukkit.block.data.BlockData b) {
        if (b instanceof CompatBlockData c) return c.furnace();
        return org.domi.furnace.api.world.BlockData.parse(b.getAsString());
    }

    private static ParticleEffect vibration(Vibration v) {
        if (v.getDestination() instanceof Vibration.Destination.BlockDestination b) {
            var l = b.getLocation();
            return ParticleEffect.Vibration.toBlock(new BlockPos(l.getBlockX(), l.getBlockY(), l.getBlockZ()), v.getArrivalTime());
        }
        // 엔티티로 가는 진동은 엔티티 네트워크 번호·눈높이가 필요한데 Furnace API 가 아직 내주지 않는다
        throw new UnsupportedFeature("Vibration.Destination.EntityDestination", "엔티티로 가는 진동 입자는 아직 없다 — BlockDestination 을 쓴다");
    }
}
