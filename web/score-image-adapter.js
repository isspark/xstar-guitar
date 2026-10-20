(function (root) {
  "use strict";
  const durations = { s: 240, e: 480, q: 960, h: 1920, w: 3840, de: 720, dq: 1440 };
  const shapes = {
    "C#m": { notes: [0, 1, 2, 2, 0, null], bass: 5, diagram: ["x", 1, 3, 3, 2, 1], baseFret: 4 },
    "G#m": { notes: [0, 0, 0, 2, 2, 0], bass: 6, diagram: [1, 3, 3, 1, 1, 1], baseFret: 4 },
    B: { notes: [3, 0, 0, 0, 2, 3], bass: 6, diagram: ["x", 1, 3, 3, 3, 1], baseFret: 2 },
    E: { notes: [0, 1, 0, 2, 3, null], bass: 5, diagram: [0, 2, 2, 1, 0, 0], baseFret: 1 }
  };

  function expand(document) {
    if (!document.rhythmSheet) return document;
    const sheet = document.rhythmSheet;
    const lines = [
      '\\title "画"', '\\subtitle "图片转录 · 变调夹 4 品 · 1=♭A · 4/4"',
      `\\tempo ${document.defaultTempo}`, '\\track "Guitar" { instrument 25 }',
      '\\staff { tabs }', '\\tuning (E4 B3 G3 D3 A2 E2)', `\\capo ${sheet.capo}`, '\\ts (4 4)'
    ];
    const measures = [];
    const chordDiagrams = {};
    const normalizedBars = sheet.bars.map((bar, measureIndex) => ({
      ...bar,
      tap: Array.isArray(bar.tap) ? bar.tap
        : measureIndex < 9 ? [1, 3, 5, 7]
        : bar.pattern === "strum" ? [1, 4, 7] : []
    }));
    normalizedBars.forEach((bar, measureIndex) => {
      const shape = shapes[bar.chord];
      if (!shape) throw new Error(`未知和弦：${bar.chord}`);
      chordDiagrams[bar.chord] = { frets: shape.diagram, baseFret: shape.baseFret };
      if (bar.section) lines.push(`\\section "${bar.section}"`);
      let rhythm;
      if (bar.pattern === "break") {
        const chord = shape.notes.flatMap((fret, index) => fret === null ? [] : [`${fret}.${index + 1}`]).join(" ");
        rhythm = `(${chord}).4{beam down} r.4 r.4 r.4`;
      } else if (bar.pattern === "ending") {
        const chord = shape.notes.flatMap((fret, index) => fret === null ? [] : [`${fret}.${index + 1}`]).join(" ");
        rhythm = `(${chord}).1{ad}`;
      } else {
        const slots = [960, 480, 240, 240, 240, 240, 480, 480, 240, 240];
        const strings = [shape.bass, 1, 3, 2, 3, 2, shape.bass, 1, 3, 2];
        rhythm = slots.map((ticks, index) => {
          const duration = 3840 / ticks;
          if (bar.pattern === "strum") {
            const chord = shape.notes.flatMap((fret, stringIndex) => fret === null ? [] : [`${fret}.${stringIndex + 1}`]).join(" ");
            return `(${chord}).${duration}{beam down}`;
          }
          // Cross heads in the supplied images specify string/rhythm, not frets.
          return index === 1 || index === 7
            ? `(x.1 x.2 x.3).${duration}` : `x.${strings[index]}.${duration}`;
        }).join(" ");
      }
      // The first page of the source uses four isolated tap dots between
      // chord changes; later strummed bars use the three visible tap accents.
      // Keep the positions in the expanded Score JSON so playback and SVG
      // rendering consume the same timing data.
      const tapIndexes = bar.tap;
      // Tap is a separate annotation from the chord/fret data. It is kept in
      // the Score JSON and drawn by the SVG layer, so it cannot corrupt the
      // existing beat-property braces in generated AlphaTex.
      lines.push(rhythm + (measureIndex === sheet.bars.length - 1 ? "" : " |"));
      let tick = 0;
      const syllables = (bar.lyrics || "").split(/\s+/);
      const events = bar.notes.split(/\s+/).map((token, index) => {
        const [pitch, duration] = token.split(":");
        const durationTicks = durations[duration];
        if (!durationTicks) throw new Error(`第 ${measureIndex + 1} 小节时值错误：${token}`);
        const event = {
          tick, value: pitch.replace(/[,']/g, ""), durationTicks,
          octaveOffset: pitch.includes(",") ? -1 : pitch.includes("'") ? 1 : 0,
          dotted: duration.startsWith("d"), tieToNext: (bar.ties || []).includes(index)
        };
        const lyric = syllables[index];
        if (lyric && lyric !== "_" && lyric !== "~") event.lyric = lyric;
        tick += durationTicks;
        return event;
      });
      if (tick !== 3840) throw new Error(`第 ${measureIndex + 1} 小节不是四拍：${tick}`);
      if (bar.lyrics && syllables.length !== events.length) throw new Error(`第 ${measureIndex + 1} 小节歌词数量不匹配`);
      measures.push({ measureIndex, chord: bar.chord, tap: tapIndexes, events });
    });
    return {
      ...document,
      rhythmSheet: { ...sheet, bars: normalizedBars },
      alphaTabSource: { type: "alphatex", text: lines.join("\n"), chordDiagramsMode: "custom-only" },
      chordDiagrams,
      jianpu: { tracks: [{ id: "vocal", name: "图片简谱", alignByTick: true, events: measures }] }
    };
  }
  if (typeof module !== "undefined" && module.exports) module.exports = { expand };
  else root.scoreImageAdapter = { expand };
})(typeof window === "undefined" ? globalThis : window);
