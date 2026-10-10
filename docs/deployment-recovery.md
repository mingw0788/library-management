# 部署与恢复说明

本说明对应当前可本地运行的版本。T22 的 V8 历史演练保留在文末；D03 已完成当前 V12 开发库快照到新独立库的恢复、全表比对及新版 JAR 登录/借还验证，详见 [V12 恢复验收报告](recovery-v12-report.md)。没有部署到公网或上传数据。

## 目标环境与边界

已实际使用的运行基线是 Windows 11、Java 21.0.1、Spring Boot 4.1.1 和 MySQL Community Server 8.0.44。应用以可执行 JAR 运行，部署机器只需安装兼容的 Java 21 运行环境；构建机另外需要联网下载固定 Maven Wrapper 和项目依赖。

生产 profile 固定连接名为 `library_prod` 的应用数据库。不要把生产库配置成 `library_dev`、`library_test` 或 `library_restore_drill`。当前机器没有公网域名、HTTPS 证书、反向代理或生产实例，因此此文档给出部署边界和操作步骤，不表示系统已经上线。

应用默认只绑定 `127.0.0.1:8080`，适合由同一台机器上的反向代理访问。对外提供服务前，必须由代理提供 HTTPS、把明文 HTTP 跳转到 HTTPS，并限制后端端口只能由本机代理访问。防火墙和数据库网络规则也应只允许部署主机连接数据库。

## 配置与秘密

`config/application-prod.example.yml` 是不含秘密的生产配置示例。将它复制到部署目录的 `config/application-prod.yml`，或只通过部署平台注入下面的环境变量。`config/application-prod.yml` 已加入 Git 忽略规则，不要把真实连接配置写进仓库。

| 环境变量 | 用途 |
|---|---|
| `LIBRARY_PROD_DB_HOST` | MySQL 主机名或地址 |
| `LIBRARY_PROD_DB_PORT` | MySQL 端口，默认 3306 |
| `LIBRARY_PROD_DB_USERNAME` | 仅获准访问 `library_prod` 的应用账号 |
| `LIBRARY_PROD_DB_PASSWORD` | 由部署平台或受限秘密存储提供的密码 |

不要将数据库管理员/root 账号用于应用运行。应用会在启动时运行 Flyway 迁移并用 Hibernate 校验结构，因此当前实现的这个 schema 专用账号还需要执行当前迁移所需的建表、改表、索引和外键操作权限，以及业务运行所需的读写权限；权限范围只限 `library_prod.*`，不能授予全局或其他库权限。当前版本尚未把迁移账号和日常运行账号拆成两套连接。

首次需要创建管理员时，临时设置 `LIBRARY_INITIAL_ADMIN_ENABLED=true`、`LIBRARY_INITIAL_ADMIN_USERNAME`、`LIBRARY_INITIAL_ADMIN_PASSWORD` 和 `LIBRARY_INITIAL_ADMIN_DISPLAY_NAME`。启动器只在没有管理员记录时创建账号，新账号登录后必须修改密码。确认登录和改密成功后，关闭初始化开关并删除临时密码变量；以后不要保留或重复使用初始化密码。

生产 profile 显式将会话 Cookie 设为 `Secure`、`HttpOnly` 和 `SameSite=Lax`。TLS 在可信反向代理终止；代理必须清理客户端自行提交的 `Forwarded`/`X-Forwarded-*` 请求头，再写入自己的协议和主机信息。由于应用信任这些转发头，不能让外部客户端绕过代理直接访问应用端口。CSRF 防护保持开启。

当前 Flyway 迁移包含 V12 预约到期结构。`library.reservations.enabled` 在基础配置默认关闭，dev profile 继承关闭，test profile 用于真实数据库验收；X04 完整验收通过后，prod profile 已配置开启预约和每分钟到期扫描。部署仍未执行，生产 schema、备份与恢复、HTTPS 代理和上线检查尚未验证；不要把本地配置启用视为已经发布。

应用请求日志写到标准输出，安全过滤器只记录方法、路径、状态码或异常类型，不记录密码、Cookie、令牌、查询参数或异常消息。部署平台应限制日志读取权限、设置轮转与保留期；不要打开包含业务参数的 SQL/请求体调试日志，也不要将秘密放入命令历史或普通日志。

## 构建、启动与停止

在配有本项目专用 `library_test` 和 `library_dev` 的构建机上，于项目根目录 PowerShell 加载隔离配置，再运行完整验证并打包：

```powershell
. .\scripts\set-local-db-env.ps1 -Profile test
. .\scripts\set-local-db-env.ps1 -Profile dev
.\mvnw.cmd verify
```

成品为 `target\library-management-0.0.1-SNAPSHOT.jar`。发布前先在隔离环境部署同一个 JAR 并通过登录及借还检查；将 JAR、外部配置、版本号和迁移记录一同备份到受控交付位置。不要将构建目录、数据库备份或本机 `.local/` 配置放入源码提交。

