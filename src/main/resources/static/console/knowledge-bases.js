const KbCommon = window.KnowFlowConsoleCommon;

const kbState = {
    token: KbCommon.readToken(),
    query: KbCommon.parseQuery(),
    user: null,
    records: [],
    selectedKnowledgeBaseId: null,
    selectedKnowledgeBase: null,
    pageNo: 1,
    pageSize: 10,
    total: 0,
    lastRefreshAt: null,
    importFiles: [],
    importResults: [],
    importRunning: false,
    filters: {
        keyword: "",
        status: ""
    }
};

const kbElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    cacheKbElements();
    initKbStateFromQuery();
    bindKbEvents();
    if (kbState.token) {
        await restoreKbSession();
        return;
    }
    showKbLoggedOutState();
});

function cacheKbElements() {
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
        "lastRefreshText",
        "searchButton",
        "resetButton",
        "contextBanner",
        "kbPageMeta",
        "kbTable",
        "kbPaginationText",
        "prevPageButton",
        "nextPageButton",
        "kbDetailBody"
    ].forEach((id) => {
        kbElements[id] = document.getElementById(id);
    });
}

function initKbStateFromQuery() {
    const query = kbState.query;
    kbState.pageNo = Number(query.pageNo || 1);
    kbState.selectedKnowledgeBaseId = query.knowledgeBaseId ? Number(query.knowledgeBaseId) : null;
    kbState.filters.keyword = query.keyword || "";
    kbState.filters.status = query.status || "";
    applyKbFilterInputs();
}

function bindKbEvents() {
    kbElements.loginForm.addEventListener("submit", handleKbLogin);
    kbElements.refreshButton.addEventListener("click", () => loadKbConsole(true));
    kbElements.logoutButton.addEventListener("click", logoutKbConsole);
    kbElements.searchButton.addEventListener("click", async () => {
        readKbFiltersFromInputs();
        kbState.pageNo = 1;
        await loadKbConsole(false);
    });
    kbElements.resetButton.addEventListener("click", async () => {
        kbState.filters = { keyword: "", status: "" };
        kbState.pageNo = 1;
        kbState.selectedKnowledgeBaseId = null;
        kbState.selectedKnowledgeBase = null;
        resetKbImportState();
        applyKbFilterInputs();
        syncKbQuery();
        await loadKbConsole(false);
    });
    kbElements.prevPageButton.addEventListener("click", async () => {
        if (kbState.pageNo <= 1) {
            return;
        }
        kbState.pageNo -= 1;
        await loadKbConsole(true);
    });
    kbElements.nextPageButton.addEventListener("click", async () => {
        const totalPage = Math.max(1, Math.ceil(kbState.total / kbState.pageSize));
        if (kbState.pageNo >= totalPage) {
            return;
        }
        kbState.pageNo += 1;
        await loadKbConsole(true);
    });
}

async function restoreKbSession() {
    try {
        kbState.user = await KbCommon.api("/api/v1/auth/me", { token: kbState.token });
        showKbLoggedInState();
        renderKbContextBanner();
        await loadKbConsole(true);
    } catch (error) {
        handleKbAuthFailure(error, "登录已过期，请重新登录。");
    }
}

async function handleKbLogin(event) {
    event.preventDefault();
    const username = kbElements.usernameInput.value.trim();
    const password = kbElements.passwordInput.value;
    if (!username || !password) {
        setKbBanner("请输入账号和密码。", "error");
        return;
    }

    kbElements.loginButton.disabled = true;
    setKbBanner("正在登录知识库管理台...", "info");
    try {
        const data = await KbCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        kbState.token = data.token;
        KbCommon.saveToken(data.token);
        kbElements.passwordInput.value = "";
        await restoreKbSession();
        setKbBanner("知识库管理台已就绪。", "success", 1400);
    } catch (error) {
        setKbBanner(error.message || "登录失败。", "error");
    } finally {
        kbElements.loginButton.disabled = false;
    }
}

function logoutKbConsole() {
    kbState.token = "";
    kbState.user = null;
    kbState.records = [];
    kbState.selectedKnowledgeBaseId = null;
    kbState.selectedKnowledgeBase = null;
    kbState.total = 0;
    resetKbImportState();
    KbCommon.clearToken();
    KbCommon.updateQuery({
        pageNo: null,
        knowledgeBaseId: null,
        keyword: null,
        status: null
    });
    showKbLoggedOutState();
    setKbBanner("已退出知识库管理台。", "success");
}

function showKbLoggedOutState() {
    kbElements.loginPanel.hidden = false;
    kbElements.workspace.hidden = true;
    kbElements.sessionName.textContent = "未登录";
    kbElements.sessionMeta.textContent = "请先登录后管理租户知识库。";
}

