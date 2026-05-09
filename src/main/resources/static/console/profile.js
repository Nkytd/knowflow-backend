const ProfileCommon = window.KnowFlowConsoleCommon;

const profileState = {
    token: ProfileCommon.readToken(),
    user: null,
    oldPasswordVisible: false
};

const profileElements = {};

document.addEventListener("DOMContentLoaded", async () => {
    ProfileCommon.renderConsoleShells();
    cacheProfileElements();
    bindProfileEvents();
    profileState.token = ProfileCommon.readToken();
    if (profileState.token) {
        await restoreProfileSession();
        return;
    }
    showLoggedOutState();
});

function cacheProfileElements() {
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
        "readonlyProfileInfo",
        "backButton",
        "profileForm",
        "realNameInput",
        "ageInput",
        "genderInput",
        "emailInput",
        "phoneInput",
        "saveProfileButton",
        "passwordForm",
        "oldPasswordInput",
        "oldPasswordToggle",
        "newPasswordInput",
        "confirmPasswordInput",
        "changePasswordButton"
    ].forEach((id) => {
        profileElements[id] = document.getElementById(id);
    });
}

function bindProfileEvents() {
    profileElements.loginForm.addEventListener("submit", handleLogin);
    profileElements.logoutButton?.addEventListener("click", logoutProfile);
    profileElements.refreshButton?.addEventListener("click", restoreProfileSession);
    profileElements.profileForm.addEventListener("submit", handleSaveProfile);
    profileElements.passwordForm.addEventListener("submit", handleChangePassword);
    profileElements.oldPasswordToggle.addEventListener("click", toggleOldPasswordVisibility);
    profileElements.backButton.addEventListener("click", goBackToSourcePage);
}

async function handleLogin(event) {
    event.preventDefault();
    const username = profileElements.usernameInput.value.trim();
    const password = profileElements.passwordInput.value;
    if (!username || !password) {
        setBanner("请输入账号和密码。", "error");
        return;
    }
    profileElements.loginButton.disabled = true;
    try {
        const loginData = await ProfileCommon.api("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password }),
            attachAuth: false
        });
        profileState.token = loginData.token;
        ProfileCommon.saveToken(loginData.token);
        profileElements.passwordInput.value = "";
        await restoreProfileSession();
        setBanner("用户中心已就绪。", "success");
    } catch (error) {
        setBanner(error.message || "登录失败。", "error");
    } finally {
        profileElements.loginButton.disabled = false;
    }
}

async function restoreProfileSession() {
    profileState.token = ProfileCommon.readToken();
    if (!profileState.token) {
        showLoggedOutState();
        return;
    }
    try {
        const user = await ProfileCommon.api("/api/v1/auth/me", { token: profileState.token });
        profileState.user = user;
        showLoggedInState();
        renderProfileInfo();
        fillProfileForm();
    } catch (error) {
        if (error instanceof ProfileCommon.AuthExpiredError) {
            logoutProfile("登录已过期，请重新登录。");
            return;
        }
        setBanner(error.message || "加载用户中心失败。", "error");
    }
}

function showLoggedInState() {
    profileElements.loginPanel.hidden = true;
    profileElements.workspace.hidden = false;
    if (profileElements.sessionName) {
        profileElements.sessionName.textContent = profileState.user.realName || profileState.user.username;
    }
    if (profileElements.sessionMeta) {
        profileElements.sessionMeta.textContent = ProfileCommon.formatUserSessionMeta(profileState.user);
    }
}

function showLoggedOutState() {
    profileElements.loginPanel.hidden = false;
    profileElements.workspace.hidden = true;
    if (profileElements.sessionName) {
        profileElements.sessionName.textContent = "未登录";
    }
    if (profileElements.sessionMeta) {
        profileElements.sessionMeta.textContent = "请先登录后查看用户中心。";
    }
}

function logoutProfile(message = "已退出登录。") {
    profileState.token = "";
    profileState.user = null;
    ProfileCommon.clearToken();
    showLoggedOutState();
    setBanner(message, "success");
}


