package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zonesafe.zonesafe_be.domain.Alarm;
import me.zonesafe.zonesafe_be.domain.Clip;
import me.zonesafe.zonesafe_be.domain.Video;
import me.zonesafe.zonesafe_be.repository.ClipRepository;
import me.zonesafe.zonesafe_be.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClipExtractionService {

    private final VideoRepository videoRepository;
    private final ClipRepository clipRepository;

    @Value("${videos.storage.base-path:./storage/videos}")
    private String videosBasePath;

    @Value("${clips.storage.base-path:./storage/clips}")
    private String clipsBasePath;

    @Value("${clips.extract.window-sec:5}")
    private int windowSec;

    // 업로드 영상에서 알람 발생 시각의 ±windowSec 구간을 ffmpeg 로 잘라 Clip 으로 저장.
    // 실패 시 null 반환 — 호출 측의 알람 저장 흐름을 막지 않음.
    @Transactional
    public Clip extractForAlarm(Alarm alarm, Long videoId, double centerSec) {
        try {
            Video video = videoRepository.findById(videoId).orElse(null);
            if (video == null) {
                log.warn("클립 추출 스킵 — videoId={} 영상 없음", videoId);
                return null;
            }

            Path srcPath = Paths.get(videosBasePath).toAbsolutePath()
                    .resolve(video.getFilePath()).normalize();
            if (!Files.exists(srcPath)) {
                log.warn("클립 추출 스킵 — 원본 영상 파일 없음: {}", srcPath);
                return null;
            }

            double start = Math.max(0.0, centerSec - windowSec);
            double end = centerSec + windowSec;
            double duration = end - start;

            Path clipsDir = Paths.get(clipsBasePath).toAbsolutePath().normalize();
            Files.createDirectories(clipsDir);

            String storedFilename = UUID.randomUUID() + ".mp4";
            Path outPath = clipsDir.resolve(storedFilename);

            runFfmpegExtract(srcPath, outPath, start, duration);

            long fileSize;
            try {
                fileSize = Files.size(outPath);
            } catch (IOException e) {
                fileSize = 0L;
            }

            String thumbFilename = storedFilename.substring(0, storedFilename.length() - 4) + ".jpg";
            Path thumbPath = clipsDir.resolve(thumbFilename);
            try {
                runFfmpegThumbnail(outPath, thumbPath, duration / 2.0);
            } catch (Exception e) {
                log.warn("클립 썸네일 생성 실패 (무시): {}", e.getMessage());
                thumbFilename = null;
            }

            ZonedDateTime occurredAt = alarm.getOccurredAt() != null ? alarm.getOccurredAt() : ZonedDateTime.now();
            ZonedDateTime startAt = occurredAt.minusSeconds((long) Math.round(centerSec - start));
            ZonedDateTime endAt = occurredAt.plusSeconds((long) Math.round(end - centerSec));

            Clip clip = new Clip();
            clip.setCamera(alarm.getCamera());
            clip.setAlarmId(alarm.getAlarmId());
            clip.setDuration((int) Math.round(duration));
            clip.setFileSize(fileSize);
            clip.setFormat("mp4");
            clip.setFilePath(storedFilename);
            clip.setThumbnailPath(thumbFilename);
            clip.setOccurredAt(occurredAt);
            clip.setStartAt(startAt);
            clip.setEndAt(endAt);

            Clip saved = clipRepository.save(clip);
            log.info("클립 추출 완료 — clipId={}, alarmId={}, videoId={}, {}s ~ {}s",
                    saved.getClipId(), alarm.getAlarmId(), videoId, start, end);
            return saved;
        } catch (Exception e) {
            log.error("클립 추출 실패 — alarmId={}, videoId={}, t={}s",
                    alarm.getAlarmId(), videoId, centerSec, e);
            return null;
        }
    }

    private void runFfmpegExtract(Path src, Path out, double start, double duration)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-ss", Double.toString(start),
                "-i", src.toString(),
                "-t", Double.toString(duration),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-c:a", "aac",
                "-movflags", "+faststart",
                out.toString()
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (java.io.InputStream in = proc.getInputStream()) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        if (!proc.waitFor(60, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new IOException("ffmpeg 클립 추출 시간 초과");
        }
        if (proc.exitValue() != 0) {
            throw new IOException("ffmpeg 클립 추출 실패 (exit=" + proc.exitValue() + ")");
        }
    }

    private void runFfmpegThumbnail(Path src, Path out, double atSec)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-ss", Double.toString(Math.max(0.0, atSec)),
                "-i", src.toString(),
                "-frames:v", "1",
                "-q:v", "3",
                out.toString()
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (java.io.InputStream in = proc.getInputStream()) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        if (!proc.waitFor(15, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new IOException("ffmpeg 썸네일 시간 초과");
        }
        if (proc.exitValue() != 0) {
            throw new IOException("ffmpeg 썸네일 실패 (exit=" + proc.exitValue() + ")");
        }
    }
}