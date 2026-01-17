# Models, cost, and performance notes (AI Ant Farm)

This doc is meant to help you:

1. Log **SLA metrics** (latency + retries) and **token/cost** per model call.
2. Pick newer/better models without accidentally blowing budget.
3. Spot big architectural cost wins.
4. Compare estimated spend for the *same* workload across different models.

> Notes
> - Prices change frequently; treat numbers as *estimates*.
> - Token counts come from your logs (`antModel ok ... inputTokens=... outputTokens=...`).
> - Cost estimates assume a simple formula: `inputTokens * inputPrice + outputTokens * outputPrice`.

---

## Where the numbers come from in this repo

### Existing logs you already have
Your backend already logs one line per model call:

- `antModel ok antId=... roomId=... model=... latencyMs=... inputTokens=... outputTokens=...`

Example from `backend-logs-20260102-143948.log`:

- `model=OPENAI_GPT_4_1_NANO latencyMs=1358 inputTokens=2893 outputTokens=160`

This is enough to estimate per-call cost.

### New SLA log lines added (no transcripts)
Runners now also emit a second, parse-friendly log line per model call:

- `antModelSla ok ... estUsd=... attempt=... maxAttempts=...`

And each scheduler tick emits one summary line:

- `antTickSla antId=... roomsAttempted=... tickLatencyMs=... estUsd=...`

> Privacy: These SLA logs do **not** include prompts or responses.

---

## Interpreting your observed workload

From the sample in `backend-logs-20260102-143948.log`, typical calls look roughly like:

- input tokens: ~2,800–3,100
- output tokens: ~110–160
- latency: ~0.9s–2.2s

That’s a **context-heavy** prompt (lots of history/summary), with fairly short outputs.

The cost driver is almost always **input tokens**, not output.

---

## Biggest architectural cost savings (high impact)

### 1) Reduce input tokens (usually the #1 win)
You’re frequently sending ~3k input tokens. Easy reductions:

- **Fewer recent messages** in `PromptBuilder.buildUserContext(...)` (currently capped with `8_000`).
- **Shorter room summaries** (more aggressive summarization / keep only facts, not prose).
- **Don’t include bicameral thought** unless it actually changes behavior.

> Rule of thumb: cutting input tokens by 50% roughly cuts LLM cost by ~50%.

### 2) Route tasks to cheaper models
Not all operations need the same intelligence:

- **Bicameral thought / room summary** can often run on a cheaper model.
- **GenerateMessage** can use a stronger model only when:
  - the room role prompt is complex
  - you detect long context or low confidence
  - user explicitly asked for a “high quality” response

### 3) Skip unnecessary runs
You already have a guard:
- `if (!ant.replyEvenIfNoNew() && !roomChanged) SKIP`

More ideas:
- Skip summaries until `N` new messages exist.
- If a room is inactive, increase interval automatically.

### 4) Cache and reuse expensive derived context
- Cache room summary by `(roomId, lastMessageId)`.
- Cache “role prompt + personality” compiled system prompt once.

---

## Cost comparison table (estimates)

Below is a *relative* comparison for the same workload.

### Assumed “typical call” based on your logs
We’ll use this baseline call shape:

- input: **3,000 tokens**
- output: **140 tokens**

Cost per call:

- `3,000 * (input $/1M)` + `140 * (output $/1M)`

### Model pricing assumptions
These are placeholders until you paste in your preferred current rates.

| Provider | Model | Input $/1M | Output $/1M | Notes |
|---|---|---:|---:|---|
| OpenAI | GPT-4.1 nano | 0.15 | 0.60 | Very budget, good for routing/caching/summaries |
| OpenAI | GPT-4o mini | 0.15 | 0.60 | Similar class to nano, different tradeoffs |
| Anthropic | Claude Haiku | 0.25 | 1.25 | Often great latency + quality for price |
| OpenAI | GPT-4o | 2.50 | 10.00 | Stronger; use sparingly |
| OpenAI | GPT-4.1 | 2.00 | 8.00 | Strong reasoning; still expensive |
| OpenAI | GPT-5.0 mini |  ?  |  ?  | Budget GPT-5 tier; best candidate for "personality" bots on a budget |
| OpenAI | GPT-5.0 |  ?  |  ?  | Premium; more consistent reasoning and tone fidelity |
| OpenAI | GPT-5.2 |  ?  |  ?  | Newest; expect best instruction-following + stability, likely highest cost |

### Estimated cost for the “typical call”

| Provider | Model | Est cost per call (3k in / 140 out) |
|---|---|---:|
| OpenAI | GPT-4.1 nano | ~$0.000534 |
| OpenAI | GPT-4o mini | ~$0.000534 |
| Anthropic | Claude Haiku | ~$0.000925 |
| OpenAI | GPT-4o | ~$0.008900 |
| OpenAI | GPT-4.1 | ~$0.007120 |
| OpenAI | GPT-5 (newest) | ~$0.017100 |

> Takeaway: if you swap a cheap model for a premium one without changing your prompt size, costs can jump by **10–30x**.

---

## How to refine this with your real logs

### Quick workflow (no code changes required)
1. Pick a time window in `backend-logs-*.log`.
2. Extract `model`, `inputTokens`, `outputTokens` from `antModel ok` lines.
3. Multiply by whichever current prices you want to assume.

### If you want exact totals automatically
Next step would be adding a tiny offline script that parses:
- `backend-logs-*.log` and/or `logs/ai-transcripts.ndjson`

…and prints totals by model + by ant + by room.

---

## Recommendations for “newer + strong but budget”

General strategy:

