const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const alphaTab = require('../docs/vendor/alphatab/alphaTab.min.js');
const doc = JSON.parse(fs.readFileSync(__dirname + '/scores/wei_ni_liu_yi_zhan_deng.json', 'utf8'));
const app = fs.readFileSync(__dirname + '/app.js', 'utf8');
const validate = vm.runInNewContext(app.slice(app.indexOf('  function validateScoreDocument('), app.indexOf('  function normalizedEvents(')) + '\nvalidateScoreDocument;');
validate(doc);
function parse(text) {
  const importer = new alphaTab.importer.AlphaTexImporter();
  importer.initFromString(text, new alphaTab.Settings());
  return importer.readScore();
}
const score = parse(doc.alphaTabSource.text);
const raw = parse('\\track "Vocal" {instrument 24}\n\\staff {tabs}\n\\tuning (E4 B3 G3 D3 A2 E2)\n\\ts (4 4)\n' + doc.transcription.bars.map(b => b.tab).join(' |\n'));
assert.equal(score.masterBars.length, 28);
assert.equal(score.tracks.length, 2);
assert.equal(score.tempo, 72);
const melody = score.tracks[0].staves[0].bars;
const guitar = score.tracks[1].staves[0].bars;
const sig = bar => bar.voices[0].beats.map(b => [b.displayStart, b.displayDuration, b.isRest, b.notes.map(n => [n.string, n.fret, n.realValue])]);
for (const track of score.tracks) for (const bar of track.staves[0].bars) {
  assert.equal(bar.voices[0].beats.reduce((s, b) => s + b.displayDuration, 0), 3840, `${track.name}, bar ${bar.index + 1}: 4/4`);
}
for (const [i, source] of doc.transcription.bars.entries()) {
  assert.deepEqual(sig(melody[i]), sig(raw.tracks[0].staves[0].bars[i]), `bar ${i + 1}: melody/tab`);
  for (const change of source.chords) {
    assert.ok(guitar[i].voices[0].beats.some(b => b.displayStart === change.tick), 'chord change aligns to accompaniment');
  }
  for (const beat of guitar[i].voices[0].beats) {
    const change = source.chords.filter(c => c.tick <= beat.displayStart).at(-1);
    for (const note of beat.notes) assert.equal(note.fret, doc.chordDiagrams[change.name].frets[note.string - 1], `bar ${i + 1}: accompaniment obeys ${change.name} voicing`);
  }
}
const degrees = [0, 2, 4, 5, 7, 9, 11];
const pitches = [];
for (const measure of doc.jianpu.tracks[0].events) {
  const beats = melody[measure.measureIndex].voices[0].beats;
  assert.equal(measure.events.map(e => e.lyric).join(''), doc.transcription.bars[measure.measureIndex].lyricText);
  assert.equal(measure.events.length, beats.length);
  for (const [i, event] of measure.events.entries()) {
    assert.equal(event.tick, beats[i].displayStart);
    assert.equal(event.durationTicks, beats[i].displayDuration);
    const midi = 60 + degrees[Number(event.value) - 1] + 12 * (event.octaveOffset || 0);
    assert.equal(beats[i].notes[0].realValue, midi, `bar ${measure.measureIndex + 1}: numbered notation matches playback`);
    pitches.push(midi);
  }
}
assert.equal(Math.min(...pitches), 59);
assert.equal(Math.max(...pitches), 76);
assert.equal(pitches.length, 120);
assert.equal(melody[23].voices[0].beats.at(-1).notes[0].realValue, 60, 'vocal resolves to tonic C4');
assert.deepEqual(guitar[27].voices[0].beats[0].notes.map(n => n.realValue).sort((a,b) => a-b), [48, 52, 55, 60, 64], 'final C chord');
console.log('PASS: 28 bars, 2 tracks, full 4/4; import validator; 120 lyric/notation/playback anchors; B3–E5 range; every accompaniment note matches chord fingering; C-major cadence.');
