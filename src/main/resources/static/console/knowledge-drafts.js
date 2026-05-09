const DraftCommon = window.KnowFlowConsoleCommon;

const draftState = {
    token: DraftCommon.readToken(),
    query: DraftCommon.parseQuery(),
    user: null,
    menus: [],
    knowledgeBases: [],
    drafts: [],
    selectedDraft: null,
    selectedDraftId: null,
    pageNo: 1,
    pageSize: 10,
    total: 0,
    lastRefreshAt: null,
    filters: {
        knowledgeBaseId: "",
        status: "",
        draftType: ""
    }
};

const draftElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    cacheDraftElements();
    initDraftStateFromQuery();
    bindDraftEvents();
    if (draftState.token) {
        await restoreDraftSession();
        return;
    }
    showDraftLoggedOutState();
});

function cacheDraftElements() {
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
        "statusFilter",
        "draftTypeFilter",
        "capabilityTags",
        "lastRefreshText",
        "searchButton",
        "resetButton",
        "contextBanner",
        "draftPageMeta",
        "draftTable",
        "draftPaginationText",
        "prevPageButton",
        "nextPageButton",
        "draftDetailBody"
    ].forEach((id) => {
        draftElements[id] = document.getElementById(id);
    });
}

function initDraftStateFromQuery() {
    const query = draftState.query;
    draftState.pageNo = Number(query.pageNo || 1);
    draftState.selectedDraftId = query.draftId ? Number(query.draftId) : null;
    draftState.filters.knowledgeBaseId = query.knowledgeBaseId || "";
    draftState.filters.status = query.status || "";
    draftState.filters.draftType = query.draftType || "";
    applyDraftFilterInputs();
}

function bindDraftEvents() {
    draftElements.loginForm.addEventListener("submit", handleDraftLogin);
    draftElements.refreshButton.addEventListener("click", () => loadDraftConsole(true));
    draftElements.logoutButton.addEventListener("click", logoutDraftConsole);
    draftElements.searchButton.addEventListener("click", async () => {
        readDraftFiltersFromInputs();
        draftState.pageNo = 1;
        await loadDraftConsole(false);
    });
    draftElements.resetButton.addEventListener("click", async () => {
        draftState.filters = { knowledgeBaseId: "", status: "", draftType: "" };
        draftState.pageNo = 1;
        draftState.selectedDraftId = null;
        draftState.query = {};
        applyDraftFilterInputs();
        await loadDraftConsole(false);
    });
    draftElements.prevPageButton.addEventListener("click", async () => {
        if (draftState.pageNo <= 1) {
            return;
        }
        draftState.pageNo -= 1;
        await loadDraftConsole(true);
    });
    draftElements.nextPageButton.addEventListener("click", async () => {
        const totalPage = Math.max(1, Math.ceil(draftState.total / draftState.pageSize));
        if (draftState.pageNo >= totalPage) {
            return;
        }
        draftState.pageNo += 1;
        await loadDraftConsole(true);
    });
}

async function restoreDraftSession() {
    try {
        const [user, menus, knowledgeBases] = await Promise.all([
            DraftCommon.api("/api/v1/auth/me", { token: draftState.token }),
            DraftCommon.api("/api/v1/auth/menus", { token: draftState.token }),
            DraftCommon.api("/api/v1/admin/knowledge-drafts/knowledge-bases/options", { token: draftState.token })
        ]);
        draftState.user = user;
        draftState.menus = menus || [];
        draftState.knowledgeBases = knowledgeBases || [];
        renderDraftKnowledgeBaseOptions();
        renderDraftCapabilities();
        showDraftLoggedInState();
        renderDraftContextBanner();
        await loadDraftConsole(true);
    } catch (error) {
        handleDraftAuthFailure(error, "会话已失效，请重新登录。");
    }
}

async function handleDraftLogin(event) {
    event.preventDefault();
    const username = draftElements.usernameInput.value.trim();
    const password = draftElements.passwordInput.value;
    if (!username || !password) {
        setDraftBanner("请输入账号和密码。", "error");
        return;
    }

    draftElements.loginButton.disabled = true;
    setDraftBanner("正在登录知识草稿台...", "info");
    try {
        const data = await DraftCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        draftState.token = data.token;
        DraftCommon.saveToken(data.token);
        draftElements.passwordInput.value = "";
        await restoreDraftSession();
        setDraftBanner("登录成功，草稿队列已加载。", "success");
    } catch (error) {
        setDraftBanner(error.message || "登录失败。", "error");
    } finally {
        draftElements.loginButton.disabled = false;
    }
}

