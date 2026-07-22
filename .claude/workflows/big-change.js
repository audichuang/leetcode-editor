export const meta = {
  name: 'big-change',
  description: '大改動標準鏈:Sonnet 打底(研究+實作)、Fable 兜底(規劃+裁決)、Opus 中間檢查(直審+帶 Codex 雙軌)',
  whenToUse: '使用者要做一次成塊的大改動(功能、重構、現代化、效能強化)且要求多 agent 品質保證時。args: { objective: "這次要達成什麼", areas?: [{key, focus}](省略則自動拆分區), researchers?: 數量(預設6) }',
  phases: [
    { title: 'Scope', detail: '無 areas 時由 sonnet 拆研究分區', model: 'sonnet' },
    { title: 'Research', detail: 'sonnet 分區研究現況', model: 'sonnet' },
    { title: 'Plan', detail: 'fable 綜合研究產出計畫', model: 'fable' },
    { title: 'PlanCheck', detail: 'opus 帶 codex 審計畫,fable 修訂', model: 'opus' },
    { title: 'Develop', detail: 'sonnet 逐 task 實作', model: 'sonnet' },
    { title: 'GateTest', detail: '快速編譯/測試門檻' },
    { title: 'Review', detail: 'opus 直審 + opus 帶 codex 雙軌 review', model: 'opus' },
    { title: 'Triage', detail: 'fable 裁決哪些 findings 要修', model: 'fable' },
    { title: 'Fix', detail: 'sonnet 套用修正', model: 'sonnet' },
    { title: 'ReReview', detail: '雙軌複審,最多 2 輪收斂', model: 'opus' },
    { title: 'FinalVerify', detail: '跑 repo 的完整驗證鏈' },
  ],
}

if (!args || !args.objective) throw new Error('需要 args.objective:這次大改動要達成什麼')
const OBJECTIVE = args.objective
const N_RESEARCHERS = (args && args.researchers) || 6

const CTX = `你在一個既有的 git repo 裡工作(working directory 就是 repo root)。
開工前必讀 repo root 的 AGENTS.md 與 CLAUDE.md(存在的話),其中的 invariant、驗證要求、平台限制是硬性規定,違反即算失敗。
若 repo root 有 .codegraph/,優先用 shell 指令 codegraph explore "<問題或符號>" / codegraph node <符號或檔案> 理解程式碼,比 grep+讀檔省。
所有輸出資料(finding/計畫文字)用繁體中文,程式識別字保持原文。

這次改動的總目標:${OBJECTIVE}`

const AREA_SCHEMA = {
  type: 'object', required: ['areas'],
  properties: { areas: { type: 'array', items: { type: 'object', required: ['key', 'focus'], properties: {
    key: { type: 'string', description: 'kebab-case 短代號' },
    focus: { type: 'string', description: '這個分區要調查什麼,指名關鍵檔案/目錄' } } } } },
}

let areas = args.areas
if (!areas || !areas.length) {
  phase('Scope')
  log(`先由 sonnet 把目標拆成 ${N_RESEARCHERS} 個研究分區…`)
  const scoped = await agent(`${CTX}

任務:瀏覽 repo 結構(ls / codegraph),把總目標拆成 ${N_RESEARCHERS} 個互不重疊的研究分區,每區給 key 與 focus(focus 要指名關鍵檔案/目錄,讓研究員直接開工)。分區以「改動會落在哪些程式碼」為準,不是抽象主題。`,
    { label: 'scope', phase: 'Scope', model: 'sonnet', schema: AREA_SCHEMA })
  if (!scoped) throw new Error('分區 agent 失敗')
  areas = scoped.areas
}
log(`研究分區:${areas.map(a => a.key).join(', ')}`)

