package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.MapColor;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-212 3단계: FAWE 옛 도식 변환기({@code PaperweightDataConverters} — 책·표지판 글, 침대 아이템)와 {@code getRichItemName} 이 부르는 도우미.
 * 기대값은 바닐라 1.21.1 jar(GsonHelper·StringUtil·DyeColor·Items·Potions 바이트코드, en_us 번역 키)에서 확인한 것이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsLegacyHelpersTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() { server = FurnaceTestServer.builder().failFast(false).start(); }

    @AfterAll
    void stop() { server.close(); }

    /** FAWE DataConverterSignText.a 와 같은 꼴: Component 는 글자 하나면 literal, 배열이면 이어 붙인다. */
    static final Gson SIGN = new GsonBuilder().registerTypeAdapter(Component.class, (JsonDeserializer<Component>) (json, t, ctx) -> {
        if (json.isJsonPrimitive()) return Component.literal(json.getAsString());
        throw new JsonParseException("Don't know how to turn " + json + " into a Component");
    }).create();

    @Test
    void gsonHelperReadsLikeVanilla() {
        assertEquals("Hello", GsonHelper.fromJson(SIGN, "\"Hello\"", Component.class, true).getString());
        assertEquals("abc", GsonHelper.fromJson(new Gson(), "abc", String.class, true), "lenient 은 따옴표 없는 글자를 받는다");
        assertThrows(JsonParseException.class, () -> GsonHelper.fromJson(new Gson(), "abc", String.class, false), "strict 은 IO 오류를 JsonParseException 으로");
        var nul = assertThrows(JsonParseException.class, () -> GsonHelper.fromJson(new Gson(), "null", String.class, true));
        assertEquals("JSON data was null or empty", nul.getMessage());
        assertNull(GsonHelper.fromNullableJson(new Gson(), "null", String.class, true));
        assertThrows(JsonParseException.class, () -> GsonHelper.fromJson(SIGN, "{\"text\":\"x\"}", Component.class, true), "어댑터의 거절은 그대로");
    }

    @Test
    void stringUtilIsNullOrEmpty() {
        assertTrue(StringUtil.isNullOrEmpty(null));
        assertTrue(StringUtil.isNullOrEmpty(""));
        assertFalse(StringUtil.isNullOrEmpty(" "), "공백은 비지 않았다(StringUtils.isEmpty)");
    }

    @Test
    void dyeColorsMatchVanilla() {
        assertEquals(14, DyeColor.RED.getId(), "FAWE 침대 아이템 변환기: 옛 침대의 기본 색");
        assertEquals(16, DyeColor.values().length);
        for (DyeColor c : DyeColor.values()) assertSame(c, DyeColor.byId(c.getId()));
        assertSame(DyeColor.WHITE, DyeColor.byId(16), "범위 밖은 흰색");
        assertSame(DyeColor.WHITE, DyeColor.byId(-1));
        assertSame(DyeColor.LIGHT_BLUE, DyeColor.byName("light_blue", null));
        assertSame(DyeColor.BLACK, DyeColor.byName("nope", DyeColor.BLACK));
        assertEquals("light_gray", DyeColor.LIGHT_GRAY.getSerializedName());
        assertEquals("light_gray", DyeColor.LIGHT_GRAY.toString());
        assertEquals(0xFFF9FFFE, DyeColor.WHITE.getTextureDiffuseColor(), "천 색은 불투명 알파");
        assertEquals(0x0000FF, DyeColor.BLUE.getTextColor());
        assertSame(DyeColor.GREEN, DyeColor.byFireworkColor(0x3B511A));
        assertNull(DyeColor.byFireworkColor(0x123456));
        assertSame(MapColor.COLOR_LIGHT_GREEN, DyeColor.LIME.getMapColor());
    }

    static ItemStack stack(String id, Map<String, String> components) {
        return ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack(id, 1, components, Set.of()), 1);
    }

    @Test
    void itemDescriptionIdsFollowTheItemKind() {
        assertEquals("block.minecraft.stone", Item.byKey("minecraft:stone").getDescriptionId(), "블록 아이템은 블록 키");
        assertEquals("block.minecraft.oak_sign", Item.byKey("minecraft:oak_sign").getDescriptionId());
        assertEquals("block.minecraft.air", Item.byKey("minecraft:air").getDescriptionId());
        assertEquals("item.minecraft.diamond_sword", Item.byKey("minecraft:diamond_sword").getDescriptionId());
        assertEquals("item.minecraft.wheat", Item.byKey("minecraft:wheat").getDescriptionId(), "밀 블록이 있어도 밀 아이템은 그냥 Item");
        assertEquals("item.minecraft.nether_wart", Item.byKey("minecraft:nether_wart").getDescriptionId(), "ItemNameBlockItem");
        assertEquals("item.minecraft.wheat_seeds", Item.byKey("minecraft:wheat_seeds").getDescriptionId());
        assertEquals("item.minecraft.powder_snow_bucket", Item.byKey("minecraft:powder_snow_bucket").getDescriptionId());
        assertEquals("block.minecraft.stone", stack("minecraft:stone", Map.of()).getDescriptionId());
    }

    @Test
    void stackDescriptionIdsOfPotionsShieldsAndCompasses() {
        assertEquals("item.minecraft.potion.effect.swiftness",
                stack("minecraft:potion", Map.of("minecraft:potion_contents", "{potion:\"minecraft:long_swiftness\"}")).getDescriptionId());
        assertEquals("item.minecraft.splash_potion.effect.water",
                stack("minecraft:splash_potion", Map.of("minecraft:potion_contents", "\"minecraft:water\"")).getDescriptionId(), "짧은 꼴");
        assertEquals("item.minecraft.lingering_potion.effect.turtle_master",
                stack("minecraft:lingering_potion", Map.of("minecraft:potion_contents", "{potion:\"minecraft:strong_turtle_master\"}")).getDescriptionId());
        assertEquals("item.minecraft.tipped_arrow.effect.empty", stack("minecraft:tipped_arrow", Map.of()).getDescriptionId(), "물약이 없으면 empty");
        assertEquals("item.minecraft.potion.effect.empty",
                stack("minecraft:potion", Map.of("minecraft:potion_contents", "{custom_color:255}")).getDescriptionId());
        assertEquals("item.minecraft.shield.red", stack("minecraft:shield", Map.of("minecraft:base_color", "\"red\"")).getDescriptionId());
        assertEquals("item.minecraft.shield", stack("minecraft:shield", Map.of()).getDescriptionId());
        assertEquals("item.minecraft.lodestone_compass",
                stack("minecraft:compass", Map.of("minecraft:lodestone_tracker", "{tracked:0b}")).getDescriptionId());
        assertEquals("item.minecraft.compass", stack("minecraft:compass", Map.of()).getDescriptionId());
    }
}
