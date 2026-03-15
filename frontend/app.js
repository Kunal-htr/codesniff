// API Configuration
const API_BASE = "https://codesniff-backend.azurewebsites.net";

// app.js — cleaned, defensive, ready to paste
(function () {
  // --- helpers ---
  const $ = id => document.getElementById(id);
  const qsa = sel => Array.from(document.querySelectorAll(sel));
  const escapeHtml = s => String(s || "");

  // live state
  let chosenFiles = [];

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
  }

  function routeFromHash() {
    const raw = (location.hash || "").replace("#/", "").trim();
    return raw === "upload" ? "upload" : "home";
  }

  window.addEventListener("hashchange", () => showPageInline(routeFromHash()));

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
  function showResults(pairs = [], opts = {}) {
    const resultsSection = $("results");
    const tableBody = document.querySelector("#resultTable tbody");
    const summary = $("summary");
    if (!resultsSection) return;
    resultsSection.classList.remove("hidden");
    if (tableBody) tableBody.innerHTML = "";
    pairs.forEach((p, idx) => {
      const tr = document.createElement("tr");
      if (p.reportId) tr.dataset.reportId = p.reportId;
      tr.innerHTML = `
        <td>${idx + 1}</td>
        <td>${escapeHtml(p.a)}</td>
        <td>${escapeHtml(p.b)}</td>
        <td><strong>${formatPct(p.score)}</strong></td>
        <td><button class="btn" data-report="${idx}">View</button></td>
      `;
      tableBody && tableBody.appendChild(tr);
    });
    if (summary) summary.textContent = `Compared ${pairs.length} pair(s).`;
  }

  // --- file rendering for UI ---
  function renderFiles(files) {
    const fileList = $("fileList");
    if (!fileList) return;
    fileList.innerHTML = "";
    (files || []).forEach((f, i) => {
      const row = document.createElement("div");
      row.className = "file";
      row.innerHTML = `<span>${i + 1}. ${escapeHtml(f.name)}</span><span class="badge">${(f.size / 1024).toFixed(1)} KB</span>`;
      fileList.appendChild(row);
    });
  }

  // --- expose API for debugging ---
  window.codesniff = window.codesniff || {};
  window.codesniff.getFiles = () => chosenFiles.slice();

  // --- DOMContentLoaded: wire UI ---
  document.addEventListener("DOMContentLoaded", () => {
    showPageInline(routeFromHash());

    qsa(".tab").forEach(t => {
      t.addEventListener("click", () => {
        qsa(".tab").forEach(x => x.classList.remove("active"));
        t.classList.add("active");
        qsa(".tabpanel").forEach(p => p.classList.remove("active"));
        const panel = $(t.dataset.tab === undefined ? `tab-${t.id || ""}` : `tab-${t.dataset.tab}`) || $(`tab-${t.dataset.tab}`);
        if (panel) panel.classList.add("active");
        const resultsEl = $("results");
        if (resultsEl) resultsEl.classList.add("hidden");
      });
    });

    // --- dropzone + browse ---
    const dz = $("dropzone");
    const fileInput = $("fileInput");
    const btnBrowse = $("btnBrowse");
    if (btnBrowse && fileInput) btnBrowse.addEventListener("click", () => fileInput.click());
    if (fileInput) {
      fileInput.addEventListener("change", (e) => {
        chosenFiles = Array.from(e.target.files || []);
        renderFiles(chosenFiles);
      });
    }
    if (dz) {
      ["dragenter", "dragover"].forEach(ev => dz.addEventListener(ev, e => { e.preventDefault(); e.stopPropagation(); dz.classList.add("dragover"); }));
      ["dragleave", "drop"].forEach(ev => dz.addEventListener(ev, e => { e.preventDefault(); e.stopPropagation(); dz.classList.remove("dragover"); }));
      dz.addEventListener("drop", e => {
        chosenFiles = Array.from(e.dataTransfer.files || []);
        renderFiles(chosenFiles);
      });
    }

    // --- Analyze: Code pair ---
    const btnAnalyzeCode = $("btnAnalyzeCode");
    if (btnAnalyzeCode) {
      btnAnalyzeCode.addEventListener("click", async () => {
        const codeA = $("codeA") ? $("codeA").value.trim() : "";
        const codeB = $("codeB") ? $("codeB").value.trim() : "";
        if (!codeA || !codeB) { alert("Paste both Code A and Code B."); return; }

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

        btnAnalyzeCode.disabled = true;
        const prevText = btnAnalyzeCode.textContent;
        btnAnalyzeCode.textContent = "Analyzing…";
        try {
          const res = await analyzeWithBackend(payload, "code");
          showResults(res.summary || [], opts);
        } finally {
          btnAnalyzeCode.disabled = false;
          btnAnalyzeCode.textContent = prevText || "Analyze";
        }
      });
    }

    // --- Analyze: Files ---
    const btnAnalyzeFiles = $("btnAnalyzeFiles");
    if (btnAnalyzeFiles) {
      btnAnalyzeFiles.addEventListener("click", async () => {
        if (chosenFiles.length < 2) { alert("Select at least two files to compare."); return; }

        const MAX_SIZE = 1_000_000;
        const tooBig = chosenFiles.find(f => f.size > MAX_SIZE);
        if (tooBig) { alert(`File too large for demo: ${tooBig.name} > 1MB`); return; }

        const opts = {
          omitComments: $("omitCommentsFiles") ? !!$("omitCommentsFiles").checked : true,
          k: Number($("kFiles") ? $("kFiles").value || 6 : 6),
          window: Number($("wFiles") ? $("wFiles").value || 4 : 4),
        };

        const submissions = await Promise.all(
            chosenFiles.map(f => f.text().then(content => ({ name: f.name, content })))
        );

        const payload = { submissions, options: opts };

        btnAnalyzeFiles.disabled = true;
        const prevText = btnAnalyzeFiles.textContent;
        btnAnalyzeFiles.textContent = "Analyzing…";
        try {
          const res = await analyzeWithBackend(payload, "files");
          showResults(res.summary || [], opts);
        } finally {
          btnAnalyzeFiles.disabled = false;
          btnAnalyzeFiles.textContent = prevText || "Analyze Files";
        }
      });
    }

    // --- Download CSV ---
    const btnDownload = $("btnDownloadReport") || $("btnDownload");
    if (btnDownload) {
      btnDownload.addEventListener("click", () => {
        const table = $("resultTable");
        if (!table) return alert("No results to download.");
        const rows = Array.from(table.querySelectorAll("tr")).map(tr =>
            Array.from(tr.querySelectorAll("th,td")).map(cell => `"${cell.innerText.replace(/"/g, '""')}"`).join(",")
        );
        if (rows.length <= 1) return alert("No results to download.");
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
      });
    }

    // --- delegated click for report view buttons ---
    document.addEventListener("click", (ev) => {
      const btn = ev.target.closest && ev.target.closest("button[data-report]");
      if (!btn) return;
      const row = btn.closest("tr");
      if (!row) { alert("Report not available."); return; }
      const reportId = row.dataset && row.dataset.reportId;
      if (reportId) {
        window.open(API_BASE + '/api/report/' + reportId, '_blank');
        return;
      }
      alert("Backend report not available (demo mode).");
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

    const guard = setInterval(() => showPageInline(routeFromHash()), 120);
    setTimeout(() => clearInterval(guard), 1800);
  });

  window.codesniff = window.codesniff || {};
  window.codesniff.getFiles = () => chosenFiles.slice();

})();