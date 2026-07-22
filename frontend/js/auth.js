import { API_BASE } from './config.js';
import { setCurrentUser } from './state.js';
import { $, showToast, setLoading } from './utils.js';
import { routeFromHash } from './router.js';

export async function checkAuthState() {
  try {
    const res = await fetch(API_BASE + "/api/auth/me", {credentials: 'include'});
    if (res.ok) {
      const data = await res.json();
      if (data.authenticated) {
        setCurrentUser(data.email);
        const outNav = $("nav-logged-out");
        const inNav = $("nav-logged-in");
        if (outNav) outNav.classList.add("hidden");
        if (inNav) inNav.classList.remove("hidden");
        
        const avatar = $("avatarName");
        if (avatar) {
            avatar.textContent = data.name ? data.name : data.email.split('@')[0];
        }
        
        const dashEmail = $("dashEmail");
        if (dashEmail) {
            dashEmail.textContent = data.name ? `${data.name} (${data.email})` : data.email;
        }

        const profName = $("profName");
        const profEmail = $("profEmail");
        if (profName && profEmail) {
            profName.value = data.name || "";
            profEmail.value = data.email || "";
        }
        
        const displayProfName = $("displayProfName");
        const displayProfEmail = $("displayProfEmail");
        const profileAvatarInitial = $("profileAvatarInitial");
        if (displayProfName && displayProfEmail && profileAvatarInitial) {
            const dispName = data.name || data.email.split('@')[0];
            displayProfName.textContent = dispName;
            displayProfEmail.textContent = data.email;
            profileAvatarInitial.textContent = dispName.charAt(0).toUpperCase();
        }
      } else {
        logoutUI();
      }
    }
  } catch (e) {
    console.warn("Auth check failed", e);
  }
}

export function logoutUI() {
  setCurrentUser(null);
  const outNav = $("nav-logged-out");
  const inNav = $("nav-logged-in");
  if (outNav) outNav.classList.remove("hidden");
  if (inNav) inNav.classList.add("hidden");
  const current = routeFromHash();
  if (current === "dashboard" || current === "profile") {
    location.hash = "#/home";
  }
}

