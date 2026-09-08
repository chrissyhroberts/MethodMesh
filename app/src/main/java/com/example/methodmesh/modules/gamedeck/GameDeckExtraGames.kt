package com.example.methodmesh.modules.gamedeck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

private val ExtraP1 = Color(0xFFE84A5F)
private val ExtraP2 = Color(0xFFFFC857)

@Composable
internal fun ExtraGameScreen(
    game: String,
    stateJson: String,
    rngMode: String,
    seed: String,
    soundEnabled: Boolean,
    cpuOpponent: Boolean,
    onCpuOpponentChanged: (Boolean) -> Unit,
    onStateChange: (String) -> Unit
) {
    when (game) {
        GameDeckExtraEngine.TIC_TAC_TOE -> TicTacToeScreen(stateJson, soundEnabled, cpuOpponent, onCpuOpponentChanged, onStateChange)
        GameDeckExtraEngine.REVERSI -> ReversiScreen(stateJson, soundEnabled, cpuOpponent, onCpuOpponentChanged, onStateChange)
        GameDeckExtraEngine.NIM -> NimScreen(stateJson, soundEnabled, cpuOpponent, onCpuOpponentChanged, onStateChange)
        GameDeckExtraEngine.LIGHTS_OUT -> LightsOutScreen(stateJson, onStateChange)
        GameDeckExtraEngine.FIFTEEN -> FifteenScreen(stateJson, onStateChange)
        GameDeckExtraEngine.MEMORY -> MemoryScreen(stateJson, onStateChange)
        GameDeckExtraEngine.SHUT_THE_BOX -> ShutTheBoxScreen(stateJson, rngMode, seed, soundEnabled, onStateChange)
        GameDeckExtraEngine.CODEBREAKER -> CodebreakerScreen(stateJson, onStateChange)
        GameDeckExtraEngine.TWENTY_FORTY_EIGHT -> TwentyFortyEightScreen(stateJson, rngMode, seed, onStateChange)
        GameDeckExtraEngine.DOTS_AND_BOXES -> DotsAndBoxesScreen(stateJson, onStateChange)
        GameDeckExtraEngine.SUDOKU -> SudokuScreen(stateJson, rngMode, seed, onStateChange)
        GameDeckExtraEngine.TAKUZU -> TakuzuScreen(stateJson, rngMode, seed, onStateChange)
        GameDeckExtraEngine.CHESS -> ChessScreen(stateJson, rngMode, seed, soundEnabled, cpuOpponent, onCpuOpponentChanged, onStateChange)
        GameDeckExtraEngine.GO_9X9 -> GoScreen(stateJson, rngMode, seed, soundEnabled, cpuOpponent, onCpuOpponentChanged, onStateChange)
    }
}

@Composable
private fun ExtraRail(label:String, detail:String, active:Boolean, top:Boolean, human:Boolean, control:(@Composable ()->Unit)?=null){
    Surface(
        modifier=Modifier.fillMaxWidth().graphicsLayer{rotationZ=if(top&&human)180f else 0f},
        shape=RoundedCornerShape(22.dp),
        color=(if(top) ExtraP2 else ExtraP1).copy(alpha=if(active).22f else .10f),
        tonalElevation=if(active)5.dp else 0.dp
    ){
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween){
            Column { Text(label,fontWeight=FontWeight.Black); Text(detail,style=MaterialTheme.typography.labelSmall) }
            control?.invoke()
        }
    }
}

@Composable
internal fun ExtraTabletop(
    turn:Int,
    topHuman:Boolean,
    topDetail:String,
    bottomDetail:String,
    bottomLabel:String="PLAYER 1",
    topControl:(@Composable ()->Unit)?=null,
    bottomControl:(@Composable ()->Unit)?=null,
    board:@Composable ()->Unit
){
    BoxWithConstraints(Modifier.fillMaxSize()){
        val rail=86.dp; val gap=6.dp; val centre=(maxHeight-rail-rail-gap-gap).coerceAtLeast(140.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally){
            Box(Modifier.fillMaxWidth().height(rail),contentAlignment=Alignment.Center){ExtraRail(if(topHuman)"PLAYER 2" else "CPU",topDetail,turn==2,true,topHuman,topControl)}
            Spacer(Modifier.height(gap)); Box(Modifier.fillMaxWidth().height(centre),contentAlignment=Alignment.Center){board()}; Spacer(Modifier.height(gap))
            Box(Modifier.fillMaxWidth().height(rail),contentAlignment=Alignment.Center){ExtraRail(bottomLabel,bottomDetail,turn==1,false,true,bottomControl)}
        }
    }
}

