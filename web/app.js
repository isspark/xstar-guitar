(function () {
  "use strict";

  const SVG_NS = "http://www.w3.org/2000/svg";
  const $ = (id) => document.getElementById(id);
  const scoreHost = $("score");
  const scoreScroll = $("score-scroll");
  const emptyState = $("empty-state");
  const engineStatus = $("engine-status");
  const renderStatus = $("render-status");
  const positionStatus = $("position-status");
  const tempoInput = $("tempo-input");
  const tempoOutput = $("tempo-output");
  const jianpuToggle = $("jianpu-toggle");
  const lyricsToggle = $("lyrics-toggle");
  const followToggle = $("follow-toggle");
  const scoreTitle = $("score-title");
  const scoreFile = $("score-file");
  const importScoreButton = $("import-score");
  const builtInScore = $("built-in-score");

  const state = {
    api: null,
    document: null,
    svg: null,
    extensionNodes: [],
    scale: 1,
    baseAlphaTex: null,
    playbackCapo: 0,
    playbackTempo: 72
  };

  function setStatus(text, type) {
    engineStatus.textContent = text;
    engineStatus.className = "status " + (type ? "status-" + type : "status-pending");
  }

  function showError(message, statusText = "加载失败") {
    setStatus(statusText, "error");
    renderStatus.textContent = message;
    emptyState.textContent = message;
    emptyState.classList.add("visible");
  }

  function createSvgNode(name, attrs) {
    const node = document.createElementNS(SVG_NS, name);
    Object.entries(attrs || {}).forEach(([key, value]) => node.setAttribute(key, String(value)));
    return node;
  }

  // Score JSON owns the chord label and fingering. alphaTab receives tab
  // notation only, so the custom layer emits exactly one chord block.
  function renderChordDiagram(group, chordName, diagram, x, y) {
    if (!diagram || !Array.isArray(diagram.frets)) return;
    // Use an explicit title row inside the reserved system header. Keeping the
    // title and grid in the same group avoids SVG clipping at the page top.
    const gridTop = Math.max(64, y - 112);
    const left = x - 24;
    const chord = createSvgNode("g", { class: "chord-diagram", "data-layer": "chord-diagram" });
    const title = createSvgNode("text", {
      x,
      y: Math.max(28, gridTop - 14),
      class: "chord-diagram-name",
      "data-chord-title": chordName,
      "dominant-baseline": "alphabetic"
    });
    title.textContent = chordName;
    chord.appendChild(title);
    for (let fret = 0; fret < 5; fret++) {
      chord.appendChild(createSvgNode("line", {
        x1: left, y1: gridTop + fret * 10, x2: left + 48, y2: gridTop + fret * 10,
        class: fret === 0 ? "chord-nut" : "chord-grid"
      }));
    }
    for (let stringIndex = 0; stringIndex < 6; stringIndex++) {
      const stringX = left + stringIndex * 9.6;
      chord.appendChild(createSvgNode("line", {
        x1: stringX, y1: gridTop, x2: stringX, y2: gridTop + 40, class: "chord-grid"
      }));
      const fretValue = diagram.frets[stringIndex];
      const marker = createSvgNode("text", { x: stringX, y: gridTop - 4, class: "chord-marker" });
      marker.textContent = fretValue === "x" ? "×" : fretValue === 0 ? "○" : "";
      chord.appendChild(marker);
      if (typeof fretValue === "number" && fretValue > 0) {
        chord.appendChild(createSvgNode("circle", {
          cx: stringX, cy: gridTop + (fretValue - .5) * 10, r: 4, class: "chord-dot"
        }));
      }
    }
    if (diagram.baseFret > 1) {
      const fretLabel = createSvgNode("text", { x: left - 10, y: gridTop + 8, class: "chord-marker" });
      fretLabel.textContent = diagram.baseFret;
      chord.appendChild(fretLabel);
    }
    group.appendChild(chord);
  }

  function removeAlphaTabLyrics(alphaTex) {
    // The custom layer owns the final lyric row. Remove AlphaTex lyric
    // annotations so alphaTab cannot render a second row above the staff.
    return alphaTex
      .replace(/\s+lyrics\s+"(?:[^"\\]|\\.)*"/g, "")
      .replace(/\s+lyrics\s+'(?:[^'\\]|\\.)*'/g, "");
  }

  function removeAlphaTabChordNamesFromTex(alphaTex) {
    return alphaTex.replace(/\{ch\s+"(?:[^"\\]|\\.)*"\s*\}/g, "");
  }

  function removeAlphaTabChordDiagrams(alphaTex) {
    return alphaTex.replace(/^\\chordDiagramsInScore\s+true\s*$/gm, "");
  }


  function removeAlphaTabCredit() {
    if (!state.svg) return;
    [...state.svg.querySelectorAll("text")]
      .filter((node) => node.textContent.trim().toLowerCase().includes("rendered by alphatab"))
      .forEach((node) => node.remove());
  }

  function scheduleCreditCleanup() {
    removeAlphaTabCredit();
    [0, 50, 150, 300].forEach((delay) => window.setTimeout(removeAlphaTabCredit, delay));
  }

  async function fetchJson(path) {
    const response = await fetch(path, { cache: "no-store" });
    if (!response.ok) throw new Error(`无法加载 Score JSON：${response.status} ${path}`);
    return response.json();
  }

  function normalizeScoreDocument(value) {
    let documentData = value;
    // Permit exported wrappers such as { score: { ... } } without changing
    // the canonical Score JSON format used by the runtime.
    if (documentData && documentData.score && typeof documentData.score === "object") {
      documentData = documentData.score;
    }
    if (!documentData || typeof documentData !== "object") {
      throw new Error("Score JSON 顶层必须是对象");
    }
    // Accept the short alphaTex form when importing hand-written files.
    if (!documentData.alphaTabSource) {
      const alphaTex = documentData.alphaTex || documentData.alphatex || documentData.alphaTab;
      if (typeof alphaTex === "string") {
        documentData.alphaTabSource = { type: "alphatex", text: alphaTex };
      } else if (documentData.alphaTexSource && typeof documentData.alphaTexSource === "object") {
        documentData.alphaTabSource = documentData.alphaTexSource;
      }
    }
    return window.scoreImageAdapter ? window.scoreImageAdapter.expand(documentData) : documentData;
  }

  async function fetchAlphaTex(source) {
    if (!source) throw new Error("Score JSON 缺少 alphaTabSource");
    if (source.text) {
      let inline = source.text;
      if (source.lyricsMode === "custom-only") inline = removeAlphaTabLyrics(inline);
      if (source.chordDiagramsMode === "custom-only") inline = removeAlphaTabChordDiagrams(inline);
      inline = removeAlphaTabChordNamesFromTex(inline);
      return inline;
    }
    if (!source.path) throw new Error("Score JSON 缺少 alphaTabSource.path 或 alphaTabSource.text");
    const baseUrl = state.document && state.document.__baseUrl ? state.document.__baseUrl : document.baseURI;
    const sourceUrl = new URL(source.path, baseUrl).href;
    const response = await fetch(sourceUrl, { cache: "no-store" });
    if (!response.ok) throw new Error(`无法加载 AlphaTex：${response.status} ${sourceUrl}`);
    const text = await response.text();
    const marker = `${source.global || "latestAlphaTex"} =`;
    const start = text.indexOf(marker);
    const templateStart = text.indexOf("`", start);
    const templateEnd = text.lastIndexOf("`");
    if (start < 0 || templateStart < 0 || templateEnd <= templateStart) {
      throw new Error("AlphaTex 源文件不是受支持的 String.raw 模板格式");
    }
    let alphaTex = text.slice(templateStart + 1, templateEnd);
    if (source.lyricsMode === "custom-only") alphaTex = removeAlphaTabLyrics(alphaTex);
    if (source.chordDiagramsMode === "custom-only") alphaTex = removeAlphaTabChordDiagrams(alphaTex);
    alphaTex = removeAlphaTabChordNamesFromTex(alphaTex);
    return alphaTex;
  }

  function validateScoreDocument(document) {
    if (!document || document.version !== 1) throw new Error("不支持的 Score JSON 版本");
    if (!document.source || !document.source.id || !document.source.title) throw new Error("Score JSON 缺少 source 元数据");
    if (!document.alphaTabSource || (!document.alphaTabSource.path && !document.alphaTabSource.text)) {
      throw new Error("Score JSON 缺少 alphaTabSource.path 或 alphaTabSource.text");
    }
    if (!document.jianpu || !Array.isArray(document.jianpu.tracks)) throw new Error("Score JSON 缺少 jianpu.tracks");
    document.jianpu.tracks.forEach((track) => {
      if (!track.id || !Array.isArray(track.events)) throw new Error("简谱 track 格式错误");
      track.events.forEach((event) => {
        const standard = Number.isInteger(event.measureIndex) && Array.isArray(event.events);
        const legacy = Number.isInteger(event.measure) && Array.isArray(event.values);
        if (!standard && !legacy) throw new Error("简谱事件必须使用 measureIndex + events 格式");
        if (standard && event.events.some((note) => !Number.isFinite(note.tick)
          || typeof note.value !== "string"
          || (!note.value && !(typeof note.lyric === "string" && note.lyric.trim()))
          || !Number.isFinite(note.durationTicks))) {
          throw new Error("简谱 note 必须包含 tick、value 和 durationTicks");
        }
        if (legacy && event.lyrics && event.lyrics.length > event.values.length) throw new Error("歌词数量不能超过简谱音符数量");
      });
    });
  }

  function normalizedEvents(track) {
    return track.events.flatMap((event) => {
      if (Number.isInteger(event.measureIndex)) {
        return [{
          measureIndex: event.measureIndex,
          chord: event.chord,
          events: event.events.map((note, index) => ({
            ...note,
            beatIndex: Number.isInteger(note.beatIndex) ? note.beatIndex : track.alignByTick ? undefined : index
          }))
        }];
      }
      return [{
        measureIndex: event.measure,
        chord: event.chord,
        events: event.values.map((value, index) => ({
          tick: index * 480,
          value,
          durationTicks: 480,
          beatIndex: index,
          lyric: event.lyrics && event.lyrics[index]
        }))
      }];
    });
  }

  function getSvgMetrics() {
    const raw = state.svg && state.svg.getAttribute("viewBox");
    const values = (raw || "0 0 1000 1000").trim().split(/[ ,]+/).map(Number);
    return { x: values[0] || 0, y: values[1] || 0, width: values[2] || 1000, height: values[3] || 1000 };
  }

  function removeExtension() {
    state.extensionNodes.forEach((node) => node.remove());
    state.extensionNodes = [];
  }

  function getBeatBounds(measureIndex, tick, beatIndex) {
    const api = state.api;
    const masterBar = api && api.boundsLookup && typeof api.boundsLookup.findMasterBarByIndex === "function"
      ? api.boundsLookup.findMasterBarByIndex(measureIndex)
      : null;
    const renderedBeats = masterBar && masterBar.bars
      ? masterBar.bars.flatMap((bar) => bar.beats || [])
      : [];
    if (!renderedBeats.length) return null;
    const masterBarStart = Number(masterBar.start || 0);
    if (Number.isInteger(beatIndex) && beatIndex >= 0 && beatIndex < renderedBeats.length) {
      const explicitBeat = renderedBeats[beatIndex];
      return explicitBeat ? { beat: explicitBeat.beat, bounds: explicitBeat } : null;
    }
    const requestedTick = Number(tick || 0);
    const closest = renderedBeats.reduce((current, candidate, index) => {
      const candidateBeat = candidate.beat || {};
      const absoluteStart = Number(candidateBeat.start ?? candidateBeat.tick ?? index * 480);
      const relativeStart = absoluteStart >= masterBarStart ? absoluteStart - masterBarStart : absoluteStart;
      const currentBeat = current.value.beat || {};
      const currentAbsolute = Number(currentBeat.start ?? currentBeat.tick ?? current.index * 480);
      const currentRelative = currentAbsolute >= masterBarStart ? currentAbsolute - masterBarStart : currentAbsolute;
      return Math.abs(relativeStart - requestedTick) < Math.abs(currentRelative - requestedTick)
        ? { value: candidate, index }
        : current;
    }, {
      value: renderedBeats[0],
      index: 0
    });
    const beatBounds = closest.value;
    return beatBounds ? { beat: beatBounds.beat, bounds: beatBounds } : null;
  }

  function getRealBeatRect(beatBounds) {
    const rect = beatBounds && (beatBounds.realBounds || beatBounds.visualBounds);
    if (!rect || !Number.isFinite(rect.x) || !Number.isFinite(rect.y)) return null;
    return { x: rect.x, y: rect.y, width: rect.w || rect.width || 0, height: rect.h || rect.height || 0 };
  }

  function getBeatAnchorRect(beatBounds) {
    const rect = getRealBeatRect(beatBounds);
    if (!rect) return null;
    // Return the final text anchor coordinate. Do not center this value again
    // in renderJianpu: noteHeadBounds is already the rendered tab glyph box.
    const note = beatBounds && beatBounds.notes && beatBounds.notes.find((item) => item.noteHeadBounds);
    const noteRect = note && note.noteHeadBounds;
    if (noteRect && Number.isFinite(noteRect.x)) {
      return {
        // noteHeadBounds is a rectangle around the rendered tab number. Use
        // its center as the shared text anchor; renderJianpu consumes this
        // final coordinate directly and must not center it a second time.
        x: noteRect.x + Math.max(1, noteRect.w || noteRect.width || 1) / 2,
        y: rect.y,
        width: 0,
        height: rect.height
      };
    }
    return { ...rect, x: rect.x + rect.width / 2, width: 0 };
  }

  function getMeasureAnchor(measureIndex) {
    const lookup = state.api && state.api.boundsLookup;
    const masterBar = lookup && typeof lookup.findMasterBarByIndex === "function"
      ? lookup.findMasterBarByIndex(measureIndex)
      : null;
    const firstBar = masterBar && masterBar.bars && masterBar.bars[0];
    const firstBeat = firstBar && firstBar.beats && firstBar.beats[0];
    const firstRect = getRealBeatRect(firstBeat);
    if (firstRect) return firstRect;
    const barRect = masterBar && getRealBeatRect(masterBar);
    if (barRect) return barRect;
    return null;
  }

  function getTimedAnchor(measureIndex, tick) {
    const masterBar = state.api.boundsLookup.findMasterBarByIndex(measureIndex);
    const bar = masterBar && masterBar.bars && masterBar.bars[0];
    const beats = bar && bar.beats || [];
    const timeline = beats.map((bounds) => ({
      tick: bounds.beat.displayStart,
      duration: bounds.beat.displayDuration,
      rect: getBeatAnchorRect(bounds)
    })).filter((item) => item.rect && Number.isFinite(item.tick)).sort((a, b) => a.tick - b.tick);
    if (!timeline.length) return null;
    const current = timeline.filter((item) => item.tick <= tick).pop() || timeline[0];
    const next = timeline.find((item) => item.tick > current.tick);
    if (tick === current.tick) return current.rect;
    const barRect = getRealBeatRect(bar);
    const endX = next ? next.rect.x : barRect.x + barRect.width - 8;
    const endTick = next ? next.tick : current.tick + current.duration;
    return { ...current.rect, x: current.rect.x + (endX - current.rect.x) * (tick - current.tick) / (endTick - current.tick) };
  }

  function renderJianpu() {
    if (!state.api || !state.api.boundsLookup) throw new Error("alphaTab 尚未提供 Beat 布局边界");
    const group = createSvgNode("g", { class: "jianpu-extension", "data-layer": "jianpu" });
    const lyricGroup = createSvgNode("g", { class: "lyric-extension", "data-layer": "lyrics" });
    const chordGroup = createSvgNode("g", { class: "chord-diagram-extension", "data-layer": "chord-diagrams" });
    const renderedChords = new Set();
    if (state.document.rhythmSheet) {
      // Keep notes visible in alphaTab's model so rhythmic stems and beams
      // remain intact. Only hide the corresponding SVG fret-number text.
      const fretGlyphs = [...state.svg.querySelectorAll("text")]
        .filter((node) => /^\d+$/.test(node.textContent.trim()))
        .map((node) => {
          const box = node.getBBox();
          const matrix = state.svg.getScreenCTM().inverse().multiply(node.getScreenCTM());
          const center = new DOMPoint(box.x + box.width / 2, box.y + box.height / 2).matrixTransform(matrix);
          return { node, x: center.x, y: center.y };
        });
      state.document.rhythmSheet.bars.forEach((bar, measureIndex) => {
        if (bar.pattern !== "strum" && bar.pattern !== "break" && !bar.tap?.length) return;
        const masterBar = state.api.boundsLookup.findMasterBarByIndex(measureIndex);
        const bounds = masterBar && masterBar.bars && masterBar.bars[0];
        if (!bounds) return;
        (bounds.beats || []).forEach((beat, index) => {
          if (bar.pattern === "break" && index !== 0) return;
          const rect = getBeatAnchorRect(beat);
          if (!rect) return;
          const x = rect.x;
          const heads = (beat.notes || []).filter((note) => note.noteHeadBounds).map((note) => {
            const box = note.noteHeadBounds;
            return { string: note.note.string, x: box.x, y: box.y,
              width: box.w ?? box.width, height: box.h ?? box.height };
          });
          if (heads.length < 2) return;
          heads.sort((a, b) => a.y - b.y);
          const first = heads[0];
          const last = heads[heads.length - 1];
          const firstY = first.y + first.height / 2;
          const lastY = last.y + last.height / 2;
          const stringSpacing = (lastY - firstY) / Math.abs(last.string - first.string);
          const top = firstY;
          const bottom = top + 5 * stringSpacing;
          fretGlyphs.forEach((glyph) => {
            if (heads.some((head) => glyph.x >= head.x - 1 && glyph.x <= head.x + head.width + 1
              && glyph.y >= head.y - 1 && glyph.y <= head.y + head.height + 1)) {
              glyph.node.setAttribute("visibility", "hidden");
            }
          });
          // alphaTab leaves gaps in string lines behind fret numbers. Fill
          // those gaps now that the numbers are hidden.
          heads.forEach(head => group.appendChild(createSvgNode("line", {
            x1: head.x - 2, x2: head.x + head.width + 2,
            y1: head.y + head.height / 2, y2: head.y + head.height / 2,
            stroke: "#A5A5A5", "stroke-width": 1.17
          })));
          // Up on the page means a downstroke (bass to treble), matching
          // the source rhythm chart. The short wings form an open arrow.
          if (bar.pattern === "strum" || bar.pattern === "break") {
            const pointsDown = bar.pattern === "strum" && [3, 5, 9].includes(index);
            const tip = pointsDown ? bottom : top;
            const wing = pointsDown ? tip - 7 : tip + 7;
            group.appendChild(createSvgNode("path", {
              class: "strum-arrow",
              d: `M ${x} ${top} L ${x} ${bottom} M ${x - 3} ${wing} L ${x} ${tip} L ${x + 3} ${wing}`,
              fill: "none", stroke: "#1a2330", "stroke-width": 1.2
            }));
          }
          if (Array.isArray(bar.tap) && bar.tap.includes(index)) {
            group.appendChild(createSvgNode("circle", {
              class: "strum-tap-dot", "data-tap": `${measureIndex}-${index}`,
              "data-measure": measureIndex, "data-tick": index * 384,
              // Tap dots belong in the small space above the first string,
              // never on a string line. `top` is the first-string y position.
              cx: x, cy: top - 9, r: 4,
              fill: "#1a2330", stroke: "#fffdf8", "stroke-width": 1
            }));
          }
        });
      });
    }

    // Image transcriptions own a tick-based chord timeline, independent of
    // lyrics. Render every change, including instrumental bars and mid-bar changes.
    state.document.transcription?.bars.forEach((bar, measureIndex) => {
      (bar.chords || []).forEach(change => {
        const diagram = state.document.chordDiagrams?.[change.name];
        const frets = state.document.transcription.voicings?.[change.voicing] || diagram?.frets;
        const anchor = getTimedAnchor(measureIndex, change.tick);
        if (!anchor || !frets) return;
        renderChordDiagram(chordGroup, change.name, { ...diagram, frets }, anchor.x, anchor.y);
        renderedChords.add(`${measureIndex}-${change.name}`);
      });
    });

    state.document.jianpu.tracks.forEach((track) => {
      normalizedEvents(track).forEach((measureEvent) => {
        const values = measureEvent.events;
        const chordName = measureEvent.chord || state.document.chords?.[measureEvent.measureIndex];
        const chordKey = `${measureEvent.measureIndex}-${chordName}`;
        if (chordName && state.document.chordDiagrams?.[chordName] && !renderedChords.has(chordKey)) {
          const anchor = getMeasureAnchor(measureEvent.measureIndex);
          if (anchor) {
            renderChordDiagram(chordGroup, chordName, state.document.chordDiagrams[chordName], anchor.x, anchor.y);
            renderedChords.add(chordKey);
          }
        }
        const notePositions = [];
        values.forEach((event, index) => {
          const beatLayout = getBeatBounds(measureEvent.measureIndex, event.tick, event.beatIndex);
          const rect = track.alignByTick ? getTimedAnchor(measureEvent.measureIndex, event.tick)
            : beatLayout && getBeatAnchorRect(beatLayout.bounds);
          if (!rect) return;
          const x = rect.x;
          const y = rect.y + rect.height + (track.alignByTick ? 64 : 32);
          const tick = event.tick;
          const note = createSvgNode("text", {
            id: `jianpu-${track.id}-m${measureEvent.measureIndex}-t${tick}`,
            class: "jianpu-note",
            x,
            y,
            "data-track": track.id,
            "data-measure": measureEvent.measureIndex,
            "data-tick": tick
          });
          note.textContent = event.value;
          if (event.lyric) note.dataset.lyric = event.lyric;
          group.appendChild(note);
          notePositions.push({ event, x, y });
          if (track.alignByTick && event.value) {
            const beams = event.durationTicks < 480 ? 2 : event.durationTicks < 960 ? 1 : 0;
            for (let beam = 0; beam < beams; beam++) {
              group.appendChild(createSvgNode("line", {
                x1: x - 5, x2: x + 5, y1: y + 4 + beam * 4, y2: y + 4 + beam * 4,
                class: "jianpu-underline"
              }));
            }
            if (event.dotted) {
              group.appendChild(createSvgNode("circle", { cx: x + 9, cy: y - 6, r: 1.5, class: "jianpu-octave" }));
            }
            if (event.octaveOffset) {
              group.appendChild(createSvgNode("circle", {
                cx: x, cy: event.octaveOffset < 0 ? y + 9 + beams * 4 : y - 21, r: 1.5, class: "jianpu-octave"
              }));
            }
            for (let extension = 960; extension < Math.floor(event.durationTicks / 960) * 960; extension += 960) {
              const extensionRect = getTimedAnchor(measureEvent.measureIndex, event.tick + extension);
              if (!extensionRect) continue;
              const dash = createSvgNode("text", { x: extensionRect.x, y, class: "jianpu-note" });
              dash.textContent = "—";
              // Rests do not use sustaining dashes.
              if (event.value !== "0") group.appendChild(dash);
            }
          }

          if (event.lyric) {
            const lyric = createSvgNode("text", {
              class: "lyric-syllable",
              x,
              y: y + (track.alignByTick ? 36 : 26),
              "data-measure": measureEvent.measureIndex,
              "data-tick": tick
            });
            lyric.textContent = event.lyric;
            lyricGroup.appendChild(lyric);
          }
        });
        notePositions.forEach((position, index) => {
          const next = notePositions[index + 1];
          if (!position.event.tieToNext || !next) return;
          group.appendChild(createSvgNode("path", {
            d: `M ${position.x} ${position.y - 23} Q ${(position.x + next.x) / 2} ${position.y - 34} ${next.x} ${next.y - 23}`,
            fill: "none", class: "jianpu-underline"
          }));
        });
      });
    });

    state.svg.appendChild(group);
    state.svg.appendChild(lyricGroup);
    state.svg.appendChild(chordGroup);
    state.extensionNodes.push(group, lyricGroup, chordGroup);
    updateExtensionVisibility();
  }

  function updateExtensionVisibility() {
    const jianpu = state.svg && state.svg.querySelector(".jianpu-extension");
    const lyrics = state.svg && state.svg.querySelector(".lyric-extension");
    if (jianpu) jianpu.style.display = jianpuToggle.checked ? "" : "none";
    if (lyrics) lyrics.style.display = lyricsToggle.checked ? "" : "none";
  }

  function setActivePosition(position) {
    if (!state.svg) return;
    let measure = Number(position && (position.currentMeasure ?? position.measureIndex ?? 0));
    let tick = Number(position && (position.currentTick ?? position.tick ?? 0));
    if (state.api.tickCache && position && Number.isFinite(position.currentTime)) {
      const lookup = state.api.tickCache.findBeat(new Set(state.api.tracks.map(track => track.index)), tick);
      if (!lookup) return;
      measure = lookup.beat.voice.bar.index;
      tick = lookup.beat.displayStart + Math.max(0, tick - lookup.start);
    }
    state.svg.querySelectorAll(".jianpu-note.active").forEach((node) => node.classList.remove("active"));
    const notes = [...state.svg.querySelectorAll(`.jianpu-note[data-measure="${measure}"]`)].sort((a, b) => Number(a.dataset.tick) - Number(b.dataset.tick));
    const active = notes.filter(node => Number(node.dataset.tick) <= tick).pop();
    if (active) active.classList.add("active");
    state.svg.querySelectorAll(".lyric-syllable.active").forEach((node) => node.classList.remove("active"));
    const lyrics = [...state.svg.querySelectorAll(`.lyric-syllable[data-measure="${measure}"]`)].sort((a, b) => Number(a.dataset.tick) - Number(b.dataset.tick));
    const activeLyric = lyrics.filter(node => Number(node.dataset.tick) <= tick).pop();
    if (activeLyric) activeLyric.classList.add("active");
    state.svg.querySelectorAll(".strum-tap-dot.active").forEach(node => node.classList.remove("active"));
    const tapDots = [...state.svg.querySelectorAll(`.strum-tap-dot[data-measure="${measure}"]`)]
      .sort((a, b) => Number(a.dataset.tick) - Number(b.dataset.tick));
    const activeTap = tapDots.filter(node => Number(node.dataset.tick) <= tick).pop();
    if (activeTap) activeTap.classList.add("active");
    const anchor = getTimedAnchor(measure, tick);
    const masterBar = state.api.boundsLookup.findMasterBarByIndex(measure);
    const bar = masterBar && getRealBeatRect(masterBar.bars[0]);
    if (anchor && bar) {
      let cursor = state.svg.querySelector('.playback-cursor');
      if (!cursor) {
        cursor = createSvgNode('rect', { class: 'playback-cursor', 'pointer-events': 'none' });
        state.svg.appendChild(cursor);
      }
      cursor.setAttribute('x', anchor.x - 5);
      cursor.setAttribute('y', bar.y - 5);
      cursor.setAttribute('width', 10);
      cursor.setAttribute('height', bar.height + 115);
      const matrix = state.svg.getScreenCTM();
      const top = new DOMPoint(anchor.x, bar.y - 30).matrixTransform(matrix).y;
      const bottom = new DOMPoint(anchor.x, bar.y + bar.height + 115).matrixTransform(matrix).y;
      if (followToggle.checked && (state.followedRow !== bar.y || top < 80 || bottom > window.innerHeight - 40)) {
        state.followedRow = bar.y;
        window.scrollBy({ top: top - window.innerHeight * .25, behavior: 'smooth' });
      }
    }
    positionStatus.textContent = `小节 ${measure + 1} · tick ${tick}`;
  }

  function seekToScorePoint(clientX, clientY) {
    if (!state.svg || !state.api || !state.api.boundsLookup) return;
    const matrix = state.svg.getScreenCTM();
    if (!matrix) return;
    const point = new DOMPoint(clientX, clientY).matrixTransform(matrix.inverse());
    let nearest = null;
    const target = document.elementFromPoint(clientX, clientY);
    const beatNode = target && target.closest ? target.closest("[class*='b'], [data-beat-id]") : null;
    const beatId = beatNode && (beatNode.getAttribute("data-beat-id") || ((beatNode.getAttribute("class") || "").match(/\bb(\d+)\b/) || [])[1]);
    if (beatId) {
      state.api.tracks.forEach(track => track.staves.forEach(staff => staff.bars.forEach(bar => {
        (bar.voices || []).forEach(voice => (voice.beats || []).forEach(beat => {
          if (String(beat.id) === String(beatId)) nearest = { beat, distance: 0 };
        }));
      })));
    }
    if (nearest) {
      const tick = Number(nearest.beat.start ?? nearest.beat.tick ?? nearest.beat.displayStart ?? 0);
      if (typeof state.api.tickPosition === "number" || "tickPosition" in state.api) state.api.tickPosition = tick;
      else if (typeof state.api.seekTo === "function") state.api.seekTo(tick);
      if (typeof state.api.play === "function") state.api.play();
      setActivePosition({ currentTime: tick, currentTick: tick });
      return;
    }
    state.api.tracks.forEach(track => {
      track.staves.forEach(staff => {
        staff.bars.forEach(bar => {
          (bar.voices || []).forEach(voice => (voice.beats || []).forEach(beat => {
            const masterBar = state.api.boundsLookup.findMasterBarByIndex(bar.index);
            const bounds = masterBar && masterBar.bars
              ? masterBar.bars.flatMap(item => item.beats || []).find(item => item.beat && item.beat.id === beat.id)
              : null;
            const rect = getRealBeatRect(bounds);
            if (!rect) return;
            const inside = point.x >= rect.x - 16 && point.x <= rect.x + rect.width + 16
              && point.y >= rect.y - 70 && point.y <= rect.y + rect.height + 130;
            const distance = inside ? 0 : Math.hypot(
              point.x - (rect.x + rect.width / 2),
              point.y - (rect.y + rect.height / 2),
            );
            if (!nearest || distance < nearest.distance) nearest = { beat, distance };
          }));
        });
      });
    });
    if (!nearest) return;
    const tick = Number(nearest.beat.start ?? nearest.beat.tick ?? nearest.beat.displayStart ?? 0);
    if (typeof state.api.tickPosition === "number" || "tickPosition" in state.api) state.api.tickPosition = tick;
    else if (typeof state.api.seekTo === "function") state.api.seekTo(tick);
    if (typeof state.api.play === "function") state.api.play();
    setActivePosition({ currentTime: tick, currentTick: tick });
  }

  function bindScoreTap() {
    if (!state.svg || state.svg.dataset.tapBound === "true") return;
    state.svg.dataset.tapBound = "true";
    state.svg.style.cursor = "pointer";
    state.svg.addEventListener("pointerup", event => seekToScorePoint(event.clientX, event.clientY));
  }

  function renderBeatHitLayer() {
    if (!state.svg || !state.api?.boundsLookup) return;
    state.svg.querySelector(".score-hit-layer")?.remove();
    const layer = createSvgNode("g", { class: "score-hit-layer" });
    state.api.tracks.forEach(track => track.staves.forEach(staff => staff.bars.forEach(bar => {
      const masterBar = state.api.boundsLookup.findMasterBarByIndex(bar.index);
      const beatBounds = masterBar?.bars?.flatMap(item => item.beats || []) || [];
      const rects = beatBounds.map(getRealBeatRect).filter(Boolean);
      if (!rects.length) return;
      const left = Math.min(...rects.map(rect => rect.x)) - 20;
      const right = Math.max(...rects.map(rect => rect.x + rect.width)) + 20;
      const top = Math.min(...rects.map(rect => rect.y)) - 90;
      const bottom = Math.max(...rects.map(rect => rect.y + rect.height)) + 150;
      layer.appendChild(createSvgNode("rect", {
        x: left, y: top, width: Math.max(40, right - left), height: Math.max(80, bottom - top),
        fill: "transparent", "pointer-events": "all",
        "data-score-measure": bar.index,
      }));
    })));
    state.svg.appendChild(layer);
    layer.addEventListener("pointerup", event => {
      const target = event.target.closest("[data-score-measure]");
      if (!target) return;
      event.preventDefault();
      event.stopPropagation();
      playFromScorePosition(Number(target.dataset.scoreMeasure));
    });
  }

  function playFromScorePosition(measure) {
    const api = state.api;
    const masterBar = api?.score?.masterBars[measure];
    if (!masterBar) return;
    if (!api.isReadyForPlayback || !api.tickCache) {
      state.pendingMeasure = measure;
      renderStatus.textContent = `等待音源加载，将从第 ${measure + 1} 小节播放`;
      return;
    }
    // Layout bounds contain geometry, not MIDI time. Resolve the model master
    // bar through the generated MIDI timeline (also accounts for repeats).
    const absoluteTick = api.tickCache.getMasterBarStart(masterBar);
    api.tickPosition = absoluteTick;
    api.play();
    setActivePosition({ currentMeasure: measure, currentTick: 0 });
    renderStatus.textContent = `从第 ${measure + 1} 小节播放`;
  }

  function handleNativeScoreTap(clientX, clientY) {
    if (!state.svg) return false;
    const hitRects = [...state.svg.querySelectorAll(".score-hit-layer [data-score-measure]")];
    let chosen = null;
    let distance = Number.POSITIVE_INFINITY;
    hitRects.forEach(rect => {
      const box = rect.getBoundingClientRect();
      const inside = clientX >= box.left && clientX <= box.right && clientY >= box.top && clientY <= box.bottom;
      const dx = inside ? 0 : Math.max(box.left - clientX, 0, clientX - box.right);
      const dy = inside ? 0 : Math.max(box.top - clientY, 0, clientY - box.bottom);
      const value = dx * dx + dy * dy;
      if (value < distance) {
        distance = value;
        chosen = rect;
      }
    });
    if (!chosen || distance > 9000) return false;
    playFromScorePosition(Number(chosen.dataset.scoreMeasure), Number(chosen.dataset.scoreTick));
    return true;
  }

  function bindPlayerEvents() {
    const api = state.api;
    if (!api) return;
    const on = (eventName, handler) => {
      if (api[eventName] && typeof api[eventName].on === "function") api[eventName].on(handler);
    };
    on("playerPositionChanged", setActivePosition);
    on("playerStateChanged", (value) => {
      positionStatus.textContent = value.state === 1 ? "播放中" : value.stopped ? "已停止" : "已暂停";
      if (value.stopped) {
        state.followedRow = null;
        state.svg?.querySelector('.playback-cursor')?.remove();
      }
    });
    on("playerReady", () => {
      $("play-button").disabled = false;
      renderStatus.textContent = "音源已加载 · 可以播放";
      if (Number.isInteger(state.pendingMeasure)) {
        const measure = state.pendingMeasure;
        state.pendingMeasure = null;
        playFromScorePosition(measure);
        return;
      }
      if (state.pendingMobilePlay) {
        state.pendingMobilePlay = false;
        api.play();
      }
    });
    on("soundFontLoad", () => { renderStatus.textContent = "正在加载本地音源…"; });
  }

  function resolveImageCrossPitches(score, document) {
    if (!document.transcription?.bars) return;
    let currentChord = null;
    score.tracks[0].staves[0].bars.forEach((bar, index) => {
      const source = document.transcription.bars[index];
      bar.voices[0].beats.forEach(beat => {
        for (const change of source.chords || []) {
          if (change.tick <= beat.displayStart) currentChord = change;
        }
        const frets = document.transcription.voicings[currentChord?.voicing]
          || document.chordDiagrams[currentChord?.name]?.frets;
        beat.notes.forEach(note => {
          // Preserve explicitly numbered notes; only chart crosses get pitches.
          if (!note.isDead && !note.tieOrigin?._imageCross) return;
          note._imageCross = true;
          if (note.isTieDestination && note.tieOrigin?._imageCross) {
            note.fret = note.tieOrigin.fret;
            note.isDead = false;
            return;
          }
          const fret = frets?.[note.string - 1];
          if (Number.isInteger(fret)) {
            note.fret = fret;
            note.isDead = false;
          }
        });
      });
    });
  }

  function restoreImageCrossGlyphs() {
    if (!state.document.transcription?.bars) return;
    const inverse = state.svg.getScreenCTM().inverse();
    const glyphs = [...state.svg.querySelectorAll('text')]
      .filter(node => /^\d+$/.test(node.textContent.trim()))
      .map(node => {
        const box = node.getBBox();
        const center = new DOMPoint(box.x + box.width / 2, box.y + box.height / 2)
          .matrixTransform(inverse.multiply(node.getScreenCTM()));
        return { node, center };
      });
    state.document.transcription.bars.forEach((source, index) => {
      const bounds = state.api.boundsLookup.findMasterBarByIndex(index)?.bars[0];
      bounds?.beats.forEach(beat => (beat.notes || []).forEach(note => {
        if (!note.note._imageCross || !note.noteHeadBounds) return;
        const box = note.noteHeadBounds;
        const width = box.w ?? box.width;
        const height = box.h ?? box.height;
        glyphs.forEach(({ node, center }) => {
          if (center.x >= box.x - 1 && center.x <= box.x + width + 1
            && center.y >= box.y - 1 && center.y <= box.y + height + 1) node.textContent = 'x';
        });
      }));
    });
  }

  function renderScore(alphaTex) {
    if (!window.alphaTab) throw new Error("alphaTab 资源未加载");
    const settings = {
      // Extensions and SVG export currently share one SVG document. Lazy
      // partials can be detached/replaced while scrolling, losing our nodes.
      core: { engine: "svg", useWorkers: false, includeNoteBounds: true, enableLazyLoading: false },
      display: {
        staveProfile: "Tab",
        // Keep all systems in one partial, including imported long scores.
        barCountPerPartial: Number.MAX_SAFE_INTEGER,
        scale: 1,
        firstSystemPaddingTop: 96,
        systemPaddingTop: 96
      },
      player: {
        enablePlayer: true, enableCursor: false,
        soundFont: window.AndroidScore ? "sonivox.sf2" : "../docs/vendor/alphatab/sonivox.sf2"
      }
    };
    if (state.document.rhythmSheet) {
      settings.display.barsPerRow = 4;
      settings.display.systemPaddingTop = 130;
      settings.display.systemPaddingBottom = 120;
      settings.display.lastSystemPaddingBottom = 120;
    }
    if (state.document.transcription?.bars) {
      // Custom chord diagrams extend above each system and lyrics below it.
      // AlphaTab cannot measure these SVG additions, so reserve both regions.
      settings.display.barsPerRow = 4;
      settings.display.firstSystemPaddingTop = 130;
      settings.display.systemPaddingTop = 130;
      settings.display.systemPaddingBottom = 120;
      settings.display.lastSystemPaddingBottom = 120;
    }
    if (window.AndroidScore) {
      settings.display.barsPerRow = window.innerWidth < 600 ? 1 : 2;
      settings.display.scale = 0.85;
    }
    state.api = new alphaTab.AlphaTabApi(scoreHost, settings);
    if (window.AndroidScore) {
      // Compose buttons call these methods through evaluateJavascript. Queue
      // play until alphaSynth has finished loading the local SoundFont.
      window.scoreApi = {
        play: () => {
          if (state.api.isReadyForPlayback) state.api.play();
          else state.pendingMobilePlay = true;
        },
        pause: () => state.api.pause(),
        stop: () => { state.pendingMobilePlay = false; state.api.stop(); }
      };
    }
    state.api.scoreLoaded.on(score => {
      resolveImageCrossPitches(score, state.document);
      if (!state.document.rhythmSheet) return;
      const shapes = { 'C#m': [0, 1, 2, 2, 0, 0], 'G#m': [0, 0, 0, 2, 2, 0],
        B: [3, 0, 0, 0, 2, 3], E: [0, 1, 0, 2, 3, 0] };
      score.tracks[0].staves[0].bars.forEach(bar => {
        const source = state.document.rhythmSheet.bars[bar.index];
        if (source.pattern) return;
        bar.voices[0].beats.forEach(beat => beat.notes.forEach(note => {
          note.isDead = false;
          note.fret = shapes[source.chord][note.string - 1];
        }));
      });
    });
    $("play-button").disabled = true;
    state.followedRow = null;
    const api = state.api;
    let finalized = false;
    api.renderStarted && api.renderStarted.on(() => {
      finalized = false;
      removeExtension();
      state.svg = null;
      renderStatus.textContent = "正在生成 SVG";
    });
    bindPlayerEvents();
    window.setTimeout(() => setTempo(state.playbackTempo), 0);
    function mergeRenderedSvgs() {
      const svgs = [...scoreHost.querySelectorAll("svg.at-surface-svg")];
      if (!svgs.length) return null;
      const chunks = svgs.map(svg => ({ svg,
        x: parseFloat(svg.parentElement.style.left) || 0,
        y: parseFloat(svg.parentElement.style.top) || 0,
        width: parseFloat(svg.getAttribute("width")), height: parseFloat(svg.getAttribute("height"))
      }));
      const width = Math.max(...chunks.map(c => c.x + c.width));
      const height = Math.max(...chunks.map(c => c.y + c.height));
      const layers = chunks.map(c => {
        const layer = createSvgNode("g", { transform: `translate(${c.x} ${c.y})`, "data-native-chunk": "true" });
        [...c.svg.childNodes].forEach(node => layer.appendChild(node.cloneNode(true)));
        return layer;
      });
      const first = svgs[0];
      first.replaceChildren(...layers);
      // Crosses in this rhythm chart indicate string positions, not dead-note
      // audio. Restore the chart glyph after rendering the pitched model.
      if (state.document.rhythmSheet) {
        state.document.rhythmSheet.bars.forEach((source, index) => {
          if (source.pattern) return;
          const bounds = api.boundsLookup.findMasterBarByIndex(index)?.bars[0];
          bounds?.beats.forEach(beat => {
            first.querySelectorAll(`g.b${beat.beat.id} text`).forEach(node => {
              if (/^\d+$/.test(node.textContent.trim())) node.textContent = 'x';
            });
          });
        });
      }
      first.setAttribute("width", width);
      first.setAttribute("height", height);
      first.setAttribute("viewBox", `0 0 ${width} ${height}`);
      Object.assign(first.parentElement.style, { top: "0px", left: "0px", width: `${width}px`, height: `${height}px` });
      svgs.slice(1).forEach(svg => { svg.style.display = "none"; });
      return first;
    }
    const finalizeRender = (attempt = 0) => {
      if (finalized || state.api !== api) return;
      state.svg = scoreHost.querySelector("svg");
      if (!state.svg) {
        if (attempt < 12) {
          window.setTimeout(() => finalizeRender(attempt + 1), 0);
          return;
        }
        showError("alphaTab 已完成布局，但没有找到 SVG 输出节点。请检查渲染引擎配置。", "渲染失败");
        return;
      }
      try {
        state.svg = mergeRenderedSvgs();
        bindScoreTap();
        renderBeatHitLayer();
        restoreImageCrossGlyphs();
        renderJianpu();
        scheduleCreditCleanup();
        finalized = true;
        emptyState.classList.remove("visible");
        renderStatus.textContent = state.api.isReadyForPlayback ? "音源已加载 · 可以播放" : "SVG 已生成 · 等待音源加载";
        $("play-button").disabled = !state.api.isReadyForPlayback;
        setStatus("渲染就绪", "ready");
      } catch (error) {
        showError(error.message || String(error), "布局失败");
        emptyState.textContent = "alphaTab 未返回可用的 Beat 布局，已停止绘制扩展层。";
        console.error(error);
      }
    };
    // alphaTab queues partial DOM insertion after postRenderFinished. Merge
    // after that queue drains; otherwise late inserts replace our merged SVG.
    state.api.postRenderFinished.on(() => window.setTimeout(() => finalizeRender(), 150));
    state.api.error.on((error) => {
      showError(error && error.message ? error.message : "alphaTab 渲染错误", "渲染失败");
    });
    // alphaTab 1.8.x exposes AlphaTex loading as the tex(text) method.
    // Assigning to api.tex only creates an unrelated JavaScript property and
    // leaves the score empty.
    if (typeof state.api.tex !== "function") throw new Error("当前 alphaTab 不支持 AlphaTex 加载 API");
    state.api.tex(alphaTex);
  }

  function alphaTexWithCapo(alphaTex, capo) {
    const value = Math.max(0, Math.min( capo, 12));
    if (/\\capo\s+\d+/i.test(alphaTex)) return alphaTex.replace(/\\capo\s+\d+/i, `\\capo ${value}`);
    return alphaTex.replace(/(\\tuning[^\n]*\n)/i, `$1\\capo ${value}\n`);
  }

  function setMobilePlaybackSettings(capo, tempo) {
    state.playbackCapo = Number(capo) || 0;
    state.playbackTempo = Math.max(40, Math.min(240, Number(tempo) || 72));
    if (!state.baseAlphaTex || !window.AndroidScore) return;
    if (state.api && typeof state.api.stop === "function") state.api.stop();
    if (state.api && typeof state.api.destroy === "function") state.api.destroy();
    scoreHost.replaceChildren();
    state.svg = null;
    state.extensionNodes = [];
    renderScore(alphaTexWithCapo(state.baseAlphaTex, state.playbackCapo));
  }
  window.setMobilePlaybackSettings = setMobilePlaybackSettings;

  function playerCall(name, ...args) {
    if (state.api && typeof state.api[name] === "function") state.api[name](...args);
  }

  function setTempo(value) {
    tempoOutput.value = value;
    tempoOutput.textContent = value;
    if (!state.api) return;
    if (typeof state.api.changeTempo === "function") state.api.changeTempo(Number(value));
    else if ("playbackSpeed" in state.api) state.api.playbackSpeed = Number(value) / Number(state.document.defaultTempo || 72);
  }

  async function boot() {
    if (window.AndroidScore) {
      const song = new URLSearchParams(location.search).get("song") || "yu_jian";
      const text = window.AndroidScore.load(song);
      if (!text) return showError("无法读取内置曲谱：" + song);
      const data = song === "zui_lang_man_de_shi" ? {
        version: 1, source: { id: song, title: "最浪漫的事" },
        alphaTabSource: { type: "alphatex", text }, jianpu: { tracks: [] }
      } : JSON.parse(text);
      return loadScoreDocument(data);
    }
    return loadScoreDocument(builtInScore.value);
  }

  async function loadScoreDocument(pathOrDocument) {
    try {
      setStatus("加载 Score JSON", "pending");
      renderStatus.textContent = "正在读取曲谱资源…";
      emptyState.classList.remove("visible");
      if (state.api && typeof state.api.destroy === "function") state.api.destroy();
      const loaded = typeof pathOrDocument === "string" ? await fetchJson(pathOrDocument) : pathOrDocument;
      state.document = normalizeScoreDocument(loaded);
      validateScoreDocument(state.document);
      document.title = `XStar Guitar · ${state.document.source.title}`;
      scoreTitle.textContent = state.document.source.title;
      $("score-source-summary").textContent = state.document.source.subtitle || state.document.source.title;
      const transcriptionNotice = $("transcription-notice");
      transcriptionNotice.textContent = state.document.source.transcriptionNotes || "";
      transcriptionNotice.hidden = !transcriptionNotice.textContent;
      tempoInput.value = state.document.defaultTempo || 72;
      setTempo(tempoInput.value);
      const alphaTex = await fetchAlphaTex(state.document.alphaTabSource);
      state.baseAlphaTex = alphaTex;
      state.playbackCapo = Number(state.document.capo || state.document.rhythmSheet?.capo || 0);
      state.playbackTempo = Number(state.document.defaultTempo || 72);
      scoreHost.replaceChildren();
      state.svg = null;
      state.extensionNodes = [];
      renderScore(alphaTexWithCapo(alphaTex, state.playbackCapo));
    } catch (error) {
      showError(error.message || String(error));
      console.error(error);
    }
  }

  importScoreButton.addEventListener("click", () => {
    scoreFile.click();
  });
  $("play-button").addEventListener("click", () => playerCall("play"));
  $("pause-button").addEventListener("click", () => playerCall("pause"));
  $("stop-button").addEventListener("click", () => playerCall("stop"));
  tempoInput.addEventListener("input", () => setTempo(tempoInput.value));
  $("zoom-in").addEventListener("click", () => {
    state.scale = Math.min(1.6, state.scale + 0.1);
    scoreHost.style.transform = `scale(${state.scale})`;
    scoreHost.style.marginBottom = `${(state.scale - 1) * 600}px`;
  });
  $("zoom-out").addEventListener("click", () => {
    state.scale = Math.max(.7, state.scale - 0.1);
    scoreHost.style.transform = `scale(${state.scale})`;
    scoreHost.style.marginBottom = `${(state.scale - 1) * 600}px`;
  });
  $("theme-toggle").addEventListener("click", () => document.body.classList.toggle("light"));
  jianpuToggle.addEventListener("change", updateExtensionVisibility);
  lyricsToggle.addEventListener("change", updateExtensionVisibility);
  followToggle.addEventListener("change", () => {
    if (!followToggle.checked) state.followedRow = null;
  });
  scoreFile.addEventListener("change", async (event) => {
    const file = event.target.files && event.target.files[0];
    if (!file) return;
    try {
      setStatus("读取文件", "pending");
      renderStatus.textContent = `正在导入：${file.name}`;
      const fileText = (await file.text()).replace(/^\uFEFF/, "");
      const documentData = normalizeScoreDocument(JSON.parse(fileText));
      await loadScoreDocument(documentData);
    } catch (error) {
      showError(error.message || String(error), "导入失败");
      console.error(error);
    } finally {
      scoreFile.value = "";
    }
  });
  builtInScore.addEventListener("change", () => loadScoreDocument(builtInScore.value));
  $("export-svg").addEventListener("click", () => {
    if (!state.svg) return;
    const source = new XMLSerializer().serializeToString(state.svg);
    const blob = new Blob([source], { type: "image/svg+xml;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${state.document.source.id}.svg`;
    link.click();
    URL.revokeObjectURL(url);
  });
  window.xstarScoreRuntime = { state, setActivePosition, handleNativeScoreTap };
  window.handleNativeScoreTap = handleNativeScoreTap;
  boot();
})();
