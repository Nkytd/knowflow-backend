window.KnowFlowConsoleCommon = (() => {
    const SESSION_KEY = "knowflow.console.token";
    const ENUM_LABELS = {
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
            NOT_CREATED: "未创建",
            UNKNOWN: "未知状态"
        },
        slaStatus: {
            ON_TRACK: "正常",
            AT_RISK: "临近超时",
            MET: "已达成",
            BREACHED: "已超时",
            PAUSED: "已暂停",
            UNKNOWN: "未知 SLA"
        },
        draftStatus: {
            PENDING_REVIEW: "待审核",
            APPROVED: "已通过",
            REJECTED: "已驳回",
            PUBLISHED: "已发布",
            NOT_CREATED: "未创建",
            UNKNOWN: "未知状态"
        },
        knowledgeBaseStatus: {
            ENABLED: "启用",
            DISABLED: "停用",
            PENDING: "待处理",
            PROCESSING: "处理中",
            SUCCESS: "成功",
            FAILED: "失败",
            READY: "就绪",
            UNKNOWN: "未知状态"
        },
        taskType: {
            PARSE: "解析",
            INDEX_VECTOR: "向量索引",
            UNKNOWN: "未知任务类型"
        },
        replayStatus: {
            READY: "可回放",
            PENDING: "待处理",
            PROCESSING: "回放中",
            SUCCESS: "成功",
            FAILED: "失败",
            MANUAL_REQUIRED: "待人工处理",
            AUTO_REPLAYED: "已自动回放",
            MANUAL_REPLAYED: "已人工回放",
            UNKNOWN: "未知状态"
        },
        importStatus: {
            UPLOADING: "上传中",
            SUCCESS: "成功",
            FAILED: "失败",
            PENDING: "待上传"
        },
        priority: {
            LOW: "低",
            MEDIUM: "中",
            HIGH: "高",
            URGENT: "紧急",
            NORMAL: "普通",
            UNKNOWN: "未知优先级"
        },
        draftType: {
            FAQ: "常见问答",
            ARTICLE: "知识文章"
        },
        ticketSourceType: {
            QA_HANDOFF: "问答转工单",
            MANUAL_CREATE: "人工创建",
            MANUAL: "人工创建",
            UNKNOWN: "未知来源"
        },
        ticketCommentType: {
            USER_REPLY: "用户回复",
            AGENT_REPLY: "客服回复",
            INTERNAL_NOTE: "内部备注",
            SOLUTION: "解决方案"
        },
        ticketActionType: {
            CREATE: "创建工单",
            ACCEPT: "接单",
            ASSIGN: "改派",
            USER_REPLY: "用户补充",
            AGENT_REPLY: "客服回复",
            INTERNAL_NOTE: "内部备注",
            RESOLVE: "标记已解决",
            CLOSE: "关闭工单",
            SLA_BREACHED: "SLA 超时"
        },
        ticketFlowStatus: {
            START: "开始",
            UNCHANGED: "状态未变化",
            PENDING: "待处理",
            PROCESSING: "处理中",
            WAITING_USER: "待用户反馈",
            RESOLVED: "已解决",
            CLOSED: "已关闭",
            BREACHED: "已超时",
            UNKNOWN: "未知状态"
        },
        auditBizType: {
            TICKET: "工单",
            KNOWLEDGE_DRAFT: "知识草稿",
            DOCUMENT: "文档",
            PARSE_TASK: "解析任务",
            KNOWLEDGE_BASE: "知识库",
            QA_MESSAGE: "问答记录",
            QA_SESSION: "问答会话"
        },
        pageSource: {
            dashboard: "运营看板",
            assistant: "问答工作台",
            workbench: "问答工作台",
            tickets: "工单管理页",
            ticket: "工单管理页",
            "qa-records": "问答记录页",
            draft: "知识草稿页",
            "knowledge-drafts": "知识草稿页",
            "audit-log": "审计日志页",
            "audit-logs": "审计日志页",
            documents: "文档管理页",
            "parse-tasks": "解析任务页",
            "dead-letters": "死信治理页",
            "ops-health": "运维健康页",
            "knowledge-bases": "知识库管理页",
            "retrieval-evaluations": "检索评估页"
        },
        roleCode: {
            SUPER_ADMIN: "平台超级管理员",
            TENANT_ADMIN: "租户管理员",
            KNOWLEDGE_OPERATOR: "知识运营",
            SUPPORT_AGENT: "支持客服",
            END_USER: "终端用户"
        }
    };
    const CONSOLE_MODULES = [
        {
            key: "dashboard",
            label: "运营看板",
            href: "/admin/dashboard",
            pageKeys: ["dashboard"]
        },
        {
            key: "assistant",
            label: "智能问答入口",
            href: "/assistant",
            pageKeys: ["assistant"]
        },
        {
            key: "knowledge",
            label: "知识运营",
            href: "/admin/knowledge-bases",
            pageKeys: ["knowledge-bases", "documents", "parse-tasks", "retrieval-evaluations"]
        },
        {
            key: "collaboration",
            label: "工单协同",
            href: "/admin/qa-records",
            pageKeys: ["qa-records", "tickets", "knowledge-drafts"]
        },
        {
            key: "governance",
            label: "系统治理",
            href: "/admin/ops-health",
            pageKeys: ["ops-health", "dead-letters", "audit-logs"]
        }
    ];
    const CONSOLE_PAGES = {
        dashboard: {
            key: "dashboard",
            moduleKey: "dashboard",
            pageLabel: "运营看板",
            href: "/admin/dashboard"
        },
        assistant: {
            key: "assistant",
            moduleKey: "assistant",
            pageLabel: "智能问答入口",
            href: "/assistant"
        },
        profile: {
            key: "profile",
            moduleKey: "assistant",
            pageLabel: "用户中心",
            href: "/admin/profile"
        },
        "knowledge-bases": {
            key: "knowledge-bases",
            moduleKey: "knowledge",
            pageLabel: "知识库管理",
            href: "/admin/knowledge-bases"
        },
        documents: {
            key: "documents",
            moduleKey: "knowledge",
            pageLabel: "文档管理",
            href: "/admin/documents"
        },
        "parse-tasks": {
            key: "parse-tasks",
            moduleKey: "knowledge",
            pageLabel: "解析任务",
            href: "/admin/parse-tasks"
        },
        "qa-records": {
            key: "qa-records",
            moduleKey: "collaboration",
            pageLabel: "问答记录",
            href: "/admin/qa-records"
        },
        tickets: {
            key: "tickets",
            moduleKey: "collaboration",
            pageLabel: "工单管理",
            href: "/admin/tickets"
        },
        "knowledge-drafts": {
            key: "knowledge-drafts",
            moduleKey: "collaboration",
            pageLabel: "知识草稿",
            href: "/admin/knowledge-drafts"
        },
        "retrieval-evaluations": {
            key: "retrieval-evaluations",
            moduleKey: "knowledge",
            pageLabel: "检索评估",
            href: "/admin/retrieval-evaluations"
        },
        "ops-health": {
            key: "ops-health",
            moduleKey: "governance",
            pageLabel: "运维健康",
            href: "/admin/ops-health"
        },
        "dead-letters": {
            key: "dead-letters",
            moduleKey: "governance",
            pageLabel: "死信治理",
            href: "/admin/dead-letters"
        },
        "audit-logs": {
            key: "audit-logs",
            moduleKey: "governance",
            pageLabel: "审计日志",
            href: "/admin/audit-logs"
        }
    };
    const PAGE_KEY_ALIASES = {
        workbench: "assistant",
        draft: "knowledge-drafts",
        ticket: "tickets",
        "audit-log": "audit-logs"
    };

    class AuthExpiredError extends Error {
        constructor(message) {
            super(message);
            this.name = "AuthExpiredError";
        }
    }

    document.addEventListener("DOMContentLoaded", () => {
        renderConsoleShells();
        renderSourceReturnBanner();
        enhanceHeroPanels();
        decorateProfileLinks();
    });

    async function api(url, options = {}) {
        const {
            token = "",
            attachAuth = true,
            method = "GET",
            headers = {},
            body
        } = options;

        const requestHeaders = { ...headers };
        if (body && !requestHeaders["Content-Type"]) {
            requestHeaders["Content-Type"] = "application/json";
        }
        if (attachAuth && token) {
            requestHeaders.Authorization = `Bearer ${token}`;
        }

        const response = await fetch(url, {
            method,
            headers: requestHeaders,
            body
        });

        let payload = null;
        try {
            payload = await response.json();
        } catch (error) {
            payload = null;
        }

        if (response.status === 401 || payload?.code === 40101) {
            throw new AuthExpiredError(payload?.message || "登录状态已过期");
        }
        if (!response.ok || !payload || payload.code !== 0) {
            throw new Error(payload?.message || `请求失败，状态码 ${response.status}`);
        }
        return payload.data;
    }

    function readToken() {
        return window.localStorage.getItem(SESSION_KEY) || "";
    }

    function saveToken(token) {
        window.localStorage.setItem(SESSION_KEY, token);
    }

    function clearToken() {
        window.localStorage.removeItem(SESSION_KEY);
    }

    function parseQuery() {
        return Object.fromEntries(new URLSearchParams(window.location.search).entries());
    }

    function updateQuery(nextParams) {
        const searchParams = new URLSearchParams(window.location.search);
        Object.entries(nextParams).forEach(([key, value]) => {
            if (value === undefined || value === null || value === "") {
                searchParams.delete(key);
                return;
            }
            searchParams.set(key, String(value));
        });
        const nextQuery = searchParams.toString();
        const nextUrl = nextQuery ? `${window.location.pathname}?${nextQuery}` : window.location.pathname;
        window.history.replaceState({}, "", nextUrl);
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

    function truncateText(value, maxLength) {
        const text = String(value || "").trim();
        if (!text) {
            return "";
        }
        if (text.length <= maxLength) {
            return text;
        }
        return `${text.slice(0, maxLength)}...`;
    }

    function enumLabel(category, value, fallback = "") {
        const raw = String(value ?? "").trim();
        if (!raw) {
            return fallback;
        }
        const labels = ENUM_LABELS[category];
        if (!labels) {
            return fallback || raw;
        }
        return labels[raw] || labels[raw.toUpperCase()] || fallback || raw;
    }

    function statusClass(status) {
        const normalized = String(status || "").toUpperCase();
        if (normalized === "SUCCESS" || normalized === "RESOLVED" || normalized === "CLOSED" || normalized === "PUBLISHED" || normalized === "ON_TRACK" || normalized === "MET") {
            return "status-success";
        }
        if (normalized === "NO_HIT" || normalized === "REJECTED" || normalized === "FAILED" || normalized === "MANUAL_REQUIRED" || normalized === "BREACHED") {
            return "status-danger";
        }
        if (normalized === "PENDING" || normalized === "PENDING_REVIEW" || normalized === "WAITING_USER" || normalized === "READY" || normalized === "AT_RISK") {
            return "status-warn";
        }
        if (normalized === "PROCESSING" || normalized === "APPROVED" || normalized === "AUTO_REPLAYED" || normalized === "MANUAL_REPLAYED" || normalized === "PAUSED") {
            return "status-info";
        }
        return "";
    }

    function hasRole(user, ...roles) {
        if (!user?.roleCodes?.length) {
            return false;
        }
        return roles.some((role) => user.roleCodes.includes(role));
    }

    function normalizePageKey(pageKey) {
        const raw = String(pageKey || "").trim();
        return PAGE_KEY_ALIASES[raw] || raw;
    }

    function getConsolePageConfig(pageKey) {
        const normalized = normalizePageKey(pageKey);
        return CONSOLE_PAGES[normalized] || null;
    }

    function getConsoleModuleConfig(moduleKey) {
        return CONSOLE_MODULES.find((module) => module.key === moduleKey) || null;
    }

    function getConsoleModulePages(moduleKey) {
        return (getConsoleModuleConfig(moduleKey)?.pageKeys || [])
            .map((pageKey) => getConsolePageConfig(pageKey))
            .filter(Boolean);
    }

    function getModuleMenuPages(moduleConfig, currentPageKey) {
        if (!moduleConfig) {
            return [];
        }
        return getConsoleModulePages(moduleConfig.key)
            .filter((page) => page.key !== currentPageKey);
    }

    function formatRoleList(roleCodes, fallback = "普通成员") {
        const labels = (roleCodes || [])
            .map((roleCode) => enumLabel("roleCode", roleCode, roleCode || fallback))
            .filter(Boolean);
        return labels.length ? labels.join(" / ") : fallback;
    }

    function formatUserSessionMeta(user, options = {}) {
        if (!user) {
            return "";
        }
        const {
            includeTenant = true,
            tenantLabel = "租户",
            accountLabel = "账号",
            roleLabel = "角色",
            delimiter = " | "
        } = options;
        const parts = [`${accountLabel}：${user.username || "未知账号"}`];
        if (includeTenant && user.tenantId !== undefined && user.tenantId !== null && user.tenantId !== "") {
            parts.push(`${tenantLabel}：${user.tenantId}`);
        }
        parts.push(`${roleLabel}：${formatRoleList(user.roleCodes)}`);
        return parts.join(delimiter);
    }

    function renderStateBlock(options = {}) {
        const {
            type = "empty",
            title = "暂无数据",
            message = "当前没有可展示的内容。",
            actionHref = "",
            actionText = "",
            compact = false
        } = options;
        const className = type === "loading" ? "loading-state" : "empty-state";
        const safeHref = normalizeInternalReturnUrl(actionHref);
        return `
            <div class="${className} ${type ? `state-block-${escapeAttr(type)}` : ""} ${compact ? "state-block-compact" : ""}">
                <h4>${escapeHtml(title)}</h4>
                ${message ? `<p>${escapeHtml(message)}</p>` : ""}
                ${safeHref && actionText ? `<a class="ghost-button state-block-action" href="${escapeAttr(safeHref)}">${escapeHtml(actionText)}</a>` : ""}
            </div>
        `;
    }


    function decorateProfileLinks() {
        const from = encodeURIComponent(`${window.location.pathname}${window.location.search || ""}`);
        document.querySelectorAll("[data-profile-link]").forEach((link) => {
            link.href = `/admin/profile?from=${from}`;
        });
    }

    function renderSourceReturnBanner() {
        const query = parseQuery();
        const returnUrl = normalizeInternalReturnUrl(query.returnUrl || query.from || "");
        if (!returnUrl || isCurrentInternalUrl(returnUrl)) {
            return;
        }
        const shell = document.querySelector(".console-shell");
        const header = shell?.querySelector("[data-console-shell]");
        if (!shell || !header || shell.querySelector("[data-global-return-banner]")) {
            return;
        }
        if (shell.querySelector("#contextBanner")) {
            return;
        }
        const sourceLabel = resolveReturnSourceLabel(query.source, returnUrl);
        const banner = document.createElement("section");
        banner.className = "context-banner shell-return-banner";
        banner.dataset.globalReturnBanner = "true";
        banner.innerHTML = `
            <span class="context-source">${escapeHtml(sourceLabel ? `来自${sourceLabel}` : "跨页面返回")}</span>
            <span>当前页面由深链跳转进入，处理完成后可以回到来源页。</span>
            <a class="ghost-button context-return-button" href="${escapeAttr(returnUrl)}">返回${escapeHtml(sourceLabel || "来源页")}</a>
        `;
        header.insertAdjacentElement("afterend", banner);
    }

    function normalizeInternalReturnUrl(rawUrl) {
        const text = String(rawUrl || "").trim();
        if (!text || (!text.startsWith("/") && !text.startsWith(window.location.origin))) {
            return "";
        }
        try {
            const url = new URL(text, window.location.origin);
            if (url.origin !== window.location.origin) {
                return "";
            }
            return `${url.pathname}${url.search}${url.hash}`;
        } catch (error) {
            return "";
        }
    }

    function isCurrentInternalUrl(url) {
        const current = `${window.location.pathname}${window.location.search || ""}${window.location.hash || ""}`;
        return current === url;
    }

    function resolveReturnSourceLabel(source, returnUrl) {
        const sourceLabel = enumLabel("pageSource", source, "");
        if (sourceLabel) {
            return sourceLabel;
        }
        try {
            const returnPath = new URL(returnUrl, window.location.origin).pathname;
            const pageConfig = Object.values(CONSOLE_PAGES)
                .find((page) => page.href === returnPath);
            return pageConfig?.pageLabel || "";
        } catch (error) {
            return "";
        }
    }

    function enhanceHeroPanels() {
        document.querySelectorAll(".hero-panel").forEach((heroPanel) => {
            if (heroPanel.dataset.heroEnhanced === "true") {
                return;
            }
            const highlight = heroPanel.querySelector(".hero-highlight");
            if (!highlight) {
                return;
            }
            const content = heroPanel.firstElementChild;
            if (!content) {
                return;
            }
            const actions = document.createElement("div");
            actions.className = "hero-actions";
            const toggle = document.createElement("button");
            toggle.className = "ghost-button hero-help-toggle";
            toggle.type = "button";
            toggle.setAttribute("aria-expanded", "false");
            toggle.textContent = "展开说明";
            actions.appendChild(toggle);
            content.appendChild(actions);

            const setOpen = (open) => {
                heroPanel.classList.toggle("hero-help-open", open);
                highlight.hidden = !open;
                toggle.setAttribute("aria-expanded", String(open));
                toggle.textContent = open ? "收起说明" : "展开说明";
            };

            toggle.addEventListener("click", () => {
                setOpen(!heroPanel.classList.contains("hero-help-open"));
            });
            setOpen(false);
            heroPanel.dataset.heroEnhanced = "true";
        });
    }
    function renderConsoleShells() {
        document.querySelectorAll("[data-console-shell]").forEach((header) => {
            renderConsoleShell(header);
        });
    }

    function renderConsoleShell(header) {
        if (!header || header.dataset.shellRendered === "true") {
            return;
        }
        const legacyHeroHelp = readLegacyHeroHelp(header);
        const pageConfig = getConsolePageConfig(header.dataset.pageKey);
        if (!pageConfig) {
            return;
        }
        const moduleConfig = getConsoleModuleConfig(pageConfig.moduleKey);
        const moduleMenuPages = getModuleMenuPages(moduleConfig, pageConfig.key);
        const kicker = header.dataset.kicker || moduleConfig?.label || "KnowFlow";
        const title = header.dataset.title || pageConfig.pageLabel;
        const description = header.dataset.description || "";
        const inlineDescription = header.dataset.inlineDescription || "";
        const sessionHint = header.dataset.sessionHint || "请先登录后继续使用控制台。";
        const titleHelpTitle = header.dataset.titleHelpTitle || legacyHeroHelp.title || "";
        const titleHelpDescription = header.dataset.titleHelpDescription || legacyHeroHelp.description || description || "";
        const titleHelpHighlightTitle = header.dataset.titleHelpHighlightTitle || legacyHeroHelp.highlightTitle || "";
        const titleHelpHighlight = header.dataset.titleHelpHighlight || legacyHeroHelp.highlight || "";
        const titleHelpKicker = header.dataset.titleHelpKicker || legacyHeroHelp.kicker || kicker;
        const hasTitleHelp = titleHelpTitle || titleHelpDescription || titleHelpHighlight;

        header.innerHTML = `
            <div class="shell-unified-bar">
                <div class="brand-copy shell-unified-brand ${hasTitleHelp ? "has-title-help" : ""}">
                    <span class="brand-kicker">${escapeHtml(kicker)}</span>
                    <h1 class="brand-title" tabindex="0">${escapeHtml(title)}</h1>
                    ${inlineDescription ? `<p>${escapeHtml(inlineDescription)}</p>` : ""}
                    ${hasTitleHelp ? `
                        <div class="shell-title-help" role="tooltip">
                            <span class="panel-kicker">${escapeHtml(titleHelpKicker)}</span>
                            ${titleHelpTitle ? `<h2>${escapeHtml(titleHelpTitle)}</h2>` : ""}
                            ${titleHelpDescription ? `<p>${escapeHtml(titleHelpDescription)}</p>` : ""}
                            ${titleHelpHighlight ? `
                                <div class="shell-title-help-highlight">
                                    ${titleHelpHighlightTitle ? `<strong>${escapeHtml(titleHelpHighlightTitle)}</strong>` : ""}
                                    <p>${escapeHtml(titleHelpHighlight)}</p>
                                </div>
                            ` : ""}
                        </div>
                    ` : ""}
                </div>
                <nav class="shell-unified-nav" aria-label="全局导航">
                    <div class="console-nav shell-primary-nav">
                        ${CONSOLE_MODULES.map((module) => `
                            <a
                                class="nav-link shell-primary-link ${module.key === moduleConfig?.key ? "active" : ""}"
                                href="${escapeAttr(module.href)}"
                            >${escapeHtml(module.label)}</a>
                        `).join("")}
                        <button
                            class="ghost-button shell-menu-toggle"
                            type="button"
                            data-shell-menu-toggle
                            aria-expanded="false"
                        >全部页面</button>
                    </div>
                    <div class="shell-secondary-nav" aria-label="模块内页面">
                        ${moduleMenuPages.length ? `
                            <div class="shell-module-menu">
                                <button
                                    class="ghost-button shell-module-toggle"
                                    type="button"
                                    data-module-menu-toggle
                                    aria-expanded="false"
                                >
                                    <span>${escapeHtml(moduleConfig?.label || "模块页面")}</span>
                                    <small>${escapeHtml(pageConfig.pageLabel)}</small>
                                </button>
                                <div class="shell-module-popover" data-module-menu hidden>
                                    <div class="shell-module-current">
                                        <span>当前页面</span>
                                        <strong>${escapeHtml(pageConfig.pageLabel)}</strong>
                                    </div>
                                    <div class="shell-module-links">
                                        ${moduleMenuPages.map((page) => `
                                            <a class="shell-module-link" href="${escapeAttr(page.href)}">${escapeHtml(page.pageLabel)}</a>
                                        `).join("")}
                                    </div>
                                </div>
                            </div>
                        ` : ""}
                    </div>
                </nav>
                <div class="shell-nav-actions">
                    <div class="shell-user-menu">
                        <button
                            class="ghost-button shell-user-toggle"
                            type="button"
                            data-user-menu-toggle
                            aria-expanded="false"
                        >
                            <span class="shell-user-avatar">KF</span>
                            <span class="shell-user-toggle-text">
                                <strong id="sessionName" class="session-name">未登录</strong>
                            </span>
                        </button>
                        <div class="shell-user-popover" data-user-menu hidden>
                            <div class="shell-user-menu-list">
                                <div class="shell-user-menu-row shell-user-menu-info shell-user-name-row">
                                    <strong class="shell-popover-user-name">未登录</strong>
                                    <small id="sessionMeta">${escapeHtml(sessionHint)}</small>
                                </div>
                                <a class="shell-user-menu-row shell-user-menu-link" href="/admin/profile" data-profile-link>
                                    <span>用户中心</span>
                                    <small>查看资料与修改密码</small>
                                </a>
                                <button id="refreshButton" class="shell-user-menu-row shell-user-menu-action" type="button">
                                    <span>刷新</span>
                                    <small>重新加载当前页面数据</small>
                                </button>
                                <button id="logoutButton" class="shell-user-menu-row shell-user-menu-action is-danger" type="button">
                                    <span>退出登录</span>
                                    <small>清除本地登录状态</small>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="shell-page-menu" data-shell-menu hidden>
                    <div class="shell-page-menu-head">
                        <strong>全部页面</strong>
                        <button class="ghost-button shell-page-menu-close" type="button" data-shell-menu-close>收起</button>
                    </div>
                    <div class="shell-page-menu-grid">
                        ${CONSOLE_MODULES.map((module) => `
                            <section class="shell-menu-group">
                                <div class="shell-menu-group-title">${escapeHtml(module.label)}</div>
                                <div class="shell-menu-group-links">
                                    ${getConsoleModulePages(module.key).map((page) => `
                                        <a
                                            class="shell-page-link ${page.key === pageConfig.key ? "active" : ""}"
                                            href="${escapeAttr(page.href)}"
                                        >${escapeHtml(page.pageLabel)}</a>
                                    `).join("")}
                                </div>
                            </section>
                        `).join("")}
                    </div>
                </div>
            </div>
        `;
        header.dataset.shellRendered = "true";
        legacyHeroHelp.element?.remove();
        bindConsoleShellMenu(header);
        bindConsoleModuleMenu(header);
        bindConsoleUserMenu(header);
    }

    function readLegacyHeroHelp(header) {
        const shell = header.closest(".console-shell") || document;
        const heroPanel = Array.from(shell.children || [])
            .find((child) => child.classList?.contains("hero-panel")) || shell.querySelector(".hero-panel");
        if (!heroPanel) {
            return {};
        }
        const highlight = heroPanel.querySelector(".hero-highlight");
        const descriptionParagraph = Array.from(heroPanel.querySelectorAll("p"))
            .find((paragraph) => !highlight?.contains(paragraph));
        return {
            element: heroPanel,
            kicker: readElementText(heroPanel.querySelector(".panel-kicker")),
            title: readElementText(heroPanel.querySelector("h2")),
            description: readElementText(descriptionParagraph),
            highlightTitle: readElementText(highlight?.querySelector("strong")),
            highlight: readElementText(highlight?.querySelector("p"))
        };
    }

    function readElementText(element) {
        return element?.textContent?.trim() || "";
    }


    function bindConsoleUserMenu(header) {
        const toggle = header.querySelector("[data-user-menu-toggle]");
        const menu = header.querySelector("[data-user-menu]");
        const userName = header.querySelector("#sessionName");
        const popoverUserName = header.querySelector(".shell-popover-user-name");
        if (!toggle || !menu) {
            return;
        }

        const syncPopoverName = () => {
            if (popoverUserName && userName) {
                popoverUserName.textContent = userName.textContent || "未登录";
            }
        };
        const setOpen = (open) => {
            if (open) {
                closeConsolePopovers(header, "user");
            }
            syncPopoverName();
            menu.hidden = !open;
            toggle.setAttribute("aria-expanded", String(open));
            header.classList.toggle("shell-user-menu-open", open);
        };

        toggle.addEventListener("click", (event) => {
            event.stopPropagation();
            setOpen(menu.hidden);
        });

        document.addEventListener("click", (event) => {
            if (!header.contains(event.target)) {
                setOpen(false);
            }
        });

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                setOpen(false);
            }
        });
    }
    function bindConsoleShellMenu(header) {
        bindConsoleDropdown(header, {
            toggleSelector: "[data-shell-menu-toggle]",
            closeSelector: "[data-shell-menu-close]",
            menuSelector: "[data-shell-menu]",
            openClass: "shell-menu-open",
            kind: "page"
        });
    }

    function bindConsoleModuleMenu(header) {
        bindConsoleDropdown(header, {
            toggleSelector: "[data-module-menu-toggle]",
            menuSelector: "[data-module-menu]",
            openClass: "shell-module-menu-open",
            kind: "module"
        });
    }

    function bindConsoleDropdown(header, config) {
        const toggle = header.querySelector(config.toggleSelector);
        const closeButton = header.querySelector(config.closeSelector);
        const menu = header.querySelector(config.menuSelector);
        if (!toggle || !menu) {
            return;
        }

        const setOpen = (open) => {
            if (open) {
                closeConsolePopovers(header, config.kind);
            }
            menu.hidden = !open;
            toggle.setAttribute("aria-expanded", String(open));
            header.classList.toggle(config.openClass, open);
        };

        toggle.addEventListener("click", (event) => {
            event.stopPropagation();
            setOpen(menu.hidden);
        });

        closeButton?.addEventListener("click", () => {
            setOpen(false);
        });

        document.addEventListener("click", (event) => {
            if (!header.contains(event.target)) {
                setOpen(false);
            }
        });

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                setOpen(false);
            }
        });
    }

    function closeConsolePopovers(header, except) {
        if (except !== "page") {
            const pageMenu = header.querySelector("[data-shell-menu]");
            const pageToggle = header.querySelector("[data-shell-menu-toggle]");
            if (pageMenu && pageToggle) {
                pageMenu.hidden = true;
                pageToggle.setAttribute("aria-expanded", "false");
                header.classList.remove("shell-menu-open");
            }
        }
        if (except !== "module") {
            const moduleMenu = header.querySelector("[data-module-menu]");
            const moduleToggle = header.querySelector("[data-module-menu-toggle]");
            if (moduleMenu && moduleToggle) {
                moduleMenu.hidden = true;
                moduleToggle.setAttribute("aria-expanded", "false");
                header.classList.remove("shell-module-menu-open");
            }
        }
        if (except !== "user") {
            const userMenu = header.querySelector("[data-user-menu]");
            const userToggle = header.querySelector("[data-user-menu-toggle]");
            if (userMenu && userToggle) {
                userMenu.hidden = true;
                userToggle.setAttribute("aria-expanded", "false");
                header.classList.remove("shell-user-menu-open");
            }
        }
    }

    return {
        SESSION_KEY,
        AuthExpiredError,
        api,
        readToken,
        saveToken,
        clearToken,
        parseQuery,
        updateQuery,
        formatNumber,
        formatPercent,
        formatDateTime,
        escapeHtml,
        escapeAttr,
        truncateText,
        enumLabel,
        statusClass,
        hasRole,
        formatRoleList,
        formatUserSessionMeta,
        getConsolePageConfig,
        getConsoleModuleConfig,
        getConsoleModulePages,
        renderStateBlock,
        renderConsoleShells,
        renderConsoleShell
    };
})();



