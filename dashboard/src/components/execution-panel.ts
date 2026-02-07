/**
 * Constellation Dashboard - Execution Panel Component
 *
 * Handles input forms, execution results, and execution history.
 */

import type { DagVisualizer } from './dag-visualizer.js';
import { startExecutionWithVisualization } from '../hooks/useExecutionVisualization.js';

class ExecutionPanel {
    private inputsFormId: string;
    private outputsDisplayId: string;
    private historyListId: string;
    private executionDetailId: string;

    private onExecutionSelect: ((execution: StoredExecution) => void) | undefined;
    public currentScript: FileContentResponse | null;
    public currentInputs: InputParam[];
    private selectedExecutionId: string | null;

    constructor(options: ExecutionPanelOptions) {
        this.inputsFormId = options.inputsFormId;
        this.outputsDisplayId = options.outputsDisplayId;
        this.historyListId = options.historyListId;
        this.executionDetailId = options.executionDetailId;

        this.onExecutionSelect = options.onExecutionSelect;
        this.currentScript = null;
        this.currentInputs = [];
        this.selectedExecutionId = null;
    }

    /**
     * Load script metadata and render input form
     */
    async loadScript(scriptPath: string): Promise<FileContentResponse> {
        const inputsForm = document.getElementById(this.inputsFormId)!;
        const outputsDisplay = document.getElementById(this.outputsDisplayId)!;

        // Show loading state while fetching script metadata
        inputsForm.innerHTML = `
            <div class="loading-indicator">
                <div class="spinner" style="width: 20px; height: 20px;"></div>
                <span>Loading script...</span>
            </div>
        `;

        try {
            // Use query param endpoint for paths with slashes
            const response = await fetch(`/api/v1/file?path=${encodeURIComponent(scriptPath)}`);
            if (!response.ok) {
                throw new Error(`Failed to load script: ${response.statusText}`);
            }

            const data: FileContentResponse = await response.json();
            this.currentScript = data;
            this.currentInputs = data.inputs || [];

            // Render input form
            this.renderInputForm(inputsForm, data.inputs);

            // Clear outputs
            outputsDisplay.innerHTML = '<p class="placeholder-text">Run the script to see outputs</p>';

            return data;
        } catch (error) {
            console.error('Error loading script:', error);
            inputsForm.innerHTML = `
                <div class="output-error">
                    Failed to load script: ${this.escapeHtml((error as Error).message)}
                </div>
            `;
            throw error;
        }
    }

    /**
     * Render the input form
     */
    renderInputForm(container: HTMLElement, inputs: InputParam[]): void {
        if (!inputs || inputs.length === 0) {
            container.innerHTML = '<p class="placeholder-text">This script has no inputs</p>';
            return;
        }

        container.innerHTML = inputs.map(input => `
            <div class="input-field">
                <label for="input-${input.name}">
                    ${this.escapeHtml(input.name)}
                    ${input.required ? '' : '<span class="optional">(optional)</span>'}
                </label>
                ${this.renderInputControl(input)}
                <span class="type-hint">${this.escapeHtml(input.paramType)}</span>
            </div>
        `).join('');
    }

