# 项目进度记录

## [日期 2023-11-07]

### 已完成
- 添加了材料收集状态管理功能
- 创建了新的POST端点 `/api/materials/status` 用于标记材料是否已收集
- 在材料列表API响应中添加了收集状态字段 `collected`
- 为新端点添加了完整的错误处理和验证逻辑
- 支持CORS预检请求，确保前端可以正常调用API
- 重构了API结构，将处理器按端点分离到独立文件中：
  - 创建了`handlers`包用于存放API处理器
  - 将`PlacementsHandler`、`MaterialsHandler`、`TextHandler`和`MaterialStatusHandler`分离为独立文件
  - 更新了`MaterialHttpServer`类以支持新的结构
  - 添加了公共方法以支持处理器间的协作

### 进行中
- 继续优化材料清单解析功能
- 探索使用Litematica API获取更准确的材料列表

### 计划
- 添加批量更新材料收集状态的端点
- 添加材料收集进度统计功能

### 问题与解决方案
- 问题：需要在不同请求之间保持材料收集状态
  解决方案：使用ConcurrentHashMap实现线程安全的状态存储
- 问题：API处理逻辑代码过于集中，不易维护
  解决方案：将不同端点的处理逻辑分离到独立文件，提高代码可维护性

## [2025-06-11] （MCP_server_time: 2025-06-11T08:00:49+08:00）

### 已完成
- 改进了材料收集状态API，从布尔值改为数字值：
  - 修改了 `MaterialHttpServer` 类中的数据结构，从 `Map<String, Map<String, Boolean>>` 改为 `Map<String, Map<String, Integer>>`
  - 更新了 `updateMaterialCollectionStatus` 方法接受数字值而非布尔值，并验证数量不超过总需求
  - 修改了 `MaterialStatusHandler` 类接受 `count` 参数而非 `collected` 参数
  - 更新了 API 响应格式，提供更详细的收集状态信息：已收集数量、剩余数量和完成百分比
  - 改进了文本格式输出，添加收集进度列，显示 "已收集/总数 (百分比%)" 格式
  - 添加了验证逻辑确保收集数量不超过需求总数
  - 为保持向后兼容，仍保留了 `collected` 布尔字段（当全部收集完成时为 true）

### 进行中
- 无

### 计划
- 测试新的材料收集状态API
- 考虑添加批量更新材料收集状态的端点

### 问题与解决方案
- 问题：材料收集状态仅支持布尔值（已收集/未收集），无法显示部分完成状态
  解决方案：重构API和数据结构支持数字值，表示已收集的具体数量

## [2025-06-10] （MCP_server_time: 2025-06-10T16:58:44+08:00）

### 已完成
- 重写了材料解析逻辑，不再依赖Litematica API：
  - 新增了`parseSchematicFile`方法，直接从.litematic文件中解压并解析NBT数据
  - 解析过程：先通过gzip解压文件，然后解析NBT数据格式，分析Regions、BlockStatePalette和BlockStates
  - 重构了`getMaterialListFromPlacement`方法的流程，优先使用直接解析方式
  - 添加了多重降级策略，确保在各种情况下都能尽可能获取到真实材料数据
- 修复了材料HTTP服务器的环境检测问题：
  - 移除了`isClientEnvironment()`方法中错误的环境检测逻辑，使其始终尝试获取真实数据
  - 解决了误判为服务器环境导致不尝试解析投影材料的问题
  - 优化了`getMaterialListFromPlacement`方法的结构，确保正确处理各种边缘情况
- 删除了未实现的材料收集功能，包括：
  - 移除了`material`包及其所有类（SyncmaticaMaterialEntry、SyncmaticaMaterialList、DeliveryPosition）
  - 从ServerPlacement类中移除了材料相关的字段和方法
  - 移除了GUI中的材料收集按钮和相关处理代码
  - 移除了材料收集相关的语言资源条目
- 实现了材料HTTP服务器功能：
  - 创建了`MaterialHttpServer`类，提供HTTP服务器功能
  - 创建了`MaterialServerFeature`类，管理HTTP服务器的生命周期
  - 在Context类中添加对材料服务器功能的支持
  - 添加了API文档
- 重新实现材料HTTP服务器功能，提供以下API:
  - GET /api/placements - 获取所有投影列表
  - GET /api/materials?id=<投影ID> - 获取指定投影的材料清单
- 使用真实的Litematica数据代替示例数据，实现了对结构材料的准确统计
- 改进了MaterialServerFeature类，添加了更好的初始化和错误处理
- 添加了MaterialServerFeature的getter方法到Context类
- 修复了材料HTTP服务器的多项关键问题：
  - 修复了未渲染投影无法获取材料的问题，添加临时渲染和卸载逻辑
  - 更健壮地解析查询参数，支持URL编码处理
  - 增加了详细的调试日志，便于问题排查
  - 修复了UUID比较逻辑，确保正确匹配投影
  - 修正了IIDContainer接口的导入路径，解决编译错误
  - 修正了SchematicHolder的方法使用，从错误的loadSchematic改为正确的getOrLoad方法
  - 添加了客户端环境检测，防止在服务器端访问Litematica API导致崩溃

### 进行中
- 无

### 计划
- 进一步测试新解析逻辑在各种场景下的表现
- 优化大型结构的解析效率

### 问题与解决方案
- 问题：依赖Litematica API解析材料导致在某些环境下无法正常工作
  解决方案：实现了直接从.litematic文件解析NBT数据的方法，完全不依赖API
- 问题：材料HTTP服务器错误地判断运行环境为服务器端，导致不尝试解析投影
  解决方案：修改环境检测逻辑，确保始终尝试解析投影材料
- 问题：需要通过HTTP API提供投影材料数据
  解决方案：实现基于Java内置HTTP服务器的API服务
- 问题：与Litematica API不兼容导致编译错误
  解决方案：暂时使用示例数据，将来研究正确的API用法后再实现真实数据提取
- 遇到Litematica API兼容性问题：通过直接使用SchematicPlacement和LitematicaBlockStateContainer获取方块状态
- 解决了BlockState计数逻辑问题，确保正确统计每种方块的数量
- 问题：在Syncmatica HTTP API中访问投影材料清单时找不到渲染的SchematicPlacement
  解决方案：增加多种降级措施，包括临时渲染、直接从文件加载、更精确的UUID比较等
- 问题：材料加载代码中使用了不存在的API方法（DataManager.getSchematicHolder和loadSchematic）
  解决方案：修正为正确的API方法（SchematicHolder.getInstance().getOrLoad()）
- 问题：在服务器环境中运行时由于缺少客户端API导致错误
  解决方案：添加环境检测，在服务器端运行时使用示例数据而不尝试访问Litematica API 