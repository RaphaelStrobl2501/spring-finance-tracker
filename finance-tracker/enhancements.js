'use strict';
// Load AFTER app.js as a classic deferred script (not type="module").
(() => {
  const workspace = document.getElementById('workspace');
  if (!workspace || typeof api !== 'function') return;
  const originalRender = render;
  const editor = document.createElement('section');
  editor.hidden = true;
  editor.innerHTML = `<h2>Edit transaction</h2><form id="editTransaction">
    <label>Description<input name="description" required></label>
    <div class="grid"><label>Amount<input name="amount" type="number" min="0.00000001" step="any" required></label>
    <label>Date<input name="date" type="date" required></label>
    <label>Category<select name="category" required></select></label></div>
    <p>Transaction type cannot be changed through your update endpoint.</p>
    <div class="actions"><button>Save changes</button><button type="button" id="cancelEdit">Cancel</button></div></form>`;
  workspace.prepend(editor);
  const form = editor.querySelector('form');
  let editingId = null;
  const closeEditor = () => { editor.hidden = true; editingId = null; form.reset(); };
  editor.querySelector('#cancelEdit').onclick = closeEditor;
  // The supplied controller uses integer IDs; enable actions only for an actual id field.
  const validId = id => (typeof id === 'number' && Number.isSafeInteger(id) && id >= 0) ||
    (typeof id === 'string' && /^\d+$/.test(id));
  render = function(rows) {
    originalRender(rows);
    const table = document.querySelector('#transactions table');
    if (!table) return;
    const th = document.createElement('th'); th.scope = 'col'; th.textContent = 'Actions';
    table.tHead.rows[0].append(th);
    rows.forEach((row, index) => {
      const cell = table.tBodies[0].rows[index].insertCell();
      if (!validId(row.id)) { cell.textContent = 'No integer id in response'; return; }
      const edit = document.createElement('button'); edit.type = 'button'; edit.textContent = 'Edit';
      edit.onclick = () => run(async () => {
        const current = await api('/transactions/' + encodeURIComponent(row.id));
        // GET the full current resource before editing; don't guess response mappings.
        if (!current || typeof current.description !== 'string' || typeof current.date !== 'string' ||
            !['number','string'].includes(typeof current.amount) || typeof current.category !== 'string') {
          throw new Error('Editing expects description, amount, date, and category fields. Share your Transaction model to adapt this mapping.');
        }
        const categoryValues = Array.from(document.getElementById('category').options, o => o.value);
        if (!categoryValues.includes(current.category)) categoryValues.push(current.category);
        const select = form.elements.category;
        select.replaceChildren(...categoryValues.map(value => new Option(value, value)));
        form.elements.description.value = current.description;
        form.elements.amount.value = current.amount;
        form.elements.date.value = current.date;
        select.value = current.category;
        editingId = row.id; editor.hidden = false;
        form.elements.description.focus(); editor.scrollIntoView({behavior:'smooth',block:'start'});
      });
      const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = 'Delete';
      remove.style.marginLeft = '8px';
      remove.onclick = () => {
        if (!confirm(`Delete transaction ${row.id}? This cannot be undone.`)) return;
        run(async () => {
          await api('/transactions/' + encodeURIComponent(row.id), {method:'DELETE'});
          if (String(editingId) === String(row.id)) closeEditor();
          message('Transaction deleted.');
          try { await loadTransactions(); } catch(error) { message('Deleted, but refresh failed: ' + error.message, true); }
        });
      };
      cell.append(edit, remove);
    });
  };
  form.onsubmit = event => {
    event.preventDefault();
    if (!validId(editingId)) return;
    const id = editingId;
    run(async () => {
      const amount = Number(form.elements.amount.value);
      const description = form.elements.description.value.trim();
      if (!description || !Number.isFinite(amount) || amount <= 0) throw new Error('Enter a description and a positive amount.');
      await api('/transactions/' + encodeURIComponent(id), {method:'PATCH',body:{
        description, amount, category:form.elements.category.value, date:form.elements.date.value
      }});
      closeEditor(); message('Transaction updated.');
      try { await loadTransactions(); } catch(error) { message('Updated, but refresh failed: ' + error.message, true); }
    });
  };
  const analytics = document.createElement('section');
  analytics.innerHTML = `<h2>Monthly analytics</h2>
    <p>Enter the monthly analytics GET path from your controller. It was not included in the shared code.</p>
    <form id="monthlyAnalytics"><label>Endpoint path<input name="path" placeholder="/your-monthly-analytics-path" required pattern="/[^?#]*"></label>
    <div class="grid"><label>Year<input name="year" type="number" min="2000" max="9999" required></label>
    <label>Month<select name="month"></select></label></div>
    <label>Month parameter format<select name="format"><option value="enum">Enum (JANUARY)</option><option value="number">Number (1–12)</option></select></label>
    <p>Requests use year and month query parameters. Share your analytics controller if yours differs.</p>
    <button>Load monthly analytics</button></form><div id="monthlyResult" aria-live="polite"></div>`;
  workspace.append(analytics);
  const monthlyForm = analytics.querySelector('form');
  const months = ['JANUARY','FEBRUARY','MARCH','APRIL','MAY','JUNE','JULY','AUGUST','SEPTEMBER','OCTOBER','NOVEMBER','DECEMBER'];
  months.forEach((month,index) => monthlyForm.elements.month.add(new Option(month,String(index + 1))));
  const now = new Date(); monthlyForm.elements.year.value = now.getFullYear(); monthlyForm.elements.month.value = String(now.getMonth()+1);
  try {
    monthlyForm.elements.path.value = localStorage.getItem('finance-monthly-path') || '';
    monthlyForm.elements.format.value = localStorage.getItem('finance-monthly-format') || 'enum';
  } catch {}
  const result = analytics.querySelector('#monthlyResult');
  monthlyForm.onsubmit = event => {
    event.preventDefault();
    run(async () => {
      result.replaceChildren();
      const path = monthlyForm.elements.path.value.trim();
      if (!/^\/(?!\/)[^?#]*$/.test(path)) throw new Error('Enter an API path beginning with one slash, without query parameters.');
      const index = Number(monthlyForm.elements.month.value)-1;
      const query = new URLSearchParams({year:monthlyForm.elements.year.value,
        month:monthlyForm.elements.format.value === 'number' ? String(index+1) : months[index]});
      const data = await api(path + '?' + query);
      if (!data || Array.isArray(data) || typeof data !== 'object' ||
          !['totalIncome','totalExpenses','balance'].every(key => data[key] != null)) {
        throw new Error('Expected MonthlyAnalyticsResponse with totalIncome, totalExpenses, and balance. Check the configured path.');
      }
      try { localStorage.setItem('finance-monthly-path',path); localStorage.setItem('finance-monthly-format',monthlyForm.elements.format.value); } catch {}
      const period = document.createElement('p'); period.textContent = `${data.month ?? months[index]} ${data.year ?? monthlyForm.elements.year.value}`;
      result.append(period);
      const list = document.createElement('dl');
      [['Income','totalIncome'],['Expenses','totalExpenses'],['Balance','balance']].forEach(([label,key]) => {
        const term = document.createElement('dt'), value = document.createElement('dd');
        term.textContent = label; value.textContent = String(data[key]); list.append(term,value);
      });
      result.append(list); message('Monthly analytics loaded.');
    });
  };
  // Clear account-specific content on logout, settings change, or next login.
  const clear = () => { closeEditor(); result.replaceChildren(); };
  document.getElementById('logout').addEventListener('click',clear);
  document.getElementById('settings').addEventListener('submit',clear);
  document.getElementById('login').addEventListener('submit',clear);
})();
