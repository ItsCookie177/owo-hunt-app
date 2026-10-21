package dev.owohunt.core.domain

/** Port dari zod schema.ts — validasi manual, pesan sama dengan versi web. */
fun validateConfig(c: AppConfig): List<String> {
    val errs = mutableListOf<String>()
    fun range(name: String, v: Long?, min: Long, max: Long) {
        if (v == null || v < min || v > max) errs += "$name di luar rentang [$min..$max]"
    }
    range("minDelaySec", c.minDelaySec, 5, 600)
    range("maxDelaySec", c.maxDelaySec, 5, 600)
    range("maxSessionMin", c.maxSessionMin, 1, 120)
    errs += if (c.breakEveryCmd in 0..100) emptyList() else listOf("breakEveryCmd di luar rentang [0..100]")
    range("breakMinSec", c.breakMinSec, 5, 600)
    range("breakMaxSec", c.breakMaxSec, 5, 600)
    range("startGraceSec", c.startGraceSec, 5, 120)
    if (c.rotation.isEmpty()) errs += "rotation minimal 1 command"
    c.rotation.forEachIndexed { i, cmd ->
        if (cmd.id.isBlank()) errs += "rotation[$i].id kosong"
        if (cmd.text.isBlank()) errs += "rotation[$i].text kosong"
        cmd.maxPerSession?.takeIf { it <= 0 }?.let { errs += "rotation[$i].maxPerSession harus > 0" }
        cmd.amount?.takeIf { it < 1 || it > 10_000_000_000L }?.let {
            errs += "rotation[$i].amount di luar rentang [1..10_000_000_000]"
        }
    }
    if (c.maxDelaySec < c.minDelaySec) errs += "maxDelaySec harus >= minDelaySec"
    return errs
}

/** Override delay start (schema startSchema). */
fun validateDelayOverride(o: DelayOverride): List<String> {
    val errs = mutableListOf<String>()
    listOf(o.minDelaySec to "minDelaySec", o.maxDelaySec to "maxDelaySec").forEach { (v, n) ->
        if (v != null && (v < 5 || v > 600)) errs += "$n di luar rentang [5..600]"
    }
    if (o.minDelaySec != null && o.maxDelaySec != null && o.maxDelaySec < o.minDelaySec) {
        errs += "maxDelaySec harus >= minDelaySec"
    }
    return errs
}
