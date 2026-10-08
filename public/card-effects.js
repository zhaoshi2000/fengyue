/* Author CSS runs only inside sandboxed, non-interactive frames. */
window.CardEffects = (() => {
  const html = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const css = value => String(value ?? '').replaceAll('<', '\\3C ');
  const safeUrl = value => /^(https:\/\/[^\s<>"']+|\/media\/[0-9a-f-]{36}\.(png|jpg)|\/palace-mystery-bg\.png)$/.test(String(value || '')) ? String(value) : '';
  const csp = `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src http: https: data:; font-src 'none'; script-src 'none'; form-action 'none'">`;
  function cardDoc(item) {
    const card = item?.card || {};
    const url = safeUrl(card.backgroundUrl);
    const background = url ? `,url(${JSON.stringify(url)}) center/cover` : '';
    return `<!doctype html><html><head><meta charset="utf-8">${csp}<style>*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;font-family:system-ui,'Microsoft YaHei',sans-serif}.card{height:100%;width:100%;padding:20px;display:flex;flex-direction:column;justify-content:flex-end;position:relative;overflow:hidden;background:linear-gradient(135deg,#4e3e75d9,#ad6b91bb)${background};color:#fff;border-radius:17px}.card:before{content:'';position:absolute;width:180px;height:180px;border:1px solid #fff8;border-radius:50%;right:-55px;top:-60px}.sigil{position:absolute;right:21px;top:14px;font-size:33px;opacity:.8}.card small{letter-spacing:2px;font-size:10px;opacity:.8}.card h2{font-size:21px;line-height:1.3;margin:8px 0 6px;max-width:85%}.card p{font-size:11px;line-height:1.5;max-width:90%;margin:0;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}${css(card.authorCss)}@media(prefers-reduced-motion:reduce){*,*::before,*::after{animation:none!important;transition:none!important}}</style></head><body><div class="card"><span class="sigil">${html(item?.icon || '✦')}</span><small>INTERACTIVE STORY</small><h2>${html(item?.title || '角色卡预览')}</h2><p>${html(item?.summary || '写下故事简介，预览卡片效果。')}</p></div></body></html>`;
  }
  function sceneDoc(item) {
    const card = item?.card || {};
    const url = safeUrl(card.backgroundUrl);
    const background = url ? `,url(${JSON.stringify(url)}) center/cover` : '';
    return `<!doctype html><html><head><meta charset="utf-8">${csp}<style>*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden}.scene{position:relative;width:100%;height:100%;overflow:hidden;background:linear-gradient(115deg,#171820c0,#20232a44 55%,#28343c66)${background};background-color:#342a42;background-size:cover}.scene:before{content:'';position:absolute;inset:0;background:radial-gradient(circle at 68% 32%,#ffe1ae35,transparent 25%)}.aura{position:absolute;left:10%;bottom:7%;width:40vmin;height:40vmin;border:1px solid #ffe7c149;border-radius:50%;box-shadow:0 0 70px #ffe9bd22;animation:auraFloat 10s ease-in-out infinite}.sigil{position:absolute;right:15%;top:18%;font-size:45px;color:#fff8;animation:auraFloat 7s ease-in-out infinite}@keyframes auraFloat{50%{transform:translateY(-25px) rotate(8deg)}}${css(card.authorCss)}@media(prefers-reduced-motion:reduce){*,*::before,*::after{animation:none!important;transition:none!important}}</style></head><body><div class="scene"><div class="aura"></div><div class="sigil">✦</div></div></body></html>`;
  }
  return { cardDoc, sceneDoc, safeUrl };
})();
