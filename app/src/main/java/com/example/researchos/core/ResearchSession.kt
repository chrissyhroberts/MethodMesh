package com.example.researchos.core

class ResearchSession {

    private val graph = ResearchGraph()

    var invocationContext: com.example.researchos.core.researchos.InvocationContext = com.example.researchos.core.researchos.InvocationContext()
        private set

    fun setInvocationContext(context: com.example.researchos.core.researchos.InvocationContext) {
        invocationContext = context
    }

    fun invocationContextMap(): Map<String, String> = invocationContext.asMap()

    val asEntities: Collection<com.example.researchos.core.researchos.Entity>
        get() = graph.asEntities.values

    val asObservations: Collection<com.example.researchos.core.researchos.Observation>
        get() = graph.asObservations.values

    val asStates: Collection<com.example.researchos.core.researchos.State>
        get() = graph.asStates.values

    val transformations: Collection<com.example.researchos.core.researchos.Transformation>
        get() = graph.transformations.values

    val executionResults: Collection<com.example.researchos.core.researchos.ExecutionResult>
        get() = graph.executionResults.values

    fun graph(): ResearchGraph = graph

    fun add(entity: com.example.researchos.core.researchos.Entity) {
        graph.add(entity)
    }

    fun add(observation: com.example.researchos.core.researchos.Observation) {
        graph.add(observation)
    }

    fun record(result: com.example.researchos.core.researchos.ExecutionResult): com.example.researchos.core.researchos.ExecutionResult {
        val enriched = if (com.example.researchos.core.researchos.InvocationContext.from(result.request.context) == null) {
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
