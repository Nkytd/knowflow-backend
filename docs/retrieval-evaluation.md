# 检索评估说明

## 为什么要做这份材料

很多 AI 项目在面试里容易被追问一句话：  
“你怎么证明你的检索做得更好了？”

所以这个仓库补了一套轻量评估方法，目标不是做学术 benchmark，而是让你能拿出：

- 可重复执行的测试样本
- 结构化的结果报告
- 前后版本对比的数据依据

## 提供的文件

- 样例评估集：`docs/retrieval-eval-cases.sample.json`
- 评估脚本：`scripts/evaluate-retrieval.ps1`

## 评估维度

每条 case 会检查：

- `expectedStatus`
- `expectedMinSourceCount`
- Top1 来源文档是否符合预期
- 回答中是否包含关键术语
- Top1 证据片段是否包含关键证据词
- 按问题类型统计通过率

当前样例评估集覆盖 4 类问题：

- `direct_hit`：资料中有直接答案的问题，例如 Redis 数据结构、Flask 启动参数
- `semantic_hit`：需要语义理解的问题，例如 Java 多态、World Model 架构
- `technical_reasoning`：技术推理类问题，例如 Self-Attention 计算顺序差异
- `no_hit`：知识库无关问题，用来验证低置信度兜底和转人工逻辑

## 运行方式

先保证服务已经启动，并且目标知识库里已经导入了对应资料。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\evaluate-retrieval.ps1 `
  -BaseUrl "http://127.0.0.1:8081" `
  -KnowledgeBaseId 1 `
  -CasesFile ".\docs\retrieval-eval-cases.sample.json"
```

执行完成后，脚本会：

- 控制台打印通过率
- 输出每条 case 的判定结果
- 输出状态准确率、Top1 来源准确率、证据关键词覆盖率
- 输出失败 case 的原因
- 生成 JSON 报告到 `target/retrieval-eval-report.json`

## 输出指标解释

报告中的 `summary` 字段包含这些指标：

| 指标 | 含义 | 面试解释 |
|---|---|---|
| `passRate` | 所有 case 的综合通过率 | 整体检索与回答链路是否稳定 |
| `statusAccuracy` | `SUCCESS/NO_HIT` 判定准确率 | 系统是否能区分可回答与不可回答问题 |
| `sourceCountAccuracy` | 来源数量是否达到预期 | 是否真的召回了足够证据 |
| `top1SourceAccuracy` | Top1 来源文档是否符合预期 | 首条证据是否命中正确文档 |
| `answerKeywordCoverage` | 回答是否覆盖关键术语 | 生成答案是否包含必要信息 |
| `evidenceKeywordCoverage` | Top1 片段是否包含证据关键词 | 召回片段本身是否可靠 |
| `successCasePassRate` | 应该命中的问题通过率 | 正向召回能力 |
| `noHitCasePassRate` | 应该无命中的问题通过率 | 低置信度兜底能力 |

报告中的 `categorySummary` 字段会按问题类型聚合结果，这样你可以区分：

- 是直接命中类问题表现差
- 还是语义问题表现差
- 还是无关问题误命中

这比只看一个总通过率更有说服力。

## 面试中的讲法

你可以这样讲：

1. 我先把样本问题固化成评估集  
2. 每次改检索策略后都跑一次评估脚本  
3. 关注命中状态、来源文档正确率、关键术语覆盖率  
4. 这样可以把“感觉回答更好了”变成“有数据支撑的优化”  

如果面试官继续追问“为什么不用人工感觉判断”，可以这样回答：

> 人工看单次回答容易有主观性，所以我把典型问题固定成评估集。每个 case 不只判断回答状态，还会判断 Top1 来源文档、来源数量、答案关键词和证据片段关键词。这样每次调整查询扩展、召回权重、TopK 或兜底阈值之后，都能用同一批样本做回归验证，避免优化一个问题却破坏另一类问题。

如果面试官追问“这个评估是不是很简陋”，可以这样回答：

> 这套评估不是学术级 benchmark，而是工程项目里的轻量回归评估。它的价值在于成本低、可重复、能快速发现召回退化。后续如果继续生产化，会补人工标注集、Recall@K、MRR、NDCG，以及引入 rerank 前后的对比实验。

## 推荐展示指标

- 总样本数
- `SUCCESS` / `NO_HIT` 判定准确率
- Top1 来源文档命中率
- Top1 证据关键词覆盖率
- 关键术语覆盖率
- 按问题类型的通过率
- 版本对比提升幅度

## 一次完整评估怎么展示

面试或答辩中可以按这个顺序展示：

1. 先展示 `docs/retrieval-eval-cases.sample.json`，说明你有固定评估集
2. 再运行 `scripts/evaluate-retrieval.ps1`，说明评估可以自动化执行
3. 再打开 `target/retrieval-eval-report.json`，展示 summary 和 failedCases
4. 最后讲一次失败 case 如何驱动优化，例如调整关键词扩展、标题加权、TopK 或 no-hit 阈值

推荐话术：

> 我没有只停留在“能回答”这个层面，而是补了轻量检索评估。它会把问题按 direct_hit、semantic_hit、technical_reasoning、no_hit 分类，并输出状态准确率、Top1 来源准确率、证据关键词覆盖率等指标。这样我可以用数据判断检索策略是否真的变好。

## 建议你怎么继续补

1. 把评估集扩展到 20 到 50 条问题
2. 增加同义表达、模糊提问、跨文档问题
3. 记录优化前后报告，形成一张对比表
4. 在简历或面试中引用具体提升数据
5. 增加 `baseline-report.json` 和 `optimized-report.json`，形成版本对比证据
