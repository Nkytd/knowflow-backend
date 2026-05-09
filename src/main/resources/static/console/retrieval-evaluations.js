const RetrievalEvalCommon = window.KnowFlowConsoleCommon;

const retrievalEvalState = {
    token: RetrievalEvalCommon.readToken(),
    query: RetrievalEvalCommon.parseQuery(),
    user: null,
    knowledgeBases: [],
    cases: [],
    runs: [],
    results: [],
    selectedRunId: null,
    casePageNo: 1,
    casePageSize: 8,
    caseTotal: 0,
    runPageNo: 1,
    runPageSize: 8,
    runTotal: 0,
    filters: {
        knowledgeBaseId: "",
        keyword: "",
        enabled: ""
    }
};

const retrievalEvalElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    cacheRetrievalEvalElements();
    initRetrievalEvalStateFromQuery();
    bindRetrievalEvalEvents();
    if (retrievalEvalState.token) {
        await restoreRetrievalEvalSession();
        return;
    }
    showRetrievalEvalLoggedOutState();
});

function cacheRetrievalEvalElements() {
    [
        "statusBanner",
        "loginPanel",
        "workspace",
        "loginForm",
        "usernameInput",
        "passwordInput",
        "loginButton",
        "sessionName",
        "sessionMeta",
        "refreshButton",
        "logoutButton",
        "knowledgeBaseFilter",
        "keywordFilter",
        "enabledFilter",
        "runTopKInput",
        "searchButton",
        "runEvaluationButton",
        "resetButton",
        "metricPassRate",
        "metricPassMeta",
        "metricRecallAtK",
        "metricTop1HitRate",
        "metricNoHitAccuracy",
        "casePageMeta",
        "caseTable",
        "casePaginationText",
        "prevCasePageButton",
        "nextCasePageButton",
        "caseForm",
        "caseKnowledgeBaseSelect",
        "caseExpectedStatusSelect",
        "caseNameInput",
        "caseQuestionInput",
        "caseExpectedDocumentIdInput",
        "caseTopKInput",
        "caseKeywordsInput",
        "caseRemarkInput",
        "createCaseButton",
        "runPageMeta",
        "runTable",
        "resultMeta",
        "resultBody"
    ].forEach((id) => {
        retrievalEvalElements[id] = document.getElementById(id);
    });
}

function initRetrievalEvalStateFromQuery() {
    const query = retrievalEvalState.query;
    retrievalEvalState.casePageNo = Number(query.casePageNo || 1);
    retrievalEvalState.selectedRunId = query.runId ? Number(query.runId) : null;
    retrievalEvalState.filters.knowledgeBaseId = query.knowledgeBaseId || "";
    retrievalEvalState.filters.keyword = query.keyword || "";
    retrievalEvalState.filters.enabled = query.enabled || "";
    applyRetrievalEvalFilters();
}

function bindRetrievalEvalEvents() {
    retrievalEvalElements.loginForm.addEventListener("submit", handleRetrievalEvalLogin);
    retrievalEvalElements.refreshButton.addEventListener("click", () => loadRetrievalEvalConsole(true));
    retrievalEvalElements.logoutButton.addEventListener("click", logoutRetrievalEvalConsole);
    retrievalEvalElements.searchButton.addEventListener("click", async () => {
        readRetrievalEvalFilters();
        retrievalEvalState.casePageNo = 1;
        await loadRetrievalEvalConsole(false);
    });
    retrievalEvalElements.resetButton.addEventListener("click", async () => {
        retrievalEvalState.filters = { knowledgeBaseId: "", keyword: "", enabled: "" };
        retrievalEvalState.casePageNo = 1;
        retrievalEvalState.selectedRunId = null;
        retrievalEvalElements.runTopKInput.value = "";
        applyRetrievalEvalFilters();
        await loadRetrievalEvalConsole(false);
    });
    retrievalEvalElements.prevCasePageButton.addEventListener("click", async () => {
        if (retrievalEvalState.casePageNo <= 1) {
            return;
        }
        retrievalEvalState.casePageNo -= 1;
        await loadRetrievalEvalConsole(true);
    });
    retrievalEvalElements.nextCasePageButton.addEventListener("click", async () => {
        const maxPage = Math.max(1, Math.ceil(retrievalEvalState.caseTotal / retrievalEvalState.casePageSize));
        if (retrievalEvalState.casePageNo >= maxPage) {
            return;
        }
        retrievalEvalState.casePageNo += 1;
        await loadRetrievalEvalConsole(true);
    });
    retrievalEvalElements.runEvaluationButton.addEventListener("click", runRetrievalEvaluation);
    retrievalEvalElements.caseForm.addEventListener("submit", createRetrievalEvalCase);
}

