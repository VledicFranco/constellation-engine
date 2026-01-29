/**
 * Constellation Dashboard - Main Application
 *
 * Wires together all components and handles navigation.
 */

class ConstellationDashboard {
    // Components
    private fileBrowser: FileBrowser | null;
    private dagVisualizer: DagVisualizer | null;
    private executionPanel: ExecutionPanel | null;
    private codeEditor: CodeEditor | null;

    // State
    private currentView: 'scripts' | 'history';
    private currentScriptPath: string | null;
    private currentDagVizIR: DagVizIR | null;

    // DOM elements
    private elements: DashboardElements;

    constructor() {
        // Components
        this.fileBrowser = null;
        this.dagVisualizer = null;
        this.executionPanel = null;
        this.codeEditor = null;

        // State
        this.currentView = 'scripts';
        this.currentScriptPath = null;
        this.currentDagVizIR = null;

        // DOM elements
        this.elements = {
            scriptsView: document.getElementById('scripts-view')!,
            historyView: document.getElementById('history-view')!,
            runBtn: document.getElementById('run-btn') as HTMLButtonElement,
            refreshBtn: document.getElementById('refresh-btn') as HTMLButtonElement,
            currentFile: document.getElementById('current-file')!,
            nodeDetails: document.getElementById('node-details')!,
            nodeDetailsPanel: document.getElementById('node-details-panel')!,
            closeDetails: document.getElementById('close-details')!,
            loadingOverlay: document.getElementById('loading-overlay')!,
            statusMessage: document.getElementById('status-message')!,
            historyFilter: document.getElementById('history-filter') as HTMLInputElement,
            layoutTB: document.getElementById('layout-tb')!,
            layoutLR: document.getElementById('layout-lr')!,
            zoomIn: document.getElementById('zoom-in')!,
            zoomOut: document.getElementById('zoom-out')!,
            zoomFit: document.getElementById('zoom-fit')!,
            editorDagSplit: document.getElementById('editor-dag-split')!,
            splitHandle: document.getElementById('split-handle')!
        };
    }

    /**
     * Initialize the dashboard
     */
    async init(): Promise<void> {
        this.showLoading('Initializing...');

        try {
            // Initialize components
            this.initFileBrowser();
            this.initDagVisualizer();
            this.initExecutionPanel();
            this.initCodeEditor();
            this.initSplitHandle();

            // Set up event listeners
            this.setupEventListeners();

            // Load initial data
            await this.fileBrowser!.load();

            // Handle hash-based navigation
            this.handleHashNavigation();

            this.hideLoading();
            this.setStatus('Ready');

        } catch (error) {
            console.error('Initialization error:', error);
            this.hideLoading();
            this.setStatus('Initialization failed');
        }
    }

    /**
     * Initialize the file browser component
     */
    private initFileBrowser(): void {
        this.fileBrowser = new FileBrowser('file-tree', async (path: string) => {
            await this.loadScript(path);
        });
    }

    /**
     * Initialize the DAG visualizer component
     */
    private initDagVisualizer(): void {
        this.dagVisualizer = new DagVisualizer('dag-canvas', (nodeData: NodeDetailData | null) => {
            this.showNodeDetails(nodeData);
        });
        this.dagVisualizer.init();
    }

    /**
     * Initialize the execution panel component
     */
    private initExecutionPanel(): void {
        this.executionPanel = new ExecutionPanel({
            inputsFormId: 'inputs-form',
            outputsDisplayId: 'outputs-display',
            historyListId: 'history-list',
            executionDetailId: 'execution-detail',
            onExecutionSelect: async (execution: StoredExecution) => {
                // Load DAG for selected execution
                if (execution.dagVizIR) {
                    this.dagVisualizer!.render(execution.dagVizIR);
                }
            }
        });
    }

    /**
     * Initialize the code editor component
     */
    private initCodeEditor(): void {
        this.codeEditor = new CodeEditor({
            containerId: 'editor-container',
            textareaId: 'code-editor-textarea',
            errorBannerId: 'editor-error-banner',
            onPreviewResult: (dagVizIR: DagVizIR | null, errors: string[], inputs: InputParam[]) => {
                this.handleLivePreview(dagVizIR, errors, inputs);
            }
        });
        this.codeEditor.init();
    }

    /**
     * Initialize the draggable split handle between editor and DAG
     */
    private initSplitHandle(): void {
        const splitHandle = document.getElementById('split-handle')!;
        const splitContainer = document.getElementById('editor-dag-split')!;
        const editorContainer = document.getElementById('editor-container')!;

        let isDragging = false;

        splitHandle.addEventListener('mousedown', (e: MouseEvent) => {
            isDragging = true;
            e.preventDefault();
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';
        });

        document.addEventListener('mousemove', (e: MouseEvent) => {
            if (!isDragging) return;
            const rect = splitContainer.getBoundingClientRect();
            const offset = e.clientX - rect.left;
            const pct = (offset / rect.width) * 100;
            const clamped = Math.max(15, Math.min(85, pct));
            editorContainer.style.width = clamped + '%';
        });

        document.addEventListener('mouseup', () => {
            if (!isDragging) return;
            isDragging = false;
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
            // Notify Cytoscape to recalculate after resize
            this.dagVisualizer?.fit();
        });

        // Refit DAG when editor is toggled (container size changes)
        const toggleBtn = document.getElementById('editor-toggle-btn');
        toggleBtn?.addEventListener('click', () => {
            // Wait for layout reflow before refitting
            requestAnimationFrame(() => {
                requestAnimationFrame(() => {
                    this.dagVisualizer?.fit();
                });
            });
        });
    }

