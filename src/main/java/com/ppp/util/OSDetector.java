package com.ppp.util;

public class OSDetector {
    public enum OS {
        WINDOWS, LINUX, MAC, OTHER
    }

    private static final OS currentOS;

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            currentOS = OS.WINDOWS;
        } else if (osName.contains("linux")) {
            currentOS = OS.LINUX;
        } else if (osName.contains("mac")) {
            currentOS = OS.MAC;
        } else {
            currentOS = OS.OTHER;
        }
    }

    public static OS getOS() {
        return currentOS;
    }

    public static boolean isWindows() {
        return currentOS == OS.WINDOWS;
    }

    public static boolean isLinux() {
        return currentOS == OS.LINUX;
    }

    public static boolean isMac() {
        return currentOS == OS.MAC;
    }
}