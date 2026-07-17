package com.bettermifitness.sync.health

/**
 * Maps Mi Fitness sport / workout category strings (from decompiled
 * [FitnessPersistKey] and sport report `category` / `sport_type`) to
 * official Health Connect exercise types and HealthKit workout activity types.
 *
 * Only uses platform types that exist for third-party write (no invented types).
 * Unknown Mi sports fall back to "other" on both platforms.
 *
 * HC ints: androidx.health.connect.client.records.ExerciseSessionRecord
 * HK ints: HKWorkoutActivityType raw values (stable NSUInteger)
 */
object SportTypeMapper {

    data class Mapping(
        /** Human title for Health record (title field). */
        val title: String,
        /** Health Connect ExerciseSessionRecord.EXERCISE_TYPE_*. */
        val healthConnectType: Int,
        /** HealthKit HKWorkoutActivityType raw value. */
        val healthKitType: Long,
    )

    // Health Connect (connect-client 1.1 / platform constants from APK)
    private const val HC_OTHER = 0
    private const val HC_BADMINTON = 2
    private const val HC_BASEBALL = 4
    private const val HC_BASKETBALL = 5
    private const val HC_BIKING = 8
    private const val HC_BIKING_STATIONARY = 9
    private const val HC_BOOT_CAMP = 10
    private const val HC_BOXING = 11
    private const val HC_CALISTHENICS = 13
    private const val HC_CRICKET = 14
    private const val HC_DANCING = 16
    private const val HC_ELLIPTICAL = 25
    private const val HC_EXERCISE_CLASS = 26
    private const val HC_FENCING = 27
    private const val HC_FOOTBALL_AMERICAN = 28
    private const val HC_FOOTBALL_AUSTRALIAN = 29
    private const val HC_FRISBEE_DISC = 31
    private const val HC_GOLF = 32
    private const val HC_GUIDED_BREATHING = 33
    private const val HC_GYMNASTICS = 34
    private const val HC_HANDBALL = 35
    private const val HC_HIIT = 36
    private const val HC_HIKING = 37
    private const val HC_ICE_HOCKEY = 38
    private const val HC_ICE_SKATING = 39
    private const val HC_MARTIAL_ARTS = 44
    private const val HC_PADDLING = 46
    private const val HC_PARAGLIDING = 47
    private const val HC_PILATES = 48
    private const val HC_RACQUETBALL = 50
    private const val HC_ROCK_CLIMBING = 51
    private const val HC_ROLLER_HOCKEY = 52
    private const val HC_ROWING = 53
    private const val HC_ROWING_MACHINE = 54
    private const val HC_RUGBY = 55
    private const val HC_RUNNING = 56
    private const val HC_RUNNING_TREADMILL = 57
    private const val HC_SAILING = 58
    private const val HC_SCUBA_DIVING = 59
    private const val HC_SKATING = 60
    private const val HC_SKIING = 61
    private const val HC_SNOWBOARDING = 62
    private const val HC_SNOWSHOEING = 63
    private const val HC_SOCCER = 64
    private const val HC_SOFTBALL = 65
    private const val HC_SQUASH = 66
    private const val HC_STAIR_CLIMBING = 68
    private const val HC_STAIR_CLIMBING_MACHINE = 69
    private const val HC_STRENGTH_TRAINING = 70
    private const val HC_STRETCHING = 71
    private const val HC_SURFING = 72
    private const val HC_SWIMMING_OPEN_WATER = 73
    private const val HC_SWIMMING_POOL = 74
    private const val HC_TABLE_TENNIS = 75
    private const val HC_TENNIS = 76
    private const val HC_VOLLEYBALL = 78
    private const val HC_WALKING = 79
    private const val HC_WATER_POLO = 80
    private const val HC_WEIGHTLIFTING = 81
    private const val HC_WHEELCHAIR = 82
    private const val HC_YOGA = 83

