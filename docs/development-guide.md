# 开发与运行说明

本文面向初学者，按“准备本机连接 → 构建验证 → 启动 → 初始化账号 → 观察请求与数据 → 排错”的顺序说明。当前环境基线为 Windows、Java 21、Spring Boot 4.1.1、Maven Wrapper 3.9.16 与 MySQL 8.0.44；版本细节见[环境文档](environment.md)。

## 项目目录

| 路径 | 用途 |
|---|---|
| `src/main/java/com/example/library/account` | 账号、读者档案、密码规则和首次管理员初始化 |
| `src/main/java/com/example/library/catalog` | 分类、书目、实体副本、目录搜索及库存汇总 |
| `src/main/java/com/example/library/loan` | 借还规则、借出/归还/续借事务和借阅查询 |
| `src/main/java/com/example/library/reservation` | X02 预约实体、FIFO 查询及测试 profile 内的创建/取消服务 |
| `src/main/java/com/example/library/audit` | 关键业务操作审计 |
| `src/main/java/com/example/library/security` | Spring Security、当前登录身份、角色和会话复核 |
| `src/main/java/com/example/library/controller` | 网页路由、输入接收和页面模型；不应在这里复制业务规则 |
| `src/main/java/com/example/library/dashboard`、`web` | 首页统计和表单反馈 |
| `src/main/resources/templates`、`static` | Thymeleaf 页面及 CSS |
| `src/main/resources/db/migration` | 只追加的 Flyway 版本迁移，当前为 V1—V10 |
| `src/main/resources/application*.yml` | 默认值与 dev/test/prod 环境连接配置 |
| `src/test/java` | 页面、服务、权限、真实 MySQL、并发和性能验收 |
| `scripts/set-local-db-env.ps1` | 从未提交的本机 JSON 安全载入 dev/test 连接环境变量 |
| `docs` | 需求、数据库/部署约定、用户手册、验收和进度证据 |

## 第一次准备与构建

在有项目专用 `library_dev`、`library_test` 和受限应用账号的机器上，使用未跟踪的本机文件保存连接信息。新克隆仓库时可复制公开模板，然后只填写自己的 dev/test 专用账号；不要用 root 运行应用：

```powershell
Set-Location 'E:\AI\图书管理系统\library-management'
New-Item -ItemType Directory -Force .local | Out-Null
if (-not (Test-Path .local\mysql-connection.json)) {
    Copy-Item config\mysql-connection.example.json .local\mysql-connection.json
}
notepad .local\mysql-connection.json
```

本机脚本只接受固定的 `library_dev` 和 `library_test` 数据库名，避免不小心把自动测试连到其他 schema。环境变量载入当前 PowerShell 会话，不会显示密码：

```powershell
. .\scripts\set-local-db-env.ps1 -Profile test
. .\scripts\set-local-db-env.ps1 -Profile dev
.\mvnw.cmd verify
```

`verify` 会运行完整测试并打包 `target\library-management-0.0.1-SNAPSHOT.jar`。测试不会清空数据库；测试产生的有标记数据会由测试自身清理。当前回归使用真实 MySQL，不以 H2 代替数据库锁、迁移或并发验证。

### 在专用空库首次复现迁移

自动测试配置固定使用 `library_test`，并包含该库名称的隔离断言。需要从空库复现初次迁移时，先由数据库管理员在本机 MySQL 创建一个全新的独立 schema 和只访问该 schema 的专用账号；不要删除或清空现有 `library_test`、`library_dev` 或恢复演练库。可用 prod profile 加外部配置指向这个一次性验收 schema，配置文件保存在被忽略的 `.local` 中，结构参考 `config/application-prod.example.yml`，只把 URL 的 schema 名改为验收库名。随后使用 prod 环境变量启动打包 JAR；Flyway 应从 V1 自动运行到 V10，Hibernate 再校验结构。检查后停止应用，并按本机数据库管理规则处理这个一次性 schema。T23 曾验证 V1—V8，X01 加入 V9，X02 在现有 dev/test 库验证 V10 升级。

## 本地启动和管理员初始化

开发日常启动连接 `library_dev`：

```powershell
. .\scripts\set-local-db-env.ps1 -Profile dev
.\mvnw.cmd spring-boot:run
```

浏览器访问 <http://localhost:8080/>；退出时在启动窗口按 `Ctrl+C`。也可以在构建完成后运行可执行 JAR：

