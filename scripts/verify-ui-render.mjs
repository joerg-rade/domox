/**
 * Headless-Chromium measurement client for scripts/verify-ui-render.sh
 *
 * Asserts the USER-VISIBLE properties of the D3 graph column in a list/table, which is the
 * class of bug a CSS review cannot catch — see the "empty column" incident: the panel was
 * correctly sized (96x32) while the graph's CONTENT was drawn 343px to the right of the
 * clipped cell, i.e. nothing was on screen.  Asserting the panel's box would have passed.
 *
 * Usage:  node scripts/verify-ui-render.mjs <cdp-port>
 * Exit:   0 = all assertions hold, 1 = at least one failed, 2 = usage/connection error.
 */
const port = process.argv[2];
if (!port) {
    console.error('usage: node scripts/verify-ui-render.mjs <cdp-port>');
    process.exit(2);
}

// ---- CDP plumbing -----------------------------------------------------------

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function firstPageTarget() {
    for (let i = 0; i < 40; i++) {
        try {
            const res = await fetch(`http://127.0.0.1:${port}/json`);
            const page = (await res.json()).find(t => t.type === 'page' && t.webSocketDebuggerUrl);
            if (page) return page;
        } catch { /* not up yet */ }
        await sleep(250);
    }
    throw new Error(`no CDP page target on port ${port}`);
}

const target = await firstPageTarget();
const ws = new WebSocket(target.webSocketDebuggerUrl);
let seq = 0;
const pending = new Map();
const send = (method, params) => new Promise(resolve => {
    const id = ++seq;
    pending.set(id, resolve);
    ws.send(JSON.stringify({ id, method, params }));
});
ws.addEventListener('message', ev => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg.result); pending.delete(msg.id); }
});
await new Promise(r => ws.addEventListener('open', r));
await send('Runtime.enable', {});

// ---- measurement ------------------------------------------------------------

const MEASURE = `(() => {
  const td = document.querySelector('td[class*="-d3Graph"]');
  const panel = td.querySelector('.d3GraphPanel');
  const svg = panel.querySelector('svg');
  const g = svg.querySelector('g');
  const cs = getComputedStyle(panel);
  const pr = panel.getBoundingClientRect();
  let visibleW = 0, visibleH = 0, contentW = 0, contentH = 0;
  if (g) {
    const bb = g.getBBox();
    const m = g.getScreenCTM();
    const pt = svg.createSVGPoint();
    const xs = [], ys = [];
    [[bb.x, bb.y], [bb.x + bb.width, bb.y], [bb.x, bb.y + bb.height], [bb.x + bb.width, bb.y + bb.height]]
      .forEach(([x, y]) => { pt.x = x; pt.y = y; const s = pt.matrixTransform(m); xs.push(s.x); ys.push(s.y); });
    const l = Math.min(...xs), r = Math.max(...xs), t = Math.min(...ys), b = Math.max(...ys);
    contentW = r - l; contentH = b - t;
    visibleW = Math.min(pr.right, r) - Math.max(pr.left, l);
    visibleH = Math.min(pr.bottom, b) - Math.max(pr.top, t);
  }
  return JSON.stringify({
    panelW: Math.round(pr.width), panelH: Math.round(pr.height), position: cs.position,
    contentW: Math.round(contentW), contentH: Math.round(contentH),
    visibleW: Math.round(visibleW), visibleH: Math.round(visibleH),
    rowH: Math.round(td.closest('tr').getBoundingClientRect().height)
  });
})()`;

const measure = async () => {
    const out = await send('Runtime.evaluate', { expression: MEASURE, returnByValue: true });
    return JSON.parse(out.result.value);
};

const hover = async selector => {
    const out = await send('Runtime.evaluate', {
        expression: `(() => { const b = document.querySelector('${selector}').getBoundingClientRect();
            return JSON.stringify({ x: Math.round(b.x + b.width / 2), y: Math.round(b.y + b.height / 2) }); })()`,
        returnByValue: true
    });
    const { x, y } = JSON.parse(out.result.value);
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y, buttons: 0 });
    await sleep(300);
};

// ---- assertions -------------------------------------------------------------

const failures = [];
const check = (label, ok, detail) => {
    console.log(`  [${ok ? 'OK' : 'FAIL'}] ${label}${detail ? '  — ' + detail : ''}`);
    if (!ok) failures.push(label);
};
const fmt = m => `panel=${m.panelW}x${m.panelH} ${m.position} content=${m.contentW}x${m.contentH} `
    + `visible=${m.visibleW}x${m.visibleH} row=${m.rowH}`;

await hover('td.causeway-domox-ClassCdd-candidateName');       // start away from the graph cell
const teaser = await measure();
console.log('  teaser: ' + fmt(teaser));
check('teaser shows the graph inside the cell', teaser.visibleW > 4 && teaser.visibleH > 4);
check('teaser does not blow up the row', teaser.rowH <= 120, `row=${teaser.rowH}px`);

await hover('td.causeway-domox-ClassCdd-candidateName');       // a non-graph cell again
const otherCell = await measure();
check('popup stays closed over a non-graph column', otherCell.position === 'static');

await hover('td[class*="-d3Graph"]');
const popup = await measure();
console.log('  hover : ' + fmt(popup));
check('popup opens over the graph column', popup.position === 'absolute');
check('popup is large enough to read', popup.panelW >= 600 && popup.panelH >= 500,
    `${popup.panelW}x${popup.panelH}`);
check('popup shows the graph', popup.visibleW > 100 && popup.visibleH > 100);
check('popup does not change the row height', Math.abs(popup.rowH - teaser.rowH) <= 2,
    `${teaser.rowH} -> ${popup.rowH}`);

if (failures.length) {
    console.error(`\nUI RENDER CHECK FAILED (${failures.length}): ${failures.join('; ')}`);
    process.exit(1);
}
console.log('OK: table-cell graph rendering behaves as expected.');
process.exit(0);