    /**
     * Render an input control based on type
     */
    private renderInputControl(input: InputParam): string {
        const type = input.paramType.toLowerCase();
        // Handle null, undefined, and actual values
        const rawDefault = input.defaultValue;
        const defaultValue = (rawDefault !== undefined && rawDefault !== null)
            ? (typeof rawDefault === 'object'
                ? JSON.stringify(rawDefault)
                : String(rawDefault))
            : '';

        if (type === 'int' || type === 'integer' || type === 'long') {
            return `<input type="number" id="input-${input.name}" name="${input.name}"
                    value="${this.escapeHtml(defaultValue)}" step="1">`;
        }

        if (type === 'double' || type === 'float' || type === 'number') {
            return `<input type="number" id="input-${input.name}" name="${input.name}"
                    value="${this.escapeHtml(defaultValue)}" step="any">`;
        }

        if (type === 'boolean' || type === 'bool') {
            return `
                <select id="input-${input.name}" name="${input.name}">
                    <option value="true" ${defaultValue === 'true' ? 'selected' : ''}>true</option>
                    <option value="false" ${defaultValue === 'false' ? 'selected' : ''}>false</option>
                </select>
            `;
        }

        if (type === 'string') {
            return `<input type="text" id="input-${input.name}" name="${input.name}"
                    value="${this.escapeHtml(defaultValue)}" placeholder="Enter text...">`;
        }

        // For complex types (records, lists), use textarea with JSON
        return `<textarea id="input-${input.name}" name="${input.name}"
                placeholder="Enter JSON value..." rows="3">${this.escapeHtml(defaultValue)}</textarea>`;
    }

    /**
     * Get input values from the form
     */
    getInputValues(): Record<string, unknown> {
        const values: Record<string, unknown> = {};

        this.currentInputs.forEach(input => {
            const element = document.getElementById(`input-${input.name}`) as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | null;
            if (!element) return;

            const type = input.paramType.toLowerCase();
            const value = element.value;

            try {
                if (type === 'int' || type === 'integer' || type === 'long') {
                    values[input.name] = parseInt(value, 10);
                } else if (type === 'double' || type === 'float' || type === 'number') {
                    values[input.name] = parseFloat(value);
                } else if (type === 'boolean' || type === 'bool') {
                    values[input.name] = value === 'true';
                } else if (type === 'string') {
                    values[input.name] = value;
                } else {
                    // Try to parse as JSON for complex types
                    values[input.name] = value ? JSON.parse(value) : null;
                }
            } catch (e) {
                // If JSON parsing fails, use raw string
                values[input.name] = value;
            }
        });

        return values;
    }

    /**
     * Load input values from a preset
     */
    loadInputValues(values: Record<string, unknown>): void {
        this.currentInputs.forEach(input => {
            const element = document.getElementById(`input-${input.name}`) as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | null;
            if (!element) return;

            const value = values[input.name];
            if (value === undefined || value === null) return;

            const type = input.paramType.toLowerCase();

            if (type === 'boolean' || type === 'bool') {
                element.value = String(value);
            } else if (typeof value === 'object') {
                element.value = JSON.stringify(value);
            } else {
                element.value = String(value);
            }
        });
    }

    // Track cleanup function for live visualization
    private cleanupVisualization: (() => void) | null = null;

