const test = require('node:test');
const assert = require('node:assert/strict');
const { normalizePercent, percentForEntry, markerStyle } = require('../main/resources/static/map-position.js');

test('uses the assigned percentage and clamps it to the aisle', () => {
  assert.equal(percentForEntry({ aislePercent: 35 }, 0, 1), 35);
  assert.equal(percentForEntry({ aislePercent: -5 }, 0, 1), 0);
  assert.equal(percentForEntry({ aislePercent: 120 }, 0, 1), 100);
});

test('distributes items with no saved percentage through the aisle', () => {
  assert.equal(percentForEntry({}, 0, 3), 25);
  assert.equal(percentForEntry({}, 1, 3), 50);
  assert.equal(percentForEntry({}, 2, 3), 75);
});

test('maps vertical aisle percentages to the vertical centerline', () => {
  assert.deepEqual(markerStyle('vertical', 35), {
    left: '50%',
    top: '35%',
    transform: 'translate(-50%, -50%)'
  });
});

test('maps horizontal aisle percentages to the horizontal centerline', () => {
  assert.deepEqual(markerStyle('horizontal', 65), {
    left: '65%',
    top: '50%',
    transform: 'translate(-50%, -50%)'
  });
});

test('normalizes invalid percentages to a safe midpoint', () => {
  assert.equal(normalizePercent(Number.NaN), 50);
});