function showKbLoggedInState() {
    kbElements.loginPanel.hidden = true;
    kbElements.workspace.hidden = false;
    kbElements.sessionName.textContent = kbState.user.realName || kbState.user.username;
    kbElements.sessionMeta.textContent = KbCommon.formatUserSessionMeta(kbState.user);
}

function renderKbContextBanner() {
    const { source, documentId } = kbState.query;
    if (!source && !documentId) {
        kbElements.contextBanner.hidden = true;
        kbElements.contextBanner.textContent = "";
        return;
    }
    const parts = [];
    if (source) {
        parts.push(`来自 ${KbCommon.enumLabel("pageSource", source, source)}`);
    }
    if (documentId) {
        parts.push(`关联文档 #${documentId}`);
    }
    if (!kbElements.contextBanner) {
        return;
    }
    kbElements.contextBanner.hidden = false;
    kbElements.contextBanner.textContent = parts.join(" | ");
}

async function loadKbConsole(keepSelection) {
    if (!kbState.token) {
        showKbLoggedOutState();
        return;
    }

    renderKbListLoading();
    syncKbQuery();
    try {
        const pageData = await KbCommon.api(buildKbListUrl(), { token: kbState.token });
        kbState.records = pageData.records || [];
        kbState.total = pageData.total || 0;
        kbState.pageNo = pageData.pageNo || kbState.pageNo;
        kbState.pageSize = pageData.pageSize || kbState.pageSize;
        kbState.lastRefreshAt = new Date();
        if (kbElements.lastRefreshText) {
            kbElements.lastRefreshText.textContent = KbCommon.formatDateTime(kbState.lastRefreshAt);
        }
        renderKbList();

        const preferredId = resolvePreferredKbId(keepSelection);
        if (preferredId) {
            await loadKbDetail(preferredId, true);
        } else {
            kbState.selectedKnowledgeBase = null;
            resetKbImportState();
            renderKbDetail();
        }
    } catch (error) {
        if (error instanceof KbCommon.AuthExpiredError) {
            handleKbAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        kbElements.kbTable.innerHTML = KbCommon.renderStateBlock({
            type: "error",
            title: "知识库列表加载失败",
            message: error.message || "未知错误",
            compact: true
        });
        setKbBanner(error.message || "知识库列表加载失败。", "error");
    }
}

function buildKbListUrl() {
    const params = new URLSearchParams({
        pageNo: String(kbState.pageNo),
        pageSize: String(kbState.pageSize)
    });
    if (kbState.filters.keyword) {
        params.set("keyword", kbState.filters.keyword);
    }
    if (kbState.filters.status) {
        params.set("status", kbState.filters.status);
    }
    return `/api/v1/admin/knowledge-bases?${params.toString()}`;
}

function renderKbListLoading() {
    kbElements.kbPageMeta.textContent = "正在加载知识库目录...";
    kbElements.kbTable.innerHTML = KbCommon.renderStateBlock({
        type: "loading",
        title: "正在加载知识库",
        message: "系统正在读取当前租户的知识空间目录。",
        compact: true
    });
    kbElements.kbPaginationText.textContent = "暂无分页信息";
}

function renderKbList() {
    const totalPage = Math.max(1, Math.ceil(kbState.total / kbState.pageSize));
    kbElements.kbPageMeta.textContent = `当前租户共有 ${KbCommon.formatNumber(kbState.total)} 个知识库`;
    kbElements.kbPaginationText.textContent = `第 ${kbState.pageNo} / ${totalPage} 页`;
    kbElements.prevPageButton.disabled = kbState.pageNo <= 1;
    kbElements.nextPageButton.disabled = kbState.pageNo >= totalPage;

    if (!kbState.records.length) {
        kbElements.kbTable.innerHTML = KbCommon.renderStateBlock({
            title: "未找到知识库",
            message: "可以在右侧面板创建第一个知识库。",
            compact: true
        });
        return;
    }

    kbElements.kbTable.innerHTML = `
        <table class="data-table">
            <thead>
            <tr>
                <th>编码 / 名称</th>
                <th>状态</th>
                <th>文档数</th>
                <th>更新时间</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody>
            ${kbState.records.map((record) => `
                <tr class="${record.id === kbState.selectedKnowledgeBaseId ? "active-row" : ""}">
                    <td>
                        <div class="table-question">
                            <strong>${KbCommon.escapeHtml(record.kbName || `知识库 #${record.id}`)}</strong>
                            <div class="meta-line">${KbCommon.escapeHtml(record.kbCode || "AUTO")}</div>
                        </div>
                    </td>
                    <td><span class="status-pill ${KbCommon.statusClass(record.status)}">${KbCommon.escapeHtml(KbCommon.enumLabel("knowledgeBaseStatus", record.status, "未知状态"))}</span></td>
                    <td>${KbCommon.formatNumber(record.docCount || 0)}</td>
                    <td>${KbCommon.escapeHtml(KbCommon.formatDateTime(record.updatedAt || record.createdAt))}</td>
                    <td><button class="link-button table-row-button" type="button" data-kb-id="${record.id}">查看</button></td>
                </tr>
            `).join("")}
            </tbody>
        </table>
    `;

    kbElements.kbTable.querySelectorAll("[data-kb-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await loadKbDetail(Number(button.dataset.kbId), false);
        });
    });
}

