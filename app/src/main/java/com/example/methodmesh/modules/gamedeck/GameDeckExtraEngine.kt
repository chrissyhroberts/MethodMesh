package com.example.methodmesh.modules.gamedeck

import com.example.methodmesh.modules.chance.As100DiceSimulationMethod
import com.example.methodmesh.modules.chance.DiceSimulationFields
import org.json.JSONArray
import org.json.JSONObject

/** Additional compact games built on a shared JSON-state convention. */
object GameDeckExtraEngine {
    const val TIC_TAC_TOE = "tic_tac_toe"
    const val REVERSI = "reversi"
    const val NIM = "nim"
    const val LIGHTS_OUT = "lights_out"
    const val FIFTEEN = "fifteen_puzzle"
    const val MEMORY = "memory_pairs"
    const val SHUT_THE_BOX = "shut_the_box"
    const val CODEBREAKER = "codebreaker"
    const val TWENTY_FORTY_EIGHT = "twenty_forty_eight"
    const val DOTS_AND_BOXES = "dots_and_boxes"
    const val SUDOKU = "sudoku"
    const val TAKUZU = "takuzu"
    const val CHESS = "chess"
    const val GO_9X9 = "go_9x9"

    val games = listOf(
        TIC_TAC_TOE,
        REVERSI,
        NIM,
        LIGHTS_OUT,
        FIFTEEN,
        MEMORY,
        SHUT_THE_BOX,
        CODEBREAKER,
        TWENTY_FORTY_EIGHT,
        DOTS_AND_BOXES,
        SUDOKU,
        TAKUZU,
        CHESS,
        GO_9X9
    )
    fun isSupported(game: String) = game in games

    fun newStateJson(game: String, rngMode: String = "secure_random", seed: String = ""): String = when (game) {
        TIC_TAC_TOE -> JSONObject()
            .put("game", game).put("board", JSONArray(List(9) { 0 }))
            .put("turn", 1).put("winner", "").put("moves", 0).toString()
        REVERSI -> reversiInitial()
        NIM -> JSONObject().put("game", game).put("heaps", JSONArray(listOf(3, 5, 7)))
            .put("turn", 1).put("winner", "").put("moves", 0).toString()
        LIGHTS_OUT -> {
            val lights = MutableList(25) { 0 }
            repeat(12) { i ->
                val idx = chanceInt(25, rngMode, "$seed|lights|$i")
                toggleLights(lights, idx)
            }
            // Random legal presses can cancel one another. Do not hand the player
            // an already-solved board.
            if (lights.all { it == 0 }) toggleLights(lights, 12)
            JSONObject().put("game", game).put("lights", JSONArray(lights)).put("turn", 1)
                .put("winner", "").put("moves", 0).toString()
        }
        FIFTEEN -> fifteenInitial(rngMode, seed)
        MEMORY -> memoryInitial(rngMode, seed)
        SHUT_THE_BOX -> JSONObject().put("game", game).put("open", JSONArray((1..9).toList()))
            .put("dice", JSONArray()).put("turn", 1).put("winner", "").put("moves", 0)
            .put("phase", "roll").put("selected", JSONArray()).put("score", 45).toString()
        CODEBREAKER -> {
            val secret = (0 until 4).map { chanceInt(6, rngMode, "$seed|code|$it") + 1 }
            JSONObject().put("game", game).put("secret", JSONArray(secret)).put("guess", JSONArray(listOf(1,1,1,1)))
                .put("history", JSONArray()).put("turn", 1).put("winner", "").put("moves", 0).toString()
        }
        TWENTY_FORTY_EIGHT -> twentyFortyEightInitial(rngMode, seed)
        DOTS_AND_BOXES -> JSONObject()
            .put("game", game)
            .put("horizontal", JSONArray(List(20) { 0 }))
            .put("vertical", JSONArray(List(20) { 0 }))
            .put("boxes", JSONArray(List(16) { 0 }))
            .put("scores", JSONArray(listOf(0, 0)))
            .put("turn", 1)
            .put("winner", "")
            .put("moves", 0)
            .toString()
        SUDOKU -> GameDeckPuzzleEngine.sudokuInitial(rngMode, seed)
        TAKUZU -> GameDeckPuzzleEngine.takuzuInitial(rngMode, seed)
        CHESS -> GameDeckBoardEngine.chessInitial()
        GO_9X9 -> GameDeckBoardEngine.goInitial()
        else -> JSONObject().put("game", game).put("turn", 1).put("winner", "").put("moves", 0).toString()
    }

