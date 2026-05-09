const OpsCommon = window.KnowFlowConsoleCommon;

const opsState = {
    token: OpsCommon.readToken(),
    query: OpsCommon.parseQuery(),
    user: null,
    days: 7,
    overview: null,
    infrastructure: null,
    aiUsage: null,
    lastRefreshAt: null
};

const opsElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    cacheOpsElements();
    initOpsStateFromQuery();
    bindOpsEvents();
    if (opsState.token) {
        await restoreOpsSession();
        return;
    }
    showOpsLoggedOutState();
});

function cacheOpsElements() {
    [
        "statusBanner",
        "loginPanel",
        "workspace",
        "loginForm",
        "usernameInput",
        "passwordInput",
        "loginButton",
        "opsRefreshButton",
        "sessionName",
        "sessionMeta",
        "refreshButton",
        "logoutButton",
        "lastRefreshText",
        "healthSummary",
        "healthActions",
        "metricCards",
        "aiUsageSummary",
        "aiUsageCards",
        "aiModelTableBody",
        "statusDistribution",
        "dlqMetrics",
        "governanceMetrics",
        "taskTypeTableBody",
        "infraSummary",
        "infraHealthGrid",
        "opsActionChecklist",
        "qaRecordsLink",
        "parseTasksLink",
        "deadLettersLink",
        "taskChainLink",
        "failedTasksLink"
    ].forEach((id) => {
        opsElements[id] = document.getElementById(id);
    });
    opsElements.rangeButtons = Array.from(document.querySelectorAll(".range-button"));
}

function initOpsStateFromQuery() {
    const days = Number(opsState.query.days || 7);
    opsState.days = [7, 14, 30, 90].includes(days) ? days : 7;
    renderRangeButtons();
    updateOpsDeepLinks();
}

function bindOpsEvents() {
    opsElements.loginForm.addEventListener("submit", handleOpsLogin);
    opsElements.opsRefreshButton.addEventListener("click", () => loadOpsOverview(false));
    opsElements.refreshButton?.addEventListener("click", () => loadOpsOverview(false));
    opsElements.logoutButton?.addEventListener("click", () => {
        OpsCommon.clearToken();
        opsState.token = "";
        opsState.user = null;
        showOpsLoggedOutState();
    });
    opsElements.rangeButtons.forEach((button) => {
        button.addEventListener("click", async () => {
            const days = Number(button.dataset.days || 7);
            if (days === opsState.days) {
                return;
            }
            opsState.days = days;
            renderRangeButtons();
            syncOpsQuery();
            updateOpsDeepLinks();
            await loadOpsOverview(false);
        });
    });
}

async function restoreOpsSession() {
    try {
        opsState.user = await OpsCommon.api("/api/v1/auth/me", { token: opsState.token });
        showOpsLoggedInState();
        await loadOpsOverview(true);
    } catch (error) {
        handleOpsAuthFailure(error, "登录已过期，请重新登录。");
    }
}

async function handleOpsLogin(event) {
    event.preventDefault();
    const username = opsElements.usernameInput.value.trim();
    const password = opsElements.passwordInput.value;
    if (!username || !password) {
        setOpsBanner("请输入账号和密码。", "error");
        return;
    }

    opsElements.loginButton.disabled = true;
    setOpsBanner("正在登录运维健康页...", "info");
    try {
        const data = await OpsCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        opsState.token = data.token;
        OpsCommon.saveToken(data.token);
        opsElements.passwordInput.value = "";
        await restoreOpsSession();
        setOpsBanner("运维健康指标已加载。", "success", 1600);
    } catch (error) {
        setOpsBanner(error.message || "登录失败。", "error");
    } finally {
        opsElements.loginButton.disabled = false;
    }
}

