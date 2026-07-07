package com.dwinovo.numen.qq;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Forward-WebSocket client for a NapCatQQ (OneBot 11) endpoint — JDK
 * {@link WebSocket} only, no bot-framework dependency.
 *
 * <p>Inbound: OneBot {@code post_type:"message"} events are filtered by the
 * config's group whitelist (private chat behind its own switch) and handed to
 * the consumer as an {@link Inbound}. Everything else (heartbeats, echoes,
 * notices) is ignored.
 *
 * <p>Outbound: {@link #sendGroupMsg}/{@link #sendPrivateMsg} enqueue OneBot
 * actions on a single-thread executor — callers (the tool runs on the client
 * main thread) never block on the network.
 *
 * <p>Reconnects forever with capped backoff; {@link #stop} ends it.
 */
public final class NapCatClient {

    /** One QQ message, already whitelist-filtered. {@code groupId} is 0 for private chat. */
    public record Inbound(long groupId, long userId, String senderName, String text) {}

    private static final long RECONNECT_MIN_MS = 5_000;
    private static final long RECONNECT_MAX_MS = 30_000;

    private final QqBridgeConfig cfg;
    private final Consumer<Inbound> handler;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ExecutorService outbound = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "numen-qq-outbound");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocket ws;
    private volatile boolean stopped;

    public NapCatClient(QqBridgeConfig cfg, Consumer<Inbound> handler) {
        this.cfg = cfg;
        this.handler = handler;
    }

    // ---- lifecycle ----

    public void start() {
        Thread t = new Thread(this::connectLoop, "numen-qq-connect");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        stopped = true;
        WebSocket w = ws;
        if (w != null) w.abort();
        outbound.shutdown();
    }

    private void connectLoop() {
        long backoff = RECONNECT_MIN_MS;
        while (!stopped) {
            try {
                WebSocket.Builder b = http.newWebSocketBuilder();
                if (!cfg.accessToken().isBlank()) {
                    b.header("Authorization", "Bearer " + cfg.accessToken());
                }
                ws = b.buildAsync(URI.create(cfg.wsUrl()), new Listener()).join();
                Constants.LOG.info("[numen-qq] connected to {}", cfg.wsUrl());
                return; // Listener schedules the next connectLoop on close/error.
            } catch (RuntimeException ex) {
                Constants.LOG.warn("[numen-qq] connect to {} failed, retrying in {}s: {}",
                        cfg.wsUrl(), backoff / 1000, ex.toString());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoff = Math.min(backoff * 2, RECONNECT_MAX_MS);
            }
        }
    }

    private void scheduleReconnect() {
        if (stopped) return;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(RECONNECT_MIN_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            connectLoop();
        }, "numen-qq-reconnect");
        t.setDaemon(true);
        t.start();
    }

    // ---- outbound (OneBot 11 actions) ----

    public void sendGroupMsg(long groupId, String text) {
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        params.addProperty("message", text);
        sendAction("send_group_msg", params);
    }

    public void sendPrivateMsg(long userId, String text) {
        JsonObject params = new JsonObject();
        params.addProperty("user_id", userId);
        params.addProperty("message", text);
        sendAction("send_private_msg", params);
    }

    private void sendAction(String action, JsonObject params) {
        JsonObject o = new JsonObject();
        o.addProperty("action", action);
        o.add("params", params);
        o.addProperty("echo", "numen-qq");
        outbound.execute(() -> {
            WebSocket w = ws;
            if (w == null) {
                Constants.LOG.warn("[numen-qq] {} dropped — not connected", action);
                return;
            }
            try {
                w.sendText(o.toString(), true).join();
            } catch (RuntimeException ex) {
                Constants.LOG.warn("[numen-qq] {} failed: {}", action, ex.toString());
            }
        });
    }

    // ---- inbound ----

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String frame = partial.toString();
                partial.setLength(0);
                try {
                    route(JsonParser.parseString(frame).getAsJsonObject());
                } catch (RuntimeException ex) {
                    Constants.LOG.debug("[numen-qq] unparsable frame ignored: {}", ex.toString());
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            Constants.LOG.warn("[numen-qq] websocket error: {}", error.toString());
            scheduleReconnect();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            Constants.LOG.info("[numen-qq] websocket closed ({} {})", statusCode, reason);
            scheduleReconnect();
            return null;
        }

        private void route(JsonObject o) {
            if (!o.has("post_type") || !"message".equals(o.get("post_type").getAsString())) return;
            String type = o.has("message_type") ? o.get("message_type").getAsString() : "";
            String text = o.has("raw_message") ? o.get("raw_message").getAsString() : "";
            if (text.isBlank()) return;

            JsonObject sender = o.has("sender") && o.get("sender").isJsonObject()
                    ? o.getAsJsonObject("sender") : new JsonObject();
            long userId = sender.has("user_id") ? sender.get("user_id").getAsLong() : 0L;
            // Group card (per-group display name) beats the global nickname.
            String name = sender.has("card") && !sender.get("card").getAsString().isBlank()
                    ? sender.get("card").getAsString()
                    : sender.has("nickname") ? sender.get("nickname").getAsString() : String.valueOf(userId);

            if ("group".equals(type)) {
                long groupId = o.has("group_id") ? o.get("group_id").getAsLong() : 0L;
                if (!cfg.groupWhitelist().contains(groupId)) return;
                handler.accept(new Inbound(groupId, userId, name, text));
            } else if ("private".equals(type) && cfg.listenPrivate()) {
                handler.accept(new Inbound(0L, userId, name, text));
            }
        }
    }
}
