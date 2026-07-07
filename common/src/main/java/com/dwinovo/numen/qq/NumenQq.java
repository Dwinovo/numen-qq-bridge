package com.dwinovo.numen.qq;

import com.dwinovo.numen.agent.tool.ToolRegistry;
import com.dwinovo.numen.api.NumenGateway;
import com.dwinovo.numen.client.agent.NumenRoster;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * Loader-agnostic core of the bridge — both entry points call
 * {@link #initClient} once during client init.
 *
 * <p>The whole mod is a thin "subclass" over numen-api's two open ends:
 * <ul>
 *   <li><b>inbound</b> — whitelisted QQ messages become
 *       {@code NumenGateway.enqueue(companion, "[QQ群… · 昵称] 内容")}: the string
 *       shape is this bridge's own convention, the engine takes it verbatim;</li>
 *   <li><b>outbound</b> — a {@code send_qq_message} {@link com.dwinovo.numen.agent.tool.NumenTool}
 *       lets the companion decide when and what to reply. There is no reply
 *       callback anywhere: the brain talks back the same way it swings a
 *       pickaxe, by calling a tool.</li>
 * </ul>
 */
public final class NumenQq {

    private NumenQq() {}

    public static void initClient(Path configDir) {
        QqBridgeConfig cfg = QqBridgeConfig.load(configDir.resolve("numen").resolve("qq_bridge.json"));
        if (!cfg.enabled()) {
            Constants.LOG.info("[numen-qq] ws_url not set — bridge idle (config/numen/qq_bridge.json)");
            return;
        }
        NapCatClient client = new NapCatClient(cfg, msg -> deliver(cfg, msg));
        client.start();
        ToolRegistry.register(new SendQqMessageTool(client, cfg));
        Constants.LOG.info("[numen-qq] bridge up: {} group(s) whitelisted, private={}",
                cfg.groupWhitelist().size(), cfg.listenPrivate());
    }

    /**
     * Route one QQ message to the companion's queue. Runs on the websocket
     * thread — roster lookup and enqueue both belong to the client main
     * thread, so hop first.
     */
    private static void deliver(QqBridgeConfig cfg, NapCatClient.Inbound msg) {
        Minecraft.getInstance().execute(() -> {
            NumenRoster.Entry target = pickCompanion(cfg);
            if (target == null) {
                Constants.LOG.warn("[numen-qq] QQ message dropped — no companion on the roster yet");
                return;
            }
            String label = msg.groupId() != 0
                    ? "[QQ群" + msg.groupId() + " · " + msg.senderName() + "]"
                    : "[QQ私聊 · " + msg.senderName() + "]";
            boolean ok = NumenGateway.enqueue(target.uuid(), label + " " + msg.text());
            if (!ok) {
                Constants.LOG.warn("[numen-qq] enqueue for {} refused — open the companion's chat once "
                        + "so its agent loop exists", target.name());
            }
        });
    }

    /** The configured companion by name, or the roster's first entry when unset. */
    private static NumenRoster.Entry pickCompanion(QqBridgeConfig cfg) {
        var entries = NumenRoster.instance().entries();
        if (entries.isEmpty()) return null;
        if (cfg.companionName().isBlank()) return entries.get(0);
        for (NumenRoster.Entry e : entries) {
            if (cfg.companionName().equals(e.name())) return e;
        }
        return null;
    }
}