async function restoreRetrievalEvalSession() {
    try {
        const [user, knowledgeBasePage] = await Promise.all([
            RetrievalEvalCommon.api("/api/v1/auth/me", { token: retrievalEvalState.token }),
            RetrievalEvalCommon.api("/api/v1/admin/knowledge-bases?pageNo=1&pageSize=200", { token: retrievalEvalState.token })
        ]);
        retrievalEvalState.user = user;
        retrievalEvalState.knowledgeBases = knowledgeBasePage.records || [];
        renderKnowledgeBaseOptions();
        showRetrievalEvalLoggedInState();
        await loadRetrievalEvalConsole(true);
    } catch (error) {
        handleRetrievalEvalAuthFailure(error, "登录状态已失效，请重新登录。");
    }
}

async function handleRetrievalEvalLogin(event) {
    event.preventDefault();
    const username = retrievalEvalElements.usernameInput.value.trim();
    const password = retrievalEvalElements.passwordInput.value;
    if (!username || !password) {
        setRetrievalEvalBanner("请输入账号和密码。", "error");
        return;
    }

    retrievalEvalElements.loginButton.disabled = true;
    setRetrievalEvalBanner("正在登录检索评估台...", "info");
    try {
        const data = await RetrievalEvalCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        retrievalEvalState.token = data.token;
        RetrievalEvalCommon.saveToken(data.token);
        retrievalEvalElements.passwordInput.value = "";
        await restoreRetrievalEvalSession();
        setRetrievalEvalBanner("检索评估台已就绪。", "success");
    } catch (error) {
        setRetrievalEvalBanner(error.message || "登录失败。", "error");
    } finally {
        retrievalEvalElements.loginButton.disabled = false;
    }
}

function logoutRetrievalEvalConsole() {
    retrievalEvalState.token = "";
    retrievalEvalState.user = null;
    retrievalEvalState.cases = [];
    retrievalEvalState.runs = [];
    retrievalEvalState.results = [];
    retrievalEvalState.selectedRunId = null;
    RetrievalEvalCommon.clearToken();
    showRetrievalEvalLoggedOutState();
    setRetrievalEvalBanner("已退出检索评估台。", "success");
}

function showRetrievalEvalLoggedOutState() {
    retrievalEvalElements.loginPanel.hidden = false;
    retrievalEvalElements.workspace.hidden = true;
    retrievalEvalElements.sessionName.textContent = "未登录";
    retrievalEvalElements.sessionMeta.textContent = "请先登录后管理检索评估。";
}

function showRetrievalEvalLoggedInState() {
    retrievalEvalElements.loginPanel.hidden = true;
    retrievalEvalElements.workspace.hidden = false;
    retrievalEvalElements.sessionName.textContent = retrievalEvalState.user.realName || retrievalEvalState.user.username;
    retrievalEvalElements.sessionMeta.textContent = RetrievalEvalCommon.formatUserSessionMeta(retrievalEvalState.user);
}

