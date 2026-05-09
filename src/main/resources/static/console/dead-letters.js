const DeadLetterCommon = window.KnowFlowConsoleCommon;

const deadLetterState = {
    token: DeadLetterCommon.readToken(),
    query: DeadLetterCommon.parseQuery(),
    user: null,
    menus: [],
    records: [],
    selectedRecord: null,
    selectedId: null,
    pageNo: 1,
    pageSize: 10,
    total: 0,
    lastRefreshAt: null,
    filters: {
        replayStatus: "",
        taskType: "",
        taskId: "",
        documentId: "",
        keyword: ""
    }
};

const deadLetterElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    cacheDeadLetterElements();
    initDeadLetterStateFromQuery();
    bindDeadLetterEvents();
    if (deadLetterState.token) {
        await restoreDeadLetterSession();
        return;
    }
    showDeadLetterLoggedOutState();
});

function cacheDeadLetterElements() {
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
        "replayStatusFilter",
        "taskTypeFilter",
        "taskIdFilter",
        "documentIdFilter",
        "keywordFilter",
        "searchButton",
        "resetButton",
        "contextBanner",
        "capabilityTags",
        "lastRefreshText",
        "deadLetterPageMeta",
        "deadLetterTable",
        "deadLetterPaginationText",
        "prevPageButton",
        "nextPageButton",
        "deadLetterDetailBody"
    ].forEach((id) => {
        deadLetterElements[id] = document.getElementById(id);
    });
}

function initDeadLetterStateFromQuery() {
    const query = deadLetterState.query;
    deadLetterState.pageNo = Number(query.pageNo || 1);
    deadLetterState.selectedId = query.deadLetterId ? Number(query.deadLetterId) : null;
    deadLetterState.filters.replayStatus = query.replayStatus || "";
    deadLetterState.filters.taskType = query.taskType || "";
    deadLetterState.filters.taskId = query.taskId || "";
    deadLetterState.filters.documentId = query.documentId || "";
    deadLetterState.filters.keyword = query.keyword || "";
    applyDeadLetterFilterInputs();
}

function bindDeadLetterEvents() {
    deadLetterElements.loginForm.addEventListener("submit", handleDeadLetterLogin);
    deadLetterElements.refreshButton.addEventListener("click", () => loadDeadLetters(true));
    deadLetterElements.logoutButton.addEventListener("click", logoutDeadLetterConsole);
    deadLetterElements.searchButton.addEventListener("click", async () => {
        readDeadLetterFiltersFromInputs();
        deadLetterState.pageNo = 1;
        await loadDeadLetters(false);
    });
    deadLetterElements.resetButton.addEventListener("click", async () => {
        deadLetterState.filters = {
            replayStatus: "",
            taskType: "",
            taskId: "",
            documentId: "",
            keyword: ""
        };
        deadLetterState.pageNo = 1;
        deadLetterState.selectedId = null;
        applyDeadLetterFilterInputs();
        await loadDeadLetters(false);
    });
    deadLetterElements.prevPageButton.addEventListener("click", async () => {
        if (deadLetterState.pageNo <= 1) {
            return;
        }
        deadLetterState.pageNo -= 1;
        await loadDeadLetters(true);
    });
    deadLetterElements.nextPageButton.addEventListener("click", async () => {
        const totalPage = Math.max(1, Math.ceil(deadLetterState.total / deadLetterState.pageSize));
        if (deadLetterState.pageNo >= totalPage) {
            return;
        }
        deadLetterState.pageNo += 1;
        await loadDeadLetters(true);
    });
}

async function restoreDeadLetterSession() {
    try {
        const [user, menus] = await Promise.all([
            DeadLetterCommon.api("/api/v1/auth/me", { token: deadLetterState.token }),
            DeadLetterCommon.api("/api/v1/auth/menus", { token: deadLetterState.token })
        ]);
        deadLetterState.user = user;
        deadLetterState.menus = menus || [];
        renderDeadLetterCapabilities();
        renderDeadLetterContextBanner();
        showDeadLetterLoggedInState();
        await loadDeadLetters(true);
    } catch (error) {
        handleDeadLetterAuthFailure(error, "登录已过期，请重新登录。");
    }
}

