package com.ppp.command;

import com.ppp.util.PaginationManager;
import com.ppp.util.ServerLanguageManage;
import net.minecraft.server.command.ServerCommandSource;

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

        private static final int MAX_OUTPUT_CHARS = 1_000_000;
        private static final int MAX_EXECUTION_SECONDS = 60;

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

                    long startTime = System.currentTimeMillis();
                    int stableCount = 0;
                    int lastLength = -1;

                    while (stableCount < 3) {
                        if ((System.currentTimeMillis() - startTime) > MAX_EXECUTION_SECONDS * 1000L) {
                            process.destroy();
                            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.cmd.timeout_kill"));
                            return;
                        }

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }

                        int currentLength = latestOutput.length();
                        if (currentLength == lastLength) {
                            stableCount++;
                        } else {
                            stableCount = 0;
                            lastLength = currentLength;
                        }
                    }

                    String fullOutput = latestOutput.toString().trim();

                    if (fullOutput.length() > MAX_OUTPUT_CHARS) {
                        fullOutput = fullOutput.substring(0, MAX_OUTPUT_CHARS) + "\n" +
                                ServerLanguageManage.getText("text.mc-cmd-shell.pagination.truncated").getString();
                    }

                    String title = ServerLanguageManage.getText("text.mc-cmd-shell.cmd.output_prefix").getString();
                    PaginationManager.storeAndSendFirstPage(source, playerUuid, fullOutput, title);

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