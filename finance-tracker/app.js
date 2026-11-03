'use strict';
const $ = id => document.getElementById(id);
let credentials = null;
let busy = false;
let config = {base: 'http://localhost:8080', types: 'INCOME,EXPENSE'};
try { config = {...config, ...JSON.parse(localStorage.getItem('finance-settings') || '{}')}; } catch {}
$('base').value = config.base; $('types').value = config.types;
const message = (text, error = false) => { $('status').textContent = text; $('status').dataset.error = String(error); };
function options(select, values, all = false) {
  select.replaceChildren();
  if (all) select.add(new Option('All categories', ''));
  values.forEach(v => select.add(new Option(v, v)));
}
function applyTypes() { options($('type'), config.types.split(',').map(v => v.trim()).filter(Boolean)); }
applyTypes();
$('create').elements.date.value = new Date().toLocaleDateString('en-CA');
function authHeader(value) {
  if (value.username.includes(':')) throw new Error('HTTP Basic usernames cannot contain a colon.');
  const bytes = new TextEncoder().encode(`${value.username}:${value.password}`);
  return 'Basic ' + btoa(Array.from(bytes, b => String.fromCharCode(b)).join(''));
}
async function api(path, {method = 'GET', body, auth = credentials} = {}) {
  const headers = {Accept: 'application/json'};
  if (auth) headers.Authorization = authHeader(auth);
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  let response;
  try {
    response = await fetch(config.base.replace(/\/+$/, '') + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'omit', redirect: 'error', signal: AbortSignal.timeout(15000)
    });
  } catch (error) {
    throw new Error(`Cannot reach API: ${error.message}. Check the base URL, backend, CORS, and HTTP Basic security settings.`);
  }
  const raw = await response.text();
  let data = raw;
  try { data = raw ? JSON.parse(raw) : null; } catch {}
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${typeof data === 'string' ? data : JSON.stringify(data)}`.slice(0, 2000));
  return data;
}
async function run(task) {
  if (busy) return;
  busy = true;
  document.querySelectorAll('button').forEach(b => b.disabled = true);
  try { await task(); } catch (error) { message(error.message, true); }
  finally { busy = false; document.querySelectorAll('button').forEach(b => b.disabled = false); }
}
function showSession(active) {
  $('auth').hidden = active; $('workspace').hidden = !active; $('logout').hidden = !active;
}
function render(rows) {
  if (!Array.isArray(rows)) throw new Error('Expected a transaction array from GET /transactions. Check your response model.');
  $('transactions').replaceChildren(); $('count').textContent = `${rows.length} transaction(s)`;
  if (!rows.length) { $('transactions').textContent = 'No transactions for these filters.'; return; }
  // Display actual response fields rather than assuming the unavailable Transaction model.
  const keys = [...new Set(rows.flatMap(row => Object.keys(row || {})))];
  const table = document.createElement('table'), head = table.createTHead().insertRow();
  keys.forEach(key => { const th = document.createElement('th'); th.scope = 'col'; th.textContent = key; head.append(th); });
  const body = table.createTBody();
  rows.forEach(row => { const tr = body.insertRow(); keys.forEach(key => {
    const value = row[key]; tr.insertCell().textContent = value == null ? '—' : typeof value === 'object' ? JSON.stringify(value) : String(value);
  }); });
  $('transactions').append(table);
}
async function loadTransactions() {
  const params = new URLSearchParams();
  if ($('start').value && $('end').value && $('start').value > $('end').value) throw new Error('From date must be before the through date.');
  [['category','filterCategory'], ['startDate','start'], ['endDate','end']].forEach(([key,id]) => { if ($(id).value) params.set(key,$(id).value); });
  const query = params.toString();
  render(await api('/transactions' + (query ? '?' + query : '')));
}
$('settings').onsubmit = event => {
  event.preventDefault();
  try {
    const url = new URL($('base').value.trim());
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) throw new Error('Use an HTTP(S) base URL without credentials, query, or fragment.');
    const types = $('types').value.split(',').map(t => t.trim()).filter(Boolean);
    if (!types.length) throw new Error('Enter at least one transaction type.');
    config = {base:url.href.replace(/\/+$/, ''), types:types.join(',')};
    localStorage.setItem('finance-settings', JSON.stringify(config));
    credentials = null; $('password').value = ''; showSession(false); applyTypes();
    $('transactions').replaceChildren(); message('Settings saved. Log in to this API.');
  } catch(error) { message(error.message, true); }
};
$('login').onsubmit = event => { event.preventDefault(); run(async () => {
  const candidate = {username:$('username').value, password:$('password').value};
  // No /login endpoint: verify Basic credentials against the protected resource.
  const rows = await api('/transactions', {auth:candidate});
  render(rows); credentials = candidate; $('password').value = ''; showSession(true);
  try {
    const categories = await api('/categories');
    if (!Array.isArray(categories) || !categories.every(c => typeof c === 'string')) throw new Error('Expected an array of category enum names.');
    options($('category'), categories); options($('filterCategory'), categories, true);
    message('Logged in. Ready to add transactions.');
  } catch(error) { message(`Logged in, but categories could not load: ${error.message}`, true); }
}); };
$('register').onclick = () => {
  if (!$('login').reportValidity()) return;
  run(async () => {
    await api('/auth/register', {method:'POST', auth:null, body:{username:$('username').value,password:$('password').value}});
    message('Account created. Click Log in to sign in.');
  });
};
$('logout').onclick = () => { credentials = null; $('password').value = ''; showSession(false); $('transactions').replaceChildren(); message('Logged out.'); };
$('create').onsubmit = event => { event.preventDefault(); run(async () => {
  const fields = new FormData($('create'));
  const amount = Number(fields.get('amount'));
  if (!Number.isFinite(amount) || amount <= 0) throw new Error('Amount must be positive.');
  const payload = {description:fields.get('description').trim(), amount, category:fields.get('category'), date:fields.get('date'), type:fields.get('type')};
  if (!payload.description) throw new Error('Enter a description.');
  await api('/transactions', {method:'POST',body:payload});
  $('create').elements.description.value = ''; $('create').elements.amount.value = '';
  message('Transaction created.');
  try { await loadTransactions(); } catch(error) { message(`Transaction created, but refresh failed: ${error.message}`, true); }
}); };
$('refresh').onclick = () => run(async () => { await loadTransactions(); message('Transactions refreshed.'); });
$('filters').onsubmit = event => { event.preventDefault(); run(async () => { await loadTransactions(); message('Filters applied.'); }); };
