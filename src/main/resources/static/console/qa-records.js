const QaCommon = window.KnowFlowConsoleCommon;

const qaState = {
    token: QaCommon.readToken(),
    query: QaCommon.parseQuery(),
    user: null,
    menus: [],
    knowledgeBases: [],
    records: [],
    sources: [],
    retrievalDebug: null,
    selectedRecord: null,
    selectedRecordId: null,
    pageNo: 1,
    pageSize: 10,
    total: 0,
    filters: {
        keyword: "",
        knowledgeBaseId: "",
        answerStatus: "",
        needHumanHandoff: "",
        sessionId: ""
    }
};

const qaElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    cacheQaElements();
    initQaStateFromQuery();
    bindQaEvents();
    if (qaState.token) {
        await restoreQaSession();
        return;
    }
    showQaLoggedOutState();
});

function cacheQaElements() {
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
        "keywordFilter",
        "knowledgeBaseFilter",
        "answerStatusFilter",
        "handoffFilter",
        "sessionIdFilter",
        "searchButton",
        "resetButton",
        "contextBanner",
        "qaPageMeta",
        "qaTable",
        "qaPaginationText",
        "prevPageButton",
        "nextPageButton",
        "qaDetailBody"
    ].forEach((id) => {
        qaElements[id] = document.getElementById(id);
    });
}

function initQaStateFromQuery() {
    const query = qaState.query;
    qaState.pageNo = Number(query.pageNo || 1);
    qaState.selectedRecordId = query.messageId ? Number(query.messageId) : null;
    qaState.filters.keyword = query.keyword || "";
    qaState.filters.knowledgeBaseId = query.knowledgeBaseId || "";
    qaState.filters.answerStatus = query.answerStatus || "";
    qaState.filters.needHumanHandoff = query.needHumanHandoff || "";
    qaState.filters.sessionId = query.sessionId || "";
    applyQaFilterInputs();
}

function bindQaEvents() {
    qaElements.loginForm.addEventListener("submit", handleQaLogin);
    qaElements.refreshButton.addEventListener("click", () => loadQaConsole(true));
    qaElements.logoutButton.addEventListener("click", logoutQaConsole);
    qaElements.searchButton.addEventListener("click", async () => {
        readQaFiltersFromInputs();
        qaState.pageNo = 1;
        await loadQaConsole(false);
    });
    qaElements.resetButton.addEventListener("click", async () => {
        qaState.filters = {
            keyword: "",
            knowledgeBaseId: "",
            answerStatus: "",
            needHumanHandoff: "",
            sessionId: ""
        };
        qaState.pageNo = 1;
        qaState.selectedRecordId = null;
        qaState.selectedRecord = null;
        qaState.sources = [];
        qaState.retrievalDebug = null;
        applyQaFilterInputs();
        await loadQaConsole(false);
    });
    qaElements.prevPageButton.addEventListener("click", async () => {
        if (qaState.pageNo <= 1) {
            return;
        }
        qaState.pageNo -= 1;
        await loadQaConsole(true);
    });
    qaElements.nextPageButton.addEventListener("click", async () => {
        const totalPage = Math.max(1, Math.ceil(qaState.total / qaState.pageSize));
        if (qaState.pageNo >= totalPage) {
            return;
        }
        qaState.pageNo += 1;
        await loadQaConsole(true);
    });
}

async function restoreQaSession() {
    try {
        const [user, menus, knowledgeBases] = await Promise.all([
            QaCommon.api("/api/v1/auth/me", { token: qaState.token }),
            QaCommon.api("/api/v1/auth/menus", { token: qaState.token }),
            QaCommon.api("/api/v1/admin/knowledge-drafts/knowledge-bases/options", { token: qaState.token })
        ]);
        qaState.user = user;
        qaState.menus = menus || [];
        qaState.knowledgeBases = knowledgeBases || [];
        renderKnowledgeBaseOptions();
        showQaLoggedInState();
        renderQaContextBanner();
        await loadQaConsole(true);
    } catch (error) {
        handleQaAuthFailure(error, "登录状态已失效，请重新登录。");
    }
}

