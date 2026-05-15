package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.PageResponseDto;
import me.zonesafe.zonesafe_be.dto.VideoAnalysisJobStatusDto;
import me.zonesafe.zonesafe_be.dto.VideoAnalyzeJobResponseDto;
import me.zonesafe.zonesafe_be.dto.VideoAnalyzeRequestDto;
import me.zonesafe.zonesafe_be.dto.VideoResponseDto;
import me.zonesafe.zonesafe_be.enums.VideoStatus;
import me.zonesafe.zonesafe_be.service.VideoAnalysisService;
import me.zonesafe.zonesafe_be.service.VideoService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {
    private final VideoService videoService;
    private final VideoAnalysisService videoAnalysisService;

    //영상 업로드 (TODO: Spring Security 도입 후 @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") 적용)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> uploadVideo(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long cameraContext,
            @RequestParam(required = false) String description
    ) {
        //TODO: 인증 도입 후 SecurityContext 에서 username 추출
        String uploadedBy = "admin";
        VideoResponseDto dto = videoService.uploadVideo(file, name, siteId, cameraContext, description, uploadedBy);
        return Map.of("success", true, "data", dto);
    }

    //영상 목록 조회
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Map<String, PageResponseDto<VideoResponseDto>> getVideos(
            @RequestParam(required = false) VideoStatus status,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String uploadedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 20, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<VideoResponseDto> pageResult = videoService.getVideos(status, siteId, uploadedBy, from, to, pageable);
        return Map.of("data", new PageResponseDto<>(pageResult));
    }

    //영상 상세 조회
    @GetMapping("/{videoId}")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, VideoResponseDto> getVideoById(@PathVariable Long videoId) {
        return Map.of("data", videoService.getVideoById(videoId));
    }

    //영상 삭제 (TODO: Spring Security 도입 후 @PreAuthorize 로 ADMIN 또는 소유자 검증)
    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVideo(@PathVariable Long videoId) {
        videoService.deleteVideo(videoId);
    }

    //영상 다운로드
    @GetMapping("/{videoId}/download")
    public ResponseEntity<Resource> downloadVideo(@PathVariable Long videoId) {
        Resource resource = videoService.loadVideoResource(videoId);
        String filename = "video_" + videoId + ".mp4";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    //영상 스트리밍 (Range Request 지원)
    private static final long STREAM_CHUNK_SIZE = 1024L * 1024L;

    @GetMapping("/{videoId}/stream")
    public ResponseEntity<ResourceRegion> streamVideo(
            @PathVariable Long videoId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) throws IOException {
        Resource resource = videoService.loadVideoResource(videoId);
        long contentLength = resource.contentLength();

        ResourceRegion region;
        HttpStatus status;
        if (rangeHeader == null || rangeHeader.isBlank()) {
            long rangeLength = Math.min(STREAM_CHUNK_SIZE, contentLength);
            region = new ResourceRegion(resource, 0, rangeLength);
            status = HttpStatus.OK;
        } else {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(STREAM_CHUNK_SIZE, end - start + 1);
            region = new ResourceRegion(resource, start, rangeLength);
            status = HttpStatus.PARTIAL_CONTENT;
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.valueOf("video/mp4"))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    //영상 썸네일
    @GetMapping("/{videoId}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(@PathVariable Long videoId) {
        Resource resource = videoService.loadThumbnailResource(videoId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    //영상 분석 시작 (TODO: Spring Security 도입 후 @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')") 적용)
    @PostMapping("/{videoId}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, VideoAnalyzeJobResponseDto> startAnalysis(
            @PathVariable Long videoId,
            @RequestBody(required = false) VideoAnalyzeRequestDto request
    ) {
        return Map.of("data", videoAnalysisService.startAnalysis(videoId, request));
    }

    //영상 분석 작업 상태 조회
    @GetMapping("/{videoId}/analyze/jobs/{jobId}")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, VideoAnalysisJobStatusDto> getAnalysisJobStatus(
            @PathVariable Long videoId,
            @PathVariable String jobId
    ) {
        return Map.of("data", videoAnalysisService.getJobStatus(videoId, jobId));
    }
}
