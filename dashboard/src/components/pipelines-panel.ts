/**
 * Constellation Dashboard - Pipelines Panel Component
 *
 * Provides pipeline lifecycle management: listing loaded pipelines, version
 * history, reload, canary visualization, and suspended execution management.
 */

class PipelinesPanel {
    private pipelinesListId: string;
    private pipelineDetailId: string;
    private suspendedListId: string;

    private pipelines: PipelineSummary[];
    private selectedPipeline: string | null;
    private expandedVersions: Set<string>;
    private pollInterval: ReturnType<typeof setInterval> | null;

    constructor(options: PipelinesPanelOptions) {
        this.pipelinesListId = options.pipelinesListId;
        this.pipelineDetailId = options.pipelineDetailId;
        this.suspendedListId = options.suspendedListId;

        this.pipelines = [];
        this.selectedPipeline = null;
        this.expandedVersions = new Set();
        this.pollInterval = null;
    }

    /**
     * Load and render the pipeline list
     */
    async loadPipelines(): Promise<void> {
        const container = document.getElementById(this.pipelinesListId)!;

        try {
            const response = await fetch('/pipelines');
            if (!response.ok) throw new Error(`Failed to load pipelines: ${response.statusText}`);

            const data: PipelineListResponse = await response.json();
            this.pipelines = data.pipelines;
            this.renderPipelineList(container);
        } catch (error) {
            console.error('Error loading pipelines:', error);
            container.innerHTML = `
                <div class="placeholder-text">
                    Failed to load pipelines: ${this.escapeHtml((error as Error).message)}
                </div>
            `;
        }
    }

    /**
     * Load and render suspended executions
     */
    async loadSuspended(): Promise<void> {
        const container = document.getElementById(this.suspendedListId)!;

        try {
            const response = await fetch('/executions');
            if (!response.ok) {
                if (response.status === 400) {
                    container.innerHTML = '<div class="placeholder-text">Suspension store not enabled</div>';
                    return;
                }
                throw new Error(`Failed to load executions: ${response.statusText}`);
            }

            const data: SuspendedExecutionListResponse = await response.json();
            this.renderSuspendedList(container, data.executions);
        } catch (error) {
            console.error('Error loading suspended executions:', error);
            container.innerHTML = `
                <div class="placeholder-text">
                    ${this.escapeHtml((error as Error).message)}
                </div>
            `;
        }
    }

    /**
     * Render the pipeline list
     */
    private renderPipelineList(container: HTMLElement): void {
        if (this.pipelines.length === 0) {
            container.innerHTML = `
                <div class="placeholder-text">
                    <p>No pipelines loaded</p>
                    <p>Load a .cst file or use POST /compile to add pipelines</p>
                </div>
            `;
            return;
        }

        container.innerHTML = this.pipelines.map(pipeline => {
            const primaryAlias = pipeline.aliases.length > 0 ? pipeline.aliases[0] : null;
            const hashShort = pipeline.structuralHash.substring(0, 12);
            const aliasDisplay = primaryAlias || hashShort;
            const otherAliases = pipeline.aliases.slice(1);

            return `
                <div class="pipeline-card ${this.selectedPipeline === (primaryAlias || pipeline.structuralHash) ? 'selected' : ''}"
                     data-hash="${pipeline.structuralHash}" data-alias="${primaryAlias || ''}">
                    <div class="pipeline-card-header">
                        <div class="pipeline-name-row">
                            <span class="pipeline-name">${this.escapeHtml(aliasDisplay)}</span>
                            ${otherAliases.length > 0 ? `<span class="pipeline-aliases">+${otherAliases.length} alias${otherAliases.length > 1 ? 'es' : ''}</span>` : ''}
                        </div>
                        <span class="pipeline-hash" title="${pipeline.structuralHash}">${hashShort}</span>
                    </div>
                    <div class="pipeline-card-meta">
                        <span class="pipeline-meta-item">${pipeline.moduleCount} modules</span>
                        <span class="pipeline-meta-item">${pipeline.declaredOutputs.length} outputs</span>
                        <span class="pipeline-meta-item">${this.formatDate(pipeline.compiledAt)}</span>
                    </div>
                    <div class="pipeline-card-actions">
                        ${primaryAlias ? `
                            <button class="pipeline-btn pipeline-btn-primary" data-action="execute" data-ref="${this.escapeHtml(primaryAlias)}">Execute</button>
                            <button class="pipeline-btn" data-action="reload" data-name="${this.escapeHtml(primaryAlias)}">Reload</button>
                            <button class="pipeline-btn" data-action="versions" data-name="${this.escapeHtml(primaryAlias)}">Versions</button>
                        ` : ''}
                        <button class="pipeline-btn pipeline-btn-danger" data-action="delete" data-hash="${pipeline.structuralHash}">Delete</button>
                    </div>
                    <div class="pipeline-versions-panel" id="versions-${pipeline.structuralHash}" style="display: none;"></div>
                    <div class="pipeline-canary-panel" id="canary-${primaryAlias || pipeline.structuralHash}" style="display: none;"></div>
                </div>
            `;
        }).join('');

        // Attach action handlers
        container.querySelectorAll('.pipeline-btn').forEach(btn => {
            btn.addEventListener('click', (e: Event) => {
                e.stopPropagation();
                const el = e.currentTarget as HTMLElement;
                const action = el.dataset.action!;
                this.handleAction(action, el);
            });
        });

        // Attach card click to show detail
        container.querySelectorAll('.pipeline-card').forEach(card => {
            card.addEventListener('click', () => {
                const alias = (card as HTMLElement).dataset.alias;
                const hash = (card as HTMLElement).dataset.hash!;
                this.selectPipeline(alias || hash);
            });
        });

        // Check for active canaries
        this.checkCanaries();
    }

