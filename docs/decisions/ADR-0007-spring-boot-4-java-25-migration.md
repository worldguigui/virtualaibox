# ADR-0007: Spring Boot 4.1.1 与 Java 25 升级迁移

- 状态：Accepted
- 日期：2026-11-13

## 决策背景

- 现状：SpringBoot 3.x 版本官方已于 26年6月停止开源维护，Java 也有较新的版本。
- 良好的虚拟线程特性，似乎是vab之后并发的基础？
- 当前处于项目启动的初期，各种依赖的引入较少，迁移起来很方便。

## 决策选择

Q. 为什么选择现在升级，而不是继续停留在 3.5.x？<br/>
A. 一是迁移成本并不高，二是迁移后版本更新，更有可能吃到后续红利。vab项目本身并不是传统的Spring项目，Boot在这里只是一个快速搭建网页的框架而已，不如用新版本SpringBoot，搭配上Java更成熟的虚拟线程等技术。

Q. 迁移带来的核心好处是什么？<br/>
A. Boot 侧。1.回归受支持主线（可持续的安全/缺陷补丁）；
2.自动配置模块化让依赖面按需裁剪（`spring-boot-autoconfigure` 拆分为`spring-boot-webmvc` / `spring-boot-restclient` 等独立模块）；
3.Jackson 3 成为默认 JSON 库，与生态未来对齐；
4.JSpecify 空安全约束进入框架 API，IDE/静态工具可读，降低 NPE 面。
Java 侧。5. 25 是最新 LTS，补丁接收窗口最长；虚拟线程在此基线成熟稳定，与本项目天然契合。

Q. 为什么一步跨到 4.1.1，而不是先 4.0 再 4.1？<br/>
A. 4.1 是 4.0 的增量演进，破坏面小于 Boot3 到 Boot4 的跨度，再加上 4.1.1 是最新稳定补丁版。

## 最终决策

| 项 | 3.5.14 基线 | 升级后 |
|---|---|---|
| Spring Boot | 3.5.14 | 4.1.1（parent） |
| Java | 21 | 25（`java.version=25`） |
| Web starter | spring-boot-starter-web | spring-boot-starter-webmvc |
| JSON | Jackson 2（com.fasterxml） | Jackson 3（tools.jackson），仅 LLMBrain 一处 |
| langchain4j HTTP 通道 | spring-restclient 集成 | langchain4j-http-client-jdk + 新建的同名 Bean|
| 死依赖清理 | — | 移除 spring-boot-starter-webflux、langchain4j-reactor（零引用） |

- 保持不变：langchain4j BOM 1.19.5、ANTLR 4.13.2、frontend-maven-plugin 前端构建链、 application.yml 全部配置（启动日志零属性弃用告警）。
- [spring.threads.virtual.enabled](../../src/main/resources/application.yml) 不进行设置，虚拟线程本次不开启

## 移植边界（或改造边界）

- 只改动依赖配置，不改动业务逻辑
- 三方依赖 langchain4j 没有适配的 Boot4 starter，手动声明与 starter 工厂方法同名的 Bean

## 最终实现

### langchain4j 过渡方案（该 starter 无 Boot 4 适配版，其装配路径引用了 Boot 4 已删除的类）
1. pom 排除 `langchain4j-http-client-spring-restclient`
2. 新增 `config/LlmHttpClientConfig`，声明与 starter 工厂方法**同名**的 Bean `openAiChatModelHttpClientBuilder`（JDK HttpClient 实现）
3. 显式引入 `langchain4j-http-client-jdk` 承接 HTTP 职责。

### Jackson 3 迁移
1. 全项目仅 `LLMBrain` 一处直接使用，改 imports 即完成；

## 决策后果

当前对 langchain4j 的兼容更多是一种补丁，如果后续官方发布 Boot 4 适配版后，删除类 `config/LlmHttpClientConfig` + pom 排除项 + 显式依赖（pom 与类注释中均已标注）。