function resolvePreferredKbId(keepSelection) {
    if (keepSelection && kbState.selectedKnowledgeBaseId) {
        return kbState.selectedKnowledgeBaseId;
    }
    if (kbState.query.knowledgeBaseId) {
        return Number(kbState.query.knowledgeBaseId);
    }
    if (kbState.selectedKnowledgeBaseId) {
        return kbState.selectedKnowledgeBaseId;
    }
    return kbState.records[0]?.id || null;
}

async function loadKbDetail(knowledgeBaseId, silent) {
    if (!knowledgeBaseId) {
        return;
    }
    const changedSelection = kbState.selectedKnowledgeBaseId !== knowledgeBaseId;
    if (changedSelection) {
        resetKbImportState();
    }
    kbState.selectedKnowledgeBaseId = knowledgeBaseId;
    syncKbQuery();
    renderKbList();
    if (!silent) {
        kbElements.kbDetailBody.innerHTML = KbCommon.renderStateBlock({
            type: "loading",
            title: "正在加载知识库详情",
            message: "即将展示元数据、文档统计、失败洞察和批量导入入口。",
            compact: true
        });
    }

    try {
        kbState.selectedKnowledgeBase = await KbCommon.api(`/api/v1/admin/knowledge-bases/${knowledgeBaseId}`, { token: kbState.token });
        renderKbDetail();
    } catch (error) {
        if (error instanceof KbCommon.AuthExpiredError) {
            handleKbAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        kbElements.kbDetailBody.innerHTML = KbCommon.renderStateBlock({
            type: "error",
            title: "知识库详情加载失败",
            message: error.message || "未知错误",
            compact: true
        });
        setKbBanner(error.message || "知识库详情加载失败。", "error");
    }
}

function renderKbDetail() {
    const kb = kbState.selectedKnowledgeBase;
    const selectedSection = kb
        ? `
            <section class="summary-card">
                <span class="panel-kicker">当前知识库</span>
                <strong>${KbCommon.escapeHtml(kb.kbName || `知识库 #${kb.id}`)}</strong>
                <div class="chips">
                <span class="status-pill ${KbCommon.statusClass(kb.status)}">${KbCommon.escapeHtml(KbCommon.enumLabel("knowledgeBaseStatus", kb.status, "未知状态"))}</span>
                    <span class="status-pill">${KbCommon.escapeHtml(kb.kbCode || "AUTO")}</span>
                    <span class="status-pill">${KbCommon.formatNumber(kb.docCount || 0)} 个文档</span>
                </div>
                <p>${KbCommon.escapeHtml(kb.description || "暂未填写描述。")}</p>
                <div class="action-row" style="margin-top:14px;">
                    <a class="link-button" href="${buildDocumentLink(kb)}">打开文档管理</a>
                    <a class="link-button" href="${buildFailedParseDocumentLink(kb)}">打开失败文档</a>
                    <a class="link-button" href="/admin/dashboard">打开运营看板</a>
                </div>
            </section>

            ${renderKbStatsSection(kb)}
            ${renderKbFailureSection(kb)}
            ${renderKbImportSection(kb)}

            <section class="detail-card">
                <strong>编辑当前知识库</strong>
                <div class="form-grid">
                    <label class="field">
                        <span>编码</span>
                        <input type="text" value="${KbCommon.escapeAttr(kb.kbCode || "")}" disabled>
                    </label>
                    <label class="field">
                        <span>状态</span>
                        <input type="text" value="${KbCommon.escapeAttr(kb.status || "")}" disabled>
                    </label>
                    <label class="field full-span">
                        <span>名称</span>
                        <input id="editKbNameInput" type="text" value="${KbCommon.escapeAttr(kb.kbName || "")}">
                    </label>
                    <label class="field full-span">
                        <span>描述</span>
                        <textarea id="editKbDescriptionInput">${KbCommon.escapeHtml(kb.description || "")}</textarea>
                    </label>
                </div>
                <div class="footer-actions" style="margin-top:14px;">
                    <button id="saveKbButton" class="primary-button" type="button">保存修改</button>
                    <button id="toggleKbStatusButton" class="ghost-button" type="button">${kb.status === "ENABLED" ? "停用" : "启用"}</button>
                    <a class="link-button" href="${buildDocumentLink(kb)}">管理文档</a>
                </div>
            </section>
        `
        : `
            ${KbCommon.renderStateBlock({
                title: "暂未选择知识库",
                message: "可以创建一个新的知识库，或从左侧列表选择一条记录进行编辑。",
                compact: true
            })}
        `;

    kbElements.kbDetailBody.innerHTML = `
        ${selectedSection}
        <section class="detail-card">
            <strong>创建新知识库</strong>
            <div class="form-grid">
                <label class="field">
                    <span>自定义编码（可选）</span>
                    <input id="createKbCodeInput" type="text" placeholder="KB_SUPPORT_FAQ">
                </label>
                <label class="field">
                    <span>名称</span>
                    <input id="createKbNameInput" type="text" placeholder="客服知识库">
                </label>
                <label class="field full-span">
                    <span>描述</span>
                    <textarea id="createKbDescriptionInput" placeholder="描述这个知识库的用途与覆盖范围。"></textarea>
                </label>
            </div>
            <div class="footer-actions" style="margin-top:14px;">
                <button id="createKbButton" class="navy-button" type="button">创建知识库</button>
            </div>
        </section>
    `;

    bindKbDetailActions();
}

function renderKbStatsSection(kb) {
    const stats = kb.stats || defaultKbStats();
    const cards = [
        {
            title: "文档总量",
            value: stats.totalDocuments,
            tone: "info",
            caption: `${KbCommon.formatNumber(stats.enabledDocuments)} 启用 / ${KbCommon.formatNumber(stats.disabledDocuments)} 停用`
        },
        {
            title: "解析成功",
            value: stats.parseSuccessCount,
            tone: "success",
            caption: `${KbCommon.formatNumber(stats.parsePendingCount)} 待处理 / ${KbCommon.formatNumber(stats.parseProcessingCount)} 处理中`
        },
        {
            title: "解析失败",
            value: stats.parseFailedCount,
            tone: stats.parseFailedCount > 0 ? "danger" : "neutral",
            caption: `${KbCommon.formatNumber(stats.failedDocumentCount)} 个文档待处理`
        },
        {
            title: "索引成功",
            value: stats.indexSuccessCount,
            tone: "success",
            caption: `${KbCommon.formatNumber(stats.indexPendingCount)} 待处理 / ${KbCommon.formatNumber(stats.indexProcessingCount)} 处理中`
        },
        {
            title: "索引失败",
            value: stats.indexFailedCount,
            tone: stats.indexFailedCount > 0 ? "danger" : "neutral",
            caption: `${KbCommon.formatNumber(stats.openDeadLetterCount)} 条未关闭死信记录`
        },
        {
            title: "知识分块",
            value: stats.totalChunks,
            tone: "navy",
            caption: "可直接参与召回的内容块"
        }
    ];

    return `
        <section class="detail-card">
            <div class="panel-subheader">
                <div>
                    <strong>运行态快照</strong>
                    <div class="meta-line">展示当前知识库的文档吞吐、解析健康度和检索就绪情况。</div>
                </div>
                <div class="chips">
                    <span class="status-pill">失败文档：${KbCommon.formatNumber(stats.failedDocumentCount)}</span>
                    <span class="status-pill">死信记录：${KbCommon.formatNumber(stats.openDeadLetterCount)}</span>
                </div>
            </div>
            <div class="stat-grid">
                ${cards.map((card) => `
                    <article class="stat-card ${card.tone ? `tone-${card.tone}` : ""}">
                        <span class="stat-label">${KbCommon.escapeHtml(card.title)}</span>
                        <strong class="stat-value">${KbCommon.formatNumber(card.value || 0)}</strong>
                        <div class="stat-caption">${KbCommon.escapeHtml(card.caption)}</div>
                    </article>
                `).join("")}
            </div>
        </section>
    `;
}

function renderKbFailureSection(kb) {
    const stats = kb.stats || defaultKbStats();
    const failedDocuments = kb.failedDocuments || [];
    const topFailureReasons = kb.topFailureReasons || [];

    if (!failedDocuments.length) {
        return `
            <section class="detail-card">
                <div class="panel-subheader">
                    <div>
                        <strong>失败洞察</strong>
                        <div class="meta-line">把解析链路失败转换成更适合演示的治理视图。</div>
                    </div>
                    <div class="chips">
                        <span class="status-pill">失败文档 ${KbCommon.formatNumber(stats.failedDocumentCount)}</span>
                    </div>
                </div>
                ${KbCommon.renderStateBlock({
                    type: "success",
                    title: "当前知识库暂无失败任务",
                    message: "现有文档都在顺利完成解析与索引，尚未记录失败信息。",
                    compact: true
                })}
            </section>
        `;
    }

    const maxReasonCount = Math.max(...topFailureReasons.map((item) => Number(item.documentCount || 0)), 1);
    return `
        <section class="detail-card danger-panel">
            <div class="panel-subheader">
                <div>
                    <strong>失败洞察</strong>
                    <div class="meta-line">可视化主要阻塞原因，并继续钻取到具体需要治理的失败文档。</div>
                </div>
                <div class="chips">
                    <span class="status-pill status-danger">${KbCommon.formatNumber(stats.parseFailedCount)} 个解析失败</span>
                    <span class="status-pill status-danger">${KbCommon.formatNumber(stats.indexFailedCount)} 个索引失败</span>
                    <span class="status-pill">${KbCommon.formatNumber(stats.openDeadLetterCount)} 条死信</span>
                </div>
            </div>
            <div class="insight-grid">
                <article class="detail-card">
                    <strong>高频失败原因</strong>
                    <div class="section-stack compact-stack">
                        ${topFailureReasons.map((item) => {
                            const ratio = Math.max(12, Math.round((Number(item.documentCount || 0) / maxReasonCount) * 100));
                            return `
                                <div class="reason-item">
                                    <div class="reason-head">
                                        <div>
                                            <div class="reason-title">${KbCommon.escapeHtml(item.reason || "暂无失败原因")}</div>
                                        <div class="tiny-line">${KbCommon.escapeHtml(KbCommon.enumLabel("taskType", item.taskType, "解析"))} · 示例文档 ${KbCommon.escapeHtml(item.sampleDocumentName || "未知文档")}</div>
                                        </div>
                                        <span class="status-pill">${KbCommon.formatNumber(item.documentCount || 0)} 个文档</span>
                                    </div>
                                    <div class="reason-bar">
                                        <div class="reason-fill" style="width:${ratio}%;"></div>
                                    </div>
                                </div>
                            `;
                        }).join("")}
                    </div>
                </article>

                <article class="detail-card">
                    <strong>失败文档</strong>
                    <div class="section-stack compact-stack">
                        ${failedDocuments.map((item) => `
                            <div class="failure-doc-card">
                                <div class="panel-subheader">
                                    <div>
                                        <strong>${KbCommon.escapeHtml(item.docName || `文档 #${item.documentId}`)}</strong>
                                        <div class="tiny-line">${KbCommon.escapeHtml(item.docCode || "AUTO")} · 更新时间 ${KbCommon.escapeHtml(KbCommon.formatDateTime(item.updatedAt))}</div>
                                    </div>
                                    <div class="chips">
                                        <span class="status-pill ${KbCommon.statusClass(item.parseStatus)}">${KbCommon.escapeHtml(KbCommon.enumLabel("knowledgeBaseStatus", item.parseStatus, "未知状态"))}</span>
                                        <span class="status-pill ${KbCommon.statusClass(item.indexStatus)}">${KbCommon.escapeHtml(KbCommon.enumLabel("knowledgeBaseStatus", item.indexStatus, "未知状态"))}</span>
                                    </div>
                                </div>
                                <p class="failure-copy">${KbCommon.escapeHtml(item.errorMessage || "暂无失败原因。")}</p>
                                <div class="chips">
                                    <span class="status-pill">${KbCommon.escapeHtml(KbCommon.enumLabel("taskType", item.latestTaskType, "解析"))}</span>
                                    <span class="status-pill">重试 ${KbCommon.formatNumber(item.retryCount || 0)}</span>
                                    <span class="status-pill">${KbCommon.formatNumber(item.deadLetterCount || 0)} 条死信记录</span>
                                    <span class="status-pill">${KbCommon.formatNumber(item.chunkCount || 0)} 个分块</span>
                                </div>
                                <div class="action-row" style="margin-top:12px;">
                                    <a class="link-button" href="${buildDocumentDetailLink(kb, item)}">打开文档</a>
                                    <a class="link-button" href="${buildParseTaskLink(item)}">打开解析任务</a>
                                    ${Number(item.deadLetterCount || 0) > 0 ? `<a class="link-button" href="${buildDeadLetterLink(item)}">打开死信治理</a>` : ""}
                                </div>
                            </div>
                        `).join("")}
                    </div>
                </article>
            </div>
        </section>
    `;
}

function renderKbImportSection(kb) {
    const selectedFileNames = kbState.importFiles.map((file) => file.name);
    const finishedCount = kbState.importResults.filter((item) => item.status === "SUCCESS" || item.status === "FAILED").length;
    const successCount = kbState.importResults.filter((item) => item.status === "SUCCESS").length;
    const failedCount = kbState.importResults.filter((item) => item.status === "FAILED").length;

    return `
        <section class="detail-card">
            <div class="panel-subheader">
                <div>
                    <strong>批量导入资料</strong>
                    <div class="meta-line">选择多个文件顺序上传，并立即查看哪些文档已经进入解析链路。</div>
                </div>
                <div class="chips">
                    <span class="status-pill">${KbCommon.formatNumber(selectedFileNames.length)} 个待上传</span>
                    <span class="status-pill">${KbCommon.formatNumber(successCount)} 个成功</span>
                    <span class="status-pill">${KbCommon.formatNumber(failedCount)} 个失败</span>
                </div>
            </div>
            <div class="form-grid">
                <label class="field">
                    <span>目标知识库</span>
                    <input type="text" value="${KbCommon.escapeAttr(kb.kbName || `知识库 #${kb.id}`)}" disabled>
                </label>
                <label class="field">
                    <span>已选文件</span>
                    <input id="batchImportFilesInput" type="file" multiple ${kbState.importRunning ? "disabled" : ""}>
                </label>
            </div>
            <div class="field-note" style="margin-top:10px;">
                建议优先导入 txt、md、csv、json、yaml、yml、log 等更适合解析的文件。即使是不支持的文件也会上传成功，但解析 Worker 会将其标记为失败。
            </div>
            <div class="footer-actions" style="margin-top:14px;">
                <button id="runBatchImportButton" class="navy-button" type="button" ${kbState.importRunning ? "disabled" : ""}>${kbState.importRunning ? "导入中..." : "上传所选文件"}</button>
                <button id="clearImportQueueButton" class="ghost-button" type="button" ${kbState.importRunning || (!kbState.importFiles.length && !kbState.importResults.length) ? "disabled" : ""}>清空队列</button>
                <a class="link-button" href="${buildDocumentLink(kb)}">打开文档管理</a>
            </div>
            <div class="section-stack compact-stack" style="margin-top:14px;">
                ${selectedFileNames.length ? `
                    <div class="detail-card import-card">
                        <strong>待上传文件</strong>
                        <div class="import-file-list">
                            ${selectedFileNames.map((name) => `<span class="tag">${KbCommon.escapeHtml(name)}</span>`).join("")}
                        </div>
                    </div>
                ` : ""}
                ${kbState.importResults.length ? `
                    <div class="detail-card import-card">
                        <div class="panel-subheader">
                            <div>
                                <strong>导入进度</strong>
                                <div class="tiny-line">已处理 ${KbCommon.formatNumber(finishedCount)} / ${KbCommon.formatNumber(kbState.importResults.length)} 个文件</div>
                            </div>
                            <div class="chips">
                                <span class="status-pill">${KbCommon.formatNumber(successCount)} 个成功</span>
                                <span class="status-pill">${KbCommon.formatNumber(failedCount)} 个失败</span>
                            </div>
                        </div>
                        <div class="section-stack compact-stack" style="margin-top:12px;">
                            ${kbState.importResults.map((item) => `
                                <div class="import-result-item">
                                    <div class="panel-subheader">
                                        <div>
                                            <strong>${KbCommon.escapeHtml(item.fileName)}</strong>
                                            <div class="tiny-line">${KbCommon.escapeHtml(item.message || defaultImportMessage(item))}</div>
                                        </div>
                                        <span class="status-pill ${KbCommon.statusClass(importResultStatusToVisualStatus(item.status))}">${KbCommon.escapeHtml(KbCommon.enumLabel("importStatus", item.status, item.status || "待上传"))}</span>
                                    </div>
                                    <div class="action-row" style="margin-top:10px;">
                                        ${item.documentId ? `<a class="link-button" href="${buildImportedDocumentLink(kb, item.documentId)}">打开文档</a>` : ""}
                                    </div>
                                </div>
                            `).join("")}
                        </div>
                    </div>
                ` : ""}
            </div>
        </section>
    `;
}

function bindKbDetailActions() {
    bindKbIfPresent("createKbButton", async () => {
        const kbCode = document.getElementById("createKbCodeInput")?.value.trim();
        const kbName = document.getElementById("createKbNameInput")?.value.trim();
        const description = document.getElementById("createKbDescriptionInput")?.value.trim();
        if (!kbName) {
            setKbBanner("知识库名称不能为空。", "error");
            return;
        }
        try {
            const created = await KbCommon.api("/api/v1/admin/knowledge-bases", {
                token: kbState.token,
                method: "POST",
                body: JSON.stringify({
                    kbCode: kbCode || null,
                    kbName,
                    description
                })
            });
            kbState.selectedKnowledgeBaseId = created.id;
            resetKbImportState();
            setKbBanner("知识库创建成功。", "success", 1400);
            await loadKbConsole(false);
            await loadKbDetail(created.id, true);
        } catch (error) {
            if (error instanceof KbCommon.AuthExpiredError) {
                handleKbAuthFailure(error, "登录已过期，请重新登录。");
                return;
            }
            setKbBanner(error.message || "创建知识库失败。", "error");
        }
    });

    bindKbIfPresent("saveKbButton", async () => {
        if (!kbState.selectedKnowledgeBaseId) {
            return;
        }
        const kbName = document.getElementById("editKbNameInput")?.value.trim();
        const description = document.getElementById("editKbDescriptionInput")?.value.trim();
        if (!kbName) {
            setKbBanner("知识库名称不能为空。", "error");
            return;
        }
        try {
            await KbCommon.api(`/api/v1/admin/knowledge-bases/${kbState.selectedKnowledgeBaseId}`, {
                token: kbState.token,
                method: "PUT",
                body: JSON.stringify({ kbName, description })
            });
            setKbBanner("知识库已更新。", "success", 1400);
            await loadKbConsole(true);
        } catch (error) {
            if (error instanceof KbCommon.AuthExpiredError) {
                handleKbAuthFailure(error, "登录已过期，请重新登录。");
                return;
            }
            setKbBanner(error.message || "更新知识库失败。", "error");
        }
    });

    bindKbIfPresent("toggleKbStatusButton", async () => {
        if (!kbState.selectedKnowledgeBase) {
            return;
        }
        const nextStatus = kbState.selectedKnowledgeBase.status === "ENABLED" ? "DISABLED" : "ENABLED";
        try {
            await KbCommon.api(`/api/v1/admin/knowledge-bases/${kbState.selectedKnowledgeBaseId}/status`, {
                token: kbState.token,
                method: "PUT",
                body: JSON.stringify({ status: nextStatus })
            });
            setKbBanner(`知识库状态已切换为 ${nextStatus}。`, "success", 1400);
            await loadKbConsole(true);
        } catch (error) {
            if (error instanceof KbCommon.AuthExpiredError) {
                handleKbAuthFailure(error, "登录已过期，请重新登录。");
                return;
            }
            setKbBanner(error.message || "切换知识库状态失败。", "error");
        }
    });

    const batchImportFilesInput = document.getElementById("batchImportFilesInput");
    if (batchImportFilesInput) {
        batchImportFilesInput.addEventListener("change", () => {
            kbState.importFiles = Array.from(batchImportFilesInput.files || []);
            renderKbDetail();
        });
    }

    bindKbIfPresent("clearImportQueueButton", () => {
        resetKbImportState();
        renderKbDetail();
    });

    bindKbIfPresent("runBatchImportButton", async () => {
        await runBatchImport();
    });
}

async function runBatchImport() {
    if (!kbState.selectedKnowledgeBase) {
        setKbBanner("请先选择一个知识库。", "error");
        return;
    }
    if (!kbState.importFiles.length) {
        setKbBanner("请选择一个或多个待导入文件。", "error");
        return;
    }

    kbState.importRunning = true;
    kbState.importResults = kbState.importFiles.map((file) => ({
        fileName: file.name,
        status: "QUEUED",
        message: "等待上传"
    }));
    renderKbDetail();

    try {
        for (let index = 0; index < kbState.importFiles.length; index += 1) {
            const file = kbState.importFiles[index];
            kbState.importResults[index] = {
                ...kbState.importResults[index],
                status: "UPLOADING",
                message: "正在上传并创建解析任务"
            };
            renderKbDetail();

            try {
                const uploaded = await uploadKbDocument(kbState.selectedKnowledgeBase.id, file);
                kbState.importResults[index] = {
                    fileName: file.name,
                    status: "SUCCESS",
                    message: `文档 #${uploaded.id} 上传成功，解析状态为 ${KbCommon.enumLabel("knowledgeBaseStatus", uploaded.parseStatus, "待处理")}。`,
                    documentId: uploaded.id
                };
            } catch (error) {
                if (error instanceof KbCommon.AuthExpiredError) {
                    throw error;
                }
                kbState.importResults[index] = {
                    fileName: file.name,
                    status: "FAILED",
                    message: error.message || "上传失败"
                };
            }
            renderKbDetail();
        }

        const successCount = kbState.importResults.filter((item) => item.status === "SUCCESS").length;
        const failedCount = kbState.importResults.filter((item) => item.status === "FAILED").length;
        kbState.importRunning = false;
        kbState.importFiles = [];
        renderKbDetail();
        setKbBanner(`批量导入完成：${successCount} 个成功，${failedCount} 个失败。`, failedCount ? "info" : "success");
        await loadKbConsole(true);
    } catch (error) {
        kbState.importRunning = false;
        renderKbDetail();
        if (error instanceof KbCommon.AuthExpiredError) {
            handleKbAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setKbBanner(error.message || "批量导入失败。", "error");
    }
}