async function loadOpsOverview(quiet) {
    if (!quiet) {
        setOpsBanner("正在刷新运维指标...", "info");
    }
    opsElements.opsRefreshButton.disabled = true;
    try {
        const [overviewResult, infrastructureResult, aiUsageResult] = await Promise.allSettled([
            OpsCommon.api(`/api/v1/admin/ops/tasks/overview?days=${opsState.days}`, { token: opsState.token }),
            OpsCommon.api("/api/v1/admin/ops/infrastructure/health", { token: opsState.token }),
            OpsCommon.api(`/api/v1/admin/ops/ai/usage?days=${opsState.days}`, { token: opsState.token })
        ]);
        if (overviewResult.status === "rejected") {
            throw overviewResult.reason;
        }
        opsState.overview = overviewResult.value;
        opsState.infrastructure = infrastructureResult.status === "fulfilled"
            ? infrastructureResult.value
            : {
                summary: `基础设施健康检查暂不可用：${normalizeOpsError(infrastructureResult.reason)}`,
                components: [],
                errorMessage: normalizeOpsError(infrastructureResult.reason)
            };
        opsState.aiUsage = aiUsageResult.status === "fulfilled"
            ? aiUsageResult.value
            : {
                days: opsState.days,
                modelMetrics: [],
                errorMessage: normalizeOpsError(aiUsageResult.reason)
            };
        opsState.lastRefreshAt = new Date().toISOString();
        renderOpsOverview();
        if (infrastructureResult.status === "rejected" || aiUsageResult.status === "rejected") {
            setOpsBanner("核心任务指标已加载，但部分附属指标暂不可用，请查看页面内提示。", "warning", 2600);
        } else {
            setOpsBanner(`已加载近 ${opsState.days} 天运维指标。`, "success", 1600);
        }
    } catch (error) {
        handleOpsAuthFailure(error, error.message || "加载运维指标失败。");
    } finally {
        opsElements.opsRefreshButton.disabled = false;
    }
}

function renderOpsOverview() {
    renderRangeButtons();
    updateOpsDeepLinks();
    opsElements.lastRefreshText.textContent = `统计窗口：近 ${opsState.days} 天；刷新时间：${OpsCommon.formatDateTime(opsState.lastRefreshAt)}`;
    renderHealthSummary();
    renderOpsActionChecklist();
    renderMetricCards();
    renderAiUsage();
    renderInfrastructureHealth();
    renderStatusDistribution();
    renderDlqMetrics();
    renderGovernanceMetrics();
    renderTaskTypeTable();
}

function renderAiUsage() {
    const usage = opsState.aiUsage || {};
    if (usage.errorMessage) {
        opsElements.aiUsageSummary.textContent = "AI 调用统计暂时不可用，其他运维指标不受影响。";
        opsElements.aiUsageCards.innerHTML = `
            <div class="ops-load-error">
                <strong>AI 指标加载失败</strong>
                <p>${OpsCommon.escapeHtml(usage.errorMessage)}</p>
                <a class="link-button" href="${OpsCommon.escapeAttr(buildOpsLink("/admin/qa-records"))}">查看问答记录</a>
            </div>
        `;
        opsElements.aiModelTableBody.innerHTML = `<tr><td colspan="8">AI 调用统计接口暂不可用：${OpsCommon.escapeHtml(usage.errorMessage)}</td></tr>`;
        return;
    }
    const models = usage.modelMetrics || [];
    opsElements.aiUsageSummary.textContent = `近 ${usage.days || opsState.days} 天共 ${formatNumber(usage.totalCallCount)} 次 AI/问答生成，Token ${formatNumber(usage.totalTokens)}，估算成本 ${formatMoney(usage.estimatedCostCny)}。`;
    const cards = [
        ["总调用", usage.totalCallCount],
        ["成功调用", usage.successCallCount],
        ["失败调用", usage.failedCallCount],
        ["未命中", usage.noHitCount],
        ["转人工", usage.handoffCount],
        ["缓存命中率", formatPercent(usage.cacheHitRate)],
        ["平均生成", formatDuration(usage.avgGenerationLatencyMs)],
        ["估算成本", formatMoney(usage.estimatedCostCny)]
    ];
    opsElements.aiUsageCards.innerHTML = cards.map(([label, value]) => `
        <div class="ops-dlq-item">
            <span>${OpsCommon.escapeHtml(label)}</span>
            <strong>${OpsCommon.escapeHtml(String(value ?? 0))}</strong>
        </div>
    `).join("");
    if (!models.length) {
        opsElements.aiModelTableBody.innerHTML = `<tr><td colspan="8">当前统计窗口暂无 AI 调用记录。</td></tr>`;
        return;
    }
    opsElements.aiModelTableBody.innerHTML = models.map((item) => `
        <tr>
            <td>${OpsCommon.escapeHtml(item.modelName || "unknown")}</td>
            <td>${formatNumber(item.callCount)}</td>
            <td>${formatNumber(item.successCount)}</td>
            <td>${formatNumber(item.failedCount)}</td>
            <td>${formatNumber(item.totalTokens)}</td>
            <td><span class="status-pill ${Number(item.failureRate || 0) >= 10 ? "failed" : "success"}">${formatPercent(item.failureRate)}</span></td>
            <td>${formatDuration(item.avgGenerationLatencyMs)}</td>
            <td>${formatMoney(item.estimatedCostCny)}</td>
        </tr>
    `).join("");
}

