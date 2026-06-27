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
    const raw = (location.hash || "").replace("#/", "").trim();
    return raw === "upload" ? "upload" : "home";
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
      summary.textContent = `Compared ${pairs.length} pair(s).`;
    }

    // Scroll to results smoothly
    setTimeout(() => {
      resultsSection.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 200);
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
    showPageInline(routeFromHash());
    initScrollEffects();
    initScrollAnimations();

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
        window.open(API_BASE + '/api/report/' + reportId, '_blank');
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
  (function() {
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

})();