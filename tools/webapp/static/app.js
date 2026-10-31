const $ = (s, r = document) => r.querySelector(s);
const el = (html) => { const t = document.createElement("template"); t.innerHTML = html.trim(); return t.content.firstChild; };
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const api = (p, opt) => fetch(p, opt).then(r => r.json());

let maps = [], sprites = [], sel = { map: null, sprite: null };

function toast(msg) {
  let t = $(".toast") || document.body.appendChild(el('<div class="toast"></div>'));
  t.textContent = msg; t.classList.add("show");
  clearTimeout(t._t); t._t = setTimeout(() => t.classList.remove("show"), 2200);
}

function setName(id, name) {
  return api("/api/names", { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id, name }) }).then(() => toast("Đã lưu tên"));
}

/* ------- navigation ------- */
document.querySelectorAll(".nav").forEach(b => b.onclick = () => {
  document.querySelectorAll(".nav").forEach(n => n.classList.toggle("active", n === b));
  document.querySelectorAll(".view").forEach(v => v.classList.add("hidden"));
  $("#view-" + b.dataset.view).classList.remove("hidden");
});

/* ------- maps ------- */
function renderMapList(filter = "") {
  const list = $("#map-list"); list.innerHTML = "";
  const key = filter.toLowerCase();
  maps.filter(m => (m.name + " " + m.mapId).toLowerCase().includes(key)).forEach(m => {
    const row = el(`<div class="row" data-idx="${m.idx}">
      <span class="title">${m.kind === "orig" ? "★ " : ""}${esc(m.name)}</span>
      <span class="meta">${m.w}×${m.h} · <span class="tag npc">${m.npc}</span> <span class="tag mon">${m.monster}</span></span>
    </div>`);
    row.onclick = () => selectMap(m.idx, row);
    list.appendChild(row);
  });
}

function selectMap(idx, row) {
  document.querySelectorAll("#map-list .row").forEach(r => r.classList.toggle("sel", r === row));
  api("/api/map/" + idx).then(d => {
    sel.map = d;
    const rows = (arr, cols) => arr.length
      ? `<table><tr>${cols.map(c => `<th>${c[0]}</th>`).join("")}</tr>${arr.slice(0, 200).map(o =>
          `<tr>${cols.map(c => `<td>${esc(c[1](o))}</td>`).join("")}</tr>`).join("")}</table>`
      : `<div class="empty" style="margin:8px">(không có)</div>`;
    $("#map-detail").innerHTML = `
      <div class="toolbar">
        <div class="name-edit">
          <input id="map-name" value="${esc(d.name)}">
          <button class="btn accent" id="map-save">Lưu tên</button>
        </div>
      </div>
      <h2>${esc(d.name)}</h2>
      <div class="sub">mapId ${esc(d.mapId)} · ${d.size[0]}×${d.size[1]} · ${d.kind === "orig" ? "map gốc" : "map .map"}</div>
      <div class="canvas-wrap"><img src="/api/map/${idx}.png?t=${Date.now()}" alt="map"></div>
      <div class="grid2">
        <div class="card"><h3>Quái (${d.monsters.length})</h3>${rows(d.monsters,
          [["Tên", o => o.name], ["Lv", o => o.level], ["HP", o => (o.hp ?? "") + "/" + (o.maxHp ?? "")], ["Ô", o => o.x + "," + o.y]])}</div>
        <div class="card"><h3>NPC (${d.npcs.length})</h3>${rows(d.npcs,
          [["Tên", o => o.name], ["Lv", o => o.level], ["Ô", o => o.x + "," + o.y]])}</div>
        <div class="card"><h3>Portal (${d.portals.length})</h3>${rows(d.portals,
          [["→ Map", o => o.targetMap ?? o.toMap ?? ""], ["Ô", o => (o.x ?? "") + "," + (o.y ?? "")]])}</div>
        <div class="card"><h3>Spawn (${d.spawns.length})</h3>${rows(d.spawns,
          [["Kind", o => o.kind], ["Template", o => o.template], ["Ô", o => o.x + "," + o.y]])}</div>
      </div>`;
    $("#map-save").onclick = () => setName("map:" + d.mapId, $("#map-name").value).then(load);
  });
}

/* ------- sprites ------- */
let anim = { timer: null, frames: [], i: 0, id: null };
function stopAnim() { if (anim.timer) clearInterval(anim.timer); anim.timer = null; }

function renderSpriteList(filter = "") {
  const list = $("#sprite-list"); list.innerHTML = "";
  const key = filter.toLowerCase();
  sprites.filter(s => (s.name + " " + s.id).toLowerCase().includes(key)).forEach(s => {
    const row = el(`<div class="row" data-id="${s.id}">
      <span class="title">${esc(s.name)}</span>
      <span class="meta">${s.server ? "server · " : ""}${s.frames}f · ${s.actions.length} action</span></div>`);
    row.onclick = () => selectSprite(s, row);
    list.appendChild(row);
  });
}

function selectSprite(s, row) {
  document.querySelectorAll("#sprite-list .row").forEach(r => r.classList.toggle("sel", r === row));
  sel.sprite = s; stopAnim();
  $("#sprite-detail").innerHTML = `
    <div class="toolbar">
      <div class="name-edit"><input id="sp-name" value="${esc(s.name)}"><button class="btn accent" id="sp-save">Lưu tên</button></div>
      <select id="sp-action">${s.actions.map(a => `<option>${a}</option>`).join("")}</select>
      <button class="btn" id="sp-export">Export .ani / .sprite</button>
    </div>
    <h2>${esc(s.name)}</h2>
    <div class="sub">sprite ${s.id} · ${s.frames} khung · ${s.actions.length} action · ${s.server ? "khôi phục từ capture server" : "sprite gốc trong JAR"}</div>
    <div class="canvas-wrap"><img id="sp-img" alt="frame"></div>`;
  $("#sp-save").onclick = () => setName("sprite:" + s.id, $("#sp-name").value).then(load);
  $("#sp-export").onclick = () => api("/api/export/ani/" + s.id, { method: "POST" })
    .then(r => toast(r.ok ? "Đã export " + r.written.length + " file" : "Lỗi: " + r.error));
  $("#sp-action").onchange = playAction;
  playAction();
}

function playAction() {
  stopAnim();
  const s = sel.sprite, action = $("#sp-action").value;
  const frames = (s.actionFrames && s.actionFrames[action]) || Array.from({ length: s.frames }, (_, i) => i);
  anim.i = 0;
  const img = $("#sp-img");
  const step = () => { img.src = `/api/sprite/${s.id}/${frames[anim.i % frames.length]}.png?t=${Date.now()}`; anim.i++; };
  step(); anim.timer = setInterval(step, 100);
}

/* ------- boot ------- */
function load() {
  return Promise.all([api("/api/maps"), api("/api/sprites")]).then(([m, s]) => {
    maps = m; sprites = s;
    $("#stats").textContent = `${maps.length} maps · ${sprites.length} sprites`;
    renderMapList($("#map-search").value); renderSpriteList($("#sprite-search").value);
  });
}
// the index is built once per process; rebuild it after Thu-Du-Lieu or the monitor writes a new run
$("#reload").onclick = e => {
  e.target.disabled = true;
  api("/api/reload", { method: "POST" }).then(load).finally(() => { e.target.disabled = false; });
};
$("#map-search").oninput = e => renderMapList(e.target.value);
$("#sprite-search").oninput = e => renderSpriteList(e.target.value);
load();
