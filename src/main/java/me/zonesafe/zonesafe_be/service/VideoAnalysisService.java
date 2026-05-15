package me.zonesafe.zonesafe_be.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.domain.Video;
import me.zonesafe.zonesafe_be.domain.VideoAnalysisJob;
import me.zonesafe.zonesafe_be.dto.VideoAnalysisJobStatusDto;
import me.zonesafe.zonesafe_be.dto.VideoAnalyzeJobResponseDto;
import me.zonesafe.zonesafe_be.dto.VideoAnalyzeRequestDto;
import me.zonesafe.zonesafe_be.enums.AnalysisJobStatus;
import me.zonesafe.zonesafe_be.repository.VideoAnalysisJobRepository;
import me.zonesafe.zonesafe_be.repository.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoAnalysisService {
    private final VideoRepository videoRepository;
    private final VideoAnalysisJobRepository jobRepository;
    private final ObjectMapper objectMapper;

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

        //TODO: roiIds / saveClips / skipFrames / alarmRuleOverride 를 ML 파이프라인에 전달
        //TODO: 비동기 워커가 job 을 RUNNING 으로 전환 후 프레임 단위 추론 수행

        VideoAnalysisJob saved = jobRepository.save(job);

        VideoAnalyzeJobResponseDto dto = new VideoAnalyzeJobResponseDto();
        dto.setJobId(saved.getJobId());
        dto.setVideoId(video.getVideoId());
        dto.setStatus(saved.getStatus());
        return dto;
    }

    private String generateJobId() {
        String raw = UUID.randomUUID().toString().replace("-", "");
        return "anlz-" + raw.substring(0, 4) + "-" + raw.substring(4, 8);
    }
}
