package io.constellation.lang.viz

/** Configuration for the Sugiyama layout algorithm */
case class LayoutConfig(
    direction: String = "TB",   // "TB" (top-bottom) or "LR" (left-right)
    nodeWidth: Double = 180,    // Default node width
    nodeHeight: Double = 60,    // Default node height
    nodeSpacing: Double = 40,   // Horizontal spacing within a layer
    layerSpacing: Double = 100  // Vertical spacing between layers
)

/** Sugiyama layout algorithm for DAG visualization
  *
  * The algorithm works in three phases:
  * 1. Layer Assignment - assign each node to a layer using longest path
  * 2. Crossing Minimization - reorder nodes within layers to minimize edge crossings
  * 3. Position Assignment - compute x,y coordinates
  */
object SugiyamaLayout:

  /** Apply Sugiyama layout to a DAG
    *
    * @param dag The DAG to layout (nodes without positions)
    * @param config Layout configuration
    * @return DAG with positions assigned to all nodes
    */
  def layout(dag: DagVizIR, config: LayoutConfig = LayoutConfig()): DagVizIR =
    if dag.nodes.isEmpty then return dag

    // Build adjacency lists
    val (successors, predecessors) = buildAdjacencyLists(dag)

    // Phase 1: Layer assignment
    val nodeLayers = assignLayers(dag.nodes, successors, predecessors)

    // Phase 2: Crossing minimization
    val orderedLayers = minimizeCrossings(nodeLayers, successors, predecessors)

    // Phase 3: Position assignment
    val positions = assignPositions(orderedLayers, config)

    // Update nodes with positions
    val positionedNodes = dag.nodes.map { node =>
      positions.get(node.id) match {
        case Some(pos) => node.copy(position = Some(pos))
        case None      => node
      }
    }

    // Calculate bounds
    val bounds = calculateBounds(positions.values.toList, config)

    dag.copy(
      nodes = positionedNodes,
      metadata = dag.metadata.copy(
        layoutDirection = config.direction,
        bounds = Some(bounds)
      )
    )

  /** Build successor and predecessor adjacency lists */
  private def buildAdjacencyLists(dag: DagVizIR): (Map[String, List[String]], Map[String, List[String]]) =
    val successors = dag.edges
      .groupBy(_.source)
      .view
      .mapValues(_.map(_.target).toList)
      .toMap
      .withDefaultValue(List.empty)

    val predecessors = dag.edges
      .groupBy(_.target)
      .view
      .mapValues(_.map(_.source).toList)
      .toMap
      .withDefaultValue(List.empty)

    (successors, predecessors)

  /** Assign nodes to layers using longest path from sources
    *
    * Sources (nodes with no predecessors) are at layer 0.
    * Each other node is placed at max(predecessor layers) + 1.
    */
  private def assignLayers(
      nodes: List[VizNode],
      successors: Map[String, List[String]],
      predecessors: Map[String, List[String]]
  ): Map[Int, List[VizNode]] =
    val nodeById = nodes.map(n => n.id -> n).toMap

    // Find sources (no predecessors)
    val sources = nodes.filter(n => predecessors(n.id).isEmpty).map(_.id)

    // BFS/topological order to assign layers
    var layerOf = Map.empty[String, Int]
    var queue = sources.toList
    var visited = Set.empty[String]

    // Initialize sources at layer 0
    sources.foreach(id => layerOf = layerOf + (id -> 0))

    // Process in topological order
    while queue.nonEmpty do
      val current = queue.head
      queue = queue.tail

      if !visited.contains(current) then
        visited = visited + current
        val currentLayer = layerOf.getOrElse(current, 0)

        // Update successors
        for succ <- successors(current) do
          val newLayer = currentLayer + 1
          val existingLayer = layerOf.getOrElse(succ, 0)
          layerOf = layerOf + (succ -> math.max(existingLayer, newLayer))

          // Add to queue if all predecessors visited
          if predecessors(succ).forall(visited.contains) then
            queue = queue :+ succ

    // Handle any unvisited nodes (cycles or disconnected)
    val unvisited = nodes.filterNot(n => visited.contains(n.id))
    val maxLayer = if layerOf.isEmpty then 0 else layerOf.values.max
    unvisited.foreach(n => layerOf = layerOf + (n.id -> (maxLayer + 1)))

    // Group nodes by layer
    nodes
      .groupBy(n => layerOf.getOrElse(n.id, 0))
      .view
      .mapValues(_.toList)
      .toMap

  /** Minimize edge crossings using barycenter heuristic
    *
    * Iteratively reorders nodes within each layer based on the
    * average position of their neighbors in adjacent layers.
    */
  private def minimizeCrossings(
      layers: Map[Int, List[VizNode]],
      successors: Map[String, List[String]],
      predecessors: Map[String, List[String]]
  ): Map[Int, List[VizNode]] =
    if layers.isEmpty then return layers

    val maxLayer = layers.keys.max
    var orderedLayers = layers

    // Multiple passes for better results
    for _ <- 0 until 4 do
      // Forward pass (top to bottom)
      for layer <- 1 to maxLayer do
        orderedLayers = reorderLayer(orderedLayers, layer, predecessors, forward = true)

      // Backward pass (bottom to top)
      for layer <- (maxLayer - 1) to 0 by -1 do
        orderedLayers = reorderLayer(orderedLayers, layer, successors, forward = false)

    orderedLayers

  /** Reorder a single layer based on neighbor positions */
  private def reorderLayer(
      layers: Map[Int, List[VizNode]],
      layerIndex: Int,
      adjacency: Map[String, List[String]], // predecessors or successors
      forward: Boolean
  ): Map[Int, List[VizNode]] =
    val currentLayer = layers.getOrElse(layerIndex, List.empty)
    val adjacentLayerIndex = if forward then layerIndex - 1 else layerIndex + 1
    val adjacentLayer = layers.getOrElse(adjacentLayerIndex, List.empty)

    if adjacentLayer.isEmpty then return layers

    // Create position map for adjacent layer
    val adjacentPositions = adjacentLayer.zipWithIndex.map { case (n, i) => n.id -> i.toDouble }.toMap

    // Calculate barycenter for each node in current layer
    val barycenters = currentLayer.map { node =>
      val neighbors = adjacency(node.id).filter(adjacentPositions.contains)
      val barycenter = if neighbors.isEmpty then
        // Keep relative position if no neighbors
        currentLayer.indexOf(node).toDouble
      else
        neighbors.map(adjacentPositions).sum / neighbors.length
      (node, barycenter)
    }

    // Sort by barycenter
    val reordered = barycenters.sortBy(_._2).map(_._1)
    layers + (layerIndex -> reordered)

  /** Assign x,y positions to nodes */
  private def assignPositions(
      layers: Map[Int, List[VizNode]],
      config: LayoutConfig
  ): Map[String, Position] =
    val isHorizontal = config.direction == "LR"

    layers.flatMap { case (layerIndex, nodes) =>
      val layerSize = nodes.length
      val totalWidth = layerSize * config.nodeWidth + (layerSize - 1) * config.nodeSpacing

      nodes.zipWithIndex.map { case (node, indexInLayer) =>
        // Center nodes in their layer
        val offset = indexInLayer * (config.nodeWidth + config.nodeSpacing)
        val centered = offset - totalWidth / 2 + config.nodeWidth / 2

        val pos = if isHorizontal then
          Position(
            x = layerIndex * (config.nodeWidth + config.layerSpacing),
            y = centered
          )
        else
          Position(
            x = centered,
            y = layerIndex * (config.nodeHeight + config.layerSpacing)
          )

        node.id -> pos
      }
    }

  /** Calculate bounding box for the visualization */
  private def calculateBounds(positions: List[Position], config: LayoutConfig): Bounds =
    if positions.isEmpty then
      Bounds(0, 0, config.nodeWidth, config.nodeHeight)
    else
      val xs = positions.map(_.x)
      val ys = positions.map(_.y)
      val padding = 20

      Bounds(
        minX = xs.min - config.nodeWidth / 2 - padding,
        minY = ys.min - config.nodeHeight / 2 - padding,
        maxX = xs.max + config.nodeWidth / 2 + padding,
        maxY = ys.max + config.nodeHeight / 2 + padding
      )
