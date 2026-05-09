const DashboardCommon = window.KnowFlowConsoleCommon;
const SESSION_KEY = 'knowflow.console.token';

const DASHBOARD_MENU_LABELS = {
    DASHBOARD: "运营看板",
    ASSISTANT: "智能问答入口",
    WORKBENCH: "问答工作台",
    "QA RECORDS": "问答记录",
    TICKETS: "工单管理",
    "AUDIT LOGS": "审计日志",
    "KNOWLEDGE BASES": "知识库",
    DOCUMENTS: "文档管理",
    "PARSE TASKS": "解析任务",
    "DEAD LETTERS": "死信治理",
    "KNOWLEDGE DRAFTS": "知识草稿",
    USERS: "用户管理",
    ROLES: "角色管理"
};

const state = {
    token: window.localStorage.getItem(SESSION_KEY) || "",
    trendDays: 7,
    analysisDays: 30,
    user: null,
    menus: [],
    overview: null,
    trends: [],
    hotQuestions: [],
    noHitQuestions: [],
    detail: null,
    selectedQuestionKey: null,
    lastRefreshAt: null
};

const DASHBOARD_ENUM_LABELS = {
    answerStatus: {
        SUCCESS: "已命中",
        NO_HIT: "未命中",
        FAILED: "回答失败",
        MANUAL_REQUIRED: "需人工介入",
        PENDING_REVIEW: "待复核",
        UNKNOWN: "未知状态"
    },
    ticketStatus: {
        PENDING: "待处理",
        PROCESSING: "处理中",
        WAITING_USER: "待用户反馈",
        RESOLVED: "已解决",
        CLOSED: "已关闭",
        UNKNOWN: "未知状态"
    },
    priority: {
        LOW: "低",
        MEDIUM: "中",
        HIGH: "高",
        URGENT: "紧急",
        NORMAL: "普通",
        UNKNOWN: "未知优先级"
    },
    draftStatus: {
        PENDING_REVIEW: "待审核",
        APPROVED: "已通过",
        REJECTED: "已驳回",
        PUBLISHED: "已发布",
        UNKNOWN: "未知状态"
    },
    draftType: {
        FAQ: "常见问答",
        ARTICLE: "知识文章"
    }
};

const elements = {};

class AuthExpiredError extends Error {
    constructor(message) {
        super(message);
        this.name = "AuthExpiredError";
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    cacheElements();
    bindEvents();
    renderDayButtons();
    if (state.token) {
        await restoreSession();
        return;
    }
    showLoggedOutState();
});

function cacheElements() {
    [
        "statusBanner",
        "sessionName",
        "sessionMeta",
        "loginPanel",
        "dashboardApp",
        "loginForm",
        "usernameInput",
        "passwordInput",
        "loginButton",
        "refreshButton",
        "logoutButton",
        "permissionTags",
        "lastRefreshText",
        "metricGrid",
        "valueStory",
        "funnelRail",
        "trendLegend",
        "trendChart",
        "hotQuestionsTable",
        "noHitTable",
        "questionDetailBody"
    ].forEach((id) => {
        elements[id] = document.getElementById(id);
    });
}

function bindEvents() {
    elements.loginForm.addEventListener("submit", handleLogin);
    elements.refreshButton.addEventListener("click", () => refreshDashboard(false));
    elements.logoutButton.addEventListener("click", logout);

    document.querySelectorAll("[data-trend-days]").forEach((button) => {
        button.addEventListener("click", async () => {
            state.trendDays = Number(button.dataset.trendDays);
            renderDayButtons();
            await refreshDashboard(false);
        });
    });

    document.querySelectorAll("[data-analysis-days]").forEach((button) => {
        button.addEventListener("click", async () => {
            state.analysisDays = Number(button.dataset.analysisDays);
            renderDayButtons();
            await refreshDashboard(true);
        });
    });
}

async function restoreSession() {
    try {
        const [user, menus] = await Promise.all([
            api("/api/v1/auth/me"),
            api("/api/v1/auth/menus")
        ]);
        state.user = user;
        state.menus = menus || [];
        showLoggedInState();
        await refreshDashboard(true);
    } catch (error) {
        handleAuthFailure(error, "会话已失效，请重新登录。");
    }
}

