# 开发进度与验收记录

记录日期：2026-10-09，业务时区 Asia/Shanghai。

## 执行约定

用户要求：根据提供的计划开发，每完成一步都说明已完成内容和下一步安排，得到用户允许才进入下一步。步骤粒度为 T01—T24；X01—X04 需要另外选择。用户已逐步允许至 T04；本步账号密码模型现已完成，等待用户允许 T05。

文档中的可复制提示词作为任务参考；原文“本次仅规划”描述原计划制作时的交付，不覆盖本次用户明确要求开展开发的指令。各步按用户要求和本项目更新后的需求基线实施。

## 任务状态

| 任务 | 状态 | 说明 |
|---|---|---|
| T01 环境与需求基线 | 环境检查通过 | 更正管理连接后验证 MySQL 8.0.44、专用开发/测试库和受限账号；文档记录在 environment.md |
| T02 最小可运行网页 | 已完成，已提交 | 首页工程与验收通过；实现提交 `5fdd27d`，进度文档更新另有提交 |
| T03 数据库接入 | 已完成，已提交 | 专用库、应用账号、profile、迁移与真实 MySQL 验收通过；实现提交 `65bf715`，最终验证 5 项全过 |
| T04 用户与密码模型 | 已完成，已提交 | `app_user` 迁移、账号实体/Repository/服务、登录名归一化、BCrypt 与密码策略通过真实 MySQL 验收；提交 `1f8668d` |
| T05—T24 | 未开始 | 按编号逐步推进，不并行实现多个步骤 |
| X01—X04 | 未选择、未开始 | 不属于当前 MVP 开发授权 |

## T01：环境与需求基线

### 已完成的工作

1. 阅读用户提供的计划，确认独立项目根 `E:\AI\图书管理系统\library-management`；初始工作区为空，不存在需要保留的用户改动或暂存内容。
2. 建立 `docs/plan.md`（保留原计划并注明当前基线调整）、`docs/requirements.md`、`docs/environment.md` 和本记录。
3. 明确三角色、一个账号一个角色、5 册/14 天、实体副本借还、Asia/Shanghai、MVP/可选扩展边界，以及每步必须等待用户允许的流程。
4. 用户已选择沿用现有 MySQL 8.0，将原计划 8.4 默认值调整为本机 8.0.44；没有安装新数据库或修改现有服务。
5. 核验现有 JDK、Maven、Git、MySQL 程序版本和端口；固定后续 Boot 4.1.1、Maven Wrapper 目标 3.9.16；未生成 Java、HTML、SQL、构建工程或功能页面。
6. 提供不含凭据的本机连接模板，实际本机配置使用被忽略的 `.local/` 目录，开发/测试连接分开；本地 Git 仓库已在独立项目根初始化，分支为 `main`。
7. 用户随后授权代理填写其提供的管理凭据；首次只读验证返回 1045。用户更正密码后，T03 预检连接通过；随后只在确认名称未占用后创建本项目专用数据库和受限账号，未改其他库。

### 实际检查与结果

