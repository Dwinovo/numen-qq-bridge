package com.dwinovo.numen.qq;

import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.agent.tool.ToolCall;
import com.dwinovo.numen.task.TaskResult;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * The companion's outbound half of the QQ bridge: one tool, {@code
 * send_qq_message}. The engine treats it like any other hand — mining a block
 * and posting to a QQ group are the same kind of thing — so the model decides
 * on its own when a QQ reply is called for (a request arrived tagged
 * {@code [QQ群… · 昵称]}, or the owner asked it to notify the group).
 */
public final class SendQqMessageTool implements NumenTool {

    private final NapCatClient client;
    private final QqBridgeConfig cfg;

    public SendQqMessageTool(NapCatClient client, QqBridgeConfig cfg) {
        this.client = client;
        this.cfg = cfg;
    }

    @Override
    public String name() {
        return "send_qq_message";
    }

    @Override
    public String description() {
        return "Send a chat message to a QQ group through the connected QQ bot. "
                + "Use this to answer requests that arrived from QQ (their messages are prefixed "
                + "like [QQ群123456 · 昵称]) or when the owner asks you to notify the QQ group. "
                + "Reply in the language the QQ message used. "
                + "group_id may be omitted: it defaults to the group the bridge is configured for.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "message", Map.of(
                                "type", "string",
                                "description", "The chat text to send to the QQ group."),
                        "group_id", Map.of(
                                "type", "integer",
                                "description", "Target QQ group number. Omit to use the default configured group.")),
                "required", List.of("message"));
    }

    @Override
    public void invoke(ToolCall call) {
        JsonObject args = call.args();
        String message = args.has("message") ? args.get("message").getAsString() : "";
        if (message.isBlank()) {
            call.complete(TaskResult.fail("message is empty").toJson());
            return;
        }
        long groupId = args.has("group_id") ? args.get("group_id").getAsLong() : cfg.defaultGroup();
        if (groupId == 0) {
            call.complete(TaskResult.fail(
                    "no target group: pass group_id or add one to group_whitelist in qq_bridge.json").toJson());
            return;
        }
        client.sendGroupMsg(groupId, message);
        call.complete(TaskResult.ok("sent to QQ group " + groupId).toJson());
    }
}
