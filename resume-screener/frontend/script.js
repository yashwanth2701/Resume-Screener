const API_BASE = "http://localhost:8080/api";

const state = {
  resumes: [],
  jobs: [],
  selectedResumeIds: new Set(), // empty set = "match all resumes"
  selectedJobId: null,
};

// ---------- Bootstrapping ----------
document.addEventListener("DOMContentLoaded", () => {
  checkApiStatus();
  refreshResumes();
  refreshJobs();
  wireFileInput();
  wireUploadForm();
  wireJobForm();
  wireRunMatch();
});

async function checkApiStatus() {
  const pill = document.getElementById("apiStatus");
  try {
    const res = await fetch(`${API_BASE}/jobs`);
    if (!res.ok) throw new Error("bad status");
    pill.textContent = "API connected";
    pill.className = "status-pill status-ok";
  } catch (e) {
    pill.textContent = "API unreachable — start the Spring Boot app";
    pill.className = "status-pill status-down";
  }
}

// ---------- Resumes ----------
function wireFileInput() {
  const input = document.getElementById("fileInput");
  const label = document.getElementById("fileDropLabel");
  input.addEventListener("change", () => {
    label.textContent = input.files.length ? input.files[0].name : "Drop a resume or click to browse";
  });
}

function wireUploadForm() {
  const form = document.getElementById("uploadForm");
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const input = document.getElementById("fileInput");
    if (!input.files.length) return;

    const formData = new FormData();
    formData.append("file", input.files[0]);

    const submitBtn = form.querySelector("button[type=submit]");
    submitBtn.disabled = true;
    submitBtn.textContent = "Parsing…";

    try {
      const res = await fetch(`${API_BASE}/resumes/upload`, { method: "POST", body: formData });
      if (!res.ok) throw new Error(await res.text());
      input.value = "";
      document.getElementById("fileDropLabel").textContent = "Drop a resume or click to browse";
      await refreshResumes();
    } catch (err) {
      alert("Could not parse resume: " + err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = "Parse & store";
    }
  });
}

async function refreshResumes() {
  try {
    const res = await fetch(`${API_BASE}/resumes`);
    state.resumes = res.ok ? await res.json() : [];
  } catch {
    state.resumes = [];
  }
  renderResumes();
  updateRunMatchAvailability();
}

function renderResumes() {
  const container = document.getElementById("resumeList");
  if (!state.resumes.length) {
    container.innerHTML = `<p class="empty-hint">No resumes yet.</p>`;
    return;
  }

  container.innerHTML = "";
  state.resumes.forEach((r) => {
    const item = document.createElement("div");
    const isSelected = state.selectedResumeIds.has(r.id);
    item.className = "list-item selectable" + (isSelected ? " selected" : "");
    item.innerHTML = `
      <div class="list-item-main">
        <span class="list-item-title">${escapeHtml(r.candidateName || r.fileName)}</span>
        <span class="list-item-meta">${escapeHtml(r.fileName)} · ${r.email ? escapeHtml(r.email) : "no email found"}</span>
        <div class="chip-row">${(r.skills || []).slice(0, 6).map(s => `<span class="chip">${escapeHtml(s)}</span>`).join("")}</div>
      </div>
    `;
    item.addEventListener("click", () => {
      if (state.selectedResumeIds.has(r.id)) {
        state.selectedResumeIds.delete(r.id);
      } else {
        state.selectedResumeIds.add(r.id);
      }
      renderResumes();
    });
    container.appendChild(item);
  });
}

// ---------- Jobs ----------
function wireJobForm() {
  const form = document.getElementById("jobForm");
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const title = document.getElementById("jobTitle").value.trim();
    const description = document.getElementById("jobDescription").value.trim();
    const skillsRaw = document.getElementById("jobSkills").value.trim();
    const requiredSkills = skillsRaw ? skillsRaw.split(",").map(s => s.trim()).filter(Boolean) : [];

    const submitBtn = form.querySelector("button[type=submit]");
    submitBtn.disabled = true;

    try {
      const res = await fetch(`${API_BASE}/jobs`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title, description, requiredSkills }),
      });
      if (!res.ok) throw new Error(await res.text());
      form.reset();
      await refreshJobs();
    } catch (err) {
      alert("Could not save job: " + err.message);
    } finally {
      submitBtn.disabled = false;
    }
  });
}

