package org.bukkit.permissions;

/** 첨부가 떼어질 때 불린다. */
public interface PermissionRemovedExecutor {

    void attachmentRemoved(PermissionAttachment attachment);
}
