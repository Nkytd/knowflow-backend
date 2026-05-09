const WorkbenchCommon = window.KnowFlowConsoleCommon;

const workbenchState = {
    token: WorkbenchCommon.readToken(),
    query: WorkbenchCommon.parseQuery(),
    user: null,
    knowledgeBases: [],
    selectedKnowledgeBaseId: null,
    selectedModelPreference: "FAST",
    sessions: [],
    selectedSessionId: null,
    messages: [],
    pendingMessages: [],
    selectedMessageId: null,
    selectedMessage: null,
    tickets: [],
    selectedTicketId: null,
    selectedTicket: null,
    selectedTicketComments: [],
    handoffDraft: {
        title: "",
        content: "",
        priority: "HIGH"
    }
};

const workbenchElements = {};

const MODEL_OPTIONS = [
    { value: "FAST", label: "快速问答", description: "LongCat-Flash-Chat，适合日常知识库问答。" },
    { value: "THINKING", label: "深度思考", description: "LongCat-Flash-Thinking-2601，适合复杂推理，速度较慢。" },
    { value: "LITE", label: "极速轻量", description: "LongCat-Flash-Lite，优先追求响应速度。" },
    { value: "EXPERIMENTAL", label: "实验对话", description: "LongCat-Flash-Chat-2602-Exp，适合对比体验。" }
];

document.addEventListener("DOMContentLoaded", async () => {
    document.body.classList.add("assistant-page");
    cacheWorkbenchElements();
    initWorkbenchStateFromQuery();
    installAssistantTitleHelpContent();
    bindWorkbenchEvents();
    scheduleAssistantGeometrySync();
    if (workbenchState.token) {
        await restoreWorkbenchSession();
        return;
    }
    showWorkbenchLoggedOutState();
});

function installAssistantTitleHelpContent() {
    const titleHelp = document.querySelector(".console-header[data-page-key=\"assistant\"] .shell-title-help");
    if (!titleHelp) {
        return;
    }
    if (!document.getElementById("assistantTitleHelpOverview")) {
        titleHelp.insertAdjacentHTML("beforeend", `
            <section id="assistantTitleHelpJourney" class="assistant-title-help-section">
                <span class="panel-kicker">用户旅程</span>
                <div class="assistant-journey-steps assistant-title-help-steps">
                    <span>1. 选择知识库</span>
                    <span>2. 发起问题</span>
                    <span>3. 查看证据</span>
                    <span>4. 转人工工单</span>
                    <span>5. 沉淀知识草稿</span>
                </div>
            </section>
            <section id="assistantTitleHelpOverview" class="assistant-title-help-section assistant-overview-panel">
                <span class="panel-kicker">工作台概览</span>
                <h2 id="focusPopoverHeading">当前问答工作台数据</h2>
                <p id="focusPopoverDescription">这里收纳当前页面的关键数据，避免指标卡片占用问答首屏空间。</p>
                <div id="focusPopoverMeta" class="assistant-overview-popover-grid"></div>
            </section>
        `);
    }
    renderFocusPopover();
}

function renderFocusPopover() {
    const heading = document.getElementById("focusPopoverHeading");
    const description = document.getElementById("focusPopoverDescription");
    const meta = document.getElementById("focusPopoverMeta");
    if (!heading || !description || !meta) {
        return;
    }

    const cards = buildOverviewCards();
    const noHitCard = cards.find((card) => card.key === "handoff");
    const ticketCard = cards.find((card) => card.key === "tickets");
    heading.textContent = "当前问答工作台数据";
    description.textContent = `待转人工 ${WorkbenchCommon.formatNumber(noHitCard?.value || 0)} 条，未关闭工单 ${WorkbenchCommon.formatNumber(ticketCard?.value || 0)} 个。`;
    meta.innerHTML = cards.map(renderOverviewCardHtml).join("");
}

function buildOverviewCards() {
    const selectedKnowledgeBase = getSelectedKnowledgeBase();
    const filteredSessions = getFilteredSessions();
    const openTickets = workbenchState.tickets.filter((ticket) => !isClosedTicket(ticket.status));
    const resolvedTickets = workbenchState.tickets.filter((ticket) => isResolvedTicket(ticket.status));
    const noHitCount = workbenchState.messages.filter((message) => message.needHumanHandoff).length;
    const answeredCount = workbenchState.messages.filter((message) => !message.needHumanHandoff).length;

    return [
        {
            key: "knowledgeBases",
            title: "可用知识库",
            value: workbenchState.knowledgeBases.length,
            caption: "当前用户可使用的知识空间",
            tone: "info"
        },
        {
            key: "documents",
            title: "当前知识库文档",
            value: selectedKnowledgeBase?.docCount || 0,
            caption: selectedKnowledgeBase ? selectedKnowledgeBase.kbName : "请选择一个知识库",
            tone: "navy"
        },
        {
            key: "sessions",
            title: "当前知识库会话",
            value: filteredSessions.length,
            caption: "当前知识库下的对话线程数",
            tone: "success"
        },
        {
            key: "answered",
            title: "已回答消息",
            value: answeredCount,
            caption: "当前会话中由 AI 回答的消息数",
            tone: "success"
        },
        {
            key: "handoff",
            title: "待转人工",
            value: noHitCount,
            caption: "当前会话中需要人工支持的消息数",
            tone: noHitCount > 0 ? "danger" : "info"
        },
        {
            key: "tickets",
            title: "我的工单",
            value: openTickets.length,
            caption: `${WorkbenchCommon.formatNumber(resolvedTickets.length)} 个已解决 / 已关闭`,
            tone: openTickets.length > 0 ? "danger" : "info"
        }
    ];
}

function renderOverviewCardHtml(card) {
    return `
        <article class="stat-card assistant-overview-card ${card.tone ? `tone-${card.tone}` : ""}">
            <span class="stat-label">${WorkbenchCommon.escapeHtml(card.title)}</span>
            <strong class="stat-value">${WorkbenchCommon.formatNumber(card.value || 0)}</strong>
            <div class="stat-caption">${WorkbenchCommon.escapeHtml(card.caption)}</div>
        </article>
    `;
}

function scheduleAssistantGeometrySync() {
    const sync = () => requestAnimationFrame(applyAssistantGeometry);
    sync();
    setTimeout(sync, 120);
    setTimeout(sync, 500);
    window.addEventListener("resize", sync);
}

function applyAssistantGeometry() {
    const shell = document.querySelector(".console-shell");
    const header = document.querySelector('.console-header[data-page-key="assistant"]');
    const workspace = document.getElementById("workspace");
    const primaryGrid = document.getElementById("assistantPrimaryGrid");
    const sidebar = document.getElementById("assistantSidebar");
    const chatPanel = document.querySelector(".assistant-chat-panel");
    if (!shell || !header || !workspace || !primaryGrid || !sidebar || !chatPanel) {
        return;
    }

    shell.style.maxWidth = "none";
    shell.style.width = "100vw";
    shell.style.margin = "0";
    shell.style.padding = "8px 14px 14px";
    shell.style.boxSizing = "border-box";

    header.style.margin = "0";
    header.style.borderRadius = "20px";

    workspace.style.gap = "8px";
    workspace.style.marginTop = "0px";
    primaryGrid.style.marginTop = "0px";
    primaryGrid.style.gap = "10px";
    primaryGrid.style.alignItems = "stretch";

    const headerBottom = header.getBoundingClientRect().bottom;
    const primaryTop = primaryGrid.getBoundingClientRect().top;
    const desiredTop = headerBottom + 8;
    const extraGap = primaryTop - desiredTop;
    if (extraGap > 2) {
        workspace.style.marginTop = `${Math.round(-extraGap)}px`;
    }

    const availableHeight = Math.max(640, Math.round(window.innerHeight - desiredTop - 16));
    [sidebar, chatPanel].forEach((element) => {
        element.style.height = `${availableHeight}px`;
        element.style.minHeight = "640px";
        element.style.marginTop = "0";
        element.style.alignSelf = "stretch";
        element.style.borderRadius = "20px";
    });
}

function cacheWorkbenchElements() {
    [
        "statusBanner",
        "sessionName",
        "sessionMeta",
        "refreshButton",
        "logoutButton",
        "loginPanel",
        "workspace",
        "loginForm",
        "usernameInput",
        "passwordInput",
        "loginButton",
        "contextBanner",
        "overviewMetrics",
        "selectedKbSummary",
        "knowledgeBaseList",
        "assistantPrimaryGrid",
        "assistantSidebar",
        "sidebarToggleButton",
        "knowledgeBaseSelect",
        "sessionTitleInput",
        "createSessionButton",
        "promptSuggestionDrawer",
        "promptSuggestionList",
        "sessionListMeta",
        "sessionList",
        "messageMeta",
        "askForm",
        "questionInput",
        "askButton",
        "knowledgeBasePicker",
        "knowledgeBasePickerLabel",
        "knowledgeBasePickerMenu",
        "modelPicker",
        "modelPickerLabel",
        "modelPickerMenu",
        "messageList",
        "scrollToBottomButton",
        "messageDetail",
        "ticketMeta",
        "ticketList",
        "ticketDetail"
    ].forEach((id) => {
        workbenchElements[id] = document.getElementById(id);
    });
}

function initWorkbenchStateFromQuery() {
    const { knowledgeBaseId, sessionId, messageId, ticketId } = workbenchState.query;
    workbenchState.selectedKnowledgeBaseId = knowledgeBaseId ? Number(knowledgeBaseId) : null;
    workbenchState.selectedSessionId = sessionId ? Number(sessionId) : null;
    workbenchState.selectedMessageId = messageId ? Number(messageId) : null;
    workbenchState.selectedTicketId = ticketId ? Number(ticketId) : null;
}

