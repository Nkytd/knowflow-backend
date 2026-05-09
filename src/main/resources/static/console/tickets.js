const TicketCommon = window.KnowFlowConsoleCommon;

const ticketState = {
    token: TicketCommon.readToken(),
    query: TicketCommon.parseQuery(),
    user: null,
    menus: [],
    tickets: [],
    comments: [],
    flows: [],
    assignees: [],
    knowledgeBases: [],
    selectedTicket: null,
    selectedTicketId: null,
    pageNo: 1,
    pageSize: 10,
    total: 0,
    filters: {
        keyword: "",
        status: "",
        priority: "",
        sourceType: "",
        slaStatus: "",
        assigneeUserId: ""
    }
};

const ticketElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    cacheTicketElements();
    initTicketStateFromQuery();
    bindTicketEvents();
    if (ticketState.token) {
        await restoreTicketSession();
        return;
    }
    showTicketLoggedOutState();
});

function cacheTicketElements() {
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
        "statusFilter",
        "priorityFilter",
        "sourceTypeFilter",
        "slaStatusFilter",
        "assigneeFilter",
        "searchButton",
        "resetButton",
        "contextBanner",
        "ticketPageMeta",
        "ticketTable",
        "ticketPaginationText",
        "prevPageButton",
        "nextPageButton",
        "ticketDetailBody"
    ].forEach((id) => {
        ticketElements[id] = document.getElementById(id);
    });
}

function initTicketStateFromQuery() {
    const query = ticketState.query;
    ticketState.pageNo = Number(query.pageNo || 1);
    ticketState.selectedTicketId = query.ticketId ? Number(query.ticketId) : null;
    ticketState.filters.keyword = query.keyword || "";
    ticketState.filters.status = query.status || "";
    ticketState.filters.priority = query.priority || "";
    ticketState.filters.sourceType = query.sourceType || "";
    ticketState.filters.slaStatus = query.slaStatus || "";
    ticketState.filters.assigneeUserId = query.assigneeUserId || "";
    applyTicketFilterInputs();
}

function bindTicketEvents() {
    ticketElements.loginForm.addEventListener("submit", handleTicketLogin);
    ticketElements.refreshButton.addEventListener("click", () => loadTicketConsole(true));
    ticketElements.logoutButton.addEventListener("click", logoutTicketConsole);
    ticketElements.searchButton.addEventListener("click", async () => {
        readTicketFiltersFromInputs();
        ticketState.pageNo = 1;
        await loadTicketConsole(false);
    });
    ticketElements.resetButton.addEventListener("click", async () => {
        ticketState.filters = { keyword: "", status: "", priority: "", sourceType: "", slaStatus: "", assigneeUserId: "" };
        ticketState.pageNo = 1;
        ticketState.selectedTicketId = null;
        ticketState.query = {};
        applyTicketFilterInputs();
        await loadTicketConsole(false);
    });
    ticketElements.prevPageButton.addEventListener("click", async () => {
        if (ticketState.pageNo <= 1) {
            return;
        }
        ticketState.pageNo -= 1;
        await loadTicketConsole(true);
    });
    ticketElements.nextPageButton.addEventListener("click", async () => {
        const maxPage = Math.max(1, Math.ceil(ticketState.total / ticketState.pageSize));
        if (ticketState.pageNo >= maxPage) {
            return;
        }
        ticketState.pageNo += 1;
        await loadTicketConsole(true);
    });
}

async function restoreTicketSession() {
    try {
        const [user, menus, assignees, knowledgeBases] = await Promise.all([
            TicketCommon.api("/api/v1/auth/me", { token: ticketState.token }),
            TicketCommon.api("/api/v1/auth/menus", { token: ticketState.token }),
            TicketCommon.api("/api/v1/admin/tickets/assignees", { token: ticketState.token }),
            TicketCommon.api("/api/v1/admin/knowledge-drafts/knowledge-bases/options", { token: ticketState.token })
        ]);
        ticketState.user = user;
        ticketState.menus = menus || [];
        ticketState.assignees = assignees || [];
        ticketState.knowledgeBases = knowledgeBases || [];
        renderTicketAssigneeFilterOptions();
        showTicketLoggedInState();
        renderTicketContextBanner();
        await loadTicketConsole(true);
    } catch (error) {
        handleTicketAuthFailure(error, "会话已失效，请重新登录。");
    }
}

async function handleTicketLogin(event) {
    event.preventDefault();
    const username = ticketElements.usernameInput.value.trim();
    const password = ticketElements.passwordInput.value;
    if (!username || !password) {
        setTicketBanner("请输入账号和密码。", "error");
        return;
    }

    ticketElements.loginButton.disabled = true;
    setTicketBanner("正在登录工单管理台...", "info");
    try {
        const data = await TicketCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        ticketState.token = data.token;
        TicketCommon.saveToken(data.token);
        ticketElements.passwordInput.value = "";
        await restoreTicketSession();
        setTicketBanner("登录成功，工单池已加载。", "success");
    } catch (error) {
        setTicketBanner(error.message || "登录失败。", "error");
    } finally {
        ticketElements.loginButton.disabled = false;
    }
}

