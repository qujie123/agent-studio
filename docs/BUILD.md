# 构建、运行与维护 / Build, run and maintain

## 中文

### Windows 本地运行

安装版不需要开发工具。源码版需要 JDK 26（设置 `JAVA_HOME` 并将其 `bin` 加入 `PATH`）；Maven Wrapper 会下载 Maven。首次构建需要联网。

```powershell
.\mvnw.cmd -B -ntp verify
.\start-local.cmd
powershell -NoProfile -File scripts/local.ps1 -Action Credentials
# 使用完成后
.\stop-local.cmd
```

源码版本默认打开 `http://127.0.0.1:8081`。数据在仓库 `data/`；初始账号 `local_admin`，密码随机生成并由 Windows DPAPI 保存。不要提交数据、日志或 `local-secrets.xml`。安装版数据与源码版不同，位于 `%LOCALAPPDATA%\AgentStudio\workspace\data`。

只想试用可执行 `powershell -NoProfile -File scripts/start-demo.ps1`，默认端口 8080、账号 `demo_admin`、密码 `DemoOnly_2026!`。这是公开演示密码；Demo 使用内存数据库，重启会清空数据，只应在本机试用。

macOS/Linux 可用 `sh ./mvnw -B -ntp verify` 构建，再运行 `java -jar target/agent-platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo`。Windows 本地启动器依赖 DPAPI；没有提供 macOS/Linux 安装包，也没有完成这些系统的安装验收。

### 模型与 Python

界面中的“模型服务”可配置个人或平台连接。未配置时返回明确标记的模拟结果；真实服务由供应商收费。调用模型会向服务商发送相关输入和上下文。余额不等于平台估算用量，详见[限制](LIMITATIONS.md)。

Python 执行可选：先安装并启动 Docker Desktop（Linux 容器模式），然后运行 `prepare-python-preview.cmd`。首次准备需要下载镜像。网页预览及普通对话不依赖 Docker。不要用多租户公网服务共用个人 Docker 主机。

### PostgreSQL 部署

默认（非 `local`、非 `demo`）配置使用 PostgreSQL。先自行创建空数据库和专用数据库用户，再通过受保护的进程环境配置：

| 环境变量 | 用途 |
| --- | --- |
| `DB_URL` | JDBC 地址，例如 `jdbc:postgresql://127.0.0.1:5432/agent_platform` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号及强密码 |
| `JWT_SECRET` | 独立随机密钥，至少 32 字节 |
| `SETTINGS_ENCRYPTION_KEY` | 独立、稳定的随机加密密钥；不要随意更换 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_EMAIL` | 首次创建管理员的信息；密码至少 12 字符，最多 72 UTF-8 字节 |
| `SERVER_ADDRESS` | 本机试用请明确设为 `127.0.0.1` |

执行 `powershell -NoProfile -File scripts/start.ps1`，或直接 `java -jar target/agent-platform-0.0.1-SNAPSHOT.jar`。Flyway 自动执行迁移；PostgreSQL 为 V1–V9，本地 H2 为 V1–V7。不要开启 Flyway clean 或把测试命令指向业务数据库。默认 PostgreSQL 配置不是已加固的公网部署方案；[已知限制](LIMITATIONS.md)列出了所需的补充措施。

### 测试

```powershell
.\mvnw.cmd -B -ntp verify
node scripts/frontend-regressions.cjs
powershell -NoProfile -File scripts/build-regressions.ps1
# 下列测试需要本机 PostgreSQL 工具，默认路径 C:\Program Files\PostgreSQL\18\bin
powershell -NoProfile -File scripts/test-postgres.ps1
# 全部 Java 测试，包括 PostgreSQL 与真实 Docker/GUI
powershell -NoProfile -File scripts/test-release.ps1
# 浏览器测试还需要 Node.js 20+、Microsoft Edge 和 Playwright
npm ci
powershell -NoProfile -File scripts/test-browser.ps1
```

`test-postgres.ps1 -PostgresBin <目录>` 可指定其他 PostgreSQL 工具位置。它创建独立临时集群，完成后清理该集群；不操作现有服务。浏览器测试使用隔离 Demo 端口 18080。没有 Docker 时可加 `-WithoutDocker`，但这不是完整发布验收。普通 Maven 验证会跳过未配置的 PostgreSQL/Docker 测试；请查看跳过数量。

### 构建 Windows 安装包

准备 JDK 26 和官方 Inno Setup 6 编译器，核对下载来源与签名。在 Windows 执行：

```powershell
powershell -NoProfile -File scripts/package-windows.ps1 -JdkPath "$env:JAVA_HOME" -InnoCompiler "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" -Version 1.7.2
powershell -NoProfile -File scripts/test-windows-package.ps1 -Browser
```

输出在 `dist/windows/1.7.2/`。打包只复制指定程序文件、许可和帮助，内置 jlink 精简运行时；不会复制数据库或真实模型 Key。验收会临时安装、启动、卸载程序，因此遇到已有安装时会拒绝执行。不要为了验收删除自己的安装或数据。二进制构建尚未做到逐字节可复现，版本号 1.7.2 是发布包版本；内部 Maven artifact 仍为 `0.0.1-SNAPSHOT`。

### 备份与维护

停止后台后，备份整个 `data/` 目录，包含数据库和密钥。先在原 Windows 账号、原电脑上验证恢复；DPAPI 不是跨电脑备份格式。卸载保留数据。`scripts/ClearLocalModels.java` 是离线清除模型配置的维护工具，会修改数据库；不是启动步骤，使用前必须停机、备份并阅读源码。`verify-local-data.jsh` 是历史迁移检查辅助脚本，不能替代当前完整测试。

## English

### Local Windows use

The installer needs no development tools. Source builds require JDK 26, with `JAVA_HOME` set and its `bin` on `PATH`. Maven Wrapper downloads Maven; the first build needs internet access. Run `mvnw.cmd -B -ntp verify`, then `start-local.cmd`. Display initial credentials with `powershell -NoProfile -File scripts/local.ps1 -Action Credentials`; stop with `stop-local.cmd`.

The source workspace defaults to `http://127.0.0.1:8081`, stores data in the repository's `data/`, and creates `local_admin` with a random, DPAPI-protected password. Never commit data, logs or `local-secrets.xml`. The installer has a separate workspace at `%LOCALAPPDATA%\AgentStudio\workspace\data`.

