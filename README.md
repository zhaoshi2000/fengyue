# AI风月本地版

基于 [aquantancee.xyz](https://aquantancee.xyz/) 可访问首页信息重建的本地项目。前端位于 `public/`，后端使用 **Java 21、Spring Boot 4.1.1 和 MySQL 8**。目标站在本机无法完整访问，因此界面不是原站源码或逐像素复制。

## 启动

需要 Java 21、Maven 3.9+ 和本地 MySQL 8。首次在 PowerShell 中运行：

```powershell
cd D:\git\aquantancee-local
.\setup-local-db.ps1
.\run.ps1
```

`setup-local-db.ps1` 会提示输入本机 MySQL 管理员密码，创建 `aquantancee` 数据库和专用账号，并将专用账号的连接信息写入忽略 Git 提交的 `.env.local`。该脚本只需执行一次；重新执行会轮换项目账号密码。

`run.ps1` 从 `.env.local` 读取配置，使用 Maven 启动 Spring Boot。打开 <http://localhost:3000>。首次启动会自动创建表和示例故事；注册后可直接登录。也可设置 `APP_PORT` 更换端口。

手动运行 JAR 时，需将 `.env.local` 中的 `DB_URL`、`DB_USER`、`DB_PASSWORD` 设置为进程环境变量，再使用 Java 21 执行 `target/aquantancee-local-1.0.0.jar`。

## 功能

- 注册、登录、退出和密码修改；密码以 BCrypt 哈希保存，会话令牌以 SHA-256 哈希保存于 MySQL，浏览器使用 `HttpOnly` Cookie。
- 故事搜索、分区、排序和分页；登录用户可发布、编辑和删除自己的角色卡。
- 角色卡包含人物设定、场景、开场白、示例对话、快捷选项、背景图片和作者编写的 CSS。卡片预览与聊天场景通过无脚本沙盒渲染 CSS，支持动画，不会覆盖站点的登录和导航界面。
- 作者可以上传 PNG/JPEG 背景图（单张不超过 8 MB），文件保存在 `data/uploads/`；迁移本地项目时请同时备份这个目录和 MySQL 数据库。
- 收藏、关注作者、浏览历史、按北京时间每日签到以及按账号保存的聊天记录。
- 独立互动聊天页、多个会话、开场白、历史消息、快捷选项、消息复制/编辑/删除、最后一条 AI 回复重新生成，以及账号隔离；示例故事地址为 <http://localhost:3000/zh/explore/installed/6a46cbbf-a5f5-47fa-8568-44903607d0bf>。
- 首页静态资源与 API 由同一 Spring Boot 服务提供，前端无须单独启动。

聊天后端支持兼容 Chat Completions 的模型接口。未设置 `AI_API_KEY` 时，页面明确显示“演示模式”，回复由本地规则产生；设置密钥后后端会调用模型，并将回复保存在 MySQL。当前本地配置使用 `gpt-6-luna`，请求地址为 `http://82.157.64.38:28082/v1/chat/completions`。首次配置密钥时运行：

```powershell
.\set-ai-key.ps1
.\run.ps1
```

密钥保存在 Git 忽略的 `.ai-key.dpapi` 中，由当前 Windows 用户的 DPAPI 加密；`run.ps1` 启动时将其加载到进程环境变量。该文件只能在同一 Windows 用户环境中解密，迁移电脑时需重新运行 `set-ai-key.ps1`。当前模型服务使用 HTTP，调用时密钥会以明文通过网络传输；正式使用建议改用 HTTPS 地址。更换服务时可在 `.env.local` 中修改 `AI_API_URL` 和 `AI_MODEL`。生图、支付、邀请奖励与 App 下载需要相应的外部服务。旧版 SQLite 文件仍保存在 `data/` 作为备份；切换时该文件中没有注册用户或聊天记录。

## API

| 功能 | 接口 |
| --- | --- |
| 状态和首页 | `GET /api/health`、`GET /api/bootstrap` |
| 注册、登录、退出 | `POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/logout` |
| 当前用户与密码 | `GET /api/me`、`GET /api/me/items`、`PUT /api/me/password` |
| 故事 | `GET/POST /api/items`、`GET/PUT/DELETE /api/items/{id}` |
| 背景图 | `POST /api/media`（multipart `file`）、`GET /media/{name}` |
| 互动 | `POST /api/items/{id}/visit`、`POST /api/items/{id}/favorite`、`POST /api/follow/{author}`、`POST /api/checkin` |
| 独立聊天 | `GET /api/chat/config`、`GET/POST /api/items/{id}/conversations`、`GET/DELETE /api/conversations/{id}`、`POST /api/conversations/{id}/messages`、`PATCH/DELETE /api/conversations/{id}/messages/{messageId}`、`POST /api/conversations/{id}/regenerate` |
| 旧版聊天兼容 | `GET/POST /api/items/{id}/chat` |

`GET /api/items` 支持 `q`、`category`、`sort`、`view`、`page` 和 `limit` 参数。写接口使用 JSON 请求体；需要登录的接口依靠同源 Cookie 验证。

## 验证

```powershell
mvn.cmd -DskipTests package
```

已在本机 MySQL 和浏览器中验证角色卡发布与二次编辑、图片上传、自定义 CSS 动画、开场白、发消息、刷新恢复历史、消息编辑/重新生成/删除，以及另一账号无法读取会话。模型请求格式已使用本地模拟服务验证；真实模型调用仍需配置自己的 API Key。
