/**
 * Measurement client for scripts/measure-mermaid.sh — the numbers behind
 * `docs/formatting/readability-guide.md` §"Diagram size".
 *
 * Two modes, because the fixture must exist *before* the browser loads it while the results can only
 * be read *after*:
 *   node scripts/measure-mermaid.mjs gen  <width> <dir> <file|dir...>   # writes <dir>/measure.html
 *   node scripts/measure-mermaid.mjs read <cdp-port>                    # prints one row per block
 *
 * `gen` walks the given paths, collects every ```mermaid fenced block, and writes a fixture that
 * renders each block into a container `<width>px` wide — the width of the chat column the diagram
 * will actually live in.  `read` drives headless Chromium over CDP and prints, per block:
 *
 *   ok|WIDE  natW=<natural px>  font=<effective px>  <file> #<n>
 *
 * `natW` is the SVG's own viewBox width; `font` is the smallest label's on-screen size once the
 * renderer has scaled the diagram down to the container (`scale × 18px`).  A block the renderer had
 * to shrink is flagged WIDE, and `≤ 420px` natural is the cheap conservative target.
 *
 * Exit: 0 = measured, 1 = the page never finished, 2 = usage error.
 */
import { readFileSync, writeFileSync, statSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const [, , mode, ...rest] = process.argv;

// ---- gen --------------------------------------------------------------------

function mdFiles(path) {
    const st = statSync(path);
    if (st.isFile()) return path.endsWith('.md') ? [path] : [];
    return readdirSync(path, { withFileTypes: true }).flatMap(e => {
        const p = join(path, e.name);
        if (e.isDirectory()) return e.name === 'node_modules' || e.name === 'target' ? [] : mdFiles(p);
        return e.isFile() && e.name.endsWith('.md') ? [p] : [];
    });
}

function mermaidBlocks(file) {
    const lines = readFileSync(file, 'utf8').split('\n');
    const out = [];
    for (let i = 0; i < lines.length; i++) {
        if (!lines[i].trimStart().startsWith('```mermaid')) continue;
        const body = [];
        i++;
        while (i < lines.length && !lines[i].startsWith('```')) body.push(lines[i++]);
        out.push({ name: `${file} #${out.length + 1}`, src: body.join('\n') + '\n' });
    }
    return out;
}

if (mode === 'gen') {
    const [width, dir, ...paths] = rest;
    if (!width || !dir || !paths.length) {
        console.error('usage: node scripts/measure-mermaid.mjs gen <width> <dir> <file|dir...>');
        process.exit(2);
    }
    const blocks = paths.flatMap(mdFiles).flatMap(mermaidBlocks);
    if (!blocks.length) {
        console.error('no ```mermaid blocks found in: ' + paths.join(', '));
        process.exit(2);
    }
    const html = `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>body{margin:0;background:#fff}.col{width:${width}px}</style>
<script src="mermaid.min.js"></script></head><body><pre id="out">PENDING</pre><div id="stage"></div>
<script>const BLOCKS=${JSON.stringify(blocks)};
mermaid.initialize({startOnLoad:false,securityLevel:"loose"});
(async()=>{const rows=[];
for(let i=0;i<BLOCKS.length;i++){const c=document.createElement("div");c.className="col";
document.getElementById("stage").appendChild(c);
try{const{svg}=await mermaid.render("f"+i,BLOCKS[i].src);c.innerHTML=svg;
const s=c.querySelector("svg");const vb=s.viewBox.baseVal;const r=s.getBoundingClientRect();
const sc=r.width/vb.width;let m=1e9;
for(const e of c.querySelectorAll(".nodeLabel,.edgeLabel span,text")){const x=(e.textContent||"").trim();
if(!x)continue;const f=parseFloat(getComputedStyle(e).fontSize);if(!isFinite(f))continue;if(f*sc<m)m=f*sc}
rows.push((sc<0.999?"WIDE":"ok  ")+"  natW="+String(Math.round(vb.width)).padStart(5)+"px"+
"  font="+m.toFixed(1)+"px  "+BLOCKS[i].name);
}catch(e){rows.push("ERR   "+BLOCKS[i].name+" | "+String(e).slice(0,70))}}
document.getElementById("out").textContent=rows.join(String.fromCharCode(10));
document.title="DONE"})();</script></body></html>
`;
    writeFileSync(join(dir, 'measure.html'), html);
    console.log(`fixture: ${blocks.length} block(s) at a ${width}px column`);
    process.exit(0);
}

// ---- read -------------------------------------------------------------------

if (mode !== 'read' || !rest[0]) {
    console.error('usage: node scripts/measure-mermaid.mjs read <cdp-port>');
    process.exit(2);
}
const port = rest[0];
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

const evalJs = async expression => {
    const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (r && r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails).slice(0, 400));
    return r && r.result ? r.result.value : undefined;
};

let done = false;
for (let i = 0; i < 200; i++) {
    if (await evalJs('document.title') === 'DONE') { done = true; break; }
    await sleep(250);
}
console.log(await evalJs("document.getElementById('out').textContent"));
if (!done) { console.error('WARN: the page never reported DONE'); process.exit(1); }
process.exit(0);