async function handleDeadLetterLogin(event) {
    event.preventDefault();
    const username = deadLetterElements.usernameInput.value.trim();
    const password = deadLetterElements.passwordInput.value;
    if (!username || !password) {
        setDeadLetterBanner("请输入账号和密码。", "error");
        return;
    }

    deadLetterElements.loginButton.disabled = true;
    setDeadLetterBanner("正在登录并加载死信队列...", "info");
    try {
        const data = await DeadLetterCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        deadLetterState.token = data.token;
        DeadLetterCommon.saveToken(data.token);
        deadLetterElements.passwordInput.value = "";
        await restoreDeadLetterSession();
        setDeadLetterBanner("死信队列加载完成。", "success");
    } catch (error) {
        setDeadLetterBanner(error.message || "登录失败。", "error");
    } finally {
        deadLetterElements.loginButton.disabled = false;
    }
}

function logoutDeadLetterConsole() {
    deadLetterState.token = "";
    deadLetterState.user = null;
    deadLetterState.menus = [];
    deadLetterState.records = [];
    deadLetterState.selectedRecord = null;
    deadLetterState.selectedId = null;
    deadLetterState.total = 0;
    DeadLetterCommon.clearToken();
    DeadLetterCommon.updateQuery({
        deadLetterId: null,
        pageNo: null,
        replayStatus: null,
        taskType: null,
        taskId: null,
        documentId: null,
        keyword: null
    });
    showDeadLetterLoggedOutState();
    setDeadLetterBanner("已退出死信治理台。", "success");
}

function showDeadLetterLoggedOutState() {
    deadLetterElements.loginPanel.hidden = false;
    deadLetterElements.workspace.hidden = true;
    deadLetterElements.sessionName.textContent = "未登录";
    deadLetterElements.sessionMeta.textContent = "请先登录后查看失败解析与索引任务。";
}

function showDeadLetterLoggedInState() {
    deadLetterElements.loginPanel.hidden = true;
    deadLetterElements.workspace.hidden = false;
    deadLetterElements.sessionName.textContent = deadLetterState.user.realName || deadLetterState.user.username;
    deadLetterElements.sessionMeta.textContent = DeadLetterCommon.formatUserSessionMeta(deadLetterState.user);
}

function renderDeadLetterCapabilities() {
    const tags = [];
    deadLetterState.user?.roleCodes?.forEach((role) => tags.push(`<span class="tag">${DeadLetterCommon.escapeHtml(role)}</span>`));
    if (deadLetterState.menus?.length) {
        deadLetterState.menus.forEach((menu) => tags.push(`<span class="tag">${DeadLetterCommon.escapeHtml(menu.name)}</span>`));
    }
    tags.push('<span class="tag">死信队列</span>');
    tags.push('<span class="tag">自动重试</span>');
    tags.push('<span class="tag">人工回放</span>');
    deadLetterElements.capabilityTags.innerHTML = tags.join("");
}

function renderDeadLetterContextBanner() {
    const { source } = deadLetterState.query;
    const returnLink = buildDeadLetterReturnLink();
    const parts = [];
    if (source) {
        parts.push(`来自 ${DeadLetterCommon.enumLabel("pageSource", source, source)}`);
    }
    if (deadLetterState.filters.taskId) {
        parts.push(`聚焦任务 #${deadLetterState.filters.taskId}`);
    }
    if (deadLetterState.filters.documentId) {
        parts.push(`聚焦文档 #${deadLetterState.filters.documentId}`);
    }
    if (!parts.length && !returnLink) {
        deadLetterElements.contextBanner.hidden = true;
        deadLetterElements.contextBanner.textContent = "";
        return;
    }
    deadLetterElements.contextBanner.hidden = false;
    deadLetterElements.contextBanner.innerHTML = `
        <strong>死信治理定位</strong>
        <span>${DeadLetterCommon.escapeHtml(parts.join(" | ") || "正在查看死信治理队列")}</span>
        ${returnLink}
    `;
}

