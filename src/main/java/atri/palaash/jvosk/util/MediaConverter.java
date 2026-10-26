package atri.palaash.jvosk.util;

import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.ArgType;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingArgument;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.SimpleArgument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

/**
 * Converts audio or video to 16 kHz mono PCM WAV with the bundled JAVE
 * FFmpeg. Optional speech enhancement is a cheap IIR band-limit plus
 * loudness normalize — no extra native libraries and no host ffmpeg.
 */
public final class MediaConverter {

    /**
     * Keep the speech band and lift quiet vocals. highpass/lowpass are IIR;
     * dynaudnorm is a light peak/RMS match. Avoids heavy source-separation.
     */
    public static final String SPEECH_ENHANCE_FILTER =
            "highpass=f=120,lowpass=f=4000,dynaudnorm=f=150:g=15";

    private MediaConverter() {}

    public static File toPcmWav(File source, boolean enhanceSpeech)
            throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Conversion cancelled by user");
        }
        File tempWav = File.createTempFile("jvosk_", ".wav");
        try {
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("pcm_s16le");
            audio.setSamplingRate(16000);
            audio.setChannels(1);
            audio.setBitRate(256000);

            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("wav");
            attrs.setAudioAttributes(audio);

            EncodingArgument extra = new SimpleArgument(ArgType.OUTFILE, ignored -> {
                if (enhanceSpeech) {
                    return Stream.of("-vn", "-af", SPEECH_ENHANCE_FILTER);
                }
                return Stream.of("-vn");
            });

            Encoder encoder = new Encoder();
            encoder.encode(List.of(new MultimediaObject(source)), tempWav, attrs, null, List.of(extra));

            if (Thread.currentThread().isInterrupted()) {
                Files.deleteIfExists(tempWav.toPath());
                throw new InterruptedException("Conversion cancelled by user");
            }
            return tempWav;
        } catch (EncoderException e) {
            Files.deleteIfExists(tempWav.toPath());
            throw new IOException(
                    "Media conversion failed. The file may be corrupted or in an unsupported format.\n"
                            + "Error: " + e.getMessage(), e);
        } catch (IOException | InterruptedException e) {
            Files.deleteIfExists(tempWav.toPath());
            throw e;
        } catch (RuntimeException e) {
            Files.deleteIfExists(tempWav.toPath());
            throw e;
        }
    }
}
