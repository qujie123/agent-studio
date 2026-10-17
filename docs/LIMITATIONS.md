# 已知限制 / Known limitations

## 中文

这是持续开发的本地优先工作台，不是已经通过独立安全审计的企业 SaaS。测试通过不能证明没有漏洞。

- Windows 安装流程在 Windows 11 x64 实测；没有完成其他电脑、Windows 10、macOS/Linux 安装器测试。安装包未代码签名，可能显示未知发布者。界面在浏览器打开，不是原生桌面编辑器。
- 本地 H2 数据库是单机模式，不支持多实例共享。账号、对话和成果并非整库加密；Windows 私有 ACL 和 API Key 加密不等于磁盘全加密。DPAPI 密钥不能直接跨电脑/Windows 账号复制。
- 未实现 MFA、邮箱验证、密码找回邮件、登录限流、团队/组织租户隔离。个人工作台资源有所有权校验，但不是企业租户隔离系统。不要直接暴露到公网。
- 注册接口仍接受至少 6 字符的密码，管理员创建/重置密码要求至少 12 字符；密码策略尚未统一。请自行使用独立强密码，勿把本机注册接口当作公网身份服务。
- 自定义模型地址允许本机 HTTP 和 HTTPS 服务，面向可信本机使用；没有完整的出站网络隔离与 DNS 重绑定防护。公网部署前需额外访问控制、网络策略、HTTPS、限流、监控及独立安全审查。
- 14 个服务商预设只有 OpenAI Chat Completions、Anthropic Messages 两种文本协议适配；不是所有模型/地区/套餐都支持。真实付费 Key 未纳入自动验收。不支持所有私有协议、Responses-only、多模态或自动工具调用。
- 余额查询仅适配 DeepSeek 与 OpenRouter；本平台 Token 记录不是服务商账单。
- 知识库是有限文本检索，不是向量数据库或完整 RAG 管线。工作流是有限的步骤编排，不是通用可视化工作流引擎。
- Python 运行需要 Docker 和指定镜像；GUI 是录屏预览而非交互式远程桌面。不安装任意依赖，不回退为主机执行。容器不是绝对安全边界，多租户应使用独立隔离宿主。
- 成品交付限制为最多 100 个 UTF-8 文本文件、合计 1 MiB；静态网页可预览，任意 Node/React/后端工程不能免构建运行。生成代码仍需用户审查，不保证可运行或适合生产。
- 尚未建立长期负载/可用性基准、自动升级和跨平台数据迁移流程。暂无独立渗透测试或完整依赖漏洞审计结论。
- 源码与本地 JAR 可被分析；本项目不提供防破解授权或不可逆向保证。开源与“隐藏源码”不是同一个目标。

## English

This is an actively developed, local-first workspace, not an independently audited enterprise SaaS. Passing tests does not prove the absence of vulnerabilities.

- The installer was tested on Windows 11 x64 on one machine. Other machines, Windows 10 and macOS/Linux installers are unverified. The unsigned installer may show an unknown publisher warning. The UI opens in a browser, not a native desktop editor.
- File-based H2 is a single-machine mode, not a shared multi-instance database. Accounts, conversations and artifacts are not encrypted as a whole database. Private Windows ACLs and API-key encryption are not full-disk encryption. DPAPI secrets are tied to the original Windows account and computer.
- MFA, email verification, email password recovery, login rate limiting and organization-level tenant isolation are not implemented. Resource ownership checks are not enterprise tenant isolation. Do not expose the application directly to the public internet.
- Public registration still accepts passwords of at least six characters, while managed creation/reset requires twelve. Password policy is not yet unified. Use independent strong passwords and do not treat local registration as a public identity service.
- Custom endpoints allow local HTTP and HTTPS providers for trusted local use. Comprehensive egress isolation and DNS-rebinding protection are absent. Public hosting requires additional access controls, network policies, HTTPS, rate limiting, monitoring and independent security review.
- Fourteen provider presets use only two text protocols: OpenAI Chat Completions and Anthropic Messages. Model, region and plan compatibility varies. Automated tests do not use paid provider credentials. Not all proprietary protocols, Responses-only endpoints, multimodal features or automatic tool calls are supported.
- Balance queries support only DeepSeek and OpenRouter. Local token records are not provider billing statements.
- Knowledge search is bounded text retrieval, not a vector database or a complete RAG pipeline. Workflows provide bounded step orchestration, not a general visual workflow engine.
- Python requires Docker and prepared images. GUI preview is a recording, not an interactive remote desktop. Arbitrary dependencies are not installed; generated code never falls back to host execution. Containers are not an absolute security boundary; multi-tenant hosting needs separate isolation hosts.
- Deliveries support up to 100 UTF-8 text files and 1 MiB total. Static sites can be previewed, but arbitrary Node/React/backend projects still need their build/runtime setup. Generated code requires user review and is not guaranteed to run or be production-ready.
- Long-running load/availability benchmarks, automatic updates and cross-platform data migration are not established. No independent penetration-test or comprehensive dependency-vulnerability clearance is claimed.
- Source and local JARs can be analyzed. There is no anti-cracking license system or guarantee against reverse engineering. Open source is not source-code secrecy.