const RESEARCH_SCHEMA = {
  type: 'object', required: ['area', 'current_state', 'issues', 'opportunities'],
  properties: {
    area: { type: 'string' },
    current_state: { type: 'string', description: '現況架構與實作方式,3-6 句' },
    issues: { type: 'array', items: { type: 'object', required: ['title', 'detail', 'files'], properties: {
      title: { type: 'string' }, detail: { type: 'string' },
      files: { type: 'array', items: { type: 'string' } },
      severity: { type: 'string', enum: ['high', 'medium', 'low'] } } } },
    opportunities: { type: 'array', items: { type: 'object', required: ['title', 'detail', 'approach'], properties: {
      title: { type: 'string' }, detail: { type: 'string' },
      approach: { type: 'string', description: '建議的具體做法(指名 API/函式/寫法)' },
      files: { type: 'array', items: { type: 'string' } },
      impact: { type: 'string' } } } },
  },
}

phase('Research')
log(`派 ${areas.length} 個 sonnet 分區研究…`)
const research = (await parallel(areas.map(a => () =>
  agent(`${CTX}

你是研究員,負責分區「${a.key}」:${a.focus}

要求:
1. 先用 codegraph explore(或讀檔)建立整體圖像,再精讀關鍵檔案。
2. issues:實際存在、與總目標相關的問題,每條附檔案路徑。
3. opportunities:每條指名具體做法(要用的 API/函式/寫法);禁止建議違反 repo invariant 的東西。
4. 誠實:找不到就少列,別湊數。`,
    { label: `research:${a.key}`, phase: 'Research', model: 'sonnet', schema: RESEARCH_SCHEMA })
))).filter(Boolean)
log(`研究完成:${research.length}/${areas.length} 區,共 ${research.reduce((n, r) => n + r.issues.length, 0)} 個問題、${research.reduce((n, r) => n + r.opportunities.length, 0)} 個機會`)

const PLAN_SCHEMA = {
  type: 'object', required: ['overview', 'tasks'],
  properties: {
    overview: { type: 'string', description: '整體策略,5-10 句' },
    tasks: { type: 'array', items: { type: 'object', required: ['id', 'title', 'goal', 'files', 'steps', 'verify'], properties: {
      id: { type: 'integer' }, title: { type: 'string' }, goal: { type: 'string' },
      files: { type: 'array', items: { type: 'string' } },
      steps: { type: 'array', items: { type: 'string' } },
      verify: { type: 'string' }, risk: { type: 'string' } } } },
  },
}

phase('Plan')
log('fable 綜合研究結果規劃…')
let plan = await agent(`${CTX}

你是總規劃師。分區研究結果(JSON):
${JSON.stringify(research)}

產出完整實作計畫,規劃紀律:
1. 依「價值 ÷ 風險」排序,選 6-12 個 task;砍掉投機、高風險低收益、違反 invariant 的建議,在 overview 說明取捨。
2. task 之間 files 盡量不重疊(會依序實作,重疊會互踩)。
3. steps 具體到實作者不用再做設計決策(指名 API/寫法、要刪的舊碼)。
4. verify 欄寫清楚驗證方式;無法自動驗的標註「需最終手動驗」。`,
  { label: 'plan:fable', phase: 'Plan', model: 'fable', schema: PLAN_SCHEMA })
if (!plan) throw new Error('規劃 agent 失敗')
log(`計畫產出:${plan.tasks.length} 個 task`)

const CODEX_PLAN_SCHEMA = {
  type: 'object', required: ['ok', 'concerns'],
  properties: {
    ok: { type: 'boolean' },
    concerns: { type: 'array', items: { type: 'object', required: ['title', 'detail', 'blocking'], properties: {
      title: { type: 'string' }, detail: { type: 'string' }, blocking: { type: 'boolean' } } } },
    suggestions: { type: 'array', items: { type: 'string' } },
  },
}

phase('PlanCheck')
log('opus 帶 codex 審計畫…')
const planCheck = await agent(`${CTX}

你是審查協調者,請透過 Codex(working dir 設 repo root)對計畫做獨立審查,合併 Codex 意見與你的判斷:

計畫(JSON):
${JSON.stringify(plan)}

審查重點:1) steps 指名的 API/寫法是否真的存在且適用;2) task 間檔案是否重疊;3) 有無違反 repo invariant 的步驟;4) 高風險低收益該砍的 task。blocking=true 只留「照做一定出事」的項目。`,
  { label: 'plancheck:opus+codex', phase: 'PlanCheck', model: 'opus', agentType: 'codex:codex-rescue', schema: CODEX_PLAN_SCHEMA })

