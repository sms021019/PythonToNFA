package com.redos;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class CStringLength {
    static {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            String libName = "";
            if (osName.contains("win")) {
                libName = "libStringLength.dll"; // Windows
            } else if (osName.contains("mac")) {
                libName = "libStringLength.dylib"; // macOS
            } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
                libName = "libStringLength.so"; // Linux/Unix
            } else {
                throw new UnsupportedOperationException("Unsupported operating system: " + osName);
            }
            // Extract the native library to a temporary file
            InputStream in = CStringLength.class.getResourceAsStream("/" + libName);
            File tempFile = File.createTempFile("libStringLength", ".dylib");
            tempFile.deleteOnExit();
            Files.copy(in, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            in.close();

            // Load the extracted library
            System.load(tempFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load native library", e);
        }
    }

    public static native int getStringLength(String str);
        
}
