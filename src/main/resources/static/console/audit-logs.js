const AuditCommon = window.KnowFlowConsoleCommon;

const auditState = {
    token: AuditCommon.readToken(),
    query: AuditCommon.parseQuery(),
    user: null,
    menus: [],
    records: [],
    selectedLog: null,
    selectedLogId: null,
    pageNo: 1,
    pageSize: 10,
    total: 0,
    filters: {
        keyword: "",
        moduleCode: "",
        actionCode: "",
        bizType: "",
        successFlag: ""
    }
};

const auditElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    cacheAuditElements();
    initAuditStateFromQuery();
    bindAuditEvents();
    if (auditState.token) {
        await restoreAuditSession();
        return;
    }
    showAuditLoggedOutState();
});

function cacheAuditElements() {
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
        "moduleFilter",
        "actionFilter",
        "bizTypeFilter",
        "successFilter",
        "searchButton",
        "resetButton",
        "auditPageMeta",
        "auditTable",
        "auditPaginationText",
        "prevPageButton",
        "nextPageButton",
        "auditDetailBody"
    ].forEach((id) => {
        auditElements[id] = document.getElementById(id);
    });
}

function initAuditStateFromQuery() {
    const query = auditState.query;
    auditState.pageNo = Number(query.pageNo || 1);
    auditState.selectedLogId = query.logId ? Number(query.logId) : null;
    auditState.filters.keyword = query.keyword || "";
    auditState.filters.moduleCode = query.moduleCode || "";
    auditState.filters.actionCode = query.actionCode || "";
    auditState.filters.bizType = query.bizType || "";
    auditState.filters.successFlag = query.successFlag || "";
    applyAuditFilterInputs();
}

function bindAuditEvents() {
    auditElements.loginForm.addEventListener("submit", handleAuditLogin);
    auditElements.refreshButton.addEventListener("click", () => loadAuditConsole(true));
    auditElements.logoutButton.addEventListener("click", logoutAuditConsole);
    auditElements.searchButton.addEventListener("click", async () => {
        readAuditFiltersFromInputs();
        auditState.pageNo = 1;
        await loadAuditConsole(false);
    });
    auditElements.resetButton.addEventListener("click", async () => {
        auditState.filters = {
            keyword: "",
            moduleCode: "",
            actionCode: "",
            bizType: "",
            successFlag: ""
        };
        auditState.pageNo = 1;
        auditState.selectedLogId = null;
        applyAuditFilterInputs();
        await loadAuditConsole(false);
    });
    auditElements.prevPageButton.addEventListener("click", async () => {
        if (auditState.pageNo <= 1) {
            return;
        }
        auditState.pageNo -= 1;
        await loadAuditConsole(true);
    });
    auditElements.nextPageButton.addEventListener("click", async () => {
        const totalPage = Math.max(1, Math.ceil(auditState.total / auditState.pageSize));
        if (auditState.pageNo >= totalPage) {
            return;
        }
        auditState.pageNo += 1;
        await loadAuditConsole(true);
    });
}

async function restoreAuditSession() {
    try {
        const [user, menus] = await Promise.all([
            AuditCommon.api("/api/v1/auth/me", { token: auditState.token }),
            AuditCommon.api("/api/v1/auth/menus", { token: auditState.token })
        ]);
        auditState.user = user;
        auditState.menus = menus || [];
        showAuditLoggedInState();
        await loadAuditConsole(true);
    } catch (error) {
        handleAuditAuthFailure(error, "登录状态已失效，请重新登录。");
    }
}

async function handleAuditLogin(event) {
    event.preventDefault();
    const username = auditElements.usernameInput.value.trim();
    const password = auditElements.passwordInput.value;
    if (!username || !password) {
        setAuditBanner("请输入用户名和密码。", "error");
        return;
    }

    auditElements.loginButton.disabled = true;
    setAuditBanner("正在登录审计日志管理台...", "info");
    try {
        const data = await AuditCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        auditState.token = data.token;
        AuditCommon.saveToken(data.token);
        auditElements.passwordInput.value = "";
        await restoreAuditSession();
        setAuditBanner("审计日志管理台已就绪。", "success");
    } catch (error) {
        setAuditBanner(error.message || "登录失败。", "error");
    } finally {
        auditElements.loginButton.disabled = false;
    }
}