function bindWorkbenchEvents() {
    workbenchElements.loginForm.addEventListener("submit", handleWorkbenchLogin);
    workbenchElements.logoutButton.addEventListener("click", logoutWorkbench);
    workbenchElements.refreshButton.addEventListener("click", async () => {
        await loadWorkbenchData(true);
        setWorkbenchBanner("智能问答入口已刷新。", "success");
    });
    workbenchElements.createSessionButton.addEventListener("click", createSession);
    workbenchElements.sidebarToggleButton.addEventListener("click", toggleAssistantSidebar);
    workbenchElements.askForm.addEventListener("submit", askQuestion);
    workbenchElements.questionInput.addEventListener("keydown", handleQuestionInputKeydown);
    workbenchElements.messageList.addEventListener("click", closePromptDrawer);
    workbenchElements.messageList.addEventListener("scroll", updateScrollToBottomButton);
    workbenchElements.scrollToBottomButton.addEventListener("click", () => scrollMessagesToBottom("smooth"));
    workbenchElements.knowledgeBaseSelect.addEventListener("change", async () => {
        await switchKnowledgeBase(Number(workbenchElements.knowledgeBaseSelect.value || 0));
    });
    document.addEventListener("pointerdown", closeFloatingMenusOnOutsideClick, true);
    document.addEventListener("click", closeFloatingMenusOnOutsideClick, true);
}

async function restoreWorkbenchSession() {
    try {
        const [user, knowledgeBases] = await Promise.all([
            WorkbenchCommon.api("/api/v1/auth/me", { token: workbenchState.token }),
            WorkbenchCommon.api("/api/v1/app/knowledge-bases/options", { token: workbenchState.token })
        ]);
        workbenchState.user = user;
        workbenchState.knowledgeBases = knowledgeBases || [];
        showWorkbenchLoggedInState();
        renderWorkbenchContext();
        await loadWorkbenchData(true);
    } catch (error) {
        handleWorkbenchAuthFailure(error, "登录已过期，请重新登录。");
    }
}

async function handleWorkbenchLogin(event) {
    event.preventDefault();
    const username = workbenchElements.usernameInput.value.trim();
    const password = workbenchElements.passwordInput.value;
    if (!username || !password) {
        setWorkbenchBanner("请输入账号和密码。", "error");
        return;
    }

    workbenchElements.loginButton.disabled = true;
    setWorkbenchBanner("正在登录智能问答入口...", "info");
    try {
        const loginData = await WorkbenchCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        workbenchState.token = loginData.token;
        WorkbenchCommon.saveToken(loginData.token);
        workbenchElements.passwordInput.value = "";
        await restoreWorkbenchSession();
        setWorkbenchBanner("智能问答入口已就绪。", "success");
    } catch (error) {
        setWorkbenchBanner(error.message || "登录失败。", "error");
    } finally {
        workbenchElements.loginButton.disabled = false;
    }
}

function logoutWorkbench() {
    workbenchState.token = "";
    workbenchState.user = null;
    workbenchState.knowledgeBases = [];
    workbenchState.selectedKnowledgeBaseId = null;
    workbenchState.sessions = [];
    workbenchState.selectedSessionId = null;
    workbenchState.messages = [];
    workbenchState.selectedMessageId = null;
    workbenchState.selectedMessage = null;
    workbenchState.tickets = [];
    workbenchState.selectedTicketId = null;
    workbenchState.selectedTicket = null;
    workbenchState.selectedTicketComments = [];
    WorkbenchCommon.clearToken();
    syncWorkbenchQuery();
    showWorkbenchLoggedOutState();
    setWorkbenchBanner("已退出智能问答入口。", "success");
}

function showWorkbenchLoggedOutState() {
    workbenchElements.loginPanel.hidden = false;
    workbenchElements.workspace.hidden = true;
    workbenchElements.sessionName.textContent = "未登录";
    workbenchElements.sessionMeta.textContent = "请先登录后开始智能问答体验。";
}

function showWorkbenchLoggedInState() {
    workbenchElements.loginPanel.hidden = true;
    workbenchElements.workspace.hidden = false;
    workbenchElements.sessionName.textContent = workbenchState.user.realName || workbenchState.user.username;
    workbenchElements.sessionMeta.textContent = WorkbenchCommon.formatUserSessionMeta(workbenchState.user);
}

function renderWorkbenchContext() {
    workbenchElements.contextBanner.hidden = true;
    workbenchElements.contextBanner.textContent = "";
    renderFocusPopover();
}

async function loadWorkbenchData(keepSelections) {
    const [sessionsPage, ticketsPage] = await Promise.all([
        WorkbenchCommon.api("/api/v1/app/qa/sessions?pageNo=1&pageSize=50", { token: workbenchState.token }),
        WorkbenchCommon.api("/api/v1/app/tickets?pageNo=1&pageSize=50", { token: workbenchState.token })
    ]);

    workbenchState.sessions = sessionsPage.records || [];
    workbenchState.tickets = ticketsPage.records || [];
    ensureKnowledgeBaseSelection();
    renderWorkbenchChrome();

    const sessionId = resolveSelectedSessionId(keepSelections);
    if (sessionId) {
        await selectSession(sessionId, true);
    } else {
        clearMessageState();
        syncWorkbenchQuery();
        renderWorkbenchChrome();
        renderMessageList();
        renderMessageDetail();
    }

    const ticketId = resolveSelectedTicketId(keepSelections);
    if (ticketId) {
        await selectTicket(ticketId, true);
    } else {
        clearTicketState();
        syncWorkbenchQuery();
        renderWorkbenchChrome();
        renderTicketDetail();
    }
}

function ensureKnowledgeBaseSelection() {
    const availableIds = new Set(workbenchState.knowledgeBases.map((item) => String(item.id)));
    if (workbenchState.selectedKnowledgeBaseId && availableIds.has(String(workbenchState.selectedKnowledgeBaseId))) {
        return;
    }

    const queryKnowledgeBaseId = workbenchState.query.knowledgeBaseId ? Number(workbenchState.query.knowledgeBaseId) : null;
    if (queryKnowledgeBaseId && availableIds.has(String(queryKnowledgeBaseId))) {
        workbenchState.selectedKnowledgeBaseId = queryKnowledgeBaseId;
        return;
    }

    const selectedSession = workbenchState.sessions.find((item) => item.id === workbenchState.selectedSessionId);
    if (selectedSession && availableIds.has(String(selectedSession.knowledgeBaseId))) {
        workbenchState.selectedKnowledgeBaseId = selectedSession.knowledgeBaseId;
        return;
    }

    const firstSession = workbenchState.sessions.find((item) => availableIds.has(String(item.knowledgeBaseId)));
    if (firstSession) {
        workbenchState.selectedKnowledgeBaseId = firstSession.knowledgeBaseId;
        return;
    }

    workbenchState.selectedKnowledgeBaseId = workbenchState.knowledgeBases[0]?.id || null;
}

async function switchKnowledgeBase(knowledgeBaseId) {
    workbenchState.selectedKnowledgeBaseId = knowledgeBaseId || null;
    const currentSession = getSelectedSession();
    const filteredSessions = getFilteredSessions();
    renderWorkbenchChrome();

    if (currentSession && String(currentSession.knowledgeBaseId) === String(workbenchState.selectedKnowledgeBaseId)) {
        syncWorkbenchQuery();
        return;
    }

    if (filteredSessions.length) {
        await selectSession(filteredSessions[0].id, false);
        return;
    }

    clearMessageState();
    syncWorkbenchQuery();
    renderWorkbenchChrome();
    renderMessageList();
    renderMessageDetail();
}

function renderWorkbenchChrome() {
    renderKnowledgeBaseOptions();
    renderKnowledgeBasePicker();
    renderModelPicker();
    renderOverviewMetrics();
    renderKnowledgeBaseSummary();
    renderKnowledgeBaseCards();
    renderPromptSuggestions();
    renderSessionList();
    renderTicketList();
}

function renderKnowledgeBaseOptions() {
    const options = workbenchState.knowledgeBases || [];
    if (!options.length) {
        workbenchElements.knowledgeBaseSelect.innerHTML = `<option value="">暂无已启用知识库</option>`;
        return;
    }

    workbenchElements.knowledgeBaseSelect.innerHTML = options.map((item) => `
        <option value="${item.id}" ${String(item.id) === String(workbenchState.selectedKnowledgeBaseId) ? "selected" : ""}>
            ${WorkbenchCommon.escapeHtml(item.kbName)} (${WorkbenchCommon.escapeHtml(String(item.docCount ?? 0))} 个文档)
        </option>
    `).join("");
}

function renderKnowledgeBasePicker() {
    const options = workbenchState.knowledgeBases || [];
    const selected = getSelectedKnowledgeBase();
    if (!options.length) {
        workbenchElements.knowledgeBasePickerLabel.textContent = "暂无知识库";
        workbenchElements.knowledgeBasePickerMenu.innerHTML = `
            <div class="assistant-kb-picker-empty">请先在管理台启用知识库。</div>
        `;
        return;
    }

    workbenchElements.knowledgeBasePickerLabel.textContent = selected
        ? `${selected.kbName} · ${WorkbenchCommon.formatNumber(selected.docCount || 0)} 文档`
        : "选择知识库";
    workbenchElements.knowledgeBasePickerMenu.innerHTML = options.map((item) => {
        const active = String(item.id) === String(workbenchState.selectedKnowledgeBaseId);
        return `
            <button class="assistant-kb-option ${active ? "is-active" : ""}" type="button" data-kb-id="${item.id}">
                <span>${WorkbenchCommon.escapeHtml(item.kbName)}</span>
                <small>${WorkbenchCommon.escapeHtml(item.kbCode || "未设置编码")} · ${WorkbenchCommon.formatNumber(item.docCount || 0)} 个文档</small>
            </button>
        `;
    }).join("");

    workbenchElements.knowledgeBasePickerMenu.querySelectorAll(".assistant-kb-option").forEach((button) => {
        button.addEventListener("click", async () => {
            workbenchElements.knowledgeBasePicker.open = false;
            await switchKnowledgeBase(Number(button.dataset.kbId));
        });
    });
}

