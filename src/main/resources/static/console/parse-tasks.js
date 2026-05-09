const TaskCommon = window.KnowFlowConsoleCommon;

const taskState = {
    token: TaskCommon.readToken(),
    query: TaskCommon.parseQuery(),
    user: null,
    tasks: [],
    selectedTaskId: null,
    selectedTask: null,
    selectedDocument: null,
    pageNo: 1,
    pageSize: 10,
    total: 0,
    lastRefreshAt: null,
    filters: {
        documentId: "",
        status: "",
        taskType: ""
    }
};

const taskElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    cacheTaskElements();
    initTaskStateFromQuery();
    bindTaskEvents();
    if (taskState.token) {
        await restoreTaskSession();
        return;
    }
    showTaskLoggedOutState();
});

function cacheTaskElements() {
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
        "documentIdFilter",
        "statusFilter",
        "taskTypeFilter",
        "lastRefreshText",
        "searchButton",
        "resetButton",
        "contextBanner",
        "taskPageMeta",
        "taskTable",
        "taskPaginationText",
        "prevPageButton",
        "nextPageButton",
        "taskDetailBody"
    ].forEach((id) => {
        taskElements[id] = document.getElementById(id);
    });
}

function initTaskStateFromQuery() {
    const query = taskState.query;
    taskState.pageNo = Number(query.pageNo || 1);
    taskState.selectedTaskId = query.taskId ? Number(query.taskId) : null;
    taskState.filters.documentId = query.documentId || "";
    taskState.filters.status = query.status || "";
    taskState.filters.taskType = query.taskType || "";
    applyTaskFilterInputs();
}

function bindTaskEvents() {
    taskElements.loginForm.addEventListener("submit", handleTaskLogin);
    taskElements.refreshButton.addEventListener("click", () => loadTaskConsole(true));
    taskElements.logoutButton.addEventListener("click", logoutTaskConsole);
    taskElements.searchButton.addEventListener("click", async () => {
        readTaskFiltersFromInputs();
        taskState.pageNo = 1;
        await loadTaskConsole(false);
    });
    taskElements.resetButton.addEventListener("click", async () => {
        taskState.filters = {
            documentId: "",
            status: "",
            taskType: ""
        };
        taskState.pageNo = 1;
        taskState.selectedTaskId = null;
        taskState.selectedTask = null;
        taskState.selectedDocument = null;
        applyTaskFilterInputs();
        syncTaskQuery();
        await loadTaskConsole(false);
    });
    taskElements.prevPageButton.addEventListener("click", async () => {
        if (taskState.pageNo <= 1) {
            return;
        }
        taskState.pageNo -= 1;
        await loadTaskConsole(true);
    });
    taskElements.nextPageButton.addEventListener("click", async () => {
        const totalPage = Math.max(1, Math.ceil(taskState.total / taskState.pageSize));
        if (taskState.pageNo >= totalPage) {
            return;
        }
        taskState.pageNo += 1;
        await loadTaskConsole(true);
    });
}

async function restoreTaskSession() {
    try {
        taskState.user = await TaskCommon.api("/api/v1/auth/me", { token: taskState.token });
        showTaskLoggedInState();
        renderTaskContextBanner();
        await loadTaskConsole(true);
    } catch (error) {
        handleTaskAuthFailure(error, "登录已过期，请重新登录。");
    }
}

async function handleTaskLogin(event) {
    event.preventDefault();
    const username = taskElements.usernameInput.value.trim();
    const password = taskElements.passwordInput.value;
    if (!username || !password) {
        setTaskBanner("请输入账号和密码。", "error");
        return;
    }

    taskElements.loginButton.disabled = true;
    setTaskBanner("正在登录解析任务监控台...", "info");
    try {
        const data = await TaskCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        taskState.token = data.token;
        TaskCommon.saveToken(data.token);
        taskElements.passwordInput.value = "";
        await restoreTaskSession();
        setTaskBanner("解析任务监控台已就绪。", "success");
    } catch (error) {
        setTaskBanner(error.message || "登录失败。", "error");
    } finally {
        taskElements.loginButton.disabled = false;
    }
}

