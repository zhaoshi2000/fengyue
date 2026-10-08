const $ = selector => document.querySelector(selector);
const state = { user: null, items: [], featured: [], categories: [], category: '推荐', sort: 'recommended', view: '', query: '', page: 1, hasMore: false, activeItem: null, editingItemId: null };
const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const number = value => Number(value || 0).toLocaleString('zh-CN');
let toastTimer;
function toast(message) { const node = $('#toast'); node.textContent = message; node.classList.remove('hidden'); clearTimeout(toastTimer); toastTimer = setTimeout(() => node.classList.add('hidden'), 3200); }
async function api(url, options = {}) {
  const response = await fetch(url, { credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, ...options });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || '请求失败');
  return data;
}
function modal(html, wide = false) { $('#modalContent').innerHTML = html; $('#modalBackdrop').classList.remove('hidden'); $('.modal').classList.toggle('wide', wide); document.body.style.overflow = 'hidden'; }
function closeModal() { $('#modalBackdrop').classList.add('hidden'); document.body.style.overflow = ''; state.activeItem = null; }
function updateProfile() {
  const user = state.user;
  $('#profileName').textContent = user ? user.name : '欢迎来到 AI风月';
  $('#profileLevel').textContent = user ? 'Lv.1 · 故事探索者' : '开启你的探索之旅';
  $('#profilePoints').innerHTML = `${user ? number(user.points) : '0'} <small>积分</small>`;
  $('#guestActions').classList.toggle('hidden', Boolean(user));
  $('#logoutButton').classList.toggle('hidden', !user);
  $('#topLogin').classList.toggle('hidden', Boolean(user));
  $('#topRegister').classList.toggle('hidden', Boolean(user));
  $('#topAvatar').classList.toggle('hidden', !user);
  $('#adminLink').classList.toggle('hidden', !user?.admin);
  if (user) $('#topAvatar').textContent = user.name.slice(0, 1);
}
function renderCategories() {
  $('#categoryTabs').innerHTML = state.categories.map(category => `<button class="tab ${category === state.category ? 'active' : ''}" role="tab" aria-selected="${category === state.category}" data-category="${esc(category)}">${esc(category)}</button>`).join('');
}
function renderRanking() { document.querySelectorAll('.rank-button').forEach(button => button.classList.toggle('active', button.dataset.sort === state.sort)); $('#sortSelect').value = state.sort; }
function card(item, index) {
  const custom = Boolean(item.card?.authorCss || item.card?.backgroundUrl);
  const art = custom ? `<iframe class="card-effect-frame" sandbox="" loading="lazy" tabindex="-1" aria-hidden="true" srcdoc="${esc(CardEffects.cardDoc(item))}"></iframe>` : `<span class="cover-icon">${esc(item.icon)}</span>`;
  return `<article class="story-card card-style-${index % 6} ${custom ? 'has-author-effect' : ''}" style="--card-order:${Math.min(index, 11)}"><div class="card-cover cover-theme-${esc(item.theme)}"><span class="category-badge">${esc(item.category)}</span>${art}<button class="favorite-button ${item.favorite ? 'active' : ''}" data-favorite="${esc(item.id)}" aria-label="${item.favorite ? '取消收藏' : '收藏'}">${item.favorite ? '♥' : '♡'}</button></div><div class="card-body"><button class="card-open" data-open="${esc(item.id)}"><h3 class="card-title">${esc(item.title)}</h3><p class="card-author">by ${esc(item.author)}</p><p class="card-summary">${esc(item.summary)}</p></button><div class="card-meta"><span>◉ ${number(item.views)}</span><span>♡ ${number(item.likes)}</span></div></div></article>`;
}
function renderCards() {
  $('#cardGrid').innerHTML = state.items.length ? state.items.map((item, index) => card(item, index)).join('') : `<div class="empty"><strong>这里还没有故事</strong>试试其他关键词或分区。</div>`;
  const labels = { favorites: '我的收藏', following: '我的关注', history: '浏览历史' };
  $('#feedTitle').textContent = labels[state.view] || (state.query ? `搜索：${state.query}` : state.category === '推荐' ? '为你推荐' : state.category);
  $('#feedSubtitle').textContent = `共 ${state.items.length} 个故事`;
}
function renderFeatured() {
  $('#featuredList').innerHTML = state.featured.slice(0, 4).map(item => `<div class="feature-item" data-open="${esc(item.id)}"><div class="feature-thumb cover-theme-${esc(item.theme)}">${esc(item.icon)}</div><div><strong>${esc(item.title)}</strong><span>${esc(item.author)} · ${number(item.views)} 浏览</span></div></div>`).join('');
}
let listRequest = 0;
async function loadItems(append = false) {
  const request = ++listRequest;
  const page = append ? state.page + 1 : 1;
  const params = new URLSearchParams({ q: state.query, category: state.category, sort: state.sort, page, limit: 24 });
  if (state.view) params.set('view', state.view);
  if (!append) $('#cardGrid').innerHTML = '<div class="loading">正在加载内容…</div>';
  $('#loadMore').disabled = true;
  try { const data = await api(`/api/items?${params}`); if (request !== listRequest) return; state.items = append ? [...state.items, ...data.items] : data.items; state.page = page; state.hasMore = data.hasMore; renderCards(); $('#loadMore').classList.toggle('hidden', !data.hasMore); }
  catch (error) { if (request === listRequest) { if (!append) $('#cardGrid').innerHTML = `<div class="empty">${esc(error.message)}</div>`; else toast(error.message); } }
  finally { if (request === listRequest) $('#loadMore').disabled = false; }
}
function authModal(mode = 'login') {
  const login = mode === 'login';
  modal(`<h2 id="modalTitle">${login ? '欢迎回来' : '创建账号'}</h2><p class="modal-lead">${login ? '登录后继续收藏和探索你的故事。' : '注册后即可创作、收藏和每日签到。'}</p><form id="authForm" data-mode="${mode}"><label class="form-field">昵称<input name="name" minlength="2" maxlength="24" required autocomplete="username" placeholder="输入昵称"></label><label class="form-field">密码<input name="password" type="password" minlength="${login ? 1 : 8}" required autocomplete="${login ? 'current-password' : 'new-password'}" placeholder="${login ? '输入密码' : '至少 8 位'}"></label><button class="form-submit">${login ? '登录' : '注册并开始探索'}</button></form><div class="switch-auth">${login ? '还没有账号？' : '已有账号？'} <button class="text-link" data-auth="${login ? 'register' : 'login'}">${login ? '立即注册' : '立即登录'}</button></div>`);
}
function needLogin() { if (state.user) return false; authModal('login'); toast('请先登录'); return true; }
async function doAuth(form) {
  const mode = form.dataset.mode, values = Object.fromEntries(new FormData(form));
  try { const data = await api(`/api/auth/${mode}`, { method: 'POST', body: JSON.stringify(values) }); state.user = data.user; updateProfile(); closeModal(); await loadItems(); toast(mode === 'login' ? '登录成功' : '注册成功，已赠送 20 积分'); } catch (error) { toast(error.message); }
}
async function createModal() {
  if (needLogin()) return;
  try {
    const data = await api('/api/me/items');
    modal(`<h2 id="modalTitle">我的角色卡</h2><p class="modal-lead">创建角色与世界观，设定开场白，再用 CSS 设计独特的展示效果。</p><button class="form-submit" id="newCardButton">＋ 新建角色卡</button><div class="my-card-list">${data.items.length ? data.items.map(item => `<button class="my-card-row" data-edit-card="${esc(item.id)}"><span class="my-card-icon">${esc(item.icon)}</span><span><strong>${esc(item.title)}</strong><small>${esc(item.category)} · 点击继续编辑</small></span><b>编辑 ↗</b></button>`).join('') : '<p>还没有发布角色卡。点击上方按钮开始创作。</p>'}</div>`, true);
  } catch (error) { toast(error.message); }
}
function editorField(name, label, value = '', max = 4000, placeholder = '') {
  return `<label class="form-field">${label}<textarea name="${name}" maxlength="${max}" placeholder="${esc(placeholder)}">${esc(value)}</textarea></label>`;
}
function worldEntryRow(entry = {}) {
  return `<div class="world-entry"><div class="world-entry-head"><strong>世界书条目</strong><label><input class="world-enabled" type="checkbox" ${entry.enabled !== false ? 'checked' : ''}> 启用</label><button type="button" data-remove-world>删除</button></div>
    <label class="form-field">名称<input class="world-title" maxlength="80" value="${esc(entry.title || '')}" placeholder="例如：旧王朝"></label>
    <label class="form-field">触发词（逗号分隔；留空则始终生效）<input class="world-keywords" maxlength="200" value="${esc(entry.keywords || '')}" placeholder="旧王朝，皇城"></label>
    <label class="form-field">设定内容<textarea class="world-content" maxlength="3000" placeholder="地点、角色、规则或剧情记忆">${esc(entry.content || '')}</textarea></label></div>`;
}
function cardEditor(item = null) {
  if (needLogin()) return;
  state.editingItemId = item?.id || null;
  const card = item?.card || {};
  modal(`<h2 id="modalTitle">${item ? '编辑角色卡' : '新建角色卡'}</h2><p class="modal-lead">角色设定会参与后续对话；自定义 CSS 只在隔离的卡片和场景中运行。</p>
    <div class="card-editor-layout"><form id="cardForm" class="card-editor-form">
      <fieldset><legend>基础信息</legend><label class="form-field">标题<input name="title" minlength="2" maxlength="40" required value="${esc(item?.title || '')}" placeholder="角色或故事名称"></label>
      <div class="editor-row"><label class="form-field">分区<select name="category">${state.categories.filter(x => x !== '推荐').map(x => `<option ${x === item?.category ? 'selected' : ''}>${esc(x)}</option>`).join('')}</select></label><label class="form-field">符号<input name="icon" maxlength="4" value="${esc(item?.icon || '✨')}"></label></div>
      ${editorField('summary', '简介', item?.summary || '', 240, '10–240 字，介绍角色卡')} </fieldset>
      <fieldset><legend>角色与剧情</legend>${editorField('personality', '人物设定', card.personality, 4000, '性格、身份、说话方式')}${editorField('scenario', '场景与世界观', card.scenario, 4000, '当前地点、关系与事件')}${editorField('firstMessage', '开场白', card.firstMessage, 3000, '新对话中的第一条角色消息')}${editorField('exampleDialogue', '示例对话', card.exampleDialogue, 4000, '帮助模型学习角色的表达方式')}${editorField('quickReplies', '快捷选项（每行一个）', card.quickReplies, 1000, '询问线索\n查看周围\n继续故事')}</fieldset>
      <fieldset><legend>世界书</legend><p class="editor-help">设定会按触发词注入聊天上下文；留空触发词表示每轮生效。最多 30 条。</p><div id="worldEntries">${(card.worldEntries || []).map(worldEntryRow).join('')}</div><button type="button" class="world-add" id="addWorldEntry">＋ 添加设定</button></fieldset>
      <fieldset><legend>视觉样式</legend><label class="form-field">背景图片地址<input name="backgroundUrl" maxlength="500" value="${esc(card.backgroundUrl || '')}" placeholder="HTTPS 地址，或上传图片自动填写"></label><label class="form-field">上传背景图（PNG / JPEG，8 MB 内）<input name="backgroundFile" type="file" accept="image/png,image/jpeg"></label>${editorField('authorCss', '卡片 CSS', card.authorCss, 12000, '.card { border-radius: 24px; }\n.scene:after { content: "✦"; animation: float 3s infinite; }')}<p class="editor-help">可设置 .card、.sigil、.scene 和 .aura；支持渐变、伪元素与 @keyframes 动画。</p></fieldset>
      <button class="form-submit">${item ? '保存角色卡' : '发布角色卡'}</button>
    </form><aside class="editor-preview"><span>实时预览</span><iframe id="cardPreviewFrame" sandbox="" title="角色卡 CSS 预览"></iframe><small>预览在隔离的画布中运行，不影响网站其他界面。</small></aside></div>`, true);
  updateCardPreview();
}
function updateCardPreview() {
  const form = $('#cardForm'); if (!form) return;
  const values = Object.fromEntries(new FormData(form));
  $('#cardPreviewFrame').srcdoc = CardEffects.cardDoc({ title: values.title, summary: values.summary, icon: values.icon, card: values });
}
async function uploadMedia(file) {
  const body = new FormData(); body.append('file', file);
  const response = await fetch('/api/media', { method: 'POST', credentials: 'same-origin', body });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || '图片上传失败');
  return data.url;
}
async function doCardSave(form) {
  const button = form.querySelector('button[type="submit"], .form-submit'); button.disabled = true;
  try {
    const values = Object.fromEntries(new FormData(form));
    const file = values.backgroundFile; delete values.backgroundFile;
    values.worldEntries = [...form.querySelectorAll('.world-entry')].map(row => ({
      title: row.querySelector('.world-title').value.trim(),
      keywords: row.querySelector('.world-keywords').value.trim(),
      content: row.querySelector('.world-content').value.trim(),
      enabled: row.querySelector('.world-enabled').checked
    }));
    if (file instanceof File && file.size) values.backgroundUrl = await uploadMedia(file);
    const id = state.editingItemId;
    const data = await api(id ? `/api/items/${encodeURIComponent(id)}` : '/api/items', { method: id ? 'PUT' : 'POST', body: JSON.stringify(values) });
    closeModal(); state.category = '推荐'; state.sort = 'latest'; state.view = ''; state.query = ''; $('#searchInput').value = '';
    renderCategories(); renderRanking(); await loadItems(); await openItem(data.item.id);
    toast(id ? '角色卡已保存' : '角色卡已发布');
  } catch (error) { toast(error.message); }
  finally { button.disabled = false; }
}
async function favorite(id) {
  if (needLogin()) return;
  try { const data = await api(`/api/items/${encodeURIComponent(id)}/favorite`, { method: 'POST' }); state.user = data.user; state.items = state.items.map(x => x.id === id ? data.item : x); state.featured = state.featured.map(x => x.id === id ? data.item : x); renderCards(); renderFeatured(); if (state.activeItem?.id === id) { state.activeItem = data.item; const button = $('#detailFavorite'); if (button) button.textContent = data.item.favorite ? '♥ 已收藏' : '♡ 收藏'; } toast(data.item.favorite ? '已加入收藏' : '已取消收藏'); } catch (error) { toast(error.message); }
}
async function follow(author) {
  if (needLogin()) return;
  try { const data = await api(`/api/follow/${encodeURIComponent(author)}`, { method: 'POST' }); state.user = data.user; const button = $('#detailFollow'); if (button) button.textContent = data.following ? '✓ 已关注' : '+ 关注作者'; if (state.view === 'following') await loadItems(); toast(data.following ? '已关注作者' : '已取消关注'); } catch (error) { toast(error.message); }
}
function detailHtml(item) {
  const effect = item.card?.authorCss || item.card?.backgroundUrl;
  const cover = effect ? `<iframe class="detail-effect-frame" sandbox="" tabindex="-1" title="作者设计的卡片效果" srcdoc="${esc(CardEffects.cardDoc(item))}"></iframe>` : `<span>${esc(item.icon)}</span>`;
  return `<div class="detail-cover cover-theme-${esc(item.theme)}">${cover}</div><h2 id="modalTitle">${esc(item.title)}</h2><p class="detail-meta">${esc(item.author)} · ${esc(item.category)} · ${number(item.views)} 次浏览 · ${number(item.likes)} 次收藏</p><p class="detail-summary">${esc(item.summary)}</p><div class="detail-actions"><button class="primary" id="startChat">开始聊天</button><button id="detailFavorite">${item.favorite ? '♥ 已收藏' : '♡ 收藏'}</button><button id="detailFollow">${item.following ? '✓ 已关注' : '+ 关注作者'}</button>${item.editable ? '<button id="editCardButton">编辑角色卡</button>' : ''}</div><p class="disclaimer">角色卡设定将用于后续对话；模型状态可在聊天页查看。</p>`;
}
async function openItem(id) {
  try { const data = await api(`/api/items/${encodeURIComponent(id)}/visit`, { method: 'POST' }); state.activeItem = data.item; state.items = state.items.map(x => x.id === id ? data.item : x); renderCards(); modal(detailHtml(data.item), true); } catch (error) { toast(error.message); }
}
async function checkin() {
  if (needLogin()) return;
  try { const data = await api('/api/checkin', { method: 'POST' }); state.user = data.user; updateProfile(); toast(`签到成功，获得 ${data.reward} 积分`); } catch (error) { toast(error.message); }
}
function selectView(view) { if (needLogin()) return; state.view = view; state.category = '推荐'; state.query = ''; $('#searchInput').value = ''; renderCategories(); loadItems(); $('#browse').scrollIntoView({ behavior: 'smooth' }); }
function infoModal(title, content) { modal(`<h2 id="modalTitle">${esc(title)}</h2><p class="modal-lead">${esc(content)}</p><button class="form-submit" id="infoClose">知道了</button>`); }
function handleAction(action) {
  if (action === 'create') return createModal();
  if (action === 'checkin') return checkin();
  if (action === 'invite') return infoModal('有奖邀请', '本地演示版可通过每日签到获得积分；邀请系统需要线上域名和好友账号。');
  if (action === 'chat') return selectView('history');
  if (action === 'novel') { state.category = '剧情'; state.view = ''; renderCategories(); loadItems(); $('#browse').scrollIntoView({ behavior: 'smooth' }); return; }
  const messages = { gift: ['礼包', '礼包活动暂未开放。'], points: ['积分中心', '本地版通过注册和每日签到获得积分。'], media: ['AI生图/视频', '此功能需要接入生成模型；当前项目已实现内容发布和聊天演示。'], app: ['App下载', '本地版可在手机浏览器打开同一局域网地址使用。'], forum: ['论坛', '你可以通过“创作”发布新故事，与其他本地用户分享。'] };
  if (messages[action]) infoModal(...messages[action]);
}
document.addEventListener('click', event => {
  const target = event.target;
  if (target === $('#modalBackdrop') || target.closest('#modalClose') || target.closest('#infoClose')) return closeModal();
  const auth = target.closest('[data-auth]'); if (auth) return authModal(auth.dataset.auth);
  const action = target.closest('[data-action]'); if (action) return handleAction(action.dataset.action);
  const category = target.closest('[data-category]'); if (category) { state.category = category.dataset.category; state.view = ''; renderCategories(); return loadItems(); }
  const rank = target.closest('[data-sort]'); if (rank) { state.sort = rank.dataset.sort; renderRanking(); return loadItems(); }
  const view = target.closest('[data-view]'); if (view) return selectView(view.dataset.view);
  const fav = target.closest('[data-favorite]'); if (fav) return favorite(fav.dataset.favorite);
  const open = target.closest('[data-open]'); if (open) return openItem(open.dataset.open);
  const editCard = target.closest('[data-edit-card]'); if (editCard) return api(`/api/items/${encodeURIComponent(editCard.dataset.editCard)}`).then(data => cardEditor(data.item)).catch(error => toast(error.message));
  if (target.closest('#newCardButton')) return cardEditor();
  if (target.closest('#addWorldEntry')) { const root = $('#worldEntries'); if (root.children.length >= 30) return toast('世界书最多 30 条'); root.insertAdjacentHTML('beforeend', worldEntryRow()); return; }
  if (target.closest('[data-remove-world]')) { target.closest('.world-entry').remove(); return; }
  if (target.closest('#editCardButton')) return cardEditor(state.activeItem);
  if (target.closest('#detailFavorite')) return favorite(state.activeItem.id);
  if (target.closest('#detailFollow')) return follow(state.activeItem.author);
  if (target.closest('#startChat')) { window.location.href = `/zh/explore/installed/${encodeURIComponent(state.activeItem.id)}`; return; }
  if (target.closest('#topLogin') || target.closest('#sideLogin')) return authModal('login');
  if (target.closest('#topRegister') || target.closest('#sideRegister')) return authModal('register');
  if (target.closest('#topAvatar')) return selectView('favorites');
  if (target.closest('#advancedButton')) return $('#advancedPanel').classList.toggle('hidden');
  if (target.closest('#clearFilter')) { state.query = ''; state.category = '推荐'; state.sort = 'recommended'; state.view = ''; $('#searchInput').value = ''; renderCategories(); renderRanking(); return loadItems(); }
  if (target.closest('#heroExplore')) return $('#browse').scrollIntoView({ behavior: 'smooth' });
  if (target.closest('#searchButton')) { state.query = $('#searchInput').value.trim(); state.view = ''; return loadItems(); }
  if (target.closest('#loadMore')) return loadItems(true);
  if (target.closest('#sideCheckin')) return checkin();
  if (target.closest('#inviteCard')) return handleAction('invite');
  if (target.closest('#menuButton')) return $('#sidebar').classList.toggle('open');
  if (target.closest('#logoutButton')) return api('/api/auth/logout', { method: 'POST' }).then(() => { state.user = null; state.view = ''; updateProfile(); loadItems(); toast('已退出登录'); }).catch(error => toast(error.message));
});
document.addEventListener('submit', event => { if (event.target.id === 'authForm') { event.preventDefault(); doAuth(event.target); } if (event.target.id === 'cardForm') { event.preventDefault(); doCardSave(event.target); } });
document.addEventListener('input', event => { if (event.target.closest('#cardForm')) updateCardPreview(); });
$('#searchInput').addEventListener('keydown', event => { if (event.key === 'Enter') { state.query = event.target.value.trim(); state.view = ''; loadItems(); } });
$('#sortSelect').addEventListener('change', event => { state.sort = event.target.value; renderRanking(); loadItems(); });
document.addEventListener('keydown', event => { if (event.key === 'Escape') closeModal(); });
async function init() { try { const data = await api('/api/bootstrap'); state.user = data.user; state.featured = data.featured; state.categories = data.categories; updateProfile(); renderCategories(); renderRanking(); renderFeatured(); await loadItems(); const editId = new URLSearchParams(location.search).get('edit'); if (editId && state.user?.admin) { const detail = await api(`/api/items/${encodeURIComponent(editId)}`); cardEditor(detail.item); } } catch (error) { toast(`服务连接失败：${error.message}`); } }
init();