function logoutDraftConsole() {
    draftState.token = "";
    draftState.user = null;
    draftState.menus = [];
    draftState.selectedDraft = null;
    draftState.selectedDraftId = null;
    draftState.total = 0;
    DraftCommon.clearToken();
    DraftCommon.updateQuery({ draftId: null });
    showDraftLoggedOutState();
    setDraftBanner("已退出知识草稿台。", "success");
}

function showDraftLoggedOutState() {
    draftElements.loginPanel.hidden = false;
    draftElements.workspace.hidden = true;
    draftElements.sessionName.textContent = "未登录";
    draftElements.sessionMeta.textContent = "请先登录后加载知识草稿";
}

function showDraftLoggedInState() {
    draftElements.loginPanel.hidden = true;
    draftElements.workspace.hidden = false;
    draftElements.sessionName.textContent = draftState.user.realName || draftState.user.username;
    draftElements.sessionMeta.textContent = DraftCommon.formatUserSessionMeta(draftState.user);
}

function renderDraftCapabilities() {
    if (!draftElements.capabilityTags) {
        return;
    }
    const tags = [];
    tags.push(`<span class="tag">可查看草稿</span>`);
    if (DraftCommon.hasRole(draftState.user, "TENANT_ADMIN", "SUPPORT_AGENT", "KNOWLEDGE_OPERATOR")) {
        tags.push(`<span class="tag">可编辑草稿</span>`);
    }
    if (DraftCommon.hasRole(draftState.user, "TENANT_ADMIN", "KNOWLEDGE_OPERATOR")) {
        tags.push(`<span class="tag">可审核/发布</span>`);
    }
    draftElements.capabilityTags.innerHTML = tags.join("");
}

function renderDraftContextBanner() {
    const source = draftState.query.source;
    const ticketId = draftState.query.ticketId;
    const messageId = draftState.query.messageId;
    if (!source && !ticketId && !messageId) {
        draftElements.contextBanner.hidden = true;
        draftElements.contextBanner.textContent = "";
        return;
    }
    draftElements.contextBanner.hidden = false;
    const sourceLabel = DraftCommon.enumLabel("pageSource", source, source || "其它页面");
    draftElements.contextBanner.textContent = ticketId
        ? `当前为来自 ${sourceLabel} 的深链草稿视图，关联来源工单 #${ticketId}${messageId ? `，来源问答 #${messageId}` : ""}。`
        : `当前为来自 ${sourceLabel} 的深链草稿视图${messageId ? `，来源问答 #${messageId}` : ""}。`;
}

