package Persistence;

import Protocol.Command;
import Protocol.CommandParser;
import Store.CommandExecutor;
import Store.DataStore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class AofReplayer {

    private final String filePath;
    private final CommandExecutor executor;
    private final DataStore dataStore;

    public AofReplayer(String filePath, CommandExecutor executor, DataStore dataStore) {
        this.filePath = filePath;
        this.executor = executor;
        this.dataStore = dataStore;
    }

    public void replay() {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("No AOF file found, starting with empty store.");
            return;
        }

        int count = 0;
        int skipped=0;//adding feature to skip replay of commands that do not make sense
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                try {
                    replayLine(line.trim());
                    count++;
                } catch (RuntimeException e) {
                    skipped++;
                    System.out.println("Skipping bad AOF line: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading AOF file: " + e.getMessage());
        }

        System.out.println("Replayed " + count + " commands from AOF (" + skipped + " skipped).");
    }

    private void replayLine(String line) {
        String[] parts = line.split("\\s+");

        // special case: absolute-timestamp EXPIRE, written only by AOF logging
        // itself (never sent by a real client), so intercept it before normal parsing
        if (parts.length == 4 && parts[0].equalsIgnoreCase("EXPIRE") && parts[3].equalsIgnoreCase("ABS")) {
            String key = parts[1];
            long expiryTimestamp = Long.parseLong(parts[2]);

            // if the timestamp already passed while the server was down,
            // don't bother restoring it — just let it be treated as expired/missing

                dataStore.setExpiryAt(key, expiryTimestamp);

            return;
        }

        // normal case: SET / DEL, parsed and executed exactly like a live command
        List<String> args = Arrays.asList(parts).subList(1, parts.length);
        Command command = new Command(parts[0], args);
        executor.execute(command);
    }
}