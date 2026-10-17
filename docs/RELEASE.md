# Agent Studio 1.7.2 · 本地智能体工作台 / Local Agent Workspace

## 中文

将智能体、Skills、多轮对话和可下载项目交付整理到一个本地工作台。这个版本以 MIT 公开源码，并整理 Windows 安装包、构建步骤、验收摘要与真实限制。

### 本次发布

- Windows x64 安装包内置 Java 和 H2，无需 IDEA；首次启动生成独立管理员密码，没有真实模型 Key。
- 保留已有的模型接入、流式对话、静态网页预览、代码/完整 ZIP 下载，以及可选 Docker Python 录屏预览。
- 补齐中文在前、英文在后的说明、MIT 和第三方许可；修正首次构建测试脚本的目录/依赖路径问题。
- 源码与二进制分开发布，不含个人数据库、备份、密钥或聊天记录。

下载 `AgentStudio-1.7.2-windows-x64-setup.exe`，对照 `SHA256SUMS.txt` 校验。`INSTALL_GUIDE.txt` 包含中英双语的安装、启动、停止与备份步骤。OpenJDK 对应源码单独提供；它不是运行程序所需的第二个安装包。

已通过 126 项 Java 全量测试和 11 个浏览器专项；详细安装器与导出验证见仓库 [验证记录](https://github.com/qujie123/agent-studio/blob/main/docs/VERIFICATION.md)。

**请先了解不足**：安装包未签名，只在当前 Windows 11 x64 环境验收；GUI 预览是录屏，余额仅适配部分供应商；没有 MFA、登录限流或独立安全审计，不建议直接暴露到公网。详见 [已知限制](https://github.com/qujie123/agent-studio/blob/main/docs/LIMITATIONS.md)。

## English

Bring agents, Skills, multi-turn conversations and downloadable projects into one local workspace. This MIT source release includes a Windows installer, build instructions, a verification summary and explicit limitations.

### In this release

- The Windows x64 installer bundles Java and H2; IDEA is not required. First launch creates an independent administrator password, with no real model key bundled.
- Existing model connections, streaming chat, static-site previews, code/complete ZIP downloads and optional Docker-based Python recordings are included.
- Chinese-first/English-following documentation, MIT and third-party notices are supplied. Fresh-clone test-directory and machine-specific dependency-path issues are corrected.
- Source and binaries are distributed separately, without personal databases, backups, secrets or conversation records.

Download `AgentStudio-1.7.2-windows-x64-setup.exe` and verify it using `SHA256SUMS.txt`. The bilingual `INSTALL_GUIDE.txt` covers installation, startup, shutdown and backup. Matching OpenJDK source is supplied separately; it is not another installer required to run the application.

All 126 Java tests and 11 browser suites passed. See the repository's [verification record](https://github.com/qujie123/agent-studio/blob/main/docs/VERIFICATION.md) for installer/export checks.

**Know the limits**: the unsigned installer was tested on the current Windows 11 x64 machine only. GUI previews are recordings; balance support is provider-specific. MFA, login rate limiting and independent security-audit clearance are absent. Direct public internet exposure is not recommended. Read [known limitations](https://github.com/qujie123/agent-studio/blob/main/docs/LIMITATIONS.md).
