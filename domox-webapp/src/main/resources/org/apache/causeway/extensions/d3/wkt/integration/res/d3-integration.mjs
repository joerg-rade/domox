/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
;(function ($, undefined) {

    'use strict';

    if (!d3) {
        console.error("Missing D3 prerequisites.");
        return;
    }

    if (typeof(WicketStuff) !== 'object') {
        window.WicketStuff = {};
    }

    if (typeof(WicketStuff.D3) === 'object') {
        return;
    }

    var PLACEHOLDER = "<PLACEHOLDER>";

    /** Longest label line before a node name is wrapped (see {@link wrapLabel}). */
    var LABEL_MAX_CHARS = 40;

    /** The UML name compartment's height — the server sizes a box's members against the same figure. */
    var UML_NAME_H = 20;

    /**
     * The Causeway page of one domain row: `/wicket/object/<objectType>:<id>`.
     *
     * `objectType` is the *framework's* type — what the payload read back from `@Named` — never a name
     * derived from the Java class, or the URL would name a type Causeway does not know.
     */
    function entityPageUrl(contextPath, objectType, id) {
        return (contextPath || '') + '/wicket/object/' + encodeURIComponent(objectType) + ':' + id;
    }

    /**
     * Whether a thing the payload described stands for a domain row — a **box**, or a line of a package's
     * **contents** — and so is a click-through: only when the payload gave it **both** `objectType` and
     * `candidateId`, and only then.
     *
     * One predicate for the whole affordance — the pointer cursor, the click handler and the underlined
     * name — so the cue a reader sees can never disagree with what a click actually does.  A class box and
     * a package's content line carry the same pair, so the same rule covers both.
     */
    function isClickable(d) {
        return d.candidateId != null && !!d.objectType;
    }

    /** Between a compartment's separator and its first member line, and before each later compartment. */
    var UML_MEMBER_PAD = 8;

    /** One member line, at the font size the members are drawn in. */
    var UML_MEMBER_LINE_H = 12;

    /** The font size of the member lines. */
    var UML_MEMBER_FONT = 9;

    /** The weight of a connector that stands for a single thing — every connector but the overview's. */
    var UML_LINK_W = 1.5;

    /** Each further relation an overview connector stands for, on top of {@link UML_LINK_W}. */
    var UML_OVERVIEW_LINK_STEP = 0.6;

    /** The heaviest an overview connector is drawn, however many relations it stands for. */
    var UML_OVERVIEW_LINK_MAX = 6;

    /**
     * The weight of one connector: {@link UML_LINK_W}, or — on the sub-domain overview — the number of
     * relations the line stands for.
     *
     * The overview is the one diagram whose edge label is a *count* rather than a verb: the server
     * aggregates every collaboration that leaves one sub-domain for another into a single edge and labels
     * it with how many it stands for.  Drawing that figure as the line's weight is a second reading of the
     * same number — a reader can see which pair of sub-domains the model leans on without reading twenty
     * labels — and it is the only thing that makes a many-to-one hub legible at a glance.
     *
     * The count is read only in the package flavour, which the payload states once (`"packages":true`):
     * a per-sub-domain diagram labels its edges with the verb ("owns", "is a"), and parsing those as
     * numbers would be meaningless.  A label that is not a positive integer — an empty one, a verb, a
     * cardinality — keeps the plain weight.
     */
    function umlLinkWeight(link, asPackage) {
        var count = asPackage ? parseInt(link.label, 10) : NaN;
        if (!isFinite(count) || count < 1) {
            return UML_LINK_W;
        }
        return Math.min(UML_LINK_W + (count - 1) * UML_OVERVIEW_LINK_STEP, UML_OVERVIEW_LINK_MAX);
    }

    /**
     * Draws a UML class diagram from the geometry the model itself stores.
     *
     * The payload is produced by `UmlLayoutDiagrams` on the server: every node carries its own
     * rectangle (`x`, `y`, `w`, `h`) and every link its polyline (`points`), so there is nothing to
     * lay out — the picture is drawn exactly as the model's author drew it.  A `d3-force` simulation
     * would destroy precisely that (boxes drift, edges cross, the original routing is lost), which is
     * why this path never starts one.
     */
    function renderStaticUml(svg, containerEl, data, config) {
        var canvas = data.canvas || {};
        var cw = canvas.width || 1200;
        var ch = canvas.height || 800;

        // Fit the stored canvas into the cell: keep the aspect ratio rather than a fixed height.
        var available = containerEl.clientWidth || cw;
        var heightPx = Math.max(320, Math.round(available * ch / cw));
        svg.attr('width', '100%')
            .attr('height', heightPx)
            .attr('viewBox', '0 0 ' + cw + ' ' + ch)
            .attr('preserveAspectRatio', 'xMidYMid meet');

        // One arrowhead marker, reused by every generalization.
        appendUmlArrowMarker(svg);

        var layer = svg.append('g').attr('class', 'uml-layer');

        var byId = {};
        (data.nodes || []).forEach(function (n) { byId[n.id] = n; });

        // ---- connectors first, so the boxes sit on top of them ----
        (data.links || []).forEach(function (link) {
            var points = link.points && link.points.length > 1
                ? link.points
                : fallbackPoints(link, byId);
            if (!points || points.length < 2) return;

            var d = 'M ' + points.map(function (p) { return p[0] + ' ' + p[1]; }).join(' L ');
            var path = layer.append('path')
                .attr('d', d)
                .attr('fill', 'none')
                .attr('stroke', link.kind === 'generalization' ? '#000000' : '#7f8c8d')
                .attr('stroke-width', UML_LINK_W);
            if (link.kind === 'generalization' && link.arrow !== false) {
                path.attr('marker-end', 'url(#uml-generalization-arrow)');
            }
            if (link.label) {
                var anchor = labelAnchor(points);
                if (anchor) {
                    layer.append('text')
                        .attr('x', anchor.x).attr('y', anchor.y)
                        .attr('text-anchor', anchor.horizontal ? 'middle' : 'start')
                        .attr('font-size', 9).attr('fill', '#555')
                        // A white halo, painted *behind* the glyphs: 58 of the bundled model's 156
                        // labelled connectors have another line passing over the text, and without this
                        // the label is unreadable where it happens.
                        .attr('stroke', '#ffffff').attr('stroke-width', 3)
                        .attr('stroke-linejoin', 'round').attr('paint-order', 'stroke')
                        .text(link.label);
                }
            }
        });

        // ---- the class boxes ----
        var box = layer.selectAll('g.uml-class')
            .data(data.nodes || [], function (d) { return d.id; })
            .enter().append('g')
            .attr('class', 'uml-class')
            .attr('transform', function (d) { return 'translate(' + d.x + ',' + d.y + ')'; });

        box.append('rect')
            .attr('width', function (d) { return d.w; })
            .attr('height', function (d) { return d.h; })
            .attr('rx', 3)
            .attr('fill', '#eaf3fb')
            .attr('stroke', '#3498DB')
            .attr('stroke-width', 1.5);

        // The name compartment — the separator line a UML class box carries.
        box.append('line')
            .attr('x1', 0).attr('x2', function (d) { return d.w; })
            .attr('y1', 20).attr('y2', 20)
            .attr('stroke', '#3498DB').attr('stroke-width', 1);

        box.append('text')
            .attr('x', function (d) { return d.w / 2; })
            .attr('y', 14)
            .attr('text-anchor', 'middle')
            .attr('font-size', 12).attr('font-weight', 'bold').attr('fill', '#1b3b57')
            .text(function (d) { return d.name || d.id; })
            .each(function (d) {
                // A model's box is only as wide as its own layout says, and over a third of them are
                // narrower than their class name at 12px — so the label is shrunk to fit rather than
                // spilling over the neighbouring boxes.  Wrapping is not the answer here: half of those
                // boxes are 28 units tall (no room for a second line) and most of the names are compound
                // words with nothing to break on.  Measured, not estimated — character widths vary a lot.
                var available = d.w - 10;                       // a little padding inside the box
                var needed = this.getComputedTextLength();
                if (needed > available && needed > 0) {
                    d3.select(this).attr('font-size', Math.max(6, 12 * available / needed));
                }
            });

        // ---- the legend, pinned to the top-left corner ----
        appendUmlLegend(svg, data, cw, available);

        // ---- zoom & pan: the drawing is static, the view need not be ----
        if (config.enableZoom !== false) {
            svg.call(d3.zoom().scaleExtent([0.2, 8]).on('zoom', function (event) {
                layer.attr('transform', event.transform);
            }));
        }
    }

    /**
     * Draws an **assembled** class diagram — a payload carrying `"force":true` — as a force-simulated,
     * draggable graph.
     *
     * Where {@link renderStaticUml} redraws a picture the model's author already laid out, this payload
     * (produced by `UmlDiagramLayout`) has no geometry beyond a *seed* rectangle per box and no routes at
     * all.  The boxes are therefore placed by a `d3-force` simulation the reader can drag, and a connector
     * is a straight centre-to-centre line clipped to the two box edges: a routed path would be stale the
     * moment the first tick ran, which is why the orthogonal router is not used on this path.
     *
     * Each box carries three compartments — name, attributes, methods — and is filled with its
     * Coad/Lefebvre archetype's colour; the archetypes actually drawn are listed in the legend.  The server
     * sizes each box with these same figures ({@link UML_NAME_H} etc.), so a compartment is never clipped —
     * `scripts/verify-uml-render.sh` is what keeps the two in step.
     *
     * A box the payload ties to a domain row (`candidateId` + `objectType`) is a **click-through** to that
     * row's own page, in a new tab — the class name in an assembled diagram opens the {@code ClassCdd} the
     * review works with.  A box with no row behind it carries neither key and is drawn inert.
     *
     * The simulation is run to completion **before** anything is drawn, so the first paint is the settled
     * picture rather than a jump: the seed is deterministic and so are the forces, which is also what lets
     * the render test assert on positions.  The timer is started by the first drag, not before.
     */
    function renderForceUml(svg, containerEl, data, config) {
        var canvas = data.canvas || {};
        var cw = canvas.width || 1200;
        var ch = canvas.height || 800;
        var available = containerEl.clientWidth || cw;
        // The diagram fills the tab, not a strip of it: the SVG's box is the container's width and at
        // least three quarters of the window's height, so a wide drawing is centred in a panel that uses
        // the height it has instead of leaving the rest of the tab empty.  A list cell is unaffected —
        // its stylesheet pins the SVG's own width and height with `!important` (static/css/domox.css
        // rules 3 and 5), so the taller box is clipped to the teaser/popup exactly as before.
        var tallEnough = Math.round((window.innerHeight || 0) * 0.75);
        svg.attr('width', '100%')
            .attr('height', Math.max(320, Math.round(available * ch / cw), tallEnough))
            .attr('viewBox', '0 0 ' + cw + ' ' + ch)
            .attr('preserveAspectRatio', 'xMidYMid meet');

        var layer = svg.append('g').attr('class', 'uml-layer');
        appendUmlArrowMarker(svg);

        // ---- the boxes, seeded at the rectangle the server chose -----------------
        var nodes = (data.nodes || []).map(function (n) {
            var w = n.w || 180;
            var h = n.h || UML_NAME_H;
            return {
                id: n.id,
                name: n.name || n.id,
                attributes: n.attributes || [],
                methods: n.methods || [],
                // A package's contents: the same lines as `attributes`, each with the class row it stands
                // for, so a name in the body can be a link (see `appendUmlPackage`).  Empty on a class box.
                contents: n.contents || [],
                color: n.color || '#eaf3fb',
                // A box the is-a arrows point at: the server marks it, and its name is drawn in italics
                // (the UML convention for an abstract class) so a reader can see which boxes a
                // generalization below belongs to.
                superclass: n.superclass === true,
                // The domain row behind the box, when the server named one: what a click on the box
                // opens.  Carried through unchanged — a box with no row behind it keeps both absent, and
                // the box is drawn inert.
                objectType: n.objectType,
                candidateId: n.candidateId,
                w: w,
                h: h,
                // The simulation works in box *centres*, the payload states the top-left corner.
                x: n.x + w / 2,
                y: n.y + h / 2,
                seedX: n.x + w / 2,
                seedY: n.y + h / 2
            };
        });

        if (nodes.length === 0) {
            svg.append('text').attr('class', 'uml-empty')
                .attr('x', 12).attr('y', 24).attr('font-size', 12).attr('fill', '#555')
                .text('No classes in this diagram');
            svg.attr('data-uml-settled', 'true');
            return;
        }

        var byId = {};
        nodes.forEach(function (n) { byId[n.id] = n; });

        var links = [];
        (data.links || []).forEach(function (l) {
            var source = byId[l.source];
            var target = byId[l.target];
            if (!source || !target) return;         // an edge to a box this diagram does not draw
            links.push({
                source: source,
                target: target,
                kind: l.kind || 'association',
                label: l.label || '',
                // A generalization's arrow belongs on the super-class, which the payload states as the
                // *source*; the line is therefore drawn target -> source so the marker lands there.
                // Every other connector is direction-free and is drawn source -> target.
                reversed: l.kind === 'generalization',
                arrow: l.kind === 'generalization' && l.arrow !== false
            });
        });

        // ---- settle the layout before the first paint ----------------------------
        // The seed is a *layout*, not a suggestion: the server already put every box where it belongs —
        // a hierarchy stacked top-down in one column, sub-domains in bands — so the anchors are strong
        // and the links are weak, and the simulation only relaxes the seed instead of dissolving it.
        // (With weak anchors a whole-model diagram collapses into one cloud, which is how the boxes of a
        // super-class and its sub-classes ended up far apart with the connector crossing the boxes
        // between them.)
        var simulation = d3.forceSimulation(nodes)
            .force('link', d3.forceLink(links).id(function (d) { return d.id; })
                .distance(200).strength(0.08))
            .force('charge', d3.forceManyBody().strength(-40).distanceMax(500))
            .force('collide', forceRectCollide(18))
            .force('x', d3.forceX(function (d) { return d.seedX; }).strength(0.3))
            .force('y', d3.forceY(function (d) { return d.seedY; }).strength(0.3))
            .stop();
        var ticks = nodes.length > 400 ? 180 : 300;
        for (var i = 0; i < ticks; i++) {
            simulation.tick();
        }
        containerEl._d3Simulation = simulation;

        // ---- the connectors ------------------------------------------------------
        // `asPackage` is read once here, ahead of both the connectors and the boxes: the connector weight
        // is the count on an overview edge (see {@link umlLinkWeight}) and the box is a package symbol
        // there, and both must read the same flag.
        var asPackage = data.packages === true;

        var linkSel = layer.selectAll('line.uml-link')
            .data(links)
            .enter().append('line')
            .attr('class', function (d) { return 'uml-link uml-link-' + d.kind; })
            .attr('stroke', function (d) { return d.kind === 'generalization' ? '#000000' : '#7f8c8d'; })
            .attr('stroke-width', function (d) { return umlLinkWeight(d, asPackage); })
            .attr('marker-end', function (d) { return d.arrow ? 'url(#uml-generalization-arrow)' : null; });

        var labelSel = layer.selectAll('text.uml-link-label')
            .data(links.filter(function (d) { return d.label; }))
            .enter().append('text')
            .attr('class', 'uml-link-label')
            .attr('text-anchor', 'middle')
            .attr('font-size', 9).attr('fill', '#555')
            // A white halo painted behind the glyphs: a label on a straight centre-to-centre line very
            // often has another line under it, and without the halo it is unreadable where it happens.
            .attr('stroke', '#ffffff').attr('stroke-width', 3)
            .attr('stroke-linejoin', 'round').attr('paint-order', 'stroke')
            .text(function (d) { return d.label; });

        // ---- the class boxes, one <g> each so it can be dragged whole ------------
        // On the sub-domain overview each box *is* a sub-domain, so it is drawn as a UML package
        // (`uml-package`) rather than as a class box; the rectangle the simulation moves is the same one.
        var box = layer.selectAll('g.uml-class')
            .data(nodes, function (d) { return d.id; })
            .enter().append('g')
            .attr('class', asPackage ? 'uml-class uml-package' : 'uml-class');

        box.append('rect')
            .attr('class', 'uml-box')
            .attr('x', function (d) { return -d.w / 2; })
            .attr('y', function (d) { return -d.h / 2; })
            .attr('width', function (d) { return d.w; })
            .attr('height', function (d) { return d.h; })
            .attr('rx', 3)
            .attr('fill', function (d) { return d.color; })
            .attr('stroke', '#3498db').attr('stroke-width', 1.5);

        appendUmlCompartments(box, asPackage, config);

        // ---- a class box is a click-through to the class it stands for -----------
        // The payload states the row behind each box (`candidateId` + `objectType`, emitted by
        // `DiagramJsonWriter` for a node `ClassDiagram` built from a ClassCdd), and the reader expects a
        // class name to open that class's page — the same click-through the CRC candidate diagrams have
        // always had, and the same vocabulary, so one rule covers both.  A box with no row behind it
        // (the sub-domain overview's packages, a diagram whose nodes are not model rows) carries neither
        // key and stays inert.
        //
        // The affordance is stated three times, and all three read {@link isClickable}: the name is
        // underlined (above, in `appendUmlCompartments` — the same cue the CRC diagrams use for a
        // clickable name, and the only one that is visible *before* the pointer arrives), the cursor is a
        // pointer, and the click opens the page.
        //
        // The handler sits on the box `<g>`, beside the drag handler below: d3-drag suppresses the click
        // only when the pointer actually moved, so a click opens the page and a drag still moves the box.
        box.style('cursor', function (d) {
                return isClickable(d) ? 'pointer' : 'default';
            })
            .on('click', function (event, d) {
                if (!isClickable(d)) return;
                window.open(entityPageUrl(config.contextPath, d.objectType, d.candidateId), '_blank');
            });

        // ---- position everything, then wire up dragging and zoom -----------------
        function ticked() {
            linkSel.each(function (link) {
                var ends = umlLinkEnds(link);
                d3.select(this)
                    .attr('x1', ends.from.x).attr('y1', ends.from.y)
                    .attr('x2', ends.to.x).attr('y2', ends.to.y);
            });
            labelSel.each(function (link) {
                var ends = umlLinkEnds(link);
                d3.select(this)
                    .attr('x', (ends.from.x + ends.to.x) / 2)
                    .attr('y', (ends.from.y + ends.to.y) / 2 - 4);
            });
            box.attr('transform', function (d) { return 'translate(' + d.x + ',' + d.y + ')'; });
        }
        // The simulation was ticked to completion *before* anything was drawn, so it needs no tick
        // listener until a drag restarts it — and then it needs one, or the box under the pointer would
        // stay where the seed put it while the simulation moved everything else around it.
        simulation.on('tick', ticked);
        ticked();

        // ---- drag a box: the layout is a suggestion, the reader's arrangement is not ---
        if (config.enableDragging !== false) {
            box.call(d3.drag()
                .on('start', function (event, d) {
                    if (!event.active) simulation.alphaTarget(0.3).restart();
                    d.fx = d.x;
                    d.fy = d.y;
                })
                .on('drag', function (event, d) {
                    d.fx = event.x;
                    d.fy = event.y;
                })
                .on('end', function (event, d) {
                    if (!event.active) simulation.alphaTarget(0);
                    // The box stays where it was dropped — `fx` is deliberately not cleared.  The point of
                    // dragging a class diagram is to tidy it, and a box that sprang back to where the seed
                    // put it would make that impossible.
                }));
        }

        // ---- the legend, pinned to the top-left corner ---- (it now lists the archetypes too)
        appendUmlLegend(svg, data, cw, available);

        if (config.enableZoom !== false) {
            svg.call(d3.zoom().scaleExtent([0.2, 8]).on('zoom', function (event) {
                layer.attr('transform', event.transform);
            }));
        }
        svg.attr('data-uml-settled', 'true');
    }

    /**
     * One arrowhead marker, reused by every generalization of both UML paths.
     *
     * `orient="auto-start-reverse"` means the same marker serves a line drawn in either direction, which
     * the force path relies on: it draws a generalization from the sub-class to the super-class so the
     * arrow lands on the super-class, while the static path draws each route as the export stored it.
     */
    function appendUmlArrowMarker(svg) {
        svg.append('defs').append('marker')
            .attr('id', 'uml-generalization-arrow')
            .attr('viewBox', '0 0 10 10')
            .attr('refX', 10).attr('refY', 5)
            .attr('markerWidth', 9).attr('markerHeight', 9)
            .attr('orient', 'auto-start-reverse')
            .append('path')
            .attr('d', 'M 0 0 L 10 5 L 0 10 z')
            .attr('fill', '#000000');
    }

    /**
     * Draws the three compartments of every class box: the name, then the attributes, then the methods.
     *
     * The box `<g>` is already positioned at the box's **centre**, so every y here is relative to that
     * centre and the rectangle's own top edge is `-h/2`.  All three compartments are drawn on every box,
     * with a separator under the name and one between the two member compartments, **whether or not a
     * compartment holds anything**: an empty compartment is a compartment, not a missing line, and this is
     * what the PDF (PlantUML) draws.  The server sizes each box the same way, counting an empty
     * compartment's padding.
     *
     * The figures are the server's: `UmlDiagramLayout` sized each box as
     * `max(BOX_H, UML_NAME_H + attributes + methods)`, so a compartment is never clipped.  A line that is
     * still too wide for its box is **shrunk**, never clipped and never allowed to cross into the
     * neighbouring box.
     *
     * With `asPackage` set the same rectangle is drawn as a UML **package** instead — see
     * {@link appendUmlPackage}.  Only what is drawn inside the box differs, so the box the server sized
     * and the simulation moves is unchanged.
     *
     * `config` carries the context path the click-through URLs are built from; only the package branch
     * needs it, and only for the content lines that stand for a class.
     */
    function appendUmlCompartments(box, asPackage, config) {
        if (asPackage) {
            box.each(function (d) { appendUmlPackage(d3.select(this), d, config); });
            return;
        }
        box.each(function (d) {
            var g = d3.select(this);
            var attributeH = umlCompartmentHeight(d.attributes);

            shrinkToFit(g.append('text').attr('class', 'uml-name')
                .attr('text-anchor', 'middle').attr('x', 0).attr('y', -d.h / 2 + 14)
                .attr('font-size', 12).attr('font-weight', 'bold').attr('fill', '#1b3b57')
                .attr('font-style', d.superclass ? 'italic' : null)
                // Underlined when the box is a click-through, so the name itself says it is a link — the
                // cue has to be visible before the pointer arrives.  The CRC diagrams already underline a
                // clickable name, so this is the same affordance, not a second one.
                .attr('text-decoration', isClickable(d) ? 'underline' : null)
                .text(d.name), d.w - 10, 12);

            // Both separators are always drawn: the name / attributes / methods structure *is* the box.
            var nameBottom = -d.h / 2 + UML_NAME_H;
            separator(g, d, nameBottom);
            separator(g, d, nameBottom + attributeH);

            umlMemberLines(d, d.attributes, 0)
                .concat(umlMemberLines(d, d.methods, attributeH))
                .forEach(function (row) {
                    shrinkToFit(g.append('text').attr('class', 'uml-member')
                        .attr('x', -d.w / 2 + 6).attr('y', row.y)
                        .attr('font-size', UML_MEMBER_FONT).attr('fill', '#33475b')
                        .text(row.text), d.w - 12, UML_MEMBER_FONT);
                });
        });
    }

    /**
     * A UML **package**: a tab across the top-left carrying the name, and a body below it holding the
     * package's contents.  This is how the sub-domain overview draws a node, because there a node *is* a
     * sub-domain and its contents are the classes of that sub-domain that relate to a class outside it
     * (`ClassDiagram#graphOverview`).
     *
     * The tab is as tall as the name compartment ({@link UML_NAME_H}) and the body fills the rest of the
     * rectangle, so the server's box size — and therefore the geometry and the forces that place it — is
     * exactly the class box's; only what is drawn inside the rectangle differs.  There are **no**
     * compartment separators: a package has a body, not compartments.  The body's member lines reuse
     * {@link umlMemberLines} at the same offsets, so a long contents list shrinks exactly as a class's
     * member list does.
     *
     * **A content line that stands for a class is a link.**  The package itself is not clickable — it is a
     * sub-domain, and there is no one row a click on it could mean — so the payload gives *each line* its
     * own `objectType` / `candidateId` (`contents`), and a click on a name opens that class's page in a new
     * tab.  The affordance is the same threefold one a class box carries, and it reads the same predicate
     * ({@link isClickable}): the name is underlined — the cue visible before the pointer arrives, and the
     * one the CRC diagrams already use for a clickable name — the cursor is a pointer, and the click opens
     * the page.  A line with no row behind it (a payload built from a picture, or a class whose row is
     * unknown) keeps all three absent and is drawn as plain text.
     */
    function appendUmlPackage(g, d, config) {
        var left = -d.w / 2;
        var top = -d.h / 2;
        var bodyTop = top + UML_NAME_H;

        // The tab is drawn first and the body over it: the two rectangles meet along the tab's bottom edge,
        // which is the line that separates the package's name from its contents.
        var tab = g.append('path').attr('class', 'uml-package-tab')
            .attr('fill', '#ffffff').attr('stroke', '#3498db').attr('stroke-width', 1.5);
        g.append('rect').attr('class', 'uml-package-body')
            .attr('x', left).attr('y', bodyTop)
            .attr('width', d.w).attr('height', Math.max(0, d.h - UML_NAME_H))
            .attr('fill', d.color).attr('stroke', '#3498db').attr('stroke-width', 1.5);

        var name = g.append('text').attr('class', 'uml-name')
            .attr('text-anchor', 'middle').attr('y', top + 14)
            .attr('font-size', 12).attr('font-weight', 'bold').attr('fill', '#1b3b57')
            // The same rule as a class box's name, so the cue can never drift from the handler that the
            // box `<g>` carries: a package has no row behind it today, and so is never underlined.
            .attr('text-decoration', isClickable(d) ? 'underline' : null)
            .text(d.name);
        // The tab is sized to the name (measured, like every other label here), capped by the box so it can
        // never overhang; the name is then shrunk into whatever tab that leaves.
        var tabW = Math.min(d.w, Math.max(56, Math.round(name.node().getComputedTextLength()) + 16));
        name.attr('x', left + tabW / 2);
        shrinkToFit(name, tabW - 10, 12);
        tab.attr('d', umlPackageTabPath(left, top, tabW));

        umlMemberLines(d, umlContentNames(d), 0).forEach(function (row, i) {
            var content = umlContentAt(d, i);
            // A content line that stands for a class is a link, and the line itself carries the affordance:
            // underlined (the cue visible before the pointer arrives), a pointer cursor, and the click.  A
            // line with no row behind it keeps all three absent.
            var link = isClickable(content);
            var line = g.append('text')
                .attr('class', link ? 'uml-member uml-content-link' : 'uml-member')
                .attr('x', left + 6).attr('y', row.y)
                .attr('font-size', UML_MEMBER_FONT).attr('fill', '#33475b')
                .attr('text-decoration', link ? 'underline' : null)
                .style('cursor', link ? 'pointer' : null)
                .text(row.text);
            shrinkToFit(line, d.w - 12, UML_MEMBER_FONT);
            if (link) {
                line.on('click', function (event) {
                    // The line is inside the box's `<g>`, which is draggable and carries the box's own click
                    // handler: stop the event so a click on a name cannot be read as a click on the package.
                    // d3-drag still moves the box — it suppresses the click only when the pointer moved.
                    event.stopPropagation();
                    window.open(entityPageUrl(config.contextPath, content.objectType, content.candidateId),
                        '_blank');
                });
            }
        });
    }

    /**
     * A package's content lines: the payload's own list, each entry `{name, objectType, candidateId}`.
     *
     * A payload from an older server (or a hand-written one) carries only the names in `attributes`, so
     * those are read as lines with no row behind them — the body still draws, just without links.
     */
    function umlContentLines(d) {
        if (d.contents && d.contents.length) {
            return d.contents;
        }
        return (d.attributes || []).map(function (name) { return { name: name }; });
    }

    /** The names a package's body draws, in the order {@link umlContentLines} lists them. */
    function umlContentNames(d) {
        return umlContentLines(d).map(function (content) { return content.name; });
    }

    /** The `i`th content line, or an empty one — so a line the payload did not describe is simply inert. */
    function umlContentAt(d, i) {
        return umlContentLines(d)[i] || {};
    }

    /** The tab's outline: rounded at the top, square where it meets the body. */
    function umlPackageTabPath(left, top, tabW) {
        var r = Math.min(3, tabW / 2);
        return 'M' + left + ',' + (top + UML_NAME_H)
            + 'L' + left + ',' + (top + r)
            + 'Q' + left + ',' + top + ' ' + (left + r) + ',' + top
            + 'L' + (left + tabW - r) + ',' + top
            + 'Q' + (left + tabW) + ',' + top + ' ' + (left + tabW) + ',' + (top + r)
            + 'L' + (left + tabW) + ',' + (top + UML_NAME_H) + 'Z';
    }

    /** One compartment separator, spanning the box at `y` (relative to the box's centre). */
    function separator(g, d, y) {
        g.append('line').attr('class', 'uml-separator')
            .attr('x1', -d.w / 2).attr('x2', d.w / 2)
            .attr('y1', y).attr('y2', y)
            .attr('stroke', '#3498db').attr('stroke-width', 1);
    }

    /** A member compartment's height: its padding plus one line per member, empty or not. */
    function umlCompartmentHeight(lines) {
        return UML_MEMBER_PAD + (lines ? lines.length : 0) * UML_MEMBER_LINE_H;
    }

    /**
     * The member lines of one compartment as `{text, y}` rows, `y` relative to the box's centre and
     * `before` the height of the compartments above it.  The 9 is the baseline within a 12-unit row: the
     * ascender sits at 2 and the descender at 11, so no glyph reaches the separator.
     */
    function umlMemberLines(d, lines, before) {
        var top = -d.h / 2 + UML_NAME_H + before + UML_MEMBER_PAD;
        return (lines || []).map(function (text, i) {
            return { text: text, y: top + i * UML_MEMBER_LINE_H + 9 };
        });
    }

    /**
     * Shrinks a text label until it fits the width it is given, so a long class or member name stays
     * inside its box rather than crossing into the neighbouring one.  Measured, not estimated — character
     * widths vary a lot — and it only ever shrinks: the box was already widened to fit its class name on
     * the server, and the class-diagram PDF keeps one font size for the same reason.
     */
    function shrinkToFit(selection, available, maxFont) {
        selection.each(function () {
            var needed = this.getComputedTextLength();
            if (needed > available && needed > 0) {
                d3.select(this).attr('font-size', Math.max(6, maxFont * available / needed));
            }
        });
    }

    /** The two ends of a connector, as the points where its straight line leaves each box. */
    function umlLinkEnds(link) {
        var a = link.reversed ? link.target : link.source;
        var b = link.reversed ? link.source : link.target;
        return { from: umlEdgePoint(a, b), to: umlEdgePoint(b, a) };
    }

    /**
     * Where the line from `node`'s centre towards `towards`'s centre crosses `node`'s rectangle.
     *
     * Both axis scales are computed and the smaller one wins, which is what makes the point land on an
     * edge rather than on a corner; an axis the line does not move along contributes infinity so it can
     * never win.  This is the whole of the force path's routing — an orthogonal route would have to be
     * re-computed on every tick, and would then be a different route every tick.
     */
    function umlEdgePoint(node, towards) {
        var dx = towards.x - node.x;
        var dy = towards.y - node.y;
        if (dx === 0 && dy === 0) return { x: node.x, y: node.y };
        var scale = Math.min(
            Math.abs(dx) < 1e-9 ? Infinity : (node.w / 2) / Math.abs(dx),
            Math.abs(dy) < 1e-9 ? Infinity : (node.h / 2) / Math.abs(dy));
        return { x: node.x + dx * scale, y: node.y + dy * scale };
    }

    /**
     * A rectangular collision force — `d3.forceCollide` treats a node as a **circle**, and these are boxes.
     *
     * A circle large enough to clear a 180x40 class box on every side has a radius of ~92, so two boxes
     * beside each other would be pushed 184 units apart to gain the 18 of clearance the layout wants.  This
     * resolves the overlap on the axis of least penetration instead: boxes side by side slide apart
     * sideways, boxes stacked slide apart vertically, which is the arrangement the server's seed grid
     * already has, so the force tidies the picture rather than rearranging it.
     *
     * Pairs are found through a uniform grid, not all `n²` of them: the assembled whole-model diagram is
     * ~970 boxes, and half a million pairs per tick for a few hundred ticks is not a picture anyone waits
     * for.  The cell is the largest box dimension, so only the eight neighbouring cells can hold a box that
     * overlaps.
     */
    function forceRectCollide(gap) {
        var nodes = [];
        var cell = 1;

        function collide(alpha) {
            var buckets = {};
            var i;
            var j;
            for (i = 0; i < nodes.length; i++) {
                var n = nodes[i];
                var key = Math.floor(n.x / cell) + ',' + Math.floor(n.y / cell);
                (buckets[key] || (buckets[key] = [])).push(n);
            }
            for (i = 0; i < nodes.length; i++) {
                var a = nodes[i];
                var cx = Math.floor(a.x / cell);
                var cy = Math.floor(a.y / cell);
                for (var ox = -1; ox <= 1; ox++) {
                    for (var oy = -1; oy <= 1; oy++) {
                        var bucket = buckets[(cx + ox) + ',' + (cy + oy)];
                        if (!bucket) continue;
                        for (j = 0; j < bucket.length; j++) {
                            // Each pair is reached twice, once from each side, so each visit moves it by
                            // half the overlap — the two together are exactly the separation needed.
                            separate(a, bucket[j], alpha);
                        }
                    }
                }
            }
        }

        function separate(a, b, alpha) {
            if (a === b) return;
            var dx = b.x - a.x;
            var dy = b.y - a.y;
            var overlapX = (a.w + b.w) / 2 + gap - Math.abs(dx);
            if (overlapX <= 0) return;
            var overlapY = (a.h + b.h) / 2 + gap - Math.abs(dy);
            if (overlapY <= 0) return;
            if (overlapX < overlapY) {
                var sx = (dx < 0 ? -1 : 1) * overlapX / 2 * alpha;
                if (a.fx == null) a.x -= sx;
                if (b.fx == null) b.x += sx;
            } else {
                var sy = (dy < 0 ? -1 : 1) * overlapY / 2 * alpha;
                if (a.fy == null) a.y -= sy;
                if (b.fy == null) b.y += sy;
            }
        }

        collide.initialize = function (n) {
            nodes = n;
            cell = 1;
            for (var i = 0; i < nodes.length; i++) {
                cell = Math.max(cell, nodes[i].w || 0, nodes[i].h || 0);
            }
        };
        collide.gap = function (value) {
            if (!arguments.length) return gap;
            gap = value;
            return collide;
        };
        return collide;
    }

    /**
     * Where to hang a connector's label: the middle of its **longest straight run**, nudged clear of the
     * line.
     *
     * Not the middle of the route — that is usually a corner, and a label sitting on a corner makes the
     * edge look indented around it.  In the bundled model 124 of the 156 labelled connectors put the
     * label on a corner, and the 28 two-point ones put it on the end point itself.
     */
    function labelAnchor(points) {
        var best = null;
        for (var i = 1; i < points.length; i++) {
            var length = Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]);
            if (best === null || length > best.length) {
                best = { length: length, from: points[i - 1], to: points[i] };
            }
        }
        if (best === null) return null;
        var horizontal = Math.abs(best.to[0] - best.from[0]) > Math.abs(best.to[1] - best.from[1]);
        return {
            x: (best.from[0] + best.to[0]) / 2 + (horizontal ? 0 : 4),
            y: (best.from[1] + best.to[1]) / 2 + (horizontal ? -4 : 0),
            horizontal: horizontal
        };
    }

    /**
     * A straight line between two box centres, for a connector the export left without a usable path.
     *
     * A generalization's arrowhead belongs on its `source` — the super-class — so the line is stated
     * target -> source; for every other connector the direction is immaterial.
     */
    function fallbackPoints(link, byId) {
        var source = byId[link.source];
        var target = byId[link.target];
        if (!source || !target) return null;
        var from = [source.x + source.w / 2, source.y + source.h / 2];
        var to = [target.x + target.w / 2, target.y + target.h / 2];
        return link.kind === 'generalization' ? [to, from] : [from, to];
    }

    /**
     * The legend, pinned to the top-left corner: which line is which.
     *
     * Appended to the SVG rather than to the zoomed layer, so panning and zooming leave it in place.
     * Every size is multiplied by `canvasWidth / availableWidth` — the inverse of the fit that maps the
     * model's own canvas onto the cell — so the legend keeps a constant on-screen size whatever the
     * model's canvas happens to be.
     *
     * A row prints its **label** and nothing else: the explanation the payload carries for it is the
     * row's tooltip (`<title>`), so the legend reads as a key rather than as a paragraph of prose.
     */
    function appendUmlLegend(svg, data, canvasWidth, availableWidth) {
        var linkKinds = {};
        (data.links || []).forEach(function (l) { linkKinds[l.kind] = true; });

        // Only explain what the drawing actually contains: a link kind no connector uses, or an archetype
        // no box is painted in, gets no row.
        var archetypes = {};
        (data.nodes || []).forEach(function (n) { archetypes[n.color] = true; });
        var entries = (data.legend || []).filter(function (item) {
            if (item.kind === 'node') return (data.nodes || []).length > 0;
            if (item.kind === 'archetype') return !!archetypes[item.color];
            return linkKinds[item.kind];
        });
        if (entries.length === 0) return;

        var k = canvasWidth / (availableWidth || canvasWidth);
        var pad = 10 * k;
        var row = 22 * k;
        var fontSize = 12 * k;
        var sample = 26 * k;
        // The default width — what the box gets when no label can be measured.  It is never wider than the
        // canvas itself (a very narrow cell would otherwise clip the legend); a measurable label narrows it
        // further, at the end of this function.
        var boxWidth = Math.min(330 * k, canvasWidth - 24 * k);

        var legend = svg.append('g')
            .attr('class', 'uml-legend')
            .attr('transform', 'translate(' + 12 * k + ',' + 12 * k + ')');

        var box = legend.append('rect')
            .attr('width', boxWidth)
            .attr('height', entries.length * row + pad * 2)
            .attr('rx', 4 * k)
            .attr('fill', 'rgba(255, 255, 255, 0.88)')
            .attr('stroke', '#cccccc');

        entries.forEach(function (item, i) {
            var midY = pad + i * row + row / 2;
            var swatch = legend.append('g')
                .attr('transform', 'translate(' + pad + ',' + midY + ')');

            if (item.kind === 'node') {
                if (data.packages === true) {
                    // The overview's boxes are packages, so the legend shows the symbol they are drawn
                    // as: a tab over a body, in the same blue the boxes are stroked in.
                    swatch.append('rect')
                        .attr('y', -7 * k)
                        .attr('width', sample).attr('height', 14 * k)
                        .attr('fill', '#eaf3fb')
                        .attr('stroke', item.color).attr('stroke-width', 1.5);
                    swatch.append('rect')
                        .attr('y', -7 * k)
                        .attr('width', sample * 0.45).attr('height', 4 * k)
                        .attr('fill', '#ffffff')
                        .attr('stroke', item.color).attr('stroke-width', 1.5);
                } else {
                    swatch.append('rect')
                        .attr('y', -7 * k)
                        .attr('width', sample).attr('height', 14 * k)
                        .attr('rx', 2 * k)
                        .attr('fill', '#eaf3fb')
                        .attr('stroke', item.color).attr('stroke-width', 1.5);
                }
            } else if (item.kind === 'archetype') {
                // A filled swatch in the archetype's own colour — which is exactly what a box is filled
                // with, so the legend can be matched to the picture at a glance.
                swatch.append('rect')
                    .attr('y', -7 * k)
                    .attr('width', sample).attr('height', 14 * k)
                    .attr('rx', 2 * k)
                    .attr('fill', item.color)
                    .attr('stroke', '#3498db').attr('stroke-width', 1.5);
            } else {
                // A plain line for an association; the same line capped by an arrow for a
                // generalization.  The arrow is an explicit triangle so it scales with the legend —
                // the diagram's marker is sized in stroke-widths, which would not.
                var generalization = item.kind === 'generalization';
                swatch.append('line')
                    .attr('x1', 0).attr('y1', 0)
                    .attr('x2', generalization ? sample - 8 * k : sample).attr('y2', 0)
                    .attr('stroke', item.color).attr('stroke-width', 1.5);
                if (generalization) {
                    swatch.append('path')
                        .attr('d', 'M ' + (sample - 8 * k) + ' ' + (-4 * k)
                                + ' L ' + sample + ' 0 L ' + (sample - 8 * k) + ' ' + (4 * k) + ' z')
                        .attr('fill', item.color);
                }
            }

            // The row prints its label; the explanation rides as the row's tooltip, so the legend stays a
            // key.  The `<title>` hangs off the swatch group rather than off the text, so the row's own
            // `textContent` is the label and nothing else.
            swatch.append('title').text(item.description || item.label);

            legend.append('text')
                .attr('x', pad + sample + 8 * k)
                .attr('y', midY + fontSize * 0.36)
                .attr('font-size', fontSize)
                .attr('fill', '#333333')
                .text(item.label);
        });

        // The box hugs the widest label instead of standing at the default width over empty space.  A
        // panel that is not on screen measures 0, and then the default width stands.
        var widest = 0;
        legend.selectAll('text').each(function () { widest = Math.max(widest, this.getComputedTextLength()); });
        if (widest > 0) {
            box.attr('width', Math.min(pad * 2 + sample + 8 * k + widest, canvasWidth - 24 * k));
        }
    }

    /**
     * Splits a long label into lines of at most `maxChars`, so a literal node — whose name is the
     * literal's value, often an ontology's own comment running to hundreds of characters — stays a
     * node instead of a multi-thousand-pixel band across the canvas.
     *
     * Breaks on the last blank that fits and, failing that, after the last character that is neither a
     * letter nor a digit (so punctuation such as `;` or `,` is a break point too).  A run with no break
     * opportunity at all is hard-broken, which is the only case that splits a word.
     */
    function wrapLabel(value, maxChars) {
        var text = value == null ? '' : String(value);
        if (text.length <= maxChars) {
            return [text];
        }
        var lines = [];
        var rest = text;
        while (rest.length > maxChars) {
            var cut = lastBreak(rest, maxChars);
            lines.push(rest.slice(0, cut).replace(/\s+$/, ''));
            rest = rest.slice(cut).replace(/^\s+/, '');
        }
        if (rest.length) {
            lines.push(rest);
        }
        return lines;
    }

    /** Index just past the last break opportunity in the first `maxChars` characters. */
    function lastBreak(text, maxChars) {
        var i;
        for (i = maxChars; i > 0; i--) {                    // a blank is the preferred break
            if (/\s/.test(text.charAt(i - 1))) return i;
        }
        for (i = maxChars; i > 0; i--) {                    // else after any non-letter/non-digit
            if (!/[\p{L}\p{N}_]/u.test(text.charAt(i - 1))) return i;
        }
        return maxChars;                                    // nothing to break on
    }

    WicketStuff.D3 = {

        init: function (config) {

            var containerEl = document.getElementById(config.canvasId);
            if (!containerEl) {
                console.error('D3 canvas element not found:', config.canvasId);
                return;
            }
            // Measure available space from the container; fall back to config values
            var width = containerEl.clientWidth || config.initialWidth || 800;
            var height = (containerEl.clientHeight || config.initialHeight || Math.round(width * 0.6)) * 2;
            var enableDragging = config.enableDragging !== false;
            var enableZoom = config.enableZoom !== false;

            // Use D3 selections throughout — never mix jQuery with D3 selection methods
            var svg = d3.select(containerEl)
                .append('svg')
                .attr('width', '100%')
                .attr('height', height)
                .attr('viewBox', '0 0 ' + width + ' ' + height)
                .attr('preserveAspectRatio', 'xMidYMid meet');

            // Parse the graph data (expected as JSON);
            // if graphData is null, undefined, or empty, use an empty graph
            var nodes, links;
            try {
                if (config.graphData) {
                    var data = JSON.parse(config.graphData);
                    nodes = data.nodes || [];
                    links = data.links || [];
                } else {
                    nodes = [];
                    links = [];
                }
            } catch (e) {
                console.error('D3 graph data parse error:', e);
                return;
            }

            // A "uml" payload is one of two things, and the flag says which: `force:true` is the assembled
            // model's diagram, whose boxes the reader places (a seed grid plus a force simulation), while
            // anything else is the OntoUML export's own stored layout, redrawn as it was drawn.
            if (data && data.layout === 'uml') {
                if (data.force) {
                    renderForceUml(svg, containerEl, data, config);
                } else {
                    renderStaticUml(svg, containerEl, data, config);
                }
                return;
            }

            // If there's no data, render an empty graph placeholder
            if (nodes.length === 0 && links.length === 0) {
                svg.append('text')
                    .attr('x', width / 2)
                    .attr('y', height / 2)
                    .attr('text-anchor', 'middle')
                    .attr('font-family', 'sans-serif')
                    .attr('font-size', '14px')
                    .attr('fill', '#999')
                    .text('No graph data available');
                return;
            }

            // Derive subdomain groups for cluster layout.
            // Nodes with the same subdomain are pulled toward a shared group center;
            // nodes without a subdomain are pulled to the canvas center.
            var subdomains = [];
            var subdomainIndex = {};
            nodes.forEach(function (d) {
                var sd = d.subdomain || '';
                if (sd && subdomainIndex[sd] === undefined) {
                    subdomainIndex[sd] = subdomains.length;
                    subdomains.push(sd);
                }
            });
            nodes.forEach(function (d) {
                d.group = d.subdomain && subdomainIndex[d.subdomain] !== undefined
                        ? subdomainIndex[d.subdomain] : -1;
            });

            // Compute the center position for each subdomain group, spread in a ring
            // around the canvas center so groups are visually separated.
            function getGroupCenter(index) {
                if (index < 0) return { x: width / 2, y: height / 2 };
                var count = subdomains.length || 1;
                var angle = (index / count) * 2 * Math.PI - Math.PI / 2;
                var ringRadius = Math.min(width, height) * 0.30;
                return {
                    x: width / 2 + ringRadius * Math.cos(angle),
                    y: height / 2 + ringRadius * Math.sin(angle)
                };
            }

            // Force parameters from config (with sensible defaults)
            var linkDistance = config.linkDistance != null ? config.linkDistance : 180;
            var repulsionStrength = config.repulsionStrength != null ? config.repulsionStrength : -400;
            var collisionRadius = config.collisionRadius != null ? config.collisionRadius : 60;
            var distStrength = config.distributionStrength != null ? config.distributionStrength : 0.08;

            // Create the force simulation
            // Nodes in the same subdomain group are pulled toward a shared group center
            // (arranged in a ring), while ungrouped nodes converge on the canvas center.
            var simulation = d3.forceSimulation(nodes)
                .force('link', d3.forceLink(links)
                    .id(function (d) { return d.id; })
                    // Keep the edge-to-edge gap constant regardless of node size:
                    // center-to-center = r(src) + r(tgt) + linkDistance, so larger
                    // central nodes don't crowd their satellites (r is 8-32 px).
                    .distance(function (d) {
                        return (d.source.r || 8) + (d.target.r || 8) + linkDistance;
                    }))
                .force('charge', d3.forceManyBody()
                    .strength(repulsionStrength))
                .force('collide', d3.forceCollide()
                    .radius(function (d) {
                        return (d.r || 8) * 4 + collisionRadius;
                    }))
                .force('x', d3.forceX(function (d) {
                    return getGroupCenter(d.group).x;
                }).strength(distStrength))
                .force('y', d3.forceY(function (d) {
                    return getGroupCenter(d.group).y;
                }).strength(distStrength));

            // Re-center/distribute on window resize so the force layout stays proportioned to the container
            window.addEventListener('resize', function () {
                var w = containerEl.clientWidth;
                var h = containerEl.clientHeight;
                if (w && (w !== width || h !== height)) {
                    width = w;
                    height = h || height;
                    svg.attr('viewBox', '0 0 ' + width + ' ' + height);
                    simulation.force('x', d3.forceX(function (d) {
                        return getGroupCenter(d.group).x;
                    }).strength(distStrength));
                    simulation.force('y', d3.forceY(function (d) {
                        return getGroupCenter(d.group).y;
                    }).strength(distStrength));
                    simulation.alpha(0.5).restart();
                }
            });

            // Zoom behaviour (applied to the container)
            var g = svg.append('g');
            if (enableZoom) {
                var zoom = d3.zoom()
                    .scaleExtent([0.1, 10])
                    .on('zoom', function (event) {
                        g.attr('transform', event.transform);
                    });
                svg.call(zoom);
            }

            // ---- Extract legend from data ----
            var legend = data.legend || [];

            // ---- Links (lines) ----
            var link = g.append('g')
                .attr('class', 'd3-links')
                .selectAll('line')
                .data(links)
                .join('line')
                .attr('stroke', '#999')
                .attr('stroke-opacity', 0.6)
                .attr('stroke-width', function (d) { return Math.sqrt(d.value || 1); });

            // ---- Edge labels ----
            var linkLabel = g.append('g')
                .attr('class', 'd3-link-labels')
                .selectAll('text')
                .data(links)
                .join('text')
                .text(function (d) { return d.label || ''; })
                .attr('font-size', 9)
                .attr('fill', '#555')
                .attr('text-anchor', 'middle')
                .attr('dominant-baseline', 'central');

            // ---- Helper functions for node rendering ----
            function nodeScale(d) {
                return (d.r || 8) / 8;
            }
            function clampFont(px) {
                return Math.max(6, Math.min(18, Math.round(px)));
            }

            // ---- Nodes (rounded rectangles + multi-line labels) ----
            var node = g.append('g')
                .attr('class', 'd3-nodes')
                .selectAll('g')
                .data(nodes)
                .join('g');

            // Rounded rectangle behind the label (fitted after measuring)
            node.append('rect')
                .attr('rx', 9)
                .attr('ry', 9)
                .attr('fill', function (d) { return d.color || '#69b3a2'; })
                .attr('stroke', '#333')
                .attr('stroke-width', function (d) {
                    return d.approved ? 4 : 2;
                })
                .attr('stroke-dasharray', function (d) {
                    return d.synonym ? '6,4' : null;
                });

            // 2-4 line label: optional italic subdomain / name / <<POS>> / source ontology
            var nodeText = node.append('text')
                .attr('text-anchor', 'middle')
                .attr('dominant-baseline', 'central')
                .attr('fill', '#fff')
                .attr('pointer-events', 'none');

            // Line 1: subdomain in italics (only if present — skip empty/falsy)
            nodeText.filter(function (d) { return d.subdomain; })
                .append('tspan')
                .attr('x', 0)
                .attr('dy', '-0.9em')
                .attr('font-style', 'italic')
                .attr('font-size', function (d) {
                    return clampFont(7 * nodeScale(d));
                })
                .text(function (d) { return d.subdomain; });

            // Line 2..n: candidate name (bold, underlined) — WRAPPED when it is long.
            //   A literal's name is the literal's own value (an ontology comment can run to hundreds
            //   of characters), which would otherwise stretch its node across the whole canvas.
            //   When subdomain is present the first line sits 1.15em below the subdomain line, and
            //   when it is absent the first line is at the text baseline; each further line is one
            //   1.15em below the previous one.
            //   The click handler is bound to the node <g>, not to the text, so every wrapped line
            //   stays part of the same clickable node.
            node.each(function (d) {
                var label = d3.select(this).select('text');
                var fontSize = clampFont(11 * nodeScale(d));
                wrapLabel(d.name || d.id, LABEL_MAX_CHARS).forEach(function (line, index) {
                    label.append('tspan')
                        .attr('x', 0)
                        .attr('dy', index === 0 ? (d.subdomain ? '1.15em' : '0') : '1.15em')
                        .attr('font-weight', 'bold')
                        .attr('text-decoration', 'underline')
                        .attr('font-size', fontSize)
                        .text(line);
                });
            });

            // Line 3: POS in double brackets
            // The dy offset scales with the node's size multiplier so the gap
            // between the name line and the POS line stays proportional to the
            // rest of the label (font sizes and padding all scale with nodeScale).
            // A base of 14px prevents the crowding that 1.15em @ 7px (~8px) caused.
            nodeText.append('tspan')
                .attr('x', 0)
                .attr('dy', function (d) {
                    return (14 * nodeScale(d)).toFixed(1) + 'px';
                })
                .attr('font-size', function (d) {
                    return clampFont(7 * nodeScale(d));
                })
                .text(function (d) { return '<<' + (d.pos || '') + '>>'; });

            // Line 4: the source reference ontology (gist, sBPMN, BFO, Schema.org, …) — only when the
            //   payload carries one.  An ontology node's entry was read from a specific named graph, so
            //   this shows at a glance which vocabulary the concept came from.  Omitted when blank
            //   (e.g. the bundled single-file ontology), so the CRC candidate graphs are unaffected.
            nodeText.filter(function (d) { return d.ontology; })
                .append('tspan')
                .attr('x', 0)
                .attr('dy', function (d) {
                    return (12 * nodeScale(d)).toFixed(1) + 'px';
                })
                .attr('font-size', function (d) {
                    return clampFont(6 * nodeScale(d));
                })
                .attr('font-style', 'italic')
                .attr('opacity', 0.9)
                .text(function (d) { return d.ontology; });

            // Fit the rectangle around the measured label, and CENTRE the (possibly multi-line) label
            // on the node's own origin.  Both are needed together: a wrapped name makes the text block
            // far taller than the one-line case the box was originally sized for, so a box that merely
            // grew (with the label hanging below the origin) would leave the text half outside it.
            // NOTE: getBBox() ignores the element's own transform, so it is measured once and the same
            // shift is applied to the box.
            node.each(function (d) {
                var labelEl = d3.select(this).select('text').node();
                if (!labelEl) return;
                var bbox = labelEl.getBBox();
                var sf = nodeScale(d) || 1;
                var pad = 7 * sf;
                var shift = -(bbox.y + bbox.height / 2);
                d3.select(this).select('text').attr('transform', 'translate(0,' + shift.toFixed(1) + ')');
                var w = bbox.width + pad * 2;
                var h = bbox.height + pad * 2;
                d3.select(this).select('rect')
                    .attr('width', w)
                    .attr('height', h)
                    .attr('x', -w / 2)
                    .attr('y', -h / 2);
            });

            // Node click handler
            //   * a CRC candidate node opens its entity page in a new tab (as before);
            //   * an ontology node re-runs the ontology search for the node's own label — the text the
            //     underlined name shows — and redraws the graph in place.  The label is what the user
            //     clicks, and it is exactly what `Corpora#searchOntologyAsD3Graph` matches on.
            // The drill-down JSON for a term, from the Wicket-mounted resource
            // (`OntologyDiagramResourceInitializer`).  The renderer cannot address a Causeway
            // view-model page directly: its id is a *signed* memento that only the framework can mint,
            // and the RestfulObjects API answers a page-originated fetch with 401.
            function reSearchJson(term) {
                var url = (config.contextPath || '') + '/wicket/ontology-diagram?term='
                        + encodeURIComponent(term);
                return fetch(url, { headers: { 'Accept': 'application/json' } })
                    .then(function (response) {
                        if (!response.ok) throw new Error('HTTP ' + response.status);
                        return response.text();
                    });
            }

            // Redraw this canvas with the clicked node's neighbourhood.
            function reSearch(term) {
                return reSearchJson(term).then(function (json) {
                    // destroy() stops the simulation and clears the canvas; init() then draws the
                    // drilled-down graph (and re-attaches these handlers, so drilling down again works).
                    WicketStuff.D3.destroy(config.canvasId);
                    WicketStuff.D3.init(Object.assign({}, config, { graphData: json }));
                });
            }

            // Open the same drill-down in a NEW TAB, as the FULL Causeway object page (Ctrl/Cmd-click,
            // like a plain link).  The object page cannot be linked to directly — a view-model URL's id
            // is a signed memento only the framework can mint — so this points at a mount that lets
            // Causeway mint it and redirects to the object page.
            function reSearchInNewTab(term) {
                window.open((config.contextPath || '') + '/wicket/ontology-drilldown?term='
                        + encodeURIComponent(term), '_blank');
                return Promise.resolve();
            }

            node.style('cursor', function (d) {
                    return (d.candidateId && d.objectType) || (data.reSearch && d.name) ? 'pointer' : 'default';
                })
                .on('click', function (event, d) {
                    if (event.defaultPrevented) return;
                    if (d.candidateId && d.objectType) {
                        // A CRC candidate: its entity page, always in a new tab.
                        window.open(entityPageUrl(config.contextPath, d.objectType, d.candidateId), '_blank');
                        return;
                    }
                    // Only ontology payloads opt in (`reSearch`): a CRC diagram's synthetic nodes have
                    // no candidate to open and must not trigger an ontology search.  Ctrl/Cmd-click
                    // opens the drill-down in a new tab, like a plain link.
                    if (data.reSearch && d.name) {
                        var drill = (event.ctrlKey || event.metaKey) ? reSearchInNewTab : reSearch;
                        drill(d.name).catch(function (e) {
                            console.error('Ontology re-search for "' + d.name + '" failed:', e);
                        });
                    }
                })
                .on('auxclick', function (event, d) {
                    // Middle-click -> new tab, as for any link.
                    if (event.defaultPrevented || event.button !== 1) return;
                    if (data.reSearch && d.name && !(d.candidateId && d.objectType)) {
                        event.preventDefault();
                        reSearchInNewTab(d.name).catch(function (e) {
                            console.error('Ontology re-search for "' + d.name + '" failed:', e);
                        });
                    }
                });

            // Drag behaviour
            if (enableDragging) {
                var drag = d3.drag()
                    .on('start', function (event, d) {
                        if (!event.active) simulation.alphaTarget(0.3).restart();
                        d.fx = d.x;
                        d.fy = d.y;
                    })
                    .on('drag', function (event, d) {
                        d.fx = event.x;
                        d.fy = event.y;
                    })
                    .on('end', function (event, d) {
                        if (!event.active) simulation.alphaTarget(0);
                        d.fx = null;
                        d.fy = null;
                    });
                node.call(drag);
            }

            // ---- Legend ----
            if (legend.length > 0) {
                // Legend layout:
                //   title baseline at y=18
                //   rowY = 35 + i*28  (first swatch top at y=35, pitch 28)
                //   swatch height = 14 → last swatch bottom = 21 + 28n
                //   bottom padding = 10 → box height = 31 + 28n
                var legendGroup = svg.append('g')
                    .attr('class', 'd3-legend')
                    .attr('transform', 'translate(20, 20)');
                // Compute the widest row text to size the background box.  A row is its label: the
                // explanation rides as the row's tooltip (below), so a long description can no longer
                // stretch the legend across the canvas.
                var maxTextWidth = 240;
                legend.forEach(function (item) {
                    var text = item.label || '';
                    // Approximate width: ~5.6 px per character at font-size 10
                    var w = 8 + 20 + text.length * 5.6 + 8;
                    if (w > maxTextWidth) maxTextWidth = w;
                });
                legendGroup.append('rect')
                    .attr('width', maxTextWidth)
                    .attr('height', legend.length * 28 + 31)
                    .attr('fill', 'rgba(255, 255, 255, 0.80)')
                    .attr('stroke', '#ccc')
                    .attr('rx', 4);
                legendGroup.append('text')
                    .attr('x', 8).attr('y', 18)
                    .attr('font-size', 11).attr('font-weight', 'bold')
                    .attr('fill', '#333')
                    .text('Node Type Legend');
                legend.forEach(function (item, i) {
                    var rowY = 35 + i * 28;
                    var swatch = legendGroup.append('g');
                    swatch.append('rect')
                        .attr('x', 8).attr('y', rowY)
                        .attr('width', 14).attr('height', 14)
                        .attr('fill', item.color).attr('rx', 2);
                    if (item.description) {
                        swatch.append('title').text(item.description);
                    }
                    swatch.append('text')
                        .attr('x', 28).attr('y', rowY + 11)
                        .attr('font-size', 10).attr('fill', '#444')
                        .text(item.label || '');
                });
            }

            // ---- Simulation tick ----
            simulation.on('tick', function () {
                link
                    .attr('x1', function (d) { return d.source.x; })
                    .attr('y1', function (d) { return d.source.y; })
                    .attr('x2', function (d) { return d.target.x; })
                    .attr('y2', function (d) { return d.target.y; });

                linkLabel
                    .attr('x', function (d) { return (d.source.x + d.target.x) / 2; })
                    .attr('y', function (d) { return (d.source.y + d.target.y) / 2; });

                node.attr('transform', function (d) {
                    return 'translate(' + d.x + ',' + d.y + ')';
                });
            });

            // Store the simulation for cleanup (on the DOM element)
            containerEl._d3Simulation = simulation;
        },

        destroy: function (canvasId) {
            var containerEl = document.getElementById(canvasId);
            if (!containerEl) return;
            var simulation = containerEl._d3Simulation;
            if (simulation) {
                simulation.stop();
                delete containerEl._d3Simulation;
            }
            // Clear all children (SVG and any other content)
            while (containerEl.firstChild) {
                containerEl.removeChild(containerEl.firstChild);
            }
        }

    };

})(jQuery);