async function handleQaLogin(event) {
    event.preventDefault();
    const username = qaElements.usernameInput.value.trim();
    const password = qaElements.passwordInput.value;
    if (!username || !password) {
        setQaBanner("请输入用户名和密码。", "error");
        return;
    }

    qaElements.loginButton.disabled = true;
    setQaBanner("正在登录问答记录管理台...", "info");
    try {
        const data = await QaCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        qaState.token = data.token;
        QaCommon.saveToken(data.token);
        qaElements.passwordInput.value = "";
        await restoreQaSession();
        setQaBanner("问答记录管理台已就绪。", "success");
    } catch (error) {
        setQaBanner(error.message || "登录失败。", "error");
    } finally {
        qaElements.loginButton.disabled = false;
    }
}

function logoutQaConsole() {
    qaState.token = null;
    qaState.user = null;
    qaState.menus = [];
    qaState.records = [];
    qaState.sources = [];
    qaState.retrievalDebug = null;
    qaState.selectedRecord = null;
    qaState.selectedRecordId = null;
    qaState.total = 0;
    QaCommon.clearToken();
    QaCommon.updateQuery({ messageId: null });
    showQaLoggedOutState();
    setQaBanner("已退出问答记录管理台。", "success");
}

function showQaLoggedOutState() {
    qaElements.loginPanel.hidden = false;
    qaElements.workspace.hidden = true;
    qaElements.sessionName.textContent = "未登录";
    qaElements.sessionMeta.textContent = "请先登录管理账号";
}

function showQaLoggedInState() {
    qaElements.loginPanel.hidden = true;
    qaElements.workspace.hidden = false;
    qaElements.sessionName.textContent = qaState.user?.realName || qaState.user?.username || "管理用户";
    qaElements.sessionMeta.textContent = `租户 #${qaState.user?.tenantId || "-"} · ${qaState.user?.username || "-"}`;
}

function renderQaContextBanner() {
    const { source } = qaState.query;
    const sourceText = source
        ? `<span class="context-source">来自 ${QaCommon.escapeHtml(QaCommon.enumLabel("pageSource", source, source))}</span>`
        : "";
    const returnLink = buildQaReturnLink();
    qaElements.contextBanner.innerHTML = `
        <strong>问答记录工作台</strong>
        ${sourceText}
        <span>这里可以审查用户问题、AI 回答、召回来源、工单/草稿联动，并通过检索调试面板解释 RAG 命中过程。</span>
        ${returnLink}
    `;
}