function logoutTaskConsole() {
    taskState.token = "";
    taskState.user = null;
    taskState.tasks = [];
    taskState.selectedTaskId = null;
    taskState.selectedTask = null;
    taskState.selectedDocument = null;
    taskState.total = 0;
    TaskCommon.clearToken();
    TaskCommon.updateQuery({
        pageNo: null,
        taskId: null,
        documentId: null,
        status: null,
        taskType: null
    });
    showTaskLoggedOutState();
    setTaskBanner("已退出解析任务监控台。", "success");
}

function showTaskLoggedOutState() {
    taskElements.loginPanel.hidden = false;
    taskElements.workspace.hidden = true;
    taskElements.sessionName.textContent = "未登录";
    taskElements.sessionMeta.textContent = "请先登录后查看异步处理运行态。";
}

function showTaskLoggedInState() {
    taskElements.loginPanel.hidden = true;
    taskElements.workspace.hidden = false;
    taskElements.sessionName.textContent = taskState.user.realName || taskState.user.username;
    taskElements.sessionMeta.textContent = TaskCommon.formatUserSessionMeta(taskState.user);
}

function renderTaskContextBanner() {
    const { source, documentId, knowledgeBaseId } = taskState.query;
    const returnLink = buildTaskReturnLink();
    if (!source && !documentId && !knowledgeBaseId && !returnLink) {
        taskElements.contextBanner.hidden = true;
        taskElements.contextBanner.textContent = "";
        return;
    }
    const parts = [];
    if (source) {
        parts.push(`来自 ${TaskCommon.enumLabel("pageSource", source, source)}`);
    }
    if (documentId) {
        parts.push(`文档 #${documentId}`);
    }
    if (knowledgeBaseId) {
        parts.push(`知识库 #${knowledgeBaseId}`);
    }
    taskElements.contextBanner.hidden = false;
    taskElements.contextBanner.innerHTML = `
        <strong>解析任务定位</strong>
        <span>${TaskCommon.escapeHtml(parts.join(" | ") || "正在查看解析任务队列")}</span>
        ${returnLink}
    `;
}

