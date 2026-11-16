// 바닐라 CommandQueueEntry(ho) — (프레임, 할 일).
package org.domi.furnace.server.command.exec;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.commands.execution.CommandQueueEntry")
public record CommandQueueEntry(Frame frame, EntryAction action) {
    public void execute(ExecutionContext context) { action.execute(context, frame); }
}