async function refreshJobs() {
  try {
    const res = await fetch(`${API_BASE}/jobs`);
    state.jobs = res.ok ? await res.json() : [];
  } catch {
    state.jobs = [];
  }
  renderJobs();
  updateRunMatchAvailability();
}

function renderJobs() {
  const container = document.getElementById("jobList");
  if (!state.jobs.length) {
    container.innerHTML = `<p class="empty-hint">No roles saved yet.</p>`;
    return;
  }

  container.innerHTML = "";
  state.jobs.forEach((j) => {
    const item = document.createElement("div");
    const isSelected = state.selectedJobId === j.id;
    item.className = "list-item selectable" + (isSelected ? " selected" : "");
    item.innerHTML = `
      <div class="list-item-main">
        <span class="list-item-title">${escapeHtml(j.title)}</span>
        <span class="list-item-meta">${(j.description || "").length} chars · ${(j.requiredSkills || []).length} listed skills</span>
        <div class="chip-row">${(j.requiredSkills || []).slice(0, 6).map(s => `<span class="chip">${escapeHtml(s)}</span>`).join("")}</div>
      </div>
    `;
    item.addEventListener("click", () => {
      state.selectedJobId = state.selectedJobId === j.id ? null : j.id;
      renderJobs();
      updateRunMatchAvailability();
    });
    container.appendChild(item);
  });
}

function updateRunMatchAvailability() {
  document.getElementById("runMatchBtn").disabled = !state.selectedJobId;
}

// ---------- Matching ----------
function wireRunMatch() {
  document.getElementById("runMatchBtn").addEventListener("click", runMatching);
}

async function runMatching() {
  if (!state.selectedJobId) return;

  const loading = document.getElementById("matchLoading");
  const btn = document.getElementById("runMatchBtn");
  loading.classList.remove("hidden");
  btn.disabled = true;

  try {
    const res = await fetch(`${API_BASE}/match`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        jobDescriptionId: state.selectedJobId,
        resumeIds: Array.from(state.selectedResumeIds), // empty => all resumes, per API contract
      }),
    });
    if (!res.ok) throw new Error(await res.text());
    const results = await res.json();
    renderResults(results);
  } catch (err) {
    alert("Matching failed: " + err.message);
  } finally {
    loading.classList.add("hidden");
    btn.disabled = false;
  }
}

function renderResults(results) {
  const body = document.getElementById("resultsBody");
  if (!results.length) {
    body.innerHTML = `<tr><td colspan="5" class="empty-hint">No matches returned.</td></tr>`;
    return;
  }

  body.innerHTML = "";
  results.forEach((r) => {
    const meterClass = r.score >= 7 ? "" : r.score >= 4 ? "mid" : "low";
    const row = document.createElement("tr");
    row.innerHTML = `
      <td>${escapeHtml(r.candidateName || "Unknown")}</td>
      <td>
        <div class="score-cell">
          <span class="score-num">${r.score}</span>
          <div class="score-meter"><div class="score-meter-fill ${meterClass}" style="width:${r.score * 10}%"></div></div>
        </div>
      </td>
      <td class="justification-cell">${escapeHtml(r.justification || "")}</td>
      <td><div class="chip-row">${(r.matchedSkills || []).map(s => `<span class="chip">${escapeHtml(s)}</span>`).join("")}</div></td>
      <td><div class="chip-row">${(r.missingSkills || []).map(s => `<span class="chip">${escapeHtml(s)}</span>`).join("")}</div></td>
    `;
    body.appendChild(row);
  });
}

// ---------- Utils ----------
function escapeHtml(str) {
  if (str == null) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
