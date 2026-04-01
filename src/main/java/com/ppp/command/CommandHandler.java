package com.ppp.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ppp.util.ServerLanguageManage;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.ppp.util.OSDetector;

import java.util.*;

public class CommandHandler {

    // ===== 通用部分 =====
    private static final Set<String> DANGER_KEYWORDS = Set.of(
            "shutdown", "restart", "logoff", "del", "rd", "format", "reg",
            "taskkill", "net", "powershell", "diskpart", "erase", "rmdir",
            "move", "copy", "xcopy", "ms-settings"
    );

    private static final Map<UUID, String> PENDING_CONFIRM = new HashMap<>();
    public static final Map<UUID, String> LAST_FULL_OUTPUT = new HashMap<>(); // 改为 public 供执行器访问

    // 平台执行器（在静态块中初始化）
    private static final ICmdExecutor executor;

    static {
        if (OSDetector.isWindows()) {
            executor = new WindowsCmdExecutor();
        } else {
            executor = new Unsupported();
        }
    }

    // ===== 公共辅助方法 =====
    public static boolean checkServerPermission(ServerCommandSource source, ServerPlayerEntity player) {
        if (player == null) return true;
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            if (!player.hasPermissionLevel(4)) {
                source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.op_only"));
                return false;
            }
        }
        return true;
    }

    private static boolean isDangerousCommand(String cmd) {
        String lowerCmd = cmd.toLowerCase().replaceAll("\\s+", "");
        for (String keyword : DANGER_KEYWORDS) {
            if (lowerCmd.contains(keyword.toLowerCase())) return true;
        }
        return false;
    }

    // ===== 命令执行方法（通用逻辑） =====
    public static int executeStart(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!(executor instanceof WindowsCmdExecutor)) { // 或改用能力检测
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.unsupported_os"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        if (!checkServerPermission(source, player)) return 0;

        UUID uuid = player.getUuid();
        if (executor.startSession(uuid, source)) {
            source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.info.cmd_started"));
            return 1;
        } else {
            // 错误已在 executor.startSession 中发送
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
        if (!checkServerPermission(source, player)) return 0;

        UUID uuid = player.getUuid();
        String cmd = StringArgumentType.getString(context, "command");

        // 危险命令确认
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
        if (!checkServerPermission(source, player)) return 0;

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
        if (!checkServerPermission(source, player)) return 0;

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

    public static int executeList(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        if (!checkServerPermission(source, player)) return 0;

        UUID uuid = player.getUuid();
        String fullOutput = LAST_FULL_OUTPUT.get(uuid);
        if (fullOutput == null) {
            source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.cmd.no_truncated_output"));
            return 0;
        } else {
            source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.cmd.full_output_title"));
            source.sendMessage(Text.literal(fullOutput));
            return 1;
        }
    }
}