@Composable
internal fun CpuModeButton(cpu:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){
    OutlinedButton(onClick={onChange(!cpu)},enabled=enabled){
        Text(if(cpu)"MODE: CPU" else "MODE: 2P",fontWeight=FontWeight.Bold)
    }
}

@Composable
private fun TicTacToeScreen(
    stateJson:String,
    sound:Boolean,
    cpu:Boolean,
    onCpuChanged:(Boolean)->Unit,
    onStateChange:(String)->Unit
){
    val s=JSONObject(stateJson)
    val turn=s.optInt("turn",1)
    LaunchedEffect(stateJson,cpu){
        if(cpu&&turn==2&&s.optString("winner").isBlank()){
            delay(420)
            val decision=GameDeckAgents.choose(
                game=GameDeckExtraEngine.TIC_TAC_TOE,
                stateJson=stateJson,
                seat=2,
                profile=GameDeckAgents.STANDARD,
                decisionSeed="interactive|ttt|${s.optInt("moves")}"
            )
            val c=decision.action?.toIntOrNull() ?: -1
            if(c>=0){
                GameDeckSound.cpu(sound)
                onStateChange(GameDeckExtraEngine.ticTacToeMove(stateJson,c))
            }
        }
    }
    val terminal=s.optString("winner").isNotBlank()
    val topDetail=when{
        terminal -> "◆  •  ${s.optInt("moves")} turns"
        turn==2&&cpu -> "CPU THINKING…"
        turn==2 -> "YOUR TURN  •  ◆"
        else -> "◆  •  ${s.optInt("moves")} turns"
    }
    val bottomDetail=if(!terminal&&turn==1)"YOUR TURN  •  ●" else "●  •  ${s.optInt("moves")} turns"
    ExtraTabletop(turn,!cpu,topDetail,bottomDetail,bottomLabel=if(cpu)"YOU" else "PLAYER 1",topControl={CpuModeButton(cpu,s.optInt("moves")==0,onCpuChanged)}){
        SquareBoard(3){cell,side->
            val v=s.getJSONArray("board").optInt(cell)
            Surface(
                modifier=Modifier.size(side).padding(3.dp),
                shape=RoundedCornerShape(12.dp),
                color=if(v==0)MaterialTheme.colorScheme.primaryContainer.copy(alpha=.38f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation=if(v==0)2.dp else 0.dp,
                onClick={onStateChange(GameDeckExtraEngine.ticTacToeMove(stateJson,cell))},
                enabled=v==0&&!terminal&&!(cpu&&turn==2)
            ){
                Box(contentAlignment=Alignment.Center){
                    Text(
                        if(v==1)"●" else if(v==2)"◆" else "",
                        style=MaterialTheme.typography.headlineLarge,
                        fontWeight=FontWeight.Black,
                        color=if(v==1)ExtraP1 else ExtraP2
                    )
                }
            }
        }
    }
}

@Composable
private fun ReversiScreen(
    stateJson:String,
    sound:Boolean,
    cpu:Boolean,
    onCpuChanged:(Boolean)->Unit,
    onStateChange:(String)->Unit
){
    val s=JSONObject(stateJson)
    val turn=s.optInt("turn",1)
    val legal=GameDeckExtraEngine.reversiLegalMoves(stateJson).toSet()
    val b=s.getJSONArray("board")
    val c1=(0 until 64).count{b.optInt(it)==1}
    val c2=(0 until 64).count{b.optInt(it)==2}
    LaunchedEffect(stateJson,cpu){
        if(s.optString("winner").isBlank()&&legal.isEmpty()){
            val resolved=GameDeckExtraEngine.reversiResolveBlockedTurn(stateJson)
            if(resolved!=stateJson){
                delay(300)
                onStateChange(resolved)
            }
        } else if(cpu&&turn==2&&s.optString("winner").isBlank()){
            delay(500)
            val decision=GameDeckAgents.choose(
                game=GameDeckExtraEngine.REVERSI,
                stateJson=stateJson,
                seat=2,
                profile=GameDeckAgents.STANDARD,
                decisionSeed="interactive|reversi|${s.optInt("moves")}"
            )
            val c=decision.action?.toIntOrNull() ?: -1
            if(c>=0){
                GameDeckSound.cpu(sound)
                onStateChange(GameDeckExtraEngine.reversiMove(stateJson,c))
            }
        }
    }
    val terminal=s.optString("winner").isNotBlank()
    val topDetail=when{
        terminal -> "$c2 discs"
        turn==2&&cpu -> "CPU THINKING…  •  $c2 discs"
        turn==2 -> "YOUR TURN  •  tap a dot  •  $c2 discs"
        else -> "$c2 discs"
    }
    val bottomDetail=if(!terminal&&turn==1)"YOUR TURN  •  tap a dot  •  $c1 discs" else "$c1 discs"
    ExtraTabletop(turn,!cpu,topDetail,bottomDetail,bottomLabel=if(cpu)"YOU" else "PLAYER 1",topControl={CpuModeButton(cpu,s.optInt("moves")==0,onCpuChanged)}){
        SquareBoard(8){cell,side->
            val v=b.optInt(cell)
            Box(
                Modifier.size(side).padding(1.dp)
                    .background(Color(0xFF2E9D70), RoundedCornerShape(3.dp))
                    .clickable(enabled=cell in legal&&!terminal&&!(cpu&&turn==2)){
                        onStateChange(GameDeckExtraEngine.reversiMove(stateJson,cell))
                    },
                contentAlignment=Alignment.Center
            ){
                if(v!=0) Surface(
                    Modifier.size(side*.72f),
                    CircleShape,
                    color=if(v==1)Color(0xFFF4F1EA) else Color(0xFF222222),
                    tonalElevation=3.dp
                ){}
                else if(cell in legal) Surface(
                    Modifier.size(side*.26f),
                    CircleShape,
                    color=MaterialTheme.colorScheme.primary.copy(alpha=.70f)
                ){}
            }
        }
    }
}

@Composable
private fun NimScreen(
    stateJson:String,
    sound:Boolean,
    cpu:Boolean,
    onCpuChanged:(Boolean)->Unit,
    onStateChange:(String)->Unit
){
    val s=JSONObject(stateJson)
    val turn=s.optInt("turn",1)
    val heaps=s.getJSONArray("heaps")
    LaunchedEffect(stateJson,cpu){
        if(cpu&&turn==2&&s.optString("winner").isBlank()){
            delay(420)
            val decision=GameDeckAgents.choose(
                game=GameDeckExtraEngine.NIM,
                stateJson=stateJson,
                seat=2,
                profile=GameDeckAgents.STANDARD,
                decisionSeed="interactive|nim|${s.optInt("moves")}"
            )
            val parts=decision.action?.split(":")
            if(parts?.size==2){
                GameDeckSound.cpu(sound)
                onStateChange(
                    GameDeckExtraEngine.nimMove(
                        stateJson,
                        parts[0].toInt(),
                        parts[1].toInt()
                    )
                )
            }
        }
    }
    val terminal=s.optString("winner").isNotBlank()
    val topDetail=when{
        terminal -> "◆"
        turn==2&&cpu -> "CPU THINKING…"
        turn==2 -> "YOUR TURN  •  take from one row"
        else -> "Take from one row"
    }
    val bottomDetail=if(!terminal&&turn==1)"YOUR TURN  •  take from one row" else "Take from one row"
    ExtraTabletop(turn,!cpu,topDetail,bottomDetail,bottomLabel=if(cpu)"YOU" else "PLAYER 1",topControl={CpuModeButton(cpu,s.optInt("moves")==0,onCpuChanged)}){
        Column(verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(
                "Tap a counter to remove it and every counter to its right.",
                textAlign=TextAlign.Center,
                style=MaterialTheme.typography.labelSmall,
                fontWeight=FontWeight.Bold
            )
            (0..2).forEach{h->
                val n=heaps.optInt(h)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                    Text("${h+1}",fontWeight=FontWeight.Black)
                    repeat(n){i->
                        Surface(
                            modifier=Modifier.size(38.dp).clickable(enabled=!terminal&&!(cpu&&turn==2)){
                                onStateChange(GameDeckExtraEngine.nimMove(stateJson,h,n-i))
                            },
                            shape=CircleShape,
                            color=if(turn==1)ExtraP1 else ExtraP2
                        ){}
                    }
                }
            }
        }
    }
}