    // ---------- Tic-tac-toe ----------
    fun ticTacToeMove(stateJson: String, cell: Int): String {
        val s = JSONObject(stateJson); if (s.optString("winner").isNotBlank() || cell !in 0..8) return stateJson
        val b = IntArray(9) { s.getJSONArray("board").optInt(it) }; if (b[cell] != 0) return stateJson
        val p = s.optInt("turn", 1).coerceIn(1, 2); b[cell] = p
        val winner = tttWinner(b).let { if (it > 0) it.toString() else if (b.none { v -> v == 0 }) "draw" else "" }
        return JSONObject().put("game", TIC_TAC_TOE).put("board", JSONArray(b.toList()))
            .put("turn", if (winner.isBlank()) 3 - p else p).put("winner", winner).put("moves", s.optInt("moves") + 1).toString()
    }

    fun ticTacToeCpuChoice(stateJson: String, player: Int = 2): Int {
        val s = JSONObject(stateJson); val b = IntArray(9) { s.getJSONArray("board").optInt(it) }
        val legal = (0..8).filter { b[it] == 0 }; if (legal.isEmpty()) return -1
        for (c in legal) { val x=b.copyOf(); x[c]=player; if(tttWinner(x)==player) return c }
        val opp = 3-player
        for (c in legal) { val x=b.copyOf(); x[c]=opp; if(tttWinner(x)==opp) return c }
        if (4 in legal) return 4
        return listOf(0,2,6,8).firstOrNull { it in legal } ?: legal.first()
    }

    private fun tttWinner(b: IntArray): Int {
        val lines = arrayOf(intArrayOf(0,1,2),intArrayOf(3,4,5),intArrayOf(6,7,8),intArrayOf(0,3,6),intArrayOf(1,4,7),intArrayOf(2,5,8),intArrayOf(0,4,8),intArrayOf(2,4,6))
        return lines.firstNotNullOfOrNull { l -> b[l[0]].takeIf { it != 0 && it == b[l[1]] && it == b[l[2]] } } ?: 0
    }

    // ---------- Reversi ----------
    private fun reversiInitial(): String {
        val b = IntArray(64)
        b[27]=2; b[28]=1; b[35]=1; b[36]=2
        return JSONObject()
            .put("game", REVERSI)
            .put("board", JSONArray(b.toList()))
            .put("turn",1)
            .put("winner","")
            .put("moves",0)
            .put("passes",0)
            .toString()
    }

    fun reversiLegalMoves(stateJson: String, player: Int = JSONObject(stateJson).optInt("turn",1)): List<Int> {
        val s = JSONObject(stateJson)
        val board = s.getJSONArray("board")
        val b = IntArray(64) { board.optInt(it) }
        return (0 until 64).filter { b[it] == 0 && reversiFlips(b, it, player).isNotEmpty() }
    }

    fun reversiMove(stateJson: String, cell: Int): String {
        val s=JSONObject(stateJson)
        if(s.optString("winner").isNotBlank()) return stateJson
        val b=IntArray(64){s.getJSONArray("board").optInt(it)}
        val p=s.optInt("turn",1).coerceIn(1,2)
        val flips=reversiFlips(b,cell,p)
        if(flips.isEmpty()) return stateJson

        b[cell]=p
        flips.forEach{b[it]=p}
        val other=3-p
        val otherLegal=(0 until 64).any{b[it]==0&&reversiFlips(b,it,other).isNotEmpty()}
        val selfLegal=(0 until 64).any{b[it]==0&&reversiFlips(b,it,p).isNotEmpty()}
        val finished=!otherLegal&&!selfLegal
        val next=if(otherLegal) other else p
        val c1=b.count{it==1}
        val c2=b.count{it==2}
        val winner=if(!finished) "" else when { c1>c2->"1"; c2>c1->"2"; else->"draw" }
        val implicitPasses = s.optInt("passes",0) + if(!finished && !otherLegal && selfLegal) 1 else 0

        return JSONObject()
            .put("game",REVERSI)
            .put("board",JSONArray(b.toList()))
            .put("turn",next)
            .put("winner",winner)
            .put("moves",s.optInt("moves")+1)
            .put("passes",implicitPasses)
            .toString()
    }

