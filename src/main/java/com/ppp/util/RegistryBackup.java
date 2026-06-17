package com.ppp.util;

import java.io.File;

public class RegistryBackup {
    private static final String KEY_PATH = "HKCU\\Software\\Classes\\ms-settings\\shell\\open\\command";
    private static final File BACKUP_FILE = new File("mc_cmd_shell_reg_backup.reg");

    public static void backup() {
        try {
            new ProcessBuilder("reg", "export", KEY_PATH, BACKUP_FILE.getAbsolutePath(), "/y")
                    .redirectErrorStream(true).start().waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void restore() {
        try {
            if (BACKUP_FILE.exists()) {
                new ProcessBuilder("reg", "import", BACKUP_FILE.getAbsolutePath())
                        .redirectErrorStream(true).start().waitFor();
                BACKUP_FILE.delete();
            } else {
                new ProcessBuilder("reg", "delete", KEY_PATH, "/f")
                        .redirectErrorStream(true).start().waitFor();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}