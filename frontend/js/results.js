import { $, escapeHtml, getSeverity, formatPct, showToast } from './utils.js';
import { openDiffViewer } from './diffViewer.js';

export function showResults(pairs = [], opts = {}) {
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

export function initResults() {
  // Download CSV
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

  // Delegated click for report view buttons
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
}