    /**
     * Handle pipeline action button clicks
     */
    private async handleAction(action: string, el: HTMLElement): Promise<void> {
        switch (action) {
            case 'execute':
                await this.executePipeline(el.dataset.ref!);
                break;
            case 'reload':
                await this.reloadPipeline(el.dataset.name!);
                break;
            case 'versions':
                await this.toggleVersions(el.dataset.name!);
                break;
            case 'delete':
                await this.deletePipeline(el.dataset.hash!);
                break;
            case 'canary-promote':
                await this.canaryPromote(el.dataset.name!);
                break;
            case 'canary-rollback':
                await this.canaryRollback(el.dataset.name!);
                break;
            case 'resume':
                await this.resumeExecution(el.dataset.id!);
                break;
            case 'delete-execution':
                await this.deleteExecution(el.dataset.id!);
                break;
        }
    }

    /**
     * Select a pipeline and show its details
     */
    private async selectPipeline(ref: string): Promise<void> {
        this.selectedPipeline = ref;
        const detailContainer = document.getElementById(this.pipelineDetailId)!;

        // Update selection UI
        const listContainer = document.getElementById(this.pipelinesListId)!;
        listContainer.querySelectorAll('.pipeline-card').forEach(card => {
            const alias = (card as HTMLElement).dataset.alias;
            const hash = (card as HTMLElement).dataset.hash;
            card.classList.toggle('selected', alias === ref || hash === ref);
        });

        try {
            const response = await fetch(`/pipelines/${encodeURIComponent(ref)}`);
            if (!response.ok) throw new Error(`Failed to load pipeline: ${response.statusText}`);

            const detail: PipelineDetailResponse = await response.json();
            this.renderPipelineDetail(detailContainer, detail);
        } catch (error) {
            console.error('Error loading pipeline detail:', error);
            detailContainer.innerHTML = `
                <div class="output-error">
                    Failed to load pipeline: ${this.escapeHtml((error as Error).message)}
                </div>
            `;
        }
    }

