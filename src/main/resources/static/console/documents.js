const DocumentCommon = window.KnowFlowConsoleCommon;

const documentState = {
    token: DocumentCommon.readToken(),
    query: DocumentCommon.parseQuery(),
    user: null,
    knowledgeBases: [],
    documents: [],
    selectedDocumentId: null,
    selectedDocument: null,
    selectedDocumentIds: [],
    preview: null,
    previewDocumentId: null,
    previewLoading: false,
    pageNo: 1,
    pageSize: 10,
    total: 0,
    lastRefreshAt: null,
    filters: {
        knowledgeBaseId: "",
        status: "",
        parseStatus: ""
    }
};

const documentElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    cacheDocumentElements();
    initDocumentStateFromQuery();
    bindDocumentEvents();
    if (documentState.token) {
        await restoreDocumentSession();
        return;
    }
    showDocumentLoggedOutState();
});

function cacheDocumentElements() {
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
        "parseStatusFilter",
        "searchButton",
        "resetButton",
        "batchActionBar",
        "documentPageMeta",
        "documentTable",
        "documentPaginationText",
        "prevPageButton",
        "nextPageButton",
        "documentDetailBody"
    ].forEach((id) => {
        documentElements[id] = document.getElementById(id);
    });
}

function initDocumentStateFromQuery() {
    const query = documentState.query;
    documentState.pageNo = Number(query.pageNo || 1);
    documentState.selectedDocumentId = query.documentId ? Number(query.documentId) : null;
    documentState.filters.knowledgeBaseId = query.knowledgeBaseId || "";
    documentState.filters.status = query.status || "";
    documentState.filters.parseStatus = query.parseStatus || "";
}

function bindDocumentEvents() {
    documentElements.loginForm.addEventListener("submit", handleDocumentLogin);
    documentElements.refreshButton.addEventListener("click", () => loadDocumentConsole(true));
    documentElements.logoutButton.addEventListener("click", logoutDocumentConsole);
    documentElements.searchButton.addEventListener("click", async () => {
        readDocumentFiltersFromInputs();
        documentState.pageNo = 1;
        documentState.selectedDocumentIds = [];
        await loadDocumentConsole(false);
    });
    documentElements.resetButton.addEventListener("click", async () => {
        documentState.filters = {
            knowledgeBaseId: "",
            status: "",
            parseStatus: ""
        };
        documentState.pageNo = 1;
        documentState.selectedDocumentId = null;
        documentState.selectedDocument = null;
        documentState.selectedDocumentIds = [];
        resetPreviewState();
        applyDocumentFilterInputs();
        syncDocumentQuery();
        await loadDocumentConsole(false);
    });
    documentElements.prevPageButton.addEventListener("click", async () => {
        if (documentState.pageNo <= 1) {
            return;
        }
        documentState.pageNo -= 1;
        await loadDocumentConsole(true);
    });
    documentElements.nextPageButton.addEventListener("click", async () => {
        const totalPage = Math.max(1, Math.ceil(documentState.total / documentState.pageSize));
        if (documentState.pageNo >= totalPage) {
            return;
        }
        documentState.pageNo += 1;
        await loadDocumentConsole(true);
    });
}

async function restoreDocumentSession() {
    try {
        const [user, knowledgeBasePage] = await Promise.all([
            DocumentCommon.api("/api/v1/auth/me", { token: documentState.token }),
            DocumentCommon.api("/api/v1/admin/knowledge-bases?pageNo=1&pageSize=200", { token: documentState.token })
        ]);
        documentState.user = user;
        documentState.knowledgeBases = knowledgeBasePage.records || [];
        applyDocumentFilterInputs();
        showDocumentLoggedInState();
        await loadDocumentConsole(true);
    } catch (error) {
        handleDocumentAuthFailure(error, "登录已过期，请重新登录。");
    }
}

async function handleDocumentLogin(event) {
    event.preventDefault();
    const username = documentElements.usernameInput.value.trim();
    const password = documentElements.passwordInput.value;
    if (!username || !password) {
        setDocumentBanner("请输入账号和密码。", "error");
        return;
    }

    documentElements.loginButton.disabled = true;
    setDocumentBanner("正在登录文档管理台...", "info");
    try {
        const data = await DocumentCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        documentState.token = data.token;
        DocumentCommon.saveToken(data.token);
        documentElements.passwordInput.value = "";
        await restoreDocumentSession();
        setDocumentBanner("文档管理台已就绪。", "success");
    } catch (error) {
        setDocumentBanner(error.message || "登录失败。", "error");
    } finally {
        documentElements.loginButton.disabled = false;
    }
}

