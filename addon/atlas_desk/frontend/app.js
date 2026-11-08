"use strict";
const TZ = "America/New_York";
const $ = (id) => document.getElementById(id);

const timeFmt = new Intl.DateTimeFormat("en-US", { timeZone: TZ, hour: "numeric", minute: "2-digit" });
const timeSecFmt = new Intl.DateTimeFormat("en-US", { timeZone: TZ, hour: "numeric", minute: "2-digit", second: "2-digit" });
const dayFmt = new Intl.DateTimeFormat("en-US", { timeZone: TZ, weekday: "short" });
const dateFmt = new Intl.DateTimeFormat("en-US", { timeZone: TZ, weekday: "long", month: "long", day: "numeric" });

function fmtTime(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  return isNaN(d) ? "—" : timeFmt.format(d);
}
function fmtDuration(s) {
  if (s == null) return null;
  s = Math.max(0, Math.round(s));
  const h = Math.floor(s / 3600), m = Math.round((s % 3600) / 60);
  if (h > 0) return `${h}h ${String(m).padStart(2, "0")}m`;
  if (m > 0) return `${m}m`;
  return `${s}s`;
}
function esc(t) {
  const d = document.createElement("div");
  d.textContent = t == null ? "" : String(t);
  return d.innerHTML;
}
function footUpdated(el, card) {
  if (card && card.stale) {
    el.textContent = card.stale_since ? `Stale since ${fmtTime(card.stale_since)}` : "Stale — no data yet";
    el.classList.add("stale");
  } else {
    el.textContent = `Updated ${timeFmt.format(new Date())}`;
    el.classList.remove("stale");
  }
}

/* ---------------- Printer ---------------- */
function renderPrinter(p) {
  const big = $("printer-big"), lines = $("printer-lines"),
        bar = $("printer-bar"), fill = $("printer-fill");
  if (!p || !p.available) {
    big.textContent = "Unavailable";
    bar.hidden = true;
    lines.innerHTML = `<div class="dim">The printer isn't reporting right now — it may be off or asleep.</div>`;
  } else {
    const state = p.state ? p.state[0].toUpperCase() + p.state.slice(1) : "Unknown";
    big.textContent = (p.state === "printing" && p.progress != null)
      ? `Printing · ${Math.round(p.progress)}%` : state;
    if (p.progress != null && (p.state === "printing" || p.state === "paused" || p.state === "finished")) {
      bar.hidden = false;
      fill.style.width = `${Math.min(100, Math.max(0, p.progress))}%`;
    } else bar.hidden = true;
    const rows = [];
    if (p.job_name) rows.push(`<div>${esc(p.job_name)}</div>`);
    const left = fmtDuration(p.time_remaining_s);
    if (p.state === "printing" && left) rows.push(`<div class="dim">≈${left} left${p.eta ? ` · ETA ${fmtTime(p.eta)}` : ""}</div>`);
    if (p.nozzle_c != null || p.bed_c != null)
      rows.push(`<div class="dim">Nozzle ${p.nozzle_c != null ? Math.round(p.nozzle_c) + "°C" : "—"} · Bed ${p.bed_c != null ? Math.round(p.bed_c) + "°C" : "—"}</div>`);
    if (p.queued != null)
      rows.push(`<div class="dim">Slicer queue: ${p.queued} waiting${p.active_jobs ? ` · ${p.active_jobs} running` : ""}</div>`);
    lines.innerHTML = rows.join("");
  }
  footUpdated($("printer-foot"), p);
}

/* ---------------- House ---------------- */
function renderHouse(h) {
  const big = $("house-big"), lines = $("house-lines");
  big.textContent = h && h.temp_f != null ? `${Math.round(h.temp_f)}°` : "—";
  if (!h || (h.temp_f == null && !h.mode)) {
    lines.innerHTML = `<div class="dim">No house data yet.</div>`;
  } else {
    const rows = [];
    const bits = [];
    if (h.target_f != null) bits.push(`Set ${Math.round(h.target_f)}°`);
    if (h.mode) bits.push(h.mode[0].toUpperCase() + h.mode.slice(1));
    if (h.hvac_action) bits.push(h.hvac_action === "idle" ? "Idle" : h.hvac_action);
    if (bits.length) rows.push(`<div class="dim">${esc(bits.join(" · "))}</div>`);
    const who = [];
    if (h.jon) who.push(`Jon ${h.jon === "home" ? "home" : "away"}`);
    if (h.jenna) who.push(`Jenna ${h.jenna === "home" ? "home" : "away"}`);
    if (who.length) rows.push(`<div>${esc(who.join(" · "))}</div>`);
    if (h.summary) rows.push(`<div>${esc(h.summary)}</div>`);
    lines.innerHTML = rows.join("");
  }
  footUpdated($("house-foot"), h);
}

