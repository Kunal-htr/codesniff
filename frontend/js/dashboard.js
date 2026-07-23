import { $, escapeHtml } from './utils.js';
import { API_BASE } from './config.js';
import { openDiffViewer } from './diffViewer.js';

let currentSearch = "";
let currentSortBy = "createdAt";
let currentSortDir = "desc";
let searchTimeout = null;

export function initDashboard() {
  const searchInput = $("historySearch");
  const sortSelect = $("historySort");
  const searchBtn = $("historySearchBtn");
  const sortDropdownBtn = $("sortDropdownBtn");
  const sortDropdownMenu = $("sortDropdownMenu");
  const sortDropdownLabel = $("sortDropdownLabel");

  if (searchInput && searchBtn) {
    searchBtn.addEventListener("click", () => {
      currentSearch = searchInput.value.trim();
      loadHistory();
    });
    
    searchInput.addEventListener("keypress", (e) => {
      if (e.key === 'Enter') {
        currentSearch = searchInput.value.trim();
        loadHistory();
      }
    });
  }

  if (sortDropdownBtn && sortDropdownMenu) {
    sortDropdownBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      sortDropdownMenu.classList.toggle("hidden");
    });
    
    document.addEventListener("click", (e) => {
      if (!sortDropdownMenu.contains(e.target) && e.target !== sortDropdownBtn) {
        sortDropdownMenu.classList.add("hidden");
      }
    });
    
    sortDropdownMenu.querySelectorAll(".dropdown-item").forEach(item => {
      item.addEventListener("click", (e) => {
        const val = e.currentTarget.dataset.value;
        const [by, dir] = val.split(",");
        currentSortBy = by;
        currentSortDir = dir;
        
        sortDropdownLabel.textContent = e.currentTarget.textContent;
        sortDropdownMenu.querySelectorAll(".dropdown-item").forEach(i => i.classList.remove("selected"));
        e.currentTarget.classList.add("selected");
        sortDropdownMenu.classList.add("hidden");
        
        loadHistory();
      });
    });
  }
}

export async function loadHistory() {
  const listEl = $("historyList");
  const errEl = $("historyError");
  if (!listEl) return;

  listEl.innerHTML = `
    <div class="history-loading" style="grid-column: 1 / -1;">
      <div class="duotone-spinner"></div>
      <div>Loading your history...</div>
    </div>
  `;
  if (errEl) errEl.classList.add("hidden");

  try {
    let url = `/api/history?sortBy=${currentSortBy}&sortDir=${currentSortDir}`;
    if (currentSearch) {
      url += `&search=${encodeURIComponent(currentSearch)}`;
    }

    const res = await fetch(API_BASE + url, { credentials: 'include' });
    if (!res.ok) {
      throw new Error(`Failed to load history: ${res.status}`);
    }

    const data = await res.json();
    const items = Array.isArray(data) ? data : (data.content || []);
    renderHistory(items);
  } catch (err) {
    console.error("Dashboard error:", err);
    if (errEl) {
      errEl.textContent = "Failed to load history. Please try again.";
      errEl.classList.remove("hidden");
    }
    listEl.innerHTML = "";
  }
}

export async function initDashboardStats() {
  const streakEl = $("dashStreak");
  const totalEl = $("dashTotalAnalyses");
  const graphContainer = $("dashGraphContainer");
  if (!streakEl || !totalEl || !graphContainer) return;
  
  graphContainer.innerHTML = `<div style="width: 100%; text-align: center; color: var(--text-secondary); margin-bottom: 20px;">Loading stats...</div>`;

  try {
    const res = await fetch(API_BASE + "/api/history/stats", { credentials: 'include' });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`HTTP ${res.status}: ${errText}`);
    }
    const data = await res.json();
    
    const counts = data.dailyCounts || [];
    const recentTotal = counts.reduce((sum, d) => sum + d.count, 0);
    
    streakEl.textContent = recentTotal;
    totalEl.textContent = data.totalAnalyses;
    
    // Draw SVG
    if (counts.length === 0) {
      graphContainer.innerHTML = `<div style="width: 100%; text-align: center; color: var(--text-secondary); margin-bottom: 20px;">No data available</div>`;
      return;
    }
    
    const maxCount = Math.max(...counts.map(d => d.count), 1); // ensure no div by zero
    
    let svgHtml = `<svg width="100%" height="100%" viewBox="0 0 1000 200" preserveAspectRatio="none" style="overflow: visible;">`;
    
    const barWidth = (1000 / counts.length) - 4; // leave 4 units gap
    
    counts.forEach((item, index) => {
      const x = index * (1000 / counts.length) + 2;
      // Calculate height (leave 10px top margin)
      const height = (item.count / maxCount) * 190;
      const y = 200 - height;
      
      const isZero = item.count === 0;
      const fill = isZero ? "rgba(30, 58, 95, 0.05)" : "url(#gradBar)";
      const title = `${item.date}: ${item.count} analyses`;
      
      // Minimum height of 4px for zero counts so they are visible
      const drawHeight = isZero ? 4 : Math.max(height, 4);
      const drawY = 200 - drawHeight;
      
      svgHtml += `
        <rect x="${x}" y="${drawY}" width="${barWidth}" height="${drawHeight}" rx="4" fill="${fill}" class="chart-bar">
          <title>${title}</title>
        </rect>
      `;
    });
    
    svgHtml += `
      <defs>
        <linearGradient id="gradBar" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stop-color="var(--green)" />
          <stop offset="100%" stop-color="var(--blue)" />
        </linearGradient>
      </defs>
    </svg>`;
    
    graphContainer.innerHTML = svgHtml;
    
  } catch (err) {
    console.error("Dashboard stats error:", err);
    graphContainer.innerHTML = `<div style="width: 100%; text-align: center; color: #d32f2f; margin-bottom: 20px;">Failed to load graph: ${err.message}</div>`;
  }
}