async function loadDeadLetters(keepSelection) {
    if (!deadLetterState.token) {
        showDeadLetterLoggedOutState();
        return;
    }

    renderDeadLetterListLoading();
    syncDeadLetterQuery();
    renderDeadLetterContextBanner();
    try {
        const pageData = await DeadLetterCommon.api(buildDeadLetterListUrl(), { token: deadLetterState.token });
        deadLetterState.records = pageData.records || [];
        deadLetterState.total = pageData.total || 0;
        deadLetterState.pageNo = pageData.pageNo || deadLetterState.pageNo;
        deadLetterState.pageSize = pageData.pageSize || deadLetterState.pageSize;
        deadLetterState.lastRefreshAt = new Date();
        deadLetterElements.lastRefreshText.textContent = `最近刷新：${DeadLetterCommon.formatDateTime(deadLetterState.lastRefreshAt)}`;
        renderDeadLetterList();

        const preferredId = resolvePreferredDeadLetterId(keepSelection);
        if (preferredId) {
            await loadDeadLetterDetail(preferredId, true);
        } else {
            deadLetterState.selectedRecord = null;
            renderDeadLetterDetail();
        }
    } catch (error) {
        if (error instanceof DeadLetterCommon.AuthExpiredError) {
            handleDeadLetterAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        deadLetterElements.deadLetterTable.innerHTML = DeadLetterCommon.renderStateBlock({
            type: "error",
            title: "死信记录加载失败",
            message: error.message || "请稍后重试，或检查当前账号是否具备死信治理权限。"
        });
        deadLetterElements.deadLetterDetailBody.innerHTML = DeadLetterCommon.renderStateBlock({
            type: "error",
            title: "回放详情暂不可用",
            message: error.message || "请稍后重试，或从左侧重新选择死信记录。"
        });
        setDeadLetterBanner(error.message || "死信记录加载失败。", "error");
    }
}

function renderDeadLetterListLoading() {
    deadLetterElements.deadLetterPageMeta.textContent = "正在加载待治理记录";
    deadLetterElements.deadLetterTable.innerHTML = DeadLetterCommon.renderStateBlock({
        type: "loading",
        title: "正在加载死信队列",
        message: "系统正在同步失败任务、回放状态和自动重试计划。"
    });
    deadLetterElements.deadLetterPaginationText.textContent = "加载中";
}

function buildDeadLetterListUrl() {
    const params = new URLSearchParams({
        pageNo: String(deadLetterState.pageNo),
        pageSize: String(deadLetterState.pageSize)
    });
    if (deadLetterState.filters.replayStatus) {
        params.set("replayStatus", deadLetterState.filters.replayStatus);
    }
    if (deadLetterState.filters.taskType) {
        params.set("taskType", deadLetterState.filters.taskType);
    }
    if (deadLetterState.filters.taskId) {
        params.set("taskId", deadLetterState.filters.taskId);
    }
    if (deadLetterState.filters.documentId) {
        params.set("documentId", deadLetterState.filters.documentId);
    }
    if (deadLetterState.filters.keyword) {
        params.set("keyword", deadLetterState.filters.keyword);
    }
    return `/api/v1/admin/dead-letters?${params.toString()}`;
}

function renderDeadLetterList() {
    const totalPage = Math.max(1, Math.ceil(deadLetterState.total / deadLetterState.pageSize));
    deadLetterElements.deadLetterPageMeta.textContent = `当前共有 ${DeadLetterCommon.formatNumber(deadLetterState.total)} 条待治理记录`;
    deadLetterElements.deadLetterPaginationText.textContent = `第 ${deadLetterState.pageNo} / ${totalPage} 页`;
    deadLetterElements.prevPageButton.disabled = deadLetterState.pageNo <= 1;
    deadLetterElements.nextPageButton.disabled = deadLetterState.pageNo >= totalPage;

    if (!deadLetterState.records.length) {
        deadLetterElements.deadLetterTable.innerHTML = DeadLetterCommon.renderStateBlock({
            title: "未找到死信记录",
            message: "可能当前异步链路运行正常，或你的筛选条件过于严格。",
            actionHref: "/admin/ops-health",
            actionText: "回到运维健康"
        });
        return;
    }

    deadLetterElements.deadLetterTable.innerHTML = `
        <table class="data-table">
            <thead>
            <tr>
                <th>死信记录</th>
                <th>任务信息</th>
                <th>状态</th>
                <th>错误信息</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody>
            ${deadLetterState.records.map((record) => `
                <tr class="${record.id === deadLetterState.selectedId ? "active-row" : ""}">
                    <td>
                        <div class="table-question">
                            <strong>${DeadLetterCommon.escapeHtml(record.deadLetterNo || `DLQ #${record.id}`)}</strong>
                            <div class="meta-line">${DeadLetterCommon.escapeHtml(record.documentName || "未关联文档")} / ${DeadLetterCommon.escapeHtml(DeadLetterCommon.enumLabel("taskType", record.taskType, "未知任务类型"))}</div>
                            <div class="tiny-line">${DeadLetterCommon.escapeHtml(DeadLetterCommon.formatDateTime(record.createdAt))}</div>
                        </div>
                    </td>
                    <td>
                        <div class="chips">
                            <span class="status-pill">${DeadLetterCommon.escapeHtml(record.taskNo || `任务 #${record.taskId}`)}</span>
                            <span class="status-pill ${DeadLetterCommon.statusClass(record.taskStatus)}">${DeadLetterCommon.escapeHtml(DeadLetterCommon.enumLabel("knowledgeBaseStatus", record.taskStatus, "未知状态"))}</span>
                        </div>
                        <div class="tiny-line">任务 ${DeadLetterCommon.escapeHtml(record.taskId || "N/A")} / 重试 ${DeadLetterCommon.escapeHtml(record.retryAttempt ?? 0)}</div>
                    </td>
                    <td>
                        <div class="chips">
                            <span class="status-pill ${DeadLetterCommon.statusClass(record.replayStatus)}">${DeadLetterCommon.escapeHtml(DeadLetterCommon.enumLabel("replayStatus", record.replayStatus, "未知状态"))}</span>
                            ${record.nextRetryAt ? `<span class="status-pill">下次重试 ${DeadLetterCommon.escapeHtml(DeadLetterCommon.formatDateTime(record.nextRetryAt))}</span>` : ""}
                        </div>
                    </td>
                    <td>${DeadLetterCommon.escapeHtml(DeadLetterCommon.truncateText(record.errorMessage || "暂无错误信息", 100) || "暂无错误信息")}</td>
                    <td><button class="link-button table-row-button" type="button" data-dead-letter-id="${record.id}">查看</button></td>
                </tr>
            `).join("")}
            </tbody>
        </table>
    `;

    deadLetterElements.deadLetterTable.querySelectorAll("[data-dead-letter-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await loadDeadLetterDetail(Number(button.dataset.deadLetterId), false);
        });
    });
}

