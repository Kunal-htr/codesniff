import { API_BASE } from './config.js';
import { escapeHtml } from './utils.js';

let _diffMatchedLines = [];
let _diffMatchIndex = 0;

/**
 * Builds the line-by-line HTML for one diff panel.
 */
function buildPanelHTML(rawCode, matchedRows) {
  const lines = rawCode.split("\n");
  if (lines.length > 1 && lines[lines.length - 1] === "") {
    lines.pop();
  }
  const matchSet = new Set(matchedRows);
  let html = "";
  for (let i = 0; i < lines.length; i++) {
    const lineNum = i + 1;
    const isMatched = matchSet.has(lineNum);
    const cls = isMatched ? "diff-line matched" : "diff-line";
    html += `<div class="${cls}"><span class="line-num">${lineNum}</span><span class="line-content">${escapeHtml(lines[i])}</span></div>`;
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
 * If `historyId` is provided, fetches from the historical endpoint instead.
 * Called when the user clicks "View" in the results table or History modal.
 */
export function openDiffViewer(reportId, historyId = null) {
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

  // Fetch data
  let dataPromise, reportDataPromise;
  if (historyId) {
    const p = fetch(API_BASE + `/api/history/${historyId}/report/${encodeURIComponent(reportId)}`, { credentials: 'include' }).then(res => {
      if (!res.ok) return res.json().catch(() => null).then(b => { throw new Error((b && b.message) ? b.message : "HTTP " + res.status); });
      return res.json();
    });
    dataPromise = p;
    reportDataPromise = p.then(d => d.reportData || d);
  } else {
    dataPromise = fetch(API_BASE + "/api/report/" + encodeURIComponent(reportId) + "/matches").then(res => {
      if (!res.ok) return res.json().catch(() => null).then(b => { throw new Error((b && b.message) ? b.message : "HTTP " + res.status); });
      return res.json();
    });
    reportDataPromise = fetch(API_BASE + "/api/report/" + encodeURIComponent(reportId)).then(res => {
      if (!res.ok) return res.json().catch(() => null).then(b => { throw new Error((b && b.message) ? b.message : "HTTP " + res.status); });
      return res.json();
    });
  }

  return Promise.all([dataPromise, reportDataPromise])
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
      const exportBase = historyId 
        ? API_BASE + `/api/history/${historyId}/report/${encodeURIComponent(reportId)}/export?format=`
        : API_BASE + "/api/report/" + encodeURIComponent(reportId) + "/export?format=";
        
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

export function closeDiffViewer() {
  const overlay = document.getElementById("diff-overlay");
  if (overlay) overlay.classList.add("hidden");
  document.body.style.overflow = "";
}

export function initDiffViewer() {
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
}