function renderHealthSummary() {
    const overview = opsState.overview || {};
    const level = overview.healthLevel || "HEALTHY";
    const score = Number(overview.healthScore ?? 100);
    const issues = overview.healthIssues || [];
    const levelClass = level === "CRITICAL" ? "danger" : level === "WARNING" ? "warning" : "healthy";
    const levelLabel = level === "CRITICAL" ? "高风险" : level === "WARNING" ? "需关注" : "健康";

    opsElements.healthSummary.className = `summary-card ops-health-summary ${levelClass}`;
    opsElements.healthSummary.querySelector("strong").textContent = `${levelLabel}，健康分 ${score}`;
    opsElements.healthSummary.querySelector("p").textContent = overview.healthSummary || "当前异步任务链路整体健康。";

    if (!issues.length) {
        opsElements.healthActions.innerHTML = [
            `<span class="status-pill success">暂无风险项</span>`,
            `<a class="status-pill" href="${OpsCommon.escapeAttr(buildOpsLink("/admin/parse-tasks"))}">查看任务明细</a>`,
            `<a class="status-pill" href="${OpsCommon.escapeAttr(buildOpsLink("/admin/dead-letters"))}">查看死信治理</a>`
        ].join("");
        return;
    }

    opsElements.healthActions.innerHTML = issues.map((issue) => `
        <article class="ops-issue-card ${issue.severity === "CRITICAL" ? "danger" : issue.severity === "WARNING" ? "warning" : "notice"}">
            <div>
                <span>${OpsCommon.escapeHtml(severityLabel(issue.severity))}</span>
                <strong>${OpsCommon.escapeHtml(issue.title || "运维风险")}</strong>
                <p>${OpsCommon.escapeHtml(issue.description || "请关注该风险项。")}</p>
            </div>
            <a class="link-button" href="${OpsCommon.escapeAttr(enrichOpsActionUrl(issue.actionUrl || "/admin/ops-health"))}">${OpsCommon.escapeHtml(issue.actionText || "查看详情")}</a>
        </article>
    `).join("");
}