```powershell
. .\scripts\set-local-db-env.ps1 -Profile dev
java -jar target\library-management-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

首次管理员初始化默认关闭。只在新建的空验收库或确定尚无管理员的目标库上临时设置 `LIBRARY_INITIAL_ADMIN_ENABLED=true`，并通过受控的进程环境提供 `LIBRARY_INITIAL_ADMIN_USERNAME`、`LIBRARY_INITIAL_ADMIN_PASSWORD`、`LIBRARY_INITIAL_ADMIN_DISPLAY_NAME`。不要把密码写入仓库文件、命令历史或共享终端记录。启动日志只说明初始化成功或因已有管理员跳过，不打印密码。第一次登录须立即改密；随后停止应用、关闭初始化开关并清除全部初始化环境变量，再按普通 dev/prod 配置启动。已存在管理员时，初始化流程不会重设账号或密码。

应用启动时 Flyway 会验证并应用尚未执行的迁移；Hibernate 使用 `validate` 核对结构，不会替代迁移或自动改表。当前结构版本为 V10。X02 的预约功能默认关闭；`application.yml` 中 `library.reservations.enabled` 为 `false`，只有 `application-test.yml` 将它打开，而且预约服务和控制器还受 `test` profile 限制。dev/prod 不会加载预约写服务、页面或路由。已执行的迁移文件不可编辑、删除或用 `repair` 掩盖差异；结构变化要新建下一版本迁移，并按本计划验收。

## 一次借出的请求和数据流

以馆员提交借出为例：浏览器读取 `/staff/loans/checkout` 表单，提交时 Spring Security 先检查登录、角色、会话状态和 CSRF 令牌；控制器只解析读者证号与副本条码，交给 `LoanCheckoutService`。服务在 `READ COMMITTED` 事务中重新解析并锁定需要的账号、读者档案、书目、副本和借阅数据，重查额度、逾期、同书目和副本状态。全部通过后，同一事务新增借阅、把副本改为 `ON_LOAN` 并写审计，再提交并返回页面。任何一步失败都会整体回滚。

普通只读页面也先经过安全过滤器和路由权限，再由控制器调用服务读取 Repository，最后由 Thymeleaf 把模型渲染成 HTML。读者借阅查询和续借的身份来自已登录账号，不接受页面传来的他人编号。续借服务按账号、读者档案、书目、副本、借阅顺序加锁，重验资格后在同一事务内延长日期、更新次数并写审计。完整加锁次序和时间约定见[数据库结构与并发说明](database.md)。

## 配置、迁移与秘密

- 默认应用绑定 `127.0.0.1:8080`，业务时区 `Asia/Shanghai`；数据库事件时刻按 UTC 保存，应还日按本地 `DATE` 保存。
- dev、test、prod 使用不同 schema 和各自受限账号。prod 凭据从 `LIBRARY_PROD_DB_HOST/PORT/USERNAME/PASSWORD` 读取；首次管理员变量见本节上文。
- `.local/`、`target/`、`logs/`、`backups/` 和 `delivery/` 已忽略。连接密码、个人数据、备份、日志和构建产物不加入提交。
- `config/application-prod.example.yml` 仅供复制到本机/部署目录的外部配置，不含真实密码。上线前的 HTTPS、反向代理、备份、恢复和应用回退要求见[部署与恢复说明](deployment-recovery.md)。

## 备份与恢复

需要保留业务数据库时，由有权限的维护人员使用专用备份账号生成该 schema 的一致性备份，并将备份、JAR 版本和配置版本存入受限且加密的备份位置。恢复演练必须导入到单独创建的空演练库；本项目的备份文件可能含有删除既有表的语句，因此导入前要逐字核对目标 schema，绝不能把演练导入到生产库。

恢复后先核对 Flyway 版本、Hibernate 结构检查和关键表行数，再使用恢复库的专用账号启动同一打包 JAR，检查登录、检索及借出/归还数据闭环。保留旧 JAR 不代表可以回滚数据库迁移；出现问题时应暂停流量、保留现场并评估向前修复或经过单独批准的恢复点。完整备份命令、权限范围、恢复操作和回退边界见[部署与恢复说明](deployment-recovery.md)。
## 常见开发问题

| 现象 | 检查与处理 |
|---|---|
| 启动提示数据库用户名/密码缺失 | 在当前 PowerShell 会话先 dot-source `set-local-db-env.ps1`；确认 `.local/mysql-connection.json` 存在且字段已填。不要把密码贴入聊天或提交。 |
| MySQL 1045 | 账号或密码认证失败；用数据库管理员工具核对受限应用账号。不要尝试覆盖其他账号密码或改用 root 启动应用。 |
| Unknown database / access denied | 检查当前 profile 与对应 schema 权限；dev/test 脚本会校验目标库名。不要在别的业务库上运行迁移。 |
| Flyway checksum mismatch | 已执行迁移文件与历史不一致；停止启动，检查 Git 差异和实际数据库。不要运行自动修复或手改历史记录。 |
| Hibernate schema validation failed | 核对 Flyway 是否到 V10、当前 profile/schema 是否正确，以及迁移是否完整；不要关闭 `validate` 来掩盖结构不匹配。 |
| 8080 被占用 | 检查占用程序；若需要，可仅本次运行传入 `--server.port=8081`。不要结束不相关进程。 |
| Maven 下载失败 | Wrapper 和依赖首次需联网；确认网络可访问项目配置的 HTTPS 仓库镜像，再重试。不要为本项目修改全局 Maven、代理或固定依赖版本。 |
