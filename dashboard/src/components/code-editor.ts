/**
 * Constellation Dashboard - Code Editor Component
 *
 * Provides an inline code editor for .cst files with live DAG preview.
 * Uses debounced POST /api/v1/preview to compile and update the DAG
 * as the user types.
 */

class CodeEditor {
    private containerId: string;
    private textareaId: string;
    private errorBannerId: string;
    private onPreviewResult: (dagVizIR: DagVizIR | null, errors: string[], inputs: InputParam[]) => void;

    // DOM elements (bound in init)
    private container: HTMLElement | null;
    private textarea: HTMLTextAreaElement | null;
    private highlightPre: HTMLPreElement | null;
    private errorBanner: HTMLElement | null;
    private toggleBtn: HTMLElement | null;
    private newScriptBtn: HTMLElement | null;
    private statusEl: HTMLElement | null;

    // State
    private expanded: boolean;
    private previewTimer: ReturnType<typeof setTimeout> | null;
    private previewCounter: number;
    private readonly DEBOUNCE_MS: number;

    constructor(options: CodeEditorOptions) {
        this.containerId = options.containerId;
        this.textareaId = options.textareaId;
        this.errorBannerId = options.errorBannerId;
        this.onPreviewResult = options.onPreviewResult || (() => {});

        // DOM elements (bound in init)
        this.container = null;
        this.textarea = null;
        this.highlightPre = null;
        this.errorBanner = null;
        this.toggleBtn = null;
        this.newScriptBtn = null;
        this.statusEl = null;

        // State
        this.expanded = true;  // Start expanded by default
        this.previewTimer = null;
        this.previewCounter = 0;
        this.DEBOUNCE_MS = 400;
    }

    /**
     * Bind DOM elements and event listeners
     */
    init(): void {
        this.container = document.getElementById(this.containerId);
        this.textarea = document.getElementById(this.textareaId) as HTMLTextAreaElement | null;
        this.highlightPre = document.getElementById('code-highlight') as HTMLPreElement | null;
        this.errorBanner = document.getElementById(this.errorBannerId);
        this.toggleBtn = document.getElementById('editor-toggle-btn');
        this.newScriptBtn = document.getElementById('new-script-btn');
        this.statusEl = document.getElementById('editor-status');

        if (!this.container || !this.textarea) return;

        // Toggle expand/collapse
        this.toggleBtn?.addEventListener('click', () => this.toggle());

        // New script button
        this.newScriptBtn?.addEventListener('click', () => this.newScript());

        // Live preview on input with syntax highlighting
        this.textarea.addEventListener('input', () => {
            this.updateHighlighting();
            this.schedulePreview();
        });

        // Sync scroll between textarea and highlight overlay
        this.textarea.addEventListener('scroll', () => this.syncScroll());

        // Tab key inserts spaces instead of changing focus
        this.textarea.addEventListener('keydown', (e: KeyboardEvent) => {
            if (e.key === 'Tab') {
                e.preventDefault();
                const ta = this.textarea!;
                const start = ta.selectionStart;
                const end = ta.selectionEnd;
                const value = ta.value;
                ta.value = value.substring(0, start) + '    ' + value.substring(end);
                ta.selectionStart = ta.selectionEnd = start + 4;
                this.updateHighlighting();
                this.schedulePreview();
            }
        });

        // Initial highlighting
        this.updateHighlighting();
    }

    /**
     * Load source code into the editor (e.g. when a file is selected)
     */
    loadSource(source: string): void {
        if (!this.textarea) return;
        this.textarea.value = source || '';
        this.updateHighlighting();
        this.clearErrors();
    }

    /**
     * Expand the editor body
     */
    expand(): void {
        if (!this.container) return;
        this.expanded = true;
        this.container.classList.remove('collapsed');
        this.container.classList.add('expanded');
        if (this.toggleBtn) this.toggleBtn.textContent = 'Hide';
        const split = document.getElementById('editor-dag-split');
        if (split) split.classList.remove('editor-collapsed');
    }

    /**
     * Collapse the editor body
     */
    collapse(): void {
        if (!this.container) return;
        this.expanded = false;
        this.container.classList.remove('expanded');
        this.container.classList.add('collapsed');
        if (this.toggleBtn) this.toggleBtn.textContent = 'Edit';
        const split = document.getElementById('editor-dag-split');
        if (split) split.classList.add('editor-collapsed');
    }