async function uploadKbDocument(knowledgeBaseId, file) {
    const formData = new FormData();
    formData.append("knowledgeBaseId", String(knowledgeBaseId));
    formData.append("file", file);

    const response = await fetch("/api/v1/admin/documents/upload", {
        method: "POST",
        headers: {
            Authorization: `Bearer ${kbState.token}`
        },
        body: formData
    });

    let payload = null;
    try {
        payload = await response.json();
    } catch (error) {
        payload = null;
    }

    if (response.status === 401 || payload?.code === 40101) {
        throw new KbCommon.AuthExpiredError(payload?.message || "登录已过期");
    }
    if (!response.ok || !payload || payload.code !== 0) {
        throw new Error(payload?.message || `上传失败，状态码 ${response.status}`);
    }
    return payload.data;
}

function bindKbIfPresent(id, handler) {
    const element = document.getElementById(id);
    if (element) {
        element.addEventListener("click", handler);
    }
}

function buildDocumentLink(kb) {
    const params = new URLSearchParams({
        knowledgeBaseId: String(kb.id),
        source: "knowledge-bases"
    });
    return `/admin/documents?${params.toString()}`;
}

function buildFailedParseDocumentLink(kb) {
    const params = new URLSearchParams({
        knowledgeBaseId: String(kb.id),
        parseStatus: "FAILED",
        source: "knowledge-bases"
    });
    return `/admin/documents?${params.toString()}`;
}

