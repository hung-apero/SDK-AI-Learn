package com.baseproject.stt.android

import com.baseproject.stt.SttResult

class AndroidSttResult(
    text: String,
    isFinal: Boolean,
    languageCode: String? = null,
) : SttResult(text = text, isFinal = isFinal, languageCode = languageCode)
