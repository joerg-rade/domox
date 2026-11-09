/**
 * Headless-Chromium assertion client for scripts/verify-uml-render.sh
 *
 * Asserts that the STATIC UML branch of `d3-integration.mjs` draws what the payload says: one box per
 * node, one path per connector, an arrow marker on every generalization, the legend, and the payload's
 * own canvas as the SVG viewBox.  The *geometry* is asserted over the real model by
 * `UmlLayoutIntegrityTest`; this is about the client not dropping any of it.
 *
 * Usage:  node scripts/verify-uml-render.mjs <cdp-port>
 * Exit:   0 = all assertions hold, 1 = at least one failed, 2 = usage/connection error.
 */
const port = process.argv[2];
if (!port) {
    console.error('usage: node scripts/verify-uml-render.mjs <cdp-port>');
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

// The static renderer runs synchronously inside init(); wait a beat regardless.
await sleep(400);

// ---- measurement ------------------------------------------------------------

const MEASURE = `(() => {
  const svg = document.querySelector('#d3CanvasFixture svg');
  if (!svg) return JSON.stringify({ error: 'the renderer produced no svg' });
  const layer = svg.querySelector('g.uml-layer');
  const paths = layer ? Array.from(layer.querySelectorAll('path')) : [];
  const legend = svg.querySelector('g.uml-legend');
  return JSON.stringify({
    viewBox: svg.getAttribute('viewBox'),
    boxes: layer ? layer.querySelectorAll('g.uml-class').length : 0,
    boxNames: layer ? Array.from(layer.querySelectorAll('g.uml-class text')).map(t => t.textContent) : [],
    paths: paths.length,
    arrowed: paths.filter(p => p.getAttribute('marker-end')).length,
    strokes: paths.map(p => p.getAttribute('stroke')),
    labels: layer ? Array.from(layer.querySelectorAll('g.uml-class')).map(g => {
        const t = g.querySelector('text'), r = g.querySelector('rect');
        return { name: t.textContent, text: t.getComputedTextLength(),
                 box: parseFloat(r.getAttribute('width')), font: parseFloat(getComputedStyle(t).fontSize) };
    }) : [],
    linkLabels: layer ? Array.from(layer.querySelectorAll('text'))
        .filter(t => t.textContent === 'rel')
        .map(t => ({ x: parseFloat(t.getAttribute('x')), y: parseFloat(t.getAttribute('y')),
                     anchor: t.getAttribute('text-anchor'), stroke: t.getAttribute('stroke'),
                     paintOrder: t.getAttribute('paint-order') })) : [],
    legend: !!legend,
    legendRows: legend ? legend.querySelectorAll('text').length : 0
  });
})()`;

const out = await send('Runtime.evaluate', { expression: MEASURE, returnByValue: true });
const m = JSON.parse(out.result.value);

// ---- assertions -------------------------------------------------------------

const failures = [];
const check = (label, ok, detail) => {
    console.log(`  [${ok ? 'OK' : 'FAIL'}] ${label}${detail ? '  — ' + detail : ''}`);
    if (!ok) failures.push(label);
};

if (m.error) {
    console.error(`UML RENDER CHECK FAILED: ${m.error}`);
    process.exit(1);
}

check('the SVG uses the payload canvas as its viewBox', m.viewBox === '0 0 600 400', m.viewBox);
check('one box per node', m.boxes === 3, String(m.boxes));
check('the boxes carry their names',
    JSON.stringify(m.boxNames) === JSON.stringify(['Alpha', 'Beta', 'ServiceProviderOnNegotiation']),
    JSON.stringify(m.boxNames));
check('one path per connector', m.paths === 3, String(m.paths));
check('only the connector that carries the arrow draws one', m.arrowed === 1, String(m.arrowed));
check('the arrow is orange and the association grey',
    m.strokes.includes('#e67e22') && m.strokes.includes('#7f8c8d'), JSON.stringify(m.strokes));

const overflowing = m.labels.filter(l => l.text > l.box + 0.5);
check('every label fits inside its box (the long one is shrunk to fit)',
    overflowing.length === 0,
    overflowing.map(l => `${l.name}: ${Math.round(l.text)} > ${l.box}`).join('; '));

const tooSmall = m.labels.filter(l => l.font < 8);
check('a shrunk label stays readable (font >= 8px)', tooSmall.length === 0,
    tooSmall.map(l => `${l.name}: ${l.font.toFixed(1)}px`).join('; '));

// The fixture's association runs [[40,70],[40,290]] and carries the label "rel": it must hang on the
// middle of that run (y≈180), not on the end point (y=70 or 290), which is where a naive
// points[floor(n/2)] anchor puts it.
const rel = m.linkLabels[0];
check('the connector label is drawn', !!rel);
check('the connector label sits on the middle of its run, clear of both ends',
    !!rel && Math.abs(rel.y - 180) <= 10 && Math.abs(rel.y - 70) > 50 && Math.abs(rel.y - 290) > 50,
    rel ? `y=${rel.y} anchor=${rel.anchor}` : 'no connector label found');
check('the connector label carries a white halo, so a line behind it cannot hide the text',
    !!rel && rel.stroke === '#ffffff' && rel.paintOrder === 'stroke',
    rel ? `stroke=${rel.stroke} paint-order=${rel.paintOrder}` : 'no connector label found');

check('the legend is drawn', m.legend);
check('the legend explains all three kinds', m.legendRows === 3, String(m.legendRows));

if (failures.length) {
    console.error(`\nUML RENDER CHECK FAILED (${failures.length}): ${failures.join('; ')}`);
    process.exit(1);
}
console.log('OK: the static UML renderer draws boxes, arrows and the legend.');
process.exit(0);
