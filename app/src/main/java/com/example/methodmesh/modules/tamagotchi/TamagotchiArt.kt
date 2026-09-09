package com.example.methodmesh.modules.tamagotchi

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun KawaiiCreature(
    creature: CreatureDefinition,
    phenotype: Phenotype,
    modifier: Modifier = Modifier
) {
    val motion = rememberInfiniteTransition(label = "tamagotchi-motion")
    val breathe by motion.animateFloat(
        initialValue = .985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(if (phenotype.sleeping) 1800 else 1200), RepeatMode.Reverse),
        label = "breathe"
    )
    val bob by motion.animateFloat(
        initialValue = if (phenotype.sleeping) 0f else -2f,
        targetValue = if (phenotype.sleeping) 1f else 3f,
        animationSpec = infiniteRepeatable(tween(if (phenotype.energy > 70) 800 else 1450), RepeatMode.Reverse),
        label = "bob"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = breathe, scaleY = breathe, translationY = bob)
        ) {
            val w = size.width
            val h = size.height
            val body = Color(creature.bodyHue)
            val accent = Color(creature.accentHue)
            val ink = Color(0xFF493F49)
            val blush = Color(0x55F07A9A)

            drawOval(Color(0x18000000), topLeft = Offset(w*.20f,h*.80f), size = Size(w*.60f,h*.13f))

            when (creature.id) {
                "pip" -> {
                    drawOval(accent, Offset(w*.23f,h*.02f), Size(w*.17f,h*.39f))
                    drawOval(accent, Offset(w*.60f,h*.02f), Size(w*.17f,h*.39f))
                }
                "mossbit" -> {
                    val l = Path().apply { moveTo(w*.31f,h*.36f); quadraticBezierTo(w*.02f,h*.08f,w*.43f,h*.18f); close() }
                    val r = Path().apply { moveTo(w*.69f,h*.36f); quadraticBezierTo(w*.98f,h*.08f,w*.57f,h*.18f); close() }
                    drawPath(l, accent); drawPath(r, accent)
                }
                "nib" -> repeat(5) { i -> drawCircle(accent, w*.065f, Offset(w*(.24f+i*.13f),h*.31f)) }
                "sprig" -> {
                    drawOval(accent, Offset(w*.31f,h*.07f), Size(w*.22f,h*.29f))
                    drawOval(accent, Offset(w*.48f,h*.07f), Size(w*.22f,h*.29f))
                }
                "tumble" -> repeat(3) { i ->
                    drawCircle(accent.copy(alpha=.65f), w*.035f, Offset(w*(.34f+i*.16f),h*.28f))
                }
            }

            drawOval(body, Offset(w*.14f,h*.23f), Size(w*.72f,h*.61f))

            if (creature.id == "mallow") {
                repeat(7) { i ->
                    val a = 2.0 * PI * i / 7.0
                    drawCircle(body.copy(alpha=.85f), w*.105f, Offset(w*.5f + sin(a).toFloat()*w*.32f, h*.52f + kotlin.math.cos(a).toFloat()*h*.25f))
                }
            }

            drawCircle(blush, w*.065f, Offset(w*.27f,h*.59f))
            drawCircle(blush, w*.065f, Offset(w*.73f,h*.59f))

            if (phenotype.sleeping) {
                drawArc(ink, 0f, 180f, false, Offset(w*.31f,h*.46f), Size(w*.14f,h*.07f), style=Stroke(w*.018f, cap=StrokeCap.Round))
                drawArc(ink, 0f, 180f, false, Offset(w*.55f,h*.46f), Size(w*.14f,h*.07f), style=Stroke(w*.018f, cap=StrokeCap.Round))
            } else {
                drawCircle(ink, w*.025f, Offset(w*.38f,h*.50f))
                drawCircle(ink, w*.025f, Offset(w*.62f,h*.50f))
                drawCircle(Color.White, w*.008f, Offset(w*.372f,h*.492f))
                drawCircle(Color.White, w*.008f, Offset(w*.612f,h*.492f))
            }

            val mouthRect = Rect(Offset(w*.43f,h*.59f), Size(w*.14f,h*.10f))
            drawArc(
                color = ink,
                startAngle = if (phenotype.unwell) 180f else 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = mouthRect.topLeft,
                size = mouthRect.size,
                style = Stroke(w*.018f, cap=StrokeCap.Round)
            )

            if (creature.id == "mossbit" && phenotype.mood > 60) {
                drawCircle(Color(0xFF78624C), w*.055f, Offset(w*.78f,h*.70f))
                drawCircle(Color(0xFFF2D8AD), w*.018f, Offset(w*.78f,h*.70f))
            }
            if (creature.id == "nib" && phenotype.mood > 68 && !phenotype.sleeping) {
                repeat(3) { i ->
                    drawCircle(accent.copy(alpha=.45f), w*(.025f+i*.012f), Offset(w*.84f,h*(.30f+i*.07f)))
                }
            }
            if (phenotype.sleeping) {
                drawLine(ink.copy(alpha=.45f), Offset(w*.72f,h*.24f), Offset(w*.78f,h*.18f), w*.015f, StrokeCap.Round)
                drawLine(ink.copy(alpha=.35f), Offset(w*.80f,h*.16f), Offset(w*.84f,h*.11f), w*.012f, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun CareGlyph(kind: CareGlyphKind, modifier: Modifier = Modifier) {
    Box(modifier.size(42.dp).background(Color(0x11FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(29.dp)) {
            val ink = Color(0xFF5A4857)
            val accent = Color(0xFFCC6F98)
            val stroke = Stroke(width = size.minDimension*.08f, cap = StrokeCap.Round)
            when (kind) {
                CareGlyphKind.Feed -> {
                    drawArc(ink, 0f, 180f, false, Offset(size.width*.1f,size.height*.35f), Size(size.width*.8f,size.height*.5f), style=stroke)
                    drawLine(accent, Offset(size.width*.20f,size.height*.38f), Offset(size.width*.80f,size.height*.38f), stroke.width, StrokeCap.Round)
                }
                CareGlyphKind.Drink -> {
                    val p = Path().apply { moveTo(size.width*.50f,size.height*.06f); cubicTo(size.width*.18f,size.height*.46f,size.width*.22f,size.height*.88f,size.width*.50f,size.height*.92f); cubicTo(size.width*.78f,size.height*.88f,size.width*.82f,size.height*.46f,size.width*.50f,size.height*.06f); close() }
                    drawPath(p, accent)
                }
                CareGlyphKind.Play -> {
                    drawCircle(accent, size.width*.30f, center)
                    drawCircle(Color.White.copy(alpha=.35f), size.width*.07f, Offset(size.width*.40f,size.height*.40f))
                }
                CareGlyphKind.Rest -> {
                    drawArc(ink, -30f, 240f, false, Offset(size.width*.15f,size.height*.18f), Size(size.width*.62f,size.height*.62f), style=Stroke(size.width*.16f, cap=StrokeCap.Round))
                }
                CareGlyphKind.Clean -> {
                    drawCircle(accent,size.width*.15f,Offset(size.width*.30f,size.height*.55f)); drawCircle(accent,size.width*.10f,Offset(size.width*.61f,size.height*.34f)); drawCircle(accent,size.width*.07f,Offset(size.width*.70f,size.height*.69f))
                }
                CareGlyphKind.Medicine -> {
                    drawRoundRect(accent, Offset(size.width*.22f,size.height*.38f), Size(size.width*.56f,size.height*.25f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.width*.12f,size.width*.12f))
                    drawLine(Color.White,Offset(size.width*.50f,size.height*.40f),Offset(size.width*.50f,size.height*.61f),size.width*.05f)
                }
                CareGlyphKind.Observe -> {
                    drawOval(ink,Offset(size.width*.10f,size.height*.30f),Size(size.width*.80f,size.height*.40f),style=stroke)
                    drawCircle(accent,size.width*.13f,center)
                }
                CareGlyphKind.Measure -> {
                    drawLine(ink,Offset(size.width*.25f,size.height*.15f),Offset(size.width*.25f,size.height*.85f),stroke.width)
                    repeat(4){ i -> drawLine(ink,Offset(size.width*.25f,size.height*(.22f+i*.16f)),Offset(size.width*(if(i%2==0).62f else .50f),size.height*(.22f+i*.16f)),stroke.width*.65f) }
                }
                CareGlyphKind.Time -> {
                    drawCircle(ink,size.width*.37f,center,style=stroke); drawLine(accent,center,Offset(size.width*.50f,size.height*.28f),stroke.width); drawLine(accent,center,Offset(size.width*.69f,size.height*.55f),stroke.width)
                }
                CareGlyphKind.History -> {
                    repeat(3){ i -> drawCircle(accent,size.width*.055f,Offset(size.width*.18f,size.height*(.26f+i*.24f))); drawLine(ink,Offset(size.width*.33f,size.height*(.26f+i*.24f)),Offset(size.width*.84f,size.height*(.26f+i*.24f)),stroke.width*.7f) }
                }
                CareGlyphKind.Analyse -> {
                    drawLine(ink,Offset(size.width*.18f,size.height*.78f),Offset(size.width*.18f,size.height*.46f),stroke.width); drawLine(accent,Offset(size.width*.42f,size.height*.78f),Offset(size.width*.42f,size.height*.28f),stroke.width); drawLine(ink,Offset(size.width*.66f,size.height*.78f),Offset(size.width*.66f,size.height*.15f),stroke.width)
                }
                CareGlyphKind.Export -> {
                    drawLine(ink,Offset(size.width*.20f,size.height*.72f),Offset(size.width*.80f,size.height*.72f),stroke.width); drawLine(accent,Offset(size.width*.50f,size.height*.14f),Offset(size.width*.50f,size.height*.57f),stroke.width); drawLine(accent,Offset(size.width*.50f,size.height*.57f),Offset(size.width*.34f,size.height*.42f),stroke.width); drawLine(accent,Offset(size.width*.50f,size.height*.57f),Offset(size.width*.66f,size.height*.42f),stroke.width)
                }
                CareGlyphKind.Sparkle -> {
                    val p = Path().apply { moveTo(size.width*.50f,size.height*.06f); lineTo(size.width*.59f,size.height*.40f); lineTo(size.width*.94f,size.height*.50f); lineTo(size.width*.59f,size.height*.60f); lineTo(size.width*.50f,size.height*.94f); lineTo(size.width*.41f,size.height*.60f); lineTo(size.width*.06f,size.height*.50f); lineTo(size.width*.41f,size.height*.40f); close() }
                    drawPath(p,accent)
                }
            }
        }
    }
}