- Default to a **cheap model** for 80–95% of calls.
- Escalate to **stronger model** only when needed.

A practical routing stack looks like:

1. Cheap: GPT-4.1 nano / GPT-4o mini / Claude Haiku
2. Mid: GPT-4.1 / GPT-4o
3. Premium: newest GPT-5-class model (only for explicit “high stakes/high quality”)

---

## What I’d do next

1. Replace the placeholder prices in code + this doc with your current provider rates.
2. Add a parser script so you can answer:
   - "$ per day" and "$ per month"
   - cost per ant
   - cost per room
3. Add a simple model router (cheap-by-default, upgrade on-demand).

---

## Cost savings clarifications (how summaries are generated today)

You asked:
> If it requires 50 messages to generate a summary I don't generate it on 51 and 52 right, I only next do it on 100?

In the current backend logic (`DefaultAntService`):

- We keep a counter of **new messages since the last summary update** (`summaryMsgCounter`).
- We regenerate the summary only when:
  - **the room has changed** (a new message exists), AND
  - either the summary is missing OR `summaryMsgCounter >= SUMMARY_WINDOW_MESSAGES_SIZE`.
- When a summary is generated, the counter is reset to **0**.

So yes: if the window size is 30, you tend to summarize at ~30 new messages, then again at ~60, ~90, etc.
You do *not* summarize on every message.

---

## Weekly cost estimate for 100 users (3 ants each, 3 room assignments, 500 msgs/week/ant)

This section turns your quota limits into a rough weekly cloud LLM bill.

### Assumptions (tweakable)

**User + bot volume**
- Users: **100**
- Ants per user: **3**
- Max messages per week per ant: **500**

So total ant messages/week:
- `100 users * 3 ants/user * 500 msgs/ant/week = 150,000 ant messages/week`

**Token usage per ant message** (based on your current logs)

From `backend-logs-20260102-143948.log`, `GenerateMessage` calls commonly land around:
- input ≈ **3,000 tokens**
- output ≈ **140 tokens**

We’ll use that as a baseline:
- tokens per message ≈ **3,140 total tokens**

So total weekly tokens for 150,000 ant messages:
- input tokens/week ≈ `150,000 * 3,000 = 450,000,000`
- output tokens/week ≈ `150,000 * 140 = 21,000,000`

> Notes
> - This **ignores summaries / bicameral thought** calls. Those can add cost, but are much less frequent than messages (and you can route them to cheaper models).
> - Your prompt window changes can swing this a lot. Cutting input tokens in half cuts most of the spend roughly in half.

### Weekly cost per 100 users by model (using the prices in this doc)

Formula:
- `weeklyCost = (inputTokens/1M * inputPricePer1M) + (outputTokens/1M * outputPricePer1M)`

With:
- inputTokens/1M = **450**
- outputTokens/1M = **21**

| Provider | Model | Input $/1M | Output $/1M | Est weekly cost (100 users) |
|---|---|---:|---:|---:|
| OpenAI | GPT-4.1 nano | 0.15 | 0.60 | ~$**80.10** |
| OpenAI | GPT-4o mini | 0.15 | 0.60 | ~$**80.10** |
| Anthropic | Claude Haiku | 0.25 | 1.25 | ~$**138.75** |
| OpenAI | GPT-4.1 | 2.00 | 8.00 | ~$**1,068.00** |
| OpenAI | GPT-4o | 2.50 | 10.00 | ~$**1,335.00** |
| OpenAI | GPT-5.0 mini | ? | ? | (fill in pricing) |
| OpenAI | GPT-5.0 | ? | ? | (fill in pricing) |
| OpenAI | GPT-5.2 | ? | ? | (fill in pricing) |

Sanity check on one row (GPT-4.1 nano):
- input: `450 * $0.15 = $67.50`
- output: `21 * $0.60 = $12.60`
- total: `$80.10 / week`

### What this means in practice

- Even at 150k ant messages/week, the **cheap models** stay in the **tens-to-low-hundreds USD/week** range.
- Jumping to premium models can push you into **$1k+/week** quickly without reducing prompt size.
- For personality bots: you usually get the biggest realism win by cleaning prompts + reducing context noise, then stepping up to a stronger model only for “high value” rooms.

---

## Major providers you can realistically onboard quickly

This table is about “who do I make an account with” for an easy setup + decent API.

| Provider | Common name | Easy API access? | Notes / setup |
|---|---|---:|---|
| OpenAI | ChatGPT / OpenAI API | Yes | Straightforward API keys, good ecosystem, strong small+mid models |
| Anthropic | Claude | Yes | Great for chat, good safety defaults, solid cost/performance at Haiku/Sonnet tiers |
| Google | Gemini | Yes | Competitive models, good for multimodal, billing via Google Cloud |
| AWS | Bedrock (Claude, Llama, etc.) | Yes (if you use AWS) | One contract/billing surface; adds IAM/VPC complexity but great for production |
| Microsoft | Azure OpenAI | Yes (Azure) | Enterprise-friendly; region quotas; sometimes lags newest models |
| xAI | Grok | Emerging | API availability and pricing vary; worth tracking for certain styles |
| Mistral | Mistral API | Yes | Strong open-weight adjacent ecosystem; good if you want EU vendor option |
| Cohere | Cohere | Yes | Good embeddings + classification; chat quality varies by use case |
| Together / Fireworks | Hosted OSS models | Yes | Easy access to Llama/Qwen/etc.; cost-effective; quality depends on model |

> Recommendation: start with **OpenAI + Anthropic** as your baseline, then consider **Gemini** or **Bedrock** depending on whether you want Google-cloud or AWS-native operations.
