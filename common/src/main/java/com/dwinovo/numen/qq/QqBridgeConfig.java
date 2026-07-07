package com.dwinovo.numen.qq;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridge config at {@code config/numen/qq_bridge.json}. Plain Gson-over-file,
 * created with commented-by-README defaults on first launch. An empty
 * {@code ws_url} disables the bridge entirely. Both whitelists share one
 * convention — <b>empty = accept nothing</b>: every listened group and every
 * QQ account allowed to command the companion over private chat is an
 * explicit opt-in, because whoever gets through is spending the owner's LLM
 * tokens.
 */
public record QqBridgeConfig(
        String wsUrl,
        String accessToken,
        List<Long> groupWhitelist,
        List<Long> userWhitelist,
        String companionName) {

    public boolean enabled() {
        return !wsUrl.isBlank();
    }

    /** First whitelisted group — the default target for {@code send_qq_message}. */
    public long defaultGroup() {
        return groupWhitelist.isEmpty() ? 0L : groupWhitelist.get(0);
    }

    public static QqBridgeConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            writeDefault(file);
            return new QqBridgeConfig("", "", List.of(), List.of(), "");
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return new QqBridgeConfig(
                    str(o, "ws_url"),
                    str(o, "access_token"),
                    longs(o, "group_whitelist"),
                    longs(o, "user_whitelist"),
                    str(o, "companion_name"));
        } catch (IOException | RuntimeException ex) {
            Constants.LOG.warn("[numen-qq] unreadable config {} — bridge disabled: {}", file, ex.toString());
            return new QqBridgeConfig("", "", List.of(), List.of(), "");
        }
    }

    private static List<Long> longs(JsonObject o, String key) {
        List<Long> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray(key)) {
                out.add(el.getAsLong());
            }
        }
        return List.copyOf(out);
    }

    private static void writeDefault(Path file) {
        JsonObject o = new JsonObject();
        o.addProperty("ws_url", "");
        o.addProperty("access_token", "");
        o.add("group_whitelist", new JsonArray());
        o.add("user_whitelist", new JsonArray());
        o.addProperty("companion_name", "");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
            Constants.LOG.info("[numen-qq] wrote default config {} — fill in ws_url to enable the bridge", file);
        } catch (IOException ex) {
            Constants.LOG.warn("[numen-qq] failed to write default config {}: {}", file, ex.toString());
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el == null || el.isJsonNull() ? "" : el.getAsString();
    }
}
