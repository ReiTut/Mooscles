package com.mooscles

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.mooscles.photos.AndroidPhotoCapture
import com.mooscles.screens.exercise.photo.ExercisePhotos
import com.mooscles.screens.exercise.photo.LocalExercisePhotos

/**
 * Gives the screens the app's exercise photos (plan 4B decision 11): the store of [app] and the gallery and camera
 * launchers of [context]. Without this, no screen has a "Photo" row and none reads a photo.
 */
@Composable
fun ProvideExercisePhotos(app: MoosclesApplication, context: Context, content: @Composable () -> Unit) {
    val photos = remember(app) { ExercisePhotos(app.photos, AndroidPhotoCapture(context)) }
    CompositionLocalProvider(LocalExercisePhotos provides photos, content = content)
}
