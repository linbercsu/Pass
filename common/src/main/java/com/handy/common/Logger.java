package com.handy.common;

import org.joda.time.DateTime;

public class Logger {
    public static final int V = 0;
    public static final int D = 1;
    public static final int E = 2;
    private static int level = D;

    public static void init(int logLevel) {
        level = logLevel;
    }

    public static void v(String message) {
        if (level > V)
            return;

        String date = DateTime.now().toString();
        String format = String.format("V %s %s", date, message);
        System.out.println(format);
    }

    public static void v(String message, Object... args) {
        if (level > V)
            return;

        String string = String.format(message, args);
        v(string);
    }
    public static void d(String message) {
        if (level > D)
            return;

        String date = DateTime.now().toString();
        String format = String.format("D %s %s", date, message);
        System.out.println(format);
    }
    public static void d(String message, Object... args) {
        if (level > D)
            return;

        String string = String.format(message, args);
        d(string);
    }

    public static void e(String message) {
        String date = DateTime.now().toString();
        String format = String.format("E %s %s", date, message);
        System.err.println(format);
    }

    public static void e(String message, Object... args) {
        String string = String.format(message, args);
        e(string);
    }

    public static void e(Throwable throwable) {
        e("exception------------>");
        throwable.printStackTrace();
    }
}