async function loadRetrievalEvalConsole(keepSelection) {
    if (!retrievalEvalState.token) {
        showRetrievalEvalLoggedOutState();
        return;
    }

    renderCasesLoading();
    renderRunsLoading();
    syncRetrievalEvalQuery();
    try {
        const [casePage, runPage] = await Promise.all([
            RetrievalEvalCommon.api(buildCaseListUrl(), { token: retrievalEvalState.token }),
            RetrievalEvalCommon.api(buildRunListUrl(), { token: retrievalEvalState.token })
        ]);
        retrievalEvalState.cases = casePage.records || [];
        retrievalEvalState.caseTotal = casePage.total || 0;
        retrievalEvalState.casePageNo = casePage.pageNo || retrievalEvalState.casePageNo;
        retrievalEvalState.casePageSize = casePage.pageSize || retrievalEvalState.casePageSize;
        retrievalEvalState.runs = runPage.records || [];
        retrievalEvalState.runTotal = runPage.total || 0;
        renderCaseList();
        renderRunList();

        const preferredRunId = resolvePreferredRunId(keepSelection);
        if (preferredRunId) {
            await loadRunResults(preferredRunId);
        } else {
            retrievalEvalState.results = [];
            renderRunMetrics(retrievalEvalState.runs[0] || null);
            renderResultDetail();
        }
    } catch (error) {
        if (error instanceof RetrievalEvalCommon.AuthExpiredError) {
            handleRetrievalEvalAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        retrievalEvalElements.caseTable.innerHTML = RetrievalEvalCommon.renderStateBlock({
            type: "error",
            title: "评估样本加载失败",
            message: error.message || "请稍后重试。"
        });
        retrievalEvalElements.runTable.innerHTML = RetrievalEvalCommon.renderStateBlock({
            type: "error",
            title: "运行记录加载失败",
            message: error.message || "请稍后重试。"
        });
        setRetrievalEvalBanner(error.message || "检索评估加载失败。", "error");
    }
}

function buildCaseListUrl() {
    const params = new URLSearchParams({
        pageNo: String(retrievalEvalState.casePageNo),
        pageSize: String(retrievalEvalState.casePageSize)
    });
    if (retrievalEvalState.filters.knowledgeBaseId) {
        params.set("knowledgeBaseId", retrievalEvalState.filters.knowledgeBaseId);
    }
    if (retrievalEvalState.filters.keyword) {
        params.set("keyword", retrievalEvalState.filters.keyword);
    }
    if (retrievalEvalState.filters.enabled) {
        params.set("enabled", retrievalEvalState.filters.enabled);
    }
    return `/api/v1/admin/retrieval-evaluations/cases?${params.toString()}`;
}

function buildRunListUrl() {
    const params = new URLSearchParams({
        pageNo: String(retrievalEvalState.runPageNo),
        pageSize: String(retrievalEvalState.runPageSize)
    });
    if (retrievalEvalState.filters.knowledgeBaseId) {
        params.set("knowledgeBaseId", retrievalEvalState.filters.knowledgeBaseId);
    }
    return `/api/v1/admin/retrieval-evaluations/runs?${params.toString()}`;
}

function renderCasesLoading() {
    retrievalEvalElements.casePageMeta.textContent = "正在加载评估样本";
    retrievalEvalElements.caseTable.innerHTML = RetrievalEvalCommon.renderStateBlock({
        type: "loading",
        title: "正在同步评估集",
        message: "正在读取固定问题、预期状态、关键词和目标文档。"
    });
}

function renderRunsLoading() {
    retrievalEvalElements.runPageMeta.textContent = "正在加载运行记录";
    retrievalEvalElements.runTable.innerHTML = RetrievalEvalCommon.renderStateBlock({
        type: "loading",
        title: "正在同步运行记录",
        message: "正在读取最近的检索评估结果。"
    });
}

function renderCaseList() {
    const totalPage = Math.max(1, Math.ceil(retrievalEvalState.caseTotal / retrievalEvalState.casePageSize));
    retrievalEvalElements.casePageMeta.textContent = `共 ${RetrievalEvalCommon.formatNumber(retrievalEvalState.caseTotal)} 条样本`;
    retrievalEvalElements.casePaginationText.textContent = `第 ${retrievalEvalState.casePageNo} / ${totalPage} 页`;
    retrievalEvalElements.prevCasePageButton.disabled = retrievalEvalState.casePageNo <= 1;
    retrievalEvalElements.nextCasePageButton.disabled = retrievalEvalState.casePageNo >= totalPage;

    if (!retrievalEvalState.cases.length) {
        retrievalEvalElements.caseTable.innerHTML = RetrievalEvalCommon.renderStateBlock({
            title: "暂无评估样本",
            message: "先新增几条典型问题，再运行检索评估。"
        });
        return;
    }

    retrievalEvalElements.caseTable.innerHTML = `
        <table class="data-table">
            <thead>
            <tr>
                <th>样本</th>
                <th>预期</th>
                <th>关键词</th>
                <th>TopK</th>
                <th>状态</th>
            </tr>
            </thead>
            <tbody>
            ${retrievalEvalState.cases.map((evalCase) => `
                <tr>
                    <td>
                        <div class="table-question">
                            <strong>${RetrievalEvalCommon.escapeHtml(evalCase.caseName || `Case #${evalCase.id}`)}</strong>
                            <div class="meta-line">${RetrievalEvalCommon.escapeHtml(evalCase.questionText || "暂无问题")}</div>
                            <div class="tiny-line">${RetrievalEvalCommon.escapeHtml(evalCase.knowledgeBaseName || `知识库 #${evalCase.knowledgeBaseId}`)}</div>
                        </div>
                    </td>
                    <td>
                        <div class="chips">
                            <span class="status-pill ${RetrievalEvalCommon.statusClass(evalCase.expectedStatus)}">${RetrievalEvalCommon.escapeHtml(RetrievalEvalCommon.enumLabel("answerStatus", evalCase.expectedStatus, evalCase.expectedStatus || "未知"))}</span>
                        </div>
                        <div class="tiny-line">${RetrievalEvalCommon.escapeHtml(evalCase.expectedDocumentName || (evalCase.expectedDocumentId ? `文档 #${evalCase.expectedDocumentId}` : "不限定文档"))}</div>
                    </td>
                    <td>${renderKeywordChips(evalCase.expectedKeywords)}</td>
                    <td>${RetrievalEvalCommon.escapeHtml(evalCase.topK || 5)}</td>
                    <td><span class="status-pill ${evalCase.enabled ? "status-success" : "status-warn"}">${evalCase.enabled ? "启用" : "停用"}</span></td>
                </tr>
            `).join("")}
            </tbody>
        </table>
    `;
}

function renderRunList() {
    retrievalEvalElements.runPageMeta.textContent = `共 ${RetrievalEvalCommon.formatNumber(retrievalEvalState.runTotal)} 次运行`;
    if (!retrievalEvalState.runs.length) {
        retrievalEvalElements.runTable.innerHTML = RetrievalEvalCommon.renderStateBlock({
            title: "暂无评估运行",
            message: "点击“运行评估”后，这里会展示通过率和召回指标。"
        });
        return;
    }

    retrievalEvalElements.runTable.innerHTML = `
        <table class="data-table">
            <thead>
            <tr>
                <th>运行编号</th>
                <th>样本</th>
                <th>通过率</th>
                <th>Recall@K / Top1</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody>
            ${retrievalEvalState.runs.map((run) => `
                <tr class="${run.id === retrievalEvalState.selectedRunId ? "active-row" : ""}">
                    <td>
                        <strong>${RetrievalEvalCommon.escapeHtml(run.runNo || `Run #${run.id}`)}</strong>
                        <div class="tiny-line">${RetrievalEvalCommon.escapeHtml(run.knowledgeBaseName || "全部知识库")}</div>
                        <div class="tiny-line">${RetrievalEvalCommon.escapeHtml(RetrievalEvalCommon.formatDateTime(run.finishedAt || run.createdAt))}</div>
                    </td>
                    <td>${RetrievalEvalCommon.escapeHtml(`${run.passedCases || 0}/${run.totalCases || 0}`)}</td>
                    <td><span class="status-pill ${resolveMetricClass(run.passRate)}">${formatMetric(run.passRate)}</span></td>
                    <td>
                        <div class="meta-line">${formatMetric(run.recallAtK)} / ${formatMetric(run.top1HitRate)}</div>
                        <div class="tiny-line">No-hit ${formatMetric(run.noHitAccuracy)}</div>
                    </td>
                    <td><button class="link-button" type="button" data-run-id="${run.id}">查看结果</button></td>
                </tr>
            `).join("")}
            </tbody>
        </table>
    `;
    retrievalEvalElements.runTable.querySelectorAll("[data-run-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await loadRunResults(Number(button.dataset.runId));
        });
    });
}

