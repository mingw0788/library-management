# 开发环境基线

检查日期：2026-10-09（Asia/Shanghai）。T02 已完成；T01 数据库登录认证失败，数据库连接尚未验收。

## 1. 项目与工作区

- 工作区：`E:\AI\图书管理系统`，检查时为空，没有已有源码、暂存内容或未提交改动。
- 独立项目根：`E:\AI\图书管理系统\library-management`。
- 检查工作区和上级目录，没有发现适用的 `AGENTS.md`，也没有父级 Git 仓库。
- 只在独立项目根建立本地 Git 仓库；不修改用户 Git 身份，不设置远程、不推送。
- 每个 T 编号作为一个步骤。完成后报告结果并停止，必须获得用户允许才开始下一编号。

## 2. 固定版本与实际检查

| 组件 | 本项目固定版本 / 方案 | 当前检查结果 |
|---|---|---|
| Java | Oracle JDK 21.0.1 LTS，完整版本 `21.0.1+12-LTS-29` | `java`、`javac` 可用；安装目录 `E:\jdk-21.0.1` |
| Spring Boot | 4.1.1，稳定 GA | 官方发布公告、系统要求与稳定版本列表已核对；T02 已创建最小网页工程，实际构建结果见进度记录 |
| Maven / Wrapper | Apache Maven 3.9.16；Wrapper 3.3.4，only-script | 本机 Maven 可用；T02 已通过官方插件生成 Wrapper，固定下载 3.9.16 |
| Git | 2.54.0.windows.1 | 可用；提交姓名与邮箱已配置，不在文档中公开其值 |
| MySQL | Community Server 8.0.44，Windows x86_64 | 客户端与服务程序均为 8.0.44；服务 `MySQL80` 正运行；本次 root 登录返回 1045，专用库连接未验证 |
| 容器 | 非必需，不用于当前基线 | Docker 29.8.2、Compose v5.5.1 已安装；引擎未运行 |

用户在本次对话中明确选择沿用现有 MySQL 8.0，替代原计划 MySQL 8.4 LTS 的默认假设。固定为当前安装的 8.0.44，不自动升级、不安装第二套服务。涉及 MySQL 的后续迁移、索引、事务、生成列、锁与并发检查必须在真实 8.0.44 专用测试库执行，不能把版本兼容推断当作已通过的数据库验收。

Spring Boot 4.1.1 官方声明支持 Java 17—26、Maven 3.6.3 及以上，因此当前 JDK 21.0.1 和 Maven 3.9.16 满足其声明的构建条件。实际构建及依赖兼容性在 T02/T03 验证。Maven 官网将 3.9.16 列为仍维护的稳定版本；不为追随最新版本而更换本机版本。

依赖以 Spring Boot 管理版本为准，不单独猜测或自动升级。已核对 Boot 4.1.1 官方依赖表：管理 Connector/J 9.7.0、Flyway 12.4.0（含 MySQL 模块）。Connector/J 9.7.0 版本说明声明支持 MySQL Server 8.0 及以后版本；Flyway 官方 MySQL 文档将 8.0 列为已验证版本。因此采用现有 8.0.44 未发现官方声明范围内的直接兼容阻断；这仍不代表连接、迁移或事务实测通过。T03 再按固定版本引入准确依赖入口及独立 MySQL 支持模块并验证实际运行。

## 3. 机器与端口

| 项目 | 记录 |
|---|---|
| 操作系统 | Windows 11 家庭中文版，64 位，版本 10.0.26200 |
| CPU | Intel Core i5-1155G7，4 核 8 线程 |
| 内存 | 15.79 GB；检查时可用约 5.83 GB，会随运行程序变化 |
| 应用端口 | 8080，T01 初检时无监听；T02 默认只监听本机 127.0.0.1，实际启动结果见进度记录 |
| MySQL 地址 | `127.0.0.1:3306`，已有 MySQL 服务监听 |

性能验收在 T21 重新记录当时机器状态、样例规模、预热、20 并发会话、p95 和错误率；本次工具检查不代表性能测试。

### 项目构建设置

本机全局 Maven 设置使用 HTTP Aliyun 镜像，首次生成 Wrapper 时下载停滞。没有修改该全局文件；项目以 `.mvn/maven.config` 的 `--global-settings .mvn/settings.xml` 使用项目内的全局设置基线，默认仓库为 HTTPS Maven Central，依赖缓存为 `.local/maven-repository/`。用户级 Maven 设置仍按 Maven 的合并规则生效。官方 Wrapper 使用 HTTPS 固定发行版地址，Windows 构建入口为 `mvnw.cmd verify`。

首次 Wrapper 发行包下载曾受本机代理影响耗时较长，最终官方发行包的本机缓存已就绪；执行 `mvnw.cmd -version` 确认使用用户级 Wrapper 缓存中的 Maven 3.9.16。官方 Wrapper 脚本保持原样，没有更改系统代理或全局 Maven 安装。首次依赖下载只影响准备耗时，具体构建结果以进度记录为准。

## 4. 数据库与配置隔离