    /**
     * Toggle between expanded and collapsed
     */
    toggle(): void {
        if (this.expanded) {
            this.collapse();
        } else {
            this.expand();
        }
    }

    /**
     * Create a new script with starter template
     */
    newScript(): void {
        const template = '# New script\nin text: String\n\nresult = Uppercase(text)\n\nout result\n';
        this.loadSource(template);
        this.expand();
        this.textarea?.focus();
        this.schedulePreview();
    }

    /**
     * Schedule a debounced preview compilation
     */
    private schedulePreview(): void {
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
    private async executePreview(): Promise<void> {
        const source = this.textarea!.value.trim();

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

            const result: PreviewResponse = await response.json();

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
            this.showErrors([(error as Error).message]);
            this.setStatus('Network error');
            this.onPreviewResult(null, [(error as Error).message], []);
        }
    }

    /**
     * Extract input declarations from dagVizIR nodes.
     * Looks for nodes with kind === 'Input' and maps label -> name, typeSignature -> paramType.
     */
    private extractInputsFromDag(dagVizIR: DagVizIR): InputParam[] {
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
    private showErrors(errors: string[]): void {
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
    clearErrors(): void {
        if (!this.errorBanner) return;
        this.errorBanner.innerHTML = '';
        this.errorBanner.classList.remove('visible');
    }

    /**
     * Update the status indicator text
     */
    private setStatus(text: string): void {
        if (this.statusEl) {
            this.statusEl.textContent = text;
        }
    }

    /**
     * Escape HTML to prevent XSS
     */
    private escapeHtml(text: string): string {
        if (text === null || text === undefined) return '';
        const div = document.createElement('div');
        div.textContent = String(text);
        return div.innerHTML;
    }

    /**
     * Sync scroll position between textarea and highlight overlay
     */
    private syncScroll(): void {
        if (!this.textarea || !this.highlightPre) return;
        this.highlightPre.scrollTop = this.textarea.scrollTop;
        this.highlightPre.scrollLeft = this.textarea.scrollLeft;
    }

    /**
     * Update syntax highlighting in the overlay
     */
    private updateHighlighting(): void {
        if (!this.textarea || !this.highlightPre) return;
        const code = this.textarea.value;
        this.highlightPre.innerHTML = this.highlightCode(code);
    }

    /**
     * Apply syntax highlighting to constellation-lang code
     */
    private highlightCode(code: string): string {
        if (!code) return '';

        // Escape HTML first
        let html = this.escapeHtml(code);

        // Comments (# to end of line)
        html = html.replace(/(#[^\n]*)/g, '<span class="hl-comment">$1</span>');

        // Strings (double-quoted, handling escapes)
        html = html.replace(/(&quot;(?:[^&]|&(?!quot;))*?&quot;)/g, '<span class="hl-string">$1</span>');

        // Annotations (@example, @description, etc.)
        html = html.replace(/(@\w+)/g, '<span class="hl-annotation">$1</span>');

        // Keywords
        const keywords = ['in', 'out', 'type', 'match', 'if', 'else', 'branch', 'otherwise', 'when', 'and', 'or', 'not', 'with', 'use'];
        const keywordPattern = new RegExp(`\\b(${keywords.join('|')})\\b`, 'g');
        html = html.replace(keywordPattern, '<span class="hl-keyword">$1</span>');

        // Built-in types
        const types = ['String', 'Int', 'Float', 'Boolean', 'List', 'Optional', 'Candidates'];
        const typePattern = new RegExp(`\\b(${types.join('|')})\\b`, 'g');
        html = html.replace(typePattern, '<span class="hl-type">$1</span>');

        // Numbers (integers and floats)
        html = html.replace(/\b(\d+\.?\d*)\b/g, '<span class="hl-number">$1</span>');

        // Function calls (PascalCase identifiers followed by parenthesis)
        html = html.replace(/\b([A-Z][a-zA-Z0-9]*)\s*\(/g, '<span class="hl-function">$1</span>(');

        // Operators
        html = html.replace(/(\||\+|->|=&gt;|&lt;|&gt;|==|!=|&lt;=|&gt;=|\?\?)/g, '<span class="hl-operator">$1</span>');

        // Add trailing newline to match textarea behavior
        if (code.endsWith('\n')) {
            html += '\n';
        }

        return html;
    }
}

// Export for use in main.js
(window as Window).CodeEditor = CodeEditor;
