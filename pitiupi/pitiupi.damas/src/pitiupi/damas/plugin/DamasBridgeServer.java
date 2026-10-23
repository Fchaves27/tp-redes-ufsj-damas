package pitiupi.damas.plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DamasBridgeServer {
    private static final Logger LOGGER = Logger.getLogger(DamasBridgeServer.class.getName());
    private static final int MAX_HTTP_HEADER = 8192;
    private static final int MAX_FRAME_BYTES = 65536;

    private final int port;
    private final Consumer<String> commandHandler;
    private final AtomicBoolean browserConnected = new AtomicBoolean();
    private final ExecutorService clients = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "pitiupi-damas-client");
        thread.setDaemon(true);
        return thread;
    });
    private ServerSocket serverSocket;
    private volatile Socket browserSocket;
    private volatile OutputStream browserOutput;

    DamasBridgeServer(int port, Consumer<String> commandHandler) {
        this.port = port;
        this.commandHandler = commandHandler;
    }

    void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
        Thread thread = new Thread(this::acceptClients, "pitiupi-damas-websocket");
        thread.setDaemon(true);
        thread.start();
    }

    void send(String message) {
        Socket socket = browserSocket;
        OutputStream output = browserOutput;
        if (socket == null || output == null || socket.isClosed()) {
            return;
        }
        try {
            synchronized (output) {
                writeFrame(output, 0x1, message.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            closeBrowser(socket);
        }
    }

    private void acceptClients() {
        ServerSocket listener = serverSocket;
        try {
            while (!listener.isClosed()) {
                Socket socket = listener.accept();
                clients.execute(() -> handleClient(socket));
            }
        } catch (IOException exception) {
            if (!listener.isClosed()) {
                LOGGER.log(Level.SEVERE, "A ponte local do jogo foi interrompida.", exception);
            }
        }
    }

    private void handleClient(Socket socket) {
        try (socket) {
            socket.setSoTimeout(0);
            byte[] requestBytes = readHttpHeader(socket.getInputStream());
            String request = new String(requestBytes, StandardCharsets.ISO_8859_1);
            String[] lines = request.split("\r\n");
            if (lines.length == 0) {
                return;
            }
            String[] requestLine = lines[0].split(" ");
            if (requestLine.length < 2 || !"GET".equals(requestLine[0])) {
                writeHttpResponse(socket.getOutputStream(), 405, "Method Not Allowed", "Método não permitido.");
                return;
            }

            String path = requestLine[1];
            String upgrade = headerValue(lines, "Upgrade");
            if ("websocket".equalsIgnoreCase(upgrade)) {
                handleWebSocket(socket, lines, path);
            } else {
                servePage(socket, path);
            }
        } catch (IOException exception) {
            if (!socket.isClosed()) {
                closeBrowser(socket);
            }
        }
    }

    private void servePage(Socket socket, String path) throws IOException {
        String resource;
        String contentType;
        if ("/".equals(path) || "/index.html".equals(path)) {
            resource = "/web/index.html";
            contentType = "text/html; charset=utf-8";
        } else if ("/estilo.css".equals(path)) {
            resource = "/web/estilo.css";
            contentType = "text/css; charset=utf-8";
        } else {
            writeHttpResponse(socket.getOutputStream(), 404, "Not Found", "Página não encontrada.");
            return;
        }

        try (InputStream input = DamasBridgeServer.class.getResourceAsStream(resource)) {
            if (input == null) {
                writeHttpResponse(socket.getOutputStream(), 500, "Internal Server Error", "Arquivo do jogo ausente no plugin.");
                return;
            }
            byte[] body = input.readAllBytes();
            OutputStream output = socket.getOutputStream();
            String headers = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "X-Content-Type-Options: nosniff\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(headers.getBytes(StandardCharsets.ISO_8859_1));
            output.write(body);
            output.flush();
        }
    }

    private void handleWebSocket(Socket socket, String[] headers, String path) throws IOException {
        String origin = headerValue(headers, "Origin");
        String key = headerValue(headers, "Sec-WebSocket-Key");
        String version = headerValue(headers, "Sec-WebSocket-Version");
        if (!"/ws".equals(path) || !isLocalOrigin(origin) || key == null || !"13".equals(version)) {
            writeHttpResponse(socket.getOutputStream(), 403, "Forbidden", "Conexão WebSocket não permitida.");
            return;
        }
        if (!browserConnected.compareAndSet(false, true)) {
            writeHttpResponse(socket.getOutputStream(), 409, "Conflict", "O jogo já está aberto em outra aba.");
            return;
        }

        boolean upgraded = false;
        try {
            String accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1")
                            .digest((key.trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                    .getBytes(StandardCharsets.US_ASCII))
            );
            OutputStream output = socket.getOutputStream();
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            output.write(response.getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
            browserSocket = socket;
            browserOutput = output;
            upgraded = true;
            send("CONNECTED");
            readFrames(socket, socket.getInputStream());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IOException("SHA-1 não está disponível para o handshake WebSocket.", exception);
        } finally {
            if (upgraded) {
                closeBrowser(socket);
            } else {
                browserConnected.set(false);
            }
        }
    }

    private static boolean isLocalOrigin(String origin) {
        if (origin == null) {
            return false;
        }
        try {
            String host = java.net.URI.create(origin).getHost();
            return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void readFrames(Socket socket, InputStream input) throws IOException {
        while (!socket.isClosed()) {
            int first = input.read();
            if (first < 0) {
                return;
            }
            int second = readRequired(input);
            int opcode = first & 0x0f;
            boolean finalFrame = (first & 0x80) != 0;
            boolean masked = (second & 0x80) != 0;
            if ((first & 0x70) != 0 || !finalFrame || !masked) {
                writeFrame(browserOutput, 0x8, new byte[0]);
                return;
            }

            long length = second & 0x7f;
            if (length == 126) {
                length = ((long) readRequired(input) << 8) | readRequired(input);
            } else if (length == 127) {
                length = 0;
                for (int index = 0; index < 8; index++) {
                    length = (length << 8) | readRequired(input);
                }
            }
            if (length < 0 || length > MAX_FRAME_BYTES || (opcode >= 0x8 && length > 125)) {
                writeFrame(browserOutput, 0x8, new byte[]{(byte) 0x03, (byte) 0xf1});
                return;
            }

            byte[] mask = readExact(input, 4);
            byte[] payload = readExact(input, (int) length);
            for (int index = 0; index < payload.length; index++) {
                payload[index] ^= mask[index & 3];
            }

            if (opcode == 0x8) {
                writeFrame(browserOutput, 0x8, payload);
                return;
            }
            if (opcode == 0x9) {
                writeFrame(browserOutput, 0xA, payload);
                continue;
            }
            if (opcode != 0x1) {
                writeFrame(browserOutput, 0x8, new byte[]{(byte) 0x03, (byte) 0xea});
                return;
            }
            commandHandler.accept(new String(payload, StandardCharsets.UTF_8));
        }
    }

    private static int readRequired(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new IOException("Conexão encerrada durante a leitura WebSocket.");
        }
        return value;
    }

    private static byte[] readExact(InputStream input, int length) throws IOException {
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Quadro WebSocket incompleto.");
        }
        return bytes;
    }

    private static byte[] readHttpHeader(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int state = 0;
        while (bytes.size() < MAX_HTTP_HEADER) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("Conexão encerrada durante o cabeçalho HTTP.");
            }
            bytes.write(value);
            state = switch (state) {
                case 0 -> value == '\r' ? 1 : 0;
                case 1 -> value == '\n' ? 2 : 0;
                case 2 -> value == '\r' ? 3 : 0;
                case 3 -> value == '\n' ? 4 : 0;
                default -> 4;
            };
            if (state == 4) {
                return bytes.toByteArray();
            }
        }
        throw new IOException("Cabeçalho HTTP excede o tamanho permitido.");
    }

    private static String headerValue(String[] lines, String name) {
        for (String line : lines) {
            int separator = line.indexOf(':');
            if (separator > 0 && name.equalsIgnoreCase(line.substring(0, separator).trim())) {
                return line.substring(separator + 1).trim();
            }
        }
        return null;
    }

    private static void writeHttpResponse(OutputStream output, int status, String reason, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        output.write(bytes);
        output.flush();
    }

    private static void writeFrame(OutputStream output, int opcode, byte[] payload) throws IOException {
        synchronized (output) {
            output.write(0x80 | opcode);
            if (payload.length < 126) {
                output.write(payload.length);
            } else if (payload.length <= 65535) {
                output.write(126);
                output.write((payload.length >>> 8) & 0xff);
                output.write(payload.length & 0xff);
            } else {
                output.write(127);
                output.write(ByteBuffer.allocate(8).putLong(payload.length).array());
            }
            output.write(payload);
            output.flush();
        }
    }

    private void closeBrowser(Socket socket) {
        if (browserSocket == socket) {
            browserSocket = null;
            browserOutput = null;
            browserConnected.set(false);
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing an already-closed browser socket needs no further action.
        }
    }
}