    /**
     * Render pipeline detail panel
     */
    private renderPipelineDetail(container: HTMLElement, detail: PipelineDetailResponse): void {
        const inputEntries = Object.entries(detail.inputSchema);
        const outputEntries = Object.entries(detail.outputSchema);

        container.innerHTML = `
            <div class="pipeline-detail-info">
                <div class="detail-section">
                    <h4>Structural Hash</h4>
                    <div class="value">${this.escapeHtml(detail.structuralHash)}</div>
                </div>

                <div class="detail-section">
                    <h4>Aliases</h4>
                    <div class="value">${detail.aliases.length > 0 ? detail.aliases.map(a => this.escapeHtml(a)).join(', ') : 'None'}</div>
                </div>

                <div class="detail-section">
                    <h4>Compiled At</h4>
                    <div class="value">${this.escapeHtml(detail.compiledAt)}</div>
                </div>

                ${inputEntries.length > 0 ? `
                    <div class="detail-section">
                        <h4>Inputs</h4>
                        <div class="schema-list">
                            ${inputEntries.map(([name, type]) => `
                                <div class="schema-item">
                                    <span class="schema-name">${this.escapeHtml(name)}</span>
                                    <span class="schema-type">${this.escapeHtml(type)}</span>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                ` : ''}

                ${outputEntries.length > 0 ? `
                    <div class="detail-section">
                        <h4>Outputs</h4>
                        <div class="schema-list">
                            ${outputEntries.map(([name, type]) => `
                                <div class="schema-item">
                                    <span class="schema-name">${this.escapeHtml(name)}</span>
                                    <span class="schema-type">${this.escapeHtml(type)}</span>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                ` : ''}

                <div class="detail-section">
                    <h4>Modules (${detail.modules.length})</h4>
                    <div class="module-list">
                        ${detail.modules.map(m => `
                            <div class="module-item">
                                <span class="module-name">${this.escapeHtml(m.name)}</span>
                                <span class="module-version">v${this.escapeHtml(m.version)}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Execute a pipeline by reference — shows an inline input form
     */
    private async executePipeline(ref: string): Promise<void> {
        const detailContainer = document.getElementById(this.pipelineDetailId)!;

        // Fetch pipeline detail for input schema
        try {
            const detailResp = await fetch(`/pipelines/${encodeURIComponent(ref)}`);
            if (!detailResp.ok) throw new Error('Failed to load pipeline details');
            const detail: PipelineDetailResponse = await detailResp.json();

            const inputEntries = Object.entries(detail.inputSchema);
            detailContainer.innerHTML = `
                <div class="pipeline-execute-form">
                    <h3>Execute: ${this.escapeHtml(ref)}</h3>
                    ${inputEntries.length > 0 ? `
                        <div class="execute-inputs">
                            ${inputEntries.map(([name, type]) => `
                                <div class="input-field">
                                    <label for="pipeline-input-${name}">${this.escapeHtml(name)}</label>
                                    ${this.renderInputForType(name, type)}
                                    <span class="type-hint">${this.escapeHtml(type)}</span>
                                </div>
                            `).join('')}
                        </div>
                    ` : '<p class="placeholder-text">This pipeline has no inputs</p>'}
                    <div class="execute-actions">
                        <button class="primary-btn" id="pipeline-execute-btn">
                            <svg viewBox="0 0 24 24" width="16" height="16">
                                <path fill="currentColor" d="M8 5v14l11-7z"/>
                            </svg>
                            Execute
                        </button>
                        <button class="pipeline-btn" id="pipeline-execute-cancel">Cancel</button>
                    </div>
                    <div id="pipeline-execute-result"></div>
                </div>
            `;

            // Wire up execute button
            document.getElementById('pipeline-execute-btn')!.addEventListener('click', async () => {
                const inputs = this.collectInputs(detail.inputSchema);
                await this.runExecution(ref, inputs);
            });

            // Wire up cancel button
            document.getElementById('pipeline-execute-cancel')!.addEventListener('click', () => {
                this.selectPipeline(ref);
            });
        } catch (error) {
            detailContainer.innerHTML = `
                <div class="output-error">
                    ${this.escapeHtml((error as Error).message)}
                </div>
            `;
        }
    }

    /**
     * Run the actual execution call and display results
     */
    private async runExecution(ref: string, inputs: Record<string, unknown>): Promise<void> {
        const resultContainer = document.getElementById('pipeline-execute-result')!;
        resultContainer.innerHTML = `
            <div class="loading-indicator">
                <div class="spinner" style="width: 20px; height: 20px;"></div>
                <span>Executing...</span>
            </div>
        `;

        try {
            const response = await fetch('/execute', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ref, inputs })
            });

            const result: CoreExecuteResponse = await response.json();

            if (result.status === 'suspended') {
                resultContainer.innerHTML = `
                    <div class="output-suspended">
                        <div class="output-header">
                            <span class="status-badge suspended">Suspended</span>
                            ${result.executionId ? `<span class="execution-id">${result.executionId}</span>` : ''}
                        </div>
                        ${result.missingInputs ? `
                            <div class="missing-inputs">
                                <h4>Missing Inputs</h4>
                                ${Object.entries(result.missingInputs).map(([name, type]) => `
                                    <div class="schema-item">
                                        <span class="schema-name">${this.escapeHtml(name)}</span>
                                        <span class="schema-type">${this.escapeHtml(type)}</span>
                                    </div>
                                `).join('')}
                            </div>
                        ` : ''}
                        ${Object.keys(result.outputs).length > 0 ? `
                            <div class="partial-outputs">
                                <h4>Partial Outputs</h4>
                                <pre class="output-json">${this.escapeHtml(JSON.stringify(result.outputs, null, 2))}</pre>
                            </div>
                        ` : ''}
                    </div>
                `;
                // Refresh suspended list
                this.loadSuspended();
            } else if (result.success) {
                resultContainer.innerHTML = `
                    <div class="output-success">
                        <div class="output-header">
                            <span class="status-badge completed">Completed</span>
                        </div>
                        <pre class="output-json">${this.escapeHtml(JSON.stringify(result.outputs, null, 2))}</pre>
                    </div>
                `;
            } else {
                resultContainer.innerHTML = `
                    <div class="output-error">
                        <div class="output-header">
                            <span class="status-badge failed">Failed</span>
                        </div>
                        <pre>${this.escapeHtml(result.error || 'Execution failed')}</pre>
                    </div>
                `;
            }
        } catch (error) {
            resultContainer.innerHTML = `
                <div class="output-error">
                    ${this.escapeHtml((error as Error).message)}
                </div>
            `;
        }
    }

