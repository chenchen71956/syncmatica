# Syncmatica

Syncmatica是一个Minecraft Fabric模组，为Litematica模组提供了多人联机环境下的结构蓝图（schematic）共享功能。

## 功能概述

- **蓝图共享**：允许玩家在服务器上共享Litematica结构蓝图
- **远程下载**：其他玩家可以下载并查看共享的蓝图
- **实时协作**：多名玩家可以同时查看相同的结构蓝图
- **权限管理**：控制谁可以修改或删除共享的蓝图

## 依赖关系

- Minecraft（具体版本见gradle.properties）
- Fabric Loader
- Litematica
- Malilib

## 技术说明

该模组实现了客户端与服务端之间的蓝图文件同步传输，使用UUID标识唯一蓝图，并支持各种放置操作（旋转、镜像等）。

项目包含完整的网络通信架构、文件存储系统和用户界面集成。

## 开发状态

该模组仍在开发中，部分功能可能不完整或存在问题。最近的变更可以在PROGRESS.md文件中查看。 