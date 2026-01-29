/**
 * Constellation Dashboard - DAG Visualizer Component
 *
 * Uses Cytoscape.js with dagre layout for hierarchical DAG visualization.
 */

class DagVisualizer {
    constructor(containerId, onNodeSelect) {
        this.containerId = containerId;
        this.onNodeSelect = onNodeSelect;
        this.cy = null;
        this.layoutDirection = 'TB'; // TB (top-bottom) or LR (left-right)
        this.executionStates = {};

        // Node color mapping
        this.nodeColors = {
            Input: '#56d364',
            Output: '#58a6ff',
            Operation: '#d2a8ff',
            Literal: '#ffa657',
            Merge: '#f778ba',
            Project: '#a5d6ff',
            FieldAccess: '#a5d6ff',
            Conditional: '#ff7b72',
            Guard: '#a5d6ff',
            Branch: '#ff7b72',
            Coalesce: '#ffa657',
            HigherOrder: '#d2a8ff',
            ListLiteral: '#ffa657',
            BooleanOp: '#ff7b72',
            StringInterp: '#ffa657'
        };

        // Status color mapping
        this.statusColors = {
            Pending: '#6e7681',
            Running: '#58a6ff',
            Completed: '#3fb950',
            Failed: '#f85149'
        };
    }

    /**
     * Initialize the Cytoscape instance
     */
    init() {
        // Register dagre layout if available
        if (typeof cytoscape !== 'undefined' && typeof cytoscapeDagre !== 'undefined') {
            cytoscape.use(cytoscapeDagre);
        }

        this.cy = cytoscape({
            container: document.getElementById(this.containerId),
            style: this.getStylesheet(),
            layout: { name: 'preset' },
            minZoom: 0.2,
            maxZoom: 3,
            wheelSensitivity: 0.3,
            boxSelectionEnabled: false
        });

        // Handle node selection
        this.cy.on('tap', 'node', (evt) => {
            const node = evt.target;
            this.selectNode(node);
        });

        // Handle background tap to deselect
        this.cy.on('tap', (evt) => {
            if (evt.target === this.cy) {
                this.deselectAll();
            }
        });

        // Tooltip element
        this.tooltip = document.getElementById('dag-tooltip');

        // Hover tooltip + neighborhood highlighting
        this.cy.on('mouseover', 'node', (evt) => {
            const node = evt.target;

            // Show tooltip
            if (this.tooltip) {
                const kind = node.data('kind') || '';
                const label = node.data('label') || '';
                const typeSig = node.data('typeSignature') || '';
                const value = node.data('value');
                const status = node.data('status') || 'Pending';

                let html = `<span class="tooltip-kind">${kind}</span>`;
                html += `<span class="tooltip-label">${label}</span>`;
                if (typeSig) {
                    html += `<span class="tooltip-type">${typeSig}</span>`;
                }
                if (status !== 'Pending') {
                    html += `<span class="tooltip-status tooltip-status-${status.toLowerCase()}">${status}</span>`;
                }
                if (value !== undefined && value !== null) {
                    const displayVal = typeof value === 'string' ? value : JSON.stringify(value);
                    const truncated = displayVal.length > 120 ? displayVal.slice(0, 120) + '…' : displayVal;
                    html += `<span class="tooltip-value">${truncated}</span>`;
                }

                this.tooltip.innerHTML = html;
                this.tooltip.style.display = 'block';

                const renderedPos = node.renderedPosition();
                const container = this.cy.container().getBoundingClientRect();
                this.tooltip.style.left = (container.left + renderedPos.x + 15) + 'px';
                this.tooltip.style.top = (container.top + renderedPos.y - 15) + 'px';
            }

            // Neighborhood highlighting
            const neighborhood = node.closedNeighborhood();
            this.cy.elements().addClass('dimmed');
            neighborhood.removeClass('dimmed');
        });

        this.cy.on('mouseout', 'node', () => {
            // Hide tooltip
            if (this.tooltip) {
                this.tooltip.style.display = 'none';
            }

            // Restore all elements
            this.cy.elements().removeClass('dimmed');
        });
    }

