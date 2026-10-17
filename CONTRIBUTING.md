# 贡献说明 / Contributing

## 中文

欢迎提交可复现的问题、文档纠错和小范围改进。请先阅读 [README](README.md)、[构建指南](docs/BUILD.md)和[已知限制](docs/LIMITATIONS.md)。

1. Issue 写清版本、操作系统、预期行为、实际行为和最小复现步骤。只提供脱敏日志。
2. 修复尽量附带能够重现问题的测试；避免不相关重构和格式化。
3. 提交前执行 Maven verify 与相关前端/脚本测试；涉及 Docker、数据库、安装器时执行对应专项。说明未执行或跳过的项目，不要把跳过写成通过。
4. 不提交 `data/`、`backups/`、`target/`、安装器二进制、真实 API Key、密码或私人对话。二进制随 Releases 发布。
5. 保留第三方许可和来源。原创贡献使用本仓库 MIT 许可；不要上传无权再分发的代码或素材。

敏感安全问题不要贴到公开 Issue。请在 GitHub 支持且仓库启用时使用私密漏洞报告；若入口不可用，先用不含利用细节和用户资料的消息联系维护者。项目没有承诺响应时限，也没有独立安全认证。

## English

Reproducible issues, documentation corrections and focused improvements are welcome. Read the [README](README.md), [build guide](docs/BUILD.md) and [limitations](docs/LIMITATIONS.md) first.

1. Include the version, OS, expected/actual behavior and minimal reproduction. Share sanitized logs only.
2. Add a regression test where practical. Avoid unrelated refactoring and formatting.
3. Run Maven verify and relevant frontend/script tests. Changes to Docker, persistence or packaging need their dedicated checks. State skipped or unexecuted checks honestly.
4. Never commit `data/`, `backups/`, `target/`, installer binaries, real API keys, passwords or private conversations. Distribute binaries through Releases.
5. Preserve third-party licenses and provenance. Original contributions use this repository's MIT license; do not submit material you cannot redistribute.

Do not post sensitive vulnerabilities in public issues. Use GitHub private vulnerability reporting where available and enabled. Otherwise contact the maintainer without exploit details or user data first. No response-time commitment or independent security certification is claimed.
