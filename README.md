# Agent Studio · 本地智能体工作台 / Local Agent Workspace

把智能体配置、多轮对话和项目交付放在一个工作台里。

Configure agents, continue conversations, and inspect project deliverables in one workspace.

[下载 Windows 安装包 / Windows downloads](https://github.com/qujie123/agent-studio/releases) · [构建指南 / Build guide](docs/BUILD.md) · [已知限制 / Limitations](docs/LIMITATIONS.md) · [验证记录 / Verification](docs/VERIFICATION.md)

## 中文

Agent Studio 是基于 Java / Spring Boot 的本地优先智能体工作台。它把模型连接、Skills、知识文档、任务与对话历史组织在一起，并提供静态网页预览、代码文件及完整项目下载。适合个人实验、学习和受控环境中的原型；不是已完成安全认证的企业 SaaS。

### 能做什么

- **管理智能体与资源**：创建智能体，为不同智能体绑定自己的 Skills 和知识文档。
- **持续对话与任务**：多轮上下文、流式文本、会话历史、后台任务队列、运行快照与用量记录。
- **连接模型服务**：14 个可编辑服务商预设及自定义地址；OpenAI Chat Completions 与 Anthropic Messages 文本协议。个人配置与平台默认配置分开。
- **查看并下载成品**：隔离静态网页预览、保留文件名与后缀的单文件下载、完整 ZIP，以及兼容浏览器中的文件夹保存。
- **可选 Python 环境**：在 Docker 中运行 Python Skills，预览指定 GUI 库的录屏；不在主机直接运行生成代码。
- **本地数据与界面**：H2 持久化、PostgreSQL 部署配置、用户与审计管理、黑白主题和分组导航。

### 快速开始

**Windows 用户**：从 [Releases](https://github.com/qujie123/agent-studio/releases) 下载 `AgentStudio-1.7.2-windows-x64-setup.exe`，核对同版本的 `SHA256SUMS.txt` 后安装。启动桌面或开始菜单的 Agent Studio；界面会在默认浏览器打开。包内有 Java 和 H2，不需要 IDEA、Maven 或 Node。

首次启动提供 `local_admin` 和随机密码，开始菜单“查看初始登录信息”可再次查看；网页改密后以新密码为准。安装包没有预置真实模型 Key。到“模型服务 → API 接入与余额”填写自己的配置，调用真实模型可能产生费用。

数据位于 `%LOCALAPPDATA%\AgentStudio\workspace\data`。关闭网页不会停止后台；请从开始菜单“停止 Agent Studio”。更新、卸载或备份前先停止，卸载保留用户数据。不要删除密钥文件，DPAPI 数据不能直接跨电脑或 Windows 账号使用。

**从源码运行**：安装 JDK 26，在仓库根目录执行：

```powershell
.\mvnw.cmd verify
.\start-local.cmd
```

首次构建需要联网下载 Maven 及依赖。本地源码版入口默认是 `http://127.0.0.1:8081`，初始密码用 `powershell -NoProfile -File scripts/local.ps1 -Action Credentials` 查看。更多平台命令、演示模式、PostgreSQL、测试和安装器构建见[构建指南](docs/BUILD.md)。

### 先了解限制

安装包未签名，只在当前 Windows 11 x64 环境验收。Python 预览需要另行准备 Docker；GUI 是录屏而不是交互桌面。不是所有服务商协议和余额接口都支持，生成的任意工程也不保证无需构建就能运行。知识检索不是完整向量 RAG，工作流不是通用代理引擎。

本项目尚无独立安全审计结论，缺少 MFA、登录限流等公网部署所需措施，不建议直接暴露到互联网。全部边界与不足见[已知限制](docs/LIMITATIONS.md)。

### 许可与参与

项目原创代码以 [MIT](LICENSE) 开源；第三方组件保留各自许可，详见[第三方说明](docs/THIRD_PARTY_NOTICES.md)。欢迎提交可复现的问题、测试或小范围改进，见[贡献说明](CONTRIBUTING.md)。不要在 Issue 中上传真实 Key、数据库、密码或未脱敏日志。

## English

Agent Studio is a local-first agent workspace built with Java and Spring Boot. It brings model connections, Skills, knowledge documents, tasks and conversation history together, with static-site previews and downloadable code/projects. It is intended for personal experiments, learning and controlled prototypes—not a security-certified enterprise SaaS.

### What it does

- **Agents and resources**: create agents and attach your own Skills and knowledge documents to each one.
- **Conversations and tasks**: multi-turn context, streaming text, saved history, background task queues, execution snapshots and usage records.
- **Model connections**: 14 editable provider presets plus custom endpoints, with OpenAI Chat Completions and Anthropic Messages text protocols. Personal settings are separate from platform defaults.
- **Deliverable previews and downloads**: isolated static-site previews, correctly named individual files, complete ZIPs and folder saving in compatible browsers.
- **Optional Python environment**: Docker-based Python Skills and recorded previews for selected GUI libraries; generated code does not execute directly on the host.
- **Local data and UI**: persistent H2, PostgreSQL deployment configuration, user/audit management, monochrome themes and grouped navigation.

### Quick start

**Windows users**: download `AgentStudio-1.7.2-windows-x64-setup.exe` from [Releases](https://github.com/qujie123/agent-studio/releases), verify it against that release's `SHA256SUMS.txt`, and install. Launch Agent Studio from the desktop or Start menu. The UI opens in your default browser. Java and H2 are bundled; IDEA, Maven and Node are not required.

The first launch provides `local_admin` and a random password. The Start menu can display the initial credentials again; after changing the password in the web UI, use the new one. No real provider key is bundled. Configure your own connection under model services; real model calls may incur provider charges.

Data lives in `%LOCALAPPDATA%\AgentStudio\workspace\data`. Closing the browser does not stop the server. Use the Stop entry in the Start menu before updating, uninstalling or backing up. Uninstall preserves user data. Do not delete secret files; DPAPI-protected data cannot simply be moved between computers or Windows accounts.

**From source**: install JDK 26, then run `mvnw.cmd verify` and `start-local.cmd` from the repository root on Windows. The first build downloads Maven and dependencies. The source-based local workspace defaults to `http://127.0.0.1:8081`; display its initial credentials with `powershell -NoProfile -File scripts/local.ps1 -Action Credentials`. See the [build guide](docs/BUILD.md) for other platforms, demo mode, PostgreSQL, testing and installer builds.

### Know the limits

The unsigned installer has been verified on the current Windows 11 x64 machine only. Python previews require separately prepared Docker; GUI previews are recordings, not interactive desktops. Not every provider protocol or balance API is supported, and generated projects may still need build steps. Knowledge retrieval is not a full vector RAG pipeline, and workflows are not a general autonomous-agent engine.

There is no independent security-audit clearance. MFA, login rate limiting and other controls needed for public hosting are absent; direct internet exposure is not recommended. See all documented boundaries in [known limitations](docs/LIMITATIONS.md).

### License and contributions

Original project code is [MIT-licensed](LICENSE). Third-party components retain their own licenses; see [third-party notices](docs/THIRD_PARTY_NOTICES.md). Reproducible issues, tests and focused improvements are welcome; read [contributing](CONTRIBUTING.md). Never post real keys, databases, passwords or unsanitized logs in issues.
