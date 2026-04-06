package com.ppp.command;

import com.ppp.util.ServerLanguageManage;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class WindowsCmdExecutor implements ICmdExecutor {
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool();
    private final Map<UUID, CmdProcessHolder> processes = new HashMap<>();

    @Override
    public boolean startSession(UUID playerUuid, ServerCommandSource source) {
        if (processes.containsKey(playerUuid)) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/q", "/k");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            processes.put(playerUuid, new CmdProcessHolder(process));
            return true;
        } catch (IOException e) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.start_failed", e.getMessage()));
            return false;
        }
    }

    @Override
    public void executeCommand(String cmd, UUID playerUuid, ServerCommandSource source) {
        CmdProcessHolder holder = processes.get(playerUuid);
        if (holder == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.no_process"));
            return;
        }
        holder.executeCommandAsync(cmd, playerUuid, source);
    }

    @Override
    public void closeSession(UUID playerUuid) {
        CmdProcessHolder holder = processes.remove(playerUuid);
        if (holder != null) holder.close();
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
                    latestOutput.append(ServerLanguageManage.getText("text.mc-cmd-shell.cmd.read_exception", e.getMessage()).getString()).append("\n");
                }
            });
        }

        public void executeCommandAsync(String cmd, UUID playerUuid, ServerCommandSource source) {
            ASYNC_EXECUTOR.submit(() -> {
                try {
                    latestOutput.setLength(0);
                    input.write(cmd + "\n");
                    input.flush();

                    int stableCount = 0;
                    int lastLength = -1;
                    while (stableCount < 3) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        int currentLength = latestOutput.length();
                        if (currentLength == lastLength) stableCount++;
                        else {
                            stableCount = 0;
                            lastLength = currentLength;
                        }
                    }

                    String fullOutput = latestOutput.toString().trim();
                    String[] lines = fullOutput.split("\n");
                    int lineCount = lines.length;
                    final int MAX_LINES = 35;

                    Text result;
                    if (lineCount > MAX_LINES) {
                        StringBuilder truncated = new StringBuilder();
                        for (int i = 0; i < MAX_LINES; i++) truncated.append(lines[i]).append("\n");
                        String truncatedOutput = truncated.toString().trim();
                        CommandHandler.LAST_FULL_OUTPUT.put(playerUuid, fullOutput);
                        String truncatedMessage = ServerLanguageManage.getText("text.mc-cmd-shell.cmd.truncated", MAX_LINES).getString();
                        result = ServerLanguageManage.getText("text.mc-cmd-shell.cmd.output_prefix", truncatedOutput + "\n" + truncatedMessage);
                    } else {
                        CommandHandler.LAST_FULL_OUTPUT.remove(playerUuid);
                        if (fullOutput.isEmpty())
                            result = ServerLanguageManage.getText("text.mc-cmd-shell.cmd.no_output");
                        else
                            result = ServerLanguageManage.getText("text.mc-cmd-shell.cmd.output_prefix", fullOutput);
                    }
                    source.sendMessage(result);
                } catch (IOException e) {
                    source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.cmd.send_failed", e.getMessage()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
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