function renderModelPicker() {
    const selected = getSelectedModelOption();
    workbenchElements.modelPickerLabel.textContent = selected.label;
    workbenchElements.modelPickerMenu.innerHTML = MODEL_OPTIONS.map((item) => {
        const active = item.value === workbenchState.selectedModelPreference;
        return `
            <button class="assistant-kb-option assistant-model-option ${active ? "is-active" : ""}" type="button" data-model-preference="${item.value}">
                <span>${WorkbenchCommon.escapeHtml(item.label)}</span>
                <small>${WorkbenchCommon.escapeHtml(item.description)}</small>
            </button>
        `;
    }).join("");

    workbenchElements.modelPickerMenu.querySelectorAll("[data-model-preference]").forEach((button) => {
        button.addEventListener("click", () => {
            workbenchState.selectedModelPreference = button.dataset.modelPreference || "FAST";
            workbenchElements.modelPicker.open = false;
            renderModelPicker();
        });
    });
}

function getSelectedModelOption() {
    return MODEL_OPTIONS.find((item) => item.value === workbenchState.selectedModelPreference) || MODEL_OPTIONS[0];
}

function toggleAssistantSidebar() {
    const collapsed = workbenchElements.assistantPrimaryGrid.classList.toggle("sidebar-collapsed");
    workbenchElements.sidebarToggleButton.title = collapsed ? "展开侧栏" : "收起侧栏";
    workbenchElements.sidebarToggleButton.setAttribute("aria-label", collapsed ? "展开会话侧栏" : "收起会话侧栏");
}

function closeFloatingMenusOnOutsideClick(event) {
    closeDetailsOnOutsideClick(workbenchElements.knowledgeBasePicker, event);
    closeDetailsOnOutsideClick(workbenchElements.modelPicker, event);
    document.querySelectorAll(".assistant-prompt-drawer[open]").forEach((drawer) => {
        closeDetailsOnOutsideClick(drawer, event);
    });
}

function closePromptDrawer() {
    document.querySelectorAll(".assistant-prompt-drawer[open]").forEach((drawer) => {
        drawer.open = false;
    });
}

function closeDetailsOnOutsideClick(detailsElement, event) {
    if (!detailsElement?.open) {
        return;
    }
    const eventPath = typeof event.composedPath === "function" ? event.composedPath() : [];
    const clickedInside = eventPath.includes(detailsElement) || detailsElement.contains(event.target);
    if (clickedInside) {
        return;
    }
    detailsElement.open = false;
}

function renderOverviewMetrics() {
    workbenchElements.overviewMetrics.hidden = true;
    workbenchElements.overviewMetrics.innerHTML = "";
    renderFocusPopover();
}

function renderKnowledgeBaseSummary() {
    const knowledgeBase = getSelectedKnowledgeBase();
    if (!knowledgeBase) {
        workbenchElements.selectedKbSummary.innerHTML = WorkbenchCommon.renderStateBlock({
            title: "当前没有可用知识库",
            message: "请先启用至少一个知识库，再使用智能问答入口。",
            compact: true
        });
        return;
    }

    const sessionCount = workbenchState.sessions.filter((item) => String(item.knowledgeBaseId) === String(knowledgeBase.id)).length;
    workbenchElements.selectedKbSummary.innerHTML = `
        <section class="summary-card assistant-kb-summary">
            <span class="panel-kicker">当前知识库</span>
            <strong>${WorkbenchCommon.escapeHtml(knowledgeBase.kbName)}</strong>
            <div class="chips" style="margin-top:10px;">
                <span class="status-pill">${KbLabel(knowledgeBase.kbCode)}</span>
                <span class="status-pill">${WorkbenchCommon.formatNumber(knowledgeBase.docCount || 0)} 个文档</span>
                <span class="status-pill">${WorkbenchCommon.formatNumber(sessionCount)} 个会话</span>
                <span class="status-pill">${WorkbenchCommon.formatNumber(workbenchState.tickets.length)} 个工单</span>
            </div>
            <p class="assistant-summary-caption">${WorkbenchCommon.escapeHtml(knowledgeBase.description || "暂未填写描述，可使用该知识库处理日常服务问题。")}</p>
            <div class="action-row" style="margin-top:14px;">
                <a class="link-button" href="/admin/knowledge-bases?knowledgeBaseId=${knowledgeBase.id}&source=assistant">打开后台知识库</a>
                <a class="link-button" href="/admin/documents?knowledgeBaseId=${knowledgeBase.id}&source=assistant">查看文档管理</a>
            </div>
        </section>
    `;
}

function renderKnowledgeBaseCards() {
    if (!workbenchState.knowledgeBases.length) {
        workbenchElements.knowledgeBaseList.innerHTML = "";
        return;
    }

    workbenchElements.knowledgeBaseList.innerHTML = workbenchState.knowledgeBases.map((knowledgeBase) => {
        const isActive = String(knowledgeBase.id) === String(workbenchState.selectedKnowledgeBaseId);
        const sessionCount = workbenchState.sessions.filter((item) => String(item.knowledgeBaseId) === String(knowledgeBase.id)).length;
        return `
            <article class="assistant-kb-card ${isActive ? "active" : ""}">
                <div class="assistant-kb-title-row">
                    <div class="assistant-kb-copy">
                        <strong>${WorkbenchCommon.escapeHtml(knowledgeBase.kbName)}</strong>
                        <div class="meta-line">${KbLabel(knowledgeBase.kbCode)}</div>
                    </div>
                    <span class="status-pill">${WorkbenchCommon.formatNumber(knowledgeBase.docCount || 0)} 个文档</span>
                </div>
                <p>${WorkbenchCommon.escapeHtml(WorkbenchCommon.truncateText(knowledgeBase.description || "该知识空间已可用于智能问答与工单转交。", 120))}</p>
                <div class="assistant-kb-stat-row">
                    <span class="tag">${WorkbenchCommon.formatNumber(sessionCount)} 个会话</span>
                    <span class="tag">${WorkbenchCommon.formatNumber(knowledgeBase.docCount || 0)} 个可用文档</span>
                </div>
                <div class="footer-actions" style="margin-top:12px;">
                    <button class="link-button" type="button" data-kb-id="${knowledgeBase.id}">${isActive ? "已选中" : "使用该知识库"}</button>
                </div>
            </article>
        `;
    }).join("");

    workbenchElements.knowledgeBaseList.querySelectorAll("[data-kb-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await switchKnowledgeBase(Number(button.dataset.kbId));
        });
    });
}

function renderPromptSuggestions() {
    const knowledgeBase = getSelectedKnowledgeBase();
    if (!knowledgeBase) {
        workbenchElements.promptSuggestionList.innerHTML = WorkbenchCommon.renderStateBlock({
            title: "请先选择知识库",
            message: "选中一个知识空间后，系统会根据该库生成推荐问题。",
            compact: true
        });
        return;
    }

    const prompts = buildPromptSuggestions(knowledgeBase);
    workbenchElements.promptSuggestionList.innerHTML = prompts.map((item) => `
        <button class="assistant-prompt-button" type="button" data-prompt-text="${WorkbenchCommon.escapeAttr(item.text)}">
            <strong>${WorkbenchCommon.escapeHtml(item.title)}</strong>
            <span>${WorkbenchCommon.escapeHtml(item.text)}</span>
        </button>
    `).join("");

    workbenchElements.promptSuggestionList.querySelectorAll("[data-prompt-text]").forEach((button) => {
        button.addEventListener("click", () => {
            workbenchElements.questionInput.value = button.dataset.promptText || "";
            workbenchElements.promptSuggestionDrawer.open = false;
            workbenchElements.questionInput.focus();
        });
    });
}