function renderRunMetrics(run) {
    retrievalEvalElements.metricPassRate.textContent = formatMetric(run?.passRate);
    retrievalEvalElements.metricRecallAtK.textContent = formatMetric(run?.recallAtK);
    retrievalEvalElements.metricTop1HitRate.textContent = formatMetric(run?.top1HitRate);
    retrievalEvalElements.metricNoHitAccuracy.textContent = formatMetric(run?.noHitAccuracy);
    retrievalEvalElements.metricPassMeta.textContent = run
        ? `${run.runNo || `Run #${run.id}`} · ${run.passedCases || 0}/${run.totalCases || 0} 通过`
        : "暂无运行记录";
}

async function loadRunResults(runId) {
    retrievalEvalState.selectedRunId = runId;
    syncRetrievalEvalQuery();
    renderRunList();
    retrievalEvalElements.resultBody.innerHTML = RetrievalEvalCommon.renderStateBlock({
        type: "loading",
        title: "正在加载结果明细",
        message: "正在读取每个 case 的命中状态、分数、证据片段和 query variants。"
    });
    try {
        const [run, results] = await Promise.all([
            RetrievalEvalCommon.api(`/api/v1/admin/retrieval-evaluations/runs/${runId}`, { token: retrievalEvalState.token }),
            RetrievalEvalCommon.api(`/api/v1/admin/retrieval-evaluations/runs/${runId}/results`, { token: retrievalEvalState.token })
        ]);
        retrievalEvalState.results = results || [];
        renderRunMetrics(run);
        renderResultDetail(run);
    } catch (error) {
        if (error instanceof RetrievalEvalCommon.AuthExpiredError) {
            handleRetrievalEvalAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        retrievalEvalElements.resultBody.innerHTML = RetrievalEvalCommon.renderStateBlock({
            type: "error",
            title: "结果明细加载失败",
            message: error.message || "请稍后重试。"
        });
        setRetrievalEvalBanner(error.message || "结果明细加载失败。", "error");
    }
}

function renderResultDetail(run) {
    if (!retrievalEvalState.results.length) {
        retrievalEvalElements.resultMeta.textContent = "选择一轮运行查看明细";
        retrievalEvalElements.resultBody.innerHTML = RetrievalEvalCommon.renderStateBlock({
            title: "暂无结果明细",
            message: "运行评估后，这里会展示每条 case 的召回证据。"
        });
        return;
    }
    retrievalEvalElements.resultMeta.textContent = `${run?.runNo || "当前运行"} · ${retrievalEvalState.results.length} 条结果`;
    retrievalEvalElements.resultBody.innerHTML = retrievalEvalState.results.map((result) => `
        <article class="eval-result-card ${result.passed ? "eval-result-pass" : "eval-result-fail"}">
            <div class="eval-result-header">
                <div>
                    <strong>${RetrievalEvalCommon.escapeHtml(result.questionText || `Case #${result.caseId}`)}</strong>
                    <div class="tiny-line">
                        预期 ${RetrievalEvalCommon.escapeHtml(RetrievalEvalCommon.enumLabel("answerStatus", result.expectedStatus, result.expectedStatus || "-"))}
                        · 实际 ${RetrievalEvalCommon.escapeHtml(RetrievalEvalCommon.enumLabel("answerStatus", result.actualStatus, result.actualStatus || "-"))}
                    </div>
                </div>
                <span class="status-pill ${result.passed ? "status-success" : "status-danger"}">${result.passed ? "通过" : "失败"}</span>
            </div>
            <div class="eval-score-grid">
                ${renderEvalMetric("Top 分", formatScore(result.topRecallScore))}
                ${renderEvalMetric("Lexical", formatScore(result.topLexicalScore))}
                ${renderEvalMetric("Vector", formatScore(result.topVectorScore))}
                ${renderEvalMetric("命中排名", result.hitRank ? `#${result.hitRank}` : "未命中")}
                ${renderEvalMetric("关键词", `${result.keywordHitCount || 0}/${result.keywordTotalCount || 0}`)}
                ${renderEvalMetric("Top 文档", result.actualTopDocumentName || "无")}
            </div>
            ${result.failureReason ? `<p class="eval-failure">${RetrievalEvalCommon.escapeHtml(result.failureReason)}</p>` : ""}
            ${renderEvalHits(result.hits)}
            ${renderQueryVariants(result.queryVariants)}
        </article>
    `).join("");
}

function renderEvalHits(hits) {
    if (!Array.isArray(hits) || !hits.length) {
        return RetrievalEvalCommon.renderStateBlock({
            compact: true,
            title: "暂无召回证据",
            message: "当前 case 没有达到可靠召回阈值的片段。"
        });
    }
    return `
        <div class="eval-hit-list">
            ${hits.slice(0, 3).map((hit) => `
                <article class="eval-hit-item">
                    <strong>#${RetrievalEvalCommon.escapeHtml(hit.rankNo || "-")} ${RetrievalEvalCommon.escapeHtml(hit.documentName || `文档 #${hit.documentId || "-"}`)}</strong>
                    <div class="tiny-line">score ${formatScore(hit.recallScore)} · lexical ${formatScore(hit.lexicalScore)} · vector ${formatScore(hit.vectorScore)}</div>
                    <p>${RetrievalEvalCommon.escapeHtml(RetrievalEvalCommon.truncateText(hit.snippetText || "", 220))}</p>
                </article>
            `).join("")}
        </div>
    `;
}

function renderQueryVariants(variants) {
    if (!Array.isArray(variants) || !variants.length) {
        return "";
    }
    return `
        <div class="eval-variant-row">
            ${variants.slice(0, 5).map((variant) => `
                <span>${RetrievalEvalCommon.escapeHtml(variant.queryText || variant.text || JSON.stringify(variant))}</span>
            `).join("")}
        </div>
    `;
}

async function runRetrievalEvaluation() {
    readRetrievalEvalFilters();
    const payload = {};
    if (retrievalEvalState.filters.knowledgeBaseId) {
        payload.knowledgeBaseId = Number(retrievalEvalState.filters.knowledgeBaseId);
    }
    const topK = Number(retrievalEvalElements.runTopKInput.value);
    if (Number.isFinite(topK) && topK > 0) {
        payload.topK = topK;
    }
    retrievalEvalElements.runEvaluationButton.disabled = true;
    setRetrievalEvalBanner("正在运行检索评估，请稍候。", "info");
    try {
        const run = await RetrievalEvalCommon.api("/api/v1/admin/retrieval-evaluations/runs", {
            token: retrievalEvalState.token,
            method: "POST",
            body: JSON.stringify(payload)
        });
        retrievalEvalState.selectedRunId = run.id;
        setRetrievalEvalBanner(`评估完成，通过率 ${formatMetric(run.passRate)}。`, "success");
        await loadRetrievalEvalConsole(true);
    } catch (error) {
        if (error instanceof RetrievalEvalCommon.AuthExpiredError) {
            handleRetrievalEvalAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        setRetrievalEvalBanner(error.message || "运行评估失败。", "error");
    } finally {
        retrievalEvalElements.runEvaluationButton.disabled = false;
    }
}

async function createRetrievalEvalCase(event) {
    event.preventDefault();
    const knowledgeBaseId = retrievalEvalElements.caseKnowledgeBaseSelect.value;
    const caseName = retrievalEvalElements.caseNameInput.value.trim();
    const questionText = retrievalEvalElements.caseQuestionInput.value.trim();
    if (!knowledgeBaseId || !caseName || !questionText) {
        setRetrievalEvalBanner("请填写知识库、样本名称和问题。", "error");
        return;
    }

    const expectedDocumentId = Number(retrievalEvalElements.caseExpectedDocumentIdInput.value);
    const topK = Number(retrievalEvalElements.caseTopKInput.value || 5);
    const payload = {
        knowledgeBaseId: Number(knowledgeBaseId),
        caseName,
        questionText,
        expectedStatus: retrievalEvalElements.caseExpectedStatusSelect.value || "SUCCESS",
        expectedKeywords: parseKeywordInput(retrievalEvalElements.caseKeywordsInput.value),
        topK: Number.isFinite(topK) && topK > 0 ? topK : 5,
        enabled: true,
        remark: retrievalEvalElements.caseRemarkInput.value.trim()
    };
    if (Number.isFinite(expectedDocumentId) && expectedDocumentId > 0) {
        payload.expectedDocumentId = expectedDocumentId;
    }

    retrievalEvalElements.createCaseButton.disabled = true;
    try {
        await RetrievalEvalCommon.api("/api/v1/admin/retrieval-evaluations/cases", {
            token: retrievalEvalState.token,
            method: "POST",
            body: JSON.stringify(payload)
        });
        retrievalEvalElements.caseForm.reset();
        retrievalEvalElements.caseTopKInput.value = "5";
        if (retrievalEvalState.filters.knowledgeBaseId) {
            retrievalEvalElements.caseKnowledgeBaseSelect.value = retrievalEvalState.filters.knowledgeBaseId;
        }
        setRetrievalEvalBanner("评估样本已创建。", "success");
        retrievalEvalState.casePageNo = 1;
        await loadRetrievalEvalConsole(true);
    } catch (error) {
        if (error instanceof RetrievalEvalCommon.AuthExpiredError) {
            handleRetrievalEvalAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        setRetrievalEvalBanner(error.message || "创建评估样本失败。", "error");
    } finally {
        retrievalEvalElements.createCaseButton.disabled = false;
    }
}

function renderKnowledgeBaseOptions() {
    const filterValue = retrievalEvalState.filters.knowledgeBaseId || "";
    const options = `
        <option value="">全部知识库</option>
        ${retrievalEvalState.knowledgeBases.map((kb) => `
            <option value="${kb.id}" ${String(filterValue) === String(kb.id) ? "selected" : ""}>
                ${RetrievalEvalCommon.escapeHtml(kb.kbName)} (${RetrievalEvalCommon.escapeHtml(RetrievalEvalCommon.enumLabel("knowledgeBaseStatus", kb.status, kb.status || "未知"))})
            </option>
        `).join("")}
    `;
    retrievalEvalElements.knowledgeBaseFilter.innerHTML = options;
    retrievalEvalElements.caseKnowledgeBaseSelect.innerHTML = options.replace('<option value="">全部知识库</option>', '<option value="">请选择知识库</option>');
    if (filterValue) {
        retrievalEvalElements.caseKnowledgeBaseSelect.value = filterValue;
    }
}

function applyRetrievalEvalFilters() {
    if (!retrievalEvalElements.knowledgeBaseFilter) {
        return;
    }
    retrievalEvalElements.knowledgeBaseFilter.value = retrievalEvalState.filters.knowledgeBaseId;
    retrievalEvalElements.keywordFilter.value = retrievalEvalState.filters.keyword;
    retrievalEvalElements.enabledFilter.value = retrievalEvalState.filters.enabled;
}

function readRetrievalEvalFilters() {
    retrievalEvalState.filters.knowledgeBaseId = retrievalEvalElements.knowledgeBaseFilter.value;
    retrievalEvalState.filters.keyword = retrievalEvalElements.keywordFilter.value.trim();
    retrievalEvalState.filters.enabled = retrievalEvalElements.enabledFilter.value;
}

function syncRetrievalEvalQuery() {
    RetrievalEvalCommon.updateQuery({
        casePageNo: retrievalEvalState.casePageNo,
        knowledgeBaseId: retrievalEvalState.filters.knowledgeBaseId,
        keyword: retrievalEvalState.filters.keyword,
        enabled: retrievalEvalState.filters.enabled,
        runId: retrievalEvalState.selectedRunId
    });
}

function resolvePreferredRunId(keepSelection) {
    if (keepSelection && retrievalEvalState.selectedRunId) {
        return retrievalEvalState.selectedRunId;
    }
    if (retrievalEvalState.query.runId) {
        return Number(retrievalEvalState.query.runId);
    }
    return retrievalEvalState.runs[0]?.id || null;
}

function renderKeywordChips(keywords) {
    if (!Array.isArray(keywords) || !keywords.length) {
        return `<span class="tiny-line">未设置</span>`;
    }
    return `<div class="chips">${keywords.map((keyword) => `<span class="status-pill">${RetrievalEvalCommon.escapeHtml(keyword)}</span>`).join("")}</div>`;
}

function renderEvalMetric(label, value) {
    return `
        <div class="sla-metric">
            <span>${RetrievalEvalCommon.escapeHtml(label)}</span>
            <strong>${RetrievalEvalCommon.escapeHtml(value)}</strong>
        </div>
    `;
}

function parseKeywordInput(value) {
    return String(value || "")
        .split(/[,，]/)
        .map((keyword) => keyword.trim())
        .filter(Boolean);
}

function formatMetric(value) {
    if (value === undefined || value === null || value === "") {
        return "--";
    }
    return `${Math.round(Number(value || 0) * 100)}%`;
}

function formatScore(value) {
    if (value === undefined || value === null || value === "") {
        return "--";
    }
    return Number(value || 0).toFixed(3).replace(/0+$/, "").replace(/\.$/, "");
}

function resolveMetricClass(value) {
    const number = Number(value || 0);
    if (number >= 0.8) {
        return "status-success";
    }
    if (number >= 0.6) {
        return "status-warn";
    }
    return "status-danger";
}

function handleRetrievalEvalAuthFailure(error, message) {
    console.error(error);
    logoutRetrievalEvalConsole();
    setRetrievalEvalBanner(message, "error");
}

function setRetrievalEvalBanner(message, type = "info") {
    retrievalEvalElements.statusBanner.hidden = false;
    retrievalEvalElements.statusBanner.className = `status-banner ${type}`;
    retrievalEvalElements.statusBanner.textContent = message;
}