function logoutDocumentConsole() {
    documentState.token = "";
    documentState.user = null;
    documentState.documents = [];
    documentState.selectedDocumentId = null;
    documentState.selectedDocument = null;
    documentState.selectedDocumentIds = [];
    documentState.total = 0;
    resetPreviewState();
    DocumentCommon.clearToken();
    DocumentCommon.updateQuery({
        pageNo: null,
        documentId: null,
        knowledgeBaseId: null,
        status: null,
        parseStatus: null
    });
    showDocumentLoggedOutState();
    setDocumentBanner("已退出文档管理台。", "success");
}

function showDocumentLoggedOutState() {
    documentElements.loginPanel.hidden = false;
    documentElements.workspace.hidden = true;
    documentElements.sessionName.textContent = "未登录";
    documentElements.sessionMeta.textContent = "请先登录后管理文档导入。";
}

function showDocumentLoggedInState() {
    documentElements.loginPanel.hidden = true;
    documentElements.workspace.hidden = false;
    documentElements.sessionName.textContent = documentState.user.realName || documentState.user.username;
    documentElements.sessionMeta.textContent = DocumentCommon.formatUserSessionMeta(documentState.user);
}


async function loadDocumentConsole(keepSelection) {
    if (!documentState.token) {
        showDocumentLoggedOutState();
        return;
    }

    renderDocumentListLoading();
    syncDocumentQuery();
    try {
        const pageData = await DocumentCommon.api(buildDocumentListUrl(), { token: documentState.token });
        documentState.documents = pageData.records || [];
        documentState.total = pageData.total || 0;
        documentState.pageNo = pageData.pageNo || documentState.pageNo;
        documentState.pageSize = pageData.pageSize || documentState.pageSize;
        documentState.lastRefreshAt = new Date();
        if (documentElements.lastRefreshText) {
            documentElements.lastRefreshText.textContent = DocumentCommon.formatDateTime(documentState.lastRefreshAt);
        }
        reconcileSelectedDocuments();
        renderDocumentBatchActionBar();
        renderDocumentList();

        const preferredId = resolvePreferredDocumentId(keepSelection);
        if (preferredId) {
            await loadDocumentDetail(preferredId, true);
        } else {
            documentState.selectedDocument = null;
            resetPreviewState();
            renderDocumentDetail();
        }
    } catch (error) {
        if (error instanceof DocumentCommon.AuthExpiredError) {
            handleDocumentAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        documentElements.documentTable.innerHTML = DocumentCommon.renderStateBlock({
            type: "error",
            title: "文档列表加载失败",
            message: error.message || "请稍后重试，或检查当前账号是否具备文档管理权限。"
        });
        renderDocumentBatchActionBar();
        setDocumentBanner(error.message || "文档列表加载失败。", "error");
    }
}

function buildDocumentListUrl() {
    const params = new URLSearchParams({
        pageNo: String(documentState.pageNo),
        pageSize: String(documentState.pageSize)
    });
    if (documentState.filters.knowledgeBaseId) {
        params.set("knowledgeBaseId", documentState.filters.knowledgeBaseId);
    }
    if (documentState.filters.status) {
        params.set("status", documentState.filters.status);
    }
    if (documentState.filters.parseStatus) {
        params.set("parseStatus", documentState.filters.parseStatus);
    }
    return `/api/v1/admin/documents?${params.toString()}`;
}

function renderDocumentListLoading() {
    documentElements.documentPageMeta.textContent = "正在加载文档清单...";
    documentElements.documentTable.innerHTML = DocumentCommon.renderStateBlock({
        type: "loading",
        title: "正在加载文档",
        message: "系统正在同步知识库文件、解析状态与索引状态。"
    });
    documentElements.documentPaginationText.textContent = "暂无分页信息";
    renderDocumentBatchActionBar();
}

function renderDocumentList() {
    const totalPage = Math.max(1, Math.ceil(documentState.total / documentState.pageSize));
    const allSelected = !!documentState.documents.length
        && documentState.documents.every((documentItem) => isDocumentSelected(documentItem.id));

    documentElements.documentPageMeta.textContent = `当前范围内共有 ${DocumentCommon.formatNumber(documentState.total)} 个文档`;
    documentElements.documentPaginationText.textContent = `第 ${documentState.pageNo} / ${totalPage} 页`;
    documentElements.prevPageButton.disabled = documentState.pageNo <= 1;
    documentElements.nextPageButton.disabled = documentState.pageNo >= totalPage;

    if (!documentState.documents.length) {
        documentElements.documentTable.innerHTML = DocumentCommon.renderStateBlock({
            title: "未找到文档",
            message: "可以在右侧面板上传第一份资料，也可以重置筛选后重新查询。"
        });
        return;
    }

    documentElements.documentTable.innerHTML = `
        <table class="data-table">
            <thead>
            <tr>
                <th>
                    <label class="checkbox-row">
                        <input id="selectAllDocumentsCheckbox" type="checkbox" ${allSelected ? "checked" : ""}>
                        <span>文档</span>
                    </label>
                </th>
                <th>所属知识库</th>
                <th>解析 / 索引</th>
                <th>分块数</th>
                <th>操作</th>
            </tr>
            </thead>
            <tbody>
            ${documentState.documents.map((documentItem) => `
                <tr class="${documentItem.id === documentState.selectedDocumentId ? "active-row" : ""}">
                    <td>
                        <div class="table-question">
                            <label class="checkbox-row">
                                <input type="checkbox" data-select-document-id="${documentItem.id}" ${isDocumentSelected(documentItem.id) ? "checked" : ""}>
                                <span>选择</span>
                            </label>
                            <strong>${DocumentCommon.escapeHtml(documentItem.docName || `文档 #${documentItem.id}`)}</strong>
                            <div class="meta-line">${DocumentCommon.escapeHtml(documentItem.docCode || "AUTO")}</div>
                        </div>
                    </td>
                    <td>
                        <div class="meta-line">${DocumentCommon.escapeHtml(resolveKnowledgeBaseName(documentItem.knowledgeBaseId))}</div>
                        <div class="tiny-line">${DocumentCommon.escapeHtml(DocumentCommon.formatDateTime(documentItem.updatedAt || documentItem.createdAt))}</div>
                    </td>
                    <td>
                        <div class="chips">
                            <span class="status-pill ${DocumentCommon.statusClass(documentItem.parseStatus)}">${DocumentCommon.escapeHtml(DocumentCommon.enumLabel("knowledgeBaseStatus", documentItem.parseStatus, "未知状态"))}</span>
                            <span class="status-pill ${DocumentCommon.statusClass(documentItem.indexStatus)}">${DocumentCommon.escapeHtml(DocumentCommon.enumLabel("knowledgeBaseStatus", documentItem.indexStatus, "未知状态"))}</span>
                        </div>
                    </td>
                    <td>${DocumentCommon.formatNumber(documentItem.chunkCount || 0)}</td>
                    <td><button class="link-button table-row-button" type="button" data-document-id="${documentItem.id}">查看</button></td>
                </tr>
            `).join("")}
            </tbody>
        </table>
    `;

    documentElements.documentTable.querySelectorAll("[data-document-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            await loadDocumentDetail(Number(button.dataset.documentId), false);
        });
    });

    documentElements.documentTable.querySelectorAll("[data-select-document-id]").forEach((checkbox) => {
        checkbox.addEventListener("change", () => {
            toggleDocumentSelection(Number(checkbox.dataset.selectDocumentId), checkbox.checked);
        });
    });

    const selectAllCheckbox = document.getElementById("selectAllDocumentsCheckbox");
    if (selectAllCheckbox) {
        selectAllCheckbox.addEventListener("change", () => {
            toggleSelectAllDocuments(selectAllCheckbox.checked);
        });
    }
}