function renderHistory(items) {
  const listEl = $("historyList");
  if (!listEl) return;

  if (!items || items.length === 0) {
    if (currentSearch) {
      listEl.innerHTML = `
        <div class="history-empty" style="grid-column: 1 / -1;">
          <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          <h3>No matches found</h3>
          <p>No history records match "${currentSearch}"</p>
        </div>
      `;
    } else {
      listEl.innerHTML = `
        <div class="history-empty" style="grid-column: 1 / -1;">
          <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <polyline points="12 6 12 12 16 14"></polyline>
          </svg>
          <h3>No analyses yet</h3>
          <p>Run your first comparison to see history here.</p>
          <a href="#/upload" class="btn btn-primary" style="display:inline-flex;">Start Analysis</a>
        </div>
      `;
    }
    return;
  }

  listEl.innerHTML = items.map(item => {
    const rawSim = item.highestSimilarity || 0;
    const sim = rawSim <= 1.0 && rawSim > 0 ? rawSim * 100 : rawSim; // Convert 0-1 to 0-100
    const simDisplay = Number.isInteger(sim) ? sim : parseFloat(sim.toFixed(2));
    let badgeClass = "low";
    if (sim >= 80) badgeClass = "high";
    else if (sim >= 50) badgeClass = "medium";

    const dateStr = new Date(item.createdAt).toLocaleDateString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });

    const maxFiles = 3;
    const fileNames = item.fileNames || [];
    const displayedFiles = fileNames.slice(0, maxFiles);
    const extraFiles = fileNames.length - maxFiles;

    const filesHtml = displayedFiles.map(f => `
      <div class="file-row">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"></path>
          <polyline points="14 2 14 8 20 8"></polyline>
        </svg>
        <span>${f}</span>
      </div>
    `).join('');

    return `
      <div class="history-card" data-id="${item.id}">
        <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
          <div style="display: flex; align-items: center; gap: 8px;">
            <span class="sim-badge ${badgeClass}">${simDisplay}% Match</span>
            <span class="card-date">${dateStr}</span>
          </div>
          <button class="pin-btn" data-id="${item.id}" data-pinned="${item.isPinned}" style="background: transparent; border: none; cursor: pointer; color: ${item.isPinned ? 'var(--green)' : 'var(--border)'}; padding: 4px; transition: color 0.2s;" title="${item.isPinned ? 'Unpin' : 'Pin to top'}">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="${item.isPinned ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
            </svg>
          </button>
        </div>
        <div class="card-files">
          ${filesHtml}
          ${extraFiles > 0 ? `<div class="file-extra">+${extraFiles} more file${extraFiles > 1 ? 's' : ''}</div>` : ''}
        </div>
        <div class="card-actions">
          <button class="card-btn view-btn" data-id="${item.id}" title="View details (Phase D2)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
              <circle cx="12" cy="12" r="3"></circle>
            </svg>
            View
          </button>
          <button class="card-btn delete delete-btn" data-id="${item.id}" title="Delete record">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"></polyline>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            </svg>
            Delete
          </button>
        </div>
      </div>
    `;
  }).join('');

  // Bind events
  listEl.querySelectorAll('.delete-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      const id = e.currentTarget.dataset.id;
      if (confirm("Are you sure you want to delete this analysis record?")) {
        await deleteHistory(id);
      }
    });
  });

  let isModalLoading = false;
  listEl.querySelectorAll('.view-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      if (isModalLoading) return;
      isModalLoading = true;
      const id = e.currentTarget.dataset.id;
      const originalHtml = btn.innerHTML;
      btn.innerHTML = `<div class="btn-spinner"></div> Loading...`;
      btn.disabled = true;
      try {
        await openHistoryModal(id);
      } finally {
        btn.innerHTML = originalHtml;
        btn.disabled = false;
        isModalLoading = false;
      }
    });
  });
  
  listEl.querySelectorAll('.pin-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.preventDefault();
      e.stopPropagation();
      const id = e.currentTarget.dataset.id;
      const currentlyPinned = e.currentTarget.dataset.pinned === "true";
      try {
        const res = await fetch(API_BASE + `/api/history/${id}/pin`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ pinned: !currentlyPinned }),
          credentials: 'include'
        });
        if (res.ok) {
          // Re-fetch to sort on backend, or we could sort DOM manually.
          // Since we want matching backend sort behavior, a quick reload is safest without full page refresh.
          loadHistory();
        }
      } catch (err) {
        console.error("Pin toggle failed", err);
      }
    });
  });
}

