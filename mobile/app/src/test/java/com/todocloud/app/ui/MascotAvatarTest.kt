package com.todocloud.app.ui

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class MascotAvatarTest {
    @Test
    fun plateKeepsTheSizeOfTheOldLetterAvatar() {
        // The account row's height and text position depend on it.
        assertEquals(44f, MascotAvatarLayout.PLATE_DP)
    }

    @Test
    fun wholePictureStaysInsideTheWhitePlate() {
        assertTrue(MascotAvatarLayout.pictureInsidePlate())
        assertTrue(MascotAvatarLayout.PLATE_DP - 2 * MascotAvatarLayout.INSET_DP >= 34f, "picture too small to read")
    }

    @Test
    fun plateCheckRejectsShapesThatWouldCutThePicture() {
        // A circle with the same inset would cut the picture's corners (ears, hair) ...
        assertFalse(MascotAvatarLayout.pictureInsidePlate(corner = 22f, inset = 4f))
        // ... and needs at least 22 * (1 - 1/√2) ≈ 6.45dp of inset to hold the whole picture.
        assertTrue(MascotAvatarLayout.pictureInsidePlate(corner = 22f, inset = 6.5f))
        assertFalse(MascotAvatarLayout.pictureInsidePlate(inset = 0f))
        assertFalse(MascotAvatarLayout.pictureInsidePlate(inset = 22f))
    }
}
