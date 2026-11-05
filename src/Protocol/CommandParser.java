package Protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CommandParser {
    private static final int MAX_ARGS = 1024;
    private static final int MAX_BULK_LEN = 1024 * 1024; // 1 MB per value

    /**
     * Reads one command from the raw byte stream. RESP bulk-string lengths are
     * byte counts, so the payload is read before decoding it as UTF-8.
     * Plain inline text remains supported for manual testing.
     */
    public static Command parseCommand(InputStream input) throws IOException {
        String line = readLine(input);
        while (line != null && line.isEmpty()) {
            line = readLine(input);
        }
        if (line == null) return null;

        List<String> tokens = line.charAt(0) == '*'
                ? parseRespArray(input, line)
                : parseInline(line);

        if (tokens.isEmpty()) return parseCommand(input);

        String name = tokens.get(0);
        List<String> args = tokens.subList(1, tokens.size());
        return new Command(name, args);
    }

    private static List<String> parseRespArray(InputStream input, String header) throws IOException {
        int numArgs;
        try {
            numArgs = Integer.parseInt(header.substring(1).trim());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid RESP array header: " + header, e);
        }

        if (numArgs <= 0) return new ArrayList<>();
        if (numArgs > MAX_ARGS) {
            throw new IOException("Too many arguments: " + numArgs);
        }

        List<String> tokens = new ArrayList<>(numArgs);
        for (int i = 0; i < numArgs; i++) {
            String bulkHeader = readLine(input);
            if (bulkHeader == null || bulkHeader.isEmpty() || bulkHeader.charAt(0) != '$') {
                throw new IOException("Expected bulk string header, got: " + bulkHeader);
            }

            int len;
            try {
                len = Integer.parseInt(bulkHeader.substring(1).trim());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid bulk length: " + bulkHeader, e);
            }

            if (len == -1) {
                tokens.add(null);
                continue;
            }
            if (len < 0 || len > MAX_BULK_LEN) {
                throw new IOException("Invalid bulk length: " + len);
            }

            byte[] payload = input.readNBytes(len);
            if (payload.length != len) {
                throw new IOException("Unexpected EOF reading bulk string");
            }
            expectCrlf(input);
            tokens.add(new String(payload, StandardCharsets.UTF_8));
        }
        return tokens;
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int current;
        while ((current = input.read()) != -1) {
            if (current == '\n') {
                byte[] line = bytes.toByteArray();
                int length = line.length;
                if (length > 0 && line[length - 1] == '\r') {
                    length--;
                }
                return new String(line, 0, length, StandardCharsets.UTF_8);
            }
            bytes.write(current);
        }

        if (bytes.size() == 0) return null;
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static void expectCrlf(InputStream input) throws IOException {
        int cr = input.read();
        int lf = input.read();
        if (cr != '\r' || lf != '\n') {
            throw new IOException("Bulk string is not terminated by CRLF");
        }
    }

    private static List<String> parseInline(String line) {
        List<String> tokens = new ArrayList<>();
        for (String part : line.trim().split("\\s+")) {
            if (!part.isEmpty()) tokens.add(part);
        }
        return tokens;
    }
}
