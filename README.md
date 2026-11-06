# GeyserConnect

基于 [GeyserMC/GeyserConnect](https://github.com/GeyserMC/GeyserConnect) 修改的 Geyser 扩展，为基岩版玩家提供服务器选择菜单、个人收藏及中文界面。

## 功能

- 进入 Geyser 后显示菜单，公共基岩服与个人收藏直接列在主页。
- 置顶“连接到java服务器”入口，继续当前 Geyser 宿主配置的 Java 连接与认证流程。
- 收藏支持添加、编辑、删除及可选名称；未填写名称时显示地址。
- 内置简体中文、英文语言文件，支持自定义文字。
- 个人收藏支持 JSON、SQLite、MySQL，默认关闭。
- 支持 Geyser Standalone，以及 ViaProxy 中的 Geyser。

## 构建

需要 **JDK 21**，并将 `JAVA_HOME` 指向 JDK 安装目录。项目包含 Gradle Wrapper，无需额外安装 Gradle；首次构建需要联网下载 Gradle 与 Maven 依赖。

```powershell
git clone https://github.com/Kongtu5i/geyser-bedrockconnect.git
cd geyser-bedrockconnect
.\gradlew.bat clean build
```

Linux / macOS：

```sh
chmod +x gradlew
./gradlew clean build
```

生成文件：`build/libs/GeyserConnect.jar`。

构建使用 Gradle 8.14.3；Geyser API/Core 固定为 `2.12.1-20261105.083747-17`，具体依赖版本见 `gradle/libs.versions.toml`。依赖 JAR 校验记录保留在 `gradle/verification-metadata.xml`。

这是最小源码项目，保留运行源码、资源、构建文件与许可证，不包含开发测试、测试宿主、DNS 部署示例或自动发布流程。`build` 完成编译与打包；此精简版本未包含测试代码。

## 安装

1. 关闭 Geyser；替换旧扩展前备份扩展的数据目录。
2. 将 `GeyserConnect.jar` 放入该 Geyser 实例的 `extensions` 目录，同一实例只保留一份。
3. ViaProxy 环境需要先安装 Geyser-ViaProxy，再将本扩展放入 **Geyser 自己的** `extensions` 目录。
4. 重启 Geyser。首次加载会生成 `config.yml` 和 `languages` 目录；玩家首次进入时生成 `welcome.txt`。

本扩展依赖 Geyser 的内部实现，建议使用与上述编译依赖兼容的 Geyser 2.12.1 构建；其他版本需要重新验证。它是 Geyser 扩展，需要由 Geyser 加载。

基岩版的监听地址、端口、NetherNet / RakNet、信令及 Java 目标与认证，均在 Geyser 或 ViaProxy 中配置。

## 配置

以下均指 **GeyserConnect 扩展数据目录** 中的 `config.yml`；除欢迎文字外，修改后重启生效。

### 语言与欢迎文字

```yaml
language-file: languages/zh_CN.yml
welcome-file: welcome.txt
hard-player-limit: false
```

- 英文界面改为 `language-file: languages/en_US.yml`。
- 自定义语言可复制内置文件并填写相对路径，使用 UTF-8，保留键名及 `{player}` 等占位符。
- 缺失或无效的语言条目回退到内置英文。升级时已有语言文件不会被覆盖，需要自行补充新键。
- `welcome.txt` 非空时进服显示欢迎内容，修改文字后无需重启。
- `hard-player-limit` 开启后按宿主 Geyser 的人数上限限制进入。

### 公共基岩服务器

`servers` 列表按配置顺序显示在个人收藏前；玩家无法编辑这些公共条目。用自己的目标地址替换示例：

```yaml
servers:
  - name: 我的基岩服务器
    address: bedrock.example.com
    port: 19132
    bedrock: true
    category: OFFICIAL
```

`name` 可以省略；`category` 用于兼容原格式，不影响 `bedrock: true` 条目的显示。使用 `servers: []` 可关闭公共列表。仓库内默认配置仍保留上游服务器示例，其地址是否可用需自行确认。

### 个人收藏

```yaml
custom-servers:
  enabled: true
  max: 10
  storage-type: json
  mysql:
    user: geyser_connect
    pass: ""
    database: geyser_connect
    host: localhost
    port: 3306
```

| `storage-type` | 存储位置 / 要求 |
| --- | --- |
| `json` | 扩展数据目录的 `players/<玩家XUID>.json` |
| `sqlite` | 扩展数据目录的 `players.db` |
| `mysql` | 按 `mysql` 配置连接已有数据库 |

默认 `enabled: false`。关闭个人收藏不会隐藏公共列表。更换存储类型不会自动迁移数据；迁移前先备份。

旧记录中的 Java 服务器不会出现在基岩转服列表，原数据仍保留。历史 `online` 字段只为数据兼容保留，不参与基岩转服，也不决定 Java 正版认证。旧 `vhost` 配置和 `passAsVhost` 用法已不再生效。

### Java 入口与 ConnectPlus

“连接到java服务器”使用 **当前 Geyser 宿主配置的默认 Java 目标**，并继续宿主原有的认证流程。Java 正版账号、Floodgate、ViaProxy 的 `proxy-online-mode` / `auth-method` 等应在对应宿主中配置。

若要连接 ConnectPlus，先将 Geyser 的 Java 目标配置为已安装 ConnectPlus 的 ViaProxy 入口；Java 连接交接后，后续选服与账号处理交给 ViaProxy / ConnectPlus。本扩展的基岩服务器收藏不提供 Java 地址或认证选项。

进入 Java 连接流程后，当前会话交给宿主处理；本扩展不提供从 Java 游戏会话返回菜单的跨服功能。

## 命令

| 命令 | 用途 |
| --- | --- |
| `/geyserconnect menu` | 玩家处于入口菜单阶段时重新打开菜单 |
| `geyserconnect messageall chat <消息>` | 控制台向菜单中的玩家发送聊天文字 |
| `geyserconnect messageall gui <消息>` | 控制台向菜单中的玩家显示通知表单 |
| `geyserconnect transferall <地址>[:端口]` | 控制台将入口会话转至指定基岩服务器，默认端口 19132 |

IPv6 带端口示例：`[2001:db8::1]:19132`。转服目标必须能被玩家客户端直接访问。

## 许可与来源

基于 [GeyserMC/GeyserConnect](https://github.com/GeyserMC/GeyserConnect)（上游基线提交 `42664798e4f7fe1d08534f6718af23c70dffebc5`）修改，保留原作者与版权声明，使用 [MIT 许可证](LICENSE)。Geyser 与 ViaProxy 为各自项目维护的独立组件。