function buildDocumentDetailLink(kb, item) {
    const params = new URLSearchParams({
        knowledgeBaseId: String(kb.id),
        documentId: String(item.documentId),
        source: "knowledge-bases"
    });
    return `/admin/documents?${params.toString()}`;
}

function buildImportedDocumentLink(kb, documentId) {
    const params = new URLSearchParams({
        knowledgeBaseId: String(kb.id),
        documentId: String(documentId),
        source: "knowledge-bases"
    });
    return `/admin/documents?${params.toString()}`;
}

function buildParseTaskLink(item) {
    const params = new URLSearchParams({
        documentId: String(item.documentId),
        source: "knowledge-bases"
    });
    if (item.latestTaskType) {
        params.set("taskType", item.latestTaskType);
    }
    return `/admin/parse-tasks?${params.toString()}`;
}

function buildDeadLetterLink(item) {
    const params = new URLSearchParams({
        documentId: String(item.documentId),
        source: "knowledge-bases"
    });
    if (item.latestTaskId) {
        params.set("taskId", String(item.latestTaskId));
    }
    if (item.latestTaskType) {
        params.set("taskType", item.latestTaskType);
    }
    return `/admin/dead-letters?${params.toString()}`;
}

function defaultKbStats() {
    return {
        totalDocuments: 0,
        enabledDocuments: 0,
        disabledDocuments: 0,
        parsePendingCount: 0,
        parseProcessingCount: 0,
        parseSuccessCount: 0,
        parseFailedCount: 0,
        indexPendingCount: 0,
        indexProcessingCount: 0,
        indexSuccessCount: 0,
        indexFailedCount: 0,
        failedDocumentCount: 0,
        totalChunks: 0,
        openDeadLetterCount: 0
    };
}

