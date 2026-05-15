package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.PageResponseDto;
import me.zonesafe.zonesafe_be.dto.VideoResponseDto;
import me.zonesafe.zonesafe_be.enums.VideoStatus;
import me.zonesafe.zonesafe_be.service.VideoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZonedDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {
    private final VideoService videoService;

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
}