if (planCheck && planCheck.concerns.length > 0) {
  log(`計畫審查:${planCheck.concerns.filter(c => c.blocking).length} 個阻斷性意見,fable 修訂…`)
  const revised = await agent(`${CTX}

你是總規劃師。計畫經 Codex 審查,意見(JSON):
${JSON.stringify(planCheck)}

原計畫(JSON):
${JSON.stringify(plan)}

修訂:blocking 必須處理(改或刪該 task);非 blocking 自行判斷。維持規劃紀律與輸出格式。`,
    { label: 'plan-revise:fable', phase: 'PlanCheck', model: 'fable', schema: PLAN_SCHEMA })
  if (revised) plan = revised
  log(`修訂後:${plan.tasks.length} 個 task`)
} else {
  log('計畫審查通過')
}

const DEV_SCHEMA = {
  type: 'object', required: ['task_id', 'status', 'summary', 'files_changed'],
  properties: {
    task_id: { type: 'integer' },
    status: { type: 'string', enum: ['done', 'partial', 'skipped'] },
    summary: { type: 'string' },
    files_changed: { type: 'array', items: { type: 'string' } },
    notes: { type: 'string' },
  },
}

phase('Develop')
const devResults = []
for (const t of plan.tasks) {
  log(`實作 task ${t.id}/${plan.tasks.length}:${t.title}`)
  const r = await agent(`${CTX}

你是實作工程師。計畫概要:${plan.overview}
已完成的前置 task(別破壞它們的改動):${JSON.stringify(devResults.map(d => ({ id: d.task_id, summary: d.summary, files: d.files_changed })))}

你的 task(JSON):
${JSON.stringify(t)}

要求:
1. 直接在 working tree 實作,**不要 git commit**、不要開 branch。
2. 嚴格照 steps;指名的 API 不存在或簽名不符時選最接近的官方替代,notes 說明。
3. 只動 files 清單內的檔案(必要的 import/設定微調可例外,notes 註明)。
4. 完成後跑該 task 的 verify(至少讓專案編譯過);修不過才回報 partial。
5. status=skipped 僅限 task 前提不成立。`,
    { label: `dev:task${t.id}`, phase: 'Develop', model: 'sonnet', schema: DEV_SCHEMA })
  devResults.push(r || { task_id: t.id, status: 'skipped', summary: 'agent 失敗/被略過', files_changed: [], notes: '' })
}
log(`開發完成:${devResults.filter(d => d.status === 'done').length} done / ${devResults.filter(d => d.status === 'partial').length} partial / ${devResults.filter(d => d.status === 'skipped').length} skipped`)

const GATE_SCHEMA = {
  type: 'object', required: ['passed', 'log_tail'],
  properties: { passed: { type: 'boolean' }, log_tail: { type: 'string' } },
}

phase('GateTest')
log('跑快速編譯/測試門檻…')
const GATE_PROMPT = `${CTX}

在 repo root 跑「快速驗證」:編譯 + 單元測試(從 AGENTS.md 或 build 設定找對應指令,例如 ./gradlew compileJava test、npm test 等;單一指令可能跑數分鐘,Bash timeout 設 600000)。全綠 → passed=true;失敗 → passed=false,log_tail 貼關鍵錯誤。不要改程式碼。`
let gate = await agent(GATE_PROMPT, { label: 'gate', phase: 'GateTest', schema: GATE_SCHEMA })
if (gate && !gate.passed) {
  log('門檻失敗,sonnet 修復後重驗…')
  await agent(`${CTX}

working tree 有一批未 commit 的改動(git diff 可見),但編譯/測試失敗:
${gate.log_tail}

修到全綠。只修錯誤本身,不回退功能改動,不 git commit。`,
    { label: 'gate-fix', phase: 'GateTest', model: 'sonnet' })
  gate = await agent(GATE_PROMPT, { label: 'gate-retry', phase: 'GateTest', schema: GATE_SCHEMA })
}
if (!gate || !gate.passed) {
  return { status: 'blocked', stage: 'GateTest', detail: gate ? gate.log_tail : 'gate agent 失敗', plan, devResults }
}

