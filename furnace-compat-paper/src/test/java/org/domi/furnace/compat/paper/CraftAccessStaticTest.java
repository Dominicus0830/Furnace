package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.domi.furnace.compat.shimgen.CraftHierarchyLock;
import org.junit.jupiter.api.Test;

/**
 * T-264 설계 3.1 [검토 3]: Paper 에서 패키지 전용인 Craft 클래스는 Furnace 에서도 패키지 전용이고, 밖(craftbukkit 꾸러미 밖)에서는
 * {@link CraftAccess} 한 곳만 그 이름을 든다. CraftAccess 의 핸들은 static final 이다.
 */
class CraftAccessStaticTest {

    @Test
    void onlyCraftAccessNamesPackagePrivateCraftClasses() throws Exception {
        List<String> hidden = new ArrayList<>();
        for (CraftHierarchyLock.Entry e : CraftHierarchyLock.read(Path.of("src/shimspec/craft-hierarchy-1.21.1.lock")).values()) {
            if (!e.name().startsWith(CraftHierarchyLock.PREFIX) || e.name().contains("$") || (e.access() & 0x1) != 0) continue;
            hidden.add(e.name());
        }
        assertTrue(!hidden.isEmpty(), "the lock lists package-private Craft classes");
        Path root = Path.of(CraftAccess.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String rel = root.relativize(f).toString().replace('\\', '/');
                if (rel.startsWith(CraftHierarchyLock.PREFIX) || rel.startsWith("org/domi/furnace/compat/paper/CraftAccess")) continue;
                String text = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
                for (String h : hidden) {
                    if (text.contains(h + ";") || text.contains(h.replace('/', '.'))) bad.add(rel + " -> " + h);
                }
            }
        }
        assertEquals(List.of(), bad, "package-private Craft classes are named outside CraftAccess");
    }

    @Test
    void handlesAreStaticFinal() {
        for (Field f : CraftAccess.class.getDeclaredFields()) {
            if (f.getType() == MethodHandle.class || f.getType() == Class.class) {
                assertTrue(Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()), f.getName());
            }
        }
    }

    @Test
    void generatedPackagePrivateClassesStayPackagePrivate() throws IOException {
        assertTrue(!Modifier.isPublic(CraftAccess.CRAFT_MINECART_MOB_SPAWNER.getModifiers()));
        assertEquals("org.bukkit.craftbukkit.entity.CraftMinecartMobSpawner", CraftAccess.CRAFT_MINECART_MOB_SPAWNER.getName());
    }
}
