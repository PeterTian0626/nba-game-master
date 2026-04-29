package com.peter.nbagamemaster.demo.invoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TestApiKey {

    private static final String API_KEY_NAME = "OPENAI_API_KEY";
    private static final Path DOTENV_PATH = Path.of(".env");

    private TestApiKey() {
    }

    public static String requireApiKey() {
        String apiKey = System.getenv(API_KEY_NAME);
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("openai.apiKey");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = readFromDotenv();
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API key is missing. Set OPENAI_API_KEY, run with -Dopenai.apiKey=your_key, "
                            + "or add OPENAI_API_KEY=your_key to .env.");
        }
        return apiKey;
    }

    private static String readFromDotenv() {
        if (!Files.exists(DOTENV_PATH)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(DOTENV_PATH);
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = line.substring(0, separatorIndex).trim();
                if (!API_KEY_NAME.equals(key)) {
                    continue;
                }

                String value = line.substring(separatorIndex + 1).trim();
                if (value.length() >= 2) {
                    boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
                    boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
                    if (doubleQuoted || singleQuoted) {
                        value = value.substring(1, value.length() - 1);
                    }
                }
                return value;
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read .env file at " + DOTENV_PATH.toAbsolutePath(), e);
        }
    }
}
