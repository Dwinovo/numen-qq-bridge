# Numen QQ Bridge (numen-qq-mcp)

把 **QQ 群聊**和 **Minecraft 里的 Numen AI 同伴**接通:群友在 QQ 里 @ 它干活,它在游戏里动手,干完自己回到群里汇报。

```
QQ 群  ──NapCatQQ (OneBot 11 WebSocket)──▶  numen-qq  ──NumenGateway.enqueue──▶  同伴的消息队列
QQ 群  ◀──send_group_msg────────────────  numen-qq  ◀──send_qq_message 工具──  同伴自己决定回复
```

- **入站**:白名单群的消息包装成 `[QQ群123456 · 昵称] 内容`,进入同伴的消息队列——同伴空闲立刻开工,忙的时候在下一个动作间隙看到;
- **出站**:本模组向 numen-api 注册一个 `send_qq_message` 工具。同伴回不回、回什么、什么时候回,由它的大脑决定——对它来说,给 QQ 群发消息和挥一下镐子是同一种动作;
- **零第三方依赖**:JDK 内置 WebSocket + Gson,和 numen 家族同一套哲学。

这是 [numen-api](https://github.com/Dwinovo/numen-api) 公开集成接口的参考实现:一个外部世界桥,只用了两个公开入口——`NumenGateway.enqueue`(入站)和 `ToolRegistry.register`(出站)。

## 环境要求

| 组件 | 说明 |
|---|---|
| Minecraft 1.21.1 | Fabric 或 NeoForge |
| [Numen](https://github.com/Dwinovo/minecraft-numen) | 同伴本体(numen-api 引擎已内置其中) |
| [NapCatQQ](https://github.com/NapNeko/NapCatQQ) | QQ 机器人框架,开启 OneBot 11 **正向 WebSocket** |
| Java 21 | 随游戏 |

## 使用步骤

1. 部署 NapCatQQ,登录机器人 QQ 号,在设置里开启**正向 WebSocket 服务**(记下端口,如 `3001`;建议同时设置 token);
2. 把本模组放进 `mods/`(与 Numen 同装),启动一次游戏,生成配置文件 `config/numen/qq_bridge.json`;
3. 填配置:

```json
{
  "ws_url": "ws://127.0.0.1:3001",
  "access_token": "你的NapCat token,没设就留空",
  "group_whitelist": [123456789],
  "listen_private": false,
  "companion_name": ""
}
```

| 字段 | 含义 |
|---|---|
| `ws_url` | NapCat 正向 WS 地址。**留空 = 关闭桥接** |
| `access_token` | NapCat 的鉴权 token,可留空 |
| `group_whitelist` | 接收哪些群的消息。**空列表 = 一个群也不收**,每个群都是显式授权——机器人挂在三十个群里也不会刷爆你的 API 账单 |
| `listen_private` | 是否接收机器人收到的私聊 |
| `companion_name` | 消息投递给哪个同伴,留空 = 名册上的第一个 |

4. 进入存档,召唤同伴并**打开一次它的聊天面板**(让它的 agent 循环就位),之后群里说话它就能收到。

## 注意事项

- 本模组装在**玩家客户端**(同伴由主人的客户端驱动、烧主人的 API key),不装服务端;
- 群消息会消耗你自己的大模型 token——白名单请只加信得过的群;
- 同伴执行 QQ 指令的效果与所选模型能力直接相关。

## 构建

```bash
./gradlew :fabric:build :neoforge:build
# 产物在 fabric/build/libs 与 neoforge/build/libs
```

## 许可

MIT。