function renderMetricCards() {
    const overview = opsState.overview || {};
    const dlq = overview.deadLetterMetrics || {};
    const governance = overview.governanceMetrics || {};
    const duplicateSkipCount = Number(governance.duplicateConsumptionSkippedCount || 0)
        + Number(governance.nonPendingMessageSkippedCount || 0);
    const cards = [
        { label: "任务总数", value: formatNumber(overview.totalTaskCount), hint: `近 ${opsState.days} 天创建的异步任务` },
        { label: "成功率", value: formatPercent(overview.successRate), hint: `${formatNumber(overview.successTaskCount)} 个任务成功` },
        { label: "失败率", value: formatPercent(overview.failureRate), hint: `${formatNumber(overview.failedTaskCount)} 个任务失败`, tone: Number(overview.failureRate || 0) >= 10 ? "danger" : "" },
        { label: "P95 耗时", value: formatDuration(overview.p95DurationMs), hint: `平均耗时 ${formatDuration(overview.avgDurationMs)}` },
        { label: "疑似卡住", value: formatNumber(overview.staleProcessingTaskCount), hint: "处理中但长期无心跳的任务", tone: Number(overview.staleProcessingTaskCount || 0) > 0 ? "warning" : "" },
        { label: "未解决 DLQ", value: formatNumber(dlq.unresolvedCount), hint: "仍需自动重试或人工处理", tone: Number(dlq.unresolvedCount || 0) > 0 ? "danger" : "" },
        { label: "幂等拦截", value: formatNumber(duplicateSkipCount), hint: "重复消费或非待处理消息被安全跳过", tone: duplicateSkipCount > 0 ? "warning" : "" },
        { label: "过期 attempt", value: formatNumber(governance.staleAttemptCompletionSkippedCount), hint: "旧 worker 晚返回结果被拦截", tone: Number(governance.staleAttemptCompletionSkippedCount || 0) > 0 ? "warning" : "" },
        { label: "启动恢复", value: formatNumber(governance.startupStaleTaskRecoveredCount), hint: "应用启动时接管卡住任务", tone: Number(governance.startupStaleTaskRecoveredCount || 0) > 0 ? "warning" : "" }
    ];

    opsElements.metricCards.innerHTML = cards.map((card) => `
        <article class="ops-metric-card ${card.tone || ""}">
            <span>${OpsCommon.escapeHtml(card.label)}</span>
            <strong>${OpsCommon.escapeHtml(card.value)}</strong>
            <p>${OpsCommon.escapeHtml(card.hint)}</p>
        </article>
    `).join("");
}


function renderInfrastructureHealth() {
    const infrastructure = opsState.infrastructure || {};
    const components = infrastructure.components || [];
    opsElements.infraSummary.textContent = infrastructure.summary || "暂未获取到基础设施健康状态。";
    if (!components.length) {
        opsElements.infraHealthGrid.innerHTML = OpsCommon.renderStateBlock({
            title: "暂无基础设施组件状态",
            message: "基础设施健康接口未返回组件明细，核心任务指标仍可继续查看。",
            compact: true
        });
        return;
    }
    opsElements.infraHealthGrid.innerHTML = components.map((component) => {
        const tone = infrastructureToneClass(component.status);
        return `
            <article class="ops-infra-card ${tone}">
                <div class="ops-infra-card-head">
                    <div>
                        <span>${OpsCommon.escapeHtml(component.type || "依赖组件")}</span>
                        <strong>${OpsCommon.escapeHtml(component.name || component.key || "未知组件")}</strong>
                    </div>
                    <b>${OpsCommon.escapeHtml(infrastructureStatusLabel(component.status))}</b>
                </div>
                <p>${OpsCommon.escapeHtml(component.description || "暂无说明。")}</p>
                <a class="link-button" href="${OpsCommon.escapeAttr(enrichOpsActionUrl(component.actionUrl || "/admin/ops-health"))}">${OpsCommon.escapeHtml(component.actionText || "查看详情")}</a>
            </article>
        `;
    }).join("");
}

function infrastructureToneClass(status) {
    const normalized = String(status || "").toUpperCase();
    if (normalized === "UP") {
        return "success";
    }
    if (normalized === "DISABLED") {
        return "muted";
    }
    if (normalized === "DOWN" || normalized === "OUT_OF_SERVICE") {
        return "failed";
    }
    return "warning";
}

function infrastructureStatusLabel(status) {
    const labels = {
        UP: "正常",
        DOWN: "异常",
        OUT_OF_SERVICE: "不可用",
        UNKNOWN: "未知",
        DISABLED: "未启用",
        DEGRADED: "降级"
    };
    return labels[String(status || "").toUpperCase()] || "未知";
}
function renderStatusDistribution() {
    const overview = opsState.overview || {};
    const total = Math.max(Number(overview.totalTaskCount || 0), 1);
    const rows = overview.statusMetrics || [];
    if (!rows.length) {
        opsElements.statusDistribution.innerHTML = OpsCommon.renderStateBlock({
            title: "暂无任务状态数据",
            message: "当前统计窗口还没有异步任务状态记录。",
            compact: true
        });
        return;
    }

    opsElements.statusDistribution.innerHTML = rows.map((item) => {
        const count = Number(item.count || 0);
        const width = Math.min(100, Math.round(count * 100 / total));
        const statusClass = statusToneClass(item.status);
        return `
            <div class="ops-distribution-row">
                <div class="ops-distribution-meta">
                    <span>${OpsCommon.escapeHtml(OpsCommon.enumLabel("knowledgeBaseStatus", item.status, item.status || "未知状态"))}</span>
                    <strong>${formatNumber(count)}</strong>
                </div>
                <div class="ops-bar ${statusClass}"><i style="width: ${width}%"></i></div>
            </div>
        `;
    }).join("");
}