async function createSession() {
    const knowledgeBaseId = Number(workbenchElements.knowledgeBaseSelect.value || workbenchState.selectedKnowledgeBaseId || 0);
    if (!knowledgeBaseId) {
        setWorkbenchBanner("请先选择一个已启用的知识库。", "error");
        return;
    }

    const knowledgeBase = getKnowledgeBaseById(knowledgeBaseId);
    const title = workbenchElements.sessionTitleInput.value.trim() || buildAutoSessionTitle("", knowledgeBase);
    workbenchElements.createSessionButton.disabled = true;
    try {
        const session = await createSessionRequest(knowledgeBaseId, title);
        workbenchElements.sessionTitleInput.value = "";
        workbenchState.selectedKnowledgeBaseId = knowledgeBaseId;
        await loadWorkbenchData(false);
        await selectSession(session.id, false);
        setWorkbenchBanner("会话已创建，现在可以开始提问。", "success");
    } catch (error) {
        if (error instanceof WorkbenchCommon.AuthExpiredError) {
            handleWorkbenchAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setWorkbenchBanner(error.message || "创建会话失败。", "error");
    } finally {
        workbenchElements.createSessionButton.disabled = false;
    }
}

async function createSessionRequest(knowledgeBaseId, sessionTitle) {
    return WorkbenchCommon.api("/api/v1/app/qa/sessions", {
        token: workbenchState.token,
        method: "POST",
        body: JSON.stringify({
            knowledgeBaseId,
            sessionTitle
        })
    });
}

async function selectSession(sessionId, silent) {
    const session = workbenchState.sessions.find((item) => item.id === sessionId);
    if (!session) {
        clearMessageState();
        syncWorkbenchQuery();
        renderWorkbenchChrome();
        renderMessageList();
        renderMessageDetail();
        return;
    }

    workbenchState.selectedSessionId = sessionId;
    workbenchState.selectedKnowledgeBaseId = session.knowledgeBaseId;
    syncWorkbenchQuery();
    renderWorkbenchChrome();

    if (!silent) {
        workbenchElements.messageList.innerHTML = WorkbenchCommon.renderStateBlock({
            type: "loading",
            title: "正在加载会话内容",
            message: "系统正在读取这条会话下的完整问答历史。",
            compact: true
        });
    }

    try {
        const messagePage = await WorkbenchCommon.api(
            `/api/v1/app/qa/messages?sessionId=${sessionId}&pageNo=1&pageSize=100`,
            { token: workbenchState.token }
        );
        workbenchState.messages = messagePage.records || [];
        renderWorkbenchChrome();
        renderMessageList();

        const messageId = resolveSelectedMessageId();
        if (messageId) {
            await selectMessage(messageId, true);
        } else {
            workbenchState.selectedMessageId = null;
            workbenchState.selectedMessage = null;
            renderMessageDetail();
        }
    } catch (error) {
        if (error instanceof WorkbenchCommon.AuthExpiredError) {
            handleWorkbenchAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setWorkbenchBanner(error.message || "加载会话消息失败。", "error");
        workbenchElements.messageList.innerHTML = WorkbenchCommon.renderStateBlock({
            type: "error",
            title: "会话内容加载失败",
            message: error.message || "未知错误",
            compact: true
        });
    }
}

function resolveSelectedSessionId(keepSelections) {
    const filteredSessions = getFilteredSessions();
    if (keepSelections && workbenchState.selectedSessionId && filteredSessions.some((item) => item.id === workbenchState.selectedSessionId)) {
        return workbenchState.selectedSessionId;
    }
    const querySessionId = workbenchState.query.sessionId ? Number(workbenchState.query.sessionId) : null;
    if (querySessionId && filteredSessions.some((item) => item.id === querySessionId)) {
        return querySessionId;
    }
    return filteredSessions[0]?.id || null;
}

function resolveSelectedMessageId() {
    if (workbenchState.selectedMessageId && workbenchState.messages.some((item) => item.id === workbenchState.selectedMessageId)) {
        return workbenchState.selectedMessageId;
    }
    const queryMessageId = workbenchState.query.messageId ? Number(workbenchState.query.messageId) : null;
    if (queryMessageId && workbenchState.messages.some((item) => item.id === queryMessageId)) {
        return queryMessageId;
    }
    return workbenchState.messages.at(-1)?.id || null;
}

async function askQuestion(event) {
    event.preventDefault();
    const question = workbenchElements.questionInput.value.trim();
    if (!question) {
        setWorkbenchBanner("请输入问题。", "error");
        return;
    }

    workbenchElements.askButton.disabled = true;
    let pendingId = null;
    try {
        const sessionId = await ensureSessionForQuestion(question);
        workbenchElements.questionInput.value = "";
        pendingId = addPendingMessage(sessionId, question);
        setWorkbenchBanner("问题已提交，后台正在异步检索知识库并生成回答...", "info");

        const message = await WorkbenchCommon.api("/api/v1/app/qa/messages", {
            token: workbenchState.token,
            method: "POST",
            body: JSON.stringify({
                sessionId,
                question,
                modelPreference: workbenchState.selectedModelPreference || "FAST"
            })
        });
        removePendingMessage(pendingId);
        await loadWorkbenchData(true);
        await selectSession(sessionId, true);
        await pollMessageUntilFinished(message.id, sessionId);
    } catch (error) {
        if (error instanceof WorkbenchCommon.AuthExpiredError) {
            handleWorkbenchAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        if (pendingId) {
            markPendingMessageFailed(pendingId, error.message || "提问失败，请稍后重试。");
        }
        setWorkbenchBanner(error.message || "提问失败。", "error");
    } finally {
        workbenchElements.askButton.disabled = false;
    }
}

async function pollMessageUntilFinished(messageId, sessionId) {
    const maxAttempts = 45;
    const startedAt = Date.now();
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
        const message = await WorkbenchCommon.api(`/api/v1/app/qa/messages/${messageId}`, { token: workbenchState.token });
        upsertWorkbenchMessage(message);
        workbenchState.selectedMessageId = message.id;
        workbenchState.selectedMessage = message;
        renderMessageList();
        renderMessageDetail();
        scrollMessagesToBottom();

        if (message.answerStatus && message.answerStatus !== "GENERATING") {
            setWorkbenchBanner(
                message.needHumanHandoff
                    ? "当前未检索到可靠答案，你可以在详情区将其转交人工支持。"
                    : "已成功生成带依据的回答。",
                message.needHumanHandoff ? "info" : "success"
            );
            return message;
        }
        setWorkbenchBanner(resolveGeneratingHint(Date.now() - startedAt), "info");
        await sleep(resolvePollingDelay(Date.now() - startedAt, attempt));
    }
    await selectSession(sessionId, true);
    await selectMessage(messageId, false);
    setWorkbenchBanner("回答仍在生成中，你可以稍后刷新查看结果。", "info");
    return null;
}

function resolvePollingDelay(elapsedMs, attempt) {
    if (attempt < 2) {
        return 260;
    }
    if (elapsedMs < 3000) {
        return 420;
    }
    if (elapsedMs < 10000) {
        return 850;
    }
    return 1600;
}

function resolveGeneratingHint(elapsedMs) {
    if (elapsedMs < 1200) {
        return "问题已提交，正在进入知识库检索。";
    }
    if (elapsedMs < 3500) {
        return "正在检索知识库并排序相关片段。";
    }
    if (elapsedMs < 9000) {
        return "正在组织证据并生成回答。";
    }
    return "回答即将完成，复杂问题可能需要多等待几秒。";
}

function upsertWorkbenchMessage(message) {
    const index = workbenchState.messages.findIndex((item) => item.id === message.id);
    if (index >= 0) {
        workbenchState.messages.splice(index, 1, message);
    } else {
        workbenchState.messages = [...workbenchState.messages, message];
    }
}

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}
async function ensureSessionForQuestion(question) {
    const knowledgeBaseId = Number(workbenchElements.knowledgeBaseSelect.value || workbenchState.selectedKnowledgeBaseId || 0);
    if (!knowledgeBaseId) {
        throw new Error("请先选择一个已启用的知识库再提问。");
    }

    const activeSession = getSelectedSession();
    if (activeSession && String(activeSession.knowledgeBaseId) === String(knowledgeBaseId)) {
        return activeSession.id;
    }

    const knowledgeBase = getKnowledgeBaseById(knowledgeBaseId);
    const sessionTitle = workbenchElements.sessionTitleInput.value.trim() || buildAutoSessionTitle(question, knowledgeBase);
    const createdSession = await createSessionRequest(knowledgeBaseId, sessionTitle);
    workbenchState.selectedKnowledgeBaseId = knowledgeBaseId;
    workbenchState.selectedSessionId = createdSession.id;
    workbenchElements.sessionTitleInput.value = "";
    return createdSession.id;
}

function focusWorkbenchPanel(panelId) {
    const panel = document.getElementById(panelId);
    if (!panel) {
        return;
    }
    panel.scrollIntoView({ behavior: "smooth", block: "start" });
    panel.classList.remove("assistant-panel-focus");
    void panel.offsetWidth;
    panel.classList.add("assistant-panel-focus");
    window.setTimeout(() => panel.classList.remove("assistant-panel-focus"), 1600);
}

async function selectMessage(messageId, silent) {
    if (!messageId) {
        return;
    }
    workbenchState.selectedMessageId = messageId;
    syncWorkbenchQuery();
    renderMessageList();

    if (!silent) {
        workbenchElements.messageDetail.innerHTML = WorkbenchCommon.renderStateBlock({
            type: "loading",
            title: "正在加载回答洞察",
            message: "系统正在拉取证据片段、模型信息和后续动作。",
            compact: true
        });
    }

    try {
        const detail = await WorkbenchCommon.api(`/api/v1/app/qa/messages/${messageId}`, { token: workbenchState.token });
        workbenchState.selectedMessage = detail;
        syncHandoffDraftFromMessage(detail);
        const linkedTicket = getLinkedTicketForMessageId(messageId);
        renderMessageList();
        renderMessageDetail();
        if (!silent) {
            focusWorkbenchPanel("messageInsightPanel");
            setWorkbenchBanner("已打开回答洞察区，可以查看证据、反馈或转人工操作。", "info");
        }
        if (linkedTicket && linkedTicket.id !== workbenchState.selectedTicketId) {
            await selectTicket(linkedTicket.id, true);
        }
    } catch (error) {
        if (error instanceof WorkbenchCommon.AuthExpiredError) {
            handleWorkbenchAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setWorkbenchBanner(error.message || "回答洞察加载失败。", "error");
        workbenchElements.messageDetail.innerHTML = WorkbenchCommon.renderStateBlock({
            type: "error",
            title: "回答洞察加载失败",
            message: error.message || "未知错误",
            compact: true
        });
    }
}

function syncHandoffDraftFromMessage(message) {
    workbenchState.handoffDraft = {
        title: `消息 #${message.id} 需要人工跟进`,
        content: `问题：${message.questionText}\n\nAI 回答：${message.answerText || "暂无回答"}\n\n请由人工继续跟进该问题。`,
        priority: "HIGH"
    };
}

async function submitFeedback(feedbackType) {
    if (!workbenchState.selectedMessageId) {
        return;
    }

    try {
        await WorkbenchCommon.api(`/api/v1/app/qa/messages/${workbenchState.selectedMessageId}/feedback`, {
            token: workbenchState.token,
            method: "POST",
            body: JSON.stringify({
                feedbackType,
                feedbackReason: feedbackType === "LIKE" ? "回答有帮助" : "需要更好的支持"
            })
        });
        setWorkbenchBanner(`反馈已提交：${feedbackType}。`, "success");
    } catch (error) {
        if (error instanceof WorkbenchCommon.AuthExpiredError) {
            handleWorkbenchAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setWorkbenchBanner(error.message || "提交反馈失败。", "error");
    }
}

async function handoffMessageFromChat(messageId) {
    const message = workbenchState.messages.find((item) => String(item.id) === String(messageId));
    if (!message) {
        setWorkbenchBanner("没有找到要转人工的问答记录，请刷新后重试。", "error");
        return;
    }
    const linkedTicket = getLinkedTicketForMessageId(message.id);
    if (linkedTicket) {
        await selectTicket(linkedTicket.id, false);
        setWorkbenchBanner(`这条问题已经关联工单 ${linkedTicket.ticketNo || `#${linkedTicket.id}`}。`, "info");
        return;
    }
    try {
        const ticket = await createHandoffTicket(message, buildDefaultHandoffPayload(message));
        await loadWorkbenchData(true);
        await selectMessage(message.id, true);
        await selectTicket(ticket.id, false);
        setWorkbenchBanner(`已创建人工工单 ${ticket.ticketNo || `#${ticket.id}`}，支持人员可继续跟进。`, "success");
    } catch (error) {
        if (error instanceof WorkbenchCommon.AuthExpiredError) {
            handleWorkbenchAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setWorkbenchBanner(error.message || "创建工单失败。", "error");
    }
}

async function createHandoffTicket(message, payload) {
    return WorkbenchCommon.api(`/api/v1/app/qa/messages/${message.id}/handoff`, {
        token: workbenchState.token,
        method: "POST",
        body: JSON.stringify(payload)
    });
}

function buildDefaultHandoffPayload(message) {
    return {
        title: buildDefaultHandoffTitle(message),
        content: buildDefaultHandoffContent(message),
        priority: message.needHumanHandoff ? "HIGH" : "MEDIUM"
    };
}

function buildDefaultHandoffTitle(message) {
    const question = String(message.questionText || "知识库问题需要人工处理").trim();
    return question.length > 80 ? `${question.slice(0, 80)}...` : question;
}

function buildDefaultHandoffContent(message) {
    const question = message.questionText || "暂无问题内容";
    const answer = message.answerText || "AI 未能生成可靠回答";
    const sourceCount = WorkbenchCommon.formatNumber(message.sourceCount || 0);
    return [`用户问题：${question}`, `AI 当前回答：${answer}`, `检索证据数量：${sourceCount}`, "请人工复核该问题，并在解决后沉淀为知识草稿。"].join("\n\n");
}

async function handoffSelectedMessage() {
    const message = workbenchState.selectedMessage;
    if (!message || !workbenchState.selectedMessageId) {
        return;
    }

    const title = document.getElementById("handoffTitleInput")?.value.trim() || workbenchState.handoffDraft.title;
    const content = document.getElementById("handoffContentInput")?.value.trim() || workbenchState.handoffDraft.content;
    const priority = document.getElementById("handoffPriorityInput")?.value || workbenchState.handoffDraft.priority;

    try {
        const ticket = await createHandoffTicket(message, {
            title,
            content,
            priority
        });
        await loadWorkbenchData(true);
        await selectMessage(message.id, true);
        await selectTicket(ticket.id, false);
        setWorkbenchBanner(`已基于当前问题创建工单 ${ticket.ticketNo}。`, "success");
    } catch (error) {
        if (error instanceof WorkbenchCommon.AuthExpiredError) {
            handleWorkbenchAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setWorkbenchBanner(error.message || "创建工单失败。", "error");
    }
}

function renderSessionList() {
    const filteredSessions = getFilteredSessions();
    const selectedKnowledgeBase = getSelectedKnowledgeBase();
    workbenchElements.sessionListMeta.textContent = selectedKnowledgeBase
        ? `${selectedKnowledgeBase.kbName} 下共有 ${WorkbenchCommon.formatNumber(filteredSessions.length)} 个会话`
        : "请选择一个知识库查看对话线程。";

    if (!selectedKnowledgeBase) {
        workbenchElements.sessionList.innerHTML = WorkbenchCommon.renderStateBlock({
            title: "暂未选择知识库",
            message: "请选择一个知识空间继续操作。",
            compact: true
        });
        return;
    }
    if (!filteredSessions.length) {
        workbenchElements.sessionList.innerHTML = WorkbenchCommon.renderStateBlock({
            title: "当前还没有会话",
            message: "你可以先创建一个会话，或直接提问，系统会自动生成会话。",
            compact: true
        });
        return;
    }

    workbenchElements.sessionList.innerHTML = filteredSessions.map((session) => {
        const isActive = session.id === workbenchState.selectedSessionId;
        return `
            <article class="assistant-session-card ${isActive ? "active" : ""}" data-session-id="${session.id}" tabindex="0" role="button" aria-label="打开会话 ${WorkbenchCommon.escapeAttr(session.sessionTitle || session.sessionNo || `会话 #${session.id}`)}">
                <div class="assistant-card-head">
                    <div>
                        <strong>${WorkbenchCommon.escapeHtml(session.sessionTitle || session.sessionNo || `会话 #${session.id}`)}</strong>
                        <div class="assistant-mini-meta">${WorkbenchCommon.escapeHtml(session.sessionNo || "")}</div>
                    </div>
                    <span class="status-pill ${WorkbenchCommon.statusClass(session.status)}">${WorkbenchCommon.escapeHtml(session.status || "ACTIVE")}</span>
                </div>
                <p>${WorkbenchCommon.escapeHtml(`最近消息：${WorkbenchCommon.formatDateTime(session.lastMessageAt || session.createdAt)}`)}</p>
                <div class="footer-actions" style="margin-top:12px;">
                    <button class="link-button" type="button" tabindex="-1">${isActive ? "当前查看中" : "打开会话"}</button>
                </div>
            </article>
        `;
    }).join("");

    bindSessionListSelection();
}

function bindSessionListSelection() {
    workbenchElements.sessionList.querySelectorAll(".assistant-session-card[data-session-id]").forEach((card) => {
        const openSession = async () => {
            await selectSession(Number(card.dataset.sessionId), false);
        };
        card.addEventListener("click", openSession);
        card.addEventListener("keydown", async (event) => {
            if (event.key !== "Enter" && event.key !== " ") {
                return;
            }
            event.preventDefault();
            await openSession();
        });
    });
}

function renderMessageList() {
    const session = getSelectedSession();
    if (!session) {
        workbenchElements.messageMeta.textContent = "请选择一个会话，或直接提问开始新的对话。";
        workbenchElements.messageList.innerHTML = `
            <div class="assistant-chat-empty">
                <h4>今天想了解什么？</h4>
                <p>选择左侧知识库后直接输入问题，系统会自动开启新会话。</p>
            </div>`;
        updateScrollToBottomButton();
        return;
    }

    const displayMessages = getDisplayMessagesForSession(session.id);
    workbenchElements.messageMeta.textContent = `${session.sessionTitle || session.sessionNo} · ${WorkbenchCommon.formatNumber(displayMessages.length)} 条消息`;
    if (!displayMessages.length) {
        workbenchElements.messageList.innerHTML = `
            <div class="assistant-chat-empty">
                <h4>当前会话还没有消息</h4>
                <p>可以在底部输入框直接开始提问，或使用推荐问题快速填充。</p>
            </div>`;
        updateScrollToBottomButton();
        return;
    }

    workbenchElements.messageList.innerHTML = displayMessages.map((message) => renderChatTurn(message)).join("");
    workbenchElements.messageList.querySelectorAll("[data-message-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await selectMessage(Number(button.dataset.messageId), false);
        });
    });
    workbenchElements.messageList.querySelectorAll("[data-ticket-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await selectTicket(Number(button.dataset.ticketId), false);
        });
    });
    workbenchElements.messageList.querySelectorAll("[data-handoff-message-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await handoffMessageFromChat(button.dataset.handoffMessageId);
        });
    });
    updateScrollToBottomButton();
}

function normalizeAssistantAnswerText(value) {
    const text = String(value || "暂未生成回答。");
    if (text.includes("No reliable answer was found from the current knowledge base")) {
        return "当前知识库中没有检索到足够可靠的依据，暂时无法给出可信回答。你可以点击下方“创建人工工单”，把问题和上下文转交支持人员继续处理。";
    }
    return text;
}

function buildQaRecordAdminHref(message) {
    const params = new URLSearchParams();
    if (message?.id) {
        params.set("messageId", message.id);
    }
    params.set("source", "assistant");
    return `/admin/qa-records?${params.toString()}`;
}

function buildTicketAdminHref(ticket, message) {
    const params = new URLSearchParams();
    if (ticket?.id) {
        params.set("ticketId", ticket.id);
    }
    params.set("source", "assistant");
    if (message?.id) {
        params.set("messageId", message.id);
    }
    return `/admin/tickets?${params.toString()}`;
}

function renderAssistantMarkdown(value) {
    const text = normalizeAssistantAnswerText(value).replace(/\r\n/g, "\n").trim();
    if (!text) {
        return "<p>暂未生成回答。</p>";
    }
    const blocks = text.split(/\n{2,}/).map((block) => block.trim()).filter(Boolean);
    return blocks.map((block) => {
        const lines = block.split("\n").map((line) => line.trim()).filter(Boolean);
        const isOrderedList = lines.length > 1 && lines.every((line) => /^\d+[.、]\s+/.test(line));
        const isUnorderedList = lines.length > 1 && lines.every((line) => /^[-*]\s+/.test(line));
        if (isOrderedList || isUnorderedList) {
            const tag = isOrderedList ? "ol" : "ul";
            const items = lines.map((line) => {
                const content = line.replace(/^\d+[.、]\s+/, "").replace(/^[-*]\s+/, "");
                return `<li>${renderInlineMarkdown(content)}</li>`;
            }).join("");
            return `<${tag} class="assistant-answer-list">${items}</${tag}>`;
        }
        return `<p>${lines.map(renderInlineMarkdown).join("<br>")}</p>`;
    }).join("");
}

function renderInlineMarkdown(value) {
    return WorkbenchCommon.escapeHtml(value)
        .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
        .replace(/__([^_]+)__/g, "<strong>$1</strong>")
        .replace(/`([^`]+)`/g, "<code>$1</code>");
}

function renderChatTurn(message) {
    const linkedTicket = getLinkedTicketForMessageId(message.id);
    const shouldShowHandoffAction = !message.pending && !linkedTicket && message.needHumanHandoff;
    const isActive = message.id === workbenchState.selectedMessageId;
    return `
        <article class="assistant-chat-turn ${isActive ? "active" : ""}">
            <div class="assistant-chat-row user">
                <div class="assistant-chat-avatar">我</div>
                <div class="assistant-chat-bubble user">
                    <div class="assistant-chat-meta">${WorkbenchCommon.escapeHtml(WorkbenchCommon.formatDateTime(message.createdAt))}</div>
                    <p>${WorkbenchCommon.escapeHtml(message.questionText || "暂无问题内容")}</p>
                </div>
            </div>
            <div class="assistant-chat-row assistant">
                <div class="assistant-chat-avatar">KF</div>
                <div class="assistant-chat-bubble assistant ${message.needHumanHandoff ? "assistant-alert" : ""} ${message.pending ? "assistant-pending" : ""} ${message.failed ? "assistant-failed" : ""}">
                    <div class="assistant-chat-toolbar">
                        <span class="status-pill ${WorkbenchCommon.statusClass(message.answerStatus)}">${WorkbenchCommon.escapeHtml(message.pending ? (message.failed ? "生成失败" : "生成中") : formatWorkbenchAnswerStatus(message.answerStatus))}</span>
                        <span class="status-pill">${WorkbenchCommon.formatNumber(message.sourceCount || 0)} 条证据</span>
                        <span class="status-pill">${WorkbenchCommon.escapeHtml(message.modelName || "未知模型")}</span>
                    </div>
                    <div class="assistant-answer-content">${renderAssistantMarkdown(message.answerText)}</div>
                    ${message.pending && !message.failed ? `<div class="assistant-thinking"><i></i><i></i><i></i><span>正在检索知识库并生成回答</span></div>` : ""}
                    ${message.pending ? "" : `
                    <div class="assistant-thread-actions">
                        <a class="link-button" href="${buildQaRecordAdminHref(message)}">查看证据与操作</a>
                        ${linkedTicket ? `<a class="link-button" href="${buildTicketAdminHref(linkedTicket, message)}">打开关联工单</a>` : ""}
                        ${shouldShowHandoffAction ? `<button class="primary-button assistant-inline-handoff" type="button" data-handoff-message-id="${message.id}">创建人工工单</button>` : ""}
                    </div>`}
                </div>
            </div>
        </article>
    `;
}


function getDisplayMessagesForSession(sessionId) {
    const persistedMessages = workbenchState.messages || [];
    const pendingMessages = (workbenchState.pendingMessages || []).filter((message) => String(message.sessionId) === String(sessionId));
    return [...persistedMessages, ...pendingMessages];
}

function addPendingMessage(sessionId, question) {
    const pendingMessage = {
        id: `pending-${Date.now()}`,
        sessionId,
        pending: true,
        failed: false,
        questionText: question,
        answerText: "正在检索知识库、构造提示词并生成回答...",
        answerStatus: "GENERATING",
        modelName: getSelectedModelOption().label,
        sourceCount: 0,
        needHumanHandoff: false,
        createdAt: new Date().toISOString()
    };
    workbenchState.pendingMessages = [...(workbenchState.pendingMessages || []), pendingMessage];
    workbenchState.selectedSessionId = sessionId;
    renderMessageList();
    scrollMessagesToBottom();
    return pendingMessage.id;
}

function removePendingMessage(pendingId) {
    workbenchState.pendingMessages = (workbenchState.pendingMessages || []).filter((message) => message.id !== pendingId);
}

function markPendingMessageFailed(pendingId, errorMessage) {
    workbenchState.pendingMessages = (workbenchState.pendingMessages || []).map((message) => {
        if (message.id !== pendingId) {
            return message;
        }
        return {
            ...message,
            failed: true,
            answerStatus: "FAILED",
            answerText: errorMessage || "提问失败，请稍后重试。"
        };
    });
    renderMessageList();
    scrollMessagesToBottom();
}
function scrollMessagesToBottom(behavior = "smooth") {
    requestAnimationFrame(() => {
        const container = workbenchElements.messageList;
        if (!container) {
            return;
        }
        container.scrollTo({ top: container.scrollHeight, behavior });
        requestAnimationFrame(updateScrollToBottomButton);
    });
}

function updateScrollToBottomButton() {
    const container = workbenchElements.messageList;
    const button = workbenchElements.scrollToBottomButton;
    if (!container || !button) {
        return;
    }
    const distanceToBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    const shouldShow = distanceToBottom > 96;
    button.hidden = !shouldShow;
    button.classList.toggle("is-visible", shouldShow);
}

function handleQuestionInputKeydown(event) {
    if (event.key !== "Enter" || event.shiftKey || event.ctrlKey || event.metaKey || event.isComposing) {
        return;
    }
    event.preventDefault();
    workbenchElements.askForm.requestSubmit();
}
function renderMessageDetail() {
    const message = workbenchState.selectedMessage;
    if (!message) {
        workbenchElements.messageDetail.innerHTML = WorkbenchCommon.renderStateBlock({
            title: "暂未选择消息",
            message: "选择一条对话记录后，这里会展示证据依据与支持操作。",
            compact: true
        });
        return;
    }

    const linkedTicket = getLinkedTicketForMessageId(message.id);
    const canOpenAdminDocument = WorkbenchCommon.hasRole(workbenchState.user, "TENANT_ADMIN", "KNOWLEDGE_OPERATOR");
    const sourcesHtml = message.sources?.length
        ? message.sources.map((source) => `
            <article class="assistant-evidence-card">
                <div class="assistant-card-head">
                    <div>
                        <strong>${WorkbenchCommon.escapeHtml(source.documentName || `文档 #${source.documentId || "未知"}`)}</strong>
                        <div class="assistant-mini-meta">排序 ${WorkbenchCommon.escapeHtml(source.rankNo || "-")} · 策略 ${WorkbenchCommon.escapeHtml(formatWorkbenchRecallStrategy(source.recallStrategy))}</div>
                    </div>
                    <span class="status-pill">召回分 ${WorkbenchCommon.escapeHtml(source.recallScore ?? "-")}</span>
                </div>
                <div class="chips" style="margin-top:10px;">
                    ${source.lexicalScore != null ? `<span class="status-pill">词法 ${WorkbenchCommon.escapeHtml(source.lexicalScore)}</span>` : ""}
                    ${source.vectorScore != null ? `<span class="status-pill">向量 ${WorkbenchCommon.escapeHtml(source.vectorScore)}</span>` : ""}
                    ${canOpenAdminDocument && source.documentId ? `<a class="link-button" href="/admin/documents?documentId=${source.documentId}&source=assistant&messageId=${message.id}">打开文档</a>` : ""}
                </div>
                <p>${WorkbenchCommon.escapeHtml(source.snippetText || "")}</p>
            </article>
        `).join("")
        : `<div class="assistant-note-card"><strong>暂无检索证据</strong><p>该回答没有返回来源片段，通常意味着当前知识库未检索到足够可靠的匹配内容。</p></div>`;

    const linkedTicketSection = linkedTicket
        ? `
            <section class="detail-card assistant-ticket-banner">
                <strong>关联支持工单</strong>
                <p>这条消息已经转交人工支持，对应工单为 ${WorkbenchCommon.escapeHtml(linkedTicket.ticketNo || `工单 #${linkedTicket.id}`)}。</p>
                <div class="chips" style="margin-top:12px;">
                    <span class="status-pill ${WorkbenchCommon.statusClass(linkedTicket.status)}">${WorkbenchCommon.escapeHtml(formatWorkbenchTicketStatus(linkedTicket.status))}</span>
                    <span class="status-pill">${WorkbenchCommon.escapeHtml(formatWorkbenchPriority(linkedTicket.priority))}</span>
                    <span class="status-pill">${WorkbenchCommon.escapeHtml(linkedTicket.assigneeName || "未分配")}</span>
                </div>
                <div class="footer-actions" style="margin-top:12px;">
                    <button id="openLinkedTicketButton" class="primary-button" type="button">打开工单时间线</button>
                    <a class="link-button" href="/admin/tickets?ticketId=${linkedTicket.id}&source=assistant&messageId=${message.id}">打开后台工单</a>
                </div>
            </section>
        `
        : "";

    const handoffSection = !linkedTicket && message.needHumanHandoff ? `
        <section class="detail-card">
            <strong>转交人工支持</strong>
            <p>系统判断这条回答置信度较低，你可以创建工单，并把原始问答上下文一并带给支持团队。</p>
            <div class="form-grid" style="margin-top:14px;">
                <label class="field full-span">
                    <span>工单标题</span>
                    <input id="handoffTitleInput" type="text" value="${WorkbenchCommon.escapeAttr(workbenchState.handoffDraft.title)}">
                </label>
                <label class="field full-span">
                    <span>工单描述</span>
                    <textarea id="handoffContentInput">${WorkbenchCommon.escapeHtml(workbenchState.handoffDraft.content)}</textarea>
                </label>
                <label class="field">
                    <span>优先级</span>
                    <select id="handoffPriorityInput">
                        <option value="HIGH" ${workbenchState.handoffDraft.priority === "HIGH" ? "selected" : ""}>高</option>
                        <option value="MEDIUM" ${workbenchState.handoffDraft.priority === "MEDIUM" ? "selected" : ""}>中</option>
                        <option value="LOW" ${workbenchState.handoffDraft.priority === "LOW" ? "selected" : ""}>低</option>
                    </select>
                </label>
            </div>
            <div class="footer-actions" style="margin-top:12px;">
                <button id="handoffButton" class="primary-button" type="button">创建工单</button>
            </div>
        </section>
    ` : "";

    workbenchElements.messageDetail.innerHTML = `
        <section class="summary-card">
            <span class="panel-kicker">当前回答</span>
            <strong>${WorkbenchCommon.escapeHtml(message.questionText || "暂无问题内容")}</strong>
            <div class="chips" style="margin-top:12px;">
                <span class="status-pill ${WorkbenchCommon.statusClass(message.answerStatus)}">${WorkbenchCommon.escapeHtml(formatWorkbenchAnswerStatus(message.answerStatus))}</span>
                <span class="status-pill">${message.needHumanHandoff ? "需要人工跟进" : "已返回有依据回答"}</span>
                <span class="status-pill">${WorkbenchCommon.escapeHtml(message.modelName || "未知模型")}</span>
                <span class="status-pill">${WorkbenchCommon.escapeHtml(WorkbenchCommon.formatDateTime(message.createdAt))}</span>
            </div>
            <p>${WorkbenchCommon.escapeHtml(message.answerText || "暂无回答内容")}</p>
        </section>

        <section class="detail-columns">
            <article class="detail-card">
                <strong>回答指标</strong>
                <p>总耗时：${WorkbenchCommon.escapeHtml(formatWorkbenchDuration(message.latencyMs))}</p>
                <p>检索耗时：${WorkbenchCommon.escapeHtml(formatWorkbenchDuration(message.retrievalLatencyMs))}</p>
                <p>生成耗时：${WorkbenchCommon.escapeHtml(formatWorkbenchDuration(message.generationLatencyMs))}</p>
                <p>检索缓存：${message.retrievalCacheHit ? "命中" : "未命中"}</p>
                <p>回答模式：${WorkbenchCommon.escapeHtml(formatAnswerMode(message.answerMode))}</p>
                <p>输入令牌：${WorkbenchCommon.escapeHtml(message.inputTokens ?? 0)}</p>
                <p>输出令牌：${WorkbenchCommon.escapeHtml(message.outputTokens ?? 0)}</p>
                <p>证据数：${WorkbenchCommon.escapeHtml(message.sourceCount ?? 0)}</p>
                <div class="footer-actions" style="margin-top:12px;">
                    <button id="feedbackLikeButton" class="soft-button" type="button">有帮助</button>
                    <button id="feedbackDislikeButton" class="ghost-button" type="button">需改进</button>
                </div>
            </article>
            <article class="detail-card">
                <strong>建议下一步</strong>
                <p>${WorkbenchCommon.escapeHtml(buildGuidanceText(message, linkedTicket))}</p>
                <div class="chips" style="margin-top:12px;">
                    <span class="status-pill">${message.needHumanHandoff ? "可转人工处理" : "建议继续会话追问"}</span>
                    <span class="status-pill">${WorkbenchCommon.formatNumber(message.sources?.length || 0)} 条证据块</span>
                </div>
            </article>
        </section>

        <section class="detail-card">
            <strong>检索证据</strong>
            <div class="assistant-evidence-list">${sourcesHtml}</div>
        </section>

        ${linkedTicketSection}
        ${handoffSection}
    `;

    document.getElementById("feedbackLikeButton")?.addEventListener("click", async () => {
        await submitFeedback("LIKE");
    });
    document.getElementById("feedbackDislikeButton")?.addEventListener("click", async () => {
        await submitFeedback("DISLIKE");
    });
    document.getElementById("handoffButton")?.addEventListener("click", async () => {
        await handoffSelectedMessage();
    });
    document.getElementById("openLinkedTicketButton")?.addEventListener("click", async () => {
        await selectTicket(linkedTicket.id, false);
    });
}

function buildGuidanceText(message, linkedTicket) {
    if (linkedTicket) {
        return "该问题已经转交人工处理，建议继续跟踪工单时间线，并在需要时补充更多上下文。";
    }
    if (message.needHumanHandoff) {
        return "系统未能从当前知识库验证出可靠答案，建议将该问题升级为支持工单，保留完整上下文继续处理。";
    }
    if (!message.sources?.length) {
        return "当前回答没有可见证据块，建议先复核知识库内容，再把它作为最终答复使用。";
    }
    return "当前回答已经关联到检索到的知识分块，建议先查看下方证据，再在同一会话中继续追问。";
}

function renderTicketList() {
    workbenchElements.ticketMeta.textContent = `你的问答流程已生成 ${WorkbenchCommon.formatNumber(workbenchState.tickets.length)} 个工单`;
    if (!workbenchState.tickets.length) {
        workbenchElements.ticketList.innerHTML = WorkbenchCommon.renderStateBlock({
            title: "当前还没有工单",
            message: "当回答需要人工跟进时，转交后的工单会自动出现在这里。",
            compact: true
        });
        return;
    }

    workbenchElements.ticketList.innerHTML = workbenchState.tickets.map((ticket) => {
        const isActive = ticket.id === workbenchState.selectedTicketId;
        return `
            <article class="assistant-ticket-card ${isActive ? "active" : ""}">
                <div class="assistant-card-head">
                    <div>
                        <strong>${WorkbenchCommon.escapeHtml(ticket.ticketNo || `工单 #${ticket.id}`)}</strong>
                        <div class="assistant-mini-meta">${WorkbenchCommon.escapeHtml(ticket.reporterName || "当前用户")}</div>
                    </div>
                    <div class="chips">
                        <span class="status-pill ${WorkbenchCommon.statusClass(ticket.status)}">${WorkbenchCommon.escapeHtml(formatWorkbenchTicketStatus(ticket.status))}</span>
                        <span class="status-pill">${WorkbenchCommon.escapeHtml(formatWorkbenchPriority(ticket.priority))}</span>
                    </div>
                </div>
                <p>${WorkbenchCommon.escapeHtml(ticket.title || "暂无工单标题")}</p>
                <div class="footer-actions" style="margin-top:12px;">
                    <button class="link-button" type="button" data-ticket-id="${ticket.id}">${isActive ? "正在查看时间线" : "打开时间线"}</button>
                </div>
            </article>
        `;
    }).join("");

    workbenchElements.ticketList.querySelectorAll("[data-ticket-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await selectTicket(Number(button.dataset.ticketId), false);
        });
    });
}

async function selectTicket(ticketId, silent) {
    if (!ticketId) {
        return;
    }
    workbenchState.selectedTicketId = ticketId;
    syncWorkbenchQuery();
    renderTicketList();

    if (!silent) {
        workbenchElements.ticketDetail.innerHTML = WorkbenchCommon.renderStateBlock({
            type: "loading",
            title: "正在加载工单时间线",
            message: "系统正在读取工单详情、回复和处理记录。",
            compact: true
        });
    }

    try {
        const [detail, comments] = await Promise.all([
            WorkbenchCommon.api(`/api/v1/app/tickets/${ticketId}`, { token: workbenchState.token }),
            WorkbenchCommon.api(`/api/v1/app/tickets/${ticketId}/comments`, { token: workbenchState.token })
        ]);
        workbenchState.selectedTicket = detail;
        workbenchState.selectedTicketComments = comments || [];
        renderTicketList();
        renderTicketDetail();
        if (!silent) {
            focusWorkbenchPanel("ticketPanel");
            setWorkbenchBanner("已打开关联工单区，可以查看时间线或补充回复。", "info");
        }
    } catch (error) {
        if (error instanceof WorkbenchCommon.AuthExpiredError) {
            handleWorkbenchAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setWorkbenchBanner(error.message || "工单时间线加载失败。", "error");
        workbenchElements.ticketDetail.innerHTML = WorkbenchCommon.renderStateBlock({
            type: "error",
            title: "工单时间线加载失败",
            message: error.message || "未知错误",
            compact: true
        });
    }
}

function resolveSelectedTicketId(keepSelections) {
    if (keepSelections && workbenchState.selectedTicketId && workbenchState.tickets.some((item) => item.id === workbenchState.selectedTicketId)) {
        return workbenchState.selectedTicketId;
    }
    const queryTicketId = workbenchState.query.ticketId ? Number(workbenchState.query.ticketId) : null;
    if (queryTicketId && workbenchState.tickets.some((item) => item.id === queryTicketId)) {
        return queryTicketId;
    }
    return workbenchState.tickets[0]?.id || null;
}

function renderTicketDetail() {
    const ticket = workbenchState.selectedTicket;
    if (!ticket) {
        workbenchElements.ticketDetail.innerHTML = WorkbenchCommon.renderStateBlock({
            title: "暂未选择工单",
            message: "选择一个支持工单后，可以查看可见时间线并继续补充上下文。",
            compact: true
        });
        return;
    }

    const commentsHtml = workbenchState.selectedTicketComments.length
        ? workbenchState.selectedTicketComments.map((comment) => `
            <article class="assistant-evidence-card">
                <div class="assistant-card-head">
                    <div>
                        <strong>${WorkbenchCommon.escapeHtml(formatWorkbenchTicketCommentType(comment.commentType))}</strong>
                        <div class="assistant-mini-meta">${WorkbenchCommon.escapeHtml(comment.commentUserName || "未知用户")} · ${WorkbenchCommon.escapeHtml(WorkbenchCommon.formatDateTime(comment.createdAt))}</div>
                    </div>
                    <span class="status-pill">${comment.visibleToUser ? "用户可见" : "内部备注"}</span>
                </div>
                <p>${WorkbenchCommon.escapeHtml(comment.content || "")}</p>
            </article>
        `).join("")
        : `<div class="assistant-note-card"><strong>当前没有可见回复</strong><p>支持团队还没有为这张工单添加用户可见更新。</p></div>`;

    workbenchElements.ticketDetail.innerHTML = `
        <section class="summary-card">
            <span class="panel-kicker">当前工单</span>
            <strong>${WorkbenchCommon.escapeHtml(ticket.ticketNo || `工单 #${ticket.id}`)} | ${WorkbenchCommon.escapeHtml(ticket.title || "未命名工单")}</strong>
            <div class="chips" style="margin-top:12px;">
                <span class="status-pill ${WorkbenchCommon.statusClass(ticket.status)}">${WorkbenchCommon.escapeHtml(formatWorkbenchTicketStatus(ticket.status))}</span>
                <span class="status-pill">${WorkbenchCommon.escapeHtml(formatWorkbenchPriority(ticket.priority))}</span>
                <span class="status-pill">${WorkbenchCommon.escapeHtml(ticket.assigneeName || "未分配")}</span>
            </div>
            <p>${WorkbenchCommon.escapeHtml(ticket.content || "暂无工单描述")}</p>
        </section>

        <section class="detail-columns">
            <article class="detail-card">
                <strong>工单上下文</strong>
                <p>原始问题：${WorkbenchCommon.escapeHtml(ticket.sourceQuestionText || "暂无")}</p>
                <p>AI 回答：${WorkbenchCommon.escapeHtml(ticket.sourceAnswerText || "暂无")}</p>
                <p>最近回复：${WorkbenchCommon.escapeHtml(WorkbenchCommon.formatDateTime(ticket.lastReplyAt || ticket.updatedAt))}</p>
                ${ticket.relatedDraftId ? `<p>关联草稿：${WorkbenchCommon.escapeHtml(ticket.relatedDraftTitle || `草稿 #${ticket.relatedDraftId}`)} (${WorkbenchCommon.escapeHtml(formatWorkbenchDraftStatus(ticket.relatedDraftStatus))})</p>` : ""}
            </article>
            <article class="detail-card">
                <strong>补充回复</strong>
                <label class="field">
                    <span>补充更多上下文</span>
                    <textarea id="ticketReplyInput" placeholder="可以补充更多背景信息、截图摘要或后续发现，帮助支持同学继续处理。"></textarea>
                </label>
                <div class="footer-actions" style="margin-top:12px;">
                    <button id="ticketReplyButton" class="primary-button" type="button">发送回复</button>
                    <a class="link-button" href="/admin/tickets?ticketId=${ticket.id}&source=assistant&messageId=${ticket.sourceQaMessageId || ""}">打开后台工单视图</a>
                </div>
            </article>
        </section>

        <section class="detail-card">
            <strong>可见时间线</strong>
            <div class="assistant-ticket-timeline">${commentsHtml}</div>
        </section>
    `;

    document.getElementById("ticketReplyButton")?.addEventListener("click", async () => {
        await replyToTicket();
    });
}

async function replyToTicket() {
    if (!workbenchState.selectedTicketId) {
        return;
    }
    const content = document.getElementById("ticketReplyInput")?.value.trim();
    if (!content) {
        setWorkbenchBanner("请输入要发送给支持团队的回复内容。", "error");
        return;
    }

    try {
        await WorkbenchCommon.api(`/api/v1/app/tickets/${workbenchState.selectedTicketId}/reply`, {
            token: workbenchState.token,
            method: "POST",
            body: JSON.stringify({ content })
        });
        await selectTicket(workbenchState.selectedTicketId, true);
        await loadWorkbenchData(true);
        setWorkbenchBanner("回复已发送给支持团队。", "success");
    } catch (error) {
        if (error instanceof WorkbenchCommon.AuthExpiredError) {
            handleWorkbenchAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setWorkbenchBanner(error.message || "发送工单回复失败。", "error");
    }
}

function getSelectedKnowledgeBase() {
    return workbenchState.knowledgeBases.find((item) => String(item.id) === String(workbenchState.selectedKnowledgeBaseId)) || null;
}

function getKnowledgeBaseById(knowledgeBaseId) {
    return workbenchState.knowledgeBases.find((item) => String(item.id) === String(knowledgeBaseId)) || null;
}

function getSelectedSession() {
    return workbenchState.sessions.find((item) => item.id === workbenchState.selectedSessionId) || null;
}

function getFilteredSessions() {
    if (!workbenchState.selectedKnowledgeBaseId) {
        return workbenchState.sessions;
    }
    return workbenchState.sessions.filter((item) => String(item.knowledgeBaseId) === String(workbenchState.selectedKnowledgeBaseId));
}

function getLinkedTicketForMessageId(messageId) {
    return workbenchState.tickets.find((item) => String(item.sourceQaMessageId || "") === String(messageId)) || null;
}

function clearMessageState() {
    workbenchState.messages = [];
    workbenchState.selectedSessionId = null;
    workbenchState.selectedMessageId = null;
    workbenchState.selectedMessage = null;
}

function clearTicketState() {
    workbenchState.selectedTicketId = null;
    workbenchState.selectedTicket = null;
    workbenchState.selectedTicketComments = [];
}

function buildPromptSuggestions(knowledgeBase) {
    const context = `${knowledgeBase.kbName || ""} ${knowledgeBase.description || ""} ${knowledgeBase.kbCode || ""}`.toLowerCase();
    if (/(vpn|network|it|support|service desk|runbook)/.test(context)) {
        return [
            { title: "常见接入问题", text: "为什么我连不上 VPN，第一步应该检查什么？" },
            { title: "新员工开通", text: "新员工应该如何完成电脑和账号的开通流程？" },
            { title: "排查流程", text: "请给我一份桌面客户端登录失败的逐步排查清单。" },
            { title: "转人工时机", text: "这类支持问题在什么情况下应该升级给人工处理？" }
        ];
    }
    if (/(finance|expense|invoice|reimbursement|payment)/.test(context)) {
        return [
            { title: "报销政策", text: "差旅和餐饮费用的报销流程是什么？" },
            { title: "发票规则", text: "员工在提交报销前需要遵循哪些发票要求？" },
            { title: "审批链路", text: "跨部门费用申请需要哪些角色参与审批？" },
            { title: "未覆盖场景", text: "如果报销场景与现有制度不匹配，我应该怎么处理？" }
        ];
    }
    if (/(hr|employee|leave|attendance|personnel)/.test(context)) {
        return [
            { title: "请假制度", text: "员工应该如何申请年假，由谁审批？" },
            { title: "考勤异常", text: "考勤异常应该如何发起更正流程？" },
            { title: "入职材料", text: "新员工入职申请通常需要准备哪些资料？" },
            { title: "特殊场景", text: "如果政策库里没有现成答案，HR 应该如何处理？" }
        ];
    }
    return [
        { title: "流程查询", text: `${knowledgeBase.kbName} 覆盖的标准处理流程是什么？` },
        { title: "步骤清单", text: `请给我一份在 ${knowledgeBase.kbName} 中处理常见请求的实操清单。` },
        { title: "适用条件", text: "在执行这个流程之前，用户应该先确认哪些前置条件？" },
        { title: "兜底路径", text: "如果知识库里没有可靠答案，后续应该怎样转交支持团队？" }
    ];
}

function buildAutoSessionTitle(question, knowledgeBase) {
    const trimmedQuestion = String(question || "").replace(/\s+/g, " ").trim();
    const shortQuestion = trimmedQuestion ? trimmedQuestion.slice(0, 28) : "通用咨询";
    return `${knowledgeBase?.kbName || "知识库"} - ${shortQuestion}`;
}

function KbLabel(kbCode) {
    return WorkbenchCommon.escapeHtml(kbCode || "自动编码");
}

function formatWorkbenchRoleList(roleCodes) {
    const labels = (roleCodes || [])
        .map((roleCode) => WorkbenchCommon.enumLabel("roleCode", roleCode, "普通成员"))
        .filter(Boolean);
    if (!labels.length) {
        return "普通成员";
    }
    return labels.join(" / ");
}

function formatWorkbenchDuration(value) {
    const numericValue = Number(value || 0);
    if (!Number.isFinite(numericValue) || numericValue <= 0) {
        return "0 毫秒";
    }
    if (numericValue < 1000) {
        return `${numericValue} 毫秒`;
    }
    return `${(numericValue / 1000).toFixed(2)} 秒`;
}

function formatAnswerMode(mode) {
    const labels = {
        ASYNC_GENERATING: "异步生成中",
        FAST_RETRIEVAL: "高置信快速回答",
        LLM_GENERATED: "大模型生成",
        NO_HIT_FALLBACK: "未命中降级",
        FAILED: "生成失败"
    };
    return labels[mode] || "未知模式";
}
function formatWorkbenchAnswerStatus(status) {
    return WorkbenchCommon.enumLabel("answerStatus", status, "未知状态");
}

function formatWorkbenchTicketStatus(status) {
    return WorkbenchCommon.enumLabel("ticketStatus", status, "未知状态");
}

function formatWorkbenchPriority(priority) {
    return WorkbenchCommon.enumLabel("priority", priority, "未知优先级");
}

function formatWorkbenchDraftStatus(status) {
    return WorkbenchCommon.enumLabel("draftStatus", status, "未知状态");
}

function formatWorkbenchTicketCommentType(commentType) {
    return WorkbenchCommon.enumLabel("ticketCommentType", commentType, "评论");
}

function formatWorkbenchRecallStrategy(strategy) {
    const normalized = String(strategy || "").trim().toUpperCase();
    if (!normalized || normalized === "UNKNOWN") {
        return "未知策略";
    }
    if (normalized === "LEXICAL") {
        return "词法召回";
    }
    if (normalized === "VECTOR") {
        return "向量召回";
    }
    if (normalized === "HYBRID") {
        return "混合召回";
    }
    return "未知策略";
}

function isClosedTicket(status) {
    const normalized = String(status || "").toUpperCase();
    return normalized === "RESOLVED" || normalized === "CLOSED";
}

function isResolvedTicket(status) {
    const normalized = String(status || "").toUpperCase();
    return normalized === "RESOLVED";
}

function syncWorkbenchQuery() {
    WorkbenchCommon.updateQuery({
        knowledgeBaseId: workbenchState.selectedKnowledgeBaseId,
        sessionId: workbenchState.selectedSessionId,
        messageId: workbenchState.selectedMessageId,
        ticketId: workbenchState.selectedTicketId
    });
}

function handleWorkbenchAuthFailure(error, message) {
    console.error(error);
    logoutWorkbench();
    setWorkbenchBanner(message, "error");
}

function setWorkbenchBanner(message, type = "info") {
    workbenchElements.statusBanner.hidden = false;
    workbenchElements.statusBanner.className = `status-banner ${type}`;
    workbenchElements.statusBanner.textContent = message;
}


