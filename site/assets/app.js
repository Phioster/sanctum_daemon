/* Alles hier ist entweder Deko oder kommt aus der oeffentlichen GitHub-API.
   Die Seite hat kein Backend und sammelt nichts. */
(() => {
  'use strict';

  const REPO = 'Phioster/sanctum_daemon';
  const $ = (s) => document.querySelector(s);
  const calm = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---------- [fx]: dim -> max -> off, gemerkt ---------- */

  const MODES = ['dim', 'max', 'off'];
  const btn = $('#fx');

  function setFx(mode) {
    document.documentElement.dataset.fx = mode;
    btn.textContent = `[fx: ${mode}]`;
    try { localStorage.setItem('fx', mode); } catch (e) { /* privates Fenster */ }
  }

  let stored = null;
  try { stored = localStorage.getItem('fx'); } catch (e) { /* s.o. */ }
  setFx(MODES.includes(stored) ? stored : (calm ? 'off' : 'dim'));

  btn.addEventListener('click', () => {
    setFx(MODES[(MODES.indexOf(document.documentElement.dataset.fx) + 1) % MODES.length]);
  });

  /* ---------- Uhr, im Format des Dashboards ---------- */

  const pad = (n) => String(n).padStart(2, '0');
  function tick() {
    const d = new Date();
    // Der Doppelpunkt blinkt, wie die Uhr-Kachel im Dashboard. Die Klammern
    // setzt das Stylesheet, damit sie beim Umbruch nicht auf eigene Zeilen rutschen.
    $('#t-clock').innerHTML =
      `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${String(d.getFullYear()).slice(2)},` +
      ` ${pad(d.getHours())}<span class="blink">:</span>${pad(d.getMinutes())}`;
  }
  tick();
  setInterval(tick, 10000);

  /* ---------- whoami tippt sich einmal ---------- */

  const typed = $('#typed');
  const text = typed.dataset.text || '';
  if (document.documentElement.dataset.fx === 'off') {
    typed.textContent = text;
  } else {
    let i = 0;
    const step = () => {
      // In Spruengen statt Zeichen fuer Zeichen: 600 Zeichen einzeln sind 600 Layouts.
      i = Math.min(text.length, i + 3);
      typed.textContent = text.slice(0, i);
      if (i < text.length) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }

  /* ---------- [ok] leuchtet auf, wenn es in Sicht kommt ---------- */

  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver((entries) => {
      entries.forEach((e, n) => {
        if (!e.isIntersecting) return;
        setTimeout(() => e.target.classList.add('lit'), n * 70);
        io.unobserve(e.target);
      });
    }, { threshold: 0.6 });
    document.querySelectorAll('.ok').forEach((el) => io.observe(el));
  } else {
    document.querySelectorAll('.ok').forEach((el) => el.classList.add('lit'));
  }

  /* ---------- Slash-Befehle als Sprungmarken ---------- */

  const JUMPS = {
    dl: 'download', download: 'download', d: 'download',
    faq: 'faq', f: 'faq',
    shots: 'screenshots', s: 'screenshots', screenshots: 'screenshots',
    features: 'features', feat: 'features',
    works: 'works', w: 'works',
    top: 'top', home: 'top',
  };
  const cmd = $('#cmd');
  cmd.addEventListener('keydown', (ev) => {
    if (ev.key !== 'Enter') return;
    const q = cmd.value.trim().replace(/^\//, '').toLowerCase();
    if (q === 'fx') { btn.click(); cmd.value = ''; return; }
    if (q === 'git' || q === 'github') { location.href = `https://github.com/${REPO}`; return; }
    const id = JUMPS[q];
    if (id) {
      document.getElementById(id).scrollIntoView({ behavior: calm ? 'auto' : 'smooth' });
      cmd.value = '';
      cmd.blur();
    } else {
      cmd.value = '';
      cmd.placeholder = `no such command: /${q}`;
      setTimeout(() => { cmd.placeholder = cmd.dataset.ph; }, 1800);
    }
  });
  cmd.dataset.ph = cmd.placeholder;

  /* ---------- echte Zahlen aus der oeffentlichen Release-API ---------- */

  const mib = (b) => `${(b / 1048576).toFixed(1)} MB`;

  fetch(`https://api.github.com/repos/${REPO}/releases/latest`)
    .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
    .then((rel) => {
      $('#t-version').textContent = rel.tag_name;
      const d = new Date(rel.published_at);
      $('#t-released').textContent = `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()}`;

      (rel.assets || []).forEach((a) => {
        const card = a.name.includes('-tv-') ? '#dl-tv' : '#dl-phone';
        const el = document.querySelector(card);
        if (!el) return;
        el.querySelector('.size').textContent = mib(a.size);
        el.querySelector('.count').textContent = a.download_count;
        el.querySelector('.btn').href = a.browser_download_url;
        el.querySelector('.btn').textContent = `[ ${a.name} ]`;
      });
    })
    .catch(() => {
      // Ohne Netz oder ueber dem API-Limit bleibt die Seite benutzbar: auf die Release-Liste zeigen.
      document.querySelectorAll('.btn').forEach((b) => {
        if (!b.href || b.href.endsWith('#')) b.href = `https://github.com/${REPO}/releases/latest`;
      });
      document.querySelectorAll('.size, .count').forEach((e) => { e.textContent = '—'; });
      $('#t-version').textContent = 'see releases';
    });

  /* ---------- Matrix-Regen ---------- */

  const cv = $('#rain');
  const ctx = cv.getContext('2d');
  const GLYPHS = '01</>{}[]#$%&*+-=~^|\\ABCDEFGHJKLMNPRSTUVWXYZ';
  let cols = 0, drops = [], step = 22, raf = 0, last = 0;

  function size() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    cv.width = Math.floor(innerWidth * dpr);
    cv.height = Math.floor(innerHeight * dpr);
    cv.style.width = innerWidth + 'px';
    cv.style.height = innerHeight + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    cols = Math.ceil(innerWidth / step);
    drops = Array.from({ length: cols }, () => Math.random() * -50);
  }

  function frame(t) {
    raf = requestAnimationFrame(frame);
    if (t - last < 55) return;            // ~18 fps, das reicht und schont den Akku
    last = t;
    ctx.fillStyle = 'rgba(2,8,5,.40)';
    ctx.fillRect(0, 0, innerWidth, innerHeight);
    ctx.font = `${step - 2}px ${getComputedStyle(document.body).fontFamily}`;
    for (let i = 0; i < cols; i++) {
      const y = drops[i] * step;
      ctx.fillStyle = Math.random() < 0.10 ? '#ccffcc' : 'rgba(0,255,65,.75)';
      ctx.fillText(GLYPHS[(Math.random() * GLYPHS.length) | 0], i * step, y);
      drops[i] = y > innerHeight && Math.random() > 0.99 ? 0 : drops[i] + 1;
    }
  }

  function rain(on) {
    if (on && !raf) { size(); raf = requestAnimationFrame(frame); }
    if (!on && raf) { cancelAnimationFrame(raf); raf = 0; ctx.clearRect(0, 0, innerWidth, innerHeight); }
  }

  const wantsRain = () => document.documentElement.dataset.fx !== 'off' && !calm;
  rain(wantsRain());
  btn.addEventListener('click', () => rain(wantsRain()));
  addEventListener('resize', () => { if (raf) size(); });
  // Im Hintergrund nicht weiterrechnen.
  document.addEventListener('visibilitychange', () => rain(!document.hidden && wantsRain()));
})();
