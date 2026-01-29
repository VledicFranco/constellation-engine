/**
 * Constellation Dashboard - Main Application
 *
 * Wires together all components and handles navigation.
 */

class ConstellationDashboard {
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
            scriptsView: document.getElementById('scripts-view'),
            historyView: document.getElementById('history-view'),
            runBtn: document.getElementById('run-btn'),
            refreshBtn: document.getElementById('refresh-btn'),
            currentFile: document.getElementById('current-file'),
            nodeDetails: document.getElementById('node-details'),
            nodeDetailsPanel: document.getElementById('node-details-panel'),
            closeDetails: document.getElementById('close-details'),
            loadingOverlay: document.getElementById('loading-overlay'),
            statusMessage: document.getElementById('status-message'),
            historyFilter: document.getElementById('history-filter'),
            layoutTB: document.getElementById('layout-tb'),
            layoutLR: document.getElementById('layout-lr'),
            zoomIn: document.getElementById('zoom-in'),
            zoomOut: document.getElementById('zoom-out'),
            zoomFit: document.getElementById('zoom-fit')
        };
    }

    /**
     * Initialize the dashboard
     */
    async init() {
        this.showLoading('Initializing...');

        try {
            // Initialize components
            this.initFileBrowser();
            this.initDagVisualizer();
            this.initExecutionPanel();
            this.initCodeEditor();

            // Set up event listeners
            this.setupEventListeners();

            // Load initial data
            await this.fileBrowser.load();

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
    initFileBrowser() {
        this.fileBrowser = new FileBrowser('file-tree', async (path) => {
            await this.loadScript(path);
        });
    }

    /**
     * Initialize the DAG visualizer component
     */
    initDagVisualizer() {
        this.dagVisualizer = new DagVisualizer('dag-canvas', (nodeData) => {
            this.showNodeDetails(nodeData);
        });
        this.dagVisualizer.init();
    }

    /**
     * Initialize the execution panel component
     */
    initExecutionPanel() {
        this.executionPanel = new ExecutionPanel({
            inputsFormId: 'inputs-form',
            outputsDisplayId: 'outputs-display',
            historyListId: 'history-list',
            executionDetailId: 'execution-detail',
            onExecutionSelect: async (execution) => {
                // Load DAG for selected execution
                if (execution.dagVizIR) {
                    this.dagVisualizer.render(execution.dagVizIR);
                }
            }
        });
    }

    /**
     * Initialize the code editor component
     */
    initCodeEditor() {
        this.codeEditor = new CodeEditor({
            containerId: 'editor-container',
            textareaId: 'code-editor-textarea',
            errorBannerId: 'editor-error-banner',
            onPreviewResult: (dagVizIR, errors, inputs) => {
                this.handleLivePreview(dagVizIR, errors, inputs);
            }
        });
        this.codeEditor.init();
    }

    /**
     * Handle live preview results from the code editor
     */
    handleLivePreview(dagVizIR, errors, inputs) {
        if (dagVizIR) {
            this.dagVisualizer.render(dagVizIR);
            this.currentDagVizIR = dagVizIR;
        } else if (!errors || errors.length === 0) {
            // Empty source -- clear the DAG
            this.dagVisualizer.clear();
            this.currentDagVizIR = null;
        }

        if (inputs && inputs.length > 0) {
            const inputsForm = document.getElementById('inputs-form');
            // Save existing input values by name
            const savedValues = {};
            inputsForm.querySelectorAll('[id^="input-"]').forEach(el => {
                const name = el.name;
                if (name) savedValues[name] = el.value;
            });
            // Re-render input form with new inputs
            this.executionPanel.currentInputs = inputs;
            this.executionPanel.renderInputForm(inputsForm, inputs);
            // Restore values for matching input names
            inputs.forEach(input => {
                if (savedValues[input.name] !== undefined) {
                    const el = document.getElementById(`input-${input.name}`);
                    if (el) el.value = savedValues[input.name];
                }
            });
        }
    }

    /**
     * Set up event listeners
     */
    setupEventListeners() {
        // Navigation buttons
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                this.switchView(btn.dataset.view);
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
            this.dagVisualizer.zoomIn();
        });

        this.elements.zoomOut.addEventListener('click', () => {
            this.dagVisualizer.zoomOut();
        });

        this.elements.zoomFit.addEventListener('click', () => {
            this.dagVisualizer.fit();
        });

        // History filter
        let filterTimeout;
        this.elements.historyFilter.addEventListener('input', (e) => {
            clearTimeout(filterTimeout);
            filterTimeout = setTimeout(() => {
                this.executionPanel.loadHistory(e.target.value);
            }, 300);
        });

        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
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
    switchView(view) {
        this.currentView = view;

        // Update nav buttons
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.view === view);
        });

        // Update view visibility
        this.elements.scriptsView.classList.toggle('active', view === 'scripts');
        this.elements.historyView.classList.toggle('active', view === 'history');

        // Load data for view
        if (view === 'history') {
            this.executionPanel.loadHistory();
        }

        // Update URL hash
        window.location.hash = view === 'scripts' ? '' : `/${view}`;
    }

    /**
     * Handle URL hash navigation
     */
    handleHashNavigation() {
        const hash = window.location.hash;

        if (hash.startsWith('#/history')) {
            this.switchView('history');
        } else if (hash.startsWith('#/executions/')) {
            const executionId = hash.replace('#/executions/', '');
            this.switchView('history');
            this.executionPanel.selectExecution(executionId);
        } else {
            this.switchView('scripts');
        }
    }

    /**
     * Load a script
     */
    async loadScript(path) {
        this.showLoading('Loading script...');

        try {
            this.currentScriptPath = path;

            // Update UI
            this.elements.currentFile.textContent = path;
            this.elements.runBtn.disabled = false;

            // Load script metadata
            const scriptData = await this.executionPanel.loadScript(path);

            // Populate code editor with file content
            if (this.codeEditor) {
                this.codeEditor.loadSource(scriptData.content);
            }

            // Try to compile and show initial DAG
            await this.compileAndShowDag(scriptData.content);

            this.setStatus(`Loaded: ${path}`);

        } catch (error) {
            console.error('Error loading script:', error);
            this.setStatus(`Error: ${error.message}`);
        } finally {
            this.hideLoading();
        }
    }

    /**
     * Compile script and show DAG visualization
     */
    async compileAndShowDag(source) {
        try {
            const response = await fetch('/api/v1/preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ source: source })
            });

            const result = await response.json();

            if (result.success && result.dagVizIR) {
                this.dagVisualizer.render(result.dagVizIR);
            } else {
                // Compilation failed, show placeholder
                this.dagVisualizer.clear();
                if (result.errors && result.errors.length > 0) {
                    console.warn('Compilation errors:', result.errors);
                }
            }

        } catch (error) {
            console.error('Error compiling for preview:', error);
            this.dagVisualizer.clear();
        }
    }

    /**
     * Execute the current script
     */
    async executeCurrentScript() {
        if (!this.currentScriptPath) return;

        this.showLoading('Executing...');
        this.setStatus('Executing script...');

        try {
            const result = await this.executionPanel.execute(
                this.currentScriptPath,
                this.dagVisualizer
            );

            if (result.success) {
                this.setStatus('Execution completed');
            } else {
                this.setStatus(`Execution failed: ${result.error || 'Unknown error'}`);
            }

        } catch (error) {
            console.error('Execution error:', error);
            this.setStatus(`Error: ${error.message}`);
        } finally {
            this.hideLoading();
        }
    }

    /**
     * Set layout direction for DAG
     */
    setLayoutDirection(direction) {
        this.elements.layoutTB.classList.toggle('active', direction === 'TB');
        this.elements.layoutLR.classList.toggle('active', direction === 'LR');
        this.dagVisualizer.setLayoutDirection(direction);
    }

    /**
     * Show node details panel
     */
    showNodeDetails(nodeData) {
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
    hideNodeDetails() {
        this.elements.nodeDetails.innerHTML = '<p class="placeholder-text">Click a node to see details</p>';
        this.elements.nodeDetailsPanel.style.display = 'none';
        this.dagVisualizer.deselectAll();
    }

    /**
     * Refresh current view
     */
    async refresh() {
        if (this.currentView === 'scripts') {
            await this.fileBrowser.load();
            if (this.currentScriptPath) {
                await this.loadScript(this.currentScriptPath);
            }
        } else if (this.currentView === 'history') {
            await this.executionPanel.loadHistory(this.elements.historyFilter.value);
        }
    }

    /**
     * Show loading overlay
     */
    showLoading(message = 'Loading...') {
        const loadingText = this.elements.loadingOverlay.querySelector('.loading-text');
        if (loadingText) {
            loadingText.textContent = message;
        }
        this.elements.loadingOverlay.classList.remove('hidden');
    }

    /**
     * Hide loading overlay
     */
    hideLoading() {
        this.elements.loadingOverlay.classList.add('hidden');
    }

    /**
     * Set status bar message
     */
    setStatus(message) {
        this.elements.statusMessage.textContent = message;
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

// Initialize dashboard when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    window.dashboard = new ConstellationDashboard();
    window.dashboard.init();
});