    /**
     * Handle live preview results from the code editor
     */
    private handleLivePreview(dagVizIR: DagVizIR | null, errors: string[], inputs: InputParam[]): void {
        if (dagVizIR) {
            this.dagVisualizer!.render(dagVizIR);
            this.currentDagVizIR = dagVizIR;
        } else if (!errors || errors.length === 0) {
            // Empty source -- clear the DAG
            this.dagVisualizer!.clear();
            this.currentDagVizIR = null;
        }

        if (inputs && inputs.length > 0) {
            const inputsForm = document.getElementById('inputs-form')!;
            // Save existing input values by name
            const savedValues: Record<string, string> = {};
            inputsForm.querySelectorAll('[id^="input-"]').forEach(el => {
                const name = (el as HTMLInputElement).name;
                if (name) savedValues[name] = (el as HTMLInputElement).value;
            });
            // Re-render input form with new inputs
            this.executionPanel!.currentInputs = inputs;
            this.executionPanel!.renderInputForm(inputsForm, inputs);
            // Restore values for matching input names
            inputs.forEach(input => {
                if (savedValues[input.name] !== undefined) {
                    const el = document.getElementById(`input-${input.name}`) as HTMLInputElement | null;
                    if (el) el.value = savedValues[input.name];
                }
            });
        }
    }

