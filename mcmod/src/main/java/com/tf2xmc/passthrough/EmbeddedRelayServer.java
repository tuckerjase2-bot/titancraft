package com.tf2xmc.passthrough;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.tf2xmc.passthrough.PassthroughProtocol.RelayEvent;

/**
 * Embedded HTTP relay server.
 * Runs inside the Minecraft mod on 127.0.0.1:25600.
 * No external process needed.
 *
 * All handlers are exception-proof: an uncaught exception in a handler
 * closes the connection without any response (curl error 52), so every
 * handler catches Throwable, logs it, and still replies.
 */
public class EmbeddedRelayServer {

    private final int port;
    private HttpServer server;
    private final ConcurrentLinkedQueue<RelayEvent> eventQueue = new ConcurrentLinkedQueue<>();

    // Shared state
    private volatile PassthroughProtocol.Tf2StatePayload tf2State = null;
    private volatile boolean tf2Connected = false;
    private volatile PassthroughProtocol.McStatePayload mcState = null;
    private volatile boolean mcConnected = false;

    public EmbeddedRelayServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/tf2/state", new Tf2StateHandler());
        server.createContext("/tf2/event", new Tf2EventHandler());
        server.createContext("/mc/state", new McStateHandler());
        server.createContext("/mc/event", new McEventHandler());
        server.createContext("/events", new EventsHandler());
        server.setExecutor(null);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // === Direct state setters (called from same process) ===

    public void setMcStateDirect(PassthroughProtocol.McStatePayload state) {
        this.mcState = state;
        this.mcConnected = true;
        eventQueue.add(new RelayEvent("mc", PassthroughProtocol.MC_STATE, state));
    }

    public void setTf2StateDirect(PassthroughProtocol.Tf2StatePayload state) {
        this.tf2State = state;
        this.tf2Connected = true;
        eventQueue.add(new RelayEvent("tf2", PassthroughProtocol.TF2_STATE, state));
    }

    public void addMcEventDirect(String type, Object payload) {
        eventQueue.add(new RelayEvent("mc", type, payload));
    }

    public void addTf2EventDirect(String type, Object payload) {
        eventQueue.add(new RelayEvent("tf2", type, payload));
    }

    // === State accessors ===

    public PassthroughProtocol.Tf2StatePayload getTf2State() {
        return tf2State;
    }

    public boolean isTf2Connected() {
        return tf2Connected;
    }

    public PassthroughProtocol.McStatePayload getMcState() {
        return mcState;
    }

    public boolean isMcConnected() {
        return mcConnected;
    }

    public List<RelayEvent> drainEvents() {
        List<RelayEvent> result = new ArrayList<>();
        RelayEvent e;
        while ((e = eventQueue.poll()) != null) {
            result.add(e);
        }
        return result;
    }

    // === HTTP plumbing ===

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        // Headers MUST be set before sendResponseHeaders, or they are dropped
        // (and mutating them afterwards can throw mid-response).
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Reply 500 with the error message; never let an exception escape. */
    private void trySendError(HttpExchange ex, Throwable t) {
        try {
            String msg = String.valueOf(t).replace("\"", "'");
            sendJson(ex, 500, "{\"ok\":false,\"error\":\"" + msg + "\"}");
        } catch (Throwable ignored) {
            try { ex.close(); } catch (Throwable ignored2) { }
        }
    }

    // === HTTP Handlers ===

    private class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                sendJson(ex, 200, "{\"ok\":true,\"service\":\"tf2xmc-relay\"}");
            } catch (Throwable t) {
                trySendError(ex, t);
            }
        }
    }

    private class Tf2StateHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String body = readBody(ex);
                PassthroughProtocol.Tf2StatePayload state =
                        PassthroughProtocol.GSON.fromJson(body, PassthroughProtocol.Tf2StatePayload.class);
                if (state == null) {
                    sendJson(ex, 400, "{\"ok\":false,\"error\":\"empty or null state\"}");
                    return;
                }
                setTf2StateDirect(state);
                sendJson(ex, 200, "{\"ok\":true}");
            } catch (Throwable t) {
                t.printStackTrace();
                trySendError(ex, t);
            }
        }
    }

    private class Tf2EventHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String body = readBody(ex);
                PassthroughProtocol.RelayEvent evt =
                        PassthroughProtocol.GSON.fromJson(body, PassthroughProtocol.RelayEvent.class);
                if (evt == null) {
                    sendJson(ex, 400, "{\"ok\":false,\"error\":\"empty event\"}");
                    return;
                }
                eventQueue.add(evt);
                sendJson(ex, 200, "{\"ok\":true}");
            } catch (Throwable t) {
                t.printStackTrace();
                trySendError(ex, t);
            }
        }
    }

    private class McStateHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                PassthroughProtocol.McStatePayload s = mcState;
                sendJson(ex, 200, s == null ? "{}" : PassthroughProtocol.GSON.toJson(s));
            } catch (Throwable t) {
                trySendError(ex, t);
            }
        }
    }

    private class McEventHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String body = readBody(ex);
                PassthroughProtocol.RelayEvent evt =
                        PassthroughProtocol.GSON.fromJson(body, PassthroughProtocol.RelayEvent.class);
                if (evt == null) {
                    sendJson(ex, 400, "{\"ok\":false,\"error\":\"empty event\"}");
                    return;
                }
                eventQueue.add(evt);
                sendJson(ex, 200, "{\"ok\":true}");
            } catch (Throwable t) {
                t.printStackTrace();
                trySendError(ex, t);
            }
        }
    }

    private class EventsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                List<RelayEvent> events = drainEvents();
                String json = "{\"events\":" + PassthroughProtocol.GSON.toJson(events) + "}";
                sendJson(ex, 200, json);
            } catch (Throwable t) {
                trySendError(ex, t);
            }
        }
    }
}
