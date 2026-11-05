package Server;

import Protocol.Command;
import Protocol.CommandParser;
import Store.DataStore;
import Store.CommandExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final DataStore dataStore;
    private final CommandExecutor executor;

    public ClientHandler(Socket clientSocket, DataStore dataStore, CommandExecutor executor) {
        this.clientSocket = clientSocket;
        this.dataStore = dataStore;
        this.executor = executor;
    }

    @Override
    public void run() {
        String clientAddress = clientSocket.getRemoteSocketAddress().toString();

        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {
            Command command;
            while ((command = CommandParser.parseCommand(in)) != null) {
                String response;
                try {
                    response = dispatch(command);
                } catch (RuntimeException e) {
                    System.out.println("Error handling " + command.getName() + ": " + e);
                    response = "-ERR internal error\\r\\n";
                }

                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();

                if (command.getName().equals("QUIT")) break;
            }
        } catch (IOException e) {
            System.out.println("Connection error with " + clientAddress + ": " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket: " + e.getMessage());
            }
            System.out.println("Client disconnected: " + clientAddress);
        }
    }

    private String dispatch(Command command) {
        String cmd = command.getName();
        List<String> args = command.getArgs();

        switch (cmd) {
            case "PING":
                String pong = args.isEmpty() ? "PONG" : args.get(0);
                return "+" + pong + "\\r\\n";

            case "ECHO":
                if (args.isEmpty()) return "-ERR wrong number of arguments for 'echo'\\r\\n";
                String echo = String.join(" ", args);
                int echoLength = echo.getBytes(StandardCharsets.UTF_8).length;
                return "$" + echoLength + "\\r\\n" + echo + "\\r\\n";

            case "GET": {
                if (args.size() != 1) return "-ERR wrong number of arguments for 'get'\\r\\n";
                String result = dataStore.get(args.get(0));
                if (result == null) return "$-1\\r\\n";
                int valueLength = result.getBytes(StandardCharsets.UTF_8).length;
                return "$" + valueLength + "\\r\\n" + result + "\\r\\n";
            }

            case "EXISTS":
                if (args.size() != 1) return "-ERR wrong number of arguments for 'exists'\\r\\n";
                return ":" + (dataStore.exists(args.get(0)) ? 1 : 0) + "\\r\\n";

            case "QUIT":
                return "+OK\\r\\n";

            case "SET":
            case "DEL":
            case "EXPIRE":
                return executor.execute(command);

            default:
                return "-ERR unknown command '" + cmd + "'\\r\\n";
        }
    }
}