    /**
     * Execute the current script
     */
    async execute(scriptPath: string, dagVisualizer: DagVisualizer): Promise<DashboardExecuteResponse> {
        const outputsDisplay = document.getElementById(this.outputsDisplayId)!;
        const inputs = this.getInputValues();

        // Cleanup any previous visualization subscription
        if (this.cleanupVisualization) {
            this.cleanupVisualization();
            this.cleanupVisualization = null;
        }

        outputsDisplay.innerHTML = `
            <div class="loading-indicator">
                <div class="spinner" style="width: 24px; height: 24px;"></div>
                <span>Executing...</span>
            </div>
        `;

        try {
            const response = await fetch('/api/v1/execute', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    scriptPath: scriptPath,
                    inputs: inputs,
                    source: 'dashboard'
                })
            });

            const result: DashboardExecuteResponse = await response.json();

            if (result.success) {
                // Start live visualization if we have an execution ID
                if (result.executionId && dagVisualizer) {
                    this.cleanupVisualization = startExecutionWithVisualization(
                        result.executionId,
                        dagVisualizer
                    );
                }

                this.displaySuccess(outputsDisplay, result);

                // Update DAG with execution state if available
                if (result.executionId && dagVisualizer) {
                    await this.loadExecutionDag(result.executionId, dagVisualizer);
                }
            } else {
                this.displayError(outputsDisplay, result.error || 'Execution failed');
            }

            return result;
        } catch (error) {
            console.error('Execution error:', error);
            this.displayError(outputsDisplay, (error as Error).message);
            throw error;
        }
    }

    /**
     * Display successful execution result
     */
    private displaySuccess(container: HTMLElement, result: DashboardExecuteResponse): void {
        const duration = result.durationMs ? `${result.durationMs}ms` : '';

        container.innerHTML = `
            <div class="output-success">
                <div class="output-header">
                    <span class="status-badge completed">Completed</span>
                    ${duration ? `<span class="duration">${duration}</span>` : ''}
                </div>
                <pre class="output-json">${this.escapeHtml(JSON.stringify(result.outputs, null, 2))}</pre>
            </div>
        `;
    }

    /**
     * Display execution error
     */
    private displayError(container: HTMLElement, errorMessage: string): void {
        container.innerHTML = `
            <div class="output-error">
                <div class="output-header">
                    <span class="status-badge failed">Failed</span>
                </div>
                <pre>${this.escapeHtml(errorMessage)}</pre>
            </div>
        `;
    }

    /**
     * Load execution DAG for visualization
     */
    private async loadExecutionDag(executionId: string, dagVisualizer: DagVisualizer): Promise<void> {
        try {
            const response = await fetch(`/api/v1/executions/${executionId}/dag`);
            if (response.ok) {
                const dagVizIR: DagVizIR = await response.json();
                dagVisualizer.render(dagVizIR);
            }
        } catch (error) {
            console.error('Error loading execution DAG:', error);
        }
    }

    /**
     * Load execution history
     */
    async loadHistory(filter: string = '', limit: number = 50): Promise<void> {
        const historyList = document.getElementById(this.historyListId)!;

        try {
            let url = `/api/v1/executions?limit=${limit}`;
            if (filter) {
                url += `&script=${encodeURIComponent(filter)}`;
            }

            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`Failed to load history: ${response.statusText}`);
            }

            const data: ExecutionListResponse = await response.json();
            this.renderHistoryList(historyList, data.executions);

        } catch (error) {
            console.error('Error loading history:', error);
            historyList.innerHTML = `
                <div class="output-error">
                    Failed to load history: ${this.escapeHtml((error as Error).message)}
                </div>
            `;
        }
    }

    /**
     * Render the execution history list
     */
    private renderHistoryList(container: HTMLElement, executions: ExecutionSummary[]): void {
        if (!executions || executions.length === 0) {
            container.innerHTML = `
                <div class="placeholder-text">
                    <p>No execution history</p>
                    <p><a href="#" class="link-to-scripts">Run a script from the Scripts view</a> to see results here.</p>
                </div>
            `;
            const link = container.querySelector('.link-to-scripts');
            if (link) {
                link.addEventListener('click', (e: Event) => {
                    e.preventDefault();
                    const scriptsBtn = document.querySelector('.nav-btn[data-view="scripts"]') as HTMLElement | null;
                    if (scriptsBtn) scriptsBtn.click();
                });
            }
            return;
        }

        container.innerHTML = executions.map(exec => `
            <div class="history-item ${exec.executionId === this.selectedExecutionId ? 'selected' : ''}"
                 data-execution-id="${exec.executionId}">
                <div class="header">
                    <span class="script-name">${this.escapeHtml(exec.scriptPath || exec.dagName)}</span>
                    <span class="status-badge ${exec.status.toLowerCase()}">${exec.status}</span>
                </div>
                <div class="meta">
                    <span class="time">${this.formatTime(exec.startTime)}</span>
                    ${exec.endTime ? `<span class="duration">${exec.endTime - exec.startTime}ms</span>` : ''}
                    <span class="nodes">${exec.nodeCount} nodes</span>
                </div>
            </div>
        `).join('');

        // Add click handlers
        container.querySelectorAll('.history-item').forEach(item => {
            item.addEventListener('click', () => {
                this.selectExecution((item as HTMLElement).dataset.executionId!);
            });
        });
    }

    /**
     * Select an execution from history
     */
    async selectExecution(executionId: string): Promise<void> {
        this.selectedExecutionId = executionId;

        // Update selection UI
        const historyList = document.getElementById(this.historyListId)!;
        historyList.querySelectorAll('.history-item').forEach(item => {
            item.classList.toggle('selected', (item as HTMLElement).dataset.executionId === executionId);
        });

        // Load execution details
        const detailContainer = document.getElementById(this.executionDetailId)!;

        try {
            const response = await fetch(`/api/v1/executions/${executionId}`);
            if (!response.ok) {
                throw new Error(`Failed to load execution: ${response.statusText}`);
            }

            const execution: StoredExecution = await response.json();
            this.renderExecutionDetail(detailContainer, execution);

            // Notify callback
            if (this.onExecutionSelect) {
                this.onExecutionSelect(execution);
            }

        } catch (error) {
            console.error('Error loading execution:', error);
            detailContainer.innerHTML = `
                <div class="output-error">
                    Failed to load execution: ${this.escapeHtml((error as Error).message)}
                </div>
            `;
        }
    }

    /**
     * Render execution details
     */
    private renderExecutionDetail(container: HTMLElement, execution: StoredExecution): void {
        const statusClass = execution.status.toLowerCase();
        const duration = execution.endTime ? execution.endTime - execution.startTime : null;

        container.innerHTML = `
            <div class="execution-info">
                <div class="detail-section">
                    <h4>Status</h4>
                    <span class="status-badge ${statusClass}">${execution.status}</span>
                    ${duration ? `<span class="duration">${duration}ms</span>` : ''}
                </div>

                <div class="detail-section">
                    <h4>Script</h4>
                    <div class="value">${this.escapeHtml(execution.scriptPath || execution.dagName)}</div>
                </div>

                <div class="detail-section">
                    <h4>Time</h4>
                    <div class="value">${this.formatDateTime(execution.startTime)}</div>
                </div>

                <div class="detail-section">
                    <h4>Inputs</h4>
                    <pre class="value">${this.escapeHtml(JSON.stringify(execution.inputs, null, 2))}</pre>
                </div>

                ${execution.outputs ? `
                    <div class="detail-section">
                        <h4>Outputs</h4>
                        <pre class="value output-success">${this.escapeHtml(JSON.stringify(execution.outputs, null, 2))}</pre>
                    </div>
                ` : ''}

                ${execution.error ? `
                    <div class="detail-section">
                        <h4>Error</h4>
                        <pre class="value output-error">${this.escapeHtml(execution.error)}</pre>
                    </div>
                ` : ''}

                <div class="detail-section">
                    <h4>Source</h4>
                    <div class="value">${this.escapeHtml(execution.source)}</div>
                </div>
            </div>
        `;
    }

    /**
     * Format timestamp for display
     */
    private formatTime(timestamp: number): string {
        const date = new Date(timestamp);
        return date.toLocaleTimeString();
    }

    /**
     * Format full datetime
     */
    private formatDateTime(timestamp: number): string {
        const date = new Date(timestamp);
        return date.toLocaleString();
    }

    /**
     * Clear the outputs display
     */
    clearOutputs(): void {
        const outputsDisplay = document.getElementById(this.outputsDisplayId)!;
        outputsDisplay.innerHTML = '<p class="placeholder-text">Run the script to see outputs</p>';
    }

    /**
     * Clear the inputs form
     */
    clearInputs(): void {
        const inputsForm = document.getElementById(this.inputsFormId)!;
        inputsForm.innerHTML = '<p class="placeholder-text">Load a script to see inputs</p>';
        this.currentScript = null;
        this.currentInputs = [];
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

// Export as ES module
export { ExecutionPanel };

// Also assign to window for backward compatibility
(window as Window).ExecutionPanel = ExecutionPanel;
