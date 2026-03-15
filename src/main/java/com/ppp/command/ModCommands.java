package com.ppp.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ModCommands {

    private static final Set<String> DANGER_KEYWORDS = Set.of(
            "shutdown", "restart", "logoff", "del", "rd", "format", "reg",
            "taskkill", "net", "powershell", "diskpart", "erase", "rmdir",
            "move", "copy", "xcopy", "ms-settings"
    );

    private static final Map<UUID, String> PENDING_CONFIRM = new HashMap<>();
    private static final Map<UUID, CmdProcessHolder> PLAYER_CMD_PROCESSES = new HashMap<>();
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("cmd")
                    .then(literal("start")
                            .executes(ModCommands::executeStart))
                    .then(literal("run")
                            .then(argument("command", StringArgumentType.greedyString())
                                    .executes(ModCommands::executeRun)))
                    .then(literal("stop")
                            .executes(ModCommands::executeStop))
            );
            dispatcher.register(literal("cmdconfirm")
                    .executes(ModCommands::executeConfirm));
        });
    }

    private static int executeStart(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        UUID uuid = player.getUuid();
        if (PLAYER_CMD_PROCESSES.containsKey(uuid)) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.cmd_already_running"));
            return 0;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/q", "/k");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            PLAYER_CMD_PROCESSES.put(uuid, new CmdProcessHolder(process));
            source.sendMessage(Text.translatable("text.mc-cmd-shell.info.cmd_started"));
            return 1;
        } catch (IOException e) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.start_failed", e.getMessage()));
            return 0;
        }
    }

    private static int executeRun(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        if (!player.getServer().isSingleplayer() && !player.hasPermissionLevel(4)) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.op_only"));
            return 0;
        }
        UUID uuid = player.getUuid();
        CmdProcessHolder holder = PLAYER_CMD_PROCESSES.get(uuid);
        if (holder == null) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.no_process"));
            return 0;
        }
        String cmd = StringArgumentType.getString(context, "command");
        if (isDangerousCommand(cmd)) {
            PENDING_CONFIRM.put(uuid, cmd);
            source.sendMessage(Text.translatable("text.mc-cmd-shell.warning.dangerous_command"));
            source.sendMessage(Text.translatable("text.mc-cmd-shell.info.confirm"));
            return 0;
        }
        // executeCommand 现在返回 Text 对象
        Text output = holder.executeCommand(cmd);
        source.sendMessage(output);
        return 1;
    }

    private static int executeStop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        UUID uuid = player.getUuid();
        CmdProcessHolder holder = PLAYER_CMD_PROCESSES.get(uuid);
        if (holder == null) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.no_process_to_stop"));
            return 0;
        }
        holder.close();
        PLAYER_CMD_PROCESSES.remove(uuid);
        source.sendMessage(Text.translatable("text.mc-cmd-shell.info.cmd_stopped"));
        return 1;
    }

    private static int executeConfirm(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        UUID uuid = player.getUuid();
        String pendingCmd = PENDING_CONFIRM.remove(uuid);
        if (pendingCmd == null) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.nothing_to_confirm"));
            return 0;
        }
        CmdProcessHolder holder = PLAYER_CMD_PROCESSES.get(uuid);
        if (holder == null) {
            source.sendError(Text.translatable("text.mc-cmd-shell.error.no_process"));
            return 0;
        }
        Text output = holder.executeCommand(pendingCmd);
        source.sendMessage(Text.translatable("text.mc-cmd-shell.info.executed_dangerous", pendingCmd));
        source.sendMessage(output);
        return 1;
    }

    private static boolean isDangerousCommand(String cmd) {
        String lowerCmd = cmd.toLowerCase().replaceAll("\\s+", "");
        for (String keyword : DANGER_KEYWORDS) {
            if (lowerCmd.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static class CmdProcessHolder {
        private final Process process;
        private final BufferedWriter input;
        private final StringBuilder latestOutput = new StringBuilder();

        public CmdProcessHolder(Process process) throws IOException {
            this.process = process;
            this.input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "GBK"), 1);
            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            ASYNC_EXECUTOR.submit(() -> {
                String line;
                try {
                    while ((line = outputReader.readLine()) != null) {
                        if (!line.trim().endsWith(">") && !line.trim().isEmpty()) {
                            latestOutput.append(line).append("\n");
                        }
                    }
                } catch (IOException e) {
                    // 异步异常也使用可翻译文本，但这里直接转成字符串，最终作为命令输出的一部分
                    latestOutput.append(Text.translatable("text.mc-cmd-shell.cmd.read_exception", e.getMessage()).getString()).append("\n");
                }
            });
        }

        // 修改返回类型为 Text
        public Text executeCommand(String cmd) {
            try {
                latestOutput.setLength(0);
                input.write(cmd + "\n");
                input.flush();
                Thread.sleep(800);
                String result = latestOutput.toString().trim();
                if (result.isEmpty()) {
                    return Text.translatable("text.mc-cmd-shell.cmd.no_output");
                } else {
                    // 将实际输出作为参数嵌入到翻译文本中
                    return Text.translatable("text.mc-cmd-shell.cmd.output_prefix", result);
                }
            } catch (IOException e) {
                return Text.translatable("text.mc-cmd-shell.cmd.send_failed", e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Text.translatable("text.mc-cmd-shell.cmd.timeout", e.getMessage());
            }
        }

        public void close() {
            try {
                if (input != null) input.close();
                if (process != null) {
                    process.destroy();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}