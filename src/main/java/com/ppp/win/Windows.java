package com.ppp.win;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ppp.common.Common;
import com.ppp.common.ICmdExecutor;
import com.ppp.common.Unsupported;
import com.ppp.util.PaginationManager;
import com.ppp.util.ServerLanguageManage;
import com.ppp.util.OSDetector;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public class Windows {

    private static final Set<String> DANGER_KEYWORDS = Set.of(
            "shutdown", "restart", "logoff", "del", "rd", "format", "reg",
            "taskkill", "net", "powershell", "diskpart", "erase", "rmdir",
            "move", "copy", "xcopy", "ms-settings"
    );

    private static final Map<UUID, String> PENDING_CONFIRM = new HashMap<>();
    private static final ICmdExecutor executor;

    static {
        if (OSDetector.isWindows()) {
            executor = new WindowsCmdExecutor();
        } else {
            executor = new Unsupported();
        }
    }

    private static boolean isDangerousCommand(String cmd) {
        String lowerCmd = cmd.toLowerCase().replaceAll("\\s+", "");
        for (String keyword : DANGER_KEYWORDS) {
            if (lowerCmd.contains(keyword.toLowerCase())) return true;
        }
        return false;
    }

    public static int executeStart(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(executor instanceof WindowsCmdExecutor)) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.unsupported_os"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        if (!Common.checkServerPermission(source, player)) return 0;

        UUID uuid = player.getUuid();
        if (executor.startSession(uuid, source)) {
            source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.info.cmd_started"));
            return 1;
        } else {
            return 0;
        }
    }

    public static int executeRun(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(executor instanceof WindowsCmdExecutor)) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.unsupported_os"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        if (!Common.checkServerPermission(source, player)) return 0;

        UUID uuid = player.getUuid();
        String cmd = StringArgumentType.getString(context, "command");

        if (isDangerousCommand(cmd)) {
            PENDING_CONFIRM.put(uuid, cmd);
            source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.warning.dangerous_command"));
            source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.info.confirm"));
            return 0;
        }

        executor.executeCommand(cmd, uuid, source);
        return 1;
    }

    public static int executeStop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(executor instanceof WindowsCmdExecutor)) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.unsupported_os"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        if (!Common.checkServerPermission(source, player)) return 0;

        executor.closeSession(player.getUuid());
        source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.info.cmd_stopped"));
        return 1;
    }

    public static int executeConfirm(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(executor instanceof WindowsCmdExecutor)) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.unsupported_os"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        if (!Common.checkServerPermission(source, player)) return 0;

        UUID uuid = player.getUuid();
        String pendingCmd = PENDING_CONFIRM.remove(uuid);
        if (pendingCmd == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.nothing_to_confirm"));
            return 0;
        }

        executor.executeCommand(pendingCmd, uuid, source);
        source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.info.executed_dangerous", pendingCmd));
        return 1;
    }

    public static int executeClear(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        if (!Common.checkServerPermission(source, player)) return 0;

        UUID uuid = player.getUuid();
        PENDING_CONFIRM.remove(uuid);
        PaginationManager.clear(uuid);
        source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.info.cleared"));
        return 1;
    }
}