    /**
     * Get the Cytoscape stylesheet
     */
    getStylesheet() {
        return [
            // Node styles
            {
                selector: 'node',
                style: {
                    'label': 'data(label)',
                    'text-valign': 'center',
                    'text-halign': 'center',
                    'font-size': '12px',
                    'font-family': '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
                    'color': '#0d1117',
                    'text-wrap': 'wrap',
                    'text-max-width': '100px',
                    'width': 'label',
                    'height': 'label',
                    'padding': '12px',
                    'shape': 'roundrectangle',
                    'background-color': 'data(color)',
                    'border-width': 2,
                    'border-color': 'data(borderColor)',
                    'text-outline-color': 'data(color)',
                    'text-outline-width': 0
                }
            },
            // Selected node
            {
                selector: 'node:selected',
                style: {
                    'border-width': 3,
                    'border-color': '#58a6ff',
                    'box-shadow': '0 0 10px #58a6ff'
                }
            },
            // Input nodes
            {
                selector: 'node[kind = "Input"]',
                style: {
                    'shape': 'ellipse'
                }
            },
            // Output nodes
            {
                selector: 'node[kind = "Output"]',
                style: {
                    'shape': 'ellipse'
                }
            },
            // Edge styles
            {
                selector: 'edge',
                style: {
                    'width': 2,
                    'line-color': '#484f58',
                    'target-arrow-color': '#484f58',
                    'target-arrow-shape': 'triangle',
                    'arrow-scale': 1.3,
                    'curve-style': 'bezier',
                    'label': 'data(label)',
                    'font-size': '11px',
                    'color': '#e6edf3',
                    'text-background-color': '#161b22',
                    'text-background-opacity': 0.85,
                    'text-background-padding': '3px',
                    'text-background-shape': 'roundrectangle',
                    'text-rotation': 'autorotate',
                    'text-margin-y': -10
                }
            },
            // Optional edges
            {
                selector: 'edge[kind = "Optional"]',
                style: {
                    'line-style': 'dashed',
                    'line-color': '#6e7681'
                }
            },
            // Control edges
            {
                selector: 'edge[kind = "Control"]',
                style: {
                    'line-color': '#ff7b72',
                    'target-arrow-color': '#ff7b72'
                }
            },
            // Running nodes - blue border + glow
            {
                selector: 'node[status = "Running"]',
                style: {
                    'border-width': 4,
                    'border-color': '#58a6ff',
                    'overlay-color': '#58a6ff',
                    'overlay-opacity': 0.08,
                    'overlay-padding': 6
                }
            },
            // Completed nodes - green border + glow
            {
                selector: 'node[status = "Completed"]',
                style: {
                    'border-width': 4,
                    'border-color': '#3fb950',
                    'overlay-color': '#3fb950',
                    'overlay-opacity': 0.08,
                    'overlay-padding': 6
                }
            },
            // Failed nodes - red border + glow
            {
                selector: 'node[status = "Failed"]',
                style: {
                    'border-width': 4,
                    'border-color': '#f85149',
                    'overlay-color': '#f85149',
                    'overlay-opacity': 0.08,
                    'overlay-padding': 6
                }
            },
            // Dimmed state for non-hovered neighborhood
            {
                selector: '.dimmed',
                style: {
                    'opacity': 0.15
                }
            }
        ];
    }

    /**
     * Load and render a DAG visualization
     */
    render(dagVizIR) {
        if (!this.cy) {
            this.init();
        }

        // Clear existing elements
        this.cy.elements().remove();

        if (!dagVizIR || !dagVizIR.nodes || dagVizIR.nodes.length === 0) {
            return;
        }

        // Convert DagVizIR to Cytoscape elements
        const elements = this.convertToElements(dagVizIR);

        // Add elements
        this.cy.add(elements);

        // Run layout
        this.runLayout();
    }

    /**
     * Convert DagVizIR to Cytoscape elements
     */
    convertToElements(dagVizIR) {
        const elements = [];

        // Add nodes
        dagVizIR.nodes.forEach(node => {
            const color = this.nodeColors[node.kind] || '#8b949e';
            const status = node.executionState?.status || 'Pending';
            const borderColor = this.statusColors[status] || color;

            // Better merge node labels: show "merge" instead of just "+"
            let label = node.label;
            if (node.kind === 'Merge' && label === '+') {
                label = 'merge';
            }

            elements.push({
                group: 'nodes',
                data: {
                    id: node.id,
                    label: label,
                    kind: node.kind,
                    typeSignature: node.typeSignature,
                    color: color,
                    borderColor: borderColor,
                    status: status,
                    value: node.executionState?.value,
                    durationMs: node.executionState?.durationMs,
                    error: node.executionState?.error
                },
                position: node.position ? { x: node.position.x, y: node.position.y } : undefined
            });
        });

        // Add edges
        dagVizIR.edges.forEach(edge => {
            elements.push({
                group: 'edges',
                data: {
                    id: edge.id,
                    source: edge.source,
                    target: edge.target,
                    label: edge.label || '',
                    kind: edge.kind || 'Data'
                }
            });
        });

        return elements;
    }

