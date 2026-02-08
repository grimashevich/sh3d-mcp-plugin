package com.sh3d.mcp.server;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TcpServerTest {

    @Test
    @Disabled("TODO: implement integration test with mock HomeAccessor")
    void testStartAndStop() {
        // PluginConfig config = PluginConfig.load();
        // CommandRegistry registry = new CommandRegistry();
        // registry.register("ping", new PingHandler());
        // TcpServer server = new TcpServer(config, registry, mockAccessor);
        // server.start();
        // assertTrue(server.isRunning());
        // server.stop();
        // assertFalse(server.isRunning());
    }

    @Test
    @Disabled("TODO: implement integration test with socket connection")
    void testPingOverSocket() {
        // 1. Запустить TcpServer
        // 2. Подключиться через Socket
        // 3. Отправить {"action": "ping"}
        // 4. Прочитать ответ
        // 5. Проверить {"status": "ok", "data": {"version": "0.1.0"}}
        // 6. Остановить сервер
    }
}