function logoutTicketConsole() {
    ticketState.token = "";
    ticketState.user = null;
    ticketState.menus = [];
    ticketState.tickets = [];
    ticketState.comments = [];
    ticketState.flows = [];
    ticketState.selectedTicket = null;
    ticketState.selectedTicketId = null;
    ticketState.total = 0;
    TicketCommon.clearToken();
    TicketCommon.updateQuery({ ticketId: null });
    showTicketLoggedOutState();
    setTicketBanner("已退出工单管理台。", "success");
}

function showTicketLoggedOutState() {
    ticketElements.loginPanel.hidden = false;
    ticketElements.workspace.hidden = true;
    ticketElements.sessionName.textContent = "未登录";
    ticketElements.sessionMeta.textContent = "请先登录后加载工单数据";
}

function showTicketLoggedInState() {
    ticketElements.loginPanel.hidden = true;
    ticketElements.workspace.hidden = false;
    ticketElements.sessionName.textContent = ticketState.user.realName || ticketState.user.username;
    ticketElements.sessionMeta.textContent = TicketCommon.formatUserSessionMeta(ticketState.user);
}

function renderTicketContextBanner() {
    const source = ticketState.query.source;
    const questionText = ticketState.query.questionText;
    if (!source && !questionText) {
        ticketElements.contextBanner.hidden = true;
        ticketElements.contextBanner.textContent = "";
        return;
    }
    ticketElements.contextBanner.hidden = false;
    const sourceLabel = TicketCommon.enumLabel("pageSource", source, source || "其它页面");
    ticketElements.contextBanner.textContent = questionText
        ? `当前为来自 ${sourceLabel} 的深链工单视图，关联问题：${questionText}`
        : `当前为来自 ${sourceLabel} 的深链工单视图。`;
}

async function loadTicketConsole(keepSelection) {
    if (!ticketState.token) {
        showTicketLoggedOutState();
        return;
    }
    renderTicketListLoading();
    syncTicketQuery();

    try {
        const pageData = await TicketCommon.api(buildTicketListUrl(), { token: ticketState.token });
        ticketState.tickets = pageData.records || [];
        ticketState.total = pageData.total || 0;
        ticketState.pageNo = pageData.pageNo || ticketState.pageNo;
        ticketState.pageSize = pageData.pageSize || ticketState.pageSize;
        renderTicketList();

        const preferredTicketId = resolvePreferredTicketId(keepSelection);
        if (preferredTicketId) {
            await loadTicketDetail(preferredTicketId, true);
        } else {
            ticketState.selectedTicket = null;
            renderTicketDetail();
        }
    } catch (error) {
        if (error instanceof TicketCommon.AuthExpiredError) {
            handleTicketAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        setTicketBanner(error.message || "加载工单失败。", "error");
        ticketElements.ticketTable.innerHTML = TicketCommon.renderStateBlock({
            type: "error",
            title: "工单加载失败",
            message: error.message || "请稍后重试，或检查当前账号是否具备工单管理权限。"
        });
    }
}

function renderTicketListLoading() {
    ticketElements.ticketPageMeta.textContent = "正在加载工单队列";
    ticketElements.ticketTable.innerHTML = TicketCommon.renderStateBlock({
        type: "loading",
        title: "正在同步工单池",
        message: "系统正在加载状态、SLA、处理人和来源问答信息。"
    });
    ticketElements.ticketPaginationText.textContent = "加载中";
}

function buildTicketListUrl() {
    const params = new URLSearchParams({
        pageNo: String(ticketState.pageNo),
        pageSize: String(ticketState.pageSize)
    });
    if (ticketState.filters.keyword) {
        params.set("keyword", ticketState.filters.keyword);
    }
    if (ticketState.filters.status) {
        params.set("status", ticketState.filters.status);
    }
    if (ticketState.filters.priority) {
        params.set("priority", ticketState.filters.priority);
    }
    if (ticketState.filters.sourceType) {
        params.set("sourceType", ticketState.filters.sourceType);
    }
    if (ticketState.filters.slaStatus) {
        params.set("slaStatus", ticketState.filters.slaStatus);
    }
    if (ticketState.filters.assigneeUserId) {
        params.set("assigneeUserId", ticketState.filters.assigneeUserId);
    }
    return `/api/v1/admin/tickets?${params.toString()}`;
}

function renderTicketList() {
    const totalPage = Math.max(1, Math.ceil(ticketState.total / ticketState.pageSize));
    ticketElements.ticketPageMeta.textContent = `共 ${TicketCommon.formatNumber(ticketState.total)} 张工单`;
    ticketElements.ticketPaginationText.textContent = `第 ${ticketState.pageNo} / ${totalPage} 页`;
    ticketElements.prevPageButton.disabled = ticketState.pageNo <= 1;
    ticketElements.nextPageButton.disabled = ticketState.pageNo >= totalPage;

    if (!ticketState.tickets.length) {
        ticketElements.ticketTable.innerHTML = TicketCommon.renderStateBlock({
            title: "暂无匹配工单",
            message: "试试放宽筛选条件，或者回到运营看板继续排查高频未命中主题。",
            actionHref: "/admin/dashboard",
            actionText: "回到运营看板"
        });
        return;
    }

    ticketElements.ticketTable.innerHTML = `
        <table class="data-table">
            <thead>
            <tr>
                <th>工单</th>
                <th>状态/优先级</th>
                <th>SLA</th>
                <th>当前处理人</th>
                <th>最近更新时间</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody>
            ${ticketState.tickets.map((ticket) => `
                <tr class="${ticket.id === ticketState.selectedTicketId ? "active-row" : ""}">
                    <td>
                        <div class="table-question">
                            <strong>${TicketCommon.escapeHtml(ticket.ticketNo)}</strong>
                            <div class="meta-line">${TicketCommon.escapeHtml(ticket.title || "无标题工单")}</div>
                        </div>
                    </td>
                    <td>
                        <div class="chips">
                            <span class="status-pill ${TicketCommon.statusClass(ticket.status)}">${TicketCommon.escapeHtml(TicketCommon.enumLabel("ticketStatus", ticket.status, "未知状态"))}</span>
                            <span class="status-pill">${TicketCommon.escapeHtml(TicketCommon.enumLabel("priority", ticket.priority, "未知优先级"))}</span>
                        </div>
                    </td>
                    <td>${renderTicketSlaCell(ticket)}</td>
                    <td>
                        <div class="meta-line">${TicketCommon.escapeHtml(ticket.assigneeName || "未分配")}</div>
                        <div class="tiny-line">${TicketCommon.escapeHtml(TicketCommon.enumLabel("ticketSourceType", ticket.sourceType, ticket.sourceType || "未知来源"))}</div>
                    </td>
                    <td>${TicketCommon.escapeHtml(TicketCommon.formatDateTime(ticket.updatedAt || ticket.createdAt))}</td>
                    <td><button class="link-button table-row-button" type="button" data-ticket-id="${ticket.id}">查看处理</button></td>
                </tr>
            `).join("")}
            </tbody>
        </table>
    `;

    ticketElements.ticketTable.querySelectorAll("[data-ticket-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await loadTicketDetail(Number(button.dataset.ticketId), false);
        });
    });
}

