(function (root) {
  function normalizePercent(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return 50;
    return Math.max(0, Math.min(100, number));
  }

  function percentForEntry(entry, index, count) {
    const fallback = Math.round((index + 1) * 100 / (Math.max(0, count) + 1));
    return normalizePercent(entry && entry.aislePercent != null ? entry.aislePercent : fallback);
  }

  function markerStyle(orientation, percent) {
    const position = `${normalizePercent(percent)}%`;
    if (String(orientation).toLowerCase() === 'horizontal') {
      return { left: position, top: '50%', transform: 'translate(-50%, -50%)' };
    }
    return { left: '50%', top: position, transform: 'translate(-50%, -50%)' };
  }

  const api = { normalizePercent, percentForEntry, markerStyle };
  root.FigoMapPosition = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(globalThis);
