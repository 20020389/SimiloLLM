package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Env {
    private static final Map<String, String> ENV_MAP = new HashMap<>();

    static {
        loadEnvFile(".env");
    }

    private static void loadEnvFile(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            System.err.println("⚠️ File .env không tồn tại ở " + file.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // bỏ qua comment (# hoặc ;)
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue;
                }

                int idx = line.indexOf("=");
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();

                    // bỏ dấu quote nếu có
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }

                    ENV_MAP.put(key, value);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi đọc file .env", e);
        }
    }

    public static String get(String key) {
        return ENV_MAP.get(key);
    }

    public static Map<String, String> all() {
        return Collections.unmodifiableMap(ENV_MAP);
    }
}