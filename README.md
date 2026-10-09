# 图书管理系统

当前提供 T02 的最小中文首页，使用 Spring Boot 4.1.1、Spring MVC 和 Thymeleaf。本步不需要数据库账号即可启动，后续按计划逐步增加功能。

## 当前进度

- T02：创建启动入口、首页、样式、Maven Wrapper 和首页自动测试；构建、实际 HTTP、桌面及 390px 窄屏浏览器验证均通过。实际验收记录见 [开发进度](docs/progress.md)。
- T01：环境与需求文档已经建立，MySQL 认证仍返回 1045，专用库检查未通过；没有标记为完成。用户要求继续后，先执行不依赖数据库的 T02。
- T03：尚未开始。接入数据库前必须解决登录问题并验证独立开发库、测试库。

## 环境

固定 Java 21、Spring Boot 4.1.1、Maven 3.9.16。当前本机使用 Oracle JDK 21.0.1，完整版本记录见 [环境文档](docs/environment.md)。首次使用 Wrapper 会下载固定 Maven 和项目依赖，需要联网；无需另外安装全局 Maven。

项目通过 `.mvn/maven.config` 使用项目内的全局设置基线，依赖从默认 HTTPS Maven Central 获取，缓存放在被忽略的 `.local/maven-repository/`；不会修改本机 Maven 全局设置。这样可避开本机既有 HTTP 镜像造成的下载停滞。个人用户级 Maven 设置仍遵循 Maven 原有合并规则。

## Windows 构建与测试

在 PowerShell 中进入项目目录：

```powershell
Set-Location 'E:\AI\图书管理系统\library-management'
.\mvnw.cmd verify
```

`verify` 会编译源码、运行首页自动测试并生成可执行 JAR。首页测试检查完整应用能否启动、请求 `/` 是否返回 HTTP 200、是否渲染正确模板以及是否包含中文系统名和启动提示。

## 启动与停止

```powershell
.\mvnw.cmd spring-boot:run
```

浏览器访问 [本机首页](http://localhost:8080/)。当前仅监听本机地址。结束时在启动窗口按 `Ctrl+C`。

也可以在完成构建后运行：

```powershell
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
| `src/test/java/com/example/library/web/HomePageTest.java` | 完整应用上下文中的首页请求测试 |

一次首页请求依次经过：浏览器请求 `/` → Spring MVC 找到 `HomeController` → 返回视图名 `index` → Thymeleaf 渲染 HTML → 浏览器加载本地 CSS。此过程没有数据库访问。

## 计划与本机配置

- [项目计划与已采用调整](docs/plan.md)
- [需求与权限规则](docs/requirements.md)
- [开发环境与数据库约定](docs/environment.md)
- [逐步验收与剩余问题](docs/progress.md)

本机数据库凭据只放在被忽略的 `.local/` 目录，公开 JSON 示例不包含真实值；当前网页不会读取该文件。数据库配置、迁移和真实 MySQL 检查在 T03 完成。
