package com.ppp.util;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import java.util.*;

public class PaginationManager {
    private static final int LINES_PER_PAGE = 40;
    private static final int MAX_PAGES = 400;
    private static final int MAX_TOTAL_CHARS = 2_000_000;
    private static final int MAX_LINE_LENGTH = 80;

    private static final Map<UUID, List<String>> playerPages = new HashMap<>();

    public static void storeAndSendFirstPage(ServerCommandSource source, UUID playerUuid, String output, String titlePrefix) {
        String[] rawLines = output.split("\n");
        List<String> processedLines = new ArrayList<>();

        for (String line : rawLines) {
            if (line.length() <= MAX_LINE_LENGTH) {
                processedLines.add(line);
            } else {
                int start = 0;
                while (start < line.length()) {
                    int end = Math.min(start + MAX_LINE_LENGTH, line.length());
                    processedLines.add(line.substring(start, end));
                    start = end;
                }
            }
        }

        List<String> pages = new ArrayList<>();
        StringBuilder pageBuilder = new StringBuilder();
        int lineCount = 0;
        int totalChars = 0;
        boolean truncated = false;

        for (String line : processedLines) {
            if (totalChars + line.length() + 1 > MAX_TOTAL_CHARS) {
                pageBuilder.append("\n").append(ServerLanguageManage.getText("text.mc-cmd-shell.pagination.truncated").getString());
                truncated = true;
                break;
            }
            pageBuilder.append(line).append("\n");
            totalChars += line.length() + 1;
            lineCount++;
            if (lineCount >= LINES_PER_PAGE) {
                pages.add(pageBuilder.toString());
                pageBuilder.setLength(0);
                lineCount = 0;
                if (pages.size() >= MAX_PAGES) {
                    pages.add(ServerLanguageManage.getText("text.mc-cmd-shell.pagination.too_many_pages").getString());
                    break;
                }
            }
        }
        if (pageBuilder.length() > 0) {
            pages.add(pageBuilder.toString());
        }
        if (pages.isEmpty()) {
            pages.add(ServerLanguageManage.getText("text.mc-cmd-shell.pagination.no_output").getString());
        }

        playerPages.put(playerUuid, pages);

        if (truncated) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.pagination.truncated"));
        }

        sendPage(source, playerUuid, 1, titlePrefix);
    }

    public static void sendPage(ServerCommandSource source, UUID playerUuid, int pageNum, String titlePrefix) {
        List<String> pages = playerPages.get(playerUuid);
        if (pages == null || pageNum < 1 || pageNum > pages.size()) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.pagination.invalid_page"));
            return;
        }
        String content = pages.get(pageNum - 1);
        source.sendMessage(Text.literal(titlePrefix + (titlePrefix.isEmpty() ? "" : "\n") + content));
        if (pages.size() > 1) {
            String pageInfo = ServerLanguageManage.getText("text.mc-cmd-shell.pagination.page_info", pageNum, pages.size()).getString();
            source.sendMessage(Text.literal(pageInfo));
        }
    }

    public static void clear(UUID playerUuid) {
        playerPages.remove(playerUuid);
    }
}