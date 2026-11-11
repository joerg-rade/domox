/**
 * Headless-Chromium assertion client for scripts/verify-ontology-render.sh
 *
 * Asserts that the FORCE-LAYOUT branch of `d3-integration.mjs` draws the multi-line ontology node
 * label — in particular the fourth line, the source reference ontology (gist, Schema.org, …) that the
 * payload carries per node.  The server-side mapping is pinned by `RdfGraphDiagramsTest`; this is
 * about the client actually drawing it.
 *
 * Usage:  node scripts/verify-ontology-render.mjs <cdp-port>
 * Exit:   0 = all assertions hold, 1 = at least one failed, 2 = usage/connection error.
 */
const port = process.argv[2];
if (!port) {
    console.error('usage: node scripts/verify-ontology-render.mjs <cdp-port>');
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

// Unlike the static UML renderer, this path starts a d3-force simulation; give it time to settle.
await sleep(2000);

// ---- measurement ------------------------------------------------------------

const MEASURE = `(() => {
  const svg = document.querySelector('#d3CanvasFixture svg');
  if (!svg) return JSON.stringify({ error: 'the renderer produced no svg' });
  const groups = Array.from(svg.querySelectorAll('g.d3-nodes > g'));
  return JSON.stringify({
    groups: groups.length,
    nodes: groups.map(g => {
      const tspans = Array.from(g.querySelectorAll('tspan'));
      return {
        text: (g.querySelector('text') || {}).textContent || '',
        lines: tspans.map(t => ({
          text: t.textContent,
          y: Math.round(t.getBBox().y),
          italic: getComputedStyle(t).fontStyle === 'italic'
        }))
      };
    })
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
    console.error(`ONTOLOGY RENDER CHECK FAILED: ${m.error}`);
    process.exit(1);
}

const byName = {};
// The <text> element's textContent concatenates every tspan, so key each node by its FIRST label
// line — the node's name (the fixture sets no subdomain, so the name is the first line).
m.nodes.forEach(n => { if (n.lines.length) byName[n.lines[0].text] = n; });
const lineTexts = n => n ? n.lines.map(l => l.text) : [];

check('one node group per node', m.groups === 3, String(m.groups));

const gist = byName['Agreement'];
check('the gist node is drawn', !!gist);
check('it shows its name, its kind and its source ontology',
    JSON.stringify(lineTexts(gist)) === JSON.stringify(['Agreement', '<<Class>>', 'gist']),
    JSON.stringify(lineTexts(gist)));

const schema = byName['Person'];
check('the Schema.org node shows its source ontology',
    lineTexts(schema).includes('Schema.org'), JSON.stringify(lineTexts(schema)));

const local = byName['LocalThing'];
check('a node with no ontology draws no fourth line',
    !!local && lineTexts(local).length === 2 && lineTexts(local).every(t => t !== 'gist' && t !== 'Schema.org'),
    JSON.stringify(lineTexts(local)));

// The ontology line must sit BELOW the <<POS>> line, and be italic (it is a provenance note, not the
// node's name).
if (gist) {
    const pos = gist.lines.find(l => l.text === '<<Class>>');
    const onto = gist.lines.find(l => l.text === 'gist');
    check('the ontology line sits below the kind line',
        !!pos && !!onto && onto.y > pos.y, pos && onto ? `pos.y=${pos.y} onto.y=${onto.y}` : 'missing line');
    check('the ontology line is italic', !!onto && onto.italic);
}

if (failures.length) {
    console.error(`\nONTOLOGY RENDER CHECK FAILED (${failures.length}): ${failures.join('; ')}`);
    process.exit(1);
}
console.log('OK: the ontology renderer draws the node label with its source ontology on the fourth line.');
process.exit(0);
