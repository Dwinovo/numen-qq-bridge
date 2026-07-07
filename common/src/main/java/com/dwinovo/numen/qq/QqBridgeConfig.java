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
 * {@code ws_url} disables the bridge entirely; an empty {@code group_whitelist}
 * accepts NO group messages — every listened group is an explicit opt-in, so a
 * bot sitting in thirty groups doesn't flood the companion (and the owner's
 * API bill) by default.
 */
public record QqBridgeConfig(
        String wsUrl,
        String accessToken,
        List<Long> groupWhitelist,
        boolean listenPrivate,
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
            return new QqBridgeConfig("", "", List.of(), false, "");
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            List<Long> groups = new ArrayList<>();
            if (o.has("group_whitelist") && o.get("group_whitelist").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("group_whitelist")) {
                    groups.add(el.getAsLong());
                }
            }
            return new QqBridgeConfig(
                    str(o, "ws_url"),
                    str(o, "access_token"),
                    List.copyOf(groups),
                    o.has("listen_private") && o.get("listen_private").getAsBoolean(),
                    str(o, "companion_name"));
        } catch (IOException | RuntimeException ex) {
            Constants.LOG.warn("[numen-qq] unreadable config {} — bridge disabled: {}", file, ex.toString());
            return new QqBridgeConfig("", "", List.of(), false, "");
        }
    }

    private static void writeDefault(Path file) {
        JsonObject o = new JsonObject();
        o.addProperty("ws_url", "");
        o.addProperty("access_token", "");
        o.add("group_whitelist", new JsonArray());
        o.addProperty("listen_private", false);
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
