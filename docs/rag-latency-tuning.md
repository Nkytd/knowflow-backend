# RAG Latency Tuning

This project uses an OpenAI-compatible chat API for final answer generation. The slowest part of a real RAG request is usually the remote model call, so KnowFlow controls both prompt size and answer size.

## Key Settings

| Environment variable | Default | Meaning |
| --- | ---: | --- |
| `KNOWFLOW_LLM_OPENAI_MAX_CONTEXT_CHUNKS` | `5` | Maximum retrieved chunks sent to the chat model. |
| `KNOWFLOW_LLM_OPENAI_MAX_CONTEXT_CHARS` | `6000` | Maximum total characters of retrieved context in one prompt. |
| `KNOWFLOW_LLM_OPENAI_MAX_CHUNK_CHARS` | `1200` | Maximum characters kept from each chunk. |
| `KNOWFLOW_LLM_OPENAI_MAX_OUTPUT_TOKENS` | `700` | Maximum answer tokens requested from the chat model. |
| `KNOWFLOW_LLM_OPENAI_READ_TIMEOUT_MS` | `60000` | HTTP read timeout for the model API call. |

## Recommended Demo Values

For interview demos or local smoke tests, prefer a smaller context budget:

```powershell
$env:KNOWFLOW_LLM_OPENAI_MAX_CONTEXT_CHUNKS='3'
$env:KNOWFLOW_LLM_OPENAI_MAX_CONTEXT_CHARS='3500'
$env:KNOWFLOW_LLM_OPENAI_MAX_CHUNK_CHARS='900'
$env:KNOWFLOW_LLM_OPENAI_MAX_OUTPUT_TOKENS='450'
```

This usually improves perceived latency because the model receives fewer tokens and has a shorter maximum generation length.

## Engineering Trade-off

Lower budgets are faster and cheaper, but may miss secondary evidence. Higher budgets improve completeness for complex questions, but increase latency and token cost. For production, tune these values together with retrieval quality metrics and the QA performance fields stored on `qa_message`:

- `retrieval_latency_ms`
- `generation_latency_ms`
- `retrieval_cache_hit`
- `answer_mode`