function statusToneClass(status) {
    const normalized = String(status || "").toUpperCase();
    const mapping = {
        SUCCESS: "success",
        READY: "success",
        FAILED: "failed",
        ERROR: "failed",
        PROCESSING: "processing",
        RUNNING: "processing",
        PENDING: "pending",
        WAITING: "pending",
        CANCELLED: "muted"
    };
    return mapping[normalized] || "muted";
}
function renderDlqMetrics() {
    const dlq = (opsState.overview || {}).deadLetterMetrics || {};
    const items = [
        ["死信总数", dlq.totalCount],
        ["未解决", dlq.unresolvedCount],
        ["可回放", dlq.readyCount],
        ["待人工", dlq.manualRequiredCount],
        ["已回放", dlq.replayedCount],
        ["已解决", dlq.resolvedCount]
    ];
    opsElements.dlqMetrics.innerHTML = items.map(([label, value]) => `
        <div class="ops-dlq-item">
            <span>${OpsCommon.escapeHtml(label)}</span>
            <strong>${formatNumber(value)}</strong>
        </div>
    `).join("");
}

function renderGovernanceMetrics() {
    const governance = (opsState.overview || {}).governanceMetrics || {};
    const duplicateSkipCount = Number(governance.duplicateConsumptionSkippedCount || 0)
        + Number(governance.nonPendingMessageSkippedCount || 0);
    const items = [
        ["治理事件", governance.totalEventCount],
        ["幂等拦截", duplicateSkipCount],
        ["锁冲突跳过", governance.duplicateConsumptionSkippedCount],
        ["非待处理跳过", governance.nonPendingMessageSkippedCount],
        ["过期 attempt", governance.staleAttemptCompletionSkippedCount],
        ["启动恢复", governance.startupStaleTaskRecoveredCount]
    ];
    opsElements.governanceMetrics.innerHTML = items.map(([label, value]) => `
        <div class="ops-dlq-item">
            <span>${OpsCommon.escapeHtml(label)}</span>
            <strong>${formatNumber(value)}</strong>
        </div>
    `).join("");
}

function renderTaskTypeTable() {
    const rows = (opsState.overview || {}).taskTypeMetrics || [];
    if (!rows.length) {
        opsElements.taskTypeTableBody.innerHTML = `<tr><td colspan="8">当前统计窗口暂无任务类型指标。</td></tr>`;
        return;
    }
    opsElements.taskTypeTableBody.innerHTML = rows.map((item) => `
        <tr>
            <td>${OpsCommon.escapeHtml(OpsCommon.enumLabel("taskType", item.taskType, item.taskType || "未知任务"))}</td>
            <td>${formatNumber(item.totalCount)}</td>
            <td>${formatNumber(item.successCount)}</td>
            <td>${formatNumber(item.failedCount)}</td>
            <td>${formatNumber(item.processingCount)}</td>
            <td><span class="status-pill ${Number(item.failureRate || 0) >= 10 ? "failed" : "success"}">${formatPercent(item.failureRate)}</span></td>
            <td>${formatDuration(item.avgDurationMs)}</td>
            <td>${formatDuration(item.p95DurationMs)}</td>
        </tr>
    `).join("");
}