async function loadQaConsole(keepSelection) {
    if (!qaState.token) {
        showQaLoggedOutState();
        return;
    }
    renderQaListLoading();
    syncQaQuery();

    try {
        const pageData = await QaCommon.api(buildQaListUrl(), { token: qaState.token });
        qaState.records = pageData.records || [];
        qaState.total = pageData.total || 0;
        qaState.pageNo = pageData.pageNo || qaState.pageNo;
        qaState.pageSize = pageData.pageSize || qaState.pageSize;
        renderQaList();

        const preferredRecordId = resolvePreferredRecordId(keepSelection);
        if (preferredRecordId) {
            await loadQaDetail(preferredRecordId, true);
            focusSelectedQaRecord();
        } else {
            qaState.selectedRecord = null;
            qaState.sources = [];
            qaState.retrievalDebug = null;
            renderQaDetail();
        }
    } catch (error) {
        if (error instanceof QaCommon.AuthExpiredError) {
            handleQaAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        setQaBanner(error.message || "问答记录加载失败。", "error");
        qaElements.qaTable.innerHTML = QaCommon.renderStateBlock({
            type: "error",
            title: "问答记录加载失败",
            message: error.message || "请稍后重试，或检查当前账号是否具备问答记录查看权限。"
        });
    }
}

function buildQaListUrl() {
    const params = new URLSearchParams({
        pageNo: String(qaState.pageNo),
        pageSize: String(qaState.pageSize)
    });
    if (qaState.filters.keyword) {
        params.set("keyword", qaState.filters.keyword);
    }
    if (qaState.filters.knowledgeBaseId) {
        params.set("knowledgeBaseId", qaState.filters.knowledgeBaseId);
    }
    if (qaState.filters.answerStatus) {
        params.set("answerStatus", qaState.filters.answerStatus);
    }
    if (qaState.filters.needHumanHandoff !== "") {
        params.set("needHumanHandoff", qaState.filters.needHumanHandoff);
    }
    if (qaState.filters.sessionId) {
        params.set("sessionId", qaState.filters.sessionId);
    }
    return `/api/v1/admin/qa-records?${params.toString()}`;
}

function renderQaListLoading() {
    qaElements.qaPageMeta.textContent = "正在同步问答记录";
    qaElements.qaTable.innerHTML = QaCommon.renderStateBlock({
        type: "loading",
        title: "正在加载问答记录",
        message: "系统正在同步问题、答案、召回来源和后续业务联动状态。"
    });
    qaElements.qaPaginationText.textContent = "加载中";
}

function buildQaDisplayRecords() {
    const records = [...qaState.records];
    const selected = qaState.selectedRecord;
    if (selected && selected.id && !records.some((record) => record.id === selected.id)) {
        return [{ ...selected, deepLinked: true }, ...records];
    }
    return records.map((record) => record.id === selected?.id ? { ...record, ...selected } : record);
}
function renderQaList() {
    const totalPage = Math.max(1, Math.ceil(qaState.total / qaState.pageSize));
    qaElements.qaPageMeta.textContent = `共 ${QaCommon.formatNumber(qaState.total)} 条问答记录`;
    qaElements.qaPaginationText.textContent = `第 ${qaState.pageNo} / ${totalPage} 页`;
    qaElements.prevPageButton.disabled = qaState.pageNo <= 1;
    qaElements.nextPageButton.disabled = qaState.pageNo >= totalPage;

    const displayRecords = buildQaDisplayRecords();
    if (!displayRecords.length) {
        qaElements.qaTable.innerHTML = QaCommon.renderStateBlock({
            title: "暂无匹配问答记录",
            message: "你可以调整筛选条件，或者从运营看板继续钻取热点和未命中主题。",
            actionHref: "/admin/dashboard",
            actionText: "回到运营看板"
        });
        return;
    }

    qaElements.qaTable.innerHTML = `
        <table class="data-table">
            <thead>
            <tr>
                <th>问题与会话</th>
                <th>知识库 / 提问人</th>
                <th>回答状态</th>
                <th>工单 / 草稿</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody>
            ${displayRecords.map((record) => `
                <tr class="${record.id === qaState.selectedRecordId ? "active-row" : ""} ${record.deepLinked ? "deep-linked-row" : ""}" data-record-row-id="${record.id}">
                    <td>
                        <div class="table-question">
                            <strong>#${record.id} · ${QaCommon.escapeHtml(formatQaQuestionText(record.questionText, 44))}</strong>${record.deepLinked ? `<span class="status-pill info" style="margin-left:8px;">深链定位</span>` : ""}
                            <div class="meta-line">${QaCommon.escapeHtml(formatQaSessionText(record))}</div>
                        </div>
                    </td>
                    <td>
                        <div class="meta-line">${QaCommon.escapeHtml(record.knowledgeBaseName || "未绑定知识库")}</div>
                        <div class="tiny-line">${QaCommon.escapeHtml(record.realName || record.username || "匿名用户")}</div>
                    </td>
                    <td>
                        <div class="chips">
                            <span class="status-pill ${QaCommon.statusClass(record.answerStatus)}">${QaCommon.escapeHtml(QaCommon.enumLabel("answerStatus", record.answerStatus, "未知状态"))}</span>
                            ${record.needHumanHandoff ? `<span class="status-pill warning">需人工</span>` : `<span class="status-pill success">AI 完成</span>`}
                        </div>
                    </td>
                    <td>
                        <div class="tiny-line">工单：${QaCommon.escapeHtml(record.ticketNo || "未创建")}</div>
                        <div class="tiny-line">草稿：${record.draftId ? `#${QaCommon.escapeHtml(record.draftId)}` : "未创建"}</div>
                    </td>
                    <td><button class="small-button" data-record-id="${record.id}">查看详情</button></td>
                </tr>
            `).join("")}
            </tbody>
        </table>
    `;

    qaElements.qaTable.querySelectorAll("[data-record-id]").forEach((button) => {
        button.addEventListener("click", () => loadQaDetail(Number(button.dataset.recordId), false));
    });
}

function resolvePreferredRecordId(keepSelection) {
    const linkedRecordId = Number(qaState.query.messageId || qaState.selectedRecordId || 0);
    if (linkedRecordId) {
        return linkedRecordId;
    }
    if (keepSelection && qaState.selectedRecordId && qaState.records.some((record) => record.id === qaState.selectedRecordId)) {
        return qaState.selectedRecordId;
    }
    return qaState.records[0]?.id || null;
}

function focusSelectedQaRecord() {
    requestAnimationFrame(() => {
        const queuePanel = document.getElementById("qaQueuePanel");
        const activeRow = document.querySelector(`#qaTable [data-record-row-id="${qaState.selectedRecordId}"]`) || document.querySelector("#qaTable .active-row");
        if (activeRow) {
            queuePanel?.scrollIntoView({ behavior: "smooth", block: "start" });
            window.setTimeout(() => activeRow.scrollIntoView({ behavior: "smooth", block: "center" }), 220);
            activeRow.classList.add("qa-row-focus");
            window.setTimeout(() => activeRow.classList.remove("qa-row-focus"), 1600);
            return;
        }
        queuePanel?.scrollIntoView({ behavior: "smooth", block: "start" });
        window.setTimeout(() => qaElements.qaDetailBody?.scrollIntoView({ behavior: "smooth", block: "start" }), 260);
    });
}

async function loadQaDetail(recordId, silent) {
    if (!recordId) {
        return;
    }
    qaState.selectedRecordId = recordId;
    syncQaQuery();
    renderQaList();
    if (!silent) {
        qaElements.qaDetailBody.innerHTML = QaCommon.renderStateBlock({
            type: "loading",
            title: "正在加载问答详情",
            message: "正在拉取答案、召回来源、检索调试和业务闭环信息。"
        });
    }

    try {
        const [detail, sources, retrievalDebug] = await Promise.all([
            QaCommon.api(`/api/v1/admin/qa-records/${recordId}`, { token: qaState.token }),
            QaCommon.api(`/api/v1/admin/qa-records/${recordId}/sources`, { token: qaState.token }),
            QaCommon.api(`/api/v1/admin/qa-records/${recordId}/retrieval-debug`, { token: qaState.token })
        ]);
        qaState.selectedRecord = detail;
        qaState.sources = sources || [];
        qaState.retrievalDebug = retrievalDebug || null;
        renderQaList();
        renderQaDetail();
        focusSelectedQaRecord();
    } catch (error) {
        if (error instanceof QaCommon.AuthExpiredError) {
            handleQaAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        qaElements.qaDetailBody.innerHTML = QaCommon.renderStateBlock({
            type: "error",
            title: "问答详情加载失败",
            message: error.message || "请稍后重试，或从左侧重新选择一条问答记录。"
        });
        setQaBanner(error.message || "问答详情加载失败。", "error");
    }
}

function renderQaDetail() {
    const record = qaState.selectedRecord;
    if (!record) {
        qaElements.qaDetailBody.innerHTML = QaCommon.renderStateBlock({
            title: "选择一条问答记录开始查看",
            message: "左侧点选记录后，这里会展示答案、召回来源、检索调试和业务联动信息。"
        });
        return;
    }

    qaElements.qaDetailBody.innerHTML = `
        <section class="summary-card">
            <span class="panel-kicker">当前选中问答</span>
            <strong>问答 #${QaCommon.escapeHtml(record.id)} · ${QaCommon.escapeHtml(formatQaQuestionText(record.questionText, 80))}</strong>
            <div class="chips">
                <span class="status-pill ${QaCommon.statusClass(record.answerStatus)}">${QaCommon.escapeHtml(QaCommon.enumLabel("answerStatus", record.answerStatus, "未知状态"))}</span>
                <span class="status-pill">${record.needHumanHandoff ? "需要人工接入" : "AI 独立完成"}</span>
                <span class="status-pill">${QaCommon.escapeHtml(record.knowledgeBaseName || "未绑定知识库")}</span>
                <span class="status-pill">${QaCommon.escapeHtml(QaCommon.formatDateTime(record.createdAt))}</span>
            </div>
            <p>${QaCommon.escapeHtml(record.questionText || "暂无问题内容")}</p>
            <div class="action-row" style="margin-top:14px;">
                <a class="link-button" href="/admin/dashboard">回到运营看板</a>
                ${record.ticketId ? `<a class="link-button" href="${buildTicketLink(record)}">打开关联工单</a>` : ""}
                ${record.draftId ? `<a class="link-button" href="${buildDraftLink(record)}">打开关联草稿</a>` : ""}
            </div>
        </section>

        <section class="detail-columns">
            <article class="detail-card">
                <strong>会话与用户</strong>
                <p>${QaCommon.escapeHtml(formatQaSessionText(record))}</p>
                <p>${QaCommon.escapeHtml(record.realName || record.username || "匿名用户")}</p>
                <p>${QaCommon.escapeHtml(record.modelName || "未知模型")}</p>
                <p>总耗时：${QaCommon.escapeHtml(formatQaDuration(record.latencyMs))}</p>
                <p>检索耗时：${QaCommon.escapeHtml(formatQaDuration(record.retrievalLatencyMs))}</p>
                <p>生成耗时：${QaCommon.escapeHtml(formatQaDuration(record.generationLatencyMs))}</p>
                <p>检索缓存：${record.retrievalCacheHit ? "命中" : "未命中"}</p>
                <p>回答模式：${QaCommon.escapeHtml(formatQaAnswerMode(record.answerMode))}</p>
            </article>
            <article class="detail-card">
                <strong>AI 答案</strong>
                <p>${QaCommon.escapeHtml(record.answerText || "暂无 AI 答案")}</p>
            </article>
        </section>

        <section class="timeline-grid">
            <article class="detail-card">
                <strong>检索调试</strong>
                <div class="section-stack">${renderRetrievalDebug()}</div>
            </article>
            <article class="detail-card">
                <strong>召回来源</strong>
                <div class="section-stack">${renderQaSources()}</div>
            </article>
            <article class="detail-card">
                <strong>业务联动</strong>
                <div class="section-stack">${renderQaBusinessLinks(record)}</div>
            </article>
        </section>
    `;
}

function renderRetrievalDebug() {
    const debug = qaState.retrievalDebug;
    if (!debug) {
        return `<div class="empty-state"><p>检索调试数据暂未加载。</p></div>`;
    }
    const chunks = debug.chunks || [];
    return `
        <div class="retrieval-debug-summary">
            <span>最低有效阈值 ${formatScore(debug.minRecallScore)}</span>
            <span>Top1 召回分 ${formatScore(debug.topRecallScore)}</span>
            <span>候选 Chunk ${chunks.length} 个</span>
        </div>
        <div>
            <div class="meta-line">实际 Query Variants</div>
            ${renderQueryVariants(debug.queryVariants || [])}
        </div>
        <div>
            <div class="meta-line">Chunk 分数拆解</div>
            ${renderDebugChunks(chunks)}
        </div>
    `;
}

function renderQueryVariants(variants) {
    if (!variants.length) {
        return `<div class="empty-state"><p>没有生成 query variants，可能使用了默认检索客户端。</p></div>`;
    }
    return `<div class="query-variant-list">
        ${variants.map((variant) => `
            <span class="query-variant-chip" title="归一化：${QaCommon.escapeHtml(variant.normalizedText || "-")}">
                <strong>${QaCommon.escapeHtml(variant.text || "-")}</strong>
                <small>${QaCommon.escapeHtml(formatVariantSource(variant.source))} · 权重 ${formatScore(variant.weight)}</small>
            </span>
        `).join("")}
    </div>`;
}

function renderDebugChunks(chunks) {
    if (!chunks.length) {
        return `<div class="empty-state"><p>没有进入回答上下文的 chunk，因此无法展示分数拆解。</p></div>`;
    }
    return chunks.map((chunk) => `
        <article class="debug-score-card">
            <div class="debug-score-head">
                <strong>#${QaCommon.escapeHtml(chunk.rankNo || "-")} · ${QaCommon.escapeHtml(chunk.documentName || `文档 ${chunk.documentId || "N/A"}`)}</strong>
                <span class="status-pill">${QaCommon.escapeHtml(formatRecallStrategy(chunk.recallStrategy))}</span>
            </div>
            ${renderScoreBar("最终分", chunk.recallScore, 1)}
            ${renderScoreBar("词法分", chunk.lexicalScore, 1)}
            ${renderScoreBar("向量分", chunk.vectorScore, 1)}
            <p>${QaCommon.escapeHtml(QaCommon.truncateText(chunk.snippetText || "", 120))}</p>
        </article>
    `).join("");
}

function renderScoreBar(label, value, maxValue) {
    const numeric = Number(value || 0);
    const width = Math.max(0, Math.min(100, (numeric / (maxValue || 1)) * 100));
    return `
        <div class="score-row">
            <span>${label}</span>
            <div class="score-bar"><i style="width:${width.toFixed(2)}%"></i></div>
            <b>${formatScore(value)}</b>
        </div>
    `;
}

function renderQaSources() {
    if (!qaState.sources.length) {
        return `<div class="empty-state"><p>当前问答没有召回知识来源，通常意味着直接未命中。</p></div>`;
    }
    return qaState.sources.map((source) => `
        <article class="timeline-item">
            <strong>${QaCommon.escapeHtml(source.documentName || `文档 #${source.documentId || "N/A"}`)}</strong>
            <div class="meta-line">排序 ${QaCommon.escapeHtml(source.rankNo || "-")} · 召回分 ${QaCommon.escapeHtml(source.recallScore ?? "-")}</div>
            <p>${QaCommon.escapeHtml(source.snippetText || "")}</p>
        </article>
    `).join("");
}

function renderQaBusinessLinks(record) {
    return `
        <article class="timeline-item">
            <strong>工单</strong>
            <div class="chips" style="margin-top:8px;">
                <span class="status-pill ${QaCommon.statusClass(record.ticketStatus)}">${QaCommon.escapeHtml(QaCommon.enumLabel("ticketStatus", record.ticketStatus, "未创建"))}</span>
            </div>
            <p>${QaCommon.escapeHtml(record.ticketNo || "当前问答尚未转成工单。")}</p>
            ${record.ticketId ? `<div class="footer-actions" style="margin-top:12px;"><a class="link-button" href="${buildTicketLink(record)}">进入工单页</a></div>` : ""}
        </article>
        <article class="timeline-item">
            <strong>知识草稿</strong>
            <div class="chips" style="margin-top:8px;">
                <span class="status-pill ${QaCommon.statusClass(record.draftStatus)}">${QaCommon.escapeHtml(QaCommon.enumLabel("draftStatus", record.draftStatus, "未创建"))}</span>
            </div>
            <p>${record.draftId ? `草稿 #${QaCommon.escapeHtml(record.draftId)}` : "当前问答尚未沉淀为知识草稿。"}</p>
            ${record.draftId ? `<div class="footer-actions" style="margin-top:12px;"><a class="link-button" href="${buildDraftLink(record)}">进入草稿页</a></div>` : ""}
        </article>
    `;
}

function formatScore(value) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric.toFixed(4) : "-";
}

function formatVariantSource(source) {
    const mapping = {
        ORIGINAL: "原始问题",
        NORMALIZED: "归一化",
        CORE_PHRASE: "核心短语",
        CJK_NGRAM: "中文切片",
        ALIAS: "同义扩展"
    };
    return mapping[source] || source || "未知来源";
}

function formatRecallStrategy(strategy) {
    const mapping = {
        HYBRID: "混合召回",
        LEXICAL: "词法召回",
        VECTOR: "向量召回"
    };
    if (!strategy) {
        return "未标记策略";
    }
    const [mode, source] = String(strategy).split(":");
    const sourceText = source ? ` · ${formatVariantSource(source)}` : "";
    return `${mapping[mode] || mode}${sourceText}`;
}

function formatQaQuestionText(questionText, maxLength) {
    return QaCommon.truncateText(questionText || "", maxLength) || "暂无问题内容";
}

function formatQaDuration(value) {
    const numericValue = Number(value || 0);
    if (!Number.isFinite(numericValue) || numericValue <= 0) {
        return "0 毫秒";
    }
    if (numericValue < 1000) {
        return `${numericValue} 毫秒`;
    }
    return `${(numericValue / 1000).toFixed(2)} 秒`;
}

function formatQaAnswerMode(mode) {
    const labels = {
        ASYNC_GENERATING: "异步生成中",
        FAST_RETRIEVAL: "高置信快速回答",
        LLM_GENERATED: "大模型生成",
        NO_HIT_FALLBACK: "未命中降级",
        FAILED: "生成失败"
    };
    return labels[mode] || "未知模式";
}
function formatQaSessionText(record) {
    return record.sessionTitle || record.sessionNo || `会话 #${record.sessionId || "N/A"}`;
}

function buildTicketLink(record) {
    const params = new URLSearchParams({
        ticketId: String(record.ticketId),
        source: "qa-records",
        messageId: String(record.id)
    });
    if (record.draftId) {
        params.set("draftId", String(record.draftId));
    }
    return `/admin/tickets?${params.toString()}`;
}

function buildDraftLink(record) {
    const params = new URLSearchParams({
        draftId: String(record.draftId),
        source: "qa-records",
        messageId: String(record.id)
    });
    if (record.ticketId) {
        params.set("ticketId", String(record.ticketId));
    }
    return `/admin/knowledge-drafts?${params.toString()}`;
}

function renderKnowledgeBaseOptions() {
    const currentValue = qaState.filters.knowledgeBaseId || "";
    qaElements.knowledgeBaseFilter.innerHTML = `
        <option value="">全部知识库</option>
        ${qaState.knowledgeBases.map((knowledgeBase) => `
            <option value="${knowledgeBase.id}" ${String(currentValue) === String(knowledgeBase.id) ? "selected" : ""}>
                ${QaCommon.escapeHtml(knowledgeBase.kbName)} (${QaCommon.escapeHtml(QaCommon.enumLabel("knowledgeBaseStatus", knowledgeBase.status, "未知状态"))})
            </option>
        `).join("")}
    `;
}

function applyQaFilterInputs() {
    qaElements.keywordFilter.value = qaState.filters.keyword;
    qaElements.knowledgeBaseFilter.value = qaState.filters.knowledgeBaseId;
    qaElements.answerStatusFilter.value = qaState.filters.answerStatus;
    qaElements.handoffFilter.value = qaState.filters.needHumanHandoff;
    qaElements.sessionIdFilter.value = qaState.filters.sessionId;
}

function readQaFiltersFromInputs() {
    qaState.filters.keyword = qaElements.keywordFilter.value.trim();
    qaState.filters.knowledgeBaseId = qaElements.knowledgeBaseFilter.value;
    qaState.filters.answerStatus = qaElements.answerStatusFilter.value;
    qaState.filters.needHumanHandoff = qaElements.handoffFilter.value;
    qaState.filters.sessionId = qaElements.sessionIdFilter.value.trim();
}

function syncQaQuery() {
    QaCommon.updateQuery({
        pageNo: qaState.pageNo,
        messageId: qaState.selectedRecordId,
        keyword: qaState.filters.keyword,
        knowledgeBaseId: qaState.filters.knowledgeBaseId,
        answerStatus: qaState.filters.answerStatus,
        needHumanHandoff: qaState.filters.needHumanHandoff,
        sessionId: qaState.filters.sessionId,
        source: qaState.query.source,
        returnUrl: qaState.query.returnUrl,
        returnLabel: qaState.query.returnLabel
    });
}

function buildQaReturnLink() {
    const returnUrl = normalizeQaReturnUrl(qaState.query.returnUrl);
    if (!returnUrl) {
        return "";
    }
    const label = qaState.query.returnLabel || "来源页面";
    return `<a class="link-button context-return-button" href="${QaCommon.escapeAttr(returnUrl)}">返回${QaCommon.escapeHtml(label)}</a>`;
}

function normalizeQaReturnUrl(value) {
    const url = String(value || "").trim();
    if (!url || !url.startsWith("/") || url.startsWith("//") || url.toLowerCase().startsWith("javascript:")) {
        return "";
    }
    return url;
}

function handleQaAuthFailure(error, message) {
    console.error(error);
    logoutQaConsole();
    setQaBanner(message, "error");
}

function setQaBanner(message, type = "info") {
    qaElements.statusBanner.hidden = false;
    qaElements.statusBanner.className = `status-banner ${type}`;
    qaElements.statusBanner.textContent = message;
}
