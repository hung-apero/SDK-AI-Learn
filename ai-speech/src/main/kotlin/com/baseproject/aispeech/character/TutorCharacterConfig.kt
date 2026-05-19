package com.baseproject.aispeech.character

import java.io.File

/**
 * Locations of the three model files the SDK needs to load.
 *
 * The SDK does NOT download, fetch, or version-check these files — it expects
 * them to already exist on disk. Apps are responsible for whatever strategy
 * they prefer (bundle as assets and copy to filesDir on first run, download
 * from a server with versioned URLs, sync from a CMS, etc.).
 *
 * All three files must be on the local filesystem and readable.
 *
 * @param atlasFile Spine `.atlas` region map
 * @param jsonFile  Spine `.json` skeleton (binary `.skel` is not supported by
 *                  the current SDK; convert via the Spine editor if needed)
 * @param pngFile   texture atlas image
 */
data class TutorCharacterConfig(
    val atlasFile: File,
    val jsonFile:  File,
    val pngFile:   File
)
