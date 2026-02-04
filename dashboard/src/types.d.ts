/**
 * Constellation Dashboard - Shared Type Declarations
 *
 * Mirrors Scala types from DagVizIR.scala, DashboardModels.scala,
 * and ExecutionStorage.scala. Also provides ambient Cytoscape typings
 * for the subset of the API used by the dashboard.
 */

// =============================================================================
// DagVizIR types (from lang-compiler)
// =============================================================================

type NodeKind =
    | 'Input'
    | 'Output'
    | 'Operation'
    | 'Literal'
    | 'Merge'
    | 'Project'
    | 'FieldAccess'
    | 'Conditional'
    | 'Guard'
    | 'Branch'
    | 'Coalesce'
    | 'HigherOrder'
    | 'ListLiteral'
    | 'BooleanOp'
    | 'StringInterp';

type EdgeKind = 'Data' | 'Optional' | 'Control';

type ExecutionStatus = 'Pending' | 'Running' | 'Completed' | 'Failed';

interface Position {
    x: number;
    y: number;
}

interface ExecutionState {
    status: ExecutionStatus;
    value?: unknown;
    durationMs?: number;
    error?: string;
}

interface VizNode {
    id: string;
    kind: NodeKind;
    label: string;
    typeSignature: string;
    position?: Position;
    executionState?: ExecutionState;
}

interface VizEdge {
    id: string;
    source: string;
    target: string;
    label?: string;
    kind: EdgeKind;
}

interface NodeGroup {
    id: string;
    label: string;
    nodeIds: string[];
    collapsed: boolean;
}

interface Bounds {
    minX: number;
    minY: number;
    maxX: number;
    maxY: number;
}

interface VizMetadata {
    title?: string;
    layoutDirection: string;
    bounds?: Bounds;
}

interface DagVizIR {
    nodes: VizNode[];
    edges: VizEdge[];
    groups: NodeGroup[];
    metadata: VizMetadata;
}

// =============================================================================
// Dashboard model types (from http-api)
// =============================================================================

type FileType = 'file' | 'directory';

interface FileNode {
    name: string;
    path: string;
    fileType: FileType;
    size?: number;
    modifiedTime?: number;
    children?: FileNode[];
}

interface FilesResponse {
    root: string;
    files: FileNode[];
}

interface InputParam {
    name: string;
    paramType: string;
    required: boolean;
    defaultValue?: unknown;
}

interface OutputParam {
    name: string;
    paramType: string;
}

interface FileContentResponse {
    path: string;
    name: string;
    content: string;
    inputs: InputParam[];
    outputs: OutputParam[];
    lastModified?: number;
}

interface PreviewResponse {
    success: boolean;
    dagVizIR?: DagVizIR;
    errors: string[];
}

interface DashboardExecuteRequest {
    scriptPath: string;
    inputs: Record<string, unknown>;
    sampleRate?: number;
    source?: string;
}

interface DashboardExecuteResponse {
    success: boolean;
    executionId: string;
    outputs: Record<string, unknown>;
    error?: string;
    dashboardUrl?: string;
    durationMs?: number;
}

interface ExecutionSummary {
    executionId: string;
    dagName: string;
    scriptPath?: string;
    startTime: number;
    endTime?: number;
    status: ExecutionStatus;
    source: string;
    nodeCount: number;
    outputPreview?: string;
}

interface StoredExecution {
    executionId: string;
    dagName: string;
    scriptPath?: string;
    startTime: number;
    endTime?: number;
    inputs: Record<string, unknown>;
    outputs?: Record<string, unknown>;
    status: ExecutionStatus;
    nodeResults: Record<string, unknown>;
    dagVizIR?: DagVizIR;
    sampleRate: number;
    source: string;
    error?: string;
}

interface ExecutionListResponse {
    executions: ExecutionSummary[];
    total: number;
    limit: number;
    offset: number;
}

// =============================================================================
// Pipeline lifecycle types (from http-api ConstellationRoutes)
// =============================================================================

interface PipelineSummary {
    structuralHash: string;
    syntacticHash: string;
    aliases: string[];
    compiledAt: string;
    moduleCount: number;
    declaredOutputs: string[];
}

interface PipelineListResponse {
    pipelines: PipelineSummary[];
}

interface PipelineDetailResponse {
    structuralHash: string;
    syntacticHash: string;
    aliases: string[];
    compiledAt: string;
    modules: ModuleInfo[];
    declaredOutputs: string[];
    inputSchema: Record<string, string>;
    outputSchema: Record<string, string>;
}

interface ModuleInfo {
    name: string;
    description: string;
    version: string;
    inputs: Record<string, string>;
    outputs: Record<string, string>;
}

interface PipelineVersionInfo {
    version: number;
    structuralHash: string;
    createdAt: string;
    active: boolean;
}

interface PipelineVersionsResponse {
    name: string;
    versions: PipelineVersionInfo[];
    activeVersion: number;
}

interface ReloadResponse {
    success: boolean;
    previousHash?: string;
    newHash: string;
    name: string;
    changed: boolean;
    version: number;
    canary?: CanaryStateResponse;
}

interface RollbackResponse {
    success: boolean;
    name: string;
    previousVersion: number;
    activeVersion: number;
    structuralHash: string;
}

// =============================================================================
// Canary release types (from http-api CanaryRouter)
// =============================================================================

interface CanaryVersionInfo {
    version: number;
    structuralHash: string;
}

interface VersionMetricsResponse {
    requests: number;
    successes: number;
    failures: number;
    avgLatencyMs: number;
    p99LatencyMs: number;
}

