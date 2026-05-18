package com.baseproject.aispeech.stt.android

import com.baseproject.aispeech.stt.SttResult

class AndroidSttResult(
    text: String,
    isFinal: Boolean,
    languageCode: String? = null,
) : SttResult(text = text, isFinal = isFinal, languageCode = languageCode)