    /**
     * Resolve an imported/headless Reversi state whose current seat has no move.
     * Valid engine-produced states normally resolve this implicitly after the
     * preceding move, but the explicit operation makes replay/simulation robust.
     */
    fun reversiResolveBlockedTurn(stateJson: String): String {
        val s = JSONObject(stateJson)
        if (s.optString("winner").isNotBlank()) return stateJson

        val p = s.optInt("turn", 1).coerceIn(1, 2)
        if (reversiLegalMoves(stateJson, p).isNotEmpty()) return stateJson

        val other = 3 - p
        if (reversiLegalMoves(stateJson, other).isNotEmpty()) {
            return s.put("turn", other)
                .put("passes", s.optInt("passes", 0) + 1)
                .toString()
        }

        val board = s.getJSONArray("board")
        val c1 = (0 until 64).count { board.optInt(it) == 1 }
        val c2 = (0 until 64).count { board.optInt(it) == 2 }
        val winner = when {
            c1 > c2 -> "1"
            c2 > c1 -> "2"
            else -> "draw"
        }
        return s.put("winner", winner).toString()
    }

    fun reversiCpuChoice(stateJson: String, player: Int = 2): Int {
        val legal=reversiLegalMoves(stateJson,player); if(legal.isEmpty()) return -1
        val s=JSONObject(stateJson); val b=IntArray(64){s.getJSONArray("board").optInt(it)}
        return legal.maxByOrNull { cell ->
            val corner = if(cell in setOf(0,7,56,63)) 100 else 0
            corner + reversiFlips(b,cell,player).size
        } ?: legal.first()
    }

    private fun reversiFlips(b:IntArray, cell:Int, p:Int):List<Int>{
        if(cell !in 0..63 || b[cell]!=0) return emptyList(); val r=cell/8; val c=cell%8; val out=mutableListOf<Int>(); val opp=3-p
        for(dr in -1..1) for(dc in -1..1){ if(dr==0&&dc==0) continue; var rr=r+dr; var cc=c+dc; val line=mutableListOf<Int>()
            while(rr in 0..7&&cc in 0..7&&b[rr*8+cc]==opp){line+=rr*8+cc;rr+=dr;cc+=dc}
            if(line.isNotEmpty()&&rr in 0..7&&cc in 0..7&&b[rr*8+cc]==p) out+=line
        }; return out
    }

    // ---------- Nim ----------
    fun nimMove(stateJson:String, heap:Int, take:Int):String{
        val s=JSONObject(stateJson); if(s.optString("winner").isNotBlank()) return stateJson
        val h=IntArray(3){s.getJSONArray("heaps").optInt(it)}; val p=s.optInt("turn",1).coerceIn(1,2)
        if(heap !in 0..2 || take !in 1..h[heap]) return stateJson; h[heap]-=take
        val winner=if(h.all{it==0}) p.toString() else ""
        return JSONObject().put("game",NIM).put("heaps",JSONArray(h.toList())).put("turn",if(winner.isBlank()) 3-p else p).put("winner",winner).put("moves",s.optInt("moves")+1).toString()
    }

    fun nimCpuChoice(stateJson:String):Pair<Int,Int>{
        val h=IntArray(3){JSONObject(stateJson).getJSONArray("heaps").optInt(it)}; val xor=h.fold(0){a,v->a xor v}
        if(xor!=0) for(i in h.indices){val target=h[i] xor xor; if(target<h[i]) return i to (h[i]-target)}
        val i=h.indexOfFirst{it>0}; return if(i>=0) i to 1 else -1 to 0
    }

    // ---------- Lights Out ----------
    fun lightsMove(stateJson:String, cell:Int):String{
        val s=JSONObject(stateJson); if(s.optString("winner").isNotBlank()||cell !in 0..24) return stateJson
        val l=MutableList(25){s.getJSONArray("lights").optInt(it)}; toggleLights(l,cell)
        return JSONObject().put("game",LIGHTS_OUT).put("lights",JSONArray(l)).put("turn",1).put("winner",if(l.all{it==0})"cleared" else "").put("moves",s.optInt("moves")+1).toString()
    }
    private fun toggleLights(l:MutableList<Int>,cell:Int){val r=cell/5;val c=cell%5;listOf(r to c,r-1 to c,r+1 to c,r to c-1,r to c+1).forEach{(rr,cc)->if(rr in 0..4&&cc in 0..4){val i=rr*5+cc;l[i]=1-l[i]}}}

