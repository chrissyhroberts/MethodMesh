package com.example.methodmesh.core

class ResearchSession {

    private val graph = ResearchGraph()

    var invocationContext: com.example.methodmesh.core.methodmesh.InvocationContext = com.example.methodmesh.core.methodmesh.InvocationContext()
        private set

    fun setInvocationContext(context: com.example.methodmesh.core.methodmesh.InvocationContext) {
        invocationContext = context
    }

    fun invocationContextMap(): Map<String, String> = invocationContext.asMap()

    val asEntities: Collection<com.example.methodmesh.core.methodmesh.Entity>
        get() = graph.asEntities.values

    val asObservations: Collection<com.example.methodmesh.core.methodmesh.Observation>
        get() = graph.asObservations.values

    val asStates: Collection<com.example.methodmesh.core.methodmesh.State>
        get() = graph.asStates.values

    val transformations: Collection<com.example.methodmesh.core.methodmesh.Transformation>
        get() = graph.transformations.values

    val executionResults: Collection<com.example.methodmesh.core.methodmesh.ExecutionResult>
        get() = graph.executionResults.values

    fun graph(): ResearchGraph = graph

    fun add(entity: com.example.methodmesh.core.methodmesh.Entity) {
        graph.add(entity)
    }

    fun add(observation: com.example.methodmesh.core.methodmesh.Observation) {
        graph.add(observation)
    }

    fun record(result: com.example.methodmesh.core.methodmesh.ExecutionResult): com.example.methodmesh.core.methodmesh.ExecutionResult {
        val enriched = if (com.example.methodmesh.core.methodmesh.InvocationContext.from(result.request.context) == null) {
            result.copy(
                request = result.request.copy(
                    context = invocationContext.asMap() + result.request.context
                )
            )
        } else {
            result
        }
        return graph.record(enriched)
    }

    fun clear() {
        graph.clear()
    }
}