interface CanaryMetricsResponse {
    oldVersion: VersionMetricsResponse;
    newVersion: VersionMetricsResponse;
}

interface CanaryStateResponse {
    pipelineName: string;
    oldVersion: CanaryVersionInfo;
    newVersion: CanaryVersionInfo;
    currentWeight: number;
    currentStep: number;
    status: string;
    startedAt: string;
    metrics: CanaryMetricsResponse;
}

// =============================================================================
// Suspended execution types (from http-api ConstellationRoutes)
// =============================================================================

interface SuspendedExecution {
    executionId: string;
    structuralHash: string;
    resumptionCount: number;
    missingInputs: Record<string, string>;
    createdAt: string;
}

interface SuspendedExecutionListResponse {
    executions: SuspendedExecution[];
}

interface CoreExecuteResponse {
    success: boolean;
    outputs: Record<string, unknown>;
    error?: string;
    status?: string;
    executionId?: string;
    missingInputs?: Record<string, string>;
    pendingOutputs?: string[];
    resumptionCount?: number;
}

// =============================================================================
// Component option types
// =============================================================================

interface ExecutionPanelOptions {
    inputsFormId: string;
    outputsDisplayId: string;
    historyListId: string;
    executionDetailId: string;
    onExecutionSelect?: (execution: StoredExecution) => void;
}

interface CodeEditorOptions {
    containerId: string;
    textareaId: string;
    errorBannerId: string;
    onPreviewResult?: (dagVizIR: DagVizIR | null, errors: string[], inputs: InputParam[]) => void;
}

interface NodeDetailData {
    id: string;
    label: string;
    kind: string;
    typeSignature: string;
    status?: string;
    value?: unknown;
    durationMs?: number;
    error?: string;
}

interface DashboardElements {
    scriptsView: HTMLElement;
    pipelinesView: HTMLElement;
    historyView: HTMLElement;
    runBtn: HTMLButtonElement;
    refreshBtn: HTMLButtonElement;
    currentFile: HTMLElement;
    nodeDetails: HTMLElement;
    nodeDetailsPanel: HTMLElement;
    closeDetails: HTMLElement;
    loadingOverlay: HTMLElement;
    statusMessage: HTMLElement;
    historyFilter: HTMLInputElement;
    layoutTB: HTMLElement;
    layoutLR: HTMLElement;
    zoomIn: HTMLElement;
    zoomOut: HTMLElement;
    zoomFit: HTMLElement;
    editorDagSplit: HTMLElement;
    splitHandle: HTMLElement;
}

// =============================================================================
// Ambient Cytoscape typings (subset used by dashboard)
// =============================================================================

interface CytoscapeStylesheet {
    selector: string;
    style: Record<string, unknown>;
}

interface CytoscapeLayoutOptions {
    name: string;
    rankDir?: string;
    nodeSep?: number;
    rankSep?: number;
    edgeSep?: number;
    padding?: number;
    animate?: boolean;
    animationDuration?: number;
}

interface CytoscapeLayout {
    run(): void;
}

interface CytoscapeEvent {
    target: CytoscapeNode | CytoscapeInstance;
}

interface CytoscapeNode {
    data(key: string): unknown;
    data(key: string, value: unknown): void;
    select(): void;
    unselect(): void;
    renderedPosition(): Position;
    closedNeighborhood(): CytoscapeCollection;
}

interface CytoscapeCollection {
    length: number;
    remove(): void;
    addClass(cls: string): void;
    removeClass(cls: string): void;
    forEach(fn: (ele: CytoscapeNode) => void): void;
    style(key: string, value: string): void;
}

interface CytoscapeInstance {
    add(elements: unknown[]): void;
    elements(): CytoscapeCollection;
    nodes(): CytoscapeCollection & { unselect(): void };
    edges(): CytoscapeCollection;
    on(event: string, handler: (evt: CytoscapeEvent) => void): void;
    on(event: string, selector: string, handler: (evt: CytoscapeEvent) => void): void;
    zoom(): number;
    zoom(level: number): void;
    fit(eles?: unknown, padding?: number): void;
    resize(): void;
    layout(options: CytoscapeLayoutOptions): CytoscapeLayout;
    container(): HTMLElement;
    getElementById(id: string): CytoscapeCollection & CytoscapeNode;
    batch(fn: () => void): void;
    destroy(): void;
    png(options: { output: string; bg: string; scale: number }): Blob;
}

interface CytoscapeOptions {
    container: HTMLElement | null;
    style: CytoscapeStylesheet[];
    layout: { name: string };
    minZoom: number;
    maxZoom: number;
    wheelSensitivity: number;
    boxSelectionEnabled: boolean;
}

interface CytoscapeStatic {
    (options: CytoscapeOptions): CytoscapeInstance;
    use(plugin: unknown): void;
}

// =============================================================================
// Global declarations
// =============================================================================

declare const cytoscape: CytoscapeStatic;
declare const cytoscapeDagre: unknown;

interface PipelinesPanelOptions {
    pipelinesListId: string;
    pipelineDetailId: string;
    suspendedListId: string;
    onPipelineExecute?: (name: string, inputs: Record<string, unknown>) => void;
}

interface Window {
    FileBrowser: typeof FileBrowser;
    DagVisualizer: typeof DagVisualizer;
    ExecutionPanel: typeof ExecutionPanel;
    CodeEditor: typeof CodeEditor;
    PipelinesPanel: typeof PipelinesPanel;
    dashboard: ConstellationDashboard;
}
