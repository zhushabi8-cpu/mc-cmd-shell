package com.ppp.util;

import java.util.Scanner;

public class CmdExecutor {
    public static String run(String command) {
        try {
            Process process = new ProcessBuilder("cmd", "/c", command).redirectErrorStream(true).start();
            try (Scanner sc = new Scanner(process.getInputStream(), "GBK")) {
                StringBuilder output = new StringBuilder();
                while (sc.hasNextLine()) {
                    output.append(sc.nextLine()).append("\n");
                }
                return output.toString();
            }
        } catch (Exception e) {
            return "执行失败: " + e.getMessage();
        }
    }
}