| 用途 | Profile | 数据库名称 | 账号边界 | 当前状态 |
|---|---|---|---|---|
| 开发 | dev | `library_dev` | 专用开发账号，仅访问开发库 | 尚未验证存在或可连接 |
| 自动测试 | test | `library_test` | 独立测试账号，仅访问测试库 | 尚未验证存在或可连接 |
| 恢复演练 | 仅演练外部配置 | `library_restore_drill` | 独立演练账号，不覆盖开发/测试库 | T22 再创建和验证 |
| 部署 | prod | 由部署目标决定，必须与前述库区分 | 最小权限；运行与迁移权限按目标分离 | T22 决定，未部署 |

- 应用不使用数据库 root 或其他管理员账号。创建专用数据库/账号若需要管理权限，仅在环境准备时使用，不写入应用配置。
- 初次检查未发现用户常见 MySQL 登录配置或项目约定连接环境变量。随后用户明确授权代理填入其提供的管理凭据；凭据仅保存在被忽略的本机配置中，不写入公开示例、文档或 Git。
- 客户端没有加入 PATH，但可通过 `C:\Program Files\MySQL\MySQL_Server 8.0\bin\mysql.exe` 直接使用，无需重装。
- 本机连接配置保存在项目 `.local/mysql-connection.json`，该目录整体忽略，不提交。模板见 [mysql-connection.example.json](../config/mysql-connection.example.json)。密码只在本机填写，不粘贴到聊天中。
- 模板仅供环境准备读取，不表示 Spring Boot 会自动加载 JSON。T03 将应用连接明确映射到外部环境变量或受控外部配置。
- 未来开发变量约定：`LIBRARY_DB_URL`、`LIBRARY_DB_USERNAME`、`LIBRARY_DB_PASSWORD`；测试使用独立的 `LIBRARY_TEST_DB_URL`、`LIBRARY_TEST_DB_USERNAME`、`LIBRARY_TEST_DB_PASSWORD`。
- T01 先进行只读连接检查。如专用库尚不存在，环境准备范围仅限新建本项目专用开发库、测试库和对应受限账号；保留现有数据库、账号与数据，不清库、不覆盖、不向业务表写数据。若发现同名现有业务库或权限冲突，停止相关操作并报告。
- 本次通过 TCP 连接 `127.0.0.1:3306`，以用户提供的 root 凭据尝试只读实例检查，MySQL 返回 1045（Access denied）。服务可达但认证未通过；未修改任何数据库或账号，专用库存在性与权限仍未知。

## 5. 时间、事务及业务配置

- 业务时区固定 `Asia/Shanghai`；当前机器时区不作为业务规则来源。
- 事件时刻使用 Java `Instant`，数据库 `DATETIME(6)` 按 UTC 写入和读取；JDBC/数据库会话采用同一 UTC 约定。
- 应还日期使用 `LocalDate` / `DATE`：借出时刻在业务时区的日期加 14 个自然日，应还当天不逾期。
- 事务基线 `READ COMMITTED`，由应用连接设置并在 T03 实测。尚未查询数据库会话值，不修改现有 MySQL 全局配置。
- 字符集 `utf8mb4`、存储引擎 InnoDB。具体排序规则、专用库权限和会话时区在连接验证及 T03 记录实测值。
- 默认最大未还 5 册、借期 14 天；启动时校验配置为正数。修改政策需要更新需求与验收，不直接改前端限制。
- dev/test/prod 不得静默连接到另一个库。缺少必填连接信息应明确失败；不关闭 CSRF、不使用 H2 代替 MySQL 并发验证。

## 6. 初始凭据与待补齐项

- 初始管理员在 T05 通过外部受控配置创建，密码不硬编码、不保存到 Git，首次登录强制修改；本次没有创建应用账号。
- 工作人员创建/重置密码采用当面或用户认可的私密方式交付。日志、审计和普通页面不长期保存明文密码；重置和改密使旧会话失效。
- 用户要求代理直接填写后，已将其提供的管理凭据写入 `.local/mysql-connection.json` 的 `provisioning` 段。一次验证返回认证错误 1045；需要用户确认正确凭据或可用的认证方式，不猜测其他密码。
- 填写后先进行不写数据的身份/版本/权限/库名检查，确认连接的是本机目标实例；仅在专用库尚不存在且管理权限足够时准备独立库/账号，不修改已有库。
- T01 在专用开发库、测试库连接验证通过前保持“待补齐环境”。用户随后明确要求继续，允许先做无需数据库的 T02；T02 本身不引入 JDBC/JPA/Flyway/MySQL，不读取本机数据库凭据。数据库缺项保留，T03 前必须补齐；没有数据库验收通过的承诺。

## 7. 官方核验依据

核对日期：2026-10-09。以下链接用于说明已核验事实，不能代替本项目实际运行。

- [Spring Boot 4.1.1 GA 发布公告](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/)
- [Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring 官方版本支持映射](https://spring.io/projects/generations/)
- [Apache Maven 下载与维护版本](https://maven.apache.org/download.cgi)
- [Apache Maven 发布历史](https://maven.apache.org/docs/history.html)
- [MySQL 8.0 官方手册](https://dev.mysql.com/doc/refman/8.0/en/)
- [Spring Boot 管理的依赖版本表](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
- [Connector/J 9.7.0 发布与服务端兼容范围](https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-7-0.html)
- [Flyway MySQL 支持模块与版本范围](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)
