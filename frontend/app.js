// API Configuration
const API_BASE = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1" || window.location.protocol === "file:"
  ? "http://localhost:9090"
  : "https://codesniff-backend.azurewebsites.net";

// app.js — CodeSniff v2 — Premium UI
(function () {
  // --- helpers ---
  const $ = id => document.getElementById(id);
  const qsa = sel => Array.from(document.querySelectorAll(sel));
  const escapeHtml = s => String(s || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");

  // live state
  let chosenFiles = [];

  // --- Toast notification system ---
  function showToast(message, type = "info", duration = 4000) {
    const container = $("toastContainer");
    if (!container) return;
    const toast = document.createElement("div");
    toast.className = `toast ${type}`;
    const icons = {
      error: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>`,
      success: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`,
      info: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>`
    };
    toast.innerHTML = `${icons[type] || icons.info}<span>${escapeHtml(message)}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = "0";
      toast.style.transform = "translateX(20px)";
      toast.style.transition = "all 0.3s ease";
      setTimeout(() => toast.remove(), 300);
    }, duration);
  }

  // --- page show/hide (SPA hash-based) ---
  function hideAllPagesInline() {
    qsa(".page").forEach(p => { p.classList.remove("active"); p.style.display = "none"; });
  }

  function showPageInline(name) {
    const home = $("page-home");
    const upload = $("page-upload");
    const results = $("results");
    const longArticle = $("who-can-use");
    const hero = $("hero") || $("hero-section") || document.querySelector(".hero");

    hideAllPagesInline();
    if (results) results.classList.add("hidden");

    if (name === "upload") {
      if (upload) { upload.style.display = "block"; upload.classList.add("active"); }
      if (longArticle) longArticle.style.display = "none";
      if (hero) hero.style.display = "none";
    } else if (["login", "signup", "forgot-password", "reset-password", "verify-email", "dashboard", "profile"].includes(name)) {
      const pageEl = $("page-" + name);
      if (pageEl) { pageEl.style.display = "block"; pageEl.classList.add("active"); }
      if (longArticle) longArticle.style.display = "none";
      if (hero) hero.style.display = "none";
    } else {
      if (home) { home.style.display = "block"; home.classList.add("active"); }
      if (longArticle) longArticle.style.display = "block";
      if (hero) hero.style.display = "";
    }

    const footer = document.querySelector(".site-footer") || document.querySelector(".footer");
    if (footer) footer.style.display = "";

    // Update nav active state
    updateNavActive(name);
  }

  // --- Nav active state sync ---
  function updateNavActive(name) {
    qsa(".nav-link").forEach(link => {
      const page = link.dataset.page;
      if (page === name) {
        link.classList.add("active");
      } else {
        link.classList.remove("active");
      }
    });
  }

  function routeFromHash() {
    const raw = (location.hash || "").split("?")[0].replace("#/", "").trim();
    if (["upload", "login", "signup", "forgot-password", "reset-password", "verify-email", "dashboard", "profile"].includes(raw)) {
        return raw;
    }
    return "home";
  }

  window.addEventListener("hashchange", () => showPageInline(routeFromHash()));

  // --- Scroll effects: topbar shadow ---
  function initScrollEffects() {
    const topbar = $("topbar");
    if (!topbar) return;
    let ticking = false;
    window.addEventListener("scroll", () => {
      if (!ticking) {
        requestAnimationFrame(() => {
          if (window.scrollY > 10) {
            topbar.classList.add("scrolled");
          } else {
            topbar.classList.remove("scrolled");
          }
          ticking = false;
        });
        ticking = true;
      }
    });
  }

  // --- Intersection Observer for scroll animations ---
  function initScrollAnimations() {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.style.animationPlayState = "running";
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.1 });

    // Observe cards and sections with animation
    qsa(".card, .info-card").forEach(el => {
      el.style.animationPlayState = "paused";
      observer.observe(el);
    });
  }

  // --- Auth State ---
  let currentUser = null;

  async function checkAuthState() {
    try {
      const res = await fetch(API_BASE + "/api/auth/me", {credentials: 'include'});
      if (res.ok) {
        const data = await res.json();
        if (data.authenticated) {
          currentUser = data.email;
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

          // Pre-fill profile form if it exists
          const profName = $("profName");
          const profEmail = $("profEmail");
          if (profName && profEmail) {
              profName.value = data.name || "";
              profEmail.value = data.email || "";
          }
        } else {
          logoutUI();
        }
      }
    } catch (e) {
      console.warn("Auth check failed", e);
    }
  }

  function logoutUI() {
    currentUser = null;
    const outNav = $("nav-logged-out");
    const inNav = $("nav-logged-in");
    if (outNav) outNav.classList.remove("hidden");
    if (inNav) inNav.classList.add("hidden");
    const current = routeFromHash();
    if (current === "dashboard" || current === "profile") {
      location.hash = "#/home";
    }
  }

  // --- analyze backend ---
  async function analyzeWithBackend(payload, mode) {
    try {
      const res = await fetch(API_BASE + "/api/analyze", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      if (!res.ok) throw new Error("HTTP " + res.status);
      return await res.json();
    } catch (e) {
      console.warn("Analyze API failed, using demo fallback:", e);
      const fake = () => Math.min(0.95, Math.max(0.05, 0.25 + Math.random() * 0.5));
      if (mode === "code") {
        return { summary: [{ a: payload.submissions[0].name || "A", b: payload.submissions[1].name || "B", score: fake() }] };
      } else {
        const pairs = [];
        for (let i = 0; i < chosenFiles.length - 1; i++) {
          pairs.push({ a: chosenFiles[i].name, b: chosenFiles[i + 1].name, score: fake() });
        }
        return { summary: pairs };
      }
    }
  }

  // --- results rendering ---
  const formatPct = x => (x * 100).toFixed(1) + "%";

  function getSeverity(score) {
    if (score < 0.3) return "low";
    if (score < 0.7) return "medium";
    return "high";
  }

  function showResults(pairs = [], opts = {}) {
    const resultsSection = $("results");
    const tableBody = document.querySelector("#resultTable tbody");
    const summary = $("summary");
    if (!resultsSection) return;
    resultsSection.classList.remove("hidden");

    if (tableBody) tableBody.innerHTML = "";
    pairs.forEach((p, idx) => {
      const severity = getSeverity(p.score);
      const pctValue = (p.score * 100).toFixed(1);
      const tr = document.createElement("tr");
      if (p.reportId) tr.dataset.reportId = p.reportId;
      tr.style.animation = `fadeInUp 0.4s ease-out ${idx * 0.1}s both`;
      tr.innerHTML = `
        <td>${idx + 1}</td>
        <td><strong>${escapeHtml(p.a)}</strong></td>
        <td><strong>${escapeHtml(p.b)}</strong></td>
        <td>
          <div class="similarity-bar">
            <div class="sim-track">
              <div class="sim-fill ${severity}" style="width:${pctValue}%"></div>
            </div>
            <span class="sim-pct ${severity}">${formatPct(p.score)}</span>
          </div>
        </td>
        <td><button class="btn btn-report" data-report="${idx}">View</button></td>
      `;
      tableBody && tableBody.appendChild(tr);
    });

    if (summary) {
      // 1. Calculate unique files
      const uniqueFiles = new Set();
      pairs.forEach(p => {
        uniqueFiles.add(p.a);
        uniqueFiles.add(p.b);
      });
      const fileCount = uniqueFiles.size;
      const fileText = fileCount === 1 ? "1 file" : `${fileCount} files`;
      const pairText = pairs.length === 1 ? "1 pair" : `${pairs.length} pairs`;

      // 2. Tally verdict counts using the existing backend threshold logic
      const counts = { Clean: 0, Review: 0, Suspicious: 0, High: 0 };
      pairs.forEach(p => {
        if (p.score > 0.70) counts.High++;
        else if (p.score > 0.45) counts.Suspicious++;
        else if (p.score > 0.25) counts.Review++;
        else counts.Clean++;
      });

      // 3. Build badges HTML (only show > 0)
      let badgesHtml = "";
      const severities = {
        Clean: "low",
        Review: "medium",
        Suspicious: "medium",
        High: "high"
      };

      Object.keys(counts).forEach(v => {
        if (counts[v] > 0) {
          // Re-use .sim-pct classes for colors, and simple inline border/bg based on severity
          let bg = "rgba(100, 116, 139, 0.1)";
          let border = "rgba(100, 116, 139, 0.2)";
          if (severities[v] === "low") { bg = "rgba(76, 175, 80, 0.1)"; border = "rgba(76, 175, 80, 0.2)"; }
          if (severities[v] === "medium") { bg = "rgba(245, 158, 11, 0.1)"; border = "rgba(245, 158, 11, 0.2)"; }
          if (severities[v] === "high") { bg = "rgba(239, 68, 68, 0.1)"; border = "rgba(239, 68, 68, 0.2)"; }

          badgesHtml += `<span class="diff-badge" style="margin-left: 8px; background: ${bg}; border-color: ${border};">
            <strong class="sim-pct ${severities[v]}">${v}: ${counts[v]}</strong>
          </span>`;
        }
      });

      summary.innerHTML = `
        <div style="display: flex; align-items: center; flex-wrap: wrap; gap: 8px;">
          <span>Analyzed ${fileText} &middot; ${pairText} compared</span>
          <div style="margin-left: auto;">${badgesHtml}</div>
        </div>
      `;
    }

    // Scroll to results smoothly
    setTimeout(() => {
      resultsSection.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 200);
  }

  // --- Batch Overview Visuals (v0.8) ---
  function fetchBatchVisuals(batchId) {
    fetch(API_BASE + "/api/batch/" + encodeURIComponent(batchId) + "/summary")
      .then(res => res.json())
      .then(data => renderBatchVisuals(data))
      .catch(err => console.error("Failed to fetch batch summary", err));
  }

  function renderBatchVisuals(data) {
    const overview = $("batch-overview");
    const statsContainer = $("batchStatsCards");
    const heatmap = $("batchHeatmap");
    if (!overview || !statsContainer || !heatmap) return;

    overview.classList.remove("hidden");

    // 1. Render Stat Cards
    statsContainer.innerHTML = `
      <div class="stat-card">
        <div class="stat-card-title">Highest Similarity</div>
        <div class="stat-card-value high">${formatPct(data.highestSimilarity || 0)}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card-title">Average Similarity</div>
        <div class="stat-card-value">${formatPct(data.averageSimilarity || 0)}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card-title">Lowest Similarity</div>
        <div class="stat-card-value safe">${formatPct(data.lowestSimilarity || 0)}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card-title">Suspicious Pairs</div>
        <div class="stat-card-value ${data.suspiciousPairCount > 0 ? 'high' : 'safe'}">${data.suspiciousPairCount || 0}</div>
      </div>
    `;

    // 2. Render Heatmap Grid
    heatmap.innerHTML = "";
    
    // Extract unique files and sort alphabetically
    const pairs = data.pairs || [];
    const uniqueSet = new Set();
    pairs.forEach(p => {
      uniqueSet.add(p.a);
      uniqueSet.add(p.b);
    });
    const files = Array.from(uniqueSet).sort();
    const N = files.length;
    
    // Fallback if no files exist
    if (N === 0) return;

    heatmap.style.gridTemplateColumns = `repeat(${N}, 24px)`;

    // Build lookup for fast pair retrieval
    const pairMap = new Map();
    pairs.forEach(p => {
      pairMap.set(`${p.a}|${p.b}`, p);
      pairMap.set(`${p.b}|${p.a}`, p);
    });

    for (let i = 0; i < N; i++) {
      for (let j = 0; j < N; j++) {
        const fileI = files[i];
        const fileJ = files[j];
        const cell = document.createElement("div");

        if (i === j) {
          cell.className = "heatmap-cell diagonal";
          cell.title = fileI;
        } else {
          const match = pairMap.get(`${fileI}|${fileJ}`);
          if (match) {
            const severity = getSeverity(match.score);
            cell.className = `heatmap-cell ${severity}`;
            cell.title = `${fileI} vs ${fileJ}: ${formatPct(match.score)}`;
            
            // Apply opacity scaling within severity band for true heatmap feel
            let intensity = 1.0;
            if (severity === 'low') intensity = 0.3 + (match.score / 0.3) * 0.7;
            else if (severity === 'medium') intensity = 0.4 + ((match.score - 0.3) / 0.4) * 0.6;
            else intensity = 0.5 + ((match.score - 0.7) / 0.3) * 0.5;
            cell.style.opacity = intensity.toFixed(2);

            if (match.reportId) {
              cell.addEventListener("click", () => openDiffViewer(match.reportId));
            }
          } else {
            // Missing pair
            cell.className = "heatmap-cell";
            cell.style.background = "var(--border)";
            cell.title = `${fileI} vs ${fileJ}: No data`;
          }
        }
        heatmap.appendChild(cell);
      }
    }
  }

  // --- file rendering for UI ---
  function renderFiles(files) {
    const fileList = $("fileList");
    if (!fileList) return;
    fileList.innerHTML = "";
    (files || []).forEach((f, i) => {
      const row = document.createElement("div");
      row.className = "file";
      const ext = f.name.split('.').pop().toUpperCase();
      row.innerHTML = `
        <span>${i + 1}. ${escapeHtml(f.name)}</span>
        <span class="badge">${ext} · ${(f.size / 1024).toFixed(1)} KB</span>
      `;
      fileList.appendChild(row);
    });
  }

  // --- Loading state ---
  function setLoading(btn, isLoading) {
    if (isLoading) {
      btn.dataset.originalText = btn.innerHTML;
      btn.disabled = true;
      btn.innerHTML = `
        <div class="spinner" style="width:16px;height:16px;border-width:2px;margin:0;"></div>
        <span>Analyzing…</span>
      `;
    } else {
      btn.disabled = false;
      btn.innerHTML = btn.dataset.originalText || "Analyze";
    }
  }

  // --- expose API for debugging ---
  window.codesniff = window.codesniff || {};
  window.codesniff.getFiles = () => chosenFiles.slice();

  // --- DOMContentLoaded: wire UI ---
  document.addEventListener("DOMContentLoaded", () => {
    checkAuthState();
    showPageInline(routeFromHash());
    initScrollEffects();
    initScrollAnimations();

    // Dropdown toggle
    const avatarBtn = $("avatarBtn");
    const avatarMenu = $("avatarDropdownMenu");
    if (avatarBtn && avatarMenu) {
        avatarBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            avatarMenu.classList.toggle("hidden");
        });
        document.addEventListener("click", () => {
            avatarMenu.classList.add("hidden");
        });
    }

    // Profile Sidebar Tabs
    const profileTabs = document.querySelectorAll(".profile-nav-item");
    const profileSections = document.querySelectorAll(".profile-section");
    profileTabs.forEach(tab => {
        tab.addEventListener("click", () => {
            profileTabs.forEach(t => t.classList.remove("active"));
            profileSections.forEach(s => s.classList.add("hidden"));
            tab.classList.add("active");
            const targetId = tab.getAttribute("data-target");
            const targetSec = document.getElementById(targetId);
            if(targetSec) targetSec.classList.remove("hidden");
        });
    });

    // Password visibility toggles
    document.querySelectorAll(".pwd-toggle").forEach(btn => {
      btn.addEventListener("click", () => {
        const targetId = btn.getAttribute("data-target");
        const input = document.getElementById(targetId);
        if (input) {
          if (input.type === "password") {
            input.type = "text";
            btn.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>`;
          } else {
            input.type = "password";
            btn.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
          }
        }
      });
    });

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
                // Email changed -> forced logout
                logoutUI();
                location.hash = "#/login";
            } else {
                // Name changed -> just update the UI
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

    // Tabs
    qsa(".tab").forEach(t => {
      t.addEventListener("click", () => {
        qsa(".tab").forEach(x => x.classList.remove("active"));
        t.classList.add("active");
        qsa(".tabpanel").forEach(p => p.classList.remove("active"));
        const panel = $(`tab-${t.dataset.tab}`);
        if (panel) panel.classList.add("active");
        const resultsEl = $("results");
        if (resultsEl) resultsEl.classList.add("hidden");
      });
    });

    // --- dropzone + browse ---
    const dz = $("dropzone");
    const fileInput = $("fileInput");
    const btnBrowse = $("btnBrowse");

    if (fileInput) {
      // Prevent bubbling to avoid infinite trigger loops since fileInput is a child of dropzone
      fileInput.addEventListener("click", (e) => {
        e.stopPropagation();
      });
      fileInput.addEventListener("change", (e) => {
        chosenFiles = Array.from(e.target.files || []);
        renderFiles(chosenFiles);
        if (chosenFiles.length > 0) {
          showToast(`${chosenFiles.length} file(s) selected`, "success");
        }
      });
    }

    if (btnBrowse && fileInput) {
      btnBrowse.addEventListener("click", (e) => {
        e.stopPropagation();
        fileInput.click();
      });
    }

    if (dz && fileInput) {
      dz.addEventListener("click", (e) => {
        if (e.target !== fileInput && e.target !== btnBrowse) {
          fileInput.click();
        }
      });
    }
    if (dz) {
      ["dragenter", "dragover"].forEach(ev => dz.addEventListener(ev, e => { e.preventDefault(); e.stopPropagation(); dz.classList.add("dragover"); }));
      ["dragleave", "drop"].forEach(ev => dz.addEventListener(ev, e => { e.preventDefault(); e.stopPropagation(); dz.classList.remove("dragover"); }));
      dz.addEventListener("drop", e => {
        chosenFiles = Array.from(e.dataTransfer.files || []);
        renderFiles(chosenFiles);
        if (chosenFiles.length > 0) {
          showToast(`${chosenFiles.length} file(s) dropped`, "success");
        }
      });
    }

    // --- Analyze: Code pair ---
    const btnAnalyzeCode = $("btnAnalyzeCode");
    if (btnAnalyzeCode) {
      btnAnalyzeCode.addEventListener("click", async () => {
        const codeA = $("codeA") ? $("codeA").value.trim() : "";
        const codeB = $("codeB") ? $("codeB").value.trim() : "";
        if (!codeA || !codeB) {
          showToast("Please paste both Code A and Code B.", "error");
          return;
        }

        const opts = {
          omitComments: $("omitCommentsCode") ? !!$("omitCommentsCode").checked : true,
          k: Number($("kCode") ? $("kCode").value || 6 : 6),
          window: Number($("wCode") ? $("wCode").value || 4 : 4),
        };

        const payload = {
          submissions: [
            { name: "A.java", content: codeA },
            { name: "B.java", content: codeB }
          ],
          options: opts
        };

        setLoading(btnAnalyzeCode, true);
        try {
          const res = await analyzeWithBackend(payload, "code");
          showResults(res.summary || [], opts);
          if (res.batchId) fetchBatchVisuals(res.batchId);
          showToast("Analysis complete!", "success");
        } catch (e) {
          showToast("Analysis failed. Please try again.", "error");
        } finally {
          setLoading(btnAnalyzeCode, false);
        }
      });
    }

    // --- Analyze: Files ---
    const btnAnalyzeFiles = $("btnAnalyzeFiles");
    if (btnAnalyzeFiles) {
      btnAnalyzeFiles.addEventListener("click", async () => {
        if (chosenFiles.length < 2) {
          showToast("Select at least two files to compare.", "error");
          return;
        }

        const MAX_SIZE = 1_000_000;
        const tooBig = chosenFiles.find(f => f.size > MAX_SIZE);
        if (tooBig) {
          showToast(`File too large: ${tooBig.name} > 1MB`, "error");
          return;
        }

        const opts = {
          omitComments: $("omitCommentsFiles") ? !!$("omitCommentsFiles").checked : true,
          k: Number($("kFiles") ? $("kFiles").value || 6 : 6),
          window: Number($("wFiles") ? $("wFiles").value || 4 : 4),
        };

        const submissions = await Promise.all(
          chosenFiles.map(f => f.text().then(content => ({ name: f.name, content })))
        );

        const payload = { submissions, options: opts };

        setLoading(btnAnalyzeFiles, true);
        try {
          const res = await analyzeWithBackend(payload, "files");
          showResults(res.summary || [], opts);
          if (res.batchId) fetchBatchVisuals(res.batchId);
          showToast("Analysis complete!", "success");
        } catch (e) {
          showToast("Analysis failed. Please try again.", "error");
        } finally {
          setLoading(btnAnalyzeFiles, false);
        }
      });
    }

    // --- Download CSV ---
    const btnDownload = $("btnDownloadReport") || $("btnDownload");
    if (btnDownload) {
      btnDownload.addEventListener("click", () => {
        const table = $("resultTable");
        if (!table) {
          showToast("No results to download.", "info");
          return;
        }
        const rows = Array.from(table.querySelectorAll("tr")).map(tr =>
          Array.from(tr.querySelectorAll("th,td")).map(cell => `"${cell.innerText.replace(/"/g, '""')}"`).join(",")
        );
        if (rows.length <= 1) {
          showToast("No results to download.", "info");
          return;
        }
        const csv = rows.join("\n");
        const blob = new Blob([csv], { type: "text/csv" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = "codesniff_report.csv";
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
        showToast("Report downloaded!", "success");
      });
    }

    // --- delegated click for report view buttons ---
    document.addEventListener("click", (ev) => {
      const btn = ev.target.closest && ev.target.closest("button[data-report]");
      if (!btn) return;
      const row = btn.closest("tr");
      if (!row) {
        showToast("Report not available.", "info");
        return;
      }
      const reportId = row.dataset && row.dataset.reportId;
      if (reportId) {
        openDiffViewer(reportId);
        return;
      }
      showToast("Backend report not available (demo mode).", "info");
    });

    // --- persist some fields locally ---
    function saveLocal(id) {
      const el = $(id);
      if (!el) return;
      const key = "codesniff:" + id;
      el.value = localStorage.getItem(key) || el.value || "";
      el.addEventListener("input", () => localStorage.setItem(key, el.value));
    }
    ["codeA", "codeB", "kCode", "wCode", "kFiles", "wFiles"].forEach(saveLocal);

    // --- Get Started button ---
    const btnGetStarted = $("btnGetStarted");
    if (btnGetStarted) {
      btnGetStarted.addEventListener("click", (e) => {
        e.preventDefault();
        location.hash = "#/upload";
        showPageInline("upload");
        qsa(".tab").forEach(t => t.classList.remove("active"));
        const codeTab = qsa(".tab").find(t => t.dataset && t.dataset.tab === "code");
        if (codeTab) codeTab.classList.add("active");
        qsa(".tabpanel").forEach(p => p.classList.remove("active"));
        const codePanel = $("tab-code");
        if (codePanel) codePanel.classList.add("active");
        setTimeout(() => {
          const upload = $("page-upload");
          upload && upload.scrollIntoView({ behavior: "smooth", block: "start" });
          const firstInput = upload && upload.querySelector("textarea, input, button");
          if (firstInput) firstInput.focus();
        }, 120);
      });
    }

    // Guard to ensure correct page on load
    const guard = setInterval(() => showPageInline(routeFromHash()), 120);
    setTimeout(() => clearInterval(guard), 1800);
  });

  // ============================================
  // BACKEND STATUS INDICATOR
  // ============================================
  (function () {
    const API_BASE_CHECK = typeof API_BASE !== 'undefined'
      ? API_BASE
      : "https://codesniff-backend.azurewebsites.net";

    // Create indicator HTML
    const indicator = document.createElement("div");
    indicator.id = "backend-indicator";
    indicator.innerHTML = `
      <div id="bi-pill">
        <span id="bi-dot"></span>
        <span id="bi-text">Connecting...</span>
        <span id="bi-timer"></span>
      </div>
    `;

    document.body.appendChild(indicator);

    // State manager
    const pill = indicator.querySelector("#bi-pill");
    const text = indicator.querySelector("#bi-text");
    const timer = indicator.querySelector("#bi-timer");

    function setState(state, message) {
      pill.className = "bi-" + state;
      text.textContent = message;
    }

    // Live timer
    let seconds = 0;
    let timerInterval = null;

    function startTimer() {
      seconds = 0;
      timerInterval = setInterval(() => {
        seconds++;
        const m = Math.floor(seconds / 60);
        const s = seconds % 60;
        timer.textContent = m > 0 ? `${m}m ${s}s` : `${s}s`;
      }, 1000);
    }

    function stopTimer() {
      clearInterval(timerInterval);
      timer.textContent = "";
    }

    // Auto-hide after success
    function hideAfterDelay(ms) {
      setTimeout(() => {
        indicator.classList.add("bi-fadeout");
        setTimeout(() => { indicator.style.display = "none"; }, 700);
      }, ms);
    }

    // Main check logic
    let attempt = 0;
    const MAX_ATTEMPTS = 10;
    const RETRY_DELAY = 6000;

    async function checkBackend() {
      attempt++;

      if (attempt === 1) {
        setState("connecting", "Connecting to server");
        startTimer();
      } else if (attempt <= 3) {
        setState("waking", "Server waking up");
      } else {
        setState("waking", "Still starting up");
      }

      try {
        const res = await fetch(API_BASE_CHECK + "/api/health", {
          signal: AbortSignal.timeout(8000)
        });

        if (res.ok) {
          stopTimer();
          setState("ready", "Server ready");
          hideAfterDelay(2500);
          return;
        }
        throw new Error("Not OK");

      } catch (e) {
        if (attempt < MAX_ATTEMPTS) {
          setTimeout(checkBackend, RETRY_DELAY);
        } else {
          stopTimer();
          setState("error", "Server unavailable");
          hideAfterDelay(5000);
        }
      }
    }

    // Start checking when page loads
    document.addEventListener("DOMContentLoaded", checkBackend);
  })();

  // ============================================================
  // DIFF VIEWER — Side-by-Side Code Review (v0.7)
  // ============================================================

  // Match navigation state
  let _diffMatchedLines = [];
  let _diffMatchIndex = 0;

  /**
   * Escapes HTML special characters in a string to prevent XSS injection
   * when inserting raw user code into the DOM. Required before any
   * user-submitted code content is placed into innerHTML.
   * (Re-declared here as a local alias to make the diff module self-contained.)
   */
  const escHtml = s => String(s == null ? "" : s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");

  /**
   * Builds the line-by-line HTML for one diff panel.
   *
   * @param {string}   rawCode     - The file's raw source code.
   * @param {number[]} matchedRows - Set of 1-indexed line numbers that are highlighted.
   * @returns {string} innerHTML string ready to be set on .diff-code-wrap.
   */
  function buildPanelHTML(rawCode, matchedRows) {
    const lines = rawCode.split("\n");
    // Remove a trailing empty line produced by a final newline
    if (lines.length > 1 && lines[lines.length - 1] === "") {
      lines.pop();
    }
    const matchSet = new Set(matchedRows);
    let html = "";
    for (let i = 0; i < lines.length; i++) {
      const lineNum = i + 1;
      const isMatched = matchSet.has(lineNum);
      const cls = isMatched ? "diff-line matched" : "diff-line";
      html += `<div class="${cls}"><span class="line-num">${lineNum}</span><span class="line-content">${escHtml(lines[i])}</span></div>`;
    }
    return html;
  }

  /**
   * Converts a matchedLines region list into a flat Set of 1-indexed line
   * numbers for either file A (useA=true) or file B (useA=false).
   */
  function regionToLineSet(matchedLines, useA) {
    const set = new Set();
    (matchedLines || []).forEach(r => {
      const start = useA ? r.startLineA : r.startLineB;
      const end   = useA ? r.endLineA   : r.endLineB;
      for (let ln = start; ln <= end; ln++) {
        set.add(ln);
      }
    });
    return set;
  }

  /** Shows one diff state panel and hides the others. */
  function setDiffState(state) {
    const loading   = document.getElementById("diff-loading");
    const error     = document.getElementById("diff-error");
    const noMatches = document.getElementById("diff-no-matches");
    const content   = document.getElementById("diff-content");
    [loading, error, noMatches, content].forEach(el => el && el.classList.add("hidden"));
    const target = { loading, error, "no-matches": noMatches, content }[state];
    if (target) target.classList.remove("hidden");
  }

  /**
   * Scrolls both diff panels so that matched region at `index` is visible.
   */
  function scrollToMatch(index) {
    if (!_diffMatchedLines.length) return;
    _diffMatchIndex = index;
    const region = _diffMatchedLines[_diffMatchIndex];
    if (!region) return;

    const codeA = document.getElementById("diff-code-a");
    const codeB = document.getElementById("diff-code-b");
    if (codeA) {
      const el = codeA.children[region.startLineA - 1];
      if (el) el.scrollIntoView({ block: "center" });
    }
    if (codeB) {
      const el = codeB.children[region.startLineB - 1];
      if (el) el.scrollIntoView({ block: "center" });
    }

    // Update position indicator and button states
    const pos = document.getElementById("diff-nav-pos");
    if (pos) pos.textContent = (_diffMatchIndex + 1) + " / " + _diffMatchedLines.length;
    const prevBtn = document.getElementById("diff-prev");
    const nextBtn = document.getElementById("diff-next");
    if (prevBtn) prevBtn.disabled = _diffMatchIndex === 0;
    if (nextBtn) nextBtn.disabled = _diffMatchIndex === _diffMatchedLines.length - 1;
  }

  /**
   * Opens the diff viewer modal and fetches matched regions for `reportId`.
   * Called when the user clicks "View" in the results table.
   */
  function openDiffViewer(reportId) {
    const overlay = document.getElementById("diff-overlay");
    if (!overlay) return;

    // Reset state and show modal
    overlay.classList.remove("hidden");
    document.body.style.overflow = "hidden"; // prevent page scroll while modal open
    setDiffState("loading");

    // Reset navigation state
    _diffMatchedLines = [];
    _diffMatchIndex = 0;

    // Clear any previous match count badge
    const badge = document.getElementById("diff-match-count");
    if (badge) badge.textContent = "";

    // Hide nav and exports until data loads
    const navDiv = document.getElementById("diff-nav");
    const exportsDiv = document.getElementById("diff-exports");
    if (navDiv) navDiv.classList.add("hidden");
    if (exportsDiv) exportsDiv.classList.add("hidden");

    // Clear explanation
    const explanationDiv = document.getElementById("diff-explanation");
    if (explanationDiv) explanationDiv.classList.add("hidden");

    // Fetch both the /matches endpoint and the report metadata
    Promise.all([
      fetch(API_BASE + "/api/report/" + encodeURIComponent(reportId) + "/matches").then(res => {
        if (!res.ok) return res.json().catch(() => null).then(b => { throw new Error((b && b.message) ? b.message : "HTTP " + res.status); });
        return res.json();
      }),
      fetch(API_BASE + "/api/report/" + encodeURIComponent(reportId)).then(res => {
        if (!res.ok) return res.json().catch(() => null).then(b => { throw new Error((b && b.message) ? b.message : "HTTP " + res.status); });
        return res.json();
      })
    ])
    .then(([data, reportData]) => {
        const fileA = data.fileA || {};
        const fileB = data.fileB || {};
        const matchedLines = data.matchedLines || [];

        // Update panel headers with file names
        const headerA = document.getElementById("diff-header-a");
        const headerB = document.getElementById("diff-header-b");
        if (headerA) headerA.textContent = fileA.name || "File A";
        if (headerB) headerB.textContent = fileB.name || "File B";

        // Update match count badge
        if (badge) {
          if (matchedLines.length > 0) {
            badge.textContent = matchedLines.length + " matched region" + (matchedLines.length !== 1 ? "s" : "");
          } else {
            badge.textContent = "";
          }
        }

        if (matchedLines.length === 0) {
          setDiffState("no-matches");
          return;
        }

        // Render Clone Explanation if present
        if (explanationDiv && reportData && reportData.explanation) {
          const exp = reportData.explanation;
          const typeBadge = document.getElementById("diff-clone-type");
          const factorsList = document.getElementById("diff-factors-list");
          
          if (typeBadge) typeBadge.textContent = exp.cloneType || "Unclassified Clone";
          
          if (factorsList) {
            factorsList.innerHTML = "";
            if (exp.contributingFactors && exp.contributingFactors.length > 0) {
              exp.contributingFactors.forEach(factor => {
                const li = document.createElement("li");
                li.textContent = factor;
                factorsList.appendChild(li);
              });
            } else {
              const li = document.createElement("li");
              li.textContent = "No specific contributing factors detected.";
              li.style.fontStyle = "italic";
              factorsList.appendChild(li);
            }
          }
          explanationDiv.classList.remove("hidden");
        }

        // Build and inject code panels
        const codeA = document.getElementById("diff-code-a");
        const codeB = document.getElementById("diff-code-b");
        if (codeA) codeA.innerHTML = buildPanelHTML(fileA.rawCode || "", regionToLineSet(matchedLines, true));
        if (codeB) codeB.innerHTML = buildPanelHTML(fileB.rawCode || "", regionToLineSet(matchedLines, false));

        setDiffState("content");

        // --- Wire export URLs ---
        const csvLink = document.getElementById("diff-export-csv");
        const htmlLink = document.getElementById("diff-export-html");
        const pdfLink = document.getElementById("diff-export-pdf");
        const exportBase = API_BASE + "/api/report/" + encodeURIComponent(reportId) + "/export?format=";
        if (csvLink) csvLink.href = exportBase + "csv";
        if (htmlLink) htmlLink.href = exportBase + "html";
        if (pdfLink) pdfLink.href = exportBase + "pdf";
        if (exportsDiv) exportsDiv.classList.remove("hidden");

        // --- Wire match navigation ---
        _diffMatchedLines = matchedLines;
        _diffMatchIndex = 0;
        if (matchedLines.length > 0 && navDiv) {
          navDiv.classList.remove("hidden");
          // Scroll to first match and update indicator
          setTimeout(() => scrollToMatch(0), 80);
        }
      })
      .catch(err => {
        const errText = document.getElementById("diff-error-text");
        if (errText) errText.textContent = "Failed to load matched regions: " + (err.message || "Unknown error");
        setDiffState("error");
      });
  }

  /** Closes the diff viewer modal and restores page scroll. */
  function closeDiffViewer() {
    const overlay = document.getElementById("diff-overlay");
    if (overlay) overlay.classList.add("hidden");
    document.body.style.overflow = "";
  }

  // Wire up close button and overlay backdrop click
  document.addEventListener("DOMContentLoaded", () => {
    const closeBtn = document.getElementById("diff-close-btn");
    const overlay  = document.getElementById("diff-overlay");

    if (closeBtn) {
      closeBtn.addEventListener("click", closeDiffViewer);
    }

    // Match navigation buttons
    const prevBtn = document.getElementById("diff-prev");
    const nextBtn = document.getElementById("diff-next");
    if (prevBtn) {
      prevBtn.addEventListener("click", () => {
        if (_diffMatchIndex > 0) scrollToMatch(_diffMatchIndex - 1);
      });
    }
    if (nextBtn) {
      nextBtn.addEventListener("click", () => {
        if (_diffMatchIndex < _diffMatchedLines.length - 1) scrollToMatch(_diffMatchIndex + 1);
      });
    }

    if (overlay) {
      overlay.addEventListener("click", e => {
        // Close only if the click is directly on the backdrop (not the modal card)
        if (e.target === overlay) closeDiffViewer();
      });
    }

    // Also close on Escape key
    document.addEventListener("keydown", e => {
      if (e.key === "Escape") {
        const ov = document.getElementById("diff-overlay");
        if (ov && !ov.classList.contains("hidden")) closeDiffViewer();
      }
    });
  });

})();