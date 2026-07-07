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
        return "Send a chat message over QQ through the connected QQ bot. "
                + "Use this to answer requests that arrived from QQ, or when the owner asks you to "
                + "notify someone on QQ. Requests from a group are prefixed [QQ群123456 · 昵称] — "
                + "reply with that group_id. Requests from a direct chat are prefixed "
                + "[QQ私聊 · 昵称(123456)] — reply with that number as user_id. "
                + "Pass exactly one of group_id / user_id; with neither, the default configured "
                + "group is used. Reply in the language the QQ message used.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "message", Map.of(
                                "type", "string",
                                "description", "The chat text to send."),
                        "group_id", Map.of(
                                "type", "integer",
                                "description", "Target QQ group number, for replies to group messages."),
                        "user_id", Map.of(
                                "type", "integer",
                                "description", "Target QQ account number, for replies to direct ([QQ私聊]) messages.")),
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
        if (args.has("user_id")) {
            long userId = args.get("user_id").getAsLong();
            client.sendPrivateMsg(userId, message);
            call.complete(TaskResult.ok("sent to QQ user " + userId).toJson());
            return;
        }
        long groupId = args.has("group_id") ? args.get("group_id").getAsLong() : cfg.defaultGroup();
        if (groupId == 0) {
            call.complete(TaskResult.fail(
                    "no target: pass group_id or user_id (see the [QQ…] prefix of the request), "
                    + "or add a group to group_whitelist in qq_bridge.json").toJson());
            return;
        }
        client.sendGroupMsg(groupId, message);
        call.complete(TaskResult.ok("sent to QQ group " + groupId).toJson());
    }
}
