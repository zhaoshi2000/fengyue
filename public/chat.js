const $ = selector => document.querySelector(selector);
const storyId = decodeURIComponent(location.pathname.split('/').filter(Boolean).at(-1) || '');
const state = { item: null, user: null, conversationId: null, conversations: [], messages: [], config: null, busy: false, authMode: 'login', editingMessageId: null };
let toastTimer;

async function api(path, options = {}) {
  const response = await fetch(path, { credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, ...options });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || '请求失败');
  return data;
}
function toast(message) { const node = $('#toast'); node.textContent = message; node.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => node.hidden = true, 3500); }
function showInfo(title, message) { $('#infoTitle').textContent = title; $('#infoText').textContent = message; $('#infoDialog').showModal(); }
function setUser(user) {
  state.user = user;
  $('#userName').textContent = user ? user.name : '游客';
  $('#userAvatar').textContent = user ? user.name.slice(0, 1) : '访';
  $('#userPoints').textContent = user ? `积分 ${Number(user.points).toLocaleString('zh-CN')}` : '登录后保存会话';
  $('#accountButton').textContent = user ? '退出登录' : '登录 / 注册';
  $('#composerNote').textContent = user ? (state.config?.mode === 'model' ? `正在使用 ${state.config.model}，会话自动保存` : '当前为本地演示回复，真实 AI 需配置 API Key') : '游客可阅读故事，登录后可创建和保存对话';
}
function setConfig(config) {
  state.config = config;
  $('#modelChip').textContent = config.model;
  $('#modeTag').textContent = config.mode === 'model' ? 'AI 已连接' : '演示模式';
  $('#modeTag').classList.toggle('model', config.mode === 'model');
  setUser(state.user);
}
function authDialog(mode = 'login') {
  state.authMode = mode;
  $('#authTitle').textContent = mode === 'login' ? '登录后继续聊天' : '创建账号';
  $('#authSubmit').textContent = mode === 'login' ? '登录' : '注册并开始聊天';
  $('#switchAuth').textContent = mode === 'login' ? '没有账号？注册' : '已有账号？登录';
  $('#authPassword').minLength = mode === 'login' ? 1 : 8;
  $('#authPassword').autocomplete = mode === 'login' ? 'current-password' : 'new-password';
  $('#authForm').reset();
  $('#authDialog').showModal();
}
function requireLogin() { if (state.user) return true; authDialog(); toast('请先登录'); return false; }
async function loadConversations() {
  if (!state.user) { state.conversations = []; renderConversations(); return; }
  const data = await api(`/api/items/${encodeURIComponent(storyId)}/conversations`);
  state.conversations = data.conversations;
  renderConversations();
}
function renderConversations() {
  const list = $('#conversationList'); list.replaceChildren();
  if (!state.user) { const empty = document.createElement('p'); empty.className = 'conversation-empty'; empty.textContent = '登录后查看你的会话'; list.append(empty); return; }
  if (!state.conversations.length) { const empty = document.createElement('p'); empty.className = 'conversation-empty'; empty.textContent = '还没有会话，点击“新对话”开始'; list.append(empty); return; }
  for (const conversation of state.conversations) {
    const row = document.createElement('div'); row.className = `conversation-item${conversation.id === state.conversationId ? ' active' : ''}`;
    const select = document.createElement('button'); select.className = 'conversation-select'; select.textContent = `▤　${conversation.title}`; select.title = conversation.title;
    select.addEventListener('click', () => openConversation(conversation.id));
    const remove = document.createElement('button'); remove.className = 'conversation-delete'; remove.textContent = '×'; remove.title = '删除会话';
    remove.addEventListener('click', event => { event.stopPropagation(); deleteConversation(conversation.id); });
    row.append(select, remove); list.append(row);
  }
}
function renderMessages(messages) {
  state.messages = messages;
  const root = $('#messages'); root.replaceChildren();
  const active = messages.length > 0;
  $('#intro').hidden = active;
  root.hidden = !active;
  $('#sceneFrame').hidden = !active;
  $('.chat-main').classList.toggle('has-messages', active);
  const latestAssistant = [...messages].reverse().find(message => message.role === 'assistant')?.id;
  const canRegenerate = messages.some(message => message.role === 'user');
  for (const message of messages) {
    const row = document.createElement('div'); row.className = `message ${message.role === 'user' ? 'user' : 'assistant'}${String(message.id).startsWith('pending-') ? ' pending' : ''}`;
    const avatar = document.createElement('div'); avatar.className = 'message-avatar'; avatar.textContent = message.role === 'user' ? '我' : '✿';
    const body = document.createElement('div'); body.className = 'message-body';
    const name = document.createElement('div'); name.className = 'message-name'; name.textContent = message.role === 'user' ? (state.user?.name || '我') : state.item.title;
    const time = document.createElement('div'); time.className = 'message-time';
    time.textContent = message.createdAt ? new Date(message.createdAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '';
    const content = document.createElement('div'); content.className = 'message-content'; content.textContent = message.content;
    body.append(name, time, content);
    if (!String(message.id).startsWith('pending-')) {
      const actions = document.createElement('div'); actions.className = 'message-actions';
      for (const [action, label] of [['copy', '▣ 复制'], ['edit', '✎ 编辑'], ['delete', '♲ 删除'], ...(canRegenerate && message.role === 'assistant' && message.id === latestAssistant ? [['regenerate', '↻ 重新生成']] : [])]) {
        const button = document.createElement('button'); button.type = 'button'; button.dataset.messageAction = action; button.dataset.messageId = message.id; button.textContent = label; actions.append(button);
      }
      body.append(actions);
    }
    row.append(avatar, body); root.append(row);
  }
  $('#chatScroll').scrollTop = $('#chatScroll').scrollHeight;
}
async function openConversation(id) {
  try {
    const data = await api(`/api/conversations/${encodeURIComponent(id)}`);
    state.conversationId = id; renderConversations(); renderMessages(data.messages);
    if (innerWidth < 681) $('#storySide').classList.remove('open');
    $('#messageInput').focus();
  } catch (error) { toast(error.message); }
}
async function newConversation() {
  if (!requireLogin()) return;
  try {
    const data = await api(`/api/items/${encodeURIComponent(storyId)}/conversations`, { method: 'POST' });
    state.conversationId = data.conversation.id;
    const detail = await api(`/api/conversations/${encodeURIComponent(state.conversationId)}`);
    renderMessages(detail.messages); await loadConversations();
    if (innerWidth < 681) $('#storySide').classList.remove('open');
    $('#messageInput').focus(); toast('新对话已创建');
  } catch (error) { toast(error.message); }
}
async function deleteConversation(id) {
  if (!confirm('删除这段会话及全部消息？')) return;
  try {
    await api(`/api/conversations/${encodeURIComponent(id)}`, { method: 'DELETE' });
    if (state.conversationId === id) { state.conversationId = null; renderMessages([]); }
    await loadConversations(); toast('会话已删除');
  } catch (error) { toast(error.message); }
}
async function sendMessage() {
  if (state.busy || !requireLogin()) return;
  const input = $('#messageInput'); const message = input.value.trim(); if (!message) return;
  state.busy = true; $('#sendButton').disabled = true; $('#composerNote').textContent = '正在生成回复…';
  let previous = [...state.messages];
  try {
    if (!state.conversationId) {
      const created = await api(`/api/items/${encodeURIComponent(storyId)}/conversations`, { method: 'POST' });
      state.conversationId = created.conversation.id;
      const opening = await api(`/api/conversations/${encodeURIComponent(state.conversationId)}`);
      renderMessages(opening.messages); previous = [...opening.messages];
    }
    renderMessages([...previous,
      { id: 'pending-user', role: 'user', content: message, createdAt: new Date().toISOString() },
      { id: 'pending-assistant', role: 'assistant', content: '正在续写故事…', createdAt: new Date().toISOString() }]);
    await api(`/api/conversations/${encodeURIComponent(state.conversationId)}/messages`, { method: 'POST', body: JSON.stringify({ message }) });
    input.value = ''; updateCount();
    const detail = await api(`/api/conversations/${encodeURIComponent(state.conversationId)}`);
    renderMessages(detail.messages); await loadConversations();
  } catch (error) { renderMessages(previous); toast(error.message); }
  finally { state.busy = false; $('#sendButton').disabled = false; setUser(state.user); input.focus(); }
}
function updateCount() { const input = $('#messageInput'); $('#messageCount').textContent = `${input.value.length} / 2000`; input.style.height = 'auto'; input.style.height = `${Math.min(input.scrollHeight, 140)}px`; }
function renderQuickReplies() {
  const root = $('#quickReplies'); root.replaceChildren();
  const configured = String(state.item?.card?.quickReplies || '').split(/\r?\n/).map(x => x.trim()).filter(Boolean).slice(0, 8);
  for (const label of (configured.length ? configured : ['继续故事', '询问线索', '观察周围', '换个方向'])) {
    const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
    button.addEventListener('click', () => { if (!requireLogin()) return; $('#messageInput').value = label; updateCount(); sendMessage(); });
    root.append(button);
  }
}
async function init() {
  try {
    const [itemData, meData, config] = await Promise.all([
      api(`/api/items/${encodeURIComponent(storyId)}`), api('/api/me'), api('/api/chat/config')
    ]);
    state.item = itemData.item; document.title = `${state.item.title} - AI风月`;
    $('#storyTitle').textContent = state.item.title; $('#storySummary').textContent = state.item.summary;
    $('#introTitle').textContent = `✿　${state.item.title}　✿`; $('#backgroundText').textContent = state.item.summary;
    $('#sceneFrame').srcdoc = CardEffects.sceneDoc(state.item);
    if (state.item.card?.authorCss || state.item.card?.backgroundUrl) {
      $('#authorPreview').hidden = false;
      $('#authorPreviewFrame').srcdoc = CardEffects.cardDoc(state.item);
    }
    renderQuickReplies();
    setConfig(config); setUser(meData.user); await loadConversations();
    const selected = new URLSearchParams(location.search).get('conversation');
    if (selected && state.conversations.some(c => c.id === selected)) await openConversation(selected);
  } catch (error) { showInfo('页面加载失败', error.message); }
}

$('#newChat').addEventListener('click', newConversation);
$('#newChatBottom').addEventListener('click', newConversation);
$('#messageForm').addEventListener('submit', event => { event.preventDefault(); sendMessage(); });
$('#messageInput').addEventListener('input', updateCount);
$('#messageInput').addEventListener('keydown', event => { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); sendMessage(); } });
$('#messages').addEventListener('click', async event => {
  const button = event.target.closest('[data-message-action]'); if (!button) return;
  const message = state.messages.find(x => x.id === button.dataset.messageId); if (!message || !state.conversationId) return;
  const base = `/api/conversations/${encodeURIComponent(state.conversationId)}`;
  try {
    if (button.dataset.messageAction === 'copy') { await navigator.clipboard.writeText(message.content); toast('已复制消息'); return; }
    if (button.dataset.messageAction === 'edit') { state.editingMessageId = message.id; $('#editMessageInput').value = message.content; $('#editMessageDialog').showModal(); return; }
    if (button.dataset.messageAction === 'delete') { if (!confirm('删除这条消息？')) return; const data = await api(`${base}/messages/${encodeURIComponent(message.id)}`, { method: 'DELETE' }); renderMessages(data.messages); await loadConversations(); toast('消息已删除'); return; }
    if (button.dataset.messageAction === 'regenerate') { button.disabled = true; const data = await api(`${base}/regenerate`, { method: 'POST' }); renderMessages(data.messages); await loadConversations(); toast('已重新生成回复'); }
  } catch (error) { toast(error.message); button.disabled = false; }
});
$('#editMessageForm').addEventListener('submit', async event => {
  event.preventDefault(); if (!state.editingMessageId || !state.conversationId) return;
  try {
    const data = await api(`/api/conversations/${encodeURIComponent(state.conversationId)}/messages/${encodeURIComponent(state.editingMessageId)}`,
      { method: 'PATCH', body: JSON.stringify({ content: $('#editMessageInput').value }) });
    $('#editMessageDialog').close(); renderMessages(data.messages); await loadConversations(); toast('消息已修改');
  } catch (error) { toast(error.message); }
});
$('#authForm').addEventListener('submit', async event => {
  event.preventDefault(); const button = $('#authSubmit'); button.disabled = true;
  try {
    const body = Object.fromEntries(new FormData(event.target));
    const data = await api(`/api/auth/${state.authMode}`, { method: 'POST', body: JSON.stringify(body) });
    setUser(data.user); $('#authDialog').close(); await loadConversations(); toast(state.authMode === 'login' ? '登录成功' : '注册成功');
  } catch (error) { toast(error.message); }
  finally { button.disabled = false; }
});
$('#switchAuth').addEventListener('click', () => { $('#authDialog').close(); authDialog(state.authMode === 'login' ? 'register' : 'login'); });
$('#accountButton').addEventListener('click', async () => { if (!state.user) return authDialog(); try { await api('/api/auth/logout', { method: 'POST' }); state.conversationId = null; setUser(null); renderMessages([]); renderConversations(); toast('已退出登录'); } catch (error) { toast(error.message); } });
document.querySelectorAll('[data-close-dialog]').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
$('#detailsButton').addEventListener('click', () => showInfo(state.item?.title || '作品详情', state.item?.summary || ''));
$('#storyInfoButton').addEventListener('click', () => showInfo(state.item?.title || '作品详情', state.item?.summary || ''));
$('#archivesButton').addEventListener('click', () => showInfo('热门存档', '每个账号的会话都显示在左侧列表中。点击“新对话”可创建另一条故事线。'));
$('#settingsButton').addEventListener('click', () => showInfo('模型设置', state.config?.mode === 'model' ? `当前模型：${state.config.model}。模型服务由本地 Spring Boot 后端调用。` : '当前为本地演示模式。设置服务器环境变量 AI_API_KEY 后重启服务即可连接兼容聊天接口；可选 AI_MODEL 和 AI_API_URL。'));
$('#scrollBottomButton').addEventListener('click', () => $('#chatScroll').scrollTo({ top: $('#chatScroll').scrollHeight, behavior: 'smooth' }));
$('#toggleIntroButton').addEventListener('click', () => { $('#intro').hidden = !$('#intro').hidden; $('#chatScroll').scrollTop = 0; });
$('#toggleStorySide').addEventListener('click', () => { if (innerWidth < 681) $('#storySide').classList.toggle('open'); else { $('#storySide').classList.toggle('collapsed'); $('.chat-app').classList.toggle('side-collapsed'); } });
$('#mobileMenu').addEventListener('click', () => $('#globalSide').classList.toggle('open'));
document.addEventListener('keydown', event => { if (event.key === 'Escape') { $('#globalSide').classList.remove('open'); $('#storySide').classList.remove('open'); } });
init();