export function initAuth() {
  // Signup Form
  const signupForm = $("signupForm");
  if (signupForm) {
    signupForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      
      const pwdError = $("signupPwdError");
      pwdError.style.display = "none";
      
      const name = $("signupName").value.trim();
      const email = $("signupEmail").value.trim();
      const password = $("signupPassword").value;
      const confirmPassword = $("signupConfirmPassword").value;
      
      if (password !== confirmPassword) {
          pwdError.style.display = "block";
          return;
      }
      
      const btn = $("btnSignupSubmit");
      setLoading(btn, true);
      try {
        const res = await fetch(API_BASE + "/api/auth/register", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name, email, password })
        });
        const data = await res.json();
        if (res.ok) {
          showToast(data.message, "success", 6000);
          signupForm.reset();
        } else {
          showToast(data.message || "Registration failed", "error");
        }
      } catch (e) {
        showToast("Network error.", "error");
      } finally {
        setLoading(btn, false);
      }
    });
  }

  // Login Form
  const loginForm = $("loginForm");
  if (loginForm) {
    loginForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const email = $("loginEmail").value.trim();
      const password = $("loginPassword").value;
      const btn = $("btnLoginSubmit");
      setLoading(btn, true);
      try {
        const res = await fetch(API_BASE + "/api/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, password }),
          credentials: 'include'
        });
        if (res.ok) {
          showToast("Logged in successfully!", "success");
          loginForm.reset();
          await checkAuthState();
          location.hash = "#/dashboard";
        } else {
          const data = await res.json();
          showToast(data.message || "Login failed", "error");
        }
      } catch (e) {
        showToast("Network error.", "error");
      } finally {
        setLoading(btn, false);
      }
    });
  }

  // Logout Button
  const btnLogout = $("btnLogout");
  if (btnLogout) {
    btnLogout.addEventListener("click", async (e) => {
      e.preventDefault();
      try {
        await fetch(API_BASE + "/api/auth/logout", { method: "POST", credentials: 'include' });
        showToast("Logged out.", "info");
        logoutUI();
      } catch (e) {
        console.error(e);
      }
    });
  }

  // Forgot Password Form
  const forgotForm = $("forgotForm");
  if (forgotForm) {
    forgotForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const email = $("forgotEmail").value.trim();
      const btn = $("btnForgotSubmit");
      setLoading(btn, true);
      try {
        const res = await fetch(API_BASE + "/api/auth/forgot-password", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email })
        });
        const data = await res.json();
        if (res.ok) {
          showToast(data.message, "info", 5000);
          forgotForm.reset();
        } else {
          showToast(data.message || "Request failed", "error");
        }
      } catch (e) {
        showToast("Network error.", "error");
      } finally {
        setLoading(btn, false);
      }
    });
  }

  // Reset Password Form
  const resetForm = $("resetForm");
  if (resetForm) {
    resetForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const token = new URLSearchParams(window.location.hash.split("?")[1]).get("token");
      const newPassword = $("resetPassword").value;
      const btn = $("btnResetSubmit");
      if (!token) {
        showToast("No reset token found in URL.", "error");
        return;
      }
      setLoading(btn, true);
      try {
        const res = await fetch(API_BASE + "/api/auth/reset-password", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ token, newPassword })
        });
        const data = await res.json();
        if (res.ok) {
          showToast(data.message, "success");
          resetForm.reset();
          setTimeout(() => { location.hash = "#/login"; }, 2000);
        } else {
          showToast(data.message || "Reset failed", "error");
        }
      } catch (e) {
        showToast("Network error.", "error");
      } finally {
        setLoading(btn, false);
      }
    });
  }

  // Profile Details Update Form
  const updateProfileForm = $("updateProfileForm");
  if (updateProfileForm) {
    updateProfileForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const name = $("profName").value.trim();
      const email = $("profEmail").value.trim();
      const btn = $("btnUpdateProfileSubmit");
      setLoading(btn, true);
      try {
        const res = await fetch(API_BASE + "/api/auth/profile", {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name, email }),
          credentials: 'include'
        });
        const data = await res.json();
        if (res.ok) {
          showToast(data.message, "success", 6000);
          if (data.emailChanged) {
              logoutUI();
              location.hash = "#/login";
          } else {
              await checkAuthState();
          }
        } else {
          showToast(data.message || "Failed to update profile", "error");
        }
      } catch (e) {
        showToast("Network error.", "error");
      } finally {
        setLoading(btn, false);
      }
    });
  }

  // Profile Form (Change Password)
  const profileForm = $("profileForm");
  if (profileForm) {
    profileForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const currentPassword = $("profCurrentPass").value;
      const newPassword = $("profNewPass").value;
      const btn = $("btnProfileSubmit");
      setLoading(btn, true);
      try {
        const res = await fetch(API_BASE + "/api/auth/change-password", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ currentPassword, newPassword }),
          credentials: 'include'
        });
        const data = await res.json();
        if (res.ok) {
          showToast(data.message, "success");
          profileForm.reset();
        } else {
          showToast(data.message || "Failed to change password", "error");
        }
      } catch (e) {
        showToast("Network error.", "error");
      } finally {
        setLoading(btn, false);
      }
    });
  }

  // Verify Email Hook
  window.addEventListener("hashchange", () => {
    const hash = location.hash;
    if (hash.startsWith("#/verify-email")) {
      const token = new URLSearchParams(hash.split("?")[1]).get("token");
      if (token) {
        const loadingEl = $("verifyEmailLoading");
        const resultEl = $("verifyEmailResult");
        const msgEl = $("verifyEmailMsg");
        if (loadingEl) loadingEl.classList.remove("hidden");
        if (resultEl) resultEl.classList.add("hidden");
        
        fetch(API_BASE + "/api/auth/verify-email", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ token })
        })
        .then(res => res.json().then(data => ({ status: res.status, ok: res.ok, data })))
        .then(res => {
          if (loadingEl) loadingEl.classList.add("hidden");
          if (resultEl) resultEl.classList.remove("hidden");
          if (res.ok) {
            msgEl.textContent = res.data.message;
            msgEl.style.color = "var(--success)";
          } else {
            msgEl.textContent = res.data.message || "Verification failed.";
            msgEl.style.color = "var(--error)";
          }
        })
        .catch(e => {
          if (loadingEl) loadingEl.classList.add("hidden");
          if (resultEl) resultEl.classList.remove("hidden");
          msgEl.textContent = "Network error during verification.";
          msgEl.style.color = "var(--error)";
        });
      }
    }
  });

  if (location.hash.startsWith("#/verify-email")) {
      window.dispatchEvent(new Event("hashchange"));
  }
}