    // ---------- Fifteen ----------
    private fun fifteenInitial(rngMode:String,seed:String):String{
        // Start from the actual solved 15-puzzle state. Scrambling exclusively via
        // legal blank moves guarantees that every generated board is solvable.
        val a = MutableList(16) { index -> if (index == 15) 0 else index + 1 }
        var blank = 15
        var previousBlank = -1
        repeat(160){i->
            val r=blank/4
            val c=blank%4
            var options=listOf(blank-4,blank+4,blank-1,blank+1).filter{
                it in 0..15 &&
                    kotlin.math.abs(it/4-r)+kotlin.math.abs(it%4-c)==1
            }
            // Avoid immediate backtracking when there is another legal choice. This
            // gives a better scramble without affecting the solvability guarantee.
            if (options.size > 1 && previousBlank in options) {
                options = options.filter { it != previousBlank }
            }
            val pick=options[chanceInt(options.size,rngMode,"$seed|15|$i")]
            a[blank]=a[pick]
            a[pick]=0
            previousBlank=blank
            blank=pick
        }
        val solved = (0..14).all { a[it] == it + 1 } && a[15] == 0
        if (solved) {
            // One final legal move preserves solvability while avoiding a trivial
            // generated puzzle.
            val neighbor = if (blank >= 4) blank - 4 else blank + 4
            a[blank] = a[neighbor]
            a[neighbor] = 0
        }
        return JSONObject().put("game",FIFTEEN).put("tiles",JSONArray(a)).put("turn",1).put("winner","").put("moves",0).toString()
    }
    fun fifteenMove(stateJson:String,cell:Int):String{
        val s=JSONObject(stateJson)
        if(s.optString("winner").isNotBlank()) return stateJson
        val a=MutableList(16){s.getJSONArray("tiles").optInt(it)}
        val blank=a.indexOf(0)
        if(cell !in 0..15||kotlin.math.abs(cell/4-blank/4)+kotlin.math.abs(cell%4-blank%4)!=1)return stateJson
        a[blank]=a[cell]
        a[cell]=0
        val solved=(0..14).all{a[it]==it+1}&&a[15]==0
        return JSONObject()
            .put("game",FIFTEEN)
            .put("tiles",JSONArray(a))
            .put("turn",1)
            .put("winner",if(solved)"cleared" else "")
            .put("moves",s.optInt("moves")+1)
            .toString()
    }

    // ---------- Memory pairs ----------
    private fun memoryInitial(rngMode:String,seed:String):String{
        val cards=(1..8).flatMap{listOf(it,it)}.toMutableList()
        for(i in cards.lastIndex downTo 1){
            val j=chanceInt(i+1,rngMode,"$seed|memory|$i")
            val t=cards[i];cards[i]=cards[j];cards[j]=t
        }
        return JSONObject()
            .put("game",MEMORY)
            .put("cards",JSONArray(cards))
            .put("matched",JSONArray(List(16){0}))
            .put("face_up",JSONArray())
            .put("scores",JSONArray(listOf(0,0)))
            .put("turn",1)
            .put("winner","")
            .put("moves",0)
            .put("phase","choose")
            .toString()
    }

    /**
     * Flip one card. A non-matching second card enters a visible `resolve` phase
     * instead of disappearing in the same state transition.
     */
    fun memoryFlip(stateJson:String,cell:Int):String{
        val s=JSONObject(stateJson)
        if(s.optString("winner").isNotBlank()||cell !in 0..15||s.optString("phase","choose")!="choose") return stateJson
        val matched=MutableList(16){s.getJSONArray("matched").optInt(it)}
        if(matched[cell]==1) return stateJson

        val face=mutableListOf<Int>()
        val old=s.getJSONArray("face_up")
        for(i in 0 until old.length()) face+=old.optInt(i)
        if(cell in face || face.size >= 2) return stateJson

        face+=cell
        s.put("face_up",JSONArray(face))
        if(face.size<2) return s.toString()

        val cards=s.getJSONArray("cards")
        val scores=IntArray(2){s.getJSONArray("scores").optInt(it)}
        val p=s.optInt("turn",1).coerceIn(1,2)
        val moves=s.optInt("moves")+1

        return if(cards.optInt(face[0])==cards.optInt(face[1])){
            matched[face[0]]=1
            matched[face[1]]=1
            scores[p-1]++
            val done=matched.all{it==1}
            val winner=if(!done)"" else when{
                scores[0]>scores[1]->"1"
                scores[1]>scores[0]->"2"
                else->"draw"
            }
            JSONObject()
                .put("game",MEMORY).put("cards",cards)
                .put("matched",JSONArray(matched)).put("face_up",JSONArray())
                .put("scores",JSONArray(scores.toList())).put("turn",p)
                .put("winner",winner).put("moves",moves).put("phase","choose")
                .toString()
        } else {
            s.put("face_up",JSONArray(face))
                .put("moves",moves)
                .put("phase","resolve")
                .put("next_turn",3-p)
                .toString()
        }
    }

