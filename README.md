# alert-console-starter

告警配置管理页面 starter。**不是独立 Web 应用**，不单独启动进程或端口；业务应用引入后，随宿主 Spring Boot 应用启动，复用业务端口访问 `/alert-console/`。

## 形态

- 普通 jar，不是可执行 jar；
- 依赖 `alert-starter`，复用其 `alertJdbcTemplate`、`AlertConfigProvider`、`AlertConfigLoader`；
- 前端构建产物打包在 `static/alert-console/`，不需要 Nginx；
- 默认关闭，配置 `alert.console.enabled=true` 才注册页面和 API；
- 多个业务应用引入时，各自有页面入口，但读写同一个共享 `alert_config` 库。

## 接入

先安装或部署 `alert-starter:1.0.0`，再引入：

```xml
<dependency>
    <groupId>com.middleware.alert</groupId>
    <artifactId>alert-console-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

配置：

```properties
alert.enabled=true
alert.datasource.url=jdbc:kingbase8://host:54321/pv?currentSchema=alert
alert.datasource.username=alert_writer
alert.datasource.password=ENC(....)
alert.config.enabled=true

# 开启管理页面
alert.console.enabled=true
```

访问：

```
http://host:业务端口/alert-console/
```

> 页面没有鉴权，仅限内网使用。业务应用若有登录/鉴权拦截器，需要在拦截器中排除 `/alert-console/**`。

## 权限

管理页面运行在业务应用内，复用 `alert.datasource`，所以应用账号需要：

```sql
GRANT INSERT ON alert_info TO <应用账号>;
GRANT USAGE, SELECT ON SEQUENCE alert_info_id_seq TO <应用账号>;
-- 引入 alert-console-starter 后
GRANT SELECT, INSERT, UPDATE ON alert_config TO <应用账号>;
```

不引入管理页面的应用，`alert_config` 只需要 `SELECT`。

## 页面功能

页面分三个页签，数据全部来自共享库（`alert_config` / `alert_info`），无需额外服务：

- **告警配置**
  - 服务下拉 + 左侧服务列表，来源 `alert_config.app_name` 去重，展示版本、实例数、告警数、正常/待关注；
  - 配置分组：控制台、写入、去重、限流、业务异常、NPE/栈溢出、慢请求、异步异常、慢 SQL、内存、出站 HTTP；
  - 表单编辑：BOOL 用下拉，LIST 用多行文本，其他用输入框；修改项标“已修改”，保存按钮显示待保存项数；
  - 显示库中值、当前实例生效值、未初始化/需重启/只读标记；
  - 保存：逐项校验 + 乐观锁，配置被其他实例改过会返回 409；
  - 保存并刷新：保存后先刷新当前实例，再按 `console.instances` 刷新其他实例；
  - 只刷新该服务；
  - 刷新结果：实例、成功/失败、耗时、失败原因；
  - 查看实例：下拉选择“本机（当前进程）”或 `console.instances` 中登记的地址，展示该实例的生效值与版本（不可达时显示原因）。

- **告警大屏**
  - KPI：告警总数、近 1 小时、严重/错误/警告、受影响服务；
  - 告警趋势按级别堆叠，支持 1h / 6h / 24h / 7d；
  - 告警类型分布、服务告警 Top 10、服务健康矩阵、慢请求 Top URI；
  - 实时告警流（最近 8 条），点击打开详情抽屉；
  - 自动刷新 30s / 60s / 关闭，支持全屏；
  - 资源水位（连接池/线程池/堆内存）为 M2/M3 预留，未接入时展示提示文案。

- **告警列表**
  - 跨服务查询 `alert_info`：服务多选、类型、级别、时间范围、关键字（标题/URI/traceId）；
  - 顶部统计总数与严重/错误/警告数；
  - 分页（默认每页 20 条），点击“详情”打开抽屉查看堆栈、SQL、扩展信息。

新增应用仍通过接口完成：`POST /alert-console/api/apps`，自动复制 35 条默认配置，不需要 DBA 写 SQL。

## 多实例刷新

在 `console.instances` 中配置实例地址，格式 `ip:port,ip:port`：

```properties
console.instances=10.117.32.11:10009,10.117.32.12:10009
```

- 当前实例直接调用 `AlertConfigLoader.refresh()`；
- 其他实例调用 `POST http://ip:port/alert/refresh/local`（该端点由 **alert-starter** 提供，业务服务无需引入 console）；
- 连接 1s、读 3s，单个实例失败不影响其他实例；
- 只访问配置表中声明的地址，不接受请求参数传入任意 URL；
- 升级顺序：先升级业务服务（alert-starter），再升级管理端（alert-console-starter）。

## API

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/alert-console/api/apps` | 应用列表 |
| `POST` | `/alert-console/api/apps` | 新增应用（复制 35 条默认配置） |
| `GET` | `/alert-console/api/services` | 服务列表（版本、实例、告警数、状态） |
| `GET` | `/alert-console/api/config?appName=` | 查询配置 |
| `PUT` | `/alert-console/api/config` | 保存配置（乐观锁） |
| `GET` | `/alert-console/api/alerts` | 告警分页查询（appNames/type/level/range/keyword/page/size） |
| `GET` | `/alert-console/api/alerts/{id}` | 告警详情（含堆栈、SQL、扩展信息） |
| `GET` | `/alert-console/api/dashboard?range=` | 大屏聚合（range：1h/6h/24h/7d） |
| `POST` | `/alert-console/api/refresh` | 刷新当前 + `console.instances` 中的实例 |
| `GET` | `/alert-console/api/config/effective?appName=&instance=` | 查看指定实例的配置生效值（本机读快照，其他实例远程拉取） |
| `GET` | `/alert-console/api/health` | 当前应用与时间 |

## 前端开发

源码在 `ui/`，Vue 3 + Element Plus + Vite：

```bash
cd ui
npm install
npm run build
```

产物输出到 `../src/main/resources/static/alert-console/`。**内网 Maven 构建不需要 Node**，仓库中已提交构建产物。

> jar 中只包含 `static/alert-console/` 下的构建产物，不包含 `ui/` 源码目录；不要手工把 `ui/` 复制进 `static/`。

本地开发：

```bash
cd ui
npm run dev
# 默认代理 /alert-console/api 到 http://localhost:10009
```

## 构建与测试

```bash
mvn test
mvn package
```

产物为普通 jar（不含 `BOOT-INF`）。

## 注意事项

- `alert.console.enabled` 默认 `false`，验证完或不需要页面的环境不要开启；
- 页面无登录、无操作审计，仅限内网；
- `queue.capacity`、`alert.datasource.*`、`alert.enabled` 修改后需要重启；
- 其他配置项保存并刷新后实时生效；
- 页面修改 `console.instances` 后需要先保存，再点击刷新，才会使用新的实例列表。