async function loadDraftConsole(keepSelection) {
    if (!draftState.token) {
        showDraftLoggedOutState();
        return;
    }
    renderDraftListLoading();
    syncDraftQuery();

    try {
        const pageData = await DraftCommon.api(buildDraftListUrl(), { token: draftState.token });
        draftState.drafts = pageData.records || [];
        draftState.total = pageData.total || 0;
        draftState.pageNo = pageData.pageNo || draftState.pageNo;
        draftState.pageSize = pageData.pageSize || draftState.pageSize;
        draftState.lastRefreshAt = new Date();
        draftElements.lastRefreshText.textContent = DraftCommon.formatDateTime(draftState.lastRefreshAt);
        renderDraftList();

        const preferredDraftId = resolvePreferredDraftId(keepSelection);
        if (preferredDraftId) {
            await loadDraftDetail(preferredDraftId, true);
        } else {
            draftState.selectedDraft = null;
            renderDraftDetail();
        }
    } catch (error) {
        if (error instanceof DraftCommon.AuthExpiredError) {
            handleDraftAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        setDraftBanner(error.message || "加载草稿失败。", "error");
        draftElements.draftTable.innerHTML = DraftCommon.renderStateBlock({
            type: "error",
            title: "草稿加载失败",
            message: error.message || "请稍后重试，或检查当前账号是否具备知识草稿权限。"
        });
    }
}

function renderDraftListLoading() {
    draftElements.draftPageMeta.textContent = "正在同步草稿队列";
    draftElements.draftTable.innerHTML = DraftCommon.renderStateBlock({
        type: "loading",
        title: "正在加载草稿列表",
        message: "系统正在同步来源工单、审核状态和发布结果。"
    });
    draftElements.draftPaginationText.textContent = "加载中";
}

function buildDraftListUrl() {
    const params = new URLSearchParams({
        pageNo: String(draftState.pageNo),
        pageSize: String(draftState.pageSize)
    });
    if (draftState.filters.knowledgeBaseId) {
        params.set("knowledgeBaseId", draftState.filters.knowledgeBaseId);
    }
    if (draftState.filters.status) {
        params.set("status", draftState.filters.status);
    }
    if (draftState.filters.draftType) {
        params.set("draftType", draftState.filters.draftType);
    }
    return `/api/v1/admin/knowledge-drafts?${params.toString()}`;
}

function renderDraftList() {
    const totalPage = Math.max(1, Math.ceil(draftState.total / draftState.pageSize));
    draftElements.draftPageMeta.textContent = `共 ${DraftCommon.formatNumber(draftState.total)} 条草稿`;
    draftElements.draftPaginationText.textContent = `第 ${draftState.pageNo} / ${totalPage} 页`;
    draftElements.prevPageButton.disabled = draftState.pageNo <= 1;
    draftElements.nextPageButton.disabled = draftState.pageNo >= totalPage;

    if (!draftState.drafts.length) {
        draftElements.draftTable.innerHTML = DraftCommon.renderStateBlock({
            title: "暂无匹配草稿",
            message: "试试调整筛选条件，或先从工单页生成新的知识草稿。",
            actionHref: "/admin/tickets",
            actionText: "去工单页"
        });
        return;
    }

    draftElements.draftTable.innerHTML = `
        <table class="data-table">
            <thead>
            <tr>
                <th>草稿</th>
                <th>知识库</th>
                <th>状态/类型</th>
                <th>来源工单</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody>
            ${draftState.drafts.map((draft) => `
                <tr class="${draft.id === draftState.selectedDraftId ? "active-row" : ""}">
                    <td>
                        <div class="table-question">
                            <strong>${DraftCommon.escapeHtml(draft.title || `草稿 #${draft.id}`)}</strong>
                            <div class="meta-line">${DraftCommon.escapeHtml(DraftCommon.truncateText(draft.questionText || "", 70) || "暂无问题内容")}</div>
                        </div>
                    </td>
                    <td>
                        <div class="meta-line">${DraftCommon.escapeHtml(draft.knowledgeBaseName || "未绑定知识库")}</div>
                        <div class="tiny-line">${DraftCommon.escapeHtml(DraftCommon.formatDateTime(draft.updatedAt || draft.createdAt))}</div>
                    </td>
                    <td>
                        <div class="chips">
                            <span class="status-pill ${DraftCommon.statusClass(draft.status)}">${DraftCommon.escapeHtml(DraftCommon.enumLabel("draftStatus", draft.status, "未知状态"))}</span>
                            <span class="status-pill">${DraftCommon.escapeHtml(DraftCommon.enumLabel("draftType", draft.draftType, draft.draftType || "未设置类型"))}</span>
                        </div>
                    </td>
                    <td>
                        <div class="meta-line">${DraftCommon.escapeHtml(draft.sourceTicketNo || "无来源工单")}</div>
                        <div class="tiny-line">${draft.publishedAt ? `发布于 ${DraftCommon.escapeHtml(DraftCommon.formatDateTime(draft.publishedAt))}` : "尚未发布"}</div>
                    </td>
                    <td><button class="link-button table-row-button" type="button" data-draft-id="${draft.id}">查看草稿</button></td>
                </tr>
            `).join("")}
            </tbody>
        </table>
    `;

    draftElements.draftTable.querySelectorAll("[data-draft-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await loadDraftDetail(Number(button.dataset.draftId), false);
        });
    });
}