| 检查 | 实际方法 / 命令 | 结果 |
|---|---|---|
| 项目初始文件 | `Get-ChildItem -Force`；读取工作区及父目录的 AGENTS.md（存在时） | 工作区为空；无适用 AGENTS.md |
| Git 边界 | `git -C 'E:\AI\图书管理系统' rev-parse --show-toplevel` | 不是 Git 仓库；无父级仓库 |
| Git 工具与身份 | `git --version`；仅核对 `user.name` / `user.email` 是否配置 | Git 2.54.0.windows.1 可用，姓名和邮箱已配置 |
| Java | `java -version`、`javac -version` | Oracle JDK 21.0.1，编译器 21.0.1，可用 |
| Maven | `mvn -version` | 3.9.16，使用 JDK 21.0.1，可用 |
| MySQL 版本与服务 | 客户端/服务程序 `--version`；读取服务与进程信息 | Community 8.0.44；MySQL80 正运行 |
| 端口 | `Get-NetTCPConnection -State Listen` 筛选 8080、3306 | 8080 空闲；MySQL 3306 已监听 |
| 可选容器 | `docker --version`、`docker compose version` 与引擎状态检查 | 客户端已安装；引擎未运行，不作为当前前置条件 |
| 连接配置 | 初次检查存在性；随后按用户要求填写被忽略的本机配置，不输出秘密 | 初始凭据曾失败；用户更正后认证通过，T03 已建立独立应用账号 |
| 官方版本兼容 | 查阅 Spring / Apache 官方发布、系统要求与维护版本页面，以及 Connector/J 和 Flyway 的 MySQL 支持说明 | JDK/Maven 与 MySQL 8.0 在声明范围内；T03 已完成真实连接、迁移与构建验收 |
| 管理连接认证 | mysql.exe，经子进程环境变量传递密码，TCP 连接 127.0.0.1:3306，连接超时 5 秒只读检查 | 初次 1045；用户更正凭据后通过，确认实例为 MySQL 8.0.44 |
| 专用开发库连接 | T03 创建专用库/账号后连接并查询当前库、账号及可见库 | 通过；只能访问 `library_dev`，Flyway V1 迁移成功 |
| 专用测试库连接 | T03 创建专用库/账号后连接并查询当前库、账号及可见库 | 通过；只能访问 `library_test`，Flyway V1 迁移和真实 MySQL 回归通过 |

### 缺项与处理

- 初始提供的管理连接密码曾被拒绝；用户后来更正本机配置后只读登录成功。T03 以此创建仅用于环境准备的项目库和 schema 权限各自受限的应用账号；管理秘密和随机应用密码均未进入公开文件或 Git。
- T01 的数据库验证缺项现已补齐：开发和测试实例、版本、schema 权限均通过实测，详情及未修改其他库的边界见 [environment.md](environment.md)。

### Git 与交付状态

- 本步拟定提交主题：`docs(T01): establish environment and requirements baseline`。
- T01 初次准备时没有单独完成提交，因为当时管理认证失败；后续已在 T03 前完成缺项验证，环境结果随本次 T03 文档更新一并记录。
- T02 实现提交：`5fdd27d`（`feat(T02): add runnable web skeleton`）；T02 验收后再以文档提交记录最终状态。
- T01 当时未运行系统测试或构建：该步骤是环境/文档任务，当时尚无系统工程；不虚构自动测试数量或启动结果。后续获准 T02 的测试单独记录在下文。

### 文档与仓库核验

2026-10-09 实际执行的文档检查：

- 读取四份 Markdown，核对一级标题、代码围栏成对闭合和全部本地相对链接；通过。
- 用字符串完整后缀比较，确认 `docs/plan.md` 的后附原文与用户提供文件逐字一致；实施调整只写在原文之前；通过。
- 核对需求基线覆盖 F01—F16、N01—N08、B01—B17；通过。可选需求仍明确标记为未选择。
- 解析公开 JSON 模板，确认结构合法、三组连接的用户名和密码都是空值；通过。实际凭据只用于本机连接，不输出、不加入文档或提交。
- T01 初次核验时枚举项目文件，确认当时不存在 Java、HTML、SQL、JAR、pom.xml 或 Wrapper；通过。后续获准 T02 后创建的网页工程不属于这项历史检查。
- 执行 `git init --initial-branch=main`，仅在项目根创建本地仓库；通过，未设置远程或修改身份。
- 执行 `git check-ignore`，确认 `.local/mysql-connection.json`、`.env`、target/、logs/、backups/、delivery/ 均被忽略；通过。
- T01 初次执行 `git status --short` 时，只有当时的 `.gitignore`、config/ 和 docs/ 为未跟踪内容；当时未暂存、未提交，实际本机配置不出现在待提交列表。

以上检查只证明文档和仓库准备情况，不代表数据库认证、数据库权限或系统功能通过。T01 总体验收仍未通过。

## T02：最小可运行网页

### 本步范围与前置说明