    /**
     * Set up event listeners
     */
    private setupEventListeners(): void {
        // Navigation buttons
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                this.switchView((btn as HTMLElement).dataset.view as 'scripts' | 'history');
            });
        });

        // Run button
        this.elements.runBtn.addEventListener('click', () => {
            this.executeCurrentScript();
        });

        // Refresh button
        this.elements.refreshBtn.addEventListener('click', () => {
            this.refresh();
        });

        // Close details button
        this.elements.closeDetails.addEventListener('click', () => {
            this.hideNodeDetails();
        });

        // Layout controls
        this.elements.layoutTB.addEventListener('click', () => {
            this.setLayoutDirection('TB');
        });

        this.elements.layoutLR.addEventListener('click', () => {
            this.setLayoutDirection('LR');
        });

        // Zoom controls
        this.elements.zoomIn.addEventListener('click', () => {
            this.dagVisualizer!.zoomIn();
        });

        this.elements.zoomOut.addEventListener('click', () => {
            this.dagVisualizer!.zoomOut();
        });

        this.elements.zoomFit.addEventListener('click', () => {
            this.dagVisualizer!.fit();
        });

        // History filter
        let filterTimeout: ReturnType<typeof setTimeout>;
        this.elements.historyFilter.addEventListener('input', (e: Event) => {
            clearTimeout(filterTimeout);
            filterTimeout = setTimeout(() => {
                this.executionPanel!.loadHistory((e.target as HTMLInputElement).value);
            }, 300);
        });

        // Keyboard shortcuts
        document.addEventListener('keydown', (e: KeyboardEvent) => {
            // Ctrl/Cmd + Enter to run
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                if (this.currentScriptPath) {
                    this.executeCurrentScript();
                }
            }
        });

        // Hash change
        window.addEventListener('hashchange', () => {
            this.handleHashNavigation();
        });
    }

    /**
     * Switch between views
     */
    private switchView(view: 'scripts' | 'history'): void {
        this.currentView = view;

        // Update nav buttons
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.classList.toggle('active', (btn as HTMLElement).dataset.view === view);
        });

        // Update view visibility
        this.elements.scriptsView.classList.toggle('active', view === 'scripts');
        this.elements.historyView.classList.toggle('active', view === 'history');

        // Load data for view
        if (view === 'history') {
            this.executionPanel!.loadHistory();
        }

        // Update URL hash
        window.location.hash = view === 'scripts' ? '' : `/${view}`;
    }

    /**
     * Handle URL hash navigation
     */
    private handleHashNavigation(): void {
        const hash = window.location.hash;

        if (hash.startsWith('#/history')) {
            this.switchView('history');
        } else if (hash.startsWith('#/executions/')) {
            const executionId = hash.replace('#/executions/', '');
            this.switchView('history');
            this.executionPanel!.selectExecution(executionId);
        } else {
            this.switchView('scripts');
        }
    }

    /**
     * Load a script
     */
    private async loadScript(path: string): Promise<void> {
        this.showLoading('Loading script...');

        try {
            this.currentScriptPath = path;

            // Update UI
            this.elements.currentFile.textContent = path;
            this.elements.runBtn.disabled = false;

            // Load script metadata
            const scriptData = await this.executionPanel!.loadScript(path);

            // Populate code editor with file content
            if (this.codeEditor) {
                this.codeEditor.loadSource(scriptData.content);
            }

            // Try to compile and show initial DAG
            await this.compileAndShowDag(scriptData.content);

            this.setStatus(`Loaded: ${path}`);

        } catch (error) {
            console.error('Error loading script:', error);
            this.setStatus(`Error: ${(error as Error).message}`);
        } finally {
            this.hideLoading();
        }
    }

    /**
     * Compile script and show DAG visualization
     */
    private async compileAndShowDag(source: string): Promise<void> {
        try {
            const response = await fetch('/api/v1/preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ source: source })
            });

            const result: PreviewResponse = await response.json();

            if (result.success && result.dagVizIR) {
                this.dagVisualizer!.render(result.dagVizIR);
            } else {
                // Compilation failed, show placeholder
                this.dagVisualizer!.clear();
                if (result.errors && result.errors.length > 0) {
                    console.warn('Compilation errors:', result.errors);
                }
            }

        } catch (error) {
            console.error('Error compiling for preview:', error);
            this.dagVisualizer!.clear();
        }
    }

    /**
     * Execute the current script
     */
    private async executeCurrentScript(): Promise<void> {
        if (!this.currentScriptPath) return;

        this.showLoading('Executing...');
        this.setStatus('Executing script...');

        try {
            const result = await this.executionPanel!.execute(
                this.currentScriptPath,
                this.dagVisualizer!
            );

            if (result.success) {
                this.setStatus('Execution completed');
            } else {
                this.setStatus(`Execution failed: ${result.error || 'Unknown error'}`);
            }

        } catch (error) {
            console.error('Execution error:', error);
            this.setStatus(`Error: ${(error as Error).message}`);
        } finally {
            this.hideLoading();
        }
    }

    /**
     * Set layout direction for DAG
     */
    private setLayoutDirection(direction: string): void {
        this.elements.layoutTB.classList.toggle('active', direction === 'TB');
        this.elements.layoutLR.classList.toggle('active', direction === 'LR');
        this.dagVisualizer!.setLayoutDirection(direction);
    }

    /**
     * Show node details panel
     */
    private showNodeDetails(nodeData: NodeDetailData | null): void {
        if (!nodeData) {
            this.hideNodeDetails();
            return;
        }

        const kindClass = (nodeData.kind || 'default').toLowerCase();

        this.elements.nodeDetails.innerHTML = `
            <div class="detail-section">
                <h4>Label</h4>
                <div class="value">${this.escapeHtml(nodeData.label)}</div>
            </div>

            <div class="detail-section">
                <h4>Kind</h4>
                <span class="node-kind-badge ${kindClass}">${nodeData.kind}</span>
            </div>

            <div class="detail-section">
                <h4>Type</h4>
                <div class="value">${this.escapeHtml(nodeData.typeSignature)}</div>
            </div>

            ${nodeData.status ? `
                <div class="detail-section">
                    <h4>Status</h4>
                    <span class="status-badge ${nodeData.status.toLowerCase()}">${nodeData.status}</span>
                    ${nodeData.durationMs ? `<span class="duration">${nodeData.durationMs}ms</span>` : ''}
                </div>
            ` : ''}

            ${nodeData.value !== undefined ? `
                <div class="detail-section">
                    <h4>Value</h4>
                    <pre class="value">${this.escapeHtml(JSON.stringify(nodeData.value, null, 2))}</pre>
                </div>
            ` : ''}

            ${nodeData.error ? `
                <div class="detail-section">
                    <h4>Error</h4>
                    <div class="value output-error">${this.escapeHtml(nodeData.error)}</div>
                </div>
            ` : ''}
        `;

        this.elements.nodeDetailsPanel.style.display = 'flex';
    }

    /**
     * Hide node details panel
     */
    private hideNodeDetails(): void {
        this.elements.nodeDetails.innerHTML = '<p class="placeholder-text">Click a node to see details</p>';
        this.elements.nodeDetailsPanel.style.display = 'none';
        this.dagVisualizer!.deselectAll();
    }

    /**
     * Refresh current view
     */
    private async refresh(): Promise<void> {
        if (this.currentView === 'scripts') {
            await this.fileBrowser!.load();
            if (this.currentScriptPath) {
                await this.loadScript(this.currentScriptPath);
            }
        } else if (this.currentView === 'history') {
            await this.executionPanel!.loadHistory(this.elements.historyFilter.value);
        }
    }

    /**
     * Show loading overlay
     */
    private showLoading(message: string = 'Loading...'): void {
        const loadingText = this.elements.loadingOverlay.querySelector('.loading-text');
        if (loadingText) {
            loadingText.textContent = message;
        }
        this.elements.loadingOverlay.classList.remove('hidden');
    }

    /**
     * Hide loading overlay
     */
    private hideLoading(): void {
        this.elements.loadingOverlay.classList.add('hidden');
    }

    /**
     * Set status bar message
     */
    private setStatus(message: string): void {
        this.elements.statusMessage.textContent = message;
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
}

// Initialize dashboard when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    (window as Window).dashboard = new ConstellationDashboard();
    (window as Window).dashboard.init();
});