async function loadTaskConsole(keepSelection) {
    if (!taskState.token) {
        showTaskLoggedOutState();
        return;
    }

    renderTaskListLoading();
    syncTaskQuery();
    try {
        const pageData = await TaskCommon.api(buildTaskListUrl(), { token: taskState.token });
        taskState.tasks = pageData.records || [];
        taskState.total = pageData.total || 0;
        taskState.pageNo = pageData.pageNo || taskState.pageNo;
        taskState.pageSize = pageData.pageSize || taskState.pageSize;
        taskState.lastRefreshAt = new Date();
        taskElements.lastRefreshText.textContent = TaskCommon.formatDateTime(taskState.lastRefreshAt);
        renderTaskList();

        const preferredId = resolvePreferredTaskId(keepSelection);
        if (preferredId) {
            await loadTaskDetail(preferredId, true);
        } else {
            taskState.selectedTask = null;
            taskState.selectedDocument = null;
            renderTaskDetail();
        }
    } catch (error) {
        if (error instanceof TaskCommon.AuthExpiredError) {
            handleTaskAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        taskElements.taskTable.innerHTML = TaskCommon.renderStateBlock({
            type: "error",
            title: "解析任务加载失败",
            message: error.message || "请稍后重试，或检查当前账号是否具备解析任务查看权限。"
        });
        setTaskBanner(error.message || "解析任务加载失败。", "error");
    }
}

function buildTaskListUrl() {
    const params = new URLSearchParams({
        pageNo: String(taskState.pageNo),
        pageSize: String(taskState.pageSize)
    });
    if (taskState.filters.documentId) {
        params.set("documentId", taskState.filters.documentId);
    }
    if (taskState.filters.status) {
        params.set("status", taskState.filters.status);
    }
    if (taskState.filters.taskType) {
        params.set("taskType", taskState.filters.taskType);
    }
    return `/api/v1/admin/parse-tasks?${params.toString()}`;
}

function renderTaskListLoading() {
    taskElements.taskPageMeta.textContent = "正在加载解析任务队列...";
    taskElements.taskTable.innerHTML = TaskCommon.renderStateBlock({
        type: "loading",
        title: "正在加载解析任务",
        message: "系统正在同步解析、索引、运行态和任务治理信息。"
    });
    taskElements.taskPaginationText.textContent = "暂无分页信息";
}

function renderTaskList() {
    const totalPage = Math.max(1, Math.ceil(taskState.total / taskState.pageSize));
    taskElements.taskPageMeta.textContent = `当前范围内共有 ${TaskCommon.formatNumber(taskState.total)} 个任务`;
    taskElements.taskPaginationText.textContent = `第 ${taskState.pageNo} / ${totalPage} 页`;
    taskElements.prevPageButton.disabled = taskState.pageNo <= 1;
    taskElements.nextPageButton.disabled = taskState.pageNo >= totalPage;

    if (!taskState.tasks.length) {
        taskElements.taskTable.innerHTML = TaskCommon.renderStateBlock({
            title: "未找到解析任务",
            message: "可以调整筛选条件，或上传新文档来触发解析任务。",
            actionHref: "/admin/documents",
            actionText: "去上传文档"
        });
        return;
    }

    taskElements.taskTable.innerHTML = `
        <table class="data-table">
            <thead>
            <tr>
                <th>任务</th>
                <th>状态</th>
                <th>关联文档</th>
                <th>运行态</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody>
            ${taskState.tasks.map((task) => `
                <tr class="${task.id === taskState.selectedTaskId ? "active-row" : ""}">
                    <td>
                        <div class="table-question">
                            <strong>${TaskCommon.escapeHtml(task.taskNo || `任务 #${task.id}`)}</strong>
                            <div class="meta-line">${TaskCommon.escapeHtml(TaskCommon.enumLabel("taskType", task.taskType, "未知任务类型"))}</div>
                        </div>
                    </td>
                    <td>
                        <div class="chips">
                            <span class="status-pill ${TaskCommon.statusClass(task.status)}">${TaskCommon.escapeHtml(TaskCommon.enumLabel("knowledgeBaseStatus", task.status, "未知状态"))}</span>
                            <span class="status-pill">重试 ${TaskCommon.escapeHtml(task.retryCount ?? 0)}</span>
                        </div>
                    </td>
                    <td>
                        <div class="meta-line">文档 #${TaskCommon.escapeHtml(task.documentId || "N/A")}</div>
                        <div class="tiny-line">${TaskCommon.escapeHtml(TaskCommon.formatDateTime(task.updatedAt || task.createdAt))}</div>
                    </td>
                    <td>
                        <div class="meta-line">${TaskCommon.escapeHtml(task.runtime?.transport || "暂无运行态")}</div>
                        <div class="tiny-line">${TaskCommon.escapeHtml(task.runtime?.queueStatus || "暂无队列状态")}</div>
                    </td>
                    <td><button class="link-button table-row-button" type="button" data-task-id="${task.id}">查看</button></td>
                </tr>
            `).join("")}
            </tbody>
        </table>
    `;

    taskElements.taskTable.querySelectorAll("[data-task-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await loadTaskDetail(Number(button.dataset.taskId), false);
        });
    });
}

