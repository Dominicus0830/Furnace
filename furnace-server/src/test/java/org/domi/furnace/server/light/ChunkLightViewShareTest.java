// T-205: 공개 스냅샷에 넘긴 빛 층은 서버가 더 고치지 않는다(쓰기 시 복사) — 스냅샷은 넘긴 순간의 값을 그대로 든다.
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkLightViewShareTest {

    private static byte[][] refs() { return new byte[ChunkLight.SECTIONS][]; }

    @Test
    void sharedLayersAreCopiedOnNextWrite() {
        ChunkLight cl = new ChunkLight();
        cl.setBlock(1, 2, 3, 9);
        cl.setSky(4, 5, 6, 7);
        int s = ChunkLight.sectionIndex(2);
        byte[][] sky = refs(), block = refs();
        cl.shareLayersWithView(sky, block);
        assertSame(cl.block(s), block[s], "넘길 때는 복사하지 않는다");
        assertTrue(cl.sameLayers(sky, block));

        byte[] before = block[s].clone();
        cl.setBlock(1, 2, 3, 3);
        assertEquals(3, cl.blockAt(1, 2, 3));
        assertEquals(9, ChunkLight.get(block[s], 1, 2, 3), "스냅샷이 쥔 층은 그대로");
        assertTrue(java.util.Arrays.equals(before, block[s]));
        assertNotSame(block[s], cl.block(s), "쓰기가 복사했다");
        assertFalse(cl.sameLayers(sky, block), "바뀐 층은 참조가 다르다");

        byte[] mine = cl.block(s);
        cl.setBlock(2, 2, 3, 4);
        assertSame(mine, cl.block(s), "복사한 뒤로는 그 자리에서 쓴다(넘기기 전까지 한 번만 복사)");
        assertEquals(7, ChunkLight.get(sky[ChunkLight.sectionIndex(5)], 4, 5, 6), "다른 층은 그대로");
    }

    @Test
    void writeCopyPublishAndAdoptDropTheViewMark() {
        ChunkLight visible = new ChunkLight();
        visible.setBlock(0, 0, 0, 5);
        int s = ChunkLight.sectionIndex(0);
        byte[][] sky = refs(), block = refs();
        visible.shareLayersWithView(sky, block);
        // 빛 작업(T-064): 쓰기 사본은 원본 층을 고치지 않고, 게시는 새 층으로 바꿔 끼운다 — 그 새 층은 스냅샷 것이 아니다
        ChunkLight copy = ChunkLight.writeCopyOf(visible);
        copy.setBlock(0, 0, 0, 6);
        assertEquals(5, ChunkLight.get(block[s], 0, 0, 0));
        copy.publishTo(visible);
        assertEquals(6, visible.blockAt(0, 0, 0));
        assertEquals(5, ChunkLight.get(block[s], 0, 0, 0));
        byte[] published = visible.block(s);
        visible.setBlock(1, 0, 0, 2);
        assertSame(published, visible.block(s), "게시로 받은 층은 스냅샷이 쥐지 않았다 — 그 자리에서 쓴다");

        visible.shareLayersWithView(sky, block);
        ChunkLight fresh = new ChunkLight();
        fresh.setBlock(0, 0, 0, 11);
        visible.adoptLayersFrom(fresh);
        byte[] adopted = visible.block(s);
        visible.setBlock(3, 0, 0, 1);
        assertSame(adopted, visible.block(s), "갈아 끼운 층은 스냅샷이 쥐지 않았다");
        assertEquals(6, ChunkLight.get(block[s], 0, 0, 0), "스냅샷은 넘긴 때 값");
        assertTrue(ChunkLight.sharedIntact());
    }
}
