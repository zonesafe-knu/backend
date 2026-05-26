package me.zonesafe.zonesafe_be.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zonesafe.zonesafe_be.domain.Video;
import me.zonesafe.zonesafe_be.domain.VideoAnalysisEvent;
import me.zonesafe.zonesafe_be.domain.VideoAnalysisJob;
import me.zonesafe.zonesafe_be.dto.VideoAnalysisJobStatusDto;
import me.zonesafe.zonesafe_be.dto.VideoAnalyzeJobResponseDto;
import me.zonesafe.zonesafe_be.dto.VideoAnalyzeRequestDto;
import me.zonesafe.zonesafe_be.dto.VideoEventResponseDto;
import me.zonesafe.zonesafe_be.enums.AnalysisJobStatus;
import me.zonesafe.zonesafe_be.repository.VideoAnalysisEventRepository;
import me.zonesafe.zonesafe_be.repository.VideoAnalysisJobRepository;
import me.zonesafe.zonesafe_be.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoAnalysisService {
    private final VideoRepository videoRepository;
    private final VideoAnalysisJobRepository jobRepository;
    private final VideoAnalysisEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Value("${detection.python-path:python}")
    private String pythonPath;

    @Value("${detection.script-dir:./detection}")
    private String scriptDir;

    @Value("${detection.model-path:./detection/best.pt}")
    private String modelPath;

    @Value("${videos.storage.base-path:./storage/videos}")
    private String videosBasePath;

    private final ExecutorService detectionExecutor = Executors.newCachedThreadPool();

    //영상 탐지 이벤트 목록
    public List<VideoEventResponseDto> getEventsByVideoId(Long videoId) {
        if (!videoRepository.existsById(videoId)) {
            throw new IllegalArgumentException("해당 영상이 존재하지 않습니다. ID: " + videoId);
        }
        List<VideoAnalysisEvent> events = eventRepository.findAllByVideo_VideoIdOrderByFrameTimestampAsc(videoId);
        return events.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    private VideoEventResponseDto convertToDto(VideoAnalysisEvent event) {
        VideoEventResponseDto dto = new VideoEventResponseDto();
        dto.setEventId(event.getEventId());
        dto.setVideoId(event.getVideo().getVideoId());
        dto.setFrameTimestamp(event.getFrameTimestamp());
        dto.setSeverity(event.getSeverity());
        dto.setType(event.getType());
        dto.setRoiId(event.getRoiId());
        dto.setClipId(event.getClipId());
        dto.setOccurredAt(event.getOccurredAt());
        dto.setDetections(parseDetections(event.getDetectionsJson()));
        return dto;
    }

    private List<VideoEventResponseDto.DetectionDto> parseDetections(String detectionsJson) {
        if (detectionsJson == null || detectionsJson.isBlank()) return null;
        try {
            return objectMapper.readValue(detectionsJson,
                    new TypeReference<List<VideoEventResponseDto.DetectionDto>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    //분석 작업 상태 조회
    public VideoAnalysisJobStatusDto getJobStatus(Long videoId, String jobId) {
        VideoAnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("해당 분석 작업이 존재하지 않습니다. ID: " + jobId));

        Long jobVideoId = job.getVideo().getVideoId();
        if (!jobVideoId.equals(videoId)) {
            throw new IllegalArgumentException("해당 영상의 분석 작업이 아닙니다. videoId=" + videoId + ", jobId=" + jobId);
        }

        VideoAnalysisJobStatusDto dto = new VideoAnalysisJobStatusDto();
        dto.setJobId(job.getJobId());
        dto.setVideoId(jobVideoId);
        dto.setStatus(job.getStatus());

        if (job.getStatus() == AnalysisJobStatus.RUNNING) {
            dto.setProgress(job.getProgress());
            dto.setFramesProcessed(job.getFramesProcessed());
            dto.setTotalFrames(job.getTotalFrames());
            dto.setEventsDetected(job.getEventsDetected());
            dto.setStartedAt(job.getStartedAt());
            dto.setEstimatedRemainingSec(job.getEstimatedRemainingSec());
        } else if (job.getStatus() == AnalysisJobStatus.COMPLETED) {
            dto.setCompletedAt(job.getCompletedAt());
            dto.setResult(parseResult(job.getResultJson()));
        }
        return dto;
    }

    private VideoAnalysisJobStatusDto.Result parseResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) return null;
        try {
            return objectMapper.readValue(resultJson, VideoAnalysisJobStatusDto.Result.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    //분석 작업 시작
    @Transactional
    public VideoAnalyzeJobResponseDto startAnalysis(Long videoId, VideoAnalyzeRequestDto request) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("해당 영상이 존재하지 않습니다. ID: " + videoId));

        VideoAnalysisJob job = new VideoAnalysisJob();
        job.setJobId(generateJobId());
        job.setVideo(video);
        job.setModelId(request != null ? request.getModelId() : null);
        job.setStatus(AnalysisJobStatus.QUEUED);
        job.setProgress(0.0);

        VideoAnalysisJob saved = jobRepository.save(job);

        Long cameraId = video.getCameraContext();
        if (cameraId != null) {
            detectionExecutor.submit(() -> runDetectionScript(saved.getJobId(), video, cameraId));
        } else {
            log.warn("영상에 카메라 연결 정보(cameraContext)가 없어 분석을 시작할 수 없습니다. videoId={}", videoId);
        }

        VideoAnalyzeJobResponseDto dto = new VideoAnalyzeJobResponseDto();
        dto.setJobId(saved.getJobId());
        dto.setVideoId(video.getVideoId());
        dto.setStatus(saved.getStatus());
        return dto;
    }

    private void runDetectionScript(String jobId, Video video, Long cameraId) {
        Path videoPath = Paths.get(videosBasePath).toAbsolutePath().resolve(video.getFilePath()).normalize();
        Path scriptPath = Paths.get(scriptDir).toAbsolutePath().resolve("main.py").normalize();
        Path modelAbsPath = Paths.get(modelPath).toAbsolutePath().normalize();

        String backendUrl = "http://localhost:8080";

        try {
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(AnalysisJobStatus.RUNNING);
                job.setStartedAt(ZonedDateTime.now());
                jobRepository.save(job);
            });

            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, "-u", scriptPath.toString(),
                    "--source", videoPath.toString(),
                    "--camera-id", cameraId.toString(),
                    "--backend-url", backendUrl,
                    "--model", modelAbsPath.toString(),
                    "--loop"
            );
            pb.directory(Paths.get(scriptDir).toAbsolutePath().toFile());
            pb.redirectErrorStream(true);

            log.info("분석 시작: jobId={}, videoPath={}, cameraId={}", jobId, videoPath, cameraId);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[detection] {}", line);
                }
            }

            int exitCode = process.waitFor();

            jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(exitCode == 0 ? AnalysisJobStatus.COMPLETED : AnalysisJobStatus.FAILED);
                job.setCompletedAt(ZonedDateTime.now());
                jobRepository.save(job);
            });

            log.info("분석 종료: jobId={}, exitCode={}", jobId, exitCode);
        } catch (Exception e) {
            log.error("분석 실패: jobId={}", jobId, e);
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(AnalysisJobStatus.FAILED);
                jobRepository.save(job);
            });
        }
    }

    private String generateJobId() {
        String raw = UUID.randomUUID().toString().replace("-", "");
        return "anlz-" + raw.substring(0, 4) + "-" + raw.substring(4, 8);
    }
}
