# Numen QQ Bridge (numen-qq-mcp)

给 Minecraft 里的 [Numen](https://github.com/Dwinovo/minecraft-numen) AI 同伴绑定一个 QQ 号。

你在 QQ 上私聊它:"把家里的小麦收了"——游戏里的同伴自己规划、自己动手,干完私聊回你:"收了 64 组小麦,已放进箱子。"人不在电脑前,一样指挥你的 Minecraft 世界。拉进群里也是同样的用法。

```
你的QQ ──NapCatQQ (OneBot 11 正向WS)──▶ numen-qq ──NumenGateway.enqueue──▶ 同伴的消息队列 ──▶ 同伴干活
你的QQ ◀──send_private_msg / send_group_msg── numen-qq ◀──send_qq_message 工具── 同伴干完自己汇报
```

- **入站**:白名单内的私聊/群消息被包装成 `[QQ私聊 · 昵称(QQ号)] 内容` 或 `[QQ群123456 · 昵称] 内容`,进入同伴的消息队列。同伴空闲就立刻开工;正忙的话,消息会在下一个动作间隙被它看到(游戏聊天面板里以 ⌛ 排队行显示);
- **出站**:本模组向 numen-api 注册一个 `send_qq_message` 工具。回不回、回什么、什么时候回,由同伴的大脑决定——对它来说,给 QQ 发消息和挥一下镐子是同一种动作。标签里带着号码,模型自己认目标:私聊回私聊,群里回群里;
- **零第三方依赖**:JDK 内置 WebSocket + Gson,不引入任何机器人框架。

本项目同时是 numen-api 公开集成接口的参考实现:整座桥只用了两个公开入口——`NumenGateway.enqueue`(入站)和 `ToolRegistry.register`(出站)。想给 Numen 接 Discord、Telegram、直播弹幕,照抄这个仓库的结构即可。

---

## 环境要求

| 组件 | 说明 |
|---|---|
| Minecraft 1.21.1 | Fabric 或 NeoForge |
| Java 21 | 随游戏 |
| [Numen](https://github.com/Dwinovo/minecraft-numen) | 同伴本体(numen-api 引擎已内嵌其中),需 0.0.4 及以上引擎(含 `NumenGateway`) |
| [NapCatQQ](https://github.com/NapNeko/NapCatQQ) | QQ 机器人框架,挂在官方 QQNT 客户端上运行 |
| 一个机器人 QQ 号 | 建议注册小号专用,别拿大号当机器人 |

安装:把本模组的 jar 和 Numen 的 jar 一起放进 `mods/`(Fabric 版另需前置 Fabric API)。本模组装在**玩家客户端**——同伴由主人的客户端驱动、烧主人的 API key,服务端不用装。

---

## 第一步:部署 NapCatQQ

1. 电脑上安装官方 QQ(NT 版);
2. 下载 [NapCatQQ Releases](https://github.com/NapNeko/NapCatQQ/releases) 里的 `NapCat.Shell.zip`,解压到任意目录;
3. **以管理员身份**运行 `launcher.bat`(Win10 用 `launcher-win10.bat`);
4. 浏览器打开 `http://127.0.0.1:6099` 进 WebUI(登录 token 看终端日志,或事先在 `config/webui.json` 里自定义),用**机器人 QQ 号**扫码登录;
5. WebUI → **网络配置** → 新建 → **Websocket 服务器**(注意选"服务器",本模组是主动连过去的客户端):

   | 配置项 | 推荐值 |
   |---|---|
   | Host | `127.0.0.1`(与游戏同机;异机部署填 `0.0.0.0`) |
   | Port | `3001` |
   | Token | 设一个,防本机其他程序乱连 |
   | 消息格式 | array 或 string 都行(桥读 `raw_message`,两种格式下都存在) |

   熟悉配置文件的话,直接编辑 `config/onebot11_<机器人QQ号>.json` 的 `network.websocketServers` 再重启,效果相同;
6. 以后重启免扫码:`launcher.bat <机器人QQ号>`(快速登录)。

**私聊模式的硬性前提:用你自己的 QQ 加机器人号为好友。** QQ 不允许陌生人互发私聊,不加好友消息根本到不了 NapCat。

---

## 第二步:配置本模组

启动一次游戏,自动生成 `config/numen/qq_bridge.json`(PCL/HMCL 开了版本隔离的话,在 `versions/<版本名>/config/numen/` 下):

```json
{
  "ws_url": "ws://127.0.0.1:3001",
  "access_token": "第一步设的token,没设就留空",
  "group_whitelist": [],
  "user_whitelist": [你自己的QQ号],
  "companion_name": ""
}
```

| 字段 | 含义 |
|---|---|
| `ws_url` | NapCat 正向 WS 地址。**留空 = 关闭桥接** |
| `access_token` | 与 NapCat 里设的 token 一致,不一致会握手被拒 |
| `group_whitelist` | 接收哪些**群**的消息 |
| `user_whitelist` | 允许哪些 **QQ 号私聊**指挥同伴——填上你自己的号 |
| `companion_name` | 消息投递给哪个同伴,留空 = 名册上的第一个 |

**安全模型一句话:两个白名单都是"空 = 一条不收",默认全拒、显式放行。** 不在名单里的消息在桥这一层就被静默丢弃,到不了模型,一个 token 都不花。放谁进白名单,等于授权谁花你的大模型 API 额度——请只放信得过的人。

---

## 第三步:开始使用

1. 进入存档,召唤同伴,**打开一次它的聊天面板**(按 G),让它的 agent 循环就位;
2. 日志出现 `[numen-qq] connected to ws://127.0.0.1:3001` 即连接成功;
3. 用你的 QQ 私聊机器人:"你在哪?附近有什么?"
   - 游戏聊天面板出现 `⌛ [QQ私聊 · 你的昵称(QQ号)] …` 排队行;
   - 同伴执行、完成后自己调 `send_qq_message` 私聊回你。

之后就是自由发挥:让它挖矿、盖房、整理箱子、汇报库存——所有游戏里能下的指令,QQ 上都能下。

---

## 排障速查

| 症状 | 检查 |
|---|---|
| 日志反复 `connect failed, retrying` | NapCat 的 WS 是"服务器"模式吗?端口对吗?`access_token` 两边一致吗? |
| 私聊没任何反应 | ①大号和机器人是好友吗;②`user_whitelist` 填的是**你大号**的号(不是机器人的);③NapCat 里那个 WS 服务显示已启动吗 |
| 日志 `QQ message dropped — no companion on the roster` | 还没召唤同伴 |
| 日志 `enqueue … refused — open the companion's chat once` | 进游戏后打开一次同伴聊天面板 |
| 消息进了游戏但同伴装死 | 看 Numen 本体的 API key/模型配置(Settings 页),这一步与桥无关 |
| NapCat 端口起不来 | 检查是否重复启动了多个 NapCat 实例,全部结束进程后重启一个 |

游戏侧一切诊断信息都在日志的 `[numen-qq]` 行里:连接、重连、丢弃原因、投递结果,一行一因。

---

## 机制与边界(给开发者)

- **入站走消息队列**:`NumenGateway.enqueue(companionUuid, message)` 把字符串**原样**送进同伴的主人提示队列,与聊天框打字走同一条路,在协议合法点(工具批次边界)被模型看到。消息格式(来源标签等)是桥自己的约定,引擎不做任何特化;
- **出站走工具**:桥注册 `send_qq_message`(参数 `message` + `group_id`/`user_id` 二选一),模型从请求前缀里读出回信目标。没有回调、没有轮询——大脑用手回话;
- **工具调用队列永不开放**:它里面的每一项都是某轮 LLM 决策的产物,绑定着对话协议里的 `tool_call_id`。外部注入工具调用等于绕过大脑操作身体,这条边界是刻意封死的;
- 多加载器结构:逻辑全在 `common/`(五个类:配置、WS 客户端、装配、工具、常量),`fabric/`/`neoforge/` 各一个入口类。

## 从源码构建

```bash
./gradlew :fabric:build :neoforge:build
# 产物在 fabric/build/libs 与 neoforge/build/libs
```

依赖 `com.dwinovo.numen:numen-api-*-1.21.1:0.0.1-SNAPSHOT`,从 [numen-maven](https://github.com/Dwinovo/numen-maven) 解析。

## 许可

MIT。
