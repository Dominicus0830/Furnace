package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import net.minecraft.core.Holder;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.CraftSound;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 2026-11-14 ProtocolLib 점검에서 빠져 있던 두 shim: NBT {@code TagTypes}/{@code TagType}(NbtFactory 가 태그 클래스를 찾는 길)와
 * {@code CraftSound}(소리 패킷 값 변환). 이름·표시 이름·isValue 는 바닐라 1.21.1 jar 의 문자열 상수와 대조했다.
 */
class NbtTypesAndSoundShimTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() { server = FurnaceTestServer.builder().start(); }

    @AfterAll
    static void stop() { server.close(); }

    @Test
    void tagTypesTableMatchesVanilla() {
        List<Class<?>> classes = List.of(EndTag.class, ByteTag.class, ShortTag.class, IntTag.class, LongTag.class, FloatTag.class, DoubleTag.class,
                ByteArrayTag.class, StringTag.class, ListTag.class, CompoundTag.class, IntArrayTag.class, LongArrayTag.class);
        List<String> names = List.of("END", "BYTE", "SHORT", "INT", "LONG", "FLOAT", "DOUBLE", "BYTE[]", "STRING", "LIST", "COMPOUND", "INT[]", "LONG[]");
        List<String> pretty = List.of("TAG_End", "TAG_Byte", "TAG_Short", "TAG_Int", "TAG_Long", "TAG_Float", "TAG_Double", "TAG_Byte_Array",
                "TAG_String", "TAG_List", "TAG_Compound", "TAG_Int_Array", "TAG_Long_Array");
        for (int id = 0; id < classes.size(); id++) {
            var t = TagTypes.getType(id);
            // ProtocolLib NbtFactory: TagTypes.getType(n).getClass().getEnclosingClass() 가 태그 클래스여야 한다
            assertSame(classes.get(id), t.getClass().getEnclosingClass(), "번호 " + id);
            assertEquals(names.get(id), t.getName());
            assertEquals(pretty.get(id), t.getPrettyName());
            boolean value = id <= 6 || id == 8;   // 끝·수·문자열
            assertEquals(value, t.isValue(), "isValue " + id);
        }
        assertEquals("INVALID[13]", TagTypes.getType(13).getName());
        assertEquals("UNKNOWN_-1", TagTypes.getType(-1).getPrettyName());
        assertFalse(TagTypes.getType(99).isValue());
        assertSame(CompoundTag.TYPE, new CompoundTag().getType());
        assertSame(IntTag.TYPE, IntTag.valueOf(3).getType());
    }

    @Test
    void craftSoundConvertsBothWays() {
        SoundEvent pig = CraftSound.bukkitToMinecraft(Sound.ENTITY_PIG_AMBIENT);
        assertEquals(ResourceLocation.withDefaultNamespace("entity.pig.ambient"), pig.getLocation());
        assertSame(Sound.ENTITY_PIG_AMBIENT, CraftSound.minecraftToBukkit(pig));
        Holder<SoundEvent> h = CraftSound.bukkitToMinecraftHolder(Sound.BLOCK_NOTE_BLOCK_HARP);
        assertTrue(h instanceof Holder.Reference<SoundEvent>);
        assertSame(Sound.BLOCK_NOTE_BLOCK_HARP, CraftSound.minecraftHolderToBukkit(h));
        for (Sound s : Sound.values()) assertSame(s, CraftSound.minecraftToBukkit(CraftSound.bukkitToMinecraft(s)), s.name());
    }

    @Test
    void unregisteredSoundFailsLikePaper() {
        // Paper: 레지스트리에 없는 직접 만든 소리는 Optional.orElseThrow 의 NoSuchElementException(ProtocolLib 은 이것을 잡아 null 로 바꾼다)
        SoundEvent custom = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("myplugin", "boom"));
        assertThrows(NoSuchElementException.class, () -> CraftSound.minecraftToBukkit(custom));
        assertThrows(IllegalArgumentException.class, () -> CraftSound.minecraftToBukkit(null));
    }
}