用户在已知数据库认证未通过的情况下要求“继续下一步”。已复查本机配置，仍返回 MySQL 1045；未修改数据库或尝试其他密码。鉴于 T02 的明确范围是无数据库首页，本次先执行该独立步骤，保留 T01 未验收状态；这不是跳过 T03 的数据库门禁。

已创建固定 Spring Boot 4.1.1 / Java 21 的最小网页工程、启动入口、首页控制器、Thymeleaf 模板、本地 CSS 和首页自动测试；只引入 Web MVC、Thymeleaf 与测试依赖。Wrapper 完整构建、真实 HTTP 和桌面/窄屏浏览器检查均已通过；实现已提交 `5fdd27d`，完成状态及文档提交记录见后文。

### 实际验收记录

| 检查 | 实际方法 / 命令 | 结果 |
|---|---|---|
| 固定 Wrapper | Maven 官方 wrapper 插件 3.3.4，参数 maven=3.9.16 / type=only-script；`mvnw.cmd -version` | 通过；实际使用 Maven 3.9.16 / JDK 21.0.1 |
| 首页自动测试 | `mvnw.cmd --batch-mode --no-transfer-progress verify` 中的 HomePageTest | 通过：1 项测试，0 失败、0 错误、0 跳过；应用上下文、首页 200、index 模板、HTML 类型和中文内容验证成功 |
| 可执行 JAR 构建 | 同一次 Wrapper verify 的打包阶段 | 通过：BUILD SUCCESS，生成包含依赖的可执行 JAR；首次准备与构建用时 8 分 24 秒 |
| 真实启动与 HTTP | 运行生成 JAR，访问 `/` 和 `/css/library.css` | 通过：首页 HTML 200、中文内容正确；CSS 200 |
| 真实浏览器与窄屏 | Chrome DevTools Protocol 模拟设备 1440px×1000 和 390px×844，检查实际视口、页面滚动宽度、中文内容、CSS 栅格及三张信息卡，并截取整页 | 通过：视口与页面宽度分别为 1440/1440、390/390，均无横向溢出；标题、状态信息、CSS 和 3 张卡片正确 |
| 停止与清理 | 核对验证应用停止及 8080 释放 | 通过；验证脚本只停止了本步启动的应用，结束后端口已释放 |

首次准备曾因本机既有 HTTP 镜像及系统代理造成下载耗时；采用项目内设置并完成官方 Wrapper 缓存准备后继续构建，没有修改全局 Maven、系统代理、数据库或固定版本。公开文档、POM 范围、README 链接、原计划保留和公开文件无本机密码已检查通过。T02 各项验收均通过，源码实现已提交 `5fdd27d`；本次文档提交记录最终验收和状态。

## T03：数据库与迁移基础

用户更正 provisioning 密码后，MySQL 只读登录验证成功。确认目标库名与应用账号均不存在后，创建了隔离的 `library_dev` / `library_test`，以及 schema 权限各自受限、随机密码各不相同的 `library_dev_app` / `library_test_app`；秘密只保存在被忽略的 `.local/mysql-connection.json`。没有修改其他 schema 或使用管理账号运行应用。

已加入 Spring Data JPA、Boot 管理的 Flyway starter 与 MySQL 支持模块；Flyway 执行 `V1__create_system_guard.sql`，只建立 `system_guard` 并插入唯一 `ACTIVE_ADMIN_SET` 行。dev/test 固定连接到不同 schema，prod 使用独立必填环境变量；缺少或错误凭据快速失败。连接配置把 Hibernate DDL 设为 `validate`，事务隔离 `READ_COMMITTED`，JDBC/MySQL 会话时区 `UTC`。本机脚本从忽略配置载入当前 PowerShell 会话变量，避免输出密码。

首次实际回归发现 Boot 4 的 Flyway 自动配置需要 `spring-boot-starter-flyway`；依赖入口修正后，MySQL 测试和首页测试全部通过。项目 Maven Central 镜像因当前网络返回 403/握手失败，因此项目级 `.mvn/settings.xml` 设定可访问的 HTTPS Central 镜像；未改全局 Maven 配置。

