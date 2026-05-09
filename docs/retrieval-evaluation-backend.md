# 检索评测后端模块说明

这次项目已经不只停留在脚本评测，而是补了可持久化的后端评测模块，用来把“问答效果变好了”变成可回归、可对比、可解释的数据。

## 1. 解决什么问题

RAG 系统最容易被追问的问题是：你怎么证明检索质量变好了？

这个模块的目标是把典型问题沉淀成评测用例，每次调整 query expansion、TopK、召回权重、低置信度阈值之后，都可以用同一批样本重新跑一遍，避免只凭肉眼感受判断效果。

## 2. 数据表

- `qa_retrieval_eval_case`：保存评测用例，包括问题、期望状态、期望命中文档、期望关键词、TopK。
- `qa_retrieval_eval_run`：保存每次评测运行的汇总指标。
- `qa_retrieval_eval_result`：保存每条 case 的实际命中结果、Top1 分数、命中排名、失败原因、query variants 和候选 chunks。

## 3. 核心接口

```http
POST   /api/v1/admin/retrieval-evaluations/cases
GET    /api/v1/admin/retrieval-evaluations/cases
PUT    /api/v1/admin/retrieval-evaluations/cases/{id}
DELETE /api/v1/admin/retrieval-evaluations/cases/{id}

POST   /api/v1/admin/retrieval-evaluations/runs
GET    /api/v1/admin/retrieval-evaluations/runs
GET    /api/v1/admin/retrieval-evaluations/runs/{id}
GET    /api/v1/admin/retrieval-evaluations/runs/{id}/results
```

## 4. 创建评测用例示例

```json
{
  "knowledgeBaseId": 1,
  "caseName": "世界模型定义问题应命中指定文档",
  "questionText": "什么是世界模型",
  "expectedStatus": "SUCCESS",
  "expectedDocumentId": 10,
  "expectedKeywords": ["世界模型", "预测未来状态"],
  "topK": 5,
  "enabled": true
}
```

## 5. 运行评测示例

```json
{
  "knowledgeBaseId": 1,
  "caseIds": [1, 2, 3],
  "topK": 5
}
```

## 6. 指标解释

- `passRate`：总通过率。
- `recallAtK`：期望命中文档在 TopK 内出现的比例。
- `top1HitRate`：期望命中文档排在 Top1 的比例。
- `noHitAccuracy`：无答案问题被正确判定为 `NO_HIT` 的比例。
- `avgTopScore`：Top1 候选最终平均分。
- `avgTopLexicalScore`：Top1 候选词法平均分。
- `avgTopVectorScore`：Top1 候选向量平均分。

## 7. 面试讲法

可以这样表达：

> 我没有只靠人工感觉判断 RAG 是否变好，而是把典型问题固化成评测用例，并把每次评测运行持久化。每条结果都会记录 query variants、候选 chunk、词法分、向量分、最终分、命中文档排名和失败原因。这样我调整 query expansion、TopK、阈值或权重之后，可以用同一批 case 做回归验证，避免只优化单个问题却破坏整体召回质量。

这能体现三个能力：

- 你知道 RAG 优化不能只看单次回答，而要看可复现评测。
- 你能把评测结果结构化落库，形成工程闭环。
- 你能用指标解释检索策略调整前后的收益和风险。