在部署目录配置 `LIBRARY_PROD_DB_*` 环境变量后，明确启用 prod profile 并启动：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
java -jar .\library-management-0.0.1-SNAPSHOT.jar --spring.config.additional-location=optional:file:./config/
```

启动时 Flyway 会校验并应用未执行迁移，随后 Hibernate 校验表结构；缺少连接变量、迁移失败或结构不符时，应用应保持启动失败。检查受控日志和登录页后再开放代理流量。停止时通过服务管理器执行正常停止；在交互窗口运行时按 `Ctrl+C`，不要为了释放端口结束无关进程。

## 迁移与应用回退

部署新 JAR 前，先查看本次版本新增的 `src/main/resources/db/migration/` 文件，确认迁移内容、锁定停机/兼容策略并制作已验证可读的备份。Flyway 会自动应用版本化迁移；已执行的迁移文件不可修改或删除，也不要自动修复校验和。

保留上一份 JAR 有助于回退应用文件，但不代表数据库也能回退。旧 JAR 可能无法通过新结构的 Hibernate 校验，或不兼容新数据。出现问题时优先暂停流量、保留现场并向前修复；只有先评估应用/结构兼容性和恢复点后，才制定恢复操作。不得未经单独授权把备份恢复到生产库。

## 备份与恢复

生产备份应由有权限的人员定期运行，并放在访问受限、加密且有轮转策略的备份位置。以下 Windows 命令以本机 MySQL 为例，让客户端交互提示密码，避免把密码写入命令行；远程数据库应将主机地址替换为经过核准的目标地址：

```powershell
New-Item -ItemType Directory -Force .\backups | Out-Null
& 'C:\Program Files\MySQL\MySQL_Server 8.0\bin\mysqldump.exe' `
  --host=127.0.0.1 --port=3306 --user=library_prod_backup --password `
  --single-transaction --quick --routines --triggers --events --hex-blob `
  --set-gtid-purged=OFF --no-tablespaces `
  --result-file='.\backups\library_prod-YYYYMMDD-HHMMSS.sql' library_prod
```

备份账号仅需读取备份所需对象，并且权限限于 `library_prod.*`。该命令没有 `--databases` 参数，恢复时必须明确指定目标 schema。演练目标必须与生产库分离，例如由数据库管理员创建空的 `library_restore_drill` 和只访问该库的演练账号。当前本机演练库已经保存了测试库快照；若要再次演练，应新建一个独立空 schema 和对应账号，并在命令中复核替换目标名称。`mysqldump` 文件包含 `DROP TABLE IF EXISTS`；只有在确认目标 schema 是独立、可重建的演练库后才能导入，不能用这条流程覆盖生产库。

恢复前可用 MySQL 登录路径保存演练账号。工具会交互提示密码，避免把密码写入命令历史。下面的导入命令通过 PowerShell 的标准输入重定向传递备份文件：

```powershell
& 'C:\Program Files\MySQL\MySQL_Server 8.0\bin\mysql_config_editor.exe' `
  set --login-path=library-restore-drill --host=127.0.0.1 --port=3306 `
  --user=library_restore_app --password
$restore = Start-Process `
  -FilePath 'C:\Program Files\MySQL\MySQL_Server 8.0\bin\mysql.exe' `
  -ArgumentList @('--login-path=library-restore-drill', '--database=library_restore_drill', '--default-character-set=utf8mb4') `
  -RedirectStandardInput '.\backups\library_prod-YYYYMMDD-HHMMSS.sql' -NoNewWindow -Wait -PassThru
if ($restore.ExitCode -ne 0) { throw '恢复失败；保留现场并检查演练库日志。' }
```

登录路径保存在当前 Windows 用户配置中；请限制该账户访问，完成演练后按需用 `mysql_config_editor remove --login-path=library-restore-drill` 删除。它不是生产密码轮换或备份加密的替代品。

导入前逐字核对 `--database=library_restore_drill`；不要对 `library_prod` 执行演练恢复。恢复后核对 Flyway 迁移版本、核心表行数和一条完整借还记录，使用恢复库账号启动应用并检查登录、检索、借出、归还及库存状态。记录恢复时间和检查结果，再按组织的保留规则清除演练数据。若客户端、schema 名称或来源备份不符，停止操作，不要通过删除现有表来“修复”恢复。

## 本机演练记录

本机 `library_test` 的 V8 一致性备份已恢复到新建的 `library_restore_drill`，两个 schema 使用不同受限账号。恢复前确认目标库和账号不存在；恢复账号只能访问演练库，对测试库的跨库查询被拒绝。恢复后检查 Flyway V8、Hibernate 结构校验及关键行数：账号 0、读者档案 0、分类 3、书目 0、副本 0、借阅 0、审计记录 354。源库只读备份，没有修改开发库或测试库。

打包 JAR 通过 prod profile 和外部配置启动。管理员、馆员、读者都完成登录和首次改密；工作人员创建了读者、分类、书目和副本，随后办理借出与归还。MySQL 中借出后为 1 条未还记录且副本 `ON_LOAN`；归还后记录有归还时刻且副本 `AVAILABLE`。冒烟数据随后被重新恢复为备份快照，检查确认没有遗留演练账号或借还记录。生产配置另行实测发出的会话 Cookie 带有 `Secure`、`HttpOnly`、`SameSite=Lax` 标记。由于本机没有 HTTPS 反向代理和证书，实际 HTTPS 终止与公网访问尚未测试，也没有部署上线。