const REVIEW_SCHEMA = {
  type: 'object', required: ['findings'],
  properties: {
    findings: { type: 'array', items: { type: 'object', required: ['title', 'file', 'severity', 'detail'], properties: {
      title: { type: 'string' }, file: { type: 'string' }, line: { type: 'integer' },
      severity: { type: 'string', enum: ['critical', 'major', 'minor', 'nit'] },
      detail: { type: 'string' }, suggestion: { type: 'string' } } } },
  },
}

const REVIEW_BRIEF = `審查對象:working tree 未 commit 的改動。取得方式:git diff(已修改檔)+ git status --porcelain 找 untracked 新檔直接讀。
改動意圖(計畫概要+各 task 摘要):`

phase('Review')
log('opus 直審 + opus 帶 codex,雙軌 code-review…')
const reviewCtx = JSON.stringify({ overview: plan.overview, tasks: devResults })
const reviews = (await parallel([
  () => agent(`${CTX}

你是資深 reviewer(獨立審查)。${REVIEW_BRIEF}
${reviewCtx}

審查重點依序:1) 正確性(執行緒、NPE、資源洩漏、狀態競爭);2) repo invariant 是否被動到;3) 相依/平台相容性;4) 效能(是否真的更好、有無新開銷);5) 一致性。只報真問題,nit 少報。`,
    { label: 'review:opus', phase: 'Review', model: 'opus', schema: REVIEW_SCHEMA }),
  () => agent(`${CTX}

你是審查協調者,請透過 Codex(working dir 設 repo root)對 working tree 未 commit 改動做獨立 code-review,整理 Codex 發現成結構化 findings(可補你的判斷、去掉誤報)。${REVIEW_BRIEF}
${reviewCtx}

請 Codex 特別看:正確性、invariant、相容性、效能、以及改動是否真的落實計畫意圖。只報真問題。`,
    { label: 'review:codex', phase: 'Review', model: 'opus', agentType: 'codex:codex-rescue', schema: REVIEW_SCHEMA }),
])).filter(Boolean)
let findings = reviews.flatMap((r, i) => r.findings.map(f => ({ ...f, origin: i === 0 ? 'opus' : 'codex' })))
log(`雙軌審查:共 ${findings.length} 條 findings`)

const TRIAGE_SCHEMA = {
  type: 'object', required: ['fixes', 'rejected'],
  properties: {
    fixes: { type: 'array', items: { type: 'object', required: ['title', 'instruction', 'files'], properties: {
      title: { type: 'string' }, instruction: { type: 'string' },
      files: { type: 'array', items: { type: 'string' } }, origin: { type: 'string' } } } },
    rejected: { type: 'array', items: { type: 'object', required: ['title', 'reason'], properties: {
      title: { type: 'string' }, reason: { type: 'string' } } } },
  },
}