    // HealthKit HKWorkoutActivityType
    private const val HK_ARCHERY = 2L
    private const val HK_AUSTRALIAN_FOOTBALL = 3L
    private const val HK_BADMINTON = 4L
    private const val HK_BASEBALL = 5L
    private const val HK_BASKETBALL = 6L
    private const val HK_BOWLING = 7L
    private const val HK_BOXING = 8L
    private const val HK_CLIMBING = 9L
    private const val HK_CRICKET = 10L
    private const val HK_CROSS_TRAINING = 11L
    private const val HK_CURLING = 12L
    private const val HK_CYCLING = 13L
    private const val HK_DANCE = 14L
    private const val HK_ELLIPTICAL = 16L
    private const val HK_EQUESTRIAN = 17L
    private const val HK_FENCING = 18L
    private const val HK_FISHING = 19L
    private const val HK_FUNCTIONAL_STRENGTH = 20L
    private const val HK_GOLF = 21L
    private const val HK_GYMNASTICS = 22L
    private const val HK_HANDBALL = 23L
    private const val HK_HIKING = 24L
    private const val HK_HOCKEY = 25L
    private const val HK_MARTIAL_ARTS = 28L
    private const val HK_MIND_AND_BODY = 29L
    private const val HK_PADDLE_SPORTS = 31L
    private const val HK_PLAY = 32L
    private const val HK_PREPARATION_RECOVERY = 33L
    private const val HK_RACQUETBALL = 34L
    private const val HK_ROWING = 35L
    private const val HK_RUGBY = 36L
    private const val HK_RUNNING = 37L
    private const val HK_SAILING = 38L
    private const val HK_SKATING = 39L
    private const val HK_SNOW_SPORTS = 40L
    private const val HK_SOCCER = 41L
    private const val HK_SOFTBALL = 42L
    private const val HK_SQUASH = 43L
    private const val HK_STAIR_CLIMBING = 44L
    private const val HK_SURFING = 45L
    private const val HK_SWIMMING = 46L
    private const val HK_TABLE_TENNIS = 47L
    private const val HK_TENNIS = 48L
    private const val HK_TRACK_AND_FIELD = 49L
    private const val HK_TRADITIONAL_STRENGTH = 50L
    private const val HK_VOLLEYBALL = 51L
    private const val HK_WALKING = 52L
    private const val HK_WATER_FITNESS = 53L
    private const val HK_WATER_POLO = 54L
    private const val HK_WATER_SPORTS = 55L
    private const val HK_WRESTLING = 56L
    private const val HK_YOGA = 57L
    private const val HK_CORE_TRAINING = 59L
    private const val HK_CROSS_COUNTRY_SKIING = 60L
    private const val HK_DOWNHILL_SKIING = 61L
    private const val HK_FLEXIBILITY = 62L
    private const val HK_HIIT = 63L
    private const val HK_JUMP_ROPE = 64L
    private const val HK_KICKBOXING = 65L
    private const val HK_PILATES = 66L
    private const val HK_SNOWBOARDING = 67L
    private const val HK_STAIRS = 68L
    private const val HK_STEP_TRAINING = 69L
    private const val HK_TAI_CHI = 72L
    private const val HK_MIXED_CARDIO = 73L
    private const val HK_DISC_SPORTS = 75L
    private const val HK_FITNESS_GAMING = 76L
    private const val HK_CARDIO_DANCE = 77L
    private const val HK_SOCIAL_DANCE = 78L
    private const val HK_UNDERWATER_DIVING = 84L
    private const val HK_OTHER = 3000L

    private fun m(title: String, hc: Int, hk: Long) = Mapping(title, hc, hk)