function logoutAuditConsole() {
    auditState.token = "";
    auditState.user = null;
    auditState.menus = [];
    auditState.records = [];
    auditState.selectedLog = null;
    auditState.selectedLogId = null;
    auditState.total = 0;
    AuditCommon.clearToken();
    AuditCommon.updateQuery({ logId: null });
    showAuditLoggedOutState();
    setAuditBanner("已退出审计日志管理台。", "success");
}

function showAuditLoggedOutState() {
    auditElements.loginPanel.hidden = false;
    auditElements.workspace.hidden = true;
    auditElements.sessionName.textContent = "未登录";
    auditElements.sessionMeta.textContent = "请先登录后加载审计日志";
}

function showAuditLoggedInState() {
    auditElements.loginPanel.hidden = true;
    auditElements.workspace.hidden = false;
    auditElements.sessionName.textContent = auditState.user.realName || auditState.user.username;
    auditElements.sessionMeta.textContent = AuditCommon.formatUserSessionMeta(auditState.user);
}

async function loadAuditConsole(keepSelection) {
    if (!auditState.token) {
        showAuditLoggedOutState();
        return;
    }
    renderAuditListLoading();
    syncAuditQuery();

    try {
        const pageData = await AuditCommon.api(buildAuditListUrl(), { token: auditState.token });
        auditState.records = pageData.records || [];
        auditState.total = pageData.total || 0;
        auditState.pageNo = pageData.pageNo || auditState.pageNo;
        auditState.pageSize = pageData.pageSize || auditState.pageSize;
        renderAuditList();

        const preferredLogId = resolvePreferredLogId(keepSelection);
        if (preferredLogId) {
            await loadAuditDetail(preferredLogId, true);
        } else {
            auditState.selectedLog = null;
            renderAuditDetail();
        }
    } catch (error) {
        if (error instanceof AuditCommon.AuthExpiredError) {
            handleAuditAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        setAuditBanner(error.message || "审计日志加载失败。", "error");
        auditElements.auditTable.innerHTML = AuditCommon.renderStateBlock({
            type: "error",
            title: "审计日志加载失败",
            message: error.message || "请稍后重试，或检查当前账号是否具备审计日志查看权限。"
        });
    }
}

function buildAuditListUrl() {
    const params = new URLSearchParams({
        pageNo: String(auditState.pageNo),
        pageSize: String(auditState.pageSize)
    });
    if (auditState.filters.keyword) {
        params.set("keyword", auditState.filters.keyword);
    }
    if (auditState.filters.moduleCode) {
        params.set("moduleCode", auditState.filters.moduleCode);
    }
    if (auditState.filters.actionCode) {
        params.set("actionCode", auditState.filters.actionCode);
    }
    if (auditState.filters.bizType) {
        params.set("bizType", auditState.filters.bizType);
    }
    if (auditState.filters.successFlag !== "") {
        params.set("successFlag", auditState.filters.successFlag);
    }
    return `/api/v1/admin/audit-logs?${params.toString()}`;
}

function renderAuditListLoading() {
    auditElements.auditPageMeta.textContent = "正在同步审计日志";
    auditElements.auditTable.innerHTML = AuditCommon.renderStateBlock({
        type: "loading",
        title: "正在加载审计日志",
        message: "系统正在同步模块、动作、操作人和执行结果。"
    });
    auditElements.auditPaginationText.textContent = "加载中";
}

function renderAuditList() {
    const totalPage = Math.max(1, Math.ceil(auditState.total / auditState.pageSize));
    auditElements.auditPageMeta.textContent = `共 ${AuditCommon.formatNumber(auditState.total)} 条审计日志`;
    auditElements.auditPaginationText.textContent = `第 ${auditState.pageNo} / ${totalPage} 页`;
    auditElements.prevPageButton.disabled = auditState.pageNo <= 1;
    auditElements.nextPageButton.disabled = auditState.pageNo >= totalPage;

    if (!auditState.records.length) {
        auditElements.auditTable.innerHTML = AuditCommon.renderStateBlock({
            title: "暂无匹配日志",
            message: "你可以切换模块、动作或结果条件继续筛选。",
            actionHref: "/admin/dashboard",
            actionText: "回到运营看板"
        });
        return;
    }

    auditElements.auditTable.innerHTML = `
        <table class="data-table">
            <thead>
            <tr>
                <th>摘要</th>
                <th>模块 / 动作</th>
                <th>操作人</th>
                <th>结果 / 时间</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody>
            ${auditState.records.map((record) => `
                <tr class="${record.id === auditState.selectedLogId ? "active-row" : ""}">
                    <td>
                        <div class="table-question">
                            <strong>${AuditCommon.escapeHtml(formatAuditSummary(record))}</strong>
                            <div class="meta-line">${AuditCommon.escapeHtml(formatAuditBizRef(record))}</div>
                        </div>
                    </td>
                    <td>
                        <div class="meta-line">${AuditCommon.escapeHtml(record.moduleCode || "-")}</div>
                        <div class="tiny-line">${AuditCommon.escapeHtml(record.actionCode || "-")}</div>
                    </td>
                    <td>
                        <div class="meta-line">${AuditCommon.escapeHtml(formatAuditOperator(record))}</div>
                        <div class="tiny-line">${AuditCommon.escapeHtml(record.operatorUsername || "")}</div>
                    </td>
                    <td>
                        <div class="chips">
                            <span class="status-pill ${record.successFlag ? "status-success" : "status-danger"}">${formatAuditResult(record.successFlag)}</span>
                        </div>
                        <div class="tiny-line">${AuditCommon.escapeHtml(AuditCommon.formatDateTime(record.createdAt))}</div>
                    </td>
                    <td><button class="link-button table-row-button" type="button" data-log-id="${record.id}">查看详情</button></td>
                </tr>
            `).join("")}
            </tbody>
        </table>
    `;

    auditElements.auditTable.querySelectorAll("[data-log-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await loadAuditDetail(Number(button.dataset.logId), false);
        });
    });
}

