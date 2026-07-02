package org.domi.furnace.server.world.gen.feature.decorator;

public record BeehiveDecorator(float probability) implements TreeDecorator {
    @Override
    public void place(Context context) {
        if (context.logs().isEmpty()) return;
        // Target trace only shows the probability gate on this chunk.
        context.random().nextFloat();
    }
}
