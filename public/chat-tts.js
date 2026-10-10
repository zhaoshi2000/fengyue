const ChatTts = (() => {
  const voiceSelect = document.querySelector('#ttsVoiceSelect');
  const languageSelect = document.querySelector('#ttsLanguageSelect');
  const stopButton = document.querySelector('#ttsStopButton');
  const status = document.querySelector('#ttsStatus');
  const cache = new Map();
  let audio = null;
  let controller = null;
  let activeMessageId = null;
  let requestNumber = 0;
  let voices = [];

  function languageOf(voice) { return voice.locale?.split('-')[0] || 'other'; }
  function languageName(code) {
    try { return new Intl.DisplayNames(['zh-CN'], { type: 'language' }).of(code) || code; }
    catch (_) { return code; }
  }

  function renderVoiceOptions(preferred) {
    const choices = voices.filter(voice => languageSelect.value === '*' || languageOf(voice) === languageSelect.value);
    voiceSelect.replaceChildren();
    for (const voice of choices) {
      const option = document.createElement('option');
      option.value = voice.id; option.textContent = `${voice.locale} · ${voice.label}`; voiceSelect.append(option);
    }
    voiceSelect.value = choices.some(voice => voice.id === preferred) ? preferred : choices[0]?.id || '';
    voiceSelect.disabled = !choices.length;
    try { localStorage.setItem('fengyue.tts.voice', voiceSelect.value); } catch (_) { /* Optional preference. */ }
  }

  function refresh() {
    for (const button of document.querySelectorAll('[data-message-action="speak"]')) {
      const active = button.dataset.messageId === activeMessageId;
      button.classList.toggle('tts-active', active);
      button.textContent = active && controller ? '◌ 生成语音…'
        : active && audio && !audio.paused ? 'Ⅱ 暂停'
        : active && audio ? '▶ 继续' : '🔊 朗读';
      button.setAttribute('aria-label', button.textContent);
    }
    stopButton.hidden = !controller && !audio;
  }

  function stop() {
    requestNumber++;
    controller?.abort(); controller = null;
    if (audio) { audio.onended = null; audio.onerror = null; audio.pause(); audio.removeAttribute('src'); audio.load(); audio = null; }
    activeMessageId = null;
    status.textContent = '';
    refresh();
  }

  function clearCache() {
    stop();
    for (const value of cache.values()) URL.revokeObjectURL(value.url);
    cache.clear();
  }

  function remember(key, content, url) {
    const old = cache.get(key);
    if (old) URL.revokeObjectURL(old.url);
    cache.delete(key);
    cache.set(key, { content, url });
    while (cache.size > 8) {
      const oldestKey = cache.keys().next().value;
      URL.revokeObjectURL(cache.get(oldestKey).url);
      cache.delete(oldestKey);
    }
  }

  async function init() {
    const response = await fetch('/api/tts/voices', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('音色列表加载失败');
    const data = await response.json();
    voices = data.voices || [];
    const counts = new Map();
    for (const voice of voices) counts.set(languageOf(voice), (counts.get(languageOf(voice)) || 0) + 1);
    languageSelect.replaceChildren();
    const option = (value, label) => { const node = document.createElement('option'); node.value = value; node.textContent = label; languageSelect.append(node); };
    if (counts.has('zh')) option('zh', `中文（${counts.get('zh')}）`);
    option('*', `全部语言（${voices.length}）`);
    for (const code of [...counts.keys()].filter(code => code !== 'zh').sort((a, b) => languageName(a).localeCompare(languageName(b), 'zh-CN')))
      option(code, `${languageName(code)}（${counts.get(code)}）`);
    let saved;
    try { saved = localStorage.getItem('fengyue.tts.voice'); } catch (_) { /* Private browsing may disable storage. */ }
    const selected = voices.find(voice => voice.id === saved) || voices.find(voice => voice.id === data.defaultVoice) || voices[0];
    if (selected && languageOf(selected) !== 'zh') languageSelect.value = languageOf(selected);
    renderVoiceOptions(selected?.id);
    languageSelect.disabled = false;
  }

  async function toggle(conversationId, message) {
    if (voiceSelect.disabled) return toast('音色列表尚未就绪');
    if (activeMessageId === message.id && audio) {
      try {
        if (audio.paused) await audio.play(); else audio.pause();
        if (!audio) return;
        status.textContent = audio.paused ? '朗读已暂停' : '正在朗读';
        refresh();
      } catch (_) { toast('浏览器无法播放语音，请检查音频权限'); }
      return;
    }
    if (activeMessageId === message.id && controller) { stop(); return; }
    stop();
    const currentRequest = ++requestNumber;
    const voice = voiceSelect.value;
    const key = `${conversationId}:${message.id}:${voice}`;
    const cached = cache.get(key);
    activeMessageId = message.id;
    status.textContent = cached?.content === message.content ? '正在播放…' : '正在生成语音…';
    refresh();
    try {
      let url;
      if (cached?.content === message.content) {
        url = cached.url;
      } else {
        controller = new AbortController(); refresh();
        const response = await fetch('/api/tts/speech', {
          method: 'POST', credentials: 'same-origin', signal: controller.signal,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ conversationId, messageId: message.id, voice })
        });
        if (!response.ok) {
          const error = await response.json().catch(() => ({}));
          throw new Error(error.error || '语音生成失败');
        }
        const blob = await response.blob();
        if (!blob.size) throw new Error('语音服务没有返回音频');
        if (currentRequest !== requestNumber) return;
        url = URL.createObjectURL(blob);
        remember(key, message.content, url);
      }
      if (currentRequest !== requestNumber) return;
      controller = null;
      audio = new Audio(url);
      audio.onended = () => { audio = null; activeMessageId = null; status.textContent = ''; refresh(); };
      audio.onerror = () => { stop(); toast('音频播放失败'); };
      await audio.play();
      if (currentRequest !== requestNumber) return;
      status.textContent = '正在朗读';
      refresh();
    } catch (error) {
      if (currentRequest !== requestNumber) return;
      stop();
      if (error.name !== 'AbortError') toast(error.message || '语音播放失败');
    }
  }

  voiceSelect.addEventListener('change', () => {
    stop();
    try { localStorage.setItem('fengyue.tts.voice', voiceSelect.value); } catch (_) { /* Optional preference. */ }
  });
  languageSelect.addEventListener('change', () => { stop(); renderVoiceOptions(voiceSelect.value); });
  stopButton.addEventListener('click', stop);
  window.addEventListener('beforeunload', clearCache);
  return { init, toggle, stop, clearCache, refresh };
})();
