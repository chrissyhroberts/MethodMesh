package com.example.methodmesh.modules.gamedeck

internal enum class GameDeckShelfSection(val label: String, val subtitle: String) {
    TABLETOP("TABLETOP", "Pass-and-play and CPU opponents"),
    PUZZLES("PUZZLES", "Short solo challenges"),
    LAB("GAME LAB", "Simulation and your local play record")
}

internal data class GameDeckGameInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val section: GameDeckShelfSection,
    val objective: String,
    val steps: List<String>,
    val tip: String
)

internal object GameDeckUxCatalog {
    val games = listOf(
        GameDeckGameInfo(
            GameDeckEngine.CONNECT_FOUR, "Connect Four",
            "Drop counters and make four in a row", "2P", GameDeckShelfSection.TABLETOP,
            "Be first to connect four of your counters horizontally, vertically or diagonally.",
            listOf(
                "The glowing player rail shows whose turn it is.",
                "Tap a column, or drag the active counter from the supply into a column.",
                "Counters fall to the lowest free space."
            ),
            "Both colour and shape identify the players."
        ),
        GameDeckGameInfo(
            GameDeckEngine.SNAKES_AND_LADDERS, "Snakes & Ladders",
            "Roll, move, climb and slide", "2P", GameDeckShelfSection.TABLETOP,
            "Reach square 100 before the other player.",
            listOf(
                "Press ROLL on your own player rail.",
                "After the die settles, press the move button or tap the highlighted destination.",
                "If you land on a ladder or snake, confirm the jump."
            ),
            "The game separates the random roll from your physical move so every action stays visible."
        ),
        GameDeckGameInfo(
            GameDeckEngine.MANCALA, "Mancala",
            "Sow beans around the board", "1P/2P", GameDeckShelfSection.TABLETOP,
            "Finish with more beans in your store than your opponent.",
            listOf(
                "Tap one of the non-empty pits on your side.",
                "Its beans are sown one by one around the board.",
                "Your store is the long pit at your end."
            ),
            "Use MODE to switch between CPU and two-player play; changing mode starts a fresh game."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.TIC_TAC_TOE, "Tic Tac Toe",
            "Quick three-in-a-row tactics", "1P/2P", GameDeckShelfSection.TABLETOP,
            "Make a row, column or diagonal of three symbols.",
            listOf(
                "The active player rail shows whose turn it is.",
                "Tap any empty square.",
                "Three matching symbols in a line wins."
            ),
            "MODE switches between CPU and two-player play."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.REVERSI, "Reversi",
            "Flip discs and control the board", "1P/2P", GameDeckShelfSection.TABLETOP,
            "Finish with more discs showing your side.",
            listOf(
                "Small dots mark your legal moves.",
                "Tap a dot to trap one or more opponent discs between your new disc and an existing disc.",
                "Trapped discs flip to your side. If you have no move, the turn passes."
            ),
            "Corners are especially valuable because they cannot be flipped."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.NIM, "Nim",
            "Take counters from one row", "1P/2P", GameDeckShelfSection.TABLETOP,
            "Take the final counter.",
            listOf(
                "On your turn choose exactly one row.",
                "Tap a counter to remove it and every counter to its right in that row.",
                "Players alternate until no counters remain."
            ),
            "The simple rule hides a strong mathematical strategy."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.MEMORY, "Memory Pairs",
            "Find matching pairs", "2P", GameDeckShelfSection.TABLETOP,
            "Collect more matching pairs than the other player.",
            listOf(
                "Tap one face-down card, then a second.",
                "A matching pair stays visible and you keep the turn.",
                "A mismatch remains visible briefly, then flips back and the turn passes."
            ),
            "Matched cards stay face-up, so only the still-hidden cards need remembering."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.CHESS, "Chess",
            "Legal chess with a lightweight CPU", "CPU/2P", GameDeckShelfSection.TABLETOP,
            "Checkmate the opposing king.",
            listOf(
                "Tap one of your pieces, then tap a highlighted legal destination.",
                "Use MODE: CPU / MODE: 2P before the first move.",
                "Castling, en passant, automatic queen promotion, check, checkmate, stalemate and the 50-move draw are supported."
            ),
            "The CPU is intentionally modest: it values material, checks and simple replies rather than trying to be a tournament engine."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.GO_9X9, "Go 9×9",
            "Compact Go with capture, ko, pass and area scoring", "CPU/2P", GameDeckShelfSection.TABLETOP,
            "Control more board area than your opponent after both players pass.",
            listOf(
                "Black plays first. Tap a legal intersection to place a stone.",
                "Groups with no liberties are captured. Suicide and immediate simple-ko recapture are blocked.",
                "Two consecutive passes end the game; area scoring uses 6.5 komi for White."
            ),
            "The 9×9 board keeps games short enough for tabletop use while exercising the complete capture/liberty architecture."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.DOTS_AND_BOXES, "Dots & Boxes",
            "Draw edges and claim completed squares", "2P", GameDeckShelfSection.TABLETOP,
            "Finish with more completed boxes than the other player.",
            listOf(
                "Tap any unclaimed edge between two dots.",
                "If your edge completes a box, that box becomes yours and you play again.",
                "If it does not complete a box, the turn passes."
            ),
            "Late in the game, a single edge can hand your opponent a whole chain of boxes."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.SHUT_THE_BOX, "Shut the Box",
            "Dice, arithmetic and risk", "DICE", GameDeckShelfSection.TABLETOP,
            "Close as many numbered tiles as possible; lower remaining score is better.",
            listOf(
                "Roll the two dice.",
                "Select any open numbers whose total equals the dice total.",
                "Press CLOSE. If no combination is possible, the run ends."
            ),
            "Selected numbers show your running total before you commit."
        ),
        GameDeckGameInfo(
            GameDeckEngine.MINESWEEPER, "Minesweeper",
            "No-guess procedural minefields", "LAB", GameDeckShelfSection.PUZZLES,
            "Reveal every safe square without opening a mine.",
            listOf(
                "Choose a board preset, then reveal a first square.",
                "Numbers show how many mines touch that square.",
                "Use FLAG mode or long-press to mark suspected mines."
            ),
            "GameLab attempts to generate a board that its solver can complete without guessing."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.LIGHTS_OUT, "Lights Out",
            "Switch every light off", "PUZZLE", GameDeckShelfSection.PUZZLES,
            "Turn the whole 5×5 board dark.",
            listOf(
                "Tap any light.",
                "That light and its orthogonal neighbours toggle together.",
                "Continue until no lights remain on."
            ),
            "Every generated board is created by legal presses, so it is solvable."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.FIFTEEN, "15 Puzzle",
            "Slide tiles into numerical order", "PUZZLE", GameDeckShelfSection.PUZZLES,
            "Arrange 1–15 in order with the empty space at the bottom-right.",
            listOf(
                "Only a tile beside the empty space can move.",
                "Tap a neighbouring tile to slide it into the gap.",
                "Continue until all fifteen numbers are ordered."
            ),
            "The scramble is made from legal moves, so every generated board is solvable."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.SUDOKU, "Sudoku",
            "Unique procedural grids with selectable hardness", "PUZZLE", GameDeckShelfSection.PUZZLES,
            "Fill every row, column and 3×3 box with the digits 1–9 exactly once.",
            listOf(
                "Tap a blank square and choose a digit from the keypad.",
                "Duplicate entries in a row, column or box are highlighted.",
                "Before your first move, tap the difficulty control to generate Easy, Medium, Hard or Expert."
            ),
            "Every accepted puzzle is checked for a unique solution before play."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.TAKUZU, "Takuzu",
            "Binary logic: balance, triples and uniqueness", "PUZZLE", GameDeckShelfSection.PUZZLES,
            "Complete the 6×6 grid using 0 and 1 while satisfying all Takuzu rules.",
            listOf(
                "Tap a blank cell to cycle empty → 0 → 1.",
                "Each row and column needs three 0s and three 1s, with no run of three identical symbols.",
                "Completed rows and columns must also be unique."
            ),
            "Like Sudoku, each generated puzzle is retained only if the solver confirms one solution."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.TWENTY_FORTY_EIGHT, "2048",
            "Slide and merge equal-number tiles", "PUZZLE", GameDeckShelfSection.PUZZLES,
            "Build a 2048 tile before the board runs out of legal moves.",
            listOf(
                "Swipe the board or use the four visible arrow controls.",
                "All tiles slide in that direction; equal neighbours merge once.",
                "A new 2 or occasional 4 appears after every successful move."
            ),
            "Keep your largest tiles near a corner and avoid breaking your ordering."
        ),
        GameDeckGameInfo(
            GameDeckExtraEngine.CODEBREAKER, "Codebreaker",
            "Deduce a hidden four-digit code", "LOGIC", GameDeckShelfSection.PUZZLES,
            "Find the four digits in at most ten guesses.",
            listOf(
                "Each digit is from 1 to 6; repeats are allowed.",
                "Set four digits and press TRY CODE.",
                "Exact means right digit in the right place. Near means right digit in the wrong place."
            ),
            "Your recent guesses stay on screen so you can reason from previous clues."
        ),
        GameDeckGameInfo(
            GAMEDECK_SIMULATOR, "CPU Arena",
            "Compare automated players over 100 games", "SIM", GameDeckShelfSection.LAB,
            "Explore how different CPU policies perform against one another.",
            listOf(
                "Choose a game.",
                "Choose Agent A and Agent B difficulty profiles.",
                "Run 100 matches; agents alternate seats so first-player advantage is visible."
            ),
            "Invalid or aborted simulations are reported separately instead of being counted as draws."
        ),
        GameDeckGameInfo(
            GAMEDECK_STATS, "Player Record",
            "Your local play history and exposure", "STATS", GameDeckShelfSection.LAB,
            "See what you have played and your recorded personal results.",
            listOf(
                "Finished sessions are stored locally on this device.",
                "Two-player shared-table games are not silently assigned to Player 1.",
                "Domain exposure counts what you have played; it is not a competence score."
            ),
            "No account or network connection is required."
        )
    )

    fun info(game: String): GameDeckGameInfo? = games.firstOrNull { it.id == game }
    fun section(section: GameDeckShelfSection): List<GameDeckGameInfo> =
        games.filter { it.section == section }
}
