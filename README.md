# 图书管理系统

这是一个中文、服务端渲染的单馆图书管理系统。基础计划 T01—T24 和续借扩展 X01 已完成：管理员、馆员和读者可以按角色使用账号、馆藏目录、实体副本、借还记录和统计功能；读者可以为符合条件的本人借阅续借一次。项目使用 Java 21、Spring Boot 4.1.1、Maven Wrapper 3.9.16 和 MySQL 8.0.44。

新用户请先阅读[三角色操作手册](docs/user-guide.md)。开发者可按[开发与运行说明](docs/development-guide.md)构建、测试、启动和初始化首位管理员；环境、数据库和部署约定分别见[环境基线](docs/environment.md)、[数据库结构](docs/database.md)及[部署与恢复说明](docs/deployment-recovery.md)。每项需求的状态和验收证据见[验收清单](docs/acceptance-checklist.md)与[开发进度](docs/progress.md)。

## 本机开发

在 Windows PowerShell 中进入项目目录，先载入只保存在本机的专用测试库和开发库连接，再运行完整验证：

```powershell
Set-Location 'E:\AI\图书管理系统\library-management'
. .\scripts\set-local-db-env.ps1 -Profile test
. .\scripts\set-local-db-env.ps1 -Profile dev
.\mvnw.cmd verify
```

验证会连接隔离的 `library_test` 和 `library_dev`，运行真实 MySQL 回归并生成可执行 JAR。首次构建需要联网下载固定 Maven Wrapper 和项目依赖。完整的首次配置、空库迁移、管理员初始化和常见错误处理步骤见[开发与运行说明](docs/development-guide.md)。

测试通过后启动本机开发实例：

```powershell
. .\scripts\set-local-db-env.ps1 -Profile dev
.\mvnw.cmd spring-boot:run
```

浏览器访问 <http://localhost:8080/>。应用默认只监听本机地址；停止时在启动窗口按 `Ctrl+C`。`.local/mysql-connection.json`、本机密码、日志、备份及构建产物都不应提交到 Git。

## MVP 范围

系统包括角色授权、账号和读者档案、分类/书目/副本管理、目录检索、馆员办理借出和归还、借阅查询、审计与统计。默认最多同时借 5 册、借期 14 天；读者可按规则为本人借阅续借一次。预约扩展 X02—X04 尚未选择，也尚未实现。

本项目目前只完成本地准备和独立数据库恢复演练，没有发布到公网或生产环境。公网运行前提和回退边界见[部署与恢复说明](docs/deployment-recovery.md)。