    /** Hide a visible mismatch and pass the turn after the reveal delay. */
    fun memoryResolve(stateJson:String):String{
        val s=JSONObject(stateJson)
        if(s.optString("phase")!="resolve") return stateJson
        val next=s.optInt("next_turn",3-s.optInt("turn",1)).coerceIn(1,2)
        s.put("face_up",JSONArray())
            .put("turn",next)
            .put("phase","choose")
            .remove("next_turn")
        return s.toString()
    }

    // ---------- Shut the Box ----------
    fun shutRoll(stateJson:String,rngMode:String,seed:String):String{
        val s=JSONObject(stateJson);if(s.optString("winner").isNotBlank()||s.optString("phase")!="roll")return stateJson
        val move=s.optInt("moves");val d1=chanceInt(6,rngMode,"$seed|shut|$move|a")+1;val d2=chanceInt(6,rngMode,"$seed|shut|$move|b")+1;val total=d1+d2
        val open=mutableListOf<Int>();val a=s.getJSONArray("open");for(i in 0 until a.length())open+=a.optInt(i)
        val possible=(1 until (1 shl open.size)).any{mask->var sum=0;for(i in open.indices)if(mask and (1 shl i)!=0)sum+=open[i];sum==total}
        s.put("dice",JSONArray(listOf(d1,d2))).put("selected",JSONArray()).put("phase",if(possible)"choose" else "done")
        if(!possible)s.put("winner","stuck")
        return s.toString()
    }
    fun shutToggle(stateJson:String,n:Int):String{
        val s=JSONObject(stateJson);if(s.optString("phase")!="choose")return stateJson
        val open=mutableListOf<Int>();val a=s.getJSONArray("open");for(i in 0 until a.length())open+=a.optInt(i);if(n !in open)return stateJson
        val selected=mutableListOf<Int>();val q=s.getJSONArray("selected");for(i in 0 until q.length())selected+=q.optInt(i)
        if(n in selected)selected.remove(n) else selected+=n
        s.put("selected",JSONArray(selected));return s.toString()
    }
    fun shutConfirm(stateJson:String):String{
        val s=JSONObject(stateJson);if(s.optString("phase")!="choose")return stateJson
        val dice=s.getJSONArray("dice");val total=dice.optInt(0)+dice.optInt(1);val selected=mutableListOf<Int>();val q=s.getJSONArray("selected");for(i in 0 until q.length())selected+=q.optInt(i);if(selected.isEmpty()||selected.sum()!=total)return stateJson
        val open=mutableListOf<Int>();val a=s.getJSONArray("open");for(i in 0 until a.length())open+=a.optInt(i);open.removeAll(selected.toSet())
        val winner=if(open.isEmpty())"cleared" else ""
        return JSONObject().put("game",SHUT_THE_BOX).put("open",JSONArray(open)).put("dice",JSONArray()).put("selected",JSONArray()).put("turn",1).put("winner",winner).put("moves",s.optInt("moves")+1).put("phase","roll").put("score",open.sum()).toString()
    }

    // ---------- Codebreaker ----------
    fun codeAdjust(stateJson:String,pos:Int,delta:Int):String{val s=JSONObject(stateJson);if(s.optString("winner").isNotBlank()||pos !in 0..3)return stateJson;val g=MutableList(4){s.getJSONArray("guess").optInt(it,1)};g[pos]=((g[pos]-1+delta)%6+6)%6+1;s.put("guess",JSONArray(g));return s.toString()}
    fun codeSubmit(stateJson:String):String{val s=JSONObject(stateJson);if(s.optString("winner").isNotBlank())return stateJson;val sec=List(4){s.getJSONArray("secret").optInt(it)};val g=List(4){s.getJSONArray("guess").optInt(it)};val exact=(0..3).count{sec[it]==g[it]};val common=(1..6).sumOf{v->minOf(sec.count{it==v},g.count{it==v})};val near=common-exact;val hist=s.getJSONArray("history");hist.put(JSONObject().put("guess",JSONArray(g)).put("exact",exact).put("near",near));val moves=s.optInt("moves")+1;val winner=if(exact==4)"cleared" else if(moves>=10)"failed" else "";s.put("history",hist).put("moves",moves).put("winner",winner);return s.toString()}


