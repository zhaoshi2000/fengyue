const $ = selector => document.querySelector(selector);
const state = { view: 'overview', query: '', page: 1, total: 0, user: null };
const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const date = value => value ? String(value).replace('T', ' ').slice(0, 16) : '—';
let toastTimer;

async function api(path, options = {}) {
  const response = await fetch(path, { credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, ...options });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || '请求失败');
  return data;
}
function toast(message) {
  const node = $('#toast'); node.textContent = message; node.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => node.hidden = true, 3500);
}
function showAccess(message) {
  $('#accessMessage').textContent = message; $('#accessMessage').hidden = false; $('#adminContent').hidden = true;
}
function selectView(view) {
  state.view = view; state.query = ''; state.page = 1; $('#searchInput').value = '';
  document.querySelectorAll('[data-view]').forEach(node => node.classList.toggle('active', node.dataset.view === view));
  $('#overviewPanel').hidden = view !== 'overview'; $('#listPanel').hidden = view === 'overview';
  const labels = { overview: ['管理总览', '查看内容和社区运行情况'], items: ['作品管理', '查看和管理已发布的角色卡'], users: ['用户管理', '查看注册用户与会话'] };
  $('#pageTitle').textContent = labels[view][0]; $('#pageSubtitle').textContent = labels[view][1];
  if (view !== 'overview') { $('#listTitle').textContent = labels[view][0]; $('#listDescription').textContent = labels[view][1]; $('#searchInput').placeholder = view === 'items' ? '搜索标题或作者' : '搜索用户昵称'; }
  refresh();
}
async function refresh() {
  if (!state.user?.admin) return;
  try { if (state.view === 'overview') await loadOverview(); else await loadList(); }
  catch (error) { toast(error.message); if (error.message.includes('权限') || error.message.includes('登录')) showAccess(error.message); }
}
async function loadOverview() {
  const data = await api('/api/admin/overview');
  const values = [['注册用户', data.users, '♧'], ['已发布作品', data.items, '▤'], ['故事会话', data.conversations, '✦'], ['聊天消息', data.messages, '☷']];
  $('#statsGrid').innerHTML = values.map(([label, count, icon]) => `<div class="stat-card" data-icon="${icon}"><span>${label}</span><strong>${Number(count).toLocaleString('zh-CN')}</strong></div>`).join('');
}
async function loadList() {
  const view = state.view, page = state.page, query = state.query;
  $('#listStatus').textContent = '正在加载…'; $('#tableBody').replaceChildren();
  const data = await api(`/api/admin/${view}?q=${encodeURIComponent(query)}&page=${page}`);
  if (view !== state.view || page !== state.page || query !== state.query) return;
  state.total = data.total;
  $('#listStatus').textContent = `找到 ${data.total} 条记录，每页最多 30 条`;
  $('#tableHead').innerHTML = view === 'items' ? '<tr><th>作品</th><th>作者</th><th>分区</th><th>样式</th><th>浏览 / 收藏</th><th>发布时间</th><th>操作</th></tr>' : '<tr><th>昵称</th><th>身份</th><th>积分</th><th>作品 / 会话</th><th>注册时间</th><th>操作</th></tr>';
  $('#tableBody').innerHTML = view === 'items' ? data.items.map(item => `<tr><td><a class="item-link" target="_blank" rel="noopener" href="/zh/explore/installed/${encodeURIComponent(item.id)}">${esc(item.title)}</a></td><td>${esc(item.author)}</td><td><span class="badge">${esc(item.category)}</span></td><td>${item.customStyle ? '<span class="badge pink">自定义 CSS / 背景</span>' : '<span class="badge">默认</span>'}</td><td>${Number(item.views)} / ${Number(item.likes)}</td><td>${esc(date(item.createdAt))}</td><td><a class="edit-link" href="/?edit=${encodeURIComponent(item.id)}">编辑</a><button class="danger-button" data-delete-item="${esc(item.id)}" data-name="${esc(item.title)}">删除</button></td></tr>`).join('') : data.users.map(user => `<tr><td><strong>${esc(user.name)}</strong></td><td><span class="badge ${user.admin ? 'green' : ''}">${user.admin ? '管理员' : '普通用户'}</span></td><td>${Number(user.points)}</td><td>${Number(user.cards)} / ${Number(user.conversations)}</td><td>${esc(date(user.createdAt))}</td><td><button class="danger-button" data-delete-user="${esc(user.id)}" data-name="${esc(user.name)}" ${user.admin ? 'disabled title="管理员账号不可删除"' : ''}>删除</button></td></tr>`).join('');
  if (!$('#tableBody').children.length) $('#tableBody').innerHTML = `<tr><td colspan="7">暂无记录</td></tr>`;
  $('#pageInfo').textContent = `第 ${state.page} / ${Math.max(1, Math.ceil(data.total / 30))} 页`;
  $('#previousPage').disabled = state.page <= 1; $('#nextPage').disabled = state.page * 30 >= data.total;
}
async function removeRecord(button) {
  const type = button.dataset.deleteItem ? 'items' : 'users';
  const id = button.dataset.deleteItem || button.dataset.deleteUser;
  const name = button.dataset.name;
  if (!confirm(`确定删除“${name}”？${type === 'items' ? '相关会话和消息也会删除。' : '该用户的会话与收藏也会删除。'}此操作无法撤销。`)) return;
  button.disabled = true;
  try { await api(`/api/admin/${type}/${encodeURIComponent(id)}`, { method: 'DELETE' }); toast('已删除'); await loadList(); }
  catch (error) { toast(error.message); button.disabled = false; }
}
async function init() {
  try {
    const data = await api('/api/me'); state.user = data.user;
    if (!data.user) return showAccess('请先在首页登录管理员账号，再打开此页面。');
    if (!data.user.admin) return showAccess('当前账号没有管理员权限。请在本机运行 grant-admin.ps1 授权。');
    $('#adminIdentity').textContent = `已登录：${data.user.name}`;
    $('#adminContent').hidden = false; await loadOverview();
  } catch (error) { showAccess(error.message); }
}
document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => selectView(button.dataset.view)));
$('#refreshButton').addEventListener('click', refresh);
$('#searchForm').addEventListener('submit', event => { event.preventDefault(); state.query = $('#searchInput').value.trim(); state.page = 1; loadList().catch(error => toast(error.message)); });
$('#previousPage').addEventListener('click', () => { if (state.page > 1) { state.page--; refresh(); } });
$('#nextPage').addEventListener('click', () => { if (state.page * 30 < state.total) { state.page++; refresh(); } });
$('#tableBody').addEventListener('click', event => { const button = event.target.closest('[data-delete-item],[data-delete-user]'); if (button) removeRecord(button); });
init();
