package com.example.methodmesh.modules.sampling

/**
 * Small, deliberately plain bundled English dictionary for random-word output.
 * It is capability data rather than a password-security claim. Future password
 * capabilities can replace or extend it with a reviewed high-entropy word list.
 */
object SamplingDictionary {
    const val ID = "methodmesh.en.basic"
    const val VERSION = "1.0.0"

    val words: List<String> = """
        acorn amber anchor apple apron arch arrow atlas autumn badge bamboo basin beach beacon berry birch bird biscuit blade blossom blue board boat bolt book bottle boulder bowl branch brass breeze brick bridge brook brush bucket button cabin cactus candle canyon card carpet carrot cedar chair chalk charm cherry chest circle clay cliff cloud clover coast cocoa comet compass coral cork cotton crane creek crown crystal cup dawn deck delta desert dew diamond dish dock door dove dragon drift drum dune dusk eagle earth echo elm ember engine feather fern field finch fire flag flame flask flower flute fog forest fork fountain fox frame frost garden gate gem ginger glass globe glove gold grape grass gravel grove gull hammer harbor hazel heart heath hill honey hook horizon horse ice island ivory jade jar jasmine jet jewel kettle key kite lake lantern leaf lemon lens light lime linen lion lock lotus maple marble marsh meadow melon metal mint mirror mist moon moss mountain mouse mug mushroom needle nest night oak ocean olive opal orange orchid otter owl paper peach pearl pebble pepper pine planet plum pond poppy quartz rabbit rain reed reef ribbon ridge river robin rock rocket root rose ruby sail salt sand scarlet sea seed shell shore silver sky slate snow soap solar sparrow spice spring star stone storm stream sun table teal thorn tide tiger timber topaz torch tower trail tree tulip valley velvet vine violet wave wheat willow wind wing winter wolf wood yarn zebra
        able active agile alert alive ample ancient apt arctic aware basic bright broad calm candid careful certain clear clever close cool crisp curious daily deep direct dry eager early easy even fair fast firm flat fresh gentle giant glad grand green happy hardy honest ideal keen kind large light little lively local loose lucid major mellow mild modern neat new noble open pale patient plain prime proud quick quiet rapid rare ready real rich ripe round safe sharp short simple small smart smooth soft solid spare steady still strong sunny sweet swift tall tidy tiny true vast warm whole wide wild wise young
        adapt admire agree allow answer arrive ask bake balance bend bind blend bloom bounce build carry carve catch change chase choose climb close collect color cook copy count craft cross dance decide dig divide draw dream drink drive drop enter explore feed fetch fill find float fold follow gather give glow grow guide hang hear help hold join jump keep knead know learn lift link listen live look make measure mend mix move open pack paint pass pick plant play pour pull push read rest ride roll run sail save search select send shape share shine sing sit sort spin split stack stand start step stitch store study swim take teach test think throw tie touch trace travel turn use walk wash watch weave weigh work write
        almond apricot avocado banana barley bean beet berry bread broccoli cabbage cake cashew celery cheese chestnut cinnamon coffee corn cracker cream cucumber date fig flour garlic guava herb kiwi lentil mango millet noodle oat onion papaya pea peanut pear pickle potato pumpkin radish rice rye sage sesame spinach squash tea thyme tomato vanilla walnut yam yoghurt
        alley avenue barn bay bridge camp castle cellar chapel city corner court farm fence garage hall hamlet harbor house lane library lodge market mill office orchard park path plaza port quarry road room school shed shop square station street studio temple town track tunnel village yard
        ankle arm back brow cheek chin ear elbow eye face finger foot hair hand head heel hip knee leg neck nose palm shoulder skin thumb toe tooth waist wrist
        ant badger bat bear bee beetle buffalo butterfly camel cat cobra crab crow deer dog dolphin donkey duck falcon fish frog goat goose hare hawk heron horse insect lamb lizard mole monkey moth mouse panda parrot penguin pig pigeon rabbit raven seal sheep snail spider swan trout turtle whale wren
        april august december february friday january july june march monday november october saturday september sunday thursday tuesday wednesday
        circle cone cube curve cylinder dot edge ellipse grid hexagon line oval plane point polygon prism pyramid ray rectangle ring sphere spiral square star triangle vector
        black bronze brown coral cream cyan gold gray green indigo ivory lilac lime magenta maroon navy ochre orange pink purple red silver teal turquoise violet white yellow
        north south east west center left right upper lower inner outer near far front rear above below beside across around between beyond inside outside under over
        alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu omicron pi rho sigma tau upsilon phi chi psi omega
        acid atom base beam cell charge circuit core current data density energy force gas heat ion laser liquid mass matter metal molecule motion neutron particle phase photon plasma proton pulse quantum solid spark spectrum vapor wave
        assay batch buffer culture control enzyme gel label marker medium plate reagent sample serum slide specimen standard stock tube vial well
        audit chain checksum commit evidence hash history ledger manifest origin proof record reference signature source stamp time token trace trail version
        cohort consent contact enrolment event followup household index interview participant protocol questionnaire randomisation roster site subject survey visit
        analyse classify compare compute detect estimate evaluate generate identify inspect map match measure model observe parse process rank sample score simulate sort summarize transform validate verify
    """.trimIndent()
        .split(Regex("\\s+"))
        .map { it.trim().lowercase() }
        .filter { it.matches(Regex("[a-z]+")) }
        .distinct()

    val sha256: String by lazy { SamplingProvenance.sha256(words.joinToString("\n")) }
}