    // ---------- 2048 ----------
    private fun twentyFortyEightInitial(rngMode:String,seed:String):String{
        var cells=MutableList(16){0}
        cells=spawn2048(cells,rngMode,"$seed|2048|0")
        cells=spawn2048(cells,rngMode,"$seed|2048|1")
        return JSONObject()
            .put("game",TWENTY_FORTY_EIGHT)
            .put("cells",JSONArray(cells))
            .put("turn",1)
            .put("winner","")
            .put("moves",0)
            .put("score",0)
            .toString()
    }

    fun twentyFortyEightMove(
        stateJson:String,
        direction:String,
        rngMode:String,
        seed:String
    ):String{
        val s=JSONObject(stateJson)
        if(s.optString("winner").isNotBlank()) return stateJson
        val before=MutableList(16){s.getJSONArray("cells").optInt(it)}
        val after=before.toMutableList()
        var gained=0

        fun compress(line:List<Int>):List<Int>{
            val values=line.filter{it!=0}.toMutableList()
            val merged=mutableListOf<Int>()
            var i=0
            while(i<values.size){
                if(i+1<values.size&&values[i]==values[i+1]){
                    val v=values[i]*2
                    merged+=v
                    gained+=v
                    i+=2
                }else{
                    merged+=values[i]
                    i+=1
                }
            }
            while(merged.size<4) merged+=0
            return merged
        }

        when(direction.lowercase()){
            "left" -> for(r in 0..3){
                val line=compress((0..3).map{c->before[r*4+c]})
                for(c in 0..3)after[r*4+c]=line[c]
            }
            "right" -> for(r in 0..3){
                val line=compress((3 downTo 0).map{c->before[r*4+c]})
                for(c in 0..3)after[r*4+(3-c)]=line[c]
            }
            "up" -> for(c in 0..3){
                val line=compress((0..3).map{r->before[r*4+c]})
                for(r in 0..3)after[r*4+c]=line[r]
            }
            "down" -> for(c in 0..3){
                val line=compress((3 downTo 0).map{r->before[r*4+c]})
                for(r in 0..3)after[(3-r)*4+c]=line[r]
            }
            else -> return stateJson
        }

        if(after==before) return stateJson

        val moves=s.optInt("moves")+1
        val spawned=spawn2048(after,rngMode,"$seed|2048|${moves+1}")
        val maxTile=spawned.maxOrNull()?:0
        val winner=when{
            maxTile>=2048 -> "cleared"
            !has2048Move(spawned) -> "stuck"
            else -> ""
        }
        return JSONObject()
            .put("game",TWENTY_FORTY_EIGHT)
            .put("cells",JSONArray(spawned))
            .put("turn",1)
            .put("winner",winner)
            .put("moves",moves)
            .put("score",s.optInt("score")+gained)
            .toString()
    }

    private fun spawn2048(
        input:List<Int>,
        rngMode:String,
        seed:String
    ):MutableList<Int>{
        val cells=input.toMutableList()
        val empty=cells.indices.filter{cells[it]==0}
        if(empty.isEmpty()) return cells
        val slot=empty[chanceInt(empty.size,rngMode,"$seed|slot")]
        val value=if(chanceInt(10,rngMode,"$seed|value")==0)4 else 2
        cells[slot]=value
        return cells
    }

    private fun has2048Move(cells:List<Int>):Boolean{
        if(cells.any{it==0}) return true
        for(r in 0..3)for(c in 0..3){
            val i=r*4+c
            if(c<3&&cells[i]==cells[i+1])return true
            if(r<3&&cells[i]==cells[i+4])return true
        }
        return false
    }

