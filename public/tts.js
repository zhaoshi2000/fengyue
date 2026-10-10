const $ = selector => document.querySelector(selector);
const state = { voices: [], selected: '', audio: null, url: null, request: null };
const languageNames = typeof Intl.DisplayNames === 'function' ? new Intl.DisplayNames(['zh-CN'], { type: 'language' }) : null;

function languageOf(voice) { return voice.locale?.split('-')[0] || 'other'; }
function languageName(code) { try { return languageNames?.of(code) || code; } catch (_) { return code; } }
function setStatus(value) { $('#status').textContent = value; }
function stop() {
  state.request?.abort(); state.request = null;
  if (state.audio) { state.audio.pause(); state.audio.removeAttribute('src'); state.audio.load(); state.audio = null; }
  if (state.url) { URL.revokeObjectURL(state.url); state.url = null; }
  $('#stopPlayback').hidden = true;
}
function selectVoice(id) {
  state.selected = id;
  const voice = state.voices.find(item => item.id === id);
  $('#selectedVoice').textContent = voice ? `${voice.locale} · ${voice.label}` : '请选择音色';
  $('#playSelected').disabled = !voice;
  try { localStorage.setItem('fengyue.tts.voice', id); } catch (_) { /* Optional preference. */ }
  document.querySelectorAll('.voice-card').forEach(card => card.classList.toggle('selected', card.dataset.id === id));
}
function render() {
  const language = $('#languageFilter').value;
  const query = $('#voiceSearch').value.trim().toLowerCase();
  const visible = state.voices.filter(voice => (language === '*' || languageOf(voice) === language)
    && `${voice.id} ${voice.label} ${voice.locale} ${languageName(languageOf(voice))}`.toLowerCase().includes(query));
  $('#visibleCount').textContent = `${visible.length} 个音色`;
  const grid = $('#voiceGrid'); grid.replaceChildren();
  if (!visible.length) { const empty = document.createElement('p'); empty.className = 'empty'; empty.textContent = '没有找到匹配的音色'; grid.append(empty); return; }
  for (const voice of visible) {
    const card = document.createElement('article'); card.className = `voice-card${voice.id === state.selected ? ' selected' : ''}`; card.dataset.id = voice.id;
    const title = document.createElement('h3'); title.textContent = voice.label;
    const id = document.createElement('small'); id.textContent = voice.id;
    const meta = document.createElement('div'); meta.className = 'meta'; meta.textContent = `${voice.locale} · ${languageName(languageOf(voice))}`;
    const play = document.createElement('button'); play.type = 'button'; play.textContent = '▶ 试听';
    play.addEventListener('click', () => { selectVoice(voice.id); playSelected(); });
    card.addEventListener('click', event => { if (event.target !== play) selectVoice(voice.id); });
    card.append(title, id, meta, play); grid.append(card);
  }
}
async function playSelected() {
  if (!state.selected) return;
  const text = $('#sampleText').value.trim();
  if (!text) return setStatus('请先输入试听文字');
  stop();
  const controller = new AbortController(); state.request = controller;
  setStatus('正在生成试听音频…'); $('#stopPlayback').hidden = false;
  try {
    const response = await fetch('/api/tts/preview', { method: 'POST', credentials: 'same-origin', signal: controller.signal,
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text, voice: state.selected }) });
    if (!response.ok) { const data = await response.json().catch(() => ({})); throw new Error(data.error || '试听失败'); }
    const blob = await response.blob();
    if (controller.signal.aborted) return;
    state.request = null; state.url = URL.createObjectURL(blob); state.audio = new Audio(state.url);
    state.audio.onended = () => { stop(); setStatus('试听结束'); };
    state.audio.onerror = () => { stop(); setStatus('音频播放失败'); };
    await state.audio.play(); setStatus('正在播放试听音频');
  } catch (error) { if (!controller.signal.aborted) { stop(); setStatus(error.message || '试听失败'); } }
}
async function init() {
  try {
    const response = await fetch('/api/tts/voices', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('音色列表加载失败');
    const data = await response.json(); state.voices = data.voices || [];
    const counts = new Map();
    for (const voice of state.voices) counts.set(languageOf(voice), (counts.get(languageOf(voice)) || 0) + 1);
    const filter = $('#languageFilter'); filter.replaceChildren();
    const add = (value, label) => { const option = document.createElement('option'); option.value = value; option.textContent = label; filter.append(option); };
    if (counts.has('zh')) add('zh', `中文（${counts.get('zh')}）`);
    add('*', `全部语言（${state.voices.length}）`);
    for (const code of [...counts.keys()].filter(code => code !== 'zh').sort((a, b) => languageName(a).localeCompare(languageName(b), 'zh-CN')))
      add(code, `${languageName(code)}（${counts.get(code)}）`);
    filter.disabled = false;
    let saved; try { saved = localStorage.getItem('fengyue.tts.voice'); } catch (_) { /* Optional preference. */ }
    const selected = state.voices.find(voice => voice.id === saved) || state.voices.find(voice => voice.id === data.defaultVoice) || state.voices[0];
    if (selected) { if (languageOf(selected) !== 'zh') filter.value = languageOf(selected); selectVoice(selected.id); }
    $('#voiceCount').textContent = `当前可用 ${state.voices.length} 个音色，覆盖 ${counts.size} 种语言`;
    render();
  } catch (error) { setStatus(error.message); $('#voiceCount').textContent = '音色列表暂不可用'; }
}
$('#languageFilter').addEventListener('change', render);
$('#voiceSearch').addEventListener('input', render);
$('#playSelected').addEventListener('click', playSelected);
$('#stopPlayback').addEventListener('click', () => { stop(); setStatus('已停止试听'); });
window.addEventListener('beforeunload', stop);
$('#backButton').addEventListener('click', () => {
  const target = new URLSearchParams(location.search).get('return');
  if (target?.startsWith('/zh/explore/installed/')) location.href = target;
  else if (history.length > 1) history.back();
  else location.href = '/';
});
init();