function defaultImportMessage(item) {
    if (item.status === "SUCCESS") {
        return "上传成功";
    }
    if (item.status === "FAILED") {
        return "上传失败";
    }
    if (item.status === "UPLOADING") {
        return "上传中";
    }
    return "等待上传";
}

function importResultStatusToVisualStatus(status) {
    if (status === "SUCCESS") {
        return "SUCCESS";
    }
    if (status === "FAILED") {
        return "FAILED";
    }
    if (status === "UPLOADING") {
        return "PROCESSING";
    }
    return "PENDING";
}

function resetKbImportState() {
    kbState.importFiles = [];
    kbState.importResults = [];
    kbState.importRunning = false;
}

function applyKbFilterInputs() {
    kbElements.keywordFilter.value = kbState.filters.keyword;
    kbElements.statusFilter.value = kbState.filters.status;
}

function readKbFiltersFromInputs() {
    kbState.filters.keyword = kbElements.keywordFilter.value.trim();
    kbState.filters.status = kbElements.statusFilter.value;
}

function syncKbQuery() {
    KbCommon.updateQuery({
        pageNo: kbState.pageNo,
        knowledgeBaseId: kbState.selectedKnowledgeBaseId,
        keyword: kbState.filters.keyword,
        status: kbState.filters.status
    });
}

function handleKbAuthFailure(error, message) {
    console.error(error);
    logoutKbConsole();
    setKbBanner(message, "error");
}

function setKbBanner(message, type = "info", autoHideMs = 0) {
    window.clearTimeout(setKbBanner.timer);
    if (!message) {
        clearKbBanner();
        return;
    }
    kbElements.statusBanner.hidden = false;
    kbElements.statusBanner.className = `status-banner ${type}`;
    kbElements.statusBanner.textContent = message;
    if (autoHideMs > 0) {
        setKbBanner.timer = window.setTimeout(clearKbBanner, autoHideMs);
    }
}

function clearKbBanner() {
    window.clearTimeout(setKbBanner.timer);
    kbElements.statusBanner.hidden = true;
    kbElements.statusBanner.className = "status-banner";
    kbElements.statusBanner.textContent = "";
}



