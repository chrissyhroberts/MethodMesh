package com.example.methodmesh.modules.aviation

internal object AviationCsv {
    fun parseLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        values += current.toString()
        return values
    }
}
