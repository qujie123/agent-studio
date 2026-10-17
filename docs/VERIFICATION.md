# 发布验证记录 / Release verification

## 中文

验证日期：2026-10-17。环境：本机 Windows 11 x64、OpenJDK 26.0.2.1、独立 PostgreSQL 18 测试集群、Docker Desktop Linux 容器及 Microsoft Edge。真实业务数据库不参与测试，模型连接使用模拟服务，不消耗真实模型额度。

| 检查 | 实测结果 |
| --- | --- |
| `scripts/test-release.ps1` | 126 项 Java 测试；失败 0、错误 0、跳过 0；包括 PostgreSQL 迁移和真实 Docker/GUI |
| `scripts/test-browser.ps1` | 11 个浏览器专项全部通过；14 个页面、深浅主题、移动布局、资源归属、多轮与流式对话、交付下载和真实 Python MP4 |
| 脚本和配置 | 26 个 JS/CJS 语法、14 个 PowerShell 脚本、2 个 Python AST、POM XML 和 package JSON 检查通过 |
| 前端专项 | 账号切换/退出隔离、分段 SSE、过期流结果拒绝通过 |
| 构建脚本 | 内容变化检测、PowerShell 跨版本缓存复用、失败构建保护通过 |
| 维护工具 | `ClearLocalModels.java` 独立编译通过；未执行数据清除 |

Windows 1.7.2 已完成独立的真实安装、启动、浏览器登录和 14 页面检查，以及停止、重启、卸载验收。随机凭据、端口避让、只监听回环、私有数据权限、许可文件、重启/卸载保留数据及不继承宿主模型 Key 均通过。测试安装已卸载，未修改真实业务数据。安装包 SHA-256：`6418b8cd4c0438d40ffdaa0d22169c31ffa1ba05ecad4464a3b765c4f966dc22`。

干净导出的源码已从零生成构建产物：普通 Maven verify 成功，126 项中通过 110 项、按环境条件跳过 16 项；这是与上述零跳过全量运行分开的复现检查。`npm ci` 按锁文件安装成功。源码清单共 240 个发布文件（含 142 个 Java 文件，其中 1 个离线维护工具），相对文档链接、页面本地资源和 16 个迁移脚本均通过完整性检查。

本次修正首次克隆没有 `target` 目录时测试日志创建失败的问题，并移除浏览器测试中本机专用依赖路径。发布使用明确文件清单导出并核对源码哈希，排除个人数据库、备份、日志、密钥、IDE 配置和开发临时文件。历史内部验收记录不直接上传；本页提供脱敏摘要。

### 不能从这些结果推出什么

干净源码在 `npm ci` 后、未设置本机专用 `NODE_PATH` 的情况下，再次通过全部 11 个浏览器专项。

这不是逐行独立人工审计、全面依赖 CVE 审计或“零漏洞”保证。未验证全部付费供应商、其他用户电脑、长期压力、灾难恢复或公网多租户部署。其他环境仍需要按构建指南准备 Edge、Java 和可选 Docker/PostgreSQL。源码、依赖版本和安装器都有已知边界，详见[限制](LIMITATIONS.md)。

## English

Verification date: 2026-10-17. Environment: one Windows 11 x64 machine, OpenJDK 26.0.2.1, an isolated PostgreSQL 18 cluster, Docker Desktop Linux containers and Microsoft Edge. Business databases are excluded. Model tests use mock services, not paid credentials.

- Full Java/PostgreSQL/Docker verification: **126 tests, zero failures, errors or skips**.
- Browser verification: **all 11 suites passed**, covering 14 pages, themes, mobile layouts, ownership, multi-turn/streaming chat, artifact downloads and actual Python MP4 previews.
- Syntax/config checks: 26 JS/CJS files, 14 PowerShell scripts, two Python ASTs, POM XML and package JSON passed.
- Frontend regressions passed account/logout isolation, fragmented SSE and stale-stream rejection.
- Build regressions passed content freshness, cross-PowerShell cache reuse and failed-build protection.
- The maintenance Java utility compiled separately; no database reset was executed.

Version 1.7.2 passed independent real installation, startup, browser login and all 14 pages, shutdown, restart and uninstall checks. Random credentials, port fallback, loopback/private ACLs, required notices, restart/uninstall data preservation and rejection of inherited host model keys passed. The disposable installation was removed without modifying business data. Installer SHA-256: `6418b8cd4c0438d40ffdaa0d22169c31ffa1ba05ecad4464a3b765c4f966dc22`.

A clean source export built from scratch: ordinary Maven verify passed 110 of 126 tests, with 16 conditional skips. This reproducibility check is separate from the zero-skip full run above. Lockfile-based `npm ci` succeeded. The source export contains 240 release files, including 142 Java files (one is an offline maintenance utility). Relative documentation links, local UI resources and all 16 migration scripts passed integrity checks.

Release preparation fixed test-log creation on fresh clones without `target` and removed a machine-specific browser dependency fallback. An allowlisted source export checks copied source hashes and excludes personal databases, backups, logs, keys, IDE state and generated files. Internal historical logs are not published; this is a sanitized summary.

The clean export also passed all 11 browser suites after `npm ci`, without a machine-specific `NODE_PATH`.

These checks are not an independent line-by-line audit, comprehensive dependency CVE review or a zero-vulnerability guarantee. Paid providers, other machines, sustained load, disaster recovery and public multi-tenant hosting remain unverified. Other environments must prepare Edge, Java and optional Docker/PostgreSQL as documented. See [limitations](LIMITATIONS.md).
