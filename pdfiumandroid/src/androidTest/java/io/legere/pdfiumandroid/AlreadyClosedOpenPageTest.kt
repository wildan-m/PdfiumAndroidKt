package io.legere.pdfiumandroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.legere.pdfiumandroid.base.BasePDFTest
import io.legere.pdfiumandroid.util.AlreadyClosedBehavior
import io.legere.pdfiumandroid.util.Config
import io.legere.pdfiumandroid.util.pdfiumConfig
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E/App #93839 (Sentry APP-1VG) — Android "Already closed" crash on receipt PDF render.
 *
 * The production crash is a non-deterministic renderer-thread-vs-teardown race: a render
 * lands on PdfDocument.openPage(0) after the document was already close()d. The app sets
 * AlreadyClosedBehavior.IGNORE (react-native-pdf patch 002) so such a late call should be
 * swallowed — but stock io.legere:pdfiumandroid 1.0.35 has openPage()/openTextPage() throw
 * unconditionally, ignoring IGNORE, so the IllegalStateException crashes the renderer thread.
 *
 * This forces that exact frame deterministically:  IGNORE -> newDocument -> close() -> openPage(0)
 *
 *   Baseline (openPage does NOT honor IGNORE): FAILS — IllegalStateException("Already closed")
 *                                              at PdfDocument.openPage(PdfDocument.kt) == APP-1VG
 *   Fork     (openPage honors IGNORE):         PASSES — openPage returns instead of throwing
 */
@RunWith(AndroidJUnit4::class)
class AlreadyClosedOpenPageTest : BasePDFTest() {

    @After
    fun tearDown() {
        // Don't leak the IGNORE config into other test classes.
        pdfiumConfig = Config()
    }

    @Test
    fun openPageAfterClose_withIgnore_doesNotCrash() {
        val pdfBytes = getPdfBytes("f01.pdf")
        assertThat(pdfBytes).isNotNull()

        // Configure exactly as the app does (react-native-pdf patch 002): IGNORE via the
        // PdfiumCore constructor. (PdfiumCore.init overwrites the global pdfiumConfig, so
        // setting the global directly would be wiped by construction.)
        val core = PdfiumCore(config = Config(alreadyClosedBehavior = AlreadyClosedBehavior.IGNORE))
        val doc = core.newDocument(pdfBytes)
        doc.close()

        // The exact crashing call from the APP-1VG stack. With IGNORE set this must NOT throw.
        // On stock 1.0.35 it throws IllegalStateException("Already closed") here.
        val page = doc.openPage(0)
        assertThat(page).isNotNull()
    }
}