function resolvePreferredDraftId(keepSelection) {
    if (keepSelection && draftState.selectedDraftId) {
        return draftState.selectedDraftId;
    }
    if (draftState.query.draftId) {
        return Number(draftState.query.draftId);
    }
    if (draftState.selectedDraftId) {
        return draftState.selectedDraftId;
    }
    return draftState.drafts[0]?.id || null;
}

async function loadDraftDetail(draftId, silent) {
    if (!draftId) {
        return;
    }
    draftState.selectedDraftId = draftId;
    syncDraftQuery();
    renderDraftList();
    if (!silent) {
        draftElements.draftDetailBody.innerHTML = DraftCommon.renderStateBlock({
            type: "loading",
            title: "正在加载草稿详情",
            message: "正在读取来源工单、知识内容、审核备注和发布状态。"
        });
    }

    try {
        draftState.selectedDraft = await DraftCommon.api(`/api/v1/admin/knowledge-drafts/${draftId}`, { token: draftState.token });
        renderDraftDetail();
    } catch (error) {
        if (error instanceof DraftCommon.AuthExpiredError) {
            handleDraftAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        draftElements.draftDetailBody.innerHTML = DraftCommon.renderStateBlock({
            type: "error",
            title: "草稿详情加载失败",
            message: error.message || "请稍后重试，或从左侧重新选择草稿。"
        });
        setDraftBanner(error.message || "草稿详情加载失败。", "error");
    }
}

function renderDraftDetail() {
    const draft = draftState.selectedDraft;
    if (!draft) {
        draftElements.draftDetailBody.innerHTML = DraftCommon.renderStateBlock({
            title: "选择一条草稿开始处理",
            message: "左侧点选草稿后，这里会展示来源工单、知识内容以及审核发布动作。"
        });
        return;
    }

    const canReview = DraftCommon.hasRole(draftState.user, "TENANT_ADMIN", "KNOWLEDGE_OPERATOR");
    const editable = draft.status !== "PUBLISHED";
    const publishable = canReview && draft.status === "APPROVED";

    draftElements.draftDetailBody.innerHTML = `
        <section class="summary-card">
            <span class="panel-kicker">当前选中草稿</span>
            <strong>${DraftCommon.escapeHtml(draft.title || `草稿 #${draft.id}`)}</strong>
            <div class="chips">
                <span class="status-pill ${DraftCommon.statusClass(draft.status)}">${DraftCommon.escapeHtml(DraftCommon.enumLabel("draftStatus", draft.status, "未知状态"))}</span>
                <span class="status-pill">${DraftCommon.escapeHtml(DraftCommon.enumLabel("draftType", draft.draftType, draft.draftType || "未设置类型"))}</span>
                <span class="status-pill">${DraftCommon.escapeHtml(draft.knowledgeBaseName || "未绑定知识库")}</span>
                <span class="status-pill">${DraftCommon.escapeHtml(DraftCommon.formatDateTime(draft.createdAt))}</span>
            </div>
            <p>${DraftCommon.escapeHtml(draft.reviewRemark || "当前还没有审核备注。")}</p>
            <div class="action-row" style="margin-top:14px;">
                <a class="link-button" href="/admin/dashboard">回到运营看板</a>
                ${draft.sourceTicketId ? `<a class="link-button" href="${buildDraftTicketLink(draft)}">返回来源工单</a>` : ""}
                ${draft.sourceQaMessageId ? `<a class="link-button" href="${buildDraftQaLink(draft)}">查看来源问答</a>` : ""}
            </div>
        </section>

        <section class="detail-columns">
            <article class="detail-card">
                <strong>来源工单</strong>
                <p>${DraftCommon.escapeHtml(draft.sourceTicketNo || "无来源工单")} · ${DraftCommon.escapeHtml(draft.sourceTicketTitle || "无来源标题")}</p>
                <div class="footer-actions" style="margin-top:12px;">
                    ${draft.sourceTicketId ? `<a class="link-button" href="${buildDraftTicketLink(draft)}">打开来源工单</a>` : ""}
                    ${draft.sourceQaMessageId ? `<a class="link-button" href="${buildDraftQaLink(draft)}">打开来源问答</a>` : ""}
                </div>
            </article>
            <article class="detail-card">
                <strong>发布状态</strong>
                <p>${draft.publishedDocumentId ? `已发布文档 #${DraftCommon.escapeHtml(draft.publishedDocumentId)}，发布时间 ${DraftCommon.escapeHtml(DraftCommon.formatDateTime(draft.publishedAt))}` : "当前尚未发布到知识库。"}</p>
            </article>
            <article class="detail-card">
                <strong>来源问题</strong>
                <p>${DraftCommon.escapeHtml(draft.sourceQuestionText || draft.questionText || "暂无来源问题")}</p>
            </article>
        </section>

        <section class="detail-card">
            <strong>草稿编辑</strong>
            <div class="form-grid">
                <label class="field">
                    <span>知识库</span>
                    <select id="draftKnowledgeBaseSelect" ${editable ? "" : "disabled"}>${buildDraftKnowledgeBaseOptions(draft.knowledgeBaseId)}</select>
                </label>
                <label class="field">
                    <span>草稿类型</span>
                    <select id="draftTypeSelect" ${editable ? "" : "disabled"}>
                        <option value="FAQ" ${draft.draftType === "FAQ" ? "selected" : ""}>常见问答</option>
                        <option value="ARTICLE" ${draft.draftType === "ARTICLE" ? "selected" : ""}>知识文章</option>
                    </select>
                </label>
                <label class="field full-span">
                    <span>标题</span>
                    <input id="draftTitleInput" type="text" value="${DraftCommon.escapeAttr(draft.title || "")}" ${editable ? "" : "disabled"}>
                </label>
                <label class="field full-span">
                    <span>问题文本</span>
                    <textarea id="draftQuestionInput" ${editable ? "" : "disabled"}>${DraftCommon.escapeHtml(draft.questionText || "")}</textarea>
                </label>
                <label class="field full-span">
                    <span>答案文本</span>
                    <textarea id="draftAnswerInput" ${editable ? "" : "disabled"}>${DraftCommon.escapeHtml(draft.answerText || "")}</textarea>
                </label>
            </div>
            ${editable ? `
                <div class="footer-actions" style="margin-top:14px;">
                    <button id="saveDraftButton" class="primary-button" type="button">保存草稿</button>
                </div>
            ` : ""}
        </section>

        ${canReview ? `
            <section class="detail-actions">
                <article class="detail-card">
                    <strong>审核决策</strong>
                    <label class="field">
                        <span>审核备注</span>
                        <textarea id="reviewRemarkInput" placeholder="填写审核意见，帮助后续知识运营追踪">${DraftCommon.escapeHtml(draft.reviewRemark || "")}</textarea>
                    </label>
                    <div class="footer-actions">
                        <button id="approveDraftButton" class="soft-button" type="button">通过审核</button>
                        <button id="rejectDraftButton" class="ghost-button" type="button">驳回草稿</button>
                        ${publishable ? `<button id="publishDraftButton" class="navy-button" type="button">发布到知识库</button>` : ""}
                    </div>
                </article>
            </section>
        ` : ""}
    `;

    bindDraftDetailActions(draft);
}

function bindDraftDetailActions(draft) {
    bindDraftIfPresent("saveDraftButton", async () => {
        const payload = {
            knowledgeBaseId: Number(document.getElementById("draftKnowledgeBaseSelect")?.value),
            draftType: document.getElementById("draftTypeSelect")?.value || "FAQ",
            title: document.getElementById("draftTitleInput")?.value.trim() || "",
            questionText: document.getElementById("draftQuestionInput")?.value.trim() || "",
            answerText: document.getElementById("draftAnswerInput")?.value.trim() || ""
        };
        if (!payload.knowledgeBaseId || !payload.title || !payload.questionText || !payload.answerText) {
            setDraftBanner("知识库、标题、问题文本和答案文本都不能为空。", "error");
            return;
        }
        await postDraftAction(`/api/v1/admin/knowledge-drafts/${draft.id}`, "PUT", payload, "草稿已保存。");
    });

    bindDraftIfPresent("approveDraftButton", async () => {
        await postDraftAction(`/api/v1/admin/knowledge-drafts/${draft.id}/approve`, "POST", {
            reviewRemark: document.getElementById("reviewRemarkInput")?.value || ""
        }, "草稿已通过审核。");
    });

    bindDraftIfPresent("rejectDraftButton", async () => {
        await postDraftAction(`/api/v1/admin/knowledge-drafts/${draft.id}/reject`, "POST", {
            reviewRemark: document.getElementById("reviewRemarkInput")?.value || ""
        }, "草稿已驳回。");
    });

    bindDraftIfPresent("publishDraftButton", async () => {
        await postDraftAction(`/api/v1/admin/knowledge-drafts/${draft.id}/publish`, "POST", null, "草稿已发布到知识库。");
    });
}

async function postDraftAction(url, method, body, successMessage) {
    try {
        await DraftCommon.api(url, {
            token: draftState.token,
            method,
            body: body == null ? null : JSON.stringify(body)
        });
        setDraftBanner(successMessage, "success");
        await loadDraftConsole(true);
    } catch (error) {
        if (error instanceof DraftCommon.AuthExpiredError) {
            handleDraftAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        setDraftBanner(error.message || "草稿操作失败。", "error");
    }
}

function bindDraftIfPresent(id, handler) {
    const element = document.getElementById(id);
    if (element) {
        element.addEventListener("click", handler);
    }
}

function renderDraftKnowledgeBaseOptions() {
    const currentValue = draftState.filters.knowledgeBaseId || "";
    draftElements.knowledgeBaseFilter.innerHTML = `
        <option value="">全部知识库</option>
        ${draftState.knowledgeBases.map((knowledgeBase) => `
            <option value="${knowledgeBase.id}" ${String(currentValue) === String(knowledgeBase.id) ? "selected" : ""}>
                ${DraftCommon.escapeHtml(knowledgeBase.kbName)} (${DraftCommon.escapeHtml(DraftCommon.enumLabel("knowledgeBaseStatus", knowledgeBase.status, "未知状态"))})
            </option>
        `).join("")}
    `;
}

function buildDraftKnowledgeBaseOptions(currentKnowledgeBaseId) {
    return draftState.knowledgeBases.map((knowledgeBase) => `
        <option value="${knowledgeBase.id}" ${String(currentKnowledgeBaseId || "") === String(knowledgeBase.id) ? "selected" : ""}>
            ${DraftCommon.escapeHtml(knowledgeBase.kbName)} (${DraftCommon.escapeHtml(DraftCommon.enumLabel("knowledgeBaseStatus", knowledgeBase.status, "未知状态"))})
        </option>
    `).join("");
}

function buildDraftTicketLink(draft) {
    const params = new URLSearchParams({
        ticketId: String(draft.sourceTicketId),
        source: "draft",
        draftId: String(draft.id)
    });
    if (draft.sourceQaMessageId) {
        params.set("messageId", String(draft.sourceQaMessageId));
    }
    return `/admin/tickets?${params.toString()}`;
}

function buildDraftQaLink(draft) {
    const params = new URLSearchParams({
        messageId: String(draft.sourceQaMessageId),
        source: "draft",
        draftId: String(draft.id)
    });
    if (draft.sourceTicketId) {
        params.set("ticketId", String(draft.sourceTicketId));
    }
    return `/admin/qa-records?${params.toString()}`;
}

function applyDraftFilterInputs() {
    draftElements.statusFilter.value = draftState.filters.status;
    draftElements.draftTypeFilter.value = draftState.filters.draftType;
}

function readDraftFiltersFromInputs() {
    draftState.filters.knowledgeBaseId = draftElements.knowledgeBaseFilter.value;
    draftState.filters.status = draftElements.statusFilter.value;
    draftState.filters.draftType = draftElements.draftTypeFilter.value;
}

function syncDraftQuery() {
    DraftCommon.updateQuery({
        pageNo: draftState.pageNo,
        draftId: draftState.selectedDraftId,
        knowledgeBaseId: draftState.filters.knowledgeBaseId,
        status: draftState.filters.status,
        draftType: draftState.filters.draftType
    });
}

function handleDraftAuthFailure(error, message) {
    console.error(error);
    logoutDraftConsole();
    setDraftBanner(message, "error");
}

function setDraftBanner(message, type = "info") {
    draftElements.statusBanner.hidden = false;
    draftElements.statusBanner.className = `status-banner ${type}`;
    draftElements.statusBanner.textContent = message;
}