function renderOpsActionChecklist() {
    if (!opsElements.opsActionChecklist) {
        return;
    }
    const overview = opsState.overview || {};
    const dlq = overview.deadLetterMetrics || {};
    const governance = overview.governanceMetrics || {};
    const usage = opsState.aiUsage || {};
    const duplicateSkipCount = Number(governance.duplicateConsumptionSkippedCount || 0)
        + Number(governance.nonPendingMessageSkippedCount || 0);
    const aiFailureRate = usage.totalCallCount
        ? Number(usage.failedCallCount || 0) * 100 / Number(usage.totalCallCount || 1)
        : 0;
    const actions = [
        {
            tone: Number(overview.failedTaskCount || 0) > 0 ? "danger" : "success",
            title: "失败任务定位",
            metric: `${formatNumber(overview.failedTaskCount)} 个失败`,
            description: "先按失败任务过滤解析队列，查看错误原因、运行态快照和是否需要重试。",
            linkText: "查看失败任务",
            href: buildOpsLink("/admin/parse-tasks", { status: "FAILED" })
        },
        {
            tone: Number(dlq.unresolvedCount || 0) > 0 ? "danger" : "success",
            title: "死信队列治理",
            metric: `${formatNumber(dlq.unresolvedCount)} 条未解决`,
            description: "进入 DLQ 查看可回放、待人工、已回放记录，把失败消息重新纳入处理链路。",
            linkText: "进入死信治理",
            href: buildOpsLink("/admin/dead-letters")
        },
        {
            tone: Number(overview.staleProcessingTaskCount || 0) > 0 ? "warning" : "success",
            title: "卡住任务排查",
            metric: `${formatNumber(overview.staleProcessingTaskCount)} 个疑似卡住`,
            description: "重点观察处理中任务的心跳、队列状态和 worker 标识，判断是否需要启动恢复或人工重试。",
            linkText: "查看任务链路",
            href: buildOpsLink("/admin/parse-tasks", { status: "PROCESSING" })
        },
        {
            tone: aiFailureRate >= 10 || Number(usage.noHitCount || 0) > 0 ? "warning" : "success",
            title: "问答与模型质量",
            metric: `${formatPercent(aiFailureRate)} 失败率`,
            description: "结合问答记录查看未命中、转人工、模型耗时与来源片段，判断是检索问题还是生成问题。",
            linkText: "查看问答记录",
            href: buildOpsLink("/admin/qa-records")
        },
        {
            tone: duplicateSkipCount > 0 ? "warning" : "success",
            title: "幂等与重复消费",
            metric: `${formatNumber(duplicateSkipCount)} 次拦截`,
            description: "面试演示时可以重点说明：重复消息不会重复落库，过期 attempt 会被安全拦截。",
            linkText: "查看治理指标",
            href: buildOpsLink("/admin/parse-tasks")
        }
    ];

    opsElements.opsActionChecklist.innerHTML = actions.map((action) => `
        <article class="ops-action-card ${action.tone}">
            <span>${OpsCommon.escapeHtml(action.title)}</span>
            <strong>${OpsCommon.escapeHtml(action.metric)}</strong>
            <p>${OpsCommon.escapeHtml(action.description)}</p>
            <a class="link-button" href="${OpsCommon.escapeAttr(action.href)}">${OpsCommon.escapeHtml(action.linkText)}</a>
        </article>
    `).join("");
}

function updateOpsDeepLinks() {
    const links = [
        ["qaRecordsLink", buildOpsLink("/admin/qa-records")],
        ["parseTasksLink", buildOpsLink("/admin/parse-tasks")],
        ["deadLettersLink", buildOpsLink("/admin/dead-letters")],
        ["taskChainLink", buildOpsLink("/admin/parse-tasks")],
        ["failedTasksLink", buildOpsLink("/admin/parse-tasks", { status: "FAILED" })]
    ];
    links.forEach(([id, href]) => {
        if (opsElements[id]) {
            opsElements[id].href = href;
        }
    });
}

function buildOpsLink(path, extraParams = {}) {
    const url = new URL(path, window.location.origin);
    url.searchParams.set("source", "ops-health");
    url.searchParams.set("returnUrl", `/admin/ops-health?days=${opsState.days}`);
    url.searchParams.set("returnLabel", "运维健康监控台");
    Object.entries(extraParams).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
            url.searchParams.set(key, String(value));
        }
    });
    return `${url.pathname}${url.search}${url.hash}`;
}

