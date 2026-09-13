const state = { records: new Map(), channels: new Map(), decodeTimer: null, reader: null };
const $ = id => document.getElementById(id);

function setStatus(text) { $("status").textContent = text; }
function esc(value) { return String(value ?? "").replaceAll('"', '""'); }
function redraw() {
  const all = [...state.records.values()];
  $("wifiCount").textContent = all.filter(x => x.type === "wifi").length;
  $("bleCount").textContent = all.filter(x => x.type === "ble" || x.type === "bluetooth").length;
  $("gpsCount").textContent = all.filter(x => x.type === "gps").length;
  $("recordCount").textContent = all.length;
  $("records").textContent = all.length ? all.map(x => {
    const name = x.ssid || x.name || x.mac || x.address || "signal";
    return `${String(x.type || "signal").toUpperCase().padEnd(10)} ${String(x.rssi ?? "").padStart(4)} dBm  ${name}`;
  }).join("\n") : "Waiting for passive observations...";
  $("channels").textContent = state.channels.size ? [...state.channels.entries()].sort((a,b)=>a[0]-b[0]).map(([ch,n]) => `CH ${String(ch).padEnd(3)} ${"#".repeat(Math.min(n,18))} ${n} AP`).join("\n") : "No channel activity yet";
}
function decode(label) {
  clearTimeout(state.decodeTimer);
  const chars = "01ZX7#@$%&";
  let frame = 0;
  const tick = () => {
    const shown = [...label].map((ch, i) => i < frame ? ch : chars[(i + frame) % chars.length]).join("");
    $("decode").textContent = shown;
    frame++;
    if (frame <= label.length + 3) state.decodeTimer = setTimeout(tick, 90); else state.decodeTimer = setTimeout(() => $("decode").textContent = "", 420);
  };
  tick();
}
function ingest(message) {
  const key = message.bssid || message.mac || message.address || `${message.type}:${message.ssid || message.name}`;
  const isNew = !state.records.has(key);
  state.records.set(key, message);
  if (message.channel) state.channels.set(message.channel, (state.channels.get(message.channel) || 0) + 1);
  if (isNew) decode(message.ssid || message.name || message.mac || message.address || "signal");
  redraw();
}
async function connect() {
  if (!("serial" in navigator)) { setStatus("Web Serial unavailable // use Chrome or Edge"); return; }
  const port = await navigator.serial.requestPort();
  await port.open({ baudRate: 115200 });
  setStatus("ONLINE // passive stream connected");
  const decoder = new TextDecoderStream();
  port.readable.pipeTo(decoder.writable);
  state.reader = decoder.readable.getReader();
  let buffer = "";
  while (true) {
    const { value, done } = await state.reader.read(); if (done) break;
    buffer += value;
    const lines = buffer.split("\n"); buffer = lines.pop();
    for (const line of lines) { try { const obj = JSON.parse(line.trim()); if (obj.type) ingest(obj); else if (obj.event) setStatus(`${obj.event.toUpperCase()} // passive stream`); } catch {} }
  }
}
function exportCsv() {
  const rows = [["MAC","SSID","AUTH","CHANNEL","RSSI","TYPE","NAME"]];
  for (const x of state.records.values()) rows.push([x.bssid || x.mac || x.address || "", x.ssid || "", x.encryption || x.security || "", x.channel || "", x.rssi || "", x.type || "", x.name || ""]);
  const csv = rows.map(row => row.map(esc).map(v => `"${v}"`).join(",")).join("\n");
  const blob = new Blob([csv], { type:"text/csv" }); const link = document.createElement("a"); link.href = URL.createObjectURL(blob); link.download = "wigglefish-wardrive.csv"; link.click(); URL.revokeObjectURL(link.href);
}
$("connectButton").onclick = () => connect().catch(error => setStatus(`OFFLINE // ${error.message}`));
$("clearButton").onclick = () => { state.records.clear(); state.channels.clear(); redraw(); $("decode").textContent = ""; };
$("exportButton").onclick = exportCsv;

const canvas = $("radar"), ctx = canvas.getContext("2d"); let angle = 0;
function radar() { const w=canvas.width,h=canvas.height,cx=w/2,cy=h/2,r=Math.min(w,h)*.38; ctx.clearRect(0,0,w,h); ctx.strokeStyle="#237b91"; for(let i=1;i<=4;i++){ctx.beginPath();ctx.arc(cx,cy,r*i/4,0,Math.PI*2);ctx.stroke();} ctx.strokeStyle="#ff4fd8"; ctx.lineWidth=4; ctx.beginPath();ctx.moveTo(cx,cy);ctx.lineTo(cx+Math.cos(angle)*r,cy+Math.sin(angle)*r);ctx.stroke();ctx.fillStyle="#8ff7ff";[...state.records.values()].slice(0,18).forEach((_,i)=>{const a=i*.82,d=r*(.25+(i%5)*.14);ctx.beginPath();ctx.arc(cx+Math.cos(a)*d,cy+Math.sin(a)*d,5,0,Math.PI*2);ctx.fill();}); angle+=.025; requestAnimationFrame(radar); } radar(); redraw();