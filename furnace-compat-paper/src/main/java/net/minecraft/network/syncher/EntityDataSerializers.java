package net.minecraft.network.syncher;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 엔티티 메타데이터 값 종류 표(Mojang 이름 shim): 바닐라 1.21.1 {@code EntityDataSerializers} 의 상수 이름과 제네릭 인자.
 * 패킷 라이브러리(ProtocolLib)가 이 필드들의 제네릭 인자로 "Java 형식 → 값 종류" 표를 만든다. 번호는 바닐라 등록 순서(0 부터)다 —
 * 아래 상수 선언 순서가 곧 번호 순서다. JOML 값(VECTOR3·QUATERNION)은 아직 없다(JOML 이 이 로더에 없다).
 */
public final class EntityDataSerializers {

    private EntityDataSerializers() {}

    /** 값 종류 → 바닐라 번호(등록 순서). 모르는 종류는 -1. */
    static final java.util.Map<EntityDataSerializer<?>, Integer> IDS = new java.util.IdentityHashMap<>();

    private static int next;

    private static <T> EntityDataSerializer<T> of(String name, UnaryOperator<T> copier) {
        int id = next++;
        EntityDataSerializer<T> s = new EntityDataSerializer<>() {
            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, T> codec() {
                throw new UnsupportedNmsCall("EntityDataSerializer#codec(" + name + ")", "메타데이터 값의 바이트 변환은 아직 연결되지 않았다(ProtocolLib 다리 3단계)");
            }

            @Override
            public T copy(T value) { return copier.apply(value); }

            @Override
            public String toString() { return "EntityDataSerializer[" + name + "]"; }
        };
        IDS.put(s, id);
        return s;
    }

    private static <T> EntityDataSerializer<T> same(String name) { return of(name, UnaryOperator.identity()); }

    // 순서가 바닐라 번호다(0 BYTE … 28 ARMADILLO_STATE). 29 VECTOR3·30 QUATERNION 은 건너뛴다.
    public static final EntityDataSerializer<Byte> BYTE = same("byte");
    public static final EntityDataSerializer<Integer> INT = same("int");
    public static final EntityDataSerializer<Long> LONG = same("long");
    public static final EntityDataSerializer<Float> FLOAT = same("float");
    public static final EntityDataSerializer<String> STRING = same("string");
    public static final EntityDataSerializer<Component> COMPONENT = same("component");
    public static final EntityDataSerializer<Optional<Component>> OPTIONAL_COMPONENT = same("optional_component");
    public static final EntityDataSerializer<ItemStack> ITEM_STACK = of("item_stack", ItemStack::copy);
    public static final EntityDataSerializer<Boolean> BOOLEAN = same("boolean");
    public static final EntityDataSerializer<Rotations> ROTATIONS = same("rotations");
    public static final EntityDataSerializer<BlockPos> BLOCK_POS = same("block_pos");
    public static final EntityDataSerializer<Optional<BlockPos>> OPTIONAL_BLOCK_POS = same("optional_block_pos");
    public static final EntityDataSerializer<Direction> DIRECTION = same("direction");
    public static final EntityDataSerializer<Optional<UUID>> OPTIONAL_UUID = same("optional_uuid");
    public static final EntityDataSerializer<BlockState> BLOCK_STATE = same("block_state");
    public static final EntityDataSerializer<Optional<BlockState>> OPTIONAL_BLOCK_STATE = same("optional_block_state");
    public static final EntityDataSerializer<CompoundTag> COMPOUND_TAG = of("compound_tag", CompoundTag::copy);
    public static final EntityDataSerializer<ParticleOptions> PARTICLE = same("particle");
    public static final EntityDataSerializer<List<ParticleOptions>> PARTICLES = same("particles");
    public static final EntityDataSerializer<VillagerData> VILLAGER_DATA = same("villager_data");
    public static final EntityDataSerializer<OptionalInt> OPTIONAL_UNSIGNED_INT = same("optional_unsigned_int");
    public static final EntityDataSerializer<Pose> POSE = same("pose");
    public static final EntityDataSerializer<Holder<CatVariant>> CAT_VARIANT = same("cat_variant");
    public static final EntityDataSerializer<Holder<WolfVariant>> WOLF_VARIANT = same("wolf_variant");
    public static final EntityDataSerializer<Holder<FrogVariant>> FROG_VARIANT = same("frog_variant");
    public static final EntityDataSerializer<Optional<GlobalPos>> OPTIONAL_GLOBAL_POS = same("optional_global_pos");
    public static final EntityDataSerializer<Holder<PaintingVariant>> PAINTING_VARIANT = same("painting_variant");
    public static final EntityDataSerializer<Sniffer.State> SNIFFER_STATE = same("sniffer_state");
    public static final EntityDataSerializer<Armadillo.ArmadilloState> ARMADILLO_STATE = same("armadillo_state");

    /** 바닐라 {@code getSerializedId}: 값 종류의 번호, 모르면 -1. */
    public static int getSerializedId(EntityDataSerializer<?> serializer) {
        Integer id = IDS.get(serializer);
        return id == null ? -1 : id;
    }
}
