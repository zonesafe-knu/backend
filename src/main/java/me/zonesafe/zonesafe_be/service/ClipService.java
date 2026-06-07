package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zonesafe.zonesafe_be.domain.Camera;
import me.zonesafe.zonesafe_be.domain.Clip;
import me.zonesafe.zonesafe_be.domain.Video;
import me.zonesafe.zonesafe_be.dto.ClipResponseDto;
import me.zonesafe.zonesafe_be.repository.CameraRepository;
import me.zonesafe.zonesafe_be.repository.ClipRepository;
import me.zonesafe.zonesafe_be.repository.VideoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@Transactional(readOnly = true)
public class ClipService {
    private final ClipRepository clipRepository;
    private final CameraRepository cameraRepository;
    private final VideoRepository videoRepository;
    private final ModelMapper modelMapper;

    @Value("${clips.storage.base-path}")
    private String basePath;

    @Value("${videos.storage.base-path:./storage/videos}")
    private String videosBasePath;

    private static final int CLIP_HALF_DURATION_SEC = 5;

    //클립 목록 조회
    public Page<ClipResponseDto> getClips(Long cameraId, ZonedDateTime from, ZonedDateTime to, Pageable pageable) {
        Page<Clip> clips = clipRepository.findAllByFilter(cameraId, from, to, pageable);
        return clips.map(this::convertToDto);
    }

