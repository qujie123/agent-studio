# 第三方组件与来源 / Third-party notices and provenance

## 中文

本仓库的 MIT 许可只适用于项目原创代码与文档，不替代第三方组件许可，也不限制第三方依法享有的权利。分发安装包时应保留本文件、项目 LICENSE、Java 运行时的 `runtime/legal/` 以及依赖 JAR 中的原始许可/NOTICE。

### Java 运行时

Windows 发布包使用 Oracle 构建的 **OpenJDK 26.0.2.1+1-7**，不是把 Oracle 的其他商业发行版作为 MIT 重新许可。jlink 仅裁剪模块，未修改 OpenJDK 源码；模块和构建选项见 `scripts/package-windows.ps1`。

- 上游源码：[openjdk/jdk26u，提交 d55edf1cba61219d17565da51cd13a4d425f7c59](https://github.com/openjdk/jdk26u/tree/d55edf1cba61219d17565da51cd13a4d425f7c59)。
- 许可：GPLv2 with Classpath Exception，以及运行时 `legal` 目录列出的组件条款；以随组件提供的原文为准。
- Releases 同时提供 `openjdk-26.0.2.1-d55edf1cba61-source.tar.gz` 对应源码，SHA-256：`43dfedee474742a82ec393f052709b8ced7e6f76da7d3ec9f7de0461241f70d2`；也可对照发布页的 `SHA256SUMS.txt`。

### 应用依赖与工具

应用 JAR 使用 Maven 解析的原始依赖，第三方许可未被改写。精确版本以 `pom.xml`、解析后的 Maven 依赖和应用 JAR 的 `BOOT-INF/lib/` 为准；构建可执行 `mvnw.cmd dependency:tree` 获取依赖树。

| 组件 | 主要许可 / 来源 |
| --- | --- |
| Spring Boot / Spring Framework / Spring Security | Apache-2.0；[Spring](https://spring.io/projects) |
| Hibernate ORM 7.4.5.Final | Apache-2.0（依据该版本 Maven POM）；[Hibernate](https://hibernate.org/orm/)；以独立原始 JAR 随包提供 |
| H2 | MPL-2.0 或 EPL-1.0；[H2](https://www.h2database.com/html/license.html) |
| PostgreSQL JDBC | BSD-2-Clause；[pgJDBC](https://github.com/pgjdbc/pgjdbc) |
| Flyway Community | Apache-2.0；[Flyway](https://github.com/flyway/flyway) |
| jsoup / JJWT | MIT / Apache-2.0；[jsoup](https://github.com/jhy/jsoup)、[JJWT](https://github.com/jwtk/jjwt) |
| Maven Wrapper / Playwright（开发测试） | Apache-2.0；[Maven Wrapper](https://github.com/apache/maven-wrapper)、[Playwright](https://github.com/microsoft/playwright) |

这张表是导航，不是完整传递依赖清单或法律审计。修改和再分发第三方库时，应检查对应版本完整条款；第三方组件不因随 MIT 应用分发而变为 MIT。

安装器使用 [Inno Setup 6.7.3](https://jrsoftware.org/)，Copyright (C) 1997–2026 Jordan Russell；Portions Copyright (C) 2000–2026 Martijn Laan。保留原始署名与网址，未把 Inno Setup 声称为本项目原创。其[许可原文](https://jrsoftware.org/files/is/license.txt)独立适用。编译器不作为本仓库源码的一部分发布。

可选 Docker/Python 环境由用户通过 Dockerfile 另行构建，不打包进 Windows 安装器。Docker、Python、Debian、FFmpeg 及 Python 图形库保留各自条款；Microsoft Edge 是测试环境，不随本项目分发。

### 设计参考

历史研发记录把 [Open WebUI](https://github.com/open-webui/open-webui)、[Dify](https://github.com/langgenius/dify)、[Flowise](https://github.com/FlowiseAI/Flowise)、[New API](https://github.com/QuantumNous/new-api)、[LobeHub](https://github.com/lobehub/lobehub)、[bolt.diy](https://github.com/stackblitz-labs/bolt.diy) 作为工作区、导航、模型连接和项目交付设计参考，并记录为独立实现，而非移植这些平台代码。本项目与这些项目无隶属或背书关系，也不声称实现其全部能力。公开可阅读不等于可任意复制，请另行遵守各自许可。

## English

The repository's MIT license covers original project code and documentation only. It does not replace third-party licenses or limit rights granted by them. Preserve this notice, the project LICENSE, `runtime/legal/`, and original licenses/NOTICE files inside dependency JARs when distributing the installer.

### Java runtime

The Windows release uses Oracle-built **OpenJDK 26.0.2.1+1-7**, not an MIT relicensing of other Oracle commercial distributions. jlink selects modules without modifying OpenJDK source; see `scripts/package-windows.ps1` for the recipe. The upstream source is [openjdk/jdk26u at d55edf1cba61219d17565da51cd13a4d425f7c59](https://github.com/openjdk/jdk26u/tree/d55edf1cba61219d17565da51cd13a4d425f7c59), under GPLv2 with the Classpath Exception and the component terms supplied in `runtime/legal/`.

Releases include the matching `openjdk-26.0.2.1-d55edf1cba61-source.tar.gz`; verify its full checksum in `SHA256SUMS.txt`. Retain the bundled legal texts when redistributing the runtime.

### Libraries and tools

Maven dependencies are redistributed as original JARs with their notices intact. Exact versions are determined by `pom.xml`, Maven resolution and `BOOT-INF/lib/`; run `mvnw.cmd dependency:tree` to inspect the tree. The table above identifies principal components and their upstream projects. It is a guide, not a complete transitive license inventory or legal audit. Hibernate ORM 7.4.5.Final declares Apache-2.0 in its POM and is supplied as a separate original JAR; distribution alongside an MIT application does not relicense third-party libraries.

The installer is built with [Inno Setup 6.7.3](https://jrsoftware.org/), Copyright (C) 1997–2026 Jordan Russell; Portions Copyright (C) 2000–2026 Martijn Laan. Original attribution and website addresses are retained. Inno Setup is not original Agent Studio code, and its [license](https://jrsoftware.org/files/is/license.txt) applies separately. The compiler is not published as project source.

Optional Docker/Python environments are built separately by users from the Dockerfile, not bundled in the Windows installer. Docker, Python, Debian, FFmpeg and graphics libraries retain their own terms. Microsoft Edge is used for testing and is not distributed here.

### Design references

Development records cite Open WebUI, Dify, Flowise, New API, LobeHub and bolt.diy (linked above) as references for workspace organization, navigation, model connections and deliverables, with independent implementation rather than platform code transplantation. This project is not affiliated with or endorsed by them, nor does it reproduce their full capabilities. Publicly readable code is not automatically unrestricted; respect each project's license.
