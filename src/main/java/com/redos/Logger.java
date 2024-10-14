package com.redos;

public class Logger {
    public static int ERROR = 0;
    public static int WARN = 1;
    public static int INFO = 2;
    public static int DEBUG = 3;
    private int verbosity;

    public Logger(int verbosity) {
        this.verbosity = verbosity;
    }

    public void log(int level, String s) {
        if (verbosity < 0) {
            throw new IllegalArgumentException();
        } else {
            if (level <= verbosity) {
                System.out.println(s);
            }

        }
    }
}
