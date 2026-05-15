package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.domain.Video;
import me.zonesafe.zonesafe_be.domain.VideoAnalysisJob;
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
