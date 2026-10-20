const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const alphaTab = require('../docs/vendor/alphatab/alphaTab.min.js');
const doc = JSON.parse(fs.readFileSync(__dirname + '/scores/hui_hua_dong.json', 'utf8'));
assert.deepEqual(doc, require('./build-hui-hua-dong.cjs').makeDocument(), 'JSON matches single transcription source');
const app = fs.readFileSync(__dirname + '/app.js', 'utf8');
const validator = vm.runInNewContext(app.slice(app.indexOf('  function validateScoreDocument('), app.indexOf('  function normalizedEvents(')) + '\nvalidateScoreDocument;');
assert.doesNotThrow(() => validator(doc));
function parse(text) {
  const importer = new alphaTab.importer.AlphaTexImporter();
  importer.initFromString(text, new alphaTab.Settings());
  return importer.readScore();
}
const score = parse(doc.alphaTabSource.text);
const raw = parse('\\track "Guitar" {instrument 25}\n\\staff {tabs}\n\\tuning (E4 B3 G3 D3 A2 E2)\n\\capo 3\n\\ts (4 4)\n\\voiceMode barwise\n' + doc.transcription.bars.map(b => b.tab).join(' |\n'));
const staff = score.tracks[0].staves[0];
assert.equal(score.masterBars.length, 52);
assert.equal(staff.capo, 3);
assert.equal(score.tempo, 89);
assert.deepEqual(doc.transcription.bars.filter(b => b.page).map(b => [b.number, b.page]), [[1, 1], [16, 2], [37, 3]]);
const signature = bar => bar.voices.map(v => v.beats.map(b => ({
  start: b.displayStart, duration: b.displayDuration, grace: b.graceType, rest: b.isRest,
  notes: b.notes.map(n => [n.string, n.fret, n.isTieDestination, n.isHammerPullOrigin, n.slideOutType, n.realValue])
})));
for (const [i, bar] of staff.bars.entries()) {
  assert.equal(doc.transcription.bars[i].number, i + 1);
  assert.equal(bar.voices.length, 2);
  for (const voice of bar.voices) {
    assert.equal(voice.beats.reduce((sum, b) => sum + b.displayDuration, 0), 3840, `bar ${i + 1}, voice ${voice.index}: full 4/4`);
    for (const beat of voice.beats) for (const note of beat.notes) {
      assert.ok(!note.isDead);
      if (note.isTieDestination) assert.ok(note.tieOrigin, `bar ${i + 1}: tie has origin`);
      assert.equal(note.realValue, doc.tuning[6 - note.string] + note.fret + 3, `bar ${i + 1}: capo counted once`);
    }
  }
  assert.deepEqual(signature(bar), signature(raw.tracks[0].staves[0].bars[i]), `bar ${i + 1}: all voices match`);
}
const beats = (bar, voice = 0) => staff.bars[bar - 1].voices[voice].beats;
const positions = beat => beat.notes.map(n => [7 - n.string, n.fret]).sort((a, b) => a[0] - b[0]);
assert.deepEqual(beats(1).flatMap(b => b.notes.map(n => [7 - n.string, n.fret])), [[2, 0], [2, 1], [2, 3]], 'first pickup on second line');
assert.equal(beats(1)[3].notes[0].slideOutType, alphaTab.model.SlideOutType.Legato);
assert.equal(beats(2)[0].notes[0].isTieDestination, true);
assert.deepEqual(positions(beats(2, 1)[0]), [[6, 3]], 'bar 2 low E fret 3');
assert.deepEqual(positions(beats(3)[0]), [[1, 2], [2, 4], [3, 4]], 'bar 3 upper arpeggio');
assert.deepEqual(positions(beats(3, 1)[0]), [[5, 2]], 'bar 3 bass on fifth line');
assert.deepEqual(beats(5).filter(b => b.graceType).map(positions), [[[3, 2]]], 'bar 5 hammer grace on third line');
assert.ok(beats(5).some(b => b.notes.some(n => n.isHammerPullOrigin)));
assert.equal(beats(16)[0].notes[0].isTieDestination, true, 'page 1 to page 2 tie');
assert.equal(beats(37)[0].notes[0].isTieDestination, true, 'page 2 to page 3 tie');
assert.deepEqual(beats(49).map(b => b.notes.length), Array(8).fill(1), 'bar 49 single melody notes');
assert.deepEqual(positions(beats(50, 1)[0]), [[4, 0]], 'bar 50 bass on fourth line');
assert.deepEqual(beats(51).slice(1, 3).map(positions), [[[4, 2]], [[4, 4]]], 'bar 51 sixteenth hammer 2→4 on fourth line');
assert.deepEqual(positions(beats(52)[1]), [[1, 2], [2, 0], [4, 4]], 'final upper chord');
assert.deepEqual(positions(beats(52, 1)[1]), [[6, 3]], 'final bass on sixth line');
const before = staff.bars.map(signature);
const resolve = vm.runInNewContext(app.slice(app.indexOf('  function resolveImageCrossPitches('), app.indexOf('  function restoreImageCrossGlyphs(')) + '\nresolveImageCrossPitches;');
resolve(score, doc);
assert.deepEqual(staff.bars.map(signature), before, 'runtime pitch resolver preserves numbered notes');
console.log('PASS: actual import validator; 52 bars / 3 pages; 104 full-length voices; JSON/AlphaTex consistency; image string/fret anchors; grace/hammer/slide; cross-page ties; capo 3 pitches; runtime resolver.');