function resolvePreferredDeadLetterId(keepSelection) {
    if (keepSelection && deadLetterState.selectedId) {
        return deadLetterState.selectedId;
    }
    if (deadLetterState.query.deadLetterId) {
        return Number(deadLetterState.query.deadLetterId);
    }
    if (deadLetterState.selectedId) {
        return deadLetterState.selectedId;
    }
    return deadLetterState.records[0]?.id || null;
}

async function loadDeadLetterDetail(id, silent) {
    if (!id) {
        return;
    }
    deadLetterState.selectedId = id;
    syncDeadLetterQuery();
    renderDeadLetterList();
    if (!silent) {
        deadLetterElements.deadLetterDetailBody.innerHTML = DeadLetterCommon.renderStateBlock({
            type: "loading",
            title: "正在加载死信详情",
            message: "正在读取任务上下文、回放时间线和消息载荷。"
        });
    }
    try {
        deadLetterState.selectedRecord = await DeadLetterCommon.api(`/api/v1/admin/dead-letters/${id}`, { token: deadLetterState.token });
        renderDeadLetterDetail();
    } catch (error) {
        if (error instanceof DeadLetterCommon.AuthExpiredError) {
            handleDeadLetterAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        deadLetterElements.deadLetterDetailBody.innerHTML = DeadLetterCommon.renderStateBlock({
            type: "error",
            title: "详情加载失败",
            message: error.message || "请稍后重试，或从左侧重新选择死信记录。"
        });
        setDeadLetterBanner(error.message || "死信详情加载失败。", "error");
    }
}

function renderDeadLetterDetail() {
    const record = deadLetterState.selectedRecord;
    if (!record) {
        deadLetterElements.deadLetterDetailBody.innerHTML = DeadLetterCommon.renderStateBlock({
            title: "请选择一条失败任务",
            message: "从左侧选择死信记录后，可以查看运行态和回放控制。"
        });
        return;
    }

    const canReplay = record.replayStatus === "READY" || record.replayStatus === "MANUAL_REQUIRED";
    const runtime = record.taskRuntime || null;

    deadLetterElements.deadLetterDetailBody.innerHTML = `
        <section class="summary-card">
            <span class="panel-kicker">当前死信记录</span>
            <strong>${DeadLetterCommon.escapeHtml(record.deadLetterNo || `DLQ #${record.id}`)}</strong>
            <div class="chips">
                <span class="status-pill ${DeadLetterCommon.statusClass(record.replayStatus)}">${DeadLetterCommon.escapeHtml(DeadLetterCommon.enumLabel("replayStatus", record.replayStatus, "未知状态"))}</span>
                <span class="status-pill ${DeadLetterCommon.statusClass(record.taskStatus)}">${DeadLetterCommon.escapeHtml(DeadLetterCommon.enumLabel("knowledgeBaseStatus", record.taskStatus, "未知状态"))}</span>
                <span class="status-pill">${DeadLetterCommon.escapeHtml(DeadLetterCommon.enumLabel("taskType", record.taskType, "未知任务类型"))}</span>
                <span class="status-pill">重试 ${DeadLetterCommon.escapeHtml(record.retryAttempt ?? 0)}</span>
            </div>
            <p>${DeadLetterCommon.escapeHtml(record.errorMessage || "暂无错误信息。")}</p>
            <div class="action-row" style="margin-top:14px;">
                <a class="link-button" href="/admin/dashboard">返回运营看板</a>
                <a class="link-button" href="${buildParseTaskLink(record)}">打开解析任务</a>
                ${record.documentId ? `<a class="link-button" href="${buildDocumentLink(record)}">打开文档</a>` : ""}
                ${canReplay ? `<button id="replayButton" class="navy-button" type="button">立即回放</button>` : ""}
            </div>
        </section>

        <section class="detail-columns">
            <article class="detail-card">
                <strong>任务上下文</strong>
                <p>任务编号：${DeadLetterCommon.escapeHtml(record.taskNo || `任务 #${record.taskId}`)}</p>
                <p>任务 ID：${DeadLetterCommon.escapeHtml(record.taskId || "N/A")}</p>
                <p>关联文档：${DeadLetterCommon.escapeHtml(record.documentName || "未关联文档")}</p>
                <p>文档 ID：${DeadLetterCommon.escapeHtml(record.documentId || "N/A")}</p>
                <p>来源队列：${DeadLetterCommon.escapeHtml(record.sourceQueue || "N/A")}</p>
                <p>交换机 / 路由键：${DeadLetterCommon.escapeHtml(record.sourceExchange || "N/A")} / ${DeadLetterCommon.escapeHtml(record.routingKey || "N/A")}</p>
            </article>
            <article class="detail-card">
                <strong>回放时间线</strong>
                <p>创建时间：${DeadLetterCommon.escapeHtml(DeadLetterCommon.formatDateTime(record.createdAt))}</p>
                <p>下次重试：${DeadLetterCommon.escapeHtml(DeadLetterCommon.formatDateTime(record.nextRetryAt))}</p>
                <p>回放时间：${DeadLetterCommon.escapeHtml(DeadLetterCommon.formatDateTime(record.replayedAt))}</p>
                <p>解决时间：${DeadLetterCommon.escapeHtml(DeadLetterCommon.formatDateTime(record.resolvedAt))}</p>
            </article>
        </section>

        <section class="detail-card">
            <strong>运行态快照</strong>
            ${runtime ? `
                <div class="detail-columns" style="margin-top:14px;">
                    <article class="detail-card">
                        <strong>传输信息</strong>
                        <p>${DeadLetterCommon.escapeHtml(runtime.transport || "暂无")} / ${DeadLetterCommon.escapeHtml(runtime.queueStatus || "暂无")}</p>
                        <p>处理节点：${DeadLetterCommon.escapeHtml(runtime.workerId || "暂无")}</p>
                        <p>分块数量：${DeadLetterCommon.escapeHtml(runtime.chunkCount ?? 0)}</p>
                    </article>
                    <article class="detail-card">
                        <strong>时序信息</strong>
                        <p>入队时间：${DeadLetterCommon.escapeHtml(DeadLetterCommon.formatDateTime(runtime.queuedAt))}</p>
                        <p>出队时间：${DeadLetterCommon.escapeHtml(DeadLetterCommon.formatDateTime(runtime.dequeuedAt))}</p>
                        <p>耗时（毫秒）：${DeadLetterCommon.escapeHtml(runtime.durationMs ?? "N/A")}</p>
                    </article>
                </div>
            ` : `<p>该任务暂未持久化运行态快照。</p>`}
        </section>

        <section class="detail-card">
            <strong>消息载荷</strong>
            <pre>${DeadLetterCommon.escapeHtml(formatPayloadJson(record.payloadJson))}</pre>
        </section>
    `;

    const replayButton = document.getElementById("replayButton");
    if (replayButton) {
        replayButton.addEventListener("click", async () => {
            await replayDeadLetter(record.id);
        });
    }
}

async function replayDeadLetter(id) {
    try {
        await DeadLetterCommon.api(`/api/v1/admin/dead-letters/${id}/replay`, {
            token: deadLetterState.token,
            method: "POST"
        });
        setDeadLetterBanner("回放请求已提交。", "success");
        await loadDeadLetters(true);
    } catch (error) {
        if (error instanceof DeadLetterCommon.AuthExpiredError) {
            handleDeadLetterAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setDeadLetterBanner(error.message || "回放失败。", "error");
    }
}

function formatPayloadJson(payloadJson) {
    if (!payloadJson) {
        return "未记录消息载荷";
    }
    try {
        return JSON.stringify(JSON.parse(payloadJson), null, 2);
    } catch (error) {
        return payloadJson;
    }
}

function buildParseTaskLink(record) {
    const params = new URLSearchParams({
        taskId: String(record.taskId),
        source: "dead-letters"
    });
    if (record.documentId) {
        params.set("documentId", String(record.documentId));
    }
    if (record.taskType) {
        params.set("taskType", record.taskType);
    }
    return `/admin/parse-tasks?${params.toString()}`;
}

function buildDocumentLink(record) {
    const params = new URLSearchParams({
        documentId: String(record.documentId),
        source: "dead-letters"
    });
    return `/admin/documents?${params.toString()}`;
}

function applyDeadLetterFilterInputs() {
    deadLetterElements.replayStatusFilter.value = deadLetterState.filters.replayStatus;
    deadLetterElements.taskTypeFilter.value = deadLetterState.filters.taskType;
    deadLetterElements.taskIdFilter.value = deadLetterState.filters.taskId;
    deadLetterElements.documentIdFilter.value = deadLetterState.filters.documentId;
    deadLetterElements.keywordFilter.value = deadLetterState.filters.keyword;
}

function readDeadLetterFiltersFromInputs() {
    deadLetterState.filters.replayStatus = deadLetterElements.replayStatusFilter.value;
    deadLetterState.filters.taskType = deadLetterElements.taskTypeFilter.value;
    deadLetterState.filters.taskId = deadLetterElements.taskIdFilter.value.trim();
    deadLetterState.filters.documentId = deadLetterElements.documentIdFilter.value.trim();
    deadLetterState.filters.keyword = deadLetterElements.keywordFilter.value.trim();
}

function syncDeadLetterQuery() {
    DeadLetterCommon.updateQuery({
        pageNo: deadLetterState.pageNo,
        deadLetterId: deadLetterState.selectedId,
        replayStatus: deadLetterState.filters.replayStatus,
        taskType: deadLetterState.filters.taskType,
        taskId: deadLetterState.filters.taskId,
        documentId: deadLetterState.filters.documentId,
        keyword: deadLetterState.filters.keyword,
        source: deadLetterState.query.source,
        returnUrl: deadLetterState.query.returnUrl,
        returnLabel: deadLetterState.query.returnLabel
    });
}

function buildDeadLetterReturnLink() {
    const returnUrl = normalizeDeadLetterReturnUrl(deadLetterState.query.returnUrl);
    if (!returnUrl) {
        return "";
    }
    const label = deadLetterState.query.returnLabel || "来源页面";
    return `<a class="link-button context-return-button" href="${DeadLetterCommon.escapeAttr(returnUrl)}">返回${DeadLetterCommon.escapeHtml(label)}</a>`;
}

function normalizeDeadLetterReturnUrl(value) {
    const url = String(value || "").trim();
    if (!url || !url.startsWith("/") || url.startsWith("//") || url.toLowerCase().startsWith("javascript:")) {
        return "";
    }
    return url;
}

function handleDeadLetterAuthFailure(error, message) {
    console.error(error);
    logoutDeadLetterConsole();
    setDeadLetterBanner(message, "error");
}

function setDeadLetterBanner(message, type = "info") {
    deadLetterElements.statusBanner.hidden = false;
    deadLetterElements.statusBanner.className = `status-banner ${type}`;
    deadLetterElements.statusBanner.textContent = message;
}