    /**
     * Reload a pipeline
     */
    private async reloadPipeline(name: string): Promise<void> {
        try {
            const response = await fetch(`/pipelines/${encodeURIComponent(name)}/reload`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({})
            });

            const result: ReloadResponse = await response.json();
            if (result.success) {
                await this.loadPipelines();
                if (result.canary) {
                    this.showCanary(name, result.canary);
                }
            }
        } catch (error) {
            console.error('Error reloading pipeline:', error);
        }
    }

    /**
     * Toggle version history for a pipeline
     */
    private async toggleVersions(name: string): Promise<void> {
        // Find the pipeline card and its versions panel
        const listContainer = document.getElementById(this.pipelinesListId)!;
        const card = listContainer.querySelector(`.pipeline-card[data-alias="${name}"]`);
        if (!card) return;

        const hash = (card as HTMLElement).dataset.hash!;
        const versionsPanel = document.getElementById(`versions-${hash}`)!;

        if (this.expandedVersions.has(name)) {
            versionsPanel.style.display = 'none';
            this.expandedVersions.delete(name);
            return;
        }

        try {
            const response = await fetch(`/pipelines/${encodeURIComponent(name)}/versions`);
            if (!response.ok) {
                if (response.status === 400) {
                    versionsPanel.innerHTML = '<div class="placeholder-text">Versioning not enabled</div>';
                    versionsPanel.style.display = 'block';
                    this.expandedVersions.add(name);
                    return;
                }
                throw new Error('Failed to load versions');
            }

            const data: PipelineVersionsResponse = await response.json();
            this.renderVersionHistory(versionsPanel, name, data);
            versionsPanel.style.display = 'block';
            this.expandedVersions.add(name);
        } catch (error) {
            console.error('Error loading versions:', error);
            versionsPanel.innerHTML = `<div class="output-error">${this.escapeHtml((error as Error).message)}</div>`;
            versionsPanel.style.display = 'block';
            this.expandedVersions.add(name);
        }
    }

    /**
     * Render version history panel
     */
    private renderVersionHistory(container: HTMLElement, name: string, data: PipelineVersionsResponse): void {
        container.innerHTML = `
            <div class="versions-list">
                <h4>Version History</h4>
                ${data.versions.map(v => `
                    <div class="version-item ${v.active ? 'active' : ''}">
                        <div class="version-header">
                            <span class="version-number">v${v.version}</span>
                            <span class="version-hash">${v.structuralHash.substring(0, 12)}</span>
                            ${v.active ? '<span class="version-active-badge">Active</span>' : ''}
                        </div>
                        <div class="version-meta">
                            <span>${this.formatDate(v.createdAt)}</span>
                            ${!v.active ? `<button class="pipeline-btn pipeline-btn-sm" data-action="rollback-version" data-name="${this.escapeHtml(name)}" data-version="${v.version}">Rollback</button>` : ''}
                        </div>
                    </div>
                `).join('')}
            </div>
        `;

        // Attach rollback handlers
        container.querySelectorAll('[data-action="rollback-version"]').forEach(btn => {
            btn.addEventListener('click', async (e: Event) => {
                e.stopPropagation();
                const el = e.currentTarget as HTMLElement;
                await this.rollbackToVersion(el.dataset.name!, parseInt(el.dataset.version!));
            });
        });
    }

    /**
     * Rollback to a specific version
     */
    private async rollbackToVersion(name: string, version: number): Promise<void> {
        try {
            const response = await fetch(`/pipelines/${encodeURIComponent(name)}/rollback/${version}`, {
                method: 'POST'
            });

            if (response.ok) {
                await this.loadPipelines();
                if (this.selectedPipeline === name) {
                    await this.selectPipeline(name);
                }
            }
        } catch (error) {
            console.error('Error rolling back:', error);
        }
    }

    /**
     * Delete a pipeline
     */
    private async deletePipeline(hash: string): Promise<void> {
        try {
            const response = await fetch(`/pipelines/${hash}`, { method: 'DELETE' });
            if (response.ok) {
                await this.loadPipelines();
                // Clear detail if the deleted pipeline was selected
                const detailContainer = document.getElementById(this.pipelineDetailId)!;
                detailContainer.innerHTML = '<p class="placeholder-text">Select a pipeline to view details</p>';
            }
        } catch (error) {
            console.error('Error deleting pipeline:', error);
        }
    }

    /**
     * Check for active canary deployments on all aliased pipelines.
     * Fetches canary status in parallel using Promise.all for performance.
     */
    private async checkCanaries(): Promise<void> {
        const checks = this.pipelines.flatMap(pipeline =>
            pipeline.aliases.map(alias =>
                fetch(`/pipelines/${encodeURIComponent(alias)}/canary`)
                    .then(async response => {
                        if (response.ok) {
                            const state: CanaryStateResponse = await response.json();
                            this.showCanary(alias, state);
                        }
                    })
                    .catch(() => {
                        // No canary or router not configured — ignore
                    })
            )
        );
        await Promise.all(checks);
    }

    /**
     * Show canary state for a pipeline
     */
    private showCanary(name: string, state: CanaryStateResponse): void {
        const panel = document.getElementById(`canary-${name}`);
        if (!panel) return;

        const weightPct = Math.round(state.currentWeight * 100);
        const oldMetrics = state.metrics.oldVersion;
        const newMetrics = state.metrics.newVersion;
        const oldErrorRate = oldMetrics.requests > 0 ? ((oldMetrics.failures / oldMetrics.requests) * 100).toFixed(1) : '0.0';
        const newErrorRate = newMetrics.requests > 0 ? ((newMetrics.failures / newMetrics.requests) * 100).toFixed(1) : '0.0';

        panel.innerHTML = `
            <div class="canary-status">
                <div class="canary-header">
                    <span class="canary-badge">${this.escapeHtml(state.status)}</span>
                    <span class="canary-info">v${state.oldVersion.version} -> v${state.newVersion.version}</span>
                </div>
                <div class="canary-progress">
                    <div class="canary-progress-bar">
                        <div class="canary-progress-fill" style="width: ${weightPct}%"></div>
                    </div>
                    <span class="canary-progress-label">${weightPct}% (step ${state.currentStep + 1})</span>
                </div>
                <div class="canary-metrics-table">
                    <div class="canary-metrics-header">
                        <span>Metric</span>
                        <span>Old (v${state.oldVersion.version})</span>
                        <span>New (v${state.newVersion.version})</span>
                    </div>
                    <div class="canary-metrics-row">
                        <span>Error rate</span>
                        <span>${oldErrorRate}%</span>
                        <span>${newErrorRate}%</span>
                    </div>
                    <div class="canary-metrics-row">
                        <span>Avg latency</span>
                        <span>${oldMetrics.avgLatencyMs.toFixed(1)}ms</span>
                        <span>${newMetrics.avgLatencyMs.toFixed(1)}ms</span>
                    </div>
                    <div class="canary-metrics-row">
                        <span>P99 latency</span>
                        <span>${oldMetrics.p99LatencyMs.toFixed(1)}ms</span>
                        <span>${newMetrics.p99LatencyMs.toFixed(1)}ms</span>
                    </div>
                    <div class="canary-metrics-row">
                        <span>Requests</span>
                        <span>${oldMetrics.requests}</span>
                        <span>${newMetrics.requests}</span>
                    </div>
                </div>
                ${state.status === 'Observing' ? `
                    <div class="canary-actions">
                        <button class="pipeline-btn pipeline-btn-primary pipeline-btn-sm" data-action="canary-promote" data-name="${this.escapeHtml(name)}">Promote</button>
                        <button class="pipeline-btn pipeline-btn-danger pipeline-btn-sm" data-action="canary-rollback" data-name="${this.escapeHtml(name)}">Rollback</button>
                    </div>
                ` : ''}
            </div>
        `;

        panel.style.display = 'block';

        // Attach action handlers
        panel.querySelectorAll('.pipeline-btn').forEach(btn => {
            btn.addEventListener('click', (e: Event) => {
                e.stopPropagation();
                const el = e.currentTarget as HTMLElement;
                this.handleAction(el.dataset.action!, el);
            });
        });
    }

    /**
     * Promote canary to next step
     */
    private async canaryPromote(name: string): Promise<void> {
        try {
            const response = await fetch(`/pipelines/${encodeURIComponent(name)}/canary/promote`, {
                method: 'POST'
            });
            if (response.ok) {
                const state: CanaryStateResponse = await response.json();
                this.showCanary(name, state);
                if (state.status === 'Complete') {
                    await this.loadPipelines();
                }
            }
        } catch (error) {
            console.error('Error promoting canary:', error);
        }
    }

    /**
     * Rollback canary deployment
     */
    private async canaryRollback(name: string): Promise<void> {
        try {
            const response = await fetch(`/pipelines/${encodeURIComponent(name)}/canary/rollback`, {
                method: 'POST'
            });
            if (response.ok) {
                await this.loadPipelines();
            }
        } catch (error) {
            console.error('Error rolling back canary:', error);
        }
    }

    /**
     * Render the suspended executions list
     */
    private renderSuspendedList(container: HTMLElement, executions: SuspendedExecution[]): void {
        if (executions.length === 0) {
            container.innerHTML = '<div class="placeholder-text">No suspended executions</div>';
            return;
        }

        container.innerHTML = executions.map(exec => `
            <div class="suspended-item" data-id="${exec.executionId}">
                <div class="suspended-header">
                    <span class="execution-id">${exec.executionId.substring(0, 8)}...</span>
                    <span class="status-badge suspended">Suspended</span>
                </div>
                <div class="suspended-meta">
                    <span>Hash: ${exec.structuralHash.substring(0, 12)}</span>
                    <span>Resumptions: ${exec.resumptionCount}</span>
                    <span>${this.formatDate(exec.createdAt)}</span>
                </div>
                <div class="suspended-missing">
                    <span class="missing-label">Missing:</span>
                    ${Object.entries(exec.missingInputs).map(([name, type]) =>
                        `<span class="missing-input">${this.escapeHtml(name)}: ${this.escapeHtml(type)}</span>`
                    ).join('')}
                </div>
                <div class="suspended-actions">
                    <button class="pipeline-btn pipeline-btn-primary pipeline-btn-sm" data-action="resume" data-id="${exec.executionId}">Resume</button>
                    <button class="pipeline-btn pipeline-btn-danger pipeline-btn-sm" data-action="delete-execution" data-id="${exec.executionId}">Delete</button>
                </div>
            </div>
        `).join('');

        // Attach action handlers
        container.querySelectorAll('.pipeline-btn').forEach(btn => {
            btn.addEventListener('click', (e: Event) => {
                e.stopPropagation();
                const el = e.currentTarget as HTMLElement;
                this.handleAction(el.dataset.action!, el);
            });
        });
    }

    /**
     * Resume a suspended execution — show input form in detail panel
     */
    private async resumeExecution(executionId: string): Promise<void> {
        const detailContainer = document.getElementById(this.pipelineDetailId)!;

        // Find the suspended execution in the list to get missing inputs
        const listContainer = document.getElementById(this.suspendedListId)!;
        const item = listContainer.querySelector(`.suspended-item[data-id="${executionId}"]`);
        if (!item) return;

        // Fetch execution details to get missing inputs
        try {
            const response = await fetch(`/executions/${executionId}`);
            if (!response.ok) throw new Error('Failed to load execution');
            const exec: SuspendedExecution = await response.json();

            const missingEntries = Object.entries(exec.missingInputs);

            detailContainer.innerHTML = `
                <div class="pipeline-execute-form">
                    <h3>Resume Execution</h3>
                    <p class="resume-info">Execution ${executionId.substring(0, 8)}... (${exec.resumptionCount} resumptions)</p>
                    ${missingEntries.length > 0 ? `
                        <div class="execute-inputs">
                            ${missingEntries.map(([name, type]) => `
                                <div class="input-field">
                                    <label for="resume-input-${name}">${this.escapeHtml(name)}</label>
                                    ${this.renderInputForType(name, type, 'resume-input')}
                                    <span class="type-hint">${this.escapeHtml(type)}</span>
                                </div>
                            `).join('')}
                        </div>
                    ` : '<p class="placeholder-text">No missing inputs</p>'}
                    <div class="execute-actions">
                        <button class="primary-btn" id="pipeline-resume-btn">Resume</button>
                        <button class="pipeline-btn" id="pipeline-resume-cancel">Cancel</button>
                    </div>
                    <div id="pipeline-execute-result"></div>
                </div>
            `;

            document.getElementById('pipeline-resume-btn')!.addEventListener('click', async () => {
                const additionalInputs: Record<string, unknown> = {};
                missingEntries.forEach(([name, type]) => {
                    const el = document.getElementById(`resume-input-${name}`) as HTMLInputElement | HTMLTextAreaElement | null;
                    if (el && el.value) {
                        additionalInputs[name] = this.parseInputValue(el.value, type);
                    }
                });

                const resultContainer = document.getElementById('pipeline-execute-result')!;
                resultContainer.innerHTML = '<div class="loading-indicator"><div class="spinner" style="width: 20px; height: 20px;"></div><span>Resuming...</span></div>';

                try {
                    const resp = await fetch(`/executions/${executionId}/resume`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ additionalInputs })
                    });
                    const result: CoreExecuteResponse = await resp.json();

                    if (result.status === 'suspended') {
                        resultContainer.innerHTML = `
                            <div class="output-suspended">
                                <div class="output-header"><span class="status-badge suspended">Still Suspended</span></div>
                                ${result.missingInputs ? `<p>Missing: ${Object.keys(result.missingInputs).join(', ')}</p>` : ''}
                            </div>
                        `;
                    } else if (result.success) {
                        resultContainer.innerHTML = `
                            <div class="output-success">
                                <div class="output-header"><span class="status-badge completed">Completed</span></div>
                                <pre class="output-json">${this.escapeHtml(JSON.stringify(result.outputs, null, 2))}</pre>
                            </div>
                        `;
                    } else {
                        resultContainer.innerHTML = `
                            <div class="output-error">
                                <div class="output-header"><span class="status-badge failed">Failed</span></div>
                                <pre>${this.escapeHtml(result.error || 'Resume failed')}</pre>
                            </div>
                        `;
                    }

                    // Refresh suspended list
                    this.loadSuspended();
                } catch (error) {
                    resultContainer.innerHTML = `<div class="output-error">${this.escapeHtml((error as Error).message)}</div>`;
                }
            });

            document.getElementById('pipeline-resume-cancel')!.addEventListener('click', () => {
                detailContainer.innerHTML = '<p class="placeholder-text">Select a pipeline to view details</p>';
            });
        } catch (error) {
            detailContainer.innerHTML = `<div class="output-error">${this.escapeHtml((error as Error).message)}</div>`;
        }
    }

    /**
     * Delete a suspended execution
     */
    private async deleteExecution(executionId: string): Promise<void> {
        try {
            await fetch(`/executions/${executionId}`, { method: 'DELETE' });
            await this.loadSuspended();
        } catch (error) {
            console.error('Error deleting execution:', error);
        }
    }

    /**
     * Load a file from the file browser into the PipelineStore
     */
    async loadFileAsPipeline(path: string): Promise<boolean> {
        try {
            // Fetch file content
            const fileResp = await fetch(`/api/v1/file?path=${encodeURIComponent(path)}`);
            if (!fileResp.ok) throw new Error('Failed to load file');
            const file: FileContentResponse = await fileResp.json();

            // Derive alias from filename (strip extension)
            const name = file.name.replace(/\.cst$/, '');

            // Compile and store
            const compileResp = await fetch('/compile', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ source: file.content, name })
            });

            if (!compileResp.ok) throw new Error('Compilation failed');
            const result = await compileResp.json();
            if (!result.success) throw new Error(result.errors?.join('; ') || 'Compilation failed');

            return true;
        } catch (error) {
            console.error('Error loading file as pipeline:', error);
            return false;
        }
    }

    /**
     * Start auto-polling for canary updates
     */
    startPolling(intervalMs: number = 5000): void {
        this.stopPolling();
        this.pollInterval = setInterval(() => {
            this.checkCanaries();
        }, intervalMs);
    }

    /**
     * Stop auto-polling
     */
    stopPolling(): void {
        if (this.pollInterval) {
            clearInterval(this.pollInterval);
            this.pollInterval = null;
        }
    }

    // ========== Helper methods ==========

    private renderInputForType(name: string, type: string, prefix: string = 'pipeline-input'): string {
        const typeLower = type.toLowerCase();
        if (typeLower.includes('int') || typeLower.includes('long')) {
            return `<input type="number" id="${prefix}-${name}" name="${name}" step="1">`;
        }
        if (typeLower.includes('double') || typeLower.includes('float')) {
            return `<input type="number" id="${prefix}-${name}" name="${name}" step="any">`;
        }
        if (typeLower.includes('bool')) {
            return `<select id="${prefix}-${name}" name="${name}">
                <option value="true">true</option>
                <option value="false">false</option>
            </select>`;
        }
        if (typeLower.includes('string') || typeLower === 'cstring') {
            return `<input type="text" id="${prefix}-${name}" name="${name}" placeholder="Enter text...">`;
        }
        return `<textarea id="${prefix}-${name}" name="${name}" placeholder="Enter JSON value..." rows="2"></textarea>`;
    }

    private collectInputs(schema: Record<string, string>): Record<string, unknown> {
        const inputs: Record<string, unknown> = {};
        for (const [name, type] of Object.entries(schema)) {
            const el = document.getElementById(`pipeline-input-${name}`) as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | null;
            if (el && el.value) {
                inputs[name] = this.parseInputValue(el.value, type);
            }
        }
        return inputs;
    }

    private parseInputValue(value: string, type: string): unknown {
        const typeLower = type.toLowerCase();
        if (typeLower.includes('int') || typeLower.includes('long')) return parseInt(value, 10);
        if (typeLower.includes('double') || typeLower.includes('float')) return parseFloat(value);
        if (typeLower.includes('bool')) return value === 'true';
        if (typeLower.includes('string') || typeLower === 'cstring') return value;
        try { return JSON.parse(value); } catch { return value; }
    }

    private formatDate(isoString: string): string {
        try {
            const date = new Date(isoString);
            return date.toLocaleString();
        } catch {
            return isoString;
        }
    }

    private escapeHtml(text: string): string {
        if (text === null || text === undefined) return '';
        const div = document.createElement('div');
        div.textContent = String(text);
        return div.innerHTML;
    }
}

// Export for use in main.js
(window as Window).PipelinesPanel = PipelinesPanel;