    /**
     * Exact Mi FitnessPersistKey / sport category strings → platform mapping.
     * Keys are normalized (lowercase, trim, spaces→underscores).
     */
    private val exact: Map<String, Mapping> = mapOf(
        // Running / walking
        "outdoor_running" to m("Outdoor Running", HC_RUNNING, HK_RUNNING),
        "indoor_running" to m("Indoor Running", HC_RUNNING_TREADMILL, HK_RUNNING),
        "playground_running" to m("Track Running", HC_RUNNING, HK_RUNNING),
        "race_walking" to m("Race Walking", HC_WALKING, HK_WALKING),
        "outdoor_walking" to m("Outdoor Walking", HC_WALKING, HK_WALKING),
        "indoor_walking" to m("Indoor Walking", HC_WALKING, HK_WALKING),
        "walking_machine" to m("Treadmill Walk", HC_WALKING, HK_WALKING),
        "outdoor_hiking" to m("Hiking", HC_HIKING, HK_HIKING),
        "cross_hiking" to m("Cross Hiking", HC_HIKING, HK_HIKING),
        "trail_run" to m("Trail Running", HC_RUNNING, HK_RUNNING),
        // Cycling
        "outdoor_riding" to m("Outdoor Cycling", HC_BIKING, HK_CYCLING),
        "indoor_riding" to m("Indoor Cycling", HC_BIKING_STATIONARY, HK_CYCLING),
        "spinning" to m("Spinning", HC_BIKING_STATIONARY, HK_CYCLING),
        "bmx" to m("BMX", HC_BIKING, HK_CYCLING),
        "atv" to m("ATV", HC_OTHER, HK_OTHER),
        // Swimming / water
        "pool_swimming" to m("Pool Swimming", HC_SWIMMING_POOL, HK_SWIMMING),
        "open_swimming" to m("Open Water Swimming", HC_SWIMMING_OPEN_WATER, HK_SWIMMING),
        "fancy_swimming" to m("Artistic Swimming", HC_SWIMMING_POOL, HK_SWIMMING),
        "web_swimming" to m("Fin Swimming", HC_SWIMMING_POOL, HK_SWIMMING),
        "water_polo" to m("Water Polo", HC_WATER_POLO, HK_WATER_POLO),
        "snorkeling" to m("Snorkeling", HC_OTHER, HK_WATER_SPORTS),
        "freediving" to m("Freediving", HC_SCUBA_DIVING, HK_UNDERWATER_DIVING),
        "instrument_diving" to m("Scuba Diving", HC_SCUBA_DIVING, HK_UNDERWATER_DIVING),
        "recreational_scuba_diving" to m("Scuba Diving", HC_SCUBA_DIVING, HK_UNDERWATER_DIVING),
        "surfing" to m("Surfing", HC_SURFING, HK_SURFING),
        "indoor_surfing" to m("Indoor Surfing", HC_SURFING, HK_SURFING),
        "kite_surfing" to m("Kite Surfing", HC_SURFING, HK_SURFING),
        "paddle_board" to m("Paddle Board", HC_PADDLING, HK_PADDLE_SPORTS),
        "canoeing" to m("Canoeing", HC_PADDLING, HK_PADDLE_SPORTS),
        "kayak_rafting" to m("Kayaking", HC_PADDLING, HK_PADDLE_SPORTS),
        "rowing" to m("Rowing", HC_ROWING, HK_ROWING),
        "rowing_machine" to m("Rowing Machine", HC_ROWING_MACHINE, HK_ROWING),
        "dragon_boat" to m("Dragon Boat", HC_PADDLING, HK_PADDLE_SPORTS),
        "sailboat" to m("Sailing", HC_SAILING, HK_SAILING),
        "motorboat" to m("Motorboat", HC_OTHER, HK_WATER_SPORTS),
        "aquatic_sport" to m("Aquatic Sport", HC_OTHER, HK_WATER_SPORTS),
        // Strength / gym
        "strength_training" to m("Strength Training", HC_STRENGTH_TRAINING, HK_TRADITIONAL_STRENGTH),
        "weight_lifting" to m("Weightlifting", HC_WEIGHTLIFTING, HK_TRADITIONAL_STRENGTH),
        "barbell_training" to m("Barbell Training", HC_WEIGHTLIFTING, HK_TRADITIONAL_STRENGTH),
        "dumbbell_training" to m("Dumbbell Training", HC_STRENGTH_TRAINING, HK_TRADITIONAL_STRENGTH),
        "dead_lift" to m("Deadlift", HC_WEIGHTLIFTING, HK_TRADITIONAL_STRENGTH),
        "core_training" to m("Core Training", HC_CALISTHENICS, HK_CORE_TRAINING),
        "functional_training" to m("Functional Training", HC_STRENGTH_TRAINING, HK_FUNCTIONAL_STRENGTH),
        "physical_training" to m("Physical Training", HC_STRENGTH_TRAINING, HK_FUNCTIONAL_STRENGTH),
        "upper_limb_training" to m("Upper Body Training", HC_STRENGTH_TRAINING, HK_TRADITIONAL_STRENGTH),
        "lower_limb_training" to m("Lower Body Training", HC_STRENGTH_TRAINING, HK_TRADITIONAL_STRENGTH),
        "back_training" to m("Back Training", HC_STRENGTH_TRAINING, HK_TRADITIONAL_STRENGTH),
        "waist_training" to m("Core / Waist Training", HC_CALISTHENICS, HK_CORE_TRAINING),
        "abdwheel_training" to m("Ab Wheel", HC_CALISTHENICS, HK_CORE_TRAINING),
        "sit_ups" to m("Sit-ups", HC_CALISTHENICS, HK_CORE_TRAINING),
        "high_bar" to m("High Bar", HC_GYMNASTICS, HK_GYMNASTICS),
        "parallel_bars" to m("Parallel Bars", HC_GYMNASTICS, HK_GYMNASTICS),
        "battle_rope" to m("Battle Rope", HC_STRENGTH_TRAINING, HK_FUNCTIONAL_STRENGTH),
        "wall_ball" to m("Wall Ball", HC_STRENGTH_TRAINING, HK_FUNCTIONAL_STRENGTH),
        // Cardio machines
        "elliptical_trainer" to m("Elliptical", HC_ELLIPTICAL, HK_ELLIPTICAL),
        "climb_stairs" to m("Stair Climbing", HC_STAIR_CLIMBING, HK_STAIR_CLIMBING),
        "climbing_machine" to m("Stair Machine", HC_STAIR_CLIMBING_MACHINE, HK_STAIRS),
        "stepper" to m("Stepper", HC_STAIR_CLIMBING_MACHINE, HK_STEP_TRAINING),
        "step_training" to m("Step Training", HC_STAIR_CLIMBING, HK_STEP_TRAINING),
        "indoor_fitness" to m("Indoor Fitness", HC_OTHER, HK_MIXED_CARDIO),
        "free_training" to m("Free Training", HC_OTHER, HK_MIXED_CARDIO),
        "cross_fit" to m("CrossFit", HC_HIIT, HK_CROSS_TRAINING),
        "high_interval_training" to m("HIIT", HC_HIIT, HK_HIIT),
        "mixed_aerobics" to m("Aerobics", HC_DANCING, HK_MIXED_CARDIO),
        "aerobics" to m("Aerobics", HC_DANCING, HK_MIXED_CARDIO),
        "group_callisthenics" to m("Group Calisthenics", HC_CALISTHENICS, HK_CROSS_TRAINING),
        // Mind / body
        "yoga" to m("Yoga", HC_YOGA, HK_YOGA),
        "pilates" to m("Pilates", HC_PILATES, HK_PILATES),
        "taichi" to m("Tai Chi", HC_MARTIAL_ARTS, HK_TAI_CHI),
        "flexibility_training" to m("Flexibility", HC_STRETCHING, HK_FLEXIBILITY),
        "stretch" to m("Stretching", HC_STRETCHING, HK_FLEXIBILITY),
        // Martial arts
        "martial_arts" to m("Martial Arts", HC_MARTIAL_ARTS, HK_MARTIAL_ARTS),
        "boxing" to m("Boxing", HC_BOXING, HK_BOXING),
        "kickboxing" to m("Kickboxing", HC_BOXING, HK_KICKBOXING),
        "muay_thai" to m("Muay Thai", HC_BOXING, HK_KICKBOXING),
        "karate" to m("Karate", HC_MARTIAL_ARTS, HK_MARTIAL_ARTS),
        "judo" to m("Judo", HC_MARTIAL_ARTS, HK_MARTIAL_ARTS),
        "jiujitsu" to m("Jiu-Jitsu", HC_MARTIAL_ARTS, HK_MARTIAL_ARTS),
        "taekwondo" to m("Taekwondo", HC_MARTIAL_ARTS, HK_MARTIAL_ARTS),
        "fencing" to m("Fencing", HC_FENCING, HK_FENCING),
        "wrestling" to m("Wrestling", HC_MARTIAL_ARTS, HK_WRESTLING),
        "free_sparring" to m("Sparring", HC_MARTIAL_ARTS, HK_MARTIAL_ARTS),
        "swordswanship" to m("Kendo", HC_MARTIAL_ARTS, HK_MARTIAL_ARTS),
        // Team / ball
        "basketball" to m("Basketball", HC_BASKETBALL, HK_BASKETBALL),
        "football" to m("Soccer", HC_SOCCER, HK_SOCCER),
        "futsal" to m("Futsal", HC_SOCCER, HK_SOCCER),
        "beach_football" to m("Beach Soccer", HC_SOCCER, HK_SOCCER),
        "volleyball" to m("Volleyball", HC_VOLLEYBALL, HK_VOLLEYBALL),
        "beach_volleyball" to m("Beach Volleyball", HC_VOLLEYBALL, HK_VOLLEYBALL),
        "baseball" to m("Baseball", HC_BASEBALL, HK_BASEBALL),
        "softball" to m("Softball", HC_SOFTBALL, HK_SOFTBALL),
        "cricket" to m("Cricket", HC_CRICKET, HK_CRICKET),
        "rugby" to m("Rugby", HC_RUGBY, HK_RUGBY),
        "handball" to m("Handball", HC_HANDBALL, HK_HANDBALL),
        "hockey" to m("Hockey", HC_ICE_HOCKEY, HK_HOCKEY),
        "puck" to m("Ice Hockey", HC_ICE_HOCKEY, HK_HOCKEY),
        "dodgeball" to m("Dodgeball", HC_OTHER, HK_PLAY),
        // Racket
        "badminton" to m("Badminton", HC_BADMINTON, HK_BADMINTON),
        "tennis" to m("Tennis", HC_TENNIS, HK_TENNIS),
        "pingpong" to m("Table Tennis", HC_TABLE_TENNIS, HK_TABLE_TENNIS),
        "squash" to m("Squash", HC_SQUASH, HK_SQUASH),
        // Dance
        "dance" to m("Dance", HC_DANCING, HK_DANCE),
        "ballet" to m("Ballet", HC_DANCING, HK_DANCE),
        "ballroom_dance" to m("Ballroom Dance", HC_DANCING, HK_SOCIAL_DANCE),
        "belly_dance" to m("Belly Dance", HC_DANCING, HK_DANCE),
        "break_dance" to m("Breakdance", HC_DANCING, HK_CARDIO_DANCE),
        "hip_hop_dance" to m("Hip Hop", HC_DANCING, HK_CARDIO_DANCE),
        "jazz" to m("Jazz Dance", HC_DANCING, HK_DANCE),
        "latin_dance" to m("Latin Dance", HC_DANCING, HK_SOCIAL_DANCE),
        "modern_dance" to m("Modern Dance", HC_DANCING, HK_DANCE),
        "national_dance" to m("Folk Dance", HC_DANCING, HK_SOCIAL_DANCE),
        "square_dance" to m("Square Dance", HC_DANCING, HK_SOCIAL_DANCE),
        "street_dance" to m("Street Dance", HC_DANCING, HK_CARDIO_DANCE),
        "zumba" to m("Zumba", HC_DANCING, HK_CARDIO_DANCE),
        "pole_dance" to m("Pole Dance", HC_DANCING, HK_DANCE),
        "switch_just_dance" to m("Just Dance", HC_DANCING, HK_FITNESS_GAMING),
        "passion_cheer" to m("Cheerleading", HC_DANCING, HK_DANCE),
        // Climb / outdoor
        "rock_climbing" to m("Rock Climbing", HC_ROCK_CLIMBING, HK_CLIMBING),
        "outdoor_rock_climbing" to m("Outdoor Climbing", HC_ROCK_CLIMBING, HK_CLIMBING),
        "indoor_rock_climbing" to m("Indoor Climbing", HC_ROCK_CLIMBING, HK_CLIMBING),
        "climbing" to m("Climbing", HC_ROCK_CLIMBING, HK_CLIMBING),
        "paraglider" to m("Paragliding", HC_PARAGLIDING, HK_OTHER),
        "parkour" to m("Parkour", HC_CALISTHENICS, HK_CROSS_TRAINING),
        // Winter
        "snowboarding" to m("Snowboarding", HC_SNOWBOARDING, HK_SNOWBOARDING),
        "parallel_skiing" to m("Skiing", HC_SKIING, HK_DOWNHILL_SKIING),
        "backcountry_skiing" to m("Cross-Country Skiing", HC_SKIING, HK_CROSS_COUNTRY_SKIING),
        "snow_sports" to m("Snow Sports", HC_SKIING, HK_SNOW_SPORTS),
        "sled" to m("Sledding", HC_OTHER, HK_SNOW_SPORTS),
        "snowmobile" to m("Snowmobile", HC_OTHER, HK_SNOW_SPORTS),
        "snow_car" to m("Snow Vehicle", HC_OTHER, HK_SNOW_SPORTS),
        "curling" to m("Curling", HC_OTHER, HK_CURLING),
        "ice_skating" to m("Ice Skating", HC_ICE_SKATING, HK_SKATING),
        "indoor_skating" to m("Indoor Skating", HC_SKATING, HK_SKATING),
        "outdoor_skating" to m("Outdoor Skating", HC_SKATING, HK_SKATING),
        "skate" to m("Skating", HC_SKATING, HK_SKATING),
        "roller_skating" to m("Roller Skating", HC_SKATING, HK_SKATING),
        // Other sports from APK keys
        "golf" to m("Golf", HC_GOLF, HK_GOLF),
        "archery" to m("Archery", HC_OTHER, HK_ARCHERY),
        "bowling" to m("Bowling", HC_OTHER, HK_BOWLING),
        "frisbee" to m("Frisbee", HC_FRISBEE_DISC, HK_DISC_SPORTS),
        "gymnastics" to m("Gymnastics", HC_GYMNASTICS, HK_GYMNASTICS),
        "track_and_field" to m("Track and Field", HC_RUNNING, HK_TRACK_AND_FIELD),
        "triathlon" to m("Triathlon", HC_OTHER, HK_MIXED_CARDIO),
        "equestrian" to m("Equestrian", HC_OTHER, HK_EQUESTRIAN),
        "horse_riding" to m("Horse Riding", HC_OTHER, HK_EQUESTRIAN),
        "fishing" to m("Fishing", HC_OTHER, HK_FISHING),
        "rope_skipping" to m("Jump Rope", HC_OTHER, HK_JUMP_ROPE),
        "bobby_jump" to m("Jump Rope", HC_OTHER, HK_JUMP_ROPE),
        "hula_hoop" to m("Hula Hoop", HC_OTHER, HK_PLAY),
        "darts" to m("Darts", HC_OTHER, HK_PLAY),
        "billiards" to m("Billiards", HC_OTHER, HK_PLAY),
        "chess" to m("Chess", HC_OTHER, HK_PLAY),
        "board_game" to m("Board Game", HC_OTHER, HK_PLAY),
        "esport" to m("E-Sports", HC_OTHER, HK_FITNESS_GAMING),
        "e_sports" to m("E-Sports", HC_OTHER, HK_FITNESS_GAMING),
        "motion_sensing_game" to m("Motion Game", HC_OTHER, HK_FITNESS_GAMING),
        "foosball" to m("Table Football", HC_OTHER, HK_PLAY),
        "gateball" to m("Gateball", HC_OTHER, HK_PLAY),
        "bocci" to m("Bocce", HC_OTHER, HK_PLAY),
        "sepak_takraw" to m("Sepak Takraw", HC_OTHER, HK_PLAY),
        "shuttlecock" to m("Shuttlecock", HC_OTHER, HK_PLAY),
        "shuttlecock_kicking" to m("Jianzi", HC_OTHER, HK_PLAY),
        "footbag" to m("Footbag", HC_OTHER, HK_PLAY),
        "tug_of_war" to m("Tug of War", HC_OTHER, HK_PLAY),
        "swing" to m("Swing", HC_GOLF, HK_GOLF),
        "unknown" to m("Workout", HC_OTHER, HK_OTHER),
        "workout" to m("Workout", HC_OTHER, HK_OTHER),
    )