function resolvePreferredTicketId(keepSelection) {
    if (keepSelection && ticketState.selectedTicketId) {
        return ticketState.selectedTicketId;
    }
    if (ticketState.query.ticketId) {
        return Number(ticketState.query.ticketId);
    }
    if (ticketState.selectedTicketId) {
        return ticketState.selectedTicketId;
    }
    return ticketState.tickets[0]?.id || null;
}

async function loadTicketDetail(ticketId, silent) {
    if (!ticketId) {
        return;
    }
    ticketState.selectedTicketId = ticketId;
    syncTicketQuery();
    renderTicketList();
    if (!silent) {
        ticketElements.ticketDetailBody.innerHTML = TicketCommon.renderStateBlock({
            type: "loading",
            title: "正在加载工单详情",
            message: "正在读取完整上下文、处理记录、流转时间线和知识回流状态。"
        });
    }

    try {
        const [detail, comments, flows] = await Promise.all([
            TicketCommon.api(`/api/v1/admin/tickets/${ticketId}`, { token: ticketState.token }),
            TicketCommon.api(`/api/v1/admin/tickets/${ticketId}/comments`, { token: ticketState.token }),
            TicketCommon.api(`/api/v1/admin/tickets/${ticketId}/flows`, { token: ticketState.token })
        ]);
        ticketState.selectedTicket = detail;
        ticketState.comments = comments || [];
        ticketState.flows = flows || [];
        renderTicketDetail();
    } catch (error) {
        if (error instanceof TicketCommon.AuthExpiredError) {
            handleTicketAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        ticketElements.ticketDetailBody.innerHTML = TicketCommon.renderStateBlock({
            type: "error",
            title: "工单详情加载失败",
            message: error.message || "请稍后重试，或从左侧重新选择工单。"
        });
        setTicketBanner(error.message || "工单详情加载失败。", "error");
    }
}

function renderTicketDetail() {
    const ticket = ticketState.selectedTicket;
    if (!ticket) {
        ticketElements.ticketDetailBody.innerHTML = TicketCommon.renderStateBlock({
            title: "选择一张工单开始处理",
            message: "左侧点选工单后，这里会展示完整上下文、处理动作和回流入口。"
        });
        return;
    }

    const canManageDraft = TicketCommon.hasRole(ticketState.user, "TENANT_ADMIN", "SUPPORT_AGENT");
    const canAssign = ticketState.assignees.length > 0;
    const canReview = ticket.status !== "CLOSED" && ticket.status !== "RESOLVED";
    const canClose = ticket.status === "RESOLVED";
    const canCreateDraft = canManageDraft && (ticket.status === "RESOLVED" || ticket.status === "CLOSED");

    ticketElements.ticketDetailBody.innerHTML = `
        <section class="summary-card">
            <span class="panel-kicker">当前选中工单</span>
            <strong>${TicketCommon.escapeHtml(ticket.ticketNo)} · ${TicketCommon.escapeHtml(ticket.title || "无标题工单")}</strong>
            <div class="chips">
                <span class="status-pill ${TicketCommon.statusClass(ticket.status)}">${TicketCommon.escapeHtml(TicketCommon.enumLabel("ticketStatus", ticket.status, "未知状态"))}</span>
                <span class="status-pill">${TicketCommon.escapeHtml(TicketCommon.enumLabel("priority", ticket.priority, "未知优先级"))}</span>
                <span class="status-pill">${TicketCommon.escapeHtml(ticket.assigneeName || "未分配")}</span>
                ${renderTicketSlaPill(ticket)}
                <span class="status-pill">${TicketCommon.escapeHtml(TicketCommon.formatDateTime(ticket.createdAt))}</span>
            </div>
            <p>${TicketCommon.escapeHtml(ticket.content || "暂无工单描述")}</p>
            <div class="action-row" style="margin-top:14px;">
                <a class="link-button" href="/admin/dashboard">回到运营看板</a>
                ${ticket.sourceQaMessageId ? `<a class="link-button" href="${buildTicketQaLink(ticket)}">查看来源问答</a>` : ""}
                ${ticket.relatedDraftId ? `<a class="link-button" href="${buildTicketDraftLink(ticket)}">打开关联草稿</a>` : ""}
            </div>
        </section>

        <section class="detail-columns">
            <article class="detail-card">
                <strong>来源问题</strong>
                <p>${TicketCommon.escapeHtml(ticket.sourceQuestionText || "暂无来源问题")}</p>
            </article>
            <article class="detail-card">
                <strong>AI 原始回答</strong>
                <p>${TicketCommon.escapeHtml(ticket.sourceAnswerText || "暂无 AI 回答")}</p>
            </article>
            <article class="detail-card">
                <strong>SLA 时效</strong>
                <div class="sla-detail-grid">
                    ${renderTicketSlaMetric("策略", ticket.slaPolicy || "未配置")}
                    ${renderTicketSlaMetric("状态", TicketCommon.enumLabel("slaStatus", ticket.slaStatus, "未知状态"))}
                    ${renderTicketSlaMetric("截止时间", TicketCommon.formatDateTime(ticket.slaDueAt))}
                    ${renderTicketSlaMetric(resolveTicketSlaTimeMetricLabel(ticket), resolveSlaRemainingText(ticket))}
                </div>
                <div class="sla-progress ${resolveTicketSlaClass(ticket)}">
                    <span style="width:${resolveTicketSlaProgress(ticket)}%"></span>
                </div>
                <p class="tiny-line">${TicketCommon.escapeHtml(resolveTicketSlaHint(ticket))}</p>
            </article>
            <article class="detail-card">
                <strong>知识回流状态</strong>
                <p>${ticket.relatedDraftId
                    ? `${TicketCommon.escapeHtml(ticket.relatedDraftTitle || `草稿 #${ticket.relatedDraftId}`)} · ${TicketCommon.escapeHtml(TicketCommon.enumLabel("draftStatus", ticket.relatedDraftStatus, "未知状态"))}`
                    : "当前工单还没有关联的知识草稿。"}
                </p>
                ${ticket.relatedDraftId ? `<div class="footer-actions" style="margin-top:12px;"><a class="link-button" href="${buildTicketDraftLink(ticket)}">继续处理知识草稿</a></div>` : ""}
            </article>
        </section>

        <section class="detail-actions">
            ${ticket.status === "PENDING" ? `
                <article class="detail-card">
                    <strong>接单</strong>
                    <p>将当前待处理工单切换为处理中，并把自己设为处理人。</p>
                    <div class="footer-actions">
                        <button id="acceptTicketButton" class="primary-button" type="button">立即接单</button>
                    </div>
                </article>
            ` : ""}

            ${canAssign ? `
                <article class="detail-card">
                    <strong>改派工单</strong>
                    <div class="form-grid">
                        <label class="field">
                            <span>处理人</span>
                            <select id="assignUserSelect">${buildAssigneeOptions(ticket.assigneeUserId)}</select>
                        </label>
                        <label class="field">
                            <span>备注</span>
                            <input id="assignRemarkInput" type="text" placeholder="例如：按业务分组改派">
                        </label>
                    </div>
                    <div class="footer-actions">
                        <button id="assignTicketButton" class="soft-button" type="button">提交改派</button>
                    </div>
                </article>
            ` : ""}

            ${canReview ? `
                <article class="detail-card">
                    <strong>处理备注</strong>
                    <div class="form-grid">
                        <label class="field">
                            <span>备注类型</span>
                            <select id="commentTypeSelect">
                                <option value="AGENT_REPLY">客服回复</option>
                                <option value="INTERNAL_NOTE">内部备注</option>
                            </select>
                        </label>
                        <label class="field">
                            <span>是否对用户可见</span>
                            <label class="checkbox-row">
                                <input id="commentVisibleCheckbox" type="checkbox" checked>
                                <span>用户可见</span>
                            </label>
                        </label>
                        <label class="field full-span">
                            <span>备注内容</span>
                            <textarea id="commentContentInput" placeholder="记录处理过程，或回复用户下一步动作"></textarea>
                        </label>
                    </div>
                    <div class="footer-actions">
                        <button id="commentTicketButton" class="navy-button" type="button">提交备注</button>
                    </div>
                </article>

                <article class="detail-card">
                    <strong>解决工单</strong>
                    <label class="field">
                        <span>解决方案</span>
                        <textarea id="resolveSolutionInput" placeholder="填写最终解决方案，这部分会成为后续知识回流的重要基础"></textarea>
                    </label>
                    <div class="footer-actions">
                        <button id="resolveTicketButton" class="primary-button" type="button">标记为已解决</button>
                    </div>
                </article>
            ` : ""}

            ${canClose ? `
                <article class="detail-card">
                    <strong>关闭工单</strong>
                    <label class="field">
                        <span>关闭备注</span>
                        <input id="closeRemarkInput" type="text" placeholder="例如：用户已确认问题解决">
                    </label>
                    <div class="footer-actions">
                        <button id="closeTicketButton" class="soft-button" type="button">关闭工单</button>
                    </div>
                </article>
            ` : ""}

            ${canCreateDraft ? `
                <article class="detail-card">
                    <strong>生成知识草稿</strong>
                    <div class="form-grid">
                        <label class="field">
                            <span>目标知识库</span>
                            <select id="draftKnowledgeBaseSelect">${buildKnowledgeBaseOptions()}</select>
                        </label>
                        <label class="field">
                            <span>草稿标题</span>
                            <input id="draftTitleInput" type="text" placeholder="默认使用工单标题" value="${TicketCommon.escapeAttr(ticket.title || "")}">
                        </label>
                    </div>
                    <div id="agentDraftSuggestionBox" class="agent-suggestion-box" hidden></div>
                    <div class="footer-actions">
                        <button id="suggestDraftButton" class="soft-button" type="button">Agent 建议草稿</button>
                        <button id="createDraftButton" class="primary-button" type="button">从工单生成知识草稿</button>
                    </div>
                </article>
            ` : ""}
        </section>

        <section class="timeline-grid">
            <article class="detail-card">
                <strong>处理评论</strong>
                <div class="section-stack">
                    ${renderTicketComments()}
                </div>
            </article>
            <article class="detail-card">
                <strong>流转轨迹</strong>
                <div class="section-stack">
                    ${renderTicketFlows()}
                </div>
            </article>
        </section>
    `;

    bindTicketDetailActions(ticket);
}

function renderTicketComments() {
    if (!ticketState.comments.length) {
        return TicketCommon.renderStateBlock({
            title: "暂无处理评论",
            message: "当前工单还没有评论记录。"
        });
    }
    return ticketState.comments.map((comment) => `
        <article class="timeline-item">
            <strong>${TicketCommon.escapeHtml(TicketCommon.enumLabel("ticketCommentType", comment.commentType, comment.commentType || "评论"))}</strong>
            <div class="meta-line">${TicketCommon.escapeHtml(comment.commentUserName || "未知用户")} · ${TicketCommon.escapeHtml(TicketCommon.formatDateTime(comment.createdAt))}</div>
            <div class="chips" style="margin-top:8px;">
                <span class="status-pill">${comment.visibleToUser ? "用户可见" : "内部可见"}</span>
            </div>
            <p>${TicketCommon.escapeHtml(comment.content || "")}</p>
        </article>
    `).join("");
}

function renderTicketFlows() {
    if (!ticketState.flows.length) {
        return TicketCommon.renderStateBlock({
            title: "暂无流转轨迹",
            message: "当前工单还没有流转轨迹。"
        });
    }
    return ticketState.flows.map((flow) => `
        <article class="timeline-item">
            <strong>${TicketCommon.escapeHtml(TicketCommon.enumLabel("ticketActionType", flow.actionType, flow.actionType || "操作"))}</strong>
            <div class="meta-line">${TicketCommon.escapeHtml(flow.operatorUserName || "未知操作人")} · ${TicketCommon.escapeHtml(TicketCommon.formatDateTime(flow.createdAt))}</div>
            <div class="chips" style="margin-top:8px;">
                <span class="status-pill">${TicketCommon.escapeHtml(TicketCommon.enumLabel("ticketFlowStatus", flow.fromStatus, flow.fromStatus || "开始"))}</span>
                <span class="status-pill">${TicketCommon.escapeHtml(TicketCommon.enumLabel("ticketFlowStatus", flow.toStatus, flow.toStatus || "状态未变化"))}</span>
            </div>
            <p>${TicketCommon.escapeHtml(flow.remark || "无备注")}</p>
        </article>
    `).join("");
}

function bindTicketDetailActions(ticket) {
    bindIfPresent("acceptTicketButton", async () => {
        await postTicketAction(`/api/v1/admin/tickets/${ticket.id}/accept`, null, "工单已接单。");
    });

    bindIfPresent("assignTicketButton", async () => {
        const assigneeUserId = document.getElementById("assignUserSelect")?.value;
        if (!assigneeUserId) {
            setTicketBanner("请选择新的处理人。", "error");
            return;
        }
        await postTicketAction(`/api/v1/admin/tickets/${ticket.id}/assign`, {
            assigneeUserId: Number(assigneeUserId),
            remark: document.getElementById("assignRemarkInput")?.value || ""
        }, "工单已改派。");
    });

    const commentTypeSelect = document.getElementById("commentTypeSelect");
    if (commentTypeSelect) {
        commentTypeSelect.addEventListener("change", () => {
            const checkbox = document.getElementById("commentVisibleCheckbox");
            const internal = commentTypeSelect.value === "INTERNAL_NOTE";
            checkbox.checked = !internal;
            checkbox.disabled = internal;
        });
    }

    bindIfPresent("commentTicketButton", async () => {
        const content = document.getElementById("commentContentInput")?.value.trim();
        if (!content) {
            setTicketBanner("备注内容不能为空。", "error");
            return;
        }
        const commentType = document.getElementById("commentTypeSelect")?.value || "AGENT_REPLY";
        const visible = commentType === "INTERNAL_NOTE"
            ? false
            : Boolean(document.getElementById("commentVisibleCheckbox")?.checked);
        await postTicketAction(`/api/v1/admin/tickets/${ticket.id}/comment`, {
            commentType,
            content,
            visibleToUser: visible
        }, "工单备注已提交。");
    });

    bindIfPresent("resolveTicketButton", async () => {
        const solution = document.getElementById("resolveSolutionInput")?.value.trim();
        if (!solution) {
            setTicketBanner("请填写解决方案。", "error");
            return;
        }
        await postTicketAction(`/api/v1/admin/tickets/${ticket.id}/resolve`, { solution }, "工单已标记为已解决。");
    });

    bindIfPresent("closeTicketButton", async () => {
        await postTicketAction(`/api/v1/admin/tickets/${ticket.id}/close`, {
            remark: document.getElementById("closeRemarkInput")?.value || ""
        }, "工单已关闭。");
    });

    bindIfPresent("suggestDraftButton", async () => {
        await suggestTicketDraft(ticket);
    });

    bindIfPresent("createDraftButton", async () => {
        const knowledgeBaseId = document.getElementById("draftKnowledgeBaseSelect")?.value;
        if (!knowledgeBaseId) {
            setTicketBanner("请选择目标知识库。", "error");
            return;
        }
        try {
            const draft = await TicketCommon.api(`/api/v1/admin/knowledge-drafts/from-ticket/${ticket.id}`, {
                token: ticketState.token,
                method: "POST",
                body: JSON.stringify({
                    knowledgeBaseId: Number(knowledgeBaseId),
                    draftType: "FAQ",
                    title: document.getElementById("draftTitleInput")?.value || ""
                })
            });
            setTicketBanner("知识草稿已生成，正在跳转。", "success");
            window.location.href = buildKnowledgeDraftRedirectLink(ticket, draft.id);
        } catch (error) {
            if (error instanceof TicketCommon.AuthExpiredError) {
                handleTicketAuthFailure(error, "登录状态已失效，请重新登录。");
                return;
            }
            setTicketBanner(error.message || "生成知识草稿失败。", "error");
        }
    });
}

async function suggestTicketDraft(ticket) {
    const button = document.getElementById("suggestDraftButton");
    const suggestionBox = document.getElementById("agentDraftSuggestionBox");
    const knowledgeBaseId = document.getElementById("draftKnowledgeBaseSelect")?.value;
    if (!suggestionBox) {
        return;
    }
    suggestionBox.hidden = false;
    suggestionBox.innerHTML = TicketCommon.renderStateBlock({
        type: "loading",
        title: "Agent 正在生成草稿建议",
        message: "正在读取工单、来源问答、处理评论和目标知识库。"
    });
    if (button) {
        button.disabled = true;
    }
    try {
        const argumentsPayload = { ticketId: ticket.id };
        if (knowledgeBaseId) {
            argumentsPayload.knowledgeBaseId = Number(knowledgeBaseId);
        }
        const execution = await TicketCommon.api("/api/v1/app/agent/tools/execute", {
            token: ticketState.token,
            method: "POST",
            body: JSON.stringify({
                toolCode: "SUGGEST_KNOWLEDGE_DRAFT_FROM_TICKET",
                arguments: argumentsPayload
            })
        });
        const suggestion = execution.result || {};
        applyDraftSuggestionToForm(suggestion);
        suggestionBox.innerHTML = renderTicketDraftSuggestion(suggestion, execution.summary);
        setTicketBanner("Agent 已生成知识草稿建议，可确认后生成草稿。", "success");
    } catch (error) {
        if (error instanceof TicketCommon.AuthExpiredError) {
            handleTicketAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        suggestionBox.innerHTML = TicketCommon.renderStateBlock({
            type: "error",
            title: "Agent 建议失败",
            message: error.message || "请稍后重试，或直接人工填写草稿信息。"
        });
        setTicketBanner(error.message || "Agent 建议失败。", "error");
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

function applyDraftSuggestionToForm(suggestion) {
    const knowledgeBaseSelect = document.getElementById("draftKnowledgeBaseSelect");
    const titleInput = document.getElementById("draftTitleInput");
    if (knowledgeBaseSelect && suggestion.knowledgeBaseId) {
        knowledgeBaseSelect.value = String(suggestion.knowledgeBaseId);
    }
    if (titleInput && suggestion.title) {
        titleInput.value = suggestion.title;
    }
}

function renderTicketDraftSuggestion(suggestion, summary) {
    const confidence = Number(suggestion.confidence || 0);
    const confidenceText = confidence ? `${Math.round(confidence * 100)}%` : "待确认";
    const reasons = Array.isArray(suggestion.reasons) ? suggestion.reasons : [];
    return `
        <article class="agent-suggestion-card">
            <div class="agent-suggestion-header">
                <div>
                    <span class="panel-kicker">Agent 工具建议</span>
                    <strong>${TicketCommon.escapeHtml(summary || "已生成知识草稿建议")}</strong>
                </div>
                <span class="status-pill ${confidence >= 0.72 ? "status-success" : "status-warn"}">置信度 ${TicketCommon.escapeHtml(confidenceText)}</span>
            </div>
            <div class="sla-detail-grid">
                ${renderTicketSlaMetric("推荐知识库", suggestion.knowledgeBaseName || "请人工选择")}
                ${renderTicketSlaMetric("草稿类型", TicketCommon.enumLabel("draftType", suggestion.draftType, suggestion.draftType || "FAQ"))}
                ${renderTicketSlaMetric("推荐标题", suggestion.title || "未生成标题")}
                ${renderTicketSlaMetric("下一步", suggestion.existingDraftId ? `继续审核草稿 #${suggestion.existingDraftId}` : "确认后生成草稿")}
            </div>
            <div class="agent-suggestion-preview">
                <strong>建议问题</strong>
                <p>${TicketCommon.escapeHtml(suggestion.questionText || "暂无建议问题")}</p>
                <strong>建议答案</strong>
                <p>${TicketCommon.escapeHtml(TicketCommon.truncateText(suggestion.answerText || "暂无建议答案", 360))}</p>
            </div>
            ${reasons.length ? `
                <div class="agent-suggestion-reasons">
                    ${reasons.map((reason) => `<span>${TicketCommon.escapeHtml(reason)}</span>`).join("")}
                </div>
            ` : ""}
        </article>
    `;
}

async function postTicketAction(url, body, successMessage) {
    try {
        await TicketCommon.api(url, {
            token: ticketState.token,
            method: "POST",
            body: body == null ? null : JSON.stringify(body)
        });
        setTicketBanner(successMessage, "success");
        await loadTicketConsole(true);
    } catch (error) {
        if (error instanceof TicketCommon.AuthExpiredError) {
            handleTicketAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        setTicketBanner(error.message || "操作失败。", "error");
    }
}

function bindIfPresent(id, handler) {
    const element = document.getElementById(id);
    if (element) {
        element.addEventListener("click", handler);
    }
}

function renderTicketAssigneeFilterOptions() {
    const currentValue = ticketState.filters.assigneeUserId || "";
    ticketElements.assigneeFilter.innerHTML = `
        <option value="">全部人员</option>
        ${ticketState.assignees.map((assignee) => `
            <option value="${assignee.userId}" ${String(currentValue) === String(assignee.userId) ? "selected" : ""}>
                ${TicketCommon.escapeHtml(assignee.realName || assignee.username)} (${TicketCommon.escapeHtml((assignee.roleCodes || []).join("/"))})
            </option>
        `).join("")}
    `;
}

function buildAssigneeOptions(currentUserId) {
    return `
        <option value="">请选择处理人</option>
        ${ticketState.assignees.map((assignee) => `
            <option value="${assignee.userId}" ${String(currentUserId || "") === String(assignee.userId) ? "selected" : ""}>
                ${TicketCommon.escapeHtml(assignee.realName || assignee.username)} (${TicketCommon.escapeHtml((assignee.roleCodes || []).join("/"))})
            </option>
        `).join("")}
    `;
}

function buildKnowledgeBaseOptions() {
    return `
        <option value="">请选择知识库</option>
        ${ticketState.knowledgeBases.map((knowledgeBase) => `
            <option value="${knowledgeBase.id}">
                ${TicketCommon.escapeHtml(knowledgeBase.kbName)} (${TicketCommon.escapeHtml(TicketCommon.enumLabel("knowledgeBaseStatus", knowledgeBase.status, "未知状态"))})
            </option>
        `).join("")}
    `;
}

function renderTicketSlaCell(ticket) {
    const dueText = ticket.slaDueAt ? TicketCommon.formatDateTime(ticket.slaDueAt) : "暂无截止时间";
    return `
        <div class="sla-cell">
            ${renderTicketSlaPill(ticket)}
            <div class="tiny-line">${TicketCommon.escapeHtml(ticket.slaPolicy || "未配置策略")}</div>
            <div class="tiny-line">${TicketCommon.escapeHtml(dueText)}</div>
        </div>
    `;
}

function renderTicketSlaPill(ticket) {
    const status = ticket.slaStatus || (ticket.slaBreached ? "BREACHED" : "ON_TRACK");
    const label = TicketCommon.enumLabel("slaStatus", status, "未知 SLA");
    return `<span class="status-pill ${resolveTicketSlaClass(ticket)}">${TicketCommon.escapeHtml(label)} · ${TicketCommon.escapeHtml(resolveSlaRemainingText(ticket))}</span>`;
}

function renderTicketSlaMetric(label, value) {
    return `
        <div class="sla-metric">
            <span>${TicketCommon.escapeHtml(label)}</span>
            <strong>${TicketCommon.escapeHtml(value)}</strong>
        </div>
    `;
}

function resolveTicketSlaClass(ticket) {
    const status = String(ticket.slaStatus || "").toUpperCase();
    if (ticket.slaBreached || status === "BREACHED") {
        return "status-danger sla-breached";
    }
    if (status === "AT_RISK") {
        return "status-warn sla-risk";
    }
    if (status === "MET") {
        return "status-success sla-met";
    }
    if (status === "PAUSED") {
        return "status-info";
    }
    return "status-success";
}

function resolveSlaRemainingText(ticket) {
    if (!ticket.slaDueAt) {
        return "未设置";
    }
    const status = String(ticket.slaStatus || "").toUpperCase();
    if (ticket.slaBreached || status === "BREACHED") {
        return ticket.slaBreachedAt ? `已于 ${TicketCommon.formatDateTime(ticket.slaBreachedAt)} 超时` : "已超时";
    }
    if (status === "MET") {
        return ticket.resolvedAt
            ? `解决于 ${TicketCommon.formatDateTime(ticket.resolvedAt)}`
            : "已在 SLA 内完成";
    }
    if (status === "PAUSED") {
        return "已暂停计时";
    }
    const minutes = Number(ticket.slaRemainingMinutes);
    if (!Number.isFinite(minutes)) {
        return "无需倒计时";
    }
    if (minutes <= 0) {
        return "即将超时";
    }
    const hours = Math.floor(minutes / 60);
    const restMinutes = minutes % 60;
    if (hours >= 24) {
        const days = Math.floor(hours / 24);
        const restHours = hours % 24;
        return restHours ? `剩 ${days} 天 ${restHours} 小时` : `剩 ${days} 天`;
    }
    if (hours > 0) {
        return restMinutes ? `剩 ${hours} 小时 ${restMinutes} 分钟` : `剩 ${hours} 小时`;
    }
    return `剩 ${restMinutes} 分钟`;
}

function resolveTicketSlaTimeMetricLabel(ticket) {
    const status = String(ticket.slaStatus || "").toUpperCase();
    if (ticket.slaBreached || status === "BREACHED") {
        return "超时时间";
    }
    if (status === "MET") {
        return "完成时间";
    }
    if (status === "PAUSED") {
        return "计时状态";
    }
    return "剩余时间";
}

function resolveTicketSlaHint(ticket) {
    const status = String(ticket.slaStatus || "").toUpperCase();
    if (!ticket.slaDueAt) {
        return "这张工单暂未设置 SLA 截止时间。";
    }
    if (ticket.slaBreached || status === "BREACHED") {
        return "SLA 已超时，建议优先跟进并补充处理说明。";
    }
    if (status === "AT_RISK") {
        return "距离 SLA 截止不足 2 小时，适合在演示中展示临近超时预警。";
    }
    if (status === "MET") {
        return "工单已在 SLA 内完成，后续可沉淀到知识草稿。";
    }
    if (status === "PAUSED") {
        return "SLA 计时已暂停，等待恢复后继续跟进。";
    }
    return "SLA 正常推进，可按当前优先级处理。";
}

function resolveTicketSlaProgress(ticket) {
    const status = String(ticket.slaStatus || "").toUpperCase();
    if (ticket.slaBreached || status === "BREACHED") {
        return 100;
    }
    if (status === "MET") {
        return 100;
    }
    if (status === "PAUSED") {
        return 45;
    }
    const minutes = Number(ticket.slaRemainingMinutes);
    if (!Number.isFinite(minutes)) {
        return 0;
    }
    if (status === "AT_RISK") {
        return minutes <= 30 ? 92 : 82;
    }
    return 38;
}

function buildTicketQaLink(ticket) {
    const params = new URLSearchParams({
        messageId: String(ticket.sourceQaMessageId),
        source: "ticket",
        ticketId: String(ticket.id)
    });
    if (ticket.relatedDraftId) {
        params.set("draftId", String(ticket.relatedDraftId));
    }
    return `/admin/qa-records?${params.toString()}`;
}

function buildTicketDraftLink(ticket) {
    const params = new URLSearchParams({
        draftId: String(ticket.relatedDraftId),
        source: "ticket",
        ticketId: String(ticket.id)
    });
    if (ticket.sourceQaMessageId) {
        params.set("messageId", String(ticket.sourceQaMessageId));
    }
    return `/admin/knowledge-drafts?${params.toString()}`;
}

function buildKnowledgeDraftRedirectLink(ticket, draftId) {
    const params = new URLSearchParams({
        draftId: String(draftId),
        source: "ticket",
        ticketId: String(ticket.id)
    });
    if (ticket.sourceQaMessageId) {
        params.set("messageId", String(ticket.sourceQaMessageId));
    }
    return `/admin/knowledge-drafts?${params.toString()}`;
}

function applyTicketFilterInputs() {
    ticketElements.keywordFilter.value = ticketState.filters.keyword;
    ticketElements.statusFilter.value = ticketState.filters.status;
    ticketElements.priorityFilter.value = ticketState.filters.priority;
    ticketElements.sourceTypeFilter.value = ticketState.filters.sourceType;
    ticketElements.slaStatusFilter.value = ticketState.filters.slaStatus;
    ticketElements.assigneeFilter.value = ticketState.filters.assigneeUserId;
}

function readTicketFiltersFromInputs() {
    ticketState.filters.keyword = ticketElements.keywordFilter.value.trim();
    ticketState.filters.status = ticketElements.statusFilter.value;
    ticketState.filters.priority = ticketElements.priorityFilter.value;
    ticketState.filters.sourceType = ticketElements.sourceTypeFilter.value;
    ticketState.filters.slaStatus = ticketElements.slaStatusFilter.value;
    ticketState.filters.assigneeUserId = ticketElements.assigneeFilter.value;
}

function syncTicketQuery() {
    TicketCommon.updateQuery({
        pageNo: ticketState.pageNo,
        ticketId: ticketState.selectedTicketId,
        keyword: ticketState.filters.keyword,
        status: ticketState.filters.status,
        priority: ticketState.filters.priority,
        sourceType: ticketState.filters.sourceType,
        slaStatus: ticketState.filters.slaStatus,
        assigneeUserId: ticketState.filters.assigneeUserId
    });
}

function handleTicketAuthFailure(error, message) {
    console.error(error);
    logoutTicketConsole();
    setTicketBanner(message, "error");
}

function setTicketBanner(message, type = "info") {
    ticketElements.statusBanner.hidden = false;
    ticketElements.statusBanner.className = `status-banner ${type}`;
    ticketElements.statusBanner.textContent = message;
}



