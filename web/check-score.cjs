const fs = require('node:fs');
const assert = require('node:assert/strict');
const alphaTab = require('../docs/vendor/alphatab/alphaTab.min.js');
const doc = JSON.parse(fs.readFileSync(__dirname + '/scores/score-complete-example.json', 'utf8'));
const source = fs.readFileSync(__dirname + '/scores/guitar-techniques.alphaTex.js', 'utf8');
doc.alphaTabSource.text = source.slice(source.indexOf('String.raw`') + 11, source.lastIndexOf('`'));
const importer = new alphaTab.importer.AlphaTexImporter();
importer.initFromString(doc.alphaTabSource.text, new alphaTab.Settings());
let score;
try { score = importer.readScore(); } catch (error) {
  console.error(error.cause?.parserDiagnostics?.items || error.message);
  process.exit(1);
}
assert.equal(score.masterBars.length, 24);
const measures = doc.jianpu.tracks[0].events;
assert.equal(measures.length, score.masterBars.length);
for (const bar of score.tracks[0].staves[0].bars) {
  const beats = bar.voices[0].beats;
  const regular = beats.filter(b => b.graceType === 0);
  assert.equal(regular.reduce((sum, b) => sum + b.displayDuration, 0), bar.masterBar.calculateDuration(), `measure ${bar.index}: duration`);
  const events = measures[bar.index].events;
  assert.equal(events.length, regular.length, `measure ${bar.index}: event count`);
  events.forEach((event, index) => {
    const beat = beats[event.beatIndex ?? index];
    assert.equal(event.tick, beat.displayStart, `measure ${bar.index}: tick`);
    assert.equal(event.durationTicks, beat.displayDuration, `measure ${bar.index}: event duration`);
  });
}
const bars = score.tracks[0].staves[0].bars;
const note = (bar, beat) => bars[bar].voices[0].beats[beat].notes[0];
assert.equal(note(2, 3).isTieDestination, true);
assert.equal(note(7, 0).isHammerPullOrigin, true);
assert.equal(note(7, 2).isHammerPullOrigin, true);
assert.ok(note(8, 0).slideOutType);
assert.ok(note(10, 0).bendPoints.length);
assert.ok(note(12, 0).harmonicType);
assert.equal(note(13, 1).isDead, true);
assert.equal(score.masterBars[19].isRepeatStart, true);
assert.equal(score.masterBars[20].repeatCount, 2);
assert.equal(score.masterBars[20].alternateEndings, 1);
assert.equal(score.masterBars[21].alternateEndings, 2);
console.log('PASS: 24 measures parsed; all meter durations, jianpu ticks/durations, grace mapping and representative techniques/repeats verified.');
// Exercise alphaTab's real settings conversion, as used by AlphaTabApi.
const settings = new alphaTab.Settings();
settings.fillFromJson({
  core: { engine: 'svg', enableLazyLoading: false, includeNoteBounds: true },
  display: { barCountPerPartial: Number.MAX_SAFE_INTEGER }
});
assert.equal(settings.core.enableLazyLoading, false);
assert.equal(settings.display.barCountPerPartial, Number.MAX_SAFE_INTEGER);
console.log('PASS: alphaTab accepts eager, single-partial rendering settings.');
