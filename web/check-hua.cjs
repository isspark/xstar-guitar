const fs = require('node:fs');
const assert = require('node:assert/strict');
const alphaTab = require('../docs/vendor/alphatab/alphaTab.min.js');
const { expand } = require('./score-image-adapter.js');
const input = JSON.parse(fs.readFileSync(__dirname + '/scores/hua.json', 'utf8'));
let doc;
try { doc = expand(input); } catch (error) { console.error(error.message); process.exit(1); }
const importer = new alphaTab.importer.AlphaTexImporter();
importer.initFromString(doc.alphaTabSource.text, new alphaTab.Settings());
let score;
try { score = importer.readScore(); } catch (error) {
  console.error(error.cause?.parserDiagnostics?.items || error.message);
  process.exit(1);
}
assert.equal(score.masterBars.length, input.rhythmSheet.bars.length);
assert.equal(score.masterBars.length, 58);
assert.deepEqual(input.rhythmSheet.bars.flatMap((bar, index) => bar.page ? [index + 1] : []), [1, 22, 42]);
assert.equal(score.tracks[0].staves[0].capo, 4);
for (const bar of score.tracks[0].staves[0].bars) {
  const beats = bar.voices[0].beats;
  const pattern = input.rhythmSheet.bars[bar.index].pattern;
  const sourceBar = input.rhythmSheet.bars[bar.index];
  const tap = sourceBar.tap || (bar.index < 9 ? [1, 3, 5, 7] : sourceBar.pattern === 'strum' ? [1, 4, 7] : []);
  assert.deepEqual(doc.jianpu.tracks[0].events[bar.index].tap, tap);
  if (pattern === 'strum' || pattern === 'break') {
    const notes = beats.flatMap(beat => beat.notes);
    assert.ok(notes.length > 0, `bar ${bar.index + 1}: playback notes preserved`);
    assert.ok(notes.every(note => note.isVisible), `bar ${bar.index + 1}: visible model notes preserve stems/beams`);
    assert.ok(beats.filter(beat => !beat.isRest).every(beat => beat.notes.length > 1));
    assert.ok(notes.every(note => Number.isInteger(note.fret) && !note.isDead));
  }
  assert.equal(beats.reduce((sum, beat) => sum + beat.displayDuration, 0), 3840, `bar ${bar.index + 1}`);
  const events = doc.jianpu.tracks[0].events[bar.index].events;
  assert.equal(events.reduce((sum, event) => sum + event.durationTicks, 0), 3840);
  let tick = 0;
  events.forEach(event => {
    assert.equal(event.tick, tick);
    const preceding = beats.filter(beat => beat.displayStart <= event.tick).pop();
    assert.ok(preceding, `bar ${bar.index + 1}: missing interpolation anchor`);
    assert.ok(event.tick < preceding.displayStart + preceding.displayDuration);
    tick += event.durationTicks;
  });
}
console.log(`PASS: ${score.masterBars.length} bars, capo 4, all accompaniment and melody measures total 4/4.`);