    //클립 다운로드용 리소스 조회
    public Resource loadClipResource(Long clipId) {
        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클립이 존재하지 않습니다. ID: " + clipId));

        Path path = resolvePath(clip.getFilePath());
        FileSystemResource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("클립 파일이 존재하지 않습니다. ID: " + clipId);
        }
        return resource;
    }

    //썸네일 리소스 조회
    public Resource loadThumbnailResource(Long clipId) {
        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클립이 존재하지 않습니다. ID: " + clipId));

        if (clip.getThumbnailPath() == null || clip.getThumbnailPath().isBlank()) {
            throw new IllegalArgumentException("해당 클립의 썸네일이 없습니다. ID: " + clipId);
        }

        Path path = resolvePath(clip.getThumbnailPath());
        FileSystemResource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("썸네일 파일이 존재하지 않습니다. ID: " + clipId);
        }
        return resource;
    }

    public Clip getClip(Long clipId) {
        return clipRepository.findById(clipId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클립이 존재하지 않습니다. ID: " + clipId));
    }

    //클립 삭제 (파일 + DB)
    @Transactional
    public void deleteClip(Long clipId) {
        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new IllegalArgumentException("삭제하려는 클립이 존재하지 않습니다. ID: " + clipId));

        deleteFileQuietly(clip.getFilePath());
        deleteFileQuietly(clip.getThumbnailPath());

        clipRepository.delete(clip);
    }

    private void deleteFileQuietly(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return;
        try {
            Files.deleteIfExists(resolvePath(storedPath));
        } catch (IOException ignored) {
        }
    }

    private Path resolvePath(String storedPath) {
        Path stored = Paths.get(storedPath);
        return stored.isAbsolute() ? stored : Paths.get(basePath).resolve(storedPath).normalize();
    }

    // 업로드된 영상의 videoTimeSec 기준 ±5초(총 10초) 구간을 ffmpeg로 잘라 클립으로 저장
    @Transactional
    public Clip extractAndSaveClip(Long alarmId, Long cameraId, Long videoId,
                                   double videoTimeSec, ZonedDateTime occurredAt) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("해당 카메라가 존재하지 않습니다. ID: " + cameraId));
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("해당 영상이 존재하지 않습니다. ID: " + videoId));

        Path videoPath = resolveVideoPath(video.getFilePath());
        if (!Files.exists(videoPath)) {
            throw new IllegalStateException("영상 파일이 존재하지 않습니다: " + videoPath);
        }

        double videoDuration = video.getDuration() != null ? video.getDuration().doubleValue() : Double.MAX_VALUE;
        double startSec = Math.max(0.0, videoTimeSec - CLIP_HALF_DURATION_SEC);
        double endSec = Math.min(videoDuration, videoTimeSec + CLIP_HALF_DURATION_SEC);
        double durationSec = endSec - startSec;
        if (durationSec <= 0) {
            throw new IllegalStateException("클립 구간 계산이 잘못되었습니다. start=" + startSec + ", end=" + endSec);
        }

        String storedFilename = UUID.randomUUID() + ".mp4";
        Path clipDir;
        Path outputPath;
        try {
            clipDir = Paths.get(basePath).toAbsolutePath().normalize();
            Files.createDirectories(clipDir);
            outputPath = clipDir.resolve(storedFilename);
        } catch (IOException e) {
            throw new IllegalStateException("클립 저장 디렉토리 생성 실패: " + e.getMessage(), e);
        }

        runFfmpegCut(videoPath, outputPath, startSec, durationSec);

        long fileSize;
        try {
            fileSize = Files.size(outputPath);
        } catch (IOException e) {
            fileSize = 0L;
        }

        Clip clip = new Clip();
        clip.setCamera(camera);
        clip.setAlarmId(alarmId);
        clip.setDuration((int) Math.round(durationSec));
        clip.setFileSize(fileSize);
        clip.setFormat("mp4");
        clip.setFilePath(storedFilename);
        clip.setOccurredAt(occurredAt);
        clip.setStartAt(occurredAt.minusSeconds((long) Math.round(videoTimeSec - startSec)));
        clip.setEndAt(occurredAt.plusSeconds((long) Math.round(endSec - videoTimeSec)));

        Clip saved = clipRepository.save(clip);
        log.info("클립 저장 완료: clipId={}, alarmId={}, file={}, duration={}s",
                saved.getClipId(), alarmId, storedFilename, saved.getDuration());
        return saved;
    }

    // 정확한 구간 추출을 위해 재인코딩(ultrafast). 키프레임 어긋남으로 인한 검은 화면 방지.
    private void runFfmpegCut(Path inputPath, Path outputPath, double startSec, double durationSec) {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-ss", String.format(java.util.Locale.US, "%.3f", startSec),
                "-i", inputPath.toString(),
                "-t", String.format(java.util.Locale.US, "%.3f", durationSec),
                "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23",
                "-c:a", "aac",
                "-movflags", "+faststart",
                outputPath.toString()
        );
        pb.redirectErrorStream(true);
        try {
            Process proc = pb.start();
            try (java.io.InputStream in = proc.getInputStream()) {
                in.transferTo(java.io.OutputStream.nullOutputStream());
            }
            if (!proc.waitFor(2, TimeUnit.MINUTES)) {
                proc.destroyForcibly();
                throw new IOException("ffmpeg 클립 추출 시간 초과");
            }
            if (proc.exitValue() != 0) {
                Files.deleteIfExists(outputPath);
                throw new IOException("ffmpeg 클립 추출 실패 (exit=" + proc.exitValue() + ")");
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("클립 추출 실패: " + e.getMessage(), e);
        }
    }

    private Path resolveVideoPath(String storedPath) {
        Path stored = Paths.get(storedPath);
        return stored.isAbsolute() ? stored : Paths.get(videosBasePath).toAbsolutePath().resolve(storedPath).normalize();
    }

    private ClipResponseDto convertToDto(Clip clip) {
        ClipResponseDto dto = modelMapper.map(clip, ClipResponseDto.class);
        dto.setCameraId(clip.getCamera().getCameraId());
        dto.setDownloadUrl("/api/v1/clips/" + clip.getClipId() + "/download");
        dto.setStreamUrl("/api/v1/clips/" + clip.getClipId() + "/stream");
        dto.setThumbnailUrl("/api/v1/clips/" + clip.getClipId() + "/thumbnail");
        return dto;
    }
}