    fun normalizeKey(raw: String): String =
        raw.trim().lowercase()
            .replace(' ', '_')
            .replace('-', '_')
            .replace(Regex("[^a-z0-9_]"), "")

    fun map(activityType: String): Mapping {
        val key = normalizeKey(activityType)
        exact[key]?.let { return it }
        return fuzzy(key, activityType)
    }

    fun healthConnectExerciseType(activityType: String): Int =
        map(activityType).healthConnectType

    fun healthKitActivityType(activityType: String): Long =
        map(activityType).healthKitType

    fun displayTitle(activityType: String): String =
        map(activityType).title

    private fun fuzzy(key: String, original: String): Mapping {
        fun contains(vararg parts: String) = parts.any { it in key }
        return when {
            contains("treadmill") && contains("run") ->
                m(titleize(original), HC_RUNNING_TREADMILL, HK_RUNNING)
            contains("run", "jog") -> m(titleize(original), HC_RUNNING, HK_RUNNING)
            contains("walk") -> m(titleize(original), HC_WALKING, HK_WALKING)
            contains("hik") -> m(titleize(original), HC_HIKING, HK_HIKING)
            contains("cycl", "bik", "rid") && contains("indoor", "station", "spinn") ->
                m(titleize(original), HC_BIKING_STATIONARY, HK_CYCLING)
            contains("cycl", "bik", "rid") -> m(titleize(original), HC_BIKING, HK_CYCLING)
            contains("pool") && contains("swim") ->
                m(titleize(original), HC_SWIMMING_POOL, HK_SWIMMING)
            contains("open") && contains("swim") ->
                m(titleize(original), HC_SWIMMING_OPEN_WATER, HK_SWIMMING)
            contains("swim") -> m(titleize(original), HC_SWIMMING_POOL, HK_SWIMMING)
            contains("ellipt") -> m(titleize(original), HC_ELLIPTICAL, HK_ELLIPTICAL)
            contains("row") && contains("machine") ->
                m(titleize(original), HC_ROWING_MACHINE, HK_ROWING)
            contains("row") -> m(titleize(original), HC_ROWING, HK_ROWING)
            contains("yoga") -> m(titleize(original), HC_YOGA, HK_YOGA)
            contains("pilates") -> m(titleize(original), HC_PILATES, HK_PILATES)
            contains("hiit", "interval") -> m(titleize(original), HC_HIIT, HK_HIIT)
            contains("strength", "weight", "gym", "barbell", "dumbbell") ->
                m(titleize(original), HC_STRENGTH_TRAINING, HK_TRADITIONAL_STRENGTH)
            contains("climb", "boulder") -> m(titleize(original), HC_ROCK_CLIMBING, HK_CLIMBING)
            contains("stair") -> m(titleize(original), HC_STAIR_CLIMBING, HK_STAIR_CLIMBING)
            contains("dance", "zumba") -> m(titleize(original), HC_DANCING, HK_DANCE)
            contains("box") -> m(titleize(original), HC_BOXING, HK_BOXING)
            contains("martial", "karate", "judo", "taekwondo") ->
                m(titleize(original), HC_MARTIAL_ARTS, HK_MARTIAL_ARTS)
            contains("ski") && contains("board") ->
                m(titleize(original), HC_SNOWBOARDING, HK_SNOWBOARDING)
            contains("ski") -> m(titleize(original), HC_SKIING, HK_DOWNHILL_SKIING)
            contains("skate") -> m(titleize(original), HC_SKATING, HK_SKATING)
            contains("basket") -> m(titleize(original), HC_BASKETBALL, HK_BASKETBALL)
            contains("soccer", "football") && !contains("american") ->
                m(titleize(original), HC_SOCCER, HK_SOCCER)
            contains("tennis") && contains("table", "ping") ->
                m(titleize(original), HC_TABLE_TENNIS, HK_TABLE_TENNIS)
            contains("tennis") -> m(titleize(original), HC_TENNIS, HK_TENNIS)
            contains("badminton") -> m(titleize(original), HC_BADMINTON, HK_BADMINTON)
            contains("golf") -> m(titleize(original), HC_GOLF, HK_GOLF)
            contains("volleyball") -> m(titleize(original), HC_VOLLEYBALL, HK_VOLLEYBALL)
            contains("surf") -> m(titleize(original), HC_SURFING, HK_SURFING)
            contains("dive", "scuba") -> m(titleize(original), HC_SCUBA_DIVING, HK_UNDERWATER_DIVING)
            contains("stretch", "flex") -> m(titleize(original), HC_STRETCHING, HK_FLEXIBILITY)
            contains("rope") -> m(titleize(original), HC_OTHER, HK_JUMP_ROPE)
            else -> m(titleize(original).ifBlank { "Workout" }, HC_OTHER, HK_OTHER)
        }
    }

    private fun titleize(raw: String): String {
        val cleaned = raw.trim().replace('_', ' ').replace('-', ' ')
        if (cleaned.isBlank()) return "Workout"
        return cleaned.split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}
