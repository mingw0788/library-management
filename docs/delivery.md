# 最新完整本地交付（2026-10-10 完整优化版本）

本次包含 T01—T24、X01—X04、U01—U08、借还时间精度修复及三角色浏览器最终检查。当前本机与交付 JAR 字节一致；源码包含未提交修改。

- [完整交付 ZIP](../delivery/library-management-final-20261010.zip)
- [ZIP 摘要](../delivery/library-management-final-20261010.zip.sha256)
- [包目录与启动说明](../delivery/library-management-final-20261010/README.md)
- [最终验收报告](final-local-acceptance.md)

141 项回归全部通过，14 张桌面/手机截图布局检查通过，角色隔离、预约关闭和交互反馈验证通过。包内有当前源码快照、配置示例、运行手册、测试/UI 汇总及摘要，不含真实密码或数据库备份。第一次部署须准备自己的数据库和账号，不能仅凭 ZIP 获得原电脑的演示数据。当前仅完成本地版本，尚未部署生产。

以下历史 D04、D02、X04 包保持封存；其内部统计和“尚未验收”描述反映当时阶段，最新结果以本节及最终验收报告为准。

# 历史交付（D04 / U05 + V12 恢复验收）

后续 D05 已用此交付 JAR 完成 [V12 全新空库初始化验收](empty-v12-report.md)。封存 D04 ZIP 未改动；下文及包内“空库尚未验收”描述对应 D04 交付时状态，新报告目前保存在项目 docs/。

交付日期：2026-10-10。本包包含 U05 应用、当前完整源码快照、运行手册，以及 D03 的 V12 独立数据库恢复验收报告和无凭据 JSON 汇总。

- [最新交付 ZIP](../delivery/library-management-D04-20261010-d63295d.zip)
- [ZIP 摘要](../delivery/library-management-D04-20261010-d63295d.zip.sha256)
- [运行说明](../delivery/library-management-D04-20261010-d63295d/README.md)
- [版本信息](../delivery/library-management-D04-20261010-d63295d/VERSION.json)
- [交付验收报告](../delivery/library-management-D04-20261010-d63295d/ACCEPTANCE-REPORT.md)
- [V12 恢复验收报告](recovery-v12-report.md)

D04 只更新交付文档及源码快照；应用 src 的文件集合与内容均与 D02 完全一致，JAR 摘要仍为 `D63295D678ADAFB55DA6372B921FB855A1F335B50BA1FECA0E3041A4382E5FB2`。135 项完整回归沿用 U05，打包启动沿用 D02，真实恢复库登录及借还验证沿用 D03；本步没有重复全量测试、启动或数据库操作。

包内 `RECOVERY-SUMMARY.json` 汇总 D03 全表比对、权限隔离、应用验证及停止结果，含原始检查时间；备份 SQL、账号密码、私有连接和原始日志不分发。原开发数据及本机服务沿用现状，旧 D02/X04 包保留。当前源码含未提交改进，源码快照通过逐文件清单固定，不声称已经提交 Git。

现有电脑若要切换到此包，先在原启动窗口 Ctrl+C 停止旧实例，再在原项目根目录 PowerShell 执行：

```powershell
. .\scripts\set-local-db-env.ps1 -Profile dev
Set-Location .\delivery\library-management-D04-20261010-d63295d
java -jar .\library-management-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --library.reservations.enabled=true --library.bootstrap.admin.enabled=false
```

访问 http://localhost:8080/，沿用已有数据库和账号。其他电脑首次使用、源码构建和文件校验见包内 README。V12 的已有快照恢复已验收；V12 全新空库初始化、生产部署与 HTTPS 仍未验收。

---

以下为 D02 及 D01 历史交付记录，其“新版”表述对应当时版本。

# 新版完整本地交付（D02 / U05）

后续 D03 已完成当前 V12 独立数据库恢复验证，见 [V12 恢复验收报告](recovery-v12-report.md)。下文与已封存包内的“未重做恢复演练”记录对应 D02 交付时状态；封存 ZIP 未改动，不包含后续 D03 报告或数据库备份。

交付日期：2026-10-10。新版包含基础 T01—T24、扩展 X01—X04，以及 U01—U05 的首页导航/UI、详情直接预约、借出搜索核对、管理列表分页和读者提醒。旧 X04 包继续保留，见下方历史说明。

- [新版交付 ZIP](../delivery/library-management-U05-20261010-d63295d.zip)
- [ZIP 摘要](../delivery/library-management-U05-20261010-d63295d.zip.sha256)
- [启动说明](../delivery/library-management-U05-20261010-d63295d/README.md)
- [版本与验收](../delivery/library-management-U05-20261010-d63295d/VERSION.json)
- [验收报告](../delivery/library-management-U05-20261010-d63295d/ACCEPTANCE-REPORT.md)

交付目录位于本机忽略的 delivery/。包含可执行 JAR、源码 ZIP、源码逐文件摘要、操作和开发手册、配置示例、连接加载脚本、测试汇总及包内摘要。源码是当前工作区快照，包含未提交的 U01—U05；基线 Git HEAD 为 f67037020b4fbed52e537253d9e587a6a780427e，不声称这些改进已经提交。source.zip 解压后进入 library-management-source 才是 Maven 工程。

JAR SHA-256：D63295D678ADAFB55DA6372B921FB855A1F335B50BA1FECA0E3041A4382E5FB2，与当前运行的 U05 相同。复用 U05 最终真实 MySQL 完整验证：135 项测试，失败/错误/跳过均 0；D02 不重复全量测试，核对全部应用类与源码资源、源码快照、摘要和 ZIP 完整性。

本步从交付目录用 18085 端口启动，开发库 Flyway V12 与 Hibernate 校验通过；首页和登录页均 HTTP 200。管理员初始化和预约调度关闭，没有操作借还或预约。正常停止后端口释放，原 8080 实例保持运行。

现有电脑如需改用本包，先在原启动窗口 Ctrl+C 停止旧实例，然后在原项目根目录的 PowerShell 执行：

```powershell
. .\scripts\set-local-db-env.ps1 -Profile dev
Set-Location .\delivery\library-management-U05-20261010-d63295d
java -jar .\library-management-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --library.reservations.enabled=true --library.bootstrap.admin.enabled=false
```

访问 http://localhost:8080/，沿用已有数据库与账号，无须重复导入图书。dev 默认关闭预约，上述命令显式开启；开启后每分钟扫描到期保留。本包不附带本机数据库连接、账号密码、业务数据或数据库备份。在其他电脑使用须准备自己的数据库、受限账号与业务数据；空库管理员初始化按开发手册操作。

本次仅本地交付，未部署生产，未重做 V12 空库复现和恢复演练。历史 V8 恢复证据不代表当前版本恢复验证。包内 README 提供首次配置、构建、启动、停止与校验步骤。

---

以下为 D01 / X04 历史交付记录，其“当前”表述仅对应当时版本。

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
