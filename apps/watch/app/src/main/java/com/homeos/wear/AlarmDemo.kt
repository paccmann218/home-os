package com.homeos.wear

/**
 * Hardcoded morning-alarm demo. Nothing here reads a real calendar, ring, or clock.
 * The watch speaks these lines through Sarvam (Hindi) and waits for a spoken "हाँ".
 */
object AlarmScript {
    val intro = listOf(
        "मैंने आपके Google Calendar में देखा है कि कल सुबह आठ बजे आपकी मीटिंग है। अभी रात के नौ बज रहे हैं। आपके स्मार्ट रिंग के पिछले एक हफ़्ते की नींद देखी, तो आप अच्छी तरह सोए नहीं हैं।",
        "इसलिए मैं आपके घर की Google स्मार्ट अलार्म घड़ी खोलकर सुबह छह बजे और साढ़े छह बजे अलार्म लगा सकती हूँ। हाँ बोलिए, तो मैं यह काम अभी पूरा कर देती हूँ।",
    )
    const val WORKING = "ठीक है। मैं टास्क बना रही हूँ।"
    const val DONE = "हो गया। मैंने आपकी Google स्मार्ट घड़ी पर सुबह छह बजे और साढ़े छह बजे के अलार्म लगा दिए हैं। कल आठ बजे की मीटिंग से पहले आपको आराम से उठने का समय मिल जाएगा।"
    const val CLARIFY = "मैंने ठीक से सुना नहीं। हाँ बोलिए, तो मैं अलार्म लगा देती हूँ।"
    const val DECLINE = "ठीक है। मैं अलार्म नहीं लगाऊँगी।"
}

/** true = yes, false = no, null = ask again. */
fun interpretAlarmAnswer(raw: String): Boolean? {
    val t = raw.lowercase().trim()
    if (t.isBlank()) return null
    val yes = listOf(
        "yes", "yeah", "yep", "yup", "ok", "okay", "sure", "confirm", "do it", "set it",
        "haan", "han", "haa", "huh",
        "हाँ", "हां", "हा", "जी", "ठीक", "theek", "thik", "kar do", "कर दो", "लगा दो", "लगाओ", "करो",
    ).any { t.contains(it) }
    val no = listOf("no", "nah", "nope", "don't", "dont", "cancel", "नहीं", "नही", "मत").any { t.contains(it) }
        || t == "ना" || t.startsWith("ना ") || t.contains(" ना")
    return when {
        yes && !no -> true
        no && !yes -> false
        yes -> true
        else -> null
    }
}
