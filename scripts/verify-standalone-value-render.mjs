/**
 * Headless-Chromium measurement client for scripts/verify-standalone-value-render.sh
 *
 * Asserts the USER-VISIBLE property that a multi-line String action result keeps its line breaks,
 * which is the class of bug no server-side check can see: the PlantUML export was verified correct
 * on the wire (53 lines, real "\n") while the page showed it as ONE long line, because the viewer
 * renders such a result through the COMPACT label fragment
 * (<span class="fragment-compact-label">) and the browser default `white-space: normal` collapsed
 * every newline.  The fixture therefore carries the REAL markup shape, taken from the viewer's own
 * templates (div.standaloneValuePanel > span.standaloneValue > div.scalarNameAndValueComponentType >
 * span.fragment-compact-label), and the REAL stylesheet, and measures the number of LINE BOXES the
 * text renders as.
 *
 * Usage:  node scripts/verify-standalone-value-render.mjs <cdp-port> <expected-lines>
 * Exit:   0 = all assertions hold, 1 = at least one failed, 2 = usage/connection error.
 */
const port = process.argv[2];
const expected = Number(process.argv[3]);
if (!port || !Number.isFinite(expected)) {
    console.error('usage: node scripts/verify-standalone-value-render.mjs <cdp-port> <expected-lines>');
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

// Line boxes, not heights: a Range over the text node yields client rects per rendered line, which
// is the property under test (a collapsed value yields exactly 1).  Chrome splits those rects
// further at whitespace runs, so the LINE COUNT is the number of DISTINCT line tops — a rect count
// would over-count (19 source lines yielded 27 rects).  Heights would need the font metrics and
// would silently pass a 1-line render inside a tall box.
const MEASURE = `(() => {
  const lines = el => {
    const r = document.createRange();
    r.selectNodeContents(el);
    return new Set([...r.getClientRects()]
        .filter(x => x.height > 0)
        .map(x => Math.round(x.top))).size;
  };
  const subject = document.getElementById('subject');
  const control = document.getElementById('control');
  const inCell  = document.getElementById('inCell');
  return JSON.stringify({
    subjectLines: lines(subject),
    controlLines: lines(control),
    inCellLines:  lines(inCell),
    subjectWhiteSpace: getComputedStyle(subject).whiteSpace,
    controlWhiteSpace: getComputedStyle(control).whiteSpace,
    inCellWhiteSpace:  getComputedStyle(inCell).whiteSpace,
    subjectH: Math.round(subject.getBoundingClientRect().height),
    controlH: Math.round(control.getBoundingClientRect().height),
  });
})()`;

const out = await send('Runtime.evaluate', { expression: MEASURE, returnByValue: true });
if (out.exceptionDetails) {
    console.error('measurement threw: ' + JSON.stringify(out.exceptionDetails));
    process.exit(2);
}
const m = JSON.parse(out.result.value);

// ---- assertions -------------------------------------------------------------

const failures = [];
const check = (label, ok, detail) => {
    console.log(`  [${ok ? 'OK' : 'FAIL'}] ${label}${detail ? '  — ' + detail : ''}`);
    if (!ok) failures.push(label);
};

console.log(`  subject: ${m.subjectLines} line box(es), white-space=${m.subjectWhiteSpace}, height=${m.subjectH}px`);
console.log(`  control: ${m.controlLines} line box(es), white-space=${m.controlWhiteSpace}, height=${m.controlH}px`);

check('the multi-line result renders one line box per source line',
    m.subjectLines === expected, `${m.subjectLines} line box(es), expected ${expected}`);
check('the rule is the thing that preserves them (white-space: pre-wrap applies)',
    m.subjectWhiteSpace === 'pre-wrap', m.subjectWhiteSpace);
check('without the rule the same text collapses to a single line (the reported bug)',
    m.controlLines === 1, `control=${m.controlLines}`);
check('the control is the same text, only un-styled',
    m.controlWhiteSpace === 'normal', m.controlWhiteSpace);
check('the rule does NOT leak into list/table cells (same fragment class)',
    m.inCellLines === 1 && m.inCellWhiteSpace === 'normal',
    `inCell=${m.inCellLines} line(s), white-space=${m.inCellWhiteSpace}`);

if (failures.length) {
    console.error(`\nSTANDALONE VALUE RENDER CHECK FAILED (${failures.length}): ${failures.join('; ')}`);
    process.exit(1);
}
console.log('OK: multi-line action results keep their line breaks; table cells unaffected.');
process.exit(0);