### 本步实际验收

| 检查 | 结果 |
|---|---|
| 管理连接、服务器版本与库账号边界 | 通过；root 只用于准备；dev/test 账号只看见各自 schema |
| 测试库 Flyway 初次迁移 | 通过；MySQL 8.0.44 的 `library_test` 中迁移版本 1 成功，锁行仅一条 |
| 重复启动与开发库隔离 | 通过；第二次测试 profile 启动后迁移历史仍一条、锁行仍一条；dev profile 运行在 `library_dev` 并独立写入版本 1 |
| 事务与时区 | 通过；Spring/Hibernate 确认为 `READ_COMMITTED`；应用 JDBC 检查 test 会话是 `+00:00`，dev 配置相同；MySQL 全局值保持未修改 |
| 缺失/错误凭据 | 通过；dev 缺少账号变量和 test 错误密码均以非零状态启动失败；没有泄露密码，也没有继续写库 |
| 最终 Wrapper verify | 通过；数据库迁移/隔离测试 4 项，首页回归 1 项，合计 5 项，0 失败/错误/跳过；可执行 JAR 打包成功 |

所有实现与验收已通过，并提交为 `65bf715`（`feat(T03): configure mysql and versioned migrations`）。

## T04：用户与密码模型

本步仅增加账号基础模型与服务，不创建登录页面、账号管理页面或管理员初始化流程。新增 V2 迁移创建 `app_user`，包含唯一登录名、密码哈希、姓名、角色、状态、强制改密标志、凭据版本及 UTC 时刻；MySQL CHECK 约束限制登录名格式、三种角色、两种状态和非负凭据版本。`AppUser` 不提供角色修改方法，状态和密码变化会递增 `credential_version`。登录名统一 trim 后用 `Locale.ROOT` 转小写，并限制 ASCII 字母、数字、下划线共 3—50 位。

采用 Spring Security `PasswordEncoder` API 与 BCrypt 强度 10。密码策略为 12—64 个 Unicode 字符、UTF-8 不超过 72 字节；不修剪密码，因此空格仍按密码原样编码。姓名 trim 后为 1—100 个 Unicode 字符。账号服务只保存单向编码值，支持按归一化登录名查找。计划与需求基线已同步此密码政策；Spring Security 采用单向密码编码的设计参见 [密码存储文档](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)。

### 本步实际验收

| 检查 | 结果 |
|---|---|
| 测试库迁移 V2 与 Hibernate 结构校验 | 通过；MySQL 8.0.44 的 `library_test` 从 V1 升至 V2；dev profile 也成功迁移并通过结构校验 |
| 登录名归一化及唯一约束 | 通过；带空格/大写输入归一化后可按统一值查找，第二个等价名称由 MySQL 唯一约束拒绝 |
| 角色、状态及账号约束 | 通过；直接向 MySQL 写入未定义角色或状态被 CHECK 拒绝；角色无实体修改器 |
| 密码编码/匹配与限制 | 通过；正确密码匹配、错误密码不匹配，数据库哈希不等于明文；验证空值、11/65字符、25个汉字超72字节被拒，12字符、24个汉字（72字节）、64字符通过 |
| Wrapper 全量验证 | 通过；10 项测试，0 失败、错误、跳过；可执行 JAR 打包成功 |
| 差异和秘密边界 | 通过；只暂存本步代码、迁移、测试与需求/进度文档；本机凭据、构建产物未纳入提交 |

首次构建因镜像握手中断未能下载新依赖，随后依赖成功取得；首次测试中的异常类型断言过窄，已调整为验证 MySQL 数据访问异常并完成完整回归。最终全部检查通过。本步实现提交为 `1f8668d`（`feat(T04): add account and password model`）。

## 下一步安排

T04 已完成并提交；报告结果后停止，等待用户明确允许进入 T05（登录退出与管理员初始化）。
