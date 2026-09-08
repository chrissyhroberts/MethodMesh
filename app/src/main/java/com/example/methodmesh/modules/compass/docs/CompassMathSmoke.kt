package com.example.methodmesh.modules.compass

/** Standalone smoke harness; intentionally avoids JUnit so it can run with kotlinc. */
fun main() {
    check(CompassMath.normaliseDegrees(-1f) == 359f)
    check(CompassMath.normaliseDegrees(360f) == 0f)
    check(CompassMath.cardinalDirection(0f) == "N")
    check(CompassMath.cardinalDirection(22.5f) == "NNE")
    check(CompassMath.cardinalDirection(90f) == "E")
    check(CompassMath.cardinalDirection(225f) == "SW")
    check(CompassMath.signedErrorDegrees(1f, 359f) == 2f)
    check(CompassMath.signedErrorDegrees(359f, 1f) == -2f)
    check(CompassMath.signedErrorDegrees(180f, 0f) == -180f)
    check(CompassMath.isAligned(90f, 95f, 5f))
    check(!CompassMath.isAligned(90f, 95.1f, 5f))
    check(CompassMath.alignmentInstruction(12f, 5f) == "Turn right 12°")
    check(CompassMath.alignmentInstruction(-12f, 5f) == "Turn left 12°")
    check(CompassMath.alignmentInstruction(4.9f, 5f) == "On target")
    check(CompassMath.headingLabel(90.2f) == "090° E")
    println("CompassMath smoke test passed")
}