function goBackToSourcePage() {
    const from = ProfileCommon.parseQuery().from;
    if (from && from.startsWith("/") && !from.startsWith("//")) {
        window.location.href = from;
        return;
    }
    if (window.history.length > 1) {
        window.history.back();
        return;
    }
    window.location.href = "/admin/dashboard";
}
function renderProfileInfo() {
    const user = profileState.user || {};
    const rows = [
        ["编号", user.userId],
        ["账号", user.username],
        ["角色", ProfileCommon.formatRoleList(user.roleCodes)],
        ["租户 ID", user.tenantId],
        ["状态", ProfileCommon.enumLabel("knowledgeBaseStatus", user.status, user.status || "未知")]
    ];
    profileElements.readonlyProfileInfo.innerHTML = rows.map(([label, value]) => `
        <div class="detail-card profile-info-row readonly">
            <span class="meta-line">${ProfileCommon.escapeHtml(label)}</span>
            <strong>${ProfileCommon.escapeHtml(value ?? "-")}</strong>
        </div>
    `).join("");
}

function fillProfileForm() {
    const user = profileState.user || {};
    profileElements.realNameInput.value = user.realName || "";
    profileElements.ageInput.value = user.age ?? "";
    profileElements.genderInput.value = user.gender || "";
    profileElements.emailInput.value = user.email || "";
    profileElements.phoneInput.value = user.phone || "";
}

async function handleSaveProfile(event) {
    event.preventDefault();
    const payload = {
        realName: profileElements.realNameInput.value.trim() || null,
        age: profileElements.ageInput.value === "" ? null : Number(profileElements.ageInput.value),
        gender: profileElements.genderInput.value || null,
        email: profileElements.emailInput.value.trim() || null,
        phone: profileElements.phoneInput.value.trim() || null
    };
    profileElements.saveProfileButton.disabled = true;
    try {
        const user = await ProfileCommon.api("/api/v1/auth/profile", {
            token: profileState.token,
            method: "POST",
            body: JSON.stringify(payload)
        });
        profileState.user = user;
        showLoggedInState();
        renderProfileInfo();
        fillProfileForm();
        setBanner("用户资料已保存。", "success");
    } catch (error) {
        console.error("保存用户资料失败", error);
        setBanner(error.message || "保存用户资料失败，请确认后端已重启并完成数据库迁移。", "error");
    } finally {
        profileElements.saveProfileButton.disabled = false;
    }
}

async function handleChangePassword(event) {
    event.preventDefault();
    const oldPassword = profileElements.oldPasswordInput.value;
    const newPassword = profileElements.newPasswordInput.value;
    const confirmPassword = profileElements.confirmPasswordInput.value;
    if (!oldPassword) {
        setBanner("请输入当前正在使用的密码。", "error");
        return;
    }
    if (newPassword.length < 6) {
        setBanner("新密码至少需要 6 位。", "error");
        return;
    }
    if (newPassword !== confirmPassword) {
        setBanner("两次输入的新密码不一致。", "error");
        return;
    }

    profileElements.changePasswordButton.disabled = true;
    try {
        await ProfileCommon.api("/api/v1/auth/password", {
            token: profileState.token,
            method: "POST",
            body: JSON.stringify({ oldPassword, newPassword })
        });
        resetPasswordForm();
        setBanner("密码修改成功，请妥善保存新密码。", "success");
    } catch (error) {
        setBanner(error.message || "密码修改失败。", "error");
    } finally {
        profileElements.changePasswordButton.disabled = false;
    }
}

function resetPasswordForm() {
    profileElements.oldPasswordInput.value = "";
    profileElements.newPasswordInput.value = "";
    profileElements.confirmPasswordInput.value = "";
    profileElements.oldPasswordInput.type = "password";
    profileState.oldPasswordVisible = false;
    profileElements.oldPasswordToggle.setAttribute("aria-label", "显示旧密码");
}

function toggleOldPasswordVisibility() {
    profileState.oldPasswordVisible = !profileState.oldPasswordVisible;
    profileElements.oldPasswordInput.type = profileState.oldPasswordVisible ? "text" : "password";
    profileElements.oldPasswordToggle.setAttribute("aria-label", profileState.oldPasswordVisible ? "隐藏旧密码" : "显示旧密码");
}

function setBanner(message, type = "info") {
    profileElements.statusBanner.hidden = false;
    profileElements.statusBanner.textContent = message;
    profileElements.statusBanner.className = `status-banner ${type}`;
}