async function deleteHistory(id) {
  try {
    const res = await fetch(API_BASE + `/api/history/${id}`, {
      method: 'DELETE',
      credentials: 'include'
    });
    if (res.ok) {
      loadHistory();
      initDashboardStats();
    }
  } catch (err) {
    console.error("Failed to delete", err);
    alert("Failed to delete record. Please try again.");
  }
}

async function openHistoryModal(historyId) {
  try {
    const res = await fetch(API_BASE + `/api/history/${historyId}`, { credentials: 'include' });
    if (!res.ok) throw new Error("Failed to load history details");
    const data = await res.json();
    
    // Populate modal headers
    const dateStr = new Date(data.createdAt).toLocaleString(undefined, {
      year: 'numeric', month: 'long', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
    const rawHm = data.highestSimilarity || 0;
    const hm = rawHm <= 1.0 && rawHm > 0 ? rawHm * 100 : rawHm;
    $('modalHistoryDate').textContent = `Analysis on ${dateStr}`;
    $('modalHighestMatch').textContent = `${hm.toFixed(2)}%`;
    $('modalTotalFiles').textContent = data.fileNames.length;
    
    const pairsList = $('modalPairsList');
    pairsList.innerHTML = "";
    
    const fullResult = data.fullResultJson;
    if (fullResult && fullResult.pairs && fullResult.pairs.length > 0) {
      fullResult.pairs.forEach(pair => {
        const sim = pair.similarityScore || 0; // Already 0-100 from HistoricalPairDTO
        const simDisplay = Number.isInteger(sim) ? sim : parseFloat(sim.toFixed(2));
        let badgeClass = "low";
        if (sim >= 80) badgeClass = "high";
        else if (sim >= 50) badgeClass = "medium";
        
        const fileA = (pair.fileA && pair.fileA.name) ? pair.fileA.name : "File A";
        const fileB = (pair.fileB && pair.fileB.name) ? pair.fileB.name : "File B";
        
        const pairEl = document.createElement('div');
        pairEl.className = "card";
        pairEl.style.cssText = "padding: 16px; display: flex; justify-content: space-between; align-items: center; border: 1px solid var(--border); flex-wrap: wrap; gap: 16px; flex-shrink: 0;";
        pairEl.innerHTML = `
          <div style="flex: 1; min-width: 0;">
            <div style="font-weight: 500; color: var(--navy); margin-bottom: 4px; display: flex; align-items: center; gap: 8px;">
              <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0;" title="${escapeHtml(fileA)}">${escapeHtml(fileA)}</span>
              <span style="flex-shrink: 0;">&harr;</span>
              <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0;" title="${escapeHtml(fileB)}">${escapeHtml(fileB)}</span>
            </div>
            <span class="sim-badge ${badgeClass}">${simDisplay}% Match</span>
          </div>
          <div style="display: flex; gap: 8px; flex-shrink: 0; align-items: center;">
            <a href="${API_BASE}/api/history/${historyId}/report/${encodeURIComponent(pair.id)}/export?format=pdf" target="_blank" class="btn btn-secondary" style="padding: 6px 12px; font-size: 13px;">Download PDF</a>
            <button class="btn btn-primary btn-view-report" style="padding: 6px 12px; font-size: 13px;">View Report</button>
          </div>
        `;
        
        pairEl.querySelector('.btn-view-report').addEventListener('click', async (e) => {
           const btn = e.currentTarget;
           const originalHtml = btn.innerHTML;
           btn.innerHTML = `<div class="btn-spinner"></div> Loading...`;
           btn.disabled = true;
           try {
             await openDiffViewer(pair.id, historyId);
           } finally {
             btn.innerHTML = originalHtml;
             btn.disabled = false;
           }
        });
        
        pairsList.appendChild(pairEl);
      });
    } else {
      pairsList.innerHTML = "<div style='color: var(--text-secondary); text-align: center; padding: 20px;'>No pair details found in this record.</div>";
    }
    
    const modal = $('historyDetailsModal');
    modal.classList.remove('hidden');
    
    // Close events
    const closeBtn = $('closeHistoryModal');
    const handleClose = () => {
      modal.classList.add('hidden');
      closeBtn.removeEventListener('click', handleClose);
    };
    closeBtn.addEventListener('click', handleClose);
    
    modal.addEventListener('click', (e) => {
      if (e.target === modal) handleClose();
    });
    
  } catch (err) {
    console.error("Modal failed", err);
    alert("Failed to load full analysis details.");
  }
}
