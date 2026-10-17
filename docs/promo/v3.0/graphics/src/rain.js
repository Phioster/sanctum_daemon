// Matrix rain as static text, seeded so every render is identical.
(function () {
  let s = 7; const r = () => (s = (s * 16807) % 2147483647) / 2147483647;
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ0123456789<>=+*/:.|';
  document.querySelectorAll('.rain').forEach(el => {
    const cols = Math.ceil(el.clientWidth / 22), rows = Math.ceil(el.clientHeight / 22);
    let out = '';
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) out += r() < 0.42 ? chars[Math.floor(r() * chars.length)] + ' ' : '  ';
      out += '\n';
    }
    el.textContent = out;
  });
})();
