package com.example.model

data class RecoveryMilestone(
    val id: String,
    val shortLabel: String,
    val title: String,
    val hoursRequired: Long,
    val summary: String,
    val detail: String
)

data class WithdrawalTip(
    val title: String,
    val guidance: String
)

data class SupportHelpline(
    val region: String,
    val name: String,
    val number: String,
    val note: String
)

object RecoveryContent {
    const val VARIATION_DISCLAIMER =
        "Timelines describe general patterns reported in recovery research. Every person's body, sleep, and mood adjust at their own pace."

    const val CLINICAL_SAFETY_NOTE =
        "If you experience severe insomnia lasting several days, intense panic attacks, or thoughts of self-harm, please speak with a doctor or reach out to a crisis helpline right away."

    val MILESTONES: List<RecoveryMilestone> = listOf(
        RecoveryMilestone(
            id = "m_24h",
            shortLabel = "24h",
            title = "First 24 hours",
            hoursRequired = 24L,
            summary = "Acute THC levels decline and your body begins clearing short-term metabolites.",
            detail = "Many people notice restlessness, subtle irritability, or lighter appetite during day one. Staying hydrated and keeping the evening structured helps."
        ),
        RecoveryMilestone(
            id = "m_3d",
            shortLabel = "3d",
            title = "3 days",
            hoursRequired = 72L,
            summary = "Early withdrawal signals often peak around days 2 to 4 as CB1 receptors start recalibrating.",
            detail = "Cravings and tension can feel sharpest here, then gradually soften. REM sleep rebound may begin, making dreams more noticeable."
        ),
        RecoveryMilestone(
            id = "m_1w",
            shortLabel = "1w",
            title = "1 week",
            hoursRequired = 168L,
            summary = "Physical edge typically begins easing while daytime alertness starts returning.",
            detail = "Appetite and daytime focus often steady out after the first week. Vivid dreams are still common and are a normal sign of REM recovery."
        ),
        RecoveryMilestone(
            id = "m_2w",
            shortLabel = "2w",
            title = "2 weeks",
            hoursRequired = 336L,
            summary = "CB1 receptor density in several brain regions shows measurable recovery in imaging studies.",
            detail = "Mood swings and evening urges tend to become shorter and less frequent. Sleep onset usually becomes smoother."
        ),
        RecoveryMilestone(
            id = "m_3w",
            shortLabel = "3w",
            title = "3 weeks",
            hoursRequired = 504L,
            summary = "Daily routines feel more self-sustaining and morning clarity improves for many people.",
            detail = "Short-term memory and verbal recall often feel noticeably sharper as baseline sleep quality deepens."
        ),
        RecoveryMilestone(
            id = "m_1mo",
            shortLabel = "1mo",
            title = "1 month",
            hoursRequired = 720L,
            summary = "Cannabinoid receptor availability approaches typical baseline levels for most users.",
            detail = "Sleep architecture, natural motivation, and emotional steadiness generally stabilize. Situational triggers may still appear but pass faster."
        ),
        RecoveryMilestone(
            id = "m_2mo",
            shortLabel = "2mo",
            title = "2 months",
            hoursRequired = 1440L,
            summary = "Stress tolerance and energy rhythms continue settling into a calmer baseline.",
            detail = "People who smoked frequently often report easier breathing, steadier mornings, and less brain fog."
        ),
        RecoveryMilestone(
            id = "m_3mo",
            shortLabel = "3mo",
            title = "3 months",
            hoursRequired = 2160L,
            summary = "New coping habits feel more automatic across work, social, and evening triggers.",
            detail = "Working memory, sustained attention, and natural reward response tend to feel well-grounded by the three-month mark."
        ),
        RecoveryMilestone(
            id = "m_6mo",
            shortLabel = "6mo",
            title = "6 months",
            hoursRequired = 4320L,
            summary = "Half a year of clear-headed routines, compounding financial and respiratory benefits.",
            detail = "Cravings are typically rare and brief, often tied only to unusual stress or nostalgia rather than physical pull."
        ),
        RecoveryMilestone(
            id = "m_1y",
            shortLabel = "1y",
            title = "1 year",
            hoursRequired = 8760L,
            summary = "A full cycle of seasons, holidays, and life events navigated without weed.",
            detail = "Long-term emotional regulation, sleep consistency, and personal agency reflect a durable new baseline."
        )
    )

    val WITHDRAWAL_TIPS: List<WithdrawalTip> = listOf(
        WithdrawalTip(
            title = "Restless sleep and vivid dreams",
            guidance = "Keep a consistent wake time, dim screens 45 minutes before bed, and avoid caffeine after early afternoon. Vivid dreams reflect REM sleep catching up and usually settle over 2 to 4 weeks."
        ),
        WithdrawalTip(
            title = "Evening irritability and edge",
            guidance = "Plan a deliberate transition at the exact hour you used to smoke: a brisk 15-minute walk, a hot shower, or making tea changes the sensory cue before the urge peaks."
        ),
        WithdrawalTip(
            title = "Low appetite or stomach unease",
            guidance = "Eat smaller, lighter meals more often rather than forcing a large plate. Warm broths, fruit, yogurt, and steady hydration usually help appetite return within a week."
        ),
        WithdrawalTip(
            title = "Riding out acute cravings",
            guidance = "Most urges peak and recede within 10 to 15 minutes when you do not feed them. Slow 4-7-8 breathing lowers heart rate and gives your prefrontal cortex time to catch up."
        )
    )

    val COPING_TIPS: List<String> = listOf(
        "Urges rise like a wave, crest around minute 5, and ebb when you stay still.",
        "Unclench your jaw, drop your shoulders, and lengthen the exhale past the inhale.",
        "Drink a full glass of cold water to interrupt the automatic hand-to-mouth loop.",
        "You do not need to decide about tomorrow. Only get through the next 10 minutes.",
        "Name the feeling beneath the urge: stressed, bored, tired, lonely, or restless.",
        "Change rooms or step outside for two minutes of fresh air to break the cue."
    )

    val HELPLINES: List<SupportHelpline> = listOf(
        SupportHelpline(
            region = "India",
            name = "Tele-MANAS",
            number = "14416",
            note = "24/7 free national mental health helpline (also 1-800-891-4416)"
        ),
        SupportHelpline(
            region = "United States",
            name = "988 Suicide & Crisis Lifeline",
            number = "988",
            note = "24/7 free, confidential call or text support"
        )
    )
}