function renderDocumentBatchActionBar() {
    const selectedCount = documentState.selectedDocumentIds.length;
    if (!selectedCount) {
        documentElements.batchActionBar.hidden = true;
        documentElements.batchActionBar.innerHTML = "";
        return;
    }

    documentElements.batchActionBar.hidden = false;
    documentElements.batchActionBar.innerHTML = `
        <strong>已选择 ${DocumentCommon.formatNumber(selectedCount)} 个文档</strong>
        <div class="action-row" style="margin-top:10px;">
            <button id="batchEnableButton" class="soft-button" type="button">批量启用</button>
            <button id="batchDisableButton" class="soft-button" type="button">批量停用</button>
            <button id="batchDeleteButton" class="ghost-button" type="button">批量删除</button>
        </div>
    `;

    bindDocumentIfPresent("batchEnableButton", async () => {
        await batchUpdateSelectedDocumentsStatus("ENABLED");
    });
    bindDocumentIfPresent("batchDisableButton", async () => {
        await batchUpdateSelectedDocumentsStatus("DISABLED");
    });
    bindDocumentIfPresent("batchDeleteButton", async () => {
        await batchDeleteSelectedDocuments();
    });
}

function resolvePreferredDocumentId(keepSelection) {
    if (keepSelection && documentState.selectedDocumentId) {
        return documentState.selectedDocumentId;
    }
    if (documentState.query.documentId) {
        return Number(documentState.query.documentId);
    }
    if (documentState.selectedDocumentId) {
        return documentState.selectedDocumentId;
    }
    return documentState.documents[0]?.id || null;
}

