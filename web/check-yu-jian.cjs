const fs = require('node:fs');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const alphaTab = require('../docs/vendor/alphatab/alphaTab.min.js');
const doc = JSON.parse(fs.readFileSync(__dirname + '/scores/yu_jian.json', 'utf8'));

// Exercise the actual browser import validator, including lyric-only events.
const app = fs.readFileSync(__dirname + '/app.js', 'utf8');
const validatorSource = app.slice(app.indexOf('  function validateScoreDocument('), app.indexOf('  function normalizedEvents('));
const validate = vm.runInNewContext(validatorSource + '\nvalidateScoreDocument;');
assert.doesNotThrow(() => validate(doc));
for (const name of ['score-example', 'score-complete-example', 'zui_lang_man_de_shi']) {
  assert.doesNotThrow(() => validate(JSON.parse(fs.readFileSync(__dirname + '/scores/' + name + '.json', 'utf8'))));
}
const invalid = JSON.parse(JSON.stringify(doc));
delete invalid.jianpu.tracks[0].events[0].events[0].lyric;
assert.throws(() => validate(invalid), /简谱 note/);
delete invalid.jianpu.tracks[0].events[0].events[0].value;
assert.throws(() => validate(invalid), /简谱 note/);

// Exercise the actual chord-rendering traversal without needing a browser DOM.
const chordStart = app.indexOf('    state.document.transcription?.bars.forEach(');
const chordEnd = app.indexOf('    state.document.jianpu.tracks.forEach(', chordStart);
const drawnChords = [];
vm.runInNewContext(app.slice(chordStart, chordEnd), {
  state: { document: doc }, chordGroup: {}, renderedChords: new Set(),
  getTimedAnchor: (measureIndex, tick) => ({ x: measureIndex * 3840 + tick, y: 200 }),
  renderChordDiagram: (group, name, diagram, x) => drawnChords.push({ name, diagram, x })
});
assert.equal(drawnChords.length, doc.transcription.bars.reduce((sum, bar) => sum + bar.chords.length, 0));
assert.ok(drawnChords.some(chord => chord.name === 'D/F#' && chord.x === 3840 + 1920), 'instrumental mid-bar chord is rendered');
assert.deepEqual(drawnChords.find(chord => chord.x === 28 * 3840 + 1920).diagram.frets, doc.transcription.voicings['G-full'], 'mid-bar alternate voicing is rendered');

function parse(text) {
  const importer = new alphaTab.importer.AlphaTexImporter();
  importer.initFromString(text, new alphaTab.Settings());
  try { return importer.readScore(); } catch (error) {
    console.error(error.cause?.parserDiagnostics?.items || error.message);
    throw error;
  }
}

