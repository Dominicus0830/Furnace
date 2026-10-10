package org.domi.furnace.server.world.gen;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import jdk.jfr.Recording;
import jdk.jfr.consumer.*;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FullGenProfile {
    @Test
    void profile() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.profile"));
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(12345L, blocks, registries);
        for (int i = 0; i < 10; i++) source.generateWithFeatures(-500 + i, 500);
        Path out = Files.createTempFile("gen", ".jfr");
        try (Recording r = new Recording()) {
            r.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(5));
            r.start();
            ExecutorService ex = Executors.newFixedThreadPool(4);
            List<Future<?>> fs = new ArrayList<>();
            for (int col = 0; col < 4; col++) for (int row = -10; row <= 10; row++) { int c0 = col, r0 = row; fs.add(ex.submit(() -> source.generateWithFeatures(600 + c0, r0))); }
            for (Future<?> f : fs) f.get();
            ex.shutdown();
            r.stop();
            r.dump(out);
        }
        Map<String, Integer> self = new HashMap<>(), incl = new HashMap<>(), callers = new HashMap<>();
        int total = 0;
        for (RecordedEvent e : RecordingFile.readAllEvents(out)) {
            if (!e.getEventType().getName().equals("jdk.ExecutionSample")) continue;
            RecordedStackTrace st = e.getStackTrace();
            if (st == null || st.getFrames().isEmpty()) continue;
            total++;
            RecordedFrame top = st.getFrames().get(0);
            self.merge(name(top), 1, Integer::sum);
            Set<String> seen = new HashSet<>();
            for (RecordedFrame f : st.getFrames()) { String n = name(f); if (f.getMethod().getType().getName().contains("furnace") && seen.add(n)) incl.merge(n, 1, Integer::sum); }
            if (name(top).equals("HashMap$TreeNode.find") || name(top).equals("ArraysSupport.mismatch")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(8, st.getFrames().size()); i++) sb.append(name(st.getFrames().get(i))).append(" < ");
                callers.merge(sb.toString(), 1, Integer::sum);
            }
        }
        System.out.println("PROF total samples " + total);
        int t = total;
        self.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25)
            .forEach(en -> System.out.printf("PROF self %5.1f%% %s%n", 100.0 * en.getValue() / t, en.getKey()));
        incl.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(45)
            .forEach(en -> System.out.printf("PROF incl %5.1f%% %s%n", 100.0 * en.getValue() / t, en.getKey()));
        callers.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6)
            .forEach(en -> System.out.printf("PROF hot %d %s%n", en.getValue(), en.getKey()));
    }

    private static String name(RecordedFrame f) {
        RecordedMethod m = f.getMethod();
        String c = m.getType().getName();
        return c.substring(c.lastIndexOf('.') + 1) + "." + m.getName();
    }
}
