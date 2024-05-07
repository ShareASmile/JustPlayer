package com.brouken.player.encrypt;


import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;

import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;

public class ExtendedExtractorsFactory implements ExtractorsFactory {

    @Override
    public Extractor[] createExtractors() {
        // Create an instance of DefaultExtractorsFactory
        DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE);

        // Get the default extractors provided by the configured DefaultExtractorsFactory
        Extractor[] defaultExtractors = defaultExtractorsFactory.createExtractors();

        // Create a new array to include the default extractors and your custom extractor
        Extractor[] customExtractors = new Extractor[defaultExtractors.length + 1];

        // Copy the default extractors to the new array
        System.arraycopy(defaultExtractors, 0, customExtractors, 0, defaultExtractors.length);

        // Add your custom extractor (replace MtsExtractor with your actual custom extractor)
        customExtractors[defaultExtractors.length] = new MtsExtractor();

        // Return the array of extractors
        return customExtractors;
    }
}


