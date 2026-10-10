(() => {
    'use strict';
    document.addEventListener('submit', event => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement) || form.method.toLowerCase() !== 'post') return;
        if (form.dataset.submitting === 'true') {
            event.preventDefault();
            return;
        }
        if (form.dataset.confirm && !window.confirm(form.dataset.confirm)) {
            event.preventDefault();
            return;
        }
        form.dataset.submitting = 'true';
        form.setAttribute('aria-busy', 'true');
        // Preserve the clicked submitter's name/value in the native request.
        // Disable only after the browser has constructed the form data.
        setTimeout(() => {
            form.querySelectorAll('button[type="submit"]').forEach(button => {
                button.dataset.originalText = button.textContent;
                button.textContent = '正在提交…';
                button.disabled = true;
            });
        }, 0);
    });
    window.addEventListener('pageshow', () => {
        document.querySelectorAll('form[data-submitting="true"]').forEach(form => {
            delete form.dataset.submitting;
            form.removeAttribute('aria-busy');
            form.querySelectorAll('button[data-original-text]').forEach(button => {
                button.textContent = button.dataset.originalText;
                delete button.dataset.originalText;
                button.disabled = false;
            });
        });
    });
})();