async function loadDocumentDetail(documentId, silent) {
    if (!documentId) {
        return;
    }
    documentState.selectedDocumentId = documentId;
    syncDocumentQuery();
    renderDocumentList();
    if (!silent) {
        documentElements.documentDetailBody.innerHTML = DocumentCommon.renderStateBlock({
            type: "loading",
            title: "正在加载文档详情",
            message: "正在读取文件元数据、解析任务和预览信息。"
        });
    }

    try {
        documentState.selectedDocument = await DocumentCommon.api(`/api/v1/admin/documents/${documentId}`, { token: documentState.token });
        resetPreviewState();
        renderDocumentDetail();
        void loadDocumentPreview(documentId, true);
    } catch (error) {
        if (error instanceof DocumentCommon.AuthExpiredError) {
            handleDocumentAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        documentElements.documentDetailBody.innerHTML = DocumentCommon.renderStateBlock({
            type: "error",
            title: "文档详情加载失败",
            message: error.message || "请稍后重试，或返回文档列表重新选择。"
        });
        setDocumentBanner(error.message || "文档详情加载失败。", "error");
    }
}

function renderDocumentDetail() {
    const documentItem = documentState.selectedDocument;
    const summarySection = documentItem
        ? `
            <section class="summary-card">
                <span class="panel-kicker">当前文档</span>
                <strong>${DocumentCommon.escapeHtml(documentItem.docName || `文档 #${documentItem.id}`)}</strong>
                <div class="chips">
                <span class="status-pill ${DocumentCommon.statusClass(documentItem.status)}">${DocumentCommon.escapeHtml(DocumentCommon.enumLabel("knowledgeBaseStatus", documentItem.status, "未知状态"))}</span>
                <span class="status-pill ${DocumentCommon.statusClass(documentItem.parseStatus)}">${DocumentCommon.escapeHtml(DocumentCommon.enumLabel("knowledgeBaseStatus", documentItem.parseStatus, "未知状态"))}</span>
                <span class="status-pill ${DocumentCommon.statusClass(documentItem.indexStatus)}">${DocumentCommon.escapeHtml(DocumentCommon.enumLabel("knowledgeBaseStatus", documentItem.indexStatus, "未知状态"))}</span>
                <span class="status-pill">${DocumentCommon.escapeHtml(documentItem.fileType || "未知类型")}</span>
                </div>
                <p>存储于 ${DocumentCommon.escapeHtml(documentItem.storageType || "未知存储")}，路径为 ${DocumentCommon.escapeHtml(documentItem.storagePath || "N/A")}。</p>
                <div class="action-row" style="margin-top:14px;">
                    <button id="downloadDocumentButton" class="navy-button" type="button">下载文件</button>
                    <button id="reloadPreviewButton" class="soft-button" type="button">重新加载预览</button>
                    <a class="link-button" href="${buildKnowledgeBaseLink(documentItem)}">打开知识库</a>
                    <a class="link-button" href="${buildParseTaskLink(documentItem)}">打开解析任务</a>
                    ${shouldShowFailureGovernanceLink(documentItem) ? `<a class="link-button" href="${buildDeadLetterLink(documentItem)}">打开死信治理</a>` : ""}
                </div>
            </section>

            <section class="detail-columns">
                <article class="detail-card">
                    <strong>存储元数据</strong>
                    <p>编码：${DocumentCommon.escapeHtml(documentItem.docCode || "AUTO")}</p>
                    <p>知识库：${DocumentCommon.escapeHtml(resolveKnowledgeBaseName(documentItem.knowledgeBaseId))}</p>
                    <p>文件大小：${DocumentCommon.escapeHtml(formatBytes(documentItem.fileSize))}</p>
                    <p>版本：${DocumentCommon.escapeHtml(documentItem.versionNo ?? 1)}</p>
                    <p>分块数：${DocumentCommon.formatNumber(documentItem.chunkCount || 0)}</p>
                </article>
                <article class="detail-card">
                    <strong>处理状态</strong>
                    <p>文档状态：${DocumentCommon.escapeHtml(DocumentCommon.enumLabel("knowledgeBaseStatus", documentItem.status, "未知状态"))}</p>
                    <p>解析状态：${DocumentCommon.escapeHtml(DocumentCommon.enumLabel("knowledgeBaseStatus", documentItem.parseStatus, "未知状态"))}</p>
                    <p>索引状态：${DocumentCommon.escapeHtml(DocumentCommon.enumLabel("knowledgeBaseStatus", documentItem.indexStatus, "未知状态"))}</p>
                    <p>更新时间：${DocumentCommon.escapeHtml(DocumentCommon.formatDateTime(documentItem.updatedAt || documentItem.createdAt))}</p>
                </article>
            </section>

            <section class="detail-card">
                <strong>文档预览</strong>
                ${renderDocumentPreviewBody()}
            </section>

            <section class="detail-card">
                <strong>文档操作</strong>
                <div class="form-grid">
                    <label class="field">
                        <span>状态</span>
                        <select id="documentStatusSelect">
                            <option value="ENABLED" ${documentItem.status === "ENABLED" ? "selected" : ""}>启用</option>
                            <option value="DISABLED" ${documentItem.status === "DISABLED" ? "selected" : ""}>停用</option>
                        </select>
                    </label>
                    <label class="field">
                        <span>存储类型</span>
                        <input type="text" value="${DocumentCommon.escapeAttr(documentItem.storageType || "")}" disabled>
                    </label>
                    <label class="field full-span">
                        <span>存储路径</span>
                        <textarea disabled>${DocumentCommon.escapeHtml(documentItem.storagePath || "")}</textarea>
                    </label>
                </div>
                <div class="footer-actions" style="margin-top:14px;">
                    <button id="saveDocumentStatusButton" class="primary-button" type="button">保存状态</button>
                    <a class="link-button" href="${buildParseTaskLink(documentItem)}">查看解析任务</a>
                    ${shouldShowFailureGovernanceLink(documentItem) ? `<a class="link-button" href="${buildDeadLetterLink(documentItem)}">查看死信治理</a>` : ""}
                    <button id="deleteDocumentButton" class="ghost-button" type="button">删除文档</button>
                </div>
            </section>
        `
        : DocumentCommon.renderStateBlock({
            title: "暂未选择文档",
            message: "选择一个文档后，这里会展示它的存储元数据和处理链路状态。"
        });

    documentElements.documentDetailBody.innerHTML = `
        ${summarySection}
        <section class="detail-card">
            <strong>上传新文档</strong>
            <div class="form-grid">
                <label class="field">
                    <span>目标知识库</span>
                    <select id="uploadKnowledgeBaseSelect">${buildKnowledgeBaseOptions(resolveUploadKnowledgeBaseId(), true)}</select>
                </label>
                <label class="field">
                    <span>文件</span>
                    <input id="uploadFileInput" type="file">
                </label>
            </div>
            <div class="footer-actions" style="margin-top:14px;">
                <button id="uploadDocumentButton" class="navy-button" type="button">上传并触发解析</button>
            </div>
        </section>
    `;

    bindDocumentDetailActions();
}

function renderDocumentPreviewBody() {
    if (!documentState.selectedDocument) {
        return DocumentCommon.renderStateBlock({
            title: "等待选择文档",
            message: "选择一个文档后，可以预览已存储内容。"
        });
    }
    if (documentState.previewLoading) {
        return DocumentCommon.renderStateBlock({
            type: "loading",
            title: "正在加载预览",
            message: "正在读取已存储的文档内容。"
        });
    }
    if (!documentState.preview || documentState.previewDocumentId !== documentState.selectedDocument.id) {
        return DocumentCommon.renderStateBlock({
            title: "预览尚未就绪",
            message: "文档详情加载完成后，预览会自动刷新。"
        });
    }
    if (!documentState.preview.previewable) {
        return DocumentCommon.renderStateBlock({
            title: "暂不支持预览",
            message: documentState.preview.message || "当前文件暂不支持预览。"
        });
    }

    const previewText = documentState.preview.previewText || "";
    const helperTags = [];
    if (documentState.preview.contentType) {
        helperTags.push(`<span class="status-pill">${DocumentCommon.escapeHtml(documentState.preview.contentType)}</span>`);
    }
    if (documentState.preview.truncated) {
        helperTags.push('<span class="status-pill status-warn">预览已截断</span>');
    }

    return `
        ${helperTags.length ? `<div class="chips" style="margin-top:12px;">${helperTags.join("")}</div>` : ""}
        <pre>${DocumentCommon.escapeHtml(previewText || "（空文件）")}</pre>
    `;
}

function bindDocumentDetailActions() {
    bindDocumentIfPresent("uploadDocumentButton", async () => {
        const knowledgeBaseId = document.getElementById("uploadKnowledgeBaseSelect")?.value;
        const file = document.getElementById("uploadFileInput")?.files?.[0];
        if (!knowledgeBaseId) {
            setDocumentBanner("请选择目标知识库。", "error");
            return;
        }
        if (!file) {
            setDocumentBanner("请选择要上传的文件。", "error");
            return;
        }
        try {
            const uploaded = await uploadDocument(Number(knowledgeBaseId), file);
            documentState.filters.knowledgeBaseId = String(knowledgeBaseId);
            documentState.selectedDocumentIds = [];
            setDocumentBanner("文档上传成功，已创建解析任务。", "success");
            await loadDocumentConsole(false);
            await loadDocumentDetail(uploaded.id, true);
        } catch (error) {
            if (error instanceof DocumentCommon.AuthExpiredError) {
                handleDocumentAuthFailure(error, "登录已过期，请重新登录。");
                return;
            }
            setDocumentBanner(error.message || "文档上传失败。", "error");
        }
    });

    bindDocumentIfPresent("saveDocumentStatusButton", async () => {
        if (!documentState.selectedDocumentId) {
            return;
        }
        const status = document.getElementById("documentStatusSelect")?.value;
        if (!status) {
            setDocumentBanner("请选择文档状态。", "error");
            return;
        }
        try {
            await DocumentCommon.api(`/api/v1/admin/documents/${documentState.selectedDocumentId}/status`, {
                token: documentState.token,
                method: "PUT",
                body: JSON.stringify({ status })
            });
            setDocumentBanner("文档状态已更新。", "success");
            await loadDocumentConsole(true);
            await loadDocumentDetail(documentState.selectedDocumentId, true);
        } catch (error) {
            if (error instanceof DocumentCommon.AuthExpiredError) {
                handleDocumentAuthFailure(error, "登录已过期，请重新登录。");
                return;
            }
            setDocumentBanner(error.message || "更新文档状态失败。", "error");
        }
    });

    bindDocumentIfPresent("deleteDocumentButton", async () => {
        if (!documentState.selectedDocumentId) {
            return;
        }
        if (!window.confirm("确认删除当前文档及其已生成的分块数据吗？")) {
            return;
        }
        try {
            await DocumentCommon.api(`/api/v1/admin/documents/${documentState.selectedDocumentId}`, {
                token: documentState.token,
                method: "DELETE"
            });
            documentState.selectedDocumentIds = documentState.selectedDocumentIds.filter((id) => id !== documentState.selectedDocumentId);
            documentState.selectedDocumentId = null;
            documentState.selectedDocument = null;
            resetPreviewState();
            setDocumentBanner("文档已删除。", "success");
            await loadDocumentConsole(false);
        } catch (error) {
            if (error instanceof DocumentCommon.AuthExpiredError) {
                handleDocumentAuthFailure(error, "登录已过期，请重新登录。");
                return;
            }
            setDocumentBanner(error.message || "删除文档失败。", "error");
        }
    });

    bindDocumentIfPresent("reloadPreviewButton", async () => {
        if (!documentState.selectedDocumentId) {
            return;
        }
        await loadDocumentPreview(documentState.selectedDocumentId, false);
    });

    bindDocumentIfPresent("downloadDocumentButton", async () => {
        if (!documentState.selectedDocumentId) {
            return;
        }
        await downloadDocumentFile(documentState.selectedDocumentId);
    });
}

async function uploadDocument(knowledgeBaseId, file) {
    const formData = new FormData();
    formData.append("knowledgeBaseId", String(knowledgeBaseId));
    formData.append("file", file);

    const response = await fetch("/api/v1/admin/documents/upload", {
        method: "POST",
        headers: {
            Authorization: `Bearer ${documentState.token}`
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
        throw new DocumentCommon.AuthExpiredError(payload?.message || "登录已过期");
    }
    if (!response.ok || !payload || payload.code !== 0) {
        throw new Error(payload?.message || `上传失败，状态码 ${response.status}`);
    }
    return payload.data;
}

async function loadDocumentPreview(documentId, silent) {
    if (!documentId) {
        return;
    }
    documentState.previewLoading = true;
    documentState.preview = null;
    documentState.previewDocumentId = documentId;
    if (!silent) {
        renderDocumentDetail();
    } else {
        renderDocumentDetail();
    }

    try {
        const preview = await DocumentCommon.api(`/api/v1/admin/documents/${documentId}/preview`, {
            token: documentState.token
        });
        if (documentState.selectedDocumentId !== documentId) {
            return;
        }
        documentState.preview = preview;
        documentState.previewDocumentId = documentId;
        documentState.previewLoading = false;
        renderDocumentDetail();
    } catch (error) {
        if (error instanceof DocumentCommon.AuthExpiredError) {
            handleDocumentAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        if (documentState.selectedDocumentId !== documentId) {
            return;
        }
        documentState.previewLoading = false;
        documentState.preview = {
            documentId,
            previewable: false,
            message: error.message || "预览加载失败。"
        };
        documentState.previewDocumentId = documentId;
        renderDocumentDetail();
    }
}

async function downloadDocumentFile(documentId) {
    try {
        const response = await fetch(`/api/v1/admin/documents/${documentId}/download`, {
            method: "GET",
            headers: {
                Authorization: `Bearer ${documentState.token}`
            }
        });
        if (response.status === 401) {
            throw new DocumentCommon.AuthExpiredError("登录已过期");
        }
        if (!response.ok) {
            throw new Error(`下载失败，状态码 ${response.status}`);
        }

        const blob = await response.blob();
        const fileName = parseDownloadFileName(response.headers.get("Content-Disposition"))
            || documentState.selectedDocument?.docName
            || `document-${documentId}`;
        const blobUrl = window.URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = blobUrl;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(blobUrl);
        setDocumentBanner(`已开始下载：${fileName}。`, "success");
    } catch (error) {
        if (error instanceof DocumentCommon.AuthExpiredError) {
            handleDocumentAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setDocumentBanner(error.message || "下载文档失败。", "error");
    }
}

async function batchUpdateSelectedDocumentsStatus(status) {
    if (!documentState.selectedDocumentIds.length) {
        setDocumentBanner("请先选择至少一个文档。", "error");
        return;
    }
    try {
        const result = await DocumentCommon.api("/api/v1/admin/documents/batch/status", {
            token: documentState.token,
            method: "PUT",
            body: JSON.stringify({
                documentIds: documentState.selectedDocumentIds,
                status
            })
        });
        setDocumentBanner(`已更新 ${result.affectedCount || documentState.selectedDocumentIds.length} 个文档为 ${status} 状态。`, "success");
        await loadDocumentConsole(true);
        if (documentState.selectedDocumentId) {
            await loadDocumentDetail(documentState.selectedDocumentId, true);
        }
    } catch (error) {
        if (error instanceof DocumentCommon.AuthExpiredError) {
            handleDocumentAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setDocumentBanner(error.message || "批量更新状态失败。", "error");
    }
}

async function batchDeleteSelectedDocuments() {
    if (!documentState.selectedDocumentIds.length) {
        setDocumentBanner("请先选择至少一个文档。", "error");
        return;
    }
    if (!window.confirm(`确认删除选中的 ${documentState.selectedDocumentIds.length} 个文档及其解析产物吗？`)) {
        return;
    }
    const deletingSelectedDocument = documentState.selectedDocumentId
        && documentState.selectedDocumentIds.includes(documentState.selectedDocumentId);
    try {
        const result = await DocumentCommon.api("/api/v1/admin/documents/batch/delete", {
            token: documentState.token,
            method: "POST",
            body: JSON.stringify({
                documentIds: documentState.selectedDocumentIds
            })
        });
        documentState.selectedDocumentIds = [];
        if (deletingSelectedDocument) {
            documentState.selectedDocumentId = null;
            documentState.selectedDocument = null;
            resetPreviewState();
        }
        setDocumentBanner(`已删除 ${result.affectedCount || 0} 个文档。`, "success");
        await loadDocumentConsole(false);
    } catch (error) {
        if (error instanceof DocumentCommon.AuthExpiredError) {
            handleDocumentAuthFailure(error, "登录已过期，请重新登录。");
            return;
        }
        setDocumentBanner(error.message || "批量删除失败。", "error");
    }
}

function toggleDocumentSelection(documentId, checked) {
    if (checked) {
        if (!documentState.selectedDocumentIds.includes(documentId)) {
            documentState.selectedDocumentIds.push(documentId);
        }
    } else {
        documentState.selectedDocumentIds = documentState.selectedDocumentIds.filter((id) => id !== documentId);
    }
    renderDocumentBatchActionBar();
    renderDocumentList();
}

function toggleSelectAllDocuments(checked) {
    if (checked) {
        documentState.selectedDocumentIds = documentState.documents.map((documentItem) => documentItem.id);
    } else {
        documentState.selectedDocumentIds = [];
    }
    renderDocumentBatchActionBar();
    renderDocumentList();
}

function reconcileSelectedDocuments() {
    const visibleIds = new Set(documentState.documents.map((documentItem) => documentItem.id));
    documentState.selectedDocumentIds = documentState.selectedDocumentIds.filter((documentId) => visibleIds.has(documentId));
}

function isDocumentSelected(documentId) {
    return documentState.selectedDocumentIds.includes(documentId);
}

function resetPreviewState() {
    documentState.preview = null;
    documentState.previewDocumentId = null;
    documentState.previewLoading = false;
}

function bindDocumentIfPresent(id, handler) {
    const element = document.getElementById(id);
    if (element) {
        element.addEventListener("click", handler);
    }
}

function resolveKnowledgeBaseName(knowledgeBaseId) {
    const item = documentState.knowledgeBases.find((kb) => String(kb.id) === String(knowledgeBaseId));
    return item?.kbName || `知识库 #${knowledgeBaseId || "N/A"}`;
}

function buildKnowledgeBaseOptions(selectedId, includePlaceholder) {
    const options = documentState.knowledgeBases.map((kb) => `
        <option value="${kb.id}" ${String(selectedId || "") === String(kb.id) ? "selected" : ""}>
            ${DocumentCommon.escapeHtml(kb.kbName)} (${DocumentCommon.escapeHtml(DocumentCommon.enumLabel("knowledgeBaseStatus", kb.status, "未知状态"))})
        </option>
    `).join("");
    if (includePlaceholder) {
        return `<option value="">请选择知识库</option>${options}`;
    }
    return options;
}

function resolveUploadKnowledgeBaseId() {
    if (documentState.filters.knowledgeBaseId) {
        return documentState.filters.knowledgeBaseId;
    }
    if (documentState.selectedDocument?.knowledgeBaseId) {
        return documentState.selectedDocument.knowledgeBaseId;
    }
    return documentState.knowledgeBases[0]?.id || "";
}

function applyDocumentFilterInputs() {
    documentElements.knowledgeBaseFilter.innerHTML = buildKnowledgeBaseOptions(documentState.filters.knowledgeBaseId, true);
    documentElements.statusFilter.value = documentState.filters.status;
    documentElements.parseStatusFilter.value = documentState.filters.parseStatus;
}

function readDocumentFiltersFromInputs() {
    documentState.filters.knowledgeBaseId = documentElements.knowledgeBaseFilter.value;
    documentState.filters.status = documentElements.statusFilter.value;
    documentState.filters.parseStatus = documentElements.parseStatusFilter.value;
}

function buildKnowledgeBaseLink(documentItem) {
    const params = new URLSearchParams({
        knowledgeBaseId: String(documentItem.knowledgeBaseId),
        source: "documents",
        documentId: String(documentItem.id)
    });
    return `/admin/knowledge-bases?${params.toString()}`;
}

function buildParseTaskLink(documentItem) {
    const params = new URLSearchParams({
        documentId: String(documentItem.id),
        knowledgeBaseId: String(documentItem.knowledgeBaseId),
        source: "documents"
    });
    return `/admin/parse-tasks?${params.toString()}`;
}

function buildDeadLetterLink(documentItem) {
    const params = new URLSearchParams({
        documentId: String(documentItem.id),
        source: "documents"
    });
    const taskType = inferFailedTaskType(documentItem);
    if (taskType) {
        params.set("taskType", taskType);
    }
    return `/admin/dead-letters?${params.toString()}`;
}

function inferFailedTaskType(documentItem) {
    if (!documentItem) {
        return "";
    }
    if (documentItem.indexStatus === "FAILED") {
        return "INDEX_VECTOR";
    }
    if (documentItem.parseStatus === "FAILED") {
        return "PARSE";
    }
    return "";
}

function shouldShowFailureGovernanceLink(documentItem) {
    return documentItem?.parseStatus === "FAILED" || documentItem?.indexStatus === "FAILED";
}

function parseDownloadFileName(contentDisposition) {
    if (!contentDisposition) {
        return "";
    }
    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match?.[1]) {
        return decodeURIComponent(utf8Match[1]);
    }
    const asciiMatch = contentDisposition.match(/filename="?([^"]+)"?/i);
    return asciiMatch?.[1] || "";
}

function formatBytes(value) {
    const bytes = Number(value || 0);
    if (bytes <= 0) {
        return "0 B";
    }
    const units = ["B", "KB", "MB", "GB"];
    let size = bytes;
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
        size /= 1024;
        unitIndex += 1;
    }
    return `${size.toFixed(size >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function syncDocumentQuery() {
    DocumentCommon.updateQuery({
        pageNo: documentState.pageNo,
        documentId: documentState.selectedDocumentId,
        knowledgeBaseId: documentState.filters.knowledgeBaseId,
        status: documentState.filters.status,
        parseStatus: documentState.filters.parseStatus
    });
}

function handleDocumentAuthFailure(error, message) {
    console.error(error);
    logoutDocumentConsole();
    setDocumentBanner(message, "error");
}

function setDocumentBanner(message, type = "info") {
    documentElements.statusBanner.hidden = false;
    documentElements.statusBanner.className = `status-banner ${type}`;
    documentElements.statusBanner.textContent = message;
}



