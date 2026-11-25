/**
 * Headless-Chromium assertion client for scripts/verify-uml-render.sh
 *
 * Asserts that the STATIC UML branch of `d3-integration.mjs` draws what the payload says: one box per
 * node, one path per connector, an arrow marker on every generalization, the legend, and the payload's
 * own canvas as the SVG viewBox.  The *geometry* is asserted over the real model by
 * `UmlLayoutIntegrityTest`; this is about the client not dropping any of it.
 *
 * It does the same for the FORCE branch (the assembled model's seed grid) and for the PACKAGE flavour of
 * it (`"packages":true`, the sub-domain overview) — there each node is drawn as a UML package symbol
 * instead of a class box, with the sub-domain's outward classes in the body — each of those a link to the
 * class it stands for, so a line the payload tied to no row must stay plain text that opens nothing.
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
    legendRows: legend ? legend.querySelectorAll('text').length : 0,
    // A legend row prints its label only; the explanation the payload carries for it is the row's tooltip.
    legendTexts: legend ? Array.from(legend.querySelectorAll('text')).map(t => t.textContent) : [],
    legendTitles: legend ? Array.from(legend.querySelectorAll('title')).map(t => t.textContent) : []
  });
})()`;

const out = await send('Runtime.evaluate', { expression: MEASURE, returnByValue: true });
const m = JSON.parse(out.result.value);

// ---- measurement: the force branch ------------------------------------------

const MEASURE_FORCE = `(() => {
  const svg = document.querySelector('#d3CanvasForce svg');
  if (!svg) return JSON.stringify({ error: 'the force renderer produced no svg' });
  const layer = svg.querySelector('g.uml-layer');
  if (!layer) return JSON.stringify({ error: 'the force renderer produced no uml-layer' });
  const boxes = Array.from(layer.querySelectorAll('g.uml-class'));
  const world = g => {
    const t = /translate\\(([-\\d.]+),([-\\d.]+)\\)/.exec(g.getAttribute('transform') || '');
    const r = g.querySelector('rect.uml-box');
    const w = parseFloat(r.getAttribute('width')), h = parseFloat(r.getAttribute('height'));
    return { cx: parseFloat(t[1]), cy: parseFloat(t[2]), w: w, h: h };
  };
  // How far the deepest pair of boxes still overlaps after the simulation settled.  A rectangular
  // collision force is what keeps this at zero; a circular one would leave the corners interpenetrating.
  let worst = 0;
  for (let i = 0; i < boxes.length; i++) {
    for (let j = i + 1; j < boxes.length; j++) {
      const a = world(boxes[i]), b = world(boxes[j]);
      const ox = (a.w + b.w) / 2 - Math.abs(b.cx - a.cx);
      const oy = (a.h + b.h) / 2 - Math.abs(b.cy - a.cy);
      if (ox > 0 && oy > 0) worst = Math.max(worst, Math.min(ox, oy));
    }
  }
  const texts = [];
  boxes.forEach(g => {
    const r = g.querySelector('rect.uml-box');
    const box = parseFloat(r.getAttribute('width'));
    g.querySelectorAll('text').forEach(t => texts.push({
      name: t.textContent, box: box,
      text: t.getComputedTextLength(),
      font: parseFloat(getComputedStyle(t).fontSize)
    }));
  });
  const lines = Array.from(layer.querySelectorAll('line.uml-link'));
  const legend = svg.querySelector('g.uml-legend');
  const rel = Array.from(layer.querySelectorAll('text.uml-link-label'))
      .find(t => t.textContent === 'rel');
  // The click-through: a box the payload ties to a domain row must open that row's page, and a box with
  // no row behind it must do nothing.  window.open is stubbed for the duration of the probe, so the
  // fixture needs no second page — and the URL is read back exactly as the client built it.
  const byName = n => boxes.find(g => { const t = g.querySelector('text.uml-name'); return t && t.textContent === n; });
  // What makes the name read as a link.  Chromium computes text-decoration-line from the
  // text-decoration attribute the renderer sets; the shorthand is the fallback for a build that
  // reports only that.  The check is a regex test on the string, so either spelling answers.
  const decorationOf = el => {
    const s = getComputedStyle(el);
    return s.textDecorationLine || s.textDecoration || '';
  };
  const realOpen = window.open;
  let opened = null;
  window.open = function (url) { opened = url; return null; };
  const clickable = byName('Alpha'), inert = byName('Beta');
  if (clickable) clickable.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
  const clickableUrl = opened;
  opened = null;
  if (inert) inert.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
  const inertUrl = opened;
  window.open = realOpen;
  return JSON.stringify({
    settled: svg.getAttribute('data-uml-settled'),
    viewBox: svg.getAttribute('viewBox'),
    svgHeight: parseFloat(svg.getAttribute('height')),
    containerWidth: document.querySelector('#d3CanvasForce').clientWidth,
    expectedTall: Math.round((window.innerHeight || 0) * 0.75),
    boxes: boxes.length,
    boxNames: boxes.map(g => { const t = g.querySelector('text.uml-name'); return t ? t.textContent : null; }),
    nameStyles: boxes.map(g => { const t = g.querySelector('text.uml-name'); return t ? getComputedStyle(t).fontStyle : null; }),
    fills: boxes.map(g => g.querySelector('rect.uml-box').getAttribute('fill')),
    connectors: lines.length,
    routedPaths: layer.querySelectorAll('path').length,
    arrowed: lines.filter(l => l.getAttribute('marker-end')).length,
    strokes: lines.map(l => l.getAttribute('stroke')),
    // The weight of each connector, in payload order.  On a *class* diagram every edge keeps the plain
    // weight: the label is the verb of the relation ("owns", "is a"), so it is not a count to draw.
    linkWidths: lines.map(l => parseFloat(l.getAttribute('stroke-width'))),
    memberLines: Array.from(layer.querySelectorAll('text.uml-member')).map(t => t.textContent),
    separators: layer.querySelectorAll('line.uml-separator').length,
    texts: texts,
    worstOverlap: worst,
    linkLabel: rel ? { x: parseFloat(rel.getAttribute('x')), y: parseFloat(rel.getAttribute('y')),
                       stroke: rel.getAttribute('stroke'), paintOrder: rel.getAttribute('paint-order') } : null,
    cursors: boxes.map(g => { const t = g.querySelector('text.uml-name');
                              return { name: t ? t.textContent : null, cursor: getComputedStyle(g).cursor,
                                       decoration: t ? decorationOf(t) : null }; }),
    clickableUrl: clickableUrl,
    inertUrl: inertUrl,
    legend: !!legend,
    legendRows: legend ? legend.querySelectorAll('text').length : 0,
    // A legend row prints its label only; the explanation the payload carries for it is the row's tooltip.
    legendTexts: legend ? Array.from(legend.querySelectorAll('text')).map(t => t.textContent) : [],
    legendTitles: legend ? Array.from(legend.querySelectorAll('title')).map(t => t.textContent) : []
  });
})()`;

const outForce = await send('Runtime.evaluate', { expression: MEASURE_FORCE, returnByValue: true });
const f = JSON.parse(outForce.result.value);

// ---- measurement: the package branch (the sub-domain overview) ---------------

// `"packages":true` is the sub-domain overview: each node is a sub-domain drawn as a UML *package* — a tab
// carrying the name over a body listing the classes of that sub-domain that relate to a class outside it.
// The box the simulation moves is the class box's rectangle, so the geometry is unchanged; what is asserted
// here is that the client draws the symbol and the contents, and that it draws no compartment separators
// (a package has a body, not compartments).
const MEASURE_PACKAGES = `(() => {
  const svg = document.querySelector('#d3CanvasPackages svg');
  if (!svg) return JSON.stringify({ error: 'the package renderer produced no svg' });
  const layer = svg.querySelector('g.uml-layer');
  if (!layer) return JSON.stringify({ error: 'the package renderer produced no uml-layer' });
  const boxes = Array.from(layer.querySelectorAll('g.uml-class'));
  const measured = boxes.map(g => {
    const tab = g.querySelector('path.uml-package-tab');
    const body = g.querySelector('rect.uml-package-body');
    const name = g.querySelector('text.uml-name');
    const box = g.querySelector('rect.uml-box');
    const tb = tab ? tab.getBBox() : null, nb = name ? name.getBBox() : null, bb = body ? body.getBBox() : null;
    return {
      classes: g.getAttribute('class'),
      name: name ? name.textContent : null,
      font: name ? parseFloat(getComputedStyle(name).fontSize) : null,
      tab: tb ? { x: tb.x, y: tb.y, w: tb.width, h: tb.height } : null,
      body: bb ? { x: bb.x, y: bb.y, w: bb.width, h: bb.height } : null,
      boxH: box ? parseFloat(box.getAttribute('height')) : null,
      // The name must sit *in* the tab — horizontally and vertically — which is what makes the tab read as
      // the package's name compartment rather than as a stray rectangle behind the text.
      nameInTab: (tb && nb)
          ? (nb.x >= tb.x - 1 && nb.x + nb.width <= tb.x + tb.width + 1
             && nb.y >= tb.y - 1 && nb.y + nb.height <= tb.y + tb.height + 1)
          : false,
      // A package stands for a *sub-domain*, not a domain row: it is never a click-through, so its name
      // must not carry the underline that marks one.  text-decoration-line is what Chromium computes
      // from the attribute; the shorthand is the fallback.
      underlined: name
          ? /underline/.test(getComputedStyle(name).textDecorationLine
                             || getComputedStyle(name).textDecoration || '')
          : null
    };
  });
  const legend = svg.querySelector('g.uml-legend');
  // A package's *content line* that stands for a class is a link: underlined (the cue that shows before the
  // pointer arrives), a pointer cursor, and a click that opens that class's page.  A line the payload did
  // not tie to a row keeps all three absent.  window.open is stubbed for the duration of the probe, so
  // every line can be clicked in one pass and the URL read back exactly as the client built it.
  const decorationOf = el => {
    const s = getComputedStyle(el);
    return s.textDecorationLine || s.textDecoration || '';
  };
  const realOpen = window.open;
  let opened = null;
  window.open = function (url) { opened = url; return null; };
  const contentLines = Array.from(layer.querySelectorAll('text.uml-member')).map(el => {
    opened = null;
    el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    return { name: el.textContent, decoration: decorationOf(el),
             cursor: getComputedStyle(el).cursor, url: opened };
  });
  window.open = realOpen;
  return JSON.stringify({
    settled: svg.getAttribute('data-uml-settled'),
    viewBox: svg.getAttribute('viewBox'),
    boxes: boxes.length,
    measured: measured,
    memberLines: Array.from(layer.querySelectorAll('text.uml-member')).map(t => t.textContent),
    contentLines: contentLines,
    separators: layer.querySelectorAll('line.uml-separator').length,
    // The overview's connectors, in payload order, with the label each carries: the count is drawn twice —
    // printed, and as the weight of the line (see umlLinkWeight).
    linkWidths: Array.from(layer.querySelectorAll('line.uml-link'))
        .map(l => parseFloat(l.getAttribute('stroke-width'))),
    linkLabels: Array.from(layer.querySelectorAll('text.uml-link-label')).map(t => t.textContent),
    tabs: layer.querySelectorAll('path.uml-package-tab').length,
    bodies: layer.querySelectorAll('rect.uml-package-body').length,
    legend: !!legend,
    legendRows: legend ? Array.from(legend.querySelectorAll('text')).map(t => t.textContent) : [],
    legendTitles: legend ? Array.from(legend.querySelectorAll('title')).map(t => t.textContent) : [],
    // How much of the legend box stands empty to the right of the widest row, in screen pixels: the box is
    // measured to the label, so this is its padding and nothing more.
    legendGap: legend ? (function () {
        var boxRight = legend.getBoundingClientRect().right;
        var widest = Math.max.apply(null, Array.from(legend.querySelectorAll('text'))
            .map(function (t) { return t.getBoundingClientRect().right; }));
        return Math.round(boxRight - widest);
    })() : null
  });
})()`;

const outPackages = await send('Runtime.evaluate', { expression: MEASURE_PACKAGES, returnByValue: true });
const p = JSON.parse(outPackages.result.value);

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
check('the arrow is black and the association grey',
    m.strokes.includes('#000000') && m.strokes.includes('#7f8c8d'), JSON.stringify(m.strokes));

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
// A row is its label — the explanation the payload carries for it is the row's tooltip, not a sentence
// printed after a dash, which is what used to make every row as wide as its prose.
check('a legend row prints its label and nothing after it',
    JSON.stringify(m.legendTexts) === JSON.stringify(['Class', 'Association', 'Generalization']),
    JSON.stringify(m.legendTexts));
check('the row keeps its explanation, as the row\'s tooltip',
    m.legendTitles.length === 3 && m.legendTitles.every(t => t.length > 0),
    JSON.stringify(m.legendTitles));

// ---- assertions: the force branch -------------------------------------------

if (f.error) {
    console.error(`UML RENDER CHECK FAILED (force branch): ${f.error}`);
    process.exit(1);
}

check('the force renderer settles before it paints (data-uml-settled)',
    f.settled === 'true', String(f.settled));
check('the force SVG uses the payload canvas as its viewBox', f.viewBox === '0 0 600 400', f.viewBox);
// The panel must use the height it has: the SVG's box is the taller of the canvas' own aspect height and
// three quarters of the window, so a wide drawing is centred in a panel that fills the tab rather than
// letterboxed into a strip.  (A list cell is unaffected — its stylesheet pins the SVG's box with
// `!important` — which is why this is asserted here and not in verify-ui-render.sh.)
const expectedSvgHeight = Math.max(320, Math.round((f.containerWidth || 600) * 400 / 600), f.expectedTall);
check('the force SVG fills the tab — its box is the taller of the canvas aspect and three quarters of the window',
    f.svgHeight === expectedSvgHeight, `${f.svgHeight} vs ${expectedSvgHeight}`);
check('one box per node', f.boxes === 3, String(f.boxes));
check('the boxes carry their names',
    JSON.stringify(f.boxNames) === JSON.stringify(['Alpha', 'Beta', 'ServiceProviderOnNegotiation']),
    JSON.stringify(f.boxNames));
check('a super-class name is drawn in italics and a plain class name is not',
    JSON.stringify(f.nameStyles) === JSON.stringify(['italic', 'normal', 'normal']),
    JSON.stringify(f.nameStyles));
check('one connector per link', f.connectors === 2, String(f.connectors));
check('no connector is a routed path — a route would be stale after the first tick',
    f.routedPaths === 0, String(f.routedPaths));
check('only the generalization draws an arrowhead', f.arrowed === 1, String(f.arrowed));
check('the arrow is black and the association grey',
    f.strokes.includes('#000000') && f.strokes.includes('#7f8c8d'), JSON.stringify(f.strokes));
// The count-as-weight rule reads the label, so it must stay *off* where the label is a verb: a
// class diagram's edge says "owns" or "is a", and drawing that as a number is meaningless.
check('a class-diagram connector keeps the plain weight — its label is a verb, not a count',
    f.linkWidths.length === 2 && f.linkWidths.every(w => w === 1.5),
    JSON.stringify(f.linkWidths));

check('the attribute compartment is drawn',
    f.memberLines.includes('id: Long') && f.memberLines.includes('name: String'),
    JSON.stringify(f.memberLines));
check('the method compartment is drawn', f.memberLines.includes('approve()'), JSON.stringify(f.memberLines));
check('every box draws both compartment separators — an empty compartment still has one',
    f.separators === f.boxes * 2, String(f.separators));
check('the archetype colours its box', f.fills.includes('#ffe0b2'), JSON.stringify(f.fills));
check('a box with no archetype keeps the default fill', f.fills.includes('#eaf3fb'), JSON.stringify(f.fills));

const forceOverflowing = f.texts.filter(t => t.text > t.box + 1);
check('every name and member line fits inside its box (the long ones are shrunk to fit)',
    forceOverflowing.length === 0,
    forceOverflowing.map(t => `${t.name}: ${Math.round(t.text)} > ${t.box}`).join('; '));

const forceTooSmall = f.texts.filter(t => t.font < 8);
check('a shrunk label stays readable (font >= 8px)', forceTooSmall.length === 0,
    forceTooSmall.map(t => `${t.name}: ${t.font.toFixed(1)}px`).join('; '));

check('the rectangular collision force leaves no two boxes overlapping',
    f.worstOverlap <= 0.5, `worst overlap ${f.worstOverlap.toFixed(1)} units`);

check('the connector label is drawn on the settled route', !!f.linkLabel);
check('the connector label carries a white halo, so a line behind it cannot hide the text',
    !!f.linkLabel && f.linkLabel.stroke === '#ffffff' && f.linkLabel.paintOrder === 'stroke',
    f.linkLabel ? `stroke=${f.linkLabel.stroke} paint-order=${f.linkLabel.paintOrder}` : 'no label');

check('the legend is drawn', f.legend);
check('the legend explains the archetype as well as the node and both link kinds',
    f.legendRows === 4, String(f.legendRows));
// The archetype row's label is the model's own (the fixture's is "Party"), so the check pins the shape:
// four labels, none of them carrying an explanation after a dash.  The dash is built by code point so
// that the check cannot be defeated by an escaping slip.
const EM_DASH = String.fromCharCode(0x2014);
check('no legend row carries an explanation after a dash — the tooltip does',
    f.legendTexts.length === 4 && f.legendTexts.every(t => t.indexOf(EM_DASH) === -1)
        && f.legendTexts.slice(0, 3).join('|') === 'Class|Association|Generalization',
    JSON.stringify(f.legendTexts));
check('every legend row keeps its explanation as a tooltip',
    f.legendTitles.length === 4 && f.legendTitles.every(t => t.length > 0),
    JSON.stringify(f.legendTitles));

// A class box tied to a domain row is a click-through to that row's own page — the class name in an
// assembled diagram opens the ClassCdd the review works with.  A box with no row behind it stays inert.
// The affordance is threefold and has to be consistent: the name is underlined (the cue that is visible
// before the pointer arrives), the cursor is a pointer, and a click opens the page.
const cursorOf = n => (f.cursors.find(c => c.name === n) || {}).cursor;
const decorationOfName = n => (f.cursors.find(c => c.name === n) || {}).decoration;
check('a clickable class name is underlined — the cue shows before the pointer arrives',
    /underline/.test(decorationOfName('Alpha')), JSON.stringify(f.cursors.map(c => [c.name, c.decoration])));
check('a name with no row behind it is not underlined',
    !/underline/.test(decorationOfName('Beta'))
        && !/underline/.test(decorationOfName('ServiceProviderOnNegotiation')),
    JSON.stringify(f.cursors.map(c => [c.name, c.decoration])));
check('a box tied to a domain row is a click-through (pointer cursor)',
    cursorOf('Alpha') === 'pointer', JSON.stringify(f.cursors));
check('a click on that box opens its own page in a new tab',
    f.clickableUrl === '/wicket/object/domox.ClassCdd:42', String(f.clickableUrl));
check('a box with no row behind it is inert — no pointer cursor, and a click opens nothing',
    cursorOf('Beta') === 'default' && cursorOf('ServiceProviderOnNegotiation') === 'default'
        && f.inertUrl === null,
    JSON.stringify(f.cursors) + ' inertUrl=' + String(f.inertUrl));

// ---- assertions: the package branch (the sub-domain overview) ----------------

if (p.error) {
    console.error(`UML RENDER CHECK FAILED (package branch): ${p.error}`);
    process.exit(1);
}

check('the package renderer settles before it paints (data-uml-settled)',
    p.settled === 'true', String(p.settled));
check('the package SVG uses the payload canvas as its viewBox', p.viewBox === '0 0 600 400', p.viewBox);
check('one package per sub-domain', p.boxes === 3, String(p.boxes));
check('every box is marked as a package, not a class box',
    p.measured.length === 3 && p.measured.every(b => (b.classes || '').includes('uml-package')),
    JSON.stringify(p.measured.map(b => b.classes)));
check('each package draws a tab and a body', p.tabs === 3 && p.bodies === 3,
    `tabs=${p.tabs} bodies=${p.bodies}`);
check('the package name sits inside its tab, not in the body',
    p.measured.every(b => b.nameInTab),
    JSON.stringify(p.measured.map(b => ({ name: b.name, tab: b.tab, inTab: b.nameInTab }))));
check('the tab is the name compartment\'s height and the body fills the rest of the rectangle',
    p.measured.every(b => Math.abs(b.tab.h - 20) < 0.5 && Math.abs(b.tab.h + b.body.h - b.boxH) < 0.5),
    JSON.stringify(p.measured.map(b => ({ tabH: b.tab.h, bodyH: b.body.h, boxH: b.boxH }))));
check('a package has a body, not compartments — no separator is drawn',
    p.separators === 0, String(p.separators));
check('a package lists the classes that relate outside its sub-domain',
    JSON.stringify(p.memberLines.slice().sort()) === JSON.stringify(['Order', 'OrderLine', 'Till']),
    JSON.stringify(p.memberLines));
check('a package name is not underlined — a package is not a click-through',
    p.measured.every(b => b.underlined === false),
    JSON.stringify(p.measured.map(b => [b.name, b.underlined])));
check('a long package name is shrunk into the tab it can have, and stays readable',
    p.measured.some(b => b.name === 'CheckoutAndPaymentProcessing' && b.font < 12 && b.font >= 8),
    JSON.stringify(p.measured.map(b => ({ name: b.name, font: b.font }))));

// A name in a package's body is a link to the class it stands for, and the *line* carries all three cues —
// so a reader can neither see an underline on a name that does nothing nor miss one that does.  A line the
// payload tied to no row (`Till`) must keep all three absent.
const content = n => (p.contentLines.find(c => c.name === n) || {});
check('a content line tied to a class is underlined — the cue shows before the pointer arrives',
    /underline/.test(content('Order').decoration) && /underline/.test(content('OrderLine').decoration),
    JSON.stringify(p.contentLines));
check('a content line tied to a class is a click-through (pointer cursor)',
    content('Order').cursor === 'pointer' && content('OrderLine').cursor === 'pointer',
    JSON.stringify(p.contentLines.map(c => [c.name, c.cursor])));
check('a click on a content line opens that class\'s own page in a new tab',
    content('Order').url === '/wicket/object/domox.ClassCdd:7'
    && content('OrderLine').url === '/wicket/object/domox.ClassCdd:8',
    JSON.stringify(p.contentLines.map(c => [c.name, c.url])));
check('a content line with no row behind it is inert — plain text, no pointer, and a click opens nothing',
    content('Till').name === 'Till' && !/underline/.test(content('Till').decoration)
    && content('Till').cursor !== 'pointer' && content('Till').url === null,
    JSON.stringify(content('Till')));

// The overview's edges are the one place a label is a *count*: `ClassDiagram#overviewOf` aggregates every
// cross-sub-domain collaboration into one edge and labels it with how many it stands for.  The line is
// drawn that thick, so the two fixture edges (2 and 7 collaborations) must differ, and differ by the
// pinned step — 1.5 for a single collaboration, +0.6 for each further one — so that a silent retune of the
// scale cannot pass.
check('an overview connector is thicker the more relations it stands for',
    p.linkWidths.length === 2 && p.linkWidths[1] > p.linkWidths[0],
    JSON.stringify({ widths: p.linkWidths, labels: p.linkLabels }));
check('the connector weight is the count it carries (1.5 for one collaboration, +0.6 each further one)',
    p.linkWidths.length === 2 && Math.abs(p.linkWidths[0] - 2.1) < 0.01
        && Math.abs(p.linkWidths[1] - 5.1) < 0.01,
    JSON.stringify({ widths: p.linkWidths, labels: p.linkLabels }));

check('the legend names the package symbol, not a class box',
    p.legendRows.length > 0 && p.legendRows[0] === 'Package'
        && !p.legendRows.some(r => r.startsWith('Class')),
    JSON.stringify(p.legendRows));
check('the overview\'s legend explains the package and the association it draws — and no archetype',
    p.legendRows.length === 2 && p.legendRows[1] === 'Association',
    JSON.stringify(p.legendRows));
// The overview's package row is the one whose explanation used to run longest, so it is the row that
// proves the shortening: the label is the word, the explanation is a tooltip, and the box is measured to
// the label instead of standing at a fixed width over empty space.
check('the overview\'s rows carry their explanations as tooltips',
    p.legendTitles.length === 2 && p.legendTitles.some(t => t.indexOf('sub-domain') !== -1),
    JSON.stringify(p.legendTitles));
check('the legend box is measured to its widest row, not fixed wide',
    p.legendGap !== null && p.legendGap >= 0 && p.legendGap <= 40, `gap ${p.legendGap}px`);

if (failures.length) {
    console.error(`\nUML RENDER CHECK FAILED (${failures.length}): ${failures.join('; ')}`);
    process.exit(1);
}
console.log('OK: the UML renderers draw boxes, packages, connectors, arrows and the legend.');
process.exit(0);
