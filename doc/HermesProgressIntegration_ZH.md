# leetcode-editor × Hermes 學習進度整合規格

> 狀態：設計提案；尚未代表全部功能已實作。
> 原則：Plugin 提供可驗證的練習事件，Hermes 負責教練判斷、長期進度與 TickTick 排程。

## 目標與邊界

- Plugin 記錄題目、計時、執行、提交與自評等 metadata。
- Hermes 依事件與使用者回報判斷完成度、熟練度及複習排程。
- Accepted 只證明測資通過，不等於真正掌握。
- 完整 source code、憑證與敏感本機資訊預設不得離開 IDE。

## 現有整合點

- `CodeManager.SubmitCheckTask` 會在 LeetCode 回傳最終結果後更新題目狀態。
- `QuestionSubmitNotifier.TOPIC` 已在提交流程結束時送出 `host`、`slug`。
- Accepted 可由 `status_code == 10` 或 `question.status == "ac"` 判斷。
- `TimerBarWidget` 已持有目前題目與累積秒數，但目前只在 UI 記憶體內。
- `CodeManager` 可取得 question、language 與 solution path。

這些是實作候選接點；修改前仍須重新讀取目前程式碼，不把本段當成永遠正確的 runtime 狀態。

## 第一階段：append-only JSONL Event Reporter

預設輸出位置可設為：

```text
~/.leetcode-editor/progress/events.jsonl
```

每行一個事件，至少支援：

- `problem.opened`
- `timer.started`
- `timer.stopped`
- `code.ran`
- `submission.completed`
- `study_session.completed`

要求：

- append-only，不覆寫歷史。
- 使用 append lock 或等價機制，避免 IDE crash 留下半行 JSON。
- 每筆具 `schema_version`、唯一 `event_id` 與帶時區的 `occurred_at`。
- payload 只保留學習所需 metadata。

### `submission.completed` schema

```json
{
  "schema_version": 1,
  "event_id": "uuid",
  "event": "submission.completed",
  "occurred_at": "2026-07-18T14:30:00+08:00",
  "problem": {
    "id": 1,
    "slug": "two-sum",
    "title": "Two Sum",
    "difficulty": "Easy",
    "topics": ["Array", "Hash Table"]
  },
  "attempt": {
    "language": "python3",
    "elapsed_seconds": 1320,
    "status": "Accepted",
    "accepted": true,
    "submission_id": "optional",
    "solution_path": "optional-local-relative-path"
  },
  "plan": {
    "source": "grind75",
    "order": 1
  }
}
```

## 完成學習 Session 的自評

新增 `Finish Study Session` action／小視窗，讓使用者補充：

- 熟練度：green／yellow／red
- 提示層級：none／hint／video／full_solution／ai_full
- 是否能從空白重寫
- 暴力解是否完成
- 時間／空間複雜度
- 卡點：syntax／pattern／implementation／debugging／testing／complexity／communication
- 簡短 note

完成後產生 `study_session.completed`。Hermes 主要依此事件安排 D+1／D+3／D+7／D+14，而不是只依 Accepted。

## 第二階段：可選 webhook

IDE 與 Hermes 不在同一台機器時，可選擇 POST 到使用者設定的 Hermes webhook：

- URL 與 bearer token 存 JetBrains PasswordSafe，不進 repo。
- 只允許 HTTPS。
- 預設關閉，使用者明確啟用才傳送。
- `event_id` 作為 idempotency key。
- 失敗進 retry queue，使用 exponential backoff。
- 預設只傳 metadata，不傳完整程式碼。

另提供：

- `Export Progress JSONL`
- `Retry Pending Events`
- `Copy Daily Summary`

即使 webhook 暫時失敗，本機事件仍須保留。

## 深度連結

TickTick 任務可用 slug／LeetCode URL 開題；可評估 custom URI：

```text
leetcode-editor://open?slug=two-sum
```

## Hermes 消費流程

1. Hermes 依 curriculum/state 產生當日 TickTick 任務。
2. Plugin 事件證明 open／run／submit、耗時與 Accepted。
3. `study_session.completed` 提供提示層級、卡點與綠黃紅。
4. Hermes 結合使用者口頭回報更新學習 state。
5. 熟練度決定複習節奏：
   - green：D+3、D+7、D+14
   - yellow：D+1、D+3、D+7
   - red：D+1，必要時補同 Pattern 的更簡單題
6. TickTick 任務可依規則完成，但 Accepted 與「完成學習」必須分開。

## 隱私與安全 invariant

永遠不得輸出或傳送：

- LeetCode cookie／CSRF token
- JetBrains PasswordSafe 內容
- 完整 HTTP headers
- 密碼或 bearer token
- 未明確同意傳送的完整 source code
- 題目中的敏感本機絕對路徑

`solution_path` 優先省略或使用相對路徑；需要關聯版本時可用檔名或 Git commit。

## 實作順序

1. `submission.completed` JSONL。
2. Timer elapsed 持久化。
3. `Finish Study Session` 自評視窗與事件。
4. 匯出／補送機制。
5. 可選 Hermes webhook。
6. custom URI 深度連結。

前三項完成後，即使沒有 webhook，也能用本機 JSON summary 可靠更新學習進度。

## 驗證

任何實作改動均須遵守根目錄 `AGENTS.md` 的完整驗證鏈：

```bash
./gradlew test buildPlugin verifyPlugin
```

事件功能另需測試：

- JSONL 每行皆為合法 JSON；crash／併發不產生半行。
- `event_id` 唯一，重送具 idempotency。
- token、cookie、完整 source code 與絕對敏感路徑不出現在輸出。
- webhook 關閉或離線時，本機紀錄仍完整。