function resolvePreferredTaskId(keepSelection) {
    if (keepSelection && taskState.selectedTaskId) {
        return taskState.selectedTaskId;
    }
    if (taskState.query.taskId) {
        return Number(taskState.query.taskId);
    }
    if (taskState.selectedTaskId) {
        return taskState.selectedTaskId;
    }
    return taskState.tasks[0]?.id || null;
}

async function loadTaskDetail(taskId, silent) {
    if (!taskId) {
        return;
    }
    taskState.selectedTaskId = taskId;
    syncTaskQuery();
    renderTaskList();
    if (!silent) {
        taskElements.taskDetailBody.innerHTML = TaskCommon.renderStateBlock({
            type: "loading",
            title: "正在加载任务详情",
            message: "正在读取生命周期、关联文档和运行态快照。"
        });
    }

    try {
        const task = await TaskCommon.api(`/api/v1/admin/parse-tasks/${taskId}`, { token: taskState.token });
        taskState.selectedTask = task;
        taskState.selectedDocument = task.documentId
            ? await TaskCommon.api(`/api/v1/admin/documents/${task.documentId}`, { token: taskState.token })
            : null;
        renderTaskDetail();
    } catch (error) {
        if (error instanceof TaskCommon.AuthExpiredError) {
            handleTaskAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        taskElements.taskDetailBody.innerHTML = TaskCommon.renderStateBlock({
            type: "error",
            title: "任务详情加载失败",
            message: error.message || "请稍后重试，或从左侧重新选择任务。"
        });
        setTaskBanner(error.message || "解析任务详情加载失败。", "error");
    }
}

function renderTaskDetail() {
    const task = taskState.selectedTask;
    if (!task) {
        taskElements.taskDetailBody.innerHTML = TaskCommon.renderStateBlock({
            title: "暂未选择解析任务",
            message: "选择一个任务后，这里会展示运行态细节和恢复操作。"
        });
        return;
    }

    const runtime = task.runtime;
    const documentItem = taskState.selectedDocument;
    const canRetry = task.status !== "PROCESSING" && task.status !== "PENDING";
    const showFailureGovernanceLink = task.status === "FAILED" || !!(task.errorMessage || runtime?.errorMessage);

    taskElements.taskDetailBody.innerHTML = `
        <section class="summary-card">
            <span class="panel-kicker">当前任务</span>
            <strong>${TaskCommon.escapeHtml(task.taskNo || `任务 #${task.id}`)}</strong>
            <div class="chips">
                <span class="status-pill ${TaskCommon.statusClass(task.status)}">${TaskCommon.escapeHtml(TaskCommon.enumLabel("knowledgeBaseStatus", task.status, "未知状态"))}</span>
                <span class="status-pill">${TaskCommon.escapeHtml(TaskCommon.enumLabel("taskType", task.taskType, "未知任务类型"))}</span>
                <span class="status-pill">重试 ${TaskCommon.escapeHtml(task.retryCount ?? 0)}</span>
                <span class="status-pill">文档 #${TaskCommon.escapeHtml(task.documentId || "N/A")}</span>
            </div>
            <p>${TaskCommon.escapeHtml(task.errorMessage || runtime?.errorMessage || "该任务暂无错误信息。")}</p>
            <div class="action-row" style="margin-top:14px;">
                ${documentItem ? `<a class="link-button" href="${buildDocumentLink(documentItem)}">打开文档</a>` : ""}
                ${documentItem ? `<a class="link-button" href="${buildKnowledgeBaseLink(documentItem)}">打开知识库</a>` : ""}
                ${showFailureGovernanceLink ? `<a class="link-button" href="${buildDeadLetterLink(task, documentItem)}">打开死信治理</a>` : ""}
                ${canRetry ? `<button id="retryTaskButton" class="primary-button" type="button">重试任务</button>` : ""}
            </div>
        </section>

        <section class="detail-columns">
            <article class="detail-card">
                <strong>生命周期</strong>
                <p>创建时间：${TaskCommon.escapeHtml(TaskCommon.formatDateTime(task.createdAt))}</p>
                <p>开始时间：${TaskCommon.escapeHtml(TaskCommon.formatDateTime(task.startedAt))}</p>
                <p>结束时间：${TaskCommon.escapeHtml(TaskCommon.formatDateTime(task.finishedAt))}</p>
                <p>耗时：${TaskCommon.escapeHtml(formatDuration(task.durationMs))}</p>
            </article>
            <article class="detail-card">
                <strong>关联文档</strong>
                ${documentItem ? `
                    <p>名称：${TaskCommon.escapeHtml(documentItem.docName || `文档 #${documentItem.id}`)}</p>
                    <p>知识库：${TaskCommon.escapeHtml(taskState.query.knowledgeBaseId || documentItem.knowledgeBaseId || "N/A")}</p>
                    <p>解析状态：${TaskCommon.escapeHtml(TaskCommon.enumLabel("knowledgeBaseStatus", documentItem.parseStatus, "未知状态"))}</p>
                    <p>索引状态：${TaskCommon.escapeHtml(TaskCommon.enumLabel("knowledgeBaseStatus", documentItem.indexStatus, "未知状态"))}</p>
                ` : `<p>暂无关联文档详情。</p>`}
            </article>
        </section>

        <section class="detail-card">
            <strong>运行态快照</strong>
            ${runtime ? `
                <div class="form-grid">
                    <label class="field">
                        <span>传输方式</span>
                        <input type="text" value="${TaskCommon.escapeAttr(runtime.transport || "N/A")}" disabled>
                    </label>
                    <label class="field">
                        <span>队列状态</span>
                        <input type="text" value="${TaskCommon.escapeAttr(runtime.queueStatus || "N/A")}" disabled>
                    </label>
                    <label class="field">
                        <span>处理节点标识</span>
                        <input type="text" value="${TaskCommon.escapeAttr(runtime.workerId || "暂无")}" disabled>
                    </label>
                    <label class="field">
                        <span>入队时间</span>
                        <input type="text" value="${TaskCommon.escapeAttr(TaskCommon.formatDateTime(runtime.queuedAt))}" disabled>
                    </label>
                    <label class="field">
                        <span>出队时间</span>
                        <input type="text" value="${TaskCommon.escapeAttr(TaskCommon.formatDateTime(runtime.dequeuedAt))}" disabled>
                    </label>
                    <label class="field">
                        <span>最近心跳</span>
                        <input type="text" value="${TaskCommon.escapeAttr(TaskCommon.formatDateTime(runtime.lastHeartbeatAt))}" disabled>
                    </label>
                    <label class="field">
                        <span>排队耗时</span>
                        <input type="text" value="${TaskCommon.escapeAttr(formatDuration(runtime.queueLatencyMs))}" disabled>
                    </label>
                    <label class="field">
                        <span>分块数量</span>
                        <input type="text" value="${TaskCommon.escapeAttr(runtime.chunkCount ?? 0)}" disabled>
                    </label>
                    <label class="field">
                        <span>运行耗时</span>
                        <input type="text" value="${TaskCommon.escapeAttr(formatDuration(runtime.durationMs))}" disabled>
                    </label>
                    <label class="field full-span">
                        <span>运行时错误</span>
                        <textarea disabled>${TaskCommon.escapeHtml(runtime.errorMessage || "暂无运行时错误。")}</textarea>
                    </label>
                </div>
            ` : `${TaskCommon.renderStateBlock({
                title: "暂无运行态快照",
                message: "当前任务暂未生成运行态快照。"
            })}`}
        </section>
    `;

    bindTaskDetailActions();
}

function bindTaskDetailActions() {
    bindTaskIfPresent("retryTaskButton", async () => {
        if (!taskState.selectedTaskId) {
            return;
        }
        try {
            await TaskCommon.api(`/api/v1/admin/parse-tasks/${taskState.selectedTaskId}/retry`, {
                token: taskState.token,
                method: "POST"
            });
            setTaskBanner("解析任务已重新投递。", "success");
            await loadTaskConsole(true);
            await loadTaskDetail(taskState.selectedTaskId, true);
        } catch (error) {
            if (error instanceof TaskCommon.AuthExpiredError) {
                handleTaskAuthFailure(error, "登录已过期，请重新登录。");
                return;
            }
            setTaskBanner(error.message || "重试任务失败。", "error");
        }
    });
}

function bindTaskIfPresent(id, handler) {
    const element = document.getElementById(id);
    if (element) {
        element.addEventListener("click", handler);
    }
}

function buildDocumentLink(documentItem) {
    const params = new URLSearchParams({
        documentId: String(documentItem.id),
        knowledgeBaseId: String(documentItem.knowledgeBaseId),
        source: "parse-tasks"
    });
    return `/admin/documents?${params.toString()}`;
}

function buildKnowledgeBaseLink(documentItem) {
    const params = new URLSearchParams({
        knowledgeBaseId: String(documentItem.knowledgeBaseId),
        documentId: String(documentItem.id),
        source: "parse-tasks"
    });
    return `/admin/knowledge-bases?${params.toString()}`;
}

function buildDeadLetterLink(task, documentItem) {
    const params = new URLSearchParams({
        taskId: String(task.id),
        source: "parse-tasks"
    });
    if (documentItem?.id) {
        params.set("documentId", String(documentItem.id));
    }
    if (task.taskType) {
        params.set("taskType", task.taskType);
    }
    return `/admin/dead-letters?${params.toString()}`;
}

function applyTaskFilterInputs() {
    taskElements.documentIdFilter.value = taskState.filters.documentId;
    taskElements.statusFilter.value = taskState.filters.status;
    taskElements.taskTypeFilter.value = taskState.filters.taskType;
}

function readTaskFiltersFromInputs() {
    taskState.filters.documentId = taskElements.documentIdFilter.value.trim();
    taskState.filters.status = taskElements.statusFilter.value;
    taskState.filters.taskType = taskElements.taskTypeFilter.value;
}

function formatDuration(value) {
    const milliseconds = Number(value || 0);
    if (!milliseconds) {
        return "0 ms";
    }
    if (milliseconds < 1000) {
        return `${milliseconds} ms`;
    }
    return `${(milliseconds / 1000).toFixed(milliseconds >= 10000 ? 0 : 1)} s`;
}

function syncTaskQuery() {
    TaskCommon.updateQuery({
        pageNo: taskState.pageNo,
        taskId: taskState.selectedTaskId,
        documentId: taskState.filters.documentId,
        status: taskState.filters.status,
        taskType: taskState.filters.taskType,
        source: taskState.query.source,
        returnUrl: taskState.query.returnUrl,
        returnLabel: taskState.query.returnLabel
    });
}

function buildTaskReturnLink() {
    const returnUrl = normalizeTaskReturnUrl(taskState.query.returnUrl);
    if (!returnUrl) {
        return "";
    }
    const label = taskState.query.returnLabel || "来源页面";
    return `<a class="link-button context-return-button" href="${TaskCommon.escapeAttr(returnUrl)}">返回${TaskCommon.escapeHtml(label)}</a>`;
}

function normalizeTaskReturnUrl(value) {
    const url = String(value || "").trim();
    if (!url || !url.startsWith("/") || url.startsWith("//") || url.toLowerCase().startsWith("javascript:")) {
        return "";
    }
    return url;
}

function handleTaskAuthFailure(error, message) {
    console.error(error);
    logoutTaskConsole();
    setTaskBanner(message, "error");
}

function setTaskBanner(message, type = "info") {
    taskElements.statusBanner.hidden = false;
    taskElements.statusBanner.className = `status-banner ${type}`;
    taskElements.statusBanner.textContent = message;
}