async function handleLogin(event) {
    event.preventDefault();
    const username = elements.usernameInput.value.trim();
    const password = elements.passwordInput.value;
    if (!username || !password) {
        setBanner("请输入账号和密码。", "error");
        return;
    }

    setBanner("正在登录并加载看板...", "info");
    elements.loginButton.disabled = true;
    try {
        const loginData = await api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password })
        }, false);
        state.token = loginData.token;
        window.localStorage.setItem(SESSION_KEY, state.token);
        elements.passwordInput.value = "";
        await restoreSession();
        setBanner("登录成功，已进入运营看板。", "success");
    } catch (error) {
        setBanner(error.message || "登录失败，请检查账号密码。", "error");
    } finally {
        elements.loginButton.disabled = false;
    }
}

function logout() {
    state.token = "";
    state.user = null;
    state.menus = [];
    state.overview = null;
    state.trends = [];
    state.hotQuestions = [];
    state.noHitQuestions = [];
    state.detail = null;
    state.selectedQuestionKey = null;
    state.lastRefreshAt = null;
    window.localStorage.removeItem(SESSION_KEY);
    showLoggedOutState();
    setBanner("已退出登录。", "success");
}

async function refreshDashboard(preferKeepSelection) {
    if (!state.token) {
        showLoggedOutState();
        return;
    }

    renderLoadingShell();
    try {
        const [overview, trends, hotQuestions, noHitQuestions] = await Promise.all([
            api("/api/v1/admin/dashboard/overview"),
            api(`/api/v1/admin/dashboard/trends?days=${state.trendDays}`),
            api(`/api/v1/admin/dashboard/hot-questions?days=${state.analysisDays}&limit=8`),
            api(`/api/v1/admin/dashboard/no-hit-questions?days=${state.analysisDays}&limit=8`)
        ]);

        state.overview = overview;
        state.trends = trends || [];
        state.hotQuestions = hotQuestions || [];
        state.noHitQuestions = noHitQuestions || [];
        state.lastRefreshAt = new Date();

        renderDashboard();

        const selected = resolveSelection(preferKeepSelection);
        if (selected) {
            await loadQuestionDetail(selected.questionText, selected.knowledgeBaseId, true);
        } else {
            renderQuestionDetail(null);
        }

        setBanner("看板数据已刷新。", "success", 2200);
    } catch (error) {
        if (error instanceof AuthExpiredError) {
            handleAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        setBanner(error.message || "刷新看板失败。", "error");
        renderQuestionDetail(null, "当前无法加载问题明细，请稍后重试。");
    }
}

function resolveSelection(preferKeepSelection) {
    if (preferKeepSelection && state.detail) {
        return {
            questionText: state.detail.questionText,
            knowledgeBaseId: state.detail.knowledgeBaseId
        };
    }
    const firstNoHit = state.noHitQuestions[0];
    if (firstNoHit) {
        return {
            questionText: firstNoHit.questionText,
            knowledgeBaseId: firstNoHit.knowledgeBaseId
        };
    }
    const firstHot = state.hotQuestions[0];
    if (firstHot) {
        return {
            questionText: firstHot.questionText,
            knowledgeBaseId: firstHot.knowledgeBaseId
        };
    }
    return null;
}

async function loadQuestionDetail(questionText, knowledgeBaseId, silent) {
    const questionKey = buildQuestionKey(knowledgeBaseId, questionText);
    state.selectedQuestionKey = questionKey;
    renderQuestionTables();
    if (!silent) {
        renderQuestionDetail("loading");
    }

    try {
        const query = new URLSearchParams({
            days: String(state.analysisDays),
            questionText
        });
        if (knowledgeBaseId !== null && knowledgeBaseId !== undefined) {
            query.set("knowledgeBaseId", String(knowledgeBaseId));
        }
        state.detail = await api(`/api/v1/admin/dashboard/question-detail?${query.toString()}`);
        state.selectedQuestionKey = buildQuestionKey(state.detail.knowledgeBaseId, state.detail.questionText);
        renderQuestionTables();
        renderQuestionDetail(state.detail);
    } catch (error) {
        if (error instanceof AuthExpiredError) {
            handleAuthFailure(error, "登录状态已失效，请重新登录。");
            return;
        }
        renderQuestionDetail(null, error.message || "问题明细加载失败。");
        setBanner(error.message || "问题明细加载失败。", "error");
    }
}

function showLoggedOutState() {
    elements.loginPanel.hidden = false;
    elements.dashboardApp.hidden = true;
    elements.sessionName.textContent = "未登录";
    elements.sessionMeta.textContent = "请先登录以加载租户数据";
    clearBanner();
}

function showLoggedInState() {
    elements.loginPanel.hidden = true;
    elements.dashboardApp.hidden = false;
    elements.sessionName.textContent = state.user.realName || state.user.username;
    elements.sessionMeta.textContent = DashboardCommon.formatUserSessionMeta(state.user);
}

function handleAuthFailure(error, message) {
    console.error(error);
    logout();
    setBanner(message, "error");
}

function renderLoadingShell() {
    elements.metricGrid.innerHTML = "";
    elements.valueStory.innerHTML = DashboardCommon.renderStateBlock({
        type: "loading",
        title: "正在汇总业务价值数据",
        message: "请稍候，系统正在拉取问答、工单和知识回流统计。",
        compact: true
    });
    elements.funnelRail.innerHTML = "";
    elements.trendLegend.innerHTML = "";
    elements.trendChart.innerHTML = DashboardCommon.renderStateBlock({
        type: "loading",
        title: "趋势图加载中",
        message: "正在生成近几天的问答与闭环趋势。",
        compact: true
    });
    elements.hotQuestionsTable.innerHTML = DashboardCommon.renderStateBlock({
        type: "loading",
        title: "正在分析热点问题",
        message: "即将展示高频提问主题与人工接入情况。",
        compact: true
    });
    elements.noHitTable.innerHTML = DashboardCommon.renderStateBlock({
        type: "loading",
        title: "正在汇总未命中问题",
        message: "系统正在整理未命中主题、工单转化和知识补洞优先级。",
        compact: true
    });
    elements.lastRefreshText.textContent = "加载中";
}

function renderDashboard() {
    renderDayButtons();
    renderPermissionTags();
    renderMetrics();
    renderValueStory();
    renderFunnel();
    renderTrendChart();
    renderQuestionTables();
    elements.lastRefreshText.textContent = formatDateTime(state.lastRefreshAt);
}

function renderDayButtons() {
    document.querySelectorAll("[data-trend-days]").forEach((button) => {
        button.classList.toggle("active", Number(button.dataset.trendDays) === state.trendDays);
    });
    document.querySelectorAll("[data-analysis-days]").forEach((button) => {
        button.classList.toggle("active", Number(button.dataset.analysisDays) === state.analysisDays);
    });
}

function renderPermissionTags() {
    const tags = [];
    if (state.user?.roleCodes?.length) {
        state.user.roleCodes.forEach((role) => tags.push(`<span class="tag">${escapeHtml(DashboardCommon.enumLabel("roleCode", role, role))}</span>`));
    }
    if (state.menus?.length) {
        state.menus.forEach((menu) => tags.push(`<span class="tag">${escapeHtml(formatDashboardMenuLabel(menu))}</span>`));
    }
    elements.permissionTags.innerHTML = tags.join("") || `<span class="tag">暂无角色信息</span>`;
}

function formatDashboardMenuLabel(menu) {
    const raw = String(menu?.name || menu?.code || menu?.key || "").trim();
    if (!raw) {
        return "未命名菜单";
    }
    const normalized = raw.replace(/[_-]+/g, " ").replace(/\s+/g, " ").trim().toUpperCase();
    return DASHBOARD_MENU_LABELS[normalized] || raw;
}

function renderMetrics() {
    const overview = state.overview || {};
    const cards = [
        {
            label: "知识命中率",
            value: `${formatPercent(overview.qaHitRate)}%`,
            footnote: `${formatNumber(overview.qaSuccessCount)} / ${formatNumber(overview.qaCount)} 次问答成功命中`,
            targetId: "hotQuestionsPanel"
        },
        {
            label: "人工接入压力",
            value: `${formatPercent(overview.handoffRate)}%`,
            footnote: `${formatNumber(overview.handoffCount)} 条问答触发人工接入`,
            targetId: "noHitPanel"
        },
        {
            label: "待处理工单",
            value: formatNumber(overview.openTicketCount),
            footnote: `${formatNumber(overview.ticketCount)} 张工单中仍待继续处理`,
            targetId: "detailPanel"
        },
        {
            label: "工单闭环率",
            value: `${formatPercent(overview.ticketResolveRate)}%`,
            footnote: `${formatNumber(overview.resolvedTicketCount)} 张工单已解决或关闭`,
            targetId: "detailPanel"
        },
        {
            label: "待审核草稿",
            value: formatNumber(overview.draftPendingReviewCount),
            footnote: "这些是已经沉淀但尚未正式发布的知识回流资产",
            targetId: "detailPanel"
        },
        {
            label: "已发布知识",
            value: formatNumber(overview.draftPublishedCount),
            footnote: "这部分代表问题解决经验已真正回流到知识库",
            targetId: "detailPanel"
        }
    ];

    elements.metricGrid.innerHTML = cards.map((card) => `
        <button class="metric-card" type="button" data-target-id="${card.targetId}">
            <div class="metric-label">${escapeHtml(card.label)}</div>
            <div class="metric-value">${escapeHtml(card.value)}</div>
            <div class="metric-footnote">${escapeHtml(card.footnote)}</div>
        </button>
    `).join("");

    elements.metricGrid.querySelectorAll("[data-target-id]").forEach((button) => {
        button.addEventListener("click", () => {
            document.getElementById(button.dataset.targetId)?.scrollIntoView({ behavior: "smooth", block: "start" });
        });
    });
}

function renderValueStory() {
    const overview = state.overview || {};
    const coverageBase = Number(overview.qaNoHitCount) || 0;
    const backflowCoverage = coverageBase > 0
        ? ((Number(overview.draftPublishedCount) || 0) / coverageBase) * 100
        : 0;

    const insights = [
        {
            title: (Number(overview.qaHitRate) || 0) >= 70 ? "知识库已经承担起一线解答作用" : "知识命中率仍有明显提升空间",
            body: (Number(overview.qaHitRate) || 0) >= 70
                ? `当前命中率为 ${formatPercent(overview.qaHitRate)}%，说明平台已具备替代部分人工重复答复的能力。`
                : `当前命中率为 ${formatPercent(overview.qaHitRate)}%，说明仍有大量问题在知识覆盖、召回或答案编排上存在补强空间。`
        },
        {
            title: (Number(overview.handoffRate) || 0) > 30 ? "人工承压明显，值得优先治理高频未命中主题" : "人工接入压力相对可控",
            body: `共有 ${formatNumber(overview.handoffCount)} 次问答触发人工接入，接入率 ${formatPercent(overview.handoffRate)}%。这个指标越高，越说明知识供给需要提速。`
        },
        {
            title: backflowCoverage > 20 ? "知识回流机制开始发挥作用" : "知识回流仍是下一阶段重点",
            body: `已发布知识 ${formatNumber(overview.draftPublishedCount)} 条，按未命中问题规模估算，回流覆盖率约为 ${formatPercent(backflowCoverage)}%。这正是平台区别于普通常见问答系统的地方。`
        }
    ];

    elements.valueStory.innerHTML = insights.map((item) => `
        <div class="story-card">
            <strong>${escapeHtml(item.title)}</strong>
            <p>${escapeHtml(item.body)}</p>
        </div>
    `).join("");
}

function renderFunnel() {
    const overview = state.overview || {};
    const stages = [
        { label: "用户提问", value: Number(overview.qaCount) || 0, color: "var(--navy)" },
        { label: "成功命中", value: Number(overview.qaSuccessCount) || 0, color: "var(--teal)" },
        { label: "人工接入", value: Number(overview.handoffCount) || 0, color: "var(--clay)" },
        { label: "进入工单", value: Number(overview.ticketCount) || 0, color: "var(--sand)" },
        { label: "发布知识", value: Number(overview.draftPublishedCount) || 0, color: "var(--olive)" }
    ];
    const maxValue = Math.max(...stages.map((stage) => stage.value), 1);
    elements.funnelRail.innerHTML = stages.map((stage) => `
        <div class="funnel-stage">
            <div class="funnel-meta">
                <span>${escapeHtml(stage.label)}</span>
                <strong>${formatNumber(stage.value)}</strong>
            </div>
            <div class="funnel-bar">
                <div class="funnel-fill" style="width:${Math.max((stage.value / maxValue) * 100, stage.value > 0 ? 10 : 0)}%; background:${stage.color};"></div>
            </div>
        </div>
    `).join("");
}

function renderTrendChart() {
    const points = state.trends || [];
    const series = [
        { key: "qaCount", label: "问答量", color: "#304e69" },
        { key: "noHitCount", label: "未命中", color: "#aa4b3a" },
        { key: "ticketCreatedCount", label: "新增工单", color: "#b67637" },
        { key: "draftPublishedCount", label: "发布知识", color: "#6f7a44" }
    ];

    elements.trendLegend.innerHTML = series.map((item) => `
        <span class="legend-chip"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${item.color};margin-right:8px;"></span>${escapeHtml(item.label)}</span>
    `).join("");

    if (!points.length) {
        elements.trendChart.innerHTML = DashboardCommon.renderStateBlock({
            title: "暂无趋势数据",
            message: "登录后若当前租户已有问答和工单记录，这里会自动生成趋势图。",
            compact: true
        });
        return;
    }

    const width = 760;
    const height = 300;
    const padding = 44;
    const chartWidth = width - padding * 2;
    const chartHeight = height - padding * 2;
    const maxValue = Math.max(
        1,
        ...series.flatMap((item) => points.map((point) => Number(point[item.key]) || 0))
    );
    const xFor = (index) => points.length === 1
        ? width / 2
        : padding + (chartWidth / (points.length - 1)) * index;
    const yFor = (value) => height - padding - ((Number(value) || 0) / maxValue) * chartHeight;

    const gridLines = [0, 0.25, 0.5, 0.75, 1].map((ratio) => {
        const y = height - padding - chartHeight * ratio;
        const label = Math.round(maxValue * ratio);
        return `
            <line class="grid-line" x1="${padding}" y1="${y}" x2="${width - padding}" y2="${y}"></line>
            <text class="axis-label" x="${padding - 12}" y="${y + 4}" text-anchor="end">${label}</text>
        `;
    }).join("");

    const lines = series.map((item) => {
        const polyline = points.map((point, index) => `${xFor(index)},${yFor(point[item.key])}`).join(" ");
        const dots = points.map((point, index) => `
            <circle class="trend-dot" cx="${xFor(index)}" cy="${yFor(point[item.key])}" r="4.5" fill="${item.color}"></circle>
        `).join("");
        return `
            <polyline class="trend-line" stroke="${item.color}" points="${polyline}"></polyline>
            ${dots}
        `;
    }).join("");

    const labels = points.map((point, index) => `
        <text class="point-label" x="${xFor(index)}" y="${height - 14}" text-anchor="middle">${escapeHtml(formatShortDate(point.statDate))}</text>
    `).join("");

    elements.trendChart.innerHTML = `
        <svg class="trend-svg" viewBox="0 0 ${width} ${height}" role="img" aria-label="运营趋势图">
            ${gridLines}
            ${lines}
            ${labels}
        </svg>
    `;
}

function renderQuestionTables() {
    renderHotQuestionsTable();
    renderNoHitTable();
}

function renderHotQuestionsTable() {
    const rows = state.hotQuestions || [];
    elements.hotQuestionsTable.innerHTML = renderTable({
        headers: ["问题主题", "热度", "命中结构", "人工接入", "操作"],
        rows: rows.map((item) => {
            const key = buildQuestionKey(item.knowledgeBaseId, item.questionText);
            return {
                active: key === state.selectedQuestionKey,
                cells: [
                    `
                    <div class="question-cell">
                        <strong>${escapeHtml(item.questionText)}</strong>
                        <div class="question-meta">${escapeHtml(item.knowledgeBaseName || "未绑定知识库")} · 最近一次 ${escapeHtml(formatDateTime(item.latestAskedAt))}</div>
                    </div>
                    `,
                    `<strong>${formatNumber(item.askCount)}</strong>`,
                    `<div class="detail-muted">成功 ${formatNumber(item.successCount)} / 未命中 ${formatNumber(item.noHitCount)}</div>`,
                    `<span class="state-pill">${formatNumber(item.handoffCount)} 次</span>`,
                    `<button class="action-button" type="button" data-question="${escapeAttr(item.questionText)}" data-kb-id="${item.knowledgeBaseId ?? ""}">查看链路</button>`
                ]
            };
        })
    });
    bindQuestionButtons(elements.hotQuestionsTable);
}

function renderNoHitTable() {
    const rows = state.noHitQuestions || [];
    elements.noHitTable.innerHTML = renderTable({
        headers: ["未命中问题", "热度", "工单/草稿", "治理建议", "操作"],
        rows: rows.map((item) => {
            const key = buildQuestionKey(item.knowledgeBaseId, item.questionText);
            return {
                active: key === state.selectedQuestionKey,
                cells: [
                    `
                    <div class="question-cell">
                        <strong>${escapeHtml(item.questionText)}</strong>
                        <div class="question-meta">${escapeHtml(item.knowledgeBaseName || "未绑定知识库")} · 最近一次 ${escapeHtml(formatDateTime(item.latestAskedAt))}</div>
                    </div>
                    `,
                    `<strong>${formatNumber(item.askCount)}</strong><div class="detail-muted">人工接入 ${formatNumber(item.handoffCount)} 次</div>`,
                    `<div class="detail-muted">工单 ${formatNumber(item.relatedTicketCount)} / 已解决 ${formatNumber(item.resolvedTicketCount)} / 草稿 ${formatNumber(item.relatedDraftCount)} / 已发布 ${formatNumber(item.publishedDraftCount)}</div>`,
                    `<div class="card-text">${escapeHtml(item.suggestedAction || "暂无建议")}</div>`,
                    `<button class="action-button" type="button" data-question="${escapeAttr(item.questionText)}" data-kb-id="${item.knowledgeBaseId ?? ""}">钻取详情</button>`
                ]
            };
        })
    });
    bindQuestionButtons(elements.noHitTable);
}

function bindQuestionButtons(container) {
    container.querySelectorAll("[data-question]").forEach((button) => {
        button.addEventListener("click", async () => {
            const knowledgeBaseId = button.dataset.kbId ? Number(button.dataset.kbId) : null;
            await loadQuestionDetail(button.dataset.question, knowledgeBaseId, false);
        });
    });
}

function renderTable({ headers, rows }) {
    if (!rows.length) {
        return DashboardCommon.renderStateBlock({
            title: "暂无数据",
            message: "这个分析窗口内还没有足够的数据可以展示。",
            compact: true
        });
    }

    const headerHtml = headers.map((header) => `<th>${escapeHtml(header)}</th>`).join("");
    const rowHtml = rows.map((row) => `
        <tr class="${row.active ? "active-row" : ""}">
            ${row.cells.map((cell) => `<td>${cell}</td>`).join("")}
        </tr>
    `).join("");
    return `
        <table class="data-table">
            <thead>
            <tr>${headerHtml}</tr>
            </thead>
            <tbody>${rowHtml}</tbody>
        </table>
    `;
}

function renderQuestionDetail(detail, errorMessage) {
    if (detail === "loading") {
        elements.questionDetailBody.innerHTML = DashboardCommon.renderStateBlock({
            type: "loading",
            title: "正在加载问题链路",
            message: "系统正在拉取问答记录、关联工单和知识回流信息。",
            compact: true
        });
        return;
    }

    if (!detail) {
        elements.questionDetailBody.innerHTML = DashboardCommon.renderStateBlock({
            type: errorMessage ? "error" : "empty",
            title: errorMessage ? "问题明细加载失败" : "选择一个问题开始钻取",
            message: errorMessage || "点击左侧热点问题或未命中问题行，即可查看该主题下的完整处理链路。",
            compact: true
        });
        return;
    }

    const chips = [
        `提问 ${formatNumber(detail.askCount)} 次`,
        `命中 ${formatNumber(detail.successCount)} 次`,
        `未命中 ${formatNumber(detail.noHitCount)} 次`,
        `工单 ${formatNumber(detail.relatedTicketCount)} 张`,
        `草稿 ${formatNumber(detail.relatedDraftCount)} 条`,
        `已发布 ${formatNumber(detail.publishedDraftCount)} 条`
    ];

    elements.questionDetailBody.innerHTML = `
        <div class="detail-hero">
            <div class="panel-kicker">当前关注问题</div>
            <h4>${escapeHtml(detail.questionText)}</h4>
            <div class="detail-chip-row">
                <span class="detail-chip">${escapeHtml(detail.knowledgeBaseName || "未绑定知识库")}</span>
                <span class="detail-chip">最近一次 ${escapeHtml(formatDateTime(detail.latestAskedAt))}</span>
                ${chips.map((chip) => `<span class="detail-chip">${escapeHtml(chip)}</span>`).join("")}
            </div>
            <p>${escapeHtml(detail.suggestedAction || "暂无治理建议。")}</p>
        </div>

        <div class="detail-grid">
            <section class="detail-section">
                <strong>问答记录</strong>
                <div class="detail-muted">观察同一主题在不同会话下的命中情况和人工接入走势。</div>
                <div class="detail-stack">
                    ${renderMessageCards(detail.messages)}
                </div>
            </section>

            <section class="detail-section">
                <strong>关联工单</strong>
                <div class="detail-muted">查看该主题是否已经形成客服处理链路，以及工单当前所处状态。</div>
                <div class="detail-stack">
                    ${renderTicketCards(detail.tickets)}
                </div>
            </section>

            <section class="detail-section">
                <strong>知识回流</strong>
                <div class="detail-muted">检查工单经验是否已经沉淀为草稿，或者已经完成正式发布。</div>
                <div class="detail-stack">
                    ${renderDraftCards(detail.drafts)}
                </div>
            </section>
        </div>
    `;
}

function renderMessageCards(messages) {
    if (!messages || !messages.length) {
        return `<div class="link-card"><div class="detail-muted">当前没有可展示的问答记录。</div></div>`;
    }
    return messages.map((item) => `
        <article class="message-card">
            <div class="message-title">
                <strong>${escapeHtml(item.sessionTitle || `会话 #${item.sessionId}`)}</strong>
                <div class="message-meta">${escapeHtml(formatDateTime(item.createdAt))}</div>
            </div>
            <div class="detail-chip-row">
                <span class="message-chip ${statusClass(item.answerStatus)}">${escapeHtml(enumLabel("answerStatus", item.answerStatus, "未知状态"))}</span>
                <span class="message-chip">${item.needHumanHandoff ? "需要人工接入" : "AI 独立完成"}</span>
                <span class="message-chip">来源 ${formatNumber(item.sourceCount)} 条</span>
            </div>
            <div class="card-text">${escapeHtml(item.questionText || "")}</div>
            <div class="message-answer">${escapeHtml(truncateText(item.answerText, 180))}</div>
            <div class="card-link-row">
                <a class="action-button" href="/admin/qa-records?messageId=${item.id}&source=dashboard">查看问答记录</a>
            </div>
        </article>
    `).join("");
}

function renderTicketCards(tickets) {
    if (!tickets || !tickets.length) {
        return `<div class="link-card"><div class="detail-muted">该主题尚未转成工单，说明还处在知识补洞早期阶段。</div></div>`;
    }
    return tickets.map((item) => `
        <article class="link-card">
            <strong>${escapeHtml(item.ticketNo || `工单 #${item.id}`)}</strong>
            <div class="card-meta">${escapeHtml(item.title || "无标题工单")}</div>
            <div class="detail-chip-row">
                <span class="message-chip ${statusClass(item.status)}">${escapeHtml(enumLabel("ticketStatus", item.status, "未知状态"))}</span>
                <span class="message-chip">${escapeHtml(enumLabel("priority", item.priority, "未知优先级"))}</span>
                <span class="message-chip">${escapeHtml(item.assigneeName || "未分配")}</span>
            </div>
            <div class="card-meta">创建于 ${escapeHtml(formatDateTime(item.createdAt))}</div>
            <div class="card-meta">${item.resolvedAt ? `解决于 ${escapeHtml(formatDateTime(item.resolvedAt))}` : "尚未解决"}</div>
            <div class="card-link-row">
                <a class="action-button" href="/admin/tickets?ticketId=${item.id}&source=dashboard">进入工单页</a>
                ${item.sourceQaMessageId ? `<a class="action-button secondary-action" href="/admin/qa-records?messageId=${item.sourceQaMessageId}&source=dashboard&ticketId=${item.id}">查看来源问答</a>` : ""}
            </div>
        </article>
    `).join("");
}

function renderDraftCards(drafts) {
    if (!drafts || !drafts.length) {
        return `<div class="link-card"><div class="detail-muted">该主题还没有形成知识草稿，意味着经验沉淀尚未开始。</div></div>`;
    }
    return drafts.map((item) => `
        <article class="link-card">
            <strong>${escapeHtml(item.title || `草稿 #${item.id}`)}</strong>
            <div class="detail-chip-row">
                <span class="message-chip">${escapeHtml(enumLabel("draftType", item.draftType, item.draftType || "未设置类型"))}</span>
                <span class="message-chip ${statusClass(item.status)}">${escapeHtml(enumLabel("draftStatus", item.status, "未知状态"))}</span>
                <span class="message-chip">${escapeHtml(item.reviewerName || "待审核")}</span>
            </div>
            <div class="card-meta">创建于 ${escapeHtml(formatDateTime(item.createdAt))}</div>
            <div class="card-meta">${item.publishedAt ? `发布于 ${escapeHtml(formatDateTime(item.publishedAt))}` : "尚未发布"}</div>
            <div class="card-link-row">
                <a class="action-button" href="/admin/knowledge-drafts?draftId=${item.id}&source=dashboard&ticketId=${item.sourceTicketId || ""}">进入草稿页</a>
            </div>
        </article>
    `).join("");
}

async function api(url, options = {}, attachAuth = true) {
    const headers = {
        "Content-Type": "application/json",
        ...(options.headers || {})
    };
    if (attachAuth && state.token) {
        headers.Authorization = `Bearer ${state.token}`;
    }

    const response = await fetch(url, {
        method: options.method || "GET",
        headers,
        body: options.body
    });

    let payload = null;
    try {
        payload = await response.json();
    } catch (error) {
        payload = null;
    }

    if (response.status === 401 || payload?.code === 40101) {
        throw new AuthExpiredError(payload?.message || "Authentication expired");
    }
    if (!response.ok || !payload || payload.code !== 0) {
        throw new Error(payload?.message || `请求失败，状态码 ${response.status}`);
    }
    return payload.data;
}

function setBanner(message, type = "info", autoHideMs = 0) {
    elements.statusBanner.hidden = false;
    elements.statusBanner.className = `status-banner ${type}`;
    elements.statusBanner.textContent = message;
    if (autoHideMs > 0) {
        window.clearTimeout(setBanner.timer);
        setBanner.timer = window.setTimeout(clearBanner, autoHideMs);
    }
}

function clearBanner() {
    elements.statusBanner.hidden = true;
    elements.statusBanner.className = "status-banner";
    elements.statusBanner.textContent = "";
}

function buildQuestionKey(knowledgeBaseId, questionText) {
    return `${knowledgeBaseId ?? "null"}::${String(questionText || "").trim()}`;
}

function formatNumber(value) {
    return Number(value || 0).toLocaleString("zh-CN");
}

function formatPercent(value) {
    return Number(value || 0).toFixed(2).replace(/\.00$/, "");
}

function formatDateTime(value) {
    if (!value) {
        return "暂无时间";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return String(value);
    }
    return date.toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    });
}

function formatShortDate(value) {
    if (!value) {
        return "";
    }
    const parts = String(value).split("-");
    return parts.length === 3 ? `${Number(parts[1])}/${Number(parts[2])}` : String(value);
}

function truncateText(value, maxLength) {
    const text = String(value || "").trim();
    if (text.length <= maxLength) {
        return text || "暂无答案内容";
    }
    return `${text.slice(0, maxLength)}...`;
}

function enumLabel(category, value, fallback = "") {
    const raw = String(value ?? "").trim();
    if (!raw) {
        return fallback;
    }
    const labels = DASHBOARD_ENUM_LABELS[category];
    if (!labels) {
        return fallback || raw;
    }
    return labels[raw] || labels[raw.toUpperCase()] || fallback || raw;
}

function statusClass(status) {
    const normalized = String(status || "").toUpperCase();
    if (normalized === "SUCCESS") {
        return "status-success";
    }
    if (normalized === "NO_HIT" || normalized === "PENDING_REVIEW") {
        return "status-no-hit";
    }
    if (normalized === "PENDING" || normalized === "PROCESSING" || normalized === "WAITING_USER" || normalized === "APPROVED") {
        return "status-processing";
    }
    if (normalized === "RESOLVED" || normalized === "CLOSED" || normalized === "PUBLISHED") {
        return "status-resolved";
    }
    return "";
}

function escapeHtml(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function escapeAttr(value) {
    return escapeHtml(value).replace(/"/g, "&quot;");
}


