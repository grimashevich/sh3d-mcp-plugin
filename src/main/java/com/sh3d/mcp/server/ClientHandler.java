package com.sh3d.mcp.server;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.JsonProtocol;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обрабатывает одно TCP-соединение: читает JSON-строки, маршрутизирует команды,
 * отправляет JSON-ответы.
 */
public class ClientHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final CommandRegistry commandRegistry;
    private final HomeAccessor accessor;
    private final int maxLineLength;

    public ClientHandler(Socket socket, CommandRegistry commandRegistry, HomeAccessor accessor, int maxLineLength) {
        this.socket = socket;
        this.commandRegistry = commandRegistry;
        this.accessor = accessor;
        this.maxLineLength = maxLineLength;
    }

    @Override
    public void run() {
        String clientAddr = socket.getRemoteSocketAddress().toString();
        LOG.info("Client connected: " + clientAddr);

        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = readLine(reader)) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String response = processLine(line);
                writer.println(response);
            }

        } catch (IOException e) {
            if (!socket.isClosed()) {
                LOG.log(Level.WARNING, "Client I/O error: " + clientAddr, e);
            }
        } finally {
            close();
            LOG.info("Client disconnected: " + clientAddr);
        }
    }

    public void close() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Error closing client socket", e);
        }
    }

    /**
     * Читает строку с ограничением длины. Возвращает null при EOF.
     * Бросает IOException если строка превышает maxLineLength.
     */
    private String readLine(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            if (ch == '\n') {
                return sb.toString();
            }
            if (ch == '\r') {
                continue;
            }
            if (sb.length() >= maxLineLength) {
                throw new IOException("Line exceeds maximum length: " + maxLineLength);
            }
            sb.append((char) ch);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String processLine(String line) {
        try {
            Request request = JsonProtocol.parseRequest(line);
            Response response = commandRegistry.dispatch(request, accessor);
            return JsonProtocol.formatResponse(response);
        } catch (IllegalArgumentException e) {
            return JsonProtocol.formatError(e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error processing line", e);
            return JsonProtocol.formatError("Internal error: " + e.getMessage());
        }
    }
}
