package de.klaviatur.ui.screens.repertoire

import android.content.Context
import android.net.Uri
import de.klaviatur.data.model.SheetFileType
import java.io.File

/**
 * Kopiert eine vom Nutzer gewählte Notendatei in den App-Speicher (filesDir/sheets).
 * So bleibt sie lesbar, auch wenn die Berechtigung des Dateiauswahl-Providers verloren geht
 * oder die Originaldatei verschoben/gelöscht wird. Gibt null zurück, wenn das Kopieren scheitert
 * (dann wird die Original-URI weiterverwendet).
 */
internal fun copySheetToAppStorage(context: Context, uri: Uri, fileId: String, type: SheetFileType): Uri? {
    return try {
        val dir = File(context.filesDir, "sheets").apply { mkdirs() }
        val ext = if (type == SheetFileType.PDF) "pdf" else "img"
        val out = File(dir, "$fileId.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return null
        Uri.fromFile(out)
    } catch (e: Exception) {
        android.util.Log.e("SheetStorage", "Kopieren fehlgeschlagen", e)
        null
    }
}

/** Stellt eine mitgelieferte Beispiel-PDF aus den Assets wieder her, falls ihre Datei fehlt. */
internal fun restoreBundledAsset(context: Context, uri: Uri) {
    if (uri.scheme != "file") return
    val path = uri.path ?: return
    val file = File(path)
    if (file.exists()) return
    try {
        context.assets.open(file.name).use { input ->
            file.parentFile?.mkdirs()
            file.outputStream().use { input.copyTo(it) }
        }
    } catch (_: Exception) {
        // kein mitgeliefertes Asset – normaler Fehlerweg
    }
}

internal fun friendlySheetError(t: Throwable): String = when {
    t is SecurityException ->
        "Zugriff auf die Datei wurde entzogen. Bitte unter „Stück bearbeiten“ neu hinzufügen."
    t is java.io.FileNotFoundException || t.message?.contains("ENOENT") == true ->
        "Datei nicht mehr vorhanden. Bitte unter „Stück bearbeiten“ neu hinzufügen."
    else -> t.localizedMessage ?: t.javaClass.simpleName
}