/* ---------------- Shop ---------------- */
function money(v) { return v == null ? null : `$${Number(v).toFixed(2)}`; }
function renderShop(s) {
  const big = $("shop-big"), lines = $("shop-lines"), foot = $("shop-foot");
  foot.classList.remove("stale");
  if (!s || s.status !== "ok") {
    big.textContent = "—";
    lines.innerHTML = `<div class="dim">No snapshot yet — the shop card fills in with the morning brief.</div>`;
    foot.textContent = "No snapshot yet";
    return;
  }
  big.textContent = s.visits_7d != null ? `${s.visits_7d} visits · 7 days` : "Shop";
  const rows = [];
  if (s.visits_yesterday != null || s.orders_yesterday != null)
    rows.push(`<div>Yesterday: ${s.visits_yesterday ?? "—"} visits · ${s.orders_yesterday ?? "—"} orders${s.revenue_yesterday != null ? ` · ${money(s.revenue_yesterday)}` : ""}</div>`);
  if (s.orders_7d != null)
    rows.push(`<div>Orders (7d): ${s.orders_7d}${s.revenue_7d != null ? ` · ${money(s.revenue_7d)}` : ""}</div>`);
  if (s.active_listings != null)
    rows.push(`<div class="dim">${s.active_listings} active listings</div>`);
  if (s.ads_spend_30d != null)
    rows.push(`<div class="dim">Ads (30d): ${money(s.ads_spend_30d)} spent · ${s.ads_orders_30d ?? "—"} orders</div>`);
  lines.innerHTML = rows.join("");
  foot.textContent = s.snapshot_at ? `Snapshot ${fmtTime(s.snapshot_at)}` : "";
}

/* ---------------- Calendar ---------------- */
function eventWhen(ev) {
  if (ev.all_day) {
    const d = new Date(ev.start + "T12:00:00");
    return isNaN(d) ? "" : dayFmt.format(d);
  }
  const d = new Date(ev.start);
  if (isNaN(d)) return "";
  return `${dayFmt.format(d)} ${timeFmt.format(d)}`;
}
function renderCalendar(c) {
  const lines = $("calendar-lines"), foot = $("calendar-foot");
  foot.classList.remove("stale");
  if (!c || c.status !== "ok" || !c.events || !c.events.length) {
    lines.innerHTML = `<div class="dim">Nothing scheduled in the next few days.</div>`;
    foot.textContent = c && c.snapshot_at ? `Snapshot ${fmtTime(c.snapshot_at)}` : "No snapshot yet";
    return;
  }
  lines.innerHTML = c.events.slice(0, 3).map((ev) =>
    `<div class="ev"><span class="when">${esc(eventWhen(ev))}</span> — ${esc(ev.summary)}${ev.location ? ` <span class="dim">· ${esc(ev.location)}</span>` : ""}</div>`
  ).join("");
  foot.textContent = c.snapshot_at ? `Snapshot ${fmtTime(c.snapshot_at)}` : "";
}

/* ---------------- Loop ---------------- */
async function refresh() {
  const pill = $("hdr-status");
  try {
    const r = await fetch("/api/deck", { cache: "no-store" });
    if (!r.ok) throw new Error("bad status");
    const d = await r.json();
    renderPrinter(d.printer);
    renderHouse(d.house);
    renderShop(d.shop);
    renderCalendar(d.calendar);
    const stale = (d.printer && d.printer.stale) || (d.house && d.house.stale);
    pill.textContent = stale ? "Stale" : "Live";
    pill.classList.toggle("bad", !!stale);
  } catch (e) {
    pill.textContent = "Deck unreachable";
    pill.classList.add("bad");
  }
}

function tickClock() {
  const now = new Date();
  $("clock").textContent = timeSecFmt.format(now);
  $("dateline").textContent = dateFmt.format(now);
  const hourET = Number(new Intl.DateTimeFormat("en-US", { timeZone: TZ, hour: "numeric", hour12: false }).format(now));
  document.body.classList.toggle("night", hourET >= 22 || hourET < 7);
}

/* Kiosk helpers: wake lock + tap-header fullscreen */
let wakeLock = null;
async function keepAwake() {
  try { wakeLock = await navigator.wakeLock.request("screen"); } catch (e) { /* unsupported */ }
}
document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") keepAwake(); });
$("hdr").addEventListener("click", () => {
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  else document.documentElement.requestFullscreen().catch(() => {});
});

if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => {});
tickClock();
setInterval(tickClock, 1000);
refresh();
setInterval(refresh, 20000);
keepAwake();