function enrichOpsActionUrl(actionUrl) {
    if (!actionUrl || actionUrl === "/admin/ops-health") {
        return actionUrl || "/admin/ops-health";
    }
    const url = new URL(actionUrl, window.location.origin);
    if (!url.pathname.startsWith("/admin/") || url.pathname === "/admin/ops-health") {
        return `${url.pathname}${url.search}${url.hash}`;
    }
    url.searchParams.set("source", url.searchParams.get("source") || "ops-health");
    url.searchParams.set("returnUrl", `/admin/ops-health?days=${opsState.days}`);
    url.searchParams.set("returnLabel", "运维健康监控台");
    return `${url.pathname}${url.search}${url.hash}`;
}

function syncOpsQuery() {
    OpsCommon.updateQuery({ days: opsState.days });
}

function normalizeOpsError(error) {
    const message = error?.message || String(error || "未知错误");
    if (message.includes("No static resource") || message.includes("404")) {
        return `${message}。如果你刚更新过代码，请重启后端或强制刷新浏览器缓存。`;
    }
    return message;
}

function renderRangeButtons() {
    opsElements.rangeButtons.forEach((button) => {
        button.classList.toggle("active", Number(button.dataset.days || 7) === opsState.days);
    });
}

function showOpsLoggedInState() {
    opsElements.loginPanel.hidden = true;
    opsElements.workspace.hidden = false;
    if (opsElements.sessionName) {
        opsElements.sessionName.textContent = opsState.user?.realName || opsState.user?.username || "管理用户";
    }
    if (opsElements.sessionMeta) {
        opsElements.sessionMeta.textContent = OpsCommon.formatUserSessionMeta(opsState.user);
    }
}

function showOpsLoggedOutState() {
    opsElements.loginPanel.hidden = false;
    opsElements.workspace.hidden = true;
    if (opsElements.sessionName) {
        opsElements.sessionName.textContent = "未登录";
    }
    if (opsElements.sessionMeta) {
        opsElements.sessionMeta.textContent = "请先登录后查看运维健康指标。";
    }
    setOpsBanner("请先登录后查看运维健康指标。", "info");
}

function handleOpsAuthFailure(error, fallbackMessage) {
    if (error instanceof OpsCommon.AuthExpiredError) {
        opsState.token = "";
        opsState.user = null;
        OpsCommon.clearToken();
        showOpsLoggedOutState();
    }
    setOpsBanner(error.message || fallbackMessage, "error");
}

function setOpsBanner(message, type = "info", autoHideMs = 0) {
    window.clearTimeout(setOpsBanner.timer);
    opsElements.statusBanner.hidden = false;
    opsElements.statusBanner.className = `status-banner ${type}`;
    opsElements.statusBanner.textContent = message;
    if (autoHideMs > 0) {
        setOpsBanner.timer = window.setTimeout(clearOpsBanner, autoHideMs);
    }
}

function clearOpsBanner() {
    opsElements.statusBanner.hidden = true;
    opsElements.statusBanner.className = "status-banner";
    opsElements.statusBanner.textContent = "";
}

function severityLabel(value) {
    const labels = {
        CRITICAL: "严重",
        WARNING: "警告",
        NOTICE: "提示"
    };
    return labels[value] || "提示";
}

function formatNumber(value) {
    return OpsCommon.formatNumber(Number(value || 0));
}

function formatPercent(value) {
    return `${Number(value || 0).toFixed(1)}%`;
}

function formatDuration(value) {
    const milliseconds = Number(value || 0);
    if (!milliseconds) {
        return "0 ms";
    }
    if (milliseconds < 1000) {
        return `${Math.round(milliseconds)} ms`;
    }
    if (milliseconds < 60000) {
        return `${(milliseconds / 1000).toFixed(1)} s`;
    }
    return `${(milliseconds / 60000).toFixed(1)} min`;
}

function formatMoney(value) {
    const amount = Number(value || 0);
    return `¥${amount.toFixed(6)}`;
}