const score = parse(doc.alphaTabSource.text);
const firstNotes = score.tracks[0].staves[0].bars[0].voices[0].beats.flatMap(beat => beat.notes);
// AlphaTex string 2 (second line from top) is model string 5.
assert.deepEqual(firstNotes.map(note => [note.string, note.fret]), [[5, 3], [5, 0]], 'measure 1: 3 and 0 on the second line from top');
const raw = parse('\\track "Guitar" { instrument 25 }\n\\staff { tabs }\n\\tuning (E4 B3 G3 D3 A2 E2)\n\\capo 1\n\\ts (4 4)\n' + doc.transcription.bars.map(bar => bar.tab).join(' |\n'));
assert.equal(score.masterBars.length, 37);
assert.equal(raw.masterBars.length, 37);
assert.equal(score.tracks[0].staves[0].capo, 1);
assert.deepEqual(doc.transcription.bars.filter(bar => bar.page).map(bar => [bar.number, bar.page]), [[1, 1], [21, 2]]);
const lyricMeasures = doc.jianpu.tracks[0].events;
const bars = score.tracks[0].staves[0].bars;
assert.deepEqual(bars[1].voices[0].beats.filter(beat => beat.notes.some(note => !note.isDead)).map(beat => beat.notes.map(note => [note.string, note.fret])), [[[5, 3]], [[4, 2]], [[5, 3]], [[4, 0]]], 'measure 2: numbered notes match image lines');
assert.deepEqual(bars[4].voices[0].beats.slice(3, 6).map(beat => beat.notes.map(note => [note.string, note.fret])), [[[5, 1]], [[5, 3]], [[5, 0], [4, 0]]], 'measure 5: hammer-on and open-string pair match image lines');
for (const [index, bar] of bars.entries()) {
  const source = doc.transcription.bars[index];
  assert.equal(source.number, index + 1);
  const beats = bar.voices[0].beats;
  assert.equal(beats.reduce((sum, beat) => sum + beat.displayDuration, 0), 3840, `measure ${index + 1}: 4/4`);
  const signature = b => b.voices[0].beats.map(beat => ({
    tick: beat.displayStart, duration: beat.displayDuration, rest: beat.isRest,
    notes: beat.notes.map(note => [note.string, note.fret, note.isDead, note.isTieDestination, note.isHammerPullOrigin])
  }));
  assert.deepEqual(signature(bar), signature(raw.tracks[0].staves[0].bars[index]), `measure ${index + 1}: embedded AlphaTex matches transcription`);
  for (const chord of source.chords) {
    assert.ok(doc.chordDiagrams[chord.name], chord.name);
    if (chord.voicing) assert.ok(doc.transcription.voicings[chord.voicing], chord.voicing);
    assert.ok(beats.some(beat => beat.displayStart === chord.tick), `measure ${index + 1}: chord on beat`);
  }
  const lyrics = lyricMeasures.find(measure => measure.measureIndex === index)?.events || [];
  assert.deepEqual(lyrics.map(event => ({ tick: event.tick, text: event.lyric })), source.lyrics || [], `measure ${index + 1}: lyrics match`);
  for (const event of lyrics) {
    assert.ok(event.tick >= 0 && event.tick + event.durationTicks <= 3840);
    assert.ok(beats.some(beat => beat.displayStart === event.tick), `measure ${index + 1}: lyric anchor`);
  }
}
assert.ok(bars[4].voices[0].beats.some(beat => beat.notes.some(note => note.isHammerPullOrigin)), 'measure 5: hammer-on');
assert.ok(bars[9].voices[0].beats[0].notes.every(note => note.isTieDestination), 'measure 10: cross-measure ties');
const resolverSource = app.slice(app.indexOf('  function resolveImageCrossPitches('), app.indexOf('  function restoreImageCrossGlyphs('));
const resolvePitches = vm.runInNewContext(resolverSource + '\nresolveImageCrossPitches;');
resolvePitches(score, doc);
assert.equal(bars[1].voices[0].beats[0].notes[0].fret, 3, 'G bass: fret 3');
assert.equal(bars[1].voices[0].beats[0].notes[0].isDead, false);
assert.equal(bars[1].voices[0].beats[0].notes[0].realValue, 44, 'G bass E2 + fret 3 + capo 1');
assert.equal(bars[1].voices[0].beats[4].notes[0].fret, 2, 'D/F# bass: fret 2 at beat 3');
assert.equal(bars[1].voices[0].beats[4].notes[0].realValue, 43);
assert.equal(bars[1].voices[0].beats[2].notes[0]._imageCross, undefined, 'explicit fret remains a number');
assert.equal(bars[1].voices[0].beats[2].notes[0].fret, 3);
assert.ok(bars[9].voices[0].beats[0].notes.every(note => !note.isDead && note.fret === note.tieOrigin.fret), 'ties inherit pitched chord');
assert.equal(bars[29].voices[0].beats[0].notes.find(note => note.string === 2).fret, 3, 'C bass A string fret 3');
console.log('PASS: 37 measures, page split 20/17, capo 1, all measures 4/4; AlphaTex, lyrics, chord positions, hammer-on and cross-measure ties verified.');