    /**
     * Run the dagre layout
     */
    runLayout() {
        const layout = this.cy.layout({
            name: 'dagre',
            rankDir: this.layoutDirection,
            nodeSep: 50,
            rankSep: 80,
            edgeSep: 30,
            padding: 30,
            animate: true,
            animationDuration: 300
        });

        layout.run();
    }

    /**
     * Set the layout direction
     */
    setLayoutDirection(direction) {
        this.layoutDirection = direction;
        if (this.cy && this.cy.nodes().length > 0) {
            this.runLayout();
        }
    }

    /**
     * Zoom in
     */
    zoomIn() {
        if (this.cy) {
            this.cy.zoom(this.cy.zoom() * 1.2);
        }
    }

    /**
     * Zoom out
     */
    zoomOut() {
        if (this.cy) {
            this.cy.zoom(this.cy.zoom() / 1.2);
        }
    }

    /**
     * Fit the graph to the viewport
     */
    fit() {
        if (this.cy) {
            this.cy.fit(undefined, 30);
        }
    }

    /**
     * Select a node and show details
     */
    selectNode(node) {
        // Deselect all first
        this.cy.nodes().unselect();
        node.select();

        // Notify callback
        if (this.onNodeSelect) {
            this.onNodeSelect({
                id: node.data('id'),
                label: node.data('label'),
                kind: node.data('kind'),
                typeSignature: node.data('typeSignature'),
                status: node.data('status'),
                value: node.data('value'),
                durationMs: node.data('durationMs'),
                error: node.data('error')
            });
        }
    }

    /**
     * Deselect all nodes
     */
    deselectAll() {
        if (this.cy) {
            this.cy.nodes().unselect();
        }
        if (this.onNodeSelect) {
            this.onNodeSelect(null);
        }
    }

    /**
     * Update execution state for nodes
     */
    updateExecutionState(nodeId, state) {
        this.executionStates[nodeId] = state;

        if (this.cy) {
            const node = this.cy.getElementById(nodeId);
            if (node.length > 0) {
                const status = state.status || 'Pending';
                const borderColor = this.statusColors[status] || '#8b949e';

                node.data('status', status);
                node.data('borderColor', borderColor);
                node.data('value', state.value);
                node.data('durationMs', state.durationMs);
                node.data('error', state.error);
            }
        }
    }

    /**
     * Batch update execution states
     */
    batchUpdateExecutionStates(updates) {
        if (!updates || !Array.isArray(updates)) return;

        this.cy.batch(() => {
            updates.forEach(({ nodeId, state }) => {
                this.updateExecutionState(nodeId, state);
            });
        });
    }

    /**
     * Reset all execution states
     */
    resetExecutionStates() {
        this.executionStates = {};

        if (this.cy) {
            this.cy.batch(() => {
                this.cy.nodes().forEach(node => {
                    const kind = node.data('kind');
                    const color = this.nodeColors[kind] || '#8b949e';

                    node.data('status', 'Pending');
                    node.data('borderColor', color);
                    node.data('value', undefined);
                    node.data('durationMs', undefined);
                    node.data('error', undefined);
                });
            });
        }
    }

    /**
     * Clear the visualization
     */
    clear() {
        if (this.cy) {
            this.cy.elements().remove();
        }
        this.executionStates = {};
    }

    /**
     * Export the DAG as PNG
     */
    exportPng() {
        if (this.cy) {
            const png = this.cy.png({
                output: 'blob',
                bg: '#0d1117',
                scale: 2
            });

            const link = document.createElement('a');
            link.href = URL.createObjectURL(png);
            link.download = 'constellation-dag.png';
            link.click();
        }
    }

    /**
     * Destroy the Cytoscape instance
     */
    destroy() {
        if (this.cy) {
            this.cy.destroy();
            this.cy = null;
        }
    }
}

// Export for use in main.js
window.DagVisualizer = DagVisualizer;
