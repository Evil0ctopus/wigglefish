package com.wigglefish.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import kotlin.random.Random

/**
 * Autonomous Wigglefish companion with moods, levels, experience, and reactive commentary.
 */
class WigglePetCompanion(
    private val context: Context,
    private val onPetStateChanged: (PetState) -> Unit
) {

    enum class Mood(val tag: String) {
        HAPPY("[HAPPY]"),
        NOM_NOM("[EATING]"),
        HUNTING("[ATTACKING]"),
        EXCITED("[PWNED!]"),
        SASSY("[SASSY]"),
        ANGRY("[ALERT]"),
        SLEEPY("[SLEEPY]"),
        COOL("[CYBER_BOSS]")
    }

    enum class EvolutionStage(val stageIndex: Int, val minLevel: Int, val title: String) {
        BABY_GUPPY(0, 1, "Signal Sprout"),
        SPICY_GOLDFISH(1, 3, "Pearl Drifter"),
        NEON_TETRA(2, 5, "Prism Ray"),
        CYBER_BETTA(3, 10, "Aurora Glider"),
        APEX_PIRANHA(4, 15, "Tide Warden"),
        PACKET_MEGALODON(5, 20, "Abyssal Ray"),
        CYBER_LEVIATHAN(6, 30, "Celestial Manta");

        companion object {
            fun fromLevel(level: Int): EvolutionStage = when {
                level >= 30 -> CYBER_LEVIATHAN
                level >= 20 -> PACKET_MEGALODON
                level >= 15 -> APEX_PIRANHA
                level >= 10 -> CYBER_BETTA
                level >= 5 -> NEON_TETRA
                level >= 3 -> SPICY_GOLDFISH
                else -> BABY_GUPPY
            }
        }
    }

    data class PetState(
        val name: String = "LUMI",
        val level: Int = 1,
        val evolutionStage: EvolutionStage = EvolutionStage.BABY_GUPPY,
        val levelTitle: String = "Signal Sprout",
        val currentExp: Int = 0,
        val maxExp: Int = 100,
        val pwnedCount: Int = 0,
        val mood: Mood = Mood.HAPPY,
        val speechText: String = "Signal's up. Let's see what the airwaves are saying."
    )

    private val prefs: SharedPreferences = context.getSharedPreferences("wiggle_pet_prefs", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    var state: PetState = loadState()
        private set

    private var isInteracting = false
    private var lastAutonomousSpeechTime = System.currentTimeMillis()

    private val autonomousChatterQuotes = listOf(
        "Circuitry clear. I'll keep watch from the edge of the signal map.",
        "Copper traces warm, teal eyes online. Looking sharp.",
        "Wings spread. Signal watch is underway.",
        "Quiet mode engaged. I can still see the scan feed.",
        "A little violet glow makes every console better.",
        "I found a rhythm in the scan intervals. Interesting.",
        "All fins in sync. Ready when you are.",
        "Scanning 2.4 GHz and 5 GHz bands. Passive ears only.",
        "Packets are just tiny postcards from the airwaves.",
        "A clean channel map is a thing of beauty.",
        "No signal is a signal too. I'll keep the timeline honest.",
        "WPA3 noticed. Your neighborhood has a few good habits.",
        "That device came back again. I'll mark the repeat sighting.",
        "A strong RSSI is not a range estimate. I checked.",
        "Observer GPS is available. Object bearing is still unknown.",
        "Copper, amethyst, teal. The whole rig is in tune."
    )

    private val apSnackQuotes = listOf(
        "New access point observed. Added to the signal ledger.",
        "Fresh SSID in range. I'll keep its strongest sample handy.",
        "One more beacon in the neighborhood map.",
        "Security details noted. Check the Intel page for context.",
        "New Wi-Fi observation saved. No packets sent.",
        "Dual-band signal detected. Neat."
    )

    private val bleSnackQuotes = listOf(
        "New BLE beacon observed. Address and RSSI logged.",
        "A nearby peripheral just checked in.",
        "BLE sighting added to the passive scan list.",
        "Beacon logged. Violet status light is for ambiance."
    )

    private val pwnedHandshakeQuotes = listOf(
        "🎉 JACKPOT! KEY ACQUIRED! 🔑 I CHOMPED IT!",
        "OMGGG A 4-WAY HANDSHAKE! Sending to Hashcat! 🚀",
        "PWNED! Who's the cyber boss now?! 👑",
        "Delicious EAPOL packet! That was a 5-star meal! 🏆",
        "Another AP captured in my trophy bowl!"
    )

    private val attackQuotes = listOf(
        "⚡ FIRE ZE PACKET TORPEDOES! 🌊",
        "Active tool mode detected. Keep the scope deliberate. ⚡",
        "Beacon storm active! Floating fake SSIDs everywhere! 📡",
        "Spamming Apple and Android pairing popups! Chaos! 😈",
        "Deauth pulse launched! Watch 'em disconnect! 💥"
    )

    private val portalQuotes = listOf(
        "A clear, safe captive portal demo is ready for the lab.",
        "Portal preview ready. No credentials are requested or saved.",
        "Local DNS demo is running. Keep it within your authorized lab."
    )

    private val alertQuotes = listOf(
        "🚨 WHO'S THROWING ROCKS IN MY POND?! Threat detected!",
        "⚠️ DEAUTH STORM! Raise the cyber shields! 🛡️",
        "Rogue skimmer or AirTag spotted! Bite it! 🦈"
    )

    private val pokeQuotes = listOf(
        "Touch registered. Tentacle calibration complete.",
        "That tickles. I added a few extra sparks.",
        "Eight arms are harder to surprise than one.",
        "I glow softly, spot signals, and look fabulous doing it.",
        "A little lap through the current. Nice.",
        "Poke acknowledged. Mood: brighter.",
        "Tiny bounce, big signal energy."
    )

    init {
        startAutonomousChatterTimer()
    }

    private fun loadState(): PetState {
        val lvl = prefs.getInt("pet_lvl", 1)
        val exp = prefs.getInt("pet_exp", 0)
        val pwned = prefs.getInt("pet_pwned", 0)
        val evo = EvolutionStage.fromLevel(lvl)
        return PetState(
            level = lvl,
            evolutionStage = evo,
            levelTitle = evo.title,
            currentExp = exp,
            maxExp = getExpForLevel(lvl),
            pwnedCount = pwned,
            mood = Mood.HAPPY,
            speechText = "Ready to pwn! Connect the ESP32 and let's roll!"
        )
    }

    private fun saveState() {
        prefs.edit()
            .putInt("pet_lvl", state.level)
            .putInt("pet_exp", state.currentExp)
            .putInt("pet_pwned", state.pwnedCount)
            .apply()
    }

    private fun getExpForLevel(lvl: Int): Int = 100 + (lvl * 60)

    fun setLevel(newLevel: Int) {
        val lvl = newLevel.coerceAtLeast(1)
        val evo = EvolutionStage.fromLevel(lvl)
        state = state.copy(
            level = lvl,
            evolutionStage = evo,
            levelTitle = evo.title,
            currentExp = 0,
            maxExp = getExpForLevel(lvl)
        )
        saveState()
        triggerMood(Mood.EXCITED, "✨ Evolved to ${state.levelTitle}!")
    }

    private fun addExp(amount: Int) {
        var cur = state.currentExp + amount
        var lvl = state.level
        var max = state.maxExp
        var leveledUp = false
        val oldEvo = state.evolutionStage

        while (cur >= max) {
            cur -= max
            lvl++
            max = getExpForLevel(lvl)
            leveledUp = true
        }

        val newEvo = EvolutionStage.fromLevel(lvl)
        state = state.copy(
            level = lvl,
            evolutionStage = newEvo,
            levelTitle = newEvo.title,
            currentExp = cur,
            maxExp = max
        )
        saveState()

        if (newEvo != oldEvo) {
            triggerMood(Mood.EXCITED, "🌟 MEGA EVOLUTION! I transformed into a ${newEvo.title}!")
        } else if (leveledUp) {
            triggerMood(Mood.EXCITED, "🎉 LEVEL UP! Level $lvl reached! More power!")
        }
    }

    fun onWifiDiscovered(count: Int = 1) {
        addExp(5 * count)
        if (!isInteracting) {
            triggerMood(Mood.NOM_NOM, apSnackQuotes.random())
        }
    }

    fun onBleDiscovered(count: Int = 1) {
        addExp(3 * count)
        if (!isInteracting && Random.nextInt(3) == 0) {
            triggerMood(Mood.HAPPY, bleSnackQuotes.random())
        }
    }

    fun onHandshakeCaptured(bssid: String) {
        val newPwned = state.pwnedCount + 1
        state = state.copy(pwnedCount = newPwned)
        addExp(60)
        saveState()
        triggerMood(Mood.EXCITED, pwnedHandshakeQuotes.random())
    }

    fun onAttackStarted(attackName: String) {
        addExp(15)
        triggerMood(Mood.HUNTING, attackQuotes.random())
    }

    fun onPortalStarted() {
        addExp(20)
        triggerMood(Mood.COOL, portalQuotes.random())
    }

    fun onThreatOrSkimmerAlert(threatName: String) {
        triggerMood(Mood.ANGRY, alertQuotes.random())
    }

    fun onPetInteracted() {
        isInteracting = true
        addExp(2)
        val quote = pokeQuotes.random()
        triggerMood(Mood.HAPPY, quote)
        mainHandler.postDelayed({ isInteracting = false }, 3500)
    }

    private fun triggerMood(mood: Mood, speech: String) {
        lastAutonomousSpeechTime = System.currentTimeMillis()
        state = state.copy(mood = mood, speechText = speech)
        onPetStateChanged(state)

        // Reset to happy/idle after 5 seconds
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (state.mood != Mood.SLEEPY && !isInteracting) {
                state = state.copy(mood = Mood.HAPPY)
                onPetStateChanged(state)
            }
        }, 5000)
    }

    private fun startAutonomousChatterTimer() {
        mainHandler.post(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                // If no speech event happened in the last 4.5 seconds, chime in autonomously!
                if (!isInteracting && now - lastAutonomousSpeechTime >= 4500L) {
                    lastAutonomousSpeechTime = now
                    val randomMood = when (Random.nextInt(8)) {
                        0 -> Mood.SASSY
                        1 -> Mood.COOL
                        2 -> Mood.HAPPY
                        3 -> Mood.NOM_NOM
                        else -> Mood.HAPPY
                    }
                    val speech = autonomousChatterQuotes.random()
                    state = state.copy(mood = randomMood, speechText = speech)
                    onPetStateChanged(state)
                }

                // Next check in 4.5 - 6.5 seconds
                val nextDelay = 4500L + Random.nextLong(2000L)
                mainHandler.postDelayed(this, nextDelay)
            }
        })
    }
}
