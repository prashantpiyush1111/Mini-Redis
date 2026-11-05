package Persistence;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class AofWriter {

    private final BufferedWriter writer;
    private volatile boolean enabled = true;

    public AofWriter(String filePath) throws IOException {
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();   // create data/ if missing
        // true = append mode: don't overwrite the file if it already exists,
        // just keep adding to the end of it (important — otherwise every
        // server restart would wipe your entire history!)
        this.writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8, true));
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized void log(String command) {
        if (!enabled) return;
        try {
            writer.write(command);
            writer.newLine();
            writer.flush();   // force it to disk immediately (see note below)
        } catch (IOException e) {
            System.out.println("AOF write failed: " + e.getMessage());
        }
    }

    public void close() throws IOException {
        writer.close();
    }
}