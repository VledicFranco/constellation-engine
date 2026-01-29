/**
 * Constellation Dashboard - Code Editor Component
 *
 * Provides an inline code editor for .cst files with live DAG preview.
 * Uses debounced POST /api/v1/preview to compile and update the DAG
 * as the user types.
 */

class CodeEditor {
    constructor(options) {
        this.containerId = options.containerId;
        this.textareaId = options.textareaId;
        this.errorBannerId = options.errorBannerId;
        this.onPreviewResult = options.onPreviewResult || (() => {});

        // DOM elements (bound in init)
        this.container = null;
        this.textarea = null;
        this.errorBanner = null;
        this.toggleBtn = null;
        this.newScriptBtn = null;
        this.statusEl = null;

        // State
        this.expanded = false;
        this.previewTimer = null;
        this.previewCounter = 0;
        this.DEBOUNCE_MS = 400;
    }

    /**
     * Bind DOM elements and event listeners
     */
    init() {
        this.container = document.getElementById(this.containerId);
        this.textarea = document.getElementById(this.textareaId);
        this.errorBanner = document.getElementById(this.errorBannerId);
        this.toggleBtn = document.getElementById('editor-toggle-btn');
        this.newScriptBtn = document.getElementById('new-script-btn');
        this.statusEl = document.getElementById('editor-status');

        if (!this.container || !this.textarea) return;

        // Toggle expand/collapse
        this.toggleBtn.addEventListener('click', () => this.toggle());

        // New script button
        this.newScriptBtn.addEventListener('click', () => this.newScript());

        // Live preview on input
        this.textarea.addEventListener('input', () => this.schedulePreview());

        // Tab key inserts spaces instead of changing focus
        this.textarea.addEventListener('keydown', (e) => {
            if (e.key === 'Tab') {
                e.preventDefault();
                const start = this.textarea.selectionStart;
                const end = this.textarea.selectionEnd;
                const value = this.textarea.value;
                this.textarea.value = value.substring(0, start) + '    ' + value.substring(end);
                this.textarea.selectionStart = this.textarea.selectionEnd = start + 4;
                this.schedulePreview();
            }
        });
    }

    /**
     * Load source code into the editor (e.g. when a file is selected)
     */
    loadSource(source) {
        if (!this.textarea) return;
        this.textarea.value = source || '';
        this.clearErrors();
    }

    /**
     * Expand the editor body
     */
    expand() {
        if (!this.container) return;
        this.expanded = true;
        this.container.classList.remove('collapsed');
        this.container.classList.add('expanded');
        if (this.toggleBtn) this.toggleBtn.textContent = 'Hide';
    }

    /**
     * Collapse the editor body
     */
    collapse() {
        if (!this.container) return;
        this.expanded = false;
        this.container.classList.remove('expanded');
        this.container.classList.add('collapsed');
        if (this.toggleBtn) this.toggleBtn.textContent = 'Edit';
    }

    /**
     * Toggle between expanded and collapsed
     */
    toggle() {
        if (this.expanded) {
            this.collapse();
        } else {
            this.expand();
        }
    }

    /**
     * Create a new script with starter template
     */
    newScript() {
        const template = '# New script\nin text: String\n\nresult = Uppercase(text)\n\nout result\n';
        this.loadSource(template);
        this.expand();
        this.textarea.focus();
        this.schedulePreview();
    }

    /**
     * Schedule a debounced preview compilation
     */
    schedulePreview() {
        if (this.previewTimer) {
            clearTimeout(this.previewTimer);
        }
        this.setStatus('Typing...');
        this.previewTimer = setTimeout(() => this.executePreview(), this.DEBOUNCE_MS);
    }

    /**
     * Execute a preview compilation via POST /api/v1/preview.
     * Uses a monotonic counter to discard stale responses.
     */
    async executePreview() {
        const source = this.textarea.value.trim();

        if (!source) {
            this.clearErrors();
            this.setStatus('');
            this.onPreviewResult(null, [], []);
            return;
        }

        const requestId = ++this.previewCounter;
        this.setStatus('Compiling...');

        try {
            const response = await fetch('/api/v1/preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ source })
            });

            // Discard if a newer request was issued
            if (requestId !== this.previewCounter) return;

            const result = await response.json();

            if (result.success && result.dagVizIR) {
                this.clearErrors();
                this.setStatus('OK');
                const inputs = this.extractInputsFromDag(result.dagVizIR);
                this.onPreviewResult(result.dagVizIR, [], inputs);
            } else {
                const errors = result.errors || [];
                this.showErrors(errors);
                this.setStatus(errors.length > 0 ? `${errors.length} error(s)` : 'Error');
                this.onPreviewResult(null, errors, []);
            }
        } catch (error) {
            if (requestId !== this.previewCounter) return;
            this.showErrors([error.message]);
            this.setStatus('Network error');
            this.onPreviewResult(null, [error.message], []);
        }
    }

    /**
     * Extract input declarations from dagVizIR nodes.
     * Looks for nodes with kind === 'Input' and maps label -> name, typeSignature -> paramType.
     */
    extractInputsFromDag(dagVizIR) {
        if (!dagVizIR || !dagVizIR.nodes) return [];
        return dagVizIR.nodes
            .filter(n => n.kind === 'Input')
            .map(n => ({
                name: n.label,
                paramType: n.typeSignature || 'String',
                required: true
            }));
    }

    /**
     * Display compilation errors in the error banner
     */
    showErrors(errors) {
        if (!this.errorBanner || !errors || errors.length === 0) {
            this.clearErrors();
            return;
        }
        this.errorBanner.innerHTML = errors.map(e =>
            `<div class="editor-error-item">${this.escapeHtml(e)}</div>`
        ).join('');
        this.errorBanner.classList.add('visible');
    }

    /**
     * Clear the error banner
     */
    clearErrors() {
        if (!this.errorBanner) return;
        this.errorBanner.innerHTML = '';
        this.errorBanner.classList.remove('visible');
    }

    /**
     * Update the status indicator text
     */
    setStatus(text) {
        if (this.statusEl) {
            this.statusEl.textContent = text;
        }
    }

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        if (text === null || text === undefined) return '';
        const div = document.createElement('div');
        div.textContent = String(text);
        return div.innerHTML;
    }
}

// Export for use in main.js
window.CodeEditor = CodeEditor;
