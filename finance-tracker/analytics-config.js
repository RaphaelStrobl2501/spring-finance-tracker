'use strict';
// Load after enhancements.js. Uses the confirmed AnalyticsController contract.
(() => {
    function configureMonthlyAnalytics() {
        const form = document.getElementById('monthlyAnalytics');
        if (!form) return;
        form.elements.path.value = '/analytics/monthly';
        form.elements.format.value = 'enum';
        // Preserve the existing form handler while hiding now-confirmed API settings.
        form.elements.path.closest('label').hidden = true;
        form.elements.format.closest('label').hidden = true;
        const intro = form.previousElementSibling;
        if (intro && intro.tagName === 'P') {
            intro.textContent = 'Choose a year and month to load income, expenses, and balance.';
        }
        const help = form.querySelector('p');
        if (help) help.textContent = 'Reload analytics after changing transactions to see the latest totals.';
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', configureMonthlyAnalytics, {once:true});
    } else {
        configureMonthlyAnalytics();
    }
})();