let round = 0
const fixHistory = []
while (round < 2 && findings.length > 0) {
  round++
  phase('Triage')
  log(`第 ${round} 輪:fable 裁決 ${findings.length} 條 findings…`)
  const triage = await agent(`${CTX}

你是總裁決者。兩路 code-review 的 findings(JSON,含 origin):
${JSON.stringify(findings)}

改動意圖:${plan.overview}

逐條驗證後裁決(可疑的先自己看 code 確認):
- fixes:必須修(真 bug、invariant 違反、明確相容性/效能問題)。instruction 寫到實作者不用再判斷;同根因合併成一條。
- rejected:誤報、過度設計、成本>收益的 nit,附理由。
兩位 reviewer 衝突時以你驗證結果為準。`,
    { label: `triage:r${round}`, phase: 'Triage', model: 'fable', schema: TRIAGE_SCHEMA })
  if (!triage) break
  log(`裁決:修 ${triage.fixes.length} 條、駁回 ${triage.rejected.length} 條`)
  fixHistory.push(triage)
  if (triage.fixes.length === 0) { findings = []; break }

  phase('Fix')
  for (let i = 0; i < triage.fixes.length; i += 5) {
    const chunk = triage.fixes.slice(i, i + 5)
    log(`第 ${round} 輪修正:第 ${i + 1}-${i + chunk.length} 條…`)
    await agent(`${CTX}

你是修正工程師。對 working tree(不要 git commit)套用以下修正,instruction 是裁決者驗證過的定案,照做;若與現況矛盾,選最小且不破壞 invariant 的修法並回報:
${JSON.stringify(chunk)}

修完確認專案編譯過,回報每條處理結果。`,
      { label: `fix:r${round}:${i / 5 + 1}`, phase: 'Fix', model: 'sonnet' })
  }

  phase('ReReview')
  log(`第 ${round} 輪複審…`)
  const reReviews = (await parallel([
    () => agent(`${CTX}

複審(第 ${round} 輪):working tree 已依下列裁決修正:
${JSON.stringify(triage.fixes)}

用 git diff + 讀檔確認:1) 每條修正落實且修對;2) 沒引入新問題。只回報「仍未修好或新引入」的問題,別重報已駁回的 nit。`,
      { label: `rereview:opus:r${round}`, phase: 'ReReview', model: 'opus', schema: REVIEW_SCHEMA }),
    () => agent(`${CTX}

你是審查協調者,請透過 Codex(working dir 設 repo root)複審 working tree:先前問題已依以下裁決修正:
${JSON.stringify(triage.fixes)}

請 Codex 確認修正落實、無新問題;只留仍存在或新引入的真問題。`,
      { label: `rereview:codex:r${round}`, phase: 'ReReview', model: 'opus', agentType: 'codex:codex-rescue', schema: REVIEW_SCHEMA }),
  ])).filter(Boolean)
  findings = reReviews.flatMap((r, i) => r.findings.map(f => ({ ...f, origin: i === 0 ? 'opus' : 'codex' })))
  log(`複審結果:剩 ${findings.length} 條`)
}

phase('FinalVerify')
log('跑完整驗證鏈…')
const FINAL_SCHEMA = {
  type: 'object', required: ['passed', 'detail'],
  properties: { passed: { type: 'boolean' }, detail: { type: 'string' } },
}
const FINAL_PROMPT = `${CTX}

在 repo root 跑「完整驗證鏈」:以 AGENTS.md 規定的為準(例如本 repo 若是 leetcode-editor 就是 pkill -9 -f ideaIU || true 後 ./gradlew test buildPlugin verifyPlugin,headless 顯示問題可設 DISPLAY=:99);AGENTS.md 沒規定就跑 build+test 全套。每步可能很久:Bash timeout 600000,必要時 run_in_background 輪詢。全綠 → passed=true;任一步紅 → passed=false,detail 貼關鍵錯誤。不要改程式碼、不要 git commit。`
let finalVerify = await agent(FINAL_PROMPT, { label: 'final:full-chain', phase: 'FinalVerify', schema: FINAL_SCHEMA })
if (finalVerify && !finalVerify.passed) {
  log('完整鏈失敗,sonnet 修復後重驗…')
  await agent(`${CTX}

完整驗證鏈失敗:
${finalVerify.detail}

修到能過(只修錯誤本身,不回退功能,不 git commit),修完重跑失敗那步確認。`,
    { label: 'final-fix', phase: 'FinalVerify', model: 'sonnet' })
  finalVerify = await agent(FINAL_PROMPT, { label: 'final-retry', phase: 'FinalVerify', schema: FINAL_SCHEMA })
}

return {
  plan_overview: plan.overview,
  tasks: devResults,
  review_rounds: fixHistory.map((t, i) => ({ round: i + 1, fixed: t.fixes.length, rejected: t.rejected.length })),
  remaining_findings: findings,
  final_verify: finalVerify,
}
