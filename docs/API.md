# Syncmatica 材料服务器 API 文档

## 概述

Syncmatica 材料服务器提供了一组HTTP API，用于获取Minecraft Litematica结构的材料清单和投影信息。这些API可以被第三方应用程序或网站用来显示和管理多人游戏服务器中共享的结构投影所需的材料。

## 基本信息

- **基础URL**: `http://<server-ip>:24455`
- **内容类型**: 所有API响应均为`application/json`格式
- **跨域支持**: 所有API响应均包含`Access-Control-Allow-Origin: *`头，支持跨域请求

## API 端点

### 获取所有投影列表

获取服务器上所有共享的结构投影列表。

- **URL**: `/api/placements`
- **方法**: `GET`
- **响应**: 
  ```json
  {
    "success": true,
    "placements": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "my_house",
        "dimension": "minecraft:overworld",
        "posX": 100,
        "posY": 65,
        "posZ": -200,
        "owner": "Steve"
      },
      {
        "id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        "name": "farm_structure",
        "dimension": "minecraft:overworld",
        "posX": 200,
        "posY": 70,
        "posZ": -50,
        "owner": "Alex"
      }
    ]
  }
  ```

### 获取指定投影的材料清单

获取特定结构投影所需的材料清单。

- **URL**: `/api/materials?id=<placement-id>`
- **方法**: `GET`
- **参数**: 
  - `id`: 投影的UUID字符串（必须）
- **响应**: 
  ```json
  {
    "success": true,
    "placementId": "550e8400-e29b-41d4-a716-446655440000",
    "placementName": "my_house",
    "materials": {
      "items": [
        {
          "itemId": "minecraft:stone",
          "name": "石头",
          "count": 128
        },
        {
          "itemId": "minecraft:oak_log",
          "name": "橡木原木",
          "count": 64
        },
        {
          "itemId": "minecraft:glass",
          "name": "玻璃",
          "count": 32
        },
        {
          "itemId": "minecraft:brick",
          "name": "红砖",
          "count": 96
        }
      ]
    }
  }
  ```

## 错误处理

服务器返回标准HTTP状态码以及JSON格式的错误信息。

### 常见错误状态码

- `400 Bad Request`: 请求格式错误，如缺少必要参数
- `404 Not Found`: 指定的资源不存在，如未找到指定UUID的投影
- `405 Method Not Allowed`: 使用了不支持的HTTP方法
- `500 Internal Server Error`: 服务器内部错误

### 错误响应格式

```json
{
  "success": false,
  "error": "错误信息描述"
}
```

## 使用示例

### 使用curl获取所有投影

```bash
curl -X GET http://localhost:24455/api/placements
```

### 使用curl获取特定投影的材料清单

```bash
curl -X GET "http://localhost:24455/api/materials?id=550e8400-e29b-41d4-a716-446655440000"
```

### 使用JavaScript获取材料清单

```javascript
async function getMaterials(placementId) {
  try {
    const response = await fetch(`http://localhost:24455/api/materials?id=${placementId}`);
    const data = await response.json();
    
    if (data.success) {
      console.log(`${data.placementName} 的材料清单:`);
      data.materials.items.forEach(item => {
        console.log(`${item.name}: ${item.count}`);
      });
    } else {
      console.error(`获取材料清单失败: ${data.error}`);
    }
  } catch (error) {
    console.error(`请求出错: ${error}`);
  }
}
```

## 限制和注意事项

1. 服务器默认监听端口24455，确保该端口在防火墙中开放
2. API仅在Minecraft客户端运行时可用
3. 材料计数基于结构中的方块状态，不考虑合成配方
4. 所有共享的投影都可以通过API访问，无需鉴权 