@Composable
private fun TwentyFortyEightScreen(
    stateJson:String,
    rngMode:String,
    seed:String,
    onStateChange:(String)->Unit
){
    val s=JSONObject(stateJson)
    val cells=s.getJSONArray("cells")
    val terminal=s.optString("winner").isNotBlank()

    fun move(direction:String){
        if(!terminal){
            onStateChange(
                GameDeckExtraEngine.twentyFortyEightMove(
                    stateJson,
                    direction,
                    rngMode,
                    seed
                )
            )
        }
    }

    SoloFrame(
        "2048",
        "Swipe or use the arrows to combine equal tiles.",
        "Score ${s.optInt("score")}"
    ){
        BoxWithConstraints(Modifier.fillMaxSize()){
            val controlsHeight=170.dp
            val boardSize=minOf(
                maxWidth,
                (maxHeight-controlsHeight).coerceAtLeast(180.dp)
            )
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ){
                Box(
                    Modifier
                        .size(boardSize)
                        .pointerInput(stateJson,terminal){
                            if(!terminal){
                                var dx=0f
                                var dy=0f
                                detectDragGestures(
                                    onDragStart={dx=0f;dy=0f},
                                    onDragCancel={dx=0f;dy=0f},
                                    onDragEnd={
                                        if(abs(dx)>24f||abs(dy)>24f){
                                            if(abs(dx)>abs(dy)){
                                                move(if(dx>0f)"right" else "left")
                                            }else{
                                                move(if(dy>0f)"down" else "up")
                                            }
                                        }
                                    },
                                    onDrag={_,amount->
                                        dx+=amount.x
                                        dy+=amount.y
                                    }
                                )
                            }
                        }
                ){
                    SquareBoard(4){cell,side->
                        val value=cells.optInt(cell)
                        val tileColor=when(value){
                            0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)
                            2 -> Color(0xFFEDE0C8)
                            4 -> Color(0xFFE8CFA9)
                            8 -> Color(0xFFF2B179)
                            16 -> Color(0xFFF59563)
                            32 -> Color(0xFFF67C5F)
                            64 -> Color(0xFFF65E3B)
                            128 -> Color(0xFFEDCF72)
                            256 -> Color(0xFFEDCC61)
                            512 -> Color(0xFFEDC850)
                            1024 -> Color(0xFFEDC53F)
                            else -> Color(0xFFEDC22E)
                        }
                        Surface(
                            modifier=Modifier.size(side).padding(3.dp),
                            shape=RoundedCornerShape(11.dp),
                            color=tileColor,
                            tonalElevation=if(value>0)3.dp else 0.dp
                        ){
                            Box(contentAlignment=Alignment.Center){
                                if(value>0){
                                    Text(
                                        value.toString(),
                                        fontWeight=FontWeight.Black,
                                        style=when{
                                            value<100 -> MaterialTheme.typography.headlineSmall
                                            value<1000 -> MaterialTheme.typography.titleLarge
                                            else -> MaterialTheme.typography.titleMedium
                                        },
                                        color=if(value<=4)Color(0xFF5B5146) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(4.dp)
                ){
                    OutlinedButton(onClick={move("up")},enabled=!terminal){Text("↑")}
                    Row(horizontalArrangement=Arrangement.spacedBy(34.dp)){
                        OutlinedButton(onClick={move("left")},enabled=!terminal){Text("←")}
                        OutlinedButton(onClick={move("right")},enabled=!terminal){Text("→")}
                    }
                    OutlinedButton(onClick={move("down")},enabled=!terminal){Text("↓")}
                }
            }
        }
    }
}

@Composable
private fun DotsAndBoxesScreen(
    stateJson:String,
    onStateChange:(String)->Unit
){
    val s=JSONObject(stateJson)
    val turn=s.optInt("turn",1)
    val horizontal=s.getJSONArray("horizontal")
    val vertical=s.getJSONArray("vertical")
    val boxes=s.getJSONArray("boxes")
    val scores=s.getJSONArray("scores")
    val terminal=s.optString("winner").isNotBlank()

    val topDetail=when{
        terminal -> "${scores.optInt(1)} boxes"
        turn==2 -> "YOUR TURN  •  ${scores.optInt(1)} boxes"
        else -> "${scores.optInt(1)} boxes"
    }
    val bottomDetail=when{
        terminal -> "${scores.optInt(0)} boxes"
        turn==1 -> "YOUR TURN  •  ${scores.optInt(0)} boxes"
        else -> "${scores.optInt(0)} boxes"
    }

    ExtraTabletop(
        turn=turn,
        topHuman=true,
        topDetail=topDetail,
        bottomDetail=bottomDetail
    ){
        SquareBoard(9){cell,side->
            val r=cell/9
            val c=cell%9
            Box(
                Modifier.size(side),
                contentAlignment=Alignment.Center
            ){
                when{
                    r%2==0&&c%2==0 -> {
                        Surface(
                            modifier=Modifier.size(side*.28f),
                            shape=CircleShape,
                            color=MaterialTheme.colorScheme.onSurface
                        ){}
                    }

                    r%2==0&&c%2==1 -> {
                        val edge=(r/2)*4+(c/2)
                        val owner=horizontal.optInt(edge)
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(enabled=owner==0&&!terminal){
                                    onStateChange(
                                        GameDeckExtraEngine.dotsBoxesMove(
                                            stateJson,
                                            "h",
                                            edge
                                        )
                                    )
                                },
                            contentAlignment=Alignment.Center
                        ){
                            Surface(
                                modifier=Modifier.size(
                                    width=side*.92f,
                                    height=side*.20f
                                ),
                                shape=RoundedCornerShape(6.dp),
                                color=when(owner){
                                    1 -> ExtraP1
                                    2 -> ExtraP2
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                tonalElevation=if(owner==0)1.dp else 4.dp
                            ){}
                        }
                    }

                    r%2==1&&c%2==0 -> {
                        val edge=(r/2)*5+(c/2)
                        val owner=vertical.optInt(edge)
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(enabled=owner==0&&!terminal){
                                    onStateChange(
                                        GameDeckExtraEngine.dotsBoxesMove(
                                            stateJson,
                                            "v",
                                            edge
                                        )
                                    )
                                },
                            contentAlignment=Alignment.Center
                        ){
                            Surface(
                                modifier=Modifier.size(
                                    width=side*.20f,
                                    height=side*.92f
                                ),
                                shape=RoundedCornerShape(6.dp),
                                color=when(owner){
                                    1 -> ExtraP1
                                    2 -> ExtraP2
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                tonalElevation=if(owner==0)1.dp else 4.dp
                            ){}
                        }
                    }

                    else -> {
                        val box=(r/2)*4+(c/2)
                        val owner=boxes.optInt(box)
                        Surface(
                            modifier=Modifier.size(side*.82f),
                            shape=RoundedCornerShape(8.dp),
                            color=when(owner){
                                1 -> ExtraP1.copy(alpha=.30f)
                                2 -> ExtraP2.copy(alpha=.34f)
                                else -> Color.Transparent
                            }
                        ){
                            if(owner!=0){
                                Box(contentAlignment=Alignment.Center){
                                    Text(
                                        if(owner==1)"●" else "◆",
                                        color=if(owner==1)ExtraP1 else ExtraP2,
                                        fontWeight=FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LightsOutScreen(stateJson:String,onStateChange:(String)->Unit){
    val s=JSONObject(stateJson)
    val lights=s.getJSONArray("lights")
    val terminal=s.optString("winner").isNotBlank()
    SoloFrame(
        "LIGHTS OUT",
        "Tap a light; it also flips its neighbours.",
        "${s.optInt("moves")} moves"
    ){
        SquareBoard(5){cell,side->
            val on=lights.optInt(cell)==1
            Surface(
                modifier=Modifier.size(side).padding(2.dp),
                shape=RoundedCornerShape(9.dp),
                color=if(on)Color(0xFFFFC857) else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation=if(on)5.dp else 0.dp,
                onClick={onStateChange(GameDeckExtraEngine.lightsMove(stateJson,cell))},
                enabled=!terminal
            ){}
        }
    }
}

@Composable
private fun FifteenScreen(stateJson:String,onStateChange:(String)->Unit){
    val s=JSONObject(stateJson)
    val a=s.getJSONArray("tiles")
    val terminal=s.optString("winner").isNotBlank()
    val blank=(0 until a.length()).firstOrNull{a.optInt(it)==0} ?: 15

    SoloFrame(
        "15 PUZZLE",
        "Tap a tile beside the empty space.",
        "${s.optInt("moves")} moves"
    ){
        SquareBoard(4){cell,side->
            val v=a.optInt(cell)
            val legal=v!=0 &&
                kotlin.math.abs(cell/4-blank/4)+kotlin.math.abs(cell%4-blank%4)==1
            Surface(
                modifier=Modifier.size(side).padding(3.dp),
                shape=RoundedCornerShape(12.dp),
                color=when{
                    v==0 -> Color.Transparent
                    legal -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                tonalElevation=if(legal)4.dp else 0.dp,
                onClick={onStateChange(GameDeckExtraEngine.fifteenMove(stateJson,cell))},
                enabled=legal&&!terminal
            ){
                Box(contentAlignment=Alignment.Center){
                    if(v!=0)Text(
                        v.toString(),
                        fontWeight=FontWeight.Black,
                        style=MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryScreen(stateJson:String,onStateChange:(String)->Unit){
    val s=JSONObject(stateJson)
    val turn=s.optInt("turn",1)
    val cards=s.getJSONArray("cards")
    val matched=s.getJSONArray("matched")
    val face=s.getJSONArray("face_up")
    val visible=(0 until face.length()).map{face.optInt(it)}.toSet()
    val scores=s.getJSONArray("scores")
    val resolving=s.optString("phase","choose")=="resolve"

    // A mismatch remains visible long enough to be perceived and remembered.
    LaunchedEffect(stateJson){
        if(resolving){
            delay(850)
            onStateChange(GameDeckExtraEngine.memoryResolve(stateJson))
        }
    }

    val terminal=s.optString("winner").isNotBlank()
    val topDetail=when{
        terminal -> "${scores.optInt(1)} pairs"
        resolving&&turn==2 -> "CHECK THE PAIR…  •  ${scores.optInt(1)} pairs"
        turn==2 -> "YOUR TURN  •  flip two  •  ${scores.optInt(1)} pairs"
        else -> "${scores.optInt(1)} pairs"
    }
    val bottomDetail=when{
        terminal -> "${scores.optInt(0)} pairs"
        resolving&&turn==1 -> "CHECK THE PAIR…  •  ${scores.optInt(0)} pairs"
        turn==1 -> "YOUR TURN  •  flip two  •  ${scores.optInt(0)} pairs"
        else -> "${scores.optInt(0)} pairs"
    }
    ExtraTabletop(turn,true,topDetail,bottomDetail){
        SquareBoard(4){cell,side->
            val shown=matched.optInt(cell)==1||cell in visible
            val alreadyMatched=matched.optInt(cell)==1
            Surface(
                modifier=Modifier.size(side).padding(3.dp),
                shape=RoundedCornerShape(12.dp),
                color=when{
                    alreadyMatched -> MaterialTheme.colorScheme.primaryContainer.copy(alpha=.60f)
                    shown -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                tonalElevation=if(shown&&!alreadyMatched)5.dp else 1.dp,
                onClick={onStateChange(GameDeckExtraEngine.memoryFlip(stateJson,cell))},
                enabled=!terminal&&!resolving&&!alreadyMatched&&cell !in visible
            ){
                Box(contentAlignment=Alignment.Center){
                    Text(
                        if(shown)cards.optInt(cell).toString() else "?",
                        fontWeight=FontWeight.Black,
                        style=MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ShutTheBoxScreen(
    stateJson:String,
    rngMode:String,
    seed:String,
    sound:Boolean,
    onStateChange:(String)->Unit
){
    val s=JSONObject(stateJson)
    val open=s.getJSONArray("open")
    val openSet=(0 until open.length()).map{open.optInt(it)}.toSet()
    val selectedArray=s.optJSONArray("selected")?:JSONArray()
    val selected=(0 until selectedArray.length()).map{selectedArray.optInt(it)}.toSet()
    val dice=s.getJSONArray("dice")
    val total=if(dice.length()>=2)dice.optInt(0)+dice.optInt(1) else 0
    val selectedTotal=selected.sum()
    val phase=s.optString("phase")

    SoloFrame(
        "SHUT THE BOX",
        "Roll → choose numbers totalling the dice → close.",
        "Score ${s.optInt("score",45)}"
    ){
        Column(
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ){
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                (1..5).forEach{n->
                    ShutNumberTile(
                        number=n,
                        open=n in openSet,
                        selected=n in selected,
                        enabled=phase=="choose",
                        onClick={onStateChange(GameDeckExtraEngine.shutToggle(stateJson,n))}
                    )
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                (6..9).forEach{n->
                    ShutNumberTile(
                        number=n,
                        open=n in openSet,
                        selected=n in selected,
                        enabled=phase=="choose",
                        onClick={onStateChange(GameDeckExtraEngine.shutToggle(stateJson,n))}
                    )
                }
            }

            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                CompactDie(if(dice.length()>0)dice.optInt(0) else 0)
                CompactDie(if(dice.length()>1)dice.optInt(1) else 0)
            }

            when(phase){
                "choose" -> {
                    Text(
                        "Selected $selectedTotal of $total",
                        fontWeight=FontWeight.Black,
                        color=if(selectedTotal==total)MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick={onStateChange(GameDeckExtraEngine.shutConfirm(stateJson))},
                        enabled=selected.isNotEmpty()&&selectedTotal==total
                    ){
                        Text("CLOSE ${selected.sorted().joinToString(" + ")}")
                    }
                }
                "roll" -> {
                    Text("Roll the dice to continue.", fontWeight=FontWeight.Bold)
                    Button(
                        onClick={
                            GameDeckSound.roll(sound)
                            onStateChange(GameDeckExtraEngine.shutRoll(stateJson,rngMode,seed))
                        }
                    ){Text("ROLL DICE",fontWeight=FontWeight.Black)}
                }
                else -> Text("No open combination matches the roll.",fontWeight=FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShutNumberTile(
    number:Int,
    open:Boolean,
    selected:Boolean,
    enabled:Boolean,
    onClick:()->Unit
){
    Surface(
        modifier=Modifier.size(50.dp),
        shape=RoundedCornerShape(12.dp),
        color=when{
            !open -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.38f)
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation=if(selected)5.dp else 1.dp,
        onClick=onClick,
        enabled=open&&enabled
    ){
        Box(contentAlignment=Alignment.Center){
            Text(
                if(open)number.toString() else "×",
                fontWeight=FontWeight.Black,
                style=MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun CompactDie(value:Int){
    Surface(
        modifier=Modifier.size(58.dp),
        shape=RoundedCornerShape(15.dp),
        color=MaterialTheme.colorScheme.surface,
        tonalElevation=5.dp
    ){
        Box(contentAlignment=Alignment.Center){
            Text(
                if(value in 1..6)value.toString() else "–",
                style=MaterialTheme.typography.headlineMedium,
                fontWeight=FontWeight.Black
            )
        }
    }
}

@Composable
private fun CodebreakerScreen(stateJson:String,onStateChange:(String)->Unit){
    val s=JSONObject(stateJson)
    val g=s.getJSONArray("guess")
    val hist=s.getJSONArray("history")
    val terminal=s.optString("winner").isNotBlank()

    SoloFrame(
        "CODEBREAKER",
        "Exact = right place  •  Near = wrong place",
        "${s.optInt("moves")} / 10 guesses"
    ){
        Column(
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(10.dp)
        ){
            Text(
                if(terminal)"The code is revealed below."
                else "Set four digits, then test your code.",
                style=MaterialTheme.typography.labelMedium,
                fontWeight=FontWeight.Bold
            )

            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                (0..3).forEach{i->
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        OutlinedButton(
                            onClick={onStateChange(GameDeckExtraEngine.codeAdjust(stateJson,i,1))},
                            enabled=!terminal
                        ){Text("+")}
                        Surface(
                            shape=RoundedCornerShape(14.dp),
                            color=MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation=4.dp
                        ){
                            Box(Modifier.size(50.dp),contentAlignment=Alignment.Center){
                                Text(
                                    g.optInt(i).toString(),
                                    fontWeight=FontWeight.Black,
                                    style=MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                        OutlinedButton(
                            onClick={onStateChange(GameDeckExtraEngine.codeAdjust(stateJson,i,-1))},
                            enabled=!terminal
                        ){Text("−")}
                    }
                }
            }

            Button(
                onClick={onStateChange(GameDeckExtraEngine.codeSubmit(stateJson))},
                enabled=!terminal
            ){Text("TRY CODE",fontWeight=FontWeight.Black)}

            if(hist.length()>0){
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement=Arrangement.spacedBy(5.dp)
                ){
                    Text("RECENT GUESSES",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Black)
                    val first=(hist.length()-4).coerceAtLeast(0)
                    for(i in hist.length()-1 downTo first){
                        val attempt=hist.getJSONObject(i)
                        val guess=attempt.getJSONArray("guess")
                        Surface(
                            Modifier.fillMaxWidth(),
                            RoundedCornerShape(12.dp),
                            color=MaterialTheme.colorScheme.surfaceVariant
                        ){
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=7.dp),
                                horizontalArrangement=Arrangement.SpaceBetween,
                                verticalAlignment=Alignment.CenterVertically
                            ){
                                Text(
                                    (0 until guess.length()).joinToString(" ") { guess.optInt(it).toString() },
                                    fontWeight=FontWeight.Black
                                )
                                Text(
                                    "Exact ${attempt.optInt("exact")}  •  Near ${attempt.optInt("near")}",
                                    style=MaterialTheme.typography.labelMedium,
                                    fontWeight=FontWeight.Bold
                                )
                            }
                        }
                    }
                    if(first>0)Text(
                        "+ $first earlier ${if(first==1)"guess" else "guesses"}",
                        style=MaterialTheme.typography.labelSmall
                    )
                }
            }

            if(terminal){
                val secret=s.getJSONArray("secret")
                Surface(
                    shape=RoundedCornerShape(16.dp),
                    color=MaterialTheme.colorScheme.primaryContainer
                ){
                    Text(
                        "CODE  ${(0 until secret.length()).joinToString(" ") { secret.optInt(it).toString() }}",
                        Modifier.padding(horizontal=16.dp,vertical=10.dp),
                        fontWeight=FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
internal fun SoloFrame(
    title: String,
    subtitle: String,
    detail: String,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Capture BoxWithConstraintsScope values before entering nested layout
        // receivers. This avoids ambiguous implicit-receiver resolution on
        // Compose/Kotlin versions used by MethodMesh.
        val availableHeight = maxHeight
        val footerHeight = 88.dp
        val contentHeight = (availableHeight - footerHeight).coerceAtLeast(180.dp)

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.fillMaxWidth().height(contentHeight),
                contentAlignment = Alignment.Center
            ) {
                content()
            }

            Surface(
                modifier = Modifier.fillMaxWidth().height(footerHeight),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(title, fontWeight = FontWeight.Black)
                        Text(subtitle, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(detail, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun SquareBoard(n:Int,cell: @Composable (Int, Dp) -> Unit){BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){val board=minOf(maxWidth,maxHeight);val side=board/n;Column(Modifier.size(board)){for(r in 0 until n)Row{for(c in 0 until n)cell(r*n+c,side)}}}}