For a disposable demo, run `powershell -NoProfile -File scripts/start-demo.ps1`: port 8080, user `demo_admin`, password `DemoOnly_2026!`. This password is deliberately public; demo data is in memory and disappears on restart. Use it only locally.

On macOS/Linux, build with `sh ./mvnw -B -ntp verify`, then run `java -jar target/agent-platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo`. The persistent Windows launcher requires DPAPI. No macOS/Linux installer or installation validation is provided.

### Models and Python

Configure personal or platform connections in model services. Without a provider, responses are explicitly marked as mock output. Real providers charge separately and receive relevant inputs/context. Balance and local usage estimates differ; see [limitations](LIMITATIONS.md).

Python execution is optional. Install/start Docker Desktop in Linux-container mode, then run `prepare-python-preview.cmd`; initial setup downloads images. Static previews and ordinary chat do not require Docker. Do not share a personal Docker host with a public multi-tenant service.

### PostgreSQL deployment

The default profile (neither `local` nor `demo`) uses PostgreSQL. Create an empty database and dedicated database user. Configure protected process environment variables: `DB_URL` (for example `jdbc:postgresql://127.0.0.1:5432/agent_platform`), `DB_USERNAME`, `DB_PASSWORD`, an independent random `JWT_SECRET` of at least 32 bytes, and a separate stable `SETTINGS_ENCRYPTION_KEY`. Do not casually rotate encryption keys. Set `ADMIN_USERNAME`, `ADMIN_PASSWORD` and `ADMIN_EMAIL` for initial bootstrap; passwords require at least 12 characters and at most 72 UTF-8 bytes. Set `SERVER_ADDRESS=127.0.0.1` for local use.

Run `powershell -NoProfile -File scripts/start.ps1` or `java -jar target/agent-platform-0.0.1-SNAPSHOT.jar`. Flyway applies PostgreSQL V1–V9 or H2 V1–V7 migrations. Never enable Flyway clean or point test commands at business databases. This is not a hardened public-hosting configuration; read [limitations](LIMITATIONS.md).

### Testing and installer builds

The commands in the Chinese section apply unchanged. Start with Maven verify, `node scripts/frontend-regressions.cjs` and `scripts/build-regressions.ps1`. `test-postgres.ps1` creates/removes an isolated temporary cluster, defaulting to PostgreSQL 18 tools at `C:\Program Files\PostgreSQL\18\bin`; override with `-PostgresBin`. It does not operate the existing database service.

`test-release.ps1` adds real Docker/GUI checks to the PostgreSQL/Java run. Browser checks need Node.js 20+, Microsoft Edge and Playwright (`npm ci`), then `scripts/test-browser.ps1`; they use an isolated demo on port 18080. `-WithoutDocker` provides reduced coverage. Plain Maven verification skips external-service tests unless configured; inspect skip counts.

To build the Windows installer, obtain JDK 26 and the official Inno Setup 6 compiler, verify their provenance/signatures, and run the packaging command above with your compiler path. Output is `dist/windows/1.7.2/`. Only allowlisted program files, licenses and help are copied, together with a jlink runtime—not databases or real provider keys. `test-windows-package.ps1 -Browser` installs, launches and uninstalls a temporary package; it refuses to run over an existing installation. Do not remove personal data/installations to bypass that safeguard. Builds are not byte-for-byte reproducible. Release package 1.7.2 retains the internal Maven artifact version `0.0.1-SNAPSHOT`.

### Backup and maintenance

Stop the backend before copying the complete `data/` directory, including database and secrets. Validate restoration on the original Windows account/computer; DPAPI is not a cross-machine backup format. Uninstall preserves data. `scripts/ClearLocalModels.java` is an offline database-mutating maintenance utility, not a startup step: stop, back up and read the source before use. `verify-local-data.jsh` is a historical migration helper, not a substitute for current full regression tests.
