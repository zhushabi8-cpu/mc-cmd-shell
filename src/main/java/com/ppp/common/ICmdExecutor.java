package com.ppp.common;

import net.minecraft.server.command.ServerCommandSource;

import java.util.UUID;

public interface ICmdExecutor {
    boolean startSession(UUID playerUuid, ServerCommandSource source);

    void executeCommand(String cmd, UUID playerUuid, ServerCommandSource source);

    void closeSession(UUID playerUuid);
}