# NovaCart 架构说明

## 服务划分

```text
novacart-mall       :8090  用户业务网关
novacart-ai         无端口  统一 AI 模块，由商城模块托管
novacart-delivery   :8091  配送任务与物流状态
novacart-admin      :8092  开发者控制台
```

`novacart-ai` 是独立 Maven 模块，保存 Spring AI、工具、Prompt、商品 RAG 和统一
业务端口 `AiCommerceGateway`。该模块没有独立端口，由 `novacart-mall` 扫描并
托管运行；商城只实现业务适配器，不持有 AI 代码。

## AI 数据边界

```text
AiController
→ AiAssistantService
→ CatalogAiTools
→ AiCommerceGateway
→ MallAiCommerceGateway
→ 商品 / 购物车 / 订单 / 配送 / 用户身份
```

`MALL` 与 `DELIVERY` 共用统一商品目录，通过 `bizType` 区分。外卖物流节点由
配送服务维护，AI 通过 `deliveryStatus` 工具实时读取，不能根据商城订单初始状态
自行推断。

## 运行依赖

- MySQL：商品、购物车关联数据、订单、用户和客服消息。
- Redis：登录 Token、购物车和 Spring Cache。
- PostgreSQL + pgvector：商品语义知识索引。
- RabbitMQ：外卖支付事件和配送任务幂等创建。
- 阿里云百炼：Qwen 对话和 Embedding。
