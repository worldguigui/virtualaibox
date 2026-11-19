# ADR-0007: Spring Boot 4.1.1 与 Java 25 升级迁移

- 状态：Current
- 创建日期：2026-11-13
- 更新日期：2026-11-19

## 决策背景

- Spring Boot 3.x 官方已于 26 年 6 月停止开源维护，Java 也有较新的 LTS 版本。
- 良好的虚拟线程特性，似乎是 vab 之后并发的基础。
- 项目处于启动初期，引入的依赖较少，迁移成本低。

## 决策方案

### 方案一：停留在 3.5.x
1. **结论**：否决。脱离受支持主线，吃不到后续安全/缺陷补丁，也与虚拟线程基线脱节。

### 方案二：先升 4.0，再升 4.1
1. **结论**：否决。4.1 是 4.0 的增量演进，分两步走没有额外收益，反而多一次迁移窗口。

### 方案三：直接升到 4.1.1 + Java 25
1. **结论**：采纳。4.1.1 是当前最新稳定补丁版，破坏面小于 Boot 3 → Boot 4 的跨度。

## 最终决策

| 项 | 3.5.14 基线 | 升级后 |
|---|---|---|
| Spring Boot | 3.5.14 | 4.1.1（parent） |
| Java | 21 | 25（`java.version=25`） |
| Web starter | spring-boot-starter-web | spring-boot-starter-webmvc |
| JSON | Jackson 2（com.fasterxml） | Jackson 3（tools.jackson），仅 LLMBrain 一处 |
| langchain4j HTTP 通道 | spring-restclient 集成 | langchain4j-http-client-jdk + 新建的同名 Bean |
| 死依赖清理 | — | 移除 spring-boot-starter-webflux、langchain4j-reactor（零引用） |

Q. 迁移带来的核心好处是什么？<br/>
A. Boot 侧：① 回归受支持主线，可持续获得安全与缺陷补丁；② 自动配置模块化（`spring-boot-autoconfigure` 拆分为 `spring-boot-webmvc`/`spring-boot-restclient` 等），依赖面可按需裁剪；③ Jackson 3 成为默认 JSON 库，与生态对齐；④ JSpecify 空安全约束进入框架 API，IDE 与静态工具可读，降低 NPE 面。Java 侧：⑤ 25 是最新 LTS，补丁接收窗口最长，虚拟线程在此基线成熟稳定。

- **边界**：只改动依赖配置，不改动业务逻辑。
- 保持不变：langchain4j BOM 1.19.5、ANTLR 4.13.2、frontend-maven-plugin 前端构建链、`application.yml` 全部配置（启动日志零属性弃用告警）。
- 虚拟线程本次不开启：不设置 `spring.threads.virtual.enabled`。
- 三方依赖 langchain4j 没有适配 Boot 4 的 starter，采用过渡方案：① pom 排除 `langchain4j-http-client-spring-restclient`；② 新增 `config/LlmHttpClientConfig`，声明与 starter 工厂方法**同名**的 Bean `openAiChatModelHttpClientBuilder`（JDK HttpClient 实现）；③ 显式引入 `langchain4j-http-client-jdk` 承接 HTTP 职责。

## 决策后果

- 当前对 langchain4j 的兼容是一种补丁：官方发布 Boot 4 适配版后，应删除 `config/LlmHttpClientConfig` + pom 排除项 + 显式依赖（pom 与类注释中均已标注）。
- 升级窗口成本已经付清；后续新增依赖需自行确认 Boot 4 兼容性，框架侧不再提供 3.x 时代的隐式回退。
- Jackson 3 的包名迁移（`com.fasterxml` → `tools.jackson`）在项目内只影响 `LLMBrain` 一处，但任何新增的 JSON 处理都必须使用新包名。

## 相关文档

- 相关 ADR：无
- 相关 Spec：无
- 相关 Architecture：[architecture.md](../architecture.md)
