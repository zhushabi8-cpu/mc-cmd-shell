package com.ppp.common;

import com.ppp.util.ServerLanguageManage;
import net.minecraft.server.command.ServerCommandSource;

import java.util.UUID;

public class Unsupported implements ICmdExecutor {
    @Override
    public boolean startSession(UUID playerUuid, ServerCommandSource source) {
        source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.unsupported_os"));
        return false;
    }

    @Override
    public void executeCommand(String cmd, UUID playerUuid, ServerCommandSource source) {
        source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.unsupported_os"));
    }

    @Override
    public void closeSession(UUID playerUuid) {
    }
}