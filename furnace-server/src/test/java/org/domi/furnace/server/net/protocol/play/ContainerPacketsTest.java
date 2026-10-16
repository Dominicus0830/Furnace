// OpenScreen·ContainerSetContent·ContainerClose 바이트 인코딩 검증(M4c-3 슬라이스 B)
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class ContainerPacketsTest {

    @Test
    void openScreen_id_and_wireLayout() {
        assertEquals(0x33, OpenScreen.ID);
        OpenScreen p = new OpenScreen(3, 2, "Chest");
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(3, Types.readVarInt(buf));   // containerId
        assertEquals(2, Types.readVarInt(buf));   // menuTypeId(GENERIC_9x3)
        assertEquals(0x0a, buf.readByte());       // title NBT: 루트 compound
        assertEquals(0x08, buf.readByte());       // "text": TAG_STRING
        assertEquals(4, buf.readUnsignedShort());
        assertEquals("text", readAscii(buf, 4));
        assertEquals(5, buf.readUnsignedShort());
        assertEquals("Chest", readAscii(buf, 5));
        assertEquals(0x00, buf.readByte());       // 루트 종료
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    void containerSetContent_id_and_emptyItems() {
        assertEquals(0x13, ContainerSetContent.ID);
        ContainerSetContent p = new ContainerSetContent(3, 0,
                new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY}, ItemStack.EMPTY);
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(3, Types.readVarInt(buf));   // containerId
        assertEquals(0, Types.readVarInt(buf));   // stateId
        assertEquals(2, Types.readVarInt(buf));   // items.length
        assertEquals(0, Types.readVarInt(buf));   // slot0 empty(count=0)
        assertEquals(0, Types.readVarInt(buf));   // slot1 empty
        assertEquals(0, Types.readVarInt(buf));   // carried empty
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    void containerSetContent_nonEmptyItem_noComponents_writesZeroAddRemove() {
        ItemStack stack = new ItemStack(5, 16, null);
        ContainerSetContent p = new ContainerSetContent(1, 0, new ItemStack[]{stack}, ItemStack.EMPTY);
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        Types.readVarInt(buf); // containerId
        Types.readVarInt(buf); // stateId
        Types.readVarInt(buf); // items.length
        assertEquals(16, Types.readVarInt(buf));  // count
        assertEquals(5, Types.readVarInt(buf));   // itemId
        assertEquals(0, Types.readVarInt(buf));   // numComponentsToAdd
        assertEquals(0, Types.readVarInt(buf));   // numComponentsToRemove
        assertEquals(0, Types.readVarInt(buf));   // carried empty
        buf.release();
    }

    @Test
    void containerSetContent_componentPatch_writtenLikeVanilla() {
        // custom_model_data(종류 id 13) = 7 하나를 더한 패치
        ItemStack stack = new ItemStack(7, 1, org.domi.furnace.server.item.ComponentPatch.of(java.util.Map.of("minecraft:custom_model_data", 7)));
        ContainerSetContent p = new ContainerSetContent(1, 0, new ItemStack[]{stack}, ItemStack.EMPTY);
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        Types.readVarInt(buf); // containerId
        Types.readVarInt(buf); // stateId
        Types.readVarInt(buf); // items.length
        assertEquals(1, Types.readVarInt(buf));   // count
        assertEquals(7, Types.readVarInt(buf));   // itemId
        assertEquals(1, Types.readVarInt(buf));   // 더한 수
        assertEquals(0, Types.readVarInt(buf));   // 지운 수
        assertEquals(13, Types.readVarInt(buf));  // custom_model_data
        assertEquals(7, Types.readVarInt(buf));   // 값
        buf.release();
    }

    @Test
    void containerClose_id_and_wireLayout() {
        assertEquals(0x12, ContainerClose.ID);
        ContainerClose p = new ContainerClose(4);
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(4, Types.readVarInt(buf));
        assertFalse(buf.isReadable());
        buf.release();
    }

    private static String readAscii(ByteBuf buf, int len) {
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
