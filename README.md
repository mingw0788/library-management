# 图书管理系统

当前已完成 T01—T10，提供中文首页、登录和强制改密、角色授权、工作人员/读者、分类、书目及实体副本管理；使用 Spring Boot 4.1.1、Spring MVC、Thymeleaf、MySQL、JPA 和 Flyway。

## 当前进度

- T02：创建启动入口、首页、样式、Maven Wrapper 和首页自动测试；构建、实际 HTTP、桌面及 390px 窄屏浏览器验证均通过。实际验收记录见 [开发进度](docs/progress.md)。
- T01：环境检查已完成；本机 MySQL 8.0.44 与隔离的 dev/test 数据库及受限账号均已验证。
- T03：MySQL 8.0.44、JPA、Flyway、dev/test/prod 配置隔离和第一份迁移已建立；真实测试记录见 [开发进度](docs/progress.md)。
- T04—T06：账号密码模型、登录/退出、首次管理员初始化、统一权限与会话有效性已完成。
- T07：管理员可管理工作人员；管理员和馆员可创建、维护、禁用、重置读者；读者账号与档案采用独立表并在同一事务中创建。
- T08：管理员和馆员可新增、编辑、查询、删除分类；T09 已用书目外键保护被引用分类。
- T09：管理员和馆员可维护书目资料、校验 ISBN、归档书目；MySQL 外键已禁止删除正在被书目引用的分类，书目版本号用于拒绝过期编辑。
- T10：管理员和馆员可逐册登记、查询和注销实体副本；条码唯一，数量从副本状态汇总，未注销副本会阻止书目归档。

## 环境

固定 Java 21、Spring Boot 4.1.1、Maven 3.9.16。当前本机使用 Oracle JDK 21.0.1，完整版本记录见 [环境文档](docs/environment.md)。首次使用 Wrapper 会下载固定 Maven 和项目依赖，需要联网；无需另外安装全局 Maven。

项目通过 `.mvn/maven.config` 使用项目级 Maven 设置，依赖从 Google Cloud 托管的 Maven Central HTTPS 镜像获取，缓存放在被忽略的 `.local/maven-repository/`；不会修改本机 Maven 全局设置。当前网络对直连 Maven Central 返回 403/握手错误。个人用户级 Maven 设置仍遵循 Maven 原有合并规则。

## Windows 构建与测试

在 PowerShell 中进入项目目录：

```powershell
Set-Location 'E:\AI\图书管理系统\library-management'
.\mvnw.cmd verify
```

项目使用本机受限账号连接独立的 `library_test` 数据库。先在当前 PowerShell 会话载入本机连接配置，再运行完整测试：

```powershell
. .\scripts\set-local-db-env.ps1 -Profile test
. .\scripts\set-local-db-env.ps1 -Profile dev
.\mvnw.cmd verify
```

测试会连接真实 MySQL 8.0.44、核对数据库隔离、READ COMMITTED、UTC 会话和首个 Flyway 迁移，也会回归首页并生成可执行 JAR。命令不会清空数据库；请勿把 `.local/mysql-connection.json` 或密码提交到 Git。

## 启动与停止

```powershell
. .\scripts\set-local-db-env.ps1 -Profile dev
.\mvnw.cmd spring-boot:run
```

浏览器访问 [本机首页](http://localhost:8080/)。当前仅监听本机地址。结束时在启动窗口按 `Ctrl+C`。

也可以在完成构建后运行：

```powershell
. .\scripts\set-local-db-env.ps1 -Profile dev
java -jar target\library-management-0.0.1-SNAPSHOT.jar
```

如果 8080 已被其他程序占用，可仅对本次运行指定另一个端口：

```powershell
java -jar target\library-management-0.0.1-SNAPSHOT.jar --server.port=8081
```

这时访问 `http://localhost:8081/`，结束时同样按 `Ctrl+C`。不要为释放端口随意结束其他程序。

## 关键文件与请求过程

| 文件 | 职责 |
|---|---|
| `pom.xml` | 固定 Java / Spring Boot，声明网页、模板和测试依赖 |
| `mvnw.cmd`、`.mvn/wrapper/` | 下载并使用固定的 Maven 构建项目 |
| `src/main/java/com/example/library/LibraryApplication.java` | 应用启动入口，启动内嵌服务器并发现控制器 |
| `src/main/java/com/example/library/controller/HomeController.java` | 接收首页请求，返回模板名称 `index` |
| `src/main/resources/templates/index.html` | Thymeleaf 渲染的中文首页 |
| `src/main/resources/static/css/library.css` | 首页布局、配色及小屏幕样式 |
| `src/main/resources/application.yml` | 应用名称、本机监听地址和默认端口 |
| `src/main/resources/application-*.yml` | 按 dev/test/prod 隔离数据源，凭据只从外部环境变量读取 |
| `src/main/resources/db/migration/V1__create_system_guard.sql` | 建立系统锁表并插入唯一管理员集合锁行 |
| `scripts/set-local-db-env.ps1` | 从被忽略的本机 JSON 配置载入 dev 或 test 环境变量，不显示密码 |
| `src/test/java/com/example/library/web/HomePageTest.java` | 完整应用上下文中的首页请求测试 |

一次首页请求依次经过：浏览器请求 `/` → Spring MVC 找到 `HomeController` → 返回视图名 `index` → Thymeleaf 渲染 HTML → 浏览器加载本地 CSS。此过程没有数据库访问。

## 计划与本机配置

- [项目计划与已采用调整](docs/plan.md)
- [需求与权限规则](docs/requirements.md)
- [开发环境与数据库约定](docs/environment.md)
- [逐步验收与剩余问题](docs/progress.md)

本机数据库凭据只放在被忽略的 `.local/` 目录，公开 JSON 示例不包含真实值。应用只从当前激活 profile 对应的环境变量读取连接信息：dev 固定连接 `library_dev`，test 固定连接 `library_test`，prod 固定连接由部署环境单独提供的 `library_prod`；缺少变量时启动失败。开发和测试数据库账号都没有其他库的权限。不要把 provisioning 管理连接用于应用运行。
