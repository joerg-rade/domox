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
        svg.append('defs').append('marker')
            .attr('id', 'uml-generalization-arrow')
            .attr('viewBox', '0 0 10 10')
            .attr('refX', 10).attr('refY', 5)
            .attr('markerWidth', 9).attr('markerHeight', 9)
            .attr('orient', 'auto-start-reverse')
            .append('path')
            .attr('d', 'M 0 0 L 10 5 L 0 10 z')
            .attr('fill', '#e67e22');

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
                .attr('stroke', link.kind === 'generalization' ? '#e67e22' : '#7f8c8d')
                .attr('stroke-width', 1.5);
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
     */
    function appendUmlLegend(svg, data, canvasWidth, availableWidth) {
        var linkKinds = {};
        (data.links || []).forEach(function (l) { linkKinds[l.kind] = true; });

        // Only explain what the drawing actually contains.
        var entries = (data.legend || []).filter(function (item) {
            return item.kind === 'node' ? (data.nodes || []).length > 0 : linkKinds[item.kind];
        });
        if (entries.length === 0) return;

        var k = canvasWidth / (availableWidth || canvasWidth);
        var pad = 10 * k;
        var row = 22 * k;
        var fontSize = 12 * k;
        var sample = 26 * k;
        // Never wider than the canvas itself (a very narrow cell would otherwise clip the legend).
        var boxWidth = Math.min(330 * k, canvasWidth - 24 * k);

        var legend = svg.append('g')
            .attr('class', 'uml-legend')
            .attr('transform', 'translate(' + 12 * k + ',' + 12 * k + ')');

        legend.append('rect')
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
                swatch.append('rect')
                    .attr('y', -7 * k)
                    .attr('width', sample).attr('height', 14 * k)
                    .attr('rx', 2 * k)
                    .attr('fill', '#eaf3fb')
                    .attr('stroke', item.color).attr('stroke-width', 1.5);
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

            legend.append('text')
                .attr('x', pad + sample + 8 * k)
                .attr('y', midY + fontSize * 0.36)
                .attr('font-size', fontSize)
                .attr('fill', '#333333')
                .text(item.label + ' \u2014 ' + (item.description || ''));
        });
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

            // A "uml" payload carries its own geometry (the model's stored layout), so it is drawn
            // statically — no force simulation, and no re-layout.
            if (data && data.layout === 'uml') {
                renderStaticUml(svg, containerEl, data, config);
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
                    var basePath = config.contextPath || '';
                    if (d.candidateId && d.objectType) {
                        // A CRC candidate: its entity page, always in a new tab.
                        var entityUrl = basePath + '/wicket/object/' + encodeURIComponent(d.objectType) + ':' + d.candidateId;
                        window.open(entityUrl, '_blank');
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
                // Compute the widest row text to size the background box.
                var maxTextWidth = 240;
                legend.forEach(function (item) {
                    var text = (item.label || '') + ' \u2014 ' + (item.description || '');
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
                    swatch.append('text')
                        .attr('x', 28).attr('y', rowY + 11)
                        .attr('font-size', 10).attr('fill', '#444')
                        .text((item.label || '') + ' \u2014 ' + (item.description || ''));
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