function resolvePreferredLogId(keepSelection) {
    if (keepSelection && auditState.selectedLogId) {
        return auditState.selectedLogId;
    }
    if (auditState.query.logId) {
        return Number(auditState.query.logId);
    }
    if (auditState.selectedLogId) {
        return auditState.selectedLogId;
    }
    return auditState.records[0]?.id || null;
}

async function loadAuditDetail(logId, silent) {
    if (!logId) {
        return;
    }
    auditState.selectedLogId = logId;
    syncAuditQuery();
    renderAuditList();
    if (!silent) {
        auditElements.auditDetailBody.innerHTML = AuditCommon.renderStateBlock({
            type: "loading",
            title: "正在加载日志详情",
            message: "正在读取业务对象、请求入口和执行结果。"
        });
    }

    try {
        auditState.selectedLog = await AuditCommon.api(`/api/v1/admin/audit-logs/${logId}`, { token: auditState.token });
        renderAuditDetail();
    } catch (error) {
        if (error instanceof AuditCommon.AuthExpiredError) {
            handleAuditAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        auditElements.auditDetailBody.innerHTML = AuditCommon.renderStateBlock({
            type: "error",
            title: "日志详情加载失败",
            message: error.message || "请稍后重试，或从左侧重新选择日志。"
        });
        setAuditBanner(error.message || "审计详情加载失败。", "error");
    }
}

function renderAuditDetail() {
    const record = auditState.selectedLog;
    if (!record) {
        auditElements.auditDetailBody.innerHTML = AuditCommon.renderStateBlock({
            title: "选择一条日志开始查看",
            message: "左侧点选日志后，这里会展示业务对象、请求入口和执行结果。"
        });
        return;
    }

    auditElements.auditDetailBody.innerHTML = `
        <section class="summary-card">
            <span class="panel-kicker">当前选中日志</span>
            <strong>${AuditCommon.escapeHtml(formatAuditSummary(record))}</strong>
            <div class="chips">
                <span class="status-pill">${AuditCommon.escapeHtml(record.moduleCode || "-")}</span>
                <span class="status-pill">${AuditCommon.escapeHtml(record.actionCode || "-")}</span>
                <span class="status-pill ${record.successFlag ? "status-success" : "status-danger"}">${formatAuditResult(record.successFlag)}</span>
                <span class="status-pill">${AuditCommon.escapeHtml(AuditCommon.formatDateTime(record.createdAt))}</span>
            </div>
            <p>${AuditCommon.escapeHtml(formatAuditBizRef(record))}</p>
            <div class="action-row" style="margin-top:14px;">
                <a class="link-button" href="/admin/dashboard">回到运营看板</a>
                ${renderAuditBizLink(record)}
            </div>
        </section>

        <section class="detail-columns">
            <article class="detail-card">
                <strong>业务对象</strong>
                <p>${AuditCommon.escapeHtml(AuditCommon.enumLabel("auditBizType", record.bizType, record.bizType || "-"))} · ${AuditCommon.escapeHtml(record.bizId || "-")}</p>
                <p>${AuditCommon.escapeHtml(record.bizNo || "暂无业务编号")}</p>
            </article>
            <article class="detail-card">
                <strong>操作人</strong>
                <p>${AuditCommon.escapeHtml(formatAuditOperator(record))}</p>
                <p>${AuditCommon.escapeHtml(record.operatorUsername || "")}</p>
            </article>
        </section>

        <section class="timeline-grid">
            <article class="detail-card">
                <strong>请求入口</strong>
                <div class="section-stack">
                    <article class="timeline-item">
                        <strong>${AuditCommon.escapeHtml(record.requestMethod || "-")}</strong>
                        <p>${AuditCommon.escapeHtml(record.requestUri || "暂无请求地址")}</p>
                    </article>
                </div>
            </article>
            <article class="detail-card">
                <strong>执行结果</strong>
                <div class="section-stack">
                    <article class="timeline-item">
                        <strong>${record.successFlag ? "执行成功" : "执行失败"}</strong>
                        <p>${AuditCommon.escapeHtml(record.errorMessage || "无错误信息")}</p>
                    </article>
                </div>
            </article>
        </section>
    `;
}

function renderAuditBizLink(record) {
    if (!record.bizId || !record.bizType) {
        return "";
    }
    if (record.bizType === "TICKET") {
        return `<a class="link-button" href="/admin/tickets?ticketId=${record.bizId}&source=audit-log&logId=${record.id}">打开工单</a>`;
    }
    if (record.bizType === "KNOWLEDGE_DRAFT") {
        return `<a class="link-button" href="/admin/knowledge-drafts?draftId=${record.bizId}&source=audit-log&logId=${record.id}">打开草稿</a>`;
    }
    return "";
}

function formatAuditSummary(record) {
    return record.operationSummary || "暂无摘要";
}

function formatAuditBizRef(record) {
    return record.bizNo || `${AuditCommon.enumLabel("auditBizType", record.bizType, record.bizType || "业务")} #${record.bizId || "N/A"}`;
}

function formatAuditOperator(record) {
    return record.operatorRealName || record.operatorUsername || "系统";
}

function formatAuditResult(successFlag) {
    return successFlag ? "成功" : "失败";
}

function applyAuditFilterInputs() {
    auditElements.keywordFilter.value = auditState.filters.keyword;
    auditElements.moduleFilter.value = auditState.filters.moduleCode;
    auditElements.actionFilter.value = auditState.filters.actionCode;
    auditElements.bizTypeFilter.value = auditState.filters.bizType;
    auditElements.successFilter.value = auditState.filters.successFlag;
}

function readAuditFiltersFromInputs() {
    auditState.filters.keyword = auditElements.keywordFilter.value.trim();
    auditState.filters.moduleCode = auditElements.moduleFilter.value;
    auditState.filters.actionCode = auditElements.actionFilter.value;
    auditState.filters.bizType = auditElements.bizTypeFilter.value;
    auditState.filters.successFlag = auditElements.successFilter.value;
}

function syncAuditQuery() {
    AuditCommon.updateQuery({
        pageNo: auditState.pageNo,
        logId: auditState.selectedLogId,
        keyword: auditState.filters.keyword,
        moduleCode: auditState.filters.moduleCode,
        actionCode: auditState.filters.actionCode,
        bizType: auditState.filters.bizType,
        successFlag: auditState.filters.successFlag
    });
}

function handleAuditAuthFailure(error, message) {
    console.error(error);
    logoutAuditConsole();
    setAuditBanner(message, "error");
}

function setAuditBanner(message, type = "info") {
    auditElements.statusBanner.hidden = false;
    auditElements.statusBanner.className = `status-banner ${type}`;
    auditElements.statusBanner.textContent = message;
}



