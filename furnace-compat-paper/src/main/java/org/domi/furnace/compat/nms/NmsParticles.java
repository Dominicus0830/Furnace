package org.domi.furnace.compat.nms;

import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.particle.Particle;

/**
 * 입자 shim(엔티티 데이터 칸 particle·particles). 내장 레지스트리 {@code minecraft:particle_type} 은 {@link ParticleTypes} 초기화가 차례로 채우고,
 * 그 순서가 서버 내장 목록(바닐라 reports/registries.json)과 같은지 처음 꺼낼 때 본다. 값은 Furnace {@link Particle} 과 성분 그대로 바꾼다
 * (색 벡터·스택은 바닐라처럼 새 객체 — 서버 값과 나누지 않는다). 진동 입자는 위치 원천 shim 이 없어 두 방향 모두 UnsupportedNmsCall.
 */
public final class NmsParticles {

    private NmsParticles() {}

    private static final NmsMappedRegistry<ParticleType<?>> REGISTRY = new NmsMappedRegistry<>(Registries.PARTICLE_TYPE);
    private static volatile boolean checked;

    /** ParticleTypes 초기화가 상수마다 부른다(바닐라 Registry.register). */
    public static <T extends ParticleType<?>> T register(String name, T type) {
        REGISTRY.add(ResourceLocation.withDefaultNamespace(name), type);
        return type;
    }

    /** 내장 입자 종류 레지스트리. 처음 꺼낼 때 상수를 다 등록시키고 번호 순서가 서버 목록과 같은지 본다(다르면 조용히 틀린 번호를 주지 않는다). */
    public static Registry<ParticleType<?>> registry() {
        if (checked) return REGISTRY;
        if (ParticleTypes.OMINOUS_SPAWNING == null) return REGISTRY;   // 클래스 초기화 도중(마지막 상수가 아직 없다)
        synchronized (NmsParticles.class) {
            if (checked) return REGISTRY;
            List<String> have = new java.util.ArrayList<>();
            REGISTRY.holders().forEach(h -> have.add(h.key().location().toString()));
            List<String> want = Furnace.server().gameRegistries().builtIn("minecraft:particle_type");
            if (!have.equals(want)) throw new IllegalStateException("minecraft:particle_type differs from the server list: " + have + " vs " + want);
            checked = true;
        }
        return REGISTRY;
    }

    /** 종류 → "minecraft:…" 이름. 등록되지 않은 종류(플러그인이 만든 하위 클래스)면 IllegalArgumentException(바닐라 레지스트리 코덱과 같다). */
    public static String name(ParticleType<?> type, String at) {
        ResourceLocation l = registry().getKey(type);
        if (l == null) throw new IllegalArgumentException(at + ": can't find id for '" + type + "' in registry minecraft:particle_type");
        return l.toString();
    }

    /** shim 입자 → Furnace 입자. */
    public static Particle toApi(ParticleOptions p, String at) {
        if (p == null) throw new NullPointerException(at + ": null particle");
        String type = name(p.getType(), at);
        return switch (p) {
            case SimpleParticleType s -> Particle.of(type);
            case BlockParticleOption b -> Particle.block(type, EntityDataBridge.blockData(b.getState(), at));
            case DustParticleOptions d -> new Particle.Dust(d.getColor().x, d.getColor().y, d.getColor().z, d.getScale());
            case DustColorTransitionOptions d -> new Particle.DustTransition(d.getFromColor().x, d.getFromColor().y, d.getFromColor().z,
                    d.getToColor().x, d.getToColor().y, d.getToColor().z, d.getScale());
            case ColorParticleOption c -> new Particle.Color(type, c.furnaceColor());
            case ItemParticleOption i -> new Particle.Item(i.getItem().furnaceValue());
            case SculkChargeParticleOptions s -> new Particle.SculkCharge(s.roll());
            case ShriekParticleOption s -> new Particle.Shriek(s.getDelay());
            default -> throw new UnsupportedNmsCall(at, "the " + type + " particle cannot be converted to Furnace yet (" + p.getClass().getName() + ")");
        };
    }

    /** Furnace 입자 → shim 입자. 옵션 없는 종류는 바닐라처럼 종류 상수 그 자체다. */
    @SuppressWarnings("unchecked")
    public static ParticleOptions fromApi(Particle p, String at) {
        if (p == null) throw new NullPointerException(at + ": null particle");
        ParticleType<?> type = registry().getOptional(ResourceLocation.parse(p.type()))
                .orElseThrow(() -> new IllegalArgumentException(at + ": no " + p.type() + " in registry minecraft:particle_type"));
        return switch (p) {
            case Particle.Simple s -> {
                if (!(type instanceof SimpleParticleType simple)) throw new IllegalArgumentException(at + ": " + p.type() + " is a particle that needs options");
                yield simple;
            }
            case Particle.Block b -> new BlockParticleOption((ParticleType<BlockParticleOption>) type, SchemaPacketReverse.blockState(b.block(), at));
            case Particle.Dust d -> new DustParticleOptions(new org.joml.Vector3f(d.red(), d.green(), d.blue()), d.scale());
            case Particle.DustTransition d -> new DustColorTransitionOptions(new org.joml.Vector3f(d.fromRed(), d.fromGreen(), d.fromBlue()),
                    new org.joml.Vector3f(d.toRed(), d.toGreen(), d.toBlue()), d.scale());
            case Particle.Color c -> ColorParticleOption.create((ParticleType<ColorParticleOption>) type, c.argb());
            case Particle.Item i -> new ItemParticleOption((ParticleType<ItemParticleOption>) type,
                    (net.minecraft.world.item.ItemStack) SchemaPacketReverse.itemStack(i.item(), at));
            case Particle.SculkCharge s -> new SculkChargeParticleOptions(s.roll());
            case Particle.Shriek s -> new ShriekParticleOption(s.delay());
            case Particle.Vibration v -> throw new UnsupportedNmsCall(at, "vibration particles need position sources, which the shim does not have yet");
        };
    }
}
