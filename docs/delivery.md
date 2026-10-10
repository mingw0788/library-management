# 完整版本地交付

交付日期：2026-10-10。基础任务 T01—T24 和扩展 X01—X04 完成后，用户允许继续。D01 只刷新完整版本的本地交付包，保留原 T24 包作为历史版本。

## 文件与版本

- [完整交付 ZIP](../delivery/library-management-X04-20261010-520b1fc.zip)
- [ZIP 摘要](../delivery/library-management-X04-20261010-520b1fc.zip.sha256)
- [运行说明](../delivery/library-management-X04-20261010-520b1fc/README.md)
- [版本信息](../delivery/library-management-X04-20261010-520b1fc/VERSION.json)
- [验收报告](../delivery/library-management-X04-20261010-520b1fc/ACCEPTANCE-REPORT.md)

这些链接指向当前机器的 `delivery/` 忽略目录；Git 源码本身不包含交付 ZIP。交付包包含可执行 JAR、当前文档、两个无秘密配置示例、数据库连接加载脚本、版本/验收说明、逐文件 SHA-256 清单和独立源码 ZIP。

JAR 和 `source.zip` 对应应用源码提交 `520b1fcc1496bdbdaa3df840cf104d2bbb90813b`，包含 V1—V12 迁移和完整续借/预约功能。JAR 为 `library-management-0.0.1-SNAPSHOT.jar`，59,091,299 字节，SHA-256 为 `E385AE97902E554149B078DE0CD386AAB69D851509D0F10A6B1C92E441635B49`。文件名与旧 T24 相同，核对时应同时使用提交标识和摘要。

`source.zip` 只导出该提交的 Git 跟踪文件；包内 `docs/` 另包含 D01 的最新交付说明与记录。源码包不含 `.git`、本机连接配置、未跟踪文件或原始测试报告。

## 本机启动

在已有项目根目录的 PowerShell 中加载已有专用开发账号，再进入交付目录运行：

```powershell
. .\scripts\set-local-db-env.ps1 -Profile dev
Set-Location .\delivery\library-management-X04-20261010-520b1fc
java -jar .\library-management-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

浏览器访问 <http://localhost:8080/>；在启动窗口按 `Ctrl+C` 正常停止。现有业务账号沿用原数据库，交付包没有预设或附带账号密码。

dev 默认关闭预约。需要本机体验完整预约流程时，可在上述 Java 命令末尾显式追加 `--library.reservations.enabled=true`；只影响该次进程，启用后每分钟执行到期扫描。prod 已启用预约，但要求专用生产数据库和可信 HTTPS 代理。

在其他机器使用时，先按包内 README 准备 Java 21、MySQL 8.0.44 和受限开发账号，在 `.local/` 创建自己的配置。需要构建源码时，先解压 `source.zip`，再进入其中的 `library-management-source` 目录；运行包根目录不包含 Maven 工程。首次管理员初始化与改密见[开发手册](development-guide.md)。

## 验证和限制

X04 提交前已在真实 MySQL 上通过完整 `verify`：113 项测试、0 失败/错误/跳过，dev/test 至 V12。D01 没有重跑这些测试；复用同一已验收 JAR，逐字节比对其中的 100 个应用类和 45 个资源，核对 V12、到期调度与 prod 功能开关。

D01 实际从交付目录启动该 JAR，连接 `library_dev`；Flyway 和 Hibernate 校验通过，端口 18083 的首页与登录页均 HTTP 200，正常关闭后端口释放。初始化管理员明确关闭，未操作业务借还或预约。包内白名单、固定提交源码导出、配置空值/占位符、开发/测试专用密码比对、SHA-256 和 ZIP 完整性检查通过。

T22 的恢复演练和 T23/T24 的空库/干净检出验收仍是当时 V8 的历史证据；D01 未重做 V12 空库复现或恢复演练。生产数据库、HTTPS 代理及真实生产部署仍未实测。后续若开展本机试用、V12 恢复演练或生产部署，应先明确该步骤并获得用户允许。
