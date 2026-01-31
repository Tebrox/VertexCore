package de.tebrox.vertexCore.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class FileIO {
    static void writeUtf8(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if(parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create folder: " + parent.getAbsolutePath());
        }
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }
}