    // ---------- Dots & Boxes ----------
    fun dotsBoxesMove(
        stateJson:String,
        orientation:String,
        edge:Int
    ):String{
        val s=JSONObject(stateJson)
        if(s.optString("winner").isNotBlank())return stateJson
        val horizontal=MutableList(20){s.getJSONArray("horizontal").optInt(it)}
        val vertical=MutableList(20){s.getJSONArray("vertical").optInt(it)}
        val boxes=MutableList(16){s.getJSONArray("boxes").optInt(it)}
        val scores=IntArray(2){s.getJSONArray("scores").optInt(it)}
        val player=s.optInt("turn",1).coerceIn(1,2)

        when(orientation.lowercase()){
            "h" -> {
                if(edge !in 0..19||horizontal[edge]!=0)return stateJson
                horizontal[edge]=player
            }
            "v" -> {
                if(edge !in 0..19||vertical[edge]!=0)return stateJson
                vertical[edge]=player
            }
            else -> return stateJson
        }

        var completed=0
        for(r in 0..3)for(c in 0..3){
            val box=r*4+c
            if(boxes[box]!=0)continue
            val top=horizontal[r*4+c]!=0
            val bottom=horizontal[(r+1)*4+c]!=0
            val left=vertical[r*5+c]!=0
            val right=vertical[r*5+c+1]!=0
            if(top&&bottom&&left&&right){
                boxes[box]=player
                scores[player-1]+=1
                completed+=1
            }
        }

        val moves=s.optInt("moves")+1
        val finished=moves>=40
        val winner=if(!finished)"" else when{
            scores[0]>scores[1]->"1"
            scores[1]>scores[0]->"2"
            else->"draw"
        }
        val nextTurn=if(finished)player else if(completed>0)player else 3-player

        return JSONObject()
            .put("game",DOTS_AND_BOXES)
            .put("horizontal",JSONArray(horizontal))
            .put("vertical",JSONArray(vertical))
            .put("boxes",JSONArray(boxes))
            .put("scores",JSONArray(scores.toList()))
            .put("turn",nextTurn)
            .put("winner",winner)
            .put("moves",moves)
            .toString()
    }

    fun resultDetail(stateJson:String):String{
        val s=JSONObject(stateJson)
        return when(s.optString("game")){
            REVERSI -> {
                val b=s.getJSONArray("board")
                "P1 ${(0 until 64).count{b.optInt(it)==1}} : ${(0 until 64).count{b.optInt(it)==2}} P2"
            }
            MEMORY -> {
                val q=s.getJSONArray("scores")
                "Pairs  P1 ${q.optInt(0)} : ${q.optInt(1)} P2"
            }
            NIM -> "Last counter wins"
            LIGHTS_OUT, FIFTEEN -> "Solved in ${s.optInt("moves")} moves"
            SHUT_THE_BOX -> "Score ${s.optInt("score")}"
            CODEBREAKER -> "${s.optInt("moves")} guesses"
            TWENTY_FORTY_EIGHT -> {
                val a=s.getJSONArray("cells")
                var best=0
                for(i in 0 until a.length()){
                    best=maxOf(best,a.optInt(i))
                }
                "Score ${s.optInt("score")} • best $best"
            }
            DOTS_AND_BOXES -> {
                val q=s.getJSONArray("scores")
                "Boxes  P1 ${q.optInt(0)} : ${q.optInt(1)} P2"
            }
            SUDOKU -> "${GameDeckPuzzleDifficulty.label(s.optString("difficulty"))} • ${s.optInt("clues")} clues • ${s.optInt("moves")} entries"
            TAKUZU -> "${GameDeckPuzzleDifficulty.label(s.optString("difficulty"))} • ${s.optInt("clues")} clues • ${s.optInt("moves")} entries"
            CHESS -> "${s.optInt("moves")} half-moves"
            GO_9X9 -> {
                val black = s.optDouble("black_score", Double.NaN)
                val white = s.optDouble("white_score", Double.NaN)
                if (black.isNaN() || white.isNaN()) {
                    val captures = s.optJSONArray("captures") ?: JSONArray()
                    "Captures • Black ${captures.optInt(0)} : ${captures.optInt(1)} White"
                } else {
                    "Area score • Black ${"%.1f".format(black)} : ${"%.1f".format(white)} White"
                }
            }
            else -> "${s.optInt("moves")} moves"
        }
    }

    private fun chanceInt(bound:Int,rngMode:String,seed:String):Int{if(bound<=1)return 0;val out=As100DiceSimulationMethod.generate(mapOf("expression" to "d$bound","roll_count" to "1","player_count" to "1","history_output" to "false","rng_mode" to rngMode,"seed" to seed,"animation_mode" to "off"));return ((out[DiceSimulationFields.TOTAL]?.toIntOrNull()?:1)-1).coerceIn(0,bound-1)}
}
